package com.etrandafir.panoptes.testtracer.plugin.renderer

import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.durationMs
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.escape
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.loadCss
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.pageHtml
import com.etrandafir.panoptes.testtracer.plugin.renderer.HtmlUtils.statusBadgeHtml
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.RawAttribute
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.SpanData
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.SpanEvent
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.TestEntry
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.TraceTree

/**
 * Renders a per-test detail page (`<traceId>.html`).
 *
 * Content:
 * - Header: test name, status badge, total duration, failure message if any.
 * - Waterfall: SSR HTML/CSS span bars positioned by time percentages.
 *   Each span uses `<details><summary>` for inline attribute expand (no JS).
 */
object DetailPageRenderer {

    fun render(entry: TestEntry, tree: TraceTree): String {
        val css = loadCss()
        val body = buildString {
            append("<header><div class=\"container\"><h1>panoptes test-tracer report</h1>")
            append("<div class=\"subtitle\">Test detail</div></div></header>\n")
            append("<div class=\"container\">\n")
            append("<a class=\"back-link\" href=\"index.html\">← Back to index</a>\n")
            appendDetailHeader(this, entry)
            appendWaterfall(this, tree)
            append("</div>\n")
        }
        return pageHtml(entry.name, css, body)
    }

    private fun appendDetailHeader(sb: StringBuilder, entry: TestEntry) {
        sb.append("<div class=\"detail-header\">\n")
        sb.append("  <h2>${escape(entry.name)}</h2>\n")
        sb.append("  <div>\n")
        sb.append("    ${statusBadgeHtml(entry.status)}\n")
        sb.append("  </div>\n")
        sb.append("  <div class=\"detail-meta\">\n")
        sb.append("    <span>Duration: <strong>${durationMs(entry.durationNanos)}</strong></span>\n")
        sb.append("    <span>Spans: <strong>${entry.spanCount}</strong></span>\n")
        sb.append("    <span>Trace ID: <code>${escape(entry.traceId)}</code></span>\n")
        sb.append("  </div>\n")
        if (!entry.failureMessage.isNullOrBlank()) {
            sb.append("  <div class=\"detail-failure\"><strong>Failure:</strong> ${escape(entry.failureMessage)}</div>\n")
        }
        sb.append("</div>\n")
    }

    private fun appendWaterfall(sb: StringBuilder, tree: TraceTree) {
        // Compute overall time bounds across the entire tree
        val allSpans = collectAll(tree)
        val traceStart = allSpans.minOf { it.startEpochNanos }
        val traceEnd = allSpans.maxOf { it.endEpochNanos }
        val traceDuration = (traceEnd - traceStart).coerceAtLeast(1L)

        sb.append("<div class=\"waterfall-container\">\n")
        sb.append("  <div class=\"waterfall-header\">\n")
        sb.append("    <div class=\"col-name\">Span</div>\n")
        sb.append("    <div class=\"col-bar\">Timeline (total: ${durationMs(traceDuration)})</div>\n")
        sb.append("  </div>\n")

        renderTreeRows(sb, tree, traceStart, traceDuration, depth = 0)

        sb.append("</div>\n")
    }

