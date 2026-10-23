# Albums and Singles FSM on the website — 2026-10-16

Published the proposed album/Singles state machine in the website TL;DR section:
[Russian](https://roadio.link/tldr/ru/albums-singles-fsm/) and
[English](https://roadio.link/tldr/albums-singles-fsm/). The explanatory sources remain explicitly
non-normative proposals: [Russian](/Riff/proposals/albums-singles-fsm-ru) and
[English](/Riff/proposals/albums-singles-fsm-en).

The FSM retains the complete existing RIFF-ALBUM-V2 grouping, including AG-9 weights, threshold 65,
and complete linkage. New Singles predicates classify finished globally assembled groups; they do
not replace weighted grouping or finalize short provisional subgroups early. iTunes tag, folder and
numbering evidence remains usable; inferred filename ordinals are supplementary, not a new admission
prerequisite. No new Singles proof leaves an otherwise admitted album as an album.

The articles define classifier states, ordered guards, actions, title-base matching, artist-basket
qualification, independent availability and atomic publication. FILF ownership precedes Singles
heuristics; containers never duplicate their fragments. Retained missing positions prevent premature
album demotion until publication-generation TTL. A general DJ-set recognizer remains an explicit
implementation gap, not an inference from artist names or the word Live.

Both articles include a static SVG, four transition/predicate/scenario tables and six linked
sections. Both TL;DR indexes and the homepage summary link the topic; the sitemap includes both
routes. Article text, diagrams and language navigation require no JavaScript. The shared renderer
accepts topic-owned static assets and scoped stylesheets.

## Verification and delivery

The pinned formatter and Markdown checks pass. Repeated rendering produces identical bytes. Local
links, section anchors, language links, SVG structure and sitemap membership pass. The initial
browser review covered both languages at 320 pixels without page overflow; wide tables scroll within
their own bounds. That earlier browser evidence is distinct from the latest text clarification.

The weighted-grouping clarification changed four website files: both articles and two content-named
diagrams. Guarded individual atomic replacements checked outgoing hashes and saved private rollback
copies. All four origin files and four public HTTPS file responses match staged content, accounting
only for Cloudflare service insertions. Canonical directory-route checks find the homepage, both
indexes and English article current; the Russian article directory route still serves an older
cached edition. No cache purge was requested or performed. Existing cache policies remain.

Android behavior, device files, APK/source downloads, normative grouping contracts and Git history
are unchanged. No private library dumps or device inventories were published with the articles.
Initial publication evidence is under status/website-singles-fsm-261016; the clarification receipts
are under status/website-singles-weighted-261016 on the dev box. This is a documentation delivery,
not implementation of the proposed Singles behavior.
