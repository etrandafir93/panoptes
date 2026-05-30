package com.etrandafir.panoptes.testtracer.plugin

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
 * [ndjsonPattern], and renders a single HTML site under [outputDirectory]. Phase 4 ships a
 * placeholder renderer (counts + file list). Phase 5 swaps in the real renderer.
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
            throw MojoExecutionException("test-tracer:report found no NDJSON files matching $ndjsonPattern under $root (failOnEmpty=true)")
        }

        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw MojoExecutionException("Failed to create output directory: $outputDirectory")
        }

        val totalLines = matches.sumOf { runCatching { Files.lines(it).use { lines -> lines.count() } }.getOrDefault(0L) }

        val indexHtml = renderPlaceholderHtml(root, matches, totalLines)
        val indexFile = File(outputDirectory, "index.html")
        indexFile.writeText(indexHtml, Charsets.UTF_8)
        log.info("test-tracer:report — wrote ${indexFile.absolutePath}")
    }

    private fun findNdjsonFiles(root: Path, glob: String): List<Path> {
        if (!Files.exists(root)) return emptyList()
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$glob")
        Files.walk(root).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) }
                .filter { matcher.matches(root.relativize(it)) }
                .collect(Collectors.toList())
        }
    }

    private fun renderPlaceholderHtml(root: Path, files: List<Path>, totalLines: Long): String {
        val rows = if (files.isEmpty()) {
            "<tr><td colspan=\"2\"><em>No NDJSON files matched the pattern.</em></td></tr>"
        } else {
            files.joinToString("\n") { p ->
                val rel = root.relativize(p).invariantSeparatorsPathString
                val size = runCatching { Files.size(p) }.getOrDefault(0L)
                "<tr><td><code>${escape(rel)}</code></td><td>$size B</td></tr>"
            }
        }
        return """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<title>test-tracer report (placeholder)</title>
<style>body{font-family:sans-serif;max-width:960px;margin:2rem auto;padding:0 1rem}
table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:.5rem;text-align:left}</style>
</head>
<body>
<h1>test-tracer report</h1>
<p><strong>Phase 4 placeholder.</strong> The real renderer arrives in Phase 5.</p>
<p>Reactor root: <code>${escape(root.invariantSeparatorsPathString)}</code></p>
<p>NDJSON files: <strong>${files.size}</strong> — total lines (spans): <strong>$totalLines</strong></p>
<table><thead><tr><th>File</th><th>Size</th></tr></thead><tbody>
$rows
</tbody></table>
</body>
</html>
"""
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
