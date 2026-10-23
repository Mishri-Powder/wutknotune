package link.roadio.riff.indexer.android.level2

import android.content.ContentUris
import android.media.MediaExtractor
import android.media.MediaFormat
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDecoderAutotuneInstrumentedTest {
    @Test
    fun productionDecoderTunesAndDecodesMp3AndAac() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val volume = configuredCorpusVolume(arguments, ARG_VOLUME)
        val collection = MediaStore.Audio.Media.getContentUri(volume)

        AndroidAudioExcerptDecoder(context).use { decoder ->
            TARGET_MIMES.forEachIndexed { index, mime ->
                val id = findSourceId(collection, mime)
                val source =
                    SourceMedia(
                        locator = ContentUris.withAppendedId(collection, id).toString(),
                        relativePath = "autotune-$index",
                    )
                val decoded =
                    decoder.decode(
                        source = source,
                        decoderHint = TEST_HINT_BASE + index,
                        needsLoudness = true,
                        needsAi = true,
                        cancellation = Available,
                    )
                assertTrue("$mime AI PCM is empty", decoded.pcm.any { it != 0f })
                assertTrue("$mime loudness is unavailable", decoded.integratedLufs?.isFinite() == true)
                val reused =
                    decoder.decode(
                        source = source,
                        decoderHint = TEST_HINT_BASE + index,
                        needsLoudness = false,
                        needsAi = true,
                        cancellation = Available,
                    )
                assertTrue("$mime reused AI PCM is empty", reused.pcm.any { it != 0f })
                assertTrue("$mime reused decode unexpectedly measured loudness", reused.integratedLufs == null)
            }
        }
    }

    private fun findSourceId(
        collection: android.net.Uri,
        expectedMime: String,
    ): Long {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.contentResolver
            .query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DURATION),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                var inspected = 0
                while (cursor.moveToNext() && inspected < SCAN_LIMIT) {
                    inspected += 1
                    if (cursor.getLong(durationColumn) < MINIMUM_DURATION_MS) continue
                    val id = cursor.getLong(idColumn)
                    val locator = ContentUris.withAppendedId(collection, id)
                    val extractor = MediaExtractor()
                    try {
                        extractor.setDataSource(context, locator, emptyMap())
                        val actual =
                            (0 until extractor.trackCount)
                                .map(extractor::getTrackFormat)
                                .firstNotNullOfOrNull { format ->
                                    format.getString(MediaFormat.KEY_MIME)?.takeIf { it.startsWith("audio/") }
                                }
                        if (actual == expectedMime) return id
                    } catch (_: Throwable) {
                        Unit
                    } finally {
                        extractor.release()
                    }
                }
            }
        throw IllegalArgumentException("No $expectedMime source found")
    }

    private object Available : CancellationSignal {
        override val cancelled = false

        override fun ensureAvailable() = Unit
    }

    private companion object {
        const val ARG_VOLUME = "riffMediaVolume"
        const val SCAN_LIMIT = 1_000
        const val MINIMUM_DURATION_MS = 90_000L
        const val TEST_HINT_BASE = 0x4155544f54554e45L
        val TARGET_MIMES = listOf("audio/mpeg", "audio/mp4a-latm")
    }
}
