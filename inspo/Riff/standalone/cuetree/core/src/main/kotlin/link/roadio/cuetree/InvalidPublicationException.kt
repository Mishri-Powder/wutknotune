// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuetree

import java.io.IOException

/** Invalid persisted facts are distinct from operational filesystem failures; hosts own recreation. */
class InvalidPublicationException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
