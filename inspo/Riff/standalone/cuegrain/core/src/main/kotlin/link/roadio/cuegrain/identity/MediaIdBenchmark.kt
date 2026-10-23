// SPDX-License-Identifier: Apache-2.0
package link.roadio.cuegrain.identity

/** Debug and instrumentation telemetry emitted by the sole active Media.ID path. */
data class MediaIdBenchmarkSample(
    val source: String,
    val platform: String,
    val implementation: String,
    val success: Boolean,
    val elapsedNanos: Long,
    val sampleCount: ULong?,
    val selectedOccurrences: Int,
    val selectedPayloadBytes: Long,
    val digest: Long?,
    val errorCode: String?,
    val phasesElapsedNanos: Map<String, Long> = emptyMap(),
    val identityVersion: String,
    val mode: String? = null,
    val terminationReason: String? = null,
)

fun interface MediaIdBenchmarkObserver {
    fun record(sample: MediaIdBenchmarkSample)
}
