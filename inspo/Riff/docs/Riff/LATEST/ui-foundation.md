# Riff UI foundation

> Owner/scope: shared UI geometry, artwork and application identity

## Physical UI and artwork

All app-owned local UI uses one Player ComponentActivity, one Compose composition root, immutable
PlayerUiState and typed PlayerUiAction. No app-owned screen Activity/Fragment/XML/View trees, except
the single raw-pixel CalibrationScreenView through one AndroidView. Compose never enters Indexer,
playback/domain, IPC or storage; composables do not directly open their resources. Root owns
Back/idle/density/dialogs and preserves controller navigation/drafts.
[Shared modal dialogs](/Riff/LATEST/ui-modal-dialogs) owns the mandatory common component for every
current and future app-owned modal, including bootstrap windows.
[Inactivity and Dim Backlight](/Riff/LATEST/player-inactivity) owns local window brightness. Host
AAOS/Android Auto uses actual supported templates. Pin Compose through a stable BOM in `:app`;
retain AGP built-in Kotlin without the kotlin-android plugin.

Geometry uses physically true dp; text uses sp and user font scale. No raw-pixel/percentage,
orientation/device/density-bucket layout except raw-framebuffer calibration, the AI NARROW `2:1`
safe-content split, AI cell-corner pixel omissions and the explicitly adopted local playback
portrait/landscape row packing in [Playback transport](/Riff/LATEST/player-transport). One immutable
PhysicalDensityProvider governs non-calibration rendering. Only horizontal density scales Android;
vertical PPI is diagnostic. DIAGONAL derives `ppiX=hypot(windowWidthPx,windowHeightPx)/inches`,
density `ppiX/160`, with only source/ppiX committed and inches discarded. Read raw window bounds on
acceptance.

All devices use one density policy in production and testing. No serial, manufacturer, model,
registered-device profile or test-device allowlist may confer trust, bypass calibration, alter the
formula or exempt a device from verification. AUTO uses `diagonalPpi(9 inches)/160`. Card uses
`(ppiX/160)*(7/9)` on every device, retaining the common comfort correction; DIAGONAL uses
`ppiX/160` without that correction. A valid committed measurement takes precedence over reported
logical density. Missing or invalid calibration falls back to AUTO; if raw window dimensions are
unavailable, use a finite positive reported density, otherwise the finite positive baseline `1.0`.
Reported density is only this last-resort input, never evidence of a trusted device. A represented
`D_effective/D_reported` correction is finite in `[0.5,6.0]`. Child calibration/diagonal OK returns
only a draft; Settings alone validates/commits/activates it. Persist authority atomically under
Player active-root state; only last resolved density may be bootstrap-cached before first rendering.
Corruption falls back safely; reset removes calibration only; runtime changes rebind without
accumulating transforms.

Screen details belong to registered contracts. MAIN alone exposes Settings; one shared hierarchy
implementation supplies all categories as data. Core playback targets are at least 76dp; others at
least 64dp unless stricter. Mandatory driving actions cannot require precision or long press. Apply
all relevant Android automotive safety requirements; aaos has no bypass/deviation, automotive uses
only custom-host integration, safe UNKNOWN motion and the exact UNKNOWN Settings recovery in
application-modes. Review current official guidance and record the mode matrix before material UI
changes.

Every app button uses 10dp content padding, top-bar chrome 15dp. Pictograms fit the padded
short-side square and center along the long axis; only Settings Diagonal/Card retain their
prescribed nonsquare aspect. Targets, focus and spacing do not change. Stable slots, non-color
states, accessible complete labels, font scale, RTL and safe overflow remain required. App drawings
resolve adopted semantic names through one shared static map to ordinary APK `R.drawable`
VectorDrawable resources and standard Android/Compose resource inflation; no XML-in-configuration,
runtime replacement, Material, stock or glyph substitute exists. The explicitly adopted
[audiobook speed label](/Riff/LATEST/player-audiobook-speed#shared-playback-control) is text content
in the existing shared action component, not a replacement icon. Exact source assets remain
immutable under `references/drawable`, are copied byte-for-byte to `app/src/main/res/drawable`, and
new defaults require explicit adoption.

Artwork loading MUST NOT delay queue activation, audio source opening, Forward or automatic
occurrence transitions. Start bounded off-main/audio-critical-thread inspection of embedded artwork
in the exact chosen binding independently of audio. Show the theme-resolved default until the
current observation has a usable result, then update artwork without seeking, pausing, rebuilding
the queue or changing audio parameters. Replacement binding restarts this lookup. If unusable,
inspect only its immediate directory via stable SAF: exactly one accessible decodable `image/*`
regular document supplies artwork, while ambiguity/error/none selects the theme-resolved default.
Stop decoding once ambiguous. No image enters MDIA; no filename tie-break, network, Indexer image or
stale prior artwork substitutes. Timeout/failure retains default without delaying audio readiness.
Defaults are exact `references/drawable/ic_default_artwork_day.xml` and
`ic_default_artwork_night.xml`, packaged as `R.drawable.ic_default_artwork_day` and
`R.drawable.ic_default_artwork_night`; SYSTEM follows resolved day/night. Keep aspect ratio/crop
contract, stable bounds, inert artwork semantics and immediate theme change for default artwork.

Cancellation of provider work runs outside cache monitors and audio/UI-critical callers. A blocked
provider cancellation must not block another request or cache invalidation; worker, cancellation and
queued work remain bounded. Every occurrence transition ensures an artwork request for its exact
resolved binding and current accepted generation, including already buffered sources whose audio is
not reopened. Audio and artwork share one exact SAF path resolver that rejects incomplete or
ambiguous child listings.

One Player-owned resolver/cache identifies each result by queue revision, occurrence, exact binding
and accepted source observation. Current and prefetched occurrences must not overwrite each other.
Discard cancelled/obsolete completions, including results from an earlier binding of the same Media
ID. A transient provider/decode failure is distinct from confirmed absence/ambiguity: show default
within the existing readiness bound, then allow at most two bounded deferred retries for that
request. These retries never block audio. A late fallback must not erase a newer successful result
for the same source observation. Keep the cache and in-flight work bounded; queue/source replacement
invalidates obsolete requests. UI recreation observes the current owner rather than starting an
independent artwork pipeline.

[Local UI color system](/Riff/LATEST/ui-color-system) owns resolved colors and visible state across
local screens, dialogs and raw calibration chrome.

[Shared contrast halo](/Riff/LATEST/ui-contrast-halo) owns the common decorative silhouette renderer
for playback text/icons/position and AI overlays, preserving foreground, geometry and semantics.

### Boolean preference controls

Every current and future app-owned setting whose values are Yes/No MUST use one icon-only cyclic
control: `ic_yes` for true/Yes and `ic_no` for false/No. Use one shared Boolean preference component
and the common icon action renderer. Refer to these icons by their complete canonical names in code
and documentation. Scope-specific contracts retain target sizes, padding, selected emphasis, access
gates, defaults and persistence. Each control exposes the complete setting name, current Yes/No
value and toggle action to accessibility, with an actual selected-state property. Visible Yes/No
text is not used as control content.

### Application identity

Riff MUST use the user-adopted desert-wave application icon and associated Play Store artwork.
[Icon system section 8](/Riff/LATEST/icon-system) owns the exact supplied archive, complete import,
Android bindings, store-artwork retention and verification scope. Application icons remain ordinary
Android lifecycle resources outside the configurable general UI corpus.
