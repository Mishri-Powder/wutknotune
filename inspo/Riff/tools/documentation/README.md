# Documentation checks

Use Node.js 22+ from the repository root:

```sh
npm ci --prefix tools/documentation --ignore-scripts
npm run lint --prefix tools/documentation
npm run format --prefix tools/documentation
```

The check covers every tracked or unignored first-party Markdown document. Immutable `references/`,
historical `LEGACY/`, and vendored `compact_enc_det/` are deliberately not rewritten or counted as
maintained documentation. Generated build outputs and machine-local status artifacts are ignored by
Git.

To check an exported current Wiki tree as well:

```sh
npm run lint --prefix tools/documentation -- /absolute/path/to/wiki/Riff/LATEST
```

The optional directory is recursive. Do not point the formatting command at immutable references,
archived editions or historical Wiki trees. Wiki export and publication still follow the publication
gate in [documentation governance](https://wiki.271.pics/Riff/LATEST/documentation-governance).

The checker passes exact Git-selected file paths directly to the Markdownlint API and retains the
rule configuration in .markdownlint-cli2.jsonc. No glob/config CLI wrapper is required. The KaTeX
transitive override keeps the math parser on its reviewed security-fixed stable release.

Prettier provides consistent CommonMark-compatible formatting. Markdownlint runs its default rules
except line length (long URLs and tables are valid Markdown), repeated headings in different
sections, and the small explicit set of semantic HTML elements in the root configuration. A
successful check requires both tools to pass.

## Current contract references

Export current Wiki pages into `<tree>/<page-path>.md`, then run:

```sh
npm run references --prefix tools/documentation -- --wiki-tree /absolute/path/to/current-wiki
```

The offline check validates local document links, current Wiki targets and section anchors in
maintained Markdown and source comments. It rejects retired artifact and pipeline names. The export
is a verification input; the Wiki registry remains the authority. Archive and vendor bytes are
excluded. Refresh the export after publishing contract changes.
