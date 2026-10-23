# Player catalog membership

> Owner/scope: filtering and presentation of the ready Cuetree cohort in Player.

Indexer, through Cuetree, is the only source of MDIA facts and ALBM/ARTS/GNRV catalog projections.
Player accepts one complete validated cohort and maps its records to browse rows. It does not run
album, artist or genre grouping or invent catalog IDs. Album membership and order come from ALBM
source references; Artist and Genre membership comes from ARTS and GNRV references. A physical
source copy or a FILF occurrence may produce a distinct Folder row without creating another semantic
catalog identity.

Player may filter a ready catalog by active Music/Book mode, profile overrides and file-configured
excluded genre IDs. It may sort visible rows and construct navigation containers; these do not
change the Indexer-owned entities or their relationships. Indexer publishes ALBM/ARTS/GNRV as an
available-only Player projection of its retained Cuetree catalog. It uses cached availability flags:
a wholly unavailable Album is absent from Albums/Titles, Artists/Authors and Genres; a partly
available Album remains assembled with all retained ordered source occurrences, including
unavailable member rows. Artist and Genre rows reference only Albums having at least one available
member. The Album/Title root and Artist/Author root admission thresholds remain those of the
hierarchy contract. Player does not recheck Album, Artist or Genre availability flags. MDIA retains
the full physical hierarchy and cached availability flags for Folders; Player checks those flags to
hide unavailable branches and leaves. FILF keeps its own title/metadata and base-media relation. No
SAF access is needed while making or displaying these projections.

Automatic Book evidence is resolved by Indexer from the first available representative source and
published with the ready cohort. Player applies profile/mode filters to that evidence and the
Indexer-owned catalogs without creating catalog identities or grouping entities.

Queue and playlist membership remains Player-owned mutable state keyed by media identity. Temporary
unavailability never deletes a stored queue, playlist occurrence or bookmark. Music playlist expiry
and Bookmarks collection expiry follow [Audiobook bookmarks](/Riff/LATEST/player-bookmarks) and
require absence from the complete accepted Cuetree snapshot, not absence from the available-only
projection. A changed cohort replaces the visible model atomically; a failed or mixed-cohort read
keeps the previous valid model. Optional analysis data never determines whether the base library is
visible.

Album playback uses the ordered ALBM source occurrence for display metadata and preferred binding. A
repeated MediaID at distinct admitted Album positions remains repeated, for ordinary as well as
explicit Albums. Copy resolution belongs to [Cuetree](/Riff/LATEST/album-grouping-positions), not
the Player projection or direct Album queue. The current
[Player state schema](/Riff/LATEST/player-state) persists the presence of Cuetree `MediaID.lo`
separately from its numeric value.

## Technical Singles display

Player presents Cuetree `ARTIST_SINGLES` as `<Artist> ◇ Singles`, using the ordered complete
`albumArtist` credit joined by a semicolon and a space, and `SINGLES` as `◇ Singles`. Selection is
by catalog kind, never by matching title text. Ordinary and explicit album names remain exact. The
same presentation is used for album-context track/queue metadata.

This is a display projection only. Stored ALBM titles, IDs, ordered membership and relationships
remain unchanged. The original catalog title supplies comparison/search names and full-name sort
ties, including leading-article handling; the diamond never changes A-Z results or album order. Rows
use the existing shared text renderer, font fallback, colors, layout, icons, actions and mode
policy. Installing this display change requires no database reset or catalog rebuild.
