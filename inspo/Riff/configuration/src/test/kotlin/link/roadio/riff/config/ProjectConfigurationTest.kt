package link.roadio.riff.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ProjectConfigurationTest {
    private val packaged = PackagedConfiguration.document

    @Test
    fun packagedDefaultIsCompleteAndOnlyShippedSkin() {
        assertEquals(setOf("default"), packaged.getValue("skins").objectValue().keys)
        val config = ProjectConfiguration.resolve(packaged)
        assertEquals("default", config.activeSkin)
        assertEquals("phone", config.applicationMode)
        assertEquals(128.0, config.number("indexer.retentionGenerations"), 0.0)
        assertEquals(3.0, config.number("indexer.pipelineDepth"), 0.0)
        assertFalse(config.isWide(799.9f))
        assertTrue(config.isWide(800f))
    }

    @Test
    fun playbackHeightIsIndependentOfListRowsAndEnforcesTheSharedMinimum() {
        val defaults = ProjectConfiguration.parse("{}")
        val custom = ProjectConfiguration.parse("""{"skins":{"default":{"geometry":{"playbackButtonHeight":140}}}}""")
        for (wide in listOf(false, true)) {
            assertEquals(120.0, defaults.number("geometry.playbackButtonHeight", wide), 0.0)
            assertEquals(140.0, custom.number("geometry.playbackButtonHeight", wide), 0.0)
            assertEquals(100.0, custom.number("geometry.listRowHeight", wide), 0.0)
        }
        listOf("75", "null", "true", "\"120\"", "{\"day\":120,\"night\":140}").forEach { value ->
            rejected { ProjectConfiguration.parse("""{"skins":{"default":{"geometry":{"playbackButtonHeight":$value}}}}""") }
        }
        val largerMinimum =
            """{"skins":{"default":{"geometry":{
                "touchTargetMin":80,"playbackButtonHeight":80,"ratingTargetNarrow":80,"listRowHeightTwoTier":184
            }}}}"""
        ProjectConfiguration.parse(largerMinimum)
        rejected { ProjectConfiguration.parse(largerMinimum.replace("\"playbackButtonHeight\":80", "\"playbackButtonHeight\":79")) }
    }

    @Test
    fun partialHuBrightnessFileDeepMergesAndDefaultsRemainDisabled() {
        assertEquals("ui_config.json", ProjectConfiguration.OVERRIDE_FILE_NAME)
        val defaults = ProjectConfiguration.parse("{}")
        assertEquals(false, defaults.value("behavior.systemBrightnessWorkaroundEnabled"))
        val enabled =
            ProjectConfiguration.parse(
                """{"skins":{"default":{"behavior":{"systemBrightnessWorkaroundEnabled":true}}}}""",
            )
        for (wide in listOf(false, true)) {
            for (night in listOf(false, true)) {
                assertEquals(true, enabled.value("behavior.systemBrightnessWorkaroundEnabled", wide, night))
                assertEquals(30.0, enabled.number("behavior.dimBacklightPercent", wide, night), 0.0)
                assertEquals(30.0, enabled.number("behavior.idleTimeoutSeconds", wide, night), 0.0)
                assertEquals(defaults.value("colors.screenBackground", wide, night), enabled.value("colors.screenBackground", wide, night))
            }
        }
        assertEquals("phone", enabled.applicationMode)
        assertEquals(3.0, enabled.number("indexer.pipelineDepth"), 0.0)
        assertEquals(defaults.value("player.excludedGenreIds"), enabled.value("player.excludedGenreIds"))
        listOf("null", "1", "\"true\"", "{\"day\":true,\"night\":false}").forEach { value ->
            rejected { ProjectConfiguration.parse("""{"skins":{"default":{"behavior":{"systemBrightnessWorkaroundEnabled":$value}}}}""") }
        }
    }

    @Test
    fun dimPercentBranchesMergeIndependentlyOfTheScalarWorkaroundFlag() {
        val phone = ProjectConfiguration.parse("{}")
        val hu =
            ProjectConfiguration.parse(
                """{"skins":{"default":{"behavior":{"systemBrightnessWorkaroundEnabled":true,"dimBacklightPercent":{"day":15,"night":1}}}}}""",
            )
        val oneTheme = ProjectConfiguration.parse("""{"skins":{"default":{"behavior":{"dimBacklightPercent":{"night":12.5}}}}}""")
        val scalar = ProjectConfiguration.parse("""{"skins":{"default":{"behavior":{"dimBacklightPercent":10}}}}""")
        for (wide in listOf(false, true)) {
            for (night in listOf(false, true)) {
                assertEquals(30.0, phone.number("behavior.dimBacklightPercent", wide, night), 0.0)
                assertEquals(false, phone.value("behavior.systemBrightnessWorkaroundEnabled", wide, night))
                assertEquals(if (night) 1.0 else 15.0, hu.number("behavior.dimBacklightPercent", wide, night), 0.0)
                assertEquals(true, hu.value("behavior.systemBrightnessWorkaroundEnabled", wide, night))
                assertEquals(if (night) 12.5 else 30.0, oneTheme.number("behavior.dimBacklightPercent", wide, night), 0.0)
                assertEquals(false, oneTheme.value("behavior.systemBrightnessWorkaroundEnabled", wide, night))
                assertEquals(10.0, scalar.number("behavior.dimBacklightPercent", wide, night), 0.0)
            }
        }
        listOf("-1", "100.01", "null", "true", "\"30\"", "{\"wide\":30,\"narrow\":30}", "{\"day\":-1}").forEach { value ->
            rejected { ProjectConfiguration.parse("""{"skins":{"default":{"behavior":{"dimBacklightPercent":$value}}}}""") }
        }
        rejected {
            ProjectConfiguration.parse(
                """{"skins":{"default":{"behavior":{"systemBrightnessWorkaroundEnabled":{"day":true,"night":false}}}}}""",
            )
        }
    }

    @Test
    fun applicationModeOverridesRemainAvailableWithPhoneDefault() {
        for (mode in listOf("aaos", "automotive", "phone")) {
            val selected = ProjectConfiguration.parse("""{"applicationMode":"$mode"}""")
            assertEquals(mode, selected.applicationMode)
        }
        assertEquals("phone", ProjectConfiguration.parse("{}").applicationMode)
    }

    @Test
    fun bookmarkSpacingIsGlobalPositiveIntegerAndImmutable() {
        val defaults = ProjectConfiguration.parse("{}")
        assertEquals(15.0, defaults.number("player.bookmarkMinSpacingSeconds"), 0.0)
        val custom = ProjectConfiguration.parse("""{"player":{"bookmarkMinSpacingSeconds":20}}""")
        for (wide in listOf(false, true)) {
            for (night in listOf(false, true)) {
                assertEquals(20.0, custom.number("player.bookmarkMinSpacingSeconds", wide, night), 0.0)
            }
        }
        assertEquals(15.0, defaults.number("player.bookmarkMinSpacingSeconds"), 0.0)
        listOf("0", "-1", "2147483648", "15.0", "\"15\"", "null", "{\"day\":15,\"night\":20}").forEach { value ->
            rejected { ProjectConfiguration.parse("""{"player":{"bookmarkMinSpacingSeconds":$value}}""") }
        }
        rejected { ProjectConfiguration.parse("""{"skins":{"default":{"behavior":{"bookmarkMinSpacingSeconds":20}}}}""") }
    }

    @Test
    fun pipelineDepthIsFileConfigurableAndBounded() {
        assertEquals(
            5.0,
            ProjectConfiguration
                .parse("""{"indexer":{"pipelineDepth":5}}""")
                .number("indexer.pipelineDepth"),
            0.0,
        )
        listOf("0", "9", "3.5", "\"3\"").forEach { value ->
            rejected { ProjectConfiguration.parse("""{"indexer":{"pipelineDepth":$value}}""") }
        }
    }

    @Test
    fun fragmentTintIsFileOnlyAndContrastValidated() {
        val defaults = ProjectConfiguration.parse("{}")
        assertEquals(false, defaults.value("player.highlightFragments"))
        val enabled = ProjectConfiguration.parse("""{"player":{"highlightFragments":true}}""")
        assertEquals(true, enabled.value("player.highlightFragments"))
        assertEquals("#12483D", enabled.value("colors.fragmentTitleColor"))
        assertEquals("#B4DCCB", enabled.value("colors.fragmentTitleColor", night = true))
        rejected { ProjectConfiguration.parse("""{"player":{"highlightFragments":"yes"}}""") }
        rejected { ProjectConfiguration.parse("""{"skins":{"default":{"colors":{"fragmentTitleColor":{"day":"#dae2ea"}}}}}""") }
    }

    @Test
    fun azRgbSubtractIsFileOnlyAndRejectsInvalidOrUnsafeOverrides() {
        val defaults = ProjectConfiguration.parse("{}")
        assertEquals(32.0, defaults.number("behavior.azRgbSubtract"), 0.0)
        listOf(0, 16, 32).forEach { amount ->
            val config = ProjectConfiguration.parse("""{"skins":{"default":{"behavior":{"azRgbSubtract":$amount}}}}""")
            for (wide in listOf(false, true)) {
                for (night in listOf(false, true)) {
                    assertEquals(amount.toDouble(), config.number("behavior.azRgbSubtract", wide, night), 0.0)
                }
            }
        }
        listOf("-1", "256", "32.0", "\"32\"", "true", "{\"day\":32,\"night\":16}").forEach { amount ->
            rejected { ProjectConfiguration.parse("""{"skins":{"default":{"behavior":{"azRgbSubtract":$amount}}}}""") }
        }
        listOf("azOverlayAlpha", "azBackgroundDimAlpha").forEach { retired ->
            rejected { ProjectConfiguration.parse("""{"skins":{"default":{"behavior":{"$retired":128}}}}""") }
        }
        val rejected =
            StartupConfiguration().load {
                """{"indexer":{"pipelineDepth":5},"skins":{"default":{"behavior":{"azRgbSubtract":255}}}}"""
            }
        assertEquals(ConfigurationProblem.INVALID, rejected.problem)
        assertEquals(32.0, rejected.snapshot.number("behavior.azRgbSubtract"), 0.0)
        assertEquals(3.0, rejected.snapshot.number("indexer.pipelineDepth"), 0.0)
    }

    @Test
    fun allFourLayersApplyInOrder() {
        val skins =
            packaged.getValue("skins").objectValue() +
                ("custom" to configObject("behavior" to configObject("idleTimeoutSeconds" to 40L, "azDisabledAlpha" to 80L)))
        val apk = immutableObject(packaged + ("skins" to immutableObject(skins)) + ("activeSkin" to "custom"))
        val user =
            ConfigJson(
                """{"skins":{"default":{"behavior":{"idleTimeoutSeconds":50,"azRgbSubtract":16}},
            "custom":{"behavior":{"azDisabledAlpha":70}}}}""",
            ).parse()
        val config = ProjectConfiguration.resolve(apk, user)
        assertEquals(50.0, config.number("behavior.idleTimeoutSeconds"), 0.0)
        assertEquals(16.0, config.number("behavior.azRgbSubtract"), 0.0)
        assertEquals(70.0, config.number("behavior.azDisabledAlpha"), 0.0)
    }

    @Test
    fun newSkinInheritsUserDefaultAndPackagedDefault() {
        val config =
            ProjectConfiguration.parse(
                """{"activeSkin":"custom","skins":{"default":{"behavior":{"idleTimeoutSeconds":42}},
            "custom":{"typography":{"topBarFontFamily":{"day":"serif","night":"monospace"}}}}}""",
            )
        assertEquals(42.0, config.number("behavior.idleTimeoutSeconds"), 0.0)
        assertEquals(240.0, config.number("geometry.mainCategoryTileSide"), 0.0)
        assertEquals("serif", config.value("typography.topBarFontFamily"))
        assertEquals("monospace", config.value("typography.topBarFontFamily", night = true))
    }

    @Test
    fun arraysReplaceEvenWhenEmptyAndGlobalObjectsMerge() {
        val config =
            ProjectConfiguration.parse(
                """{"player":{"excludedGenreIds":[]},"ai":{"selectionModel":{"weights":{"arousalDistance":2}}}}""",
            )
        assertEquals(emptyList<String>(), config.value("player.excludedGenreIds"))
        assertEquals(2.0, config.number("ai.selectionModel.weights.arousalDistance"), 0.0)
        assertEquals(1.0, config.number("ai.selectionModel.weights.valenceDistance"), 0.0)
    }

    @Test
    fun responsiveAndThemeBranchesInheritIndependently() {
        val config =
            ProjectConfiguration.parse(
                """{"skins":{"default":{"geometry":{"listRowHeight":{"wide":120}},
            "colors":{"screenBackground":{"day":"#ffffff"}}}}}""",
            )
        assertEquals(120.0, config.number("geometry.listRowHeight", wide = true), 0.0)
        assertEquals(100.0, config.number("geometry.listRowHeight", wide = false), 0.0)
        assertEquals("#ffffff", config.value("colors.screenBackground", night = false))
        assertEquals("#101419", config.value("colors.screenBackground", night = true))
    }

    @Test
    fun themedResponsiveFormHasOnlyThemeOutsideWidth() {
        val leaf = ConfigurationSchema.Leaf(ConfigurationSchema.Form.THEMED_RESPONSIVE) { require(it is Long) }
        val valid = ConfigJson("""{"day":{"wide":1,"narrow":2},"night":{"wide":3,"narrow":4}}""").parse()
        leaf.validate(valid, false)
        val invalid = ConfigJson("""{"wide":{"day":1,"night":2},"narrow":{"day":3,"night":4}}""").parse()
        rejected { leaf.validate(invalid, false) }
    }

    @Test
    fun rejectsUnsupportedVersionsOldRootUnknownKeysAndMissingSkin() {
        listOf(
            """{"version":1}""",
            """{"schemaVersion":1}""",
            """{"schemaVersion":2}""",
            """{"schemaVersion":3.0}""",
            """{"schemaVersion":"3"}""",
            """{"schemaVersion":4}""",
            """{"behavior":{"enableStatusLines":false}}""",
            """{"drawables":{}}""",
            """{"colors":{}}""",
            """{"applicationMode":"PHONE"}""",
            """{"activeSkin":"absent"}""",
            """{"skins":[]}""",
            """{"skins":{"":{}}}""",
            """{"skins":{"default":{"applicationMode":"phone"}}}""",
        ).forEach { text -> rejected(text) { ProjectConfiguration.parse(text) } }
    }

    @Test
    fun rejectsEntireFileForInvalidInactiveSkin() {
        val config =
            StartupConfiguration().load {
                """{"indexer":{"retentionGenerations":37},"skins":{"unused":{"geometry":{"touchTargetMin":1}}}}"""
            }
        assertEquals(ConfigurationProblem.INVALID, config.problem)
        assertEquals(128.0, config.snapshot.number("indexer.retentionGenerations"), 0.0)
    }

    @Test
    fun rejectsUnsafeContrastAndGeometryCrossConstraints() {
        listOf(
            """{"colors":{"textPrimaryColor":{"day":"#dae2ea"}}}""",
            """{"colors":{"textSecondaryColor":{"night":"#1b242e"}}}""",
            """{"colors":{"focusRingColor":{"night":"#1b242e"}}}""",
            """{"geometry":{"mainCategoryIconSide":230}}""",
            """{"geometry":{"touchTargetMin":120}}""",
            """{"geometry":{"ratingTarget":120}}""",
            """{"geometry":{"ratingTargetNarrow":100}}""",
            """{"geometry":{"navTarget":100}}""",
            """{"typography":{"mixedButtonMinFontSize":42,"mixedButtonMaxFontSize":40}}""",
            """{"behavior":{"rowsPerPageMin":5,"rowsPerPageMax":3}}""",
        ).forEach { skin -> rejected(skin) { ProjectConfiguration.parse("""{"skins":{"default":$skin}}""") } }
    }

    @Test
    fun rejectsDisallowedVariantShapesAndTypes() {
        listOf(
            """{"geometry":{"listRowHeight":{"day":100,"night":120}}}""",
            """{"geometry":{"wideThreshold":{"wide":800,"narrow":800}}}""",
            """{"geometry":{"listRowHeight":{"wide":100,"narrow":"100"}}}""",
            """{"geometry":{"listRowHeight":{"wide":100,"other":100}}}""",
            """{"geometry":{"listRowHeight":{}}}""",
            """{"colors":{"screenBackground":{"wide":"#ffffff","narrow":"#000000"}}}""",
            """{"colors":{"screenBackground":"#ffffff"}}""",
            """{"colors":{"screenBackground":{"day":"#00ffffff"}}}""",
            """{"behavior":{"artworkAlpha":128.0}}""",
            """{"behavior":{"enableStatusLines":1}}""",
            """{"typography":{"topBarFontFamily":"comic-sans"}}""",
            """{"typography":{"topBarFontWeight":600}}""",
            """{"drawables":{}}""",
        ).forEach { skin -> rejected(skin) { ProjectConfiguration.parse("""{"skins":{"default":$skin}}""") } }
    }

    @Test
    fun strictJsonRejectsNonJsonAndUnsafeNumbers() {
        listOf(
            "",
            "[]",
            "null",
            """{"schemaVersion":3,"schemaVersion":3}""",
            """{"schemaVersion":3} trailing""",
            """{"schemaVersion":3,}""",
            """{"schemaVersion":+3}""",
            """{"schemaVersion":03}""",
            """{"schemaVersion":3e0}""",
            """{"skins":null}""",
            """{"indexer":{"retentionGenerations":NaN}}""",
            """{"indexer":{"retentionGenerations":1e999}}""",
            """{"indexer":{"retentionGenerations":99999999999999999999999999999999}}""",
            """{"/*comment*/":3}""",
            """{'schemaVersion':3}""",
        ).forEach { text -> rejected(text) { ProjectConfiguration.parse(text) } }
        assertEquals("default", ProjectConfiguration.parse("{}").activeSkin)
    }

    @Test
    fun globalBoundsAndExactIntegerTypes() {
        listOf(0, -1, 4294967296L, 1.5, "\"1\"").forEach { value ->
            rejected { ProjectConfiguration.parse("""{"indexer":{"retentionGenerations":$value}}""") }
        }
        assertEquals(
            4294967295.0,
            ProjectConfiguration
                .parse("""{"indexer":{"retentionGenerations":4294967295}}""")
                .number("indexer.retentionGenerations"),
            0.0,
        )
        listOf(
            """{"player":{"excludedGenreIds":["1","1"]}}""",
            """{"player":{"excludedGenreIds":["01"]}}""",
            """{"player":{"excludedGenreIds":["0"]}}""",
            """{"player":{"rewindPreviousThresholdSeconds":{"music":0}}}""",
            """{"ai":{"heatmapValenceCells":129}}""",
            """{"ai":{"heatmapColors":{"night":{"topLeft":"transparent"}}}}""",
            """{"ai":{"selectionModel":{"historyDecay":1}}}""",
            """{"ai":{"selectionModel":{"weights":{"wrong":1}}}}""",
        ).forEach { text -> rejected(text) { ProjectConfiguration.parse(text) } }
    }

    @Test
    fun lockedAaosCannotAcceptModeOverrides() {
        val locked = immutableObject(packaged - "applicationMode")
        assertEquals("aaos", ProjectConfiguration.resolve(locked).applicationMode)
        rejected { ProjectConfiguration.resolve(locked, configObject("applicationMode" to "phone")) }
    }

    @Test
    fun snapshotCollectionsCannotBeMutated() {
        val config = ProjectConfiguration.parse("{}")

        @Suppress("UNCHECKED_CAST")
        val ids = config.value("player.excludedGenreIds") as MutableList<Any>
        rejected { ids.clear() }
        @Suppress("UNCHECKED_CAST")
        val weights = config.value("ai.selectionModel.weights") as MutableMap<String, Any>
        rejected { weights["arousalDistance"] = 99 }
        assertEquals(1.0, config.number("ai.selectionModel.weights.arousalDistance"), 0.0)
    }

    @Test
    fun startupReadsOnlyOnceIncludingMissingInvalidAndUnreadableFiles() {
        val readers: List<() -> String?> =
            listOf(
                { null },
                { "" },
                { throw IOException("unreadable") },
                { """{"applicationMode":"phone"}""" },
            )
        readers.forEach { reader ->
            val startup = StartupConfiguration()
            val calls = AtomicInteger()
            val first =
                startup.load {
                    calls.incrementAndGet()
                    reader()
                }
            val second =
                startup.load {
                    calls.incrementAndGet()
                    """{"applicationMode":"aaos"}"""
                }
            assertSame(first, second)
            assertEquals("phone", first.snapshot.applicationMode)
            assertEquals(1, calls.get())
        }
    }

    @Test
    fun concurrentStartupCallersShareOneCompleteSnapshot() {
        val startup = StartupConfiguration()
        val reads = AtomicInteger()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results =
                executor
                    .invokeAll(
                        List(16) {
                            Callable {
                                startup.load {
                                    reads.incrementAndGet()
                                    """{"applicationMode":"phone"}"""
                                }
                            }
                        },
                    ).map { it.get() }
            assertEquals(1, reads.get())
            results.forEach { assertSame(results.first(), it) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun rejected(
        label: String = "Expected rejection",
        operation: () -> Unit,
    ) {
        assertTrue(label, runCatching(operation).isFailure)
    }
}
