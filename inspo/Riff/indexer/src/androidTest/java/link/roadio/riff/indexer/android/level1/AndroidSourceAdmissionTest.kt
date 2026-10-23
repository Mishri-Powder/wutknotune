package link.roadio.riff.indexer.android.level1

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.android.media.AndroidMediaIdV32Generator
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.indexer.level1.Level1ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CancellationException

@UnstableApi
@RunWith(AndroidJUnit4::class)
class AndroidSourceAdmissionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun realContainersAreProbedWithoutFilenameFormatAdmission() {
        AndroidSourceAdmission(context).use { admission ->
            for (asset in listOf("genres/explicit.mp3", "chapters/nero.m4a", "chapters/quicktime.m4a", "chapters/both.m4b")) {
                fixture(asset, "misleading.bin") { source ->
                    assertTrue(asset, admission.check(source, Available) is Level1ScanResult.Success)
                    AndroidMediaIdV32Generator(context).generateHigh(source, Available)
                }
            }
        }
    }

    @Test
    fun durationIsRetainedFromTheSameHandledAudioProbe() {
        fixture("genres/explicit.mp3", "duration.bin") { source ->
            AndroidSourceAdmission(context).use { admission ->
                val result = admission.inspect(source, Available)
                assertTrue(result is Level1ScanResult.Success)
                val admitted = (result as Level1ScanResult.Success).value
                assertTrue(requireNotNull(admitted.durationUs) > 0)
            }
        }
    }

    @Test
    fun nonAudioWithAudioExtensionFailsContentCheck() {
        val file = File.createTempFile("not-audio-", ".mp3", context.cacheDir)
        try {
            file.writeText("This is not an audio container.")
            AndroidSourceAdmission(context).use {
                assertEquals(Level1ScanResult.ContentScanFailed, it.check(SourceMedia(file.absolutePath, file.name), Available))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun misleadingAdaptiveExtensionDoesNotChangeThePlayerSourceConfiguration() {
        fixture("genres/explicit.mp3", "misleading.m3u8") { source ->
            AndroidSourceAdmission(context).use {
                assertTrue(it.check(source, Available) is Level1ScanResult.Success)
            }
        }
    }

    @Test
    fun readBudgetIsRetryableAndDoesNotPoisonTheNextProbe() {
        fixture("genres/explicit.mp3", "no-extension") { source ->
            AndroidSourceAdmission(context, timeoutMillis = 1_000L, maxReadBytes = 1L).use {
                assertEquals(Level1ScanResult.SourceUnavailable, it.check(source, Available))
            }
            AndroidSourceAdmission(context).use {
                assertTrue(it.check(source, Available) is Level1ScanResult.Success)
            }
        }
    }

    @Test
    fun missingSourceIsRetryable() {
        AndroidSourceAdmission(context, timeoutMillis = 1_000L).use {
            assertEquals(
                Level1ScanResult.SourceUnavailable,
                it.check(SourceMedia(File(context.cacheDir, "source-does-not-exist").absolutePath, "missing"), Available),
            )
        }
    }

    @Test
    fun cancellationPropagatesAndClosesPreparation() {
        fixture("genres/explicit.mp3", "cancelled") { source ->
            var checks = 0
            val signal =
                object : CancellationSignal {
                    override val cancelled get() = checks >= 2

                    override fun ensureAvailable() {
                        checks += 1
                        if (cancelled) throw CancellationException("test cancellation")
                    }
                }
            AndroidSourceAdmission(context).use {
                val error = runCatching { it.check(source, signal) }.exceptionOrNull()
                assertTrue(error is CancellationException)
                assertTrue(it.check(source, Available) is Level1ScanResult.Success)
            }
        }
    }

    @Test
    fun workerCancellationIsNotWrappedAsAJobFailure() {
        fixture("genres/explicit.mp3", "worker-cancelled") { source ->
            val signal =
                object : CancellationSignal {
                    override val cancelled = false

                    override fun ensureAvailable() {
                        if (Thread.currentThread().name == "RiffSourceCapabilities") {
                            throw CancellationException("worker cancelled")
                        }
                    }
                }

            AndroidSourceAdmission(context).use {
                val error = runCatching { it.check(source, signal) }.exceptionOrNull()
                assertTrue(error.toString(), error is CancellationException)
            }
        }
    }

    @Test
    fun actualDeviceInventoryRevisionIsStable() {
        AndroidSourceAdmission(context).use { first ->
            AndroidSourceAdmission(context).use { second ->
                assertEquals(first.revision, second.revision)
                assertTrue(first.revision.startsWith("SOURCE-CAPABILITIES-V3/"))
            }
        }
    }

    private fun fixture(
        asset: String,
        name: String,
        action: (SourceMedia) -> Unit,
    ) {
        val directory = File(context.cacheDir, "source-admission-fixtures").apply { mkdirs() }
        val file = File(directory, name)
        try {
            instrumentation.context.assets
                .open(asset)
                .use { input -> file.outputStream().use(input::copyTo) }
            action(SourceMedia(file.absolutePath, name))
        } finally {
            file.delete()
            directory.delete()
        }
    }

    private object Available : CancellationSignal {
        override val cancelled = false

        override fun ensureAvailable() = Unit
    }
}
