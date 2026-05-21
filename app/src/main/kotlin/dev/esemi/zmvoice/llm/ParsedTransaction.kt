package dev.esemi.zmvoice.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    @SerialName("outcome") OUTCOME,
    @SerialName("income") INCOME,
}

@Serializable
data class ParsedTransaction(
    val type: TransactionType,
    val amount: Double? = null,
    val currency: String? = null,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("tag_id") val tagId: String? = null,
    val comment: String? = null,
)
