---
name: puzzle-systems
description: Use when designing or modifying puzzle mechanics, board rules, level generation, scoring, hinting, or solver logic for the Android puzzle game.
---

# Puzzle Systems

Read `AGENTS.md`, `docs/product/game-brief.md`, and `docs/architecture/technical-baseline.md` first.

## Workflow

1. Define the rule change in domain language.
2. Model the state transition explicitly.
3. Keep randomness deterministic with injected seeds where relevant.
4. Write focused JVM tests around valid, invalid, and edge-case transitions.
5. Expose a small interface for UI or persistence layers.

## Guardrails

- Keep logic pure Kotlin.
- Optimize for correctness before clever generation tricks.
- Prefer inspectable data structures over compact but opaque encodings.

