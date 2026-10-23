package link.roadio.riff.config

/**
 * The single schema registry shared by build verification, Player and Indexer.
 * Variants belong to registered leaves, never to arbitrary owner objects.
 */
internal object ConfigurationSchema {
    enum class Form { FIXED, RESPONSIVE, THEMED, THEMED_RESPONSIVE }

    sealed interface Node {
        fun validate(
            value: Any,
            partial: Boolean,
        )

        fun merge(
            base: Any,
            overlay: Any,
        ): Any
    }

    class ObjectNode(
        val fields: Map<String, Node>,
    ) : Node {
        override fun validate(
            value: Any,
            partial: Boolean,
        ) {
            val entries = value.objectValue()
            require(entries.keys.all(fields::containsKey)) { "Unknown configuration key" }
            if (!partial) require(entries.keys == fields.keys) { "Missing configuration key" }
            entries.forEach { (key, child) -> fields.getValue(key).validate(child, partial) }
        }

        override fun merge(
            base: Any,
            overlay: Any,
        ): ConfigObject {
            val result = base.objectValue().toMutableMap()
            overlay.objectValue().forEach { (key, value) ->
                result[key] = result[key]?.let { fields.getValue(key).merge(it, value) } ?: value
            }
            return immutableObject(result)
        }
    }

    class Leaf(
        val form: Form = Form.FIXED,
        private val themeRequired: Boolean = false,
        private val check: (Any) -> Unit,
    ) : Node {
        override fun validate(
            value: Any,
            partial: Boolean,
        ) = validateForm(value, form, partial, themeRequired)

        private fun validateForm(
            value: Any,
            form: Form,
            partial: Boolean,
            required: Boolean = false,
        ) {
            if (value !is Map<*, *>) {
                require(!required) { "This color requires day and night branches" }
                check(value)
                return
            }
            val branches = value.objectValue()
            val themed = branches.keys.any { it == "day" || it == "night" }
            val keys = if (themed) setOf("day", "night") else setOf("wide", "narrow")
            require(branches.isNotEmpty() && branches.keys.all { it in keys }) { "Invalid variant" }
            if (!partial) require(branches.keys == keys) { "Incomplete variant" }
            if (themed) {
                require(form == Form.THEMED || form == Form.THEMED_RESPONSIVE) { "Theme variant not allowed" }
                val childForm = if (form == Form.THEMED_RESPONSIVE) Form.RESPONSIVE else Form.FIXED
                branches.values.forEach { validateForm(it, childForm, partial) }
            } else {
                require(form == Form.RESPONSIVE || form == Form.THEMED_RESPONSIVE) { "Width variant not allowed" }
                branches.values.forEach { validateForm(it, Form.FIXED, partial) }
            }
        }

        override fun merge(
            base: Any,
            overlay: Any,
        ): Any {
            if (overlay !is Map<*, *>) return overlay
            val replacement = overlay.objectValue()
            val keys = if ("day" in replacement || "night" in replacement) listOf("day", "night") else listOf("wide", "narrow")
            val inherited = if (base is Map<*, *>) base.objectValue() else keys.associateWith { base }
            // A scalar applies to all branches; changing variant axes replaces the complete form.
            if (inherited.keys.any { it !in keys }) return overlay
            return immutableObject(
                inherited.toMutableMap().apply {
                    replacement.forEach { (key, value) -> this[key] = this[key]?.let { merge(it, value) } ?: value }
                },
            )
        }
    }

    private fun obj(vararg fields: Pair<String, Node>) = ObjectNode(linkedMapOf(*fields))

    private fun number(
        min: Double = 0.0,
        max: Double = Float.MAX_VALUE.toDouble(),
        form: Form = Form.FIXED,
    ) = Leaf(form) { require(it is Number && it.toDouble().isFinite() && it.toDouble() in min..max) { "Invalid numeric token" } }

    private fun positive(
        form: Form = Form.FIXED,
        max: Double = Float.MAX_VALUE.toDouble(),
    ) = Leaf(form) { require(it is Number && it.toDouble().isFinite() && it.toDouble() > 0 && it.toDouble() <= max) }

