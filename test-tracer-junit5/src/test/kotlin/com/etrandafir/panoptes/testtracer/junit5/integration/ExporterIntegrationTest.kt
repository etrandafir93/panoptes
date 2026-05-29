package com.etrandafir.panoptes.testtracer.junit5.integration

import com.etrandafir.panoptes.testtracer.core.export.TestTracerSpanExporter
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import kotlin.io.path.readText

/**
 * Phase 2.5 cross-module integration test. Wires the real
 * [TestTracerSpanExporter] (writing NDJSON to a temp file) into an OTel SDK,
 * runs two sample JUnit tests via the Launcher, and asserts that the on-disk
 * file contains exactly the two root spans with the expected names, statuses,
 * and failure attribution.
 */
class ExporterIntegrationTest : FunSpec({

    val mapper = ObjectMapper()

    test("extension + TestTracerSpanExporter writes one root span per test to NDJSON") {
        val ndjsonPath = tempdir().toPath().resolve("spans.ndjson")
        val exporter = TestTracerSpanExporter(ndjsonPath)
        val sdk = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build(),
            )
            .build()
        IntegrationSetup.sdk = sdk

        try {
            runSample(IntegrationSamplePassing::class.java)
            runSample(IntegrationSampleFailing::class.java)
        } finally {
            sdk.close()
            IntegrationSetup.sdk = null
        }

        val lines = ndjsonPath.readText().lines().filter { it.isNotEmpty() }
        lines shouldHaveSize 2

        val spans = lines.map { mapper.readTree(it) }
        spans.map { it["name"].asText() } shouldContainExactlyInAnyOrder listOf(
            "test:IntegrationSamplePassing#shouldPass",
            "test:IntegrationSampleFailing#shouldFail",
        )

        val passing = spans.single { it["name"].asText().endsWith("shouldPass") }
        passing["status"]["code"].asText() shouldBe "STATUS_CODE_OK"
        passing.testStatusAttribute() shouldBe "PASSED"

        val failing = spans.single { it["name"].asText().endsWith("shouldFail") }
        failing["status"]["code"].asText() shouldBe "STATUS_CODE_ERROR"
        failing.testStatusAttribute() shouldBe "FAILED"
        failing.attribute("test.failure.message") shouldBe "boom"
        failing.attribute("test.failure.class") shouldBe "java.lang.RuntimeException"
    }
})

private fun runSample(testClass: Class<*>) {
    val launcher = LauncherFactory.create()
    val request = LauncherDiscoveryRequestBuilder.request()
        .selectors(selectClass(testClass))
        .build()
    launcher.execute(request)
}

private fun JsonNode.testStatusAttribute(): String? = attribute("test.status")

private fun JsonNode.attribute(key: String): String? {
    val attrs = this["attributes"] ?: return null
    return (0 until attrs.size())
        .map { attrs[it] }
        .firstOrNull { it["key"].asText() == key }
        ?.let { it["value"]["stringValue"].asText() }
}
