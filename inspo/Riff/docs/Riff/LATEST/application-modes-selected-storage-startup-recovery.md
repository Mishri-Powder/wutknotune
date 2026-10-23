# Riff application modes and AAOS review contract — 10. Selected-storage startup recovery

> Owner/scope: bounded section of Riff/LATEST/application-modes

## 10. Selected-storage startup recovery

The central waiting/reset contract controls exact copy and storage effects. Phone permits reset
confirmation by tap. Automotive uses parked entry; unavailable UNKNOWN evidence permits a deliberate
recovery hold and equivalent accessibility action solely for reset confirmation. Explicit RESTRICTED
evidence blocks both opening and committing reset. AAOS requires supported parked/host evidence;
absent evidence disables/omits reset with no recovery bypass. Before active-root configuration is
readable, use actual-AAOS precedence and the last valid bootstrap-cached application mode, falling
back to the packaged configuration mode (currently phone). No unavailable-root override is read.
Waiting reads no root-bound state and requires no Settings access. The gate is shared by Activity
and direct service entry points.
