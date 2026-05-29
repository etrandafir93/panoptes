package com.etrandafir.panoptes.testtracer.starter

import com.etrandafir.panoptes.testtracer.core.export.TestTracerSpanExporter
import com.etrandafir.panoptes.testtracer.core.serialization.ZipkinJsonSerializer
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires a [TestTracerSpanExporter] writing NDJSON to disk, fronted by a
 * [SimpleSpanProcessor] so spans flush on `end()` — no batch-loss class of
 * bugs if a test JVM crashes mid-suite. Spring Boot's OTel autoconfiguration
 * (or Micrometer Tracing) picks up the additional [SpanProcessor] bean
 * automatically; we deliberately do not depend on the OTel SDK autoconfigure
 * SPI.
 *
 * Zipkin v2 output is opt-in via `panoptes.test-tracer.zipkin.enabled=true`
 * and writes to a separate file so the two formats never interleave.
 */
@AutoConfiguration
@EnableConfigurationProperties(TestTracerProperties::class)
@ConditionalOnProperty(prefix = "panoptes.test-tracer", name = ["enabled"], matchIfMissing = true)
class TestTracerAutoConfiguration {

    @Bean(name = ["testTracerOtlpExporter"], destroyMethod = "shutdown")
    fun testTracerOtlpExporter(props: TestTracerProperties): TestTracerSpanExporter =
        TestTracerSpanExporter(props.output.path)

    @Bean(name = ["testTracerOtlpProcessor"])
    fun testTracerOtlpProcessor(
        @Qualifier("testTracerOtlpExporter") exporter: TestTracerSpanExporter,
    ): SpanProcessor = SimpleSpanProcessor.create(exporter)

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        prefix = "panoptes.test-tracer.zipkin",
        name = ["enabled"],
        havingValue = "true",
    )
    class ZipkinAutoConfiguration {

        @Bean(name = ["testTracerZipkinExporter"], destroyMethod = "shutdown")
        fun testTracerZipkinExporter(props: TestTracerProperties): TestTracerSpanExporter =
            TestTracerSpanExporter(props.zipkinPath, ZipkinJsonSerializer())

        @Bean(name = ["testTracerZipkinProcessor"])
        fun testTracerZipkinProcessor(
            @Qualifier("testTracerZipkinExporter") exporter: TestTracerSpanExporter,
        ): SpanProcessor = SimpleSpanProcessor.create(exporter)
    }
}
