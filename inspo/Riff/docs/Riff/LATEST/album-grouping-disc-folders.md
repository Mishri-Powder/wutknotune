# Riff Album grouping contract — AG-8 Disc-folder families

> Owner/scope: RIFF-ALBUM-V2 transient directory evidence; no tag inference or source mutation.

## Marked disc names

Recognize standalone CD, Disc, Disk and Диск markers without case sensitivity, followed by one
positive decimal disc number. Whitespace, hyphens, underscores and periods between marker and number
are equivalent; enclosing parentheses/brackets are permitted. Leading zeroes do not change the
number. Require a right token boundary after the number; a following letter invalidates the
interpretation. Do not recognize a marker embedded in an unrelated word. CD 3T is not disc 3. A
rejected marked expression cannot re-enter as a disc through the unmarked numeric fallback.

CD1, CD 1, CD-1, CD_01, CD.01, [CD1], (Disc 1), Disk-01 and Диск 1 describe the same disc ordinal.
Mixed spellings such as CD1, CD-2, [Disc 03], Диск 4 can form one family. Normalize the disc
expression, not arbitrary punctuation or semantic text in the remaining name. Album CD1 and Album
CD-2 agree; Album CD1 and Album Remastered CD2 do not agree solely through directory evidence.

With an explicit disc marker, unrelated year, MP3 or edition-number components are not candidate
disc ordinals. Do not guess among multiple conflicting marker/number interpretations.

## Family boundary and numeric order

A family contains sibling directories under one physical immediate parent with the same canonical
remaining name and distinct positive disc ordinals. Evaluate all members together; never choose a
family by successive pairs or lexical ordering. CD2 precedes CD10. Family size is not limited to
two.

Incomplete sequences such as CD1/CD3 or CD2/CD3/CD4 remain eligible. Missing discs are not
fabricated. Neither observed folder count nor largest ordinal supplies Total Discs.

Different parents or media roots never form one folder family solely from similar names. Global
metadata grouping across those boundaries remains allowed.

Unmarked numeric names, such as 01/02, retain the strict numeric-template rule: one varying positive
decimal component, same immediate parent, identical canonical nonnumeric pieces and unchanged other
numeric components. Competing overlapping templates remain ambiguous; do not choose by enumeration.

## Album disc suffixes

A validated family may bridge differing nonempty Album tags only when their canonical bases agree
after removal of one terminal standalone marked disc ordinal. Apply the same marker, separator,
bracket and token-boundary rules as folder recognition. The tag ordinal must equal the containing
folder's validated ordinal and must not conflict with an explicit Disc tag or credible bounds. An
unsuffixed Album equal to that common base may participate from another member of the same family.

This is a derived comparison operation scoped to that validated family. Do not remove arbitrary
numbers, subtitles, soundtrack labels, volume names, edition qualifiers or catalog identifiers.
Album CD1 and Album CD2 may agree; Album Original and Album Demos do not. Identical stripped text
outside the corroborating family does not itself authorize a cross-title merge. Preserve the
original tag values and every factual Disc/Total Discs observation.

The common base must be nonempty. A candidate still passes ordinary membership, release, copy and
ambiguity rules; suffix agreement is not sufficient proof of one release. For an admitted combined
album, use the common base as its display title with the ordinary deterministic representative
spelling rule. Persist it only as existing ALBM presentation, never as an inferred MDIA tag or new
identity/provenance field.

## Use and conflicts

A valid folder ordinal may supply a transient disc scope for grouping, slot checks and ordering when
Disc is missing. Track 1 in CD1 and Track 1 in CD2 then occupy different scopes. Do not persist this
ordinal as a read Disc tag or new provenance field.

An explicit Disc conflicting with the folder ordinal disqualifies that directory interpretation. Do
not rewrite the tag, mark the source failed or prohibit independent metadata-based grouping.
Credible declared bounds must still be respected.

CD1 and CD-1 are two folders with the same ordinal, not two consecutive discs. They do not establish
an unambiguous family by themselves. Analyze contents under normal copy/slot rules; do not select
one folder or invent Disc 2. Ambiguous directory evidence never overrides stronger factual metadata.

A common folder or valid family contributes residual +25 once. It can support grouping but cannot
finalize a local album before global comparison, erase strong conflicts, or supply missing Album,
Artist or totals. Metadata-established unambiguous membership survives path relocation; membership
based only on directory evidence has no such guarantee.
