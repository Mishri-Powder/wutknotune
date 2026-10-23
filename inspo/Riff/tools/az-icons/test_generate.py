"""Regression checks for the adopted single-line geometric AZ icon family."""

import unittest
from pathlib import Path
from xml.etree import ElementTree

from generate import GROUPS, glyph_paths, stroke_width, translated_path, vector


class AzGlyphTests(unittest.TestCase):
    def test_letters_have_one_baseline_and_identical_metrics(self):
        groups = [
            label
            for name, label in GROUPS.items()
            if name.startswith(("latin_", "cyrillic_"))
        ]
        self.assertEqual(16, len(groups))
        for label in groups:
            paths = glyph_paths(label)
            self.assertEqual(len(label), len(paths))
            start = (24 - (4 + 6 * (len(label) - 1))) / 2
            from generate import GLYPHS

            self.assertEqual(
                [
                    translated_path(GLYPHS[char], start + index * 6, 7, 2 / 3, 1.25)
                    for index, char in enumerate(label)
                ],
                paths,
            )
            self.assertGreaterEqual(start - 0.7, 0)
            self.assertLessEqual(start + (len(label) - 1) * 6 + 4 + 0.7, 24)
            self.assertNotIn("Ё", label)

    def test_numeric_ranges_have_separate_centered_minus(self):
        groups = [label for name, label in GROUPS.items() if name.startswith("digit_")]
        self.assertEqual(["0−1", "2−3", "4−5", "6−7", "8−9"], groups)
        for label in groups:
            paths = glyph_paths(label)
            self.assertEqual(3, len(paths))
            self.assertEqual("M10.5,12 H13.5", paths[1])

    def test_source_bytes_follow_generator_and_vector_contract(self):
        root = Path(__file__).resolve().parents[2]
        ns = "{http://schemas.android.com/apk/res/android}"
        for group, label in GROUPS.items():
            name = f"ic_az_{group}"
            data = vector(glyph_paths(label), stroke_width(name))
            xml = ElementTree.fromstring(data)
            self.assertEqual("24", xml.attrib[ns + "viewportWidth"])
            self.assertEqual("24", xml.attrib[ns + "viewportHeight"])
            self.assertEqual("24dp", xml.attrib[ns + "width"])
            self.assertEqual("24dp", xml.attrib[ns + "height"])
            self.assertEqual(
                (root / "references/drawable" / f"{name}.xml").read_text(),
                data,
            )


if __name__ == "__main__":
    unittest.main()
