package com.etrandafir.panoptes.testtracer.junit5.integration

import com.etrandafir.panoptes.testtracer.junit5.TestTracerExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Sample test used by the cross-module NDJSON integration test. Passing case. */
class IntegrationSamplePassing {

    @JvmField
    @RegisterExtension
    val extension = TestTracerExtension(checkNotNull(IntegrationSetup.sdk))

    @Test
    fun shouldPass() {
        // no-op
    }
}
