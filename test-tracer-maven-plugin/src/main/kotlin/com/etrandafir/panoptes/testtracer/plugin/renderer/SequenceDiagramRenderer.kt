package com.etrandafir.panoptes.testtracer.plugin.renderer

import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader
import java.io.ByteArrayOutputStream

/**
 * Renders PlantUML sequence text to an inline SVG string.
 * The returned string is the bare <svg>...</svg> element (XML declaration stripped),
 * ready to embed directly in HTML.
 */
object SequenceDiagramRenderer {

    fun renderToSvg(puml: String): String {
        return try {
            val os = ByteArrayOutputStream()
            SourceStringReader(puml).outputImage(os, FileFormatOption(FileFormat.SVG))
            val raw = os.toString(Charsets.UTF_8.name())
            extractSvgElement(raw)
        } catch (e: Exception) {
            """<div class="seq-error">Sequence diagram rendering failed: ${HtmlUtils.escape(e.message ?: "unknown error")}</div>"""
        }
    }

    private fun extractSvgElement(svgDoc: String): String {
        val start = svgDoc.indexOf("<svg")
        return if (start >= 0) svgDoc.substring(start) else svgDoc
    }
}
