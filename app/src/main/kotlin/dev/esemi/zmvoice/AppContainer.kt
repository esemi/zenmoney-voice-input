package dev.esemi.zmvoice

import android.content.Context
import dev.esemi.zmvoice.data.SettingsStore
import dev.esemi.zmvoice.llm.ClaudeClient
import dev.esemi.zmvoice.speech.SpeechRecognizerClient
import dev.esemi.zmvoice.zenmoney.ZenMoneyClient
import dev.esemi.zmvoice.zenmoney.ZenRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
        }
        .build()

    val settings = SettingsStore(context.applicationContext)
    val speech = SpeechRecognizerClient(context.applicationContext)
    val claude = ClaudeClient(http, json)
    private val zenClient = ZenMoneyClient(http, json)
    val zen = ZenRepository(zenClient, settings, json)
}
