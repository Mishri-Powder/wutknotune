"""Shared build-time authority for project-wide generated configuration."""

import argparse
import json
import re
from datetime import date
from pathlib import Path

CONFIG_PATH = Path(__file__).resolve().parents[1] / "config/project.json"
CONFIG = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
RELEASE_DATE = date.fromisoformat(CONFIG["releaseDate"])
RELEASE_CHANNEL = CONFIG["releaseChannel"]
if RELEASE_CHANNEL not in ("ALPHA", "BETA", "RC", "FINAL"):
    raise ValueError("Signed releaseChannel must be ALPHA, BETA, RC or FINAL")
GENRE_LOCALES = tuple(CONFIG["genreLocales"])
GNRE_GENERATION = CONFIG["gnreGeneration"]
GENRE_ADDITIONS = CONFIG["genreAdditions"]
LEADING_ARTICLES = CONFIG["leadingArticles"]
ARTIFACT_FILES = CONFIG["artifactFiles"]
ARTIFACT_NAMES = ("GNRE", "MDRG")
if (not GENRE_LOCALES or GENRE_LOCALES[0] != "en"
        or len(set(GENRE_LOCALES)) != len(GENRE_LOCALES)
        or any(not isinstance(x, str) or len(x) != 2 or not x.isalpha()
               or x != x.lower() for x in GENRE_LOCALES)
        or set(LEADING_ARTICLES) != set(GENRE_LOCALES)
        or tuple(ARTIFACT_FILES) != ARTIFACT_NAMES
        or type(GNRE_GENERATION) is not int or GNRE_GENERATION <= 0):
    raise ValueError("Invalid project configuration")
for name, version in ARTIFACT_FILES.items():
    if (not isinstance(version, dict)
            or type(version.get("flatBufferSchema")) is not int
            or version["flatBufferSchema"] <= 0
            or type(version.get("semantic")) is not int
            or version["semantic"] <= 0
            or (name != "GNRE" and (
                not isinstance(version.get("database"), str)
                or not version["database"].startswith(f"{name}-DB-V")
            ))
            or (name == "GNRE" and "database" in version)):
        raise ValueError(f"Invalid {name} artifact-file version")
for articles in LEADING_ARTICLES.values():
    if (not isinstance(articles, list) or len(set(articles)) != len(articles)
            or any(not isinstance(x, str) or not x.isalpha()
                   or x != x.casefold() for x in articles)):
        raise ValueError("Articles must be unique lowercase words")


def validate_manual_locales(
    labels: dict[str, list[str]],
    *,
    require_all: bool = False,
) -> None:
    """Manual translations must be extended whenever supported locales change."""
    if (not isinstance(labels, dict)
            or not set(labels) <= set(GENRE_LOCALES)
            or (require_all and set(labels) != set(GENRE_LOCALES))):
        raise ValueError("Manual strings must use configured genre locales")
    for values in labels.values():
        if (not isinstance(values, list) or not values
                or any(not isinstance(value, str) or not value.strip()
                       for value in values)
                or len({value.casefold() for value in values}) != len(values)):
            raise ValueError("Manual genre names must be nonempty and unique")


if (not isinstance(GENRE_ADDITIONS, dict)
        or set(GENRE_ADDITIONS) != {"required", "mtgEvidence"}):
    raise ValueError("Invalid project genre additions")
PROJECT_REQUIRED_GENRES = GENRE_ADDITIONS["required"]
if not isinstance(PROJECT_REQUIRED_GENRES, dict) or not PROJECT_REQUIRED_GENRES:
    raise ValueError("Required project genres must be a nonempty object")
for qid, record in PROJECT_REQUIRED_GENRES.items():
    if (not re.fullmatch(r"Q[1-9][0-9]*", qid)
            or not isinstance(record, dict)
            or set(record) != {"labels"}):
        raise ValueError("Required project genres must use positive Q-IDs")
    validate_manual_locales(record["labels"], require_all=True)

_mtg = GENRE_ADDITIONS["mtgEvidence"]
if (not isinstance(_mtg, dict)
        or set(_mtg) != {"firstId", "labels", "explicitSameAs"}
        or _mtg["firstId"] != -100_000
        or not isinstance(_mtg["labels"], list)
        or not _mtg["labels"]
        or len(set(_mtg["labels"])) != len(_mtg["labels"])
        or not isinstance(_mtg["explicitSameAs"], dict)
        or any(not isinstance(label, str)
               or not re.fullmatch(r"[a-z0-9]+", label)
               for label in _mtg["labels"])):
    raise ValueError("Invalid MTG evidence configuration")
