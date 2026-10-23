// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import com.dynatrace.hash4j.hashing.Hashing
import link.roadio.cuegrain.MediaInput
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.BasicFileAttributes

interface SourceHandle

interface OpenMedia :
    MediaInput,
    Closeable

data class SourceStat(
    val directory: Boolean,
    val stamp: Stamp,
    val mediaCandidate: Boolean? = null,
)

data class Child(
    val name: String,
    val handle: SourceHandle,
    val stat: SourceStat? = null,
)

/** A successful listing is complete; opaque handles live only for the current access attempt. */
interface SourceProvider {
    val id: String

    fun root(locator: String): SourceHandle

    fun stat(handle: SourceHandle): SourceStat

    fun children(directory: SourceHandle): List<Child>

    fun open(media: SourceHandle): OpenMedia

    fun bindingId(handle: SourceHandle): Long? = null
}

class PosixProvider : SourceProvider {
    override val id = "posix"

    private data class Handle(
        val boundary: Path,
        val path: Path,
    ) : SourceHandle

    override fun root(locator: String): SourceHandle {
        val path = Path.of(locator).toAbsolutePath().normalize()
        if (Files.isSymbolicLink(path)) throw IOException("Symbolic root is not supported")
        val real = path.toRealPath()
        return Handle(real, real)
    }

    private fun checked(handle: SourceHandle): Path {
        val h = handle as? Handle ?: throw IOException("Foreign provider handle")
        var p = h.path
        if (!p.startsWith(h.boundary)) throw IOException("Outside root")
        while (true) {
            if (Files.isSymbolicLink(p)) throw IOException("Symbolic links are not followed")
            if (p == h.boundary) break
            p = p.parent ?: throw IOException("Outside root")
        }
        if (!h.path.toRealPath().startsWith(h.boundary)) throw IOException("Outside root")
        return h.path
    }

    override fun stat(handle: SourceHandle): SourceStat {
        val a = Files.readAttributes(checked(handle), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        if (!a.isDirectory && !a.isRegularFile) throw IOException("Unsupported file kind")
        return SourceStat(a.isDirectory, Stamp(a.lastModifiedTime().toMillis(), a.size()))
    }

    override fun children(directory: SourceHandle): List<Child> {
        val h = directory as? Handle ?: throw IOException("Foreign provider handle")
        return Files.newDirectoryStream(checked(h)).use { stream ->
            stream.filter { !Files.isSymbolicLink(it) }.map { Child(it.fileName.toString(), Handle(h.boundary, it)) }.sortedBy { it.name }
        }
    }

    override fun bindingId(handle: SourceHandle): Long {
        val h = handle as? Handle ?: throw IOException("Foreign provider handle")
        return Hashing.xxh3_64().hashBytesToLong(
            h.path
                .toAbsolutePath()
                .normalize()
                .toString()
                .toByteArray(Charsets.UTF_8),
        )
    }

    override fun open(media: SourceHandle): OpenMedia {
        val path = checked(media)
        val channel = FileChannel.open(path, READ, NOFOLLOW_LINKS)
        return object : OpenMedia {
            override val id = path.fileName.toString()
            override val length = channel.size()

            override fun read(
                offset: Long,
                count: Int,
            ): ByteArray {
                if (offset < 0 || count < 0 || offset > length || count.toLong() > length - offset) throw IOException("Read outside input")
                val data = ByteArray(count)
                val target = ByteBuffer.wrap(data)
                while (target.hasRemaining()) {
                    if (channel.read(target, offset + target.position()) <= 0) throw IOException("Short read")
                }
                return data
            }

            override fun close() = channel.close()
        }
    }
}

data class ResolvedSource(
    val provider: SourceProvider,
    val handle: SourceHandle,
    val span: Span?,
) {
    fun open(): OpenMedia = provider.open(handle)
}

class SourceResolver(
    providers: List<SourceProvider>,
) {
    private val providers = providers.associateBy { it.id }.also { require(it.size == providers.size) }

    fun resolve(
        snapshot: Snapshot,
        index: Int,
        cancellation: Cancellation = Cancellation(),
    ): ResolvedSource {
        require(index in snapshot.nodes.indices)
        val node = snapshot.nodes[index]
        val candidates = if (node.kind == NodeKind.FRAGMENT) snapshot.baseSources(node).filter { snapshot.nodes[it].playable } else listOf(index)
        var last: IOException? = null
        for (candidate in candidates) {
            cancellation.check()
            try {
                return resolveBase(snapshot, candidate, node.span, cancellation)
            } catch (failure: IOException) {
                last = failure
            }
        }
        throw last ?: IOException("No available source for requested media")
    }

    private fun resolveBase(
        snapshot: Snapshot,
        index: Int,
        span: Span?,
        cancellation: Cancellation,
    ): ResolvedSource {
        val base = snapshot.nodes[index]
        require(base.kind == NodeKind.MEDIA)
        val names = mutableListOf<String>()
        var currentIndex = index
        var current = base
        val visited = mutableSetOf<Int>()
        while (!current.isRoot) {
            cancellation.check()
            names += current.name
            val parent = snapshot.parentOf(currentIndex) ?: throw IOException("Missing directory parent")
            check(visited.add(parent)) { "Parent cycle" }
            currentIndex = parent
            current = snapshot.nodes[parent]
        }
        val root = requireNotNull(current.source)
        val provider = providers[root.provider] ?: throw IOException("Provider is not configured: ${root.provider}")
        var handle = provider.root(requireNotNull(root.locator))
        for (name in names.asReversed()) {
            cancellation.check()
            handle = provider.children(handle).singleOrNull { it.name == name }?.handle
                ?: throw IOException("Missing or ambiguous component: $name")
        }
        if (provider.stat(handle).directory) throw IOException("Playback source became a directory")
        return ResolvedSource(provider, handle, span)
    }
}
