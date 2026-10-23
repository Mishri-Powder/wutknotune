#!/usr/bin/env python3
"""Validate private/external fixtures without including them in source distributions."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tarfile
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--fetch", action="store_true")
    parser.add_argument("--cli", type=Path, default=Path("cli/build/install/cuegrain/bin/cuegrain"))
    args = parser.parse_args()
    root = Path("evidence")
    root.mkdir(exist_ok=True)
    corpus = root / "user-cue"
    if args.archive:
        corpus.mkdir(exist_ok=True)
        with tarfile.open(args.archive) as archive:
            for member in archive.getmembers():
                if member.isfile():
                    archive.extract(member, corpus, filter="data")
    internet = root / "internet-cue"
    internet.mkdir(exist_ok=True)
    revision = "f35fd675703d8984c2ea26cca7cb403493368fd5"
    names = ["t/99_tracks.cue", "t/issue10.cue",
             "oss-fuzz/corpus/clusterfuzz-testcase-minimized-fuzz-6416083458064384.cue",
             "oss-fuzz/corpus/leak.cue"]
    provenance = []
    if args.fetch:
        for name in names:
            url = f"https://raw.githubusercontent.com/lipnitsk/libcue/{revision}/{name}"
            with urllib.request.urlopen(url, timeout=30) as response:
                data = response.read()
            destination = internet / Path(name).name
            destination.write_bytes(data)
            provenance.append({"url": url, "file": str(destination),
                               "sha256": hashlib.sha256(data).hexdigest(),
                               "license": "Upstream libcue license; research-only fixture"})
        (root / "internet-provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    paths = sorted(corpus.rglob("*.cue")) + sorted(internet.glob("*.cue"))
    hashes = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    process = subprocess.run([str(args.cli.resolve()), *map(str, paths)],
                             capture_output=True, text=True, timeout=120)
    output = json.loads(process.stdout)
    (root / "corpus-results.json").write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
    result = {
        "ced": output["ced"], "inputs": len(paths), "exitCode": process.returncode,
        "userInputs": len(list(corpus.rglob("*.cue"))),
        "sources": sum(len(r["sources"]) for r in output["results"]),
        "entries": sum(len(s["entries"]) for r in output["results"] for s in r["sources"]),
        "statusCounts": {}, "diagnosticCounts": {}, "encodingCounts": {},
        "failures": [],
    }
    for report in output["results"]:
        statuses = result["statusCounts"]
        statuses[report["status"]] = statuses.get(report["status"], 0) + 1
        for diagnostic in report["diagnostics"]:
            counts = result["diagnosticCounts"]
            counts[diagnostic["code"]] = counts.get(diagnostic["code"], 0) + 1
        for source in report["sources"]:
            encoding = source["attributes"].get("charset", "unknown")
            counts = result["encodingCounts"]
            counts[encoding] = counts.get(encoding, 0) + 1
        if report["status"] != "ok":
            result["failures"].append({"input": report["input"], "diagnostics": report["diagnostics"]})
    assert hashes == {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    result["inputBytesUnchanged"] = True
    assert not process.stderr, process.stderr
    (root / "corpus-summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({key: value for key, value in result.items() if key != "failures"},
                     ensure_ascii=False, indent=2))
    print("Detailed diagnostics:", root / "corpus-summary.json")


if __name__ == "__main__":
    main()
