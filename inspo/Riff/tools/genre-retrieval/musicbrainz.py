#!/usr/bin/env python3
"""
Scrape the MusicBrainz genre database into the shared genre-graph format.

Sources
-------
Genre list : https://musicbrainz.org/genres
    One flat <ul>; each <li> has <a href="/genre/{uuid}"><bdi>name</bdi></a>.
    The href UUID is the genre's MBID and is reused as-is.

Genre page : https://musicbrainz.org/genre/{uuid}
    Relationships live in <table class="details"> rows: <th>phrase:</th> plus a
    <td> of links. A row can point at other genres ("subgenres", "influenced
    by"), at non-genre entities ("from" -> area, "used instruments" ->
    instrument) or at external databases ("Wikidata", "other databases").

Output
------
Same envelope as genre_graph.py: {"meta": {...}, "genres": {<mbid>: {...}}} on
stdout, diagnostics on stderr. Genre-to-genre rows are normalised into the same
relation vocabulary the Wikidata side uses (parent/child/influenced_by/...) so
the two graphs can be merged; everything else is preserved verbatim under
"links". The Wikidata row is parsed down to a bare QID in "wikidata_id", which
is the join key for the merge step.

Etiquette
---------
MusicBrainz requires a meaningful User-Agent (--contact is mandatory). This is
one request per genre, a few thousand in total, so concurrency is bounded by
--workers and additionally rate-capped by --rate. Progress is checkpointed to
--cache and runs resume from it.

Usage
-----
    musicbrainz_genres.py --contact you@example.com > musicbrainz.json
    musicbrainz_genres.py --contact you@example.com --limit 20 -v > sample.json

Requires: httpx, anyio, lxml
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import sys
import time
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

import anyio
import httpx

try:
    from lxml import html as lxml_html
except ImportError:  # pragma: no cover
    sys.exit("This script requires lxml. Install it with:  pip install lxml")

LOG = logging.getLogger("musicbrainz_genres")

DEFAULT_BASE_URL = "https://musicbrainz.org"

MBID_RE = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", re.I
)

# MusicBrainz entity types appearing as /<type>/<uuid> links in relationship rows.
ENTITY_TYPES = {
    "genre", "area", "instrument", "artist", "label", "work",
    "place", "event", "series", "recording", "release-group", "release",
}

_ENTITY_HREF_RE = re.compile(r"^/([a-z-]+)/(" + MBID_RE.pattern + r")", re.I)
_QID_RE = re.compile(r"/(Q\d+)\b")


# ==========================================================================
# Relation model
# ==========================================================================

# MusicBrainz row label (lowercased, colon stripped) -> shared relation name.
# The names on the right are the same vocabulary genre_graph.py emits, which is
# what makes the merge a set union rather than a translation exercise.
#
# This table is deliberately incomplete: MusicBrainz phrasing is not formally
# published and changes. Anything unmapped is counted and reported at the end
# rather than silently dropped, so gaps surface on the first run.
GENRE_RELATION_MAP: dict[str, str] = {
    "subgenres": "child",
    "subgenre of": "parent",
    "influenced by": "influenced_by",
    "influenced genres": "influenced",
    "influenced": "influenced",
    "has fusion genres": "has_fusion_genre",
    "fusion genres": "has_fusion_genre",
    "fusion of": "fusion_of",
}

INVERSE_RELATION: dict[str, str] = {
    "parent": "child",
    "child": "parent",
    "influenced_by": "influenced",
    "influenced": "influenced_by",
    "has_fusion_genre": "fusion_of",
    "fusion_of": "has_fusion_genre",
    "part_of": "has_part",
    "has_part": "part_of",
    "same_as": "same_as",
    "different_from": "different_from",
}


# ==========================================================================
# Rate limiting and transport
# ==========================================================================


class RateLimiter:
    """Minimum interval between requests. rate <= 0 disables throttling."""

    def __init__(self, rate_per_sec: float) -> None:
        self.min_interval = 1.0 / rate_per_sec if rate_per_sec > 0 else 0.0
        self._lock = anyio.Lock()
        self._last = 0.0

    async def acquire(self) -> None:
        if self.min_interval <= 0:
            return
        async with self._lock:
            wait = self.min_interval - (time.monotonic() - self._last)
            if wait > 0:
                await anyio.sleep(wait)
            self._last = time.monotonic()


class MusicBrainzClient:
    """
    Bounded-parallelism HTTP client with retries and progress accounting.

    Same shape as QueryRunner in genre_graph.py, with two additions the WDQS
    scripts do not need: a request-rate cap, because MusicBrainz asks for one,
    and 503 handling, which is how MusicBrainz signals overload.
    """

    def __init__(
        self,
        client: httpx.AsyncClient,
        base_url: str,
        workers: int,
        rate: float,
        attempts: int = 5,
    ) -> None:
        self.client = client
        self.base_url = base_url.rstrip("/")
        self.limiter = anyio.CapacityLimiter(workers)
        self.rate = RateLimiter(rate)
        self.attempts = attempts
        self._lock = anyio.Lock()
        self._done = 0
        self._total = 0
        self._phase = ""

    def start_phase(self, phase: str, total: int) -> None:
        self._phase = phase
        self._total = total
        self._done = 0

    async def tick(self) -> None:
        async with self._lock:
            self._done += 1
            step = max(1, self._total // 20)
            if self._done % step == 0 or self._done == self._total:
                LOG.info(
                    "%s: %d/%d pages (%d%%)",
                    self._phase,
                    self._done,
                    self._total,
                    100 * self._done // max(1, self._total),
                )

    async def get_text(self, path: str) -> tuple[int, str]:
        async with self.limiter:
            return await self._get_unlimited(path)

    async def _get_unlimited(self, path: str) -> tuple[int, str]:
        url = self.base_url + path
        last_error: Exception | str = "not attempted"

        for attempt in range(self.attempts):
            await self.rate.acquire()
            try:
                response = await self.client.get(url)
            except (httpx.TransportError, httpx.TimeoutException) as exc:
                LOG.warning("transport error on %s (attempt %d): %s", path, attempt + 1, exc)
                last_error = exc
                await anyio.sleep(min(2**attempt, 30))
                continue

            if response.status_code == 503:
                # MusicBrainz throttles with 503 plus Retry-After; honour it
                # rather than guessing.
                header = response.headers.get("Retry-After", "")
                delay = float(header) if header.isdigit() else min(2**attempt, 30)
                LOG.warning("throttled on %s, sleeping %.0fs", path, delay)
                last_error = "503 Service Unavailable"
                await anyio.sleep(delay)
                continue

            if response.status_code >= 500:
                LOG.warning(
                    "server error %d on %s (attempt %d)",
                    response.status_code, path, attempt + 1,
                )
                last_error = str(response.status_code)
                await anyio.sleep(min(2**attempt, 30))
                continue

            return response.status_code, response.text

        raise RuntimeError(f"GET {url} failed after {self.attempts} attempts: {last_error}")


# ==========================================================================
# Parsing
# ==========================================================================


def parse_genre_list(html: str) -> list[tuple[str, str]]:
    """Parse /genres into [(mbid, name), ...] preserving page order."""
    doc = lxml_html.fromstring(html)
    out: list[tuple[str, str]] = []
    seen: set[str] = set()
    uls = doc.xpath('//div[contains(@id, "page")]//div[contains(@id, "content")]/ul')
    for ul in uls:
        for anchor in ul.xpath('.//li//a[starts-with(@href, "/genre/")]'):
            match = MBID_RE.search(anchor.get("href", ""))
            if not match:
                continue
            gid = match.group(0).lower()
            if gid in seen:
                continue
            seen.add(gid)
            bdi = anchor.xpath(".//bdi")
            name = (bdi[0].text_content() if bdi else anchor.text_content()).strip()
            out.append((gid, name))
    return out


def classify_href(href: str):
    """
    Map an <a> href to (target_type, target), or None to skip.

    - /<entity>/<uuid>  -> (entity_type, uuid)   e.g. genre, area, instrument
    - external http(s)  -> ("url", full_url)
    - /url/<uuid> (MB-internal 'info' pointer) and anything else -> skipped
    """
    match = _ENTITY_HREF_RE.match(href)
    if match and match.group(1).lower() in ENTITY_TYPES:
        return match.group(1).lower(), match.group(2).lower()
    if href.startswith(("http://", "https://")):
        return "url", href
    return None


def parse_genre_relations(html: str) -> list[dict]:
    """Extract every relationship row from a genre page's details table(s)."""
    doc = lxml_html.fromstring(html)
    rels: list[dict] = []
    seen: set[tuple] = set()
    for row in doc.xpath('//table[contains(@class, "details")]//tr[th]'):
        relation = row.xpath("./th")[0].text_content().strip().rstrip(":").strip()
        for anchor in row.xpath("./td//a[@href]"):
            classified = classify_href(anchor.get("href", ""))
            if classified is None:
                continue
            target_type, target = classified
            bdi = anchor.xpath(".//bdi")
            name = (bdi[0].text_content() if bdi else anchor.text_content()).strip() or None
            key = (relation, target_type, target)
            if key in seen:
                continue
            seen.add(key)
            rels.append(
                {
                    "relation": relation,
                    "target_type": target_type,
                    "target": target,
                    "target_name": name,
                }
            )
    return rels


