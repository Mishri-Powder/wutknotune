// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain.identity

import link.roadio.cuegrain.Report

interface MediaIdentityCancellation {
    val cancelled: Boolean

    fun ensureAvailable()
}

open class MediaIdentityException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

data class MediaIdentity(
    val high: Long,
    val low: Long? = null,
)

data class MediaAcquisition(
    val report: Report,
    val media_id: MediaIdentity,
)
