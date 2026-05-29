package com.etrandafir.panoptes.testtracer.junit5.samples

import com.etrandafir.panoptes.testtracer.junit5.TestTracerExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Sample JUnit 5 test class that throws. Discovered via the Launcher only. */
class ExtensionSampleFailing {

    @JvmField
    @RegisterExtension
    val extension = TestTracerExtension(SamplesSetup.sdk)

    @Test
    fun shouldFail() {
        throw RuntimeException("boom")
    }
}
