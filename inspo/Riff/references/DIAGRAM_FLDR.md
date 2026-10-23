# Project decisions

This document is used to explain the core architecture to both humen readers and
AI agents. This MUST NOT be AI generated as AI tends to propose the decisions and
design patterns that won't fit the niche scenario (slow SD access via SAF,
limited RAM and CPU power).

## Database structure (FLDR; folders only)

**Notes:**

- `generation` is the record last used as follows:

  - Album, Artist, Genre, Attribute - every time the lookup from the MDIA finds
    this record (regardless of whether the referrer is accessible or not).
    *Example:* `Eleanor Rigby.mp3` (track; mp3) updates `The Beatles` (artist) and
    `Revolver` (album), `Rock` (genre), `Pop rock` (genre) to the current
    generation when found.

  - Media, Fragment - every time source is found available `generation`
    gets updated to the current DB generation.
    *Example:* `Eleanor Rigby.mp3` updates FILM record to the current generation.

- `ACSS` always reflects the actual status of the record as measured at the
  database build. In case of the Albums, Artists, Genres, Attributes it is
  also applies (for example if no files with Artist == `The Beatles` are left,
  then record for `The Beatles` (artist) get ACSS := 1.
  After the system-wide TTL `The Beatles` (artist) gets removed from the `Artists`.

- Reconciliation is best-effort. If a move is not recognized, the
  observed source is admitted and scanned as new, including MediaID
  computation. The previous record remains with ACSS until normal
  TTL GC.

- Albums, artists, genres and attributes converge through their
  ordinary grouping, reference and lifetime rules. No special
  recovery procedure is required for an unrecognized move.

- Id is always 128 bit, signed. For media files it holds MediaID.hi and optionally
  MediaID.lo (if MediaID.hi conflicted and resolution is required).
  For other tables it is always ID.hi/null but the mechanics is the same and code
  MUST be shared.
  IDs are NOT unique (for example two copies of the same file are going to be sharing
  the same ID but MAY have different `DISPLAY_NAME` and SAF/POSIX data. Also, when
  file is moved the 'old' copy is exactly the same (except the `parentId` and flags).
  Tables MUST be sorted by ID to allow binary search.

***Previous:***

```text
-[root]--+--+
         |  |
         | +--------------+ SAF grant: eg.
         | !RiffId: 0x1234! `content://com.android.externalstorage.documents/tree/extsd-1%3AMusic`
         | +--------------+
         |  |
         |  +-[A: folder]--+-[B: folder]--+-[C: folder]--+-[D: file]
         |                                |              +-[E: file]
         |                                |              +-[F: file]
         |                                +-[G: file]
         |                                …
        +--------------+ SAF grant: eg.
        !RiffId: 0x2345! `content://com.android.externalstorage.documents/tree/Internal%3AMusic`
        +--------------+
         |
         +-[H: folder]--…
         +-[I: folder]--…
         …
```

***Current:***

`B` is removed, `C` is moved under `A`.

***Desired state:***

Tree-diff procedure is TBD at this moment. MVP should mark moved files with ACSS and
allow new locations to enter the database as a new files (with the compute penalty
for the rescanning).

```text
-[root]--+--+
         |  |
         | +--------------+ SAF grant: eg.
         | !RiffId: 0x1234! `content://com.android.externalstorage.documents/tree/extsd-1%3AMusic`
         | +--------------+
         |  |
         |  +-[A: folder]--+-[B: folder; ACSS]--+-[G: file; ACSS]
         |                 |
         |                 +-[C: folder]--+-[D: file]
         |                                +-[E: file]
         |                                +-[F: file]
         |                                …
        +--------------+ SAF grant: eg.
        !RiffId: 0x2345! `content://com.android.externalstorage.documents/tree/Internal%3AMusic`
        +--------------+
         |
         +-[H: folder]--…
         +-[I: folder]--…
         …
```

***MVP state:***
```text
-[root]--+--+
         |  |
         | +--------------+ SAF grant: eg.
         | !RiffId: 0x1234! `content://com.android.externalstorage.documents/tree/extsd-1%3AMusic`
         | +--------------+
         |  |
         |  +-[A: folder]--+-[B: folder; ACSS]--+-[C: folder; ACSS]--+-[D: file; ACSS]
         |                 |                    |                    +-[E: file; ACSS]
         |                 |                    |                    +-[F: file; ACSS]
         |                 |                    +-[G: file; ACSS]
         |                 |                    …
         |                 +-[C: folder]--+-[D: file]
         |                                +-[E: file]
         |                                +-[F: file]
         |                                …
        +--------------+ SAF grant: eg.
        !RiffId: 0x2345! `content://com.android.externalstorage.documents/tree/Internal%3AMusic`
        +--------------+
         |
         +-[H: folder]--…
         +-[I: folder]--…
         …
```

**Albums (player projection):**
```text
+-------------------------------+
!album (eg. `Please Please Me`) ! // 1st track is a fragment; 2nd is a separate file (yes, it's legit)
+-------------------------------+
!id: 0xcdef                     ! // random 64bit id at admittance
!generation: 0x00000001         ! // generation: set to the current DB database every time Album is referred to
!records: [                     !
! 0x4321/0x5432,                ! // -[track 1]-> MDIA (FILF) `I Saw Her Standing There`
! 0x9876,                       ! // -[track 2]-> MDIA (FILM) `Misery`
! …,                            ! // …
!]                              !
!ATTR: […]                      !
!flags: …                       !
+-------------------------------+
+-------------------------------+
!album (eg. `With The Beatles`) ! // Separate tracks; possibly in one folder
+-------------------------------+
!id: 0xabcd                     ! // random 64bit id at admittance
!generation: 0x00000001         !
!records: [                     !
! 0x1357,                       ! // -[track 1]-> MDIA (FILM) `It Won't Be Long`
! 0x2469,                       ! // -[track 2]-> MDIA (FILM) `All I've Got to Do`
! …,                            ! // …
!]                              !
!ATTR: […]                      ! // Name, Genres gets here
!flags: …                       !
+-------------------------------+
+---------------------------------+
!album (eg. `A Hard Day's Night`) ! // FLAC (1 file) + CUE
+---------------------------------+
!id: 0xabcd                       ! // random 64bit id at admittance
!generation: 0x00000001           !
!records: [                       !
! 0x3579/0x9753,                  ! // -[track 1]-> MDIA (FILF) `A Hard Day's Night`
! 0x3579/0x8765,                  ! // -[track 2]-> MDIA (FILF) `I Should Have Known Better`
! …,                              ! // …
!]                                !
!ATTR: […]                        ! // Name, Genres gets here
!flags: …                         !
+---------------------------------+
…
```

**Artists (player projection):**
```text
+-------------------------------+
!artist (eg. `The Beatles`)     !
+-------------------------------+
!id: 0xcdef                     ! // random 64bit id at admittance
!generation: 0x00000001         !
!albums: [                      !
! 0xcdef,                       ! // -[album 1]-> ALBM `Please Please Me`
! 0xabcd,                       ! // -[album 2]-> ALBM `With The Beatles`
! …,                            ! // …
!]                              !
!ATTR: […]                      !
!flags: …                       !
+-------------------------------+
…
```

**Genres (player projection):**

Genres points to albums; same way as the Artists (as long as ANY track
in the album has the genre). No need for the separate diagram.

**Attributes:**
```text
+-------------------------------+
!attribute (eg. `MIME type`)    !
+-------------------------------+
!id: 0xdef0                     ! // XXH3(AttributeKind + len(Byte(values)) + Byte(values))
!generation: 0x00000001         ! // generation: start TTL when attribute is referred to
!AttributeKind: 0x01            !
!flags: …                       !
!value_text: `audio/mp4`        !
|value_long: null               !
+-------------------------------+

enum AttributeKind : byte {
  Unknown = 0, // safe default; UI MUST reject zero
  MimeType = 1, // no ID3 equivalent
  Title = 2, // TIT2
  Track = 3, // TRCK (NN/--)
  TotalTracks = 4, // TRCK (--/NN)
  Album = 5, // TALB
  Artist = 6, // TPE1
  AlbumArtist = 7, // TPE2
  Genre = 8, // TCON (index to GNRE; one genre per record)
  Year = 9, // Extracted year does not establish original-release or edition identity.
  Disc = 10, // TPOS (NN/--)
  TOC = 11, // Binding-local chapter data; the future section model is a separate contract.
  GenreText = 12, // user-provided genres like `Fiction` for audiobook
  TotalDiscs = 13, // TPOS (--/NN)
  AlbumGroup = 14,
  AlbumTotalTracks = 15,
  MediaKind = 16 // Normalized positive audiobook evidence, not the raw iTunes stik value.
  Fragment = 17, // approx. CHAP
  TotalFragments = 18, // CHAP count
  // Start_us = 19,
  // End_us = 20,
  // … other attributes TBD; like Collaboration, etc.
  /// Replay Gain figures; there are two possible sources:
  /// - MDIA: ReplayGain collected from the file itself (or CUE)
  /// - MDAU: ReplayGain is measured by the Riff (less accurate)
  /// Player should prefer MDIA, that is supposedly done by the
  /// encoder (iTunes) or third-party tools (`loudgain`, `rsgain`)
  ReplayGain = 32, // compact JSON blob; use `org.json` before the playback
  // {
  //   "TrackGain": -5.5,  // float; gain in dB for -18 LUFS target (default: null)
  //   "TrackPeak": 3.5,   // float (default: null)
  //   "AlbumGain": -5.0,  // float; MUST NOT be avg(TrackGain). Riff don't calculate it. (default: null)
  //   "AlbumPeak": 3.3,   // float; MUST NOT be max(TrackPeak). Riff don't calculate it. (default: null)
  //   "GainTarget": -18,  // float; target gain (from the file) {0, -16, -18, -23, etc.} (default: null)
  //   "GainSource": "ID3" // string; {"ID3", "iTunes", "Vorbis", "LAME", "Riff", etc.} (default: null)
  // }
}
```

**Internal catalog:*

```text
**Flags:**

Scope:

- D: Folders
- M: Media files
- F: Fragments

+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
! f ! e ! d ! c ! b ! a | 9 | 8 | 7 ! 6 ! 5 ! 4 ! 3 ! 2 ! 1 ! 0 !
+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
  |   |   |   |   |   |   |                   |   |   |   |   |
  +---+---+   +---+---+   +-----------+-------+   |   |   |   +-[*]--- ACSS; no access; eg. volume is removed
      |           |                   |           |   |   |                  or permissions or grant is revoked
      |           |               RESERVED        |   |   +-----[M,F]- RESL; collision resolution is pending
      |           |                               |   |                      eg. (0x1234,null) + ACSS and new file
      |           |                               |   |                      gets (0x1234, 0x2345). In this case
      |           |                               |   |                      "new" gets (0x1234, 0x2345) + RESL.
      |           |                               |   |                      "Old" gets SHRT set until resolved.
      |           |                               |   |
      |           |                               |   +--------------- SHRT; answers to the "short" id; (0x1234, null)
      |           |                               |                          when MediaID is (0x1234, 0x3456).
      |           |                               +------------------- IDHI: same as SHRT but for 64bit IDs (album, etc.)
      |           |
      |           +------ 001: "light" rescan requested (MediaID)
      |                   010: "heavy" rescan requested (AI/ReplayGain)
      |                   100: UNUSED in current version
      |
      +------------------ 000:     FIL(D) directory (folder)
                          001:     FIL(M) media (mp3, flac, m4a, etc.)
                          010:     FIL(F) fragment (ref to FILM, optional begin_us, optional end_us)
                          011…111: UNUSED in current version

+----------------------------+
!record (Folder/SAF grant)   ! // No `parentId` means this is SAF grant; can't go "up"
+----------------------------+
!id: 0x0001/null             !
!flags: FILD,IDHI                 !
!generation: 0x00000001      ! // Generation: reboot number/generation within reboot
!SAF: <text>                 ! // Eg.: `content://com.android.externalstorage.documents/tree/extsd-1%3AMusic`
+----------------------------+
 ↑
+----------------------------+
!record (Folder)             !
+----------------------------+
!id: 0x1234                  ! // Random id; assigned at admit
!flags: FILD,IDHI            !
!generation: 0x0001          !
!parentId: 0x0001            !
!SAF: <data>                 !
!ATTR: [id]                  !
+----------------------------+
 ↑  ↑  ↑  ↑  ↑
 |  |  |  |  |
 |  |  |  | +----------------------------+   +-------------------------------+
 |  |  |  | !record (m4a/base)           !   !record (m4a/fragment)          !
 |  |  |  | +----------------------------+   +-------------------------------+
 |  |  |  | !id: 0x4321/0x6543           ! ← !id: 0x4321/0x5432 (not a typo) !
 |  |  |  | !flags: FILM                 !   !generation: …                  !
 |  |  |  | !generation: …               !   !parentId: 0x4321/0x6543        !
 |  |  |  | !parentId: 0x1234            !   !SAF: <data>                    !
 |  |  |  | !SAF: <data>                 !   !ATTR: [id]                     ! // +`Fragment` (fragment number)
 |  |  |  | !ATTR: [id]                  !   !start_us: 100, end_us: 1000000 ! //  `TotalFragments` (from CUE/TOC)
 |  |  |  | +----------------------------+   !flags: FILF                    !
 |  |  |  |                                ↖ +-------------------------------+
 |  |  | +----------------------------+      +-------------------------------+
 |  |  | !record (m4a/standalone)     !      !record (m4a/fragment)          !
 |  |  | +----------------------------+      +-------------------------------+
 |  |  | !id: 0x4321/0x5432           !      !id: 0x9876/0x7654              !
 |  |  | !flags: SHRT                 !      !generation: …                  !
 |  |  | !generation: …               !      !parentId: 0x4321/0x6543        !
 |  |  | !parentId: 0x1234            !      !SAF: <data>                    !
 |  |  | !SAF: <data>                 !      !ATTR: [id]                     ! // +`Fragment`, `TotalFragments`
 |  |  | !ATTR: [id]                  !      !start_us: …, end_us: …         !
 |  |  | +----------------------------+      !flags: FILF                    !
 |  |  |                                     +-------------------------------+
 |  | +----------------------------+
 |  | !record (Folder)             !         **Albums (ALBS):**
 |  | +----------------------------+         +-------------------------------+
 |  | !id: 0x4567                  !         !album (eg. `Revolver`)         !
 |  | !generation: …               !         +-------------------------------+
 |  | !parentId: 0x1234            !         !id: 0xcdef                     ! // random id at admittance
 |  | !SAF: <data>                 !         !records: [                     !
 |  | !ATTR: [id]                  !         ! 0x4321/0x5432,                !
 |  | +----------------------------+         ! 0x9876/0x7654,                !
 |  …                                        !]                              !
 | +----------------------------+            !ATTR: […]                      !
 | !record (flac/standalone) [1]!            !flags: …                       !
 | +----------------------------+            +-------------------------------+
 | !id: 0x5678/null             !            +-------------------------------+
 | !generation: …               !            ! …                             !
 | !parentId: 0x1234            !            +-------------------------------+
 | !ATTR: [id]                  !
 | !SAF: <data>                 !
 | !flags: FILM,SHRT,ACSS .     !
 | +----------------------------+
 …
+----------------------------+
!record (Folder)             !
+----------------------------+
!id: 0x8901/null             !
!flags: IDHI                 !
!generation: …               !
!parentId: 0x0001            !
!SAF: <data>                 !
|ATTR: [id]                  !
+----------------------------+
 ↑
+----------------------------+   +----------------------------+
!record (Folder)             ! ← !record (flac/standalone)    !
+----------------------------+   +----------------------------+
!id: 0x0123/null             !   !id: 0x5678/0x9753           !
!flags: IDHI                 !   !flags: FILM,RESL            ! // possible collision/w unavailable [1]
!generation: …               !   !generation: …               ! //   resolution is still in progress
!parentId: 0x8901            !   !parentId: 0x0123            !
!SAF: <data>                 !   !SAF: <data>                 !
!ATTR: [id]                  !   !ATTR: [id]                  !
+----------------------------+   +----------------------------+
```
