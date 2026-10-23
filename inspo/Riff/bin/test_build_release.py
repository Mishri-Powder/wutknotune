# SPDX-License-Identifier: GPL-3.0-or-later
"""Release tooling regressions; no signing key or device is required."""

import hashlib
import importlib.util
import os
import json
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "build_release", Path(__file__).with_name("build_release.py")
)
builder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(builder)


class ReleaseToolingTest(unittest.TestCase):
    def test_sdk_environment_precedence(self):
        environment = {
            "ANDROID_HOME": "/sdk/main", "ANDROID_SDK_ROOT": "/sdk/old"
        }
        with patch.dict(os.environ, environment, clear=True):
            self.assertEqual(Path("/sdk/main"), builder.sdk_root())
        with patch.dict(
            os.environ, {"ANDROID_SDK_ROOT": "/sdk/compat"}, clear=True
        ):
            self.assertEqual(Path("/sdk/compat"), builder.sdk_root())
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(Path.home() / "Android/Sdk", builder.sdk_root())

    def test_preview_tools_do_not_change_release_toolchain(self):
        with tempfile.TemporaryDirectory() as temporary:
            sdk = Path(temporary)
            pinned = sdk / "build-tools" / "36.0.0"
            pinned.mkdir(parents=True)
            (sdk / "build-tools" / "37.0.0-rc1").mkdir()
            for name in ("zipalign", "apksigner"):
                (pinned / name).touch()
            self.assertEqual(pinned, builder.build_tools(sdk))
            (pinned / "apksigner").unlink()
            with self.assertRaisesRegex(SystemExit, "incomplete"):
                builder.build_tools(sdk)

    def test_missing_sdk_reports_actionable_error(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(SystemExit, "36.0.0"):
                builder.build_tools(Path(temporary))

    def test_model_provenance_describes_the_apk(self):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / "release.apk"
            model = b"artifact model, independent of checkout"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr(
                    "assets/level2/riff-level2-efficientat-eval-v2.tflite", model
                )
            self.assertEqual(
                hashlib.sha256(model).hexdigest(),
                builder.packaged_model_sha256(apk),
            )


    def test_release_uses_selected_sdk_and_new_output_without_overwrite(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sdk = root / "sdk"
            output = root / "artifacts"
            unsigned = root / "app/build/outputs/apk/release/app-release-unsigned.apk"
            unsigned.parent.mkdir(parents=True)
            with zipfile.ZipFile(unsigned, "w") as archive:
                archive.writestr(
                    "assets/level2/riff-level2-efficientat-eval-v2.tflite",
                    b"test model",
                )
            key = root / "test-key"
            key.touch()
            tools = sdk / "build-tools/36.0.0"
            tools.mkdir(parents=True)
            for name in ("zipalign", "apksigner"):
                (tools / name).touch()

            def command(arguments, **options):
                if arguments[0] == str(root / "gradlew"):
                    self.assertEqual(":app:verifyDistributionRelease", arguments[1])
                    self.assertEqual(str(sdk), options["env"]["ANDROID_HOME"])
                    self.assertEqual(str(sdk), options["env"]["ANDROID_SDK_ROOT"])
                elif arguments[0] == str(tools / "zipalign"):
                    shutil.copyfile(arguments[-2], arguments[-1])
                elif "sign" in arguments:
                    shutil.copyfile(
                        arguments[-1], arguments[arguments.index("--out") + 1]
                    )
                return subprocess.CompletedProcess(
                    arguments, 0, "certificate SHA-256 digest: abc123"
                )

            with (
                patch.object(builder, "ROOT", root),
                patch.object(builder, "KEYSTORE", key),
                patch.object(builder, "STORE_PASSWORD", key),
                patch.object(builder, "KEY_PASSWORD", key),
                patch.dict(os.environ, {"ANDROID_HOME": str(sdk)}, clear=True),
                patch("sys.argv", ["builder", "--output-dir", str(output)]),
                patch.object(builder.subprocess, "run", side_effect=command) as run,
                patch("builtins.print"),
            ):
                builder.main()
                manifest = json.loads(
                    (output / "release-manifest.json").read_text()
                )
                self.assertEqual(
                    hashlib.sha256(b"test model").hexdigest(),
                    manifest["level2_model_sha256"],
                )
                self.assertEqual(builder.VERSION_NAME, manifest["version_name"])
                self.assertEqual(builder.RELEASE_CHANNEL, manifest["release_identity"]["channel"])
                self.assertEqual(
                    None if builder.RELEASE_CHANNEL == "FINAL" else f"{builder.VISIBLE_CHANNEL}: {builder.RELEASE_DATE_COMPACT}",
                    manifest["release_identity"]["overlay"],
                )
                artifact = Path(manifest["artifact"])
                self.assertIn(builder.VERSION_NAME, artifact.name)
                self.assertEqual(output, artifact.parent)
                original = artifact.read_bytes()
                calls = run.call_count
                with self.assertRaisesRegex(SystemExit, "Refusing to overwrite"):
                    builder.main()
                self.assertEqual(calls, run.call_count)
                self.assertEqual(original, artifact.read_bytes())


if __name__ == "__main__":
    unittest.main()
