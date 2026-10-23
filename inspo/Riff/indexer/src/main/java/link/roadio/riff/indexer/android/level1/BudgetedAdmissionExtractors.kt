package link.roadio.riff.indexer.android.level1

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.mp3.Mp3Extractor
import link.roadio.cuegrain.Id3MetadataBudget
import java.io.IOException

/**
 * Restricts ID3 allocations in the stock MP3 extractor used for source admission. Media3's
 * Id3Peeker allocates from the syncsafe size before its DataSource reads the body.
 */
@UnstableApi
internal class BudgetedAdmissionExtractors(
    private val delegate: ExtractorsFactory = DefaultExtractorsFactory(),
) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = delegate.createExtractors().map(::guard).toTypedArray()

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> = delegate.createExtractors(uri, responseHeaders).map(::guard).toTypedArray()

    private fun guard(extractor: Extractor): Extractor = if (extractor is Mp3Extractor) BudgetedMp3Extractor(extractor) else extractor
}

@UnstableApi
private class BudgetedMp3Extractor(
    private val delegate: Mp3Extractor,
) : Extractor by delegate {
    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(BudgetedId3Input(input))

    override fun read(
        input: ExtractorInput,
        seekPosition: PositionHolder,
    ): Int = delegate.read(BudgetedId3Input(input), seekPosition)
}

@UnstableApi
internal class BudgetedId3Input(
    private val delegate: ExtractorInput,
) : ExtractorInput by delegate {
    override fun peekFully(
        target: ByteArray,
        offset: Int,
        length: Int,
    ) {
        delegate.peekFully(target, offset, length)
        checkHeader(target, offset, length)
    }

    override fun peekFully(
        target: ByteArray,
        offset: Int,
        length: Int,
        allowEndOfInput: Boolean,
    ): Boolean {
        val complete = delegate.peekFully(target, offset, length, allowEndOfInput)
        if (complete) checkHeader(target, offset, length)
        return complete
    }

    private fun checkHeader(
        target: ByteArray,
        offset: Int,
        length: Int,
    ) {
        // Id3Peeker's scratch is 10 bytes, growing to 20 for its sliding header window.
        if (target.size !in 10..20 || length !in 1..10) return
        val end = offset + length
        if (end < 10 || end > target.size) return
        val start = end - 10
        if (target[start] != 'I'.code.toByte() ||
            target[start + 1] != 'D'.code.toByte() ||
            target[start + 2] != '3'.code.toByte()
        ) {
            return
        }
        val remaining = delegate.length.takeIf { it >= 0 }?.let { it - delegate.peekPosition + 10L }
        try {
            Id3MetadataBudget.bodySize(target, start, remainingSourceBytes = remaining)
        } catch (error: IllegalArgumentException) {
            throw IOException("Invalid or oversized ID3 metadata", error)
        }
    }
}
