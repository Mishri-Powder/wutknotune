# Cuetree identity and source access

## Common IDs and lookup

Every catalog record uses the same pair of signed-storage 64-bit components: `hi` and nullable `lo`.
Absent low is distinct from numeric zero. Non-media records use `(hi, null)` with IDHI; folders,
albums, artists and genre-view records receive random high IDs on admission. Their namespaces are
separate. Attributes use their specified content hash. One comparator orders signed high, then
absent low before every present signed low. One binary-range lookup handles equal IDs; repeated
media IDs retain separate source records.

Every file or fragment initially receives computed high, absent low and SHRT. When another source
repeats high, compute low for both original and contender, updating both records; reuse already
computed lows. Do not read low in the collision-free common path. An inaccessible original keeps its
short identity; the accessible contender may retain its computed low with RESL. Access failure must
not assign an unresolved new contender the original's short reference. Retry unresolved retained
sources on subsequent scans.

## Short ID alias

Lookup accepts an exact pair; a requested absent low also resolves through SHRT to the original
logical media identity with equal high. Every initially short file/fragment has SHRT. After a
collision computes low for both original and contender, the original retains SHRT with its full ID
permanently while that logical identity is retained. The contender does not inherit the older short
reference. RESL marks pending low computation and clears when resolution completes; SHRT does not
clear merely because every low is known. At most one distinct full identity in a high bucket may own
its older short alias. Equal-full-ID copies may provide alternate physical sources for that
identity. If the flagged physical occurrence expires, its SHRT alias transfers only to an equivalent
surviving full-ID occurrence, never to a different low; without an equivalent identity the short
reference becomes unresolved. Do not assign a former short alias to a new high-colliding identity.
Internal references are updated to the corresponding full pair, but external durable references
retain the MediaID with which they were written. Equal full pairs do not collapse source metadata.

## One parent reference

Each MDIA record stores one nullable parentId using the common ID type. A root DIRECTORY has no
parent. Other D/M and hidden metadata records reference a DIRECTORY ID; F references the base MEDIA
ID. Fragment nesting is not admitted in this edition. No separate baseId, persisted parent index or
origin string exists. The first available matching MEDIA supplies fragment audio; path failures
allow trying the next copy. Own fragment metadata and bounds remain unchanged. Physical D/M
availability propagates top-down.

Physical source data is a separate SourceInfo table: name, optional modification mark/size, and root
provider/locator. D/M and hidden metadata records own this data; F carries metadata and boundaries
instead. Root labels use SourceInfo.name. Handles are transient. Runtime parent/children indexes
resolve IDs and are rebuilt from each snapshot without consulting the filesystem.

Fragment observations are matched by base ID, bounds and exact attributes, without physical CUE
provenance. Equivalent observations from another readable copy refresh the same retained facts;
repeated source occurrences are allowed. An unobserved fragment keeps its generation and becomes
unavailable until renewed observation or TTL expiry. Accessible base audio alone does not invent or
refresh a removed chapter observation. Playback resolves the retained fragment through its parent
ID. Both begin/end are nullable; absent begin imposes no explicit lower bound (playback starts at
zero), absent end imposes no explicit upper bound. Explicit zero is preserved in storage.

## Pluggable computation

The caller supplies MediaIdGenerator and its stable scheme identifier is persisted and checked on
reload. Android encoded-audio computation and SAF are supplied by the Riff integration adapter,
outside this independent library. MockMediaID streams whole file bytes through XXH3-64; low uses its
distinct fixed seed. Fragment mocks include complete file bytes and span framing. They are host
experiments, not production audio equivalence. No identity implementation from Riff is copied.

## Storage and access

Only roots persist POSIX paths or grant locators. Descendants store names and parent references;
opaque provider handles are transient. A RootSpec may supply an opaque stable root identity
independent of its provider access locator. When present, the identity is persisted on the root and
matches a prior root across locator changes; a prior root without identity may adopt one at the same
locator. A changed identity at the same locator is a new root. Without one, independent providers
may match provider and locator. Riff's Android adapter always supplies its separately resolved
source-root UUID; it never uses a SAF URI as that identity. Root identity is separate from Media.ID,
and the current locator supplies access. Resolve actual children down from the root, never
concatenate document IDs. Complete directory listings or errors are required; duplicate sibling
names fail resolution. POSIX stays within roots and does not follow symbolic links.

MVP reconciliation treats an unrecognized move as new admission with identity computation. The old
source remains unavailable until ordinary TTL. No tree-diff, size/name-based continuity or special
recovery is required.

A root may additionally declare static Book intent through `RootSpec.book`; root MediaKind=2 retains
it while unavailable. It is content evidence, not identity or a Player profile override.
