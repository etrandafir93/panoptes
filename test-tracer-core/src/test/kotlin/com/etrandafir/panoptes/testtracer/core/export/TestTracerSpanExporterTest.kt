package com.etrandafir.panoptes.testtracer.core.export

import com.etrandafir.panoptes.testtracer.core.io.NdjsonSpanWriter
import com.etrandafir.panoptes.testtracer.core.model.AttributeValue
import com.etrandafir.panoptes.testtracer.core.serialization.OtlpSpanDeserializer
import io.kotest.assertions.json.shouldContainJsonKeyValue
import io.kotest.assertions.json.shouldNotContainJsonKey
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlin.io.path.readText

class TestTracerSpanExporterTest : StringSpec({

    "exports each span as one NDJSON line" {
        val file = tempdir().toPath().resolve("spans.ndjson")
        val exporter = TestTracerSpanExporter(NdjsonSpanWriter(file))

        val result = exporter.export(listOf(aSpan(name = "first"), aSpan(name = "second")))
        result.isSuccess shouldBe true
        exporter.shutdown().isSuccess shouldBe true

        val lines = file.readText().lines().filter { it.isNotEmpty() }
        lines.map { OtlpSpanDeserializer.deserialize(it).name } shouldContainExactly listOf("first", "second")
    }

    "root span omits parentSpanId" {
        val file = tempdir().toPath().resolve("spans.ndjson")
        TestTracerSpanExporter(NdjsonSpanWriter(file)).use {
            it.export(listOf(aSpan())).isSuccess shouldBe true
        }
        file.readText().trim().shouldNotContainJsonKey("$.parentSpanId")
    }

    "child span carries parentSpanId from parent context" {
        val parent = SpanContext.create(
            "0102030405060708090a0b0c0d0e0f10",
            "1112131415161718",
            TraceFlags.getSampled(),
            TraceState.getDefault(),
        )
        val file = tempdir().toPath().resolve("spans.ndjson")
        TestTracerSpanExporter(NdjsonSpanWriter(file)).use {
            it.export(listOf(aSpan(parentSpanContext = parent))).isSuccess shouldBe true
        }
        file.readText().trim().shouldContainJsonKeyValue("$.parentSpanId", "1112131415161718")
    }

    "OTel SpanData fields map onto the internal span model end-to-end" {
        val file = tempdir().toPath().resolve("spans.ndjson")
        val span = aSpan(
            name = "GET /users",
            kind = SpanKind.SERVER,
            status = StatusData.create(StatusCode.ERROR, "downstream timeout"),
            attributes = Attributes.builder()
                .put(AttributeKey.stringKey("http.method"), "GET")
                .put(AttributeKey.longKey("http.status_code"), 500L)
                .build(),
            events = listOf(
                EventData.create(
                    1_000L,
                    "exception",
                    Attributes.of(AttributeKey.stringKey("exception.type"), "TimeoutException"),
                ),
            ),
            resource = Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), "checkout"),
            ),
            scope = InstrumentationScopeInfo.create("io.opentelemetry.servlet", "2.1.0", null),
        )

        TestTracerSpanExporter(NdjsonSpanWriter(file)).use {
            it.export(listOf(span)).isSuccess shouldBe true
        }

        val parsed = OtlpSpanDeserializer.deserialize(file.readText().trim())
        parsed.name shouldBe "GET /users"
        parsed.kind.name shouldBe "SERVER"
        parsed.status.code.name shouldBe "ERROR"
        parsed.status.description shouldBe "downstream timeout"
        parsed.attributes["http.method"] shouldBe AttributeValue.StringValue("GET")
        parsed.attributes["http.status_code"] shouldBe AttributeValue.LongValue(500L)
        parsed.events.single().name shouldBe "exception"
        parsed.events.single().attributes["exception.type"] shouldBe AttributeValue.StringValue("TimeoutException")
        parsed.resource.attributes["service.name"] shouldBe AttributeValue.StringValue("checkout")
        parsed.scope.name shouldBe "io.opentelemetry.servlet"
        parsed.scope.version shouldBe "2.1.0"
    }

    "flush succeeds without writing" {
        val file = tempdir().toPath().resolve("spans.ndjson")
        val exporter = TestTracerSpanExporter(NdjsonSpanWriter(file))
        exporter.flush().isSuccess shouldBe true
        exporter.shutdown()
    }

    "export after shutdown returns failure" {
        val file = tempdir().toPath().resolve("spans.ndjson")
        val exporter = TestTracerSpanExporter(NdjsonSpanWriter(file))
        exporter.shutdown().isSuccess shouldBe true
        exporter.export(listOf(aSpan())).isSuccess shouldBe false
    }
})

private fun aSpan(
    name: String = "test-span",
    kind: SpanKind = SpanKind.INTERNAL,
    parentSpanContext: SpanContext = SpanContext.getInvalid(),
    startEpochNanos: Long = 1_000,
    endEpochNanos: Long = 2_000,
    status: StatusData = StatusData.unset(),
    attributes: Attributes = Attributes.empty(),
    events: List<EventData> = emptyList(),
    resource: Resource = Resource.empty(),
    scope: InstrumentationScopeInfo = InstrumentationScopeInfo.create("test"),
): SpanData = TestSpanData.builder()
    .setName(name)
    .setKind(kind)
    .setStartEpochNanos(startEpochNanos)
    .setEndEpochNanos(endEpochNanos)
    .setStatus(status)
    .setHasEnded(true)
    .setAttributes(attributes)
    .setEvents(events)
    .setResource(resource)
    .setInstrumentationScopeInfo(scope)
    .setParentSpanContext(parentSpanContext)
    .setSpanContext(
        SpanContext.create(
            "00000000000000000000000000000001",
            "0000000000000002",
            TraceFlags.getSampled(),
            TraceState.getDefault(),
        ),
    )
    .build()
