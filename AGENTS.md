# AGENTS.md

HyColony ports MineColonies to Hytale 0.7.0-pre.4 (pinned in `gradle.properties`). **`CLAUDE.md` is the single
source of the project rules.** Read it in full before writing or reviewing anything; this file only adds what tools
need to know first. (CLAUDE.md is in French: it covers modules, class design, style, robustness, persistence,
MineColonies fidelity, texts, tests and process.)

## Four mods

HyBlockUI (`blockui/`, UI library) ← HyDomum (`domum/core`, `domum/plugin`) ← HyColony (`core/`, `plugin/`) →
HyVanilla (`vanilla/core`, `vanilla/plugin`, Minecraft's vanilla blocks), in one direction only. Each core is pure
Java; a mod reaches another only through its `api` packages. Details: CLAUDE.md § 1.

## Verify before every commit

    ./gradlew spotlessApply   # format (palantir-java-format, import order); file by file with
                              # -PspotlessIdeHook="<absolute path>" when other sessions share the module
    ./gradlew build           # core tests, plugin compile with Error Prone and NullAway, spotlessCheck, PMD,
                              # checkFileSizes, checkSectionDividers, checkLineLength

The build must be green. Versioned git hooks enforce this: run `git config core.hooksPath .githooks` once per clone.

## Never

- Launch the Hytale server (`runServer`, `runAllMods`, `HytaleServer.jar`). The user runs it and tests in game.
- `git add -A`, `git add .`, `git add -u`, `git commit -a`: stage explicit paths only.
- `--no-verify` on commit or push (nor `-n` on commit), `git commit --amend` (HEAD may be another session's commit), `git push --force`, or changing `core.hooksPath`.
- Add a line to `gradle/file-size-allowlist.txt`, `gradle/package-size-allowlist.txt` or
  `config/pmd/known-violations.txt`: these lists only shrink.
- Write or commit `.mcp.json`, `config.json`, `config.json.bak` or `.claude/settings.local.json` (local settings).
- Edit the guardrails without the user's explicit approval: `CLAUDE.md`, `AGENTS.md`, `.claude/agents/`,
  `.claude/skills/`, `.claude/hooks/`, `.claude/settings.json`, `.githooks/`, `build-logic/`, `config/pmd/ruleset.xml`
  and the checks of the root `build.gradle.kts` (CLAUDE.md § 10).

Commit messages: `type(scope): description` in English, type in feat|fix|refactor|test|docs|build|style|chore|perf.

## Roles and procedures

Defined for Claude Code in `.claude/`; other tools can follow the same files as written procedures.

- Agents (`.claude/agents/`): `hycolony-implementer` (test-first change, then commit), `hycolony-reviewer` (independent
  review required after every change), `mc-fidelity-checker` (compares ported code with MineColonies),
  `hycolony-researcher` (verifies facts, writes only under `docs/research/`), `ui-lang-checker` (texts and windows,
  CLAUDE.md § 7).
- Skills (`.claude/skills/`): `port-mc` (order of work to port a MineColonies system), `hytale-api` (verify a Hytale API
  in the decompiled server), `add-lang-key` (add a text key to en-US and fr-FR), `add-migration` (schema bump of the
  colony save), `ship` (build, reviews, then commit).
