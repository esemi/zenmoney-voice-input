package dev.esemi.zmvoice.zenmoney

import dev.esemi.zmvoice.data.SettingsStore
import dev.esemi.zmvoice.llm.ParsedTransaction
import dev.esemi.zmvoice.llm.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ZenRepository(
    private val client: ZenMoneyClient,
    private val settings: SettingsStore,
    private val json: Json,
) {

    suspend fun refreshSnapshot(): ZenSnapshot {
        val s = settings.settings.first()
        val now = Instant.now().epochSecond
        val response = client.diff(
            token = s.zenmoneyToken,
            payload = ZenDiffRequest(currentClientTimestamp = now, serverTimestamp = 0),
        )
        settings.setZenDiff(json.encodeToString(ZenDiffResponse.serializer(), response), now)
        return response.toSnapshot()
    }

    suspend fun cachedSnapshot(): ZenSnapshot? {
        val s = settings.settings.first()
        if (s.zenDiffJson.isBlank()) return null
        return runCatching {
            json.decodeFromString(ZenDiffResponse.serializer(), s.zenDiffJson).toSnapshot()
        }.getOrNull()
    }

    suspend fun submit(parsed: ParsedTransaction, snapshot: ZenSnapshot): String {
        val s = settings.settings.first()

        val accountId = parsed.accountId
            ?: s.defaultAccountId.ifBlank { null }
            ?: snapshot.accounts.firstOrNull { !it.archive }?.id
            ?: throw ZenMoneyException("No account available; set default in settings")
        val instrument = snapshot.accountInstrument[accountId]
            ?: throw ZenMoneyException("Account $accountId has no instrument")

        val amount = parsed.amount
            ?: throw ZenMoneyException("Amount is missing — fill it in confirmation screen")
        if (amount <= 0) throw ZenMoneyException("Amount must be positive")

        val now = Instant.now().toEpochMilli() / 1000
        val userComment = parsed.comment?.trim()?.takeIf { it.isNotEmpty() }
        val comment = if (userComment != null) "$userComment ($COMMENT_TAG)" else COMMENT_TAG
        val tx = ZenTransactionDto(
            id = UUID.randomUUID().toString(),
            user = snapshot.userId,
            date = LocalDate.now().toString(),
            income = if (parsed.type == TransactionType.INCOME) amount else 0.0,
            outcome = if (parsed.type == TransactionType.OUTCOME) amount else 0.0,
            incomeAccount = accountId,
            outcomeAccount = accountId,
            incomeInstrument = instrument,
            outcomeInstrument = instrument,
            created = now,
            changed = now,
            tag = parsed.tagId?.let { listOf(it) },
            comment = comment,
        )
        client.diff(
            token = s.zenmoneyToken,
            payload = ZenDiffRequest(
                currentClientTimestamp = now,
                serverTimestamp = 0,
                transaction = listOf(tx),
            ),
        )
        return tx.id
    }

    private companion object {
        const val COMMENT_TAG = "from zmvoice"
    }
}

private fun ZenDiffResponse.toSnapshot(): ZenSnapshot {
    val instrumentTitle = instrument.associateBy({ it.id }, { it.shortTitle })
    val instrumentBySymbol = instrument.associate { it.shortTitle.uppercase() to it.id }
    val accountInstrument = account.associate { it.id to it.instrument }
    val accounts = account.map {
        ZenAccount(
            id = it.id,
            title = it.title,
            currency = instrumentTitle[it.instrument],
            archive = it.archive,
        )
    }
    val tagTitleById = tag.associate { it.id to it.title }
    val tags = tag.map { t ->
        ZenTag(
            id = t.id,
            title = t.title,
            parentTitle = t.parent?.let { tagTitleById[it] },
            archive = t.archive,
        )
    }
    val userId = user.firstOrNull()?.id ?: 0L
    return ZenSnapshot(
        userId = userId,
        accounts = accounts,
        tags = tags,
        accountInstrument = accountInstrument,
        instrumentBySymbol = instrumentBySymbol,
    )
}
