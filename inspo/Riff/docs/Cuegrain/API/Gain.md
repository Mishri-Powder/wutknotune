# Gain

Package: `link.roadio.cuegrain`. [All classes](../API.md) · [Method index](methods.md)

A decoded gain, peak or reference observation with its original field evidence. Zero is a valid
observation.

## Declaration

```kotlin
data class Gain(
    val kind: String,
    val scope: String,
    val value: Double,
    val unit: String,
    val key: String,
    val raw: String,
    val reference: Double? = null,
    val referenceUnit: String? = null,
)
```

## Constructor parameters and properties

The declaration above shows exact types, order and defaults.

| Parameter       | Meaning                                                                                   |
| --------------- | ----------------------------------------------------------------------------------------- |
| `kind`          | Representation, for example replaygain, r128, soundcheck, lame, opus-header or reference. |
| `scope`         | Track/album or representation-specific scope.                                             |
| `value`         | Decoded numeric observation; inspect unit before using.                                   |
| `unit`          | dB for gains, linear for peaks, or reference unit.                                        |
| `key`           | Original field key.                                                                       |
| `raw`           | Original field text or representation evidence.                                           |
| `reference`     | Optional explicit reference value; absence remains unknown.                               |
| `referenceUnit` | Optional reference unit, such as LUFS.                                                    |

## Member details

Cuegrain neither picks a candidate nor combines fields across sources nor applies audio gain. R128
is decoded from Q7.8 and retains its -23 LUFS reference; Opus header gain stays separate. The public
constructor does not itself enforce finite/range invariants; parsers validate their observations.

## Value operations

Kotlin provides `copy`, destructuring components, `equals`, `hashCode` and `toString`. Lists/maps
are not defensively deep-copied; keep supplied collections stable. Model construction does not run a
parser or apply application policy.

## Example

The following Kotlin statements run inside a function with `import link.roadio.cuegrain.*`. Fully
qualified names identify optional/repair-package APIs.

```kotlin
val gains = Metadata.gains(listOf(Tag("R128_TRACK_GAIN", "256")))
val gain = gains.single()
check(gain.value == 1.0 && gain.unit == "dB")
check(gain.reference == -23.0)
check(Gain("replaygain", "track", 0.0, "dB", "REPLAYGAIN_TRACK_GAIN", "0 dB").value == 0.0)
```

## Source and related API

Implementation: `core/src/main/kotlin/link/roadio/cuegrain/Model.kt`. This reference describes the
current library source; internal/private helpers are excluded. See the
[library contract](../contract.md) and [runnable recipes](https://wiki.271.pics/Cuegrain/examples).

Related types: [Cuegrain](Cuegrain.md).
