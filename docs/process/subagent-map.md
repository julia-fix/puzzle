# Subagent Map

Use subagents only when work can be split by ownership.

## Recommended roles

### Android architect

Use for:

- module boundaries
- navigation structure
- dependency direction
- ADR-quality technical decisions

Avoid giving this agent direct ownership of feature implementation files unless the task is architectural.

### Feature implementer

Use for:

- one screen
- one interaction loop
- one persistence adapter

Keep ownership inside one vertical slice.

### Gameplay engineer

Use for:

- board models
- move validation
- scoring
- generator logic
- solver logic

This role should prefer pure Kotlin and heavy test coverage.

### QA reviewer

Use for:

- failure mode review
- edge-case identification
- missing test detection
- regression-oriented code review

## Good split examples

- Agent A: implement pure move engine in `domain:game`
- Agent B: wire engine into `feature:gameplay`
- Agent C: review tests and edge cases only

## Bad split examples

- Two agents editing the same screen state class
- One agent refactoring navigation while another adds a new route
- Parallel edits to shared build logic

