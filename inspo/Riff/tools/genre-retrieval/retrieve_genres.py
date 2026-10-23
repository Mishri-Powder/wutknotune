#!/usr/bin/env python3
"""Run Riff's cached Wikidata + MusicBrainz genre retrieval pipeline."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import venv
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOL_DIR.parent))

from project_config import CONFIG_PATH, GENRE_LOCALES, REQUIRED_WIKIDATA_QIDS
from project_genres import merge_project_genres_file

REQUIREMENTS = TOOL_DIR / "requirements.txt"
SCRIPTS = (
    "build_gnre.py",
    "musicbrainz.py",
    "project_genres.py",
    "verify_gnre_asset.py",
    "wikidata_i18n.py",
    "wikidata_indx.py",
    "wikidata_musicbrainz_merge.py",
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def valid_genre_document(
    path: Path,
    required_qids: tuple[str, ...] = (),
    localized: bool = False,
) -> bool:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        return False
    if not isinstance(document, dict) or not isinstance(
        document.get("genres"), dict
    ):
        return False
    if localized:
        languages = document.get("languages")
        if languages != list(GENRE_LOCALES):
            return False
        if any(set(record.get("labels", {})) - set(GENRE_LOCALES)
               for record in document["genres"].values()):
            return False
    return all(qid in document["genres"] for qid in required_qids)


def self_check() -> None:
    for name in SCRIPTS:
        source = (TOOL_DIR / name).read_text(encoding="utf-8")
        compile(source, name, "exec")
    if not REQUIREMENTS.is_file():
        raise RuntimeError(f"missing dependency declaration: {REQUIREMENTS}")
    print(f"Validated {len(SCRIPTS)} genre retrieval scripts.")


def write_manifest(cache_dir: Path, final: Path) -> None:
    stages = [
        cache_dir / name
        for name in ("wikidata.json", "wikidata.i18n.json", "musicbrainz.json")
    ]
    if not all(path.is_file() for path in stages):
        raise RuntimeError("Genre retrieval stages are incomplete")
    manifest = {
        "final": final.name,
        "final_sha256": sha256(final),
        "stages": {path.name: sha256(path) for path in stages},
        "scripts": {name: sha256(TOOL_DIR / name) for name in SCRIPTS},
        "project_config": sha256(CONFIG_PATH),
        "genre_locales": list(GENRE_LOCALES),
    }
    manifest_path = cache_dir / "manifest.json"
    temporary = manifest_path.with_suffix(".json.tmp")
    temporary.write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    os.replace(temporary, manifest_path)


def ensure_venv(venv_dir: Path) -> Path:
    python = venv_dir / "bin" / "python"
    stamp = venv_dir / ".riff-genre-requirements.sha256"
    expected = sha256(REQUIREMENTS)
    if not python.is_file():
        venv.EnvBuilder(with_pip=True).create(venv_dir)
    if not stamp.is_file() or stamp.read_text(
            encoding="utf-8").strip() != expected:
        subprocess.run(
            [str(python), "-m", "pip", "install", "-r", str(REQUIREMENTS)],
            check=True,
        )
        stamp.write_text(expected + "\n", encoding="utf-8")
    return python


def run_json(
    python: Path,
    script: str,
    arguments: list[str],
    output: Path,
    refresh: bool,
    required_qids: tuple[str, ...] = (),
    localized: bool = False,
) -> None:
    if valid_genre_document(output, required_qids, localized) and not refresh:
        print(f"Using cached {output.name}")
        return
    if output.exists() and not refresh and not valid_genre_document(output):
        raise RuntimeError(
            f"Refusing to replace invalid cached data at {output}; "
            "inspect it, then rerun with --refresh"
        )
    temporary = output.with_name(output.name + ".tmp")
    try:
        with temporary.open("wb") as handle:
            subprocess.run(
                [str(python), str(TOOL_DIR / script), *arguments],
                stdout=handle,
                check=True,
            )
        if not valid_genre_document(temporary, required_qids, localized):
            raise RuntimeError(f"{script} produced an invalid genre document")
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--cache-dir",
        type=Path,
        default=TOOL_DIR / "cache",
        help="persistent stage and final-output directory",
    )
    parser.add_argument(
        "--venv",
        type=Path,
        default=Path.home() / "venv" / "riff-genre",
        help=(
            "Python virtual environment "
            "(kept outside project data by policy)"
        ),
    )
    parser.add_argument(
        "--contact",
        default=os.environ.get("RIFF_GENRE_CONTACT", ""),
        help="contact email or URL required by MusicBrainz and Wikidata",
    )
    parser.add_argument(
        "--refresh",
        action="store_true",
        help=(
            "retrieve every stage again and atomically replace cached outputs"
        ),
    )
    parser.add_argument(
        "--self-check",
        action="store_true",
        help=(
            "validate source syntax without installing dependencies "
            "or networking"
        ),
    )
    return parser


def main() -> int:
    arguments = build_parser().parse_args()
    if arguments.self_check:
        self_check()
        return 0

    cache_dir = arguments.cache_dir.resolve()
    final = cache_dir / "genres.merged.json"
    merge_project_genres_file(final)
    if valid_genre_document(
        final, REQUIRED_WIKIDATA_QIDS, localized=True
    ) and not arguments.refresh:
        write_manifest(cache_dir, final)
        print(f"Genre data already downloaded; using {final}")
        return 0
    if not arguments.contact.strip():
        raise SystemExit(
            "Genre data is not cached. Set RIFF_GENRE_CONTACT or pass "
            "-PgenreContact=<email-or-url> to Gradle."
        )

    cache_dir.mkdir(parents=True, exist_ok=True)
    python = ensure_venv(arguments.venv.expanduser().resolve())
    user_agent = f"RiffGenreRetrieval/1.0 ({arguments.contact.strip()})"
    wikidata = cache_dir / "wikidata.json"
    wikidata_i18n = cache_dir / "wikidata.i18n.json"
    musicbrainz = cache_dir / "musicbrainz.json"
    musicbrainz_pages = cache_dir / (
        "musicbrainz.pages.refresh.cache.json"
        if arguments.refresh
        else "musicbrainz.pages.cache.json"
    )

    run_json(
        python,
        "wikidata_indx.py",
        [
            "--seed",
            "walk",
            "--user-agent",
            user_agent,
            *[
                argument
                for qid in REQUIRED_WIKIDATA_QIDS
                for argument in ("--include-qid", qid)
            ],
        ],
        wikidata,
        arguments.refresh,
        REQUIRED_WIKIDATA_QIDS,
    )
    run_json(
        python,
        "wikidata_i18n.py",
        ["--input", str(wikidata), "--user-agent", user_agent],
        wikidata_i18n,
        arguments.refresh,
        REQUIRED_WIKIDATA_QIDS,
        localized=True,
    )
    run_json(
        python,
        "musicbrainz.py",
        [
            "--contact",
            arguments.contact.strip(),
            "--cache",
            str(musicbrainz_pages),
            "--app-name",
            "RiffGenreRetrieval",
            "--app-version",
            "1.0",
        ],
        musicbrainz,
        arguments.refresh,
    )
    run_json(
        python,
        "wikidata_musicbrainz_merge.py",
        [
            "--wikidata",
            str(wikidata_i18n),
            "--musicbrainz",
            str(musicbrainz),
            "--priority",
            "musicbrainz",
            "--edges",
            "union",
        ],
        final,
        arguments.refresh,
        REQUIRED_WIKIDATA_QIDS,
        localized=True,
    )
    write_manifest(cache_dir, final)
    print(f"Genre retrieval complete: {final}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
