package dev.esemi.zmvoice.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.esemi.zmvoice.VoiceState
import dev.esemi.zmvoice.VoiceViewModel

@Composable
fun RootScreen(
    state: VoiceState,
    viewModel: VoiceViewModel,
    hasMicPermission: () -> Boolean,
) {
    var settingsOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (settingsOpen) {
            SettingsScreen(
                viewModel = viewModel,
                onClose = { settingsOpen = false },
            )
        } else {
            when (state) {
                is VoiceState.Confirm -> ConfirmScreen(
                    confirm = state,
                    viewModel = viewModel,
                )
                else -> RecordScreen(
                    state = state,
                    viewModel = viewModel,
                    hasMicPermission = hasMicPermission,
                    onOpenSettings = { settingsOpen = true },
                )
            }
        }

        if (state is VoiceState.Error) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissError() },
                title = { Text("Ошибка") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissError() }) { Text("Ок") }
                },
            )
        }

        if (state is VoiceState.Done) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDone() },
                title = { Text("Записано") },
                text = { Text("Транзакция сохранена в ZenMoney") },
                confirmButton = {
                    Button(onClick = { viewModel.dismissDone() }) { Text("Ок") }
                },
            )
        }
    }
}
