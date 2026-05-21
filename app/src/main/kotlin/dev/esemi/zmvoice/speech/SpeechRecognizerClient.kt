package dev.esemi.zmvoice.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data object Beginning : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Error(val code: Int, val message: String) : SpeechEvent
}

class SpeechRecognizerClient(private val context: Context) {

    fun listen(languageTag: String = "ru-RU"): Flow<SpeechEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechEvent.Error(-1, "Speech recognition unavailable on this device"))
            close()
            return@callbackFlow
        }

        val main = Handler(Looper.getMainLooper())
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        val recognizerRef = arrayOfNulls<SpeechRecognizer>(1)

        main.post {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizerRef[0] = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(SpeechEvent.Ready)
                }

                override fun onBeginningOfSpeech() {
                    trySend(SpeechEvent.Beginning)
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    trySend(SpeechEvent.Error(error, errorText(error)))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    trySend(SpeechEvent.Final(texts?.firstOrNull().orEmpty()))
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    texts?.firstOrNull()?.let { trySend(SpeechEvent.Partial(it)) }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer.startListening(intent)
        }

        awaitClose {
            main.post {
                recognizerRef[0]?.run {
                    stopListening()
                    destroy()
                }
            }
        }
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "No microphone permission"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
        else -> "Speech error $code"
    }
}
