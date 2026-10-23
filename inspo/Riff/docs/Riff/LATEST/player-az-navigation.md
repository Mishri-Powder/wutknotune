# Player progressive A-Z navigation

> Owner/scope: Player A-Z navigation, matching and automatic completion

## Progressive A-Z matching and automatic completion

One shared operation filters only the immediate displayed rows of the current projected hierarchy
container, preserving source order, row identity and ordinary actions. It never recursively searches
descendants or changes media, tags, queues or playback. Local automotive/phone share it;
AAOS/projected Android Auto use host search. Applied filters and drafts are process-memory
navigation state scoped by profile, content mode, root and path; restart clears them. The single A-Z
chrome control opens the keypad and, while it is open, commits the current draft and closes it. Both
transitions preserve the applicable list's first visible row, pixel offset and surviving focus;
closing MUST NOT reset the selector to its first row.

The [matching and group membership owner](/Riff/LATEST/player-az-matching) defines comparison names,
exact Unicode/grapheme classification and the complete Latin/Cyrillic/numeric/# partition.

The A-Z chrome control applies the current draft, including empty, when it closes the keypad.
Backspace removes the last step from both the current draft and the applied, remembered filter; it
is unavailable when empty and leaves the keypad open. Duplicate names and complete group sequences
remain resolvable by closing A-Z.

The safe-bottom keypad uses eight equal-width base columns WIDE, four NARROW, exact 24dp gaps,
100dp/76dp row height, width at least height and 10dp padding around the unchanged square glyph. A
row means one physically displayed horizontal interval of buttons, not an entire alphabet. A script
block contains eight groups and occupies one WIDE row or two NARROW rows. Blocks appear from top to
bottom as Cyrillic, Latin, then numeric/control; from the bottom upward the order is
numeric/control, Latin, Cyrillic. Preserve table order inside each script.

Form physical rows before deciding visibility. Show each row iff at least one of its character
groups is available at the next position, except that the final row containing Backspace MUST always
be shown. Retain every slot and disabled button inside a shown row; never repack buttons between
rows. Thus either half of a NARROW script block may be hidden independently. The numeric controls
retain one logical order in both modes: #, 0−1, 2−3, 4−5, 6−7, 8−9, Backspace. WIDE uses one
always-visible row; NARROW forms a conditional #/0−1/2−3/4−5 row and an always-visible
6−7/8−9/Backspace row. No special reorder or placeholder exists. Backspace is always visible,
including when the whole final row is unavailable, and spans exactly two base columns including
their intervening gap; empty draft disables it. Use full safe width equally for targets, not larger
gaps; preserve minima through bounded overflow. The registered hierarchy/icon contracts govern
shared list input, protected gaps, breadcrumb, occlusion/pager behavior and exact vectors.

Prepare names/filter/counts off main and playback-critical threads, bounded to the current section
without media/Indexer I/O or an all-library UI graph. Publish candidate availability and live
draft-preview rows together in one immutable snapshot. Same-source replacement retains the last
complete preview/appearance; stale source/projection results never publish or apply. Pending updates
cannot reset grid/list geometry or committed navigation. Backspace and exit remain usable. Preview
uses its own initial anchor; uncommitted group choices never overwrite the applied return snapshot.
Backspace and every valid breadcrumb-prefix choice, including an empty root label, save the
resulting filter in the destination container and advance the search return snapshot while leaving
A-Z open. An accepted contextual Play saves the source container's current draft and preview anchor
before queue preparation, including Play from ROW_ACTIONS and bookmark marks. This navigation save
does not depend on queue success or alter queue membership. Cancel/Home/Flip then restore the latest
saved filter and anchor; they discard only later uncommitted draft changes. Source changes recompute
the retained draft; a removed path restores its surviving ancestor's independent state. The final
group plate of the current container's A-Z prefix removes exactly its last group, both while the
keypad is open and after it closes manually or automatically. Thus Albums/GHI returns to Albums with
no filter; Albums/ABC/GHI returns to Albums/ABC whether ABC or final GHI is activated. Earlier
prefix plates retain their displayed destination; ancestor-container plates retain their independent
filters and navigation. The root label selects the empty prefix and gains no additional parent
action. Open search stays open and saves through the same breadcrumb operation above. With search
closed, prefix navigation uses the ordinary filter save and automatic-entry policy below.
Accessibility describes the actual destination. The registered hierarchy contract owns non-modal
ordinary navigation/actions and full-session Back restoration.

Per-profile Auto search defaults true and changes only with successful Settings Save. With it
enabled, every ordinary navigation activation of a nonempty hierarchy container receives one A-Z
entry attempt, including first entry, child descent and return to a previously visited parent or
ancestor through Back, Parent or breadcrumb. Entry eligibility uses the complete immediate source
rows before any restored A-Z filter and requires their laid-out content height to be at least three
times the current usable ordinary list viewport. A returned container restores its applied filter,
first visible row, pixel offset and focus; automatic entry opens A-Z with that restored filter as
its draft. The A-Z chrome control is the sole explicit keypad open/close control: opening preserves
the committed anchor and closing commits the draft while preserving the preview anchor. Breadcrumb
prefixes retain only their filtering and navigation meaning. In the ordinary hierarchy, activating a
breadcrumb prefix that changes the committed A-Z steps is a navigation activation even when the
container path is unchanged; it selects that prefix and grants one new automatic-entry attempt.
Closing or applying A-Z while remaining in the same container consumes the attempt and cannot reopen
it. Reload, refresh, resize, Backspace, draft filter changes, auxiliary-menu return and activation
of a breadcrumb that leaves both path and committed prefix unchanged do not create another
navigation activation. An explicit group choice automatically commits only a nonempty result fitting
one ordinary list viewport after search closes, including safe frame, normal chrome/pager and exact
row gaps; capacity may be zero. The smaller keypad-obscured preview viewport controls scrolling
only. Opening, Backspace, breadcrumb navigation, reactions, auxiliary return, refresh, resize or
empty results never automatically close the keypad. The explicit filter saves above are independent
of automatic group completion and apply even when Auto search is false. Auto search false disables
automatic entry/apply while the manual A-Z open/apply/close control remains available.
