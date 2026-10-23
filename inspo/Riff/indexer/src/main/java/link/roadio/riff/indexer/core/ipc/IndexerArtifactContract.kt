package link.roadio.riff.indexer.core.ipc

const val INDEXER_STATUS_MAX_CHARS = 120

enum class IndexerArtifactKind { GNRE, MDRG, CUMD, CUAL, CUAR, CUGR }

data class IndexerArtifactMetadata(
    val kind: IndexerArtifactKind,
    val volumeId: String,
    /** Generation belongs to this artifact kind; cross-kind equality has no meaning. */
    val generation: Long,
    val length: Long,
    val flatBufferSchemaVersion: Int,
    val semanticVersion: Int,
    val xxh3: Long,
) {
    init {
        require(volumeId.isNotBlank()) { "volumeId must not be blank" }
        require(length >= 0) { "length must not be negative" }
        require(flatBufferSchemaVersion > 0) { "FlatBuffer schema version must be positive" }
        require(semanticVersion > 0) { "semantic version must be positive" }
    }
}
