---
name: port-mc
description: Port a MineColonies system, class or behaviour into HyColony's core, faithfully (same rules, constants, formulas). Use when the user asks to port, implement or align something "comme dans MineColonies".
disable-model-invocation: true
argument-hint: <MineColonies class or system, e.g. EntityAIWorkLumberjack>
---

Port `$ARGUMENTS` from MineColonies to HyColony. CLAUDE.md applies in full; this is the order of work.

1. **Process gate (§ 9).** A new system needs a validated design, a spec in `docs/superpowers/specs/` and a plan in `docs/superpowers/plans/` before code. A small change needs a short design validated in the conversation. Don't skip it.
2. **Read the source.** Fetch the MC class(es) from `github.com/ldtteam/minecolonies`, branch `version/main` (raw URLs under `raw.githubusercontent.com/ldtteam/minecolonies/version/main/`). Read `docs/research/` first; an analysis may already exist. Note every constant, formula, state and transition, in ticks.
3. **Map to Hytale.** Anything touching the world goes through a port (`kernel/port`, …). If a port is missing, check the real API in `build/vineflower/hytale-server` (skill `hytale-api`), never guess.
4. **TDD in `core/`.** Write the failing test first (camelCase sentence name, `Fake*` ports), then the code. Constants copied verbatim as `UPPER_SNAKE` with unit.
5. **Cite and flag.** Javadoc cites the source (`MC EntityAIWorkLumberjack.chopTree`). Every deviation carries `Deviation from MC: <why>` and is listed in the sub-project spec.
6. **Stay small.** ≤ 400 lines per file, ≤ 40 per method, ≤ 15 files per package: extract collaborators in the same change.
7. **Texts.** Player-visible strings go through skill `add-lang-key` (en-US + fr-FR).
8. **Finish.** `./gradlew spotlessApply` then `./gradlew build` green, then the `hycolony-reviewer` agent (and `mc-fidelity-checker` for the ported logic). Fix and re-review. Never launch the Hytale server: hand over to the user for in-game testing.
