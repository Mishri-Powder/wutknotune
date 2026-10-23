// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain.ced

import link.roadio.cuegrain.EncodingDetection
import link.roadio.cuegrain.EncodingDetector
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class CedEncodingDetector : EncodingDetector {
    override fun detect(bytes: ByteArray): EncodingDetection {
        require(bytes.size <= 65536)
        val result = detectNative(bytes)
        val charset = result[0].takeIf { it.isNotBlank() && runCatching { Charset.isSupported(it) }.getOrDefault(false) }
        return EncodingDetection(charset, result[1] == "true", result[2].toInt())
    }

    private external fun detectNative(bytes: ByteArray): Array<String>

    companion object {
        init {
            val android = System.getProperty("java.vm.name", "").orEmpty().contains("Dalvik", true)
            if (android) {
                System.loadLibrary("cuegrain_ced")
            } else {
                val name = System.mapLibraryName("cuegrain_ced")
                val resource =
                    requireNotNull(CedEncodingDetector::class.java.getResourceAsStream("/native/$name")) {
                        "Native CED backend is not packaged for this platform"
                    }
                val file = Files.createTempFile("cuegrain-ced-", "-" + name)
                resource.use { Files.copy(it, file, StandardCopyOption.REPLACE_EXISTING) }
                file.toFile().deleteOnExit()
                System.load(file.toAbsolutePath().toString())
            }
        }
    }
}
