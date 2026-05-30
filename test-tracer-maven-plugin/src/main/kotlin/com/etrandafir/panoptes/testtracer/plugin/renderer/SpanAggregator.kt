package com.etrandafir.panoptes.testtracer.plugin.renderer

import com.etrandafir.panoptes.testtracer.plugin.renderer.model.AggregationResult
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.OrphanGroup
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.RawAttribute
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.SpanData
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.SpanEvent
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.TestEntry
import com.etrandafir.panoptes.testtracer.plugin.renderer.model.TraceTree
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parses NDJSON files, groups spans by traceId, builds [TraceTree]s, and identifies test entries
 * vs orphan spans.
 */
object SpanAggregator {

    private val mapper = ObjectMapper()

    /**
     * Aggregate all NDJSON lines from [ndjsonFiles] into an [AggregationResult].
     * Each file is read line by line; blank lines and parse errors are skipped silently.
     */
    fun aggregate(ndjsonFiles: List<Path>): AggregationResult {
        if (ndjsonFiles.isEmpty()) {
            return AggregationResult(
                tests = emptyList(),
                traces = emptyMap(),
                orphans = OrphanGroup(emptyList()),
                noFilesFound = true,
            )
        }

        val allSpans = mutableListOf<SpanData>()
        var totalLines = 0L

        for (file in ndjsonFiles) {
            if (!Files.exists(file)) continue
            Files.lines(file).use { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach
                    totalLines++
                    runCatching { parseLine(trimmed) }.getOrNull()?.let { allSpans += it }
                }
            }
        }

        if (allSpans.isEmpty()) {
            return AggregationResult(
                tests = emptyList(),
                traces = emptyMap(),
                orphans = OrphanGroup(emptyList()),
                filesFoundButEmpty = totalLines == 0L,
            )
        }

        return buildResult(allSpans)
    }

    /**
     * Convenience overload that accepts raw NDJSON lines (useful for tests).
     */
    fun aggregateLines(lines: List<String>): AggregationResult {
        if (lines.isEmpty()) {
            return AggregationResult(
                tests = emptyList(),
                traces = emptyMap(),
                orphans = OrphanGroup(emptyList()),
                filesFoundButEmpty = true,
            )
        }
        val spans = lines.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) null else runCatching { parseLine(trimmed) }.getOrNull()
        }
        if (spans.isEmpty()) {
            return AggregationResult(
                tests = emptyList(),
                traces = emptyMap(),
                orphans = OrphanGroup(emptyList()),
                filesFoundButEmpty = true,
            )
        }
        return buildResult(spans)
    }

    private fun buildResult(allSpans: List<SpanData>): AggregationResult {
        // Group by traceId
        val byTrace: Map<String, List<SpanData>> = allSpans.groupBy { it.traceId }

        val tests = mutableListOf<TestEntry>()
        val traces = mutableMapOf<String, TraceTree>()
        val orphanSpans = mutableListOf<SpanData>()

        for ((traceId, spans) in byTrace) {
            val root = spans.firstOrNull { it.isRoot }
            if (root == null || !root.attributes.containsKey("test.method")) {
                // No root or root doesn't have test.* attributes → orphans
                orphanSpans += spans
                continue
            }

            val tree = buildTree(root, spans)
            traces[traceId] = tree

            val testClass = root.attributes["test.class"] ?: ""
            val testMethod = root.attributes["test.method"] ?: root.name
            val testName = if (testClass.isNotEmpty()) "$testClass#$testMethod" else testMethod
            val failureMessage = root.attributes["test.failure.message"]

            tests += TestEntry(
                traceId = traceId,
                name = testName,
                status = root.statusCode,
                durationNanos = root.durationNanos,
                spanCount = spans.size,
                failureMessage = failureMessage,
            )
        }

        return AggregationResult(
            tests = tests,
            traces = traces,
            orphans = OrphanGroup(orphanSpans),
        )
    }

    private fun buildTree(root: SpanData, allSpans: List<SpanData>): TraceTree {
        val childrenMap = allSpans.groupBy { it.parentSpanId }
        return buildTreeRecursive(root, childrenMap)
    }

    private fun buildTreeRecursive(span: SpanData, childrenMap: Map<String?, List<SpanData>>): TraceTree {
        val children = childrenMap[span.spanId] ?: emptyList()
        return TraceTree(
            root = span,
            children = children
                .sortedBy { it.startEpochNanos }
                .map { buildTreeRecursive(it, childrenMap) },
        )
    }

    internal fun parseLine(line: String): SpanData {
        val n = mapper.readTree(line)
        val rawAttrs = readRawAttributes(n["attributes"])
        val attrMap = rawAttrs.associate { it.key to it.displayValue }
        return SpanData(
            traceId = n["traceId"]?.asText() ?: "",
            spanId = n["spanId"]?.asText() ?: "",
            parentSpanId = n["parentSpanId"]?.asText(),
            name = n["name"]?.asText() ?: "",
            startEpochNanos = n["startTimeUnixNano"]?.asText()?.toLongOrNull() ?: 0L,
            endEpochNanos = n["endTimeUnixNano"]?.asText()?.toLongOrNull() ?: 0L,
            statusCode = n["status"]?.get("code")?.asText() ?: "STATUS_CODE_UNSET",
            statusMessage = n["status"]?.get("message")?.asText(),
            attributes = attrMap,
            rawAttributes = rawAttrs,
            events = readEvents(n["events"]),
        )
    }

    private fun readRawAttributes(node: JsonNode?): List<RawAttribute> {
        if (node == null || node.isEmpty) return emptyList()
        return (0 until node.size()).mapNotNull { i ->
            val entry = node[i]
            val key = entry["key"]?.asText() ?: return@mapNotNull null
            val value = entry["value"]?.let { displayAttributeValue(it) } ?: "null"
            RawAttribute(key, value)
        }
    }

    private fun displayAttributeValue(v: JsonNode): String = when {
        v.has("stringValue") -> v["stringValue"].asText()
        v.has("boolValue") -> v["boolValue"].asBoolean().toString()
        v.has("intValue") -> v["intValue"].asText()
        v.has("doubleValue") -> v["doubleValue"].asDouble().toString()
        v.has("arrayValue") -> {
            val values = v["arrayValue"]["values"]
            "[" + (0 until values.size()).joinToString(", ") { displayAttributeValue(values[it]) } + "]"
        }
        else -> v.toString()
    }

    private fun readEvents(node: JsonNode?): List<SpanEvent> {
        if (node == null || node.isEmpty) return emptyList()
        return (0 until node.size()).map { i ->
            val e = node[i]
            SpanEvent(
                epochNanos = e["timeUnixNano"]?.asText()?.toLongOrNull() ?: 0L,
                name = e["name"]?.asText() ?: "",
                attributes = readRawAttributes(e["attributes"]),
            )
        }
    }
}
