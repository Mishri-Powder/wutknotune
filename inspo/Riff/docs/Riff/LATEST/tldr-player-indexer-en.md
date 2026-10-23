# How Player and Indexer work together

[Read on the website](https://roadio.link/tldr/player-indexer/)

Player handles playback and interaction with the listener; Indexer prepares the library. Both belong
to the same Riff application, but run in two separate Android processes. A process is a running part
of the program with its own memory and lifetime: Android can stop one while the other continues.

## What does each process own?

Player displays screens, plays audio, manages the queue, and saves settings, playlists and listening
history. Indexer discovers files, extracts information, identifies their content, performs analysis,
and produces ready-to-use Album, Artist and Genre catalogs.

Player can filter and sort these catalogs for display, but Indexer applies the rules that group
files into albums. Each process owns its mutable data and does not open the other process's mutable
database. Library work runs separately from playback-critical tasks and remains subordinate to audio
continuity.

These boundaries are defined by the
[architecture contract](https://wiki.271.pics/Riff/LATEST/runtime-architecture).

## Why use AIDL, and how does a call work?

Two processes cannot access each other's memory like parts of a program running within one process.
They need an explicit interface: which requests are allowed, which parameters are passed, and which
responses can be returned.

Riff describes this interface using AIDL, the Android Interface Definition Language. The `.aidl`
files declare operations and data types. During the build, Android generates code that packages the
parameters, transfers them through the system's Binder mechanism, and reconstructs them at the
receiving end. Developers implement the actions performed by each call.

When Player connects, it obtains the Indexer service interface. Calling a method looks like calling
a function in program code, but its parameters travel through Binder into the other process. The
receiver performs the defined action there. AIDL describes the interface; Binder carries the call
between processes.
[Android documentation](https://developer.android.com/develop/background-work/services/aidl).

The project has two interfaces in opposite directions. `IIndexerArtifactService` lets Player contact
Indexer to establish a channel, request library data, and coordinate operations.
`IPlayerArtifactCallback` lets Indexer contact Player with notifications, progress and
acknowledgments. This reverse interface is needed because results often arrive later than requests;
Player does not have to keep asking whether work has finished.

This connection is for Riff's own components. The Indexer service is not exposed to other
applications. Communication stays on the device and needs no internet connection.

## How do they know a command was handled?

After connecting, the endpoints establish a communication session. The AIDL method
`openReliableChannel` accepts Player's callback interface and an identifier for its current
instance. The session distinguishes messages from the current processes from delayed messages left
over after a restart.

Riff defines its own reliable-delivery rules above Binder. A command contains a session identifier,
sequence number, type and parameters. The receiver performs the action defined for that command and
returns an ACK: an acknowledgment with the result. In each direction, only one command waits for
acknowledgment at a time.

Sending a call does not mean its action has happened. Some AIDL calls are marked `oneway`: the
sender continues without waiting for a reply to that call. Riff's separate acknowledgment reports
the accepted or completed action according to the command's meaning.

If the acknowledgment does not arrive, the sender repeats the same packet with the same number. The
receiver recognizes the duplicate and returns the saved result without performing the action again.
Losing an acknowledgment must not repeat a data-changing operation. For long operations, acceptance
and final completion are reported separately: confirming that work has started does not mean all
work is finished.

Reconnecting the same live instances preserves unfinished delivery. A new instance negotiates a new
session; old commands are not automatically carried into it. Message processing and acknowledgment
waits run separately from the UI and playback-critical work.
[Reliable communication contract](https://wiki.271.pics/Riff/LATEST/reliable-player-indexer-ipc).

## How is a large library transferred?

The library is transferred separately from short control messages. Indexer prepares an immutable set
of four coordinated files: media information, albums, artists and genres. This set represents one
accepted library version.

Indexer first writes the complete files and integrity information. It then makes the set current
with one atomic switch: the consumer sees either the previous complete set or the new one. Albums
from one version must never be mixed with media from another.

Through AIDL, Player receives a file descriptor: Android-provided read-only access to a specific
file that is already open. It also receives the generation number, size, format versions and a
checksum for validating the contents.

Player must not find the transferred file by reconstructing a shared path. Indexer might prepare
another version while Player processes the notification. The descriptor keeps access to the exact
file delivered, even if its former name has been removed.

Player validates and accepts the complete set. Its previous accepted library stays active during
validation. An incomplete or damaged candidate does not replace it. At startup, Indexer first offers
an existing valid set, when one exists, before starting a new scan.
[Publication contract](https://wiki.271.pics/Riff/LATEST/immutable-publication).

## When do scan results become visible?

Indexer first discovers sources, then extracts information and identities, and finally performs the
required analysis. It sends Player display-ready progress text: the stage, completed counts and
speed. Player displays that text. A change in the processed-file count does not itself mean a new
library set is available.

Updates are published only as complete, validated states. The first intermediate publication of
changed data is due 30 seconds after processing begins; later publications are due 60 seconds after
the preceding successful publication, at a safe completed-work boundary. A long operation finishes
first; partial results are not published. At pipeline completion, remaining changes are published
immediately. A scan failure preserves Player's last accepted set.
[Stages and publication timing](https://wiki.271.pics/Riff/LATEST/indexer-staging).

## What happens if Indexer crashes?

Before processing a particular source, Indexer registers an attempt in a small in-memory journal
held by Player and waits for acknowledgment. After completion, it reports the end of that attempt
and the corresponding entry is removed.

If Indexer actually dies, the remaining entry helps arrange a cautious check of the suspected source
after files are discovered again. An unfinished attempt is not immediately treated as a source
error. A lost message or delay does not mean a damaged file either. Player keeps attempt information
but does not scan or parse media.

The journal exists only in memory. If both processes die, it is lost and a new session starts normal
processing. [Recovery contract](https://wiki.271.pics/Riff/LATEST/indexer-source-failures).

## How are changes to folder access coordinated?

Before changing permissions for selected folders, Player asks an already connected Indexer to finish
its current pipeline with the old permissions and confirm readiness. Player then changes the
permissions and requests one new full pipeline. If the completion barrier fails, permissions remain
unchanged.

This prevents Indexer from continuing work with access that has already changed. When folders are
selected for the first time from a state with no grants, no pipeline exists to drain: Player saves
the selection, connects Indexer and starts the initial pipeline.
[Folder-change rules](https://wiki.271.pics/Riff/LATEST/indexer-staging#6-saf-draft-and-rescan-boundary).
