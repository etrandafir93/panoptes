package com.etrandafir.panoptes.testtracer.core.serialization

import com.etrandafir.panoptes.testtracer.core.model.Attributes
import com.etrandafir.panoptes.testtracer.core.model.InstrumentationScope
import com.etrandafir.panoptes.testtracer.core.model.Resource
import com.etrandafir.panoptes.testtracer.core.model.Span
import com.etrandafir.panoptes.testtracer.core.model.SpanEvent
import com.etrandafir.panoptes.testtracer.core.model.SpanKind
import com.etrandafir.panoptes.testtracer.core.model.SpanStatus
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.json.shouldNotContainJsonKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldNotContain

/**
 * Field-mapping correctness is exhaustively covered by [OtlpRoundTripTest].
 * This spec keeps the edge cases that the round-trip can't observe:
 * single-line invariant, OTLP-specific field names/prefixes, and presence/absence
 * of optional fields.
 */
class OtlpJsonSerializerTest : StringSpec({

    val serializer = OtlpJsonSerializer()

    "output is a single line" {
        val line = serializer.serialize(aSpan())
        line.shouldNotContain("\n")
        line.shouldNotContain("\r")
    }

    "kind is prefixed with SPAN_KIND_" {
        serializer.serialize(aSpan(kind = SpanKind.SERVER))
            .shouldContainJsonKeyValue("$.kind", "SPAN_KIND_SERVER")
    }

    "int64 nano fields are quoted strings per OTLP/JSON" {
        // shouldContainJsonKeyValue with a String value asserts the JSON value is a string,
        // not a number — exactly the OTLP/JSON int64 mapping requirement.
        serializer.serialize(aSpan(startEpochNanos = 1_700_000_000_000_000_000))
            .shouldContainJsonKeyValue("$.startTimeUnixNano", "1700000000000000000")
    }

    "parentSpanId omitted when null" {
        serializer.serialize(aSpan(parentSpanId = null))
            .shouldNotContainJsonKey("$.parentSpanId")
    }

    "parentSpanId present when set" {
        serializer.serialize(aSpan(parentSpanId = "2122232425262728"))
            .shouldContainJsonKeyValue("$.parentSpanId", "2122232425262728")
    }

    "status code prefixed with STATUS_CODE_ and message included" {
        val line = serializer.serialize(aSpan(status = SpanStatus.error("boom")))
        line.shouldContainJsonKeyValue("$.status.code", "STATUS_CODE_ERROR")
        line.shouldContainJsonKeyValue("$.status.message", "boom")
    }

    "status message omitted when null" {
        val line = serializer.serialize(aSpan(status = SpanStatus.OK))
        line.shouldContainJsonKeyValue("$.status.code", "STATUS_CODE_OK")
        line.shouldNotContainJsonKey("$.status.message")
    }

    "resource schemaUrl omitted when null" {
        serializer.serialize(aSpan(resource = Resource(Attributes.EMPTY, schemaUrl = null)))
            .shouldNotContainJsonKey("$.resource.schemaUrl")
    }

    "scope version omitted when null" {
        serializer.serialize(aSpan(scope = InstrumentationScope("io.opentelemetry.jdbc")))
            .shouldNotContainJsonKey("$.scope.version")
    }

    "events array is present even when empty" {
        serializer.serialize(aSpan(events = emptyList())).shouldContainJsonKey("$.events")
    }
})

private fun aSpan(
    traceId: String = "00000000000000000000000000000001",
    spanId: String = "0000000000000002",
    parentSpanId: String? = null,
    name: String = "test-span",
    kind: SpanKind = SpanKind.INTERNAL,
    startEpochNanos: Long = 0,
    endEpochNanos: Long = 0,
    status: SpanStatus = SpanStatus.UNSET,
    attributes: Attributes = Attributes.EMPTY,
    events: List<SpanEvent> = emptyList(),
    resource: Resource = Resource.EMPTY,
    scope: InstrumentationScope = InstrumentationScope.UNKNOWN,
) = Span(
    traceId, spanId, parentSpanId, name, kind,
    startEpochNanos, endEpochNanos, status,
    attributes, events, resource, scope,
)
