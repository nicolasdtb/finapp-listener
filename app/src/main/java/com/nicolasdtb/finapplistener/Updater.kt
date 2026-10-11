package com.nicolasdtb.finapplistener

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Uma versao mais nova publicada no GitHub Releases. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

/**
 * Atualizacao via GitHub Releases (repositorio publico, sem token).
 * O CI publica uma release "v<numero>" com o APK; aqui comparamos esse numero
 * com o versionCode instalado. O Android nao permite instalar em silencio:
 * baixamos o APK e abrimos o instalador do sistema (um toque de confirmacao).
 */
object Updater {

    private const val LATEST_URL =
        "https://api.github.com/repos/nicolasdtb/finapp-listener/releases/latest"

    fun installedVersionCode(ctx: Context): Int {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION") info.versionCode
        }
    }

    fun installedVersionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()

    /** Retorna a versao nova, ou null se ja esta atualizado / sem rede. Rodar fora da thread principal. */
    fun checkForUpdate(ctx: Context): UpdateInfo? {
        return try {
            val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v")
            val code = tag.toIntOrNull() ?: return null
            if (code <= installedVersionCode(ctx)) return null

            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isNullOrEmpty()) return null
            UpdateInfo(code, "1.$code", apkUrl)
        } catch (e: Exception) {
            AppLog.add(ctx, "Falha ao verificar atualização: ${e.message}")
            null
        }
    }

    /** Baixa o APK para o cache. Rodar fora da thread principal. Retorna o arquivo ou null. */
    fun download(ctx: Context, info: UpdateInfo): File? {
        return try {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "finapp-listener.apk")
            val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode != 200) {
                AppLog.add(ctx, "Download da atualização falhou: HTTP ${conn.responseCode}")
                return null
            }
            conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
            file
        } catch (e: Exception) {
            AppLog.add(ctx, "Download da atualização falhou: ${e.message}")
            null
        }
    }

    /** True se o app pode instalar APKs; senao abre a tela onde o usuario libera. */
    fun ensureInstallPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (ctx.packageManager.canRequestPackageInstalls()) return true
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
