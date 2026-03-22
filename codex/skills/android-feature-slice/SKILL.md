---
name: android-feature-slice
description: Use when implementing or changing a single Android feature slice in this puzzle game, especially Compose UI, ViewModel state, navigation wiring, or local persistence at the feature boundary.
---

# Android Feature Slice

Read `AGENTS.md`, `docs/architecture/technical-baseline.md`, and `docs/process/definition-of-done.md` first.

## Workflow

1. Confirm the slice and write scope.
2. Keep gameplay rules in pure Kotlin layers.
3. Keep Compose focused on rendering and event forwarding.
4. Use explicit UI state and event models.
5. Add or update tests for behavior changed by the slice.

## Guardrails

- Do not hide domain logic inside composables.
- Do not mix multiple feature concerns in one task.
- Touch shared navigation or build logic only when necessary.

