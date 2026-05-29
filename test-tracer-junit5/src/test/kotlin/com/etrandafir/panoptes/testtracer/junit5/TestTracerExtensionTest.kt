package com.etrandafir.panoptes.testtracer.junit5

import com.etrandafir.panoptes.testtracer.junit5.samples.ExtensionSampleFailing
import com.etrandafir.panoptes.testtracer.junit5.samples.ExtensionSamplePassing
import com.etrandafir.panoptes.testtracer.junit5.samples.ExtensionSampleWithChild
import com.etrandafir.panoptes.testtracer.junit5.samples.SamplesSetup
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory

class TestTracerExtensionTest : FunSpec({

    beforeEach { SamplesSetup.exporter.reset() }

    test("passing test produces a single root span with status OK") {
        runSample(ExtensionSamplePassing::class.java)

        val spans = SamplesSetup.exporter.finishedSpanItems
        spans shouldHaveSize 1
        val span = spans.single()
        span.name shouldBe "test:ExtensionSamplePassing#shouldPass"
        span.status.statusCode shouldBe StatusCode.OK
        span.parentSpanContext.isValid shouldBe false
        span.attributes[AttributeKey.stringKey("test.status")] shouldBe "PASSED"
    }

    test("failing test produces a span with status ERROR and failure attributes") {
        runSample(ExtensionSampleFailing::class.java)

        val span = SamplesSetup.exporter.finishedSpanItems.single()
        span.status.statusCode shouldBe StatusCode.ERROR
        span.status.description shouldBe "boom"
        span.attributes[AttributeKey.stringKey("test.status")] shouldBe "FAILED"
        span.attributes[AttributeKey.stringKey("test.failure.message")] shouldBe "boom"
        span.attributes[AttributeKey.stringKey("test.failure.class")] shouldBe "java.lang.RuntimeException"
        span.events.any { it.name == "exception" } shouldBe true
    }

    test("test.class and test.method are stamped on the span") {
        runSample(ExtensionSamplePassing::class.java)

        val attributes = SamplesSetup.exporter.finishedSpanItems.single().attributes
        attributes[AttributeKey.stringKey("test.class")] shouldBe
            "com.etrandafir.panoptes.testtracer.junit5.samples.ExtensionSamplePassing"
        attributes[AttributeKey.stringKey("test.method")] shouldBe "shouldPass"
    }

    test("spans created during the test nest under the root span") {
        runSample(ExtensionSampleWithChild::class.java)

        val spans = SamplesSetup.exporter.finishedSpanItems
        spans shouldHaveSize 2
        val root = spans.single { it.name.startsWith("test:") }
        val child = spans.single { it.name == "child-work" }
        child.parentSpanContext.spanId shouldBe root.spanContext.spanId
        child.parentSpanContext.traceId shouldBe root.spanContext.traceId
    }

    context("status code maps from test outcome") {
        withData(
            mapOf(
                "passing → OK" to (ExtensionSamplePassing::class.java to StatusCode.OK),
                "failing → ERROR" to (ExtensionSampleFailing::class.java to StatusCode.ERROR),
            ),
        ) { (sample, expected) ->
            SamplesSetup.exporter.reset()
            runSample(sample)
            SamplesSetup.exporter.finishedSpanItems.first().status.statusCode shouldBe expected
        }
    }
})

private fun runSample(testClass: Class<*>) {
    val launcher = LauncherFactory.create()
    val request = LauncherDiscoveryRequestBuilder.request()
        .selectors(selectClass(testClass))
        .build()
    launcher.execute(request)
}
