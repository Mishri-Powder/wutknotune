#!/usr/bin/env python3
"""Generate Riff's Kotlin-only FlatBuffers bindings and vendor its runtime."""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import subprocess
import tempfile
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VERSION = "25.12.19"
PACKAGE = "link.roadio.riff.indexer.fbs"
SCHEMAS = tuple(sorted((ROOT / "indexer/src/main/fbs").glob("*.fbs")))
GENERATED_ROOT = ROOT / "indexer/src/main/generated"
BINDING_OUTPUT = GENERATED_ROOT / "kotlin/link/roadio/riff/indexer/fbs"
ANALYSIS_OUTPUT = GENERATED_ROOT / "kotlin/link/roadio/riff/indexer/analysis/fbs"
RUNTIME_OUTPUT = GENERATED_ROOT / "kotlin/com/google/flatbuffers/kotlin"
PROVENANCE_OUTPUT = GENERATED_ROOT / "flatbuffers"
RUNTIME_BASE = (
    "https://raw.githubusercontent.com/google/flatbuffers/"
    f"v{VERSION}/kotlin/flatbuffers-kotlin/src"
)
UPSTREAM = {
    "Buffers.kt": (
        f"{RUNTIME_BASE}/commonMain/kotlin/com/google/flatbuffers/kotlin/Buffers.kt",
        "e47aa6bda46e97b5d3f0d812cdb5776f4b8874739028c83f3385643e2809513c",
    ),
    "ByteArray.kt": (
        f"{RUNTIME_BASE}/commonMain/kotlin/com/google/flatbuffers/kotlin/ByteArray.kt",
        "1e88ee6f1f8cce0a253b53d5f78da5bec292401e460b58214b7f8d71657994c3",
    ),
    "ByteArray.jvm.kt": (
        f"{RUNTIME_BASE}/jvmMain/kotlin/com/google/flatbuffers/kotlin/ByteArray.kt",
        "ea2571165e9573e3fed1ca9a5db175a8bb96993809de20d0aa91741ab49a57b4",
    ),
    "FlatBufferBuilder.kt": (
        f"{RUNTIME_BASE}/commonMain/kotlin/com/google/flatbuffers/kotlin/FlatBufferBuilder.kt",
        "914bac48998c7bb8308a8de56508ea787046b4fa11775568a3549ccd2d2a46dd",
    ),
    "Flatbuffers.kt": (
        f"{RUNTIME_BASE}/commonMain/kotlin/com/google/flatbuffers/kotlin/Flatbuffers.kt",
        "360b2e828edfaeb4c8a9f770204e12a60fcce0f90dee8f3e5ef9c0069c9da1d0",
    ),
    "Utf8.kt": (
        f"{RUNTIME_BASE}/commonMain/kotlin/com/google/flatbuffers/kotlin/Utf8.kt",
        "cbf13d25200855e7110bf0aead1ae4a55e77e04f539533a351860a42c06fd782",
    ),
    "LICENSE": (
        f"https://raw.githubusercontent.com/google/flatbuffers/v{VERSION}/LICENSE",
        "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
    ),
}


def download(
    name: str,
    runtime_cache: Path | None = None,
) -> bytes:
    url, expected = UPSTREAM[name]
    cached = runtime_cache / name if runtime_cache else None
    if cached and cached.is_file():
        data = cached.read_bytes()
    else:
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise RuntimeError(f"{name}: expected {expected}, got {actual}")
    return data


def merge_jvm_byte_array(common: str, jvm: str) -> str:
    """Combine upstream expect/actual declarations for a normal JVM source set."""
    # Upstream JVM bodies may span several lines. Preserve each complete declaration;
    # retaining only its first line silently drops the expression after an equals sign.
    # AI-INTENT: This is mechanical expect/actual adaptation, never a runtime fork.
    # https://wiki.271.pics/Riff/LATEST/dependency-security#update-acceptance-and-derived-data
    declarations = list(re.finditer(
        r"^public actual inline fun ByteArray\.(\w+)", jvm, re.MULTILINE,
    ))
    actual_by_name = {}
    for index, match in enumerate(declarations):
        end = declarations[index + 1].start() if index + 1 < len(declarations) else len(jvm)
        name = match.group(1)
        if name in actual_by_name:
            raise RuntimeError("Duplicate JVM actual declaration: " + name)
        actual_by_name[name] = jvm[match.start():end].strip().replace(" actual ", " ", 1)

    expected = set()
    merged = []
    for line in common.splitlines():
        match = re.match(r"(?:internal|public) expect inline fun ByteArray\.(\w+)", line)
        if match:
            name = match.group(1)
            if name in expected or name not in actual_by_name:
                raise RuntimeError("Missing or duplicate JVM actual declaration: " + name)
            expected.add(name)
            merged.append(actual_by_name[name])
        else:
            merged.append(line)
    if set(actual_by_name) != expected:
        raise RuntimeError("Unmatched JVM actual declarations: " + repr(set(actual_by_name) - expected))
    return "\n".join(merged) + "\n"


