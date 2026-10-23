package link.roadio.riff.indexer.level2

import link.roadio.riff.indexer.core.media.MediaId

data class AugmentationGenre(
    val id: Long,
    val genreId: Long,
    val confidence: Float,
)

/**
 * Sparse AI augmentation keyed by Media.ID.
 *
 * Every scalar travels with its own confidence; absence is represented by a null pair, never by a sentinel.
 * Genres are independent multi-label assertions and raw bytes are reserved for forward-compatible evidence.
 */
data class AugmentationRecord(
    val mediaId: MediaId,
    val pte: Float? = null,
    val pteConfidence: Float? = null,
    val arousal: Float? = null,
    val arousalConfidence: Float? = null,
    val valence: Float? = null,
    val valenceConfidence: Float? = null,
    val genres: List<AugmentationGenre> = emptyList(),
    val raw: List<Byte> = emptyList(),
) {
    fun validate(): AugmentationRecord {
        require((pte == null) == (pteConfidence == null)) { "PTE value and confidence must coexist" }
        require((arousal == null) == (arousalConfidence == null)) {
            "Arousal value and confidence must coexist"
        }
        require((valence == null) == (valenceConfidence == null)) {
            "Valence value and confidence must coexist"
        }
        pte?.let {
            require(it.isFinite() && it in -1.0f..1.0f) { "PTE must be finite and normalized" }
        }
        arousal?.let {
            require(it.isFinite() && it in -1.0f..1.0f) { "Arousal must be finite and normalized" }
        }
        valence?.let {
            require(it.isFinite() && it in -1.0f..1.0f) { "Valence must be finite and normalized" }
        }
        listOfNotNull(pteConfidence, arousalConfidence, valenceConfidence).forEach { confidence ->
            require(confidence.isFinite() && confidence in 0.0f..1.0f) {
                "Feature confidence must be finite and normalized"
            }
        }
        require(genres.map(AugmentationGenre::id).distinct().size == genres.size) {
            "Duplicate AI genre key"
        }
        genres.forEach { genre ->
            require(genre.id == genre.genreId) {
                "AI genre key must equal its stable genre ID"
            }
            require(genre.confidence.isFinite() && genre.confidence in 0.0f..1.0f) {
                "Genre confidence must be finite and normalized"
            }
        }
        return this
    }
}
