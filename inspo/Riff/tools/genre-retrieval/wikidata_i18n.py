#!/usr/bin/env python3
"""
Enrich a genre graph with multilingual labels.

Reads the JSON produced by genre_graph.py on stdin or from a file, fetches
rdfs:label plus the official skos:altLabel aliases in the requested languages,
and writes the same document back to stdout with a "labels" map added to every
genre that has one. Each language holds an array: the primary label first, then
its aliases.

    genre_graph.py --seed walk | wikidata_i18n.py > genres.json

Transport, logging, retry and CLI conventions are deliberately identical to
genre_graph.py.

Requires: httpx, anyio
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
import time
from collections import defaultdict
from pathlib import Path

import anyio
import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from project_config import GENRE_LOCALES  # noqa: E402

DEFAULT_ENDPOINT = "https://query.wikidata.org/sparql"
ENTITY_PREFIX = "http://www.wikidata.org/entity/"

LOG = logging.getLogger("wikidata_i18n")


# ==========================================================================
# Query
# ==========================================================================

# rdfs:label is the one primary name per language; skos:altLabel holds the
# officially recorded aliases, of which there can be several. Both arrive in
# one pass through a UNION, tagged by ?kind so the primary can be kept first in
# the output array.
#
# Neither side is OPTIONAL: this query does not decide who is a genre, it only
# collects names that exist. A genre with no name in any requested language
# simply contributes no rows and keeps an empty "labels" map.
LABELS_QUERY = """
SELECT ?genreId ?kind ?lang ?value WHERE {{
  VALUES ?genre {{ {values} }}
  {{ ?genre rdfs:label ?label . BIND("label" AS ?kind) }}
  {aliases}
  FILTER(LANG(?label) IN ({langs}))
  BIND(LANG(?label) AS ?lang)
  BIND(STR(?label) AS ?value)
  BIND(STRAFTER(STR(?genre), "{prefix}") AS ?genreId)
}}
"""

ALIAS_CLAUSE = 'UNION\n  { ?genre skos:altLabel ?label . BIND("alias" AS ?kind) }'


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
                # POST rather than GET: a 200-QID batch is several kilobytes
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
            "60s limit; decompose it or lower --batch-size."
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


def load_document(path: str) -> dict:
    """Read the genre_graph.py document from a path, or from stdin for '-'."""
    if path == "-":
        document = json.load(sys.stdin)
    else:
        with open(path, encoding="utf-8") as handle:
            document = json.load(handle)

    if isinstance(document, list):
        raise SystemExit(
            "input looks like the old flat list of SPARQL bindings; this "
            "script now expects the genre_graph.py document with a top-level "
            '"genres" object keyed by QID'
        )
    if not isinstance(document.get("genres"), dict):
        raise SystemExit('input has no top-level "genres" object')

    return document


async def fetch_labels(
    runner: QueryRunner,
    batches: list[list[str]],
    langs: list[str],
    with_aliases: bool,
) -> dict[str, dict[str, list[str]]]:
    """
    QID -> language -> [primary label, alias, alias, ...].

    The primary rdfs:label is always first; aliases follow in sorted order so
    the output is stable across runs. Values equal apart from case are folded
    together, since an alias that only re-capitalises the label is noise.
    """
    runner.start_phase("labels", len(batches))
    lang_list = ",".join(f'"{lang}"' for lang in langs)
    alias_clause = ALIAS_CLAUSE if with_aliases else ""

    primary: dict[str, dict[str, str]] = defaultdict(dict)
    aliases: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    lock = anyio.Lock()

    async def worker(batch: list[str]) -> None:
        # One single .format() pass. Formatting in two stages would leave the
        # template's own braces exposed to the second pass, and SPARQL is full
        # of them.
        rows = await runner.run(
            LABELS_QUERY.format(
                values=as_values(batch),
                langs=lang_list,
                prefix=ENTITY_PREFIX,
                aliases=alias_clause,
            )
        )
        async with lock:
            for row in rows:
                qid = cell(row, "genreId")
                lang = cell(row, "lang")
                value = cell(row, "value")
                if not (qid and lang and value):
                    continue
                if cell(row, "kind") == "alias":
                    aliases[qid][lang].add(value)
                else:
                    primary[qid][lang] = value

    async with anyio.create_task_group() as task_group:
        for batch in batches:
            task_group.start_soon(worker, batch)

    out: dict[str, dict[str, list[str]]] = {}
    for qid in set(primary) | set(aliases):
        per_language: dict[str, list[str]] = {}
        for lang in set(primary.get(qid, {})) | set(aliases.get(qid, {})):
            ordered: list[str] = []
            seen: set[str] = set()
            label = primary.get(qid, {}).get(lang)
            if label:
                ordered.append(label)
                seen.add(label.casefold())
            for alias in sorted(aliases.get(qid, {}).get(lang, ())):
                if alias.casefold() not in seen:
                    seen.add(alias.casefold())
                    ordered.append(alias)
            if ordered:
                per_language[lang] = ordered
        if per_language:
            out[qid] = per_language

    return out


def normalise(raw: dict | None) -> dict[str, list[str]]:
    """Keep supported locale arrays, folding case-insensitive duplicates."""
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


def merge_labels(
    document: dict,
    translations: dict[str, dict[str, list[str]]],
) -> dict:
    """
    Merge fetched names into each genre's "labels" map and return statistics.

    Every language holds an array whose first entry is the primary rdfs:label
    and whose remainder are skos:altLabel aliases. English carries no special
    status: it is one key in the map like any other.
    """
    per_language: dict[str, int] = defaultdict(int)
    alias_count = 0
    with_labels = 0
    with_aliases = 0

    for qid, record in document["genres"].items():
        labels = normalise(record.get("labels"))

        # Fetched values dictate the ordering: they carry the primary/alias
        # distinction, which a pre-existing flat array has already lost.
        for lang, values in translations.get(qid, {}).items():
            merged = list(values)
            seen = {item.casefold() for item in merged}
            for item in labels.get(lang, []):
                if item.casefold() not in seen:
                    seen.add(item.casefold())
                    merged.append(item)
            labels[lang] = merged

        labels = dict(sorted(labels.items()))
        for lang, values in labels.items():
            per_language[lang] += 1
            alias_count += max(0, len(values) - 1)

        # Rebuild with a stable key order rather than appending to the end.
        document["genres"][qid] = {
            "labels": labels,
            "musicbrainz_id": record.get("musicbrainz_id"),
            "relations": record.get("relations", {}),
        }
        with_labels += bool(labels)
        with_aliases += any(len(v) > 1 for v in labels.values())

    return {
        "genres_with_labels": with_labels,
        "genres_with_aliases": with_aliases,
        "alias_count": alias_count,
        "labels_by_language": dict(sorted(per_language.items())),
    }


# ==========================================================================
# Assembly
# ==========================================================================


async def build(args: argparse.Namespace) -> dict:
    started = time.monotonic()

    document = load_document(args.input)
    if set(args.langs) != set(GENRE_LOCALES) and document.get("languages") != list(GENRE_LOCALES):
        raise ValueError("Partial enrichment requires a complete current-locale document")
    qids = sorted(document["genres"])
    if args.limit:
        qids = qids[: args.limit]
        LOG.info("truncated to first %d genres", args.limit)
    LOG.info(
        "input: %d genres, languages: %s, aliases: %s",
        len(qids),
        ",".join(args.langs),
        "off" if args.no_aliases else "on",
    )

    headers = {
        "User-Agent": args.user_agent,
        "Accept": "application/sparql-results+json",
    }
    limits = httpx.Limits(max_connections=args.workers * 2)

    async with httpx.AsyncClient(
        headers=headers, limits=limits, timeout=args.timeout
    ) as client:
        runner = QueryRunner(client, args.endpoint, args.workers, args.attempts)
        batches = chunked(qids, args.batch_size)
        translations = await fetch_labels(
            runner, batches, args.langs, not args.no_aliases
        )

    LOG.info("fetched labels for %d genres", len(translations))

    document["languages"] = list(GENRE_LOCALES)
    stats = merge_labels(document, translations)
    elapsed = time.monotonic() - started

    # Preserve whatever genre_graph.py recorded and add this run's numbers.
    document.setdefault("meta", {}).update(
        {
            "languages": list(GENRE_LOCALES),
            "retrieved_languages": list(args.langs),
            "aliases": not args.no_aliases,
            "i18n_elapsed_seconds": round(elapsed, 1),
            **stats,
        }
    )

    LOG.info(
        "done: %d/%d genres carry labels, %d carry aliases, %d alias values, %.1fs",
        stats["genres_with_labels"],
        len(document["genres"]),
        stats["genres_with_aliases"],
        stats["alias_count"],
        elapsed,
    )
    for lang, count in stats["labels_by_language"].items():
        LOG.debug("  %s: %d", lang, count)

    return document


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Add multilingual labels to a genre_graph.py document.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "-i", "--input", default="-", help="input JSON file, or - for stdin"
    )
    parser.add_argument(
        "-w", "--workers", type=int, default=8, help="concurrent requests to WDQS"
    )
    parser.add_argument(
        "-b", "--batch-size", type=int, default=200, help="genres per query"
    )
    parser.add_argument(
        "--langs",
        type=lambda s: [x.strip() for x in s.split(",") if x.strip()],
        default=list(GENRE_LOCALES),
        help="comma-separated language codes",
    )
    parser.add_argument(
        "--no-aliases",
        action="store_true",
        help="fetch only rdfs:label, skipping skos:altLabel aliases",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="process only the first N genres (debugging)",
    )
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument(
        "--timeout", type=float, default=90.0, help="per-request timeout, seconds"
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
    if not args.langs or not set(args.langs) <= set(GENRE_LOCALES):
        parser.error("--langs must be a nonempty subset of config/project.json genreLocales")
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