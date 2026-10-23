package link.roadio.riff.indexer.android.level1

import androidx.media3.common.DataReader
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.mp3.Mp3Extractor
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

@UnstableApi
class BudgetedAdmissionExtractorsInstrumentedTest {
    @Test fun maliciousTinyId3IsRejectedBeforeMedia3BodyAllocation() {
        val bytes =
            byteArrayOf(
                'I'.code.toByte(),
                'D'.code.toByte(),
                '3'.code.toByte(),
                4,
                0,
                0,
                127,
                127,
                127,
                127,
            )
        var position = 0
        val reader =
            DataReader { target, offset, length ->
                if (position == bytes.size) {
                    -1
                } else {
                    val count = minOf(length, bytes.size - position)
                    bytes.copyInto(target, offset, position, position + count)
                    position += count
                    count
                }
            }
        val input = DefaultExtractorInput(reader, 0, bytes.size.toLong())
        assertThrows(IOException::class.java) {
            Mp3Extractor().sniff(BudgetedId3Input(input))
        }
    }
}