    private fun integer(
        min: Long,
        max: Long,
        form: Form = Form.FIXED,
    ) = Leaf(form) { require(it is Long && it in min..max) { "Expected bounded JSON integer" } }

    private fun exact(value: Double) = number(value, value)

    private fun choice(
        vararg values: String,
        form: Form = Form.FIXED,
    ) = Leaf(form) { require(it is String && it in values) { "Invalid enum" } }

    private val boolean = Leaf { require(it is Boolean) }

    private fun color(requiredTheme: Boolean = true) =
        Leaf(if (requiredTheme) Form.THEMED else Form.FIXED, requiredTheme) {
            require(parseColor(it as? String ?: error("Expected color")) ushr 24 == 255) { "Expected opaque color" }
        }

    private val responsive = Form.RESPONSIVE

    val geometry =
        obj(
            "wideThreshold" to positive(),
            "touchTargetMin" to number(76.0),
            "touchTargetPrimary" to number(76.0, form = responsive),
            "mainCategoryTileSide" to number(128.0, form = responsive),
            "mainCategoryIconSide" to positive(responsive),
            "targetSpacingMin" to number(24.0),
            "screenEdgeMargin" to number(24.0),
            "topBarHeight" to number(80.0, form = responsive),
            "navTarget" to number(80.0, form = responsive),
            "bottomBarHeight" to number(76.0, form = responsive),
            "listRowHeight" to number(76.0, form = responsive),
            "listRowHeightTwoTier" to number(176.0, form = responsive),
            "listRowGap" to number(form = responsive),
            "playbackButtonHeight" to number(76.0, form = responsive),
            "ratingTarget" to number(76.0, form = responsive),
            "ratingTargetNarrow" to number(76.0, form = responsive),
            "ratingButtonWidth" to number(76.0, form = responsive),
            "ratingButtonHeight" to number(76.0, form = responsive),
            "aiStatusInset" to exact(16.0),
            "progressBarHeight" to positive(responsive),
            "cornerRadius" to number(form = responsive),
            "nowPlayingBorder" to number(2.0, form = responsive),
            "countdownRingStroke" to positive(responsive),
            "countdownRingInset" to number(form = responsive),
            "iconButtonIconInset" to exact(10.0),
            "hierarchyRowIconInset" to exact(10.0),
            "mixedButtonIconInset" to exact(10.0),
            "mixedButtonTextGap" to number(form = responsive),
        )

    val skin =
        obj(
            "geometry" to geometry,
            "typography" to
                obj(
                    "textTitleList" to number(36.0, form = responsive),
                    "textTitlePlayback" to number(60.0, 60.0, responsive),
                    "textSecondary" to number(20.0, form = responsive),
                    "textStatusLine" to exact(20.0),
                    "textInMotionFloor" to number(36.0),
                    "mixedButtonMinFontSize" to number(36.0, form = responsive),
                    "mixedButtonMaxFontSize" to number(36.0, form = responsive),
                    "topBarFontFamily" to choice("sans-serif", "serif", "monospace", form = Form.THEMED),
                    "topBarFontWeight" to integer(300, 500, Form.THEMED),
                ),
            "colors" to
                obj(
                    "screenBackground" to color(),
                    "rowSurface" to color(),
                    "rowSurfaceNowPlaying" to color(),
                    "nowPlayingBorderColor" to color(),
                    "controlPlate" to color(),
                    "textPrimaryColor" to color(),
                    "fragmentTitleColor" to color(),
                    "textSecondaryColor" to color(),
                    "textSuppressedColor" to color(),
                    "accentActive" to color(),
                    "accentCircle" to color(),
                    "iconDisabled" to color(),
                    "iconInactive" to color(),
                    "focusRingColor" to color(),
                    "positive" to color(),
                    "error" to color(),
                    "circleFillActiveAlpha" to number(0.0, 1.0, Form.THEMED),
                    "circleFillPartialAlpha" to number(0.0, 1.0, Form.THEMED),
                ),
            "shape" to obj("panelAlpha" to integer(0, 255), "panelStrokeDp" to number()),
            "behavior" to
                obj(
                    "enableStatusLines" to boolean,
                    "statusLineTextSp" to exact(20.0),
                    "artworkAlpha" to integer(0, 255),
                    "azRgbSubtract" to integer(0, 255),
                    "azDisabledAlpha" to integer(0, 255),
                    "idleTimeoutSeconds" to integer(1, 86400),
                    "systemBrightnessWorkaroundEnabled" to boolean,
                    "dimBacklightPercent" to number(0.0, 100.0, Form.THEMED),
                    "hierarchyRootUpBehavior" to choice("disabled", "home"),
                    "rowsPerPageMax" to integer(2, 5),
                    "rowsPerPageMin" to integer(1, 5),
                    "maxStringLength" to integer(1, 120),
                    "pressFeedbackMaxMs" to integer(1, 100),
                    "skipLatencyMaxMs" to integer(1, 200),
                    "undoWindowSeconds" to Leaf { require(it is Long && it in setOf(0L, 3L, 4L, 5L)) },
                ),
        )

