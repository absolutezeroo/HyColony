# Séparer HyColony en trois mods : HyBlockUI, HyDomum, HyColony

Aujourd'hui, deux blocs qui n'appartiennent pas à HyColony vivent dans son code :
- **Domum Ornamentum (DO)** :
  - le cœur est dans `core/ornament` ;
  - l'adaptateur Hytale est dans `plugin/ornament` ;
  - les blocs forment un sous-pack désactivable ;
  - les variantes sont stockées dans `universe/hycolony/`.
- **Le kit d'interface d'inventaire** : il est dans `plugin/inventory`.

Ce projet les sort en **mods séparés**, dans le même dépôt, comme l'écosystème d'origine : MineColonies dépend de Domum Ornamentum et de BlockUI, qui sont des mods à part ([gradle.properties de MineColonies](https://github.com/ldtteam/minecolonies/blob/version/main/gradle.properties), [gradle.properties de Structurize](https://github.com/ldtteam/Structurize/blob/version/main/gradle.properties)).

La première version de cette spec (5a2f204) a été relue par un agent indépendant. Cette version corrige les 12 points bloquants et les 11 points importants de cette relecture, puis les points d'une seconde relecture.

## Décisions (validées avec l'utilisateur)

- **Trois mods, trois jars**, avec des dépendances **dans un seul sens** : HyBlockUI ← HyDomum ← HyColony.
  - **HyBlockUI** est la bibliothèque d'interface, comme BlockUI.
  - **HyDomum** est le portage de Domum Ornamentum.
  - **HyColony** est le mod de colonie.

  HyDomum ignore HyColony ; HyBlockUI ignore les deux.
- **HyDomum devient obligatoire pour HyColony**, comme Domum Ornamentum l'est pour MineColonies. Le sous-pack DO, désactivé par défaut aujourd'hui, disparaît.
- **Un seul groupe, `HyColony`**, comme le nom d'équipe `ldtteam`. Le workspace propose un groupe commun à tous les mods : c'est une convention (`HytaleWorkspacePlugin.groovy:30`), qu'un module pourrait surcharger. On la garde. Les identifiants sont donc `HyColony:hyblockui`, `HyColony:hydomum` et `HyColony:hycolony`, et les dossiers de données `mods/HyColony_<nom>` (`PendingLoadJavaPlugin.java:58`).
- **Tout renommer, sans migration**, puisque rien n'est publié (liste complète plus bas). Les blocs DO posés dans les mondes de test et les variantes sauvegardées ne sont pas repris.
- **Exception à CLAUDE.md § 3, accordée par l'utilisateur** : la clé `HyColony.CutterCraftSeconds`, ajoutée le 2026-09-28 et jamais publiée, passe dans la config de HyDomum sans que l'ancienne clé reste lisible.
- **Un seul dépôt, plusieurs modules Gradle**, avec les mêmes garde-fous partout.
- **Quatre plans** au lieu d'un (voir « Plans »). Le kit « établi » générique vient après, dans HyBlockUI.

## Faits vérifiés (sources)

### Serveur Hytale 0.6.8 (sources décompilées)

**Dépendances entre mods.**
- Le manifeste a trois champs de dépendance : `Dependencies`, `OptionalDependencies` (`Groupe:Nom` → plage de versions) et `LoadBefore` (`Mod.java:84`).
- Une dépendance est chargée avant le mod qui en dépend. Il refuse de démarrer si elle manque ou si sa version ne convient pas (`PluginManager.java` l. 482-512, 1190-1196).
- Une dépendance circulaire bloque le chargement (`Mod.java:171`, `CYCLIC_DEPENDENCY`).
- Vérifié en production (plan 1, `plugin-b-api.md` § 28.4) : quand la dépendance d'un mod **qui a un pack** manque, le plugin est refusé proprement, mais son pack reste enregistré. `AssetModule` échoue alors à ordonner les packs, et **tout le serveur s'arrête**.

**Chargement des classes, en production.** Un jar placé dans `mods/` reçoit un `PluginClassLoader` « ThirdParty » avec `childFirst = false` (`PluginManager.java:593`). Ce chargeur cherche une classe dans cet ordre :
1. les classes du serveur (`PluginClassLoader.java:95`) ;
2. son propre jar ;
3. le chargeur « pont », qui essaie d'abord les `Dependencies` puis les `OptionalDependencies`, et **en dernier recours tous les plugins chargés** (`PluginManager.java` l. 1263-1295).

Conséquence : un mod ne doit **jamais embarquer** une copie d'un autre mod. Il utiliserait sa copie, et les classes seraient en double. Il compile contre lui sans l'embarquer.

**Chargement des classes, en dev.** Le serveur de dev ne charge pas comme la production. `runAllMods` met les classes et les ressources de **tous** les mods du workspace sur le classpath de la JVM. Les mods deviennent alors des plugins du classpath (`PluginManager.java` l. 679-686), avec `childFirst = true` (l. 237), et le chargeur du serveur voit tout.

Le dev ne vérifie donc ni l'isolation entre mods, ni l'absence de copie embarquée, ni le cas « dépendance absente ». Ces points se vérifient avec les **jars de production** dans le `mods/` d'un serveur.

**Classe `Main` obligatoire.** Tout jar de mod est chargé comme `JavaPlugin` depuis sa classe `Main` (`PendingLoadJavaPlugin.java` l. 55-62). `ValidateManifestTask` refuse aussi une `Main` vide.

**Ordre des systèmes.** `SystemDependency` désigne un système **par sa classe** (`SystemDependency.java`). Un système qui doit tourner avant un autre doit donc pouvoir référencer la classe de cet autre.

### Plugin Gradle AzureDoom 1.0.51

C'est celui qu'on utilise ; ses sources sont en cache et c'est la dernière version sur maven.azuredoom.com.

**Le workspace.**
- Le plugin racine `com.azuredoom.hytale-workspace` fixe la version Hytale, le patchline et le **groupe** de chaque mod.
- `stageAllModAssets` et `runAllMods` travaillent dans **`run/` à la racine** et lancent **un seul serveur**.
- À chaque lancement, `stageAllModAssets` **supprime** le dossier `run/mods/<Groupe_Nom>` de chaque mod (`StageAllModAssetsTask.groovy` l. 216-217), puis le recrée en liens vers ses ressources et ses classes (l. 220-224). Ce que le mod écrit à côté de ces liens (config, packs extraits) est donc perdu au lancement suivant.
- Le serveur hôte vaut par défaut le premier projet dans l'ordre alphabétique, sauf si `hostProject` est fixé.

**L'identité et le manifeste de chaque mod.**
- `HytaleExtensionDefaults` lit `mod_id`, `main_class` et `manifest_dependencies` dans le `gradle.properties` **racine** (l. 376-383). Sans surcharge par module, les trois mods auraient le même identifiant, et le workspace échouerait sur « Duplicate workspace plugin identifier ».
- `requiredDependency` ajoute une dépendance au classpath mais n'écrit pas le manifeste. C'est `manifestDependencies` qui l'écrit.
- `updatePluginManifest` réécrit un `manifest.json`, suivi par git, dans chaque module.

**Ce que chaque module reçoit.**
- Chaque module garde son propre `runServer` et son propre `run/`.
- Chaque module décompile le serveur dans son propre `build/` (`HytaleIdeSourceConfigurer:34`).
- Chaque module embarque `com/azuredoom/hytale/asseteditor/**` (`hytaleBundledRuntime`), sauf si `bundleAssetEditorRuntime = false`.

**Le chargement du plugin Gradle.** `HytaleWorkspacePlugin` appelle `getByType(HytaleExtension)` sur chaque sous-projet. Le workspace et `hytale-tools` doivent donc venir du **même** classpath Gradle.

### Gradle

- **Deux projets de même nom** (`:core` et `:domum:core`) sont confondus dans la résolution des dépendances ([gradle/gradle#847](https://github.com/gradle/gradle/issues/847), toujours ouvert ; le wrapper est en 9.5.1). Il faut des noms de projet uniques.
- **Bonnes pratiques** :
  - un build inclus `build-logic` avec des plugins de convention, plutôt que `buildSrc` ;
  - un catalogue de versions ;
  - les plugins de contrôle déclarés comme dépendances de `build-logic` puis appliqués par les conventions.

  Sources : [structuration des builds](https://docs.gradle.org/current/userguide/best_practices_structuring_builds.html), [bonnes pratiques générales](https://docs.gradle.org/current/userguide/best_practices_general.html), [projets multi-modules](https://docs.gradle.org/current/userguide/multi_project_builds_intermediate.html), [ticket Spotless #747](https://github.com/diffplug/spotless/issues/747).
- `hytale-tools` déclare des dépôts dans chaque projet (`HytaleRepositoryConfigurer`). On ne peut donc pas imposer `FAIL_ON_PROJECT_REPOS`.

### Écosystème

Un mod bibliothèque expose une API distincte de son implémentation, comme DO avec son jar d'API ([Forge : dépendances](https://docs.minecraftforge.net/en/fg-5.x/dependencies/)).

## Organisation

```
HyColony/                    racine : conventions hy.workspace, catalogue gradle/libs.versions.toml
├── build-logic/             build inclus : hy.workspace, hy.java-core, hy.hytale-mod (protégé)
├── blockui/                 projet :blockui        mod HyColony:hyblockui (dev.hyblockui)
├── domum/core/              projet :domum-core     cœur Java pur de HyDomum (dev.hydomum.core)
├── domum/plugin/            projet :domum-plugin   mod HyColony:hydomum (dev.hydomum.plugin)
├── core/                    projet :core           cœur Java pur de HyColony (dev.hycolony.core)
└── plugin/                  projet :plugin         mod HyColony:hycolony (dev.hycolony.plugin)
```

- **Noms de projet uniques.** Les chemins Gradle sont `:domum-core` et `:domum-plugin` (`projectDir = file("domum/core")`), ce qui corrige B1. Chaque mod a aussi son groupe Maven : `dev.hyblockui`, `dev.hydomum` et `dev.hycolony`.
- **`hy.workspace`**, appliqué à la racine :
  - `build-logic` a `hytale-gradle-plugin` en dépendance, épinglé en **1.0.51** (aujourd'hui `1.+`). Le workspace et les mods partagent donc le même classpath, ce dont `HytaleWorkspacePlugin` a besoin (il fait `getByType(HytaleExtension)` sur chaque sous-projet).
  - Il n'applique **que** `com.azuredoom.hytale-workspace` à la racine. Y appliquer `hytale-tools` ferait de la racine un faux mod, avec le plugin `java`, un manifeste réécrit, un jar et un `runServer` (`HytaleRunTaskRegistrar:23-25`).
  - Il fixe `hostProject = ':plugin'`.
  - Sources décompilées : la redirection prévue n'est pas faite (décidé au plan 2).
    - Le plugin AzureDoom réutilise le chemin `build/vineflower/hytale-server` de chaque projet dans trois tâches (`HytaleIdeSourceConfigurer.groovy` l. 34, 64, 92-93, 168). N'en rediriger qu'une les désynchroniserait.
    - La référence reste `build/vineflower/hytale-server` à la racine, celle que citent CLAUDE.md et le skill `hytale-api`. Elle est déjà présente et reste figée tant que Hytale est épinglé en 0.6.8.
    - Si elle manque, `./gradlew :plugin:decompileServerJar` l'écrit dans `plugin/build/vineflower/hytale-server`.
- **`hy.java-core`**, pour les cœurs purs :
  - Java 25, Gson et jspecify en `compileOnly` ;
  - Error Prone et NullAway, avec `AnnotatedPackages = dev.hycolony,dev.hydomum,dev.hyblockui` ;
  - PMD (`config/pmd/ruleset.xml`) et Spotless (palantir) ;
  - les tailles de fichiers et de paquets, les séparateurs de section ;
  - un `ArchitectureTest` : aucun import `com.hypixel`.
- **`hy.hytale-mod`**, pour les mods :
  - `hytale-tools` avec l'identité du mod en surcharge (`modId`, `mainClass`, `manifestDependencies` avec leurs versions, `modDescription`, `modCredits` : sans surcharge, la description et les auteurs viennent du `gradle.properties` racine, § 28.1) ;
  - un jar qui embarque **seulement son propre cœur**, sans `asseteditor` (`bundleAssetEditorRuntime = false`), sans `config.json`, `config.json.bak` ni `packs/**` ;
  - les autres mods en `compileOnly` ;
  - le contrôle des assets du pack (voir plus bas) ;
  - les mêmes contrôles de code que ci-dessus.
- **Versions** : les versions des outils de contrôle (PMD, Error Prone, NullAway, palantir, Spotless) vivent dans `build-logic/`, qui est protégé. Le catalogue ne contient que les bibliothèques (Gson, jspecify, JUnit).

## Identité de chaque mod

| Mod | Identifiant | `Main` | `Dependencies` | Données |
|---|---|---|---|---|
| HyBlockUI | `HyColony:hyblockui` | `dev.hyblockui.HyBlockUIPlugin` (minimale) | `Hytale:AssetModule` | `mods/HyColony_hyblockui` |
| HyDomum | `HyColony:hydomum` | `dev.hydomum.plugin.HyDomumPlugin` | `Hytale:AssetModule`, `HyColony:hyblockui` | `mods/HyColony_hydomum` (config) et `universe/hydomum/` (variantes, qui suivent les mondes, comme aujourd'hui `universe/hycolony/`) |
| HyColony | `HyColony:hycolony` | `dev.hycolony.plugin.HyColonyPlugin` | `Hytale:AssetModule`, `Hytale:NPC`, `HyColony:hyblockui`, `HyColony:hydomum` | inchangé |

Les versions des trois mods restent alignées (0.1.0), et chaque dépendance entre mods demande la même version exacte.

## API des mods

Chaque mod expose un paquet `api`. Seul ce paquet est visible des autres mods.

- **HyBlockUI** : `dev.hyblockui.api`. Il contient les grilles, les dépôts, le suivi, les panneaux du joueur, `PageEvents`, `PageRedraw`, `HeldWindows`, `ReturningContainerWindow`, `PlayerItems`, et la traduction avec paramètres qui remplace `Msg` et `HytaleNotifier` pour HyDomum.
- **HyDomum**, en deux parties :
  - `dev.hydomum.api`, **Java pur**, dans `domum/core` : `VariantKey`, `OrnamentShape`, `MaterialTags` et ce dont les règles de jeu auront besoin. Le cœur de HyColony pourra s'en servir pour DO-2b, parce que c'est du Java pur (corrige B3).
  - `dev.hydomum.plugin.api`, dans `domum/plugin` : le registre des variantes, `OrnamentVariant`, et `HyDomumSystems`, qui expose la classe du système d'utilisation de l'établi. HyColony déclare ainsi sa protection **avant** ce système sans importer de classe interne (corrige B4). Une `SystemDependency` est validée à l'enregistrement et lève une exception si la classe visée n'est pas enregistrée (`ComponentRegistry.java:657-659`). HyDomum enregistre donc ce système **sans condition, en premier** dans son `setup`. Aujourd'hui, `Ornaments.register` s'arrête quand il n'y a pas de tags (`Ornaments.java:44-46`), mais le système sait déjà ne rien faire sans catalogue.
- **Dépendances Gradle** :
  - `:core` → `:domum-core` en `compileOnly` et `testImplementation` ;
  - `:plugin` → `:domum-plugin`, `:domum-core` et `:blockui` en `compileOnly`. `compileOnly` n'est pas transitif, et `OrnamentVariant` expose `VariantKey` ;
  - `:domum-plugin` → `:blockui` en `compileOnly`.

  À l'exécution, ces classes viennent des jars des dépendances.
- **Le contrôle des imports entre mods** est une tâche Gradle, `checkModApis`, dans `build-logic` (protégé).
  - Elle est appliquée par **`hy.java-core` et `hy.hytale-mod`**, et branchée sur `check`. Elle couvre donc aussi `:core`, qui voit tout `:domum-core` en `compileOnly`.
  - Elle lit les `import` des sources de chaque module et échoue sur tout import d'un autre mod hors de ses paquets permis. Ceux-ci sont listés explicitement : `dev.hyblockui.api` pour HyBlockUI, `dev.hydomum.api` et `dev.hydomum.plugin.api` pour HyDomum.
  - Un nom pleinement qualifié écrit dans le code, sans `import`, lui échappe. C'est accepté, puisque le style impose des imports explicites. Ce n'est pas un test unitaire : le plugin n'en a toujours pas (CLAUDE.md § 8). Corrige B12.

## Ce qui va où

| Aujourd'hui | Demain |
|---|---|
| `core/src/.../core/ornament/**` et ses 6 tests | `domum/core` : `dev.hydomum.core` pour l'interne, `dev.hydomum.api` pour ce qui est exposé |
| `plugin/src/.../plugin/ornament/**` | `domum/plugin` (`dev.hydomum.plugin`, API `dev.hydomum.plugin.api`) ; `Ornaments` devient `HyDomumPlugin` |
| `plugin/src/subplugins/DomumOrnamentum/` | le pack d'assets du mod HyDomum (`domum/plugin/src/main/resources`). **Le sous-pack disparaît dans le même plan** (corrige I9) |
| `IdMap.ornamentTags` (dans `hycolony/id-map.json`) | `hydomum/tags.json`, lu par HyDomum ; `IdMap` perd le champ |
| `HyColony.CutterCraftSeconds` (`ColonyConfig.HyColony`) | la config de HyDomum, `CutterCraftSeconds` : 0,5 par défaut, bornée de 0 à 10 par un record du cœur HyDomum. `ColonyConfig` perd le champ, et `ColonyConfigTest` et `FreeWorkOrderTest` sont adaptés. Une clé restée dans un ancien `config.json` de HyColony est ignorée : le plan vérifie que `ConfigQuarantine` ne met pas le fichier de côté pour autant |
| `universe/hycolony/ornament-variants.json` et `ornament-assets/` | `universe/hydomum/` (sans migration) |
| `plugin/src/.../plugin/inventory/**`, `ui/PageEvents` | `blockui` (`dev.hyblockui.api`) |
| `.ui` de l'inventaire et `Native/**` | `Pages/HyBlockUI/` dans le pack de HyBlockUI. `ui.inventory.title` passe dans `hyblockui.lang`, et chaque mod a sa propre racine de ressources (`hyblockui/`, `hydomum/`, `hycolony/`), pour éviter qu'une ressource en masque une autre en dev (corrige I6) |
| `Pages/HyColony/Cutter*.ui` | `Pages/HyDomum/` |
| `Citizen.ui` (qui reste dans HyColony) | fait référence aux panneaux et aux textures de `Pages/HyBlockUI/` |
| `Msg` et `HytaleNotifier` dans l'établi **et** dans `OrnamentCommand` | la traduction avec paramètres de HyBlockUI |
| `CutterSystem` après `BlockUseProtectionSystem` | `BlockUseProtectionSystem` (HyColony) se déclare `BEFORE` la classe publiée par `HyDomumSystems` |

**Renommages complets** (corrige I4) :
- les blocs, objets et chemins d'assets : `HyColony_DO_*` → `HyDomum_*`, `Blocks/HyColony/DO/` → `Blocks/HyDomum/`, `Icons/ItemsGenerated/HyColony` → `Icons/ItemsGenerated/HyDomum` ;
- les données du générateur : `hycolony/ornament/shapes.json` et `icons/` → `hydomum/` ;
- les traductions : les 45 clés `ornament.*` de `hycolony.lang` et les clés `hycolony.item.do.*` du sous-pack passent dans `hydomum.lang` (en-US et fr-FR) ;
- la commande : `/hyornament` → `/hydomum` ;
- le générateur `tools/domum` : ses identifiants écrits en dur dans `check_*.py`. Le plan **exécute** `python tools/domum/generate.py` et `check.py`, que le build Gradle ne lance pas.

**Contrôle des assets de chaque pack** (corrige B11) : `checkSubpluginAssets` protège aujourd'hui les sous-packs contre un asset invalide, qui arrêterait tout le serveur (`plugin-b-api.md` § 23). Ce contrôle passe dans `hy.hytale-mod` et s'applique au pack de chaque mod, avec son propre espace de noms. Les sous-packs restants de HyColony (Décorations, styles) gardent le leur.

Ce qui reste dans HyColony : tout le reste (colonie, citoyens, huttes, requêtes, construction, baguette, sous-packs Décorations et styles).

## Config et données en dev (corrige B7)

- **En production**, chaque mod garde sa config dans son dossier de données (`mods/HyColony_<nom>/config.json`), et les variantes de HyDomum dans `universe/hydomum/`.
- **En dev**, `runAllMods` recrée `run/mods/HyColony_<nom>` à chaque lancement, en liens durs vers les ressources et les classes. L'essai du plan 1 l'a vérifié (`plugin-b-api.md` § 28.5) :
  - un `config.json` placé dans les ressources du mod est **lu** au démarrage ;
  - la première écriture du mod remplace le lien, et le fichier des ressources ne change pas ;
  - ce que le mod écrit dans `run/mods/<mod>/` est perdu au lancement suivant ;
  - `run/universe/` survit.
- **Solution retenue** : la piste 1, un `config.json` dans les ressources de chaque mod.
  - **Emplacements** : `blockui/src/main/resources/config.json`, `domum/plugin/src/main/resources/config.json` et `plugin/src/main/resources/config.json`, tous ignorés par git.
  - **Fonctionnement** : on règle ce fichier **à la main**, et le mod le relit à chaque lancement. Aucun code n'est nécessaire.
  - **Changement par rapport à aujourd'hui** : `plugin/run/mods/HyColony_hycolony` est un **lien symbolique vers tout** `plugin/src/main/resources`. Le mod y réécrit donc sa config, avec les clés ajoutées à leur valeur par défaut, et son `.bak`. Avec les liens durs de `runAllMods`, ça ne se produit plus.
    - Les valeurs par défaut ajoutées par le mod ne reviennent plus dans les ressources : c'est accepté.
    - Une nouvelle clé se recopie à la main depuis `run/mods/HyColony_<nom>/config.json`.
  - **Écart au plan 1** : sa règle (tâche 5, étape 3) aurait choisi la piste 3, une tâche qui recopie une config gardée hors des ressources après le staging. Les deux pistes ont le même effet en dev, et la piste 3 coûte une tâche Gradle de plus.
    - Le seul avantage propre de la piste 3 serait une config jamais embarquée dans le jar, ni liée en dur. Il disparaît parce que `hy.hytale-mod` exclut `config.json` et `config.json.bak` du jar : l'essai a montré qu'un `config.json` des ressources y serait embarqué.
  - **Une écriture sur place traverse le lien dur** (`plugin-b-api.md` § 28.5). `PackAssets.extract` réécrit ainsi `<dossier du mod>/packs/<nom>.zip` avec `Files.write` (`PackAssets.java:54`). Le plan 2 fait donc trois choses avant de passer à `runAllMods` :
    - il supprime `plugin/src/main/resources/packs/` et `plugin/src/main/resources/config.json.bak`, deux fichiers locaux écrits par l'ancien lien symbolique, pour qu'aucun zip des ressources ne soit lié en dur ;
    - il retire l'entrée `packs/` de `.gitignore` ;
    - il corrige le commentaire de `.gitignore` « via the mods/ symlink ». `guard.js` ne cite pas `packs/`.
  - **`ConfigQuarantine` en dev** : un `config.json` illisible dans les ressources est mis de côté dans `run/mods/…`, pas dans les ressources. Le mod repart donc des valeurs par défaut à chaque lancement tant que le fichier des ressources n'est pas réparé. Le plan 2 adapte le point 59 de `docs/TESTING.md` : on casse le `config.json` des ressources, le `.broken-<date>` et le fichier neuf apparaissent dans `run/mods/HyColony_hycolony/`, puis on répare le fichier des ressources.
- **Le dossier du serveur de dev** passe de `plugin/run` à `run/` à la racine. Il faut en reprendre les mondes (`universe/`) et les permissions. La config de HyColony, elle, n'est pas dans `plugin/run`. Le plan dit précisément quoi déplacer.
- **Chemins protégés** : `guard.js` et `.gitignore` protègent `blockui/src/main/resources/config.json` et `domum/plugin/src/main/resources/config.json` (avec leurs `.bak`), comme aujourd'hui `plugin/src/main/resources/config.json`. Ce sont des chemins exacts, que `guard.js` sait déjà comparer. `run/mods/*/config.json` n'a pas besoin de protection, puisque le staging l'écrase à chaque lancement.

## Garde-fous (accord explicite de l'utilisateur, session `HYCOLONY_GUARDRAILS_UNLOCKED=1`)

**Avant tout** (plan 1, première étape, corrige B5) : `guard.js` et son banc de test refusent `runAllMods`. Ils le font sous toutes ses formes, y compris abrégées, comme `runServer`. `stageAllModAssets` reste permis, puisqu'il ne lance rien.

**Dans le plan 2**, les contrôles quittent le `build.gradle.kts` racine pour `build-logic/`, sans changer ce qu'ils vérifient.
- **Aucun module ne peut échapper aux contrôles** (corrige B6) : le `build.gradle.kts` racine, qui reste protégé, vérifie que **chaque** sous-projet applique `hy.java-core` ou `hy.hytale-mod`, et échoue sinon. Ce contrôle se place **après** le premier commentaire `// CLAUDE.md §` du fichier, là où commence la partie que `guard.js` protège.
- **Listes à mettre à jour.** `build-logic/` rejoint la liste des fichiers protégés partout où elle est recopiée :
  - CLAUDE.md § 10 et `AGENTS.md` ;
  - `.claude/hooks/guard.js` et son banc de test ;
  - `.claude/agents/hycolony-implementer.md`, qui nomme aussi `runServer`.

  Les agents et les skills qui citent `plugin/src`, `hycolony.lang` ou `build/vineflower` sont mis à jour (`add-lang-key`, `hytale-api`, `port-mc`, les agents relecteur et chercheur).
- **Hooks git.** `.githooks/pre-commit` surveille aussi `build-logic/**`, y compris les `.kt` si des conventions sont écrites en classes.
- **Listes d'exceptions** (corrige I2) : aucune entrée ne bouge. Les allowlists sont vides, et les violations PMD connues portent sur des fichiers qui restent en place. Une ligne peut seulement disparaître. Réécrire un chemin compterait comme une entrée nouvelle, ce que `guard.js` refuse même en session déverrouillée.
- **`CLAUDE.md`** décrit :
  - les cinq projets et leurs identifiants ;
  - « un cœur Java pur par mod, sans `com.hypixel` » ;
  - les dépendances dans un seul sens ;
  - la règle des paquets `api` et `checkModApis` ;
  - la vérification en production avec les jars.

## Plans (corrige I11)

Chaque étape de chaque plan compile seule : build vert, relecture indépendante, commit. Les `manifest.json` réécrits par `updatePluginManifest` sont commités avec l'étape qui les change (M6).

1. **Garde-fous et essai**, dans une session déverrouillée.
   - `guard.js` bloque `runAllMods`.
   - Puis un **essai jetable** : trois mods minimaux dans une branche d'essai. On vérifie :
     - en **dev** (`runAllMods`) : le chargement, un `.ui` d'un mod qui en inclut un d'un autre, **une texture d'un autre pack** (I5), la config et les données après un second lancement (B7) ;
     - en **production** : les trois jars dans le `mods/` d'un serveur, sans classpath de dev. On y vérifie l'appel de classe entre mods, le comportement quand une dépendance manque (constaté : arrêt de tout le serveur, `plugin-b-api.md` § 28.4), et l'absence de classes en double (B2).

     C'est l'utilisateur qui lance les serveurs.
   - Les résultats sont écrits dans `plugin-b-api.md`, et la spec est ajustée si besoin.
2. **`build-logic`**, dans une session déverrouillée : conventions, catalogue, workspace, noms de projet, contrôles déplacés et contrôle d'application des conventions. `core` et `plugin` y passent sans changer de comportement.
3. **HyBlockUI** : le module inventaire, `PageEvents`, les panneaux, la traduction, les `.ui` et les textures y déménagent. HyColony en dépend.
4. **HyDomum et nettoyage** :
   - le cœur (avec son API pure), le plugin, le pack et le générateur déménagent ;
   - la config, les tags et les données suivent, avec `/hydomum` ;
   - les renommages sont appliqués, et le sous-pack est supprimé dans la même étape ;
   - la protection passe par `HyDomumSystems` ;
   - `IdMap` et `ColonyConfig` sont nettoyés ;
   - les docs sont mises à jour, puis toute la branche est relue.

La CI (`.github/workflows/gradle.yml`) ne se déclenche que sur `main`, une branche qui n'existe pas. Elle est hors de ce projet, mais le plan 2 le signale à l'utilisateur.

## Tests et vérifications

- **Tests du cœur.** Ceux de `core/ornament` partent avec leur code. `ColonyConfigTest` et `FreeWorkOrderTest` perdent `cutterCraftSeconds`, et le cœur de HyDomum reçoit le test de sa config.
- **Contrôles du build.** Chaque cœur a son `ArchitectureTest`. `checkModApis` contrôle les imports entre mods, et le build racine vérifie que chaque projet applique sa convention.
- **Build et générateur.** `./gradlew build` construit et vérifie les trois mods. `python tools/domum/check.py` passe.
- **En jeu** : la section DO de `docs/TESTING.md` (points 133 à 162) est **réécrite** pour HyDomum : `/hydomum`, plus de `SubPlugins`, `universe/hydomum` (corrige I8). Une nouvelle section couvre :
  - en dev, les trois mods chargés sans SEVERE ;
  - en production, les trois jars ensemble, puis HyDomum seul (établi et `/hydomum`), puis HyColony sans HyDomum : **le serveur entier ne démarre pas**. Le pack de HyColony reste enregistré et fait échouer l'ordre des packs (`crash.startFailed`), et le journal nomme la dépendance manquante (`plugin-b-api.md` § 28.4). Même arrêt pour HyDomum sans HyBlockUI, puisque HyDomum a un pack. La documentation d'installation dit d'installer les trois jars ensemble ;
  - HyColony comme avant : fenêtres, onglet Inventaire du citoyen, protection de l'établi dans une colonie.

## Documentation

- `CLAUDE.md`, `AGENTS.md`, les agents et les skills sont mis à jour (voir « Garde-fous »).
- `docs/research/plugin-b-api.md` reçoit une section « Mods multiples » : manifeste, les deux modes de chargement des classes (dev et production), `Main`, workspace et staging.
- `docs/native-ui-textures.md` suit les textures dans HyBlockUI.
- `docs/TESTING.md` est réécrit pour la partie DO.
- Les specs et plans de DO renvoient à celle-ci pour les nouveaux chemins et noms.

## Hors de ce projet

- Le kit « établi » générique.
- Séparer Structurize (baguette, plans).
- Des dépôts séparés.
- Une migration des mondes de test.
- La CI.
