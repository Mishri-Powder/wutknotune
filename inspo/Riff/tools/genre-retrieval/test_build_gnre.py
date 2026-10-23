"""Offline tests for the merged-graph to GNRE compiler."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


TOOL_ROOT = Path(__file__).resolve().parent
REPOSITORY_ROOT = TOOL_ROOT.parents[1]
sys.path.insert(0, str(TOOL_ROOT))

import build_gnre  # noqa: E402
from project_config import PROJECT_REQUIRED_GENRES  # noqa: E402
import gnre_generated as gnre  # noqa: E402


class BuildGnreTest(unittest.TestCase):
    def test_builds_sorted_genres_names_relations_and_indexes(self) -> None:
        document = {
            "genres": {
                "Q9": {
                    "labels": {"ru": ["Рок"], "en": ["Rock", "Rock music"]},
                    "relations": {"parent": ["Q2"]},
                },
                "Q2": {
                    "labels": {"en": ["Music"]},
                    "relations": {"child": ["Q9"]},
                },
            }
        }

        data = build_gnre.build(document, 3, include_mtg=False)
        root = gnre.GenreList.GetRootAs(data)

        self.assertEqual(3, root.Generation())
        self.assertEqual([2, 9], [root.Genres(index).Id()
                         for index in range(2)])
        rock = root.Genres(1)
        self.assertEqual(
            [
                (b"en", b"Rock"),
                (b"en", b"Rock music"),
                (b"ru", "Рок".encode()),
            ],
            [
                (rock.Names(index).Locale(), rock.Names(index).Name())
                for index in range(rock.NamesLength())
            ],
        )
        self.assertGreater(root.IndexesLength(), root.GenresLength())

    def test_omits_cross_genre_alias_ambiguity(self) -> None:
        document = {
            "genres": {
                "Q1": {
                    "labels": {"en": ["Rock", "Rock one"]},
                    "relations": {},
                },
                "Q2": {
                    "labels": {"en": ["rock", "Rock two"]},
                    "relations": {},
                },
            }
        }

        data = build_gnre.build(document, 1, include_mtg=False)
        root = gnre.GenreList.GetRootAs(data)

        indexed_hashes = {
            root.Indexes(index).Id()
            for index in range(root.IndexesLength())
        }
        self.assertNotIn(
            build_gnre.canonical_hash(
                "rock",
                build_gnre.read_confusables(build_gnre.CONFUSABLES),
            ),
            indexed_hashes,
        )

    def test_writes_deterministic_raw_payload(self) -> None:
        document = {
            "genres": {
                "Q1": {"labels": {"en": ["Jazz"]}, "relations": {}},
            }
        }
        expected = build_gnre.build(document, 1, include_mtg=False)
        with tempfile.TemporaryDirectory() as name:
            output = Path(name) / "gnre.fb"
            build_gnre.write_atomic(output, expected)
            self.assertEqual(expected, output.read_bytes())

    def test_adds_required_project_genres(self) -> None:
        data = build_gnre.build(
            {"genres": {"Q1": {"labels": {"en": ["Music"]}, "relations": {}}}},
            3,
            include_mtg=False,
        )
        root = gnre.GenreList.GetRootAs(data)
        by_id = {
            root.Genres(index).Id(): root.Genres(index)
            for index in range(root.GenresLength())
        }
        for qid in PROJECT_REQUIRED_GENRES:
            self.assertIn(int(qid[1:]), by_id)
        self.assertEqual(b"Live", by_id[56123235].Names(0).Name())

    def test_configured_aliases_use_ordinary_indexes_without_mutating_input(self) -> None:
        document = {"genres": {"Q106833": {"labels": {"en": ["audiobook"]}, "relations": {}}}}
        data = build_gnre.build(document, 4, include_mtg=False)
        self.assertEqual(data, build_gnre.build(document, 4, include_mtg=False))
        self.assertEqual(["audiobook"], document["genres"]["Q106833"]["labels"]["en"])
        root = gnre.GenreList.GetRootAs(data)
        self.assertEqual(4, root.Generation())
        confusables = build_gnre.read_confusables(build_gnre.CONFUSABLES)
        indexes = {root.Indexes(i).Id(): root.Genres(root.Indexes(i).GenreIndex()).Id()
                   for i in range(root.IndexesLength())}
        for qid, addition in PROJECT_REQUIRED_GENRES.items():
            genre_id = int(qid[1:])
            for locale, names in addition["labels"].items():
                for name in names:
                    with self.subTest(genre_id=genre_id, locale=locale, name=name):
                        self.assertEqual(
                            genre_id,
                            indexes[build_gnre.canonical_hash(name, confusables)],
                        )

    def test_adds_all_mtg_nodes_with_explicit_and_automatic_targets(
        self,
    ) -> None:
        explicit_targets = {
            target
            for target in build_gnre.MTG_EXPLICIT_TARGETS.values()
            if target is not None
        }
        genres = {
            target: {
                "labels": {"en": [target]},
                "relations": {},
            }
            for target in explicit_targets
        }
        genres["Q1695023"] = {
            "labels": {"en": ["Alternative"]},
            "relations": {},
        }

        data = build_gnre.build({"genres": genres}, 2)
        root = gnre.GenreList.GetRootAs(data)
        by_id = {
            self.signed(root.Genres(index).Id()): root.Genres(index)
            for index in range(root.GenresLength())
        }
        mtg_ids = {
            value
            for value in by_id
            if -100_086 <= value <= -100_000
        }

        self.assertEqual(87, len(mtg_ids))
        self.assertEqual(b"60s", by_id[-100_000].Names(0).Name())
        self.assertEqual([], self.same_as_ids(root, by_id[-100_000]))
        self.assertEqual([221772], self.same_as_ids(root, by_id[-100_004]))
        self.assertEqual([164444], self.same_as_ids(root, by_id[-100_042]))
        self.assertEqual([1695023], self.same_as_ids(root, by_id[-100_005]))
        indexed_positions = {
            root.Indexes(index).GenreIndex()
            for index in range(root.IndexesLength())
        }
        positions = {
            self.signed(root.Genres(index).Id()): index
            for index in range(root.GenresLength())
        }
        self.assertTrue(
            all(positions[genre_id] not in indexed_positions
                for genre_id in mtg_ids)
        )

    def test_rejects_absent_explicit_target(self) -> None:
        with self.assertRaisesRegex(
            ValueError,
            "Explicit MTG target Q221772",
        ):
            build_gnre.build(
                {
                    "genres": {
                        "Q1": {
                            "labels": {"en": ["Music"]},
                            "relations": {},
                        },
                    },
                },
                1,
            )

    @staticmethod
    def signed(value: int) -> int:
        return value - (1 << 64) if value >= 1 << 63 else value

    @classmethod
    def same_as_ids(cls, root, genre) -> list[int]:
        return [
            cls.signed(root.Genres(relation.GenreIndex()).Id())
            for index in range(genre.RelatedLength())
            if (relation := genre.Related(index)).Kind()
            == build_gnre.RELATION_VALUES["same_as"]
        ]


if __name__ == "__main__":
    unittest.main()
