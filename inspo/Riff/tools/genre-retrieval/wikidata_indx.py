#!/usr/bin/env python3
"""
Dump the music-genre graph from Wikidata as JSON on stdout.

Phases:
  1. seed   - one query, the full genre universe
  2. attrs  - batched: English label and MusicBrainz genre ID (P8052)
  3. edges  - batched: outgoing edges between genres
  4. close  - infer every missing inverse edge

Incoming edges are never queried. The universe is closed, so an incoming edge
into A from B is an outgoing edge of B and already arrives in B's batch. That
halves the query volume; directions are reconstructed locally.

JSON goes to stdout, all diagnostics to stderr, so the output can be piped.

Requires: httpx, anyio
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import sys
import time
from collections import defaultdict

import anyio
import httpx

DEFAULT_ENDPOINT = "https://query.wikidata.org/sparql"
ENTITY_PREFIX = "http://www.wikidata.org/entity/"

LOG = logging.getLogger("genre_graph")


# ==========================================================================
# Queries
# ==========================================================================

# Seed. Returns the full genre list with no language constraint whatsoever,
# so graph membership is independent of available display-name languages.
#
# NOTE: in SPARQL "+" binds tighter than "|", so "wdt:P31|wdt:P279+" parses as
# "(wdt:P31) | (wdt:P279+)" - a DIRECT P31 to a root, or a P279 chain, but
# never a mix. An item marked "instance of some rock subgenre" is missed.
# --broad-seed switches to "(wdt:P31|wdt:P279)+", which alternates both
# properties at every step and catches those.
SEED_QUERY_NARROW = """
SELECT DISTINCT ?genreId WHERE {
  VALUES ?root { wd:Q188451 wd:Q11921029 }
  ?genre wdt:P31|wdt:P279+ ?root .
  BIND(STRAFTER(STR(?genre), "http://www.wikidata.org/entity/") AS ?genreId)
}
"""

# The same closure as SEED_QUERY_BROAD, but as one unbounded path expression
# over the whole of Wikidata. It reliably exceeds the WDQS 60s limit and comes
# back as a truncated HTTP 200. Kept only for comparison; prefer "walk".
SEED_QUERY_BROAD = """
SELECT DISTINCT ?genreId WHERE {
  VALUES ?root { wd:Q188451 wd:Q11921029 }
  ?genre (wdt:P31|wdt:P279)+ ?root .
  BIND(STRAFTER(STR(?genre), "http://www.wikidata.org/entity/") AS ?genreId)
}
"""

# One hop down from a frontier of parents. No "+" anywhere, so this is a plain
# indexed lookup that returns in milliseconds regardless of how deep the
# hierarchy goes. Iterating it to a fixed point yields exactly the same set as
# SEED_QUERY_BROAD, decomposed into queries the server can actually finish.
SEED_STEP_QUERY = """
SELECT DISTINCT ?childId WHERE {{
  VALUES ?parent {{ {values} }}
  ?child wdt:P31|wdt:P279 ?parent .
  BIND(STRAFTER(STR(?child), "{prefix}") AS ?childId)
}}
"""

# Attributes. Kept separate from edges on purpose: merged into one query, the
# OPTIONAL blocks would cross-multiply and repeat the label on every edge row.
#
# rdfs:label stays OPTIONAL. A hard join with FILTER(LANG = "en") would
# silently drop every genre without an English label - exactly the
# non-English genres worth keeping. No fallback to other languages is
# attempted: the label is decorative here, the QID is the key.
ATTRS_QUERY = """
SELECT ?genreId ?labelEn ?mbid WHERE {{
  VALUES ?genre {{ {values} }}
  BIND(STRAFTER(STR(?genre), "{prefix}") AS ?genreId)
  OPTIONAL {{ ?genre rdfs:label ?labelEn . FILTER(LANG(?labelEn) = "en") }}
  OPTIONAL {{ ?genre wdt:P8052 ?mbid . }}
}}
"""

# Edges. No "target must also be a genre" filter in SPARQL: the universe is
# already known, so a set-membership test in Python is free and, more
# importantly, guarantees the seed and the edges agree on what a genre is.
EDGES_QUERY = """
SELECT ?fromId ?propId ?toId WHERE {{
  VALUES ?from {{ {values} }}
  VALUES ?p {{ {props} }}

  ?from ?p ?to .
  FILTER(?to != ?from)

  ?prop wikibase:directClaim ?p .
  BIND(STRAFTER(STR(?prop), "{prefix}") AS ?propId)
  BIND(STRAFTER(STR(?from), "{prefix}") AS ?fromId)
  BIND(STRAFTER(STR(?to),   "{prefix}") AS ?toId)
}}
"""


# ==========================================================================
# Relation model
# ==========================================================================

# Property -> relation name in the direction leading away from the subject.
OUT_RELATION: dict[str, str] = {
    "P279": "parent",           # subclass of
    "P31": "parent",            # instance of
    "P361": "part_of",          # part of
    "P527": "has_part",         # has part(s)
    "P737": "influenced_by",    # influenced by
    "P144": "based_on",         # based on
    "P941": "inspired_by",      # inspired by
    "P155": "follows",          # follows
    "P156": "followed_by",      # followed by
    "P1269": "facet_of",        # facet of
    "P460": "same_as",          # said to be the same as
    "P1889": "different_from",  # different from
}

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
    "same_as": "same_as",
    "different_from": "different_from",
}

# Properties for which Wikidata ships an explicit inverse property. When one
# of these is stated in only one direction, that is a gap in Wikidata itself,
# not merely a relation we happen to derive - worth counting separately.
#
# P279/P31/P737/P144/P941/P1269 are absent here: Wikidata has no "has
# subclass" or "influenced" property, so their inverse is always inferred and
# its absence says nothing about data quality.
RECIPROCAL_PROPERTY: dict[str, str] = {
    "P361": "P527",
    "P527": "P361",
    "P155": "P156",
    "P156": "P155",
    "P460": "P460",
    "P1889": "P1889",
}


# ==========================================================================
# Transport
# ==========================================================================


class QueryRunner:
    """WDQS wrapper: bounded parallelism, retries, progress accounting."""

    def __init__(
        self,
        client: httpx.AsyncClient,
        endpoint: str,
        workers: int,
        attempts: int = 4,
    ) -> None:
        self.client = client
        self.endpoint = endpoint
        self.limiter = anyio.CapacityLimiter(workers)
        self.attempts = attempts
        self._lock = anyio.Lock()
        self._done = 0
        self._total = 0
        self._phase = ""

    def start_phase(self, phase: str, total: int) -> None:
        self._phase = phase
        self._total = total
        self._done = 0

    async def _tick(self) -> None:
        async with self._lock:
            self._done += 1
            step = max(1, self._total // 10)
            if self._done % step == 0 or self._done == self._total:
                LOG.info(
                    "%s: %d/%d batches (%d%%)",
                    self._phase,
                    self._done,
                    self._total,
                    100 * self._done // max(1, self._total),
                )

    async def run(self, sparql: str, timeout: float | None = None) -> list[dict]:
        async with self.limiter:
            rows = await self._run_unlimited(sparql, timeout)
        await self._tick()
        return rows

    async def _run_unlimited(self, sparql: str, timeout: float | None) -> list[dict]:
        last_error: Exception | str = "not attempted"

        for attempt in range(self.attempts):
            try:
                # POST rather than GET: a 250-QID batch is several kilobytes
                # of query text and would blow the URL length limit as a GET.
                response = await self.client.post(
                    self.endpoint,
                    data={"query": sparql},
                    timeout=timeout or self.client.timeout,
                )
            except (httpx.TransportError, httpx.TimeoutException) as exc:
                LOG.warning("transport error (attempt %d): %s", attempt + 1, exc)
                last_error = exc
                await anyio.sleep(2**attempt)
                continue

            if response.status_code == 429:
                # WDQS sends Retry-After; honour it instead of our own backoff.
                delay = float(response.headers.get("Retry-After", 2**attempt))
                LOG.warning("rate limited, sleeping %.0fs", delay)
                last_error = "429 Too Many Requests"
                await anyio.sleep(delay)
                continue

            if response.status_code >= 500:
                # Usually the engine timing out on an unlucky batch; retrying
                # the same batch normally succeeds.
                LOG.warning(
                    "server error %d (attempt %d): %s",
                    response.status_code,
                    attempt + 1,
                    response.text[:200].replace("\n", " "),
                )
                last_error = str(response.status_code)
                await anyio.sleep(2**attempt)
                continue

            response.raise_for_status()

            # WDQS streams its results, so once the first bytes are out the
            # status code can no longer change. A query that hits the server's
            # 60s limit mid-stream therefore arrives as HTTP 200 with a
            # truncated body, with a Java stack trace glued onto the end;
            # its tab characters land inside an unterminated JSON string and
            # json.loads reports "Invalid control character".
            try:
                return response.json()["results"]["bindings"]
            except (json.JSONDecodeError, KeyError) as exc:
                size = len(response.content)
                LOG.warning(
                    "malformed response (attempt %d, %d bytes) - almost "
                    "certainly a server-side timeout truncating the stream: %s",
                    attempt + 1,
                    size,
                    exc,
                )
                last_error = f"truncated response after {size} bytes"
                await anyio.sleep(2**attempt)
                continue

        raise RuntimeError(
            f"query failed after {self.attempts} attempts: {last_error}. "
            "A truncated response means the query is too heavy for the WDQS "
            "60s limit; decompose it instead of raising --seed-timeout, which "
            "is client-side and cannot extend the server's."
        )


def cell(row: dict, name: str) -> str | None:
    """Column value, or None when the variable is unbound in this row."""
    return row.get(name, {}).get("value") or None


def chunked(items: list[str], size: int) -> list[list[str]]:
    return [items[i : i + size] for i in range(0, len(items), size)]


def as_values(qids: list[str]) -> str:
    return " ".join(f"wd:{q}" for q in qids)


# ==========================================================================
# Phases
# ==========================================================================


ROOTS = ("Q188451", "Q11921029")


async def fetch_seed_single(
    runner: QueryRunner, strategy: str, timeout: float
) -> list[str]:
    """Whole universe in one query. Cheap for 'narrow', usually fatal for 'broad'."""
    runner.start_phase(f"seed/{strategy}", 1)
    query = SEED_QUERY_BROAD if strategy == "broad" else SEED_QUERY_NARROW
    rows = await runner.run(query, timeout=timeout)
    return [qid for row in rows if (qid := cell(row, "genreId"))]


async def fetch_seed_walk(
    runner: QueryRunner,
    batch_size: int,
    max_depth: int,
    max_genres: int,
) -> list[str]:
    """
    Breadth-first descent from the roots, one hierarchy level per round.

    Equivalent in result to (P31|P279)+ but every individual query is a
    single indexed hop, so nothing runs long enough to be truncated. Cycles
    are handled by the visited set; the frontier shrinks to empty on its own.
    """
    seen: set[str] = set()
    frontier: list[str] = list(ROOTS)
    # Parents fan out hard, so keep frontier batches well below the batch size
    # used for attributes and edges.
    step = max(1, min(batch_size, 100))

    for depth in range(max_depth):
        batches = chunked(frontier, step)
        runner.start_phase(f"seed/walk depth {depth}", len(batches))
        found: set[str] = set()
        lock = anyio.Lock()

        async def worker(batch: list[str]) -> None:
            rows = await runner.run(
                SEED_STEP_QUERY.format(values=as_values(batch), prefix=ENTITY_PREFIX)
            )
            local = {qid for row in rows if (qid := cell(row, "childId"))}
            async with lock:
                found.update(local)

        async with anyio.create_task_group() as task_group:
            for batch in batches:
                task_group.start_soon(worker, batch)

        fresh = found - seen - set(ROOTS)
        seen |= fresh
        LOG.info(
            "seed/walk depth %d: %d new, %d total", depth, len(fresh), len(seen)
        )

        if not fresh:
            break
        if len(seen) >= max_genres:
            LOG.warning(
                "hit --max-genres (%d) at depth %d, stopping descent early",
                max_genres,
                depth,
            )
            break
        frontier = sorted(fresh)
    else:
        LOG.warning("hit --seed-depth (%d), hierarchy may be truncated", max_depth)

    return sorted(seen)


async def fetch_seed(runner: QueryRunner, args: argparse.Namespace) -> list[str]:
    if args.seed == "walk":
        return await fetch_seed_walk(
            runner, args.batch_size, args.seed_depth, args.max_genres
        )
    return await fetch_seed_single(runner, args.seed, args.seed_timeout)


async def fetch_attrs(
    runner: QueryRunner,
    batches: list[list[str]],
    sink: dict[str, dict],
) -> None:
    runner.start_phase("attrs", len(batches))

    async def worker(batch: list[str]) -> None:
        rows = await runner.run(
            ATTRS_QUERY.format(values=as_values(batch), prefix=ENTITY_PREFIX)
        )
        for row in rows:
            qid = cell(row, "genreId")
            if qid is None:
                continue
            record = sink[qid]
            # English is just one language among many; a missing label is a
            # valid outcome and leaves the map empty, no fallbacks. Values are
            # arrays throughout the pipeline because a language can carry a
            # primary label plus aliases.
            if "en" not in record["labels"]:
                label = cell(row, "labelEn")
                if label is not None:
                    record["labels"]["en"] = [label]
            if record["musicbrainz_id"] is None:
                record["musicbrainz_id"] = cell(row, "mbid")

    async with anyio.create_task_group() as task_group:
        for batch in batches:
            task_group.start_soon(worker, batch)


async def fetch_edges(
    runner: QueryRunner,
    batches: list[list[str]],
    universe: set[str],
) -> tuple[set[tuple[str, str, str]], int]:
    """Collect raw directed (subject, property, object) triples between genres."""
    runner.start_phase("edges", len(batches))
    props = " ".join(f"wdt:{p}" for p in OUT_RELATION)
    edges: set[tuple[str, str, str]] = set()
    dropped = 0
    lock = anyio.Lock()

    async def worker(batch: list[str]) -> None:
        nonlocal dropped
        rows = await runner.run(
            EDGES_QUERY.format(
                values=as_values(batch), props=props, prefix=ENTITY_PREFIX
            )
        )
        local: set[tuple[str, str, str]] = set()
        local_dropped = 0
        for row in rows:
            src = cell(row, "fromId")
            prop = cell(row, "propId")
            dst = cell(row, "toId")
            if not (src and prop and dst):
                continue
            if prop not in OUT_RELATION:
                continue
            # Target outside the universe: not a genre (Q188451 itself, an
            # artist, a country).
            if dst not in universe:
                local_dropped += 1
                continue
            local.add((src, prop, dst))

        async with lock:
            edges.update(local)
            dropped += local_dropped

    async with anyio.create_task_group() as task_group:
        for batch in batches:
            task_group.start_soon(worker, batch)

    return edges, dropped


def complete_edges(
    edges: set[tuple[str, str, str]],
) -> tuple[dict[str, dict[str, set[str]]], dict[str, int]]:
    """
    Build per-genre relations and close every edge in both directions.

    Wikidata states most relations one way only. This pass guarantees that if
    A has parent B then B has child A, and reports how much had to be filled
    in - separating inverses that only ever exist as a derivation from cases
    where Wikidata has a real inverse property that nobody populated.
    """
    relations: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))

    # Pass 1: everything actually stated in Wikidata.
    for src, prop, dst in edges:
        relations[src][OUT_RELATION[prop]].add(dst)

    # Pass 2: close the gaps.
    stats = {"stated": len(edges), "inferred": 0, "one_sided_reciprocal": 0}
    for src, prop, dst in edges:
        inverse = INVERSE_RELATION[OUT_RELATION[prop]]
        if src in relations[dst][inverse]:
            continue  # the reciprocal statement exists, nothing to infer
        relations[dst][inverse].add(src)
        stats["inferred"] += 1

        reciprocal = RECIPROCAL_PROPERTY.get(prop)
        if reciprocal and (dst, reciprocal, src) not in edges:
            stats["one_sided_reciprocal"] += 1

    return relations, stats


# ==========================================================================
# Assembly
# ==========================================================================


async def build(args: argparse.Namespace) -> dict:
    headers = {
        "User-Agent": args.user_agent,
        "Accept": "application/sparql-results+json",
    }
    limits = httpx.Limits(max_connections=args.workers * 2)
    started = time.monotonic()

    async with httpx.AsyncClient(
        headers=headers, limits=limits, timeout=args.timeout
    ) as client:
        runner = QueryRunner(client, args.endpoint, args.workers, args.attempts)

        qids = await fetch_seed(runner, args)
        if args.limit:
            qids = qids[: args.limit]
            LOG.info("truncated to first %d genres", args.limit)
        qids = sorted(set(qids).union(args.include_qid))
        LOG.info("universe: %d genres", len(qids))

        universe = set(qids)
        genres: dict[str, dict] = {
            qid: {"labels": {}, "musicbrainz_id": None, "relations": {}}
            for qid in qids
        }
        batches = chunked(qids, args.batch_size)

        await fetch_attrs(runner, batches, genres)
        edges, dropped = await fetch_edges(runner, batches, universe)

    LOG.info(
        "collected %d stated edges, dropped %d non-genre targets", len(edges), dropped
    )

    relations, edge_stats = complete_edges(edges)
    LOG.info(
        "completion: %d inferred inverses, %d of them where Wikidata has an "
        "inverse property but only one side was stated",
        edge_stats["inferred"],
        edge_stats["one_sided_reciprocal"],
    )

    endpoints = 0
    with_label = 0
    with_mbid = 0
    for qid, record in genres.items():
        record["relations"] = {
            name: sorted(targets)
            for name, targets in sorted(relations.get(qid, {}).items())
            if targets
        }
        endpoints += sum(len(v) for v in record["relations"].values())
        with_label += "en" in record["labels"]
        with_mbid += record["musicbrainz_id"] is not None

    elapsed = time.monotonic() - started
    LOG.info(
        "done: %d genres, %d relation endpoints, %d with MBID, %d without "
        "English label, %.1fs",
        len(genres),
        endpoints,
        with_mbid,
        len(genres) - with_label,
        elapsed,
    )

    return {
        "meta": {
            "genres": len(genres),
            "relation_endpoints": endpoints,
            "stated_edges": edge_stats["stated"],
            "inferred_inverse_edges": edge_stats["inferred"],
            "one_sided_reciprocal_edges": edge_stats["one_sided_reciprocal"],
            "with_english_label": with_label,
            "without_english_label": len(genres) - with_label,
            "with_musicbrainz_id": with_mbid,
            "non_genre_targets_dropped": dropped,
            "seed": args.seed,
            "elapsed_seconds": round(elapsed, 1),
        },
        "genres": genres,
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Dump the Wikidata music-genre graph as JSON on stdout.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "-w", "--workers", type=int, default=8, help="concurrent requests to WDQS"
    )
    parser.add_argument(
        "-b", "--batch-size", type=int, default=250, help="genres per query"
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="process only the first N genres (debugging)",
    )
    parser.add_argument(
        "--include-qid",
        action="append",
        default=[],
        help="Q-id that must be included even when it is outside the seed closure",
    )
    parser.add_argument(
        "--seed",
        choices=("narrow", "walk", "broad"),
        default="narrow",
        help="narrow: P31|P279+ in one query. walk: same closure as broad, "
        "descended one level at a time (recommended). broad: (P31|P279)+ in "
        "one query, normally truncated by the WDQS 60s limit",
    )
    parser.add_argument(
        "--seed-depth",
        type=int,
        default=25,
        help="maximum hierarchy depth for --seed walk",
    )
    parser.add_argument(
        "--max-genres",
        type=int,
        default=200_000,
        help="abort the walk if the universe grows past this",
    )
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument(
        "--timeout", type=float, default=90.0, help="per-request timeout, seconds"
    )
    parser.add_argument(
        "--seed-timeout", type=float, default=300.0, help="seed query timeout, seconds"
    )
    parser.add_argument(
        "--attempts", type=int, default=4, help="attempts per request on 429/5xx"
    )
    parser.add_argument(
        "--user-agent",
        default="GenreGraph/0.3 (https://example.org/; me@example.org)",
        help="WDQS requires a contactable User-Agent",
    )
    parser.add_argument(
        "--indent", type=int, default=2, help="JSON indent, 0 for compact"
    )
    verbosity = parser.add_mutually_exclusive_group()
    verbosity.add_argument("-v", "--verbose", action="store_true", help="debug logging")
    verbosity.add_argument("-q", "--quiet", action="store_true", help="warnings only")

    args = parser.parse_args(argv)
    invalid_qids = [
        qid
        for qid in args.include_qid
        if re.fullmatch(r"Q[1-9][0-9]*", qid) is None
    ]
    if invalid_qids:
        parser.error(f"invalid --include-qid values: {', '.join(invalid_qids)}")
    if args.workers > 10:
        LOG.warning(
            "workers=%d: WDQS suggests no more than ~5 concurrent requests, "
            "expect 429s",
            args.workers,
        )
    return args


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
    logging.getLogger("httpx").setLevel(logging.WARNING)

    try:
        result = anyio.run(build, args)
    except KeyboardInterrupt:
        LOG.error("interrupted")
        raise SystemExit(130)

    json.dump(result, sys.stdout, ensure_ascii=False, indent=args.indent or None)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
