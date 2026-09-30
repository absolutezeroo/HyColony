# Audit global HyColony : 02, axe L, testabilité et outillage

```
ÉTAT : phase 2, axe L écrit. Code audité : commit 3e2e70ca. Sources : phase 0 (tests, build, CI, commits fix),
relecteurs cœur, construction/job, mods frères, docs/TESTING.md, docs/research/update-7/README.md.
Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 4/5.** 1 224 `@Test` (1 175 pour `core`, 73 pour les cœurs frères) sur 42 973 lignes de production, dont 27 145 lignes de tests pour 26 137 de `core` ; 59 des 60 derniers `fix(core)` portent un test (le 60e est un nettoyage d'avertissements) ; 0 nom de test hors camelCase ; 16 `Fake*` pour 16 ports (sauf `ColonyStorage`, testé sur disque avec `FailingStorage`/`FlakyStorage`) ; ArchUnit, PMD à liste décroissante, Error Prone + NullAway, spotless, `checkFileSizes`, CI sur chaque push ; `docs/TESTING.md` couvre chaque sous-projet livré (SP0 → HyDomum DO-2, fermier) et `/hycolony selftest` valide ids, plans, corps. Restent des reprises après rechargement non testées, deux chemins du cœur sans test, et des avertissements que le build tolère.

## 1. Constats

### L-1 — MOYEN — Reprise après rechargement non testée pour DECORATE, REMOVE, CLEAR_LEFTOVERS d'un plan MC, stade DONE et passe finale
`core/src/test/java/dev/hycolony/core/construction/builder/BuilderCleanupTest.java:112-121, 176, 265` ; `ConstructionSimulationTest.java:360, 632` ; `BuilderAITest.java:841`
Mécanisme : couverts CLEAR, SOLID, CLEAR_LEFTOVERS d'un prefab ; non couverts : DECORATE (reprise de `progressIndex` sur `decoPositions`), REMOVE (`removeList` haut → bas), CLEAR_LEFTOVERS d'un plan MC (`BuildSite.leftovers:111-116`, cellules d'air), stade DONE sauvegardé entre `site.progress(DONE, 0)` (`BuilderAI.java:221`) et `completeBuild` (une autosave peut tomber entre), passe finale (I-6).
Impact : le chemin « load → BUILDING_STEP → DONE → COMPLETE_BUILD » et la reprise d'un REMOVE ne sont vérifiés que par lecture.
Règle : § 8 (tout comportement du cœur a un test), § 5. Remède : quatre tests sur le modèle de `BuilderCleanupTest.restart()` (DECORATE : torche restante ; REMOVE : moitié des blocs minés ; DONE : `o.progress(Stage.DONE, 0)` puis restart → terminé sans replacer ; plan MC : une cellule d'air restante).
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU non.

### L-2 — MOYEN — Trois comportements du cœur sans test : déchargement d'un corps, lecture des permissions, règles de la table de découpe
`core/src/main/java/dev/hycolony/core/citizen/CitizenManager.java:200-208` (`onBodyUnloaded`, grep vide dans `core/src/test`) ; `core/.../colony/permission/PermissionsSerializer.java` (aucun test ne la référence) ; `domum/plugin/.../cutter/CutterCrafting.java:104-118` (A-4)
Mécanisme : ni le retrait de l'IA au déchargement, ni l'absence de double corps après rechargement ne sont testés ; `MissingKeysLoadTest`/`TolerantLoadTest` ne touchent ni `permissions` ni `ranks` (I-2) ; l'invariant DO-2 « à tout échec, rien n'est consommé ni donné » n'a aucun test parce que la règle est dans le plugin.
Règle : § 8. Remède : `unloadedBodyLosesItsAiAndReloadingItRebindsWithoutASecondBody` (`CitizenManagerTest`), `rankWithoutItsFlagsAndMemberWithoutNameLoadWithDefaults` (`MissingKeysLoadTest`), tests du `CutterCraftPlan` une fois remonté dans `domum/core`.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU partiel (cutter : BACKLOG).

### L-3 — BAS — Seize avertissements Error Prone et une API dépréciée pour retrait que le build laisse passer
`build-logic/src/main/kotlin/hy.java-checks.gradle.kts:28-33` ; `plugin/.../ui/highlight/HighlightMarkers.java:24` (`Player.getPlayerRef()`, `@Deprecated(forRemoval = true)`, `Player.java:1152`) ; 15 autres (G-3)
```kotlin
check("NullAway", CheckSeverity.ERROR)
```
Mécanisme : seul NullAway est en erreur ; `-Xlint:deprecation` n'est pas activé (« Some input files use or override a deprecated API ») ; un nouvel avertissement se perd dans les 16 existants.
Règle : § 3 (Error Prone tourne dans le build), § 1 (API vérifiée). Remède : traiter les 16 (un `switch` pour la rotation, `@SuppressWarnings` commentés pour les identités voulues) puis `-Werror` sur Error Prone et `-Xlint:deprecation` (garde-fou `build-logic/`, **accord de l'utilisateur requis**).
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### L-4 — BAS — Documentation périmée ou incomplète : Javadoc « 0.6.8 », constantes sans source MC, `tools/decorations` disparu
`domum/plugin/.../runtime/VariantAssets.java:31` (« pinned 0.6.8 ») ; `blockui/.../api/InventoryGrids.java:12` (« verified in game on 0.6.8 ») ; `docs/research/update-7/README.md` (« `tools/decorations/generate.py:21` » : le dossier n'existe plus, `tools/vanilla/pack.py:26` lit déjà la version épinglée) ; `core/.../colony/Colony.java:23-26`, `citizen/CitizenManager.java:18-20`, `citizen/CitizenAI.java:30-32` (M-32)
Mécanisme : le champ privé `CommonAssetModule.assets` lu par réflexion existe bien en 0.7.0-pre.4 (`CommonAssetModule.java:77`) : la note est seulement périmée ; les cadences de colonie et de respawn n'ont ni Javadoc ni source MC alors que `EXCEPTION_SUSPEND_TICKS` et `DECIDE_INTERVAL_TICKS` en ont une ; la liste « À migrer » d'Update 7 est faite à 6/8 (`toolType` GoblinMetal, `notifyTierUpgraded`, `markNeedsSaving`, `sp4-sleep-home.md`, `InventoryDrop`, `tags.py`), reste `HytaleWorldQuery.isLoaded` par colonne (DÉJÀ CONNU) et l'entrée `decorations` à retirer.
Règle : § 3, § 6. Remède : une ligne par cas ; le BACKLOG « Documenter tout le code » prévoit déjà la règle PMD `CommentRequired`.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU partiel.

### L-5 — BAS — `CLAUDE.md` § 1 et le prompt d'audit citent des chemins que le cœur vient de déplacer
`CLAUDE.md:15, 18` (`app/ui/UiPort` corrigé par c24bf475 ; l'exemple « `colony/view` » devenu `app/view` corrigé) ; `docs/BACKLOG.md` « Audit du code du 2026-09-29 » (entrée retirée par c24bf475)
Mécanisme : pendant cet audit, une autre session a déplacé la couche applicative vers `app` et mis à jour les docs (commit c24bf475) ; les chemins cités par les audits datés (`audit-A-core.md`, `audit-B…`) gardent l'ancien nom par choix (« keep the old name they criticise »).
Règle : § 10 (un garde-fou se modifie avec l'accord de l'utilisateur : fait). Remède : rien ; noté pour que le lecteur des audits datés ne cherche pas `colony/ui`.
Sévérité BAS · effort — · confiance HAUTE · DÉJÀ CONNU oui (fait).

## 2. Non retenus

- Couverture par paquet inégale (`building/module` 0 fichier de test, `kernel/ai` 1 pour 9) : les modules sont testés par les tests de bâtiments et de simulation (`ConstructionSimulationTest`, `WarehouseCourierSimulationTest`), `TickRateStateMachineTest` couvre le moteur ; le signal « fichiers de test par paquet » est trompeur ici.
- Absence de Checkstyle, SpotBugs, CPD dans le build : aucun défaut trouvé ailleurs que ces outils auraient attrapé et que PMD/Error Prone n'attrapent pas ; CPD a été lancé à la main par l'audit duplication (max 88 tokens).
- Plugins sans tests unitaires : règle du projet (§ 8), compensée par `/hycolony selftest` (ids, stockage, blueprint, place, container, break, types de huttes, spawn, move) et `docs/TESTING.md` (137 points numérotés).
- `ArchUnit` `beFreeOfCycles` seulement sur deux fonctionnalités : C-1.
- Contrôles Python des outils absents du `pre-push` : DÉJÀ CONNU (BACKLOG, garde-fou).
- ~55 lignes > 120 colonnes non vérifiées par palantir : DÉJÀ CONNU (BACKLOG, garde-fou).
- CI sans cache de dépendances explicite : `gradle/actions/setup-gradle@v5` le fait par défaut.
- `docs/TESTING.md` numérotation non monotone (133-136 entre 12 et 13, 44 après 135) : cosmétique.

## 3. Ce qui est bien fait

- `core/src/test/java/dev/hycolony/core/testing/` : 16 `Fake*` pour les ports, `TestContexts` et `TestJobs` : toute la logique du cœur s'exerce sans `Store` ni plugin (`ConstructionSimulationTest`, `WarehouseCourierSimulationTest` simulent des chantiers et des livraisons complets, avec redémarrage réel par `BuilderCleanupTest.restart()` : sauvegarde disque, nouveau manager, nouvelle IA).
- `git log --grep '^fix(core'` : 59/60 commits de correction portent leur test (§ 8 « tout bug corrigé a le test qui le reproduit »), noms en phrases camelCase (`unknownRequestsAndJobsNeverLockOrEraseAColony`…).
- `build-logic/src/main/kotlin/hy.java-checks.gradle.kts` : `checkPmdBaseline` fait échouer le build sur une violation nouvelle **et** sur une ligne de la liste qui ne se déclenche plus (la liste est forcée de rétrécir : 22 → 16 depuis l'audit B), `checkPmdRulesetLoads` refuse un ruleset illisible, `checkFileSizes`/`checkSectionDividers`/`checkModApis` sur chaque projet ; `.githooks` + `guard.js` avec son banc de test.

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| MOYEN | `core/src/test/.../construction/builder/BuilderCleanupTest.java:176, 265` | L-1 reprises après rechargement non testées |
| MOYEN | `core/.../citizen/CitizenManager.java:200-208` ; `colony/permission/PermissionsSerializer.java` ; `domum/plugin/.../cutter/CutterCrafting.java:104-118` | L-2 trois comportements sans test |
| BAS | `build-logic/src/main/kotlin/hy.java-checks.gradle.kts:28-33` ; `plugin/.../ui/highlight/HighlightMarkers.java:24` | L-3 avertissements tolérés, API dépréciée pour retrait |
| BAS | `domum/plugin/.../runtime/VariantAssets.java:31` ; `docs/research/update-7/README.md` | L-4 documentation périmée |
| BAS (fait) | `CLAUDE.md:15, 18` | L-5 chemins déplacés pendant l'audit |
