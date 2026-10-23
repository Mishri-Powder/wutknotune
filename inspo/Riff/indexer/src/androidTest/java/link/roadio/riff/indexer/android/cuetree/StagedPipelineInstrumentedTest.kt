package link.roadio.riff.indexer.android.cuetree

import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.cuetree.Child
import link.roadio.cuetree.Indexer
import link.roadio.cuetree.Life
import link.roadio.cuetree.MediaId
import link.roadio.cuetree.Node
import link.roadio.cuetree.NodeKind
import link.roadio.cuetree.OpenMedia
import link.roadio.cuetree.RootSpec
import link.roadio.cuetree.ScanOptions
import link.roadio.cuetree.Snapshot
import link.roadio.cuetree.SourceHandle
import link.roadio.cuetree.SourceInfo
import link.roadio.cuetree.SourceProvider
import link.roadio.riff.indexer.android.process.IndexerProgressReporter
import link.roadio.riff.indexer.core.CancellationSignal
import link.roadio.riff.indexer.core.analysis.AnalysisState
import link.roadio.riff.indexer.core.model.SourceMedia
import link.roadio.riff.indexer.core.pipeline.PipelineProgressRates
import link.roadio.riff.indexer.core.pipeline.PipelinePublicationClock
import link.roadio.riff.indexer.core.replaygain.ReplayGainSource
import link.roadio.riff.indexer.level1.Level1ReplayGainMetadata
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class StagedPipelineInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context
    private val tree get() =
        DocumentsContract.buildTreeDocumentUri(
            context.packageName + ".safprobe.documents",
            SafProbeDocumentsProvider.FIRST_ROOT,
        )

    @Before fun prepare() {
        context.contentResolver.getType(DocumentsContract.buildDocumentUriUsingTree(tree, SafProbeDocumentsProvider.FIRST_ROOT))
        SafProbeDocumentsProvider.reset()
    }

    @After fun cleanup() = SafProbeDocumentsProvider.reset()

    @Test fun realSafDescriptorIsSharedAndUnchangedSecondScanNeverOpensMedia() {
        val a = SafProbeDocumentsProvider.seed(SafProbeDocumentsProvider.FIRST_ROOT, "A", Document.MIME_TYPE_DIR)
        SafProbeDocumentsProvider.seed(SafProbeDocumentsProvider.FIRST_ROOT, "Z", Document.MIME_TYPE_DIR)
        val mp3 = context.assets.open("genres/explicit.mp3").use { it.readBytes() }
        SafProbeDocumentsProvider.seed(a, "first.mp3", "audio/mpeg", mp3)
        val saf = AndroidSafProvider(context.contentResolver)
        var listings = 0
        var opens = 0
        var closes = 0
        val provider =
            object : SourceProvider by saf {
                override fun children(directory: SourceHandle): List<Child> {
                    listings++
                    return saf.children(directory)
                }

                override fun open(media: SourceHandle): OpenMedia {
                    assertEquals("Every L0 listing must precede acquisition", 3, listings)
                    opens++
                    val input = saf.open(media)
                    assertEquals("first.mp3", (input as AndroidDescriptorMedia).displayName)
                    return object : AndroidDescriptorMedia {
                        private val descriptorInput = input as AndroidDescriptorMedia
                        override val displayName get() = descriptorInput.displayName
                        override val id get() = input.id
                        override val length get() = input.length
                        override val descriptor get() = descriptorInput.descriptor
                        override val offset get() = descriptorInput.offset

                        override fun read(
                            offset: Long,
                            count: Int,
                        ) = input.read(offset, count)

                        override fun close() {
                            closes++
                            input.close()
                        }
                    }
                }
            }
        val identity = AndroidCuetreeIdentity(context)
        val indexer = Indexer(identity, 100, listOf(provider))
        val roots = listOf(RootSpec("saf", tree.toString(), "Music"))
        val first = indexer.scan(roots, generation = 1, options = ScanOptions(pipelineDepth = 1))
        assertEquals(1, first.nodes.count { it.kind == NodeKind.MEDIA })
        assertEquals(1, opens)
        assertEquals(opens, closes)
        listings = 0
        val second = indexer.scan(roots, first, 2, ScanOptions(pipelineDepth = 1))
        assertEquals(1, opens)
        assertEquals(opens, closes)
        assertEquals(3, listings)
        assertTrue(second.nodes.filter { it.kind == NodeKind.MEDIA }.all { it.playable })
    }

    @Test fun completeSourceTagsAndGainSkipAllL2SourceAccessIncludingBooksAiTags() {
        for (book in listOf(false, true)) {
            val directory =
                File(context.cacheDir, "staged-l2-" + book).apply {
                    deleteRecursively()
                    mkdirs()
                }
            try {
                val rootId = MediaId(100)
                val id = MediaId(200)
                val attrs =
                    mapOf(
                        "riffPte" to listOf("0.1"),
                        "riffPteConfidence" to listOf("0.8"),
                        "riffArousal" to listOf("0.2"),
                        "riffArousalConfidence" to listOf("0.8"),
                        "riffValence" to listOf("0.3"),
                        "riffValenceConfidence" to listOf("0.8"),
                        "riffGenre" to listOf("Rock"),
                        "riffGenreConfidence" to listOf("0.8"),
                    )
                val snapshot =
                    Snapshot(
                        1,
                        AndroidCuetreeIdentity(context).scheme,
                        listOf(
                            Node(
                                NodeKind.DIRECTORY,
                                rootId,
                                source =
                                    SourceInfo(
                                        if (book) "Audiobooks" else "Music",
                                        provider = "saf",
                                        locator =
                                            if (book) {
                                                DocumentsContract
                                                    .buildTreeDocumentUri(
                                                        "fixture",
                                                        "fixture:Audiobooks",
                                                    ).toString()
                                            } else {
                                                tree.toString()
                                            },
                                    ),
                                life = Life(1),
                            ),
                            Node(NodeKind.MEDIA, id, rootId, SourceInfo("unopened.mp3", 1, 100), attributes = attrs, life = Life(1)),
                        ),
                        metadataRevision = AndroidCuetreeMetadataRevision.current,
                    )
                val store = AndroidCuetreeStore(directory, context)
                val statuses = mutableListOf<String>()
                val gain = Level1ReplayGainMetadata(0, null, null, null, ReplayGainSource.REPLAY_GAIN)
                AndroidCuetreeAnalysisPipeline(context, store, statuses::add).run(
                    snapshot,
                    mapOf((rootId to "unopened.mp3") to gain),
                    emptyMap(),
                    mapOf((rootId to "unopened.mp3") to SourceMedia("content://unavailable-must-never-open/source", "unopened.mp3")),
                    Available,
                    PipelinePublicationClock({ 0L }),
                    IndexerProgressReporter(PipelineProgressRates({ 0L })),
                    { false },
                )
                val record = requireNotNull(store.readAnalysis()).records.single()
                assertEquals(0.toShort(), record.trackGainQ8)
                if (book) {
                    assertNull(record.pte)
                    assertTrue(record.genres.isEmpty())
                } else {
                    assertEquals(AnalysisState.AVAILABLE, record.aiState)
                    assertEquals(0.1f, record.pte)
                    assertTrue(record.genres.isNotEmpty())
                }
                assertTrue("No decoder workset or progress for fully tagged files", statuses.isEmpty())
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    private object Available : CancellationSignal {
        override val cancelled = false

        override fun ensureAvailable() = Unit
    }
}
