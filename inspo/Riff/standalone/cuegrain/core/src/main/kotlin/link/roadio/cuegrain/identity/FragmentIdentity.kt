// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain.identity

import java.nio.ByteBuffer

/** Bind a known content component to nullable clip bounds without acquiring the source again. */
fun fragmentIdentityComponent(
    contentComponent: Long,
    beginUs: Long?,
    endUs: Long?,
    low: Boolean,
    hash: (ByteArray) -> Long,
): Long {
    require(beginUs == null || beginUs >= 0)
    require(endUs == null || (endUs >= 0 && (beginUs == null || endUs > beginUs)))
    val frame =
        ByteBuffer
            .allocate(27)
            .put(if (low) 0x4c.toByte() else 0x46.toByte())
            .putLong(contentComponent)
            .put(if (beginUs == null) 0.toByte() else 1.toByte())
            .putLong(beginUs ?: 0)
            .put(if (endUs == null) 0.toByte() else 1.toByte())
            .putLong(endUs ?: 0)
    return hash(frame.array())
}
