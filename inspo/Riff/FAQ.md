# Riff — questions and answers

See the [Russian FAQ](https://wiki.271.pics/Riff/LATEST/faq-ru) or the
[Wiki FAQ index](https://wiki.271.pics/Riff/LATEST/faq).

## What is Riff for?

Your own music and audiobooks on Android, mainly on aftermarket Android head units. Put your
collection on a drive, get in the car and listen. Phones and tablets work too if they run Android 12
or newer.

## Why another music player?

Poweramp, Musicolet and plenty of others already exist, and some are excellent. A head unit brings
its own annoyances: tiny buttons, strange display-density reports and power disappearing without a
polite shutdown. Riff grew out of wanting a player built around those conditions: large controls,
short routes through a collection, and books kept out of music shuffle.

The author's practical goal is music on roads where reception runs out first. Repeating one album
gets old, and making a fresh playlist for every drive is a chore. That is where offline analysis and
mood-based selection come in. Version 1.0.0 is the first application release; the AI model remains
public beta.

## Will there be a macOS or iOS version?

Neither is planned. Android head units are the project’s reason for existing; a Mac player would
solve a different problem.

## Why does the interface look so utilitarian?

So you can hit the button on the first try. The author took inspiration from a milling-machine
control panel and a little from aircraft instruments. Seriously. Artwork is welcome, but clear
controls take priority over winning a skin contest.

## Do I need an account, subscription or internet connection?

No, not for listening. Bring your own files; analysis runs on the device without uploading the
music. Riff does not supply a catalogue or download albums. Getting an APK or an update is separate.

## How do I start?

Install the signed APK from your distributor. If no media folders are selected, Riff opens the
folder picker. Choose **Add..**, pick a folder in Android's dialog, allow access, and confirm
**Media folders**. Give the library time to prepare, then browse and play.

Later, use **Settings → More… → Media folders…**. Android decides which directories and removable
drives appear in its picker. A disconnected drive cannot supply music just because permission was
previously granted.

## Does Riff move, delete or retag my audio?

Indexing reads the audio and keeps its own data separately. Display-text repair does not rewrite
source tags. Where writing is allowed, a small Riff identification folder may appear. Removing a
folder from the sources takes away access; it does not delete its music.

## How does A–Z search work?

Think of a letter filter for a long list. The author looked to car interfaces, including BMW and
Mercedes, for inspiration. Riff supports Cyrillic and Auto search: the keypad opens for long lists
and finishes when the results are short enough to choose comfortably.

Each step narrows names in the container you have open. It does not recursively search the entire
drive. **#** groups other characters rather than acting as a wildcard. Backspace undoes a step; the
A–Z control closes the keypad while keeping the filter.

## What does the AI do?

The APK contains a real offline model based on **EfficientAT**, with four tasks sharing one
backbone: Arousal, Valence, perceived speed (PTE), and genres. No internet connection is needed.

It is not a chat assistant or a perfect judge of taste. The model is beta quality. File analysis is
shared, while selection uses the active profile’s reactions, listening history and chosen
parameters. See the [current status](https://wiki.271.pics/Riff/LATEST/tldr) for edition-specific
validation.

## What do A, V and PTE mean?

Arousal is calm versus exciting: a quiet evening on the sofa versus wanting to jump around. Valence
is the positive versus gloomy tone. Together they describe emotion in the spirit of Russell's
two-axis model: high A/V is energetic and cheerful, low A/V calm and melancholic.

PTE is how fast the music feels. It is a separate estimate, not measured BPM. Fast is not always
exciting, and calm is not always slow. Values run from -1 to +1; you do not need to memorize the
numbers to choose music.

## Why is AI disabled or still analyzing?

It needs usable completed analysis. Playing successfully does not guarantee that analysis also
succeeded. AI is disabled in Books. If too few tracks qualify, a queue can be shorter than
requested; books and exclusions do not get waived to fill it. An automatically recognized book stays
out of AI even after you manually display it as Music.

## What do Like, Dislike and Ban mean?

The idea is simple: Like means “more often,” Dislike “less often,” and Ban “leave it out of AI
selection.” You can still play a banned file yourself. Changes are meant for later selections,
without rewriting the queue already playing.

Reactions are stored separately for each profile and used in later AI selections. The neural model
itself is shared: a Like changes selection preferences, rather than retraining a personal copy of
the network.

## Why ban a track instead of deleting it?

Holiday music may be welcome in winter and irritating on a summer road trip. Exclude it for a
season, then bring it back with the files intact. The author does not want a calendar-driven control
centre: head-unit clocks can be unreliable, and a simple player needs boundaries.

## How do I listen to audiobooks?

Switch **Books** on. Albums become **Titles**, and artists **Authors**. The mode is remembered for
each profile. A useful way to prepare files is adding a recognized **Audiobook** genre alongside the
literary genre, such as `Drama; Audiobook`, before copying them to the drive.

M4B filenames, supported media-kind tags and Android's actual **Audiobooks** folder provide other
automatic clues. If one representative member of a grouped album has an automatic book clue, the
whole album becomes a book. Matching Album text alone does not merge unrelated editions.

If detection gets it wrong, supported track, album/title and folder actions offer **Music** and
**Book** icon buttons. Tap either to select it, or tap the selected button again for **Auto**
(neither selected), which returns to inherited/detected classification. M4B/MP4 and supported MP3
chapters appear when present and valid; missing chapters are not invented.

## Can I change audiobook speed?

In Books mode, the playing book has a bold monospace **1.0x** control in the Ban slot. Tap to
advance by 0.1, from 0.7 through 1.5, then back to 0.7. Hold to restore 1.0. Pitch stays unchanged
and silence skipping is enabled. Music uses ordinary speed without silence skipping.

One speed is saved for all books in each profile and survives restart. Switching profiles restores
that person's speed. The current source behavior is owned by the
[audiobook speed contract](https://wiki.271.pics/Riff/LATEST/player-audiobook-speed); installed
release status is in [current delivery](https://wiki.271.pics/Riff/LATEST/tldr).

## What happens when the navigator speaks?

With the appropriate audio-focus request, music gets quieter and returns to its previous volume when
the prompt ends. A book pauses, waits for focus to return, goes back two seconds and resumes. That
little rewind helps you recover the interrupted sentence. If you pressed Pause or Stop yourself,
Riff does not resume on your behalf. The playing file's classification decides this, even while you
browse another section.

## Will it remember where I stopped?

Album and Book Title positions belong to your profile and can resume their saved context. Choosing a
particular track deliberately starts it at the beginning. Temporary Folder, aggregate, playlist and
AI queues do not have the same cold-start persistence.

The position is saved periodically during playback. Pull the power suddenly and the latest unsaved
progress can be lost. Handling rough shutdowns is a reason Riff exists, but “not one second lost
under any failure” would be too much to promise. Finishing a book does not erase manual bookmarks.

## Can different people have their own preferences?

Yes: profiles **1**, **2** and **3** in Settings. Each has its own Music/Books mode, playlists,
history and saved contexts; the collection is shared. Switching profiles does not replace the queue
already playing.

AI uses the same separation. Keep a genre in profile 1 and use **Genres → Ban** for it in profile 3:
later AI selections for profile 3 exclude it, while profile 1 keeps its preferences. After switching
profiles, generate a new selection to hear the new profile’s choices. The existing queue stays
intact.

## How do playlists and the current queue differ?

A playlist is a saved list. Starting it makes a fixed playback queue. Later playlist edits do not
rewrite what is playing. Repeated tracks may be intentional. AI Add also contributes a finite
selection, rather than a rule that keeps choosing forever.

## The buttons look the wrong size. What should I do?

Open **Settings → More… → Diagonal**. Set the diagonal, or choose **Card** and match the drawing to
a physical card or supported banknote. Confirm, then save **More**. **Auto** restores the starting
estimate. Vendor scaling or cropping can still affect the result.

## Is “Save data on” the same as choosing my music folders?

**Media folders…** chooses the music sources. **Save data on…** chooses where Riff keeps its
settings, index, analysis and listening state. Confirming the latter moves those data and may stop
playback. A later Cancel in a parent screen cannot undo a completed move or folder-access change.

If that selected USB/SD volume is missing at startup, you get `Waiting for USB/SD storage…`.
Reconnect the same volume and wait for it to become ready. Riff keeps your choice instead of quietly
opening a fresh internal database.

To start over deliberately, use `Forget and reset to Internal`, then `Reset`. That creates fresh
internal settings, playlists and history without carrying over the previous state. It does not wipe
the external drive. Cancel keeps waiting. Waiting alone cannot repair a broken drive.

## What do AU and IA under Rebuild DB mean?

**AU** rebuilds AI and ReplayGain analysis; **IA** rebuilds the media index. Select what you need
and save **More**. It takes time and can temporarily leave that part of the library unavailable. It
does not delete your music.

## Can I migrate from another player or an older Riff version?

You can pick the same original audio folders. There is no supported importer for another player's
playlists, ratings, history or bookmarks. Diagnostic exports are for investigating problems, not
restoring everything later.

Pre-release builds do not promise data-format compatibility across updates. A new edition may need
fresh state and a reindex of the original collection. An in-place update needs the same package and
a compatible signature. Uninstalling or clearing data can remove your listening state; do not make
that the first troubleshooting step.

## Why does the app show “RC”, “Beta” or an old-build message?

The label identifies the channel and edition date. Version 1.0.0 uses FINAL and has no diagonal
channel label. Alpha, Beta, Debug and Test remind you about their age after three calendar months
when a new process starts. Press **OK** and keep using the app. It is not a subscription or a timed
playback cutoff. RC and FINAL do not show the age dialog. See the
[current status](https://wiki.271.pics/Riff/LATEST/tldr) for editions.

## Is it an Android Auto or officially AAOS-compliant player?

No complete supported host integration has been established. Expect direct installation on an
Android head unit first. AAOS needs much more vehicle integration, including motion restrictions;
aftermarket units may lack reliable vehicle signals.

Car-interface guidance informed control size, touch targets and navigation. Those choices do not
establish host certification or make Riff a reliable judge of what you can do while driving.

## Where are the equalizer and sound effects?

There is no built-in equalizer, and the author does not plan to add one. His preference is to tune
the audio chain with a system DSP such as [JamesDSP](https://james34602.github.io/JamesDSPManager/)
or a separate DSP processor/amplifier. Whether a system tool fits your head unit depends on its
firmware and output path.

Riff does include ReplayGain and a peak limiter, so neighbouring recordings do not make you keep
reaching for the volume control. On Android, Riff does that work on the audio samples itself.

## What do ReplayGain F, I and N mean?

**F** outputs float32 after processing; **I** outputs int16; **N** has no Riff ReplayGain processor.
In I/F, the processor starts with PCM16, applies the gain and runs a peak limiter. F avoids rounding
the result back to int16. If F causes trouble on a particular device, try I; that is a fallback
worth checking, not a guarantee for every firmware.

Open **Settings → More… → ReplayGain**, choose the values and save **More**.

## What are the numbers 16, 18, 23 and 0?

Target loudness: 16 means -16 LUFS, 18 means -18 LUFS, and 23 means -23 LUFS. The current starting
setting is **F / 23**. This concerns the recording's overall loudness, not forcing every instant of
a song to the same level.

**0** applies no ReplayGain adjustment, but I/F still run the limiter. Use **N** to remove Riff's
processor. Missing usable gain data does not become an invented adjustment from the target alone.

## Where does ReplayGain come from? Should I prepare my files first?

That is a good way to get a whole-recording scan before copying files to the drive.
[rsgain](https://github.com/complexlogic/rsgain) and
[loudgain](https://github.com/Moonbase59/loudgain) can write ReplayGain metadata without rewriting
the audio itself. Riff also reads supported iTunes/Apple Music Sound Check data, `iTunNORM`.

Without usable data, Riff attempts its own measurement. Normally it uses three up-to-20-second
sections, up to a minute in total. If the bounded measurements fail, music can get a full-span
fallback; audiobooks do not. A hard “never more than one minute” limit would be inaccurate.

## Does float output make this a bit-perfect player?

No. I/F start with PCM16 even if the source stores more bits. Float output cannot recover precision
already lost at the input. Riff concentrates on listening comfortably in a car.

A dedicated bit-perfect USB path is a different job. For example,
[Resonāda](https://gitlab.com/resonada/resonada/-/blob/main/README.md) advertises that sort of
capability; check your actual hardware and output before assuming it will work.

## Is Riff free, and do donations unlock features?

First-party code is open under GPL-3.0-or-later. Donations do not unlock features or restrict code
access or use. Local listening needs no account or streaming subscription. GPL allows third-party
distributors to charge for their builds.

## What does GPLv3 mean for me?

You can use, study, change and share the code. Making a private modification alone does not require
you to publish it, and the licence does not take ownership of your music.

If you distribute a copy, keep the required notices and licence and supply the corresponding source
in a permitted way. Third-party components keep their own terms. The
[GNU GPL FAQ](https://www.gnu.org/licenses/gpl-faq.en.html) and
[licence text](https://www.gnu.org/licenses/gpl-3.0.html) have the details.

## May I sell it, bundle it in head-unit firmware or distribute it through a store?

Yes, with the GPL obligations still attached. Recipients keep their GPL rights; covered modified
code cannot simply become closed source. Where GPLv3's User Product rules apply, the required
installation information matters too. Check store/firmware conditions, dependencies and the
repository notices before distributing. GNU's
[GPLv3 guide](https://www.gnu.org/licenses/quick-guide-gplv3.html) explains that part.

## Does GPLv3 promise support or make this release risk-free?

No. An open-source licence is not a promise that every device works or that lost state can always be
recovered. The 1.0.0 application release still has the limitations documented here.

## Why are some files or albums missing?

Check the drive connection, permission for the actual folder and indexing progress first. Look in
**Folders**, and check Music/Books mode. An ordinary file without Album metadata may appear only in
Folders; supported CUE/chapter structures can supply an album identity. If it appears nowhere, check
readability and the actual format.

## Which formats work, and why does a file with a familiar extension fail?

The content matters more than the extension. Riff checks the real container and audio track against
available playback capabilities. Renaming something to .mp3 does not convert it. A small shareable
failing sample and its format help more than the filename alone.

## Why does playback stop with the screen off or in the background?

Closing the interface alone should not stop service playback. A vendor battery restriction,
audio-focus loss, Bluetooth Pause or an unplugged source can. Find the triggering event first; check
background restrictions if battery policy looks responsible. Include device, Android, edition and
reproduction steps when reporting it.

## Why are lock-screen or notification controls missing?

Android shows these from Riff's media session. Check the system notification/media-control settings
and whether playback is active. Vendor UI varies. Rebuilding your music library is not the answer to
a missing system button.

## Do Bluetooth and steering-wheel buttons work?

Standard Android media commands are the intended route. Some head units send wheel buttons only to
their own apps through a proprietary MCU path. Compare on-screen Play/Pause/Next with the external
commands and report the unit, firmware and connection. There is no universal MCU compatibility
promise.

## Does connecting Bluetooth or turning on the ignition start Riff?

Do not count on a universal trigger yet. The head unit may send Play itself; saved playback state is
a separate mechanism. Check what your actual unit does.

## Where do covers come from?

A suitable embedded cover comes first. Without it, fallback needs exactly one accessible, decodable
image next to the audio file. With several images, Riff does not guess between the front, back and
booklet. Otherwise you get default artwork. Automatic online downloads are not provided.

## Why are tags wrong or an album split?

Check Album, AlbumArtist, year and disc/track numbers. Folder relationships and CUE/TOC matter too;
matching Album text is not enough to merge unrelated editions. Without Title, a filename can become
the displayed name. Fix source tags externally and reindex. Riff does not guess an ambiguous broken
encoding into new text, and it does not rewrite your source tags.

## Why is a large collection still being prepared?

Thousands of files mean thousands of reads, especially on slow USB/SD. Analysis adds more work. A
file can already play while its analysis is unfinished. Check progress and source access before
resetting anything; repeated resets just restart the work.

## What should I check when there is no sound or playback stutters?

Volume, selected output, file access and drive connection. Compare another file and internal
storage. If processing looks involved, try ReplayGain N. Note whether every file fails, just one
format, or only Bluetooth. Do not start by clearing all application data.

## How do I create audiobook bookmarks?

Start from a Book Title and use Bookmark. Playing the same file through Folders alone does not
enable it. Starting from an existing mark also lets you add marks. The minimum spacing defaults to
15 seconds on the complete book timeline, including chapter boundaries.
`player.bookmarkMinSpacingSeconds` in the private configuration can change it after restart. The
button is disabled inside that interval; equality is allowed. Missing durations or current
application-mode restrictions can also disable a new mark. Finishing a book does not delete your
manual marks.

## Are diagnostic exports or internal snapshots a backup?

Diagnostic exports help investigate problems; they are not a supported restore format. Snapshots on
the same drive cannot save you from losing the drive itself. Keep original audio independently.
There is no supported importer for another player's lists, ratings, history or marks, and
pre-release updates make no data-format compatibility promise.

## How should I report a problem or request a feature?

Give the edition/date, device and Android/firmware, then the steps, expected result and actual
result. Was it only Bluetooth, only USB, after a navigator prompt, or after the screen went off? A
screenshot, relevant diagnostics and a small shareable sample are useful. “Does not work” is harder
to act on. For a feature, describe the listening task it would solve. Leave out credentials and
unrelated personal data.
