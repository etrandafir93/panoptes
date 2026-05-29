# Implementation plan

Tasks are ordered for incremental delivery — each phase produces something runnable and testable end-to-end before moving on.

---

## Phase 0 — Repo scaffolding

- [x] 0.1 GAV group id locked: `com.etrandafir.panoptes`. (Note: requires DNS TXT verification of `etrandafir.com` for Maven Central publishing in Phase 7.6 — or use `io.github.etrandafir93` for free verification.)
- [x] 0.2 Parent `pom.xml` with `<packaging>pom</packaging>`, Kotlin 2.1 + JVM 17 toolchain, four `<module>` entries.
- [x] 0.3 Shared `kotlin-maven-plugin` + dependency-management config (Spring Boot 3.4, OTel 1.45, JUnit 5.11, Kotest 5.9 BOMs) in parent pom.
- [x] 0.4 `.editorconfig`, `.gitignore`, GitHub Actions CI workflow (build + test on push/PR, surefire artifact upload, `dorny/test-reporter` for Actions-UI rendering).
- [x] 0.5 Module skeletons: each module has its own `pom.xml`, `src/main/kotlin` placeholder, `src/test/kotlin` Kotest hello-world spec.
- [x] 0.6 Maven Wrapper (`mvnw`, `mvnw.cmd`, `mvnw.ps1`) committed so contributors don't need system Maven.
- [x] 0.7 README at repo root with one-paragraph project description, modules table, placeholder quickstart, and `./mvnw verify` build instructions.

## Phase 1 — `test-tracer-core`

- [x] 1.1 Internal span model: `Span`, `SpanKind`, `SpanStatus`/`StatusCode`, `SpanEvent`, `Resource`, `InstrumentationScope`, `Attributes` (value class), `AttributeValue` sealed interface. Computed accessors on `Span`: `durationNanos`, `isRoot`, `hasError`.
- [x] 1.2 `NdjsonSpanWriter`: append-only, synchronized, flush-per-write, creates parent dir on demand. Tested for line termination, append-across-reopen, and 8-thread concurrent stress.
- [x] 1.3 `OtlpJsonSerializer`: span → single-line OTLP-shaped JSON. Flat per-span (no `resourceSpans` envelope), int64 nano fields quoted per OTLP/JSON. Manual JSON emission (no Jackson on user classpath); Jackson in test scope only for parse-back validation.
- [x] 1.4 `TestTracerSpanExporter implements SpanExporter`: `SpanDataMapper` translates OTel `SpanData` → internal `Span` (kind/status/attributes/events/resource/scope), exporter writes one NDJSON line per span via the serializer + writer. `flush()` is a no-op (already flush-per-write). `shutdown()` closes the writer. Tested end-to-end with `TestSpanData` fixtures.
- [x] 1.5 `ZipkinJsonSerializer`: span → single line of Zipkin v2 JSON. Extracted a shared `SpanSerializer` interface + internal `JsonOutput` escape helpers. `TestTracerSpanExporter` is now generic over the serializer, so Phase 3's Spring starter can register a second exporter for Zipkin output when the flag is on. Lossy-by-design Zipkin tag flattening (non-strings stringified, lists comma-joined); ERROR status → `error` + `otel.status_code` tags. Output is NDJSON; document `jq -s . file.ndjson` to convert to Zipkin's array shape.
- [ ] 1.6 Unit tests: synthetic `SpanData` → exporter → assert NDJSON content; round-trip through a deserializer.

## Phase 2 — `test-tracer-junit5`

- [x] 2.1 `TestTracerExtension implements BeforeEachCallback, AfterEachCallback`. Opens `test:<Class>#<method>` span on beforeEach (makes it current OTel context), stamps `test.class`/`test.method`/`test.status`/failure attributes + records exception event on afterEach, closes scope + ends span. No-arg constructor uses `GlobalOpenTelemetry`; secondary constructor takes an explicit `OpenTelemetry` for testing. Integration-tested via JUnit Platform Launcher running sample classes; status-mapping covered by Kotest data-driven `withData`.
- [ ] 2.2 On `beforeEach`: start root span named `test:${className}#${methodName}`, attach to OTel `Context.current()`, store the `Scope` in the JUnit `ExtensionContext` store.
- [ ] 2.3 On `afterEach`: stamp `test.status` (`passed`/`failed`/`skipped`) and `test.failure.message` if any, end span, close scope.
- [x] 2.4 Registered `TestTracerExtension` via `META-INF/services/org.junit.jupiter.api.extension.Extension` so it auto-loads when `junit.jupiter.extensions.autodetection.enabled=true`.
- [ ] 2.5 Integration test: extension + in-process OTel SDK + Phase-1 exporter → run two fake tests → assert two root spans with correct attributes in the NDJSON file.

## Phase 3 — `test-tracer-spring-boot-starter`

