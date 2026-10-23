package link.roadio.riff.indexer.core.logging

enum class RiffLogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF,
}

data class RiffLogRecord(
    val level: RiffLogLevel,
    val component: String,
    val event: String,
    val message: String,
    val fields: Map<String, Any?>,
    val error: Throwable?,
)

fun interface RiffLogSink {
    fun emit(record: RiffLogRecord)
}

object NoOpLogSink : RiffLogSink {
    override fun emit(record: RiffLogRecord) = Unit
}

class RiffLogger(
    private val component: String,
    private val minimumLevel: RiffLogLevel = RiffLogLevel.INFO,
    private val sink: RiffLogSink = NoOpLogSink,
) {
    fun child(name: String): RiffLogger =
        RiffLogger(
            component = "$component.$name",
            minimumLevel = minimumLevel,
            sink = sink,
        )

    fun trace(
        event: String,
        message: String = "",
        fields: Map<String, Any?> = emptyMap(),
    ) = log(RiffLogLevel.TRACE, event, message, fields)

    fun debug(
        event: String,
        message: String = "",
        fields: Map<String, Any?> = emptyMap(),
    ) = log(RiffLogLevel.DEBUG, event, message, fields)

    fun info(
        event: String,
        message: String = "",
        fields: Map<String, Any?> = emptyMap(),
    ) = log(RiffLogLevel.INFO, event, message, fields)

    fun warn(
        event: String,
        message: String = "",
        fields: Map<String, Any?> = emptyMap(),
        error: Throwable? = null,
    ) = log(RiffLogLevel.WARN, event, message, fields, error)

    fun error(
        event: String,
        message: String = "",
        fields: Map<String, Any?> = emptyMap(),
        error: Throwable? = null,
    ) = log(RiffLogLevel.ERROR, event, message, fields, error)

    private fun log(
        level: RiffLogLevel,
        event: String,
        message: String,
        fields: Map<String, Any?>,
        error: Throwable? = null,
    ) {
        if (minimumLevel == RiffLogLevel.OFF || level.ordinal < minimumLevel.ordinal) {
            return
        }
        sink.emit(
            RiffLogRecord(
                level = level,
                component = component,
                event = event,
                message = message,
                fields = fields,
                error = error,
            ),
        )
    }

    companion object {
        fun disabled(component: String = "riff"): RiffLogger =
            RiffLogger(
                component = component,
                minimumLevel = RiffLogLevel.OFF,
                sink = NoOpLogSink,
            )
    }
}
