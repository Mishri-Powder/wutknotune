#!/usr/bin/env python3
"""Refresh and compile the official Unicode one-to-one confusable mappings."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import struct
import tempfile
import urllib.request
from pathlib import Path


SOURCE_URL = "https://www.unicode.org/Public/security/latest/confusables.txt"
MAGIC = b"RCF1"
ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CACHE = Path(__file__).resolve().parent / "cache"
DEFAULT_OUTPUT = (
    ROOT
    / "indexer/src/main/resources/link/roadio/riff/indexer/core/tags/confusables"
)
VERSION_RE = re.compile(r"^# Version:\s*([^\s]+)\s*$", re.MULTILINE)
SHA_RE = re.compile(r"^[0-9a-f]{64}$")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def parse_source(data: bytes) -> tuple[str, list[tuple[int, int]]]:
    text = data.decode("utf-8-sig")
    version_match = VERSION_RE.search(text)
    if version_match is None:
        raise ValueError("confusables.txt has no declared Unicode version")
    mappings: dict[int, int] = {}
    parsed = 0
    for number, raw_line in enumerate(text.splitlines(), start=1):
        content = raw_line.partition("#")[0].strip()
        if not content:
            continue
        fields = [field.strip() for field in content.split(";")]
        if len(fields) < 3:
            raise ValueError(f"line {number}: expected at least three fields")
        source = [int(token, 16) for token in fields[0].split()]
        target = [int(token, 16) for token in fields[1].split()]
        if not source or not target:
            raise ValueError(f"line {number}: empty source or target")
        if any(code_point < 0 or code_point > 0x10FFFF for code_point in source + target):
            raise ValueError(f"line {number}: invalid Unicode code point")
        parsed += 1
        if len(source) == 1 and len(target) == 1:
            previous = mappings.setdefault(source[0], target[0])
            if previous != target[0]:
                raise ValueError(f"line {number}: conflicting one-to-one mapping")
    if parsed < 1_000 or len(mappings) < 1_000:
        raise ValueError("confusables.txt is implausibly small")
    return version_match.group(1), sorted(mappings.items())


def build_table(pairs: list[tuple[int, int]]) -> bytes:
    payload = bytearray(struct.pack(">4sI", MAGIC, len(pairs)))
    for source, target in pairs:
        payload.extend(struct.pack(">II", source, target))
    return bytes(payload)


def parse_properties(path: Path) -> dict[str, str]:
    properties = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        if not raw_line or raw_line.startswith("#"):
            continue
        key, separator, value = raw_line.partition("=")
        if not separator:
            raise ValueError(f"malformed property: {raw_line}")
        properties[key] = value
    return properties


def verify_generated(output_dir: Path) -> dict[str, str]:
    table_path = output_dir / "confusables-one-to-one.bin"
    manifest_path = output_dir / "confusables-one-to-one.properties"
    table = table_path.read_bytes()
    if len(table) < 8:
        raise ValueError("generated table is truncated")
    magic, count = struct.unpack_from(">4sI", table)
    if magic != MAGIC or len(table) != 8 + count * 8:
        raise ValueError("generated table header or length is invalid")
    pairs = [struct.unpack_from(">II", table, 8 + index * 8) for index in range(count)]
    if pairs != sorted(pairs) or len({source for source, _ in pairs}) != count:
        raise ValueError("generated table is not strictly source-sorted")
    properties = parse_properties(manifest_path)
    expected = {
        "formatVersion": "1",
        "sourceUrl": SOURCE_URL,
        "entryCount": str(count),
        "tableSha256": sha256(table),
    }
    for key, value in expected.items():
        if properties.get(key) != value:
            raise ValueError(f"manifest mismatch for {key}")
    if not SHA_RE.fullmatch(properties.get("sourceSha256", "")):
        raise ValueError("manifest sourceSha256 is invalid")
    if not properties.get("unicodeVersion"):
        raise ValueError("manifest Unicode version is missing")
    return properties


def atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=path.name + ".", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def download(cache_dir: Path) -> Path:
    request = urllib.request.Request(
        SOURCE_URL,
        headers={"User-Agent": "Riff-confusables-refresh/1.0"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        data = response.read()
    parse_source(data)
    destination = cache_dir / "confusables.txt"
    atomic_write(destination, data)
    return destination


def generate(source_path: Path, output_dir: Path) -> dict[str, str]:
    source = source_path.read_bytes()
    version, pairs = parse_source(source)
    table = build_table(pairs)
    properties = {
        "formatVersion": "1",
        "unicodeVersion": version,
        "sourceUrl": SOURCE_URL,
        "sourceSha256": sha256(source),
        "entryCount": str(len(pairs)),
        "tableSha256": sha256(table),
        "licenseUrl": "https://www.unicode.org/terms_of_use.html",
    }
    manifest = "".join(f"{key}={value}\n" for key, value in properties.items()).encode()
    atomic_write(output_dir / "confusables-one-to-one.bin", table)
    atomic_write(output_dir / "confusables-one-to-one.properties", manifest)
    verify_generated(output_dir)
    return properties


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    if args.verify:
        properties = verify_generated(args.output_dir)
    else:
        source = download(args.cache_dir) if args.refresh else args.cache_dir / "confusables.txt"
        if not source.is_file():
            raise SystemExit("No cached confusables.txt; run with --refresh explicitly")
        properties = generate(source, args.output_dir)
    print(" ".join(f"{key}={value}" for key, value in properties.items()))


if __name__ == "__main__":
    main()
