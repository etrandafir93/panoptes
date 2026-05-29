# panoptes/test-tracer

[![build](https://github.com/etrandafir93/panoptes/actions/workflows/build.yml/badge.svg)](https://github.com/etrandafir93/panoptes/actions/workflows/build.yml)

A Maven plugin that turns OpenTelemetry traces emitted during Spring Boot tests into a **static HTML report**, designed to be uploaded as a CI artifact for **flaky-test diagnosis**.

> **Status:** pre-alpha. Nothing published yet, APIs will change.

## Modules

| Module | What it does |
|---|---|
| `test-tracer-core` | OTel `SpanExporter` that writes spans to NDJSON. |
| `test-tracer-junit5` | JUnit 5 extension that opens a root span per test and stamps status/failure on it. |
| `test-tracer-spring-boot-starter` | Spring Boot autoconfig wiring the exporter as a `SpanProcessor` bean. |
| `test-tracer-maven-plugin` | `record` + `report` goals — globs NDJSON across modules and renders the static HTML site. |

## Quickstart *(coming in v0.1)*

```xml
<dependency>
    <groupId>com.etrandafir.panoptes</groupId>
    <artifactId>test-tracer-spring-boot-starter</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

```xml
<plugin>
    <groupId>com.etrandafir.panoptes</groupId>
    <artifactId>test-tracer-maven-plugin</artifactId>
    <version>0.1.0</version>
    <executions>
        <execution>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

```bash
./mvnw verify
# → target/test-tracer/site/index.html
```

## Building locally

Requires JDK 17+. Maven is bundled via the wrapper.

```bash
./mvnw verify
```

## Design and roadmap

- [`CLAUDE.md`](CLAUDE.md) — locked architecture decisions and scope boundaries.
- [`PLAN.md`](PLAN.md) — phased implementation plan.

## License

[Apache 2.0](LICENSE).
