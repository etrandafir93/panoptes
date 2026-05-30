package com.etrandafir.panoptes.testtracer.e2e

import com.etrandafir.panoptes.testtracer.plugin.renderer.DetailPageRenderer
import com.etrandafir.panoptes.testtracer.plugin.renderer.IndexPageRenderer
import com.etrandafir.panoptes.testtracer.plugin.renderer.OrphanPageRenderer
import com.etrandafir.panoptes.testtracer.plugin.renderer.SpanAggregator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

/**
 * Snapshot tests for the full NDJSON → HTML rendering pipeline.
 *
 * Input: a static fixture file with known, fixed span data (deterministic IDs + timestamps).
 * Snapshots: stored in src/test/resources/__snapshots__/ and committed to the repo.
 *
 * To regenerate snapshots after intentional HTML changes:
 *   ./mvnw test -pl test-tracer-e2e -Dupdate.snapshots=true
 */
class RendererE2eTest : FunSpec({

    val fixtureFile = Paths.get(
        RendererE2eTest::class.java.getResource("/fixtures/traces.ndjson")!!.toURI()
    )
    val result = SpanAggregator.aggregate(listOf(fixtureFile))

    // Sanity-check the fixture parsed correctly before snapshotting anything
    test("fixture parses to 2 tests and 1 orphan") {
        result.tests.size shouldBe 2
        result.orphans.spans.size shouldBe 1
    }

    test("index.html snapshot") {
        val html = IndexPageRenderer.render(result)
        SnapshotHelper.matchSnapshot("index", html.bodyForSnapshot())
    }

    test("detail page for passing test snapshot") {
        val entry = result.tests.first { it.name == "MyTest#passingTest" }
        val tree = result.traces[entry.traceId]!!
        val html = DetailPageRenderer.render(entry, tree)
        SnapshotHelper.matchSnapshot("detail-passing", html.bodyForSnapshot())
    }

    test("detail page for failing test snapshot") {
        val entry = result.tests.first { it.name == "MyTest#failingTest" }
        val tree = result.traces[entry.traceId]!!
        val html = DetailPageRenderer.render(entry, tree)
        SnapshotHelper.matchSnapshot("detail-failing", html.bodyForSnapshot())
    }

    test("orphans.html snapshot") {
        val html = OrphanPageRenderer.render(result.orphans)
        SnapshotHelper.matchSnapshot("orphans", html.bodyForSnapshot())
    }
})

/**
 * Extracts the <body> content and strips the inline SVG (non-deterministic PlantUML output)
 * so snapshots stay stable across PlantUML version bumps.
 */
private fun String.bodyForSnapshot(): String {
    val body = substringAfter("<body>\n").substringBefore("\n</body>").trim()
    return body.replace(Regex("<svg[^>]*>[\\s\\S]*?</svg>"), "<svg>[sequence diagram]</svg>")
}
