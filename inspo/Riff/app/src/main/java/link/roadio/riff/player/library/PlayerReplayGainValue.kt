package link.roadio.riff.player.library

data class PlayerReplayGainValue(
    val trackGainQ8: Short?,
    val trackPeak: Float? = null,
    val albumGainQ8: Short? = null,
    val albumPeak: Float? = null,
)
