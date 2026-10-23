# MDRG format

> Owner/scope: the Cuetree-keyed analysis database for ReplayGain and AI.

## Exact schema

The file identifier is `MDRG`, root `CuetreeAnalysis`, database version `MDRG-DB-V6`, descriptor
schema/semantic versions **4/6**. The maintained schema is `indexer/src/main/fbs/cuetree_mdrg.fbs`:

```fbs
namespace link.roadio.riff.indexer.analysis.fbs;

enum AnalysisState:ubyte { Pending = 0, Available = 1, CompletedNoResult = 2, RetryableFailure = 3, DeterministicFailure = 4 }
enum AnalysisGainSource:ubyte { None = 0, ReplayGain = 1, R128 = 2, Lame = 3, SoundCheck = 4, Measured = 5 }

table AnalysisMediaId {
  hi:long;
  lo:long = null;
}

table AnalysisGenre {
  genre_id:long;
  confidence:float;
}

table AnalysisRecord {
  media_id:AnalysisMediaId (required);
  gain_state:AnalysisState = Pending;
  gain_source:AnalysisGainSource = None;
  track_gain_q8:short = null;
  track_peak:float = null;
  album_gain_q8:short = null;
  album_peak:float = null;
  album_source:AnalysisGainSource = None;
  ai_state:AnalysisState = Pending;
  pte:float = null;
  pte_confidence:float = null;
  arousal:float = null;
  arousal_confidence:float = null;
  valence:float = null;
  valence_confidence:float = null;
  genres:[AnalysisGenre];
  model_revision:string;
  gain_revision:string;
  decoder_hint:long = null;
}

table CuetreeAnalysis {
  database_version:string (required);
  generation:long;
  records:[AnalysisRecord];
}

root_type CuetreeAnalysis;
file_identifier "MDRG";
```

`AnalysisMediaId.lo` is nullable. A record keeps **the exact MediaID with which it first entered
MDRG**; expansion of a Cuetree short ID does not rewrite the stored key. Records are unique and
sorted by signed `(hi, low-presence, lo)`. A retained `SHRT` marker on the original Cuetree identity
makes a stored short key resolve to that identity. Full-key contenders are never aliases.

The gain and AI state fields are independent. Absence, completed-no-result, retryable failure and
deterministic failure in one family cannot discard accepted data in the other. A Music FILM or FILF
is separately eligible for both; a Book FILM or FILF is eligible for gain and its AI state remains
Pending. FILF measured gain and AI samples cover its own nullable-bounded span; whole-container
TrackGain is not copied to FILF. External AlbumGain/AlbumPeak may be associated with the FILF from
its container metadata. Scalar presence distinguishes missing from numeric zero. Gain is signed Q7.8
dB at canonical -18 LUFS; peaks are finite linear amplitudes. A/V/PTE values are finite in [-1,+1],
confidence and genre probability in [0,1]. `genres` stores independent calibrated accepted GNRE
labels. Fuzzy genre equivalence is outside this release.

MDRG generations strictly increase per publication and are independent of Cuetree generations.
Publication is atomic under [ReplayGain storage](replaygain-storage.md).
