package com.etrandafir.panoptes.testtracer.core.io

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText

class NdjsonSpanWriterTest : StringSpec({

    "writes one LF-terminated line per call" {
        val file = tempdir().toPath().resolve("spans.ndjson")
        NdjsonSpanWriter(file).use {
            it.write("""{"a":1}""")
            it.write("""{"b":2}""")
        }
        file.readText() shouldBe """{"a":1}""" + "\n" + """{"b":2}""" + "\n"
    }

    "appends across reopen" {
        val file = tempdir().toPath().resolve("spans.ndjson")
        NdjsonSpanWriter(file).use { it.write("""{"first":true}""") }
        NdjsonSpanWriter(file).use { it.write("""{"second":true}""") }
        file.readText().lines().filter { it.isNotEmpty() } shouldBe listOf(
            """{"first":true}""",
            """{"second":true}""",
        )
    }

    "creates parent directory on demand" {
        val file = tempdir().toPath().resolve("nested/dir/spans.ndjson")
        NdjsonSpanWriter(file).use { it.write("""{"x":1}""") }
        Files.exists(file) shouldBe true
    }

    "concurrent writes produce N complete lines with no interleaving" {
        val file = tempdir().toPath().resolve("spans.ndjson")
        val threadCount = 8
        val perThread = 200
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threadCount)

        NdjsonSpanWriter(file).use { writer ->
            repeat(threadCount) { t ->
                pool.submit {
                    start.await()
                    repeat(perThread) { i ->
                        writer.write("""{"t":$t,"i":$i}""")
                    }
                }
            }
            start.countDown()
            pool.shutdown()
            pool.awaitTermination(10, TimeUnit.SECONDS) shouldBe true
        }

        val lines = file.readText().lines().filter { it.isNotEmpty() }
        lines.size shouldBe threadCount * perThread
        val expected = (0 until threadCount).flatMap { t ->
            (0 until perThread).map { i -> """{"t":$t,"i":$i}""" }
        }
        lines shouldContainAll expected
    }
})
