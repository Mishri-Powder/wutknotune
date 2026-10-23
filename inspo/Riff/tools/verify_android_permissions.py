#!/usr/bin/env python3
"""Enforce the current offline capability contract on the fully merged release manifest.

Authority: https://wiki.271.pics/Riff/LATEST/runtime-architecture#packaged-android-permissions
"""

import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
PERMISSIONS = {
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "android.permission.WAKE_LOCK",
    "android.permission.WRITE_SETTINGS",
}


def verify(path: Path) -> None:
    root = ET.parse(path).getroot()
    requested = {
        node.get(ANDROID + "name")
        for node in root
        if node.tag.startswith("uses-permission")
    }
    if requested != PERMISSIONS:
        raise ValueError(
            f"Release permissions differ: missing={sorted(PERMISSIONS - requested)}, "
            f"unexpected={sorted(requested - PERMISSIONS)}"
        )
    if root.findall("permission"):
        raise ValueError("Release must not declare an application-specific permission")
    app = root.find("application")
    if app is None:
        raise ValueError("Release application is missing")
    for node in app.iter():
        name = node.get(ANDROID + "name", "")
        if name.startswith(("androidx.work.", "com.google.android.play.core.assetpacks.")):
            raise ValueError(f"Unused runtime entry point: {name}")
    playback = next(
        (node for node in app.findall("service")
         if node.get(ANDROID + "name") == "link.roadio.riff.player.playback.RiffPlaybackService"),
        None,
    )
    if playback is None or playback.get(ANDROID + "foregroundServiceType") != "mediaPlayback":
        raise ValueError("Playback service must retain exactly its mediaPlayback foreground type")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    try:
        verify(args.manifest)
    except (ValueError, ET.ParseError) as exc:
        parser.exit(1, f"Android permission verification failed: {exc}\n")
    print("Merged release permissions: exactly 4; offline entry-point policy passed")


if __name__ == "__main__":
    main()
