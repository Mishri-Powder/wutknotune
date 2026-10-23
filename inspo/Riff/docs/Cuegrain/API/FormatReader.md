# FormatReader

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Extension interface for a metadata format reader. Cuegrain uses the first matching configured
reader; independent representations returned by that reader remain separate.

## Declaration

```kotlin
interface FormatReader
```

## Member details

### name: String

Human-readable reader identifier. It need not equal every returned Source.kind.

### `matches(header: ByteArray, hint: String?): Boolean`

Classifies the available prefix (up to 32 bytes) and optional extension hint. Matching does not
prove file validity. Implement a cheap, side-effect-free check; Cuegrain reads the prefix before
choosing a reader.

### `read(context: ParseContext): List<Source>`

Returns source observations using the supplied context's input, limits and diagnostic/cancellation
helpers. Do not open implicit related files or write source bytes. The context constructor is
internal; extensions receive an instance through this callback.

Construct Cuegrain with your reader plus whichever built-ins you want to retain. The list replaces
defaults; it is not automatically appended. The
[extension recipe](https://wiki.271.pics/Cuegrain/examples/extensions) demonstrates delegated
embedded CUE and deliberate failure handling.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val custom = object : FormatReader {
    override val name = "example"
    override fun matches(header: ByteArray, hint: String?) = hint == "example"
    override fun read(context: ParseContext): List<Source> {
        context.check()
        context.diagnostic("EXAMPLE_NOTE", "Synthetic reader example")
        return listOf(Source("example", context.input.id + "#metadata", context.input.id))
    }
}
val result = Cuegrain(readers = listOf(custom, CueReader())).read(ByteArrayInput(byteArrayOf()), "example")
check(result.sources.single().kind == "example")
check(result.diagnostics.single().severity == "warning")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Cuegrain.kt`. This reference describes
the current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [ParseContext](ParseContext.md), [Source](Source.md).
