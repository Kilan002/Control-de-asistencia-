package com.nexusautomation.nfcheck

import android.app.Application

/**
 * NFCheckApp — sin App Check.
 * Las Functions tienen enforceAppCheck: false en index.js,
 * por lo que no se requiere ningún proveedor instalado aquí.
 */
class NFCheckApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}