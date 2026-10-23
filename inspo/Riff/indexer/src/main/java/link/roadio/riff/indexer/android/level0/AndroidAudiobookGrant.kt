package link.roadio.riff.indexer.android.level0

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/**
 * Matches Android's standard Audiobooks directory from a SAF tree identity.
 *
 * Display labels are provider/UI data. The decoded tree document ID is the stable source here: the first
 * relative component after the provider's volume/root identifier must equal Android's fixed directory name.
 */
fun isStandardAudiobooksTree(treeUri: Uri): Boolean {
    val documentId =
        runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrNull()
            ?: return false
    val rootSeparator = documentId.indexOf(':')
    if (rootSeparator < 0 || rootSeparator == documentId.lastIndex) return false
    val firstRelativeComponent =
        documentId
            .substring(rootSeparator + 1)
            .substringBefore('/')
    return firstRelativeComponent == Environment.DIRECTORY_AUDIOBOOKS
}

fun isStandardAudiobooksTree(sourceKey: String): Boolean =
    runCatching { isStandardAudiobooksTree(Uri.parse(sourceKey)) }.getOrDefault(false)
