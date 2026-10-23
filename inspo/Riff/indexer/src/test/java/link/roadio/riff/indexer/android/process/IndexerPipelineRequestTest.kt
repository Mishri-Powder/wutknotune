package link.roadio.riff.indexer.android.process

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IndexerPipelineRequestTest {
    @Test
    fun analysisAndIndexRebuildScopesAreIndependent() {
        assertEquals(DatabaseRebuild.AU, DatabaseRebuild.fromMask(1))
        assertEquals(DatabaseRebuild.IA, DatabaseRebuild.fromMask(2))
        assertEquals(DatabaseRebuild.AU_IA, DatabaseRebuild.fromMask(3))
        for (invalid in listOf(0, 4)) {
            assertThrows(NoSuchElementException::class.java) { DatabaseRebuild.fromMask(invalid) }
        }
    }
}
