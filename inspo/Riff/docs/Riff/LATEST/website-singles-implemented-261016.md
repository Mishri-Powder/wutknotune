# Implemented Singles FSM website — 2026-10-16

This documentation-only delivery explains the album/Singles mechanism already implemented in
`0.1.8-singles-alpha` in branch `experiment/singles-fsm-261016`. Source articles now live under
`docs/Riff/LATEST`, rather than describing pending work in proposals. The old proposal sources
remain historical inputs; the active topic no longer renders them.

## Published mechanics

The [English article](https://roadio.link/tldr/albums-singles-fsm/) and
[Russian article](https://roadio.link/tldr/ru/albums-singles-fsm/index.html) retain a formal EFSM,
transition tables and static SVG overview. They explain unchanged weighted global grouping;
conservative filename evidence including iTunes and unsupported-WMA sibling counts; exact terminal
version bases; the implemented DJ-set metadata heuristic; whole-credit routing and qualification;
stable personal/global basket identities; FILF ownership; promotion and expiry-triggered
reclassification. The linked [Singles](/Cuetree/singles) and [filename](/Cuetree/filename-evidence)
owners remain normative.

The public Android download and public source/Git export remain the authorized RC 261015 edition.
Only explanatory website content, indexes, homepage topic text and its content-named assets changed.
The article explicitly distinguishes the experimental implementation from that public APK.

## Scope and verification

Nine changed files: two article pages, two TL;DR indexes, the homepage, its mutable/content-named
scripts and two content-named SVGs. The existing CSS, other articles, APK, archives, public source,
Git refs and sitemap routes remain byte-identical. Deployment checked outgoing hashes, kept private
rollback bytes and atomically replaced each file, publishing assets first.

Fresh checks pass bilingual links, all twelve stable section anchors, four tables per article,
static SVG XML, idempotent generation, Markdown checks, local links and current Wiki references.
Both languages render at a 320 px viewport with document/scroll width 305 px, loaded diagrams and
contained table scrolling. The default desktop preview and the public Russian article were also
visually inspected.

Origin exact coverage is **9/9**. Direct public file URL coverage is **9/9**, HTTP 200, with only
known Cloudflare challenge decoration normalized. Canonical `/`, `/tldr/`, `/tldr/ru/` and the
English article are exact and HTTP 200. The canonical Russian directory URL remains HTTP 200 but
serves the preceding proposal from a CDN HIT; its `index.html` URL is exact and visibly current. No
purge or cache configuration change was made.

## Reproduction and evidence

Source: `~/riff/worktrees/singles-fsm-261016/tools/website/topics/albums-singles-fsm.json` and its
referenced LATEST EN/RU Markdown and assets. The renderer is `tools/website/build_tldr.mjs`; it uses
the already pinned documentation parser. No dependency graph changed. No commit or push occurred.

Evidence and rollback package: `~/riff/status/website-singles-implemented-261016` on the dev box;
origin rollback copies are under the corresponding private origin task directory. This includes the
live-before archive, stage checks, the exact deployment manifest/receipt and origin/public
responses. Device-derived track data is absent from these published pages.
