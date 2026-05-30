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
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.json.shouldEqualSpecifiedJson
import io.kotest.assertions.json.shouldNotContainJsonKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.string.shouldNotContain

class ZipkinJsonSerializerTest : FunSpec({

    val serializer = ZipkinJsonSerializer()

    test("output is a single line") {
        serializer.serialize(aSpan()).shouldNotContain("\n")
    }

    test("core span fields use Zipkin v2 names and microsecond timestamps") {
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
        line.shouldEqualSpecifiedJson(
            """
            {
              "traceId": "0102030405060708090a0b0c0d0e0f10",
              "id": "1112131415161718",
              "parentId": "2122232425262728",
              "name": "GET /users",
              "kind": "SERVER",
              "timestamp": 1700000000000000,
              "duration": 500000
            }
            """.trimIndent(),
        )
    }

    test("parentId omitted for root spans") {
        serializer.serialize(aSpan(parentSpanId = null)).shouldNotContainJsonKey("$.parentId")
    }

    test("kind omitted entirely for INTERNAL spans") {
        serializer.serialize(aSpan(kind = SpanKind.INTERNAL)).shouldNotContainJsonKey("$.kind")
    }

    test("localEndpoint.serviceName comes from resource attribute") {
        serializer.serialize(aSpan(resource = Resource(Attributes.of("service.name" to StringValue("checkout")))))
            .shouldContainJsonKeyValue("$.localEndpoint.serviceName", "checkout")
    }

    test("localEndpoint.serviceName defaults to 'unknown' when missing") {
        serializer.serialize(aSpan(resource = Resource.EMPTY))
            .shouldContainJsonKeyValue("$.localEndpoint.serviceName", "unknown")
    }

    test("events become annotations with microsecond timestamps and drop attributes") {
        val line = serializer.serialize(
            aSpan(
                events = listOf(
                    SpanEvent(1_000_000, "exception", Attributes.of("type" to StringValue("X"))),
                    SpanEvent(2_000_000, "log", Attributes.EMPTY),
                ),
            ),
        )
        line.shouldEqualSpecifiedJson(
            """
            {
              "annotations": [
                { "timestamp": 1000, "value": "exception" },
                { "timestamp": 2000, "value": "log" }
              ]
            }
            """.trimIndent(),
        )
    }

    test("attributes stringified into flat tags") {
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
        line.shouldEqualSpecifiedJson(
            """
            {
              "tags": {
                "http.method": "GET",
                "http.status_code": "200",
                "http.cached": "true",
                "http.duration_ms": "12.5",
                "http.peer_ids": "1,2,3"
              }
            }
            """.trimIndent(),
        )
    }

    context("status mapping") {
        withData(
            nameFn = { it.label },
            StatusCase("ERROR adds error and otel.status_code", SpanStatus.error("downstream timeout"), expectedError = "downstream timeout", expectedCode = "ERROR"),
            StatusCase("OK adds otel.status_code only", SpanStatus.OK, expectedError = null, expectedCode = "OK"),
            StatusCase("UNSET emits neither tag", SpanStatus.UNSET, expectedError = null, expectedCode = null),
        ) { case ->
            val line = serializer.serialize(aSpan(status = case.status))
            if (case.expectedError == null) line.shouldNotContainJsonKey("$.tags.error")
            else line.shouldContainJsonKeyValue("$.tags.error", case.expectedError)

            val codePath = "$.tags['otel.status_code']"
            if (case.expectedCode == null) line.shouldNotContainJsonKey(codePath)
            else line.shouldContainJsonKeyValue(codePath, case.expectedCode)
        }
    }

    test("string escaping covers quotes, newlines, and control chars") {
        val tricky = "line1\nline2\twith \"quotes\""
        serializer.serialize(aSpan(name = tricky)).shouldContainJsonKeyValue("$.name", tricky)
    }
})

private data class StatusCase(
    val label: String,
    val status: SpanStatus,
    val expectedError: String?,
    val expectedCode: String?,
)

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
