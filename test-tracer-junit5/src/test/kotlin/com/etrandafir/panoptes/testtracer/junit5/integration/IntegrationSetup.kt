package com.etrandafir.panoptes.testtracer.junit5.integration

import io.opentelemetry.sdk.OpenTelemetrySdk

/**
 * Static holder that the sample test classes resolve their per-test
 * [OpenTelemetrySdk] from at field-init time. The integration test sets
 * [sdk] before invoking the JUnit Platform Launcher.
 */
internal object IntegrationSetup {
    @Volatile
    var sdk: OpenTelemetrySdk? = null
}