- [x] 3.1 `TestTracerAutoConfiguration` registers `TestTracerSpanExporter` + `SimpleSpanProcessor` beans, gated on `panoptes.test-tracer.enabled` (default true). Nested `@Configuration` adds a second exporter+processor pair under `panoptes.test-tracer.zipkin.enabled=true`, writing Zipkin v2 to a separate file. No OTel SDK autoconfigure SPI — plain Spring `@Bean` contract.
- [x] 3.2 `TestTracerProperties` (`@ConfigurationProperties(prefix = "panoptes.test-tracer")`) — nested `Output(dir, fileName)` and `Zipkin(enabled, fileName)`. Computed `output.path` and `zipkinPath` resolve via `java.nio.file.Paths`; Zipkin always writes next to the OTLP file.
- [x] 3.3 Registered via `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- [x] 3.4 Kotest `FunSpec` using `ApplicationContextRunner` + `AutoConfigurations.of(...)` covers: default beans present + Zipkin absent, `enabled=false` shorts everything out, `zipkin.enabled=true` adds both Zipkin beans, custom `output.dir` / `output.file-name` / `zipkin.file-name` bind onto `TestTracerProperties` and resolve to the expected paths.

## Phase 4 — `test-tracer-maven-plugin` skeleton

- [ ] 4.1 Module set to `<packaging>maven-plugin</packaging>`; `maven-plugin-plugin` + `kotlin-maven-plugin` goal ordering correct (Kotlin compile before plugin descriptor generation).
- [ ] 4.2 `RecordMojo` (`@Mojo(name="record")`): no-op in v1 beyond logging that the plugin is wired. Bound by default to `post-integration-test`. Acts as a marker / future config sink.
- [ ] 4.3 `ReportMojo` (`@Mojo(name="report", aggregator=true)`): glob NDJSON files under a configurable pattern (default `**/target/test-tracer/spans.ndjson`), parse into the internal span model, render placeholder HTML to `target/test-tracer/site/`.
- [ ] 4.4 Plugin-level `it` test (`maven-invoker-plugin`): a tiny fixture project using the starter + JUnit 5 → run `verify` → assert HTML produced.

## Phase 5 — Renderer v1

- [ ] 5.1 **Resolve open IA question** (failed-first vs duration-sorted). Sketch the landing page, decide, document in `CLAUDE.md`.
- [ ] 5.2 View model: sealed classes for `TestEntry`, `TraceTree`, `SpanNode`, `OrphanGroup`.
- [ ] 5.3 Aggregation: group spans by `traceId`, build tree, attach to `TestEntry` via root-span attributes. Spans without a `test:` root → orphan bucket.
- [ ] 5.4 Index page: test table (name, status, duration, span count), failed pinned to top, sortable client-side (vanilla JS or no JS — decide based on the IA outcome).
- [ ] 5.5 Per-test detail page: header (test name, status, total duration, failure message), waterfall view.
- [ ] 5.6 Waterfall renderer: SSR HTML/CSS, span bars positioned by time, tooltip on hover, click → side panel with attributes/events/exception.
- [ ] 5.7 Bundle CSS/assets under `src/main/resources/.../assets/`, load via classloader.
- [ ] 5.8 Diagnostic empty-state page (no NDJSON found / file empty / skipTests detected).
- [ ] 5.9 Orphan bucket page.

## Phase 6 — Sequence-diagram view

- [ ] 6.1 Decide PlantUML dependency (`net.sourceforge.plantuml:plantuml`), bundle in the plugin (not user classpath).
- [ ] 6.2 Span tree → PlantUML sequence text generator. Lifelines = unique services / span-name prefixes (decide); arrows = parent→child span relationships.
- [ ] 6.3 Render to SVG at plugin time, inline into per-test page.
- [ ] 6.4 View-mode toggle on detail page (spans / sequence).

## Phase 7 — Polish & release

- [ ] 7.1 `failOnEmpty` flag wiring + tests.
- [ ] 7.2 Distinguish `skipTests` from genuinely-empty in diagnostic page.
- [ ] 7.3 OTel compat matrix CI job (current, current-1, current-2 SDK versions).
- [ ] 7.4 README for each module with usage snippets.
- [ ] 7.5 End-to-end fixture: a small multi-module Spring Boot project, full `./mvnw verify` produces a merged report.
- [ ] 7.6 **Publish snapshots to a public Maven repo on every push to `main`.** Decide target: GitHub Packages (zero setup, `https://maven.pkg.github.com/etrandafir93/panoptes`) or Sonatype OSSRH snapshots (requires GPG + namespace verification). Wire as a separate `publish.yml` workflow gated on `branches: [main]`.
- [ ] 7.7 Maven Central release publication setup (`gpg`, `sonatype-staging` profile, release workflow triggered on tag).
- [ ] 7.8 `1.0.0` tag + release.

---

## Deferred (post-v1)

- C4 view (requires either multi-service traces only or unreliable class-graph inference).
- Multi-fork support (`forkCount > 1`): one NDJSON file per JVM, UUID-named, merged at report time.
- JUnit 4 / TestNG adapters as separate modules.
- Cross-run aggregation / trend reports (would require a publishing target with history, not just a CI artifact).
- Switch `SimpleSpanProcessor` → `BatchSpanProcessor` + explicit flush, if perf data justifies it.
