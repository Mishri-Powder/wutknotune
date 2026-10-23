#!/usr/bin/env python3
"""Independent FFmpeg/Mutagen integration checks; neither is a runtime dependency."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

from mutagen.flac import FLAC, Picture, CueSheet, CueSheetTrack, CueSheetTrackIndex
from mutagen.id3 import ID3, TIT2, TXXX, CHAP, CTOC, APIC
from mutagen.mp4 import MP4, MP4Cover, MP4FreeForm
from mutagen.oggopus import OggOpus
from mutagen.oggvorbis import OggVorbis


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cli", type=Path, default=Path("cli/build/install/cuegrain/bin/cuegrain"))
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()
    command = ["java", "-jar", str(args.jar.resolve())] if args.jar else [str(args.cli.resolve())]
    root = Path("evidence/generated")
    root.mkdir(parents=True, exist_ok=True)
    checks = []

    def run(arguments, expected=0):
        p = subprocess.run([*command, *map(str, arguments)], capture_output=True, text=True, timeout=30)
        assert p.returncode == expected, (arguments, p.returncode, p.stdout, p.stderr)
        assert not p.stderr, p.stderr
        return json.loads(p.stdout)

    for arguments, expected in [(["--help"], 0), (["--version"], 0),
                                ([], 2), (["--unknown"], 2),
                                (["--charset"], 2), (["--charset", "invalid"], 2),
                                (["evidence/missing-file.mp3"], 1)]:
        run(arguments, expected)
        checks.append("JSON CLI: " + repr(arguments))
    fixture = root / "track.mp3"
    paths = []
    for extension, codec in [("mp3", "libmp3lame"), ("m4a", "aac"), ("flac", "flac"),
                             ("ogg", "libvorbis"), ("opus", "libopus")]:
        path = root / ("track." + extension)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                        "anullsrc=r=44100:cl=stereo", "-t", "2", "-c:a", codec,
                        "-metadata", "title=Cuegrain Δ", str(path)], check=True,
                       stdin=subprocess.DEVNULL, timeout=30)
        paths.append(path)
    cue = 'TITLE "Album"\nFILE "track.mp3" MP3\nTRACK 01 AUDIO\nTITLE "Cue chapter"\nINDEX 01 00:00:00\n'
    picture = b"\xff\xd8\xff\xd9"
    mp3 = ID3(fixture)
    mp3.add(TXXX(encoding=3, desc="REPLAYGAIN_TRACK_GAIN", text=["-3.25 dB"]))
    mp3.add(TXXX(encoding=3, desc="REPLAYGAIN_TRACK_PEAK", text=["1.25"]))
    mp3.add(TXXX(encoding=3, desc="CUESHEET", text=[cue.replace("track.mp3", "not-auto-followed.wav")]))
    mp3.add(CHAP(element_id="c1", start_time=125, end_time=1000,
                 sub_frames=[TIT2(encoding=3, text=["First chapter"])]))
    mp3.add(CTOC(element_id="toc", flags=3, child_element_ids=["c1"]))
    mp3.add(APIC(encoding=3, mime="image/jpeg", type=3, desc="Front", data=picture))
    mp3.save(fixture, v2_version=4)
    mp4 = MP4(root / "track.m4a")
    mp4["----:com.apple.iTunes:replaygain_track_gain"] = [MP4FreeForm(b"-3.25 dB")]
    mp4["covr"] = [MP4Cover(picture, imageformat=MP4Cover.FORMAT_JPEG)]
    mp4.save()
    for extension, cls in [("flac", FLAC), ("ogg", OggVorbis), ("opus", OggOpus)]:
        media = cls(root / ("track." + extension))
        media["REPLAYGAIN_TRACK_GAIN"] = ["-3.25 dB"]
        media["REPLAYGAIN_TRACK_PEAK"] = ["1.25"]
        media["ARTIST"] = ["First artist", "Second artist"]
        media["CHAPTER001"] = ["00:00:00.125"]
        media["CHAPTER001NAME"] = ["First chapter"]
        if extension == "opus":
            media["R128_TRACK_GAIN"] = ["-512"]
        if extension == "flac":
            media["CUESHEET"] = [cue.replace("track.mp3", "track.flac")]
            cover = Picture()
            cover.type = 3
            cover.mime = "image/jpeg"
            cover.data = picture
            media.add_picture(cover)
            sheet = CueSheet(None)
            sheet.media_catalog_number = b""
            sheet.lead_in_samples = 88200
            sheet.compact_disc = True
            track = CueSheetTrack(1, 0)
            track.indexes = [CueSheetTrackIndex(1, 0)]
            sheet.tracks = [track, CueSheetTrack(170, media.info.total_samples)]
            media.cuesheet = sheet
            media.metadata_blocks.append(sheet)
        media.save()
    before = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    result = run(paths)
    assert len(result["results"]) == 5
    for report in result["results"]:
        assert report["status"] == "ok", report["diagnostics"]
        sources = report["sources"]
        titles = [v for s in sources for v in s["metadata"].get("title", [])]
        assert "Cuegrain Δ" in titles, (report["input"], titles)
        gains = [g for s in sources for g in s["gains"] if g["kind"] == "replaygain"]
        assert any(g["value"] == -3.25 and g["unit"] == "dB" for g in gains)
        checks.append("Independent metadata: " + Path(report["input"]).suffix)
    mp3_report = result["results"][0]
    assert {"id3", "cue"} <= {s["kind"] for s in mp3_report["sources"]}
    assert next(s for s in mp3_report["sources"] if s["kind"] == "id3")["tables"][0]["children"] == ["c1"]
    flac_report = next(r for r in result["results"] if r["input"].endswith(".flac"))
    assert {"flac.cuesheet", "cue", "vorbis", "flac.picture"} <= {s["kind"] for s in flac_report["sources"]}
    pictures = run(["--artwork", *paths[:3]])
    assert all(any(a.get("base64") == "/9j/2Q==" for s in r["sources"] for a in s["artwork"])
               for r in pictures["results"])
    checks.append("Explicit artwork extraction in MP3/MP4/FLAC")
    sidecar = root / "album.cue"
    sidecar.write_text(cue, encoding="utf-8")
    followed = run(["--follow-cue", sidecar])
    assert len(followed["results"]) == 2
    assert len(run(["--follow-cue", fixture])["results"]) == 1
    assert before == {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    checks.append("CUE group references and byte-identical read-only media")
    summaries = json.loads(Path("evidence/corpus-summary-ced.json").read_text())
    unresolved = [r["input"] for r in summaries["failures"] if "/user-cue/" in r["input"]]
    if unresolved:
        recovered = run(["--charset", "windows-1252", *unresolved])
        assert all(r["status"] == "ok" for r in recovered["results"])
        (Path("evidence") / "explicit-charset-results.json").write_text(
            json.dumps(recovered, ensure_ascii=False, indent=2) + "\n")
        checks.append(f"Explicit windows-1252 syntax/metadata recovery: {len(unresolved)} CUEs")
    summary = {"checks": checks, "passed": len(checks), "inputBytesUnchanged": True,
               "ced": result["ced"], "mutagenVersion": "1.47.0"}
    Path("evidence/cli-integration-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
