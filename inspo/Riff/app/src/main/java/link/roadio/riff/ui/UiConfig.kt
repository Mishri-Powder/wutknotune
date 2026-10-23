package link.roadio.riff.ui

import android.content.Context
import androidx.compose.runtime.Immutable
import link.roadio.riff.config.ConfigurationSnapshot
import link.roadio.riff.config.PackagedConfiguration
import link.roadio.riff.config.ProjectConfiguration
import link.roadio.riff.config.parseColor
import link.roadio.riff.player.core.selection.AiSelectionModelConfig
import link.roadio.riff.player.core.selection.AiSelectionWeights
import link.roadio.riff.shared.config.AndroidProjectConfiguration
import org.json.JSONArray
import org.json.JSONObject

/** Presentation view over one immutable startup snapshot; resize/theme selection performs no I/O. */
@Immutable
class UiConfig private constructor(
    private val snapshot: ConfigurationSnapshot,
    val wide: Boolean = true,
    val night: Boolean = false,
) {
    val activeSkin: String get() = snapshot.activeSkin

    fun resolved(
        rootWidthDp: Float,
        night: Boolean,
    ): UiConfig = UiConfig(snapshot, snapshot.isWide(rootWidthDp), night)

    fun themed(night: Boolean): UiConfig = UiConfig(snapshot, wide, night)

    fun float(path: String): Float = (value(path) as Number).toFloat()

    fun int(path: String): Int = (value(path) as Number).toInt()

    fun long(path: String): Long = (value(path) as Number).toLong()

    fun boolean(path: String): Boolean = value(path) as Boolean

    fun string(path: String): String = value(path) as String

    fun color(path: String): Int = parseColor(string(path))

    fun intList(path: String): List<Int> = (value(path) as List<*>).map { (it as Number).toInt() }

    fun floatList(path: String): List<Float> = (value(path) as List<*>).map { (it as Number).toFloat() }

    fun colorArray(path: String): IntArray = (value(path) as List<*>).map { parseColor(it as String) }.toIntArray()

    fun alphaByteList(path: String): List<Int> = (value(path) as List<*>).map { (it as String).toInt(16) }

    fun excludedGenreIds(): Set<Long> = (value("player.excludedGenreIds") as List<*>).map { (it as String).toLong() }.toSet()

    fun aiSelectionModel(): AiSelectionModelConfig = selectionModel(value("ai.selectionModel") as Map<*, *>)

    private fun value(path: String): Any = snapshot.value(path, wide, night)

    companion object {
        const val OVERRIDE_FILE_NAME = ProjectConfiguration.OVERRIDE_FILE_NAME
        private val packaged = UiConfig(PackagedConfiguration.snapshot)

        @Suppress("UNUSED_PARAMETER")
        fun defaults(context: Context): UiConfig = packaged

        fun load(context: Context): UiConfig = UiConfig(AndroidProjectConfiguration.load(context).snapshot)

        fun currentOrNull(): UiConfig? = AndroidProjectConfiguration.currentOrNull()?.let { UiConfig(it.snapshot) }

        internal fun fromUserText(text: String): UiConfig = UiConfig(ProjectConfiguration.parse(text))

        // Focused domain tests exercise the same registry as whole-file production validation.
        private fun validated(
            path: String,
            value: Any,
        ): Any = jsonValue(value).also { ProjectConfiguration.validateValue(path, it) }

        private fun jsonValue(value: Any): Any =
            when (value) {
                is JSONObject -> value.keys().asSequence().associateWith { jsonValue(value.get(it)) }
                is JSONArray -> List(value.length()) { jsonValue(value.get(it)) }
                is List<*> -> value.map { jsonValue(requireNotNull(it)) }
                is Int -> value.toLong()
                else -> value
            }

        internal fun validateAiSelectionModel(value: Any): AiSelectionModelConfig =
            selectionModel(validated("ai.selectionModel", value) as Map<*, *>)

        private fun selectionModel(value: Map<*, *>): AiSelectionModelConfig {
            fun number(key: String) = (value[key] as Number).toDouble()
            val weights = value["weights"] as Map<*, *>

            fun weight(key: String) = (weights[key] as Number).toDouble()
            return AiSelectionModelConfig(
                temperature = number("temperature"),
                axisMinimumSpan = number("axisMinimumSpan"),
                pteMinimumGroupSize = number("pteMinimumGroupSize").toInt(),
                directoryDecay = number("directoryDecay"),
                historyDecay = number("historyDecay"),
                weights =
                    AiSelectionWeights(
                        arousalDistance = weight("arousalDistance"),
                        valenceDistance = weight("valenceDistance"),
                        pteDistance = weight("pteDistance"),
                        trackLikeDeficit = weight("trackLikeDeficit"),
                        trackDislikePenalty = weight("trackDislikePenalty"),
                        directoryLikeDeficit = weight("directoryLikeDeficit"),
                        directoryDislikePenalty = weight("directoryDislikePenalty"),
                        albumLikeDeficit = weight("albumLikeDeficit"),
                        albumDislikePenalty = weight("albumDislikePenalty"),
                        artistLikeDeficit = weight("artistLikeDeficit"),
                        artistDislikePenalty = weight("artistDislikePenalty"),
                        genreLikeDeficit = weight("genreLikeDeficit"),
                        genreDislikePenalty = weight("genreDislikePenalty"),
                        playHistoryPenalty = weight("playHistoryPenalty"),
                    ),
            )
        }

        internal fun validateAiHeatmapAlphaMap(value: Any): IntArray =
            (validated("ai.heatmapAlphaMap", value) as List<*>).map { (it as String).toInt(16) }.toIntArray()

        internal fun validateIdleTimeoutSeconds(value: Any): Long = validated("behavior.idleTimeoutSeconds", value) as Long

        internal fun validateRecentHistoryGenerations(value: Any): Long = validated("player.recentHistoryGenerations", value) as Long

        internal fun validateRewindPreviousThresholdSeconds(value: Any): Map<String, Long> =
            (validated("player.rewindPreviousThresholdSeconds", value) as Map<*, *>).entries.associate {
                it.key as String to
                    it.value as Long
            }

        internal fun validateExcludedGenreIds(value: Any): Set<Long> =
            (validated("player.excludedGenreIds", value) as List<*>).map { (it as String).toLong() }.toSet()

        internal fun validateScreenDiagonalChoices(value: List<Number>): List<Float> =
            (validated("player.screenDiagonalChoicesInches", value) as List<*>).map { (it as Number).toFloat() }

        internal fun validateAzDisabledAlpha(value: Any): Int = (validated("behavior.azDisabledAlpha", value) as Long).toInt()

        internal fun validateAzRgbSubtract(value: Any): Int = (validated("behavior.azRgbSubtract", value) as Long).toInt()
    }
}
