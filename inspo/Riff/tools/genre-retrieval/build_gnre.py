#!/usr/bin/env python3
"""Compile a cached merged genre graph into the bundled GNRE FlatBuffer."""

from __future__ import annotations

import argparse
import copy
import json
import os
import struct
import sys
import unicodedata
from pathlib import Path
from typing import Any

import flatbuffers
import xxhash

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from project_config import (  # noqa: E402
    GNRE_GENERATION,
    MTG_EXPLICIT_TARGETS,
    MTG_FIRST_ID,
    MTG_LABELS,
)
from project_genres import merge_project_genres


CONFUSABLES = (
    ROOT
    / "indexer/src/main/resources/link/roadio/riff/indexer/core/tags"
    / "confusables/confusables-one-to-one.bin"
)

import gnre_generated as gnre  # noqa: E402


RELATION_VALUES = {
    name: value
    for name, value in vars(gnre.GenreRelationEnum).items()
    if not name.startswith("_") and isinstance(value, int)
}
UINT64_MASK = (1 << 64) - 1

def genre_id(key: str) -> int:
    if not key.startswith("Q"):
        raise ValueError(f"Genre key is not a Q-id: {key!r}")
    value = int(key[1:])
    if value == 0 or value < -(1 << 63) or value > UINT64_MASK:
        raise ValueError(f"Genre ID is outside the 64-bit contract: {key!r}")
    return value & UINT64_MASK


def read_confusables(path: Path) -> dict[int, int]:
    data = path.read_bytes()
    if len(data) < 8 or data[:4] != b"RCF1":
        raise ValueError("Invalid confusable-table header")
    count = struct.unpack_from(">I", data, 4)[0]
    if len(data) != 8 + count * 8:
        raise ValueError("Invalid confusable-table length")
    pairs = [struct.unpack_from(">II", data, 8 + index * 8)
             for index in range(count)]
    if [source for source, _ in pairs] != sorted(
            {source for source, _ in pairs}):
        raise ValueError("Confusable table is not source-sorted and unique")
    return dict(pairs)


def canonical_hash(value: str, confusables: dict[int, int]) -> int:
    first = unicodedata.normalize("NFKC", value).casefold()
    mapped = "".join(chr(confusables.get(ord(character), ord(character)))
                     for character in first)
    canonical = unicodedata.normalize("NFKC", mapped.casefold())
    return xxhash.xxh3_64_intdigest(canonical.encode("utf-8"))


def mtg_key(signed_id: int) -> str:
    return f"Q{signed_id}"


def add_mtg_evidence(
    source: dict[str, Any],
    confusables: dict[int, int],
) -> tuple[dict[str, Any], set[str]]:
    records = copy.deepcopy(source)
    mtg_keys: set[str] = set()
    for offset, label in enumerate(MTG_LABELS):
        signed_id = MTG_FIRST_ID - offset
        key = mtg_key(signed_id)
        if key in records:
            raise ValueError(f"MTG evidence key collides with source genre: {key}")
        records[key] = {
            "labels": {"en": [label]},
            "relations": {},
            "synthetic_id": True,
            "source": "mtg-jamendo",
        }
        mtg_keys.add(key)

    names_by_key = {
        key: normalized_names(record)
        for key, record in records.items()
        if key not in mtg_keys
        and isinstance(record, dict)
        and record.get("labels")
    }
    alias_candidates: dict[int, set[str]] = {}
    for key, names in names_by_key.items():
        for _, name in names:
            digest = canonical_hash(name, confusables)
            alias_candidates.setdefault(digest, set()).add(key)

    for offset, label in enumerate(MTG_LABELS):
        signed_id = MTG_FIRST_ID - offset
        key = mtg_key(signed_id)
        target: str | None
        if signed_id in MTG_EXPLICIT_TARGETS:
            target = MTG_EXPLICIT_TARGETS[signed_id]
            if target is not None and target not in names_by_key:
                raise ValueError(
                    f"Explicit MTG target {target} for {key} is absent"
                )
        else:
            candidates = alias_candidates.get(
                canonical_hash(label, confusables), set())
            target = next(iter(candidates)) if len(candidates) == 1 else None
        if target is not None:
            records[key]["relations"] = {"same_as": [target]}
    return records, mtg_keys


def normalized_names(record: dict[str, Any]) -> list[tuple[str, str]]:
    labels = record.get("labels")
    if not isinstance(labels, dict):
        raise ValueError("Genre labels must be a locale map")
    result: list[tuple[str, str]] = []
    locales = sorted(labels, key=lambda locale: (locale != "en", locale))
    for locale in locales:
        values = labels[locale]
        if not isinstance(values, list):
            raise ValueError(f"Genre locale {locale!r} is not a name list")
        seen: set[str] = set()
        for value in values:
            if not isinstance(value, str) or not value.strip():
                continue
            folded = value.casefold()
            if folded not in seen:
                seen.add(folded)
                result.append((locale, value))
    if not result:
        raise ValueError("Every GNRE genre requires at least one display name")
    return result


