// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException

data class MediaId(
    val hi: Long,
    val lo: Long? = null,
)

data class Span(
    val beginUs: Long? = null,
    val endUs: Long? = null,
) {
    init {
        require((beginUs == null || beginUs >= 0) && (endUs == null || endUs > (beginUs ?: 0)))
    }
}

enum class NodeKind { DIRECTORY, MEDIA, FRAGMENT, METADATA, FAILEDL1 }

enum class AlbumKind {
    ORDINARY,
    EXPLICIT,
    SINGLES,
    ARTIST_SINGLES,
    ;

    val isSingles: Boolean get() = this == SINGLES || this == ARTIST_SINGLES
}

data class RootSpec(
    val provider: String,
    val locator: String,
    val label: String = locator,
    val identity: String? = null,
    val initialStat: SourceStat? = null,
    val book: Boolean = false,
) {
    init {
        require(provider.isNotBlank() && locator.isNotBlank() && (identity == null || identity.isNotBlank()))
    }
}

data class Stamp(
    val modified: Long,
    val size: Long,
) {
    init {
        require(size >= 0)
    }
}

/** Generation is source availability or retained catalog-reference evidence; ACSS is independent. */
data class Life(
    val generation: Long,
    val flags: Int = 0,
) {
    init {
        require(generation >= 0 && flags and Flags.ACSS.inv() == 0)
    }

    val available: Boolean get() = flags and Flags.ACSS == 0

    internal fun observe(
        found: Boolean,
        generation: Long,
    ) = if (found) Life(generation) else copy(flags = flags or Flags.ACSS)

    internal fun referenced(
        used: Boolean,
        available: Boolean,
        current: Long,
    ) = Life(if (used) current else generation, if (available) 0 else Flags.ACSS)

    internal fun expired(
        currentGeneration: Long,
        ttl: Long,
    ) = !available && currentGeneration - generation >= ttl
}

object Flags {
    const val ACSS = 1
    const val RESL = 2
    const val SHRT = 4
    const val IDHI = 8
    const val FAILEDL2 = 1 shl 10
    const val KIND_SHIFT = 13
}

const val ROOT_IDENTITY_ATTRIBUTE = "sourceRootIdentity"
typealias Attributes = Map<String, List<String>>

/** Physical provider observations belong to a source, never to fragment metadata. */
data class SourceInfo(
    val name: String,
    val modified: Long? = null,
    val size: Long? = null,
    val provider: String? = null,
    val locator: String? = null,
) {
    init {
        require(size == null || size >= 0)
    }

    val stamp: Stamp? get() = if (modified != null && size != null) Stamp(modified, size) else null
}

data class Node(
    val kind: NodeKind,
    val id: MediaId,
    val parentId: MediaId? = null,
    val source: SourceInfo? = null,
    val span: Span? = null,
    val attributes: Attributes = emptyMap(),
    val life: Life,
    val identityFlags: Int =
        if ((kind == NodeKind.MEDIA || kind == NodeKind.FRAGMENT) && id.lo == null) Flags.SHRT else 0,
    val tables: List<TableObservation> = emptyList(),
    val sourceEvidence: Attributes = emptyMap(),
) {
    val folderId: Long? get() = id.hi.takeIf { kind == NodeKind.DIRECTORY }
    val mediaId: MediaId? get() =
        id.takeIf {
            kind == NodeKind.MEDIA ||
                kind == NodeKind.FRAGMENT ||
                (
                    kind == NodeKind.FAILEDL1 &&
                        (id.lo != null || identityFlags and Flags.SHRT != 0)
                )
        }
    val playable: Boolean get() = life.available && (kind == NodeKind.MEDIA || kind == NodeKind.FRAGMENT)
    val baseIdentity: Boolean get() = kind == NodeKind.MEDIA || (kind == NodeKind.FAILEDL1 && mediaId != null)
    val isRoot: Boolean get() = kind == NodeKind.DIRECTORY && parentId == null
    val name: String get() = source?.name ?: attributes.first("title") ?: attributes.first("fragment").orEmpty()
    val flags: Int get() = (kind.ordinal shl Flags.KIND_SHIFT) or identityFlags or life.flags or (if (kind == NodeKind.DIRECTORY || kind == NodeKind.METADATA || (kind == NodeKind.FAILEDL1 && mediaId == null)) Flags.IDHI else 0)
}

data class Album(
    val id: Long,
    val kind: AlbumKind,
    val title: String,
    val tracks: List<MediaId>,
    val sources: List<Int>,
    val attributes: Attributes,
    val life: Life,
) {
    val flags: Int get() = (kind.ordinal shl Flags.KIND_SHIFT) or life.flags or Flags.IDHI
}

