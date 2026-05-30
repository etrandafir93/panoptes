package com.etrandafir.panoptes.testtracer.e2e

import com.etrandafir.panoptes.testtracer.core.export.TestTracerSpanExporter
import com.etrandafir.panoptes.testtracer.plugin.renderer.SpanAggregator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.nio.file.Files

/**
 * Verifies the full export pipeline: OTel SDK → TestTracerSpanExporter → NDJSON file
 * → SpanAggregator → AggregationResult.
 *
 * This test owns the runtime integration; it does NOT snapshot the HTML (see RendererE2eTest).
 */
class ExporterE2eTest : FunSpec({

    test("spans emitted via OTel SDK are written to NDJSON and parsed back by the aggregator") {
        val ndjsonFile = Files.createTempFile("e2e-exporter", ".ndjson").also {
            it.toFile().deleteOnExit()
        }

        val exporter = TestTracerSpanExporter(ndjsonFile)
        val sdkTracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()
        val sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .build()
        val tracer = sdk.getTracer("e2e-test")

        // Passing test with a child HTTP span
        val passingRoot = tracer.spanBuilder("test:E2eTest#passingTest").startSpan()
        passingRoot.setAttribute("test.class", "E2eTest")
        passingRoot.setAttribute("test.method", "passingTest")
        passingRoot.setAttribute("test.status", "PASSED")
        Context.current().with(passingRoot).makeCurrent().use {
            tracer.spanBuilder("http:GET /hello").startSpan().apply {
                setStatus(StatusCode.OK)
                end()
            }
        }
        passingRoot.setStatus(StatusCode.OK)
        passingRoot.end()

        // Failing test
        val failingRoot = tracer.spanBuilder("test:E2eTest#failingTest").startSpan()
        failingRoot.setAttribute("test.class", "E2eTest")
        failingRoot.setAttribute("test.method", "failingTest")
        failingRoot.setAttribute("test.status", "FAILED")
        failingRoot.setAttribute("test.failure.message", "expected 1 but was 2")
        failingRoot.setStatus(StatusCode.ERROR, "expected 1 but was 2")
        failingRoot.end()

        exporter.shutdown()

        val result = SpanAggregator.aggregate(listOf(ndjsonFile))

        result.tests shouldHaveSize 2
        result.orphans.spans shouldHaveSize 0

        val passing = result.tests.first { it.name == "E2eTest#passingTest" }
        passing.status shouldBe "STATUS_CODE_OK"
        passing.spanCount shouldBe 2

        val tree = result.traces[passing.traceId]!!
        tree.children shouldHaveSize 1
        tree.children[0].root.name shouldBe "http:GET /hello"

        val failing = result.tests.first { it.name == "E2eTest#failingTest" }
        failing.status shouldBe "STATUS_CODE_ERROR"
        failing.failureMessage shouldBe "expected 1 but was 2"
    }

    test("orphan spans (no test.method) land in the orphan bucket") {
        val ndjsonFile = Files.createTempFile("e2e-orphan", ".ndjson").also {
            it.toFile().deleteOnExit()
        }

        val exporter = TestTracerSpanExporter(ndjsonFile)
        val sdkTracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build()
        val sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(sdkTracerProvider)
            .build()
        val tracer = sdk.getTracer("e2e-orphan")

        tracer.spanBuilder("background:cleanup-job").startSpan().end()

        exporter.shutdown()

        val result = SpanAggregator.aggregate(listOf(ndjsonFile))

        result.tests shouldHaveSize 0
        result.orphans.spans shouldHaveSize 1
        result.orphans.spans[0].name shouldBe "background:cleanup-job"
    }
})
