package com.nicolasdtb.finapplistener

import android.content.Context
import android.util.Log

/**
 * Tenta reenviar tudo que está pendente em [PendingQueue].
 * Itens que falharem de novo voltam para a fila (não são perdidos).
 */
object QueueFlusher {

    private const val TAG = "FinAppListener"

    @Synchronized
    fun flush(context: Context) {
        val webhookUrl = AppConfig.webhookUrl(context)
        val pending = PendingQueue.drain(context)

        if (pending.isEmpty()) {
            return
        }

        Log.i(TAG, "Tentando reenviar ${pending.size} notificação(ões) pendente(s)")

        var successCount = 0
        var failCount = 0

        for (jsonLine in pending) {
            val success = WebhookSender.sendRaw(webhookUrl, jsonLine, "item da fila")
            if (success) {
                successCount++
            } else {
                failCount++
                PendingQueue.enqueueRaw(context, jsonLine)
            }
        }

        Log.i(TAG, "Flush concluído: $successCount enviada(s), $failCount ainda pendente(s)")
    }
}
