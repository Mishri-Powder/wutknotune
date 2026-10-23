"""Merge project-configured genre additions into retrieved genre data."""

from __future__ import annotations

import copy
import json
import os
import sys
from pathlib import Path
from typing import Any


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from project_config import (  # noqa: E402
    GENRE_LOCALES,
    PROJECT_REQUIRED_GENRES,
    validate_manual_locales,
)


def _new_record(qid: str, labels: dict[str, list[str]]) -> dict[str, Any]:
    return {
        "labels": copy.deepcopy(labels),
        "links": [],
        "musicbrainz_id": None,
        "relations": {},
        "sources": ["wikidata"],
        "synthetic_id": False,
        "wikidata_id": qid,
    }


def merge_project_genres(document: dict[str, Any]) -> dict[str, Any]:
    result = copy.deepcopy(document)
    genres = result.get("genres")
    if not isinstance(genres, dict):
        raise ValueError("Merged genre document has no genres")
    for record in genres.values():
        if "labels" in record:
            record["labels"] = {
                locale: values
                for locale, values in record["labels"].items()
                if locale in GENRE_LOCALES
            }
    for qid, addition in PROJECT_REQUIRED_GENRES.items():
        configured = addition["labels"]
        validate_manual_locales(configured, require_all=True)
        existing = genres.get(qid)
        if existing is None:
            genres[qid] = _new_record(qid, configured)
            continue
        labels = existing.setdefault("labels", {})
        for locale, values in configured.items():
            current = labels.get(locale, [])
            labels[locale] = list(dict.fromkeys(values + current))
    return result


def merge_project_genres_file(path: Path) -> None:
    if not path.is_file():
        return
    original = json.loads(path.read_text(encoding="utf-8"))
    merged = merge_project_genres(original)
    if merged == original:
        return
    temporary = path.with_name(path.name + ".project.tmp")
    try:
        temporary.write_text(
            json.dumps(merged, ensure_ascii=False, indent=2, sort_keys=True)
            + "\n",
            encoding="utf-8",
        )
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)
