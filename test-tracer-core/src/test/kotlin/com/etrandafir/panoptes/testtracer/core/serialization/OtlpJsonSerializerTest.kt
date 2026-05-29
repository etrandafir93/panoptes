package com.etrandafir.panoptes.testtracer.core.serialization

import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.BooleanValue
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.DoubleValue
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.LongValue
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.StringList
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.StringValue
import com.etrandafir.panoptes.testtracer.core.model.Attributes
import com.etrandafir.panoptes.testtracer.core.model.InstrumentationScope
import com.etrandafir.panoptes.testtracer.core.model.Resource
import com.etrandafir.panoptes.testtracer.core.model.Span
import com.etrandafir.panoptes.testtracer.core.model.SpanEvent
import com.etrandafir.panoptes.testtracer.core.model.SpanKind
import com.etrandafir.panoptes.testtracer.core.model.SpanStatus
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class OtlpJsonSerializerTest : StringSpec({

    val serializer = OtlpJsonSerializer()
    val mapper = ObjectMapper()

    "output is single-line JSON" {
        val line = serializer.serialize(aSpan())
        line.shouldNotContain("\n")
        line.shouldNotContain("\r")
        mapper.readTree(line) // throws if not valid JSON
    }

    "core span fields serialize per OTLP conventions" {
        val line = serializer.serialize(
            aSpan(
                traceId = "0102030405060708090a0b0c0d0e0f10",
                spanId = "1112131415161718",
                parentSpanId = "2122232425262728",
                name = "GET /users",
                kind = SpanKind.SERVER,
                startEpochNanos = 1_700_000_000_000_000_000,
                endEpochNanos = 1_700_000_000_500_000_000,
            ),
        )
        val node = mapper.readTree(line)
        node["traceId"].asText() shouldBe "0102030405060708090a0b0c0d0e0f10"
        node["spanId"].asText() shouldBe "1112131415161718"
        node["parentSpanId"].asText() shouldBe "2122232425262728"
        node["name"].asText() shouldBe "GET /users"
        node["kind"].asText() shouldBe "SPAN_KIND_SERVER"
        // int64 nano fields must be quoted strings per OTLP/JSON mapping
        node["startTimeUnixNano"].asText() shouldBe "1700000000000000000"
        node["endTimeUnixNano"].asText() shouldBe "1700000000500000000"
    }

    "parentSpanId omitted when null" {
        val line = serializer.serialize(aSpan(parentSpanId = null))
        mapper.readTree(line)["parentSpanId"].shouldBeNull()
    }

    "status code and message are serialized" {
        val line = serializer.serialize(aSpan(status = SpanStatus.error("boom")))
        val status = mapper.readTree(line)["status"]
        status["code"].asText() shouldBe "STATUS_CODE_ERROR"
        status["message"].asText() shouldBe "boom"
    }

    "status message omitted when null" {
        val line = serializer.serialize(aSpan(status = SpanStatus.OK))
        val status = mapper.readTree(line)["status"]
        status["code"].asText() shouldBe "STATUS_CODE_OK"
        status["message"].shouldBeNull()
    }

    "all attribute value variants serialize" {
        val line = serializer.serialize(
            aSpan(
                attributes = Attributes.of(
                    "string" to StringValue("hi"),
                    "bool" to BooleanValue(true),
                    "long" to LongValue(42),
                    "double" to DoubleValue(3.14),
                    "string-list" to StringList(listOf("a", "b")),
                ),
            ),
        )
        val attrs = mapper.readTree(line)["attributes"].associateAttributes()
        attrs.getValue("string")["stringValue"].asText() shouldBe "hi"
        attrs.getValue("bool")["boolValue"].asBoolean() shouldBe true
        attrs.getValue("long")["intValue"].asText() shouldBe "42"
        attrs.getValue("double")["doubleValue"].asDouble() shouldBe 3.14
        val list = attrs.getValue("string-list")["arrayValue"]["values"]
        list[0]["stringValue"].asText() shouldBe "a"
        list[1]["stringValue"].asText() shouldBe "b"
    }

    "string escaping covers quotes, backslashes, newlines, and control chars" {
        val tricky = "line1\nline2\twith \"quotes\" and \\backslash\\ "
        val line = serializer.serialize(aSpan(name = tricky))
        mapper.readTree(line)["name"].asText() shouldBe tricky
    }

    "events serialize with their timestamps, names, and attributes" {
        val line = serializer.serialize(
            aSpan(
                events = listOf(
                    SpanEvent(100, "exception", Attributes.of("type" to StringValue("X"))),
                    SpanEvent(200, "log", Attributes.EMPTY),
                ),
            ),
        )
        val events = mapper.readTree(line)["events"]
        events.size() shouldBe 2
        events[0]["timeUnixNano"].asText() shouldBe "100"
        events[0]["name"].asText() shouldBe "exception"
        events[0]["attributes"][0]["key"].asText() shouldBe "type"
        events[0]["attributes"][0]["value"]["stringValue"].asText() shouldBe "X"
        events[1]["attributes"].size() shouldBe 0
    }

    "resource and scope are inlined per span" {
        val line = serializer.serialize(
            aSpan(
                resource = Resource(
                    Attributes.of("service.name" to StringValue("checkout")),
                    schemaUrl = "https://opentelemetry.io/schemas/1.25.0",
                ),
                scope = InstrumentationScope("io.opentelemetry.jdbc", "1.0.0"),
            ),
        )
        val node = mapper.readTree(line)
        node["resource"]["attributes"][0]["key"].asText() shouldBe "service.name"
        node["resource"]["schemaUrl"].asText() shouldBe "https://opentelemetry.io/schemas/1.25.0"
        node["scope"]["name"].asText() shouldBe "io.opentelemetry.jdbc"
        node["scope"]["version"].asText() shouldBe "1.0.0"
    }
})

private fun JsonNode.associateAttributes(): Map<String, JsonNode> =
    (0 until size()).associate { i -> get(i)["key"].asText() to get(i)["value"] }

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
