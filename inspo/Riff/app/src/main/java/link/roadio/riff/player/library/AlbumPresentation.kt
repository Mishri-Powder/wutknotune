package link.roadio.riff.player.library

import link.roadio.cuetree.Album
import link.roadio.cuetree.AlbumKind

// AI-INTENT: Decorate ready catalog kinds only; raw ALBM names still own search and order.
// Authority: https://wiki.271.pics/Riff/LATEST/player-catalog-membership#technical-singles-display
internal fun Album.playerDisplayTitle(): String =
    when (kind) {
        AlbumKind.SINGLES -> {
            "◇ Singles"
        }

        AlbumKind.ARTIST_SINGLES -> {
            val credit = attributes["albumArtist"].orEmpty().joinToString("; ")
            if (credit.isBlank()) title else "$credit ◇ Singles"
        }

        else -> {
            title
        }
    }
