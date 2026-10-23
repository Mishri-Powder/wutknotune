package link.roadio.riff.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import link.roadio.riff.R

/** Static semantic-name boundary for the adopted APK drawable resources. */
internal object UiDrawableRegistry {
    private val resources =
        mapOf(
            "ic_ai" to R.drawable.ic_ai,
            "ic_album" to R.drawable.ic_album,
            "ic_arrow_up" to R.drawable.ic_arrow_up,
            "ic_arrow_up_dot" to R.drawable.ic_arrow_up_dot,
            "ic_arrowhead_down" to R.drawable.ic_arrowhead_down,
            "ic_arrowhead_up" to R.drawable.ic_arrowhead_up,
            "ic_artist" to R.drawable.ic_artist,
            "ic_audiobook" to R.drawable.ic_audiobook,
            "ic_az_cyrillic_ag" to R.drawable.ic_az_cyrillic_ag,
            "ic_az_cyrillic_dz" to R.drawable.ic_az_cyrillic_dz,
            "ic_az_cyrillic_fch" to R.drawable.ic_az_cyrillic_fch,
            "ic_az_cyrillic_il" to R.drawable.ic_az_cyrillic_il,
            "ic_az_cyrillic_mp" to R.drawable.ic_az_cyrillic_mp,
            "ic_az_cyrillic_ru" to R.drawable.ic_az_cyrillic_ru,
            "ic_az_cyrillic_shy" to R.drawable.ic_az_cyrillic_shy,
            "ic_az_cyrillic_soft_ya" to R.drawable.ic_az_cyrillic_soft_ya,
            "ic_az_digit_01" to R.drawable.ic_az_digit_01,
            "ic_az_digit_23" to R.drawable.ic_az_digit_23,
            "ic_az_digit_45" to R.drawable.ic_az_digit_45,
            "ic_az_digit_67" to R.drawable.ic_az_digit_67,
            "ic_az_digit_89" to R.drawable.ic_az_digit_89,
            "ic_az_latin_ac" to R.drawable.ic_az_latin_ac,
            "ic_az_latin_df" to R.drawable.ic_az_latin_df,
            "ic_az_latin_gi" to R.drawable.ic_az_latin_gi,
            "ic_az_latin_jl" to R.drawable.ic_az_latin_jl,
            "ic_az_latin_mo" to R.drawable.ic_az_latin_mo,
            "ic_az_latin_ps" to R.drawable.ic_az_latin_ps,
            "ic_az_latin_tv" to R.drawable.ic_az_latin_tv,
            "ic_az_latin_wz" to R.drawable.ic_az_latin_wz,
            "ic_az_other" to R.drawable.ic_az_other,
            "ic_az_symbol" to R.drawable.ic_az_symbol,
            "ic_ban" to R.drawable.ic_ban,
            "ic_ban_filled" to R.drawable.ic_ban_filled,
            "ic_bookmark_add" to R.drawable.ic_bookmark_add,
            "ic_cancel" to R.drawable.ic_cancel,
            "ic_default_artwork_day" to R.drawable.ic_default_artwork_day,
            "ic_default_artwork_night" to R.drawable.ic_default_artwork_night,
            "ic_digit_1" to R.drawable.ic_digit_1,
            "ic_digit_2" to R.drawable.ic_digit_2,
            "ic_digit_3" to R.drawable.ic_digit_3,
            "ic_dislike" to R.drawable.ic_dislike,
            "ic_dislike_filled" to R.drawable.ic_dislike_filled,
            "ic_dots" to R.drawable.ic_dots,
            "ic_flip" to R.drawable.ic_flip,
            "ic_folder" to R.drawable.ic_folder,
            "ic_fwd" to R.drawable.ic_fwd,
            "ic_genre" to R.drawable.ic_genre,
            "ic_home" to R.drawable.ic_home,
            "ic_like" to R.drawable.ic_like,
            "ic_like_filled" to R.drawable.ic_like_filled,
            "ic_minus" to R.drawable.ic_minus,
            "ic_no" to R.drawable.ic_no,
            "ic_no_cross" to R.drawable.ic_no_cross,
            "ic_ok" to R.drawable.ic_ok,
            "ic_pause" to R.drawable.ic_pause,
            "ic_play" to R.drawable.ic_play,
            "ic_playlist" to R.drawable.ic_playlist,
            "ic_plus" to R.drawable.ic_plus,
            "ic_repeat" to R.drawable.ic_repeat,
            "ic_rew" to R.drawable.ic_rew,
            "ic_scale_0" to R.drawable.ic_scale_0,
            "ic_scale_1" to R.drawable.ic_scale_1,
            "ic_scale_10_6" to R.drawable.ic_scale_10_6,
            "ic_scale_11" to R.drawable.ic_scale_11,
            "ic_scale_2" to R.drawable.ic_scale_2,
            "ic_scale_3" to R.drawable.ic_scale_3,
            "ic_scale_4" to R.drawable.ic_scale_4,
            "ic_scale_5" to R.drawable.ic_scale_5,
            "ic_scale_6" to R.drawable.ic_scale_6,
            "ic_scale_7" to R.drawable.ic_scale_7,
            "ic_scale_8" to R.drawable.ic_scale_8,
            "ic_scale_9" to R.drawable.ic_scale_9,
            "ic_scale_auto" to R.drawable.ic_scale_auto,
            "ic_scale_banknote" to R.drawable.ic_scale_banknote,
            "ic_scale_bidirectional" to R.drawable.ic_scale_bidirectional,
            "ic_scale_card" to R.drawable.ic_scale_card,
            "ic_scale_card_ruled" to R.drawable.ic_scale_card_ruled,
            "ic_scale_coins" to R.drawable.ic_scale_coins,
            "ic_scale_diagonal" to R.drawable.ic_scale_diagonal,
            "ic_scale_eur_10" to R.drawable.ic_scale_eur_10,
            "ic_scale_eur_20" to R.drawable.ic_scale_eur_20,
            "ic_scale_eur_5" to R.drawable.ic_scale_eur_5,
            "ic_scale_eur_50" to R.drawable.ic_scale_eur_50,
            "ic_scale_horizontal" to R.drawable.ic_scale_horizontal,
            "ic_scale_iso_card" to R.drawable.ic_scale_iso_card,
            "ic_settings" to R.drawable.ic_settings,
            "ic_shuffle" to R.drawable.ic_shuffle,
            "ic_smpte_bars" to R.drawable.ic_smpte_bars,
            "ic_sort_az" to R.drawable.ic_sort_az,
            "ic_tempo_fast" to R.drawable.ic_tempo_fast,
            "ic_tempo_normal" to R.drawable.ic_tempo_normal,
            "ic_tempo_slow" to R.drawable.ic_tempo_slow,
            "ic_theme_auto" to R.drawable.ic_theme_auto,
            "ic_theme_day" to R.drawable.ic_theme_day,
            "ic_theme_night" to R.drawable.ic_theme_night,
            "ic_yes" to R.drawable.ic_yes,
        )

    val names: Set<String> = resources.keys

    @DrawableRes
    fun resourceId(name: String): Int = resources[name] ?: error("Unknown Riff drawable: $name")

    fun drawable(
        context: Context,
        name: String,
    ): Drawable =
        requireNotNull(context.getDrawable(resourceId(name))) {
            "Missing Riff drawable resource: $name"
        }.mutate()
}
