package com.nicolasdtb.finapplistener

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Fila local em disco para notificações que falharam ao ser enviadas.
 * Mesma ideia da fila usada hoje no fluxo do Automate (finapp_queue.txt):
 * uma linha JSON por notificação pendente.
 */
object PendingQueue {

    private const val TAG = "FinAppListener"
    private const val QUEUE_FILE_NAME = "pending_notifications.jsonl"

    private fun queueFile(context: Context): File =
        File(context.filesDir, QUEUE_FILE_NAME)

    @Synchronized
    fun enqueue(context: Context, appName: String, title: String, text: String) {
        val payload = JSONObject().apply {
            put("app_name", appName)
            put("title", title)
            put("text", text)
        }
        try {
            queueFile(context).appendText(payload.toString() + "\n")
            Log.i(TAG, "Notificação de $appName guardada na fila local")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao gravar na fila local", e)
        }
    }

    /**
     * Lê tudo que está pendente, limpa o arquivo, e devolve a lista de
     * payloads (como String JSON) para o chamador tentar reenviar.
     * Itens que falharem de novo devem ser re-enfileirados via [enqueueRaw].
     */
    @Synchronized
    fun drain(context: Context): List<String> {
        val file = queueFile(context)
        if (!file.exists()) return emptyList()

        val lines = try {
            file.readLines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao ler fila local", e)
            emptyList()
        }

        try {
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao limpar fila local após leitura", e)
        }

        return lines
    }

    @Synchronized
    fun enqueueRaw(context: Context, rawJsonLine: String) {
        try {
            queueFile(context).appendText(rawJsonLine.trim() + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao re-enfileirar item", e)
        }
    }

    fun hasPending(context: Context): Boolean {
        val file = queueFile(context)
        return file.exists() && file.length() > 0
    }
}
