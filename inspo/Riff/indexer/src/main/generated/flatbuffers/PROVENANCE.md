# FlatBuffers Kotlin source provenance

- Upstream: `google/flatbuffers` tag `v25.12.19`
- Binding generator: `flatc --kotlin-kmp`
- Runtime: upstream `kotlin/flatbuffers-kotlin` common/JVM source
- License: Apache-2.0; exact upstream `LICENSE` is adjacent

`ByteArray.kt` mechanically combines the upstream common `expect` declarations with the upstream JVM
`actual` implementations so the runtime compiles in the existing Android/JVM module. No FlatBuffers
Java binding or Java runtime source is included.
