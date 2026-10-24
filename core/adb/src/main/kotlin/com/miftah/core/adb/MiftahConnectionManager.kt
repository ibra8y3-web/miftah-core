package com.miftah.core.adb

import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate

/** Thin adapter supplying our persisted identity to libadb-android. */
class MiftahConnectionManager(vault: KeyVault) : AbsAdbConnectionManager() {
    private val identity: Pair<PrivateKey, Certificate> = vault.loadOrCreate()

    init { setApi(Build.VERSION.SDK_INT) }

    override fun getPrivateKey(): PrivateKey = identity.first
    override fun getCertificate(): Certificate = identity.second
    override fun getDeviceName(): String = "MIFTAH-Core"
}
