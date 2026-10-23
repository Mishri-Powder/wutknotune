package link.roadio.riff.indexer.core.tags.confusables

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.util.Properties

data class ConfusableDataRevision(
    val unicodeVersion: String,
    val sourceSha256: String,
    val tableSha256: String,
    val entryCount: Int,
)

class OneToOneConfusableMapper private constructor(
    private val sources: IntArray,
    private val targets: IntArray,
    val revision: ConfusableDataRevision,
) {
    fun map(value: String): String {
        val output = StringBuilder(value.length)
        value.codePoints().forEach { codePoint ->
            val index = sources.binarySearch(codePoint)
            output.appendCodePoint(if (index >= 0) targets[index] else codePoint)
        }
        return output.toString()
    }

    companion object {
        private const val MAGIC = 0x52434631
        private const val RESOURCE_ROOT =
            "/link/roadio/riff/indexer/core/tags/confusables/"

        val default: OneToOneConfusableMapper by lazy {
            val properties =
                Properties().apply {
                    resource("confusables-one-to-one.properties").use(::load)
                }
            val revision =
                ConfusableDataRevision(
                    unicodeVersion = properties.required("unicodeVersion"),
                    sourceSha256 = properties.required("sourceSha256"),
                    tableSha256 = properties.required("tableSha256"),
                    entryCount = properties.required("entryCount").toInt(),
                )
            resource("confusables-one-to-one.bin").use { read(it, revision) }
        }

        internal fun fromBytes(
            bytes: ByteArray,
            revision: ConfusableDataRevision,
        ): OneToOneConfusableMapper = read(ByteArrayInputStream(bytes), revision)

        private fun read(
            input: InputStream,
            revision: ConfusableDataRevision,
        ): OneToOneConfusableMapper =
            DataInputStream(input.buffered()).use { data ->
                require(data.readInt() == MAGIC) { "Invalid confusable-table magic" }
                val count = data.readInt()
                require(count == revision.entryCount && count in 1..100_000) {
                    "Invalid confusable-table entry count"
                }
                val sources = IntArray(count)
                val targets = IntArray(count)
                repeat(count) { index ->
                    val source = data.readInt()
                    val target = data.readInt()
                    require(Character.isValidCodePoint(source) && Character.isValidCodePoint(target)) {
                        "Invalid confusable-table code point"
                    }
                    require(source !in 0xD800..0xDFFF && target !in 0xD800..0xDFFF) {
                        "Surrogate code point in confusable table"
                    }
                    require(index == 0 || sources[index - 1] < source) {
                        "Confusable table is not strictly source-sorted"
                    }
                    sources[index] = source
                    targets[index] = target
                }
                require(data.read() == -1) { "Trailing bytes in confusable table" }
                OneToOneConfusableMapper(sources, targets, revision)
            }

        private fun resource(name: String): InputStream =
            requireNotNull(OneToOneConfusableMapper::class.java.getResourceAsStream(RESOURCE_ROOT + name)) {
                "Missing generated confusable resource: " + name
            }

        private fun Properties.required(name: String) = getProperty(name) ?: error("Missing confusable property: $name")
    }
}
