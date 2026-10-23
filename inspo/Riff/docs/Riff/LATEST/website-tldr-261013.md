# Riff website TL;DR

The public website now has a [TL;DR section](https://roadio.link/tldr/) with a
[Russian index](https://roadio.link/tldr/ru/). It offers extended, plain-language explanations
alongside the existing FAQ. Its first article describes how Player and Indexer work together:
[English](https://roadio.link/tldr/player-indexer/) and
[Russian](https://roadio.link/tldr/ru/player-indexer/).

## Content and navigation

The two versions cover exclusive process ownership, AIDL/Binder calls, callback direction,
session-scoped SEQ/ACK delivery, duplicate handling, immutable library cohorts, exact-file read-only
descriptors, publication timing, transient crash-attempt recovery and folder-permission barriers.
They explain the adopted contracts without new requirements or a new implementation claim. The
retained Wiki copies are [English](/Riff/LATEST/tldr-player-indexer-en) and
[Russian](/Riff/LATEST/tldr-player-indexer-ru).

TL;DR is linked from the homepage navigation, a dedicated homepage section, the source browser and
license page. The homepage RU/EN control selects the matching TL;DR routes. Each article and index
has real static language links, canonical/alternate metadata and a contents list where applicable.
Both languages remain readable without JavaScript on their own routes. The sitemap includes all four
new routes.

## Publication and verification

Published incrementally to the existing nginx site. Outgoing pages and sitemap are saved privately
under /home/yegor/nginx/backups/website-tldr-261013. New JavaScript and CSS use content-named URLs;
previous immutable asset bytes were not replaced. Eighteen exact mutable URLs were purged through
Cloudflare's Custom Purge interface, which confirmed success. Existing cache policies were retained.

Verified exact origin content and public cache/security headers, the new sitemap entries, local
website links, Russian homepage navigation, both article language directions and 320-pixel layouts
without horizontal overflow. Public CDN verification compares exact content after accounting only
for Cloudflare service insertions and requires HIT on repeated same-edge requests.

Build inputs, authority snapshots, article sources, deployed-file inventory and verification
receipts are under status/website-tldr-261013. This content update does not alter Android behavior,
downloads, the published code snapshot or canonical Git history.
