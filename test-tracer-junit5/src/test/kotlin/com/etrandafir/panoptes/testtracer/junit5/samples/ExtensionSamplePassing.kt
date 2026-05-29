package com.etrandafir.panoptes.testtracer.junit5.samples

import com.etrandafir.panoptes.testtracer.junit5.TestTracerExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Sample JUnit 5 test class that passes. Discovered explicitly by
 * `TestTracerExtensionTest` via the JUnit Platform Launcher. Surefire ignores
 * it because the class name does not match `*Test`/`*Tests`/`*TestCase`.
 */
class ExtensionSamplePassing {

    @JvmField
    @RegisterExtension
    val extension = TestTracerExtension(SamplesSetup.sdk)

    @Test
    fun shouldPass() {
        // no-op
    }
}
