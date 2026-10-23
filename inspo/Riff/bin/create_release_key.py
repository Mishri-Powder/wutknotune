#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Create Riff's persistent direct-distribution application-signing key once."""

from __future__ import annotations

import hashlib
import os
import secrets
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SECRET_ROOT = ROOT / ".secrets" / "release-signing"
KEYSTORE = SECRET_ROOT / "riff-release.jks"
STORE_PASSWORD = SECRET_ROOT / "store.pass"
KEY_PASSWORD = SECRET_ROOT / "key.pass"
CERTIFICATE = SECRET_ROOT / "riff-release-certificate.der"
ALIAS = "riff-release"


def main() -> None:
    if SECRET_ROOT.exists():
        raise SystemExit(
            f"Refusing to replace existing signing state: {SECRET_ROOT}"
        )
    SECRET_ROOT.mkdir(parents=True, mode=0o700)
    store_password = secrets.token_urlsafe(48)
    key_password = secrets.token_urlsafe(48)
    STORE_PASSWORD.write_text(store_password + "\n")
    KEY_PASSWORD.write_text(key_password + "\n")
    os.chmod(STORE_PASSWORD, 0o600)
    os.chmod(KEY_PASSWORD, 0o600)
    subprocess.run(
        [
            "keytool",
            "-genkeypair",
            "-keystore",
            str(KEYSTORE),
            "-storetype",
            "JKS",
            "-storepass",
            store_password,
            "-keypass",
            key_password,
            "-alias",
            ALIAS,
            "-keyalg",
            "RSA",
            "-keysize",
            "4096",
            "-sigalg",
            "SHA256withRSA",
            "-validity",
            "10000",
            "-dname",
            "CN=Riff Release,O=Riff",
        ],
        check=True,
        stdout=subprocess.DEVNULL,
    )
    subprocess.run(
        [
            "keytool",
            "-exportcert",
            "-keystore",
            str(KEYSTORE),
            "-storepass",
            store_password,
            "-alias",
            ALIAS,
            "-file",
            str(CERTIFICATE),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
    )
    for path in (KEYSTORE, CERTIFICATE):
        os.chmod(path, 0o600)
    fingerprint = hashlib.sha256(CERTIFICATE.read_bytes()).hexdigest()
    print(f"Created persistent Riff release key; certificate SHA-256: {fingerprint}")


if __name__ == "__main__":
    main()
