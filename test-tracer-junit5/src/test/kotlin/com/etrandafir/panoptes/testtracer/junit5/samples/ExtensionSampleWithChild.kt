package com.etrandafir.panoptes.testtracer.junit5.samples

import com.etrandafir.panoptes.testtracer.junit5.TestTracerExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Sample that opens an additional span during the test. With the extension's
 * root span as the current context, the new span should nest underneath it.
 */
class ExtensionSampleWithChild {

    @JvmField
    @RegisterExtension
    val extension = TestTracerExtension(SamplesSetup.sdk)

    @Test
    fun shouldNestChildSpan() {
        val tracer = SamplesSetup.sdk.getTracer("sample")
        val child = tracer.spanBuilder("child-work").startSpan()
        child.end()
    }
}
