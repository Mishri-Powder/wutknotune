#!/usr/bin/env python3
"""Verify the raw, mmap-compatible GNRE APK asset without third-party modules."""

from __future__ import annotations

import argparse
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from project_config import CATALOG_VOCABULARY, GNRE_GENERATION  # noqa: E402


def generation(data: bytes) -> int:
    if len(data) < 16 or data[4:8] != b"GNRE":
        raise ValueError("GNRE FlatBuffer identifier is absent")
    root = struct.unpack_from("<I", data, 0)[0]
    if root + 4 > len(data):
        raise ValueError("GNRE root table is outside the buffer")
    vtable_distance = struct.unpack_from("<i", data, root)[0]
    vtable = root - vtable_distance
    if vtable < 0 or vtable + 6 > len(data):
        raise ValueError("GNRE root vtable is outside the buffer")
    vtable_size = struct.unpack_from("<H", data, vtable)[0]
    if vtable_size < 6:
        raise ValueError("GNRE generation field is absent")
    field = struct.unpack_from("<H", data, vtable + 4)[0]
    if field == 0 or root + field + 4 > len(data):
        raise ValueError("GNRE generation field is invalid")
    value = struct.unpack_from("<i", data, root + field)[0]
    if value <= 0:
        raise ValueError("GNRE generation must be positive")
    return value


def genre_ids(data: bytes) -> set[int]:
    """Read identity keys from the existing GNRE schema for build-time role validation."""
    def field(table, ordinal):
        vtable = table - struct.unpack_from("<i", data, table)[0]
        size = struct.unpack_from("<H", data, vtable)[0]
        slot = 4 + ordinal * 2
        offset = struct.unpack_from("<H", data, vtable + slot)[0] if slot < size else 0
        return table + offset if offset else None

    root = struct.unpack_from("<I", data, 0)[0]
    vector_field = field(root, 1)
    if vector_field is None:
        raise ValueError("GNRE genres are absent")
    vector = vector_field + struct.unpack_from("<I", data, vector_field)[0]
    count = struct.unpack_from("<I", data, vector)[0]
    if count > (len(data) - vector - 4) // 4:
        raise ValueError("GNRE genre vector is outside the buffer")
    result = set()
    for index in range(count):
        slot = vector + 4 + index * 4
        table = slot + struct.unpack_from("<I", data, slot)[0]
        key = field(table, 0)
        result.add(struct.unpack_from("<Q", data, key)[0] if key else 0)
    return result


def verify_catalog_roles(data: bytes) -> None:
    required = {value for values in CATALOG_VOCABULARY["genreRoles"].values()
                for value in values}
    missing = required - genre_ids(data)
    if missing:
        raise ValueError(f"Catalog roles reference absent GNRE IDs: {sorted(missing)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("asset", type=Path)
    arguments = parser.parse_args()
    data = arguments.asset.read_bytes()
    value = generation(data)
    if value != GNRE_GENERATION:
        raise ValueError(f"Expected GNRE generation {GNRE_GENERATION}, got {value}")
    verify_catalog_roles(data)
    print(f"Verified raw GNRE generation {value}: {len(data)} FlatBuffer bytes")


if __name__ == "__main__":
    main()
