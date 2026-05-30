package com.etrandafir.panoptes.testtracer.e2e

import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Minimal snapshot helper: write on first run, compare on subsequent runs.
 *
 * Snapshots are stored as plain text files in src/test/resources/__snapshots__/.
 * To regenerate all snapshots: ./mvnw test -pl test-tracer-e2e -Dupdate.snapshots=true
 *
 * Replace this with selfie-runner-kotest when the project upgrades to Kotest 6.x.
 */
object SnapshotHelper {

    private val snapshotDir: File by lazy {
        // test-classes/ is two levels below the module root (target/test-classes)
        val testClasses = File(SnapshotHelper::class.java.getResource("/")!!.toURI())
        testClasses.parentFile.parentFile.resolve("src/test/resources/__snapshots__").also {
            it.mkdirs()
        }
    }

    private val updateSnapshots: Boolean
        get() = System.getProperty("update.snapshots") == "true"

    fun matchSnapshot(name: String, actual: String) {
        val file = File(snapshotDir, "$name.html")
        if (!file.exists() || updateSnapshots) {
            file.writeText(actual)
            return
        }
        actual shouldBe file.readText()
    }
}
