# ReplayGain and AI storage procedure

> Owner/scope: optional MDRG snapshot publication and delivery

## RG-6. One independent MDRG database

Indexer writes one full immutable [MDRG](format-mdrg.md) snapshot for both AI and ReplayGain. Its
signed-sorted unique records use the Cuetree MediaID with which each identity first entered MDRG,
including a nullable low. A later Cuetree collision does not rewrite a stored short key; the
original identity's retained SHRT alias resolves it. Each FILM and FILF is a separate analysis
identity. Records have independent gain and AI completion/failure state; numeric zero is present
data.

MDRG has its own strictly increasing generation, independent of every Cuetree database. Each changed
checkpoint (first due after 30 seconds, subsequent due after 60 seconds) and changed final flush
uses a new generation. An unchanged snapshot is not published. The selected Indexer root owns an
atomic CURRENT pointer to a complete create-only file, an exact eight-byte XXH3 sidecar, and the two
latest valid generations. The durable last-generation marker prevents reuse after a damaged current
file. The PREVIOUS pointer to the accepted prior file is durably replaced before CURRENT switches to
the new validated file. A reader falls back only to the checksum-verified PREVIOUS named by that
pointer. Uncommitted staging files are never promoted by directory enumeration. Explicit AU/IA
rebuilds preserve the independent generation watermarks; they clear content, not the allocator.
Descriptor metadata includes type, version, generation, length and XXH3 checksum. Optional MDRG
never delays Cuetree activation.

Player accepts each ready valid MDRG generation independently. Missing or invalid MDRG, unresolved
short alias, absent record or absent selected gain does not block playback and yields exactly 0
dB/factor 1.0. N omits the custom processor; I/F follow
[ReplayGain playback](replaygain-playback.md). Stored peaks do not affect current playback. Player
consumes Indexer's ready AI genre projection and does not construct one.
