# Player audio focus and restoration

> Owner/scope: restoring a play request and handling Android audio-focus interruptions.

## Restoration and interruption policy

Restore the active profile, queue/occurrence, Media.id, binding preference, playback modes, actual
position and whether playback was requested before attempting audio. Ignition continuation restores
an active user request: resume only with safely resolved media and granted focus, otherwise restore
paused with a recovery path. Follow Android 12+ audio-focus, MediaSession, foreground-service and
notification lifecycle rules. For AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK interruptions such as
navigation guidance, currently playing effective Music MUST continue at reduced volume and restore
its prior volume after focus returns. Currently playing effective Book MUST pause and, on focus
return, seek back 2,000 ms within the same occurrence before resuming. Use ordinary seek clamping at
file start; an interruption within the first two seconds introduces no special behavior or
previous-track/chapter navigation. This policy follows the playing track's effective category,
independently of the browser's Books toggle. Resume only a still-valid prior play request; manual
Pause/Stop, permanent focus loss, queue replacement or playback error cancels automatic resumption.
Temporary focus loss MUST NOT turn a retained play request into a persisted user pause. Focus resume
MUST NOT add another PLAY_STARTED event. Apply the same behavior in all application modes; host
navigation and motion restrictions remain unchanged.

[Queues](/Riff/LATEST/player-queues) owns the immutable execution revision;
[history](/Riff/LATEST/player-listening-history) owns actual-start accounting.
