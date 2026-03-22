# Definition Of Done

A task is not done until all applicable items below are satisfied.

## Always required

- The change has a single clear purpose.
- New behavior matches the stated task.
- No unrelated files were changed without reason.
- Naming matches the domain language.
- The most important verification step was run, or the reason it was not run is stated.

## Logic changes

- Core rules are covered by tests.
- Edge cases are represented explicitly.
- Determinism is preserved where expected.

## UI changes

- UI state is explicit.
- Composables do not hide domain logic.
- Loading, error, empty, and success states are considered where relevant.
- Layout intent is reasonable for phone form factors.

## Persistence or integration changes

- Failure paths are handled deliberately.
- Serialization assumptions are documented in code or task notes.
- Migration impact is identified if storage shape changes.

## Review checklist

- Is the logic in the right layer?
- Can another agent extend this without reverse-engineering hidden assumptions?
- Are tests focused on behavior rather than implementation trivia?

