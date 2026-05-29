package com.etrandafir.panoptes.testtracer.core.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SpanTest : StringSpec({

    "durationNanos returns end - start" {
        val span = aSpan(startEpochNanos = 1_000, endEpochNanos = 1_500)
        span.durationNanos shouldBe 500
    }

    "isRoot is true when parentSpanId is null" {
        aSpan(parentSpanId = null).isRoot shouldBe true
    }

    "isRoot is false when parentSpanId is set" {
        aSpan(parentSpanId = "0000000000000001").isRoot shouldBe false
    }

    "hasError is true when status code is ERROR" {
        aSpan(status = SpanStatus.error("boom")).hasError shouldBe true
    }

    "hasError is false for OK status" {
        aSpan(status = SpanStatus.OK).hasError shouldBe false
    }

    "hasError is false for UNSET status" {
        aSpan(status = SpanStatus.UNSET).hasError shouldBe false
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
