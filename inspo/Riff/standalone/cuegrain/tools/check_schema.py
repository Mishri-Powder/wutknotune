#!/usr/bin/env python3
"""Validate CLI envelopes and saved reports using the optional jsonschema oracle."""
import argparse
import json
from pathlib import Path
import subprocess

from jsonschema import Draft202012Validator


def main():
    parser = argparse.ArgumentParser()
    launcher = parser.add_mutually_exclusive_group(required=True)
    launcher.add_argument("--cli", type=Path)
    launcher.add_argument("--jar", type=Path)
    parser.add_argument("reports", nargs="*", type=Path)
    args = parser.parse_args()
    command = ["java", "-jar", str(args.jar.resolve())] if args.jar else [str(args.cli.resolve())]
    schema = json.loads(Path("schema.json").read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    count = 0
    for arguments in [[], ["--help"], ["--version"], ["--unknown"], ["missing.mp3"]]:
        result = subprocess.run([*command, *arguments], capture_output=True, text=True, timeout=30)
        assert not result.stderr
        validator.validate(json.loads(result.stdout))
        count += 1
    for path in args.reports:
        data = json.loads(path.read_text())
        for report in data if isinstance(data, list) else [data]:
            validator.validate(report)
            count += 1
    print(json.dumps({"schemaVersion": 1, "documentsValidated": count}))


if __name__ == "__main__":
    main()
