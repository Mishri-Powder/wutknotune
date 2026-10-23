# Cuetree API — source access

## SourceProvider

Implement `id: String` and:

| Method                            | Result and responsibility                                                                             |
| --------------------------------- | ----------------------------------------------------------------------------------------------------- |
| root(locator: String)             | SourceHandle for a configured root                                                                    |
| stat(handle: SourceHandle)        | SourceStat; missing/access failures throw IOException                                                 |
| children(directory: SourceHandle) | Complete List&lt;Child&gt; with optional observed child stats; errors must not return partial success |
| open(media: SourceHandle)         | Caller-owned OpenMedia                                                                                |

`SourceHandle` is an opaque marker interface. Handles live for an access attempt and are never
stored in MDIA. A SAF adapter can use actual provider document IDs here.
`Child(name: String, handle: SourceHandle, stat: SourceStat? = null)` contains one display component
and an optional stat observed in the same directory listing. Duplicate sibling names are ambiguous.
The scanner uses a listed stat without querying that child; when absent, it requests one initial
stat. It does not repeat stat after reading media or identity. Android SAF supplies every child stat
from the listing. `SourceStat(directory: Boolean, stamp: Stamp, mediaCandidate: Boolean? = null)`
distinguishes folders/files. A provider may supply explicit regular-file media admission; null uses
the scan extension policy. Android supplies concrete SAF audio-MIME candidacy. CUE sidecars retain
their separate metadata admission. `Stamp(modified: Long, size: Long)` requires nonnegative size.

`OpenMedia` combines Cuegrain's MediaInput with Closeable. It exposes id, length and
`read(offset, count): ByteArray`. Reads must return exactly count bytes or fail; the caller owns
closure. The public input is shared with Cuegrain and the identity generator.

## PosixProvider

`PosixProvider()` has provider ID `posix`. Its locator is a root path; stats use mtime in
milliseconds and size. It admits regular files/directories, ignores symbolic children and refuses
symbolic roots. File reads use position-independent channel access. It is a host/debug adapter, with
no Android dependency.

`RootSpec(provider, locator, label = locator, identity = null, initialStat = null)` defines one
configured root. `initialStat` is a transient stat captured during root discovery; the scanner uses
it without querying the root again. When absent, the scanner requests one root stat. `identity` is
an optional nonblank, opaque stable root key, independent of the locator used by the provider for
access. Cuetree persists it as the root `sourceRootIdentity` attribute and matches that key across
locator changes. A matching old root without this key adopts it at the same locator; a changed key
starts a new root. Providers without a separate key match by provider and locator. Only the root
SourceInfo persists a locator; nonroot D/M records persist names and directory parent IDs.

## SourceResolver and ResolvedSource

Construct `SourceResolver(providers: List<SourceProvider>)`.
`resolve(snapshot, index, cancellation = Cancellation()): ResolvedSource` requires an M/F source
index. For F it looks up `parentId` and tries available MEDIA copies in catalog order, preserving
the fragment's own span. A failed path resolution advances to the next matching copy; different full
IDs cannot substitute for the base. The stored parent is a logical media identity. The runtime
parent index selects an available matching occurrence and is not persisted. For each candidate it
collects component names upward to the root, then requests actual child handles downward. It never
concatenates provider document IDs and never trusts a persisted descendant URI. Missing or ambiguous
names produce IOException; cancellation propagates.

`ResolvedSource(provider, handle, span)` contains a transient provider handle and optional fragment
span. `open(): OpenMedia` transfers closure responsibility to the caller:

```kotlin
val candidates = snapshot.sources(mediaId).filter { snapshot.nodes[it].life.available }
val source = resolver.resolve(snapshot, candidates.first())
source.open().use { input ->
    val header = input.read(0, minOf(16L, input.length).toInt())
    println(header.size)
}
println(source.span)
```

A cached available flag is display state from the last scan, not a guarantee of current filesystem
access. Playback can try another candidate if opening fails after resolution; `resolve` itself only
retries path/stat failures. Playback resolution requests a fresh terminal stat after walking the
path; the stable-scan assumption does not apply to playback. Resolution itself does not refresh
persisted TTL.
