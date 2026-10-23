"""Compile the sole packaged JSON source into allocation-only Kotlin defaults.

The shared Kotlin validator checks the generated document before either APK is built.
Runtime bootstrap therefore requires neither JSON parsing nor resource/storage I/O.
"""

import json
import sys
from pathlib import Path


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate configuration key: {key}")
        result[key] = value
    return result


def kotlin(value):
    if isinstance(value, dict):
        entries = [f"{kotlin(key)} to {kotlin(item)}" for key, item in value.items()]
        return "configObject(" + ",\n".join(entries) + ")"
    if isinstance(value, list):
        return "configArray(" + ", ".join(map(kotlin, value)) + ")"
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False).replace("$", "\\$")
    if isinstance(value, bool):
        return str(value).lower()
    if isinstance(value, int):
        return str(value) + "L"
    if isinstance(value, float):
        return repr(value)
    raise ValueError(f"Unsupported configuration value: {value!r}")


def main():
    source, output = map(Path, sys.argv[1:])
    document = json.loads(source.read_text(), object_pairs_hook=unique_object)
    target = output / "link/roadio/riff/config/PackagedConfiguration.kt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        "// Generated from app/src/main/config/ui_config.json. Do not edit.\n"
        "package link.roadio.riff.config\n\n"
        "object PackagedConfiguration {\n"
        f"    internal val document = {kotlin(document)}\n"
        "    val snapshot = ConfigurationSnapshot.packaged(document)\n"
        "}\n"
    )


if __name__ == "__main__":
    main()
