package dev.esemi.zmvoice.llm

import dev.esemi.zmvoice.zenmoney.ZenAccount
import dev.esemi.zmvoice.zenmoney.ZenTag
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.LocalDate

class ClaudeParseException(message: String) : Exception(message)

@Serializable
private data class MessagesResponse(
    val content: List<ContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val error: ErrorPayload? = null,
)

@Serializable
private data class ContentBlock(
    val type: String,
    val text: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
)

@Serializable
private data class ErrorPayload(val type: String? = null, val message: String? = null)

class ClaudeClient(
    private val http: OkHttpClient,
    private val json: Json,
) {

    suspend fun parse(
        apiKey: String,
        userUtterance: String,
        accounts: List<ZenAccount>,
        tags: List<ZenTag>,
        defaultAccountId: String?,
    ): ParsedTransaction {
        require(apiKey.isNotBlank()) { "Anthropic API key is empty" }

        val today = LocalDate.now().toString()
        val systemPrompt = buildSystemPrompt(today, accounts, tags, defaultAccountId)

        val toolSchema = buildJsonObject {
            put("name", "record_transaction")
            put("description", "Записать денежную транзакцию в ZenMoney")
            putJsonObject("input_schema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("type") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add(JsonPrimitive("outcome"))
                            add(JsonPrimitive("income"))
                        }
                        put("description", "Тип: outcome (расход) или income (доход)")
                    }
                    putJsonObject("amount") {
                        put("type", "number")
                        put("description", "Сумма транзакции, положительное число")
                    }
                    putJsonObject("currency") {
                        put("type", "string")
                        put("description", "ISO-код валюты (RUB, USD, EUR). По умолчанию RUB")
                    }
                    putJsonObject("account_id") {
                        put("type", "string")
                        put("description", "ID счёта из списка accounts. По умолчанию default_account_id")
                    }
                    putJsonObject("tag_id") {
                        put("type", "string")
                        put("description", "ID наиболее подходящего тега. При сомнении пропускай")
                    }
                    putJsonObject("comment") {
                        put("type", "string")
                        put("description", "Короткий комментарий, до 60 символов")
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("type")) }
            }
        }

        val body = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 512)
            put("system", systemPrompt)
            putJsonArray("tools") { add(toolSchema) }
            putJsonObject("tool_choice") {
                put("type", "tool")
                put("name", "record_transaction")
            }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", userUtterance)
                }
            }
        }.toString()

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val responseText = http.await(request)
        val parsed: MessagesResponse = json.decodeFromString(MessagesResponse.serializer(), responseText)

        parsed.error?.let { throw ClaudeParseException("Claude error: ${it.type}: ${it.message}") }

        val toolUse = parsed.content.firstOrNull { it.type == "tool_use" && it.name == "record_transaction" }
            ?: throw ClaudeParseException("Claude did not return a tool_use block")
        val input = toolUse.input?.jsonObject
            ?: throw ClaudeParseException("Empty tool_use input")

        return ParsedTransaction(
            type = when (input["type"]?.jsonPrimitive?.contentOrNull) {
                "income" -> TransactionType.INCOME
                else -> TransactionType.OUTCOME
            },
            amount = input["amount"]?.jsonPrimitive?.doubleOrNull,
            currency = input["currency"]?.jsonPrimitive?.contentOrNull?.uppercase(),
            accountId = input["account_id"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
            tagId = input["tag_id"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
            comment = input["comment"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
        )
    }

    private fun buildSystemPrompt(
        today: String,
        accounts: List<ZenAccount>,
        tags: List<ZenTag>,
        defaultAccountId: String?,
    ): String {
        val accountLines = accounts.joinToString("\n") { a ->
            "- id=${a.id} title=\"${a.title}\" currency=${a.currency ?: "?"} archive=${a.archive}"
        }
        val tagsForPrompt = tags.filter { !it.archive }.take(150)
        val tagLines = tagsForPrompt.joinToString("\n") { t ->
            val parent = t.parentTitle?.let { " parent=\"$it\"" }.orEmpty()
            "- id=${t.id} title=\"${t.title}\"$parent"
        }
        return """
            Ты парсишь короткие голосовые команды о денежных тратах и доходах в структурированную транзакцию ZenMoney.
            Сегодня: $today.

            ОБЯЗАТЕЛЬНО вызови инструмент record_transaction. Не пиши текстовый ответ.

            Правила:
            - Если не сказано иное — type=outcome (расход).
            - amount — число в основной валюте, без копеек если копейки не названы.
            - Если валюта не названа — currency=RUB.
            - account_id выбирай из списка accounts. Если непонятно — используй default_account_id=${defaultAccountId ?: "<не задан>"}.
            - tag_id выбирай из списка tags только если ясно. При сомнениях оставляй пустым.
            - comment — компактная версия исходной фразы, до 60 символов.

            accounts:
            ${accountLines.ifBlank { "<нет>" }}

            tags:
            ${tagLines.ifBlank { "<нет>" }}
        """.trimIndent()
    }

    companion object {
        const val MODEL = "claude-haiku-4-5-20251001"
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
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    cont.resumeWith(Result.failure(ClaudeParseException("HTTP ${r.code}: $body")))
                } else {
                    cont.resumeWith(Result.success(body))
                }
            }
        }
    })
}
