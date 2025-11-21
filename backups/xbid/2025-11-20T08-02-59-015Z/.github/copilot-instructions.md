# XBid

Energy exchange platform built with Java/Kotlin, Spring Boot, and Maven in a multi-module architecture

## Project Overview

XBid is a multi-module Maven project for energy trading and exchange operations. The system handles energy bidding, market management, and trading workflows with a focus on reliability and performance. Built with a mix of Java (legacy) and Kotlin (new development) components.

**Purpose**: Enable energy trading and exchange operations with robust multi-module architecture

## Tech Stack

**Languages**: java, kotlin, xml, protobuf
**Backend**: spring, spring-boot, maven
**Infrastructure**: docker, wsl2, rabbitmq

## Key Commands

- `source ~/.sdkman/bin/sdkman-init.sh && sdk use java 17.0.16-zulu && mvnd clean install` - build
- `source ~/.sdkman/bin/sdkman-init.sh && sdk use java 17.0.16-zulu && mvnd clean install -DskipTests` - build-fast
- `source ~/.sdkman/bin/sdkman-init.sh && sdk use java 17.0.16-zulu && mvnd test` - test
- `source ~/.sdkman/bin/sdkman-init.sh && sdk use java 17.0.16-zulu && mvnd test -pl <module-name>` - test-module
- `source ~/.sdkman/bin/sdkman-init.sh && sdk use java 17.0.16-zulu && mvnd spring-boot:run` - run
- `docker build -t xbid .` - docker-build
- `docker-compose up -d` - docker-up
- `docker-compose down` - docker-down

## Conventions

### Naming

- Use camelCase for Java/Kotlin methods and variables
- Use PascalCase for class names
- Use meaningful package names following reverse domain notation

### Patterns

- Multi-module Maven project structure
- Java and Kotlin files co-located in same directories
- Separate modules by domain/functionality
- Multi-module Maven architecture
- Spring Boot application structure
- Protobuf message definitions
- RabbitMQ message-driven architecture

### Testing

- Use JUnit 5 as the primary test framework
- Use AssertJ for assertions
- Use MockK for mocking in Kotlin tests
- Use Mockito for mocking in Java tests
- Follow GWT pattern: Given, When, Then

### Project Structure

- Multi-module Maven project with domain-separated modules
- Legacy Java code should be modified minimally
- Prefer writing new classes in Kotlin over Java

### Custom

- Always use SDKMAN to ensure correct JDK 17 version (source ~/.sdkman/bin/sdkman-init.sh && sdk use java 17.0.16-zulu)
- Consider using mvnd instead of mvn for better performance
- WSL2 environment with JDK 17 via SDKMAN
- Follow Spring Boot best practices with constructor injection
- Keep controllers thin, business logic in services
- Document public APIs with JavaDoc/KDoc
- Maintain module-specific README files
- RabbitMQ used for messaging, Protobuf for message serialization

## Documentation

- **readme**: README.md
- **core-module**: m7-core/ReadMe.md
- **core-testing**: m7-core/testing.md
- **trading-module**: m7-trading/README.md
- **trading-rabbitmq**: m7-trading/README-rabbitmq.md
- **cmm-module**: m7-cmm/README.md
- **cmm-flyway**: m7-cmm/README-flyway.md
- **harvester-module**: xbid-harvester/README.md
- **db-history-cleanup**: src/main/db/history-cleanup/README.md

---

## Available AI Tools

This project uses ai-tools for enhanced AI assistance. See the base configurations for available agents, prompts, and skills.
