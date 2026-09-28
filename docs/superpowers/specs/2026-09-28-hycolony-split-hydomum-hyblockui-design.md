# Séparer HyColony en trois mods : HyBlockUI, HyDomum, HyColony

Aujourd'hui, Domum Ornamentum (DO) et la bibliothèque d'interface vivent dans HyColony :
- le cœur DO est dans `core/ornament` ;
- son adaptateur Hytale est dans `plugin/ornament` ;
- ses blocs forment un sous-pack désactivable ;
- le kit d'inventaire est dans `plugin/inventory`.

Ce projet les sort en **mods séparés**, dans le même dépôt, comme l'écosystème d'origine : MineColonies dépend de Domum Ornamentum et de BlockUI, qui sont des mods à part ([gradle.properties de MineColonies](https://github.com/ldtteam/minecolonies/blob/version/main/gradle.properties), [gradle.properties de Structurize](https://github.com/ldtteam/Structurize/blob/version/main/gradle.properties)).

## Décisions (validées avec l'utilisateur)

- **Trois mods, trois jars.**
  - **HyBlockUI** : la bibliothèque d'interface, comme BlockUI chez ldtteam.
  - **HyDomum** : le portage de Domum Ornamentum.
  - **HyColony**.
  - Les dépendances vont **dans un seul sens** : HyBlockUI ← HyDomum ← HyColony. HyDomum ignore HyColony, et HyBlockUI ignore les deux autres.
- **Tout renommer**, sans migration, puisque rien n'est publié :
  - les blocs `HyColony_DO_*` deviennent `HyDomum_*` ;
  - les clés `hycolony.ornament.*` deviennent `hydomum.*` ;
  - les clés d'interface d'inventaire deviennent `hyblockui.*` ;
  - la commande `/hyornament` devient `/hydomum`.

  Les blocs DO posés dans les mondes de test et les variantes sauvegardées ne sont pas repris.
- **Un seul dépôt, plusieurs modules Gradle**, avec les mêmes garde-fous partout.
- **Une base solide avant de refaire l'établi en kit générique** : le kit « établi » générique vient après, dans HyBlockUI.

## Faits vérifiés (sources)

- **Dépendances entre mods** : le manifeste a `Dependencies` et `OptionalDependencies` (`Groupe:Nom` → plage de versions), et aussi `LoadBefore` ([doctale](https://doctale.dev/getting-started/plugin-manifest/), [wiki technique](https://wiki.hytaleservers.host/Plugin_Manifest)).
  - `PluginManager` charge une dépendance avant le mod qui en dépend, et refuse de démarrer si elle manque ou si sa version ne convient pas (`server/core/plugin/PluginManager.java` l. 482-512, 1190-1196).
  - Une dépendance circulaire bloque le chargement ([ticket](https://github.com/realBritakee/hytale-template-plugin/issues/21)).
- **Chargement des classes** : chaque jar de mod a son `PluginClassLoader` « ThirdParty » (`childFirst = false`, `PluginManager.java` l. 593). Il cherche d'abord dans les classes du serveur, puis dans son propre jar, puis, par le chargeur « pont », dans ses `Dependencies` et `OptionalDependencies` (`PluginClassLoader.java` l. 83-130, `PluginManager.java` l. 1263-1285).
  - Conséquence : un mod ne doit **jamais embarquer** une copie d'un autre mod. Il utiliserait sa copie, et les classes seraient en double. Il compile contre lui, sans l'embarquer.
- **`Main` obligatoire** : tout jar de mod est chargé comme `JavaPlugin` depuis la classe `Main` du manifeste (`PendingLoadJavaPlugin.java` l. 55-62). HyBlockUI a donc une classe principale minimale.
- **Plugin Gradle AzureDoom 1.0.51** (celui qu'on utilise, sources en cache) : il existe un plugin racine `com.azuredoom.hytale-workspace`, qui fixe la version Hytale, le patchline et le groupe du manifeste de chaque mod (`HytaleWorkspacePlugin.groovy`).
  - Ses tâches `stageAllModAssets` et `runAllMods` déposent tous les mods dans **`run/mods` à la racine** et lancent **un seul serveur** avec tous les mods (`HytaleWorkspaceTaskRegistrar.groovy` l. 61-141).
  - `requiredDependency` ajoute une dépendance au classpath, mais n'écrit pas le manifeste. C'est la propriété `manifestDependencies` qui l'écrit.
  - Sources : [dépôt du plugin](https://github.com/AzureDoom/Hytale-Gradle-Plugin), [générateur de modèles multi-mods](https://github.com/cookieukw/Hytale-Mod-Template-Generator).
- **Bonnes pratiques Gradle** :
  - un build inclus `build-logic` avec des plugins de convention, plutôt que `buildSrc` ;
  - un catalogue de versions `gradle/libs.versions.toml` ;
  - les plugins de contrôle (Spotless, PMD, Error Prone) déclarés comme dépendances du build `build-logic` puis appliqués par les conventions.

  Sources : [structuration des builds](https://docs.gradle.org/current/userguide/best_practices_structuring_builds.html), [bonnes pratiques générales](https://docs.gradle.org/current/userguide/best_practices_general.html), [projets multi-modules](https://docs.gradle.org/current/userguide/multi_project_builds_intermediate.html), [ticket Spotless #747](https://github.com/diffplug/spotless/issues/747).
- **API d'un mod bibliothèque** : un mod expose une API distincte de son implémentation, comme DO avec son jar d'API ([Forge : dépendances](https://docs.minecraftforge.net/en/fg-5.x/dependencies/)). En Gradle : `implementation` par défaut, `api` seulement pour ce qui est exposé ([forum Gradle](https://discuss.gradle.org/t/best-practice-for-api-vs-implementation-in-multi-module-project/30519)).

## Organisation

```
HyColony/                   racine : com.azuredoom.hytale-workspace, gradle/libs.versions.toml
├── build-logic/            build inclus : plugins de convention hy.java-core et hy.hytale-mod
├── blockui/                mod HyBlockUI (dev.hyblockui), sans cœur de jeu
├── domum/core/             HyDomum, cœur Java pur (dev.hydomum.core), testé
├── domum/plugin/           mod HyDomum (dev.hydomum.plugin, API dev.hydomum.api)
├── core/                   HyColony, cœur Java pur (dev.hycolony.core)
└── plugin/                 mod HyColony (dev.hycolony.plugin)
```

- **`hy.java-core`** (cœurs purs) :
  - Java 25, Gson et jspecify en `compileOnly`, Error Prone et NullAway, PMD (`config/pmd/ruleset.xml`), Spotless (palantir) ;
  - tailles de fichiers et de paquets, séparateurs de section ;
  - un `ArchitectureTest` : aucun import `com.hypixel`.
- **`hy.hytale-mod`** (mods) :
  - `com.azuredoom.hytale-tools` et le manifeste (dépendances écrites dans `manifestDependencies`) ;
  - un jar qui embarque **seulement son propre cœur** ;
  - les autres mods en `compileOnly` ;
  - les mêmes contrôles que ci-dessus.
- **Paquet `api`** : chaque mod expose une API, et c'est tout ce que les autres mods ont le droit d'utiliser.
  - HyDomum : `dev.hydomum.api`, qui contient le registre des variantes, `VariantKey` et `OrnamentVariant` (l'actuel `ornament/api` en est l'embryon).
  - HyBlockUI : `dev.hyblockui.api`, qui contient les grilles, les dépôts, le suivi, les panneaux du joueur, `PageEvents`, `PageRedraw`, `HeldWindows`, `ReturningContainerWindow`, `PlayerItems` et la traduction avec paramètres.
  - Un test d'architecture vérifie que HyColony n'importe que les paquets `api` de ses dépendances, et HyDomum que celui de HyBlockUI.

## Ce qui va où

| Aujourd'hui | Demain |
|---|---|
| `core/src/.../core/ornament/**` et ses tests | `domum/core` (`dev.hydomum.core`), déplacé tel quel |
| `plugin/src/.../plugin/ornament/**` | `domum/plugin` (`dev.hydomum.plugin` ; `Ornaments` devient `HyDomumPlugin`, la classe principale) |
| `plugin/src/subplugins/DomumOrnamentum/` (sous-pack désactivable) | le pack d'assets du mod HyDomum ; le sous-pack disparaît. `tools/domum` écrit dans `domum/plugin/src/main/resources` |
| `IdMap.ornamentTags` (dans `hycolony/id-map.json`) | `hydomum/tags.json`, lu par HyDomum ; `IdMap` perd le champ |
| `HyColony.CutterCraftSeconds` (`ColonyConfig.HyColony`) | la config de HyDomum (`CutterCraftSeconds`, 0,5 par défaut, bornée de 0 à 10, bornes appliquées par son cœur) ; `ColonyConfig` perd le champ |
| `mods/HyColony/ornament-variants.json` et `ornament-assets/` | le dossier de données de HyDomum (nouveaux noms, sans migration) |
| `plugin/src/.../plugin/inventory/**`, `ui/PageEvents` | `blockui` (`dev.hyblockui.api`) |
| `Pages/HyColony/PlayerCharacterPanel.ui`, `PlayerStoragePanel.ui`, `Native/**` | `Pages/HyBlockUI/` dans le pack de HyBlockUI ; `ui.inventory.title` passe dans `hyblockui.lang` |
| `Pages/HyColony/Cutter*.ui` | `Pages/HyDomum/` |
| `Msg` et `HytaleNotifier` utilisés par l'établi | l'utilitaire de traduction avec paramètres de HyBlockUI |
| `CutterSystem` après `BlockUseProtectionSystem` | le système de protection de HyColony se déclare **avant** celui de l'établi de HyDomum |

Ce qui reste dans HyColony : tout le reste (colonie, citoyens, huttes, requêtes, construction, baguette). HyColony s'appuie sur `dev.hyblockui.api` pour ses fenêtres (onglet Inventaire du citoyen, `ColonyPage`) et déclare HyDomum et HyBlockUI dans son manifeste. DO-2b, les artisans qui fabriquent des blocs DO, passera par `dev.hydomum.api`.

## Garde-fous (accord explicite de l'utilisateur, session `HYCOLONY_GUARDRAILS_UNLOCKED=1`)

- Les contrôles du `build.gradle.kts` racine passent dans `build-logic/`, sans changer ce qu'ils vérifient : ils s'appliquent désormais à chaque module.
- `build-logic/` rejoint la liste des fichiers protégés dans trois fichiers qui la recopient : `CLAUDE.md` (§ 10), `AGENTS.md` et `.claude/hooks/guard.js`. Le banc de test du hook couvre ce nouveau chemin.
- `guard.js` refuse aussi `runAllMods` (et `stageAllModAssets` lancé pour exécuter), au même titre que `runServer`.
- `CLAUDE.md` décrit :
  - les modules ;
  - « un cœur Java pur par mod, sans `com.hypixel` » ;
  - les dépendances dans un seul sens ;
  - la règle des paquets `api`.
- Les listes d'exceptions (`gradle/*allowlist.txt`, `config/pmd/known-violations.txt`) sont reprises avec leurs chemins mis à jour, sans nouvelle entrée.

## Serveur de dev

`runAllMods` travaille dans `run/` **à la racine**. L'utilisateur déplace une fois ce qu'il veut garder de `plugin/run` : mondes, `config.json`, permissions. Le plan lui dit quoi déplacer. Comme toujours, c'est l'utilisateur qui lance le serveur, jamais Claude.

## Étapes

Chaque étape compile seule : build vert, relecture indépendante, commit.

1. **Essai en jeu, jetable** : trois mods minimaux avec `runAllMods`. On vérifie :
   - l'ordre de chargement ;
   - l'appel d'une classe de HyBlockUI depuis HyDomum ;
   - un `.ui` d'un mod qui en inclut un d'un autre ;
   - les trois packs d'assets actifs.

   Si un point bloque, on revoit la conception avant de déplacer quoi que ce soit.
2. **`build-logic` et garde-fous**, dans une session déverrouillée : catalogue de versions, workspace, conventions, contrôles déplacés. `core` et `plugin` y passent sans changer de comportement.
3. **HyBlockUI** : le module inventaire, `PageEvents`, les panneaux, les `.ui` et les textures natives y déménagent, et HyColony en dépend.
4. **HyDomum** :
   - le cœur, le plugin, le pack et le générateur y déménagent ;
   - la config, les tags, les données et `/hydomum` suivent ;
   - on applique les renommages et on inverse l'ordre de la protection.
5. **Nettoyage de HyColony** (`IdMap`, `ColonyConfig`, sous-pack DO), puis les docs et une relecture de toute la branche.

## Tests et vérifications

- **Tests existants** : les tests de `core/ornament` partent avec leur code, et le reste des tests de HyColony ne change pas.
- **Tests d'architecture** : chaque cœur a son `ArchitectureTest`. Des tests vérifient aussi que les imports entre mods ne passent que par les paquets `api`.
- **Build** : `./gradlew build` construit et vérifie les trois mods.
- **En jeu** (`docs/TESTING.md`, nouvelle section) :
  - les trois mods chargés sans SEVERE ;
  - HyDomum seul, sans HyColony, fonctionne : établi et `/hydomum` ;
  - HyColony avec ses deux dépendances fonctionne comme avant : fenêtres, onglet Inventaire du citoyen, protection de l'établi dans une colonie ;
  - sans HyDomum, HyColony refuse de démarrer, avec le message de dépendance manquante de Hytale.

## Documentation

- `CLAUDE.md` et `AGENTS.md` (garde-fous) sont mis à jour.
- `docs/research/plugin-b-api.md` reçoit une section « Mods multiples » : manifeste, chargeurs de classes, `Main`, workspace.
- `docs/native-ui-textures.md` suit les textures dans HyBlockUI.
- Les specs et plans existants de DO renvoient à cette spec pour les nouveaux chemins et noms.

## Hors de ce projet

- Le kit « établi » générique (onglets, recettes, ingrédients, aperçu, file de fabrication) dans HyBlockUI.
- Séparer Structurize (baguette, plans).
- Des dépôts séparés.
- Une migration des mondes de test.
