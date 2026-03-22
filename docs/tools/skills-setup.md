# Skills Setup

The repository contains Codex skills under `codex/skills/`, but Codex only auto-discovers skills from `~/.codex/skills/`.

## Recommended setup

Create symlinks from the repo skills into your personal Codex skills directory:

```bash
mkdir -p ~/.codex/skills
ln -sfn /Volumes/Files/projects/puzzle/codex/skills/android-feature-slice ~/.codex/skills/android-feature-slice
ln -sfn /Volumes/Files/projects/puzzle/codex/skills/puzzle-systems ~/.codex/skills/puzzle-systems
ln -sfn /Volumes/Files/projects/puzzle/codex/skills/review-guard ~/.codex/skills/review-guard
```

## When to use each skill

- `android-feature-slice`: one screen, one ViewModel, one persistence boundary, or one navigation-linked feature
- `puzzle-systems`: board rules, generators, solver logic, hints, scoring, and deterministic mechanics
- `review-guard`: code review and regression-focused validation

## Maintenance rule

Treat the repo versions as the source of truth. If you update a skill, keep the symlink and edit the files in the repository.

