# Riff website — RC 261013

The public [roadio.link website](https://roadio.link/) now distributes signed **0.1.2-rc**,
versionCode **5**, edition **RC 261013**, and a matching clean cut of the current main source. The
English/Russian homepage, source browser, download metadata and checksums agree on the edition. The
existing screenshot gallery remains explicitly labelled RC 261010; no new capture is claimed.

## Downloads and source

The APK is the exact installed artifact from the
[Album V2 delivery](/Riff/LATEST/album-v2-delivery-261013). Its SHA-256 is
4696cd8fff0b63a5d8b4ff8bd14c552b674ed56632072b000f7f75098b5ea7f6, and its signing certificate is
unchanged. No new Android build or installation was needed.

The source includes current implementation, uncommitted authorized fixes, tests, build support,
runtime libraries, assets, current documentation and required license notices. Local credentials,
signing keys, device evidence, generated builds, original Git metadata, historical pipeline trees,
unrelated research and duplicate unused vendor code are excluded.

The source archive and read-only Git export contain one current tree. Git has exactly one fresh root
commit, on main, with no original history. Publishing this isolated snapshot does not modify the
canonical development repository's branch, commits or remote. The current public snapshot identity,
file count, archive size and SHA-256 are owned by
[snapshot.json](https://roadio.link/downloads/snapshot.json), rather than copied into this record.

[APK metadata](https://roadio.link/downloads/apk-release.json) links the content-named APK, source
snapshot and Cuetree format 10. [SHA256SUMS](https://roadio.link/downloads/SHA256SUMS) owns both
distribution checksums. The source browser and complete GPL/third-party texts remain available. The
sitemap was regenerated from the actual current public tree; robots.txt retains its allow rule and
sitemap reference.

## Cache policy and verification

Every successful website file is eligible for Cloudflare caching, including HTML, JSON, Markdown,
plain-text source, checksums, Git metadata/objects and the distribution downloads. The active Cache
Everything rule covers all incoming website requests. Edge TTL respects origin Cache-Control; its
former fixed status-200 override was removed. Both the rule's browser TTL and the zone's Browser
Cache TTL respect existing origin headers. Development Mode remains off.

nginx supplies public, max-age=300, s-maxage=604800 for mutable pages, documentation, JSON and Git
HEAD/refs/objects-info. Content-named APK/source archives, script/style assets and hash-named Git
objects use public, max-age=31536000, immutable. Error responses use no-store. APK locations retain
the same cache/security headers as the rest of the site.

Old CDN entries were purged after publication. A later update must use new content-named artifact
URLs and purge mutable URLs; CDN purge cannot remove copies already stored in visitors' browsers.
Cache TTL is a freshness policy, not a guarantee that Cloudflare retains every object for that
entire time.

Origin verification covers exact file bytes and cache headers, full APK/archive hashes, valid
Origin-CA HTTPS, removed-file 404/no-store and an actual HTTP Git clone with one commit and the
exact current tree. Public verification checks actual CF-Cache-Status HIT in repeated requests to
the same edge. Comparing separate fresh connections can produce cold MISS responses from different
edge locations and does not demonstrate a cache defect. Cloudflare's service-only HTML insertions
are accounted for separately; distribution and raw-source bytes remain exact.

The previous website/configuration are retained privately for rollback. Canonical preparation,
inventories, checks, cache-setting readback and deployment evidence are under
status/website-261013/. See
[Cloudflare cache eligibility](https://developers.cloudflare.com/cache/concepts/default-cache-behavior/)
and [origin cache-control](https://developers.cloudflare.com/cache/concepts/cache-control/) for the
CDN behavior reviewed for this publication.
