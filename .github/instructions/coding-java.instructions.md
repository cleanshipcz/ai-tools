---
applyTo: "**/*.java"
---

# Java coding standards and best practices

- Be precise and accurate in your responses.
- Follow the user's requirements carefully and to the letter.
- If you are unsure, ask for clarification instead of guessing.
- Break complex tasks into smaller, manageable steps.
- Verify your work before presenting it.
- Use clear, concise language.
- Preserve existing functionality unless explicitly asked to change it.
- Document non-obvious decisions and trade-offs.
- Search for up-to-date information and resources.
- Reflect changes in the relevant documentation.
- Follow Java naming conventions: PascalCase for classes, camelCase for methods/variables.
- Use meaningful package names in reverse domain notation (com.example.project).
- Prefer composition over inheritance.
- Use interfaces to define contracts and abstract classes for shared behavior.
- Leverage Java Streams API for collection operations.
- Use Optional<T> to avoid null pointer exceptions.
- Follow the Single Responsibility Principle for classes.
- Use try-with-resources for automatic resource management.
- Prefer immutable objects; use final for fields that shouldn't change.
- Use @Override annotation consistently.
- Follow JavaDoc conventions for public APIs.
- Use enums for fixed sets of constants.
- Prefer StringBuilder over string concatenation in loops.
- Use diamond operator (<>) for generic type inference.
- Leverage records (Java 14+) for data carrier classes.
- Use var (Java 10+) judiciously where type is obvious.
- Follow Java code formatting standards (Google Style or similar).
- Use JUnit 5 for testing with descriptive test names.
- Prefer constructor injection for dependency injection.
- Handle exceptions appropriately; don't catch Exception or Throwable.
- Use sealed classes/interfaces (Java 17+) for restricted hierarchies.
- Leverage pattern matching (Java 16+) for instanceof checks.