    private val alphaMap =
        Leaf {
            require(it is List<*> && it.size == 7 && it.all { item -> item is String && item.matches(Regex("[0-9A-Fa-f]{2}")) })
        }
    private val excludedGenres =
        Leaf {
            require(it is List<*>)
            val ids =
                it.map { item ->
                    require(item is String)
                    val id = item.toLongOrNull()
                    require(id != null && id != 0L && id.toString() == item)
                    id
                }
            require(ids.toSet().size == ids.size)
        }
    private val diagonals =
        Leaf {
            require(it is List<*> && it.size == 16)
            val values =
                it.map { item ->
                    require(item is Number && item.toDouble().isFinite() && item.toDouble() > 0)
                    item.toDouble()
                }
            require(values.first() == 6.8 && values.last() == 15.1 && values.zipWithNext().all { (a, b) -> a < b })
        }
    private val decay = Leaf { require(it is Number && it.toDouble() > 0 && it.toDouble() < 1) }
    private val corners =
        obj(
            "topRight" to color(false),
            "bottomRight" to color(false),
            "bottomLeft" to color(false),
            "topLeft" to color(false),
        )
    private val weights =
        ObjectNode(
            listOf(
                "arousalDistance",
                "valenceDistance",
                "pteDistance",
                "trackLikeDeficit",
                "trackDislikePenalty",
                "directoryLikeDeficit",
                "directoryDislikePenalty",
                "albumLikeDeficit",
                "albumDislikePenalty",
                "artistLikeDeficit",
                "artistDislikePenalty",
                "genreLikeDeficit",
                "genreDislikePenalty",
                "playHistoryPenalty",
            ).associateWith { number(0.0, 100.0) },
        )

    fun globals(configurableMode: Boolean) =
        ObjectNode(
            linkedMapOf<String, Node>(
                "schemaVersion" to integer(ProjectConfiguration.SCHEMA_VERSION.toLong(), ProjectConfiguration.SCHEMA_VERSION.toLong()),
                "indexer" to
                    obj(
                        "retentionGenerations" to integer(1, 4294967295L),
                        "pipelineDepth" to integer(1, 8),
                    ),
                "player" to
                    obj(
                        "highlightFragments" to boolean,
                        "recentHistoryGenerations" to integer(1, 10000),
                        "bookmarkMinSpacingSeconds" to integer(1, Int.MAX_VALUE.toLong()),
                        "rewindPreviousThresholdSeconds" to obj("music" to integer(1, 60), "audiobook" to integer(1, 60)),
                        "screenDiagonalChoicesInches" to diagonals,
                        "excludedGenreIds" to excludedGenres,
                    ),
                "ai" to
                    obj(
                        "heatmapValenceCells" to integer(1, 128),
                        "heatmapArousalCells" to integer(1, 128),
                        "heatmapColors" to obj("day" to corners, "night" to corners),
                        "heatmapAlphaMap" to alphaMap,
                        "selectionModel" to
                            obj(
                                "temperature" to positive(),
                                "axisMinimumSpan" to positive(max = 2.0),
                                "pteMinimumGroupSize" to integer(1, 10000),
                                "directoryDecay" to decay,
                                "historyDecay" to decay,
                                "weights" to weights,
                            ),
                    ),
                "activeSkin" to Leaf { require(it is String && it.isNotBlank()) },
            ).apply { if (configurableMode) put("applicationMode", choice("aaos", "automotive", "phone")) },
        )

