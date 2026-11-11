package com.miftah.core.capability

import android.os.Build

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val release: String,
    val sdkInt: Int,
    val abis: List<String>,
    val readAtMillis: Long,
) {
    companion object {
        fun read(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            release = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
            readAtMillis = System.currentTimeMillis(),
        )
    }
}
