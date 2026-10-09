package com.nicolasdtb.finapplistener

import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * Monta o JSON no mesmo formato usado hoje pelo fluxo do Automate
 * ({"app_name":..., "title":..., "text":...}) e envia via POST.
 *
 * O backend usa um certificado autoassinado gerado via mkcert (CA de
 * desenvolvimento local, não reconhecida por nenhuma cadeia pública).
 * Como o tráfego fica confinado à VPN ZeroTier (mesmo racional do
 * "Trust insecure certificates" já usado no fluxo do Automate), aqui
 * desabilitamos a validação de certificado/hostname especificamente
 * para essa conexão, em vez de embutir a CA do mkcert no app.
 */
/** O que fazer com uma notificacao depois de tentar enviar. */
enum class Outcome {
    /** Servidor aceitou (2xx). */
    SUCCESS,
    /** Falha temporaria (rede, 5xx, 401 token errado...): manter na fila e tentar depois. */
    RETRY,
    /** Servidor recusou de forma definitiva (ex: 400): reenviar nao adianta, descartar. */
    DROP
}

data class SendResult(val outcome: Outcome, val code: Int? = null)

object WebhookSender {

    private const val TAG = "FinAppListener"
    private const val TIMEOUT_MS = 5000

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val trustAllHostnames = HostnameVerifier { _, _ -> true }

    fun send(webhookUrl: String, token: String, appName: String, title: String, text: String): SendResult {
        val payload = JSONObject().apply {
            put("app_name", appName)
            put("title", title)
            put("text", text)
        }
        return sendRaw(webhookUrl, token, payload.toString(), appName)
    }

    /**
     * 2xx = enviado. 401/403/408/429, 5xx e falhas de rede = tentar de novo depois
     * (401 inclui token errado: o item fica guardado ate o token ser corrigido).
     * Demais 4xx (ex: 400 "nao consegui extrair valor") = definitivo, nao volta para a fila.
     */
    private fun classify(code: Int): Outcome = when {
        code in 200..299 -> Outcome.SUCCESS
        code in listOf(401, 403, 408, 429) -> Outcome.RETRY
        code in 400..499 -> Outcome.DROP
        else -> Outcome.RETRY
    }

    /**
     * Envia um payload JSON já pronto (usado ao reenviar itens vindos da
     * fila local em [PendingQueue]).
     */
    fun sendRaw(webhookUrl: String, token: String, jsonPayload: String, labelForLog: String = "item da fila"): SendResult {
        return try {
            val url = URL(webhookUrl)
            val connection = (url.openConnection() as HttpURLConnection)

            if (connection is HttpsURLConnection) {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                connection.sslSocketFactory = sslContext.socketFactory
                connection.hostnameVerifier = trustAllHostnames
            }

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (token.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${token.trim()}")
                }
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            val outcome = classify(responseCode)
            if (outcome != Outcome.SUCCESS) {
                Log.w(TAG, "Webhook respondeu com status $responseCode para $labelForLog")
            }
            SendResult(outcome, responseCode)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao enviar $labelForLog para o webhook", e)
            SendResult(Outcome.RETRY)
        }
    }
}
