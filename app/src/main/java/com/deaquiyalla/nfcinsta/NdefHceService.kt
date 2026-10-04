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

    private val selectAid = hex("00A4040007D276000085010100")
    private val selectAidNoLe = hex("00A4040007D2760000850101")
    private val selectCc = hex("00A4000C02E103")
    private val selectNdef = hex("00A4000C02E104")

    // Capability Container: versión 2.0, NDEF file E104, lectura libre, sin escritura
    private val ccFile = hex("000F20003B00340406E1047FFF00FF")

    private var ndefFile = ByteArray(0)
    private var selected: ByteArray? = null

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        when {
            apdu.contentEquals(selectAid) || apdu.contentEquals(selectAidNoLe) -> {
                ndefFile = buildNdefFile(Prefs.url(this))
                selected = null
                return ok
            }
            apdu.contentEquals(selectCc) -> { selected = ccFile; return ok }
            apdu.contentEquals(selectNdef) -> { selected = ndefFile; return ok }
            apdu.size >= 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB0.toByte() -> {
                val file = selected ?: return notFound
                val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
                var le = apdu[4].toInt() and 0xFF
                if (le == 0) le = 256
                if (offset > file.size) return notFound
                val end = minOf(offset + le, file.size)
                return file.copyOfRange(offset, end) + ok
            }
        }
        return notFound
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
