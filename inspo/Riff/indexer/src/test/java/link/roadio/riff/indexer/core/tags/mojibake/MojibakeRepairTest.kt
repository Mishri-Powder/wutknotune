package link.roadio.cuegrain.repair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class MojibakeRepairTest {
    private val windows1251 = Charset.forName("windows-1251")
    private val windows1252 = Charset.forName("windows-1252")
    private val detector = TestMojibakeEncodingDetector()

    @Test
    fun repairsCp1251BytesDecodedAsLatin1OrWindows1252() {
        val expected = "Лебедовский вальс"
        listOf(StandardCharsets.ISO_8859_1, windows1252).forEach { wrong ->
            val broken = String(expected.toByteArray(windows1251), wrong)
            val result = repairSingle(broken)

            assertTrue(wrong.name(), result.repaired)
            assertEquals(expected, result.value)
        }
    }

    @Test
    fun repairsUtf8RussianBytesDecodedAsWindows1251() {
        val expected = "Лебедовский вальс"
        val broken = String(expected.toByteArray(StandardCharsets.UTF_8), windows1251)

        val result = repairSingle(broken)

        assertTrue(result.repaired)
        assertEquals(expected, result.value)
    }

    @Test
    fun aggregateRussianEvidenceRepairsShortFieldsUnderOneProfile() {
        val expected = listOf("Лебедовский вальс", "Оркестр", "Рок")
        val broken = expected.map { String(it.toByteArray(windows1251), StandardCharsets.ISO_8859_1) }

        assertFalse(repairSingle(broken.last()).repaired)
        val repaired = MojibakeRepair.repairTrack(broken, detector)

        assertEquals(expected, repaired.map(MojibakeRepairResult::value))
        assertTrue(repaired.all(MojibakeRepairResult::repaired))
    }

    @Test
    fun aggregateWesternEvidenceRepairsOneLetterFieldUnderOneProfile() {
        val expected = listOf("João Gilberto", "Canção do Mar", "É")
        val broken = expected.map { String(it.toByteArray(StandardCharsets.UTF_8), windows1252) }

        assertFalse(repairSingle(broken.last()).repaired)
        val repaired = MojibakeRepair.repairTrack(broken, detector)

        assertEquals(expected, repaired.map(MojibakeRepairResult::value))
        assertTrue(repaired.all(MojibakeRepairResult::repaired))
    }

    @Test
    fun rejectsAProfileWhenOneFieldCannotUseItsObservedCharset() {
        val title = "Лебедовский вальс"
        val expectedDamaged = listOf("Оркестр", "Рок")
        val brokenDamaged =
            expectedDamaged.map {
                String(it.toByteArray(windows1251), StandardCharsets.ISO_8859_1)
            }

        val repaired = MojibakeRepair.repairTrack(listOf(title) + brokenDamaged, detector)

        assertEquals(listOf(title) + brokenDamaged, repaired.map(MojibakeRepairResult::value))
        assertTrue(repaired.none(MojibakeRepairResult::repaired))
    }

    @Test
    fun conflictingTrackProfilesLeaveEveryFieldUnchanged() {
        val russian =
            String(
                "Лебедовский вальс".toByteArray(windows1251),
                StandardCharsets.ISO_8859_1,
            )
        val western =
            String(
                "João Gilberto".toByteArray(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1,
            )

        val unreliableDetector =
            TestMojibakeEncodingDetector { input ->
                MojibakeEncodingDetection(MojibakeEncoding.OTHER, false, input.size)
            }
        val repaired = MojibakeRepair.repairTrack(listOf(russian, western), unreliableDetector)

        assertEquals(listOf(russian, western), repaired.map(MojibakeRepairResult::value))
        assertTrue(repaired.none(MojibakeRepairResult::repaired))
    }

    @Test
    fun repairsPortugueseSpanishAndFrenchUtf8DecodedAsLatin1() {
        listOf(
            "João Gilberto",
            "Canção do Mar",
            "Español",
            "Mañana",
            "François",
            "Édith Piaf",
            "Château",
        ).forEach { expected ->
            val broken = String(expected.toByteArray(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)
            val result = repairSingle(broken)

            assertTrue(expected, result.repaired)
            assertEquals(expected, result.value)
        }
    }

    @Test
    fun repairsWindows1252SmartPunctuationWithoutAnAccentAllowlist() {
        listOf(
            "L’amour toujours",
            "Rock – Español",
            "«Français»",
        ).forEach { expected ->
            val broken = String(expected.toByteArray(StandardCharsets.UTF_8), windows1252)
            val result = repairSingle(broken)

            assertTrue(expected, result.repaired)
            assertEquals(expected, result.value)
        }
    }

    @Test
    fun replacementCharacterInOneFieldRejectsTheWholeTrackSet() {
        val broken =
            String(
                "Лебедовский вальс".toByteArray(windows1251),
                StandardCharsets.ISO_8859_1,
            )
        val values = listOf(broken, "Fran�ais")

        val repaired = MojibakeRepair.repairTrack(values, detector)

        assertEquals(values, repaired.map(MojibakeRepairResult::value))
        assertTrue(repaired.none(MojibakeRepairResult::repaired))
    }

    @Test
    fun leavesLegitimateAccentedAndLowConfidenceTextAlone() {
        listOf(
            "Москва",
            "São Paulo",
            "SÃO",
            "Mañana",
            "François",
            "Château",
            "Beyoncé",
            "Rock",
            "Δ Machine",
            "Fran�ais",
        ).forEach { value ->
            val result = repairSingle(value)
            assertFalse(value, result.repaired)
            assertEquals(value, result.value)
        }
    }

    @Test
    fun unreliableOrWrongCedEvidenceRejectsAnOtherwiseValidRepair() {
        val expected = "João Gilberto"
        val broken = String(expected.toByteArray(StandardCharsets.UTF_8), windows1252)
        listOf(
            MojibakeEncodingDetection(MojibakeEncoding.UTF8, false, expected.length),
            MojibakeEncodingDetection(MojibakeEncoding.WINDOWS_1251, true, expected.length),
        ).forEach { detection ->
            val rejectingDetector = TestMojibakeEncodingDetector { detection }
            val repaired = MojibakeRepair.repairTrack(listOf(broken), rejectingDetector).single()

            assertFalse(repaired.repaired)
            assertEquals(broken, repaired.value)
        }
    }

    @Test
    fun constructsNewlineFramedDetectorInputInTrackFieldOrder() {
        val expected = listOf("João Gilberto", "Canção do Mar", "É")
        val broken = expected.map { String(it.toByteArray(StandardCharsets.UTF_8), windows1252) }
        val recordingDetector = TestMojibakeEncodingDetector()

        MojibakeRepair.repairTrack(broken, recordingDetector)

        val expectedInput = expected.joinToString("\n").toByteArray(StandardCharsets.UTF_8)
        assertTrue(recordingDetector.inputs.any(expectedInput::contentEquals))
    }

    @Test
    fun capsEveryDetectorInputAtTheContractLimit() {
        val expected = "João".repeat(20_000)
        val broken = String(expected.toByteArray(StandardCharsets.UTF_8), windows1252)
        val recordingDetector = TestMojibakeEncodingDetector()

        MojibakeRepair.repairTrack(listOf(broken), recordingDetector)

        assertTrue(recordingDetector.inputs.isNotEmpty())
        assertTrue(recordingDetector.inputs.all { it.size <= CED_MAX_INPUT_BYTES })
        assertTrue(recordingDetector.inputs.any { it.size == CED_MAX_INPUT_BYTES })
    }

    private fun repairSingle(value: String): MojibakeRepairResult = MojibakeRepair.repairTrack(listOf(value), detector).single()
}
