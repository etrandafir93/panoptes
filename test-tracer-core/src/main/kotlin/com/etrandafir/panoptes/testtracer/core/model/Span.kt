package com.etrandafir.panoptes.testtracer.core.model

data class Span(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
    val name: String,
    val kind: SpanKind,
    val startEpochNanos: Long,
    val endEpochNanos: Long,
    val status: SpanStatus,
    val attributes: Attributes,
    val events: List<SpanEvent>,
    val resource: Resource,
    val scope: InstrumentationScope,
) {
    val durationNanos: Long get() = endEpochNanos - startEpochNanos
    val isRoot: Boolean get() = parentSpanId == null
    val hasError: Boolean get() = status.code == StatusCode.ERROR
}

enum class SpanKind {
    INTERNAL,
    SERVER,
    CLIENT,
    PRODUCER,
    CONSUMER,
}

data class SpanStatus(
    val code: StatusCode,
    val description: String? = null,
) {
    companion object {
        val UNSET = SpanStatus(StatusCode.UNSET)
        val OK = SpanStatus(StatusCode.OK)
        fun error(description: String? = null) = SpanStatus(StatusCode.ERROR, description)
    }
}

enum class StatusCode {
    UNSET,
    OK,
    ERROR,
}

data class SpanEvent(
    val epochNanos: Long,
    val name: String,
    val attributes: Attributes,
)
