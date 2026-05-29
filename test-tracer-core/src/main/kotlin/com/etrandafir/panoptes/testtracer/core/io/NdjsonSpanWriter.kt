package com.etrandafir.panoptes.testtracer.core.io

import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Append-only NDJSON sink. One JSON object per line, LF-terminated.
 *
 * Synchronized for in-JVM parallel test execution. Flushes on every write
 * so a JVM kill mid-suite leaves a valid prefix of complete lines on disk.
 */
class NdjsonSpanWriter(private val path: Path) : AutoCloseable {

    private val lock = Any()
    private val writer: BufferedWriter

    init {
        path.parent?.let { Files.createDirectories(it) }
        writer = Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
        )
    }

    fun write(jsonLine: String) {
        synchronized(lock) {
            writer.write(jsonLine)
            writer.write("\n")
            writer.flush()
        }
    }

    override fun close() {
        synchronized(lock) {
            writer.flush()
            writer.close()
        }
    }
}
