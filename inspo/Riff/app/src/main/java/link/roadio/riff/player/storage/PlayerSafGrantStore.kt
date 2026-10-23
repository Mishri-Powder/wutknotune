package link.roadio.riff.player.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

data class PlayerSafGrant(
    val uri: String,
    val label: String,
)

/** Player-owned view and release operations for Android's persisted SAF grants. */
class PlayerSafGrantStore(
    context: Context,
) {
    private val resolver = context.applicationContext.contentResolver

    fun hasPersistedReadGrant(): Boolean = resolver.persistedUriPermissions.any { it.isReadPermission }

    fun hasPersistedWriteGrant(uriText: String): Boolean =
        resolver.persistedUriPermissions.any { it.uri.toString() == uriText && it.isReadPermission && it.isWritePermission }

    fun preview(uriText: String): PlayerSafGrant {
        val uri = Uri.parse(uriText)
        require(uri.isHierarchical) { "Media-folder URI is invalid" }
        return PlayerSafGrant(uriText, displayLabel(uri))
    }

    fun commitDraft(
        additions: Map<String, Int>,
        removals: Set<String>,
    ) {
        val before = resolver.persistedUriPermissions.associateBy { it.uri.toString() }
        require(removals.all { before[it]?.isReadPermission == true }) {
            "A removed media-folder grant is no longer present"
        }
        val acquired = mutableListOf<Pair<Uri, Int>>()
        try {
            additions.toSortedMap().forEach { (uriText, offeredFlags) ->
                val current = before[uriText]
                val currentFlags =
                    (if (current?.isReadPermission == true) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                        (if (current?.isWritePermission == true) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                val additionalFlags = flagsToPersist(offeredFlags, currentFlags)
                if (additionalFlags != 0) {
                    val uri = Uri.parse(uriText)
                    resolver.takePersistableUriPermission(uri, additionalFlags)
                    acquired += uri to additionalFlags
                }
            }
        } catch (error: Throwable) {
            acquired.asReversed().forEach { (uri, flags) ->
                runCatching { resolver.releasePersistableUriPermission(uri, flags) }
            }
            throw error
        }
        removals.sorted().forEach(::release)
    }

    fun list(): List<PlayerSafGrant> {
        val grants =
            resolver.persistedUriPermissions
                .asSequence()
                .filter { it.isReadPermission }
                .map { permission ->
                    val uri = permission.uri
                    PlayerSafGrant(uri.toString(), displayLabel(uri))
                }.toList()
        val duplicateLabels = grants.groupingBy { it.label }.eachCount()
        return grants
            .map { grant ->
                if (duplicateLabels[grant.label] == 1) {
                    grant
                } else {
                    grant.copy(label = "${grant.label} · ${Uri.parse(grant.uri).authority.orEmpty()}")
                }
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, PlayerSafGrant::label).thenBy(PlayerSafGrant::uri))
    }

    fun release(uriText: String) {
        val uri = Uri.parse(uriText)
        require(list().any { it.uri == uriText }) { "The media-folder grant is no longer present" }
        val permission = resolver.persistedUriPermissions.first { it.uri == uri }
        val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        resolver.releasePersistableUriPermission(uri, flags)
    }

    private fun displayLabel(tree: Uri): String {
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
        val displayName =
            treeId
                ?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
                ?.let(::queryDisplayName)
                ?.takeIf(String::isNotBlank)
        if (displayName != null) {
            val volume = treeId.substringBefore(':', "").takeIf(String::isNotBlank)
            return if (volume == null) displayName else "$displayName · $volume"
        }
        return treeId?.let(Uri::decode)?.takeIf(String::isNotBlank) ?: "Media folder"
    }

    private fun queryDisplayName(document: Uri): String? =
        runCatching {
            resolver
                .query(
                    document,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    cursor.getString(0)
                }
        }.getOrNull()
}

internal fun flagsToPersist(
    offered: Int,
    current: Int,
): Int {
    require(offered and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "SAF result lacks read access" }
    val supported = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    return offered and supported and current.inv()
}