def parse_wikidata_href(html: str) -> str | None:
    """
    Return the Wikidata href from a genre details table, if present.

    MusicBrainz also puts an internal 'info' link in this row, so only the
    external wikidata.org link is eligible.
    """
    doc = lxml_html.fromstring(html)
    hrefs = doc.xpath(
        '(//table[contains(concat(" ", normalize-space(@class), " "), " details ")]'
        ' | //div[contains(concat(" ", normalize-space(@class), " "), " details ")]//table)'
        '//tr[th[normalize-space(.) = "Wikidata" or normalize-space(.) = "Wikidata:"]]'
        "//td//a[@href]/@href"
    )
    for href in hrefs:
        if href.startswith(
            ("//www.wikidata.org/", "https://www.wikidata.org/", "http://www.wikidata.org/")
        ):
            return href
    return None


def extract_qid(href: str | None) -> str | None:
    """Reduce a Wikidata href to a bare QID - the join key for the merge step."""
    if not href:
        return None
    match = _QID_RE.search(href)
    return match.group(1) if match else None


# ==========================================================================
# Normalisation
# ==========================================================================


def normalise(
    raw: dict[str, dict],
    universe: set[str],
) -> tuple[dict[str, dict], dict]:
    """
    Turn scraped rows into the shared record shape and close every edge.

    Genre-to-genre rows become "relations" in the same vocabulary as
    genre_graph.py; every other row is kept verbatim under "links" so nothing
    scraped is lost.
    """
    relations: dict[str, dict[str, set[str]]] = defaultdict(lambda: defaultdict(set))
    unmapped_genre_phrases: Counter[str] = Counter()
    other_phrases: Counter[str] = Counter()
    links: dict[str, list[dict]] = defaultdict(list)
    stated: set[tuple[str, str, str]] = set()
    dropped = 0

    for gid, record in raw.items():
        for row in record.get("relationships", []):
            phrase = row["relation"].strip().rstrip(":").strip().lower()

            if row["target_type"] != "genre":
                other_phrases[phrase] += 1
                links[gid].append(row)
                continue

            relation = GENRE_RELATION_MAP.get(phrase)
            if relation is None:
                unmapped_genre_phrases[phrase] += 1
                links[gid].append(row)
                continue

            target = row["target"]
            # A genre page can link to an entity that is not on /genres.
            if target not in universe:
                dropped += 1
                continue

            relations[gid][relation].add(target)
            stated.add((gid, relation, target))

    # Same closure pass as genre_graph.py. MusicBrainz usually states both
    # sides (subgenres on the parent, subgenre of on the child), so a high
    # inferred count here means one side of the pair is under-populated.
    inferred = 0
    for src, relation, dst in stated:
        inverse = INVERSE_RELATION.get(relation)
        if inverse is None or src in relations[dst][inverse]:
            continue
        relations[dst][inverse].add(src)
        inferred += 1

    genres: dict[str, dict] = {}
    with_qid = 0
    for gid, record in sorted(raw.items(), key=lambda kv: (kv[1].get("name") or "").lower()):
        qid = extract_qid(record.get("wikidata"))
        with_qid += qid is not None
        genres[gid] = {
            "name": record.get("name"),
            "wikidata_id": qid,
            "wikidata_url": record.get("wikidata"),
            "relations": {
                name: sorted(targets)
                for name, targets in sorted(relations.get(gid, {}).items())
                if targets
            },
            "links": links.get(gid, []),
        }
        if record.get("_error"):
            genres[gid]["error"] = record["_error"]

    stats = {
        "stated_edges": len(stated),
        "inferred_inverse_edges": inferred,
        "non_genre_targets_dropped": dropped,
        "with_wikidata_id": with_qid,
        "without_wikidata_id": len(genres) - with_qid,
        "unmapped_genre_relations": dict(unmapped_genre_phrases.most_common()),
        "non_genre_relations": dict(other_phrases.most_common()),
    }
    return genres, stats


