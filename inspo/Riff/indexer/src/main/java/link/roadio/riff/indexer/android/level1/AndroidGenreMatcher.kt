package link.roadio.riff.indexer.android.level1

import android.content.Context
import com.google.flatbuffers.kotlin.ReadWriteBuffer
import link.roadio.riff.indexer.android.tags.AndroidTagNormalization
import link.roadio.riff.indexer.core.tags.normalization.TagKind
import link.roadio.riff.indexer.level1.GenreMatcher
import link.roadio.riff.indexer.storage.gnre.GnreGenreMatcher

object AndroidGenreMatcher {
    fun load(
        context: Context,
        buffer: ReadWriteBuffer,
    ): GenreMatcher {
        val tags = AndroidTagNormalization.tags()
        return GnreGenreMatcher.decode(buffer) { canonical ->
            tags.normalize(TagKind.GENRE, canonical).id
        }
    }
}
