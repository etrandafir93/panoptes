package com.etrandafir.panoptes.testtracer.junit5

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Scope
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace

/**
 * JUnit 5 extension that opens a root span around each test method.
 *
 * - `beforeEach`: starts a span named `test:<SimpleClassName>#<methodName>`
 *   and makes it the current OTel context, so any spans the application
 *   creates during the test become children. The span and its [Scope] are
 *   stashed on the [ExtensionContext.Store].
 * - `afterEach`: stamps `test.class`, `test.method`, `test.status`; on
 *   failures also `test.failure.message`, `test.failure.class`, and records
 *   the exception as a span event. Sets span status to OK or ERROR based on
 *   `context.executionException`. Closes the scope and ends the span.
 *
 * Wiring:
 * - Class-level: `@ExtendWith(TestTracerExtension::class)`. JUnit uses the
 *   no-arg constructor which resolves the `OpenTelemetry` instance via
 *   `GlobalOpenTelemetry`.
 * - Instance-level: `@RegisterExtension val ext = TestTracerExtension(otel)`
 *   for tests of this extension and for callers that don't want to use the
 *   global registry.
 */
class TestTracerExtension(
    private val openTelemetry: OpenTelemetry,
) : BeforeEachCallback, AfterEachCallback {

    constructor() : this(GlobalOpenTelemetry.get())

    override fun beforeEach(context: ExtensionContext) {
        val tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE)
        val name = "test:${context.requiredTestClass.simpleName}#${context.requiredTestMethod.name}"
        val span = tracer.spanBuilder(name).startSpan()
        val scope = span.makeCurrent()
        val store = context.store()
        store.put(SPAN_KEY, span)
        store.put(SCOPE_KEY, scope)
    }

    override fun afterEach(context: ExtensionContext) {
        val store = context.store()
        val scope = store.get(SCOPE_KEY, Scope::class.java)
        val span = store.get(SPAN_KEY, Span::class.java) ?: return

        try {
            span.setAttribute("test.class", context.requiredTestClass.name)
            span.setAttribute("test.method", context.requiredTestMethod.name)
            val exception = context.executionException
            if (exception.isPresent) {
                val t = exception.get()
                span.setStatus(StatusCode.ERROR, t.message ?: t.javaClass.simpleName)
                span.setAttribute("test.status", "FAILED")
                span.setAttribute("test.failure.message", t.message ?: "")
                span.setAttribute("test.failure.class", t.javaClass.name)
                span.recordException(t)
            } else {
                span.setStatus(StatusCode.OK)
                span.setAttribute("test.status", "PASSED")
            }
        } finally {
            scope?.close()
            span.end()
        }
    }

    private fun ExtensionContext.store(): ExtensionContext.Store =
        getStore(Namespace.create(NAMESPACE))

    private companion object {
        const val NAMESPACE = "com.etrandafir.panoptes.testtracer.junit5"
        const val INSTRUMENTATION_SCOPE = "com.etrandafir.panoptes.testtracer.junit5"
        const val SPAN_KEY = "span"
        const val SCOPE_KEY = "scope"
    }
}
