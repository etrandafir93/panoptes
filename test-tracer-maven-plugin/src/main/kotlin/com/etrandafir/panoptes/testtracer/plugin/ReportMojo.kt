package com.etrandafir.panoptes.testtracer.plugin

import com.etrandafir.panoptes.testtracer.plugin.renderer.DetailPageRenderer
import com.etrandafir.panoptes.testtracer.plugin.renderer.IndexPageRenderer
import com.etrandafir.panoptes.testtracer.plugin.renderer.OrphanPageRenderer
import com.etrandafir.panoptes.testtracer.plugin.renderer.SpanAggregator
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Aggregator goal: walks the multi-module reactor root, finds every per-module NDJSON file matching
 * [ndjsonPattern], aggregates all spans, and renders a static HTML site under [outputDirectory].
 *
 * The site contains:
 * - `index.html` — test table (failed first, then by duration desc)
 * - `<traceId>.html` — per-test waterfall detail page
 * - `orphans.html` — spans with no test-attributed root (rendered only when orphans exist)
 */
@Mojo(
    name = "report",
    aggregator = true,
    defaultPhase = LifecyclePhase.VERIFY,
    threadSafe = true,
)
class ReportMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    lateinit var project: MavenProject

    /**
     * Glob (relative to the reactor root) used to locate per-module NDJSON span files. Default
     * matches the starter's default output location.
     */
    @Parameter(property = "panoptes.testTracer.ndjsonPattern", defaultValue = "**/target/test-tracer/spans.ndjson")
    lateinit var ndjsonPattern: String

    /**
     * Root of the reactor. Defaults to the project the aggregator runs in (i.e. the parent pom).
     */
    @Parameter(defaultValue = "\${project.basedir}", required = true)
    lateinit var baseDir: File

    /**
     * Output directory for the rendered HTML site.
     */
    @Parameter(property = "panoptes.testTracer.outputDirectory", defaultValue = "\${project.build.directory}/test-tracer/site")
    lateinit var outputDirectory: File

    /**
     * If true, fail the build when no NDJSON files were found. Defaults to false so the goal can
     * stay wired in pipelines that occasionally skip tests.
     */
    @Parameter(property = "panoptes.testTracer.failOnEmpty", defaultValue = "false")
    var failOnEmpty: Boolean = false

    override fun execute() {
        val root = baseDir.toPath().toAbsolutePath().normalize()
        log.info("test-tracer:report — scanning $root for $ndjsonPattern")

        val matches = findNdjsonFiles(root, ndjsonPattern)
        log.info("test-tracer:report — found ${matches.size} NDJSON file(s)")

        if (matches.isEmpty() && failOnEmpty) {
            throw MojoExecutionException(
                "test-tracer:report found no NDJSON files matching $ndjsonPattern under $root (failOnEmpty=true)"
            )
        }

        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw MojoExecutionException("Failed to create output directory: $outputDirectory")
        }

        val result = SpanAggregator.aggregate(matches)
        log.info(
            "test-tracer:report — aggregated ${result.tests.size} test(s), " +
                "${result.orphans.spans.size} orphan span(s)"
        )

        // index.html
        val indexHtml = IndexPageRenderer.render(result)
        File(outputDirectory, "index.html").writeText(indexHtml, Charsets.UTF_8)
        log.info("test-tracer:report — wrote index.html")

        // per-test detail pages
        for (entry in result.tests) {
            val tree = result.traces[entry.traceId] ?: continue
            val html = DetailPageRenderer.render(entry, tree)
            File(outputDirectory, "${entry.traceId}.html").writeText(html, Charsets.UTF_8)
        }
        if (result.tests.isNotEmpty()) {
            log.info("test-tracer:report — wrote ${result.tests.size} detail page(s)")
        }

        // orphans.html (only when orphans exist)
        if (result.orphans.spans.isNotEmpty()) {
            val html = OrphanPageRenderer.render(result.orphans)
            File(outputDirectory, "orphans.html").writeText(html, Charsets.UTF_8)
            log.info("test-tracer:report — wrote orphans.html (${result.orphans.spans.size} orphan span(s))")
        }
    }

    private fun findNdjsonFiles(root: Path, glob: String): List<Path> {
        if (!Files.exists(root)) return emptyList()
        // Java's PathMatcher with glob:** does not match zero-depth paths on all JVMs,
        // so we also build a matcher without the leading **/ prefix for direct-child matches.
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$glob")
        // Strip a leading "**/" so "target/..." also matches when there's no parent directory.
        val strippedGlob = if (glob.startsWith("**/")) glob.removePrefix("**/") else null
        val strippedMatcher = strippedGlob?.let { FileSystems.getDefault().getPathMatcher("glob:$it") }
        Files.walk(root).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) }
                .filter { path ->
                    val rel = root.relativize(path)
                    matcher.matches(rel) || strippedMatcher?.matches(rel) == true
                }
                .collect(Collectors.toList())
        }
    }
}
