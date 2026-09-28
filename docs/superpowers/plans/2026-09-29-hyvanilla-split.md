# HyVanilla : quatrième mod (tapis, pots de fleurs)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** sortir le sous-pack `Decorations` de HyColony dans le mod `HyColony:hyvanilla` (cœur, plugin, pack, générateur), que HyColony exige.

**Spec :** `docs/superpowers/specs/2026-09-29-hyvanilla-design.md`. Modèle : `2026-09-29-hycolony-split-4-hydomum.md` (même opération pour DO).

## Contraintes globales

- Identité : `HyColony:hyvanilla`, `Main` = `dev.hyvanilla.plugin.HyVanillaPlugin`, `Dependencies` = `Hytale:AssetModule=*`. HyColony ajoute `HyColony:hyvanilla==0.1.0` à `manifest_dependencies` (`gradle.properties`).
- Aucune règle de jeu ne change. Les tests de `FlowerPot` et `FlowerPotBlocks` déménagent : seuls le paquet et les imports changent.
- `git mv` pour chaque déplacement. `./gradlew build` vert avant chaque commit. Relecture `hycolony-reviewer` pour chaque tâche, et ses corrections relues à leur tour.
- La tâche 1 touche `build-logic/`, et la tâche 5 `CLAUDE.md` et `AGENTS.md`. Elles demandent une **session déverrouillée** (`HYCOLONY_GUARDRAILS_UNLOCKED=1`) et l'accord explicite de l'utilisateur.

## Tâche 1 : garde-fous du build (session déverrouillée)

**Files :** `build-logic/src/main/kotlin/hy.java-checks.gradle.kts`

- [ ] `modApis` : ajouter `"dev.hyvanilla." to listOf("dev.hyvanilla.api.", "dev.hyvanilla.plugin.api.")`.
- [ ] `NullAway:AnnotatedPackages` : ajouter `dev.hyvanilla`. Mettre à jour le commentaire « dev.hycolony, dev.hydomum or dev.hyblockui ».
- [ ] `./gradlew build` vert, puis commit `build: HyVanilla's api packages and NullAway scope`.

## Tâche 2 : `:vanilla-core`

**Files :** `settings.gradle.kts`, `vanilla/core/build.gradle.kts` (nouveau), `core/src/{main,test}/java/dev/hycolony/core/decoration/*` → `vanilla/core/src/{main,test}/java/dev/hyvanilla/core/`, `core/src/test/.../ArchitectureTest.java`

