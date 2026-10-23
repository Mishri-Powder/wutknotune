package link.roadio.riff.indexer.android.cuetree

import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.platform.app.InstrumentationRegistry
import link.roadio.riff.storage.hash.nativeXxh3
import link.roadio.riff.storage.hash.toXxh3CanonicalBytes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class SafRootAdmissionInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context
    private val resolver get() = context.contentResolver

    private fun tree(id: String = SafProbeDocumentsProvider.FIRST_ROOT): Uri =
        DocumentsContract.buildTreeDocumentUri(context.packageName + ".safprobe.documents", id)

    @Before fun prepare() {
        resolver.getType(DocumentsContract.buildDocumentUriUsingTree(tree(), SafProbeDocumentsProvider.FIRST_ROOT))
        SafProbeDocumentsProvider.reset()
    }

    @After fun cleanup() = SafProbeDocumentsProvider.reset()

    private fun discover(writable: Boolean = true): AndroidSafProvider.Discovery =
        AndroidSafProvider(resolver).discover(listOf(AndroidSafProvider.Grant(tree(), writable)))

    @Test fun genuinelyEmptyWritableRootPublishesAndValidatesItsUuidAfterCleanup() {
        val result = discover()
        assertEquals(1, result.roots.size)
        assertEquals(4, UUID.fromString(result.roots.single().identity).version())
        assertTrue(result.listingBugFolders.isEmpty())
        assertTrue(result.unverifiedFolders.isEmpty())
        assertEquals(1, SafProbeDocumentsProvider.temporaryCreations)
        assertEquals(1, SafProbeDocumentsProvider.deletions)
        assertEquals(1, SafProbeDocumentsProvider.markerCreations)
        assertTrue(SafProbeDocumentsProvider.temporaryDocuments().isEmpty())
        assertEquals(result.roots.single().identity, AndroidSafRootMarker(resolver).resolve(tree(), false))
    }

    @Test fun hiddenOnlyRootNeedsNoDiagnostic() {
        SafProbeDocumentsProvider.seed(SafProbeDocumentsProvider.FIRST_ROOT, ".hidden", "application/octet-stream")
        assertEquals(1, discover().roots.size)
        assertEquals(0, SafProbeDocumentsProvider.temporaryCreations)
        assertEquals(0, SafProbeDocumentsProvider.deletions)
    }

    @Test fun falseEmptyListingSkipsRootWithoutChangingAnExistingMalformedMarker() {
        val marker = SafProbeDocumentsProvider.seed(SafProbeDocumentsProvider.FIRST_ROOT, ".riff", Document.MIME_TYPE_DIR)
        val original = byteArrayOf(1, 2, 3)
        val file = SafProbeDocumentsProvider.seed(marker, "riff_root_uuid", "application/octet-stream", original)
        SafProbeDocumentsProvider.hiddenRoots += SafProbeDocumentsProvider.FIRST_ROOT
        repeat(2) {
            val result = discover()
            assertTrue(result.roots.isEmpty())
            assertEquals(listOf("Music"), result.listingBugFolders)
            assertTrue(result.unverifiedFolders.isEmpty())
        }
        assertEquals(0, SafProbeDocumentsProvider.markerCreations)
        assertEquals(2, SafProbeDocumentsProvider.temporaryCreations)
        assertEquals(2, SafProbeDocumentsProvider.deletions)
        assertTrue(
            SafProbeDocumentsProvider.nodes
                .getValue(file)
                .file!!
                .readBytes()
                .contentEquals(original),
        )
        assertTrue(SafProbeDocumentsProvider.temporaryDocuments().isEmpty())
    }

    @Test fun failedRootDoesNotBlockAnotherGrantOrChangePersistedPermissions() {
        val before = resolver.persistedUriPermissions.map { it.uri to (it.isReadPermission to it.isWritePermission) }
        SafProbeDocumentsProvider.hiddenRoots += SafProbeDocumentsProvider.FIRST_ROOT
        SafProbeDocumentsProvider.seed(SafProbeDocumentsProvider.SECOND_ROOT, "audio.mp3", "audio/mpeg")
        val result =
            AndroidSafProvider(resolver).discover(
                listOf(AndroidSafProvider.Grant(tree(), true), AndroidSafProvider.Grant(tree(SafProbeDocumentsProvider.SECOND_ROOT), true)),
            )
        assertEquals(listOf("Books"), result.roots.map { it.label })
        assertEquals(listOf("Music"), result.listingBugFolders)
        assertEquals(before, resolver.persistedUriPermissions.map { it.uri to (it.isReadPermission to it.isWritePermission) })
    }

    @Test fun emptyReadOnlyRootIsUnverifiedWithoutCreatingAnything() {
        val result = discover(false)
        assertTrue(result.roots.isEmpty())
        assertTrue(result.listingBugFolders.isEmpty())
        assertEquals(listOf("Music"), result.unverifiedFolders)
        assertEquals(0, SafProbeDocumentsProvider.temporaryCreations)
        assertEquals(0, SafProbeDocumentsProvider.markerCreations)
    }

    @Test fun validExistingRiffIdentityWinsWithoutWrites() {
        val id = "32c6c7a3-343b-4df9-815f-dc8fb6b2c741"
        val marker = SafProbeDocumentsProvider.seed(SafProbeDocumentsProvider.FIRST_ROOT, ".riff", Document.MIME_TYPE_DIR)
        val bytes = id.toByteArray()
        SafProbeDocumentsProvider.seed(marker, "riff_root_uuid", "application/octet-stream", bytes)
        SafProbeDocumentsProvider.seed(marker, "riff_root_uuid.xxh3", "application/octet-stream", nativeXxh3(bytes).toXxh3CanonicalBytes())
        assertEquals(id, discover().roots.single().identity)
        assertEquals(0, SafProbeDocumentsProvider.temporaryCreations)
        assertEquals(0, SafProbeDocumentsProvider.markerCreations)
    }

    @Test fun readOnlyMediaIndexerFallbackRetainsIdentity() {
        val id = "32c6c7a3-343b-4df9-815f-dc8fb6b2c741"
        val marker = SafProbeDocumentsProvider.seed(SafProbeDocumentsProvider.FIRST_ROOT, ".thumbnails", Document.MIME_TYPE_DIR)
        SafProbeDocumentsProvider.seed(marker, ".database_uuid", "application/octet-stream", id.toByteArray())
        assertEquals(id, discover(false).roots.single().identity)
        assertEquals(0, SafProbeDocumentsProvider.temporaryCreations)
    }

    @Test fun failedCreationIsUnverifiedAndNeverCreatesRiffMarker() {
        SafProbeDocumentsProvider.failCreate = true
        val result = discover()
        assertEquals(listOf("Music"), result.unverifiedFolders)
        assertTrue(result.listingBugFolders.isEmpty())
        assertEquals(0, SafProbeDocumentsProvider.markerCreations)
        assertTrue(SafProbeDocumentsProvider.temporaryDocuments().isEmpty())
    }

    @Test fun failedOpeningStillDeletesOnlyTheTemporaryFile() {
        SafProbeDocumentsProvider.failOpen = true
        val result = discover()
        assertEquals(listOf("Music"), result.unverifiedFolders)
        assertEquals(1, SafProbeDocumentsProvider.deletions)
        assertEquals(0, SafProbeDocumentsProvider.markerCreations)
        assertTrue(SafProbeDocumentsProvider.temporaryDocuments().isEmpty())
    }

    @Test fun failedCleanupCannotAuthorizeMarkerPublication() {
        SafProbeDocumentsProvider.failDelete = true
        val result = discover()
        assertEquals(listOf("Music"), result.unverifiedFolders)
        assertEquals(0, SafProbeDocumentsProvider.markerCreations)
        assertEquals(1, SafProbeDocumentsProvider.temporaryDocuments().size)
    }

    @Test fun loadingAndErrorResponsesAreNotEmptyDirectoryProof() {
        SafProbeDocumentsProvider.loadingRoots += SafProbeDocumentsProvider.FIRST_ROOT
        assertTrue(discover().roots.isEmpty())
        SafProbeDocumentsProvider.loadingRoots.clear()
        SafProbeDocumentsProvider.errorRoots += SafProbeDocumentsProvider.FIRST_ROOT
        assertTrue(discover().roots.isEmpty())
        assertEquals(0, SafProbeDocumentsProvider.temporaryCreations)
        assertEquals(0, SafProbeDocumentsProvider.markerCreations)
    }
}
