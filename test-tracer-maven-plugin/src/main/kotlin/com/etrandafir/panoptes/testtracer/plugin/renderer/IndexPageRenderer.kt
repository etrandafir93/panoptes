package com.etrandafir.panoptes.testtracer.plugin.renderer

import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.durationMs
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.escape
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.loadCss
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.pageHtml
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.statusBadgeHtml
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.AggregationResult

/**
 * Renders the `index.html` landing page.
 *
 * Sorting: STATUS_CODE_ERROR first, then by durationNanos descending within each status group.
 * Each test row links to `<traceId>.html`.
 * Orphan row links to `orphans.html` when orphans exist.
 * Empty states for no-files and no-spans cases.
 */
object IndexPageRenderer {

    fun render(result: AggregationResult): String {
        val css = loadCss()
        val body = buildString {
            append("""<header><div class="container"><h1>panoptes test-tracer report</h1>""")
            if (result.tests.isNotEmpty()) {
                val total = result.tests.size
                val failed = result.tests.count { it.status.endsWith("ERROR") }
                append("""<div class="subtitle">$total test(s) — $failed failed</div>""")
            }
            append("</div></header>\n")
            append("""<div class="container">""")
            append("\n")

            when {
                result.noFilesFound -> appendDiagnostic(
                    this,
                    "No spans recorded",
                    "No NDJSON files were found. Either tests were skipped, or the " +
                        "<code>test-tracer-spring-boot-starter</code> is not on the test classpath.",
                )
                result.tests.isEmpty() && result.orphans.spans.isEmpty() -> appendDiagnostic(
                    this,
                    "Tests ran but no spans were exported",
                    "NDJSON files were found but contained no parseable span lines. " +
                        "Check that <code>panoptes.test-tracer.enabled=true</code> in your test configuration " +
                        "and that the starter is loaded.",
                )
                result.tests.isEmpty() -> {
                    appendDiagnostic(
                        this,
                        "No test spans found",
                        "Spans were recorded but none had <code>test.method</code> attributes. " +
                            "All spans are in the orphan bucket.",
                    )
                    appendOrphanLink(this, result.orphans.spans.size)
                }
                else -> {
                    appendTestTable(this, result)
                    if (result.orphans.spans.isNotEmpty()) {
                        append("\n")
                        appendOrphanLink(this, result.orphans.spans.size)
                    }
                }
            }

            append("\n</div>\n")
        }
        return pageHtml("panoptes test-tracer report", css, body)
    }

    private fun appendDiagnostic(sb: StringBuilder, heading: String, detail: String) {
        sb.append("""<div class="diagnostic"><h2>${escape(heading)}</h2><p>$detail</p></div>""")
        sb.append("\n")
    }

    private fun appendOrphanLink(sb: StringBuilder, count: Int) {
        sb.append("""<p class="orphan-note">""")
        sb.append("""$count orphan span(s) detected (no test root). """)
        sb.append("""<a href="orphans.html">View orphan spans →</a>""")
        sb.append("</p>\n")
    }

    private fun appendTestTable(sb: StringBuilder, result: AggregationResult) {
        val sorted = result.tests.sortedWith(
            compareBy<com.etrandafir.panoptes.testtracer.plugin.renderer.model.TestEntry> { statusSortKey(it.status) }
                .thenByDescending { it.durationNanos }
        )

        sb.append("""<table>
<thead>
<tr>
  <th>Test</th>
  <th>Status</th>
  <th>Duration</th>
  <th>Spans</th>
</tr>
</thead>
<tbody>
""")
        for (entry in sorted) {
            val link = """<a href="${escape(entry.traceId)}.html">${escape(entry.name)}</a>"""
            val badge = statusBadgeHtml(entry.status)
            val dur = durationMs(entry.durationNanos)
            sb.append("""<tr>
  <td>$link${if (!entry.failureMessage.isNullOrBlank()) """<br/><small style="color:#b91c1c">${escape(entry.failureMessage)}</small>""" else ""}</td>
  <td>$badge</td>
  <td>${escape(dur)}</td>
  <td>${entry.spanCount}</td>
</tr>
""")
        }
        sb.append("</tbody>\n</table>\n")
    }

    /**
     * Returns a sort key so ERROR sorts first (0), then UNSET (1), then OK (2).
     */
    internal fun statusSortKey(statusCode: String): Int = when {
        statusCode.endsWith("ERROR") -> 0
        statusCode.endsWith("UNSET") -> 1
        else -> 2
    }
}
