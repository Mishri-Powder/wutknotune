package link.roadio.riff.indexer.core.media

/** MIME admits an acquisition candidate; only the device playback probe admits audio.
 * Contract: https://wiki.271.pics/Riff/LATEST/indexer-media-admission#device-derived-audio-admission
 */
internal fun isAudioCandidateMime(mime: String?): Boolean = mime?.matches(audioMime) == true

private val audioMime = Regex("audio/[a-z0-9!#$&^_.+\\-]+", RegexOption.IGNORE_CASE)
