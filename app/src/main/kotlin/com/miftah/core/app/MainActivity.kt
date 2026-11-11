package com.miftah.core.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the notification simply won't show if denied */ }

    private var pendingVpn: VpnConfig? = null
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cfg = pendingVpn
        pendingVpn = null
        if (result.resultCode == Activity.RESULT_OK && cfg != null) launchVpn(cfg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            startVpn = ::startVpn,
            stopVpn = { stopService(Intent(this, MiftahVpnService::class.java)) },
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

    /** Android shows its own VPN consent dialog the first time; the app never bypasses it. */
    private fun startVpn(cfg: VpnConfig) {
        val prepare = VpnService.prepare(this)
        if (prepare == null) {
            launchVpn(cfg)
        } else {
            pendingVpn = cfg
            vpnConsent.launch(prepare)
        }
    }

    private fun launchVpn(cfg: VpnConfig) {
        startService(
            Intent(this, MiftahVpnService::class.java)
                .setAction(MiftahVpnService.ACTION_START)
                .putExtra(MiftahVpnService.EXTRA_MODE, cfg.mode.name)
                .putExtra(MiftahVpnService.EXTRA_UPSTREAM, cfg.upstream.name)
                .putStringArrayListExtra(MiftahVpnService.EXTRA_EXCLUDED, ArrayList(cfg.excluded))
                .putStringArrayListExtra(MiftahVpnService.EXTRA_BLOCKED, ArrayList(cfg.blocked)),
        )
    }
}
