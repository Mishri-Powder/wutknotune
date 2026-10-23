# Id3MetadataBudget

`link.roadio.cuegrain.Id3MetadataBudget` owns portable validation of an ID3 header before a caller
or format reader allocates the declared tag body. It has no Android or Media3 dependency.

`Id3MetadataBudget.bodySize(header: ByteArray, offset: Int = 0, maximumTagBytes: Int = Limits().metadataBytes, remainingSourceBytes: Long? = null): Int`
requires a complete ten-byte ID3 header at the supplied buffer offset. The caller skips non-ID3
windows. A matching header must have a supported version and valid synchsafe size bytes; the
declared total, including a v2.4 footer when present, must fit the caller's positive metadata
allocation budget and any known remaining source length. Reject malformed, overflowing or oversized
declarations before allocating or reading the body. Numeric decoding and bounds are shared with
Cuegrain's reader rather than copied into application adapters.

Consumers may apply it to actual extractor header peeks. Riff wraps only the stock Media3 MP3
extractor input during source admission and checks its ten-byte header peek before exposing bytes
that would cause a large allocation. This changes no codec, media identity or tag source priority.
It must not scan arbitrary decoded audio for ID3-like substrings. Fixture tests cover malformed high
size bits, oversized tiny inputs, valid boundaries and ordinary non-ID3 headers.
