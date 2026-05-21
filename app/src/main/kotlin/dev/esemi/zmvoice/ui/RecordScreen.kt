package dev.esemi.zmvoice.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.esemi.zmvoice.VoiceState
import dev.esemi.zmvoice.VoiceViewModel

@Composable
fun RecordScreen(
    state: VoiceState,
    viewModel: VoiceViewModel,
    hasMicPermission: () -> Boolean,
    onOpenSettings: () -> Unit,
) {
    var permissionGranted by remember { mutableStateOf(hasMicPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> permissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            TextButton(onClick = onOpenSettings) { Text("Настройки") }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = statusFor(state),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = utteranceFor(state),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        val isListening = state is VoiceState.Listening
        val isBusy = state is VoiceState.Parsing || state is VoiceState.Sending
        val color = when {
            isListening -> MaterialTheme.colorScheme.error
            isBusy -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.primary
        }

        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(color)
                .pointerInput(permissionGranted) {
                    if (!permissionGranted) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            viewModel.startListening()
                            tryAwaitRelease()
                            viewModel.stopListening()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isBusy) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(
                    text = if (isListening) "Говори…" else "Зажми\nи говори",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

private fun statusFor(state: VoiceState): String = when (state) {
    is VoiceState.Idle -> "Готов"
    is VoiceState.Listening -> "Слушаю…"
    is VoiceState.Parsing -> "Разбираю…"
    is VoiceState.Sending -> "Отправляю…"
    is VoiceState.Done -> "Готово"
    is VoiceState.Error -> "Ошибка"
    is VoiceState.Confirm -> ""
}

private fun utteranceFor(state: VoiceState): String = when (state) {
    is VoiceState.Listening -> state.partial
    is VoiceState.Parsing -> state.text
    else -> ""
}
