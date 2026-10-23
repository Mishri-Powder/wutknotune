package link.roadio.riff.player.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSettingsControllerTest {
    @Test
    fun durableMoreResultSurvivesRestrictionCancellationWithoutReopeningDraft() {
        val controller = PlayerSettingsController()
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.OpenMore)
        controller.execute(PlayerSettingsCommand.SetDimBacklight(PlayerDimBacklightMode.OFF))
        val saved = requireNotNull(controller.snapshot().moreDraft)
        controller.execute(PlayerSettingsCommand.Cancel)
        val completed = controller.completePersistedSave(saved, more = true)
        assertEquals(PlayerDimBacklightMode.OFF, completed.committed.dimBacklight)
        assertEquals(null, completed.draft)
        assertEquals(null, completed.moreDraft)
    }

    @Test
    fun entryStrategiesAreCodeSelectableWithoutChangingSessionPermissions() {
        val base = SettingsAccessPolicy.PHONE.copy(motion = MotionRestriction.UNKNOWN)
        assertEquals(SettingsEntry.LONG_PRESS, base.entry)
        assertEquals(SettingsEntry.PRESS, base.copy(entryStrategy = SettingsEntryStrategy.PRESS).entry)
        assertEquals(SettingsEntry.BLOCKED, base.copy(entryStrategy = SettingsEntryStrategy.MOTION).entry)
        assertEquals(SettingsEntry.RECOVERY_HOLD, base.copy(entryStrategy = SettingsEntryStrategy.MOTION_WITH_RECOVERY).entry)
        for (strategy in SettingsEntryStrategy.entries) {
            val policy = base.copy(entryStrategy = strategy)
            assertEquals(SettingsAccess.ALLOWED, policy.access)
            assertTrue(policy.playlistMutationsAllowed)
        }
    }

    @Test
    fun currentEntryBindingsCoverModesMotionSourcesAndRecovery() {
        for (motion in MotionRestriction.entries) {
            for (source in RestrictionSource.entries) {
                assertEquals(SettingsEntry.LONG_PRESS, SettingsAccessPolicy(ApplicationMode.PHONE, motion, source).entry)
                val expectedAaos = if (motion == MotionRestriction.UNRESTRICTED) SettingsEntry.PRESS else SettingsEntry.BLOCKED
                assertEquals(expectedAaos, SettingsAccessPolicy(ApplicationMode.AAOS, motion, source, true).entry)
                val expectedAutomotive =
                    when {
                        motion == MotionRestriction.UNRESTRICTED -> SettingsEntry.PRESS
                        motion == MotionRestriction.UNKNOWN && source == RestrictionSource.UNAVAILABLE -> SettingsEntry.RECOVERY_HOLD
                        else -> SettingsEntry.BLOCKED
                    }
                for (authorized in listOf(false, true)) {
                    assertEquals(expectedAutomotive, SettingsAccessPolicy(ApplicationMode.AUTOMOTIVE, motion, source, authorized).entry)
                }
            }
        }
    }

    @Test
    fun alternateEntryStrategyCannotBypassStrictAaosOrPositiveAutomotiveRestriction() {
        for (strategy in SettingsEntryStrategy.entries) {
            for (motion in listOf(MotionRestriction.RESTRICTED, MotionRestriction.UNKNOWN)) {
                assertEquals(
                    SettingsEntry.BLOCKED,
                    SettingsAccessPolicy(ApplicationMode.AAOS, motion, RestrictionSource.UNAVAILABLE, true, strategy).entry,
                )
            }
            assertEquals(
                SettingsEntry.BLOCKED,
                SettingsAccessPolicy(
                    ApplicationMode.AUTOMOTIVE,
                    MotionRestriction.RESTRICTED,
                    RestrictionSource.UNAVAILABLE,
                    true,
                    strategy,
                ).entry,
            )
        }
    }

    @Test
    fun dimDefaultsToAutoAndMoreCommitSurvivesPrimaryCancelAndProfileSelection() {
        val controller = PlayerSettingsController()
        assertEquals(PlayerDimBacklightMode.AUTO, controller.snapshot().committed.dimBacklight)
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetTheme(PlayerColorScheme.NIGHT))
        controller.execute(PlayerSettingsCommand.OpenMore)
        controller.execute(PlayerSettingsCommand.SetDimBacklight(PlayerDimBacklightMode.OFF))
        assertEquals(PlayerDimBacklightMode.AUTO, controller.snapshot().committed.dimBacklight)
        controller.execute(PlayerSettingsCommand.CancelMore)
        controller.execute(PlayerSettingsCommand.OpenMore)
        assertEquals(PlayerDimBacklightMode.AUTO, controller.snapshot().moreDraft?.dimBacklight)
        controller.execute(PlayerSettingsCommand.SetDimBacklight(PlayerDimBacklightMode.DAY))
        controller.execute(PlayerSettingsCommand.SaveMore)
        assertEquals(PlayerColorScheme.NIGHT, controller.snapshot().draft?.colorScheme)
        controller.execute(PlayerSettingsCommand.Cancel)
        assertEquals(PlayerColorScheme.SYSTEM, controller.snapshot().committed.colorScheme)
        assertEquals(PlayerDimBacklightMode.DAY, controller.snapshot().committed.dimBacklight)
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetProfileSettings(PlayerColorScheme.NIGHT, false, true, true))
        controller.execute(PlayerSettingsCommand.Save)
        assertEquals(PlayerDimBacklightMode.DAY, controller.snapshot().committed.dimBacklight)
    }

    @Test
    fun leadingArticlesDefaultToYesAndObeyDraftSaveCancelAndProfileSelection() {
        val controller = PlayerSettingsController()
        assertTrue(controller.snapshot().committed.ignoreLeadingArticles)
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetIgnoreLeadingArticles(false))
        assertTrue(controller.snapshot().committed.ignoreLeadingArticles)
        assertFalse(requireNotNull(controller.snapshot().draft).ignoreLeadingArticles)
        controller.execute(PlayerSettingsCommand.Cancel)
        assertTrue(controller.snapshot().committed.ignoreLeadingArticles)
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetProfileSettings(PlayerColorScheme.SYSTEM, false, true, false))
        assertFalse(requireNotNull(controller.snapshot().draft).ignoreLeadingArticles)
        controller.execute(PlayerSettingsCommand.Save)
        assertFalse(controller.snapshot().committed.ignoreLeadingArticles)
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetIgnoreLeadingArticles(true))
        controller.execute(PlayerSettingsCommand.Save)
        assertTrue(controller.snapshot().committed.ignoreLeadingArticles)
    }

    @Test
    fun rebuildChoiceTogglesAndCancelDiscardsIt() {
        val controller = PlayerSettingsController()
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.OpenMore)
        assertEquals(emptySet<PlayerDatabaseRebuild>(), controller.snapshot().pendingRebuild)
        controller.execute(PlayerSettingsCommand.SetRebuild(PlayerDatabaseRebuild.AU))
        controller.execute(PlayerSettingsCommand.SetRebuild(PlayerDatabaseRebuild.IA))
        assertEquals(setOf(PlayerDatabaseRebuild.AU, PlayerDatabaseRebuild.IA), controller.snapshot().pendingRebuild)
        controller.execute(PlayerSettingsCommand.SetRebuild(PlayerDatabaseRebuild.AU))
        assertEquals(setOf(PlayerDatabaseRebuild.IA), controller.snapshot().pendingRebuild)
        controller.execute(PlayerSettingsCommand.SetRebuild(PlayerDatabaseRebuild.IA))
        assertTrue(controller.snapshot().pendingRebuild.isEmpty())
        controller.execute(PlayerSettingsCommand.SetRebuild(PlayerDatabaseRebuild.IA))
        controller.execute(PlayerSettingsCommand.CancelMore)
        controller.execute(PlayerSettingsCommand.OpenMore)
        assertTrue(controller.snapshot().pendingRebuild.isEmpty())
    }

    @Test fun `settings edits remain draft until save and cancel discards`() {
        val controller = PlayerSettingsController()
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetLongPress(true))

        assertFalse(controller.snapshot().committed.longPressStartsPlayback)
        assertTrue(requireNotNull(controller.snapshot().draft).longPressStartsPlayback)

        controller.execute(PlayerSettingsCommand.Cancel)
        assertNull(controller.snapshot().draft)
        assertFalse(controller.snapshot().committed.longPressStartsPlayback)

        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetLongPress(true))
        controller.execute(PlayerSettingsCommand.Save)
        assertTrue(controller.snapshot().committed.longPressStartsPlayback)
    }

    @Test fun `calibration is the only way to select card`() {
        val controller = PlayerSettingsController()
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.OpenMore)
        controller.execute(PlayerSettingsCommand.AcceptCalibration(190f, 188f))

        val draft = requireNotNull(controller.snapshot().moreDraft)
        assertEquals(PlayerDiagonalChoice.CARD, draft.diagonal)
        assertEquals(190f, draft.calibrationPpiX)
        assertEquals(188f, draft.calibrationPpiY)
    }

    @Test fun `screen diagonal returns only derived dpi to the settings draft`() {
        val controller = PlayerSettingsController()
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.OpenMore)
        controller.execute(PlayerSettingsCommand.AcceptDiagonal(242f))

        val draft = requireNotNull(controller.snapshot().moreDraft)
        assertEquals(PlayerDiagonalChoice.DIAGONAL, draft.diagonal)
        assertEquals(242f, draft.calibrationPpiX)
        assertNull(draft.calibrationPpiY)
        assertEquals(PlayerDiagonalChoice.AUTO, controller.snapshot().committed.diagonal)

        controller.execute(PlayerSettingsCommand.SaveMore)
        assertEquals(PlayerDiagonalChoice.DIAGONAL, controller.snapshot().committed.diagonal)
        assertEquals(242f, controller.snapshot().committed.calibrationPpiX)
    }

    @Test fun `mode and motion policy blocks restricted entry`() {
        val policy =
            SettingsAccessPolicy(
                ApplicationMode.AAOS,
                MotionRestriction.UNKNOWN,
                RestrictionSource.AAOS_UXR,
            )
        val controller = PlayerSettingsController(accessPolicy = { policy })

        val error =
            runCatching { controller.execute(PlayerSettingsCommand.Open) }
                .exceptionOrNull() as PlayerSettingsCommandException
        assertEquals("SETTINGS_UNAVAILABLE", error.code)
        assertEquals(SettingsAccess.BLOCKED, controller.snapshot().access)
    }

    @Test fun `automotive unavailable-source recovery requires authorization`() {
        var authorized = false
        val controller =
            PlayerSettingsController {
                SettingsAccessPolicy(
                    ApplicationMode.AUTOMOTIVE,
                    MotionRestriction.UNKNOWN,
                    RestrictionSource.UNAVAILABLE,
                    recoveryAuthorized = authorized,
                )
            }

        assertEquals(SettingsAccess.RECOVERY_REQUIRED, controller.snapshot().access)
        authorized = true
        controller.execute(PlayerSettingsCommand.Open)
        assertTrue(controller.snapshot().open)
    }

    @Test fun `settings commit is rejected when motion changes after opening`() {
        var motion = MotionRestriction.UNRESTRICTED
        val controller =
            PlayerSettingsController {
                SettingsAccessPolicy(ApplicationMode.AAOS, motion, RestrictionSource.AAOS_UXR)
            }
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetLongPress(true))
        motion = MotionRestriction.RESTRICTED
        assertEquals(SettingsAccess.BLOCKED, controller.snapshot().access)
        val failure = runCatching { controller.execute(PlayerSettingsCommand.Save) }.exceptionOrNull()
        assertEquals("SETTINGS_UNAVAILABLE", (failure as PlayerSettingsCommandException).code)
        assertFalse(controller.snapshot().committed.longPressStartsPlayback)
        controller.execute(PlayerSettingsCommand.Cancel)
        assertNull(controller.snapshot().draft)
    }

    @Test fun `playlist edits obey mode and current motion independent of recovery entry`() {
        assertTrue(SettingsAccessPolicy.PHONE.playlistMutationsAllowed)
        val automotiveUnknown =
            SettingsAccessPolicy(
                ApplicationMode.AUTOMOTIVE,
                MotionRestriction.UNKNOWN,
                RestrictionSource.UNAVAILABLE,
                recoveryAuthorized = true,
            )
        assertEquals(SettingsAccess.ALLOWED, automotiveUnknown.access)
        assertFalse(automotiveUnknown.playlistMutationsAllowed)
        assertTrue(
            SettingsAccessPolicy(
                ApplicationMode.AUTOMOTIVE,
                MotionRestriction.UNRESTRICTED,
                RestrictionSource.PARKING_BRAKE,
            ).playlistMutationsAllowed,
        )
        assertFalse(
            SettingsAccessPolicy(
                ApplicationMode.AAOS,
                MotionRestriction.UNRESTRICTED,
                RestrictionSource.AAOS_UXR,
            ).playlistMutationsAllowed,
        )
    }

    @Test fun `settings and playlist policy covers every mode and motion`() {
        for (mode in ApplicationMode.entries) {
            for (motion in MotionRestriction.entries) {
                val source = if (mode == ApplicationMode.AAOS) RestrictionSource.AAOS_UXR else RestrictionSource.UNAVAILABLE
                val policy = SettingsAccessPolicy(mode, motion, source)
                val expectedAccess =
                    when (mode) {
                        ApplicationMode.PHONE -> {
                            SettingsAccess.ALLOWED
                        }

                        ApplicationMode.AAOS -> {
                            if (motion == MotionRestriction.UNRESTRICTED) {
                                SettingsAccess.ALLOWED
                            } else {
                                SettingsAccess.BLOCKED
                            }
                        }

                        ApplicationMode.AUTOMOTIVE -> {
                            when (motion) {
                                MotionRestriction.UNRESTRICTED -> SettingsAccess.ALLOWED
                                MotionRestriction.RESTRICTED -> SettingsAccess.BLOCKED
                                MotionRestriction.UNKNOWN -> SettingsAccess.RECOVERY_REQUIRED
                            }
                        }
                    }
                assertEquals("$mode/$motion", expectedAccess, policy.access)
                val expectedPlaylist =
                    when (mode) {
                        ApplicationMode.PHONE -> true
                        ApplicationMode.AUTOMOTIVE -> motion == MotionRestriction.UNRESTRICTED
                        ApplicationMode.AAOS -> false
                    }
                assertEquals("$mode/$motion playlist", expectedPlaylist, policy.playlistMutationsAllowed)
            }
        }
        assertEquals(
            SettingsAccess.BLOCKED,
            SettingsAccessPolicy(
                ApplicationMode.AUTOMOTIVE,
                MotionRestriction.RESTRICTED,
                RestrictionSource.PARKING_BRAKE,
                recoveryAuthorized = true,
            ).access,
        )
    }

    @Test fun `committed child storage move rebases an open parent draft`() {
        val controller = PlayerSettingsController()
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetLongPress(true))

        controller.execute(PlayerSettingsCommand.RebaseDataLocation(PlayerDataLocation.SD))

        assertEquals(PlayerDataLocation.SD, controller.snapshot().committed.dataLocation)
        assertEquals(PlayerDataLocation.SD, controller.snapshot().draft?.dataLocation)
        assertTrue(requireNotNull(controller.snapshot().draft).longPressStartsPlayback)
        assertFalse(controller.snapshot().committed.longPressStartsPlayback)

        controller.execute(PlayerSettingsCommand.Cancel)
        assertEquals(PlayerDataLocation.SD, controller.snapshot().committed.dataLocation)
        assertFalse(controller.snapshot().committed.longPressStartsPlayback)
    }

    @Test fun `auto search is enabled by default and remains profile draft until save`() {
        val controller = PlayerSettingsController()
        assertTrue(controller.snapshot().committed.autoSearch)

        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetAutoSearch(false))
        assertTrue(controller.snapshot().committed.autoSearch)
        assertFalse(requireNotNull(controller.snapshot().draft).autoSearch)

        controller.execute(PlayerSettingsCommand.Save)
        assertFalse(controller.snapshot().committed.autoSearch)
    }

    @Test fun `rebuild remains a one shot command`() {
        val controller = PlayerSettingsController()
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.OpenMore)
        controller.execute(PlayerSettingsCommand.SetRebuild(PlayerDatabaseRebuild.IA))

        assertEquals(setOf(PlayerDatabaseRebuild.IA), controller.snapshot().pendingRebuild)

        controller.execute(PlayerSettingsCommand.SaveMore)
        assertEquals(emptySet<PlayerDatabaseRebuild>(), controller.snapshot().pendingRebuild)
    }

    @Test
    fun `more save commits installation values without committing primary draft`() {
        val controller = PlayerSettingsController()
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetLongPress(true))
        controller.execute(PlayerSettingsCommand.OpenMore)
        controller.execute(PlayerSettingsCommand.SetReplayGainMode(PlayerReplayGainMode.F))
        controller.execute(PlayerSettingsCommand.SetReplayGainTarget(PlayerReplayGainTarget.LUFS_16))
        controller.execute(PlayerSettingsCommand.AcceptDiagonal(242f))

        controller.execute(PlayerSettingsCommand.SaveMore)

        assertFalse(controller.snapshot().committed.longPressStartsPlayback)
        assertTrue(requireNotNull(controller.snapshot().draft).longPressStartsPlayback)
        assertEquals(PlayerReplayGainMode.F, controller.snapshot().committed.replayGainMode)
        assertEquals(PlayerReplayGainTarget.LUFS_16, controller.snapshot().committed.replayGainTarget)
        assertEquals(PlayerDiagonalChoice.DIAGONAL, controller.snapshot().committed.diagonal)
        assertNull(controller.snapshot().moreDraft)

        controller.execute(PlayerSettingsCommand.Cancel)
        assertFalse(controller.snapshot().committed.longPressStartsPlayback)
        assertEquals(PlayerReplayGainMode.F, controller.snapshot().committed.replayGainMode)
        assertEquals(PlayerReplayGainTarget.LUFS_16, controller.snapshot().committed.replayGainTarget)
    }

    @Test
    fun `more cancel discards only installation draft`() {
        val controller = PlayerSettingsController()
        controller.execute(PlayerSettingsCommand.Open)
        controller.execute(PlayerSettingsCommand.SetAutoSearch(false))
        controller.execute(PlayerSettingsCommand.OpenMore)
        controller.execute(PlayerSettingsCommand.SetReplayGainMode(PlayerReplayGainMode.I))
        controller.execute(PlayerSettingsCommand.SetReplayGainTarget(PlayerReplayGainTarget.ZERO))

        controller.execute(PlayerSettingsCommand.CancelMore)

        assertFalse(requireNotNull(controller.snapshot().draft).autoSearch)
        assertEquals(PlayerReplayGainMode.F, controller.snapshot().committed.replayGainMode)
        assertEquals(PlayerReplayGainTarget.LUFS_23, controller.snapshot().committed.replayGainTarget)
        assertNull(controller.snapshot().moreDraft)
    }
}