data class Artist(
    val id: Long,
    val key: String,
    val name: String,
    val albums: List<Long>,
    val life: Life,
) {
    val attributes: Attributes get() = mapOf("artist" to listOf(name))
    val flags: Int get() = Flags.IDHI or life.flags
}

data class GenreRef(
    val dictionaryId: Long? = null,
    val text: String? = null,
) {
    init {
        require((dictionaryId == null) != (text == null))
        require(text == null || text.isNotBlank())
    }
}

data class Genre(
    val ref: GenreRef,
    val name: String,
    val albums: List<Long>,
    val life: Life,
    val id: Long = RandomIds.next(),
) {
    val attributes: Attributes get() = mapOf("genre" to listOf(name)) + (ref.dictionaryId?.let { mapOf("genreId" to listOf(it.toString())) } ?: emptyMap())
    val flags: Int get() = Flags.IDHI or life.flags
}

fun interface GenreResolver {
    fun resolve(text: String): GenreRef
}

fun interface IdAllocator {
    fun next(): Long
}

object RandomIds : IdAllocator {
    override fun next() = UUID.randomUUID().mostSignificantBits
}

data class Diagnostic(
    val code: String,
    val source: String,
    val message: String,
)

class Cancellation(
    private val cancelled: () -> Boolean = { false },
) {
    fun check() {
        if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException()
    }
}

/** Snapshot indexes identify occurrences only within this coherent publication. */
data class Snapshot(
    val generation: Long,
    val identityScheme: String,
    val nodes: List<Node>,
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val diagnostics: List<Diagnostic> = emptyList(),
    val attributeCatalog: List<AttributeRecord> = emptyList(),
    val metadataRevision: String? = null,
) {
    private val directories = nodes.indices.filter { nodes[it].kind == NodeKind.DIRECTORY }.associateBy { nodes[it].id }
    val children: Map<Int, List<Int>> by lazy { nodes.indices.groupBy { parentOf(it) ?: -1 } }

    fun parentOf(index: Int): Int? {
        val node = nodes[index]
        val parent = node.parentId ?: return null
        if (node.kind != NodeKind.FRAGMENT) return directories[parent]
        val bases = baseSources(node)
        return bases.firstOrNull { nodes[it].life.available } ?: bases.firstOrNull()
    }

    private val media = nodes.indices.filter { nodes[it].mediaId != null }.sortedWith { a, b -> IdOrder.compare(nodes[a].id, nodes[b].id) }

    fun childrenOf(index: Int): List<Int> = children[index].orEmpty()

    fun sources(id: MediaId): List<Int> =
        IdOrder
            .range(media.size, { nodes[media[it]].id }, id.hi)
            .map { media[it] }
            .filter { IdOrder.matches(nodes[it].id, nodes[it].flags, id) }

    fun baseSources(fragment: Node): List<Int> {
        require(fragment.kind == NodeKind.FRAGMENT)
        val id = requireNotNull(fragment.parentId)
        return sources(id).filter { nodes[it].baseIdentity }
    }

    fun available(id: MediaId): Boolean = sources(id).any { nodes[it].playable }

    /** Compare catalog facts and occurrence order, excluding publication marks and transient diagnostics. */
    fun hasSameContentAs(other: Snapshot): Boolean = comparableContent() == other.comparableContent()

    private fun comparableContent(): Snapshot =
        copy(
            generation = 0,
            nodes = nodes.map { it.copy(life = it.life.copy(generation = 0)) },
            albums = albums.map { it.copy(life = it.life.copy(generation = 0)) },
            artists = artists.map { it.copy(life = it.life.copy(generation = 0)) },
            genres = genres.map { it.copy(life = it.life.copy(generation = 0)) },
            attributeCatalog = attributeCatalog.map { it.copy(life = it.life.copy(generation = 0)) },
            diagnostics = emptyList(),
        )

    companion object {
        fun empty(scheme: String) = Snapshot(0, scheme, emptyList())
    }
}

internal fun canonical(value: String): String =
    Normalizer
        .normalize(value, Normalizer.Form.NFKC)
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

internal fun Attributes.first(key: String): String? = get(key)?.firstOrNull { it.isNotBlank() }

internal fun Attributes.components(key: String): Set<String> =
    get(key)
        .orEmpty()
        .map(::canonical)
        .filter { it.isNotEmpty() }
        .toSet()
