# Cuegrain member index

[All classes](../API.md) · [Examples](https://wiki.271.pics/Cuegrain/examples)

Member signatures link to their owning class, which documents arguments, result, failure behavior
and examples. Constructors and data properties are listed on every class page; inherited Kotlin
value methods are described once per data class.

| Member                                                                                                           | Owner                                                   |
| ---------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- |
| `read(input: MediaInput, hint: String? = null): Report`                                                          | [Cuegrain](Cuegrain.md)                                 |
| `readGroup(inputs: List<Pair<MediaInput, String?>>): List<Report>`                                               | [Cuegrain](Cuegrain.md)                                 |
| `readWithReferences(input: MediaInput, hint: String? = null, resolver: FileResolver): List<Report>`              | [Cuegrain](Cuegrain.md)                                 |
| `read(offset: Long, count: Int): ByteArray`                                                                      | [ByteArrayInput](ByteArrayInput.md)                     |
| `resolve(cueSource: String, reference: FileReference): MediaInput?`                                              | [FileResolver](FileResolver.md)                         |
| `compareTo(other: MediaTime): Int`                                                                               | [MediaTime](MediaTime.md)                               |
| `milliseconds(): Long`                                                                                           | [MediaTime](MediaTime.md)                               |
| `readBytes(): ByteArray`                                                                                         | [Artwork](Artwork.md)                                   |
| `normalized(tags: List<Tag>, albumScope: Boolean = false, cue: Boolean = albumScope): Map<String, List<String>>` | [Metadata](Metadata.md)                                 |
| `gains(tags: List<Tag>, ctx: ParseContext? = null): List<Gain>`                                                  | [Metadata](Metadata.md)                                 |
| `riffFeatures(tags: List<Tag>): RiffFeatures`                                                                    | [Metadata](Metadata.md)                                 |
| `report(r: Report, includeArtwork: Boolean = false): Map<String, Any?>`                                          | [Json](Json.md)                                         |
| `encode(value: Any?): String`                                                                                    | [Json](Json.md)                                         |
| `detect(bytes: ByteArray): EncodingDetection`                                                                    | [EncodingDetector](EncodingDetector.md)                 |
| `CedEncodingDetector()`                                                                                          | [CedEncodingDetector](CedEncodingDetector.md)           |
| `detect(bytes: ByteArray): EncodingDetection`                                                                    | [CedEncodingDetector](CedEncodingDetector.md)           |
| `repairTrack(values: List<String>, detector: MojibakeEncodingDetector): List<MojibakeRepairResult>`              | [MojibakeRepair](MojibakeRepair.md)                     |
| `name: String`                                                                                                   | [FormatReader](FormatReader.md)                         |
| `matches(header: ByteArray, hint: String?): Boolean`                                                             | [FormatReader](FormatReader.md)                         |
| `read(context: ParseContext): List<Source>`                                                                      | [FormatReader](FormatReader.md)                         |
| `check(): Unit`                                                                                                  | [ParseContext](ParseContext.md)                         |
| `read(offset: Long, count: Int): ByteArray`                                                                      | [ParseContext](ParseContext.md)                         |
| `diagnostic(code: String, message: String, location: String? = null, severity: String = "warning"): Unit`        | [ParseContext](ParseContext.md)                         |
| `oddity(code: String, message: String, location: String? = null): Unit`                                          | [ParseContext](ParseContext.md)                         |
| `fail(code: String, message: String): Nothing`                                                                   | [ParseContext](ParseContext.md)                         |
| `boundedCount(count: Long, max: Int = options.limits.fields): Int`                                               | [ParseContext](ParseContext.md)                         |
| `CueReader()`                                                                                                    | [CueReader](CueReader.md)                               |
| `matches(header: ByteArray, hint: String?): Boolean`                                                             | [CueReader](CueReader.md)                               |
| `read(context: ParseContext): List<Source>`                                                                      | [CueReader](CueReader.md)                               |
| `parse(text: String, ctx: ParseContext, sourceId: String, embeddedFile: String? = null): Source`                 | [CueReader](CueReader.md)                               |
| `Id3Reader()`                                                                                                    | [Id3Reader](Id3Reader.md)                               |
| `matches(header: ByteArray, hint: String?): Boolean`                                                             | [Id3Reader](Id3Reader.md)                               |
| `read(context: ParseContext): List<Source>`                                                                      | [Id3Reader](Id3Reader.md)                               |
| `FlacReader()`                                                                                                   | [FlacReader](FlacReader.md)                             |
| `matches(header: ByteArray, hint: String?): Boolean`                                                             | [FlacReader](FlacReader.md)                             |
| `read(context: ParseContext): List<Source>`                                                                      | [FlacReader](FlacReader.md)                             |
| `OggReader()`                                                                                                    | [OggReader](OggReader.md)                               |
| `matches(header: ByteArray, hint: String?): Boolean`                                                             | [OggReader](OggReader.md)                               |
| `read(context: ParseContext): List<Source>`                                                                      | [OggReader](OggReader.md)                               |
| `Mp4Reader()`                                                                                                    | [Mp4Reader](Mp4Reader.md)                               |
| `matches(header: ByteArray, hint: String?): Boolean`                                                             | [Mp4Reader](Mp4Reader.md)                               |
| `read(context: ParseContext): List<Source>`                                                                      | [Mp4Reader](Mp4Reader.md)                               |
| `read(offset: Long, count: Int): ByteArray`                                                                      | [MediaInput](MediaInput.md)                             |
| `id: String; length: Long`                                                                                       | [MediaInput](MediaInput.md)                             |
| `read(offset: Long, count: Int): ByteArray`                                                                      | [FileInput](FileInput.md)                               |
| `close(): Unit`                                                                                                  | [FileInput](FileInput.md)                               |
| `metadata: Map<String, List<String>>`                                                                            | [Source](Source.md)                                     |
| `riffFeatures: RiffFeatures`                                                                                     | [Source](Source.md)                                     |
| `riffFeatures: RiffFeatures`                                                                                     | [Entry](Entry.md)                                       |
| `revision: String`                                                                                               | [MojibakeEncodingDetector](MojibakeEncodingDetector.md) |
| `detect(input: ByteArray): MojibakeEncodingDetection`                                                            | [MojibakeEncodingDetector](MojibakeEncodingDetector.md) |

Built-in readers implement `name`, `matches(header, hint)` and `read(context)`. Input
implementations provide `id`, `length` and `read(offset, count)`; `FileInput` also closes its
handle. `Source.metadata`, `Source.riffFeatures` and `Entry.riffFeatures` are computed projections.
See their class pages for exact behavior.
