@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package link.roadio.riff.player.playback

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real platform focus requests over generated audio; no library, grant or device identity fixture. */
class NavigationAudioFocusInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var player: ExoPlayer
    private lateinit var focus: NavigationAudioFocus
    private lateinit var audio: File
    private var book = false
    private val manager = context.getSystemService(AudioManager::class.java)
    private val navigator =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            ).setOnAudioFocusChangeListener({ }, Handler(Looper.getMainLooper()))
            .build()

    @Before
    fun setup() {
        audio = File(context.cacheDir, "navigation-focus.wav")
        val bytes = ByteBuffer.allocate(44 + 30 * 8_000 * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes
            .put("RIFF".toByteArray())
            .putInt(bytes.capacity() - 8)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(8_000)
            .putInt(16_000)
            .putShort(2)
            .putShort(16)
            .put("data".toByteArray())
            .putInt(bytes.capacity() - 44)
        audio.writeBytes(bytes.array())
        onMain {
            player = ExoPlayer.Builder(context).build()
            focus = NavigationAudioFocus(context, player, Handler(Looper.getMainLooper())) { book }
            player.volume = 0.65f
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(audio)))
            player.prepare()
        }
    }

    @After
    fun cleanup() {
        manager.abandonAudioFocusRequest(navigator)
        onMain {
            focus.close()
            player.release()
        }
        audio.delete()
    }

    @Test
    fun musicDucksAndRestoresOriginalVolumeWithoutPausing() {
        start(false, 5_000L)
        guidance()
        await { player.volume < 0.2f }
        onMain {
            assertTrue(player.isPlaying)
            assertTrue(focus.playRequested)
            assertEquals(0.13f, player.volume, 0.001f)
        }
        manager.abandonAudioFocusRequest(navigator)
        await { player.volume == 0.65f }
        onMain { assertTrue(player.isPlaying) }
    }

    @Test
    fun bookPausesAndSeeksBackBeforeFocusResume() {
        start(true, 7_000L)
        guidance()
        await { !player.playWhenReady }
        var paused = 0L
        onMain {
            paused = player.currentPosition
            assertTrue(focus.playRequested)
        }
        Thread.sleep(150)
        onMain { assertEquals(paused, player.currentPosition) }
        manager.abandonAudioFocusRequest(navigator)
        await { player.isPlaying }
        onMain { assertTrue(player.currentPosition in (paused - 2_000L)..(paused - 1_500L)) }
    }

    @Test
    fun aBookReachedWhileMusicIsDuckedPausesUntilNavigationEnds() {
        start(false, 7_000L)
        guidance()
        await { player.volume < 0.2f }
        onMain {
            book = true
            focus.refreshContentType()
        }
        await { !player.playWhenReady }
        var paused = 0L
        onMain { paused = player.currentPosition }
        manager.abandonAudioFocusRequest(navigator)
        await { player.isPlaying }
        onMain {
            assertEquals(0.65f, player.volume, 0.001f)
            assertTrue(player.currentPosition in (paused - 2_000L)..(paused - 1_500L))
        }
    }

    @Test
    fun changingOccurrenceDuringGuidanceCancelsPendingResume() {
        start(true, 7_000L)
        guidance()
        await { !player.playWhenReady }
        onMain {
            player.setMediaItem(
                MediaItem
                    .Builder()
                    .setMediaId("replacement")
                    .setUri(Uri.fromFile(audio))
                    .build(),
            )
            player.prepare()
        }
        manager.abandonAudioFocusRequest(navigator)
        Thread.sleep(200)
        onMain {
            assertFalse(focus.playRequested)
            assertFalse(player.playWhenReady)
        }
    }

    @Test
    fun bookNearStartUsesOrdinarySeekClamp() {
        start(true, 300L)
        guidance()
        await { !player.playWhenReady }
        manager.abandonAudioFocusRequest(navigator)
        await { player.isPlaying }
        onMain { assertTrue(player.currentPosition in 0L..500L) }
    }

    @Test
    fun explicitPauseDuringGuidanceCancelsResume() {
        start(true, 7_000L)
        guidance()
        await { !player.playWhenReady }
        onMain { focus.setPlayWhenReady(false) }
        manager.abandonAudioFocusRequest(navigator)
        Thread.sleep(200)
        onMain {
            assertFalse(focus.playRequested)
            assertFalse(player.playWhenReady)
        }
    }

    @Test
    fun permanentFocusLossDoesNotResume() {
        start(true, 7_000L)
        val other =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener({ }, Handler(Looper.getMainLooper()))
                .build()
        try {
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(other))
            await { !player.playWhenReady }
            manager.abandonAudioFocusRequest(other)
            Thread.sleep(200)
            onMain {
                assertFalse(focus.playRequested)
                assertFalse(player.playWhenReady)
            }
        } finally {
            manager.abandonAudioFocusRequest(other)
        }
    }

    private fun start(
        audiobook: Boolean,
        position: Long,
    ) {
        onMain {
            book = audiobook
            player.seekTo(position)
            focus.setPlayWhenReady(true)
        }
        await { player.isPlaying }
    }

    private fun guidance() {
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(navigator))
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        do {
            var satisfied = false
            onMain { satisfied = condition() }
            if (satisfied) return
            Thread.sleep(10)
        } while (System.nanoTime() < deadline)
        fail("Timed out waiting for playback/focus")
    }
}