# ==========================================================================
# Cache
# ==========================================================================

CACHE_VERSION = 5


def load_cache(path: Path | None) -> dict:
    if path and path.exists():
        try:
            blob = json.loads(path.read_text())
        except json.JSONDecodeError:
            LOG.warning("cache at %s is unreadable, starting fresh", path)
            return {}
        if isinstance(blob, dict) and blob.get("_cache_version") == CACHE_VERSION:
            return blob.get("genres", {})
        LOG.warning("cache at %s has a different version, starting fresh", path)
    return {}


def save_cache(path: Path | None, results: dict) -> None:
    if path:
        path.write_text(json.dumps({"_cache_version": CACHE_VERSION, "genres": results}))


# ==========================================================================
# Assembly
# ==========================================================================


async def build(args: argparse.Namespace) -> dict:
    started = time.monotonic()
    user_agent = f"{args.app_name}/{args.app_version} ( {args.contact} )"
    cache_path = Path(args.cache) if args.cache else None

    async with httpx.AsyncClient(
        headers={"User-Agent": user_agent, "Accept": "text/html,application/xhtml+xml"},
        timeout=args.timeout,
        limits=httpx.Limits(max_connections=args.workers * 2),
        follow_redirects=True,
    ) as http:
        client = MusicBrainzClient(http, args.base_url, args.workers, args.rate, args.attempts)

        LOG.info("fetching genre list from %s/genres", args.base_url)
        client.start_phase("index", 1)
        status, html = await client.get_text("/genres")
        await client.tick()
        if status != 200:
            raise SystemExit(f"/genres returned HTTP {status}")

        listing = parse_genre_list(html)
        if args.limit:
            listing = listing[: args.limit]
            LOG.info("truncated to first %d genres", args.limit)
        if not listing:
            raise SystemExit("parsed 0 genres - the page structure may have changed")
        LOG.info("universe: %d genres", len(listing))

        cached = load_cache(cache_path)
        raw: dict[str, dict] = {}
        todo: list[tuple[str, str]] = []
        for gid, name in listing:
            if gid in cached and "relationships" in cached[gid]:
                raw[gid] = cached[gid]
            else:
                todo.append((gid, name))
        if raw:
            LOG.info("resuming: %d genres already cached", len(raw))

        client.start_phase("pages", len(todo))
        lock = anyio.Lock()

        async def process(gid: str, name: str) -> None:
            status, page = await client.get_text(f"/genre/{gid}")
            record: dict = {
                "id": gid,
                "name": name,
                "relationships": parse_genre_relations(page) if status == 200 else [],
            }
            href = parse_wikidata_href(page) if status == 200 else None
            if href:
                record["wikidata"] = href
            if status != 200:
                LOG.warning("genre %s (%s) returned HTTP %d", gid, name, status)
                record["_error"] = f"HTTP {status}"
            async with lock:
                raw[gid] = record
            await client.tick()

        async def checkpoint_loop() -> None:
            while True:
                await anyio.sleep(args.checkpoint_interval)
                async with lock:
                    save_cache(cache_path, dict(raw))
                LOG.debug("checkpointed %d genres", len(raw))

        if todo:
            try:
                async with anyio.create_task_group() as outer:
                    outer.start_soon(checkpoint_loop)
                    async with anyio.create_task_group() as workers:
                        for gid, name in todo:
                            workers.start_soon(process, gid, name)
                    # Work is done; stop the checkpoint task.
                    outer.cancel_scope.cancel()
            finally:
                save_cache(cache_path, raw)

    universe = {gid for gid, _ in listing}
    genres, stats = normalise(raw, universe)
    elapsed = time.monotonic() - started

    if stats["unmapped_genre_relations"]:
        LOG.warning(
            "unmapped genre-to-genre relation phrases (kept under 'links', add "
            "them to GENRE_RELATION_MAP): %s",
            ", ".join(f"{k} x{v}" for k, v in stats["unmapped_genre_relations"].items()),
        )
    for phrase, count in stats["non_genre_relations"].items():
        LOG.debug("non-genre relation %r x%d", phrase, count)

    LOG.info(
        "done: %d genres, %d stated edges, %d inferred inverses, %d with a "
        "Wikidata id, %.1fs",
        len(genres),
        stats["stated_edges"],
        stats["inferred_inverse_edges"],
        stats["with_wikidata_id"],
        elapsed,
    )

    return {
        "meta": {
            "source": f"{args.base_url}/genres",
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "genres": len(genres),
            "relation_endpoints": sum(
                sum(len(v) for v in g["relations"].values()) for g in genres.values()
            ),
            "elapsed_seconds": round(elapsed, 1),
            **stats,
        },
        "genres": genres,
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Scrape MusicBrainz genres and relationships as JSON on stdout.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--contact",
        required=True,
        help="email or URL identifying you; MusicBrainz requires it in the User-Agent",
    )
    parser.add_argument(
        "-w", "--workers", type=int, default=4, help="concurrent page requests"
    )
    parser.add_argument(
        "--rate",
        type=float,
        default=4.0,
        help="requests/second cap on top of --workers; 0 disables",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="process only the first N genres (debugging)",
    )
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument(
        "--timeout", type=float, default=60.0, help="per-request timeout, seconds"
    )
    parser.add_argument(
        "--attempts", type=int, default=5, help="attempts per request on 503/5xx"
    )
    parser.add_argument(
        "--cache",
        default="musicbrainz_genres.cache.json",
        help="checkpoint file for resumable runs; empty string disables",
    )
    parser.add_argument("--checkpoint-interval", type=float, default=15.0)
    parser.add_argument("--app-name", default="GenreDBScraper")
    parser.add_argument("--app-version", default="3.0")
    parser.add_argument(
        "--indent", type=int, default=2, help="JSON indent, 0 for compact"
    )
    verbosity = parser.add_mutually_exclusive_group()
    verbosity.add_argument("-v", "--verbose", action="store_true", help="debug logging")
    verbosity.add_argument("-q", "--quiet", action="store_true", help="warnings only")

    args = parser.parse_args(argv)
    if args.cache == "":
        args.cache = None
    if args.workers < 1:
        parser.error("--workers must be >= 1")
    if args.workers > 8:
        LOG.warning(
            "workers=%d: MusicBrainz is stricter than WDQS, expect 503s", args.workers
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
        LOG.error("interrupted; progress was checkpointed, re-run to resume")
        raise SystemExit(130)

    json.dump(result, sys.stdout, ensure_ascii=False, indent=args.indent or None)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()