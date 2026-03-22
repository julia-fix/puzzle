# Technical Baseline

This is the default implementation model for the project until a later ADR changes it.

## Recommended stack

- Kotlin
- Android Gradle Plugin with Kotlin DSL
- Jetpack Compose for UI
- AndroidX `ViewModel`
- Coroutines and Flow
- DataStore for lightweight local settings and progress metadata
- JVM unit tests for gameplay and generation logic
- Instrumented or screenshot tests only where UI behavior justifies the cost

## Architecture priorities

1. Keep the gameplay engine pure and deterministic.
2. Keep feature UI state explicit and serializable where practical.
3. Keep persistence replaceable.
4. Keep content representation simple enough for future tooling.

## Module boundaries

### `domain:game`

Owns:

- board models
- move rules
- win and fail conditions
- score logic
- hint logic
- level generation or solving helpers

Must not depend on Android framework code.

### `feature:*`

Owns:

- screen-specific UI state
- interaction orchestration
- screen rendering
- navigation events

Should depend on pure domain modules, not the reverse.

### `data:*`

Owns:

- save/load
- settings storage
- serialization
- future remote integrations

## Coding rules

- One source of truth per screen.
- Avoid repository layers unless there is a real data boundary.
- Prefer domain use cases when multiple screens share non-trivial behavior.
- Keep side effects behind clear interfaces.
- Prefer explicit state classes over loosely related flags.
- Prefer deterministic randomization via injected seeds for generators.

## Testing strategy

- Puzzle rules: dense JVM unit coverage
- Generators and solvers: deterministic tests with seeded inputs
- ViewModels: coroutine-based state tests
- Compose UI: targeted interaction tests for critical flows only

## Performance expectations

- Gameplay updates should feel immediate on mid-range Android devices.
- Avoid allocation-heavy recomposition paths.
- Keep board-state transformations simple and inspectable.

