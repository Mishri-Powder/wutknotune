// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

/**
 * Checks the complete 10-byte ID3v2 header before any parser allocates its advertised body.
 * A caller may pass null for an unknown remaining source length.
 */
object Id3MetadataBudget {
    fun bodySize(
        header: ByteArray,
        offset: Int = 0,
        maximumTagBytes: Int = Limits().metadataBytes,
        remainingSourceBytes: Long? = null,
    ): Int {
        require(offset >= 0 && header.size - offset >= 10) { "Incomplete ID3 header" }
        require(
            header[offset] == 'I'.code.toByte() && header[offset + 1] == 'D'.code.toByte() &&
                header[offset + 2] == '3'.code.toByte(),
        ) { "Missing ID3 marker" }
        val version = header[offset + 3].toInt() and 255
        require(version in 2..4) { "Unsupported ID3 version" }
        val bytes = (6..9).map { header[offset + it].toInt() and 255 }
        require(bytes.all { it and 0x80 == 0 }) { "Invalid ID3 syncsafe size" }
        val body = (bytes[0] shl 21) or (bytes[1] shl 14) or (bytes[2] shl 7) or bytes[3]
        val footer = if (version == 4 && header[offset + 5].toInt() and 0x10 != 0) 10 else 0
        val total = 10L + body + footer
        require(maximumTagBytes >= 10 && total <= maximumTagBytes) { "ID3 metadata exceeds budget" }
        require(remainingSourceBytes == null || remainingSourceBytes >= total) { "ID3 metadata exceeds source" }
        return body
    }
}
