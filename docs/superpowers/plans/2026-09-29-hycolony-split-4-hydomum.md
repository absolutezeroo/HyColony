# Séparation en trois mods, plan 4 : HyDomum et nettoyage

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** sortir Domum Ornamentum de HyColony dans le mod `HyColony:hydomum` (cœur Java pur, plugin, pack, générateur), que HyColony exige, avec les renommages complets, puis mettre CLAUDE.md, les agents, les skills et les docs à jour.

**Architecture :**
- Deux projets Gradle :
  - `:domum-core` (`domum/core`, `hy.java-core`, groupe `dev.hydomum`) : `dev.hydomum.api` (Java pur exposé : `VariantKey`, `OrnamentShape`, `MaterialTags`), `dev.hydomum.core` (le reste), `dev.hydomum.core.cutter` ;
  - `:domum-plugin` (`domum/plugin`, `hy.hytale-mod`) : le mod, `dev.hydomum.plugin` (+ `cutter`, `debug`, `persistence`, `registry`, `runtime`), API `dev.hydomum.plugin.api` (`OrnamentVariant`, `HyDomumSystems`). Il embarque `:domum-core` (`bundled`) et dépend de HyBlockUI.
- Le pack du sous-pack `DomumOrnamentum` devient le pack du mod. Le sous-pack disparaît.
- HyColony ne voit de HyDomum que `HyDomumSystems`, pour placer sa protection avant l'établi.

**Tech Stack :** Java 25, Hytale 0.6.8, conventions `build-logic`, générateur Python `tools/domum` (Python 3.10+, Pillow).

**Spec :** `docs/superpowers/specs/2026-09-28-hycolony-split-hydomum-hyblockui-design.md` (§ « Identité », « API des mods », « Ce qui va où », renommages, « Garde-fous », « Plans » point 4, « Tests »). Résultats de l'essai : `docs/research/plugin-b-api.md` § 28.

## Contraintes globales

