---
name: review-guard
description: Use when reviewing changes in this puzzle game repository to focus on correctness, behavioural regressions, architecture drift, and missing tests.
---

# Review Guard

Read `AGENTS.md`, `docs/process/definition-of-done.md`, and `docs/process/subagent-map.md` first.

## Review order

1. correctness and regressions
2. missing tests
3. wrong-layer logic
4. maintainability risks

## Guardrails

- Prefer findings over summaries.
- Reference the exact file and lines when possible.
- Call out product ambiguity if it affects correctness.
