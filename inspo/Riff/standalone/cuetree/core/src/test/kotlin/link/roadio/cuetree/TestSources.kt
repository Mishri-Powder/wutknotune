// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import link.roadio.cuegrain.ByteArrayInput
import java.io.IOException

internal class MemoryProvider : SourceProvider {
    override val id = "opaque"

    data class Handle(
        val token: Int,
    ) : SourceHandle

    data class Item(
        val name: String,
        val parent: Handle?,
        var bytes: ByteArray?,
        var modified: Long = 1,
    )

    val rootHandle = Handle(971)
    val items = linkedMapOf(rootHandle to Item("root", null, null))
    val calls = mutableListOf<String>()
    var denied = mutableSetOf<Handle>()
    var opened = 0
    var closed = 0
    var onRead: (() -> Unit)? = null

    fun file(
        name: String,
        bytes: ByteArray = name.toByteArray(),
        parent: Handle = rootHandle,
    ): Handle {
        val handle = Handle(8191 - items.size * 37)
        items[handle] = Item(name, parent, bytes)
        return handle
    }

    fun directory(
        name: String,
        parent: Handle = rootHandle,
    ) = file(name, parent = parent).also { items.getValue(it).bytes = null }

    private fun get(handle: SourceHandle): Item {
        val h = handle as Handle
        if (h in denied) throw IOException("Unavailable")
        return items[h] ?: throw IOException("Missing")
    }

    override fun root(locator: String): SourceHandle {
        calls += "root:$locator"
        get(rootHandle)
        return rootHandle
    }

    override fun stat(handle: SourceHandle): SourceStat {
        calls += "stat:${(handle as Handle).token}"
        val item = get(handle)
        return SourceStat(item.bytes == null, Stamp(item.modified, item.bytes?.size?.toLong() ?: 0))
    }

    override fun children(directory: SourceHandle): List<Child> {
        calls += "children:${(directory as Handle).token}"
        get(directory)
        return items.filterValues { it.parent == directory }.map { Child(it.value.name, it.key) }
    }

    override fun open(media: SourceHandle): OpenMedia {
        calls += "open:${(media as Handle).token}"
        val item = get(media)
        val input = ByteArrayInput(item.bytes ?: throw IOException("Not a file"), item.name)
        opened++
        return object : OpenMedia {
            override val id = input.id
            override val length = input.length

            override fun read(
                offset: Long,
                count: Int,
            ): ByteArray {
                onRead?.invoke()
                return input.read(offset, count)
            }

            override fun close() {
                closed++
            }
        }
    }

    fun rootSpec() = RootSpec(id, "opaque-grant", "Music")
}

internal val emptyMetadata = MetadataReader { _, _, _ -> Observation(emptyMap(), emptyList(), emptyList()) }

internal fun metadataByName(values: Map<String, Attributes>) = MetadataReader { input, _, _ -> Observation(values[input.id].orEmpty(), emptyList(), emptyList()) }

internal class CountingIdentity : MediaIdGenerator {
    override val scheme = "test.collision"
    var highs = 0
    var lows = 0
    var fixedLow: Long? = null

    override fun high(
        input: link.roadio.cuegrain.MediaInput,
        span: Span?,
        cancellation: Cancellation,
    ): Long {
        cancellation.check()
        highs++
        return 123
    }

    override fun low(
        input: link.roadio.cuegrain.MediaInput,
        span: Span?,
        cancellation: Cancellation,
    ): Long {
        cancellation.check()
        lows++
        return fixedLow ?: input.read(0, 1)[0].toLong()
    }
}
