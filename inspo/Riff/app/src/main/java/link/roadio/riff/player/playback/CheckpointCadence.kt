package link.roadio.riff.player.playback

/** Deadlines are relative to confirmed persistence, never to attempted writes. */
internal class CheckpointCadence(
    initialElapsedMs: Long,
) {
    @Volatile
    private var lastSuccessMs = initialElapsedMs
    private var manipulationPending = false

    fun manipulated() {
        manipulationPending = true
    }

    fun due(
        nowMs: Long,
        playing: Boolean,
    ): Boolean =
        (manipulationPending && nowMs - lastSuccessMs >= MANIPULATION_INTERVAL_MS) ||
            (playing && nowMs - lastSuccessMs >= PERIODIC_INTERVAL_MS)

    fun submitted() {
        manipulationPending = false
    }

    fun saved(nowMs: Long) {
        lastSuccessMs = nowMs
    }

    fun cancel() {
        manipulationPending = false
    }

    companion object {
        const val MANIPULATION_INTERVAL_MS = 30_000L
        const val PERIODIC_INTERVAL_MS = 60_000L
    }
}
