# Json

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Dependency-free JSON projection and serialization. This is an object, not an instantiable writer.

## Declaration

```kotlin
object Json
```

## Member details

### `report(r: Report, includeArtwork: Boolean = false): Map<String, Any?>`

Produces a JSON-ready model containing schemaVersion, input, status, sources and diagnostics. It
returns a Map, not a String and not the CLI's multi-input envelope. Default output includes artwork
descriptors only. Opting into artwork invokes embedded loaders and adds base64; consume it while
sources remain open.

### `encode(value: Any?): String`

Serializes null, String, Boolean, integer primitives, finite Float/Double, Map and Iterable values.
Map keys become strings. Arrays, ByteArray and arbitrary objects are unsupported; project a Report
with report first. Nonfinite values throw IllegalArgumentException; unsupported types throw
IllegalStateException.

Long values are emitted as exact integer tokens. Consumers using floating-point-only JSON numbers
need lossless handling.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val result = Cuegrain().read(ByteArrayInput("TITLE \"Album\"\n".toByteArray()), "cue")
val model = Json.report(result)
val json = Json.encode(model)
check(model["schemaVersion"] == 1)
check(json.startsWith("{"))
check(runCatching { Json.encode(Double.NaN) }.isFailure)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Json.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [Report](Report.md).
