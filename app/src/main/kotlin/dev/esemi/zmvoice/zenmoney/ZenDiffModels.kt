package dev.esemi.zmvoice.zenmoney

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ZenDiffRequest(
    val currentClientTimestamp: Long,
    val serverTimestamp: Long,
    val transaction: List<ZenTransactionDto>? = null,
)

@Serializable
data class ZenDiffResponse(
    val serverTimestamp: Long = 0,
    val account: List<ZenAccountDto> = emptyList(),
    val tag: List<ZenTagDto> = emptyList(),
    val instrument: List<ZenInstrumentDto> = emptyList(),
    val user: List<ZenUserDto> = emptyList(),
    val transaction: List<JsonElement> = emptyList(),
)

@Serializable
data class ZenAccountDto(
    val id: String,
    val user: Long,
    val title: String,
    val instrument: Long,
    val archive: Boolean = false,
    val type: String? = null,
    val balance: Double? = null,
)

@Serializable
data class ZenTagDto(
    val id: String,
    val user: Long,
    val title: String,
    val parent: String? = null,
    val archive: Boolean = false,
)

@Serializable
data class ZenInstrumentDto(
    val id: Long,
    val title: String,
    val shortTitle: String,
    val symbol: String,
)

@Serializable
data class ZenUserDto(
    val id: Long,
    val login: String? = null,
)

@Serializable
data class ZenTransactionDto(
    val id: String,
    val user: Long,
    val date: String,
    val income: Double,
    val outcome: Double,
    val incomeAccount: String,
    val outcomeAccount: String,
    val incomeInstrument: Long,
    val outcomeInstrument: Long,
    val created: Long,
    val changed: Long,
    val deleted: Boolean = false,
    val hold: Boolean? = null,
    val tag: List<String>? = null,
    val comment: String? = null,
    val payee: String? = null,
    val originalPayee: String? = null,
    val mcc: Int? = null,
)

data class ZenAccount(
    val id: String,
    val title: String,
    val currency: String?,
    val archive: Boolean,
)

data class ZenTag(
    val id: String,
    val title: String,
    val parentTitle: String?,
    val archive: Boolean,
)

data class ZenSnapshot(
    val userId: Long,
    val accounts: List<ZenAccount>,
    val tags: List<ZenTag>,
    val accountInstrument: Map<String, Long>,
    val instrumentBySymbol: Map<String, Long>,
)
