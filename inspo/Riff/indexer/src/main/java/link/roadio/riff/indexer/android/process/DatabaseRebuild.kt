package link.roadio.riff.indexer.android.process

/** Independent analysis and Cuetree index rebuild scopes. */
internal enum class DatabaseRebuild(
    val mask: Int,
) {
    AU(1),
    IA(2),
    AU_IA(3),
    ;

    companion object {
        fun fromMask(mask: Int): DatabaseRebuild = entries.single { it.mask == mask }
    }
}
