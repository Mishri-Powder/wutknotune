package link.roadio.riff.indexer.android.ipc

/** All Player/Indexer control, status, and artifact messages share one ordered reliable channel. */
object ReliableCommands {
    const val READ = 100
    const val RESCAN = 101
    const val QUIESCE_SAF = 102
    const val RESUME_SAF = 103
    const val REBUILD = 104
    const val RESOLVE_GRANT = 105
    const val QUIESCE_MIGRATION = 106
    const val ACTIVATE_STORAGE = 107
    const val STATUS = 200
    const val SAF_LISTING = 201
    const val ARTIFACT = 202
    const val RESULT = 203
    const val BEGIN_SCAN = 300
    const val END_SCAN = 301
    const val RECOVER_SCAN = 302
    const val CLEAR_SCAN = 303
}
