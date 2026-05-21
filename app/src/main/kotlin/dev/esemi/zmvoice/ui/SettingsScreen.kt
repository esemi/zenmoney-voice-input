package dev.esemi.zmvoice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.esemi.zmvoice.VoiceViewModel
import dev.esemi.zmvoice.ZmVoiceApp
import dev.esemi.zmvoice.zenmoney.ZenAccount
import dev.esemi.zmvoice.zenmoney.ZenSnapshot
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: VoiceViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as ZmVoiceApp
    val store = app.container.settings

    val settings by store.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var zen by remember { mutableStateOf("") }
    var anth by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf<ZenSnapshot?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        settings?.let {
            if (zen.isEmpty()) zen = it.zenmoneyToken
            if (anth.isEmpty()) anth = it.anthropicToken
            if (account.isEmpty()) account = it.defaultAccountId
        }
        if (snapshot == null) snapshot = app.container.zen.cachedSnapshot()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Настройки", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = zen,
            onValueChange = { zen = it },
            label = { Text("ZenMoney token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = anth,
            onValueChange = { anth = it },
            label = { Text("Anthropic API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        snapshot?.let { snap ->
            val accounts: List<ZenAccount> = snap.accounts.filter { !it.archive }
            DropdownField(
                label = "Счёт по умолчанию",
                current = accounts.firstOrNull { it.id == account }?.title ?: "—",
                options = accounts.map { it.id to "${it.title} (${it.currency ?: "?"})" },
                onSelect = { account = it },
            )
        } ?: OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text("ID счёта по умолчанию") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        store.setZenmoneyToken(zen.trim())
                        store.setAnthropicToken(anth.trim())
                        store.setDefaultAccount(account.trim())
                        status = "Сохранено"
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Сохранить") }

            OutlinedButton(
                onClick = {
                    loading = true
                    status = "Загружаю…"
                    scope.launch {
                        store.setZenmoneyToken(zen.trim())
                        store.setAnthropicToken(anth.trim())
                        store.setDefaultAccount(account.trim())
                    }
                    viewModel.refreshZenSnapshot { result ->
                        loading = false
                        result.onSuccess { snap ->
                            snapshot = snap
                            status = "Подтянул: ${snap.accounts.size} счетов, ${snap.tags.size} категорий"
                        }.onFailure { e ->
                            status = "Ошибка: ${e.message}"
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.weight(1f),
            ) { Text("Подтянуть из ZenMoney") }
        }

        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
        Spacer(Modifier.height(24.dp))
    }
}
