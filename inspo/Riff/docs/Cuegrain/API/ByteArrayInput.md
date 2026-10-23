# ByteArrayInput

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

MediaInput backed by a caller-supplied byte array, useful for CUE text, fixtures and
already-buffered metadata.

## Declaration

```kotlin
class ByteArrayInput(
    private val data: ByteArray,
    override val id: String = "memory",
) : MediaInput
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter | Meaning                                                                                                 |
| --------- | ------------------------------------------------------------------------------------------------------- |
| `data`    | Backing bytes; construction retains this array rather than cloning it. Do not mutate it during parsing. |
| `id`      | Input identifier; defaults to memory.                                                                   |

## Member details

### `read(offset: Long, count: Int): ByteArray`

Returns a copy of the requested range. Invalid ranges throw EOFException. `length` is the array size
as Long. There is no resource to close.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val input = ByteArrayInput("TITLE \"Demo\"\n".toByteArray(), "memory.cue")
val copy = input.read(0, 5)
check(copy.toString(Charsets.UTF_8) == "TITLE")
check(input.length > 0)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Input.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [MediaInput](MediaInput.md).