    fun validateSemantics(skin: ConfigObject) {
        for (wide in listOf(false, true)) {
            for (night in listOf(false, true)) {
                fun value(path: String) = resolveValue(skin, path, wide, night)

                fun number(path: String) = (value(path) as Number).toDouble()
                val minimum = number("geometry.touchTargetMin")
                listOf(
                    "touchTargetPrimary",
                    "topBarHeight",
                    "navTarget",
                    "bottomBarHeight",
                    "listRowHeight",
                    "playbackButtonHeight",
                    "ratingTarget",
                    "ratingTargetNarrow",
                    "ratingButtonWidth",
                    "ratingButtonHeight",
                ).forEach {
                    require(number("geometry.$it") >= minimum) { "Target below shared minimum" }
                }
                require(number("geometry.listRowHeight") >= number("geometry.ratingTarget"))
                require(
                    number("geometry.listRowHeightTwoTier") >=
                        2 * number("geometry.ratingTargetNarrow") + number("geometry.targetSpacingMin"),
                )
                require(number("geometry.topBarHeight") >= number("geometry.navTarget"))
                val tile = number("geometry.mainCategoryTileSide")
                require(tile >= minimum)
                val icon = number("geometry.mainCategoryIconSide")
                require(icon <= tile - 2 * number("geometry.iconButtonIconInset")) { "Main icon exceeds tile" }
                if (tile == 240.0) require(icon == 160.0)
                require(number("typography.mixedButtonMaxFontSize") >= number("typography.mixedButtonMinFontSize"))
                require(number("behavior.rowsPerPageMin") <= number("behavior.rowsPerPageMax"))
                validatePaletteContrast(::value, night)
            }
        }
    }
}

internal fun resolveValue(
    root: ConfigObject,
    path: String,
    wide: Boolean,
    night: Boolean,
): Any {
    var result: Any = root
    for (key in path.split('.')) result = result.objectValue().getValue(key)
    while (result is Map<*, *>) {
        val branches = result.objectValue()
        result =
            when {
                "day" in branches && "night" in branches -> branches.getValue(if (night) "night" else "day")
                "wide" in branches && "narrow" in branches -> branches.getValue(if (wide) "wide" else "narrow")
                else -> return result
            }
    }
    return result
}

/** Same accepted names and #RRGGBB/#AARRGGBB syntax as Android Color.parseColor. */
fun parseColor(text: String): Int {
    if (text.startsWith("#")) {
        require(text.length == 7 || text.length == 9)
        require(text.drop(1).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
        val value = text.drop(1).toLong(16)
        return (if (text.length == 7) value or 0xff000000L else value).toInt()
    }
    return when (text.lowercase(java.util.Locale.ROOT)) {
        "black" -> 0xff000000.toInt()
        "darkgray", "darkgrey" -> 0xff444444.toInt()
        "gray", "grey" -> 0xff888888.toInt()
        "lightgray", "lightgrey" -> 0xffcccccc.toInt()
        "white" -> 0xffffffff.toInt()
        "red" -> 0xffff0000.toInt()
        "green", "lime" -> 0xff00ff00.toInt()
        "blue" -> 0xff0000ff.toInt()
        "yellow" -> 0xffffff00.toInt()
        "cyan", "aqua" -> 0xff00ffff.toInt()
        "magenta", "fuchsia" -> 0xffff00ff.toInt()
        "maroon" -> 0xff800000.toInt()
        "navy" -> 0xff000080.toInt()
        "olive" -> 0xff808000.toInt()
        "purple" -> 0xff800080.toInt()
        "silver" -> 0xffc0c0c0.toInt()
        "teal" -> 0xff008080.toInt()
        "transparent" -> 0
        else -> error("Invalid Android color")
    }
}
