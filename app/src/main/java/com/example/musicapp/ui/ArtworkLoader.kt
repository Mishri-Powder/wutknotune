package com.example.musicapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableDeferred
import java.util.concurrent.atomic.AtomicLong

/**
 * Embedded-artwork loader for the Poster Wall (adapted from Iris Music's ArtworkLoader).
 *
 * Three-layer acceleration so wall tiles never block the main thread:
 *  1. Memory LRU — same-session hits, no re-parse while scrolling.
 *  2. Disk cache — decoded thumbnails persisted to cacheDir keyed by file
 *     fingerprint (path|size|mtime), so a process restart doesn't reopen every
 *     audio file with MediaMetadataRetriever (which is extremely slow).
 *  3. Downsampled to tile size (<=256px) — cheap decode, bounded memory.
 *
 * Stability rules carried over from Iris:
 *  - peek() is pure in-memory O(1): safe to call during composition on the main thread.
 *  - load() deduplicates concurrent requests per path (in-flight map), so a fast
 *    drag can't pile up duplicate MediaMetadataRetriever opens.
 *  - Failed paths are remembered (bounded) and never retried within the session.
 *  - Everything IO happens on Dispatchers.IO; nothing here touches the player.
 */
object ArtworkLoader {

    private const val TARGET_PX = 256
    private const val MEM_CACHE_MAX = 256
    private const val DISK_MAX_BYTES = 6L * 1024 * 1024
    private const val DISK_TRIM_RATIO = 0.8
    private const val FAILED_MAX = 512

    @Volatile private var diskDir: File? = null
    private val diskBytes = AtomicLong(0L)
    private val cache = object : LinkedHashMap<String, Bitmap>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MEM_CACHE_MAX
    }
    private val lock = Any()
    private val failedPaths = object : LinkedHashMap<String, Unit>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > FAILED_MAX
    }
    private val diskMutex = Mutex()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()

    /** Call once from Application/Activity onCreate; never touches disk synchronously. */
    fun init(context: Context) {
        if (diskDir == null) {
            val dir = File(context.applicationContext.cacheDir, "artwork").apply { mkdirs() }
            diskDir = dir
            CoroutineScope(Dispatchers.IO).launch {
                var total = 0L
                dir.listFiles()?.forEach { total += it.length() }
                diskBytes.set(total)
                if (total > DISK_MAX_BYTES) trimDiskBlocking(dir)
            }
        }
    }

    private fun keyOf(path: String): String {
        val f = File(path)
        return if (f.exists()) "$path|${f.length()}|${f.lastModified()}" else path
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun diskFile(path: String): File? =
        diskDir?.let { File(it, sha1(keyOf(path)) + ".webp") }

    /** Synchronous memory-only peek — safe in composition (no file IO). */
    fun peek(filePath: String?): Bitmap? {
        if (filePath.isNullOrEmpty()) return null
        synchronized(lock) { return cache[filePath] }
    }

    /** Async load: memory -> disk -> parse embedded picture. Never throws. */
    suspend fun load(filePath: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (filePath.isNullOrEmpty()) return@withContext null
        synchronized(lock) {
            cache[filePath]?.let { return@withContext it }
            if (filePath in failedPaths) return@withContext null
        }
        val mine = CompletableDeferred<Bitmap?>(null)
        val existing = inFlight.putIfAbsent(filePath, mine)
        if (existing != null) return@withContext runCatching { existing.await() }.getOrNull()
        try {
            val bmp = loadUncached(filePath)
            mine.complete(bmp)
            bmp
        } catch (e: Throwable) {
            mine.complete(null) // never propagate: a broken file must not crash a tile
            null
        } finally {
            inFlight.remove(filePath, mine)
        }
    }

    private suspend fun loadUncached(filePath: String): Bitmap? {
        // 1) disk cache hit
        val df = diskFile(filePath)
        if (df != null && df.exists()) {
            val bmp = BitmapFactory.decodeFile(df.absolutePath)
            if (bmp != null) {
                synchronized(lock) { cache[filePath] = bmp }
                return bmp
            }
        }
        // 2) parse embedded artwork from the audio file itself (offline, local read)
        val bytes = readPictureBytes(filePath)
        if (bytes == null || bytes.isEmpty()) {
            synchronized(lock) { failedPaths[filePath] = Unit }
            return null
        }
        val bmp = decodeSampled(bytes, TARGET_PX) ?: return null
        synchronized(lock) { cache[filePath] = bmp }
        // 3) persist thumbnail for next launch
        if (df != null) writeDisk(df, bmp)
        return bmp
    }

    private fun readPictureBytes(filePath: String): ByteArray? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(filePath)
            mmr.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun decodeSampled(bytes: ByteArray, maxDimPx: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= maxDimPx) sample *= 2
        val real = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565 // half the memory, fine for tiles
        }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, real)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private suspend fun writeDisk(df: File, bmp: Bitmap) = diskMutex.withLock {
        runCatching {
            df.parentFile?.mkdirs()
            java.io.FileOutputStream(df).use { out ->
                // WEBP when available (Android 10+ compresses to webp natively via format arg)
                val fmt = if (android.os.Build.VERSION.SDK_INT >= 30)
                    Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.JPEG
                bmp.compress(fmt, 70, out)
            }
            diskBytes.addAndGet(df.length())
            if (diskBytes.get() > DISK_MAX_BYTES) diskDir?.let { trimDiskBlocking(it) }
        }
    }

    /** LRU-by-mtime eviction so the cache directory can only ever hold ~6MB. */
    private fun trimDiskBlocking(dir: File) {
        runCatching {
            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
            var total = files.sumOf { it.length }
            val target = (DISK_MAX_BYTES * DISK_TRIM_RATIO).toLong()
            for (f in files) {
                if (total <= target) break
                total -= f.length
                f.delete()
            }
            diskBytes.set(total.coerceAtLeast(0L))
        }
    }
}