- Identité : `HyColony:hydomum`, `Main` = `dev.hydomum.plugin.HyDomumPlugin`, `Dependencies` = `Hytale:AssetModule=*`, `HyColony:hyblockui==0.1.0`. HyColony ajoute `HyColony:hydomum==0.1.0` (la version exacte s'écrit `==`, `plugin-b-api.md` § 28.1).
- Dépendances dans un seul sens : HyBlockUI ← HyDomum ← HyColony. Entre mods, `compileOnly` seulement ; `checkModApis` passe.
- Renommages complets, sans migration : `HyColony_DO_*` → `HyDomum_*`, `Blocks/HyColony/DO/` → `Blocks/HyDomum/`, `Icons/ItemsGenerated/HyColony/DO` → `Icons/ItemsGenerated/HyDomum`, `/hyornament` → `/hydomum`, `universe/hycolony/ornament-*` → `universe/hydomum/`, clés `hycolony.ornament.*` → `hydomum.ornament.*`.
- Aucune règle de jeu ne change ; les tests du cœur déménagent avec leur code et passent tels quels (seuls paquets et imports changent).
- Tâches 3 et 4 : session **déverrouillée** (elles touchent `build-logic/`, `CLAUDE.md`, `AGENTS.md`, `.claude/agents/`, `.claude/skills/`) et accord de l'utilisateur (donné le 2026-09-29 pour ce projet ; le redemander si la session a été relancée entre-temps).
- `git mv` pour tout déplacement ; `./gradlew build` vert avant chaque commit ; relecture indépendante de chaque tâche, corrections relues ; commits `type(scope): description` + `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` ; `git add` de chemins explicites, jamais `config.json`.
- **Une autre session travaille dans le même dossier** : `git status --short` avant chaque tâche ; si un fichier de la liste « Files » a des changements d'une autre session, s'arrêter et le signaler. Commiter avec `git commit -- <chemins>` si l'index contient des renommages d'une tâche suivante.
- On ne lance jamais le serveur ; l'utilisateur teste en jeu.

## Écarts à la spec, décidés en écrivant ce plan (écrits dans la spec à la tâche 4)

1. **Traductions des blocs générés** : le générateur efface et réécrit son fichier `.lang` en entier ; il ne peut pas partager `hydomum.lang` avec les textes écrits à la main. Les noms de blocs vont dans `hydomum_blocks.lang` (clés `hydomum_blocks.item.do.*`), les 45 textes de l'établi et de la commande dans `hydomum.lang` (`hydomum.ornament.*`). Un sous-dossier de langue (`hydomum/blocks.lang`) aurait donné le préfixe `hydomum.blocks`, mais `I18nModule.getPrefix` (l. 356-364) remplace `File.separatorChar` : dans un jar lu sous Windows, le `/` resterait dans la clé.
2. **Le générateur n'efface plus `Common/` et `Server/` en entier** (`generate.py` l. 36-38) : le pack est maintenant les ressources du mod, qui contiennent aussi les `.ui` de l'établi, `manifest.json` et `hydomum.lang`. Il efface une liste explicite de chemins générés.
3. **Identifiants et sons** : `hydomum/id-map.json` (`sounds`, `ornamentTags`), comme `hycolony/id-map.json`, au lieu de `hydomum/tags.json` : les deux sons de l'établi en font partie.
4. **API de HyDomum** : `dev.hydomum.plugin.api` n'expose que `OrnamentVariant` et `HyDomumSystems` ; le registre reste interne tant que DO-2b n'en a pas besoin. De même, `:core` et `:plugin` ne dépendent pas de `:domum-core` : rien ne s'en sert encore.
5. **`ConfigQuarantine` et `UiSounds`** passent dans HyBlockUI (`dev.hyblockui.api`) : HyDomum en a besoin comme HyColony, et HyBlockUI est la bibliothèque commune aux deux. `ConfigQuarantine` reçoit le nom du mod pour ses messages.
6. **Contrôle des assets d'un pack** : la logique de `checkSubpluginAssets` devient une tâche `CheckPackAssets` de `build-logic`, que chaque mod déclare pour ses packs (HyColony : ses sous-packs, comme avant ; HyDomum : son pack). Elle ne s'applique pas d'office au pack principal de HyColony, qui n'était pas contrôlé.

## Review Focus

- **L'établi qu'un étranger à la colonie peut utiliser** si l'ordre des systèmes entre mods ne tient pas : tâche 2, étape 9, point 5 (essai en jeu).
- **Une clé de traduction affichée brute** après les renommages (établi, commande, noms de blocs, onglet créatif) : tâche 3, étape 10.
- **Le générateur qui efface un fichier écrit à la main** : tâche 3, étape 5 (liste `git status` avant/après une seconde génération).
- **HyColony démarré sans HyDomum** : le serveur ne démarre pas (§ 28.4) ; tâche 4 l'écrit dans `TESTING.md`.
- **Une vieille config de HyColony** (`HyColony.CutterCraftSeconds`, `SubPlugins.DomumOrnamentum`) : pas de quarantaine (`ExtraInfo.readUnknownKey` note la clé sans échouer) ; tâche 2, étape 9, point 6.

## Fichiers (vue d'ensemble)

- `core/src/{main,test}/java/dev/hycolony/core/ornament/**` → `domum/core/src/{main,test}/java/dev/hydomum/{api,core}/**`
- `plugin/src/main/java/dev/hycolony/plugin/ornament/**` → `domum/plugin/src/main/java/dev/hydomum/plugin/**`
- `plugin/src/subplugins/DomumOrnamentum/{Common,Server}` → `domum/plugin/src/main/resources/{Common,Server}` ; `subplugin.json` et son fragment `hycolony/id-map.json` supprimés
- `plugin/src/main/resources/hycolony/ornament/{shapes.json,icons/}` → `domum/plugin/src/main/resources/hydomum/`
- `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Cutter*.ui` → `domum/plugin/src/main/resources/Common/UI/Custom/Pages/HyDomum/`
- `plugin/…/config/ConfigQuarantine.java`, `plugin/…/ui/UiSounds.java` → `blockui/…/dev/hyblockui/api/`
- Modifiés : `ColonyConfig` (+ tests), `HyColonySection`, `HyColonyPlugin`, `IdMap`, `BlockUseProtectionSystem`, `settings.gradle.kts`, `gradle.properties`, `plugin/build.gradle.kts`, `tools/domum/**`, `hycolony.lang`, CLAUDE.md, AGENTS.md, agents, skills, docs.

---

### Tâche 1 : les projets `:domum-core` et `:domum-plugin`, vides

**Files :**
- Create : `domum/core/build.gradle.kts`, `domum/plugin/build.gradle.kts`, `domum/plugin/src/main/java/dev/hydomum/plugin/HyDomumPlugin.java`, `domum/core/src/test/java/dev/hydomum/ArchitectureTest.java`
- Modify : `settings.gradle.kts`, `gradle.properties`, `plugin/build.gradle.kts`

**Interfaces :**
- Produit : `:domum-core` et `:domum-plugin` ; `:plugin` → `compileOnly(project(":domum-plugin"))` ; le manifeste de HyColony exige `HyColony:hydomum` `=0.1.0`.

- [ ] **Étape 1 : les projets**

`settings.gradle.kts` : remplacer `include(":core", ":plugin", ":blockui")` par :

```kotlin
include(":core", ":plugin", ":blockui", ":domum-core", ":domum-plugin")
// Unique project names (gradle/gradle#847: two ":core" projects would be confused in dependency resolution).
project(":domum-core").projectDir = file("domum/core")
project(":domum-plugin").projectDir = file("domum/plugin")
```

`domum/core/build.gradle.kts` :

```kotlin
plugins { id("hy.java-core") }

// HyDomum's pure-Java core (split spec § Organisation): game rules of Domum Ornamentum, no Hytale.
group = "dev.hydomum"
```

`domum/plugin/build.gradle.kts` :

```kotlin
plugins { id("hy.hytale-mod") }

// HyDomum, the port of Domum Ornamentum (split spec § Identité de chaque mod).
group = "dev.hydomum"

dependencies {
    // Its own pure core ships inside its jar.
    bundled(project(":domum-core"))
    // Another mod: compiled against, never shipped (plugin-b-api.md § 28.2).
    compileOnly(project(":blockui"))
    compileOnly(libs.gson)
}

hytaleTools {
    modId = "hydomum"
    mainClass = "dev.hydomum.plugin.HyDomumPlugin"
    modDescription = "Domum Ornamentum for Hytale: architect's blocks in any materials, cut at the architect's cutter."
    modCredits = project.property("mod_author").toString()
    manifestDependencies = "Hytale:AssetModule=*,HyColony:hyblockui==0.1.0"
    manifestOptionalDependencies = ""
}

tasks.named<Jar>("jar") { archiveBaseName.set("HyDomum") }
```

`domum/plugin/src/main/java/dev/hydomum/plugin/HyDomumPlugin.java` :

```java
package dev.hydomum.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;

/** HyDomum's entry point (MC DO's DomumOrnamentum mod class); wires nothing yet. */
public final class HyDomumPlugin extends JavaPlugin {
    public HyDomumPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }
}
```

`domum/core/src/test/java/dev/hydomum/ArchitectureTest.java` (le cœur de HyDomum, comme celui de HyColony, ne touche jamais Hytale) :

```java
package dev.hydomum;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.hydomum", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule coreNeverTouchesHytale =
            noClasses().should().dependOnClassesThat().resideInAPackage("com.hypixel..");

    /** The pure API that other mods' cores may use (split spec § API des mods) stays pure of the internals. */
    @ArchTest
    static final ArchRule apiDoesNotDependOnInternals =
            noClasses().that().resideInAPackage("dev.hydomum.api..").should().dependOnClassesThat()
                    .resideInAPackage("dev.hydomum.core..");
}
```

**Fait à l'exécution :** avec un cœur vide, ArchUnit échoue (« rule … was applied to no classes »). Plutôt que de désactiver `failOnEmptyShould`, ce test est **reporté à la tâche 2, étape 4**, qui l'ajoute avec les classes du cœur.

- [ ] **Étape 2 : HyColony exige HyDomum**

`plugin/build.gradle.kts` : sous `compileOnly(project(":blockui"))`, ajouter `compileOnly(project(":domum-plugin"))`.

`gradle.properties` : `manifest_dependencies = …,HyColony:hyblockui==0.1.0` devient `manifest_dependencies = Hytale:AssetModule=*,Hytale:NPC=*,HyColony:hyblockui==0.1.0,HyColony:hydomum==0.1.0`.

- [ ] **Étape 3 : build, manifestes, jars, staging**

```bash
./gradlew build --console=plain
cat domum/plugin/src/main/resources/manifest.json
git diff plugin/src/main/resources/manifest.json
unzip -l domum/plugin/build/libs/HyDomum-0.1.0.jar | awk '{print $4}' | grep -v '/$'
./gradlew stageAllModAssets --console=plain -q && ls run/mods/
```

Attendu : build vert ; manifeste de HyDomum conforme à l'identité (`"HyColony:hyblockui": "=0.1.0"`) ; celui de HyColony gagne `"HyColony:hydomum": "=0.1.0"` ; le jar ne contient que `HyDomumPlugin.class` et `manifest.json` ; `run/mods/` liste trois mods (si le staging échoue sur un `.lck` de `run/logs/`, un serveur tourne : demander à l'utilisateur de l'arrêter).

- [ ] **Étape 4 : relecture et commit**

`hycolony-reviewer` : « projets :domum-core et :domum-plugin vides, identité de HyDomum, dépendances et manifestes ». Puis :

```bash
git add settings.gradle.kts gradle.properties plugin/build.gradle.kts plugin/src/main/resources/manifest.json domum/core/build.gradle.kts domum/core/src domum/plugin/build.gradle.kts domum/plugin/src/main/java domum/plugin/src/main/resources/manifest.json
git diff --cached --stat
git commit -m "$(cat <<'EOF'
feat(domum): empty HyDomum mod that HyColony requires

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Tâche 2 : le code de Domum Ornamentum déménage dans HyDomum

**Files :**
- Move : `core/…/ornament/**` (12 + 6 tests), `plugin/…/ornament/**` (26), `plugin/…/config/ConfigQuarantine.java`, `plugin/…/ui/UiSounds.java`
- Create : `domum/core/…/dev/hydomum/core/DomumConfig.java` + test, `domum/plugin/…/dev/hydomum/plugin/HyDomumConfig.java`, `…/DomumIds.java`, `…/api/HyDomumSystems.java`
- Modify : `ColonyConfig.java`, `ColonyConfigTest.java`, `FreeWorkOrderTest.java`, `HyColonySection.java`, `HyColonyPlugin.java`, `IdMap.java`, `BlockUseProtectionSystem.java`, les utilisateurs de `UiSounds` et `ConfigQuarantine`

**Interfaces :**
- Produit :
  - `dev.hydomum.core.DomumConfig(double cutterCraftSeconds)` : borné à [0, 10], `DomumConfig.defaults()` = 0.5 ;
  - `dev.hydomum.plugin.api.HyDomumSystems.cutterUse()` → `Class<? extends EntityEventSystem<EntityStore, UseBlockEvent.Pre>>` (la classe du système d'utilisation de l'établi) ;
  - `dev.hyblockui.api.ConfigQuarantine.moveAsideIfUnreadable(String modName, Path file, Codec<?> codec)` ;
  - `dev.hyblockui.api.UiSounds.play(PlayerRef, Optional<String>)` (inchangé).

- [ ] **Étape 1 : le test de la config de HyDomum, d'abord**

`domum/core/src/test/java/dev/hydomum/core/DomumConfigTest.java` :

```java
package dev.hydomum.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DomumConfigTest {
    @Test
    void cutterCraftSecondsIsClampedBetweenZeroAndTenAndDefaultsToHalfASecond() {
        assertEquals(0.0, new DomumConfig(-1).cutterCraftSeconds());
        assertEquals(10.0, new DomumConfig(99).cutterCraftSeconds());
        assertEquals(0.5, DomumConfig.defaults().cutterCraftSeconds());
    }
}
```

Run : `./gradlew :domum-core:test --tests '*DomumConfigTest*'` → ÉCHEC de compilation (`DomumConfig` n'existe pas).

- [ ] **Étape 2 : `DomumConfig`**

`domum/core/src/main/java/dev/hydomum/core/DomumConfig.java` :

```java
package dev.hydomum.core;

/**
 * HyDomum's settings, clamped as HyColony's ColonyConfig clamps its own.
 *
 * @param cutterCraftSeconds how long one craft at the architect's cutter takes, in seconds; 0 crafts at once (MC DO
 *     crafts at once; Deviation from MC: Hytale's benches take time, CraftingManager.queueCraft)
 */
public record DomumConfig(double cutterCraftSeconds) {
    private static final double MAX_CUTTER_CRAFT_SECONDS = 10;
    private static final double DEFAULT_CUTTER_CRAFT_SECONDS = 0.5;

    public DomumConfig {
        cutterCraftSeconds = Math.clamp(cutterCraftSeconds, 0, MAX_CUTTER_CRAFT_SECONDS);
    }

    /** The defaults: half a second per craft. */
    public static DomumConfig defaults() {
        return new DomumConfig(DEFAULT_CUTTER_CRAFT_SECONDS);
    }
}
```

Run : `./gradlew :domum-core:test --tests '*DomumConfigTest*'` → PASS.

- [ ] **Étape 3 : HyColony perd `cutterCraftSeconds`**

- `ColonyConfig.HyColony` : retirer le composant `cutterCraftSeconds`, son `@param`, son bornage et sa valeur dans `defaults()` (lire le record en entier avant de couper).
- `ColonyConfigTest` : retirer les trois assertions sur `cutterCraftSeconds` (l. 17-19) ; `FreeWorkOrderTest` l. 62 : retirer l'argument.
- `HyColonySection` : retirer la clé `CutterCraftSeconds` (append + champ + argument de `toCore`).

Run : `./gradlew :core:test` → PASS (mêmes tests, moins les trois assertions déplacées dans `DomumConfigTest`).

- [ ] **Étape 4 : déplacer le cœur**

```bash
C=core/src/main/java/dev/hycolony/core/ornament; T=core/src/test/java/dev/hycolony/core/ornament
DC=domum/core/src/main/java/dev/hydomum; DT=domum/core/src/test/java/dev/hydomum
mkdir -p $DC/api $DC/core/cutter $DT/core/cutter
for f in VariantKey OrnamentShape MaterialTags; do git mv $C/$f.java $DC/api/$f.java; done
for f in SavedVariants ShapeCatalog VariantRequests; do git mv $C/$f.java $DC/core/$f.java; done
for f in $C/cutter/*.java; do git mv $f $DC/core/cutter/; done
for f in SavedVariantsTest ShapeCatalogTest VariantRequestsTest; do git mv $T/$f.java $DT/core/$f.java; done
for f in $T/cutter/*.java; do git mv $f $DT/core/cutter/; done
sed -i 's/^package dev\.hycolony\.core\.ornament;/package dev.hydomum.core;/; s/^package dev\.hycolony\.core\.ornament\.cutter;/package dev.hydomum.core.cutter;/' $DC/core/*.java $DC/core/cutter/*.java $DT/core/*.java $DT/core/cutter/*.java
sed -i 's/^package dev\.hycolony\.core\.ornament;/package dev.hydomum.api;/' $DC/api/*.java
```

Ajouter `domum/core/src/test/java/dev/hydomum/ArchitectureTest.java`, tel qu'écrit à la tâche 1, étape 1 (reporté ici).

Puis les imports, dans le cœur et le plugin (encore dans HyColony à ce stade) :

```bash
grep -rl "dev\.hycolony\.core\.ornament" domum core plugin | xargs sed -i \
  -e 's/dev\.hycolony\.core\.ornament\.cutter\./dev.hydomum.core.cutter./g' \
  -e 's/dev\.hycolony\.core\.ornament\.\(VariantKey\|OrnamentShape\|MaterialTags\)\b/dev.hydomum.api.\1/g' \
  -e 's/dev\.hycolony\.core\.ornament\./dev.hydomum.core./g'
```

Les classes qui partageaient le paquet `ornament` et se voyaient sans import (par exemple `ShapeCatalog` et `OrnamentShape`) ont maintenant besoin d'un import : la compilation les signale ; les ajouter. Un membre package-private qu'une classe d'un autre paquet utilise : le rendre `public` s'il est sur un type de `dev.hydomum.api` (et le documenter), sinon déplacer plutôt le type appelant ; noter chaque cas dans le compte rendu. `ArchitectureTest.apiDoesNotDependOnInternals` doit passer : si un type de l'API a besoin d'un type interne, c'est ce dernier qui devient API, pas l'inverse.

- [ ] **Étape 5 : déplacer le plugin, et les deux outils communs dans HyBlockUI**

```bash
P=plugin/src/main/java/dev/hycolony/plugin; DP=domum/plugin/src/main/java/dev/hydomum/plugin
for d in api cutter debug persistence registry runtime; do mkdir -p $DP/$d; for f in $P/ornament/$d/*.java; do git mv $f $DP/$d/; done; done
git mv $P/ornament/Ornaments.java $DP/Ornaments.java
git mv $P/config/ConfigQuarantine.java blockui/src/main/java/dev/hyblockui/api/ConfigQuarantine.java
git mv $P/ui/UiSounds.java blockui/src/main/java/dev/hyblockui/api/UiSounds.java
sed -i 's/^package dev\.hycolony\.plugin\.ornament;/package dev.hydomum.plugin;/; s/^package dev\.hycolony\.plugin\.ornament\.\([a-z]*\);/package dev.hydomum.plugin.\1;/' $DP/*.java $DP/*/*.java
sed -i 's/^package dev\.hycolony\.plugin\.config;/package dev.hyblockui.api;/; s/^package dev\.hycolony\.plugin\.ui;/package dev.hyblockui.api;/' blockui/src/main/java/dev/hyblockui/api/{ConfigQuarantine,UiSounds}.java
grep -rl "dev\.hycolony\.plugin\.ornament\|plugin\.config\.ConfigQuarantine\|plugin\.ui\.UiSounds" plugin domum blockui | xargs sed -i \
  -e 's/dev\.hycolony\.plugin\.ornament\./dev.hydomum.plugin./g' \
  -e 's/dev\.hycolony\.plugin\.config\.ConfigQuarantine/dev.hyblockui.api.ConfigQuarantine/g' \
  -e 's/dev\.hycolony\.plugin\.ui\.UiSounds/dev.hyblockui.api.UiSounds/g'
```

Les fichiers de `plugin/…/config/` et `plugin/…/ui/` qui utilisaient `ConfigQuarantine` ou `UiSounds` sans import (même paquet) reçoivent l'import `dev.hyblockui.api.…` : `grep -rln "ConfigQuarantine\|UiSounds" plugin/src/main/java`.

`ConfigQuarantine` : ajouter un premier paramètre `String modName` à `moveAsideIfUnreadable` et remplacer `"HyColony: "` par `"%s: "` avec `modName` dans ses deux messages ; `HyColonyPlugin` l'appelle avec `"HyColony"` (le message attendu par `TESTING.md` point 59 ne change pas). Ses préfixes et `UiSounds` (`"HyColony UI sound %s failed"` → `"UI sound %s failed"`) ne citent plus HyColony.

- [ ] **Étape 6 : ce que le code déplacé prenait encore à HyColony**

`grep -rn "dev\.hycolony" domum/` doit finir vide. Les cas connus :
- `HytaleNotifier.toMessage(Msg.of(key, params…))` (4 fichiers) → `Texts.translated(key, List.of(params…))` (`dev.hyblockui.api.Texts`) ; `Msg` disparaît de ces fichiers.
- `IdMap` (dans `Ornaments`) → `DomumIds` (étape 7).
- `BlockUseProtectionSystem` (dans `CutterSystem`) → la dépendance est retournée (étape 8).

- [ ] **Étape 7 : `HyDomumPlugin`, sa config et ses identifiants**

`domum/plugin/src/main/java/dev/hydomum/plugin/DomumIds.java` : lit `/hydomum/id-map.json` du classpath du mod avec Gson (`{"sounds": {clé: id}, "ornamentTags": {tag: [ids]}}`, comme `IdMap` pour ces deux champs) ; méthodes `Optional<String> sound(String key)` et `Map<String, List<String>> ornamentTags()` ; fichier absent ou illisible → vide, journalisé une fois en WARNING (CLAUDE.md § 4). Record ou classe finale, Javadoc courte, sous 80 lignes. S'inspirer de `IdMap.sound`/`IdMap.ornamentTags` (lire `plugin/…/IdMap.java` avant d'écrire).

`domum/plugin/src/main/java/dev/hydomum/plugin/HyDomumConfig.java` : le codec de `config.json` de HyDomum, une section `HyDomum` avec `CutterCraftSeconds` (`Codec.DOUBLE`, défaut `DomumConfig.defaults()`), et `DomumConfig toCore()`. Copier la forme de `plugin/…/config/HyColonySection.java` + `HyColonyConfig.java` (la section et son conteneur), sans rien d'autre.

`Ornaments` est fondu dans `HyDomumPlugin` (puis `Ornaments.java` supprimé), qui devient :

```java
/**
 * HyDomum's entry point (MC DO's DomumOrnamentum mod class): the architect's cutter, the /hydomum command, and once
 * assets are loaded the shape and material catalogs, then the saved variants registered again before any world loads a
 * chunk holding one.
 */
public final class HyDomumPlugin extends JavaPlugin {
    private final Config<HyDomumConfig> config;

    public HyDomumPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // Before withConfig: preLoad decodes the file and a malformed one would abort the whole server start.
        ConfigQuarantine.moveAsideIfUnreadable("HyDomum", getDataDirectory().resolve("config.json"), HyDomumConfig.CODEC);
        this.config = withConfig("config", HyDomumConfig.CODEC);
    }

    @Override
    protected void setup() {
        …
    }
}
```

`setup()` reprend le corps de `Ornaments.register` avec ces changements :
- sauvegarde de la config comme `HyColonyPlugin.setup` (l. 47-50) ;
- `CutterSystem` enregistré **en premier et sans condition** : HyColony déclare sa protection avant lui, et une `SystemDependency` vers une classe non enregistrée lève une exception (`ComponentRegistry.java:657-659`) ; le système ne fait rien sans catalogue ;
- le reste (commande, chargement au `LoadAssetEvent`) seulement si `ids.ornamentTags()` n'est pas vide, sinon un WARNING « HyDomum: no material tags, blocks disabled » ;
- `cutterCraftSeconds` vient de `config.get().toCore().cutterCraftSeconds()` ;
- les chemins de données : `Constants.UNIVERSE_PATH.resolve("hydomum")`, fichiers `variants.json` et dossier `assets` ;
- les ressources : `/hydomum/shapes.json` (et `IconMap` : `/hydomum/icons/`) ;
- les messages de journal : `"hyornament: "` → `"hydomum: "`, `"HyColony: could not load Domum Ornamentum variants"` → `"HyDomum: could not load the variants"`.

`HyColonyPlugin` : retirer l'import et l'appel `Ornaments.register(…)`. `IdMap` : retirer `ornamentTags` (composant du record de données, accesseur, Javadoc).

- [ ] **Étape 8 : la protection avant l'établi, par l'API**

`domum/plugin/src/main/java/dev/hydomum/plugin/api/HyDomumSystems.java` :

```java
package dev.hydomum.plugin.api;

