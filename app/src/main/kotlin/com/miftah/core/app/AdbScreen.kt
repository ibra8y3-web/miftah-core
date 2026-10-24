package com.miftah.core.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.miftah.core.execution.AdbState

@Composable
internal fun AdbScreen(s: UiState, vm: MainViewModel) {
    var pairPort by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    val inProgress = s.adbState == AdbState.PAIRING || s.adbState == AdbState.CONNECTING
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.adb_state_label, stringResource(adbStateName(s.adbState))), style = MaterialTheme.typography.titleMedium)
        s.adbIdentity?.let { Text(stringResource(R.string.adb_shell_identity, it.name, it.uid)) }
        s.adbError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(stringResource(R.string.adb_steps), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.adb_risk), style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(pairPort, { pairPort = it.filter(Char::isDigit) }, singleLine = true,
            label = { Text(stringResource(R.string.adb_pair_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, singleLine = true,
            label = { Text(stringResource(R.string.adb_pair_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.pair(pairPort, code); code = "" }, enabled = !inProgress) {
            Text(stringResource(R.string.adb_pair_btn))
        }

        OutlinedTextField(connectPort, { connectPort = it.filter(Char::isDigit) }, singleLine = true,
            label = { Text(stringResource(R.string.adb_connect_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { vm.connect(connectPort) },
            enabled = !inProgress && (s.adbState == AdbState.DISCONNECTED || s.adbState == AdbState.FAILED),
        ) { Text(stringResource(R.string.adb_connect_btn)) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::disconnect, enabled = s.adbState == AdbState.CONNECTED) {
                Text(stringResource(R.string.adb_disconnect_btn))
            }
            OutlinedButton(onClick = vm::clearPairing, enabled = !inProgress && s.adbState != AdbState.NOT_CONFIGURED) {
                Text(stringResource(R.string.adb_clear_btn))
            }
        }
        if (inProgress) CircularProgressIndicator()
    }
}
