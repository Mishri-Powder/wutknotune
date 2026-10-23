package link.roadio.riff.indexer.core.ipc.reliable

enum class ScanAttemptMode { NORMAL, ISOLATED }

enum class SourceRecovery { NORMAL, ISOLATE, QUARANTINE }

data class ScanAttempt(
    val owner: String,
    val reference: String,
    val source: List<String>,
    val mode: ScanAttemptMode,
)

/** Opaque physical-source attempts; Player never interprets media or persists this RAM journal. */
class ScanAttemptJournal {
    private val attempts = linkedMapOf<List<String>, ScanAttempt>()
    private val deadOwners = mutableSetOf<String>()

    @Synchronized
    fun ownerDied(owner: String) {
        deadOwners += owner
    }

    @Synchronized
    fun recover(): List<ScanAttempt> = attempts.values.filter { it.owner in deadOwners }

    @Synchronized
    fun begin(attempt: ScanAttempt): SourceRecovery {
        require(attempt.source.size == 6 && attempt.source.all { it.length <= 256 })
        val prior = attempts[attempt.source]
        if (prior?.owner == attempt.owner && prior.reference == attempt.reference) return SourceRecovery.NORMAL
        val recovery =
            if (prior?.owner in deadOwners) {
                if (prior?.mode == ScanAttemptMode.ISOLATED) SourceRecovery.QUARANTINE else SourceRecovery.ISOLATE
            } else {
                SourceRecovery.NORMAL
            }
        if (recovery == SourceRecovery.QUARANTINE || (recovery == SourceRecovery.ISOLATE && attempt.mode != ScanAttemptMode.ISOLATED)) {
            return recovery
        }
        // A new source observation cannot inherit an obsolete stamp's suspicion.
        attempts.keys
            .filter {
                it[0] == attempt.source[0] &&
                    it[3] == attempt.source[3] &&
                    (it[1] != attempt.source[1] || it[2] != attempt.source[2])
            }.forEach(attempts::remove)
        attempts[attempt.source.toList()] = attempt
        return recovery
    }

    @Synchronized
    fun finish(
        owner: String,
        reference: String,
    ) {
        val key = attempts.entries.firstOrNull { it.value.owner == owner && it.value.reference == reference }?.key
        if (key != null) attempts.remove(key)
        deadOwners.retainAll(attempts.values.map { it.owner }.toSet())
    }

    @Synchronized
    fun clear(stage: String? = null) {
        if (stage == null) attempts.clear() else attempts.keys.filter { it[3] == stage }.forEach(attempts::remove)
        deadOwners.retainAll(attempts.values.map { it.owner }.toSet())
    }
}
