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
object WebhookSender {

    private const val TAG = "FinAppListener"
    private const val TIMEOUT_MS = 5000

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val trustAllHostnames = HostnameVerifier { _, _ -> true }

    fun send(webhookUrl: String, appName: String, title: String, text: String): Boolean {
        val payload = JSONObject().apply {
            put("app_name", appName)
            put("title", title)
            put("text", text)
        }

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
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            val success = responseCode in 200..299
            if (!success) {
                Log.w(TAG, "Webhook respondeu com status $responseCode para $appName")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao enviar notificação de $appName para o webhook", e)
            false
        }
    }
}
