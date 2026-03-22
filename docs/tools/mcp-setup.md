# MCP Setup

This project should use MCPs to reduce blind spots, not to increase randomness.

## Current useful MCPs in your Codex setup

- `playwright`: useful for web-based tooling, docs, dashboards, and any future internal content tools
- `figma`: useful when UI exploration starts and screen specs exist
- `atlassian`: useful if backlog and design decisions live in Jira or Confluence
- `notion`: useful if product specs or content plans live in Notion

## How to use them here

- Use MCPs for source-of-truth information that is not already in the repo.
- Prefer repo docs over external tools when both contain the same decision.
- Pull only the minimum context needed for the task.

## Recommended future MCP category

If you add one more MCP for this project, make it an Android-device bridge that exposes:

- `adb` commands
- emulator lifecycle
- screenshot capture
- logcat filtering
- package install and launch

Choose a server that keeps these operations explicit and inspectable. Avoid any MCP that hides shell execution behind vague tool names.

## Safe usage rules

- Treat external docs as advisory until reflected in repo docs or code.
- Do not let external task trackers override `AGENTS.md` without an explicit repo change.
- Prefer read-mostly MCP usage. Escalate to write actions only when the task actually requires them.

## Suggested global Codex baseline

Your current global `~/.codex/config.toml` already contains a sensible base:

- `gpt-5.4`
- `high` reasoning effort
- `multi_agent = true`

Keep that baseline. Add project-specific MCPs only when they improve a real workflow in this repo.

