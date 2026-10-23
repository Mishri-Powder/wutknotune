package link.roadio.riff.player.artwork

import link.roadio.riff.ui.ArtworkOrigin

internal data class ArtworkChoice<T>(
    val value: T?,
    val origin: ArtworkOrigin,
)

/** Contract order: embedded, exactly one usable immediate-directory image, default. */
internal fun <T> chooseArtwork(
    embedded: T?,
    directoryImages: List<T>,
): ArtworkChoice<T> =
    when {
        embedded != null -> ArtworkChoice(embedded, ArtworkOrigin.EMBEDDED)
        directoryImages.size == 1 -> ArtworkChoice(directoryImages.single(), ArtworkOrigin.DIRECTORY)
        else -> ArtworkChoice(null, ArtworkOrigin.DEFAULT)
    }
