package link.roadio.riff.ui

data class UiPalette(
    val background: Int,
    val selectBackground: Int,
    val surface: Int,
    val focused: Int,
    val selected: Int,
    val outline: Int,
    val primary: Int,
    val onPrimary: Int,
    val text: Int,
    val muted: Int,
    val positive: Int,
    val error: Int,
    val focusRing: Int = outline,
    val disabledIcon: Int = muted,
    val inactiveIcon: Int = muted,
    val suppressed: Int = muted,
    val accentCircle: Int = primary,
    val circleActiveAlpha: Float = 0.30f,
    val circlePartialAlpha: Float = 0.13f,
) {
    companion object {
        fun night(config: UiConfig) = from(config.themed(true))

        fun day(config: UiConfig) = from(config.themed(false))

        private fun from(config: UiConfig) =
            UiPalette(
                background = config.color("colors.screenBackground"),
                selectBackground = config.color("colors.screenBackground"),
                surface = config.color("colors.rowSurface"),
                focused = config.color("colors.controlPlate"),
                selected = config.color("colors.rowSurfaceNowPlaying"),
                outline = config.color("colors.nowPlayingBorderColor"),
                primary = config.color("colors.accentActive"),
                onPrimary = config.color("colors.textPrimaryColor"),
                text = config.color("colors.textPrimaryColor"),
                muted = config.color("colors.textSecondaryColor"),
                positive = config.color("colors.positive"),
                error = config.color("colors.error"),
                focusRing = config.color("colors.focusRingColor"),
                disabledIcon = config.color("colors.iconDisabled"),
                inactiveIcon = config.color("colors.iconInactive"),
                suppressed = config.color("colors.textSuppressedColor"),
                accentCircle = config.color("colors.accentCircle"),
                circleActiveAlpha = config.float("colors.circleFillActiveAlpha"),
                circlePartialAlpha = config.float("colors.circleFillPartialAlpha"),
            )
    }
}

// Authority: https://wiki.271.pics/Riff/LATEST/icon-system
// AI-INTENT: Semantic icon names are resolved only through UiDrawableRegistry.
fun iconDrawableName(name: String): String =
    (if (name.startsWith("ic_")) name else "ic_$name").also { drawable ->
        require(drawable in ICON_DRAWABLES) { "Unknown Riff icon: $name" }
    }

private val ICON_DRAWABLES =
    AzGroup.entries.map { "ic_" + it.icon }.toSet() +
        setOf(
            "ic_ai",
            "ic_az_other", // Retained corpus asset outside the selectable search groups.
            "ic_album",
            "ic_artist",
            "ic_genre",
            "ic_playlist",
            "ic_folder",
            "ic_audiobook",
            "ic_sort_az",
            "ic_flip",
            "ic_settings",
            "ic_home",
            "ic_like",
            "ic_like_filled",
            "ic_dislike",
            "ic_dislike_filled",
            "ic_ban",
            "ic_ban_filled",
            "ic_bookmark_add",
            "ic_shuffle",
            "ic_rew",
            "ic_play",
            "ic_pause",
            "ic_fwd",
            "ic_repeat",
            "ic_plus",
            "ic_minus",
            "ic_no",
            "ic_no_cross",
            "ic_dots",
            "ic_ok",
            "ic_cancel",
            "ic_arrow_up",
            "ic_arrow_up_dot",
            "ic_arrowhead_up",
            "ic_arrowhead_down",
            "ic_theme_day",
            "ic_theme_night",
            "ic_theme_auto",
            "ic_digit_1",
            "ic_digit_2",
            "ic_digit_3",
            "ic_scale_0",
            "ic_scale_1",
            "ic_scale_2",
            "ic_scale_3",
            "ic_scale_4",
            "ic_scale_5",
            "ic_scale_6",
            "ic_scale_7",
            "ic_scale_8",
            "ic_scale_9",
            "ic_scale_10_6",
            "ic_scale_11",
            "ic_scale_auto",
            "ic_scale_banknote",
            "ic_scale_bidirectional",
            "ic_scale_card",
            "ic_scale_card_ruled",
            "ic_scale_coins",
            "ic_scale_diagonal",
            "ic_scale_eur_5",
            "ic_scale_eur_10",
            "ic_scale_eur_20",
            "ic_scale_eur_50",
            "ic_scale_horizontal",
            "ic_scale_iso_card",
            "ic_tempo_fast",
            "ic_tempo_normal",
            "ic_tempo_slow",
            "ic_yes",
        )
