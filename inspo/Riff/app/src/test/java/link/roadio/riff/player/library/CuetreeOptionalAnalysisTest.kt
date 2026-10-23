package link.roadio.riff.player.library

import link.roadio.cuetree.MediaId
import link.roadio.riff.indexer.core.analysis.AnalysisRecord
import link.roadio.riff.indexer.core.analysis.AnalysisSnapshot
import link.roadio.riff.indexer.core.analysis.CuetreeAnalysisCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class CuetreeOptionalAnalysisTest {
    @Test fun allAndOnlyCurrentRebuildMasksAreAccepted() {
        assertEquals(listOf(1, 2, 3), (-1..7).filter(::isCuetreeRebuildMask))
    }

    @Test fun missingCorruptOrUnreadableAnalysisDoesNotBlockLibrary() {
        assertNull(readOptionalCuetreeAnalysis { null })
        assertNull(readOptionalCuetreeAnalysis { throw IOException("unavailable") })
        assertNull(readOptionalCuetreeAnalysis { CuetreeAnalysisCodec.decode(ByteArray(12)) })
        assertNull(
            readOptionalCuetreeAnalysis {
                AnalysisSnapshot(1, listOf(AnalysisRecord(MediaId(2)), AnalysisRecord(MediaId(1))))
            },
        )
        val valid = AnalysisSnapshot(1, listOf(AnalysisRecord(MediaId(1))))
        assertEquals(valid, readOptionalCuetreeAnalysis { valid })
    }
}
