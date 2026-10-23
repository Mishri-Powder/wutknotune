"""Current locale contracts, corpus coverage, and strict input shape tests."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_gnre
import retrieve_genres
import wikidata_i18n
import wikidata_musicbrainz_merge
from project_config import (
    CATALOG_VOCABULARY,
    CATALOG_FIELDS,
    catalog_kotlin,
    validate_catalog_vocabulary,
    GENRE_LOCALES,
    GNRE_GENERATION,
    LEADING_ARTICLES,
    MTG_EXPLICIT_TARGETS,
    MTG_LABELS,
    PROJECT_REQUIRED_GENRES,
    validate_manual_locales,
)
import gnre_generated as gnre


class ProjectLocalesTest(unittest.TestCase):
    def test_shared_locale_and_article_authority(self):
        self.assertEqual(("en", "es", "de", "ru", "pt"), GENRE_LOCALES)
        self.assertEqual(set(GENRE_LOCALES), set(LEADING_ARTICLES))
        self.assertEqual([], LEADING_ARTICLES["ru"])
        self.assertEqual(list(GENRE_LOCALES), wikidata_i18n.parse_args([]).langs)
        for addition in PROJECT_REQUIRED_GENRES.values():
            validate_manual_locales(addition["labels"], require_all=True)
        self.assertEqual(
            "Live",
            PROJECT_REQUIRED_GENRES["Q56123235"]["labels"]["en"][0],
        )
        self.assertEqual(87, len(MTG_LABELS))
        self.assertIsNone(MTG_EXPLICIT_TARGETS[-100_000])
        with self.assertRaises(ValueError):
            validate_manual_locales({"zz": ["Invalid"]})
        with self.assertRaises(ValueError):
            validate_manual_locales({"en": ["Incomplete"]}, require_all=True)


    def test_catalog_languages_are_build_selected_not_a_fixed_language_set(self):
        import copy
        candidate = copy.deepcopy(CATALOG_VOCABULARY)
        candidate["locales"]["zz"] = {key: [] for key in CATALOG_FIELDS}
        candidate["locales"]["zz"]["versionWords"] = ["Custom.evidence"]
        generated = catalog_kotlin(candidate, (*GENRE_LOCALES, "zz"))
        self.assertIn('"Custom.evidence"', generated)
        self.assertNotEqual(generated, catalog_kotlin(CATALOG_VOCABULARY, GENRE_LOCALES))
        with self.assertRaises(ValueError):
            catalog_kotlin(candidate, GENRE_LOCALES)
        del candidate["locales"]["zz"]["genericArtists"]
        with self.assertRaises(ValueError):
            validate_catalog_vocabulary(candidate, (*GENRE_LOCALES, "zz"))

    def test_catalog_rejects_invalid_terms_and_roles(self):
        import copy
        for value in (None, "word", [""], [" word"], ["word", "WORD"]):
            candidate = copy.deepcopy(CATALOG_VOCABULARY)
            candidate["locales"]["en"]["versionWords"] = value
            with self.assertRaises(ValueError):
                validate_catalog_vocabulary(candidate, GENRE_LOCALES)
        candidate = copy.deepcopy(CATALOG_VOCABULARY)
        candidate["genreRoles"]["live"] = [True]
        with self.assertRaises(ValueError):
            validate_catalog_vocabulary(candidate, GENRE_LOCALES)

    def test_catalog_semantic_roles_exist_in_shipped_dictionary(self):
        from verify_gnre_asset import verify_catalog_roles
        asset = Path(__file__).resolve().parents[2] / "indexer/src/main/assets/gnre/gnre.fb"
        verify_catalog_roles(asset.read_bytes())

    def test_only_current_array_shape_is_accepted(self):
        for normalize in (wikidata_i18n.normalise, wikidata_musicbrainz_merge.normalise_labels):
            self.assertEqual({"pt": ["Rock"]}, normalize({"pt": ["Rock", "rock"], "zz": ["Drop"]}))
            with self.assertRaises(ValueError):
                normalize({"pt": "Rock"})
        with self.assertRaises(ValueError):
            build_gnre.normalized_names({"labels": {"en": "Rock"}})

    def test_unsupported_names_never_enter_names_or_alias_indexes(self):
        data = build_gnre.build({"genres": {
            "Q1": {"labels": {"en": ["Music"], "zz": ["Unsupported unique"]}},
            "Q2": {"labels": {"zz": ["Unsupported only"]}},
        }}, GNRE_GENERATION, include_mtg=False)
        root = gnre.GenreList.GetRootAs(data)
        self.assertEqual(GNRE_GENERATION, root.Generation())
        self.assertNotIn(2, [root.Genres(i).Id() for i in range(root.GenresLength())])
        locales = {root.Genres(i).Names(j).Locale().decode()
                   for i in range(root.GenresLength())
                   for j in range(root.Genres(i).NamesLength())}
        self.assertEqual(set(GENRE_LOCALES), locales)
        confusables = build_gnre.read_confusables(build_gnre.CONFUSABLES)
        aliases = {root.Indexes(i).Id() for i in range(root.IndexesLength())}
        self.assertNotIn(build_gnre.canonical_hash("Unsupported unique", confusables), aliases)
        self.assertIn(build_gnre.canonical_hash("Livros e Discursos", confusables), aliases)

    def test_localized_cache_requires_exact_configuration(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "cache.json"
            document = {"languages": list(GENRE_LOCALES), "genres": {"Q1": {"labels": {"pt": ["Rock"]}}}}
            path.write_text(json.dumps(document))
            self.assertTrue(retrieve_genres.valid_genre_document(path, localized=True))
            document["languages"] = ["en"]
            path.write_text(json.dumps(document))
            self.assertFalse(retrieve_genres.valid_genre_document(path, localized=True))
            document["languages"] = list(GENRE_LOCALES)
            document["genres"]["Q1"]["labels"]["zz"] = ["No"]
            path.write_text(json.dumps(document))
            self.assertFalse(retrieve_genres.valid_genre_document(path, localized=True))

    def test_bundled_asset_covers_only_supported_locales(self):
        root = gnre.GenreList.GetRootAs(
            (build_gnre.ROOT / "indexer/src/main/assets/gnre/gnre.fb")
            .read_bytes()
        )
        self.assertEqual(GNRE_GENERATION, root.Generation())
        by_id = {root.Genres(i).Id(): root.Genres(i) for i in range(root.GenresLength())}
        self.assertEqual(b"Live", by_id[56123235].Names(0).Name())
        locales = {root.Genres(i).Names(j).Locale().decode()
                   for i in range(root.GenresLength())
                   for j in range(root.Genres(i).NamesLength())}
        self.assertEqual(set(GENRE_LOCALES), locales)
        portuguese_genres = sum(
            any(root.Genres(i).Names(j).Locale() == b"pt"
                for j in range(root.Genres(i).NamesLength()))
            for i in range(root.GenresLength())
        )
        # Guard against packaging only manual aliases instead of the retrieved corpus.
        self.assertGreater(portuguese_genres, 1000)


if __name__ == "__main__":
    unittest.main()
