package com.miftah.core.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.miftah.core.execution.AdbState

@Composable
internal fun AdbScreen(s: UiState, vm: MainViewModel, actions: Actions) {
    var code by rememberSaveable { mutableStateOf("") }
    var showManual by rememberSaveable { mutableStateOf(false) }
    var pairPort by rememberSaveable { mutableStateOf("") }
    var manualCode by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    val busy = s.adbState == AdbState.PAIRING || s.adbState == AdbState.CONNECTING
    val connected = s.adbState == AdbState.CONNECTED
    val canConnect = s.adbState == AdbState.DISCONNECTED || s.adbState == AdbState.FAILED
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Number)

    ScreenColumn {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_key), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(adbStateName(s.adbState)), style = MaterialTheme.typography.titleLarge)
                    s.adbIdentity?.let { Text(stringResource(R.string.adb_shell_identity, it.name, it.uid), style = MaterialTheme.typography.bodySmall) }
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            }
            s.adbError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }

        if (connected) {
            SectionCard {
                Text(stringResource(R.string.adb_connected_note))
                OutlinedButton(onClick = vm::disconnect) { Text(stringResource(R.string.adb_disconnect_btn)) }
            }
        } else {
            SectionCard(title = stringResource(R.string.adb_auto_title)) {
                Text(stringResource(R.string.adb_auto_desc), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.adb_step1), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.adb_step2), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.adb_step3), style = MaterialTheme.typography.bodySmall)
                val pp = s.detectedPairingPort
                if (pp != null) StatusPill(stringResource(R.string.adb_pair_service_found, pp), Tone.OK, R.drawable.ic_check)
                else StatusPill(stringResource(R.string.adb_pair_service_waiting), Tone.NEUTRAL)
                if (!s.pairingActive) {
                    Button(onClick = { actions.requestNotifications(); vm.beginAutoPairing() }) { Text(stringResource(R.string.adb_start_auto)) }
                } else {
                    OutlinedButton(onClick = vm::cancelAutoPairing) { Text(stringResource(R.string.adb_cancel_auto)) }
                }
                OutlinedTextField(
                    code, { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    label = { Text(stringResource(R.string.adb_pair_code)) },
                    keyboardOptions = numeric, modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.pairWithCode(code); code = "" },
                    enabled = !busy && code.length == 6 && pp != null,
                ) { Text(stringResource(R.string.adb_pair_detected_btn)) }
            }
            SectionCard(title = stringResource(R.string.adb_connect_title)) {
                val cp = s.detectedConnectPort
                if (cp != null) {
                    StatusPill(stringResource(R.string.adb_connect_found, cp), Tone.OK, R.drawable.ic_check)
                } else {
                    StatusPill(stringResource(R.string.adb_connect_waiting), Tone.WARN, R.drawable.ic_warning)
                    Text(stringResource(R.string.adb_connect_hint), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = vm::connectDetected, enabled = !busy && cp != null && canConnect) {
                    Text(stringResource(R.string.adb_connect_auto_btn))
                }
            }
        }

        TextButton(onClick = { showManual = !showManual }) { Text(stringResource(R.string.adb_manual_toggle)) }
        if (showManual) {
            SectionCard {
                OutlinedTextField(pairPort, { pairPort = it.filter(Char::isDigit) }, singleLine = true,
                    label = { Text(stringResource(R.string.adb_pair_port)) }, keyboardOptions = numeric, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(manualCode, { manualCode = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    label = { Text(stringResource(R.string.adb_pair_code)) }, keyboardOptions = numeric, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.pair(pairPort, manualCode); manualCode = "" }, enabled = !busy) { Text(stringResource(R.string.adb_pair_btn)) }
                OutlinedTextField(connectPort, { connectPort = it.filter(Char::isDigit) }, singleLine = true,
                    label = { Text(stringResource(R.string.adb_connect_port)) }, keyboardOptions = numeric, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.connect(connectPort) }, enabled = !busy && canConnect) { Text(stringResource(R.string.adb_connect_btn)) }
            }
        }
        Text(stringResource(R.string.adb_risk), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = vm::clearPairing, enabled = !busy && s.adbState != AdbState.NOT_CONFIGURED) {
            Text(stringResource(R.string.adb_clear_btn))
        }
    }
}
