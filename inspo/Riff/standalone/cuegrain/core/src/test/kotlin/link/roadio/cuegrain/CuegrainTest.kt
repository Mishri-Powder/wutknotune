// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

class CuegrainTest {
    private fun cue(
        text: String,
        options: ParseOptions = ParseOptions(),
    ) = Cuegrain(options).read(ByteArrayInput(text.toByteArray(), "album.cue"), "cue")

    private val basic = "FILE \"album.flac\" WAVE\n TRACK 01 AUDIO\n TITLE \"One\"\n INDEX 01 00:00:00\n TRACK 02 AUDIO\n INDEX 01 01:02:03\n"

    @Test fun cueFrameTimeIsExactAndLastEndIsUnknown() {
        val r = cue(basic)
        assertEquals("ok", r.status)
        val e = r.sources.single().entries
        assertEquals(MediaTime(4653, 75), e[1].start!!.time)
        assertEquals(62040, e[0].end!!.time.milliseconds())
        assertNull(e[1].end)
    }

    @Test fun noncompliantEacKeepsEachIndexFile() {
        val r =
            cue(
                "FILE \"one.wav\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nTRACK 02 AUDIO\nINDEX 00 03:00:00\nFILE \"two.wav\" WAVE\nINDEX 01 00:00:00",
            )
        val e = r.sources.single().entries[1]
        assertNotEquals(e.indexes[0].position.file, e.indexes[1].position.file)
        assertEquals(e.start, e.indexes[1].position)
        assertTrue(r.diagnostics.any { it.code == "CUE_CROSS_FILE_INDEX" })
        assertEquals("ok", r.status)
    }

    @Test fun strictReportsEacAsNonconformant() {
        val r = cue("file \"one.wav\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00", ParseOptions(mode = ParseMode.STRICT))
        assertEquals("partial", r.status)
    }

    @Test fun separateFilesAreNotOneGlobalTimeline() {
        val r =
            cue(
                "TITLE \"Album\"\nFILE \"a.flac\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nFILE \"b.flac\" WAVE\nTRACK 02 AUDIO\nINDEX 01 00:00:00",
            )
        assertEquals(
            2,
            r.sources
                .single()
                .files.size,
        )
        assertTrue(
            r.sources
                .single()
                .entries
                .all { it.end == null && it.start!!.time.ticks == 0L },
        )
        assertEquals(
            "Album",
            r.sources
                .single()
                .tags
                .single()
                .value,
        )
    }

    @Test fun pregapAndHiddenAudioStayDistinct() {
        val r = cue("FILE \"x.wav\" WAVE\nTRACK 01 AUDIO\nPREGAP 00:02:00\nINDEX 00 00:00:00\nINDEX 01 00:05:00\nPOSTGAP 00:01:00")
        val e =
            r.sources
                .single()
                .entries
                .single()
        assertEquals(150, e.pregap!!.ticks)
        assertEquals(375, e.start!!.time.ticks)
        assertEquals(
            0,
            e.indexes
                .first()
                .position.time.ticks,
        )
        assertEquals(75, e.postgap!!.ticks)
    }

    @Test fun namesPathsAndRepeatedUnknownFieldsArePreserved() {
        val r =
            cue(
                "REM LABEL A\nREM LABEL B\nFILE \"C:\\Music\\track.flac\" WAVE\nTRACK 01 AUDIO\nTITLE Theme Of \"Rome\"\nGENRE Tango\nINDEX 01 00:00:00",
            )
        assertEquals(
            listOf("A", "B"),
            r.sources
                .single()
                .tags
                .map { it.value },
        )
        assertEquals(
            "C:\\Music\\track.flac",
            r.sources
                .single()
                .files
                .single()
                .path,
        )
        assertEquals(
            "Theme Of \"Rome\"",
            r.sources
                .single()
                .entries
                .single()
                .tags
                .first()
                .value,
        )
        assertTrue(r.diagnostics.any { it.code == "CUE_EXTENSION" })
    }

