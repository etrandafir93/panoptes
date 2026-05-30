package com.etrandafir.panoptes.testtracer.plugin.renderer

import com.etrandafir.panoptes.testtracer.plugin.renderer.model.AggregationResult
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.OrphanGroup
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.SpanData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Unit tests covering Phase 5 renderer components.
 * Spans 10+ test cases across SpanAggregator, IndexPageRenderer, DetailPageRenderer, OrphanPageRenderer.
 */
class RendererTest : FunSpec({

    // ── helpers ────────────────────────────────────────────────────────────────

    fun testSpanLine(
        traceId: String = "trace001",
        spanId: String = "span001",
        parentSpanId: String? = null,
        name: String = "test:SomeClass#someMethod",
        startNano: Long = 1_000_000_000L,
        endNano: Long = 2_000_000_000L,
        statusCode: String = "STATUS_CODE_OK",
        testClass: String? = "SomeClass",
        testMethod: String? = "someMethod",
        failureMessage: String? = null,
    ): String {
        val parentField = if (parentSpanId != null) """"parentSpanId":"$parentSpanId",""" else ""
        val attrs = buildList {
            if (testClass != null) add("""{"key":"test.class","value":{"stringValue":"$testClass"}}""")
            if (testMethod != null) add("""{"key":"test.method","value":{"stringValue":"$testMethod"}}""")
            if (failureMessage != null) add("""{"key":"test.failure.message","value":{"stringValue":"$failureMessage"}}""")
        }.joinToString(",")
        return """{"traceId":"$traceId","spanId":"$spanId",${parentField}"name":"$name","kind":"SPAN_KIND_INTERNAL","startTimeUnixNano":"$startNano","endTimeUnixNano":"$endNano","status":{"code":"$statusCode"},"attributes":[$attrs],"events":[],"resource":{"attributes":[]},"scope":{"name":"test-tracer"}}"""
    }

    fun orphanSpanLine(
        traceId: String = "orphanTrace",
        spanId: String = "orphanSpan",
        name: String = "background-task",
        startNano: Long = 5_000_000_000L,
        endNano: Long = 5_500_000_000L,
    ) = """{"traceId":"$traceId","spanId":"$spanId","name":"$name","kind":"SPAN_KIND_INTERNAL","startTimeUnixNano":"$startNano","endTimeUnixNano":"$endNano","status":{"code":"STATUS_CODE_UNSET"},"attributes":[],"events":[],"resource":{"attributes":[]},"scope":{"name":"test-tracer"}}"""

    // ── SpanAggregator ──────────────────────────────────────────────────────────

    test("SpanAggregator groups a single test span into a TestEntry") {
        val lines = listOf(testSpanLine(traceId = "t1", testClass = "MyTest", testMethod = "myMethod"))
        val result = SpanAggregator.aggregateLines(lines)

        result.tests shouldHaveSize 1
        result.tests[0].name shouldBe "MyTest#myMethod"
        result.tests[0].status shouldBe "STATUS_CODE_OK"
        result.traces.containsKey("t1") shouldBe true
        result.orphans.spans shouldHaveSize 0
    }

    test("SpanAggregator routes orphan spans (no test.method) to the orphan group") {
        val lines = listOf(orphanSpanLine(traceId = "orphan1"))
        val result = SpanAggregator.aggregateLines(lines)

        result.tests shouldHaveSize 0
        result.orphans.spans shouldHaveSize 1
        result.orphans.spans[0].traceId shouldBe "orphan1"
    }

    test("SpanAggregator handles multiple traces, separating tests from orphans") {
        val lines = listOf(
            testSpanLine(traceId = "t1", testMethod = "method1"),
            testSpanLine(traceId = "t2", testMethod = "method2", statusCode = "STATUS_CODE_ERROR"),
            orphanSpanLine(traceId = "o1"),
        )
        val result = SpanAggregator.aggregateLines(lines)

        result.tests shouldHaveSize 2
        result.orphans.spans shouldHaveSize 1
    }

    test("SpanAggregator builds parent-child TraceTree correctly") {
        val rootLine = testSpanLine(traceId = "t1", spanId = "root", testMethod = "m")
        val childLine = testSpanLine(
            traceId = "t1", spanId = "child", parentSpanId = "root",
            name = "child-span", testClass = null, testMethod = null,
        )
        val result = SpanAggregator.aggregateLines(listOf(rootLine, childLine))

        result.tests shouldHaveSize 1
        result.tests[0].spanCount shouldBe 2
        val tree = result.traces["t1"]!!
        tree.root.spanId shouldBe "root"
        tree.children shouldHaveSize 1
        tree.children[0].root.spanId shouldBe "child"
    }

    test("SpanAggregator returns noFilesFound=true when called with empty list via aggregate()") {
        val result = SpanAggregator.aggregate(emptyList())
        result.noFilesFound shouldBe true
        result.tests shouldHaveSize 0
    }

    test("SpanAggregator records failure message from test.failure.message attribute") {
        val line = testSpanLine(
            traceId = "t1",
            statusCode = "STATUS_CODE_ERROR",
            failureMessage = "expected 1 but was 2",
        )
        val result = SpanAggregator.aggregateLines(listOf(line))
        result.tests[0].failureMessage shouldBe "expected 1 but was 2"
    }

    // ── IndexPageRenderer ───────────────────────────────────────────────────────

    test("IndexPageRenderer sorts ERROR tests before OK tests") {
        val lines = listOf(
            testSpanLine(traceId = "t1", testMethod = "passingTest", statusCode = "STATUS_CODE_OK", startNano = 1000L, endNano = 2000L),
            testSpanLine(traceId = "t2", testMethod = "failingTest", statusCode = "STATUS_CODE_ERROR", startNano = 1000L, endNano = 3000L),
        )
        val result = SpanAggregator.aggregateLines(lines)
        val html = IndexPageRenderer.render(result)

        val failIndex = html.indexOf("failingTest")
        val passIndex = html.indexOf("passingTest")
        assert(failIndex < passIndex) { "failing test should appear before passing test in HTML" }
    }

    test("IndexPageRenderer sorts by durationNanos descending within same status") {
        val lines = listOf(
            testSpanLine(traceId = "t1", testMethod = "short", statusCode = "STATUS_CODE_OK", startNano = 0L, endNano = 100L),
            testSpanLine(traceId = "t2", testMethod = "long", statusCode = "STATUS_CODE_OK", startNano = 0L, endNano = 10000L),
        )
        val result = SpanAggregator.aggregateLines(lines)
        val html = IndexPageRenderer.render(result)

        val longIdx = html.indexOf("long")
        val shortIdx = html.indexOf("short")
        assert(longIdx < shortIdx) { "longer test should appear first within same status" }
    }

    test("IndexPageRenderer HTML-escapes test names with special characters") {
        val line = testSpanLine(traceId = "t1", testMethod = "test<script>alert(1)</script>")
        val result = SpanAggregator.aggregateLines(listOf(line))
        val html = IndexPageRenderer.render(result)

        html shouldNotContain "<script>"
        html shouldContain "&lt;script&gt;"
    }

    test("IndexPageRenderer renders diagnostic empty-state when no tests and noFilesFound") {
        val result = SpanAggregator.aggregate(emptyList())
        val html = IndexPageRenderer.render(result)

        html shouldContain "panoptes test-tracer report"
        html shouldContain "No spans recorded"
        html shouldNotContain "<table"
    }

    test("IndexPageRenderer links to orphans.html when orphan spans exist") {
        val lines = listOf(orphanSpanLine())
        val result = SpanAggregator.aggregateLines(lines)
        val html = IndexPageRenderer.render(result)

        html shouldContain "orphans.html"
    }

    // ── Waterfall percentage calculations ──────────────────────────────────────

    test("computeLeftPct: root span at trace start has left=0") {
        val left = DetailPageRenderer.computeLeftPct(
            startNanos = 1000L,
            traceStartNanos = 1000L,
            traceDurationNanos = 1000L,
        )
        left shouldBe (0.0 plusOrMinus 0.001)
    }

    test("computeLeftPct: span starting halfway through trace has left~50") {
        val left = DetailPageRenderer.computeLeftPct(
            startNanos = 1500L,
            traceStartNanos = 1000L,
            traceDurationNanos = 1000L,
        )
        left shouldBe (50.0 plusOrMinus 0.001)
    }

    test("computeWidthPct: span covering full trace has width~100") {
        val width = DetailPageRenderer.computeWidthPct(
            startNanos = 1000L,
            endNanos = 2000L,
            traceDurationNanos = 1000L,
        )
        width shouldBe (100.0 plusOrMinus 0.001)
    }

    test("computeWidthPct: span covering half trace has width~50") {
        val width = DetailPageRenderer.computeWidthPct(
            startNanos = 1000L,
            endNanos = 1500L,
            traceDurationNanos = 1000L,
        )
        width shouldBe (50.0 plusOrMinus 0.001)
    }

    // ── OrphanPageRenderer ──────────────────────────────────────────────────────

    // ── 7.1 failOnEmpty / 7.2 testsSkipped diagnostic ─────────────────────────

    test("IndexPageRenderer shows 'Tests were skipped' diagnostic when testsSkipped=true") {
        val result = SpanAggregator.aggregate(emptyList()).copy(testsSkipped = true)
        val html = IndexPageRenderer.render(result)

        html shouldContain "Tests were skipped"
        html shouldContain "skipTests"
        html shouldNotContain "No spans recorded"
        html shouldNotContain "<table"
    }

    test("IndexPageRenderer shows 'No spans recorded' when noFilesFound and not skipped") {
        val result = SpanAggregator.aggregate(emptyList())
        val html = IndexPageRenderer.render(result)

        html shouldContain "No spans recorded"
        html shouldNotContain "Tests were skipped"
    }

    test("IndexPageRenderer shows 'Tests ran but no spans' when files found but empty") {
        val result = AggregationResult(
            tests = emptyList(),
            traces = emptyMap(),
            orphans = OrphanGroup(emptyList()),
            noFilesFound = false,
            filesFoundButEmpty = true,
        )
        val html = IndexPageRenderer.render(result)

        html shouldContain "Tests ran but no spans were exported"
        html shouldNotContain "Tests were skipped"
    }

    // ── SequenceDiagramGenerator ────────────────────────────────────────────────

    test("SequenceDiagramGenerator lifeline prefers service.name attribute") {
        val span = SpanData(
            traceId = "t1", spanId = "s1", parentSpanId = null,
            name = "http:GET /users",
            startEpochNanos = 0L, endEpochNanos = 1L,
            statusCode = "STATUS_CODE_OK", statusMessage = null,
            attributes = mapOf("service.name" to "my-service"),
            rawAttributes = emptyList(), events = emptyList(),
        )
        SequenceDiagramGenerator.lifeline(span) shouldBe "my-service"
    }

    test("SequenceDiagramGenerator lifeline falls back to span-name prefix before ':'") {
        val span = SpanData(
            traceId = "t1", spanId = "s1", parentSpanId = null,
            name = "http:GET /users",
            startEpochNanos = 0L, endEpochNanos = 1L,
            statusCode = "STATUS_CODE_OK", statusMessage = null,
            attributes = emptyMap(), rawAttributes = emptyList(), events = emptyList(),
        )
        SequenceDiagramGenerator.lifeline(span) shouldBe "http"
    }

    test("SequenceDiagramGenerator lifeline falls back to full name when no colon") {
        val span = SpanData(
            traceId = "t1", spanId = "s1", parentSpanId = null,
            name = "background-task",
            startEpochNanos = 0L, endEpochNanos = 1L,
            statusCode = "STATUS_CODE_UNSET", statusMessage = null,
            attributes = emptyMap(), rawAttributes = emptyList(), events = emptyList(),
        )
        SequenceDiagramGenerator.lifeline(span) shouldBe "background-task"
    }

    test("SequenceDiagramGenerator generates @startuml/@enduml markers") {
        val lines = listOf(testSpanLine(traceId = "t1", spanId = "root", testMethod = "m"))
        val result = SpanAggregator.aggregateLines(lines)
        val tree = result.traces["t1"]!!
        val puml = SequenceDiagramGenerator.generate(tree)

        puml shouldContain "@startuml"
        puml shouldContain "@enduml"
    }

    test("SequenceDiagramGenerator emits arrow from parent to child lifeline") {
        val rootLine = testSpanLine(traceId = "t1", spanId = "root", name = "test:MyTest#m", testMethod = "m")
        val childLine = testSpanLine(
            traceId = "t1", spanId = "child", parentSpanId = "root",
            name = "http:GET /api", testClass = null, testMethod = null,
        )
        val result = SpanAggregator.aggregateLines(listOf(rootLine, childLine))
        val tree = result.traces["t1"]!!
        val puml = SequenceDiagramGenerator.generate(tree)

        puml shouldContain "test -> http"
        puml shouldContain "activate http"
        puml shouldContain "http --> test"
        puml shouldContain "deactivate http"
    }

    test("DetailPageRenderer HTML contains both waterfall and sequence-diagram sections") {
        val rootLine = testSpanLine(traceId = "t1", spanId = "root", testMethod = "m")
        val childLine = testSpanLine(
            traceId = "t1", spanId = "child", parentSpanId = "root",
            name = "http:GET /api", testClass = null, testMethod = null,
        )
        val result = SpanAggregator.aggregateLines(listOf(rootLine, childLine))
        val entry = result.tests[0]
        val tree = result.traces["t1"]!!
        val html = DetailPageRenderer.render(entry, tree)

        html shouldContain "Waterfall"
        html shouldContain "Sequence diagram"
        html shouldContain "waterfall-container"
        html shouldContain "seq-diagram"
        html shouldContain "<svg"
    }

    // ── OrphanPageRenderer ──────────────────────────────────────────────────────

    test("OrphanPageRenderer lists all orphan spans in a table") {
        val lines = listOf(
            orphanSpanLine(spanId = "o1", name = "task-alpha"),
            orphanSpanLine(spanId = "o2", name = "task-beta", traceId = "orphanTrace2"),
        )
        val result = SpanAggregator.aggregateLines(lines)
        val html = OrphanPageRenderer.render(result.orphans)

        html shouldContain "task-alpha"
        html shouldContain "task-beta"
        html shouldContain "index.html"  // back link to index
        html shouldContain "orphan"      // page describes orphan spans
    }
})