import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hydomum.plugin.cutter.CutterSystem;

/**
 * HyDomum's systems that another mod may order its own against (SystemDependency names a system by its class): a
 * colony's protection runs before the cutter opens. HyDomum registers them first in its setup.
 */
public final class HyDomumSystems {
    private HyDomumSystems() {}

    /** The system that opens the architect's cutter on use. */
    public static Class<? extends EntityEventSystem<EntityStore, UseBlockEvent.Pre>> cutterUse() {
        return CutterSystem.class;
    }
}
```

(Vérifier les imports exacts de `EntityEventSystem` et `UseBlockEvent` dans `CutterSystem.java` et les recopier.) `CutterSystem` doit être `public` pour cela ; sa dépendance `AFTER BlockUseProtectionSystem` est retirée (`getDependencies` renvoie un ensemble vide ou disparaît). `BlockUseProtectionSystem` gagne :

```java
    private static final Set<Dependency<EntityStore>> DEPENDENCIES =
            Set.of(new SystemDependency<>(Order.BEFORE, HyDomumSystems.cutterUse()));

    /** Runs before HyDomum's cutter, which then sees the use cancelled for a stranger (was CutterSystem AFTER this). */
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
```

(Recopier les imports `Dependency`, `SystemDependency`, `Order`, `Set` depuis `CutterSystem`.) Si `BlockUseProtectionSystem` a déjà un `getDependencies`, fusionner.

Run : `./gradlew spotlessApply && ./gradlew build --console=plain` → vert ; `grep -rn "ornament" core/src plugin/src/main/java` ne trouve plus que des mots dans des commentaires sans rapport (sinon, corriger) ; `unzip -l plugin/build/libs/HyColony-0.1.0.jar | grep -c "hydomum\|ornament"` = 0 ; `unzip -l domum/plugin/build/libs/HyDomum-0.1.0.jar | grep -c "dev/hydomum/core"` > 0 (le cœur est embarqué).

- [ ] **Étape 9 : essai en jeu intermédiaire (sans les blocs)**

Le pack n'a pas encore déménagé (tâche 3) : HyDomum démarre sans tags. Demander à l'utilisateur de lancer `./gradlew runAllMods` et de vérifier :
1. journal : `hyblockui`, `hydomum`, `hycolony` chargés dans cet ordre, WARNING « HyDomum: no material tags », « HyColony runtime ready », aucun SEVERE ;
2. `run/mods/HyColony_hydomum/config.json` créé avec `HyDomum.CutterCraftSeconds: 0.5` ;
3. les fenêtres de HyColony comme avant (citoyen, hutte) ;
4. un son d'interface de HyColony (ouverture d'une fenêtre) toujours joué (`UiSounds` déplacé) ;
5. (reporté à la tâche 3, quand l'établi existe à nouveau) ;
6. l'ancienne config de HyColony, qui contient encore `HyColony.CutterCraftSeconds` et `SubPlugins.DomumOrnamentum` : pas de `config.json.broken-*`, pas de SEVERE de quarantaine.

- [ ] **Étape 10 : relecture et commit**

`hycolony-reviewer` (et `mc-fidelity-checker` sur `domum/core` : rien ne doit avoir changé des règles portées) : « déplacement sans changement de comportement du code DO dans HyDomum ; config ; DomumIds ; HyDomumSystems et ordre de la protection ; ConfigQuarantine et UiSounds dans HyBlockUI ». Corriger, faire relire.

```bash
git status --short
git add <chaque chemin déplacé, créé ou modifié par cette tâche, listé depuis git status>
git diff --cached --stat
git commit -m "$(cat <<'EOF'
refactor(domum): Domum Ornamentum's core and plugin move to the HyDomum mod

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Tâche 3 : le pack, le générateur et les renommages (session déverrouillée)

