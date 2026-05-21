package dev.esemi.zmvoice.zenmoney

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class ZenMoneyException(message: String) : Exception(message)

class ZenMoneyClient(
    private val http: OkHttpClient,
    private val json: Json,
) {

    suspend fun diff(token: String, payload: ZenDiffRequest): ZenDiffResponse {
        require(token.isNotBlank()) { "ZenMoney token is empty" }
        val body = json.encodeToString(ZenDiffRequest.serializer(), payload)
        val request = Request.Builder()
            .url("https://api.zenmoney.ru/v8/diff/")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val responseText = http.await(request)
        return json.decodeFromString(ZenDiffResponse.serializer(), responseText)
    }
}

private suspend fun OkHttpClient.await(request: Request): String = suspendCancellableCoroutine { cont ->
    val call = newCall(request)
    cont.invokeOnCancellation { runCatching { call.cancel() } }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    cont.resumeWith(Result.failure(ZenMoneyException("HTTP ${r.code}: $text")))
                } else {
                    cont.resumeWith(Result.success(text))
                }
            }
        }
    })
}
