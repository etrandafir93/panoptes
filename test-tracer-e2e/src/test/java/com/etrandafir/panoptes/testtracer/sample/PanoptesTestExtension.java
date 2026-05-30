package com.etrandafir.panoptes.testtracer.sample;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Registers the Spring-managed {@link OpenTelemetry} bean with
 * {@link GlobalOpenTelemetry} so that {@code TestTracerExtension} can pick it up.
 *
 * Spring Boot's OTel autoconfigure calls {@code .build()}, not
 * {@code .buildAndRegisterGlobal()}, so {@code GlobalOpenTelemetry.get()} would
 * otherwise return NOOP and no test root spans would be exported.
 *
 * Must be listed BEFORE {@code TestTracerExtension} in {@code @ExtendWith} so the
 * global is set before the extension tries to create the root span.
 */
class PanoptesTestExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        OpenTelemetry otel = SpringExtension.getApplicationContext(context)
                .getBean(OpenTelemetry.class);
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(otel);
    }
}
