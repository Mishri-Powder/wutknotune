// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CompleteLinkageTest {
    @Test fun completeGroupDoesNotRequireAPairCache() {
        var calls = 0
        val tracks = (0 until 20).toList()
        val groups =
            completeLinkageGroups(tracks, Cancellation()) { _, _ ->
                calls++
                90.0
            }
        assertTrue(calls >= 190)
        assertEquals(listOf(tracks), groups)
    }

    @Test fun equalIncompatibleAssignmentsRemainSeparate() {
        val groups =
            completeLinkageGroups(listOf(0, 1, 2), Cancellation()) { a, b ->
                if (a == 1 && b == 2) 40.0 else 80.0
            }
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), groups)
    }

    @Test fun everyReturnedClusterSatisfiesCompleteLinkage() {
        val random = Random(7131)
        repeat(100) {
            val tracks = (0 until random.nextInt(2, 9)).toList()
            val scores =
                tracks
                    .flatMap { a ->
                        (a + 1 until tracks.size).map { b ->
                            (a to b) to listOf(40.0, 65.0, 70.0, 85.0)[random.nextInt(4)]
                        }
                    }.toMap()
            val score: (Int, Int) -> Double = { a, b -> scores.getValue(minOf(a, b) to maxOf(a, b)) }
            for (group in completeLinkageGroups(tracks, Cancellation(), score)) {
                for (a in group.indices) for (b in a + 1 until group.size) assertTrue(score(group[a], group[b]) >= 65)
            }
        }
    }

    @Test fun ambiguousComponentDoesNotBlockAnUnrelatedLowerScoreMerge() {
        val groups =
            completeLinkageGroups((0..4).toList(), Cancellation()) { a, b ->
                when (setOf(a, b)) {
                    setOf(0, 1), setOf(0, 2) -> 80.0
                    setOf(3, 4) -> 70.0
                    else -> 40.0
                }
            }
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4)), groups)
    }
}
