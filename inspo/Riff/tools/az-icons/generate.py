"""Construct the AZ glyph family from fixed equal-width geometric skeletons.

No fonts, tracing, model generation, or modifications of existing icons are used.
Run with a staging directory; promotion is a separate Wiki-adopted operation.
"""

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from xml.etree import ElementTree

# Coordinates are local equal-width 6 x 8 glyph cells. All terminals are rounded.
GLYPHS = {
    "A": "M0,8 L3,0 L6,8 M1,5 H5",
    "B": "M0,8 V0 H3 Q6,0 6,2 Q6,4 3,4 H0 M3,4 Q6,4 6,6 Q6,8 3,8 H0",
    "C": "M6,1 Q0,-2 0,4 Q0,10 6,7",
    "D": "M0,0 V8 H2 Q6,8 6,4 Q6,0 2,0 Z",
    "E": "M6,0 H0 V8 H6 M0,4 H5",
    "F": "M6,0 H0 V8 M0,4 H5",
    "G": "M6,1 Q0,-2 0,4 Q0,10 6,7 V4 H3",
    "H": "M0,0 V8 M6,0 V8 M0,4 H6",
    "I": "M0,0 H6 M3,0 V8 M0,8 H6",
    "J": "M0,0 H6 V5 Q6,10 0,7",
    "K": "M0,0 V8 M6,0 L0,4 L6,8",
    "L": "M0,0 V8 H6",
    "M": "M0,8 V0 L3,4 L6,0 V8",
    "N": "M0,8 V0 L6,8 V0",
    "O": "M3,0 Q0,0 0,4 Q0,8 3,8 Q6,8 6,4 Q6,0 3,0 Z",
    "P": "M0,8 V0 H3 Q6,0 6,2 Q6,4 3,4 H0",
    "Q": "M3,0 Q0,0 0,4 Q0,8 3,8 Q6,8 6,4 Q6,0 3,0 Z M4,6 L6,8",
    "R": "M0,8 V0 H3 Q6,0 6,2 Q6,4 3,4 H0 M3,4 L6,8",
    "S": "M6,1 Q0,-2 0,2 Q0,4 3,4 Q6,4 6,6 Q6,10 0,7",
    "T": "M0,0 H6 M3,0 V8",
    "U": "M0,0 V5 Q0,8 3,8 Q6,8 6,5 V0",
    "V": "M0,0 L3,8 L6,0",
    "W": "M0,0 L1,8 L3,4 L5,8 L6,0",
    "X": "M0,0 L6,8 M6,0 L0,8",
    "Y": "M0,0 L3,4 L6,0 M3,4 V8",
    "Z": "M0,0 H6 L0,8 H6",
    "0": "M3,0 Q0,0 0,4 Q0,8 3,8 Q6,8 6,4 Q6,0 3,0 Z",
    "1": "M0,2 L3,0 V8 M0,8 H6",
    "2": "M0,1 Q6,-2 6,2 Q6,4 0,8 H6",
    "3": "M0,0 H6 L3,4 Q6,4 6,6 Q6,10 0,7",
    "4": "M4,0 L0,5 H6 M4,0 V8",
    "5": "M6,0 H0 V4 H3 Q6,4 6,6 Q6,10 0,7",
    "6": "M6,1 Q0,-2 0,4 V6 Q0,8 3,8 Q6,8 6,6 Q6,4 3,4 H0",
    "7": "M0,0 H6 L1,8",
    "8": "M3,0 Q0,0 0,2 Q0,4 3,4 Q6,4 6,2 Q6,0 3,0 Z M3,4 Q0,4 0,6 Q0,8 3,8 Q6,8 6,6 Q6,4 3,4 Z",
    "9": "M6,4 H3 Q0,4 0,2 Q0,0 3,0 Q6,0 6,2 V4 Q6,10 0,7",
    "#": "M2,0 L1,8 M5,0 L4,8 M0,3 H6 M0,5 H6",
    "Б": "M6,0 H0 V8 H3 Q6,8 6,6 Q6,4 3,4 H0",
    "Г": "M0,8 V0 H6",
    "Д": "M0,8 V6 H6 V8 M1,6 L2,0 H5 V6",
    "Ж": "M0,0 L6,8 M6,0 L0,8 M3,0 V8",
    "З": "M0,1 Q6,-2 6,2 Q6,4 3,4 M3,4 Q6,4 6,6 Q6,10 0,7",
    "И": "M0,0 V8 L6,0 V8",
    "Й": "M0,2 V8 L6,2 V8 M2,0 H4",
    "Л": "M0,8 L2,0 H6 V8",
    "П": "M0,8 V0 H6 V8",
    "У": "M0,0 L3,4 M6,0 L1,8",
    "Ф": "M3,0 V8 M3,1 Q0,1 0,4 Q0,7 3,7 Q6,7 6,4 Q6,1 3,1 Z",
    "Ц": "M0,0 V6 H5 V0 M5,6 H6 V8",
    "Ч": "M0,0 V4 H6 M6,0 V8",
    "Ш": "M0,0 V8 H6 V0 M3,0 V8",
    "Щ": "M0,0 V6 H6 V8 M3,0 V6 M5,0 V6",
    "Ъ": "M0,0 H2 V8 H4 Q6,8 6,6 Q6,4 4,4 H2",
    "Ы": "M0,0 V8 H2 Q4,8 4,6 Q4,4 2,4 H0 M6,0 V8",
    "Ь": "M0,0 V8 H3 Q6,8 6,6 Q6,4 3,4 H0",
    "Э": "M0,1 Q6,-2 6,4 Q6,10 0,7 M3,4 H6",
    "Ю": "M0,0 V8 M0,4 H2 M4,0 Q2,0 2,4 Q2,8 4,8 Q6,8 6,4 Q6,0 4,0 Z",
    "Я": "M6,8 V0 H3 Q0,0 0,2 Q0,4 3,4 H6 M3,4 L0,8",
}
for cyrillic, latin in zip("АВЕКМНОРСТХ", "ABEKMHOPCTX"):
    GLYPHS[cyrillic] = GLYPHS[latin]