    @Test fun invalidTimeDoesNotInventStart() {
        val r = cue("FILE \"x.wav\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:60:99")
        assertNull(
            r.sources
                .single()
                .entries
                .single()
                .start,
        )
        assertEquals("partial", r.status)
    }

    @Test fun legacyCueRequiresDetectorOrExplicitCharset() {
        val text = "FILE \"x.wav\" WAVE\nTRACK 01 AUDIO\nTITLE \"Привет мир\"\nINDEX 01 00:00:00"
        val bytes = text.toByteArray(charset("windows-1251"))
        val raw = Cuegrain().read(ByteArrayInput(bytes), "cue")
        assertEquals("ENCODING_REQUIRED", raw.diagnostics.single().code)
        val explicit = Cuegrain(ParseOptions(charset = "windows-1251")).read(ByteArrayInput(bytes), "cue")
        assertEquals(
            "Привет мир",
            explicit.sources
                .single()
                .entries
                .single()
                .tags
                .single()
                .value,
        )
    }

    @Test fun utf16BomWorksWithoutCed() {
        val bytes = basic.toByteArray(Charsets.UTF_16)
        assertEquals(
            2,
            Cuegrain()
                .read(ByteArrayInput(bytes), "cue")
                .sources
                .single()
                .entries.size,
        )
    }

    @Test fun gainZerosPeaksAndIndependentR128Survive() {
        val tags =
            listOf(
                Tag("REPLAYGAIN_TRACK_GAIN", "0 dB"),
                Tag("REPLAYGAIN_TRACK_PEAK", "1.4"),
                Tag("REPLAYGAIN_TRACK_GAIN", "-2.50 dB"),
                Tag("R128_TRACK_GAIN", "-256"),
                Tag("R128_ALBUM_GAIN", "32768"),
                Tag("REPLAYGAIN_ALBUM_GAIN", "NaN"),
                Tag("REPLAYGAIN_ALBUM_PEAK", "-1"),
            )
        val gains = Metadata.gains(tags)
        assertEquals(4, gains.size)
        assertEquals(0.0, gains[0].value, 0.0)
        assertEquals(1.4, gains[1].value, 0.0)
        assertEquals(-1.0, gains[3].value, 0.0)
        assertEquals(-23.0, gains[3].reference!!, 0.0)
    }

    @Test fun riffFeaturesAreTypedAtCueAndTrackScope() {
        val report =
            cue(
                """
                REM RIFF_VALENCE .25
                REM RIFF_VALENCE_CONFIDENCE 9e-1
                REM RIFF_GENRE Electronic
                REM RIFF_GENRE_CONFIDENCE 0.8
                REM RIFF_GENRE Ambient
                FILE "x.flac" WAVE
                TRACK 01 AUDIO
                REM RIFF_AROUSAL -0.125
                REM RIFF_PTE 1.5
                REM RIFF_PTE_CONFIDENCE 0
                INDEX 01 00:00:00
                """.trimIndent(),
            )
        assertEquals("ok", report.status)
        val source = report.sources.single()
        assertEquals(RiffMetric(0.25f, 0.9f), source.riffFeatures.valence)
        assertEquals(
            listOf(RiffGenre("Electronic", 0.8f), RiffGenre("Ambient", null)),
            source.riffFeatures.genres,
        )
        assertEquals(
            RiffMetric(-0.125f, null),
            source.entries
                .single()
                .riffFeatures.arousal,
        )
        assertEquals(
            RiffMetric(1.5f, 0f),
            source.entries
                .single()
                .riffFeatures.pte,
        )
    }

