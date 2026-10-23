package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GenreExclusionUiConfigTest {
    @Test
    fun `packaged configuration excludes the four inferred decade IDs`() {
        val root =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "app/src/main/config/ui_config.json").isFile }
        val configured =
            requireNotNull(
                Regex(
                    """"excludedGenreIds"\s*:\s*\[(.*?)]""",
                    RegexOption.DOT_MATCHES_ALL,
                ).find(File(root, "app/src/main/config/ui_config.json").readText()),
            ).groupValues[1]
                .let { body -> Regex(""""([^"]+)"""").findAll(body).map { it.groupValues[1] }.toList() }

        assertEquals(
            setOf(-100_000L, -100_001L, -100_002L, -100_003L),
            UiConfig.validateExcludedGenreIds(configured),
        )
    }

    @Test
    fun `empty override list disables genre exclusion`() {
        assertTrue(UiConfig.validateExcludedGenreIds(emptyList<String>()).isEmpty())
    }

    @Test
    fun `genre exclusion requires unique canonical nonzero signed longs`() {
        listOf(
            listOf("0"),
            listOf("-0"),
            listOf("+7"),
            listOf("07"),
            listOf("-0100000"),
            listOf("7", "7"),
            listOf("9223372036854775808"),
        ).forEach { configured ->
            assertTrue(runCatching { UiConfig.validateExcludedGenreIds(configured) }.isFailure)
        }

        assertEquals(
            setOf(Long.MIN_VALUE, -100_000L, 7L, Long.MAX_VALUE),
            UiConfig.validateExcludedGenreIds(
                listOf(Long.MIN_VALUE.toString(), "-100000", "7", Long.MAX_VALUE.toString()),
            ),
        )
    }
}
