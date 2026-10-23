#!/usr/bin/env python3
"""
Merge the Wikidata and MusicBrainz genre graphs into one document.

    musicbrainz_wikidata_merge.py --wikidata wikidata.json \
                                  --musicbrainz musicbrainz.json > merged.json

Identity
--------
The two graphs are joined on the cross-references each side publishes:
Wikidata's MusicBrainz genre ID (P8052) and the Wikidata link on the
MusicBrainz genre page. Both directions are collected, reconciled into a strict
one-to-one matching, and every rejected claim is logged.

Genres known to Wikidata are keyed by their QID. MusicBrainz-only genres get a
synthetic negative QID - Q-1, Q-2, ... - assigned in sorted MBID order so the
keys are stable across runs. Their wikidata_id stays null and synthetic_id is
true, so a synthetic handle is never mistaken for a real entity.

Edges
-----
Every edge is reduced to a canonical form first, so that "A parent B" and
"B child A" are recognised as the same fact regardless of which direction a
source happened to state it in. Union and intersection therefore operate on
facts, and re-expanding at the end guarantees the invariant: an edge is present
in both directions or in neither.

Conflicts
---------
  identity   the two sources cross-reference each other inconsistently
  direction  an antisymmetric relation is asserted both ways
Both are logged and resolved in favour of --priority. Edges present in only one
source are not conflicts - nothing contradicts them - and are governed by
--edges instead.

Names are not a conflict either. There is no "name" field; every genre carries
labels[lang] as an array - the primary Wikidata label first, then its official
skos:altLabel aliases - and a MusicBrainz name not already among them is
appended rather than resolved away. Names equal apart from case are treated as
the same name, so a MusicBrainz name matching any recorded alias adds nothing.

Requires: nothing beyond the standard library
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
import time
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from project_config import GENRE_LOCALES  # noqa: E402

LOG = logging.getLogger("musicbrainz_wikidata_merge")

WIKIDATA = "wikidata"
MUSICBRAINZ = "musicbrainz"

# Full relation vocabulary across both sources.
INVERSE_RELATION: dict[str, str] = {
    "parent": "child",
    "child": "parent",
    "part_of": "has_part",
    "has_part": "part_of",
    "influenced_by": "influenced",
    "influenced": "influenced_by",
    "based_on": "basis_for",
    "basis_for": "based_on",
    "inspired_by": "inspired",
    "inspired": "inspired_by",
    "follows": "followed_by",
    "followed_by": "follows",
    "facet_of": "has_facet",
    "has_facet": "facet_of",
    "fusion_of": "has_fusion_genre",
    "has_fusion_genre": "fusion_of",
    "same_as": "same_as",
    "different_from": "different_from",
}

# One name per inverse pair is chosen as canonical; edges stated the other way
# round are flipped onto it. Symmetric relations are canonicalised by sorting
# their endpoints instead.
CANONICAL_RELATIONS = {
    "parent",
    "part_of",
    "influenced_by",
    "based_on",
    "inspired_by",
    "follows",
    "facet_of",
    "fusion_of",
}

# Relations where asserting both directions is a contradiction rather than a
# fact about the world. Mutual influence is real and common, so influenced_by
# and inspired_by are deliberately absent.
ANTISYMMETRIC_RELATIONS = {
    "parent",
    "part_of",
    "facet_of",
    "fusion_of",
    "follows",
    "based_on",
}

Edge = tuple[str, str, str]


# ==========================================================================
# Labels
# ==========================================================================


def normalise_labels(raw: dict | None) -> dict[str, list[str]]:
    """Normalize supported locale arrays, dropping case-insensitive duplicates."""
    out: dict[str, list[str]] = {}
    for lang, value in (raw or {}).items():
        if lang not in GENRE_LOCALES:
            continue
        if not isinstance(value, list):
            raise ValueError(f"Genre locale {lang!r} is not a name list")
        values = value
        bucket: list[str] = []
        seen: set[str] = set()
        for item in values:
            if item and item.casefold() not in seen:
                seen.add(item.casefold())
                bucket.append(item)
        if bucket:
            out[lang] = bucket
    return out


def has_label(labels: dict[str, list[str]], lang: str, value: str) -> bool:
    """Case-insensitive membership test, so Jazz and jazz are the same name."""
    return any(item.casefold() == value.casefold() for item in labels.get(lang, []))


def append_label(labels: dict[str, list[str]], lang: str, value: str | None) -> bool:
    """Append a name unless an equal-ignoring-case one is already recorded."""
    if not value or has_label(labels, lang, value):
        return False
    labels.setdefault(lang, []).append(value)
    return True


# ==========================================================================
# Canonical edges
# ==========================================================================


def canonicalise(src: str, relation: str, dst: str) -> Edge | None:
    """Reduce a directed edge to the single form that represents its fact."""
    inverse = INVERSE_RELATION.get(relation)
    if inverse is None:
        return None
    if inverse == relation:  # symmetric: order the endpoints instead
        return (src, relation, dst) if src <= dst else (dst, relation, src)
    if relation in CANONICAL_RELATIONS:
        return (src, relation, dst)
    return (dst, inverse, src)


def expand(edges: set[Edge]) -> dict[str, dict[str, set[str]]]:
    """
    Re-expand canonical facts into per-node relations, both directions.

    This is where the invariant is enforced: every fact is written from both
    endpoints, so an edge can never exist one-way in the output.
    """
    relations: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    for src, relation, dst in edges:
        inverse = INVERSE_RELATION[relation]
        relations[src][relation].add(dst)
        relations[dst][inverse].add(src)
    return relations


# ==========================================================================
# Identity reconciliation
# ==========================================================================


def reconcile_identity(
    wd_genres: dict[str, dict],
    mb_genres: dict[str, dict],
    priority: str,
) -> tuple[dict[str, str], dict[str, str], list[dict]]:
    """
    Build a strict one-to-one QID <-> MBID matching from both sides' claims.

    Pairs both sources agree on are accepted first, then pairs claimed only by
    the priority source, then the rest. A pair is skipped when either of its
    ends is already taken, which is what makes the result one-to-one.
    """
    claims: dict[tuple[str, str], set[str]] = defaultdict(set)

    for qid, record in wd_genres.items():
        mbid = record.get("musicbrainz_id")
        if mbid:
            claims[(qid, mbid)].add(WIKIDATA)
    for mbid, record in mb_genres.items():
        qid = record.get("wikidata_id")
        if qid:
            claims[(qid, mbid)].add(MUSICBRAINZ)

    def rank(item: tuple[tuple[str, str], set[str]]) -> tuple[int, str, str]:
        (qid, mbid), sources = item
        if len(sources) == 2:
            score = 0  # agreed by both
        elif priority in sources:
            score = 1  # only the trusted source
        else:
            score = 2  # only the other one
        return (score, qid, mbid)

    qid_to_mbid: dict[str, str] = {}
    mbid_to_qid: dict[str, str] = {}
    conflicts: list[dict] = []

    for (qid, mbid), sources in sorted(claims.items(), key=rank):
        taken_qid = qid_to_mbid.get(qid)
        taken_mbid = mbid_to_qid.get(mbid)
        if taken_qid is None and taken_mbid is None:
            qid_to_mbid[qid] = mbid
            mbid_to_qid[mbid] = qid
            continue

        conflicts.append(
            {
                "type": "identity",
                "claim": {"wikidata_id": qid, "musicbrainz_id": mbid},
                "claimed_by": sorted(sources),
                "rejected_because": (
                    {"wikidata_id": qid, "already_matched_to": taken_qid}
                    if taken_qid is not None
                    else {"musicbrainz_id": mbid, "already_matched_to": taken_mbid}
                ),
            }
        )

    return qid_to_mbid, mbid_to_qid, conflicts


# ==========================================================================
# Edge collection
# ==========================================================================


def collect_edges(
    genres: dict[str, dict],
    key_of: dict[str, str],
) -> tuple[set[Edge], set[str]]:
    """Canonical edge set for one source, with its node ids mapped to merged keys."""
    edges: set[Edge] = set()
    unknown: set[str] = set()

    for node, record in genres.items():
        src = key_of.get(node, node)
        for relation, targets in (record.get("relations") or {}).items():
            for target in targets:
                edge = canonicalise(src, relation, key_of.get(target, target))
                if edge is None:
                    unknown.add(relation)
                    continue
                edges.add(edge)

    return edges, unknown


def resolve_directions(
    provenance: dict[Edge, set[str]],
    priority: str,
) -> tuple[set[Edge], list[dict]]:
    """Drop antisymmetric edges asserted both ways, keeping the trusted side."""
    drop: set[Edge] = set()
    conflicts: list[dict] = []

    for edge in provenance:
        src, relation, dst = edge
        if relation not in ANTISYMMETRIC_RELATIONS or src == dst:
            continue
        mirror = (dst, relation, src)
        if mirror not in provenance or mirror in drop or edge in drop:
            continue

        here, there = provenance[edge], provenance[mirror]
        if priority in here and priority not in there:
            loser, winner = mirror, edge
        elif priority in there and priority not in here:
            loser, winner = edge, mirror
        else:
            # Both sides carry equal authority - a cycle inside one source, or
            # both sources contradicting themselves the same way. Keep both and
            # report; silently picking one would invent an ordering.
            conflicts.append(
                {
                    "type": "direction",
                    "relation": relation,
                    "between": [src, dst],
                    "asserted_by": {
                        f"{src}->{dst}": sorted(here),
                        f"{dst}->{src}": sorted(there),
                    },
                    "resolution": "kept both, no source has priority here",
                }
            )
            continue

        drop.add(loser)
        conflicts.append(
            {
                "type": "direction",
                "relation": relation,
                "between": [src, dst],
                "asserted_by": {
                    f"{src}->{dst}": sorted(here),
                    f"{dst}->{src}": sorted(there),
                },
                "resolution": f"kept {winner[0]}->{winner[2]} per --priority {priority}",
            }
        )

    return drop, conflicts


# ==========================================================================
# Assembly
# ==========================================================================


def merge(args: argparse.Namespace) -> dict:
    started = time.monotonic()

    wd_doc = load_document(args.wikidata, "wikidata")
    mb_doc = load_document(args.musicbrainz, "musicbrainz")
    wd_genres = wd_doc["genres"]
    mb_genres = mb_doc["genres"]
    LOG.info(
        "input: %d Wikidata genres, %d MusicBrainz genres",
        len(wd_genres),
        len(mb_genres),
    )

    qid_to_mbid, mbid_to_qid, identity_conflicts = reconcile_identity(
        wd_genres, mb_genres, args.priority
    )
    LOG.info("matched %d genres across both sources", len(qid_to_mbid))
    for conflict in identity_conflicts:
        LOG.warning(
            "identity conflict: %s claimed by %s, rejected (%s)",
            conflict["claim"],
            "+".join(conflict["claimed_by"]),
            conflict["rejected_because"],
        )

    # Merged node key: the QID when the genre is known to Wikidata, otherwise a
    # synthetic negative QID - Q-1, Q-2, ... Assignment walks the MusicBrainz
    # ids in sorted order, so identical inputs always yield identical keys and
    # two runs stay diffable. Names are deliberately not used for the ordering:
    # they change between scrapes and would reshuffle every key.
    wd_key = {qid: qid for qid in wd_genres}
    mb_key: dict[str, str] = {}
    synthetic_count = 0
    for mbid in sorted(mb_genres):
        matched = mbid_to_qid.get(mbid)
        if matched is not None:
            mb_key[mbid] = matched
        else:
            synthetic_count += 1
            mb_key[mbid] = f"Q-{synthetic_count}"
    key_to_mbid = {key: mbid for mbid, key in mb_key.items()}

    if synthetic_count:
        LOG.info(
            "assigned synthetic ids Q-1..Q-%d to %d MusicBrainz-only genres",
            synthetic_count,
            synthetic_count,
        )

    wd_edges, wd_unknown = collect_edges(wd_genres, wd_key)
    mb_edges, mb_unknown = collect_edges(mb_genres, mb_key)
    for relation in sorted(wd_unknown | mb_unknown):
        LOG.warning("unknown relation %r has no inverse defined, skipped", relation)
    LOG.info(
        "canonical edges: %d Wikidata, %d MusicBrainz, %d shared",
        len(wd_edges),
        len(mb_edges),
        len(wd_edges & mb_edges),
    )

    provenance: dict[Edge, set[str]] = defaultdict(set)
    for edge in wd_edges:
        provenance[edge].add(WIKIDATA)
    for edge in mb_edges:
        provenance[edge].add(MUSICBRAINZ)

    drop, direction_conflicts = resolve_directions(provenance, args.priority)
    for conflict in direction_conflicts:
        LOG.warning(
            "direction conflict on %s between %s: %s",
            conflict["relation"],
            " and ".join(conflict["between"]),
            conflict["resolution"],
        )

    # A node is comparable only if both sources know it; edges touching a
    # one-source node can never appear in an intersection, which is worth
    # separating from edges that are genuinely absent on one side.
    comparable = set(qid_to_mbid) | {mbid_to_qid[m] for m in mbid_to_qid}
    both = wd_edges & mb_edges
    only_one = (wd_edges | mb_edges) - both

    if args.edges == "union":
        kept = wd_edges | mb_edges
        not_comparable = absent = 0
    else:
        kept = both
        not_comparable = sum(
            1 for src, _, dst in only_one if src not in comparable or dst not in comparable
        )
        absent = len(only_one) - not_comparable

    kept -= drop
    relations = expand(kept)

    # --- node records ---------------------------------------------------
    genres: dict[str, dict] = {}
    name_conflicts = 0
    matched_via_alias = 0
    name_examples: list[dict] = []

    keys = sorted(set(wd_key.values()) | set(mb_key.values()))
    for key in keys:
        wd_record = wd_genres.get(key)
        mbid = key_to_mbid.get(key)
        mb_record = mb_genres.get(mbid) if mbid else None

        labels = normalise_labels((wd_record or {}).get("labels"))
        mb_name = (mb_record or {}).get("name")

        # A pure case difference is not a disagreement, so "Jazz" against
        # "jazz" is neither counted nor appended. A genuinely different
        # spelling is recorded as an additional English name rather than
        # resolved away: both sources are describing the same genre and both
        # names are things people search for.
        english = labels.get("en", [])
        if english and mb_name and has_label(labels, "en", mb_name):
            # Already known - but was it the primary label or an alias? The
            # latter is a name that would have looked like a conflict before
            # aliases were pulled.
            if english[0].casefold() != mb_name.casefold():
                matched_via_alias += 1
        if english and mb_name and not has_label(labels, "en", mb_name):
            name_conflicts += 1
            if len(name_examples) < args.examples:
                name_examples.append(
                    {"key": key, WIKIDATA: list(english), MUSICBRAINZ: mb_name}
                )
            LOG.debug("name conflict on %s: %s vs %r", key, english, mb_name)

        append_label(labels, "en", mb_name)

        sources = []
        if wd_record is not None:
            sources.append(WIKIDATA)
        if mb_record is not None:
            sources.append(MUSICBRAINZ)

        genres[key] = {
            "sources": sources,
            # wikidata_id stays null on synthetic nodes: the key is a local
            # handle, not a claim that such an entity exists in Wikidata.
            "wikidata_id": key if wd_record is not None else None,
            "synthetic_id": wd_record is None,
            "musicbrainz_id": mbid,
            "labels": {lang: labels[lang] for lang in sorted(labels)},
            "relations": {
                name: sorted(targets)
                for name, targets in sorted(relations.get(key, {}).items())
                if targets
            },
            "links": (mb_record or {}).get("links", []),
        }

    if matched_via_alias:
        LOG.info(
            "%d MusicBrainz names matched a Wikidata alias rather than the "
            "primary label", matched_via_alias,
        )
    if name_conflicts:
        LOG.info(
            "%d genres carry a MusicBrainz name found in neither the Wikidata "
            "label nor its aliases; it is appended to labels.en",
            name_conflicts,
        )

    elapsed = time.monotonic() - started
    endpoints = sum(sum(len(v) for v in g["relations"].values()) for g in genres.values())
    matched = len(qid_to_mbid)

    LOG.info(
        "done: %d genres (%d matched, %d Wikidata-only, %d MusicBrainz-only), "
        "%d edges, %d relation endpoints, %.1fs",
        len(genres),
        matched,
        len(wd_genres) - matched,
        len(mb_genres) - matched,
        len(kept),
        endpoints,
        elapsed,
    )
    if args.edges == "intersection":
        LOG.info(
            "intersection dropped %d edges absent from one source and %d whose "
            "endpoints are not present in both sources",
            absent,
            not_comparable,
        )

    return {
        "meta": {
            "priority": args.priority,
            "edges": args.edges,
            "genres": len(genres),
            "matched_genres": matched,
            "wikidata_only": len(wd_genres) - matched,
            "musicbrainz_only": len(mb_genres) - matched,
            "synthetic_ids": synthetic_count,
            # Always present, empty when nothing was synthesised. Feeding this
            # back on a later run is what keeps Q-N stable as MusicBrainz
            # gains genres; without it the sorted-MBID numbering shifts.
            "carry_ids": {
                mbid: key for mbid, key in sorted(mb_key.items()) if key.startswith("Q-")
            },
            "edges_total": len(kept),
            "edges_in_both_sources": len(both),
            "edges_in_one_source": len(only_one),
            "edges_dropped_absent": absent,
            "edges_dropped_not_comparable": not_comparable,
            "edges_dropped_direction_conflict": len(drop),
            "relation_endpoints": endpoints,
            "identity_conflicts": len(identity_conflicts),
            "direction_conflicts": len(direction_conflicts),
            "name_conflicts": name_conflicts,
            "mb_names_matched_via_alias": matched_via_alias,
            "name_conflict_examples": name_examples,
            "elapsed_seconds": round(elapsed, 1),
            "sources": {
                "wikidata": wd_doc.get("meta", {}),
                "musicbrainz": mb_doc.get("meta", {}),
            },
        },
        "conflicts": {
            "identity": identity_conflicts,
            "direction": direction_conflicts,
        },
        "languages": list(GENRE_LOCALES),
        "genres": genres,
    }


def load_document(path: str, what: str) -> dict:
    if path == "-":
        document = json.load(sys.stdin)
    else:
        with open(path, encoding="utf-8") as handle:
            document = json.load(handle)
    if not isinstance(document.get("genres"), dict):
        raise SystemExit(f'{what} input has no top-level "genres" object')
    return document


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Merge the Wikidata and MusicBrainz genre graphs onto stdout.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--wikidata", required=True, help="genre_graph.py output, or - for stdin"
    )
    parser.add_argument(
        "--musicbrainz", required=True, help="musicbrainz_genres.py output"
    )
    parser.add_argument(
        "--priority",
        choices=(MUSICBRAINZ, WIKIDATA),
        default=MUSICBRAINZ,
        help="which source wins identity and direction conflicts",
    )
    parser.add_argument(
        "--edges",
        choices=("union", "intersection"),
        default="union",
        help="union keeps edges stated by either source; intersection keeps "
        "only edges both state. Affects edges only, never the genre list",
    )
    parser.add_argument(
        "--examples",
        type=int,
        default=20,
        help="differing-name examples to record in meta",
    )
    parser.add_argument(
        "--indent", type=int, default=2, help="JSON indent, 0 for compact"
    )
    verbosity = parser.add_mutually_exclusive_group()
    verbosity.add_argument("-v", "--verbose", action="store_true", help="debug logging")
    verbosity.add_argument("-q", "--quiet", action="store_true", help="warnings only")
    return parser.parse_args(argv)


def main() -> None:
    args = parse_args()

    if args.verbose:
        level = logging.DEBUG
    elif args.quiet:
        level = logging.WARNING
    else:
        level = logging.INFO

    logging.basicConfig(
        level=level,
        # stderr, always: stdout carries the JSON payload.
        stream=sys.stderr,
        format="%(asctime)s %(levelname)-7s %(message)s",
        datefmt="%H:%M:%S",
    )

    result = merge(args)
    json.dump(result, sys.stdout, ensure_ascii=False, indent=args.indent or None)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()