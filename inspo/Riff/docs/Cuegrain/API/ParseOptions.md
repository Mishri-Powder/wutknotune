# ParseOptions

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Immutable configuration values for a read. Supply options to Cuegrain; each input receives a fresh
ParseContext.

## Declaration

```kotlin
data class ParseOptions(
    val mode: ParseMode = ParseMode.COMPATIBLE,
    val charset: String? = null,
    val detector: EncodingDetector? = null,
    val repairMojibake: Boolean = false,
    val limits: Limits = Limits(),
    val cancelled: () -> Boolean = { false },
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter        | Meaning                                                                                                     |
| ---------------- | ----------------------------------------------------------------------------------------------------------- |
| `mode`           | COMPATIBLE by default; STRICT promotes designated recoverable oddities to errors, not every warning.        |
| `charset`        | Optional explicit charset for standalone CUE when no BOM exists. Does not override binary-format encodings. |
| `detector`       | Optional EncodingDetector; no backend is discovered by core automatically.                                  |
| `repairMojibake` | False by default. Source-wide conservative repair requires a detector; originals are retained.              |
| `limits`         | Positive parser/read budgets; defaults to Limits().                                                         |
| `cancelled`      | Callback polled at parser/read checkpoints. True or thread interruption throws CancellationException.       |

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val options = ParseOptions(
    mode = ParseMode.STRICT,
    charset = "windows-1251",
    limits = Limits(metadataBytes = 1024 * 1024),
)
val result = Cuegrain(options).read(ByteArrayInput("title \"Demo\"\n".toByteArray()), "cue")
check(result.diagnostics.any { it.code == "CUE_COMMAND_CASE" && it.severity == "error" })
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [EncodingDetector](EncodingDetector.md),
[Limits](Limits.md), [ParseContext](ParseContext.md), [ParseMode](ParseMode.md),
[Source](Source.md).
