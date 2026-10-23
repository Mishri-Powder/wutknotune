# ParseMode

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Enum selecting treatment of recoverable syntax oddities.

## Declaration

```kotlin
enum class ParseMode { COMPATIBLE, STRICT }
```

## Member details

### Values

- `COMPATIBLE`: designated oddities become warning diagnostics.
- `STRICT`: those same oddities become error diagnostics.

Strict parsing can still return recovered sources and a partial report. It is not a promise that
every diagnostic is fatal or every possible semantic defect is detected.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val compatible = ParseOptions(mode = ParseMode.COMPATIBLE)
val strict = compatible.copy(mode = ParseMode.STRICT)
check(strict.mode == ParseMode.STRICT)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md).
