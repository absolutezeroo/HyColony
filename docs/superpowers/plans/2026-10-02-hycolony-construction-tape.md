# Plan : ruban de chantier

Spec : `docs/superpowers/specs/2026-10-02-hycolony-construction-tape-design.md`. Chaque tâche : test d'abord, `./gradlew build` vert, un commit.

## Tâche 1 : la disposition (`core/construction/tape/TapeLayout`)

- Test `TapeLayoutTest` :
  - une emprise de 3 × 3 donne le contour élargi d'un bloc, dans l'ordre de MC (pour chaque pas : bord nord, bord sud, bord ouest, bord est, puis le coin sud-est) ;
  - les quatre coins sont des `CORNER` tournés vers l'intérieur, les côtés des `STRAIGHT` le long du bord ;
  - chaque ruban est posé au-dessus du premier bloc plein, en descendant depuis le Y le plus haut des coins, au plus hauteur + 5 ;
  - une colonne sans sol valide (ou dont la case du dessus est pleine ou occupée) n'a pas de ruban.
- Code : `record Tape(BlockPos pos, TapeShape shape, int rotation)`, `enum TapeShape`, `TapeLayout.of(min, max, WorldBlocks, ItemCatalog)`.

## Tâche 2 : poser et retirer (`ConstructionTape`, port `TapeBlocks`)

- Port `kernel/port/TapeBlocks` : `Optional<BlockState> tape(TapeShape, int rotation)`, `boolean isTape(BlockKey)`. `FakeTapeBlocks` dans `testing`, branché dans `GamePorts` et `TestContexts`.
- Test `ConstructionTapeTest` : pose autour d'un bâtiment (emprise de `HutFootprint`), rien si le réglage est coupé, retrait qui n'enlève que du ruban de `Y min − 5` à `Y max + 1`, un ruban voisin d'un autre chantier forme un T.
- Code : `ConstructionTape.place(colony, building)`, `remove(colony, building)` et `remove(colony, box)`.

## Tâche 3 : le réglage

- `ColonySettings.Toggle.CONSTRUCTION_TAPE` (vrai par défaut), `TownHallView.Settings.tape`, `ColonySerializer` (`settings.constructionTape`).
- Migration de schéma 8 en 9 (`MigrationV8ToV9`), avec la fixture `colony-v8-tape.json` et `MigrationV8ToV9Test` (skill `add-migration`).
- L'action du réglage existante (`HutActions`/`TownHallActions`) accepte la nouvelle bascule ; test.

## Tâche 4 : les branchements

- `WorkManager.create` pose ; `cancel` et `complete` retirent ; `BuildCompletion` retire autour des anciens coins avant la montée de niveau ; le retrait d'un bâtiment retire ; `WandPlacement` (survie) pose ; la fin d'un collage retire.
- Tests dans les classes de test de chacun (ou `ConstructionTapeFlowTest`) : pose à la demande d'un ordre, pas au rechargement d'une sauvegarde ; retrait à la fin, à l'annulation, à l'amélioration, au retrait du bâtiment, à la fin d'un collage.

## Tâche 5 : le bloc (plugin)

- Recherche dans les assets : un bloc sans collision cassé d'un coup et sans butin (`HitboxType`, `Gathering`, `Drops`), et ce qui rend un bloc « remplaçable » ; notes dans `docs/research/plugin-b-api.md`.
- `tools/tape/generate.py` : modèles `Straight`, `Corner`, `T_Junction`, `Cross_Junction` depuis la géométrie de MC, atlas des textures de Hytale, gabarit de raccord ; sortie dans `plugin/src/main/resources/`.
- `HyColony_Construction_Tape.json`, id-map, `HytaleTapeBlocks`, textes en-US et fr-FR, ligne du réglage dans l'onglet Réglages de l'hôtel de ville.

## Tâche 6 : documentation et relectures

- `docs/TESTING.md` : nouveaux points du ruban ; spec à jour des écarts constatés.
- Relectures : `hycolony-reviewer`, `mc-fidelity-checker`, `ui-lang-checker`.
