package com.nicolasdtb.finapplistener

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors

class BankNotificationListenerService : NotificationListenerService() {

    private val executor = Executors.newSingleThreadExecutor()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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

        AppLog.add(applicationContext, "Notificação recebida de $appName")

        val webhookUrl = AppConfig.webhookUrl(applicationContext)

        // Envia em background para não bloquear o listener do sistema.
        executor.execute {
            // Antes de enviar a nova, tenta drenar qualquer coisa que já
            // estivesse pendente — assim a ordem de chegada é preservada
            // quando a rede volta a funcionar.
            QueueFlusher.flush(applicationContext)

            AppLog.add(applicationContext, "Enviando notificação de $appName para o FinApp...")
            val success = WebhookSender.send(webhookUrl, appName, title, text)
            if (success) {
                AppLog.add(applicationContext, "Enviado com sucesso: $appName")
            } else {
                Log.w("FinAppListener", "Falha ao enviar notificação de $appName — guardando na fila")
                AppLog.add(applicationContext, "Falha ao enviar $appName — guardado na fila local")
                PendingQueue.enqueue(applicationContext, appName, title, text)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("FinAppListener", "Listener de notificações conectado")
        AppLog.add(applicationContext, "Serviço de notificações conectado")

        // Tenta drenar a fila assim que o serviço sobe (ex: reboot do celular).
        executor.execute { QueueFlusher.flush(applicationContext) }

        registerNetworkCallback()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AppLog.add(applicationContext, "Serviço de notificações desconectado")
        unregisterNetworkCallback()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
    }

    /**
     * Fica de olho em mudanças de rede (equivalente ao trigger "Network state
     * changed" que era usado no fluxo do Automate) e tenta reenviar o que
     * estiver pendente sempre que uma rede com internet ficar disponível —
     * inclui o momento em que a VPN ZeroTier conecta.
     *
     * IMPORTANTE: por padrão, um NetworkRequest exclui redes VPN da busca
     * (via a capability NET_CAPABILITY_NOT_VPN, incluída implicitamente).
     * Como o cenário principal aqui é justamente detectar quando a VPN
     * conecta, é necessário remover essa capability explicitamente — do
     * contrário o callback nunca dispara para a interface do ZeroTier.
     */
    private fun registerNetworkCallback() {
        val connectivityManager =
            getSystemService(ConnectivityManager::class.java) ?: return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                AppLog.add(applicationContext, "Rede disponível (possível VPN conectando)")
                if (!PendingQueue.hasPending(applicationContext)) {
                    AppLog.add(applicationContext, "Nenhuma notificação pendente — nada a reenviar")
                    return
                }
                Log.i("FinAppListener", "Rede disponível — tentando reenviar fila pendente")
                executor.execute {
                    // Pequena espera para dar tempo da rota da VPN estabilizar de fato.
                    Thread.sleep(2000)
                    AppLog.add(applicationContext, "Tentando reenviar fila pendente...")
                    QueueFlusher.flush(applicationContext)
                }
            }

            override fun onLost(network: Network) {
                AppLog.add(applicationContext, "Rede perdida")
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.e("FinAppListener", "Falha ao registrar callback de rede", e)
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        try {
            getSystemService(ConnectivityManager::class.java)
                ?.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.e("FinAppListener", "Falha ao desregistrar callback de rede", e)
        }
        networkCallback = null
    }
}
