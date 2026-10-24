package com.miftah.core.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actions = Actions(
            openAppInfo = { pkg -> safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))) },
            uninstallDialog = { pkg -> safeStart(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null))) },
            share = { text, mime ->
                val send = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_TEXT, text) }
                safeStart(Intent.createChooser(send, null))
            },
            openLocale = {
                if (Build.VERSION.SDK_INT >= 33) {
                    safeStart(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
            },
        )
        setContent { MiftahApp(vm, actions) }
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.action_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
