// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain.repair

import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class MojibakeRepairResult(
    val value: String,
    val repaired: Boolean,
)

/**
 * Conservative whole-track mojibake repair.
 *
 * Candidates must be reversible, supported by bounded encoding detection, and uniquely dominate alternatives.
 * Ambiguity deliberately returns the original strings: a false repair would change stable tag identity.
 */
object MojibakeRepair {
    private val windows1251 = Charset.forName("windows-1251")
    private val windows1252 = Charset.forName("windows-1252")

    fun repairTrack(
        values: List<String>,
        detector: MojibakeEncodingDetector,
    ): List<MojibakeRepairResult> {
        if (values.isEmpty()) {
            return emptyList()
        }
        if (values.any { it.indexOf('\uFFFD') >= 0 }) {
            return unchanged(values)
        }
        val candidateSets =
            (
                russianCandidateSets(values, detector) +
                    westernEuropeanCandidateSets(values, detector)
            ).distinct()
                .filter { it != values }
        val preferredSets =
            candidateSets.filterNot { candidate ->
                candidateSets.any { other ->
                    other != candidate && other.dominates(candidate, values)
                }
            }
        return if (preferredSets.size == 1) {
            values.zip(preferredSets.single()) { original, candidate ->
                MojibakeRepairResult(candidate, candidate != original)
            }
        } else {
            unchanged(values)
        }
    }

    private fun unchanged(values: List<String>): List<MojibakeRepairResult> = values.map { MojibakeRepairResult(it, false) }

    private fun List<String>.dominates(
        other: List<String>,
        original: List<String>,
    ): Boolean {
        var repairsMoreFields = false
        indices.forEach { index ->
            if (other[index] != original[index] && this[index] != other[index]) {
                return false
            }
            if (other[index] == original[index] && this[index] != original[index]) {
                repairsMoreFields = true
            }
        }
        return repairsMoreFields
    }

    private fun russianCandidateSets(
        values: List<String>,
        detector: MojibakeEncodingDetector,
    ): List<List<String>> {
        val sourceMetrics = values.map(::russianMetrics).sum()
        return listOf(
            candidateSet(
                values,
                windows1251,
                StandardCharsets.UTF_8,
                MojibakeEncoding.UTF8,
                detector,
                ::isRussianFieldCandidate,
            ),
            candidateSet(
                values,
                StandardCharsets.ISO_8859_1,
                windows1251,
                MojibakeEncoding.WINDOWS_1251,
                detector,
                ::isRussianFieldCandidate,
            ),
            candidateSet(
                values,
                windows1252,
                windows1251,
                MojibakeEncoding.WINDOWS_1251,
                detector,
                ::isRussianFieldCandidate,
            ),
        ).filterNotNull().filter { candidates ->
            val metrics = candidates.map(::russianMetrics).sum()
            candidates != values &&
                metrics.cyrillicLetters >= MIN_CYRILLIC_LETTERS &&
                metrics.cyrillicLetters * 2 >= metrics.letters &&
                sourceMetrics.evidence - metrics.evidence >= MIN_RUSSIAN_EVIDENCE_REDUCTION &&
                metrics.score - sourceMetrics.score >= MIN_RUSSIAN_SCORE_IMPROVEMENT
        }
    }

    private fun westernEuropeanCandidateSets(
        values: List<String>,
        detector: MojibakeEncodingDetector,
    ): List<List<String>> =
        listOf(
            candidateSet(
                values,
                StandardCharsets.ISO_8859_1,
                StandardCharsets.UTF_8,
                MojibakeEncoding.UTF8,
                detector,
                ::isWesternEuropeanFieldCandidate,
            ),
            candidateSet(
                values,
                windows1252,
                StandardCharsets.UTF_8,
                MojibakeEncoding.UTF8,
                detector,
                ::isWesternEuropeanFieldCandidate,
            ),
        ).filterNotNull().filter { candidates ->
            val changed = values.zip(candidates).filter { (source, candidate) -> source != candidate }
            changed.isNotEmpty() &&
                changed.sumOf { (source, _) -> source.codePointCount(0, source.length) } >
                changed.sumOf { (_, candidate) -> candidate.codePointCount(0, candidate.length) } &&
                changed.sumOf { (_, candidate) -> candidate.latinLetterCount() } >= MIN_LATIN_LETTERS
        }

    private fun candidateSet(
        values: List<String>,
        source: Charset,
        target: Charset,
        expectedEncoding: MojibakeEncoding,
        detector: MojibakeEncodingDetector,
        accepts: (String, String) -> Boolean,
    ): List<String>? {
        val candidates =
            values.map { value ->
                reversibleTranscode(value, source, target)
                    ?.takeIf { candidate -> candidate != value && accepts(value, candidate) }
                    ?: value
            }
        if (candidates == values) {
            return null
        }
        val evidence = detectorInput(values, source) ?: return null
        val detection =
            runCatching { detector.detect(evidence) }
                .onFailure {
                    if (it is java.util.concurrent.CancellationException) throw it
                }.getOrNull() ?: return null
        if (
            !detection.isReliable ||
            detection.encoding != expectedEncoding ||
            detection.bytesConsumed !in 0..evidence.size
        ) {
            return null
        }
        return candidates
    }

