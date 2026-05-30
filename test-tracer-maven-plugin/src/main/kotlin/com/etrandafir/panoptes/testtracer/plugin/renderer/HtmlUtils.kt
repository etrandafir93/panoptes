package com.etrandafir.panoptes.testtracer.plugin.renderer

/** Shared HTML utilities used by all page renderers. */
object HtmlUtils {

    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    fun loadCss(): String {
        val stream = HtmlUtils::class.java.getResourceAsStream(
            "/com/etrandafir/panoptes/testtracer/plugin/renderer/assets/report.css"
        ) ?: return "/* CSS not found */"
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun statusBadgeHtml(statusCode: String): String {
        val (cssClass, label) = when {
            statusCode.endsWith("ERROR") -> "badge-error" to "FAIL"
            statusCode.endsWith("OK") -> "badge-ok" to "PASS"
            else -> "badge-unset" to "UNSET"
        }
        return """<span class="badge $cssClass">${escape(label)}</span>"""
    }

    fun durationMs(nanos: Long): String {
        return if (nanos < 1_000_000L) "${nanos / 1_000}µs"
        else "%.1fms".format(nanos / 1_000_000.0)
    }

    fun pageHtml(title: String, css: String, bodyContent: String): String = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>${escape(title)}</title>
<style>
$css
</style>
</head>
<body>
$bodyContent
</body>
</html>
"""
}
