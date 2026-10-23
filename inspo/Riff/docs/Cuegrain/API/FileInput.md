# FileInput

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

Read-only random-access file implementation of MediaInput and java.io.Closeable.

## Declaration

```kotlin
class FileInput(
    file: File,
) : MediaInput, java.io.Closeable
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter | Meaning                                                           |
| --------- | ----------------------------------------------------------------- |
| `file`    | File to open immediately with RandomAccessFile in read-only mode. |

## Member details

### Members

`id` is the supplied file path, not a canonicalized identity. `length` is captured at construction.
`read(offset, count)` is synchronized, seeks and reads exactly count bytes; invalid ranges throw
EOFException and file-access failures propagate.

`close(): Unit` closes the owned handle. Construct and use inside `use`; consume deferred artwork
before leaving the block. Opening the file can fail before Cuegrain can create a Report. Do not
modify a file while parsing it.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val file = java.nio.file.Files.createTempFile("cuegrain-example-", ".cue").toFile()
try {
    file.writeText("TITLE \"Demo\"\n")
    FileInput(file).use { input ->
        val report = Cuegrain().read(input, "cue")
        check(report.input == file.path)
        check(input.length == file.length())
    }
} finally {
    file.delete()
}
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Input.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md), [MediaInput](MediaInput.md), [Report](Report.md).
