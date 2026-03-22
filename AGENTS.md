# Agent Instructions

This repository is organized so AI agents can work in small, reviewable slices with low ambiguity.

## Mission

Build an Android puzzle game in Kotlin with a codebase that stays testable, deterministic, and easy for agents to extend safely.

## Default product assumptions

Use these assumptions unless a task explicitly overrides them:

- Single-player mobile puzzle game
- Touch-first interaction
- Offline-first
- Deterministic gameplay rules
- Short play sessions
- Game logic should remain portable and mostly Android-free

## Technical defaults

- Language: Kotlin
- UI: Jetpack Compose
- Architecture: unidirectional data flow
- State holders: `ViewModel` at Android boundaries, immutable UI state
- Business rules: pure Kotlin engine and use-case classes
- Persistence: local only unless a task requires remote sync
- Concurrency: coroutines and structured concurrency only

## Hard constraints

- Keep gameplay rules, move validation, generation, scoring, and win detection out of Compose UI code.
- Prefer pure Kotlin modules for puzzle logic so they can be tested with fast JVM tests.
- Each change should target one vertical slice or one infrastructure concern.
- Avoid broad refactors unless the task is explicitly architectural.
- Do not mix unrelated fixes into the same change.
- Add or update tests whenever logic changes.

## Coding style

- Prefer straightforward Kotlin over clever abstractions.
- Use immutable data models by default.
- Model game state explicitly. Avoid hidden mutable globals.
- Name functions after domain behavior, not UI events.
- Keep Compose functions focused on rendering and event forwarding.
- Prefer sealed hierarchies for finite game states and actions.
- Keep Android framework types at the edges.

See `docs/engineering/kotlin-style.md` for the working style baseline.

## Module intent

When the Android project is created, prefer this shape:

- `app`: Android entry point and navigation shell
- `core:common`: shared utilities with minimal dependencies
- `core:designsystem`: theme, tokens, reusable UI primitives
- `core:model`: shared domain models
- `domain:game`: pure gameplay engine and use cases
- `data:progress`: save/load and settings storage
- `feature:gameplay`: active puzzle session
- `feature:level-select`: progression and level picking
- `feature:settings`: settings and accessibility

If the game stays small, collapse modules only after proving the extra split is not buying clarity.

## Delivery workflow

- Start by reading the relevant docs in `docs/`.
- Confirm the write scope before editing.
- Keep tasks small enough that one reviewer can verify them quickly.
- Run the highest-signal verification available for the touched area.
- Record any unresolved product ambiguity in the task notes or follow-up section.

## Subagent policy

Use subagents only for clearly separated work. Good splits:

- gameplay rules vs. UI shell
- test writing vs. feature implementation
- architecture review vs. direct coding
- content balancing vs. persistence wiring

Do not split work when the same files will be edited by multiple agents.

## Required reading by task type

- Feature work: `docs/architecture/technical-baseline.md`, `docs/engineering/kotlin-style.md`, `docs/process/definition-of-done.md`
- Planning: `docs/product/game-brief.md`, `docs/process/task-template.md`
- Review: `docs/process/definition-of-done.md`, `docs/process/subagent-map.md`
- Tooling or integration: `docs/tools/mcp-setup.md`
