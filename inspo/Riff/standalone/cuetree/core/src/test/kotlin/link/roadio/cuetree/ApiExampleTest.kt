// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import link.roadio.cuegrain.MediaInput
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class ApiExampleTest {
    @Test fun documentedScanStoreAndDirectFolderAccessRunOnPosix() {
        val base = Files.createTempDirectory("cuetree-example")
        try {
            val music = Files.createDirectory(base.resolve("music"))
            Files.write(music.resolve("track.wav"), byteArrayOf(1, 2, 3))
            val generator = MockMediaID()
            val store = SnapshotStore(base.resolve("catalog"))
            val previous = store.read() ?: Snapshot.empty(generator.scheme)
            val result =
                Indexer(generator, 1000).scan(
                    roots = listOf(RootSpec("posix", music.toString(), "Music")),
                    previous = previous,
                    generation = maxOf(System.currentTimeMillis(), Math.addExact(previous.generation, 1)),
                )
            store.write(result)
            val flat = requireNotNull(store.readFlat())
            val mdia = flat.mdia()
            val children = flat.childrenOf(flat.roots().first())
            assertEquals(1, children.size)
            val child = requireNotNull(mdia.nodes(children.first()))
            assertEquals("track.wav", child.source()!!.name())
            assertEquals(0, child.flags() and Flags.ACSS)
            val reloaded = requireNotNull(store.read())
            val again = Indexer(generator, 1000).scan(listOf(RootSpec("posix", music.toString(), "Music")), reloaded, reloaded.generation + 1)
            assertEquals(result.albums.single().id, again.albums.single().id)
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test fun failingNewCollisionTailCannotAliasExistingShortReference() {
        val p = MemoryProvider()
        p.file("original.mp3", byteArrayOf(1))
        val generator =
            object : MediaIdGenerator {
                override val scheme = "test.tail-failure"

                override fun high(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ) = 19L

                override fun low(
                    input: MediaInput,
                    span: Span?,
                    cancellation: Cancellation,
                ): Long {
                    if (input.id == "new.mp3") throw IOException("Tail temporarily inaccessible")
                    return 7
                }
            }
        val indexer = Indexer(generator, 1000, listOf(p), emptyMetadata)
        val first = indexer.scan(listOf(p.rootSpec()), generation = 1)
        p.file("new.mp3", byteArrayOf(2))
        val result = indexer.scan(listOf(p.rootSpec()), first, 2)
        assertEquals(listOf("original.mp3"), result.nodes.filter { it.kind == NodeKind.MEDIA }.map { it.name })
        assertEquals(listOf(MediaId(19, 7)), result.albums.single().tracks)
        assertTrue(result.diagnostics.any { it.code == "IDENTITY_ACCESS" })
    }
}
