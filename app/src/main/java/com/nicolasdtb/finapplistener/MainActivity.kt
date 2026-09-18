package com.nicolasdtb.finapplistener

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val nubankPackage = "com.nu.production"
    private val interPackage = "br.com.intermedium"
    private val bradescoPackage = "com.bradesco"

    private lateinit var checkboxNubank: CheckBox
    private lateinit var checkboxInter: CheckBox
    private lateinit var checkboxBradesco: CheckBox
    private lateinit var textStatus: TextView
    private lateinit var textLog: TextView
    private lateinit var scrollLog: ScrollView

    private val logHandler = Handler(Looper.getMainLooper())
    private val logRefreshInterval = 2000L
    private val logRefreshRunnable = object : Runnable {
        override fun run() {
            refreshLog()
            logHandler.postDelayed(this, logRefreshInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkboxNubank = findViewById(R.id.checkboxNubank)
        checkboxInter = findViewById(R.id.checkboxInter)
        checkboxBradesco = findViewById(R.id.checkboxBradesco)
        textStatus = findViewById(R.id.textStatus)
        textLog = findViewById(R.id.textLog)
        scrollLog = findViewById(R.id.scrollLog)

        loadState()
        requestNotificationPermissionIfNeeded()

        checkboxNubank.setOnCheckedChangeListener { _, checked ->
            AppConfig.setAppEnabled(this, nubankPackage, checked)
        }
        checkboxInter.setOnCheckedChangeListener { _, checked ->
            AppConfig.setAppEnabled(this, interPackage, checked)
        }
        checkboxBradesco.setOnCheckedChangeListener { _, checked ->
            AppConfig.setAppEnabled(this, bradescoPackage, checked)
        }

        findViewById<android.widget.Button>(R.id.buttonEnableListener).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<android.widget.Button>(R.id.buttonClearLog).setOnClickListener {
            AppLog.clear(this)
            refreshLog()
        }
    }

    /**
     * A partir do Android 13 (API 33), mostrar QUALQUER notificação — incluindo
     * a notificação persistente do foreground service que mantém o listener
     * mais resistente a ser encerrado pelo sistema — exige essa permissão em
     * tempo de execução. Sem ela, o serviço ainda roda, mas o Android pode
     * voltar a tratá-lo como processo comum em segundo plano.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        logHandler.post(logRefreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        logHandler.removeCallbacks(logRefreshRunnable)
    }

    private fun refreshLog() {
        val logText = AppLog.readAll(this)
        // Só atualiza (e rola pro fim) se o conteúdo mudou, para não
        // atrapalhar se o usuário estiver com o dedo rolando o scroll.
        if (textLog.text.toString() != logText) {
            textLog.text = logText
            scrollLog.post { scrollLog.fullScroll(android.view.View.FOCUS_DOWN) }
        }
        updateStatus()
    }

    private fun loadState() {
        checkboxNubank.isChecked = AppConfig.isAppEnabled(this, nubankPackage)
        checkboxInter.isChecked = AppConfig.isAppEnabled(this, interPackage)
        checkboxBradesco.isChecked = AppConfig.isAppEnabled(this, bradescoPackage)
    }

    private fun updateStatus() {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()

        val granted = enabledListeners.contains(packageName)
        val accessLine = if (granted) {
            "Acesso a notificações concedido — o listener está ativo em segundo plano."
        } else {
            "Acesso a notificações NÃO concedido. Toque no botão acima e habilite " +
                "\"FinApp Listener\" na lista."
        }

        val queueLine = if (PendingQueue.hasPending(this)) {
            "\n\nHá notificações aguardando reenvio (sem conexão com o FinApp no momento)."
        } else {
            "\n\nNenhuma notificação pendente na fila."
        }

        textStatus.text = accessLine + queueLine
    }
}
