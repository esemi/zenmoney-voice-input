package dev.esemi.zmvoice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.esemi.zmvoice.llm.ParsedTransaction
import dev.esemi.zmvoice.llm.TransactionType
import dev.esemi.zmvoice.speech.SpeechEvent
import dev.esemi.zmvoice.zenmoney.ZenSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val partial: String) : VoiceState
    data class Parsing(val text: String) : VoiceState
    data class Confirm(
        val original: String,
        val parsed: ParsedTransaction,
        val snapshot: ZenSnapshot,
    ) : VoiceState
    data class Sending(val parsed: ParsedTransaction) : VoiceState
    data class Done(val transactionId: String) : VoiceState
    data class Error(val message: String, val previous: VoiceState) : VoiceState
}

class VoiceViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as ZmVoiceApp).container

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var listenJob: Job? = null

    fun startListening() {
        if (_state.value is VoiceState.Listening) return
        _state.value = VoiceState.Listening(partial = "")
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            container.speech.listen().collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> {
                        val cur = _state.value
                        if (cur is VoiceState.Listening) _state.value = cur.copy(partial = event.text)
                    }
                    is SpeechEvent.Final -> handleRecognized(event.text)
                    is SpeechEvent.Error -> _state.value = VoiceState.Error(event.message, VoiceState.Idle)
                    SpeechEvent.Ready, SpeechEvent.Beginning -> Unit
                }
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        val cur = _state.value
        if (cur is VoiceState.Listening) {
            if (cur.partial.isBlank()) {
                _state.value = VoiceState.Idle
            } else {
                handleRecognized(cur.partial)
            }
        }
    }

    private fun handleRecognized(text: String) {
        if (text.isBlank()) {
            _state.value = VoiceState.Idle
            return
        }
        _state.value = VoiceState.Parsing(text)
        viewModelScope.launch {
            runCatching {
                val s = container.settings.settings.first()
                val snapshot = container.zen.cachedSnapshot()
                    ?: withContext(Dispatchers.IO) { container.zen.refreshSnapshot() }
                val parsed = withContext(Dispatchers.IO) {
                    container.claude.parse(
                        apiKey = s.anthropicToken,
                        userUtterance = text,
                        accounts = snapshot.accounts,
                        tags = snapshot.tags,
                        defaultAccountId = s.defaultAccountId.ifBlank { null },
                    )
                }
                snapshot to parsed
            }.onSuccess { (snapshot, parsed) ->
                _state.value = VoiceState.Confirm(text, parsed, snapshot)
            }.onFailure { e ->
                _state.value = VoiceState.Error(e.message ?: "Parse failed", VoiceState.Idle)
            }
        }
    }

    fun updateParsed(transform: (ParsedTransaction) -> ParsedTransaction) {
        val cur = _state.value as? VoiceState.Confirm ?: return
        _state.value = cur.copy(parsed = transform(cur.parsed))
    }

    fun submitConfirmed() {
        val cur = _state.value as? VoiceState.Confirm ?: return
        _state.value = VoiceState.Sending(cur.parsed)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { container.zen.submit(cur.parsed, cur.snapshot) }
            }.onSuccess { id ->
                _state.value = VoiceState.Done(id)
            }.onFailure { e ->
                _state.value = VoiceState.Error(e.message ?: "Send failed", cur)
            }
        }
    }

    fun cancelConfirm() {
        _state.value = VoiceState.Idle
    }

    fun dismissDone() {
        _state.value = VoiceState.Idle
    }

    fun dismissError() {
        val cur = _state.value
        _state.value = if (cur is VoiceState.Error) cur.previous else VoiceState.Idle
    }

    fun refreshZenSnapshot(onResult: (Result<ZenSnapshot>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { container.zen.refreshSnapshot() } }
            onResult(result)
        }
    }

    fun setType(type: TransactionType) {
        updateParsed { it.copy(type = type) }
    }
}
