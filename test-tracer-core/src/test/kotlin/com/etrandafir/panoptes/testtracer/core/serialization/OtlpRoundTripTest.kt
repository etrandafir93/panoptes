package com.etrandafir.panoptes.testtracer.core.serialization

import com.etrandafir.panoptes.testtracer.core.model.AttributeValue
import com.etrandafir.panoptes.testtracer.core.model.Attributes
import com.etrandafir.panoptes.testtracer.core.model.InstrumentationScope
import com.etrandafir.panoptes.testtracer.core.model.Resource
import com.etrandafir.panoptes.testtracer.core.model.Span
import com.etrandafir.panoptes.testtracer.core.model.SpanEvent
import com.etrandafir.panoptes.testtracer.core.model.SpanKind
import com.etrandafir.panoptes.testtracer.core.model.SpanStatus
import com.etrandafir.panoptes.testtracer.core.model.StatusCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class OtlpRoundTripTest : FunSpec({

    val serializer = OtlpJsonSerializer()

    test("Arb<Span> survives serialize → deserialize unchanged") {
        checkAll(PropTestConfig(iterations = 200), spanArb) { span ->
            val line = serializer.serialize(span)
            val parsed = OtlpSpanDeserializer.deserialize(line)
            parsed shouldBe span
        }
    }

    test("explicit minimal span round-trips") {
        val span = Span(
            traceId = "0102030405060708090a0b0c0d0e0f10",
            spanId = "1112131415161718",
            parentSpanId = null,
            name = "minimal",
            kind = SpanKind.INTERNAL,
            startEpochNanos = 0,
            endEpochNanos = 0,
            status = SpanStatus.UNSET,
            attributes = Attributes.EMPTY,
            events = emptyList(),
            resource = Resource.EMPTY,
            scope = InstrumentationScope.UNKNOWN,
        )
        OtlpSpanDeserializer.deserialize(serializer.serialize(span)) shouldBe span
    }

    test("Span with all attribute variants and nested events round-trips") {
        val span = Span(
            traceId = "ffffffffffffffffffffffffffffffff",
            spanId = "ffffffffffffffff",
            parentSpanId = "1111111111111111",
            name = "kitchen sink \"with quotes\"\nand newline",
            kind = SpanKind.SERVER,
            startEpochNanos = 1_700_000_000_000_000_000,
            endEpochNanos = 1_700_000_000_500_000_000,
            status = SpanStatus.error("boom"),
            attributes = Attributes.of(
                "s" to AttributeValue.StringValue("hi"),
                "b" to AttributeValue.BooleanValue(true),
                "l" to AttributeValue.LongValue(42),
                "d" to AttributeValue.DoubleValue(3.14),
                "ls" to AttributeValue.StringList(listOf("a", "b")),
                "lb" to AttributeValue.BooleanList(listOf(true, false)),
                "ll" to AttributeValue.LongList(listOf(1L, 2L, 3L)),
                "ld" to AttributeValue.DoubleList(listOf(1.5, 2.5)),
            ),
            events = listOf(
                SpanEvent(100, "log", Attributes.of("k" to AttributeValue.StringValue("v"))),
                SpanEvent(200, "exception", Attributes.EMPTY),
            ),
            resource = Resource(
                Attributes.of("service.name" to AttributeValue.StringValue("checkout")),
                schemaUrl = "https://opentelemetry.io/schemas/1.25.0",
            ),
            scope = InstrumentationScope("io.opentelemetry.jdbc", "1.0.0"),
        )
        OtlpSpanDeserializer.deserialize(serializer.serialize(span)) shouldBe span
    }
})

private val jsonSafeString: Arb<String> = Arb.string(minSize = 0, maxSize = 40)

private val attributeKey: Arb<String> = Arb.string(minSize = 1, maxSize = 20).map { it.ifBlank { "k" } }

private val finiteDouble: Arb<Double> = Arb.double().map { d ->
    when {
        d.isNaN() || d.isInfinite() -> 0.0
        else -> d
    }
}

private val attributeValueArb: Arb<AttributeValue> = Arb.choice(
    jsonSafeString.map { AttributeValue.StringValue(it) },
    Arb.boolean().map { AttributeValue.BooleanValue(it) },
    Arb.long().map { AttributeValue.LongValue(it) },
    finiteDouble.map { AttributeValue.DoubleValue(it) },
    // Non-empty lists only — the OTLP shape cannot distinguish empty StringList from empty LongList,
    // so the deserializer defaults empties to StringList. Round-trip requires at least one element.
    Arb.list(jsonSafeString, 1..3).map { AttributeValue.StringList(it) },
    Arb.list(Arb.boolean(), 1..3).map { AttributeValue.BooleanList(it) },
    Arb.list(Arb.long(), 1..3).map { AttributeValue.LongList(it) },
    Arb.list(finiteDouble, 1..3).map { AttributeValue.DoubleList(it) },
)

private val attributesArb: Arb<Attributes> =
    Arb.map(attributeKey, attributeValueArb, minSize = 0, maxSize = 5).map(::Attributes)

private val spanEventArb: Arb<SpanEvent> = Arb.bind(
    Arb.long(0, Long.MAX_VALUE),
    jsonSafeString,
    attributesArb,
    ::SpanEvent,
)

private val resourceArb: Arb<Resource> = Arb.bind(
    attributesArb,
    jsonSafeString.orNull(),
    ::Resource,
)

private val scopeArb: Arb<InstrumentationScope> = Arb.bind(
    Arb.string(minSize = 1, maxSize = 20).map { it.ifBlank { "scope" } },
    jsonSafeString.orNull(),
    ::InstrumentationScope,
)

private val statusArb: Arb<SpanStatus> = Arb.choice(
    Arb.constant(SpanStatus.UNSET),
    Arb.constant(SpanStatus.OK),
    jsonSafeString.orNull().map { SpanStatus(StatusCode.ERROR, it) },
)

private val spanArb: Arb<Span> = Arb.bind(
    Arb.of("0102030405060708090a0b0c0d0e0f10"),
    Arb.of("1112131415161718"),
    Arb.of("2122232425262728").orNull(),
    jsonSafeString,
    Arb.enum<SpanKind>(),
    Arb.long(0, Long.MAX_VALUE),
    Arb.long(0, Long.MAX_VALUE),
    statusArb,
    attributesArb,
    Arb.list(spanEventArb, 0..3),
    resourceArb,
    scopeArb,
) { traceId, spanId, parentSpanId, name, kind, start, end, status, attrs, events, resource, scope ->
    Span(traceId, spanId, parentSpanId, name, kind, start, end, status, attrs, events, resource, scope)
}

// Suppress unused warning — kept as the canonical way to sample one span if needed in REPL.
@Suppress("unused")
private fun sample(): Span = spanArb.next()
