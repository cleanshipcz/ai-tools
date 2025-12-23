# AI Tools Engine (Production)

> **The industrial-grade context compiler for AI Agents.**

## 🎯 Global Vision
The **AI Tools Engine** is the production-ready evolution of the `ai-tools` prototype. It transitions the manifest-driven architecture from a local TypeScript utility into a robust, high-performance backend written in **Kotlin**. 

This module is integrated into the `bootstrap-kotlin` ecosystem, leveraging its telemetry and utility foundations.

---

## 🚀 Core Goals

### 1. Developer Accessibility
Customization should have zero learning curve. Developers define their agents and prompts in simple, readable **YAML**. The engine handles the heavy lifting of merging, validating, and deploying.

### 2. Type-Safe Resolution
Move away from "guess-and-check" logic. Using Kotlin’s strict type system, every manifest is validated against a rigorous schema before it ever reaches an LLM.

### 3. Performance & Speed
Designed to be "instant." Utilizing **Ktor** for asynchronous processing and prepared for **GraalVM Native** compilation, the engine provides near-zero latency for CLI and API interactions.

### 4. Hybrid Architecture
- **Engine (Kotlin)**: The "Brain" for logic, validation, and orchestration.
- **Dashboard (Next.js - Planned)**: A premium visual interface for rapid prototyping and monitoring.

---

## 🛠️ Key Functionality

### 📦 Manifest Support
Natively parses and manages three primary types of manifests:
- **Agents**: Defined personas with specific purposes, defaults (temperature, style), and toolsets.
- **Prompts**: Reusable prompt templates with variable support.
- **Rulesets**: Modular instruction sets that can be shared across multiple agents.

### 🔗 Inheritance & Merging
The engine automatically resolves the "Dependency Tree." An agent can include multiple rulesets, and the engine ensures rules are deduplicated and merged in the correct priority order.

---

## 🛠️ Tech Stack (Integrated)
- **Language**: Kotlin 2.2.x
- **Server**: Ktor 3.0 (Netty Engine)
- **Serialization**: Kotlinx.serialization (JSON + YAML/kaml)
- **Telemetry**: Integrated with `cz.cleanship.telemetry`
- **Build System**: Gradle Kotlin DSL with Convention Plugins
