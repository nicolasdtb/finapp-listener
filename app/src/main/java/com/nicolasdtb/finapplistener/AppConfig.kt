package com.nicolasdtb.finapplistener

import android.content.Context

/**
 * Configuração central do listener: URL do webhook, mapa de bancos monitorados
 * e persistência simples via SharedPreferences.
 *
 * Para trocar a URL do webhook no futuro, não é necessário recompilar o app:
 * edite o valor em [PREFS_WEBHOOK_URL] pela própria tela de configurações
 * (ou ajuste WEBHOOK_URL_DEFAULT aqui e recompile, se preferir hardcoded).
 */
object AppConfig {

    private const val PREFS_NAME = "finapp_listener_prefs"
    private const val PREFS_WEBHOOK_URL = "webhook_url"

    // Mesma URL usada hoje no fluxo do Automate.
    const val WEBHOOK_URL_DEFAULT = "https://172.23.17.157/api/v1/webhooks/bank-notification"

    // package -> nome de exibição (usado tanto na tela quanto no payload "app_name")
    val MONITORED_APPS = linkedMapOf(
        "com.nu.production" to "Nubank",
        "br.com.intermedium" to "Banco Inter",
        "com.bradesco" to "Bradesco"
    )

    fun webhookUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREFS_WEBHOOK_URL, WEBHOOK_URL_DEFAULT) ?: WEBHOOK_URL_DEFAULT
    }

    fun isAppEnabled(context: Context, packageName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(prefKeyFor(packageName), false)
    }

    fun setAppEnabled(context: Context, packageName: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(prefKeyFor(packageName), enabled).apply()
    }

    private fun prefKeyFor(packageName: String) = "enabled_$packageName"
}
