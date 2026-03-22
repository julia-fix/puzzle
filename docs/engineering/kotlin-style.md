# Kotlin Style

This document sets the coding style baseline for future Android and Kotlin code in this repo.

## General

- Prefer clear, boring Kotlin over abstraction-heavy patterns.
- Keep functions small when that improves readability, not as a mechanical rule.
- Prefer data classes and sealed interfaces for explicit domain modeling.
- Avoid nullable state when a sealed state model expresses intent better.

## Naming

- Use domain terms for gameplay concepts.
- Use verbs for actions and use cases.
- Name booleans as facts, for example `isSolved`, `canUndo`, `hasHints`.
- Avoid UI-event names in domain code such as `onTileClicked`.

## State and data flow

- UI consumes immutable state.
- Domain transitions should produce new state rather than mutate hidden shared state.
- Keep side effects behind interfaces owned by the boundary layer.
- Prefer one state holder per screen or feature flow.

## Compose

- Composables render state and emit events.
- Do not place move validation, scoring, or board transition rules in Composables.
- Hoist state unless local UI-only state is clearly simpler.
- Keep preview-only helpers out of production logic paths.

## Coroutines

- Use structured concurrency only.
- Avoid launching untracked work from deep inside the stack.
- Keep dispatcher decisions at boundary layers when possible.

## Testing

- Test behavior and invariants, not private implementation details.
- Prefer deterministic inputs.
- When a bug is fixed, add a test that fails without the fix if practical.