    private fun detectorInput(
        values: List<String>,
        source: Charset,
    ): ByteArray? {
        val output = ByteArray(CED_MAX_INPUT_BYTES)
        var size = 0
        values.forEachIndexed { index, value ->
            val bytes = encode(value, source) ?: return null
            if (index > 0 && size < output.size) {
                output[size++] = 0x0A
            }
            val count = minOf(bytes.size, output.size - size)
            bytes.copyInto(output, destinationOffset = size, endIndex = count)
            size += count
        }
        return output.copyOf(size)
    }

    private fun isRussianFieldCandidate(
        source: String,
        candidate: String,
    ): Boolean {
        val sourceMetrics = russianMetrics(source)
        val candidateMetrics = russianMetrics(candidate)
        return candidateMetrics.evidence < sourceMetrics.evidence &&
            candidateMetrics.score > sourceMetrics.score &&
            candidate.codePoints().noneMatch(::isInvalid)
    }

    private fun isWesternEuropeanFieldCandidate(
        source: String,
        candidate: String,
    ): Boolean =
        candidate.codePointCount(0, candidate.length) < source.codePointCount(0, source.length) &&
            candidate.codePoints().anyMatch { it > 0x7F } &&
            candidate.codePoints().filter(Character::isLetter).allMatch(::isLatinLetter) &&
            candidate.latinLetterCount() > 0 &&
            candidate.codePoints().noneMatch(::isInvalid)

    private fun reversibleTranscode(
        value: String,
        source: Charset,
        target: Charset,
    ): String? {
        val candidate = transcode(value, source, target) ?: return null
        return candidate.takeIf { transcode(candidate, target, source) == value }
    }

    private fun transcode(
        value: String,
        source: Charset,
        target: Charset,
    ): String? =
        try {
            val bytes = encode(value, source) ?: return null
            target
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    private fun encode(
        value: String,
        charset: Charset,
    ): ByteArray? =
        try {
            val buffer =
                charset
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value))
            ByteArray(buffer.remaining()).also(buffer::get)
        } catch (_: CharacterCodingException) {
            null
        }

    private fun russianMetrics(value: String): RussianMetrics {
        val codePoints = value.codePoints().toArray()
        val cyrillic =
            codePoints.count { codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.CYRILLIC &&
                    Character.isLetter(codePoint)
            }
        val letters = codePoints.count(Character::isLetter)
        val latinMojibake =
            codePoints.count { codePoint ->
                codePoint in 0x00A0..0x00FF ||
                    codePoint in 0x2000..0x20FF ||
                    codePoint == 0x00D0 ||
                    codePoint == 0x00D1
            }
        val classicPairs =
            value.windowed(size = 2).count { pair ->
                pair[0] == '\u0420' || pair[0] == '\u0421'
            }
        val invalid = codePoints.count(::isInvalid)
        val evidence = latinMojibake + classicPairs * 2 + invalid * 8
        val score = cyrillic * 4 - latinMojibake * 5 - classicPairs * 10 - invalid * 40
        return RussianMetrics(cyrillic, letters, evidence, score)
    }

    private fun Iterable<RussianMetrics>.sum(): RussianMetrics =
        fold(RussianMetrics(0, 0, 0, 0)) { total, metrics ->
            RussianMetrics(
                cyrillicLetters = total.cyrillicLetters + metrics.cyrillicLetters,
                letters = total.letters + metrics.letters,
                evidence = total.evidence + metrics.evidence,
                score = total.score + metrics.score,
            )
        }

    private fun String.latinLetterCount(): Int =
        codePoints()
            .filter(Character::isLetter)
            .filter(::isLatinLetter)
            .count()
            .toInt()

    private fun isLatinLetter(codePoint: Int): Boolean =
        Character.isLetter(codePoint) &&
            Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN

    private fun isInvalid(codePoint: Int): Boolean =
        codePoint == 0xFFFD ||
            (
                Character.getType(codePoint) == Character.CONTROL.toInt() &&
                    codePoint != '\n'.code &&
                    codePoint != '\r'.code &&
                    codePoint != '\t'.code
            )

    private data class RussianMetrics(
        val cyrillicLetters: Int,
        val letters: Int,
        val evidence: Int,
        val score: Int,
    )

    private const val MIN_CYRILLIC_LETTERS = 4
    private const val MIN_LATIN_LETTERS = 2
    private const val MIN_RUSSIAN_EVIDENCE_REDUCTION = 3
    private const val MIN_RUSSIAN_SCORE_IMPROVEMENT = 12
}