**Files :**
- Move : `plugin/src/subplugins/DomumOrnamentum/{Common,Server}/**` → `domum/plugin/src/main/resources/`, `plugin/src/main/resources/hycolony/ornament/` → `domum/plugin/src/main/resources/hydomum/`, `Cutter.ui`, `CutterShapeButton.ui`, `CutterTabButton.ui` → `…/Pages/HyDomum/`
- Delete : `plugin/src/subplugins/DomumOrnamentum/{subplugin.json,hycolony/}`
- Create : `build-logic/src/main/kotlin/CheckPackAssets.kt`, `domum/plugin/src/main/resources/Server/Languages/{en-US,fr-FR}/hydomum.lang`
- Modify : `tools/domum/**`, `plugin/build.gradle.kts` (sous-packs), `domum/plugin/build.gradle.kts`, le code de `domum/` (clés, chemins, identifiants), `hycolony.lang`

- [ ] **Étape 1 : déplacer le pack et les ressources**

```bash
S=plugin/src/subplugins/DomumOrnamentum; R=domum/plugin/src/main/resources
mkdir -p $R
git mv $S/Common $R/Common
git mv $S/Server $R/Server
git rm -q $S/subplugin.json $S/hycolony/id-map.json
git mv plugin/src/main/resources/hycolony/ornament $R/hydomum
mkdir -p $R/Common/UI/Custom/Pages/HyDomum
for f in Cutter CutterShapeButton CutterTabButton; do git mv plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/$f.ui $R/Common/UI/Custom/Pages/HyDomum/$f.ui; done
```

