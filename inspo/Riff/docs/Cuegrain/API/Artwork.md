# Artwork

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Picture descriptor with optional deferred access to embedded bytes. External URLs remain descriptors
and are never fetched.

## Declaration

```kotlin
data class Artwork(
    val id: String,
    val mimeType: String,
    val type: Int? = null,
    val description: String? = null,
    val size: Long? = null,
    val externalUrl: String? = null,
    internal val loader: (() -> ByteArray)? = null,
    val frameIndex: Int? = null,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter     | Meaning                                                                                                           |
| ------------- | ----------------------------------------------------------------------------------------------------------------- |
| `id`          | Artwork identifier.                                                                                               |
| `mimeType`    | Declared MIME type.                                                                                               |
| `type`        | Optional picture-type code.                                                                                       |
| `description` | Optional descriptive text.                                                                                        |
| `size`        | Optional embedded size in bytes.                                                                                  |
| `externalUrl` | Optional external location, never fetched by Cuegrain.                                                            |
| `loader`      | Optional constructor callback for embedded bytes; its stored property is internal. Normally supplied by a reader. |
| `frameIndex`  | Original ID3 chapter subframe index; not the index in the artwork-only list.                                      |

## Member details

### `readBytes(): ByteArray`

Invokes the embedded-byte loader. Keep the source open until this call finishes. A missing loader
throws IllegalArgumentException; I/O failures from the loader propagate. Metadata parsing may
already have read picture bytes; deferred extraction does not imply zero picture I/O.

Do not call on an external descriptor. Custom providers must bound their own callback; adding a
callback does not add ParseContext limits automatically.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val bytes = byteArrayOf(1, 2, 3)
val embedded = Artwork("picture-1", "image/png", size = 3, loader = { bytes.copyOf() })
check(embedded.readBytes().contentEquals(bytes))
val external = Artwork("url", "image/jpeg", externalUrl = "https://example.org/cover.jpg")
check(runCatching { external.readBytes() }.isFailure)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [Metadata](Metadata.md), [ParseContext](ParseContext.md).
