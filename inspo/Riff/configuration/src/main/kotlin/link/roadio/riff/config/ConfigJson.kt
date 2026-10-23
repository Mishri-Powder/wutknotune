package link.roadio.riff.config

import java.util.Collections

internal typealias ConfigObject = Map<String, Any>

internal fun configObject(vararg entries: Pair<String, Any>): ConfigObject = Collections.unmodifiableMap(linkedMapOf(*entries))

internal fun configArray(vararg entries: Any): List<Any> = Collections.unmodifiableList(entries.toList())

internal fun immutableObject(entries: Map<String, Any>): ConfigObject = Collections.unmodifiableMap(LinkedHashMap(entries))

internal fun Any.objectValue(): ConfigObject {
    require(this is Map<*, *>) { "Expected configuration object" }
    @Suppress("UNCHECKED_CAST")
    return this as ConfigObject
}

/** Strict JSON only: no duplicate keys, null deletion, comments or JavaScript extensions. */
internal class ConfigJson(
    private val source: String,
) {
    private var offset = 0

    fun parse(): ConfigObject {
        val result = value(0).objectValue()
        whitespace()
        require(offset == source.length) { "Trailing JSON content" }
        return result
    }

    private fun value(depth: Int): Any {
        require(depth <= 64) { "Configuration nesting is too deep" }
        whitespace()
        require(offset < source.length) { "Unexpected end of JSON" }
        return when (source[offset]) {
            '{' -> objectValue(depth + 1)
            '[' -> array(depth + 1)
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            '-', in '0'..'9' -> number()
            else -> error("Unexpected JSON token")
        }
    }

    private fun objectValue(depth: Int): ConfigObject {
        expect('{')
        whitespace()
        val result = linkedMapOf<String, Any>()
        if (take('}')) return immutableObject(result)
        while (true) {
            whitespace()
            val key = string()
            require(key !in result) { "Duplicate JSON key" }
            whitespace()
            expect(':')
            result[key] = value(depth)
            whitespace()
            if (take('}')) return immutableObject(result)
            expect(',')
        }
    }

    private fun array(depth: Int): List<Any> {
        expect('[')
        whitespace()
        val result = mutableListOf<Any>()
        if (take(']')) return configArray()
        while (true) {
            result += value(depth)
            whitespace()
            if (take(']')) return configArray(*result.toTypedArray())
            expect(',')
        }
    }

    private fun string(): String {
        expect('"')
        return buildString {
            while (true) {
                require(offset < source.length) { "Unterminated JSON string" }
                when (val character = source[offset++]) {
                    '"' -> {
                        return@buildString
                    }

                    '\\' -> {
                        append(escape())
                    }

                    else -> {
                        require(character.code >= 0x20) { "Control character in JSON string" }
                        append(character)
                    }
                }
            }
        }
    }

    private fun escape(): Char {
        require(offset < source.length) { "Unterminated JSON escape" }
        return when (val escaped = source[offset++]) {
            '"', '\\', '/' -> {
                escaped
            }

            'b' -> {
                '\b'
            }

            'f' -> {
                '\u000c'
            }

            'n' -> {
                '\n'
            }

            'r' -> {
                '\r'
            }

            't' -> {
                '\t'
            }

            'u' -> {
                require(offset <= source.length - 4) { "Truncated Unicode escape" }
                val digits = source.substring(offset, offset + 4)
                require(digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                digits.toInt(16).toChar().also { offset += 4 }
            }

            else -> {
                error("Invalid JSON escape")
            }
        }
    }

    private fun number(): Number {
        val start = offset
        take('-')
        if (take('0')) {
            require(offset == source.length || source[offset] !in '0'..'9') { "Leading zero" }
        } else {
            require(digits()) { "Invalid JSON number" }
        }
        var integer = true
        if (take('.')) {
            integer = false
            require(digits()) { "Invalid JSON fraction" }
        }
        if (offset < source.length && source[offset] in "eE") {
            integer = false
            offset += 1
            if (offset < source.length && source[offset] in "+-") offset += 1
            require(digits()) { "Invalid JSON exponent" }
        }
        val text = source.substring(start, offset)
        return if (integer) {
            text.toLong()
        } else {
            text.toDouble().also {
                require(it.isFinite()) { "Non-finite JSON number" }
            }
        }
    }

    private fun digits(): Boolean {
        val start = offset
        while (offset < source.length && source[offset] in '0'..'9') offset += 1
        return offset > start
    }

    private fun literal(
        text: String,
        value: Boolean,
    ): Boolean {
        require(source.startsWith(text, offset)) { "Invalid JSON literal" }
        offset += text.length
        return value
    }

    private fun whitespace() {
        while (offset < source.length && source[offset] in " \t\r\n") offset += 1
    }

    private fun expect(character: Char) {
        require(take(character)) { "Expected JSON delimiter" }
    }

    private fun take(character: Char): Boolean =
        if (offset < source.length && source[offset] == character) {
            offset += 1
            true
        } else {
            false
        }
}