Les `.ui` de l'établi gardent `$C = "../../Common.ui"` et `../HyBlockUI/Native/…` (même profondeur).

- [ ] **Étape 2 : les traductions écrites à la main**

Déplacer les 45 lignes `ornament.*` des deux `hycolony.lang` vers `$R/Server/Languages/{en-US,fr-FR}/hydomum.lang` (mêmes lignes, même ordre, fin de ligne LF, dernière ligne terminée) et les retirer de `hycolony.lang` :

```bash
for l in en-US fr-FR; do
  grep '^ornament\.' plugin/src/main/resources/Server/Languages/$l/hycolony.lang > $R/Server/Languages/$l/hydomum.lang
  sed -i '/^ornament\./d' plugin/src/main/resources/Server/Languages/$l/hycolony.lang
done
wc -l $R/Server/Languages/*/hydomum.lang
```

Attendu : 45 lignes chacun. Le fichier généré du sous-pack, `$R/Server/Languages/<l>/hycolony.lang`, sera remplacé par `hydomum_blocks.lang` à l'étape 5 : le supprimer maintenant (`git rm`).

Puis les clés dans le code et les `.ui` de HyDomum : `grep -rl "hycolony\.ornament\." domum | xargs sed -i 's/hycolony\.ornament\./hydomum.ornament./g'`, et `grep -rn "hycolony\." domum/*/src` ne doit plus rien trouver.

