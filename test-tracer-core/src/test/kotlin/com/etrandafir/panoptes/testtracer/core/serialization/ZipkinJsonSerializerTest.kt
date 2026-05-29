package com.etrandafir.panoptes.testtracer.core.serialization

import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.BooleanValue
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.DoubleValue
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.LongList
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.LongValue
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue.StringValue
import com.etrandafir.panoptes.testtracer.core.model.Attributes
import com.etrandafir.panoptes.testtracer.core.model.InstrumentationScope
import com.etrandafir.panoptes.testtracer.core.model.Resource
import com.etrandafir.panoptes.testtracer.core.model.Span
import com.etrandafir.panoptes.testtracer.core.model.SpanEvent
import com.etrandafir.panoptes.testtracer.core.model.SpanKind
import com.etrandafir.panoptes.testtracer.core.model.SpanStatus
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class ZipkinJsonSerializerTest : StringSpec({

    val serializer = ZipkinJsonSerializer()
    val mapper = ObjectMapper()

    "output is single-line valid JSON" {
        val line = serializer.serialize(aSpan())
        line.shouldNotContain("\n")
        mapper.readTree(line)
    }

    "core span fields use Zipkin v2 names and microsecond timestamps" {
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
        node["id"].asText() shouldBe "1112131415161718"
        node["parentId"].asText() shouldBe "2122232425262728"
        node["name"].asText() shouldBe "GET /users"
        node["kind"].asText() shouldBe "SERVER"
        node["timestamp"].asLong() shouldBe 1_700_000_000_000_000L
        node["duration"].asLong() shouldBe 500_000L
    }

    "parentId omitted for root spans" {
        val line = serializer.serialize(aSpan(parentSpanId = null))
        mapper.readTree(line)["parentId"].shouldBeNull()
    }

    "kind omitted entirely for INTERNAL spans" {
        val line = serializer.serialize(aSpan(kind = SpanKind.INTERNAL))
        mapper.readTree(line)["kind"].shouldBeNull()
    }

    "localEndpoint.serviceName comes from resource attribute" {
        val line = serializer.serialize(
            aSpan(resource = Resource(Attributes.of("service.name" to StringValue("checkout")))),
        )
        mapper.readTree(line)["localEndpoint"]["serviceName"].asText() shouldBe "checkout"
    }

    "localEndpoint.serviceName defaults to 'unknown' when missing" {
        val line = serializer.serialize(aSpan(resource = Resource.EMPTY))
        mapper.readTree(line)["localEndpoint"]["serviceName"].asText() shouldBe "unknown"
    }

    "events become annotations (no attributes)" {
        val line = serializer.serialize(
            aSpan(
                events = listOf(
                    SpanEvent(1_000_000, "exception", Attributes.of("type" to StringValue("X"))),
                    SpanEvent(2_000_000, "log", Attributes.EMPTY),
                ),
            ),
        )
        val annotations = mapper.readTree(line)["annotations"]
        annotations.size() shouldBe 2
        annotations[0]["timestamp"].asLong() shouldBe 1_000L
        annotations[0]["value"].asText() shouldBe "exception"
        annotations[0]["type"].shouldBeNull() // event attributes dropped
        annotations[1]["value"].asText() shouldBe "log"
    }

    "attributes stringified into flat tags" {
        val line = serializer.serialize(
            aSpan(
                attributes = Attributes.of(
                    "http.method" to StringValue("GET"),
                    "http.status_code" to LongValue(200),
                    "http.cached" to BooleanValue(true),
                    "http.duration_ms" to DoubleValue(12.5),
                    "http.peer_ids" to LongList(listOf(1L, 2L, 3L)),
                ),
            ),
        )
        val tags = mapper.readTree(line)["tags"]
        tags["http.method"].asText() shouldBe "GET"
        tags["http.status_code"].asText() shouldBe "200"
        tags["http.cached"].asText() shouldBe "true"
        tags["http.duration_ms"].asText() shouldBe "12.5"
        tags["http.peer_ids"].asText() shouldBe "1,2,3"
    }

    "ERROR status adds error and otel.status_code tags" {
        val line = serializer.serialize(aSpan(status = SpanStatus.error("downstream timeout")))
        val tags = mapper.readTree(line)["tags"]
        tags["error"].asText() shouldBe "downstream timeout"
        tags["otel.status_code"].asText() shouldBe "ERROR"
    }

    "OK status adds otel.status_code but no error tag" {
        val line = serializer.serialize(aSpan(status = SpanStatus.OK))
        val tags = mapper.readTree(line)["tags"]
        tags["error"].shouldBeNull()
        tags["otel.status_code"].asText() shouldBe "OK"
    }

    "UNSET status emits neither tag" {
        val line = serializer.serialize(aSpan(status = SpanStatus.UNSET))
        val tags = mapper.readTree(line)["tags"]
        tags["error"].shouldBeNull()
        tags["otel.status_code"].shouldBeNull()
    }

    "string escaping covers quotes, newlines, and control chars" {
        val tricky = "line1\nline2\twith \"quotes\""
        val line = serializer.serialize(aSpan(name = tricky))
        mapper.readTree(line)["name"].asText() shouldBe tricky
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
