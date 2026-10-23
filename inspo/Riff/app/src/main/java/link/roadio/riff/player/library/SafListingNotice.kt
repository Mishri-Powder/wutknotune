package link.roadio.riff.player.library

/** Display-only facts from Indexer admission; labels never identify grants or catalog sources. */
data class SafListingNotice(
    val activeRootCount: Int,
    val activeFolderLabels: List<String>,
    val unverifiedRootCount: Int,
    val unverifiedFolderLabels: List<String>,
) {
    val title: String
        get() = if (activeRootCount > 0) "Android storage listing error" else "Media folders unavailable"

    val message: String
        get() =
            buildList {
                if (activeRootCount > 0) {
                    add("Android cannot list " + activeRootCount + " media folder(s): " + activeFolderLabels.joinToString(", ") + ".")
                    add("Restarting the device may restore access.")
                }
                if (unverifiedRootCount > 0) {
                    add(
                        "The contents of " + unverifiedRootCount + " folder(s) could not be verified: " +
                            unverifiedFolderLabels.joinToString(", ") +
                            ".",
                    )
                }
                add("Riff will skip these folders and continue with the other folders.")
            }.joinToString(" ")
}

/**
 * Process-memory acknowledgement, independent of transient status and Activity subscriptions.
 * A healthy pass resets episode deduplication but cannot dismiss an already pending OK-only notice.
 * No state is written to grants or application data, and reporting never changes navigation.
 */
internal class SafListingNoticeSession {
    var pending: SafListingNotice? = null
        private set
    private var acknowledged: SafListingNotice? = null
    private var currentReport: SafListingNotice? = null

    fun report(notice: SafListingNotice?) {
        currentReport = notice
        if (notice == null) {
            acknowledged = null
        } else if (notice != acknowledged) {
            pending = notice
        }
    }

    fun acknowledge() {
        // Recovery can precede OK: acknowledging the old notice must not suppress a new episode.
        acknowledged = pending.takeIf { it == currentReport }
        pending = null
    }
}
