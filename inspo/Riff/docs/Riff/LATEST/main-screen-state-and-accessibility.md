# Riff MAIN_SCREEN contract — State and accessibility

> Owner/scope: bounded section of Riff/LATEST/main-screen

## State and accessibility

Return from a category restores the surviving focus target, otherwise Albums/Titles. Indexing,
source loss and empty states never remove or reorder positions. Empty enabled roots show a bounded
empty state with safe return.

Traversal is Flip, policy-enabled Settings, then Albums/Titles, Artists/Authors, Genres, Playlists,
Folders, Books and AI. Disabled actions expose no activation. The screen supports touch, rotary,
directional keyboard and TalkBack; font scale through `2.0`; LTR/RTL; long translations and complex
scripts. Voice media actions remain supplied by media integration.

## Implementation and verification

One `MainScreen` composable below the single Player composition root consumes immutable
`PlayerUiState` and emits typed `PlayerUiAction` values. It owns no navigation, playback, Binder,
Indexer, database or storage state. No MAIN XML, Fragment, dedicated Activity or programmatic View
tree exists.

Verification covers both modes and all profiles, restart/defaults, atomic projection selection,
genuine catalog unavailability, exact order/labels/layout/geometry, AI readiness, disabled-input
isolation, one transition per gesture, background Flip arbitration, release-channel labels, the
exact calendar warning boundary, one dialog per eligible Player-process launch, RC/FINAL exclusion,
complete input blocking before OK and ordinary access after OK, Android-owned MAIN_SCREEN Back,
continued playback service state, status bounds, source loss, focus restoration, day/night contrast,
font scales, RTL, TalkBack/rotary/keyboard, constrained scrolling, absence of autoplay and use of
official host surfaces for AAOS/Android Auto.
