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
        val webhookToken = AppConfig.webhookToken(context)
        val pending = PendingQueue.drain(context)

        if (pending.isEmpty()) {
            return
        }

        Log.i(TAG, "Tentando reenviar ${pending.size} notificação(ões) pendente(s)")
        AppLog.add(context, "Flush: ${pending.size} item(ns) pendente(s) na fila")

        var successCount = 0
        var droppedCount = 0
        val stillPending = mutableListOf<String>()

        for (jsonLine in pending) {
            val result = WebhookSender.sendRaw(webhookUrl, webhookToken, jsonLine, "item da fila")
            when (result.outcome) {
                Outcome.SUCCESS -> successCount++
                Outcome.DROP -> {
                    droppedCount++
                    AppLog.add(context, "Item da fila recusado pelo servidor (HTTP ${result.code}) — descartado")
                }
                Outcome.RETRY -> {
                    stillPending.add(jsonLine)
                    if (result.code == 401) {
                        AppLog.add(context, "Servidor recusou o token (HTTP 401) — confira o token no app")
                    }
                }
            }
        }

        // So agora o arquivo "em envio" e apagado; o que falhou volta para a fila.
        PendingQueue.finishDrain(context, stillPending)

        val summary = "Flush concluído: $successCount enviada(s), ${stillPending.size} ainda pendente(s)" +
            if (droppedCount > 0) ", $droppedCount descartada(s)" else ""
        Log.i(TAG, summary)
        AppLog.add(context, summary)
    }
}
