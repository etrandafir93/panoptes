package com.etrandafir.panoptes.testtracer.starter

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Configuration for the test-tracer Spring Boot starter.
 *
 * Bound under the `panoptes.test-tracer.*` namespace. Defaults match the
 * Maven-plugin discovery glob (`target/test-tracer/spans.ndjson`) so the
 * report step finds the file with zero configuration.
 */
@ConfigurationProperties(prefix = "panoptes.test-tracer")
data class TestTracerProperties(
    val enabled: Boolean = true,
    val output: Output = Output(),
    val zipkin: Zipkin = Zipkin(),
) {

    data class Output(
        val dir: String = "target/test-tracer",
        val fileName: String = "spans.ndjson",
    ) {
        val path: Path
            get() = Paths.get(dir, fileName)
    }

    /**
     * Opt-in Zipkin v2 JSON output. Written to a separate NDJSON file alongside
     * the primary OTLP file so neither format is corrupted by interleaving.
     */
    data class Zipkin(
        val enabled: Boolean = false,
        val fileName: String = "spans-zipkin.ndjson",
    )

    /**
     * Resolves the Zipkin output path against [Output.dir] — the Zipkin file
     * always lives next to the OTLP file.
     */
    val zipkinPath: Path
        get() = Paths.get(output.dir, zipkin.fileName)
}
