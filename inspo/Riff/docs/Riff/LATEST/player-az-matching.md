# Player A-Z matching and group membership

> Owner/scope: exact name matching and character-group membership; child of
> [A-Z navigation](/Riff/LATEST/player-az-navigation).

## Exact name matching and group membership

Match from the beginning of each comparison name under the leading-article policy after Unicode NFC
normalization and platform extended-grapheme segmentation. Each chosen group consumes exactly one
original grapheme. To classify that grapheme, apply Unicode NFKD, ignore combining marks, take the
first remaining code point, and apply Unicode simple case mapping without expansion. Then apply the
explicit non-decomposing-letter aliases below. Retain original display text; normalization never
rewrites a visible name or changes the number of consumed graphemes. The complete group partition
is:

| Visible group           | Membership in addition to corresponding upper/lower-case letters/digits                                                                                                                                    |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ABC                     | A–C and their decomposable diacritic forms; Æ                                                                                                                                                              |
| DEF                     | D–F and their decomposable diacritic forms; Đ, Ð, Ə                                                                                                                                                        |
| GHI                     | G–I and their decomposable diacritic forms; Ħ, dotless ı                                                                                                                                                   |
| JKL                     | J–L and their decomposable diacritic forms; Ł                                                                                                                                                              |
| MNO                     | M–O and their decomposable diacritic forms; Œ, Ø, Ŋ                                                                                                                                                        |
| PQRS                    | P–S and their decomposable diacritic forms; ß, ẞ                                                                                                                                                           |
| TUV                     | T–V and their decomposable diacritic forms; Þ                                                                                                                                                              |
| WXYZ                    | W–Z and their decomposable diacritic forms                                                                                                                                                                 |
| АБВГ                    | А–Г and their decomposable diacritic forms; Ә, Ӑ, Ӓ, Ґ, Ғ, Ѓ, Ҕ                                                                                                                                            |
| ДЕЖЗ                    | Д–З and their decomposable diacritic forms; Ё, Ђ, Є, Ӗ, Җ, Ѕ, Ҙ, Џ                                                                                                                                         |
| ИЙКЛ                    | И–Л and their decomposable diacritic forms; І, Ї, Ӣ, Ј, Қ, Ҡ, Ќ, Љ                                                                                                                                         |
| МНОП                    | М–П and their decomposable diacritic forms; Ң, Ҥ, Њ, Ө, Ӧ                                                                                                                                                  |
| РСТУ                    | Р–У and their decomposable diacritic forms; Ҫ, Ћ, Ў, Ӯ, Ү, Ұ, Ӳ, Ӱ                                                                                                                                         |
| ФХЦЧ                    | Ф–Ч and their decomposable diacritic forms; Ҳ, Һ, Ҷ, Ҹ, Ӌ                                                                                                                                                  |
| ШЩЪЫ                    | Ш–Ы and their decomposable diacritic forms                                                                                                                                                                 |
| ЬЭЮЯ                    | Ь–Я and their decomposable diacritic forms                                                                                                                                                                 |
| 0−1, 2−3, 4−5, 6−7, 8−9 | The two respective ASCII digits after the same compatibility decomposition, consuming one position                                                                                                         |
| #                       | Every grapheme outside the resulting Latin/Cyrillic/ASCII-digit groups, including symbols, emoji, other-script letters and non-ASCII digits that do not compatibility-decompose to ASCII; never a wildcard |

Case, canonical equivalents, decomposable diacritic forms and the listed hidden aliases belong to
the same group. None adds a glyph or changes a visible button label. Multi-letter expansions such as
Æ→AE, ß→SS and Þ→TH are deliberately absent: one original grapheme always consumes one group. There
is no End-of-name constraint, Other group, free-text input or IME.