- [ ] **Étape 3 : le générateur**

Dans `tools/domum` (Python), appliquer ces remplacements, puis vérifier `grep -rn -i "hycolony\|subplugins\|DomumOrnamentum" tools/domum --include=*.py` (il ne doit rester que des mentions historiques dans des docstrings, reformulées) :

| Avant | Après |
|---|---|
| `PREFIX = "HyColony_DO_"` (`names.py`, `validate.py`) et tout `"HyColony_DO_…"` littéral (`check_*.py`) | `"HyDomum_"`, `"HyDomum_…"` |
| `Blocks/HyColony/DO/` | `Blocks/HyDomum/` |
| `Icons/ItemsGenerated/HyColony/DO` (et `…/HyColony`) | `Icons/ItemsGenerated/HyDomum` |
| `Server/Item/Items/HyColony/DO`, `Hitboxes/HyColony/DO` (et `Hitboxes/HyColony`) | `Server/Item/Items/HyDomum`, `Hitboxes/HyDomum` |
| icônes d'onglet `DomumOrnamentum*.png`, catégorie `DomumOrnamentum.json` | `HyDomum*.png`, `HyDomum.json` |
| préfixe de traduction `"hycolony."` (`blocks/common.py`, `blocks/cutter.py`, `tabs.py`, `validate.py`, `check_pack.py`, `check_icons.py`) | `"hydomum_blocks."` |
| fichier `hycolony.lang` (`generate.py`) | `hydomum_blocks.lang` |
| `PACK = ROOT / "plugin" / "src" / "subplugins" / "DomumOrnamentum"`, `RESOURCES = ROOT / "plugin" / "src" / "main" / "resources"` | `PACK = RESOURCES = ROOT / "domum" / "plugin" / "src" / "main" / "resources"` |
| `resources / "hycolony" / "ornament"` (`generate.py`, `manifest.py`, `check_pack.py`) et l'`IconMap` | `resources / "hydomum"` |
| `pack / "hycolony" / "id-map.json"` (`generate.py`, `check_pack.py`) | `pack / "hydomum" / "id-map.json"` |
| `CUTTER` `HyColony_DO_ArchitectsCutter` (`blocks/cutter.py`) | `HyDomum_ArchitectsCutter` |

Et dans `generate.py`, `run()` n'efface plus `Common` et `Server` en entier (écart 2) :

```python
# What the generator owns inside the mod's resources; the rest (the cutter's .ui, hydomum.lang, manifest.json) is
# written by hand and must survive a regeneration.
GENERATED = ("Common/Blocks/HyDomum", "Common/Icons/ItemsGenerated/HyDomum", "Server/Item/Block/Hitboxes/HyDomum",
             "Server/Item/CustomConnectedBlockTemplates", "Server/Item/Items/HyDomum", "hydomum")
GENERATED_FILES = ("Common/Icons/ItemCategories/HyDomum*.png", "Server/Item/Category/CreativeLibrary/HyDomum.json",
                   "Server/Languages/*/hydomum_blocks.lang")


def clear(pack):
    """Removes what a previous run generated in pack, and nothing else."""
    for generated in GENERATED:
        shutil.rmtree(pack / generated, ignore_errors=True)
    for pattern in GENERATED_FILES:
        for path in pack.glob(pattern):
            path.unlink()
```

`run()` appelle `clear(pack)` à la place des lignes 36-38. Ajuster la docstring du module (sortie : `domum/plugin/src/main/resources`).

Les identifiants Java qui suivent le générateur : `CutterSystem.CUTTER` → `"HyDomum_ArchitectsCutter"`, `VariantAssets.PAIRS` → `"Blocks/HyDomum/Pairs/"`, le préfixe des variantes s'il est écrit en dur (`grep -rn "HyColony_DO\|HyColony/DO\|ItemsGenerated/HyColony" domum/plugin/src/main/java` doit finir vide).

- [ ] **Étape 4 : régénérer**

```bash
python tools/domum/generate.py
python tools/domum/check.py
```