    @Test fun riffFeaturePairingRejectsGuessingButPreservesRawTags() {
        val report =
            cue(
                """
                REM RIFF_AROUSAL NaN
                REM RIFF_AROUSAL 0.4
                REM RIFF_AROUSAL 0.7
                REM RIFF_GENRE Jazz
                REM COMMENT separates-the-pair
                REM RIFF_GENRE_CONFIDENCE 0.6
                FILE "x.flac" WAVE
                TRACK 01 AUDIO
                INDEX 01 00:00:00
                """.trimIndent(),
            )
        val source = report.sources.single()
        assertEquals(RiffMetric(0.4f, null), source.riffFeatures.arousal)
        assertEquals(listOf(RiffGenre("Jazz", null)), source.riffFeatures.genres)
        assertEquals(6, source.tags.size)
        assertEquals(
            setOf("RIFF_FEATURE_FLOAT_INVALID", "RIFF_FEATURE_DUPLICATE", "RIFF_FEATURE_CONFIDENCE_ORPHAN"),
            report.diagnostics.map { it.code }.toSet(),
        )
    }

    @Test fun riffFeaturesUseTheSameTypedViewForVorbisComments() {
        val commentPacket =
            comments(
                "RIFF_VALENCE=0.75",
                "RIFF_VALENCE_CONFIDENCE=0.5",
                "RIFF_GENRE=Rock",
                "RIFF_GENRE_CONFIDENCE=0.7",
                "RIFF_GENRE=Indie",
            )
        val identification = byteArrayOf(1) + ascii("vorbis") + ByteArray(23)
        val metadata = byteArrayOf(3) + ascii("vorbis") + commentPacket
        val report = Cuegrain().read(ByteArrayInput(page(identification, 0, 2) + page(metadata, 1, 4), "features.ogg"))
        val features = report.sources.first { it.kind == "vorbis" }.riffFeatures
        assertEquals(RiffMetric(0.75f, 0.5f), features.valence)
        assertEquals(listOf(RiffGenre("Rock", 0.7f), RiffGenre("Indie")), features.genres)
    }

    @Test fun riffFeaturesAreReadFromId3UserText() {
        val frames =
            frame("TXXX", byteArrayOf(3) + ascii("RIFF_AROUSAL\u00000.6")) +
                frame("TXXX", byteArrayOf(3) + ascii("RIFF_AROUSAL_CONFIDENCE\u00000.4"))
        val features =
            Cuegrain()
                .read(ByteArrayInput(id3(frames), "features.mp3"))
                .sources
                .single()
                .riffFeatures
        assertEquals(RiffMetric(0.6f, 0.4f), features.arousal)
    }

    @Test fun riffFeaturesAreReadFromImplicitMp4FreeformText() {
        fun atom(
            type: String,
            data: ByteArray,
        ) = u32(8L + data.size) + ascii(type) + data

        fun freeform(
            name: String,
            value: String,
        ) = atom(
            "----",
            atom("mean", ByteArray(4) + ascii("com.apple.iTunes")) +
                atom("name", ByteArray(4) + ascii(name)) +
                atom("data", ByteArray(8) + ascii(value)),
        )

        val items = freeform("RIFF_PTE", "1.25") + freeform("RIFF_PTE_CONFIDENCE", "0.5")
        val bytes = atom("moov", atom("udta", atom("meta", ByteArray(4) + atom("ilst", items))))
        val features =
            Cuegrain()
                .read(ByteArrayInput(bytes, "features.m4a"))
                .sources
                .single()
                .riffFeatures
        assertEquals(RiffMetric(1.25f, 0.5f), features.pte)
    }

    @Test fun riffFeaturesAreJsonNumbersWithNullableConfidence() {
        val report = cue("REM RIFF_PTE 2.5\nREM RIFF_GENRE Ambient\nFILE \"x.wav\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00")
        val json = Json.encode(Json.report(report))
        assertTrue(json.contains("\"pte\":{\"value\":2.5,\"confidence\":null}"))
        assertTrue(json.contains("\"genres\":[{\"value\":\"Ambient\",\"confidence\":null}]"))
    }

