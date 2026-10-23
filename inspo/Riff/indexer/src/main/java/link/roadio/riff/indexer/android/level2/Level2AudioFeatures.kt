package link.roadio.riff.indexer.android.level2

internal object Level2AudioFeatures {
    init {
        System.loadLibrary("riff_level2_audio")
    }

    fun computeLogMel(pcm: FloatArray): FloatArray {
        require(pcm.size == PCM_FLOAT_COUNT)
        return nativeComputeLogMel(pcm).also { output ->
            require(output.size == LOG_MEL_FLOAT_COUNT)
            require(output.all(Float::isFinite))
        }
    }

    private external fun nativeComputeLogMel(pcm: FloatArray): FloatArray

    const val EXCERPT_COUNT = 3
    const val SAMPLE_RATE = 32_000
    const val EXCERPT_SAMPLES = 320_000
    const val FRAMES = 1_000
    const val MEL_BINS = 64
    const val PCM_FLOAT_COUNT = EXCERPT_COUNT * EXCERPT_SAMPLES
    const val LOG_MEL_FLOAT_COUNT = EXCERPT_COUNT * FRAMES * MEL_BINS
}
