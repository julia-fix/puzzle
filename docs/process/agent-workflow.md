# Agent Workflow

## Preferred task size

A good task is one of:

- one feature slice
- one gameplay rule change
- one persistence concern
- one tooling improvement
- one refactor with no product behavior change

Avoid combining multiple categories in one task.

## Task flow

1. Read `AGENTS.md` and the relevant doc for the task.
2. State the target write scope.
3. Identify product assumptions that the task relies on.
4. Implement the smallest end-to-end slice that proves the change.
5. Verify with tests or targeted checks.
6. Leave follow-up items explicitly instead of partially doing them.

## Ownership rules

- One agent owns one write scope.
- Shared files such as root build logic or navigation should be touched only when necessary.
- If a task expands into multiple modules, split it before editing.

## Review expectations

- Bugs and regressions first
- Missing tests second
- Architecture drift third
- Polish last

## Commit expectations

Use small commits with messages that map to a single intent, for example:

- `bootstrap android app shell`
- `add pure board state model`
- `implement swipe move validation`
- `persist settings with datastore`

