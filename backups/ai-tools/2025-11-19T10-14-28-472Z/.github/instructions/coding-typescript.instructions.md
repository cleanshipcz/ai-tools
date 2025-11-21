---
applyTo: "**/*.ts,**/*.tsx"
---

# TypeScript coding standards and best practices

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
- Automated tests are mandatory, not optional. Manual testing is insufficient for verification.
- Test infrastructure must be in place before implementing features.
- All code changes must include corresponding test changes.
- When running inside an IDE, prefer using native read/write tools rather than CLI tools.
- Use strict TypeScript configuration (strict: true in tsconfig.json).
- Prefer interfaces for public APIs, types for internal structures.
- Use readonly for immutable properties and ReadonlyArray<T> for immutable arrays.
- Leverage type guards and discriminated unions for type safety.
- Use async/await over raw Promises for better readability.
- Prefer const for immutable bindings, never use var.
- Use template literals over string concatenation.
- Leverage destructuring for objects and arrays.
- Use optional chaining (?.) and nullish coalescing (??) operators.
- Prefer functional array methods (map, filter, reduce) over loops.
- Use enums or const objects with 'as const' for constants.
- Avoid 'any' type; use 'unknown' when type is truly unknown.
- Use generics for reusable type-safe components.
- Follow naming conventions: PascalCase for types/interfaces, camelCase for variables/functions.
- Use ESLint with TypeScript rules for code quality.
- Prefer named exports over default exports for better refactoring.
- Use utility types (Partial, Pick, Omit, Record) appropriately.
- Document complex types and public APIs with JSDoc comments.
