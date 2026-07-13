package intelligence.cli.portable

internal sealed interface ConsumerState {
    data object Uninitialized : ConsumerState

    data class Resolved(
        val intent: MarketplaceIntent,
        val lock: MarketplaceLock,
    ) : ConsumerState

    data class Unresolved(val intent: MarketplaceIntent) : ConsumerState

    data class Stale(
        val intent: MarketplaceIntent,
        val lock: MarketplaceLock,
        val reason: MarketplaceLockStaleness,
    ) : ConsumerState

    data class Orphaned(val lock: MarketplaceLock) : ConsumerState

    data class Recovering(val journal: MarketplaceTransactionJournal) : ConsumerState

    data class Invalid(val reason: ConsumerStateInvalidity) : ConsumerState

    companion object {
        fun classify(
            intentBytes: ByteArray?,
            lockBytes: ByteArray?,
            journalBytes: ByteArray?,
        ): ConsumerState {
            if (journalBytes != null) {
                return when (val parsed = MarketplaceTransactionJournal.parse(journalBytes)) {
                    is MarketplaceTransactionJournalParsing.Parsed -> Recovering(parsed.journal)
                    is MarketplaceTransactionJournalParsing.Rejected ->
                        Invalid(ConsumerStateInvalidity.Journal(parsed.reason))
                }
            }
            if (intentBytes == null && lockBytes == null) return Uninitialized

            val intent =
                if (intentBytes == null) {
                    null
                } else {
                    when (val parsed = MarketplaceIntent.parse(intentBytes)) {
                        is MarketplaceIntentParsing.Parsed -> parsed.intent
                        is MarketplaceIntentParsing.Rejected ->
                            return Invalid(ConsumerStateInvalidity.Intent(parsed.reason))
                    }
                }
            val lock =
                if (lockBytes == null) {
                    null
                } else {
                    when (val parsed = MarketplaceLock.parse(lockBytes)) {
                        is MarketplaceLockParsing.Parsed -> parsed.lock
                        is MarketplaceLockParsing.Rejected ->
                            return Invalid(ConsumerStateInvalidity.Lock(parsed.reason))
                    }
                }
            if (intent == null) return Orphaned(checkNotNull(lock))
            if (lock == null) return Unresolved(intent)
            return when (val agreement = lock.agreement(intent)) {
                MarketplaceLockAgreement.Matched -> Resolved(intent, lock)
                is MarketplaceLockAgreement.Stale -> Stale(intent, lock, agreement.reason)
            }
        }
    }
}

internal sealed interface ConsumerStateInvalidity {
    data class Intent(val reason: MarketplaceIntentRejection) : ConsumerStateInvalidity

    data class Lock(val reason: MarketplaceLockRejection) : ConsumerStateInvalidity

    data class Journal(val reason: MarketplaceTransactionJournalRejection) : ConsumerStateInvalidity
}