    private fun renderTreeRows(
        sb: StringBuilder,
        node: TraceTree,
        traceStart: Long,
        traceDuration: Long,
        depth: Int,
    ) {
        val span = node.root
        val leftPct = computeLeftPct(span.startEpochNanos, traceStart, traceDuration)
        val widthPct = computeWidthPct(span.startEpochNanos, span.endEpochNanos, traceDuration)
        val barClass = when {
            span.statusCode.endsWith("ERROR") -> "span-bar span-bar-error"
            span.statusCode.endsWith("OK") -> "span-bar span-bar-ok"
            else -> "span-bar"
        }
        val depthClass = "depth-${depth.coerceAtMost(5)}"
        val tooltipText = buildTooltip(span)

        sb.append("  <div class=\"waterfall-row $depthClass\">\n")
        sb.append("    <div class=\"col-name\">${escape(span.name)}</div>\n")
        sb.append("    <div class=\"col-bar\">\n")
        sb.append("      <div class=\"span-bar-wrap\">\n")
        sb.append(
            "        <div class=\"$barClass\" " +
                "style=\"left:${leftPct}%;width:${widthPct}%\" " +
                "title=\"${escape(tooltipText)}\"></div>\n"
        )
        sb.append("        <div class=\"span-tooltip\">${escape(tooltipText)}</div>\n")
        sb.append("      </div>\n")
        sb.append("    </div>\n")
        sb.append("  </div>\n")

        // Inline expand: attributes + events
        if (span.rawAttributes.isNotEmpty() || span.events.isNotEmpty()) {
            sb.append("  <div class=\"waterfall-row $depthClass\" style=\"padding:.2rem .75rem;background:#f8fafc;\">\n")
            sb.append("    <div style=\"grid-column:1/-1;padding:.1rem .75rem .4rem;\">\n")
            sb.append("      <details>\n")
            sb.append("        <summary>${escape(span.name)} — attributes &amp; events</summary>\n")
            if (span.rawAttributes.isNotEmpty()) {
                sb.append("        <table class=\"attr-table\"><tbody>\n")
                for (attr in span.rawAttributes) {
                    sb.append("          <tr><td>${escape(attr.key)}</td><td>${escape(attr.displayValue)}</td></tr>\n")
                }
                sb.append("        </tbody></table>\n")
            }
            if (span.events.isNotEmpty()) {
                sb.append("        <p style=\"margin:.4rem 0 .1rem;font-size:.78rem;font-weight:600\">Events:</p>\n")
                for (event in span.events) {
                    sb.append("        <div style=\"font-size:.78rem;margin-bottom:.3rem\">")
                    sb.append("<strong>${escape(event.name)}</strong>")
                    if (event.attributes.isNotEmpty()) {
                        sb.append("<table class=\"attr-table\"><tbody>")
                        for (ea in event.attributes) {
                            sb.append("<tr><td>${escape(ea.key)}</td><td>${escape(ea.displayValue)}</td></tr>")
                        }
                        sb.append("</tbody></table>")
                    }
                    sb.append("</div>\n")
                }
            }
            sb.append("      </details>\n")
            sb.append("    </div>\n")
            sb.append("  </div>\n")
        }

        for (child in node.children) {
            renderTreeRows(sb, child, traceStart, traceDuration, depth + 1)
        }
    }

    private fun buildTooltip(span: SpanData): String = buildString {
        append(span.name)
        append("\nDuration: ${durationMs(span.durationNanos)}")
        append("\nStatus: ${span.statusCode.removePrefix("STATUS_CODE_")}")
        if (!span.statusMessage.isNullOrBlank()) append("\n${span.statusMessage}")
        append("\nSpan ID: ${span.spanId}")
        if (span.parentSpanId != null) append("\nParent: ${span.parentSpanId}")
    }

    private fun collectAll(tree: TraceTree): List<SpanData> {
        val result = mutableListOf(tree.root)
        for (child in tree.children) result += collectAll(child)
        return result
    }

    /**
     * Computes the left percentage offset of a span bar within the waterfall timeline.
     * Pure function — easy to unit test.
     */
    internal fun computeLeftPct(startNanos: Long, traceStartNanos: Long, traceDurationNanos: Long): Double {
        if (traceDurationNanos <= 0L) return 0.0
        return ((startNanos - traceStartNanos).toDouble() / traceDurationNanos * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Computes the width percentage of a span bar within the waterfall timeline.
     * Pure function — easy to unit test.
     */
    internal fun computeWidthPct(startNanos: Long, endNanos: Long, traceDurationNanos: Long): Double {
        if (traceDurationNanos <= 0L) return 100.0
        val width = ((endNanos - startNanos).toDouble() / traceDurationNanos * 100.0).coerceIn(0.0, 100.0)
        return width.coerceAtLeast(0.1) // always show at least a sliver
    }
}
