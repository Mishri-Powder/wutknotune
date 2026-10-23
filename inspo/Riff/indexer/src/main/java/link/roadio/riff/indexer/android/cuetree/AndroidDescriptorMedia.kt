package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.OpenMedia
import java.io.FileDescriptor

/** Borrowed descriptor: the enclosing OpenMedia acquisition is the only lifetime owner. */
internal interface AndroidDescriptorMedia : OpenMedia {
    val displayName: String get() = "Current track"
    val descriptor: FileDescriptor
    val offset: Long
}
