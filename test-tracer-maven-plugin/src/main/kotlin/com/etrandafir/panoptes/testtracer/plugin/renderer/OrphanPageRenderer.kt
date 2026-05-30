package com.etrandafir.panoptes.testtracer.plugin.renderer

import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.durationMs
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.escape
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.loadCss
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.pageHtml
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.OrphanGroup

/**
 * Renders `orphans.html` — a table of spans that belong to traces with no test-attributed root.
 */
object OrphanPageRenderer {

    fun render(orphans: OrphanGroup): String {
        val css = loadCss()
        val body = buildString {
            append("<header><div class=\"container\"><h1>panoptes test-tracer report</h1>")
            append("<div class=\"subtitle\">Orphan spans</div></div></header>\n")
            append("<div class=\"container\">\n")
            append("<a class=\"back-link\" href=\"index.html\">← Back to index</a>\n")
            append("<div class=\"orphan-note\">")
            append("These spans belong to traces where no root span with <code>test.method</code> attribute was found. ")
            append("They may come from background threads or spans started outside the JUnit 5 extension context.")
            append("</div>\n")

            if (orphans.spans.isEmpty()) {
                append("<p>No orphan spans.</p>\n")
            } else {
                append("""<table>
<thead>
<tr>
  <th>Span Name</th>
  <th>Trace ID</th>
  <th>Span ID</th>
  <th>Duration</th>
  <th>Status</th>
</tr>
</thead>
<tbody>
""")
                for (span in orphans.spans.sortedBy { it.traceId }) {
                    val dur = durationMs(span.durationNanos)
                    val statusLabel = span.statusCode.removePrefix("STATUS_CODE_")
                    append("<tr>")
                    append("<td>${escape(span.name)}</td>")
                    append("<td><code>${escape(span.traceId)}</code></td>")
                    append("<td><code>${escape(span.spanId)}</code></td>")
                    append("<td>${escape(dur)}</td>")
                    append("<td>${escape(statusLabel)}</td>")
                    append("</tr>\n")
                }
                append("</tbody>\n</table>\n")
            }

            append("</div>\n")
        }
        return pageHtml("panoptes — orphan spans", css, body)
    }
}