Attendu : « N templates, M shapes » avec les mêmes N et M qu'avant le renommage (les lire sur une génération faite **avant** l'étape 3 sur l'ancien code, ou dans le journal de la dernière génération commitée) ; `check.py` passe. Les fichiers générés portent les nouveaux noms ; `git status --short domum/plugin/src/main/resources | head` montre les renommages.

- [ ] **Étape 5 : le générateur ne touche pas au travail manuel**

```bash
git status --short > /tmp/before.txt
python tools/domum/generate.py
git status --short > /tmp/after.txt
diff /tmp/before.txt /tmp/after.txt && echo "stable"
ls domum/plugin/src/main/resources/Common/UI/Custom/Pages/HyDomum domum/plugin/src/main/resources/Server/Languages/*/hydomum.lang domum/plugin/src/main/resources/manifest.json
```

Attendu : « stable » (une seconde génération ne change rien) ; les `.ui` de l'établi, `hydomum.lang` et `manifest.json` sont toujours là.

- [ ] **Étape 6 : `/hydomum`**

`OrnamentCommand` : nom `"hyornament"` → `"hydomum"` ; ses messages de journal suivent. `grep -rn "hyornament" domum plugin tools docs/TESTING.md` : il ne reste que `docs/TESTING.md`, réécrit à la tâche 4.

- [ ] **Étape 7 : le contrôle des assets, dans `build-logic`**

`build-logic/src/main/kotlin/CheckPackAssets.kt` : une tâche `abstract class CheckPackAssets : DefaultTask()` avec `@get:InputFiles abstract val packs: ConfigurableFileCollection`, `@get:Input abstract val namespace: Property<String>`, `@get:OutputFile abstract val stamp: RegularFileProperty` et une `@TaskAction` qui reprend **mot pour mot** la logique du `checkSubpluginAssets` de `plugin/build.gradle.kts` (règles `Icon`/`Model`/`CustomModel`/`Texture`/faces, racines de `CommonAssetValidator`, puis « `value.contains("/<namespace>/")` doit exister sous `Common/` »), avec `namespace` à la place du `"/HyColony/"` écrit en dur. Javadoc : « Hytale shuts the whole server down when a pack holds an invalid asset (plugin-b-api.md § 23) ».

`plugin/build.gradle.kts` : `checkSubpluginAssets` devient

```kotlin
val checkSubpluginAssets by tasks.registering(CheckPackAssets::class) {
    packs.from(subpluginNames.map { subpluginsSrc.dir(it) })
    namespace.set("HyColony")
    stamp.set(layout.buildDirectory.file("tmp/checkSubpluginAssets.stamp"))
}
```

`domum/plugin/build.gradle.kts` :

```kotlin
// Hytale shuts the whole server down when a pack holds an invalid asset: check HyDomum's own before it ships.
val checkPackAssets by tasks.registering(CheckPackAssets::class) {
    packs.from(layout.projectDirectory.dir("src/main/resources"))
    namespace.set("HyDomum")
    stamp.set(layout.buildDirectory.file("tmp/checkPackAssets.stamp"))
}
tasks.named("check") { dependsOn(checkPackAssets) }
```

Vérifier que le contrôle mord : remplacer temporairement une `Icon` d'un item de HyDomum par `"Icons/Nope/x.png"` → `./gradlew :domum-plugin:checkPackAssets` échoue ; remettre (`git checkout -- <fichier>`). Même essai sur un sous-pack de HyColony.

- [ ] **Étape 8 : build et jars**

```bash
./gradlew spotlessApply && ./gradlew build --console=plain
unzip -l domum/plugin/build/libs/HyDomum-0.1.0.jar | awk '{print $4}' | grep -v '/$' | grep -vc '\.class$'
unzip -l plugin/build/libs/HyColony-0.1.0.jar | grep -ciE "HyDomum|HyColony_DO|ornament|Cutter"
ls plugin/src/subplugins
```

Attendu : build vert ; le jar de HyDomum contient les ressources du pack (des centaines de fichiers), celui de HyColony aucune (0) ; `plugin/src/subplugins` ne liste plus `DomumOrnamentum`.

- [ ] **Étape 9 : relecture**

`hycolony-reviewer` : « pack, générateur et renommages ; CheckPackAssets fidèle à checkSubpluginAssets ; le générateur n'efface que ce qu'il génère ; plus aucun HyColony_DO/hyornament/hycolony.ornament ». Corriger, faire relire.

- [ ] **Étape 10 : essai en jeu, par l'utilisateur**

`./gradlew runAllMods`, dans un monde de test (les blocs `HyColony_DO_*` déjà posés deviennent inconnus : attendu, sans migration). Vérifier :
1. journal : `hydomum` chargé, plus de WARNING « no material tags », aucun SEVERE ni `missing asset` ;
2. l'onglet créatif HyDomum, ses icônes et ses noms de blocs traduits (pas `hydomum_blocks.item…`) ;
3. `/hydomum` : aide traduite, `create`/`give` d'une variante ;
4. l'établi de l'architecte : ouverture (son), onglets, formes, aperçu, cases vertes/rouges, fabrication avec la barre, textes traduits (pas `hydomum.ornament…`) ;
5. **protection** : un joueur étranger à une colonie utilise l'établi posé dans cette colonie → refusé comme avant ; un membre → ouvert ;
6. relancer : les variantes créées sont toujours là (`run/universe/hydomum/variants.json`).

- [ ] **Étape 11 : commit**

