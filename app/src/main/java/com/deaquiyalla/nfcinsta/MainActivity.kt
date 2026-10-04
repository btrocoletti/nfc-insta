package com.deaquiyalla.nfcinsta

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        val title = TextView(this).apply {
            text = "NFC Instagram"
            textSize = 26f
        }
        val help = TextView(this).apply {
            text = "Escribí tu usuario o link. Con la pantalla encendida, apoyá la parte de atrás " +
                "de tu S24 contra la del otro teléfono y se le abre tu perfil."
            textSize = 15f
            setPadding(0, pad / 2, 0, pad)
        }
        val input = EditText(this).apply {
            hint = "usuario o https://instagram.com/usuario"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(Prefs.url(this@MainActivity))
        }
        val save = Button(this).apply {
            text = "Guardar"
            setOnClickListener {
                val url = normalize(input.text.toString())
                Prefs.save(this@MainActivity, url)
                input.setText(url)
                Toast.makeText(this@MainActivity, "Listo: $url", Toast.LENGTH_SHORT).show()
            }
        }
        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, pad, 0, 0)
            gravity = Gravity.START
        }
        val nfcSettings = Button(this).apply {
            text = "Abrir ajustes de NFC"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
        }

        listOf(title, help, input, save, status, nfcSettings).forEach(root::addView)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val adapter = NfcAdapter.getDefaultAdapter(this)
        status.text = when {
            adapter == null -> "Este teléfono no tiene NFC."
            !adapter.isEnabled -> "NFC desactivado. Activalo para compartir."
            else -> "NFC activo. Listo para compartir."
        }
        // Mientras la app está abierta, nuestro servicio tiene prioridad sobre otros que usen el mismo AID
        adapter?.let {
            CardEmulation.getInstance(it).setPreferredService(
                this, ComponentName(this, NdefHceService::class.java)
            )
        }
    }

    override fun onPause() {
        super.onPause()
        NfcAdapter.getDefaultAdapter(this)?.let {
            CardEmulation.getInstance(it).unsetPreferredService(this)
        }
    }

    private fun normalize(raw: String): String {
        val s = raw.trim().removePrefix("@")
        return when {
            s.isEmpty() -> Prefs.DEFAULT_URL
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.contains(".") || s.contains("/") -> "https://$s"
            else -> "https://instagram.com/$s"
        }
    }
}
