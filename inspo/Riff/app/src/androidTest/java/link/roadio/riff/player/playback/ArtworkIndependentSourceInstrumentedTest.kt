@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.indexer.core.media.MediaId
import link.roadio.riff.player.artwork.ArtworkKey
import link.roadio.riff.player.artwork.ArtworkResolution
import link.roadio.riff.player.artwork.PlayerArtworkLoader
import link.roadio.riff.player.storage.PlaybackCheckpoint
import link.roadio.riff.player.storage.PlaybackQueueKind
import link.roadio.riff.player.storage.PlaybackQueueOccurrence
import link.roadio.riff.player.storage.PlaybackQueueSnapshot
import link.roadio.riff.ui.ArtworkUiState
import link.roadio.riff.ui.PlaybackSourceRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ArtworkIndependentSourceInstrumentedTest {
    private fun field(name: String) = RiffPlaybackService::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Test
    fun realDataSpecResolutionReturnsWhileArtworkProviderIsBlocked() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val source = PlaybackSourceRef(31, 1, emptyList())
        val mediaId = MediaId(41)
        val occurrence = PlaybackQueueOccurrence(23, 0, 0, mediaId)
        val queue =
            PlaybackQueueSnapshot(
                revisionId = 17,
                kind = PlaybackQueueKind.MANUAL,
                sourceProfileId = 1,
                temporary = false,
                occurrences = listOf(occurrence),
                checkpoint = PlaybackCheckpoint(occurrenceId = 23),
            )
        val planned = PlannedOccurrence(17, occurrence, listOf(source), "Fixture", "", "")
        val expected = Uri.parse("content://riff.fixture/audio")
        val key = ArtworkKey(17, 23, 31, 0)
        val loader =
            PlayerArtworkLoader({ selected, _ ->
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                ArtworkResolution(ArtworkUiState(selected.bindingId, bitmap))
            })
        lateinit var service: RiffPlaybackService
        instrumentation.runOnMainSync {
            service = RiffPlaybackService()
            field("artworkLoader").set(service, loader)
            // The datasource method still executes; only uninitialized Media3 UI callbacks are fenced.
            (field("serviceDestroyed").get(service) as AtomicBoolean).set(true)
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val plan = field("executionPlan").get(service) as AtomicReference<QueueExecutionPlan?>
            plan.set(QueueExecutionPlan(queue, mapOf(23L to planned)))
            val bindingType = RiffPlaybackService::class.java.declaredClasses.single { it.simpleName == "ResolvedBinding" }
            val constructor = bindingType.declaredConstructors.single().apply { isAccessible = true }
            val binding = constructor.newInstance(17L, 23L, mediaId, source, expected)

            @Suppress("UNCHECKED_CAST")
            val bindings = field("resolvedBindings").get(service) as ConcurrentHashMap<Long, Any>
            bindings[23] = binding
            val spec = DataSpec(Uri.parse("${PlaybackMetadata.URI_SCHEME}://17/23"))
            val resolve =
                RiffPlaybackService::class.java
                    .getDeclaredMethod("resolveDataSpec", DataSpec::class.java, Set::class.java)
                    .apply { isAccessible = true }
            val started = System.nanoTime()
            val result = resolve.invoke(service, spec, emptySet<Long>()) as BindingDataSpec
            result.opened()
            assertTrue("Audio source waited for artwork", System.nanoTime() - started < TimeUnit.SECONDS.toNanos(1))
            assertEquals(expected, result.dataSpec.uri)
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertEquals(1L, release.count)
            assertNull(loader.artworkFor(key)?.bitmap)
            release.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (loader.artworkFor(key)?.bitmap == null && System.nanoTime() < deadline) Thread.sleep(10)
            assertSame(bitmap, loader.artworkFor(key)?.bitmap)
            assertEquals(31L, loader.artworkFor(key)?.sourceBindingId)
            field("artworkGeneration").setLong(service, 1L)
            loader.invalidate(1L)
            val select =
                RiffPlaybackService::class.java
                    .getDeclaredMethod("selectArtwork", Long::class.javaObjectType, Long::class.javaObjectType)
                    .apply { isAccessible = true }
            select.invoke(service, null, null)
            select.invoke(service, 17L, 23L)
            val replacementKey = key.copy(sourceGeneration = 1)
            val replacementDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (loader.artworkFor(replacementKey)?.bitmap == null && System.nanoTime() < replacementDeadline) Thread.sleep(10)
            assertSame(bitmap, loader.artworkFor(replacementKey)?.bitmap)
        } finally {
            release.countDown()
            loader.close()
            for (name in listOf("stateExecutor", "queueExecutor", "sourceExecutor")) {
                (field(name).get(service) as ExecutorService).shutdownNow()
            }
            bitmap.recycle()
        }
    }
}
