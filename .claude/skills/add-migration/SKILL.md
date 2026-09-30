---
name: add-migration
description: Change what a HyColony colony save holds (new, renamed, moved or reshaped persisted field) through a schema bump in MigrationChain, with the old version's fixture and its tests. Use whenever core code changes what ColonySerializer writes or reads.
argument-hint: <what the save gains or changes, e.g. "the colony's fields">
---

CLAUDE.md § 5: the save is versioned JSON (`schemaVersion`), every change goes through `MigrationChain` and keeps a fixture of the old version, reading is tolerant, and an inconsistent save is healed on load. Change: `$ARGUMENTS`.

1. **Always a bump** (CLAUDE.md § 5: every change goes through `MigrationChain`), even for a key the tolerant read would default: the step writes that default explicitly, like `v4ToV5` for the fields. Keep also a test that a save missing the key still loads (`MissingKeysLoadTest`).
2. **Fixture first.** Before touching the serializer, save a real save of the **current** schema N as `core/src/test/resources/fixtures/colony-vN-<topic>.json` (copy an existing fixture of schema N and add what the new step must convert). Never edit an existing fixture: each one pins an old version.
3. **Failing tests** (TDD), in `core/src/test/java/dev/hycolony/core/app/persistence/MigrationVNToVN+1Test.java`, modelled on `MigrationV4ToV5Test`:
   - `MigrationChain.<chain>().migrate(fixture)` gives schema N+1 and the converted content;
   - the fixture loads through `ColonyPersistence` (`FileColonyStorage` in a `@TempDir`), the colony holds the expected state, and `saveAll` writes `"schemaVersion":N+1`.
4. **The step** in `core/src/main/java/dev/hycolony/core/kernel/persist/MigrationChain.java`: a private static `vNToVN+1(JsonObject)` with a one-line Javadoc (`Schema N+1 (<sub-project>): …`), registered as `new Migration(N, MigrationChain::vNToVN+1)`, and the chain's `current` raised to N+1 together with `ColonySerializer.SCHEMA_VERSION` (both must match: if the serializer still writes N, the step replays on every load and can wipe data). Update the factory's Javadoc (the list of what each schema added). A step only reshapes JSON: no game rule, no lookup outside the document.
5. **Serializer**: write the new shape; read it tolerantly (absent key = default, unknown value = fallback, never a throw).
6. **Healing**: if the new data can contradict other data (a reference to a removed building, a count out of bounds), repair it in `ColonySerializer.heal` (or a `*Heal` collaborator like `CraftingHeal`) and mark the colony dirty; add the test that loads the inconsistent save.
7. **Check**: `./gradlew :core:test --tests '*Migration*' --tests '*Load*' --tests '*Heal*'`, then the full `./gradlew build`.

Sections serialized by their own class (`RequestSerializer`, fixture `requests-v2.json`) are part of the colony document: they share its `schemaVersion` and its chain.
