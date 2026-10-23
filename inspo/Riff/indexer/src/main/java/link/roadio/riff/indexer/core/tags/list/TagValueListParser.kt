package link.roadio.riff.indexer.core.tags.list

/** Shared repaired-value list parser; callers own repair and identity. */
object TagValueListParser {
    const val REVISION = "TAG-VALUE-LIST-V2"
    private const val MAX_WEAK_DELIMITERS = 64

    enum class DelimiterPolicy(
        internal val explicit: Regex,
    ) {
        GENRE(Regex("[;,\\r\\n]")),
        ARTIST(Regex("[;|\\r\\n]")),
    }

    /** Splits only the policy's explicit delimiters; all other punctuation stays literal. */
    fun splitExplicit(
        values: List<String>,
        policy: DelimiterPolicy,
    ): List<String> = split(values, policy) { true }

    fun split(
        values: List<String>,
        policy: DelimiterPolicy,
        recognizedWhole: (String) -> Boolean,
    ): List<String> {
        val result = linkedSetOf<String>()

        fun emit(value: String) {
            value.trim().takeIf(String::isNotEmpty)?.let(result::add)
        }

        values.forEach { raw ->
            val value = raw.trim()
            if (value.isEmpty()) return@forEach
            if (policy.explicit.containsMatchIn(value)) {
                policy.explicit.split(value).forEach(::emit)
                return@forEach
            }
            val boundaries = value.indices.filter { value[it] == '/' || value[it] == '&' }
            if (boundaries.isEmpty() ||
                boundaries.size > MAX_WEAK_DELIMITERS ||
                recognizedWhole(value)
            ) {
                emit(value)
                return@forEach
            }
            val ends = boundaries + value.length
            var start = 0
            var firstEnd = 0
            while (firstEnd < ends.size) {
                val recognizedEnd =
                    (ends.lastIndex downTo firstEnd).firstOrNull { index ->
                        val candidate = value.substring(start, ends[index]).trim()
                        candidate.isNotEmpty() && recognizedWhole(candidate)
                    }
                val endIndex = recognizedEnd ?: firstEnd
                emit(value.substring(start, ends[endIndex]))
                start = ends[endIndex] + 1
                firstEnd = endIndex + 1
            }
        }
        return result.toList()
    }
}
