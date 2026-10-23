package link.roadio.cuetree

internal val testVocabulary =
    CatalogVocabulary(
        genericArtists = setOf("various artists", "various", "va", "v.a.", "v.a", "разные исполнители", "unknown", "unknown artist", "<unknown>"),
        placeholderArtists = setOf("artist"),
        placeholderAlbumArtists = setOf("artist", "album artist"),
        placeholderAlbums = setOf("album", "unknown album", "<unknown>", "unknown", "неизвестный альбом"),
        genericTitles = setOf("track", "трек", "untitled", "unknown", "song", "audio", "lesson", "chapter", "песня", "композиция", "глава"),
        versionWords = setOf("remix", "remixes", "mix", "mixx", "edit", "version", "instrumental", "dub", "acoustic", "extended"),
        protectedWords = setOf("part", "pt", "volume", "vol", "chapter", "disc", "cd", "movement", "act"),
        setMarkers = setOf("dj set", "dj mix", "continuous mix", "mixset"),
        discWords = setOf("cd", "disc", "disk", "диск"),
        spokenGenreIds = setOf(1),
        liveGenreIds = setOf(2),
        electronicGenreIds = setOf(3),
    )
internal val testGenreResolver =
    GenreResolver { text ->
        when (canonical(text)) {
            "audiobook", "аудиокнига", "speech", "spoken word" -> GenreRef(dictionaryId = 1)
            "live" -> GenreRef(dictionaryId = 2)
            "electronic" -> GenreRef(dictionaryId = 3)
            else -> GenreRef(text = canonical(text))
        }
    }
