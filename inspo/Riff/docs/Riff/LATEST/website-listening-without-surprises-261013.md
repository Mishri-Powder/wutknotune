# Plain-language listening explanations — 2026-10-13

The user authorized publication of the adopted everyday explanations on the website and in Wiki,
with commit and push of their sources.

[Russian](https://roadio.link/tldr/ru/listening-without-surprises/) and
[English](https://roadio.link/tldr/listening-without-surprises/) describe separate indexing,
validated catalog snapshots, saved listening positions and fixed playback queues through ordinary
situations. They explain existing behavior without adding requirements. The corresponding
explanatory sources are [Russian](/Riff/LATEST/tldr-listening-without-surprises-ru) and
[English](/Riff/LATEST/tldr-listening-without-surprises-en).

The four phrases on the homepage link directly to the four article sections in the selected
language. Both TL;DR indexes include the topic and preserve the existing IPC and probabilistic
selection articles. Canonical/alternate language metadata and contents links are static; the article
text and RU/EN navigation work without JavaScript. The complete sitemap retains its 217 previous
entries and adds both article routes.

## Source and verification

Commit **ef16165be3d2f507250b2bc2676d0a1291be2dc3** is pushed on main. Its eight-file scope contains
the two Markdown sources, one common topic renderer and metadata, its build guide, an ignore-rule
exception and the explicitly pinned existing Markdown parser. No secrets, device data or generated
site files are committed.

The renderer uses the deployed TL;DR shell and shared styling. It preserves older immutable assets
and generates a new content-named homepage script. Markdown lint and current references pass; Node
syntax, actual rendered content, language switching, all four section/contents anchors, homepage RU
links and repeated generation pass. Both articles and the Russian homepage have no horizontal
overflow at a 320-pixel browser viewport.

Eight exact changed files are installed on the origin with atomic individual replacements,
comparison against outgoing hashes and private rollback copies. All eight public HTTPS file
responses returned HTTP 200 and matched current content, accounting only for Cloudflare service
insertions. The published Russian article was checked in the browser. Mutable responses retain
five-minute browser/seven-day edge cache policy; the content-named script retains one-year immutable
policy.

The authenticated Cloudflare configuration page confirmed receipt of a cache purge. After that
confirmation, the homepage, Russian homepage query, both TL;DR indexes and both article routes
returned exact current content over public HTTPS. Each repeated request was a cache HIT on the same
PDX edge connection. Six routes and twelve responses were checked. This verifies the observed edge;
no exhaustive all-edge measurement is claimed.

Android behavior, builds, installations and existing APK/source distributions are unchanged by this
content publication. Current runtime ownership and behavior remain under
[architecture](/Riff/LATEST/runtime-architecture),
[immutable publication](/Riff/LATEST/immutable-publication),
[checkpoints](/Riff/LATEST/player-chapters) and [queues](/Riff/LATEST/player-queues).

Canonical inputs, before/after inventories, checks, deployment receipts and the purge confirmation
and URL inventory are under status/website-everyday-tldr-261013/. Rollback copies remain private on
the website server. Wiki publication uses exact archives, public API readback, anonymous HTTP/title
verification and a whole-Riff visibility audit.
