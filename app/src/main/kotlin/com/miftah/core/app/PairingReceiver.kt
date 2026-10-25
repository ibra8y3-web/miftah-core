package com.miftah.core.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.launch

/** Receives the pairing code typed into the notification reply field. */
class PairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PairingCoordinator.ACTION_CODE) return
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(PairingCoordinator.KEY_CODE)?.toString()?.filter(Char::isDigit) ?: return
        val app = context.applicationContext as MiftahApplication
        val pending = goAsync()
        app.scope.launch {
            try {
                if (code.length == 6) app.pairing.submitCode(code)
                else app.pairing.submitCode("invalid")
            } finally {
                pending.finish()
            }
        }
    }
}