MTG_FIRST_ID = _mtg["firstId"]
MTG_LABELS = tuple(_mtg["labels"])
MTG_EXPLICIT_TARGETS: dict[int, str | None] = {}
for raw_id, target in _mtg["explicitSameAs"].items():
    try:
        genre_id = int(raw_id)
    except (TypeError, ValueError) as error:
        raise ValueError("Invalid MTG explicit ID") from error
    if (str(genre_id) != raw_id
            or genre_id < MTG_FIRST_ID - len(MTG_LABELS) + 1
            or genre_id > MTG_FIRST_ID
            or (target is not None
                and (not isinstance(target, str)
                     or not re.fullmatch(r"Q[1-9][0-9]*", target)))):
        raise ValueError("Invalid MTG explicit target")
    MTG_EXPLICIT_TARGETS[genre_id] = target
REQUIRED_WIKIDATA_QIDS = tuple(
    sorted(
        set(PROJECT_REQUIRED_GENRES)
        | {target for target in MTG_EXPLICIT_TARGETS.values() if target},
        key=lambda value: int(value[1:]),
    )
)


CATALOG_FIELDS = (
    "genericArtists", "placeholderArtists", "placeholderAlbumArtists",
    "placeholderAlbums", "genericTitles", "versionWords", "protectedWords",
    "setMarkers", "discWords",
)


def validate_catalog_vocabulary(vocabulary, locales):
    """Reject incomplete language selections before producing any Kotlin."""
    if (not isinstance(vocabulary, dict)
            or set(vocabulary) != {"locales", "genreRoles"}
            or not isinstance(vocabulary["locales"], dict)
            or set(vocabulary["locales"]) != set(locales)):
        raise ValueError("Catalog vocabulary must match configured languages")
    for row in vocabulary["locales"].values():
        if not isinstance(row, dict) or set(row) != set(CATALOG_FIELDS):
            raise ValueError("Catalog vocabulary categories are incomplete")
        for values in row.values():
            if (not isinstance(values, list)
                    or any(not isinstance(value, str) or not value.strip()
                           or value != value.strip() for value in values)
                    or len({value.casefold() for value in values}) != len(values)):
                raise ValueError("Catalog terms must be unique nonempty literals")
    roles = vocabulary["genreRoles"]
    if not isinstance(roles, dict) or set(roles) != {"spoken", "live", "electronic"}:
        raise ValueError("Catalog genre roles are incomplete")
    for values in roles.values():
        if (not isinstance(values, list) or not values
                or any(type(value) is not int or not 0 < value < 2**63 for value in values)
                or len(set(values)) != len(values)):
            raise ValueError("Catalog roles require unique positive GNRE IDs")


CATALOG_VOCABULARY = CONFIG["catalogVocabulary"]
validate_catalog_vocabulary(CATALOG_VOCABULARY, GENRE_LOCALES)


def kotlin_string(value):
    return json.dumps(value, ensure_ascii=False).replace("$", "\\$")


def catalog_kotlin(vocabulary, locales):
    validate_catalog_vocabulary(vocabulary, locales)
    fields = []
    for key in CATALOG_FIELDS:
        values = sorted({value for locale in locales
                         for value in vocabulary["locales"][locale][key]})
        terms = ", ".join(map(kotlin_string, values))
        fields.append(f"    val {key}: Set<String> = setOf({terms})")
    for key, values in vocabulary["genreRoles"].items():
        terms = ", ".join(str(value) + "L" for value in sorted(values))
        fields.append(f"    val {key}GenreIds: Set<Long> = setOf({terms})")
    canonical = json.dumps(
        {"languages": list(locales), "vocabulary": vocabulary},
        sort_keys=True, ensure_ascii=False, separators=(",", ":"),
    )
    fields.append(f"    const val canonical: String = {kotlin_string(canonical)}")
    return "object ProjectCatalogVocabulary {\n" + "\n".join(fields) + "\n}\n"


def generate_kotlin(output: Path) -> None:
    articles = sorted({word for words in LEADING_ARTICLES.values() for word in words})
    words = ", ".join(json.dumps(word) for word in articles)
    locales = ", ".join(json.dumps(locale) for locale in GENRE_LOCALES)
    version_objects = []
    for name in ARTIFACT_NAMES:
        version = ARTIFACT_FILES[name]
        fields = [
            f"        const val flatBufferSchema: Int = {version['flatBufferSchema']}",
            f"        const val semantic: Int = {version['semantic']}",
        ]
        if "database" in version:
            fields.append(
                "        const val database: String = "
                + json.dumps(version["database"])
            )
        version_objects.append(
            f"    object {name.title()} {{\n" + "\n".join(fields) + "\n    }"
        )
    source = (
        "// Generated from config/project.json. Do not edit.\n"
        "package link.roadio.riff.config\n\n"
        "object ProjectLanguages {\n"
        f"    val genreLocales: Set<String> = setOf({locales})\n"
        f"    val leadingArticles: Set<String> = setOf({words})\n"
        f"    const val gnreGeneration: Int = {GNRE_GENERATION}\n"
        "}\n\n"
        "object ProjectFileVersions {\n"
        + "\n\n".join(version_objects)
        + "\n}\n\n"
        + catalog_kotlin(CATALOG_VOCABULARY, GENRE_LOCALES)
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kotlin", type=Path, required=True)
    generate_kotlin(parser.parse_args().kotlin)
