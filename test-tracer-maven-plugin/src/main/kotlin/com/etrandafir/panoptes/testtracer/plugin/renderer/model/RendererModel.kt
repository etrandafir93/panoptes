package com.etrandafir.panoptes.testtracer.plugin.renderer.model

/**
 * A single span parsed from an NDJSON line. Holds only the fields relevant to the renderer.
 * Field names mirror the OTLP-flat JSON shape written by OtlpJsonSerializer.
 */
data class SpanData(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
    val name: String,
    val startEpochNanos: Long,
    val endEpochNanos: Long,
    val statusCode: String,        // "STATUS_CODE_OK" | "STATUS_CODE_ERROR" | "STATUS_CODE_UNSET"
    val statusMessage: String?,
    val attributes: Map<String, String>,   // flattened: only stringValue for display
    val rawAttributes: List<RawAttribute>, // full attribute list for display
    val events: List<SpanEvent>,
) {
    val durationNanos: Long get() = endEpochNanos - startEpochNanos
    val isRoot: Boolean get() = parentSpanId == null
}

data class RawAttribute(val key: String, val displayValue: String)

data class SpanEvent(
    val epochNanos: Long,
    val name: String,
    val attributes: List<RawAttribute>,
)

/**
 * Summary entry for the index page. One per test (root span bearing test.* attributes).
 */
data class TestEntry(
    val traceId: String,
    val name: String,           // "ClassName#methodName"
    val status: String,         // "STATUS_CODE_ERROR" | "STATUS_CODE_OK" | "STATUS_CODE_UNSET"
    val durationNanos: Long,
    val spanCount: Int,
    val failureMessage: String?,
)

/**
 * Recursive tree of spans for a single trace.
 */
data class TraceTree(
    val root: SpanData,
    val children: List<TraceTree>,
)

/**
 * Spans that belong to traces with no root bearing test.* attributes.
 */
data class OrphanGroup(val spans: List<SpanData>)

/**
 * The result of aggregating all NDJSON lines across all input files.
 */
data class AggregationResult(
    val tests: List<TestEntry>,
    val traces: Map<String, TraceTree>,
    val orphans: OrphanGroup,
    val noFilesFound: Boolean = false,
    val filesFoundButEmpty: Boolean = false,
    val testsSkipped: Boolean = false,
)
