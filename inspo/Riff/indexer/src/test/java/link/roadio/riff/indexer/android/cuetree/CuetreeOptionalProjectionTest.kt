package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CuetreeOptionalProjectionTest {
    private val factual = Snapshot.empty("test")

    @Test
    fun invalidOptionalContributionFallsBackToValidatedFacts() {
        var fallbackCalls = 0
        var reported = 0
        val result =
            projectWithOptionalAnalysis(
                analysis = 1,
                project = { evidence ->
                    if (evidence != null) throw IllegalArgumentException("optional")
                    fallbackCalls++
                    factual
                },
                onFailure = { reported++ },
            )
        assertSame(factual, result)
        assertEquals(1, fallbackCalls)
        assertEquals(1, reported)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidFactualProjectionStillFails() {
        projectWithOptionalAnalysis<Int>(1, { throw IllegalArgumentException("factual") })
    }
}
