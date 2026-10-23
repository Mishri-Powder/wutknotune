# Player content mode

> Owner/scope: Player content projection, mode and profile overrides

## Catalogs and content classification

One shared browser, classification resolver, queue engine and transport serves MUSIC and AUDIOBOOK.
Content mode is independent of the aaos/automotive/phone application policy. For each accepted
metadata/catalog cohort, Player resolves profile overrides once and retains paired lightweight MUSIC
and AUDIOBOOK projections for the active profile. MAIN_SCREEN Books atomically selects the already
prepared projection: labels, rows, enabled state and effective kinds change together, with no
intermediate empty root or transient catalog disablement. When no previously accepted coherent
cohort remains usable, a genuinely missing, corrupt or generation-mismatched combined catalog
retains the catalog readiness gate. UI labels and catalog data MUST come from the same current
profile, mode and metadata projection; obsolete asynchronous results MUST NOT replace it.

## Content mode and profile overrides

Each of profiles 1, 2 and 3 stores its own content mode, default MUSIC. MAIN_SCREEN Books
immediately saves and toggles it, including with an empty library. Restart MUST restore that
profile's saved mode. Saving another profile MUST restore its own saved mode without overwriting any
other profile: Profile 1 Books → Profile 2 Music → Profile 1 restores Books. Delayed writes are
bound to the profile that received the gesture; obsolete asynchronous UI results MUST NOT change the
current profile or mode. AUDIOBOOK uses Titles/Authors labels, relabels MAIN_SCREEN Playlists to
Bookmarks, and disables AI with the shared `behavior.azDisabledAlpha / 255` appearance and complete
input blocking. Bookmarks is enabled only when a stored mark exists; Music Playlists only when a
nonempty Music playlist exists. Fixed launcher positions and exact controls follow the registered
MAIN_SCREEN contract. Add to playlist remains available under ordinary technical/application-policy
gates. Changing mode/profile/classification never rewrites the active queue, position or permanent
playlists.

Folder, Track and Album/Title row actions expose two equal icon-only toggles, Music then Book. Their
height is the resolved `geometry.playbackButtonHeight`. Music reuses the exact `ic_genre` two-note
icon from MAIN_SCREEN Genres; Book reuses `ic_audiobook` from MAIN_SCREEN Books. Neither selected
means Auto. Pressing an unselected enabled toggle selects that type; pressing the selected toggle
clears it to Auto. Both choices remain enabled for an editable object. Pressing the other choice
switches directly to it; pressing the selected choice returns to Auto. Artist/Author and Genre show
the same two toggles disabled, create no draft and write no state. Both toggles are disabled while a
commit is in progress. An enabled toggle changes only a ROW_ACTIONS-local draft. Every deliberate
exit from ROW_ACTIONS, including Back, Play, Add to playlist and future exit actions, first commits
the final draft exactly once in one Player transaction and continues the requested exit only after
success; process death or a failed write discards the draft, keeps or restores the prior durable
value and MUST NOT appear successful.

Track identity is Media.id; folder identity is its stable ordinary-Folders identity; Album identity
is the current published Album ID. Folder Book/Music writes its sparse value and Auto deletes it.
Track Book/Music writes its sparse value and Auto deletes it. Album Book/Music atomically deletes
every member Track override and writes one Album override; Album Auto atomically deletes the Album
override and every member Track override. Effective precedence is explicit Track, then explicit
Album, then nearest ordinary-Folders ancestor with an explicit value, then published Album automatic
classification. Auto continues to the next level. Selected-state properties follow the local draft.
Settings profile drafts do not activate another profile before successful Save.

ROW_ACTIONS has no classification explanation or saved-cause text element. Its only text after the
toggles is the passive advisory
`Changing a book to Music manually does not make it available for AI recommendations.` It wraps to
its natural height and remains complete, without a fixed line count, clipped height or truncation.
The action body may scroll when necessary, preserving full-size buttons and safe-content padding.
The advisory is centered, noninteractive and also present for disabled Artist/Author and Genre
selectors. It is absent from SETTINGS_SCREEN and playlist-container actions.

Use the first currently available representative source for each Media.ID in Cuetree source order.
Do not union or vote over conflicting copies. Technical alternate-binding recovery remains
available; playback TOC always belongs to the actual source binding.

`AutomaticBook` has four positive sources resolved by Indexer from that representative: (a) an
explicit Genre resolves through validated GNRE to Wikidata Q106833 / Genre ID 106833; (b) its raw
Apple MP4/M4A/M4B Media Kind is `stik=2`; (c) its physical media filename ends in `.m4b`
(case-insensitive), including the base media source of a FILF; or (d) it belongs to a current SAF
tree whose first decoded path component after the volume/root identifier is exactly Android
`Environment.DIRECTORY_AUDIOBOOKS` (`Audiobooks`). A different or absent Media Kind does not veto
another positive source. The filename test uses only the final physical filename, not a folder or
fragment title. The SAF test compares one path component, not a display label or prefix;
`Music/Audiobooks`, `MyAudiobooks`, translations, TOC, speech genres, duration, analysis and online
lookup supply no positive evidence.

Indexer publishes this positive automatic evidence with the available-only Cuetree catalog
projection. If any representative member of a published Album is AutomaticBook, the whole Album is
automatically Book; ungrouped Media keeps its own result. Player applies Track, Album and Folder
profile overrides in the precedence above and filters the ready catalogs by effective mode. Derived
effective membership never feeds back as automatic evidence.
