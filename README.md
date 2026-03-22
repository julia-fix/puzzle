# Puzzle

Android puzzle game workspace organized for agent-driven development.

The repo is intentionally starting with process, architecture, and tooling guidance before game code. That gives Codex and other agents a stable operating model before implementation begins.

## Recommended baseline

- Kotlin-first Android app
- Jetpack Compose UI
- Pure Kotlin gameplay engine kept separate from Android UI code
- Vertical feature slices with narrow ownership
- Fast JVM tests for game rules before UI-heavy work

## Repo map

- `AGENTS.md`: root instructions for human and AI contributors
- `docs/product/game-brief.md`: product assumptions and open questions
- `docs/architecture/technical-baseline.md`: default technical stack and module plan
- `docs/engineering/kotlin-style.md`: Kotlin and Compose coding style
- `docs/content/puzzle-image-pipeline.md`: how puzzle images should be stored and described
- `docs/process/`: workflow, task template, definition of done, and subagent usage
- `docs/tools/mcp-setup.md`: MCP recommendations and safe usage model
- `docs/tools/android-sdk-setup.md`: Android SDK path and shell setup
- `docs/tools/skills-setup.md`: how to activate repo-local Codex skills
- `codex/skills/`: repo-local Codex skill definitions
- `codex/agents/`: reusable subagent role prompts

## Next implementation step

Bootstrap the Android project only after the product brief is tightened enough to answer these:

- What is the core puzzle mechanic?
- Portrait only or portrait plus landscape?
- Offline only or cloud-backed progression?
- Premium, ads, or no monetization yet?

## Codex note

The skills inside `codex/skills/` are repo-local source files. They are not auto-loaded just by existing in the repository. See `docs/tools/skills-setup.md` if you want them available from `~/.codex/skills/`.
