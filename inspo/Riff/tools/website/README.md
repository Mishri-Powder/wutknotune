# Static TL;DR topics

The bilingual article sources live in docs/Riff/LATEST. Topic metadata under topics/ points to those
sources and supplies page summaries and optional links from the homepage. One renderer uses the
existing deployed TL;DR shell and the Markdown parser pinned by tools/documentation.

From the repository root:

```sh
npm ci --prefix tools/documentation --ignore-scripts
node tools/website/build_tldr.mjs --site /absolute/path/to/staged-site --topic tools/website/topics/listening-without-surprises.json
```

The staging tree must contain the current homepage, assets/site.js, both TL;DR indexes and both
player-indexer article shells. The renderer adds the topic, updates the indexes and homepage links,
and writes a new content-named homepage script. Existing immutable asset files are preserved.

Before deployment, review the source and generated pages, check local links, anchors, language
navigation and narrow layouts, and add the new routes to the complete sitemap. Publish changed files
with a private rollback copy of outgoing bytes. Unless the user asks to preserve the cache, purge
affected mutable Cloudflare URLs. Verify both exact origin content and current public responses;
record stale edge responses separately when no purge is requested. Site delivery does not publish
Android artifacts or change application behavior.

A topic may list repository-owned UTF-8 assets with content-named `target` paths under assets/ and
optional `stylesheets` referring to those targets. The renderer copies them before generating the
article. Static SVG diagrams and transition tables remain readable without JavaScript.

The public renderer removes internal documentation link wrappers while retaining readable labels.
For existing static pages, run
`node tools/website/update_public_copy.mjs --site /path/to/staged-site` before verification. Release
APKs and their matching immutable source artifacts are outside this copy-only operation.
