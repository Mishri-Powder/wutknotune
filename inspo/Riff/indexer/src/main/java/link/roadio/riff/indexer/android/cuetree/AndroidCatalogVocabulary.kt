package link.roadio.riff.indexer.android.cuetree

import link.roadio.cuetree.CatalogVocabulary
import link.roadio.riff.config.ProjectCatalogVocabulary

/** Build-selected language policy shared by acquisition and publication-only TTL reclassification. */
internal object AndroidCatalogVocabulary {
    val value =
        CatalogVocabulary(
            genericArtists = ProjectCatalogVocabulary.genericArtists,
            placeholderArtists = ProjectCatalogVocabulary.placeholderArtists,
            placeholderAlbumArtists = ProjectCatalogVocabulary.placeholderAlbumArtists,
            placeholderAlbums = ProjectCatalogVocabulary.placeholderAlbums,
            genericTitles = ProjectCatalogVocabulary.genericTitles,
            versionWords = ProjectCatalogVocabulary.versionWords,
            protectedWords = ProjectCatalogVocabulary.protectedWords,
            setMarkers = ProjectCatalogVocabulary.setMarkers,
            discWords = ProjectCatalogVocabulary.discWords,
            spokenGenreIds = ProjectCatalogVocabulary.spokenGenreIds,
            liveGenreIds = ProjectCatalogVocabulary.liveGenreIds,
            electronicGenreIds = ProjectCatalogVocabulary.electronicGenreIds,
        )
}