    @Test fun existingChapterFixturesRemainReadableAndBothMp4SourcesSurvive() {
        for (name in listOf("id3-v23.mp3", "id3-v24.mp3", "nero.m4a", "quicktime.m4a", "both.m4b")) {
            val bytes = javaClass.getResourceAsStream("/chapters/$name")!!.use { it.readBytes() }
            val r = Cuegrain().read(ByteArrayInput(bytes, name))
            assertEquals("$name: ${r.diagnostics}", "ok", r.status)
            assertTrue(name, r.sources.any { it.entries.isNotEmpty() })
            for (source in r.sources.filter { it.entries.isNotEmpty() }) {
                assertEquals(name, listOf(0L, 333L, 1234L), source.entries.map { it.start!!.time.milliseconds() })
                if (source.kind != "mp4.nero") {
                    assertEquals(name, listOf(333L, 1234L, 2100L), source.entries.map { it.end!!.time.milliseconds() })
                }
            }
            if (name ==
                "both.m4b"
            ) {
                assertEquals(
                    setOf("mp4.quicktime", "mp4.nero"),
                    r.sources
                        .filter { it.entries.isNotEmpty() }
                        .map { it.kind }
                        .toSet(),
                )
            }
        }
    }

    @Test fun eachUtf16ValueConsumesItsOwnBom() {
        val first = "Artist One".toByteArray(Charsets.UTF_16LE)
        val second = "Artist Two".toByteArray(Charsets.UTF_16LE)
        val bom = byteArrayOf(0xff.toByte(), 0xfe.toByte())
        for (secondBom in listOf(bom, byteArrayOf())) {
            val bytes = id3(frame("TPE1", byteArrayOf(1) + bom + first + byteArrayOf(0, 0) + secondBom + second, 4), 4)
            val report = Cuegrain().read(ByteArrayInput(bytes, "artists.mp3"))
            assertEquals("ok", report.status)
            assertEquals(listOf("Artist One", "Artist Two"), report.sources.single().metadata["artist"])
        }
    }

    @Test fun malformedV23CueStopsAtTerminatorAndDiagnosesTrailingBytes() {
        val text = "FILE \"x.mp3\" MP3\nTRACK 01 AUDIO\nINDEX 01 00:00:00\n"
        val payload = byteArrayOf(3) + ascii("CUESHEET\u0000$text\u0000") + frame("TXXX", byteArrayOf(3) + ascii("album artist\u0000Artist")) + ByteArray(2048) + byteArrayOf(0xff.toByte())
        val report = Cuegrain().read(ByteArrayInput(id3(frame("TXXX", payload)), "broken.mp3"))
        assertEquals("ok", report.status)
        assertEquals(1, report.sources.count { it.kind == "cue" })
        assertEquals(
            1,
            report.sources
                .first { it.kind == "cue" }
                .entries.size,
        )
        assertEquals(listOf("ID3_TEXT_TRAILING_DATA"), report.diagnostics.map { it.code })
    }

    @Test fun embeddedCueAndId3ChaptersCoexist() {
        val cue = "FILE \"x.mp3\" MP3\nTRACK 01 AUDIO\nTITLE \"Cue title\"\nINDEX 01 00:00:00"
        val chapter =
            frame(
                "CHAP",
                ascii(
                    "ch\u0000",
                ) + u32(1234) + u32(0xffffffffL) + u32(0xffffffffL) + u32(0xffffffffL) + frame("TIT2", byteArrayOf(3) + ascii("Chapter")),
            )
        val data = id3(frame("TXXX", byteArrayOf(3) + ascii("CUESHEET\u0000$cue")) + chapter)
        val r = Cuegrain().read(ByteArrayInput(data, "x.mp3"))
        assertEquals(setOf("cue", "id3"), r.sources.map { it.kind }.toSet())
        assertEquals(
            1234,
            r.sources
                .first { it.kind == "id3" }
                .entries
                .single()
                .start!!
                .time.ticks,
        )
    }

