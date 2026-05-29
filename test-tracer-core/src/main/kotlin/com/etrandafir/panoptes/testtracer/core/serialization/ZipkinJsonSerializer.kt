package com.etrandafir.panoptes.testtracer.core.serialization

import com.etrandafir.panoptes.testtracer.core.model.AttributeValue
import com.etrandafir.panoptes.testtracer.core.model.Resource
import com.etrandafir.panoptes.testtracer.core.model.Span
import com.etrandafir.panoptes.testtracer.core.model.SpanEvent
import com.etrandafir.panoptes.testtracer.core.model.SpanKind
import com.etrandafir.panoptes.testtracer.core.model.StatusCode

/**
 * Serializes a [Span] to one line of Zipkin v2 JSON.
 *
 * Each line is a single Zipkin span object. The file is NDJSON; to feed it
 * into a Zipkin instance, wrap the lines in a JSON array (e.g.
 * `jq -s . spans.ndjson > spans.json`) and POST to `/api/v2/spans`.
 *
 * Mapping decisions:
 * - `timestamp` and `duration` are epoch microseconds (Zipkin convention).
 * - `kind` follows Zipkin's SERVER/CLIENT/PRODUCER/CONSUMER set; INTERNAL is
 *   omitted entirely (Zipkin convention for internal spans).
 * - `localEndpoint.serviceName` is taken from the resource attribute
 *   `service.name`, falling back to `"unknown"`.
 * - `tags` is a flat string-keyed string map; non-string attribute values are
 *   stringified, list values are joined with `,`. This is lossy by Zipkin
 *   design — for full fidelity use the OTLP file.
 * - Status: ERROR adds a Zipkin `error` tag with the status description; for
 *   any non-UNSET status we also emit `otel.status_code`.
 * - Events become Zipkin `annotations` (timestamp + value). Event attributes
 *   are dropped — Zipkin annotations have no key/value bag.
 */
class ZipkinJsonSerializer : SpanSerializer {

    override fun serialize(span: Span): String = buildString {
        append('{')
        appendStringField("traceId", span.traceId); append(',')
        if (span.parentSpanId != null) {
            appendStringField("parentId", span.parentSpanId); append(',')
        }
        appendStringField("id", span.spanId); append(',')
        if (span.kind != SpanKind.INTERNAL) {
            appendStringField("kind", span.kind.name); append(',')
        }
        appendStringField("name", span.name); append(',')
        append("\"timestamp\":").append(span.startEpochNanos / 1_000); append(',')
        append("\"duration\":").append(span.durationNanos / 1_000); append(',')
        appendLocalEndpoint(span.resource); append(',')
        appendAnnotations(span.events); append(',')
        appendTags(span)
        append('}')
    }

    private fun StringBuilder.appendLocalEndpoint(resource: Resource) {
        val service = (resource.attributes["service.name"] as? AttributeValue.StringValue)?.value
            ?: "unknown"
        append("\"localEndpoint\":{")
        appendStringField("serviceName", service)
        append('}')
    }

    private fun StringBuilder.appendAnnotations(events: List<SpanEvent>) {
        append("\"annotations\":[")
        events.forEachIndexed { i, e ->
            if (i > 0) append(',')
            append('{')
            append("\"timestamp\":").append(e.epochNanos / 1_000); append(',')
            appendStringField("value", e.name)
            append('}')
        }
        append(']')
    }

    private fun StringBuilder.appendTags(span: Span) {
        append("\"tags\":{")
        var first = true
        for ((k, v) in span.attributes.values) {
            if (!first) append(',') else first = false
            appendStringField(k, attributeValueAsString(v))
        }
        if (span.hasError) {
            if (!first) append(',') else first = false
            appendStringField("error", span.status.description ?: "")
        }
        if (span.status.code != StatusCode.UNSET) {
            if (!first) append(',') else first = false
            appendStringField("otel.status_code", span.status.code.name)
        }
        append('}')
    }

    private fun attributeValueAsString(v: AttributeValue): String = when (v) {
        is AttributeValue.StringValue -> v.value
        is AttributeValue.BooleanValue -> v.value.toString()
        is AttributeValue.LongValue -> v.value.toString()
        is AttributeValue.DoubleValue -> v.value.toString()
        is AttributeValue.StringList -> v.values.joinToString(",")
        is AttributeValue.BooleanList -> v.values.joinToString(",")
        is AttributeValue.LongList -> v.values.joinToString(",")
        is AttributeValue.DoubleList -> v.values.joinToString(",")
    }
}
