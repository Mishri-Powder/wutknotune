package link.roadio.riff.indexer.android.tags

import android.icu.lang.UCharacter
import android.icu.text.Normalizer2
import android.icu.util.VersionInfo
import link.roadio.cuegrain.ced.CedEncodingDetector
import link.roadio.cuegrain.repair.MojibakeEncoding
import link.roadio.cuegrain.repair.MojibakeEncodingDetection
import link.roadio.cuegrain.repair.MojibakeEncodingDetector
import link.roadio.riff.indexer.core.tags.normalization.TagNormalizer
import link.roadio.riff.indexer.core.tags.normalization.TagUnicodePlatform
import link.roadio.riff.indexer.core.tags.normalization.TagXxh3
import link.roadio.riff.storage.hash.nativeXxh3

object AndroidIcuTagUnicodePlatform : TagUnicodePlatform {
    private val nfkcNormalizer = Normalizer2.getNFKCInstance()

    override val revision: String = "ANDROID-ICU-" + VersionInfo.ICU_VERSION

    override fun nfkc(value: String): String = nfkcNormalizer.normalize(value)

    override fun caseFold(value: String): String = UCharacter.foldCase(value, true)
}

object AndroidCedEncodingDetector : MojibakeEncodingDetector {
    private val detector by lazy { CedEncodingDetector() }
    override val revision: String = "CUEGRAIN-CED-0.1.0"

    override fun detect(input: ByteArray): MojibakeEncodingDetection {
        val result = detector.detect(input)
        val encoding =
            when (result.charset?.uppercase(java.util.Locale.ROOT)) {
                "UTF-8", "UTF8" -> MojibakeEncoding.UTF8
                "WINDOWS-1251", "CP1251" -> MojibakeEncoding.WINDOWS_1251
                else -> MojibakeEncoding.OTHER
            }
        return MojibakeEncodingDetection(encoding, result.isReliable, result.bytesConsumed)
    }
}

object AndroidTagNormalization {
    fun tags(): TagNormalizer =
        TagNormalizer(
            unicode = AndroidIcuTagUnicodePlatform,
            xxh3 = TagXxh3(::nativeXxh3),
        )
}
