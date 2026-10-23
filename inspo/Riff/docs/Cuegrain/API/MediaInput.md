# MediaInput

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Caller-owned, position-independent byte source. Implement this interface for descriptors, archives
or application storage without coupling Cuegrain to that storage system.

## Declaration

```kotlin
interface MediaInput
```

## Member details

### Properties and method

`id: String` identifies this input in reports and reference deduplication; it is not a content hash.
`length: Long` is the stable byte length for a parse.

`read(offset: Long, count: Int): ByteArray` must return exactly count bytes or throw an I/O
exception. Validate nonnegative ranges using subtraction to avoid overflow. Reading must not depend
on a cursor left by another call. Cuegrain checks short reads in ParseContext.

The interface has no close method. The concrete source owner controls lifetime; keep a source
available until all deferred artwork reads finish.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val bytes = byteArrayOf(10, 20, 30)
val input: MediaInput = ByteArrayInput(bytes, "sample")
check(input.length == 3L)
check(input.read(1, 2).contentEquals(byteArrayOf(20, 30)))
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Input.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [ParseContext](ParseContext.md).
