#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build, align, sign and verify the source-declared Riff distribution APK."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from project_config import RELEASE_CHANNEL, RELEASE_DATE  # noqa: E402


BUILD_TOOLS_VERSION = "36.0.0"
SECRET_ROOT = ROOT / ".secrets" / "release-signing"
KEYSTORE = SECRET_ROOT / "riff-release.jks"
STORE_PASSWORD = SECRET_ROOT / "store.pass"
KEY_PASSWORD = SECRET_ROOT / "key.pass"
ALIAS = "riff-release"
RELEASE_DATE_COMPACT = RELEASE_DATE.strftime("%y%m%d")
RELEASE_SUFFIX = {
    "ALPHA": "alpha",
    "BETA": "public-beta",
    "RC": "rc",
    "FINAL": "",
}[RELEASE_CHANNEL]
APP_VERSION = re.search(r"versionName\s*=\s*'([^']+)'", (ROOT / "app/build.gradle").read_text()).group(1)
VERSION_NAME = APP_VERSION + (f"-{RELEASE_SUFFIX}" if RELEASE_SUFFIX else "")
VISIBLE_CHANNEL = {
    "ALPHA": "Alpha",
    "BETA": "Beta",
    "RC": "RC",
    "FINAL": "Final",
}[RELEASE_CHANNEL]
OUTPUT_ROOT = ROOT / "releases" / f"{RELEASE_DATE_COMPACT}-{RELEASE_SUFFIX or 'final'}"
OUTPUT_APK = OUTPUT_ROOT / f"riff-{VERSION_NAME}-{RELEASE_DATE_COMPACT}.apk"
OUTPUT_MANIFEST = OUTPUT_ROOT / "release-manifest.json"


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def packaged_model_sha256(apk: Path) -> str:
    with zipfile.ZipFile(apk) as archive:
        with archive.open("assets/level2/riff-level2-efficientat-eval-v2.tflite") as model:
            digest = hashlib.sha256()
            for block in iter(lambda: model.read(1024 * 1024), b""):
                digest.update(block)
            return digest.hexdigest()


def sdk_root() -> Path:
    """Keep Gradle and the external signing tools on the same SDK installation."""
    configured = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if configured:
        return Path(configured).expanduser().resolve()
    return Path.home() / "Android" / "Sdk"


def build_tools(sdk: Path) -> Path:
    # https://wiki.271.pics/Riff/LATEST/distribution
    # AI-INTENT: Match the AGP toolchain declared in BUILD.md. Selecting the newest directory
    # makes a preview SDK installation silently change release signing/alignment.
    directory = sdk / "build-tools" / BUILD_TOOLS_VERSION
    missing = [
        name for name in ("zipalign", "apksigner")
        if not (directory / name).is_file()
    ]
    if missing:
        raise SystemExit(
            f"Android SDK Build Tools {BUILD_TOOLS_VERSION} "
            f"are incomplete at {directory}: {missing}"
        )
    return directory


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=OUTPUT_ROOT,
        help="New artifact directory; existing APKs/manifests are never overwritten.",
    )
    arguments = parser.parse_args()
    output_root = arguments.output_dir.expanduser().resolve()
    output_apk = output_root / OUTPUT_APK.name
    output_manifest = output_root / OUTPUT_MANIFEST.name
    sdk = sdk_root()
    tools = build_tools(sdk)
    required = (KEYSTORE, STORE_PASSWORD, KEY_PASSWORD)
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise SystemExit(f"Release signing state is incomplete: {missing}")
    if output_apk.exists() or output_manifest.exists():
        raise SystemExit(f"Refusing to overwrite existing release: {output_root}")

    environment = os.environ.copy()
    environment["ANDROID_HOME"] = str(sdk)
    environment["ANDROID_SDK_ROOT"] = str(sdk)
    subprocess.run(
        [str(ROOT / "gradlew"), ":app:verifyDistributionRelease"],
        cwd=ROOT,
        env=environment,
        check=True,
    )
    unsigned = (
        ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk"
    )
    if not unsigned.is_file():
        raise SystemExit(f"Unsigned release input is missing: {unsigned}")

    zipalign = tools / "zipalign"
    apksigner = tools / "apksigner"
    output_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="riff-release-", dir=output_root) as temporary:
        temporary_root = Path(temporary)
        aligned = temporary_root / "aligned.apk"
        signed = temporary_root / "signed.apk"
        subprocess.run(
            [str(zipalign), "-f", "-p", "4", str(unsigned), str(aligned)],
            check=True,
        )
        subprocess.run(
            [
                str(apksigner),
                "sign",
                "--ks",
                str(KEYSTORE),
                "--ks-key-alias",
                ALIAS,
                "--ks-pass",
                f"file:{STORE_PASSWORD}",
                "--key-pass",
                f"file:{KEY_PASSWORD}",
                "--out",
                str(signed),
                str(aligned),
            ],
            check=True,
        )
        verification = subprocess.run(
            [str(apksigner), "verify", "--verbose", "--print-certs", str(signed)],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
        match = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", verification)
        if match is None:
            raise SystemExit("apksigner did not report the signing certificate digest")
        shutil.move(signed, output_apk)

    manifest = {
        "artifact": str(output_apk),
        "bytes": output_apk.stat().st_size,
        "package": "link.roadio.riff",
        "version_name": VERSION_NAME,
        "release_identity": {
            "channel": RELEASE_CHANNEL,
            "date": RELEASE_DATE.isoformat(),
            "overlay": None if RELEASE_CHANNEL == "FINAL" else f"{VISIBLE_CHANNEL}: {RELEASE_DATE_COMPACT}",
        },
        "sha256": file_sha256(output_apk),
        "signing_certificate_sha256": match.group(1),
        # Derive provenance from the verified APK, not a second model hash in source.
        # https://wiki.271.pics/Riff/LATEST/distribution
        # AI-INTENT: The manifest describes the distributed artifact even if checkout assets change.
        "level2_model_sha256": packaged_model_sha256(output_apk),
    }
    temporary_manifest = output_manifest.with_suffix(".json.tmp")
    temporary_manifest.write_text(json.dumps(manifest, indent=2) + "\n")
    os.replace(temporary_manifest, output_manifest)
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
