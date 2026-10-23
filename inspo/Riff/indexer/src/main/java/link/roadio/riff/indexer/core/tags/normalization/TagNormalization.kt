package link.roadio.riff.indexer.core.tags.normalization

import link.roadio.cuegrain.repair.MojibakeRepairResult
import link.roadio.riff.indexer.core.tags.confusables.OneToOneConfusableMapper
import java.nio.charset.StandardCharsets

interface TagUnicodePlatform {
    val revision: String

    fun nfkc(value: String): String

    fun caseFold(value: String): String
}

fun interface TagXxh3 {
    fun hash(bytes: ByteArray): Long
}

enum class TagKind(
    internal val attributeDiscriminator: Byte?,
) {
    TITLE(2),
    ALBUM(5),
    ARTIST(6),
    ALBUM_ARTIST(7),
    GENRE(null),
}

data class NormalizedTagValue(
    val raw: String,
    val display: String,
    val canonical: String,
    val id: Long,
    val mojibakeRepaired: Boolean,
)

/**
 * RIFF-TAG identity pipeline.
 *
 * Display text preserves the chosen repaired spelling, while canonical text passes through NFKC, full case
 * folding, one-to-one confusable mapping, and a final normalization before hashing. Keep identity transforms
 * deterministic and versioned; locale-sensitive UI formatting belongs in Player projections.
 */
class TagNormalizer(
    private val unicode: TagUnicodePlatform,
    private val xxh3: TagXxh3,
    private val confusables: OneToOneConfusableMapper = OneToOneConfusableMapper.default,
) {
    val revision: String =
        "RIFF-TAG-V4/CED-" +
            "d127078cedef9c6642cbe592dacdd2292b50bb19/CUEGRAIN-0.1.0" +
            "/" +
            confusables.revision.unicodeVersion +
            "/" +
            confusables.revision.sourceSha256 +
            "/" +
            unicode.revision

    fun normalize(
        kind: TagKind,
        value: String,
    ): NormalizedTagValue =
        normalize(
            kind = kind,
            raw = value,
            repaired = MojibakeRepairResult(value, false),
        )

    internal fun normalize(
        kind: TagKind,
        raw: String,
        repaired: MojibakeRepairResult,
    ): NormalizedTagValue {
        val firstNfkc = unicode.nfkc(repaired.value)
        val firstFold = unicode.caseFold(firstNfkc)
        val mapped = confusables.map(firstFold)
        val canonical = unicode.nfkc(unicode.caseFold(mapped))
        val canonicalBytes = canonical.toByteArray(StandardCharsets.UTF_8)
        val hashBytes =
            kind.attributeDiscriminator?.let { discriminator ->
                ByteArray(canonicalBytes.size + 1).also { bytes ->
                    bytes[0] = discriminator
                    canonicalBytes.copyInto(bytes, destinationOffset = 1)
                }
            } ?: canonicalBytes
        return NormalizedTagValue(
            raw = raw,
            display = repaired.value,
            canonical = canonical,
            id = xxh3.hash(hashBytes),
            mojibakeRepaired = repaired.repaired,
        )
    }

    fun normalizeOrNull(
        kind: TagKind,
        value: String?,
    ): NormalizedTagValue? = value?.takeIf(String::isNotBlank)?.let { normalize(kind, it) }
}
