package com.nicolasdtb.finapplistener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors

class BankNotificationListenerService : NotificationListenerService() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val appName = AppConfig.MONITORED_APPS[packageName] ?: return

        if (!AppConfig.isAppEnabled(applicationContext, packageName)) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()

        if (title.isEmpty() && text.isEmpty()) {
            // Notificação sem conteúdo útil (ex: notificação de grupo/resumo) — ignora.
            return
        }

        val webhookUrl = AppConfig.webhookUrl(applicationContext)

        // Envia em background para não bloquear o listener do sistema.
        executor.execute {
            val success = WebhookSender.send(webhookUrl, appName, title, text)
            if (!success) {
                Log.w("FinAppListener", "Não foi possível enviar notificação de $appName agora")
                // Ponto de extensão futuro: gravar em fila local (arquivo) e
                // reenviar depois, como já é feito hoje no fluxo do Automate.
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("FinAppListener", "Listener de notificações conectado")
    }
}
