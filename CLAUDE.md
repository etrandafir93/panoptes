# panoptes/test-tracer

A Maven plugin that produces a static HTML report of OpenTelemetry traces emitted during a Spring Boot project's test phase. Intended to be uploaded as a CI artifact for **flaky-test diagnosis**.

## Architecture

Three runtime artifacts + one build-time artifact, all Kotlin / JVM 17:

- `test-tracer-core` — OTel `SpanExporter` writing NDJSON. OTel SDK is `provided` scope.
- `test-tracer-junit5` — JUnit 5 `Extension` that opens a root span per test, stamps `test.status`/`test.failure.message` on the root span on `afterEach`.
- `test-tracer-spring-boot-starter` — `@Configuration` registering the exporter + `SimpleSpanProcessor` as Spring beans. No OTel autoconfigure SPI; depend on the Spring Boot / Micrometer Tracing bean contract.
- `test-tracer-maven-plugin` — `record` (config sink) and `report` (glob NDJSON → render HTML) goals. Renderer is an internal package, not a published artifact.

Monorepo, single parent pom. GAV: `com.etrandafir.panoptes:test-tracer-*`.

## Locked decisions

- **Language:** Kotlin. Not Java (user constraint), not Scala/Clojure (classpath/integration cost).
- **Span format on disk:** NDJSON, OTLP-shape. Renderer reads this.
- **Zipkin v2 JSON:** opt-in via flag. Exists only as an escape hatch ("drop into any Zipkin instance").
- **Attribution:** root span per test via JUnit 5 extension; OTel `Context` propagation does the rest. Spans without the test root → "orphan bucket" page. JUnit 5 only in v1.
- **Span processor:** `SimpleSpanProcessor` (synchronous flush on span end). No batch-loss class of bugs. Move to batch only if perf demands.
- **Parallelism:** in-JVM parallel tests handled by a single synchronized writer. `forkCount > 1` deferred — v1 assumes one fork.
- **OTel coupling:** `provided` in `core`, transitive in `starter`. Document minimum supported OTel version; small compat matrix in CI.
- **Multi-module Maven:** two-goal split. `record` runs per-module, `report` runs at the aggregator pom and globs all child NDJSONs into one merged HTML site.
- **No-data path:** always render a diagnostic HTML page. Distinguish "tests skipped" from "tests ran but no spans." `failOnEmpty=true` opt-in.
- **Renderer assets** (CSS, PlantUML jar, etc.) bundled in resources, read via classloader. PlantUML sequence diagrams pre-rendered to inline SVG at plugin time.

## Open questions (resolve before/during build)

- **Information architecture.** Use case is flaky-test diagnosis but the original sketch was traces-sorted-by-duration. Recommendation: failed tests first, duration as secondary sort; per-test detail page with view-mode switch.
- **View modes per test.** Spans waterfall ✓. Sequence diagram (PlantUML SVG) likely ✓. C4 view recommended cut from v1 — span data doesn't carry container/component info reliably.

## Scope boundaries

**In v1:** Spring Boot 3, Java 17 target, JUnit 5, single fork, NDJSON OTLP, optional Zipkin export, multi-module aggregation, test-list + waterfall + sequence-diagram views, diagnostic + orphan pages.

**Out of v1:** C4 view, JUnit 4 / TestNG, multi-fork support, cross-run aggregation, anything requiring a server.
