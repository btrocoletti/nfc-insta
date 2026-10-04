package com.deaquiyalla.nfcinsta

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Emula una etiqueta NFC Forum Type 4 que contiene un registro NDEF con una URL.
 * El teléfono que lo lee la trata como un sticker NFC normal y abre el link.
 */
class NdefHceService : HostApduService() {

    private val ok = byteArrayOf(0x90.toByte(), 0x00)
    private val notFound = byteArrayOf(0x6A.toByte(), 0x82.toByte())

    private val ndefAid = hex("D2760000850101")
    private val ccId = hex("E103")
    private val ndefId = hex("E104")

    // Capability Container: versión 2.0, NDEF file E104, lectura libre, sin escritura
    private val ccFile = hex("000F20003B00340406E1047FFF00FF")

    // El NDEF se arma una sola vez (y cuando cambia la URL), no en cada lectura
    private var cachedUrl: String? = null
    private var ndefFile = ByteArray(0)
    private var selected: ByteArray? = null

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        if (apdu.size < 4) return notFound
        val ins = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF

        // SELECT: se compara solo el dato (AID o ID de archivo), sin exigir P2 ni Le exactos,
        // porque cada lector (iPhone, Samsung, Pixel) manda variantes distintas.
        if (ins == 0xA4) {
            val data = selectData(apdu) ?: return notFound
            return when {
                p1 == 0x04 && data.contentEquals(ndefAid) -> {
                    refreshNdef(); selected = null; ok
                }
                p1 == 0x00 && data.contentEquals(ccId) -> { selected = ccFile; ok }
                p1 == 0x00 && data.contentEquals(ndefId) -> { refreshNdef(); selected = ndefFile; ok }
                else -> notFound
            }
        }

        // READ BINARY
        if (ins == 0xB0) {
            val file = selected ?: return notFound
            val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
            var le = if (apdu.size >= 5) apdu[4].toInt() and 0xFF else 0
            if (le == 0) le = 256
            if (offset > file.size) return notFound
            val end = minOf(offset + le, file.size)
            return file.copyOfRange(offset, end) + ok
        }

        return notFound
    }

    /** Devuelve el campo de datos de un SELECT (Lc + datos), o null si está mal formado. */
    private fun selectData(apdu: ByteArray): ByteArray? {
        if (apdu.size < 5) return null
        val lc = apdu[4].toInt() and 0xFF
        if (lc == 0 || apdu.size < 5 + lc) return null
        return apdu.copyOfRange(5, 5 + lc)
    }

    private fun refreshNdef() {
        val url = Prefs.url(this)
        if (url != cachedUrl || ndefFile.isEmpty()) {
            ndefFile = buildNdefFile(url)
            cachedUrl = url
        }
    }

    override fun onDeactivated(reason: Int) {
        selected = null
    }

    private fun buildNdefFile(url: String): ByteArray {
        val msg = NdefMessage(NdefRecord.createUri(url)).toByteArray()
        val len = msg.size
        return byteArrayOf((len shr 8).toByte(), (len and 0xFF).toByte()) + msg
    }

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