GROUPS = {
    "symbol": "#",
    "digit_01": "0−1",
    "digit_23": "2−3",
    "digit_45": "4−5",
    "digit_67": "6−7",
    "digit_89": "8−9",
    "latin_ac": "ABC",
    "latin_df": "DEF",
    "latin_gi": "GHI",
    "latin_jl": "JKL",
    "latin_mo": "MNO",
    "latin_ps": "PQRS",
    "latin_tv": "TUV",
    "latin_wz": "WXYZ",
    "cyrillic_ag": "АБВГ",
    "cyrillic_dz": "ДЕЖЗ",
    "cyrillic_il": "ИЙКЛ",
    "cyrillic_mp": "МНОП",
    "cyrillic_ru": "РСТУ",
    "cyrillic_fch": "ФХЦЧ",
    "cyrillic_shy": "ШЩЪЫ",
    "cyrillic_soft_ya": "ЬЭЮЯ",
}


def translated_path(source, dx, dy, scale_x=1, scale_y=1):
    """Transform absolute path coordinates; output uses only M/L/Q/H/V/Z."""
    tokens = re.findall(r"[MLQHVZ]|-?\d+(?:\.\d+)?", source)
    result = []
    command, axis = "", 0
    for token in tokens:
        if token.isalpha():
            command, axis = token, 0
            result.append(token)
        else:
            is_y = command == "V" or (command != "H" and axis % 2)
            offset, scale = (dy, scale_y) if is_y else (dx, scale_x)
            result.append(f"{float(token) * scale + offset:g}")
            axis += 1
    return " ".join(result)


def glyph_paths(label):
    # U+2212 is a vector stroke, with clear space on both sides of the separator.
    if len(label) == 3 and label[1] == "−":
        return [
            translated_path(GLYPHS[label[0]], 2, 8),
            "M10.5,12 H13.5",
            translated_path(GLYPHS[label[2]], 16, 8),
        ]
    if label == "#":
        return [translated_path(GLYPHS[label], 9, 8)]
    # All letters use one baseline, 4x10 cells and a fixed 6-unit advance.
    start_x = (24 - (4 + 6 * (len(label) - 1))) / 2
    return [
        translated_path(GLYPHS[char], start_x + index * 6, 7, 2 / 3, 1.25)
        for index, char in enumerate(label)
    ]


def stroke_width(name):
    return 1.4 if name.startswith(("ic_az_latin_", "ic_az_cyrillic_")) else 1.8


def vector(paths, stroke=1.8):
    body = "\n".join(
        f'    <path android:pathData="{path}" android:fillColor="#00000000" '
        f'android:strokeColor="#FFFFFFFF" android:strokeWidth="{stroke:g}" '
        'android:strokeLineCap="round" android:strokeLineJoin="round" />'
        for path in paths
    )
    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp" android:height="24dp"\n'
        '    android:viewportWidth="24" android:viewportHeight="24">\n'
        f"{body}\n</vector>\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    drawings = {f"ic_az_{name}": glyph_paths(label) for name, label in GROUPS.items()}
    drawings["ic_az_other"] = ["M4,12 H5 M11.5,12 H12.5 M19,12 H20"]
    files = {
        f"{name}.xml": vector(paths, stroke_width(name))
        for name, paths in drawings.items()
    }
    for xml in files.values():
        ElementTree.fromstring(xml)
    svg = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="960" height="600">',
        '<rect width="960" height="600" fill="#e9eef4"/>',
    ]
    for index, (name, paths) in enumerate(drawings.items()):
        x, y = 20 + (index % 8) * 120, 20 + (index // 8) * 180
        svg.append(
            f'<g transform="translate({x} {y}) scale(4)" fill="none" '
            f'stroke="#17212b" stroke-width="{stroke_width(name):g}" '
            'stroke-linecap="round" stroke-linejoin="round">'
        )
        svg.extend(f'<path d="{path}"/>' for path in paths)
        svg.append("</g>")
        svg.append(f'<text x="{x}" y="{y+125}" font-size="12">{name[6:]}</text>')
    svg.append("</svg>")
    files["contact.svg"] = "\n".join(svg)
    files["sha256.json"] = (
        json.dumps(
            {
                name: hashlib.sha256(data.encode()).hexdigest()
                for name, data in files.items()
                if name.endswith(".xml")
            },
            indent=2,
        )
        + "\n"
    )
    patch = "*** Begin Patch\n"
    for name, data in files.items():
        path = args.output / name
        if path.exists():
            raise FileExistsError(path)
        patch += f"*** Add File: {path}\n"
        patch += "".join(f"+{line}\n" for line in data.splitlines())
    patch += "*** End Patch\n"
    subprocess.run(["apply_patch"], input=patch, text=True, check=True)
    print(f"Constructed and parsed {len(drawings)} vectors in {args.output}")


if __name__ == "__main__":
    main()
