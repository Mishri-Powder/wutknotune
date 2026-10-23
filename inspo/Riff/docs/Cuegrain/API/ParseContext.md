# ParseContext

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Per-input parser state supplied to FormatReader.read. The constructor is internal; application
clients obtain reports through Cuegrain.

## Declaration

```kotlin
class ParseContext internal constructor(
    val input: MediaInput,
    val options: ParseOptions,
)
```

## Member details

### Properties

`input: MediaInput`, `options: ParseOptions` and `diagnostics: MutableList<Diagnostic>`. Use
diagnostic helpers instead of appending directly, so the diagnostic cap is enforced.

### `check(): Unit`

Polls cancellation/thread interruption and consumes one element checkpoint. Throws
CancellationException or the bounded parser failure for LIMIT_ELEMENTS.

### `read(offset: Long, count: Int): ByteArray`

Calls check, enforces per-read and aggregate byte budgets, then performs an exact read. Short
results become EOFException. The input implementation remains responsible for valid offset ranges.

### `diagnostic(code: String, message: String, location: String? = null, severity: String = "warning"): Unit`

Appends a bounded observation. When capped, replaces the final slot with an error-level
LIMIT_DIAGNOSTICS.

### `oddity(code: String, message: String, location: String? = null): Unit`

Adds a warning in compatible mode or error in strict mode without immediately throwing.

### `fail(code: String, message: String): Nothing`

Throws an internal parser failure, caught by Cuegrain.read and reported with the supplied code. Do
not depend on that internal exception class.

### `boundedCount(count: Long, max: Int = options.limits.fields): Int`

Accepts 0..max and converts to Int, otherwise fails with LIMIT_COUNT. This validates a count; it
does not consume that many element checkpoints.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val reader = object : FormatReader {
    override val name = "bounded-example"
    override fun matches(header: ByteArray, hint: String?) = hint == name
    override fun read(context: ParseContext): List<Source> {
        val count = context.boundedCount(context.input.length, 16)
        val bytes = context.read(0, count)
        if (bytes.isEmpty()) context.fail("EXAMPLE_EMPTY", "Expected some data")
        context.oddity("EXAMPLE_ODDITY", "Demonstrate mode-dependent severity")
        return listOf(Source(name, context.input.id, context.input.id))
    }
}
val result = Cuegrain(readers = listOf(reader)).read(ByteArrayInput(byteArrayOf()), reader.name)
check(result.diagnostics.single().code == "EXAMPLE_EMPTY")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Input.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [Diagnostic](Diagnostic.md),
[FormatReader](FormatReader.md), [MediaInput](MediaInput.md), [ParseOptions](ParseOptions.md).