def replace_directory(source: Path, target: Path) -> None:
    if target.exists():
        shutil.rmtree(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source, target)


def generate_bindings(flatc: Path) -> None:
    version = subprocess.run(
        [str(flatc), "--version"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if version != f"flatc version {VERSION}":
        raise RuntimeError(f"Expected flatc {VERSION}, got {version!r}")
    with tempfile.TemporaryDirectory(prefix="riff-flatbuffers-") as temporary:
        raw = Path(temporary) / "raw"
        output = Path(temporary) / "packaged"
        raw.mkdir()
        output.mkdir()
        for schema in SCHEMAS:
            per_schema = raw / schema.stem
            per_schema.mkdir()
            subprocess.run(
                [
                    str(flatc),
                    "--kotlin-kmp",
                    "-I",
                    str(schema.parent),
                    "-o",
                    str(per_schema),
                    str(schema),
                ],
                check=True,
            )
        for generated in sorted(raw.rglob("*.kt")):
            text = generated.read_text().replace(
                "\n                builder.required", "\n            builder.required",
            )
            header = "// automatically generated by the FlatBuffers compiler, do not modify\n"
            if not text.startswith(header):
                raise RuntimeError("Unexpected generated header: " + generated.name)
            package = re.search(r"^package ([\w.]+)$", text, re.MULTILINE)
            namespace = package.group(1) if package else PACKAGE
            packaged = header + "\n@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)\n"
            if not package:
                packaged += f"\npackage {namespace}\n"
            packaged += text[len(header):]
            target = output / namespace.replace(".", "/") / generated.name
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.exists() and target.read_text() != packaged:
                raise RuntimeError("Conflicting generated binding: " + str(target))
            target.write_text(packaged)
        for target in (BINDING_OUTPUT, ANALYSIS_OUTPUT):
            replace_directory(output / target.relative_to(GENERATED_ROOT / "kotlin"), target)

        # The offline GNRE compiler uses the exact same schema as the Android consumer.
        subprocess.run([str(flatc), "--python", "--gen-onefile", "-o", str(raw),
                        str(ROOT / "indexer/src/main/fbs/gnre.fbs")], check=True)
        schema_hash = hashlib.sha256((ROOT / "indexer/src/main/fbs/gnre.fbs").read_bytes()).hexdigest()
        generated = (raw / "gnre_generated.py").read_text()
        normalized = "\n".join(line.rstrip() for line in generated.splitlines()).rstrip() + "\n"
        (ROOT / "tools/genre-retrieval/gnre_generated.py").write_text(
            f"# riff-schema-sha256: {schema_hash}\n# generated-with-flatc: {VERSION}\n" + normalized,
        )


def vendor_runtime(runtime_cache: Path | None) -> None:
    with tempfile.TemporaryDirectory(prefix="riff-flatbuffers-runtime-") as temporary:
        output = Path(temporary) / "runtime"
        output.mkdir()
        downloaded = {name: download(name, runtime_cache) for name in UPSTREAM}
        for name in ("Buffers.kt", "FlatBufferBuilder.kt", "Flatbuffers.kt", "Utf8.kt"):
            (output / name).write_bytes(downloaded[name])
        merged = merge_jvm_byte_array(
            downloaded["ByteArray.kt"].decode(),
            downloaded["ByteArray.jvm.kt"].decode(),
        )
        (output / "ByteArray.kt").write_text(merged)
        replace_directory(output, RUNTIME_OUTPUT)

        PROVENANCE_OUTPUT.mkdir(parents=True, exist_ok=True)
        (PROVENANCE_OUTPUT / "LICENSE").write_bytes(downloaded["LICENSE"])
        (PROVENANCE_OUTPUT / "PROVENANCE.md").write_text(
            "# FlatBuffers Kotlin source provenance\n\n"
            f"- Upstream: `google/flatbuffers` tag `v{VERSION}`\n"
            "- Binding generator: `flatc --kotlin-kmp`\n"
            "- Runtime: upstream `kotlin/flatbuffers-kotlin` common/JVM source\n"
            "- License: Apache-2.0; exact upstream `LICENSE` is adjacent\n\n"
            "`ByteArray.kt` mechanically combines the upstream common `expect` "
            "declarations with the upstream JVM `actual` implementations so the "
            "runtime compiles in the existing Android/JVM module. No FlatBuffers "
            "Java binding or Java runtime source is included.\n"
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--flatc", required=True, type=Path)
    parser.add_argument("--vendor-runtime", action="store_true")
    parser.add_argument("--runtime-cache", type=Path)
    args = parser.parse_args()
    generate_bindings(args.flatc)
    if args.vendor_runtime:
        vendor_runtime(args.runtime_cache)


if __name__ == "__main__":
    main()
