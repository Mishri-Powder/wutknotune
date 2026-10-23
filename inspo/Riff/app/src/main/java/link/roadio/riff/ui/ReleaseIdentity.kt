package link.roadio.riff.ui

import link.roadio.riff.BuildConfig
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ReleaseChannel(
    val visibleName: String,
) {
    DEBUG("Debug"),
    TEST("Test"),
    ALPHA("Alpha"),
    BETA("Beta"),
    RC("RC"),
    FINAL("Final"),
}

data class ReleaseIdentity(
    val channel: ReleaseChannel,
    val releaseDate: LocalDate,
) {
    val announcementLabel: String?
        get() =
            if (channel == ReleaseChannel.FINAL) {
                null
            } else {
                "${channel.visibleName}: ${releaseDate.format(OVERLAY_DATE_FORMAT)}"
            }

    fun requiresAgeWarning(deviceDate: LocalDate): Boolean =
        channel in AGE_WARNING_CHANNELS &&
            !deviceDate.isBefore(releaseDate.plusMonths(NON_FINAL_LIFETIME_MONTHS))

    val ageWarningTitle: String
        get() = "Old ${channel.visibleName} build"

    val ageWarningMessage: String
        get() = "This ${channel.visibleName} build is more than three months old. It may be outdated."

    companion object {
        fun fromBuildConfig(): ReleaseIdentity =
            ReleaseIdentity(
                channel = ReleaseChannel.valueOf(BuildConfig.RELEASE_CHANNEL),
                releaseDate = LocalDate.parse(BuildConfig.RELEASE_DATE),
            )
    }
}

internal fun shouldHandleBackInApp(screen: PlayerScreen): Boolean = screen != PlayerScreen.MAIN

internal class ReleaseAgeWarningSession {
    private var acknowledged = false

    fun shouldShow(
        identity: ReleaseIdentity,
        deviceDate: LocalDate,
    ): Boolean = !acknowledged && identity.requiresAgeWarning(deviceDate)

    fun acknowledge() {
        acknowledged = true
    }
}

internal object ProcessReleaseAgeWarningSession {
    val current = ReleaseAgeWarningSession()
}

private const val NON_FINAL_LIFETIME_MONTHS = 3L
private val AGE_WARNING_CHANNELS =
    setOf(ReleaseChannel.DEBUG, ReleaseChannel.TEST, ReleaseChannel.ALPHA, ReleaseChannel.BETA)
private val OVERLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd")