    @Test fun id3V24FrameUnsynchronizationIsDecoded() {
        val title = frame("TIT2", byteArrayOf(0, 0xff.toByte(), 0, 0xe1.toByte()), 4, 2)
        val r = Cuegrain().read(ByteArrayInput(id3(title, 4), "x.mp3"))
        assertEquals(
            "ÿá",
            r.sources
                .single()
                .tags
                .single()
                .value,
        )
    }

    @Test fun flacCueOffsetsAreSamplesAndLeadoutIsNotATrack() {
        val rate = 44100L
        val packed = (rate shl 44) or (1L shl 41) or (15L shl 36) or 88200L
        val streamInfo = ByteArray(10) + u64(packed) + ByteArray(16)

        fun track(
            offset: Long,
            number: Int,
            leadout: Boolean = false,
        ): ByteArray =
            u64(offset) + byteArrayOf(number.toByte()) + ByteArray(12) + byteArrayOf(0) + ByteArray(13) +
                byteArrayOf(if (leadout) 0 else 1) + if (leadout) byteArrayOf() else u64(0) + byteArrayOf(1) + ByteArray(3)
        val cues =
            ByteArray(128) + u64(88200) + byteArrayOf(0x80.toByte()) + ByteArray(258) + byteArrayOf(3) +
                track(0, 1) + track(44100, 2) + track(88200, 170, true)
        val bytes = ascii("fLaC") + block(0, streamInfo) + block(0x85, cues)
        val r = Cuegrain().read(ByteArrayInput(bytes, "album.flac"))
        assertEquals(r.diagnostics.toString(), "ok", r.status)
        val e = r.sources.first { it.kind == "flac.cuesheet" }.entries
        assertEquals(2, e.size)
        assertEquals(MediaTime(44100, 44100), e[1].start!!.time)
        assertEquals("lead-out", e[1].endOrigin)
        assertEquals(2000, e[1].end!!.time.milliseconds())
    }

    @Test fun opusHeaderGainAndR128AreNotAddedOrConfused() {
        val head = ascii("OpusHead") + byteArrayOf(1, 2) + le16(312) + le32(48000) + le16(-256) + byteArrayOf(0)
        val comments = ascii("OpusTags") + comments("R128_TRACK_GAIN=512", "CHAPTER001=00:00:01.234", "CHAPTER001NAME=First")
        val bytes = page(head, 0, 2) + page(comments, 1, 4)
        val r = Cuegrain().read(ByteArrayInput(bytes, "x.opus"))
        assertEquals(r.diagnostics.toString(), "ok", r.status)
        assertEquals(
            -1.0,
            r.sources
                .first { it.kind == "opus" }
                .gains
                .single()
                .value,
            0.0,
        )
        val vorbis = r.sources.first { it.kind == "vorbis" }
        assertEquals(2.0, vorbis.gains.single().value, 0.0)
        assertEquals(
            MediaTime(1234, 1000),
            vorbis.entries
                .single()
                .start!!
                .time,
        )
    }

