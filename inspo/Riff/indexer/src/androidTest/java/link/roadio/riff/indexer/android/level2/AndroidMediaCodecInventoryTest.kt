package link.roadio.riff.indexer.android.level2

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Records decoder selection and batching capabilities exposed by the device. */
@RunWith(AndroidJUnit4::class)
class AndroidMediaCodecInventoryTest {
    @Test
    fun recordAudioDecoderInventory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val all = MediaCodecList(MediaCodecList.ALL_CODECS)
        val regular = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val result =
            JSONObject()
                .put("event", "media_codec_audio_inventory")
                .put("schema_version", 1)
                .put(
                    "mimes",
                    JSONArray(
                        TARGET_MIMES.map { mime ->
                            val format =
                                MediaFormat.createAudioFormat(mime, SAMPLE_RATE, CHANNELS).apply {
                                    if (mime == AAC_MIME) {
                                        setInteger(
                                            MediaFormat.KEY_AAC_PROFILE,
                                            MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                                        )
                                    }
                                }
                            JSONObject()
                                .put("mime", mime)
                                .put(
                                    "regular_default",
                                    regular.findDecoderForFormat(format) ?: JSONObject.NULL,
                                ).put(
                                    "all_default",
                                    all.findDecoderForFormat(format) ?: JSONObject.NULL,
                                ).put(
                                    "decoders",
                                    JSONArray(
                                        all.codecInfos
                                            .filter { !it.isEncoder && mime in it.supportedTypes }
                                            .map { info -> codecJson(info, mime) },
                                    ),
                                )
                        },
                    ),
                )
        val resultFile = File(context.filesDir, RESULT_FILE)
        resultFile.writeText(result.toString())
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("riffMediaCodecInventory", resultFile.absolutePath)
            },
        )
    }

    private fun codecJson(
        info: MediaCodecInfo,
        mime: String,
    ): JSONObject {
        val capabilities = info.getCapabilitiesForType(mime)
        return JSONObject()
            .put("name", info.name)
            .put("canonical_name", info.canonicalName)
            .put("alias", info.isAlias)
            .put("hardware_accelerated", info.isHardwareAccelerated)
            .put("software_only", info.isSoftwareOnly)
            .put("vendor", info.isVendor)
            .put("max_supported_instances", capabilities.maxSupportedInstances)
            .put(
                "feature_multiple_frames",
                capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_MultipleFrames),
            ).put(
                "feature_frame_parsing",
                capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_FrameParsing),
            ).put(
                "feature_low_latency",
                capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency),
            ).put(
                "feature_tunneled_playback",
                capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback),
            ).put(
                "profile_levels",
                JSONArray(
                    capabilities.profileLevels.map {
                        JSONObject().put("profile", it.profile).put("level", it.level)
                    },
                ),
            )
    }

    private companion object {
        const val RESULT_FILE = "media-codec-audio-inventory.json"
        const val AAC_MIME = "audio/mp4a-latm"
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
        val TARGET_MIMES = listOf(AAC_MIME, "audio/mpeg")
    }
}
