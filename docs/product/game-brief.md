# Game Brief

This file exists to reduce product ambiguity for agents before implementation.

## Working assumptions

- Platform: Android
- Primary language: Kotlin
- Audience: casual mobile players
- Session length: 1 to 5 minutes
- Input: tap and drag, no controller assumption
- Connectivity: game should remain playable offline

## Product goals

- Fast start into gameplay
- Rules that are easy to understand and difficult to master
- High clarity of board state
- Short feedback loops for win, fail, and retry
- Content pipeline that allows many levels without major code changes

## Non-goals for phase 1

- Real-time multiplayer
- Account system
- Server-authoritative progression
- Complex live ops

## Open decisions

These are intentionally unresolved and should be answered before heavy implementation:

- Exact puzzle mechanic
- Visual theme and art direction
- Meta-progression depth
- Monetization model
- Accessibility targets
- Portrait-only versus orientation-flexible layout

## Rules for agents

- If a task depends on one of the open decisions, call it out explicitly.
- Do not invent monetization or progression mechanics unless the task asks for exploration.
- Prefer solutions that keep the game loop independent from theme or content.

