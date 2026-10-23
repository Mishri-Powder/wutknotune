package link.roadio.riff.indexer.core.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class RiffLoggerTest {
    @Test
    fun thresholdAndNoOpAreSafeForProduction() {
        val records = mutableListOf<RiffLogRecord>()
        val logger = RiffLogger("test", RiffLogLevel.INFO, records::add)

        logger.debug("ignored")
        logger.info("accepted", fields = mapOf("count" to 1))
        RiffLogger.disabled().error("also-ignored")

        assertEquals(listOf("accepted"), records.map(RiffLogRecord::event))
    }
}
