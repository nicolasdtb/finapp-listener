package com.nicolasdtb.finapplistener

import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Monta o JSON no mesmo formato usado hoje pelo fluxo do Automate
 * ({"app_name":..., "title":..., "text":...}) e envia via POST.
 */
object WebhookSender {

    private const val TAG = "FinAppListener"
    private const val TIMEOUT_MS = 5000

    fun send(webhookUrl: String, appName: String, title: String, text: String): Boolean {
        val payload = JSONObject().apply {
            put("app_name", appName)
            put("title", title)
            put("text", text)
        }

        return try {
            val url = URL(webhookUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
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
