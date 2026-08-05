package com.nexusautomation.nfcheck

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.util.Log

class NFCManager(private val activity: Activity) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun leerTag(tag: Tag?): String {
        val id = tag?.id ?: return ""
        return id.joinToString("") { String.format("%02X", it) }
    }

    fun iniciar() {
        if (nfcAdapter == null) {
            Log.d("NFC", "No soportado en este dispositivo")
        } else {
            Log.d("NFC", "NFC listo")
        }
    }

    fun esSoportado(): Boolean = nfcAdapter != null
}