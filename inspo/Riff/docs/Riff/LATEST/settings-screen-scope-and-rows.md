# Riff SETTINGS_SCREEN contract — 1. Scope and rows

> Owner/scope: Riff/LATEST/settings-screen

## 1. Scope and rows

SETTINGS_SCREEN is reachable only through mode-gated MAIN_SCREEN under the
[central entry strategy](/Riff/LATEST/application-modes-modes#5-settings-entry). Phone uses long
press; Automotive and AAOS retain their motion/recovery gates. Entry is not a user preference.
Primary rows, in order: User, Long press starts playback, Auto search, Ignore leading articles,
Theme, More… .

More… opens a second Settings page with its own installation-scoped draft. Its exact rows are Media
folders…, Diagonal, Save data on…, Dim Backlight, ReplayGain, Rebuild DB… . The complete `More…` row
opens More, has no trailing action, and exposes `Open more settings`.

Primary and More transactions are independent; More snapshots committed installation values.
[Section 2](#2-settings-page-top-bars-and-drafts) owns Cancel, OK and idle effects.

AI and ReplayGain follow their registered MDRG, Settings and playback contracts. Media folders and
storage selectors retain independent child commits, which either Settings page Cancel cannot undo.
Diagonal/Card children return a candidate to the More draft. Rebuild DB… changes only the More
one-shot AU/IA draft and never starts a rebuild before More OK.

`applicationMode`, Indexer TTL, recent-history retention, transport thresholds and every other
registered file-only value expose no Settings row, hidden placeholder, draft field, accessibility
node, REST command or editor. Content-mode/classification have no Settings row.

## 2. Settings-page top bars and drafts

Primary Settings, More and Media folders use one shared top-bar OK implementation: the same
icon-only checkmark, target geometry and far-END anchoring, with the page-specific enabled state and
commit action supplied as parameters. Primary Settings names it `Save settings`; More names it
`Save more settings`; Media folders names it `Save media folder changes`. Each page also has
icon-only Cancel/Back at START, its noninteractive title and paging controls where needed. System
Back is that page's Cancel.

Primary Cancel discards only the profile draft and returns to MAIN_SCREEN. Primary OK commits User,
Theme, Long press, Auto search and Ignore leading articles, then returns to MAIN_SCREEN. More Cancel
discards only the installation draft and returns to primary Settings without changing its draft.
More OK immediately validates, commits and activates density, commits Dim Backlight and ReplayGain,
submits the selected AU/IA rebuild mask once, and returns to primary Settings. Storage and SAF child
OK remain independent commits.

Save freezes draft/profile, persists off main and publishes on main. Busy Save blocks
draft/navigation; recheck access/storage before commit. Uncommitted drafts never affect playback.
Storage migration MAY stop playback. Idle discards open drafts except during confirmation, saving or
migration. Completed child commits survive parent Cancel.

## 3. Shared layout

Both Settings pages use the common smooth vertical selector, viewport Up/Down, focus, accessibility
and row anchors. WIDE row height and choice targets are exactly `100dp`. NARROW row height is at
least `176dp` and choice targets are `76dp`, vertically centered. Inline targets and safe edges
retain `24dp` separation; content padding is `10dp`, top-bar padding `15dp`. Scrolling/paging never
commit.

Headings use at least `36sp`, may ellipsize only at grapheme boundaries and retain complete
accessibility text. Theme, Dim Backlight, User and Diagonal choices are icon-only. Long press, Auto
search and Ignore leading articles each follow the
[shared Boolean preference control](/Riff/LATEST/ui-foundation#boolean-preference-controls): one
cyclic icon-only button using `ic_yes` with ordinary selected blue emphasis or `ic_no` without
selected emphasis. Activating either state toggles to the other. Rebuild DB uses independent AU and
IA checkbox-semantic text choices. [Mode conformance](/Riff/LATEST/aaos-conformance) records
mode-gate verification. AU clears MDRG; IA rebuilds Cuetree while retaining MDRG; AU+IA clears both
ownership scopes. Focus, selected and disabled states remain non-color-dependent and stable under
resize, font scale through 2.0 and RTL.

## 4. Media Folders and SAF_GRANT_SELECTOR

The More-page media-folder row shows exact heading `Media folders…`. The complete visible row,
including its heading and unused area, is one interactive control named `Edit media folders` and
opens `SAF_GRANT_SELECTOR`. It has no gear, icon or separate trailing action.

The child top bar contains explicit Cancel at START, title `Media folders`, paging controls where
needed and explicit OK at END. System Back is Cancel. Entry snapshots the persisted read-tree grants
into a child-local draft. Rows are exactly `Add..` followed by the effective deterministic grant
list. Add launches a bare `ACTION_OPEN_DOCUMENT_TREE`; a valid returned read grant and any write
flag actually offered by Android are staged but not persisted until child OK. Selecting an already
persisted folder again may stage an upgrade from read-only to read/write; that capability change is
an effective grant change even when the URI list is unchanged. A grant without an offered write flag
remains read-only. The UI displays the same folder row and does not expose raw URI or grant flags.
Selecting a grant offers exact Cancel/Remove; Remove only stages its removal. Its confirmation uses
the mandatory [shared modal component](/Riff/LATEST/ui-modal-dialogs). Child Cancel discards every
staged addition/removal.

A child OK with no effective grant change returns without Indexer work. When Indexer is already
bound, a changed draft first requests a synchronous barrier while the persisted grants remain
unchanged. Indexer completes and publishes its current pipeline, delivers publication callbacks,
becomes idle and then acknowledges. During this wait the child disables mutation/navigation
controls. Barrier failure leaves grants unchanged. After acknowledgement, Player persists additions
before removals, refreshes the list, resumes Indexer and requests exactly one new Cuetree
observation scan. A grant-mutation failure is reported, resumes Indexer against the actually
retained set, and MUST NOT be reported as success. The first grant from the proven zero-grant
startup state has no active pipeline to drain: Player commits it, performs the first Indexer bind
and starts its initial Cuetree scan. The UI never logs complete URIs/document IDs. On custom-UI
startup, proven zero persisted read grants opens a fresh parent draft and immediately this child.
Player binds Indexer only after nonempty grants are committed; Cancel remains a stable empty-library
path.

## 5. User, density, playback and theme rows

User provides fixed icon-only choices 1, 2 and 3, default 1. A draft user rebinds that profile's
Theme, Long press, Auto search and Ignore leading articles, discarding unsaved profile edits for the
previously selected draft user. Primary OK commits the selected profile and its four preferences
without replacing the active queue; primary Cancel changes none. Save restores the selected
profile's independently saved Books/Music mode and does not overwrite it with the outgoing profile's
mode or a default.

Diagonal provides exact icon-only choices Diagonal, Card and Auto. Diagonal opens the registered
4-by-4 Screen Diagonal child; Card opens CALIBRATION_SCREEN; Auto uses the central density provider.
Child acceptance returns only a candidate to the More draft. More OK alone persists and activates
density.

Long press starts playback defaults to false/`ic_no`. Auto search and Ignore leading articles
default to true/`ic_yes`. All three use the same shared single-button mechanic. `ic_yes` is selected
and blue; `ic_no` is ordinary and unselected. The complete control exposes its current value and
toggle action to accessibility, keyboard and rotary input; there are never separate Yes and No
targets. Ignore leading articles uses the central leading-article search/order policy. Theme uses
one cyclic Day/Night/Auto icon button, default Auto. The installation-scoped Dim Backlight row uses
one Off/Day/Night/Auto icon button, default Auto.
[Inactivity and Dim Backlight](/Riff/LATEST/player-inactivity) owns their exact cycles, appearance,
resolved-theme applicability and window policy. Theme belongs to the primary profile draft.
