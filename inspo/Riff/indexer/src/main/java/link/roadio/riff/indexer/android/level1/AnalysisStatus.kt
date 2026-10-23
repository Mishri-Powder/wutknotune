package link.roadio.riff.indexer.android.level1

import link.roadio.riff.indexer.android.process.sourceFileStatus
import link.roadio.riff.indexer.core.media.MediaIdV32FullScanReason

internal fun fullMediaIdScanStatus(
    displayName: String,
    reason: MediaIdV32FullScanReason,
): String = sourceFileStatus("Full Media.ID scan (${reason.statusValue})", displayName)
