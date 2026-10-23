# Reliable Player/Indexer IPC

> Owner/scope: application delivery, sessions and source-attempt acknowledgments over Binder

## Delivery channel

Control messages use one shared Android-free protocol engine with thin Binder adapters. Each
direction has its own monotonic SEQ and bounded queue; at most one command awaits ACK in each
direction (Stop-and-Wait ARQ). A packet contains SESSION, SEQ, type and bounded string fields
interpreted by that type. ACK contains SESSION, acknowledged SEQ and the original application
result. SESSION identifies both endpoint incarnations. Reconnection of live endpoints retains
counters, receiver results and unacknowledged packets. New endpoint instances negotiate a new
session; stale packets never affect it.

Keep the immutable packet until its matching ACK. Retransmit the same command/SEQ on a monotonic
timer with bounded exponential backoff. Apply the expected command once, atomically retain its
result and advance the receive sequence, then ACK. Duplicate delivery returns the original result;
out-of-order future commands are not applied. Sequence reuse with different content is a protocol
error. ACKs bypass ordinary command queues and do not themselves require ACK; a repeated command
elicits another ACK. Bounded duplicate tracking cannot evict the result still needed by a sender.

ACK means the defined application action has occurred, not merely that Binder queued a transaction.
For source BEGIN it means registration, including the original NORMAL/ISOLATE/QUARANTINE decision;
for END it means removal of that exact attempt. Repeated BEGIN after ACK loss cannot turn its own
registration into a previous-crash suspicion. Commands with lengthy fences acknowledge accepted work
separately from their eventual result. Result delivery is itself reliable. Mutating commands
deduplicate before enqueueing work. State notifications may coalesce before assignment of a SEQ; an
assigned packet is retained until ACK. Session establishment synchronizes current status and SAF
notice state; Player then requests the current artifact cohort.

Process IPC on dedicated executors. Do not wait for peer ACK on the UI, playback, Binder dispatch or
catalog-loading thread, or while holding a lock needed by ACK handlers. Retry timers and ACK
handlers remain independent of source work and storage/grant fences. Saturated queues apply bounded
backpressure. RemoteException or packet loss retains the unacknowledged command for retransmission
or endpoint reconnection; it never creates FAILEDL1/FAILEDL2. Only confirmed endpoint/process death
invokes recovery policy.

## Ownership and recovery

The physical source key is a fixed-width unseeded XXH3-64 digest of the exact provider document URI,
independent of the display filename. L1 and L2 use the same key constructor and bounded
stat/stage/span framing. Long valid filenames cannot exceed protocol identity limits; display names
stay in local status handling.

Source-attempt packets are opaque Player-owned RAM records under
[source failure recovery](/Riff/LATEST/indexer-source-failures). Their identifiers, SEQ and session
tokens are transient protocol state, not additional persisted index fields or backend identities. No
per-command durable log is required. Process death ends delivery guarantees for that endpoint; a
surviving Player reconciles unfinished Indexer attempts. If both die, a fresh handshake starts
normal indexing. A reset never replays an old mutating command into a new endpoint implicitly.

Reliable notifications do not authorize Player reconstruction of catalogs. Immutable artifacts
retain the [exact-inode descriptor contract](/Riff/LATEST/immutable-publication): idempotent read
requests validate generation/checksum and own fresh descriptor duplicates; duplicates are closed
without applying a cohort twice. Descriptor acceptance and application ACK are distinct from an
expensive model load. Long operations expose correlated completion without blocking the control
receiver. Existing generation and request-once gates remain effective across duplicate delivery.

Tests use deterministic loss, duplicate/reordered frames, ACK loss, delayed stale packets, reconnect
and endpoint death. Require one effect per accepted SEQ, stable duplicate results, safe ACK
processing under a blocked business operation and no failure flag caused solely by transport.

The design adopts acknowledgment/retransmission principles from
[QUIC RFC 9000 section 13](https://www.rfc-editor.org/rfc/rfc9000.html#section-13); it is a bounded
application protocol, not a QUIC implementation.
