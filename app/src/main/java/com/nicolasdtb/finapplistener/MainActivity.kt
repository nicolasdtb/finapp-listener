package com.nicolasdtb.finapplistener

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val nubankPackage = "com.nu.production"
    private val interPackage = "br.com.intermedium"
    private val bradescoPackage = "com.bradesco"

    private lateinit var checkboxNubank: CheckBox
    private lateinit var checkboxInter: CheckBox
    private lateinit var checkboxBradesco: CheckBox
    private lateinit var textStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkboxNubank = findViewById(R.id.checkboxNubank)
        checkboxInter = findViewById(R.id.checkboxInter)
        checkboxBradesco = findViewById(R.id.checkboxBradesco)
        textStatus = findViewById(R.id.textStatus)

        loadState()

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
    }

    override fun onResume() {
        super.onResume()
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
