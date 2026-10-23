package link.roadio.riff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafGrantSelectorTest {
    @Test
    fun firstRunEntersGrantSelectionOnlyWhenZeroGrantsAreProven() {
        assertTrue(requiresInitialSafGrantSelection(false))
        assertFalse(requiresInitialSafGrantSelection(true))
        assertFalse(requiresInitialSafGrantSelection(null))
    }

    @Test
    fun `add is first and every current grant follows unchanged`() {
        val grants =
            listOf(
                SafGrantUiItem("tree:music", "Music · SD"),
                SafGrantUiItem("tree:usb", "Audio · USB"),
            )

        assertEquals(
            listOf("Add..", "Music · SD", "Audio · USB"),
            safGrantSelectorItems(grants).map(SafGrantUiItem::label),
        )
        assertEquals(SAF_ADD_ITEM_ID, safGrantSelectorItems(grants).first().id)
    }

    @Test
    fun boundGrantChangeDrainsMutatesAndRestartsInOrder() {
        val calls = mutableListOf<String>()

        runSafGrantChangeBarrier(
            indexerWasBound = true,
            quiesceIndexer = {
                calls += "quiesce"
                true
            },
            mutateGrants = { calls += "mutate" },
            resumeIndexer = { full ->
                calls += "resume:$full"
                true
            },
        )

        assertEquals(listOf("quiesce", "mutate", "resume:true"), calls)
    }

    @Test
    fun failedBarrierLeavesGrantsUntouchedAndOnlyUnquiesces() {
        val calls = mutableListOf<String>()

        runCatching {
            runSafGrantChangeBarrier(
                indexerWasBound = true,
                quiesceIndexer = {
                    calls += "quiesce"
                    false
                },
                mutateGrants = { calls += "mutate" },
                resumeIndexer = { full ->
                    calls += "resume:$full"
                    true
                },
            )
        }

        assertEquals(listOf("quiesce", "resume:false"), calls)
    }

    @Test
    fun failedMutationStillRequestsFullReconciliation() {
        val calls = mutableListOf<String>()

        runCatching {
            runSafGrantChangeBarrier(
                indexerWasBound = true,
                quiesceIndexer = {
                    calls += "quiesce"
                    true
                },
                mutateGrants = {
                    calls += "mutate"
                    error("permission failure")
                },
                resumeIndexer = { full ->
                    calls += "resume:$full"
                    true
                },
            )
        }

        assertEquals(listOf("quiesce", "mutate", "resume:true"), calls)
    }

    @Test
    fun firstGrantWithoutBoundIndexerNeedsNoBarrier() {
        val calls = mutableListOf<String>()

        runSafGrantChangeBarrier(
            indexerWasBound = false,
            quiesceIndexer = {
                calls += "quiesce"
                true
            },
            mutateGrants = { calls += "mutate" },
            resumeIndexer = { full ->
                calls += "resume:$full"
                true
            },
        )

        assertEquals(listOf("mutate"), calls)
    }

    @Test
    fun `empty grants still expose add as the sole row`() {
        assertEquals(
            listOf(SafGrantUiItem(SAF_ADD_ITEM_ID, "Add..")),
            safGrantSelectorItems(emptyList()),
        )
    }
}
