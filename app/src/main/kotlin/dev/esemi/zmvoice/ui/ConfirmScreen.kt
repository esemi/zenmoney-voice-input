package dev.esemi.zmvoice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.esemi.zmvoice.VoiceState
import dev.esemi.zmvoice.VoiceViewModel
import dev.esemi.zmvoice.llm.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmScreen(
    confirm: VoiceState.Confirm,
    viewModel: VoiceViewModel,
) {
    val parsed = confirm.parsed
    val accounts = remember(confirm.snapshot) {
        confirm.snapshot.accounts.filter { !it.archive }
    }
    val tags = remember(confirm.snapshot) {
        confirm.snapshot.tags.filter { !it.archive }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Проверь и нажми Записать", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("«${confirm.original}»", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = parsed.type == TransactionType.OUTCOME,
                onClick = { viewModel.setType(TransactionType.OUTCOME) },
                label = { Text("Расход") },
            )
            FilterChip(
                selected = parsed.type == TransactionType.INCOME,
                onClick = { viewModel.setType(TransactionType.INCOME) },
                label = { Text("Доход") },
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = parsed.amount?.let { formatAmount(it) } ?: "",
            onValueChange = { input ->
                val parsedAmount = input.replace(',', '.').toDoubleOrNull()
                viewModel.updateParsed { it.copy(amount = parsedAmount) }
            },
            label = { Text("Сумма") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = parsed.currency.orEmpty(),
            onValueChange = { v -> viewModel.updateParsed { it.copy(currency = v.uppercase()) } },
            label = { Text("Валюта") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        DropdownField(
            label = "Счёт",
            current = accounts.firstOrNull { it.id == parsed.accountId }?.title ?: "—",
            options = accounts.map { it.id to it.title },
            onSelect = { id -> viewModel.updateParsed { it.copy(accountId = id) } },
        )

        Spacer(Modifier.height(12.dp))
        DropdownField(
            label = "Категория",
            current = tags.firstOrNull { it.id == parsed.tagId }?.let { tag ->
                tag.parentTitle?.let { "$it / ${tag.title}" } ?: tag.title
            } ?: "—",
            options = tags.map { tag ->
                tag.id to (tag.parentTitle?.let { "$it / ${tag.title}" } ?: tag.title)
            },
            onSelect = { id -> viewModel.updateParsed { it.copy(tagId = id) } },
            allowClear = true,
            onClear = { viewModel.updateParsed { it.copy(tagId = null) } },
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = parsed.comment.orEmpty(),
            onValueChange = { v -> viewModel.updateParsed { it.copy(comment = v.ifBlank { null }) } },
            label = { Text("Комментарий") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { viewModel.cancelConfirm() },
                modifier = Modifier.weight(1f),
            ) { Text("Отмена") }
            Button(
                onClick = { viewModel.submitConfirmed() },
                modifier = Modifier.weight(1f),
                enabled = (parsed.amount ?: 0.0) > 0.0,
            ) { Text("Записать") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DropdownField(
    label: String,
    current: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    allowClear: Boolean = false,
    onClear: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(current, modifier = Modifier.fillMaxWidth())
            }
            if (allowClear) {
                TextButton(onClick = onClear) { Text("×") }
            }
        }
        Box {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun formatAmount(value: Double): String {
    val rounded = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    return rounded
}
