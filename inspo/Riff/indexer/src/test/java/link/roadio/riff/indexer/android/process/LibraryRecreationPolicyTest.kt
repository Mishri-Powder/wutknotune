package link.roadio.riff.indexer.android.process

import link.roadio.cuetree.InvalidPublicationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class LibraryRecreationPolicyTest {
    @Test fun onlyPersistedValidationAndMetadataIncompatibilityPermitRecreation() {
        assertTrue(libraryRequiresRecreation(InvalidPublicationException("checksum")))
        assertTrue(libraryRequiresRecreation(IncompatibleLibraryException()))
        assertFalse(libraryRequiresRecreation(IOException("volume unavailable")))
        assertFalse(libraryRequiresRecreation(IllegalArgumentException("APK configuration")))
        assertFalse(libraryRequiresRecreation(CancellationException("cancelled")))
    }
}
