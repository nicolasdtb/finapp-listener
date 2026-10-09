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

    // Arquivo onde ficam os itens "em envio". So e apagado depois que o envio termina,
    // entao se o app for encerrado no meio nada se perde (no maximo um item e reenviado,
    // e o servidor ignora duplicatas).
    private const val PROCESSING_FILE_NAME = "pending_notifications.processing.jsonl"

    // Limite da fila: acima disso os itens mais antigos sao descartados.
    private const val MAX_ITEMS = 500

    private fun queueFile(context: Context): File =
        File(context.filesDir, QUEUE_FILE_NAME)

    private fun processingFile(context: Context): File =
        File(context.filesDir, PROCESSING_FILE_NAME)

    private fun readLinesSafe(file: File): List<String> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao ler ${file.name}", e)
            emptyList()
        }
    }

    private fun writeCapped(file: File, lines: List<String>) {
        val capped = if (lines.size > MAX_ITEMS) {
            Log.w(TAG, "Fila acima de $MAX_ITEMS itens — descartando ${lines.size - MAX_ITEMS} mais antigo(s)")
            lines.takeLast(MAX_ITEMS)
        } else lines
        if (capped.isEmpty()) {
            file.delete()
        } else {
            file.writeText(capped.joinToString("\n") + "\n")
        }
    }

    @Synchronized
    fun enqueue(context: Context, appName: String, title: String, text: String) {
        val payload = JSONObject().apply {
            put("app_name", appName)
            put("title", title)
            put("text", text)
        }
        enqueueRaw(context, payload.toString())
        Log.i(TAG, "Notificação de $appName guardada na fila local")
    }

    @Synchronized
    fun enqueueRaw(context: Context, rawJsonLine: String) {
        try {
            val file = queueFile(context)
            writeCapped(file, readLinesSafe(file) + rawJsonLine.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao gravar na fila local", e)
        }
    }

    /**
     * Pega tudo que esta pendente para tentar enviar. Os itens passam para o arquivo
     * "em envio" e continuam la ate [finishDrain] ser chamado.
     */
    @Synchronized
    fun drain(context: Context): List<String> {
        val processing = processingFile(context)
        val queue = queueFile(context)
        val all = readLinesSafe(processing) + readLinesSafe(queue)
        if (all.isEmpty()) return emptyList()

        try {
            writeCapped(processing, all)
            queue.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao preparar envio da fila local", e)
        }
        return all.takeLast(MAX_ITEMS)
    }

    /**
     * Conclui um envio: [failed] sao os itens que devem continuar na fila (na frente
     * de qualquer notificacao nova que tenha chegado durante o envio).
     */
    @Synchronized
    fun finishDrain(context: Context, failed: List<String>) {
        try {
            val queue = queueFile(context)
            writeCapped(queue, failed + readLinesSafe(queue))
            processingFile(context).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao concluir envio da fila local", e)
        }
    }

    fun hasPending(context: Context): Boolean {
        val queue = queueFile(context)
        val processing = processingFile(context)
        return (queue.exists() && queue.length() > 0) || (processing.exists() && processing.length() > 0)
    }
}
