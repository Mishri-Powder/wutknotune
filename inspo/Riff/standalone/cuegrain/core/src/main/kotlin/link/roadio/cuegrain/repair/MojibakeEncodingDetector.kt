// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain.repair

enum class MojibakeEncoding {
    UTF8,
    WINDOWS_1251,
    OTHER,
}

data class MojibakeEncodingDetection(
    val encoding: MojibakeEncoding,
    val isReliable: Boolean,
    val bytesConsumed: Int,
)

interface MojibakeEncodingDetector {
    val revision: String

    fun detect(input: ByteArray): MojibakeEncodingDetection
}

const val CED_MAX_INPUT_BYTES: Int = 65_536
