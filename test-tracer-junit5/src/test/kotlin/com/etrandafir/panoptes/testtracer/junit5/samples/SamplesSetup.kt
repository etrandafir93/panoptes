package com.etrandafir.panoptes.testtracer.junit5.samples

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

/**
 * Shared in-memory OTel SDK that the sample test classes register the
 * extension against. The actual tests reset the [exporter] before each run
 * and read the collected spans after the launcher returns.
 */
internal object SamplesSetup {
    val exporter: InMemorySpanExporter = InMemorySpanExporter.create()

    val sdk: OpenTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build(),
        )
        .build()
}
