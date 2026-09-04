# AI Tools Engine (Production)

> **The industrial-grade context compiler for AI Agents.**

## 🎯 Vision
The **AI Tools Engine** is the production-ready evolution of the `ai-tools` prototype. It transitions the manifest-driven architecture into a robust, high-performance ecosystem written in **Kotlin**.

This project is built on the `cleanship` bootstrap, providing telemetry, strict code style, and modularity out of the box.

---

## 🏗️ Modular Architecture

The project is split into focused modules to ensure high maintainability and flexibility:

- **`:engine`**: The "Brain." A pure Kotlin library responsible for manifest parsing (YAML), rule resolution, inheritance logic, and validation.
- **`:server`**: The "Backend." A Ktor-powered web server that exposes the engine via a REST API. Perfect for the upcoming Web Dashboard.
- **`:cli`**: The "Utility." A command-line tool powered by Clikt, used by developers for instant manifest validation and local deployment.
- **`:telemetry`**: Shared telemetry facade for observability (Metrics, Traces, Logging).

---

## 🚀 Core Goals

### 1. Developer Accessibility
Customization should have zero learning curve. Developers define their agents and prompts in simple, readable **YAML**. The engine handles the heavy lifting of merging and validating.

### 2. Type-Safe Resolution
Using Kotlin’s strict type system (`kotlinx.serialization`), every manifest is validated against a rigorous schema before it ever reaches an LLM.

---

## 🛠️ Build and Run

This project uses the **Gradle Wrapper**.

### Run the Backend Server
```bash
./gradlew :server:run
```

### Run the CLI Tool
```bash
./gradlew :cli:run --args="--help"
```

Validate a manifest set without writing anything — every manifest is loaded, filtered, and rendered as in a deploy, every failure is reported the same way, and the absolute path of every artifact a deploy would write is logged:
```bash
./gradlew :cli:run --args="--working-dir \"<repository root>\" --dry-run"
```

From the repository root, `./deploy.sh --dry-run` runs the same thing.

### Build Everything
```bash
./gradlew build
```

---

## 🛠️ Tech Stack
- **Language**: Kotlin 2.2.x (JVM 21)
- **Frameworks**: Ktor 3.0 (Server), Clikt 5.0 (CLI)
- **Serialization**: Kotlinx.serialization (JSON + YAML/kaml)
- **Observability**: OpenTelemetry
- **Build System**: Gradle Kotlin DSL + Version Catalogs
