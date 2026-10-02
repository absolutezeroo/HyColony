---
name: port-mc
description: Port a MineColonies system, class or behaviour into HyColony's core — MC's systems faithfully (same rules, constants, formulas), Hytale's world (CLAUDE.md § 6). Use when the user asks to port, implement or align something "comme dans MineColonies".
disable-model-invocation: true
argument-hint: <MineColonies class or system, e.g. EntityAIWorkLumberjack>
---

Port `$ARGUMENTS` from MineColonies to HyColony. CLAUDE.md applies in full; this is the order of work.

1. **Process gate (§ 9).** A new system needs a validated design, a spec in `docs/superpowers/specs/` and a plan in `docs/superpowers/plans/` before code. A small change needs a short design validated in the conversation. Don't skip it.
2. **Read the source.** Read the MC class(es) in the local copy `sources/minecolonies/src/main/java/…` (see CLAUDE.md § 6). Read `docs/research/` first; an analysis may already exist. Note every constant, formula, state and transition, in ticks.
   - **Read the parent classes too** (`AbstractEntityAIBasic`, `AbstractEntityAIStructure`, `AbstractBuilding`, `AbstractBuildingContainer`; Structurize `StructurePlacer` for block placement) and list what they add: much of MC's behaviour lives there. What a parent shared by all workers does goes in `job/work`, never in one job.
   - **Note the cadence** of each method (who calls it, how often, in which state machine) next to its constants: a constant only means something with its call rate.
   - **List whole branches**, even rare ones (a 5 % chance, a fallback): leaving one out is a deviation to mark.
3. **Map to Hytale.** Sort every rule you noted into **system** (follows MC) or **world** (follows Hytale), with CLAUDE.md § 6. For each world rule (tool tier, block, recipe or bench, crop growth, food, mob, time of day…), find Hytale's closest equivalent in `build/vineflower/hytale-server` and the assets (skill `hytale-api`), and note it with its source; a world rule copied from MC while Hytale has an equivalent is a bug. A Minecraft thing is added (HyVanilla) only when nothing comparable exists and the system cannot do without it. Anything touching the world goes through a port (`kernel/port`, …). If a port is missing, check the real API in `build/vineflower/hytale-server` (skill `hytale-api`), never guess. Go through the Hytale traps of `docs/research/pieges-portage.md` § 1 (unloaded chunks, world height, block entities recreated on placement, exceptions killing threads…).
4. **TDD in the mod's core** (`core/` for HyColony, `domum/core/` for HyDomum, `vanilla/core/` for HyVanilla). Write the failing test first (camelCase sentence name, `Fake*` ports), then the code. System constants copied verbatim as `UPPER_SNAKE` with unit; a world value comes from Hytale, with its source. The test's expected values come from the MC source it cites (system) or the Hytale fact it cites (world), never from the code. Before finishing, revert each fix in your own files (back up, revert, run, restore, check `git diff`) or in a `git archive` export, and check its test fails.
5. **Cite and flag.** Javadoc cites the source (`MC EntityAIWorkLumberjack.chopTree`). Every deviation carries `Deviation from MC: <why>`; one forced by Hytale's world carries `Deviation from MC (Hytale world): <MC rule> → <Hytale equivalent, source>`. Each is listed in the sub-project spec.
6. **Stay small.** ≤ 400 lines per file, ≤ 40 per method, ≤ 15 files per package: extract collaborators in the same change.
7. **Texts.** Player-visible strings go through skill `add-lang-key` (en-US + fr-FR).
8. **Finish.** Format your files one by one (`./gradlew :core:spotlessApply -PspotlessIdeHook="<absolute path>"`; other sessions may be editing the same module), then `./gradlew build` green (read its exit code), then the `hycolony-reviewer` agent (and `mc-fidelity-checker` for the ported logic). Fix and re-review. Never launch the Hytale server: hand over to the user for in-game testing.