- [ ] `settings.gradle.kts` : `include(":vanilla-core", ":vanilla-plugin")` et `projectDir` = `vanilla/core` et `vanilla/plugin` (gradle/gradle#847, comme domum).
- [ ] `vanilla/core/build.gradle.kts` : `hy.java-core`, `group = "dev.hyvanilla"`, sur le modèle de `domum/core/build.gradle.kts`.
- [ ] `git mv` de `FlowerPot`, `FlowerPotBlocks` et de leurs deux tests, puis paquet `dev.hyvanilla.core`. Les Javadocs qui citent « Decorations pack » citent HyVanilla.
- [ ] `ArchitectureTest` de HyColony (l. 166, 185) : retirer `dev.hycolony.core.decoration..`.
- [ ] `./gradlew :vanilla-core:test` vert. La tâche 3 compile le plugin : commit groupé avec la tâche 3 si `:plugin` ne compile pas seul entre les deux.

## Tâche 3 : `:vanilla-plugin` et son pack

**Files :** `vanilla/plugin/build.gradle.kts` (nouveau), `vanilla/plugin/src/main/java/dev/hyvanilla/plugin/{HyVanillaPlugin,VanillaIds}.java`, `.../plugin/api/HyVanillaSystems.java`, `.../plugin/block/{FlowerPotSystem,FlowerPotUse}.java` (déplacés), `plugin/src/subplugins/Decorations/{Common,Server}` → `vanilla/plugin/src/main/resources/`, `tools/decorations` → `tools/vanilla`, `.gitignore`

- [ ] `build.gradle.kts` : copie de `domum/plugin/build.gradle.kts`. Il `bundled(project(":vanilla-core"))`, sans `:blockui`. `modId = "hyvanilla"`, jar `HyVanilla`, `manifestDependencies = "Hytale:AssetModule=*"`, `CheckPackAssets` avec `namespace = "HyVanilla"`.
- [ ] `.gitignore` : `vanilla/plugin/src/main/resources/config.json{,.bak}`, comme domum.
- [ ] `git mv` de `FlowerPotSystem` et de `FlowerPotUse`. `FlowerPotSystem` :
  - perd sa dépendance `Order.AFTER BlockUseProtectionSystem`, et garde `if (event.isCancelled()) return;` ;
  - reçoit sa table de pots dans son constructeur (`Map<String, Map<String, String>>`), à la place de `IdMap` ;
  - dit « HyVanilla » dans son message de journal.
- [ ] `VanillaIds` : lit `/hyvanilla/id-map.json` (`flowerPots`). Il est calqué sur `DomumIds` : fichier absent ou illisible → vide, avec un avertissement. Il vérifie que les ids existent, comme `IdMap` l. 166-169.
- [ ] `HyVanillaSystems.flowerPotUse()` renvoie `FlowerPotSystem.class`, sur le modèle de `HyDomumSystems`. `HyVanillaPlugin.setup()` enregistre `FlowerPotSystem` **sans condition** : `SystemDependency` sur une classe non enregistrée lève une exception (`ComponentRegistry.java:657-659`). Les handlers attrapent `RuntimeException` et journalisent en SEVERE.
- [ ] Pack : `git mv` de `Decorations/Common` et `Decorations/Server` dans les ressources. `Decorations/hycolony/id-map.json` → `hyvanilla/id-map.json`. `hycolony.lang` → `hyvanilla.lang` (en-US, fr-FR).
- [ ] Générateur : `git mv tools/decorations tools/vanilla`. `PACK` = `vanilla/plugin/src/main/resources`, qui n'efface que ses chemins générés (le pack contient aussi `manifest.json`). Il écrit ensuite :
  - les ids `HyVanilla_Carpet_<C>` et `HyVanilla_Flower_Pot_<C>` (états compris) ;
  - les dossiers `Blocks/HyVanilla/`, `Icons/Items/HyVanilla/`, `Items/HyVanilla/` et `Hitboxes/HyVanilla/` ;
  - les clés `hyvanilla.item.*`.

  Relancer `python tools/vanilla/generate.py`. `git status` ne doit plus rien montrer sous l'ancien chemin, et aucun `HyColony_Carpet`/`HyColony_Flower_Pot` ne doit rester (`grep -r`).
- [ ] Catégorie créative : si le sous-pack en avait une, elle devient `HyVanilla`. Sinon, garder le rangement actuel des items.
- [ ] `./gradlew build` vert (`checkPackAssets`, `checkModApis`, `checkFileSizes`), puis commit `feat(vanilla): HyVanilla mod with carpets and flower pots`.

## Tâche 4 : HyColony exige HyVanilla

**Files :** `gradle.properties`, `plugin/build.gradle.kts`, `plugin/src/main/java/dev/hycolony/plugin/{IdMap,block/BlockSystems,block/BlockUseProtectionSystem}.java`, `plugin/src/subplugins/Decorations/` (supprimé), `docs/TESTING.md`

- [ ] `manifest_dependencies` : ajouter `HyColony:hyvanilla==0.1.0`. `plugin/build.gradle.kts` : `compileOnly(project(":vanilla-plugin"))`.
- [ ] `BlockUseProtectionSystem` : ajouter `new SystemDependency<>(Order.BEFORE, HyVanillaSystems.flowerPotUse())` à côté de celui de `cutterUse()`.
- [ ] `BlockSystems` : retirer l'enregistrement du pot et sa Javadoc. `IdMap` : retirer `flowerPots`, `pots()` et leur vérification.
- [ ] Supprimer ce qui reste de `plugin/src/subplugins/Decorations/` (`subplugin.json`). Les `Styles_*` restent. `subpluginNames` se met à jour tout seul.
- [ ] `docs/TESTING.md` :
  - § « Sous-plugin Decorations » (117-128) → « HyVanilla », avec les nouveaux ids. Le point 128 (pack coupé) devient « HyColony sans HyVanilla : le serveur refuse de démarrer en nommant la dépendance » ;
  - point 113 : « fragments : 4 merged » ;
  - ajouter un point : un étranger à la colonie ne peut ni planter ni reprendre une plante dans un pot de la colonie.
- [ ] `./gradlew build` vert, puis commit `refactor(plugin): flower pots and carpets move to HyVanilla`.

## Tâche 5 : règles et docs (session déverrouillée)

**Files :** `CLAUDE.md` (§ 1, § 7), `AGENTS.md`, `.claude/agents/hycolony-reviewer.md` (liste des plugins), `docs/superpowers/specs/2026-09-28-hycolony-carpets-flower-pots-design.md` (renvoi vers la spec HyVanilla)

- [ ] Passer de trois à quatre mods, avec le schéma de dépendances de la spec, `vanilla/core` et `vanilla/plugin`, `hyvanilla.lang` et `hyvanilla/id-map.json`.
- [ ] Commit `docs: HyVanilla, the fourth mod`.

## Vérification finale

- [ ] `./gradlew build` vert, relecture indépendante terminée, `git status` propre.
- [ ] Feu vert à l'utilisateur pour tester en jeu : `docs/TESTING.md` § HyVanilla, puis les jars de production dans `mods/` (quatre ensemble, puis HyColony sans HyVanilla).

## Écarts constatés à l'implémentation

- **Pas d'entrée `.gitignore`** : HyVanilla n'a pas de `config.json`.
- **Plus de vérification des ids de pots par `/hycolony selftest`** : le générateur écrit l'id-map et les objets, puis `validate_pack` et `CheckPackAssets` les contrôlent au build. Au démarrage, le journal affiche « HyVanilla: N flower pots ».
- **Ajouts de la relecture** :
  - `vanilla/core` reçoit son propre `ArchitectureTest` (pas d'import Hytale) ;
  - `VanillaIds` attrape `RuntimeException`, pour qu'une id-map malformée n'empêche pas le mod de se charger ;
  - les `sys.path` de `tools/domum`, qui importent `pack.py` et `models.py`, pointent sur `tools/vanilla`.
- **Tâches 2 à 4 en un seul commit** : `:plugin` ne compile pas entre elles.
