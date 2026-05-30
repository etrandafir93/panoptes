package com.etrandafir.panoptes.testtracer.plugin.renderer

import com.etrandafir.panoptes.testtracer.plugin.renderer.model.SpanData
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.TraceTree

/**
 * Converts a TraceTree into PlantUML sequence diagram text.
 *
 * Lifeline identity: prefers the `service.name` resource attribute, falls back to
 * the span-name prefix before the first ':', then the full name (truncated to 20 chars).
 * Arrows follow parent→child relationships in startEpochNanos order.
 */
object SequenceDiagramGenerator {

    fun generate(tree: TraceTree): String {
        val lifelines = collectLifelines(tree)
        return buildString {
            appendLine("@startuml")
            appendLine("skinparam responseMessageBelowArrow true")
            appendLine("skinparam maxMessageSize 80")
            appendLine("skinparam sequence {")
            appendLine("  ArrowColor #555")
            appendLine("  LifeLineBorderColor #888")
            appendLine("  ParticipantBorderColor #555")
            appendLine("  ParticipantBackgroundColor #f0f4ff")
            appendLine("}")
            appendLine()
            for (ll in lifelines) {
                appendLine("""participant "${ll}" as ${sanitize(ll)}""")
            }
            appendLine()
            generateNode(this, tree)
            append("@enduml")
        }
    }

    private fun generateNode(sb: StringBuilder, node: TraceTree) {
        val parentLl = sanitize(lifeline(node.root))
        for (child in node.children.sortedBy { it.root.startEpochNanos }) {
            val childLl = sanitize(lifeline(child.root))
            sb.appendLine("$parentLl -> $childLl: ${truncate(child.root.name, 60)}")
            sb.appendLine("activate $childLl")
            generateNode(sb, child)
            sb.appendLine("$childLl --> $parentLl")
            sb.appendLine("deactivate $childLl")
        }
    }

    private fun collectLifelines(tree: TraceTree): List<String> {
        val seen = linkedSetOf<String>()
        collectLifelinesRecursive(tree, seen)
        return seen.toList()
    }

    private fun collectLifelinesRecursive(node: TraceTree, acc: LinkedHashSet<String>) {
        acc.add(lifeline(node.root))
        for (child in node.children.sortedBy { it.root.startEpochNanos }) {
            collectLifelinesRecursive(child, acc)
        }
    }

    internal fun lifeline(span: SpanData): String {
        val serviceName = span.attributes["service.name"]
        if (!serviceName.isNullOrBlank()) return serviceName
        val colonIdx = span.name.indexOf(':')
        if (colonIdx > 0) return span.name.substring(0, colonIdx)
        return span.name.take(20)
    }

    private fun sanitize(name: String): String {
        val safe = name.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return if (safe.isEmpty() || safe[0].isDigit()) "_$safe" else safe
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1) + "…"
}
