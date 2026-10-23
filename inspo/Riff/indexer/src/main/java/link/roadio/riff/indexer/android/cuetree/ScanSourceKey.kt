package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.Span
import link.roadio.cuetree.Stamp
import link.roadio.riff.storage.hash.nativeXxh3

/** Display names do not belong in the bounded protocol identity. */
internal fun scanSourceKey(
    document: String,
    stamp: Stamp,
    stage: String,
    span: Span?,
    hash: (ByteArray) -> Long = ::nativeXxh3,
): List<String> {
    require(stage == "L1" || stage == "L2")
    val key =
        java.lang.Long
            .toUnsignedString(hash(document.toByteArray(Charsets.UTF_8)), 16)
            .padStart(16, '0')
    return listOf(
        key,
        stamp.modified.toString(),
        stamp.size.toString(),
        stage,
        if (stage == "L1") "0" else span?.beginUs?.toString().orEmpty(),
        if (stage == "L1") "0" else span?.endUs?.toString().orEmpty(),
    )
}
