package link.roadio.riff.indexer.android.media

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaIdV32Generator
import link.roadio.riff.storage.hash.NativeXxh3Factory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class AndroidMediaIdCollisionAnalysisTest {
    @Test
    fun compareCompleteAudioStreamsForRepeatedV32HighValues() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = InstrumentationRegistry.getArguments().getString("riffCollisionInput")
        // Standalone correctness runs must not depend on test order or a prior corpus benchmark.
        // An explicit input keeps the opt-in full-corpus analysis available on every device.
        if (input == null) AndroidMediaIdBenchmarkTest().benchmarkConfiguredMedia()
        val benchmark = JSONObject((input?.let(::File) ?: File(context.filesDir, BENCHMARK_RESULT)).readText())
        require(benchmark.getString("identityVersion") == MediaIdV32Generator.IDENTITY_VERSION)
        val files = benchmark.getJSONArray("files")
        val repeated =
            buildMap<String, MutableList<JSONObject>> {
                repeat(files.length()) { index ->
                    val item = files.getJSONObject(index)
                    if (item.getBoolean("success")) {
                        getOrPut(item.getString("digest"), ::mutableListOf).add(item)
                    }
                }
            }.filterValues { it.size > 1 }
        val groups =
            repeated.toSortedMap().map { (mediaId, members) ->
                val analyzed =
                    members.map { member ->
                        val locator = member.getString("devicePath")
                        val full = fullAudioFingerprint(locator)
                        JSONObject(member.toString())
                            .put("fullAudioXxh3", full.digest)
                            .put("fullAccessUnitCount", full.accessUnitCount)
                            .put("fullPayloadBytes", full.payloadBytes)
                    }
                JSONObject()
                    .put("mediaId", mediaId)
                    .put("fileCount", analyzed.size)
                    .put("distinctFullAudioFingerprints", analyzed.map { it.getString("fullAudioXxh3") }.toSet().size)
                    .put("files", JSONArray(analyzed))
            }
        val result =
            JSONObject()
                .put("platform", "android")
                .put("identityVersion", MediaIdV32Generator.IDENTITY_VERSION)
                .put("repeatedMediaIdGroups", groups.size)
                .put("filesAnalyzedFully", groups.sumOf { it.getInt("fileCount") })
                .put(
                    "semanticDuplicateGroups",
                    groups.count { it.getInt("distinctFullAudioFingerprints") == 1 },
                ).put(
                    "prefixCollisionGroups",
                    groups.count { it.getInt("distinctFullAudioFingerprints") > 1 },
                ).put("groups", JSONArray(groups))
        File(context.filesDir, COLLISION_RESULT).writeText(result.toString())
    }

    private fun fullAudioFingerprint(locator: String): FullFingerprint {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(locator)
            val track =
                (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("No audio track: $locator")
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            NativeXxh3Factory.create().use { digest ->
                digest.update("RIFF-FULL-AUDIO-V1".encodeToByteArray())
                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    digest.update(byteArrayOf(1))
                    digest.update(uint32(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).toLong()))
                } else {
                    digest.update(byteArrayOf(0))
                }
                var index = 0L
                var payloadBytes = 0L
                while (extractor.sampleTrackIndex >= 0) {
                    val size = extractor.sampleSize
                    require(size in 1..MAX_ACCESS_UNIT_SIZE && index <= UINT32_MAX)
                    val bytes = ByteArray(size.toInt())
                    val actual = extractor.readSampleData(ByteBuffer.wrap(bytes), 0)
                    require(actual == bytes.size)
                    digest.update(uint32(index))
                    digest.update(uint32(size))
                    digest.update(bytes)
                    payloadBytes += size
                    index++
                    if (!extractor.advance()) break
                }
                digest.update(uint32(index))
                digest.update(uint64(payloadBytes))
                return FullFingerprint(digest.digest().toUnsignedHex(), index, payloadBytes)
            }
        } finally {
            extractor.release()
        }
    }

    private fun uint32(value: Long): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.toInt()).array()

    private fun uint64(value: Long): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()

    private fun Long.toUnsignedHex(): String =
        java.lang.Long
            .toUnsignedString(this, 16)
            .padStart(16, '0')

    private data class FullFingerprint(
        val digest: String,
        val accessUnitCount: Long,
        val payloadBytes: Long,
    )

    companion object {
        private const val BENCHMARK_RESULT = "media-id-benchmark-result.json"
        private const val COLLISION_RESULT = "media-id-collision-analysis-result.json"
        private const val MAX_ACCESS_UNIT_SIZE = 64L * 1024L * 1024L
        private const val UINT32_MAX = 0xffff_ffffL
    }
}
