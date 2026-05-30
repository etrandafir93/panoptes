package com.etrandafir.panoptes.testtracer.sample;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Builds a test-scoped {@link OpenTelemetry} instance that is explicitly wired
 * to the {@code testTracerOtlpProcessor} bean.
 *
 * Spring Boot's autoconfigured {@code otelSdkTracerProvider} does not reliably
 * include our {@code SpanProcessor} bean due to autoconfiguration ordering —
 * creating our own provider here guarantees spans reach the NDJSON exporter.
 */
@TestConfiguration(proxyBeanMethods = false)
class PanoptesTestConfig {

    @Bean
    @Primary
    OpenTelemetry panoptesTestOpenTelemetry(
            @Qualifier("testTracerOtlpProcessor") SpanProcessor testTracerProcessor) {
        var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(testTracerProcessor)
                .build();
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }
}