    @Test fun cursorViewSharesInputAndCannotReadIntoTheNextFrame() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val cursor = Cursor(bytes)
        val frame = cursor.view(2)
        assertSame(bytes, frame.data)
        assertEquals(2, cursor.position)
        assertArrayEquals(byteArrayOf(1, 2), frame.bytes(2))
        assertThrows(IllegalArgumentException::class.java) { frame.bytes(1) }
        assertArrayEquals(byteArrayOf(3, 4), cursor.bytes(2))
    }

    @Test fun boundedReaderDoesNotAllocateDeclaredHugeLength() {
        val bytes = ascii("ID3") + byteArrayOf(4, 0, 0, 127, 127, 127, 127)
        val r = Cuegrain().read(ByteArrayInput(bytes))
        assertEquals("error", r.status)
        assertTrue(r.diagnostics.any { it.code == "LIMIT_BYTES" })
    }

    @Test fun cancellationPropagatesInsteadOfBecomingAParseError() {
        try {
            cue(basic, ParseOptions(cancelled = { true }))
            fail("Expected cancellation")
        } catch (_: CancellationException) {
        }
    }

    @Test fun randomMalformedInputsReturnBoundedDiagnostics() {
        val random = java.util.Random(42)
        repeat(400) {
            val bytes = ByteArray(random.nextInt(512))
            random.nextBytes(bytes)
            if (bytes.size >= 4) ascii(listOf("ID3\u0004", "fLaC", "OggS")[it % 3]).copyInto(bytes, 0)
            val r =
                Cuegrain(
                    ParseOptions(limits = Limits(metadataBytes = 4096, totalReadBytes = 8192, elements = 1000)),
                ).read(ByteArrayInput(bytes))
            assertTrue(r.diagnostics.size <= 1000)
            Json.encode(Json.report(r))
        }
    }

    @Test fun nonzeroId3PaddingDoesNotDiscardEarlierTags() {
        val bytes = id3(frame("TIT2", byteArrayOf(0) + ascii("Title")) + byteArrayOf(0) + ascii("stale padding") + ByteArray(32))
        val report = Cuegrain().read(ByteArrayInput(bytes, "padding.mp3"))
        assertEquals("ok", report.status)
        assertEquals(listOf("Title"), report.sources.single().metadata["title"])
        assertEquals("ID3_PADDING_NONZERO", report.diagnostics.single().code)
    }

    @Test fun mp4ImplicitFreeformBinaryIsNotDecodedAsUtf8() {
        fun atom(
            type: String,
            data: ByteArray,
        ) = u32(8L + data.size) + ascii(type) + data
        val binary = ascii("vers") + byteArrayOf(0, 0, 0, 1, 0xff.toByte())
        val item =
            atom(
                "----",
                atom("mean", ByteArray(4) + ascii("com.apple.iTunes")) +
                    atom("name", ByteArray(4) + ascii("Encoding Params")) + atom("data", ByteArray(8) + binary),
            )
        val bytes = atom("moov", atom("udta", atom("meta", ByteArray(4) + atom("ilst", item))))
        val report = Cuegrain().read(ByteArrayInput(bytes, "binary.m4a"))
        assertEquals("ok", report.status)
        assertEquals(
            "7665727300000001ff",
            report.sources
                .single()
                .tags
                .single()
                .value,
        )
    }

    @Test fun performerIsAnArtistAliasOnlyInsideCue() {
        val tags = listOf(Tag("ARTIST", "Artist"), Tag("PERFORMER", "Performer"))
        assertEquals(listOf("Artist"), Metadata.normalized(tags)["artist"])
        assertEquals(listOf("Performer"), Metadata.normalized(tags)["performer"])
        assertEquals(listOf("Artist", "Performer"), Metadata.normalized(tags, cue = true)["artist"])
    }

    @Test fun embeddedCueDoesNotBindExplicitOtherFilesToTheContainer() {
        for (explicit in listOf(true, false)) {
            val text = (if (explicit) "FILE \"other.wav\" WAVE\n" else "") + "TRACK 01 AUDIO\nINDEX 01 00:00:00"
            val bytes = id3(frame("TXXX", byteArrayOf(3) + ascii("CUESHEET\u0000$text")))
            val source = Cuegrain().read(ByteArrayInput(bytes, "container.mp3")).sources.first { it.kind == "cue" }
            assertEquals("container.mp3", source.attributes["embeddedIn"])
            assertEquals(if (explicit) null else "container.mp3", source.files.single().resolvedInput)
        }
    }

    @Test fun lameGainFieldsRetainUnitsAndOriginator() {
        val gain = (1 shl 13) or (3 shl 10) or 512 or 25
        val lame = ascii("LAME3.100") + byteArrayOf(0, 0) + u32(1L shl 23) + byteArrayOf((gain ushr 8).toByte(), gain.toByte(), 0, 0) + ByteArray(8)
        val bytes = byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0) + ByteArray(32) + ascii("Xing") + u32(0) + lame
        val report = Cuegrain().read(ByteArrayInput(bytes, "gain.mp3"))
        assertEquals("ok", report.status)
        val source = report.sources.single()
        assertEquals("lame", source.kind)
        assertEquals(-2.5, source.gains.first().value, 0.0)
        assertEquals(1.0, source.gains.last().value, 0.0)
        assertEquals("linear", source.gains.last().unit)
        assertEquals("3", source.attributes["gain0Originator"])
    }

    @Test fun diagnosticBudgetStillProducesAReport() {
        val report = cue("FILE \"x.wav\" WAVE\nTRACK 01 AUDIO\n" + "INDEX 01 invalid\n".repeat(20), ParseOptions(limits = Limits(diagnostics = 3)))
        assertEquals("partial", report.status)
        assertEquals(3, report.diagnostics.size)
        assertEquals("LIMIT_DIAGNOSTICS", report.diagnostics.last().code)
    }

    @Test fun resolverBindsFilesButNeverMergesSourceTags() {
        val input = ByteArrayInput(basic.toByteArray(), "album.cue")
        val media = ByteArrayInput(id3(frame("TIT2", byteArrayOf(3) + ascii("Embedded title"))), "resolved.mp3")
        val reports = Cuegrain().readWithReferences(input, "cue") { _, _ -> media }
        assertEquals(2, reports.size)
        assertEquals(
            "resolved.mp3",
            reports
                .first()
                .sources
                .single()
                .files
                .single()
                .resolvedInput,
        )
        assertEquals(
            "One",
            reports
                .first()
                .sources
                .single()
                .entries
                .first()
                .tags
                .single()
                .value,
        )
        assertEquals(
            listOf("Embedded title"),
            reports
                .last()
                .sources
                .single()
                .metadata["title"],
        )
    }

    @Test fun invalidTrackDoesNotAppendIndexesToPreviousTrack() {
        val report = cue("FILE \"x.wav\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nTRACK invalid\nINDEX 02 00:01:00")
        assertEquals("partial", report.status)
        assertEquals(
            1,
            report.sources
                .single()
                .entries
                .single()
                .indexes.size,
        )
    }

    @Test fun cedDetectionProvenanceSurvivesSuccessfulDecoding() {
        val detector =
            object : EncodingDetector {
                override fun detect(bytes: ByteArray) = EncodingDetection("windows-1251", true, bytes.size)
            }
        val input = ByteArrayInput((basic + "REM COMMENT Привет").toByteArray(charset("windows-1251")))
        val source = Cuegrain(ParseOptions(detector = detector)).read(input, "cue").sources.single()
        assertEquals("ced", source.attributes["encodingBasis"])
        assertEquals("true", source.attributes["encodingReliable"])
        assertEquals(input.length.toString(), source.attributes["encodingBytesConsumed"])
    }

    @Test fun inputFileBytesNeverChange() {
        val f = File.createTempFile("cuegrain-test-", ".cue")
        try {
            f.writeText(basic)
            val before = f.readBytes()
            FileInput(f).use { assertEquals("ok", Cuegrain().read(it, "cue").status) }
            assertArrayEquals(before, f.readBytes())
        } finally {
            f.delete()
        }
    }

    @Test fun jsonEscapesControlCharactersAndPreservesLargeIntegers() {
        assertEquals(
            "{\"x\":\"a\\n\\\"\\\\\",\"n\":9223372036854775807}",
            Json.encode(linkedMapOf("x" to "a\n\"\\", "n" to Long.MAX_VALUE)),
        )
    }

    @Test fun chapterReferencesExposeFormatKeysAndFrameIndexesWithoutPictureExtraction() {
        val nested =
            frame("TIT3", byteArrayOf(3) + ascii("Subtitle")) +
                frame("WXXX", byteArrayOf(3, 0) + ascii("https://example.test/chapter")) +
                frame("APIC", byteArrayOf(3) + ascii("image/png") + byteArrayOf(0, 3, 0, 1, 2, 3))
        val bytes = id3(frame("CHAP", ascii("chapter") + byteArrayOf(0) + u32(0) + u32(1000) + u32(0xffffffffL) + u32(0xffffffffL) + nested))
        val report = Cuegrain().read(ByteArrayInput(bytes))
        val entry =
            report.sources
                .single()
                .entries
                .single()
        assertEquals("WXXX", entry.tags.last().formatKey)
        assertEquals(2, entry.artwork.single().frameIndex)
        assertArrayEquals(byteArrayOf(1, 2, 3), entry.artwork.single().readBytes())
        val json = Json.encode(Json.report(report))
        assertTrue(json.contains("\"formatKey\":\"WXXX\""))
        assertTrue(json.contains("\"frameIndex\":2"))
        assertFalse(json.contains("base64"))
    }

    @Test fun mediaKindAndSeparateOrdinalTotalsAreDecodedByTheLibrary() {
        fun atom(
            name: String,
            bytes: ByteArray,
        ) = u32(bytes.size.toLong() + 8) + ascii(name) + bytes
        val bytes = atom("ftyp", ascii("M4A ")) + atom("moov", atom("udta", atom("meta", ByteArray(4) + atom("ilst", atom("stik", atom("data", ByteArray(8) + byteArrayOf(2)))))))
        val source = Cuegrain().read(ByteArrayInput(bytes)).sources.single()
        assertEquals("2", source.tags.single().value)
        assertEquals(listOf("2"), Metadata.normalized(source.tags)["mediaKind"])
        val fields = Metadata.normalized(listOf(Tag("TRACKTOTAL", "12"), Tag("TOTALDISCS", "2"), Tag("gnre", "18")))
        assertEquals(listOf("12"), fields["trackTotal"])
        assertEquals(listOf("2"), fields["discTotal"])
        assertEquals(listOf("17"), fields["genre"])
    }

    private fun ascii(s: String) = s.toByteArray(Charsets.UTF_8)

    private fun u32(v: Long) = ByteArray(4) { i -> (v ushr (24 - 8 * i)).toByte() }

    private fun u64(v: Long) = ByteArray(8) { i -> (v ushr (56 - 8 * i)).toByte() }

    private fun le32(v: Long) = u32(v).reversedArray()

    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte())

    private fun sync(v: Int) = ByteArray(4) { i -> ((v ushr (21 - 7 * i)) and 127).toByte() }

    private fun frame(
        id: String,
        data: ByteArray,
        v: Int = 3,
        flags: Int = 0,
    ) = ascii(id) + (if (v == 4) sync(data.size) else u32(data.size.toLong())) + byteArrayOf(0, flags.toByte()) + data

    private fun id3(
        data: ByteArray,
        v: Int = 3,
    ) = ascii("ID3") + byteArrayOf(v.toByte(), 0, 0) + sync(data.size) + data

    private fun block(
        type: Int,
        data: ByteArray,
    ) = byteArrayOf(type.toByte()) + u32(data.size.toLong()).copyOfRange(1, 4) + data

    private fun comments(vararg tags: String) =
        le32(0) + le32(tags.size.toLong()) +
            tags.fold(byteArrayOf()) { out, s ->
                val b = ascii(s)
                out + le32(b.size.toLong()) + b
            }

    private fun page(
        packet: ByteArray,
        sequence: Int,
        flags: Int,
    ): ByteArray {
        val laces = List(packet.size / 255) { 255.toByte() } + listOf((packet.size % 255).toByte())
        return ascii("OggS") + byteArrayOf(0, flags.toByte()) + ByteArray(8) + le32(1) + le32(sequence.toLong()) + ByteArray(4) +
            byteArrayOf(laces.size.toByte()) + laces.toByteArray() + packet
    }
}
