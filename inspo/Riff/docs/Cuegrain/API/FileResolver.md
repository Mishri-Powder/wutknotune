# FileResolver

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Functional interface for explicitly resolving a CUE file reference to a caller-owned MediaInput.

## Declaration

```kotlin
fun interface FileResolver
```

## Member details

### `resolve(cueSource: String, reference: FileReference): MediaInput?`

`cueSource` is the source location supplied by the primary CUE Source; `reference` preserves its
declared path/type. Return an authorized readable input, or null to leave unresolved. This callback
owns path interpretation and access policy. The library neither searches directories nor follows
URLs.

Cache inputs by stable ID and close them after report/artwork consumption. Resolver exceptions
escape readWithReferences; it does not turn them into diagnostics. See
[reference resolution examples](https://wiki.271.pics/Cuegrain/examples/references).

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val related = ByteArrayInput("TITLE \"Related\"\n".toByteArray(), "related.cue")
val resolver = FileResolver { _, reference ->
    if (reference.path == "track.cue") related else null
}
val primary = ByteArrayInput(
    "FILE \"track.cue\" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\n".toByteArray(),
    "primary.cue",
)
val reports = Cuegrain().readWithReferences(primary, "cue", resolver)
check(reports.size == 2)
check(reports.first().sources.single().files.single().resolvedInput == "related.cue")
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Input.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [FileReference](FileReference.md),
[MediaInput](MediaInput.md), [Source](Source.md).
