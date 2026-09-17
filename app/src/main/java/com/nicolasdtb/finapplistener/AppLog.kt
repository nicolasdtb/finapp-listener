package com.nicolasdtb.finapplistener

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log de atividade simples, gravado em arquivo, para a tela do app mostrar
 * um "tail" do que está acontecendo (chegada de notificação, tentativa de
 * envio, sucesso, falha, flush da fila, mudança de rede, etc).
 *
 * Mantém só as últimas [MAX_LINES] linhas para não crescer indefinidamente.
 */
object AppLog {

    private const val LOG_FILE_NAME = "activity_log.txt"
    private const val MAX_LINES = 200

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private fun logFile(context: Context): File =
        File(context.filesDir, LOG_FILE_NAME)

    @Synchronized
    fun add(context: Context, message: String) {
        val timestamp = timeFormat.format(Date())
        val line = "[$timestamp] $message"

        val file = logFile(context)
        try {
            val existingLines = if (file.exists()) file.readLines() else emptyList()
            val newLines = (existingLines + line).takeLast(MAX_LINES)
            file.writeText(newLines.joinToString("\n") + "\n")
        } catch (e: Exception) {
            android.util.Log.e("FinAppListener", "Falha ao gravar log de atividade", e)
        }
    }

    @Synchronized
    fun readAll(context: Context): String {
        val file = logFile(context)
        if (!file.exists()) return "Nenhuma atividade registrada ainda."
        return try {
            file.readText().ifBlank { "Nenhuma atividade registrada ainda." }
        } catch (e: Exception) {
            "Erro ao ler o log: ${e.message}"
        }
    }

    @Synchronized
    fun clear(context: Context) {
        try {
            logFile(context).delete()
        } catch (e: Exception) {
            android.util.Log.e("FinAppListener", "Falha ao limpar log de atividade", e)
        }
    }
}
