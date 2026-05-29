package com.etrandafir.panoptes.testtracer.starter

import com.etrandafir.panoptes.testtracer.core.export.TestTracerSpanExporter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.opentelemetry.sdk.trace.SpanProcessor
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class TestTracerAutoConfigurationTest : FunSpec({

    val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TestTracerAutoConfiguration::class.java))

    test("default — OTLP exporter and processor present, Zipkin beans absent") {
        runner.run { ctx ->
            ctx.containsBean("testTracerOtlpExporter") shouldBe true
            ctx.containsBean("testTracerOtlpProcessor") shouldBe true
            ctx.containsBean("testTracerZipkinExporter") shouldBe false
            ctx.containsBean("testTracerZipkinProcessor") shouldBe false
            ctx.getBean("testTracerOtlpExporter", TestTracerSpanExporter::class.java)
            ctx.getBean("testTracerOtlpProcessor", SpanProcessor::class.java)
        }
    }

    test("panoptes.test-tracer.enabled=false — no beans created") {
        runner.withPropertyValues("panoptes.test-tracer.enabled=false").run { ctx ->
            ctx.containsBean("testTracerOtlpExporter") shouldBe false
            ctx.containsBean("testTracerOtlpProcessor") shouldBe false
            ctx.containsBean("testTracerZipkinExporter") shouldBe false
            ctx.containsBean("testTracerZipkinProcessor") shouldBe false
        }
    }

    test("panoptes.test-tracer.zipkin.enabled=true — both OTLP and Zipkin bean pairs present") {
        runner.withPropertyValues("panoptes.test-tracer.zipkin.enabled=true").run { ctx ->
            ctx.containsBean("testTracerOtlpExporter") shouldBe true
            ctx.containsBean("testTracerOtlpProcessor") shouldBe true
            ctx.containsBean("testTracerZipkinExporter") shouldBe true
            ctx.containsBean("testTracerZipkinProcessor") shouldBe true
            ctx.getBean("testTracerZipkinExporter", TestTracerSpanExporter::class.java)
            ctx.getBean("testTracerZipkinProcessor", SpanProcessor::class.java)
        }
    }

    test("custom output dir + fileName — bound onto TestTracerProperties") {
        runner.withPropertyValues(
            "panoptes.test-tracer.output.dir=build/custom-traces",
            "panoptes.test-tracer.output.file-name=run-1.ndjson",
        ).run { ctx ->
            ctx.containsBean("testTracerOtlpExporter") shouldBe true
            val props = ctx.getBean(TestTracerProperties::class.java)
            props.output.dir shouldBe "build/custom-traces"
            props.output.fileName shouldBe "run-1.ndjson"
            props.output.path.toString().replace('\\', '/') shouldBe "build/custom-traces/run-1.ndjson"
        }
    }

    test("custom zipkin fileName — resolved against output.dir") {
        runner.withPropertyValues(
            "panoptes.test-tracer.output.dir=build/custom-traces",
            "panoptes.test-tracer.zipkin.enabled=true",
            "panoptes.test-tracer.zipkin.file-name=zk.ndjson",
        ).run { ctx ->
            val props = ctx.getBean(TestTracerProperties::class.java)
            props.zipkinPath.toString().replace('\\', '/') shouldBe "build/custom-traces/zk.ndjson"
        }
    }
})