def create_offset_vector(
    builder: flatbuffers.Builder,
    start: Any,
    offsets: list[int],
) -> int:
    start(builder, len(offsets))
    for offset in reversed(offsets):
        builder.PrependUOffsetTRelative(offset)
    return builder.EndVector()


def build(
    document: dict[str, Any],
    generation: int,
    include_mtg: bool = True,
) -> bytes:
    if generation <= 0 or generation > (1 << 31) - 1:
        raise ValueError("GNRE generation must be a positive int32")
    source_records = merge_project_genres(document).get("genres")
    if not isinstance(source_records, dict) or not source_records:
        raise ValueError("Merged genre document has no genres")
    confusables = read_confusables(CONFUSABLES)
    if include_mtg:
        records, mtg_keys = add_mtg_evidence(
            source_records, confusables)
    else:
        records, mtg_keys = copy.deepcopy(source_records), set()

    names_by_key = {
        key: normalized_names(record)
        for key, record in records.items()
        if isinstance(record, dict) and record.get("labels")
    }
    ordered = sorted(names_by_key, key=lambda key: genre_id(key))
    if not ordered:
        raise ValueError("Merged genre document has no named genres")
    positions = {key: index for index, key in enumerate(ordered)}
    alias_candidates: dict[int, set[int]] = {}
    for key in ordered:
        if key in mtg_keys:
            continue
        for _, name in names_by_key[key]:
            digest = canonical_hash(name, confusables)
            alias_candidates.setdefault(digest, set()).add(positions[key])
    # https://wiki.271.pics/Riff/LATEST/genre-processing
    # AI-INTENT: An ambiguous alias must not silently assign the wrong genre. Omit it
    # from the singular GNRE lookup table; other aliases remain available.
    aliases = {
        digest: next(iter(candidates))
        for digest, candidates in alias_candidates.items()
        if len(candidates) == 1
    }
    if not aliases:
        raise ValueError("Merged genre document has no unambiguous aliases")

    builder = flatbuffers.Builder(1024 * 1024)
    genre_offsets: list[int] = []
    for key in ordered:
        record = records[key]
        name_offsets = []
        for locale, name in names_by_key[key]:
            locale_offset = builder.CreateString(locale)
            name_offset = builder.CreateString(name)
            gnre.GenreNameStart(builder)
            gnre.GenreNameAddLocale(builder, locale_offset)
            gnre.GenreNameAddName(builder, name_offset)
            name_offsets.append(gnre.GenreNameEnd(builder))
        names_offset = create_offset_vector(
            builder, gnre.GenreStartNamesVector, name_offsets)

        relation_offsets = []
        relations = record.get("relations", {})
        if not isinstance(relations, dict):
            raise ValueError(f"Genre {key!r} relations are not an object")
        for kind in sorted(relations):
            if kind not in RELATION_VALUES:
                raise ValueError(f"Unsupported GNRE relation: {kind!r}")
            targets = relations[kind]
            if not isinstance(targets, list):
                raise ValueError(f"GNRE relation {kind!r} is not a list")
            retained_targets = {
                target for target in targets if target in positions}
            for target in sorted(retained_targets,
                                 key=lambda value: positions[value]):
                gnre.GenreRelationStart(builder)
                gnre.GenreRelationAddKind(builder, RELATION_VALUES[kind])
                gnre.GenreRelationAddGenreIndex(builder, positions[target])
                relation_offsets.append(gnre.GenreRelationEnd(builder))
        related_offset = create_offset_vector(
            builder,
            gnre.GenreStartRelatedVector,
            relation_offsets,
        )

        gnre.GenreStart(builder)
        gnre.GenreAddId(builder, genre_id(key))
        gnre.GenreAddRelated(builder, related_offset)
        gnre.GenreAddNames(builder, names_offset)
        genre_offsets.append(gnre.GenreEnd(builder))

    index_offsets = []
    for digest, position in sorted(aliases.items()):
        gnre.GenreIndexStart(builder)
        gnre.GenreIndexAddId(builder, digest)
        gnre.GenreIndexAddGenreIndex(builder, position)
        index_offsets.append(gnre.GenreIndexEnd(builder))

    genres_offset = create_offset_vector(
        builder, gnre.GenreListStartGenresVector, genre_offsets)
    indexes_offset = create_offset_vector(
        builder, gnre.GenreListStartIndexesVector, index_offsets)
    gnre.GenreListStart(builder)
    gnre.GenreListAddGeneration(builder, generation)
    gnre.GenreListAddGenres(builder, genres_offset)
    gnre.GenreListAddIndexes(builder, indexes_offset)
    root = gnre.GenreListEnd(builder)
    builder.Finish(root, file_identifier=b"GNRE")
    return bytes(builder.Output())


def write_atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    try:
        with temporary.open("wb") as handle:
            handle.write(data)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--generation", type=int, default=GNRE_GENERATION)
    arguments = parser.parse_args()
    document = json.loads(arguments.input.read_text(encoding="utf-8"))
    flatbuffer = build(document, arguments.generation)
    write_atomic(arguments.output, flatbuffer)
    print(
        f"GNRE generation {arguments.generation}: "
        f"{len(flatbuffer)} bytes -> {arguments.output}"
    )


if __name__ == "__main__":
    main()
