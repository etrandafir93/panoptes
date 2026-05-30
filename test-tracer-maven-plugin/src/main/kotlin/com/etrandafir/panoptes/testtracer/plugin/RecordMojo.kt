package com.etrandafir.panoptes.testtracer.plugin

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

/**
 * Phase 4 placeholder. In v1 the actual span emission happens inside the JVM that runs the tests
 * (via the JUnit 5 extension + the Spring Boot starter), so this goal currently exists purely as a
 * marker / future configuration sink — for example to pin a per-module output directory, or to fail
 * the build early when the starter isn't on the test classpath.
 *
 * Bound by default to `post-integration-test` so it runs after Surefire and before the aggregator
 * `report` goal at the parent pom.
 */
@Mojo(
    name = "record",
    defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST,
    threadSafe = true,
)
class RecordMojo : AbstractMojo() {

    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    lateinit var project: MavenProject

    /**
     * Directory each module writes its `spans.ndjson` into. Reported in the log so misconfiguration
     * (starter pointing somewhere else) shows up at build time. Defaults to
     * `${project.build.directory}/test-tracer`.
     */
    @Parameter(property = "panoptes.testTracer.outputDir", defaultValue = "\${project.build.directory}/test-tracer")
    lateinit var outputDir: File

    override fun execute() {
        log.info("test-tracer:record — module=${project.artifactId}, outputDir=$outputDir")
        if (!outputDir.exists()) {
            log.info("test-tracer:record — output directory does not exist yet (no spans recorded for this module)")
        }
    }
}