```bash
git status --short
git add <chaque chemin de la tâche, depuis git status, dont build-logic/src/main/kotlin/CheckPackAssets.kt et tools/domum>
git diff --cached --stat
git commit -m "$(cat <<'EOF'
refactor(domum): the DO pack becomes HyDomum's, with HyDomum names throughout

The DomumOrnamentum sub-pack is gone: blocks are HyDomum_*, the command is
/hydomum, variants live in universe/hydomum. The generator writes into the
mod's resources and clears only what it generates.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Tâche 4 : règles, agents, skills et docs (session déverrouillée), puis relecture de toute la branche

**Files :** `CLAUDE.md`, `AGENTS.md`, `.claude/agents/{hycolony-implementer,hycolony-reviewer,hycolony-researcher}.md`, `.claude/skills/{add-lang-key,port-mc,hytale-api}/SKILL.md`, `docs/TESTING.md`, `docs/research/{plugin-b-api,domum-ornamentum,config-inventory}.md`, `docs/native-ui-textures.md`, la spec, les specs/plans DO.

- [ ] **Étape 1 : CLAUDE.md**

- § 1 « Modules et dépendances » : remplacer les deux premières puces (`core/`, `plugin/`) par la description des cinq projets et des trois mods :
  - « Trois mods, dépendances dans un seul sens : HyBlockUI (`blockui/`, bibliothèque d'interface) ← HyDomum (`domum/core`, `domum/plugin`) ← HyColony (`core/`, `plugin/`). Chaque mod a un cœur Java pur (`core/`, `domum/core/`), sans import `com.hypixel` **[build : ArchitectureTest]**, et un plugin d'adaptateurs Hytale avec son pack d'assets ; HyBlockUI n'a pas de cœur. »
  - « Un mod ne voit d'un autre que ses paquets `api` (`dev.hyblockui.api` ; `dev.hydomum.api`, `dev.hydomum.plugin.api`) **[build : `checkModApis`]**, en `compileOnly` : il n'embarque jamais un autre mod. »
  - « Tout projet applique `hy.java-core` ou `hy.hytale-mod` (`build-logic/`) **[build]**. »
  - « Le dev (`runAllMods`) ne vérifie ni l'isolation des classes ni une dépendance absente : ces cas se vérifient avec les jars de production dans un `mods/` (`docs/research/plugin-b-api.md` § 28). »
  Les puces suivantes (ports, `Fake*`, paquets par fonctionnalité…) restent, en remplaçant « le cœur »/« le plugin » par « chaque cœur »/« chaque plugin » là où c'est nécessaire.
- § 7 : « `plugin/src/main/resources/Server/Languages/*/hycolony.lang` » devient « le `.lang` du mod qui affiche le texte (`hycolony.lang`, `hydomum.lang`, `hyblockui.lang`), en en-US et fr-FR » ; « `hycolony/id-map.json` » devient « l'id-map du mod (`hycolony/id-map.json`, `hydomum/id-map.json`) ».

- [ ] **Étape 2 : AGENTS.md, agents, skills**

- `AGENTS.md` : même résumé des modules que CLAUDE.md § 1 (sans dupliquer les règles : renvoyer à CLAUDE.md).
- `hycolony-reviewer.md` : « every behaviour change in `core/` » → « in a mod's core (`core/`, `domum/core/`) » ; « no `com.hypixel` import in `core/`; no game rule in `plugin/` » → idem pour les deux cœurs et les trois plugins, plus « a mod imports another only through its api packages » ; « `hycolony.lang` » → « the mod's `.lang` ».
- `hycolony-implementer.md` et `hycolony-researcher.md` : remplacer les chemins `core/`/`plugin/` qui désignent « le code » par la liste des projets là où c'est une règle (lire chaque fichier en entier).
- `add-lang-key/SKILL.md` : les fichiers dépendent du mod (`plugin/…/hycolony.lang`, `domum/plugin/…/hydomum.lang`, `blockui/…/hyblockui.lang`) ; la commande de comparaison des clés prend le mod en argument ; les noms de blocs générés (`hydomum_blocks.lang`) ne s'éditent pas à la main (le générateur les écrit).
- `port-mc/SKILL.md` : « TDD in `core/` » → « TDD in the mod's core (`core/` for HyColony, `domum/core/` for HyDomum) ».
- `hytale-api/SKILL.md` : inchangé pour `build/vineflower` ; ajouter que les découvertes multi-mods vont dans `plugin-b-api.md` § 28.

- [ ] **Étape 3 : TESTING.md**

- Section DO (actuellement « Port des blocs d'architecte… », points 133 à 162) réécrite pour HyDomum : plus de `SubPlugins` ni de « pack activé/coupé » (le point 146 « pack coupé avec des variantes sauvegardées » disparaît) ; `/hyornament` → `/hydomum` ; `universe/hycolony/ornament-*` → `universe/hydomum/` ; le journal attendu est « HyColony:hydomum » activé ; `HyDomum.CutterCraftSeconds` dans `run/mods/HyColony_hydomum/config.json` (dev : `domum/plugin/src/main/resources/config.json`) ; noms `HyDomum_*`.
- Nouvelle section « Mods multiples » : en dev, les trois mods chargés sans SEVERE ; en production (jars dans `mods/`), les trois ensemble, puis HyBlockUI + HyDomum seuls (établi et `/hydomum`), puis HyColony sans HyDomum et HyDomum sans HyBlockUI : **le serveur ne démarre pas**, le journal nomme la dépendance manquante (§ 28.4) ; HyColony comme avant (fenêtres, onglet Inventaire, protection de l'établi).

- [ ] **Étape 4 : recherche et specs**

- `docs/research/plugin-b-api.md`, `domum-ornamentum.md`, `config-inventory.md`, `native-ui-textures.md` : chaque chemin, identifiant ou clé renommé (`grep -n "HyColony_DO\|hyornament\|plugin/ornament\|core/ornament\|CutterCraftSeconds\|DomumOrnamentum\|hycolony/ornament" docs/research/*.md docs/native-ui-textures.md`), sans toucher au contenu vérifié ; `config-inventory.md` : `CutterCraftSeconds` est dans la config de HyDomum, section `HyDomum`.
- La spec du split : ajouter une section « Écarts décidés au plan 4 » avec les six écarts de ce plan.
- Les specs et plans DO (`docs/superpowers/specs/*domum*`, `docs/superpowers/plans/*domum*`) : une ligne en tête : « Depuis le plan 4 de la séparation (spec `2026-09-28-hycolony-split-hydomum-hyblockui-design.md`), ce code vit dans le mod HyDomum : chemins et noms renommés (`HyDomum_*`, `/hydomum`, `domum/`). »

- [ ] **Étape 5 : relecture et commit**

`hycolony-reviewer` sur ces fichiers ; corriger, faire relire.

```bash
git add CLAUDE.md AGENTS.md .claude/agents .claude/skills docs
git diff --cached --stat
git commit -m "$(cat <<'EOF'
docs: rules, agents, skills and tests describe the three mods

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

(`git add .claude/agents` et `.claude/skills` sont des dossiers explicites, pas un `-A` ; vérifier le `--stat`.)

- [ ] **Étape 6 : relecture de toute la branche**

`hycolony-reviewer` sur l'ensemble des plans 1 à 4 (`git log --oneline` depuis `5a2f204`) : cohérence finale avec la spec, rien d'oublié du tableau « Ce qui va où », `./gradlew build` vert, `node .claude/hooks/test/run.js` vert, `python tools/domum/check.py` vert. Puis donner le feu vert à l'utilisateur pour la section « Mods multiples » de `TESTING.md` en production.
