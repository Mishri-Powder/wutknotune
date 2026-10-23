package link.roadio.riff.indexer.android.process

import link.roadio.cuetree.InvalidPublicationException
import java.io.IOException

internal class IncompatibleLibraryException : IOException("Incompatible Cuetree identity or metadata revision")

/** Only validated incompatibility/corruption authorizes deleting derived library files. */
internal fun libraryRequiresRecreation(error: Throwable): Boolean =
    error is InvalidPublicationException || error is IncompatibleLibraryException
