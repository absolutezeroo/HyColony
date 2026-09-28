# Séparation en trois mods, plan 1 : garde-fous et essai

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** empêcher un agent de lancer `runAllMods`, puis vérifier, avec trois mods jetables, tout ce que la spec ne peut pas savoir sans lancer un serveur, avant d'écrire le plan 2.

**Architecture :**
- La tâche 1 étend le contrôle `checkServer` de `guard.js` à `runAllMods`, écrit d'abord dans son banc de test.
- Les tâches 2 à 4 construisent un **build Gradle autonome**, `spike/`, sur une branche d'essai jamais fusionnée : trois mods minimaux (`spikeui` ← `spikedomum` ← `spikecolony`). Le build réel (`core`, `plugin`, `build.gradle.kts` racine protégé) n'est pas touché.
- L'utilisateur lance les serveurs : en dev (`runAllMods`) puis en production (les trois jars dans un `mods/`). La tâche 5 écrit les résultats dans `plugin-b-api.md` et la décision sur la config de dev dans la spec, sur `sp0-foundations`.

**Tech Stack :** Node (guard.js et son banc), Gradle 9.5.1, plugin AzureDoom `hytale-gradle-plugin` 1.0.51 (`com.azuredoom.hytale-workspace`, `com.azuredoom.hytale-tools`), Java 25, serveur Hytale 0.6.8.

**Spec :** `docs/superpowers/specs/2026-09-28-hycolony-split-hydomum-hyblockui-design.md` (§ « Garde-fous », § « Config et données en dev », § « Plans » point 1).

## Contraintes globales

- On ne lance **jamais** le serveur Hytale, ni `runServer`, ni `runAllMods`, ni `HytaleServer.jar` : l'utilisateur les lance (CLAUDE.md § 9.4). `stageAllModAssets` reste permis : il ne lance rien.
- La tâche 1 modifie des garde-fous : **accord explicite de l'utilisateur** et session lancée avec `HYCOLONY_GUARDRAILS_UNLOCKED=1` (CLAUDE.md § 10). Sans ces deux conditions, on s'arrête et on le demande.
- Groupe commun `HyColony` ; identifiants de l'essai `HyColony:spikeui`, `HyColony:spikedomum`, `HyColony:spikecolony` ; version 0.1.0 partout ; dépendance entre mods à la version exacte `0.1.0`.
- Un mod n'embarque **jamais** un autre mod : `compileOnly` entre mods, `bundleAssetEditorRuntime = false`.
- Plugin Gradle épinglé en **1.0.51**, pas `1.+`.
- Commits : `type(scope): description` en anglais, `git add` de chemins explicites, jamais `config.json`/`config.json.bak`, fin de message `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- Le code de l'essai ne vit que sur la branche `spike/multi-mod`. Il n'est **jamais** fusionné ni copié tel quel dans le vrai code.
- Docs en français ; code, commentaires et commits en anglais.

## Review Focus

- **Une abréviation de `runAllMods`** (`rAM`, `runAll`, `runallmods`, `:colony:runAllMods`) doit être refusée, et `stageAllModAssets` et ses abréviations (`sAMA`) acceptées : tâche 1, étape 1.
- **`-x runAllMods`** (exclusion) et un texte qui cite `runAllMods` (`grep`) doivent passer : tâche 1, étape 1.
- **Un jar qui embarque la copie d'un autre mod** ou `com/azuredoom/hytale/asseteditor/**` : tâche 2, étape 7 (liste du contenu des jars).
- **La config d'un mod après un second lancement de dev** : tâche 3, étapes 4 et 5 (compteur `Counter`).
- **Un mod dont la dépendance manque** doit être refusé proprement, sans arrêter les autres ni le serveur : tâche 4, étape 4.

## Fichiers

Tâche 1 (sur `sp0-foundations`, session déverrouillée) :
- Modifier `.claude/hooks/guard.js:367-371` (`checkServer`).
- Modifier `.claude/hooks/test/commands.txt` (bloc « Server launch », l. 128-164).
- Modifier `CLAUDE.md:117`, `AGENTS.md:17`, `.claude/agents/hycolony-implementer.md:32` : nommer `runAllMods` à côté de `runServer`.

Tâches 2 à 4 (sur `spike/multi-mod`, jetable) :
- Créer `spike/settings.gradle.kts`, `spike/build.gradle.kts`, `spike/gradle.properties`.
- Créer `spike/ui/` : `build.gradle.kts`, `SpikeUiPlugin.java`, `api/SpikeUi.java`, `Box.ui`, `Panel.ui`, `Tex.png`, `spikeui.lang`.
- Créer `spike/domum/` : `build.gradle.kts`, `SpikeDomumPlugin.java`, `SpikeDomumConfig.java`, `api/SpikeDomum.java`.
- Créer `spike/colony/` : `build.gradle.kts`, `SpikeColonyPlugin.java`, `SpikeCommand.java`, `SpikePage.java`, `Page.ui`.
- Créer `spike/RESULTS.md` : les observations brutes, remplies au fil des tâches 3 et 4.

Tâche 5 (sur `sp0-foundations`) :
- Modifier `docs/research/plugin-b-api.md` : nouvelle section `## 27. Mods multiples`, avant `## Could not verify`.
- Modifier la spec, § « Config et données en dev » : la solution retenue.

`spike/run/` et `spike/*/build/` sont déjà ignorés (`.gitignore` : `run/`, `build/`). `spike/domum/src/main/resources/config.json` (tâche 3) ne l'est pas : il n'est **jamais** indexé.

---

### Tâche 1 : `guard.js` refuse `runAllMods`

**Préalable :** l'utilisateur a donné son accord explicite pour cette modification des garde-fous **et** la session tourne avec `HYCOLONY_GUARDRAILS_UNLOCKED=1`. Sinon : s'arrêter et le demander.

**Files :**
- Modify : `.claude/hooks/guard.js:367-371`
- Modify : `.claude/hooks/test/commands.txt` (après la ligne 164, `allow Bash ./gradlew build -x runServer`)
- Modify : `CLAUDE.md:117`, `AGENTS.md:17`, `.claude/agents/hycolony-implementer.md:32`

**Interfaces :**
- Consomme : `checkServer(task)`, appelée par `checkGradle(args)` pour chaque tâche Gradle (dernier segment après `:`), qui saute déjà la valeur de `-x`/`--exclude-task`.
- Produit : `checkServer` refuse toute tâche qui abrège `runServer` **ou** `runAllMods`, avec le même message.

- [ ] **Étape 1 : écrire les cas du banc**

Ajouter après la ligne `allow Bash ./gradlew build -x runServer` de `.claude/hooks/test/commands.txt` :

```
// runAllMods launches the workspace server (AzureDoom hytale-workspace): denied like runServer, abbreviations too.
deny Bash ./gradlew runAllMods
deny Bash ./gradlew :colony:runAllMods
deny Bash ./gradlew -p spike runAllMods
deny Bash ./gradlew rAM
deny Bash ./gradlew runAll
deny Bash ./gradlew runallmods
deny Bash gradlew.bat runAllMods
deny PowerShell .\gradlew runAllMods
deny PowerShell Start-Process .\gradlew.bat -ArgumentList 'runAllMods'
deny Bash ./gradlew stageAllModAssets && ./gradlew runAllMods
allow Bash ./gradlew stageAllModAssets
allow Bash ./gradlew -p spike stageAllModAssets
allow Bash ./gradlew sAMA
allow Bash ./gradlew build -x runAllMods
allow Bash grep -rn runAllMods docs/
```

- [ ] **Étape 2 : lancer le banc, vérifier l'échec**

Run : `node .claude/hooks/test/run.js`
Attendu : ÉCHEC sur les lignes `deny ... runAllMods`, `:colony:runAllMods`, `-p spike runAllMods`, `rAM`, `runAll`, `runallmods`, `gradlew.bat runAllMods`, `.\gradlew runAllMods`, `Start-Process … 'runAllMods'` (qui revérifie la commande lancée), `... && ./gradlew runAllMods` : acceptées au lieu d'être refusées. Les lignes `allow` passent.

- [ ] **Étape 3 : étendre `checkServer`**

Remplacer, dans `.claude/hooks/guard.js`, la fonction entière :

```js
function checkServer(task) {
    const humps = task.match(/[A-Z]?[^A-Z]*/g).filter(Boolean);
    const camel = new RegExp("^" + humps.map((h) => h.replace(/[^\w]/g, "\\$&") + "[a-z0-9]*").join(""), "i");
    if (task && camel.test("runServer")) deny("CLAUDE.md § 9.4: never launch the Hytale server; the user restarts it and tests in game.");
}
```

par :

```js
/** Gradle tasks that start a Hytale server: runServer (each mod) and runAllMods (the workspace, all mods at once). */
const SERVER_TASKS = ["runServer", "runAllMods"];

function checkServer(task) {
    const humps = task.match(/[A-Z]?[^A-Z]*/g).filter(Boolean);
    const camel = new RegExp("^" + humps.map((h) => h.replace(/[^\w]/g, "\\$&") + "[a-z0-9]*").join(""), "i");
    if (task && SERVER_TASKS.some((t) => camel.test(t))) deny("CLAUDE.md § 9.4: never launch the Hytale server; the user restarts it and tests in game.");
}
```

- [ ] **Étape 4 : relancer le banc**

Run : `node .claude/hooks/test/run.js`
Attendu : tous les cas passent, anciens et nouveaux, aucun plantage.

- [ ] **Étape 5 : nommer `runAllMods` dans les textes des garde-fous**

- `CLAUDE.md:117` : `- le lancement du serveur Hytale (tâche Gradle \`runServer\`, même abrégée, …` devient `- le lancement du serveur Hytale (tâches Gradle \`runServer\` et \`runAllMods\`, même abrégées, \`HytaleServer.jar\`, \`Start-Process\`) ;`
- `AGENTS.md:17` : `(\`runServer\`, \`HytaleServer.jar\`)` devient `(\`runServer\`, \`runAllMods\`, \`HytaleServer.jar\`)`.
- `.claude/agents/hycolony-implementer.md:32` : même remplacement.

- [ ] **Étape 6 : relecture indépendante**

Lancer l'agent `hycolony-reviewer` sur les changements non commités : « guard.js refuse runAllMods et ses abréviations comme runServer ; stageAllModAssets reste permis ; banc à jour ; CLAUDE.md, AGENTS.md et l'agent implementer nomment runAllMods ». Corriger ce qu'il trouve, faire relire les corrections.

- [ ] **Étape 7 : commit**

```bash
git add .claude/hooks/guard.js .claude/hooks/test/commands.txt CLAUDE.md AGENTS.md .claude/agents/hycolony-implementer.md
git diff --cached --stat
git commit -m "$(cat <<'EOF'
chore(hooks): guard blocks runAllMods like runServer

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

La suite du plan peut tourner dans une session **non** déverrouillée.

---

### Tâche 2 : trois mods jetables qui compilent

**Files :** tout sous `spike/`, sur la branche `spike/multi-mod`.

**Interfaces :**
- Produit : trois jars dans `spike/{ui,domum,colony}/build/libs/`, et les classes publiques :
  - `dev.spikeui.api.SpikeUi.hello()` → `String` ;
  - `dev.spikedomum.api.SpikeDomum.hello()` → `String`, qui appelle `SpikeUi.hello()` ;
  - la commande `/spike`, qui ouvre `Pages/SpikeColony/Page.ui`.

- [ ] **Étape 1 : créer la branche**

```bash
git switch -c spike/multi-mod
```

- [ ] **Étape 2 : le build autonome**

`spike/settings.gradle.kts` :

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "AzureDoom Maven"
            url = uri("https://maven.azuredoom.com/mods")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "HyColonySpike"
include(":ui", ":domum", ":colony")
```

`spike/build.gradle.kts` (le workspace vient du même jar que `hytale-tools`, chargé une fois à la racine : c'est ce que fera `build-logic`) :

```kotlin
import com.azuredoom.gradle.hytale.HytaleWorkspaceExtension

plugins {
    id("com.azuredoom.hytale-tools") version "1.0.51" apply false
}

apply(plugin = "com.azuredoom.hytale-workspace")

extensions.configure<HytaleWorkspaceExtension> {
    manifestGroup.set("HyColony")
    hytaleVersion.set("0.6.8")
    hostProject.set(":colony")
}
```

`spike/gradle.properties` : copie de `gradle.properties` racine (`cp gradle.properties spike/gradle.properties`), puis remplacer `mod_name = HyColony` par `mod_name = HyColonySpike`. Les champs d'identité (`mod_id`, `main_class`, `manifest_dependencies`) sont surchargés dans chaque module : c'est ce que l'essai vérifie (« Duplicate workspace plugin identifier » sinon).

- [ ] **Étape 3 : le mod `spikeui`**

`spike/ui/build.gradle.kts` :

```kotlin
plugins {
    java
    id("com.azuredoom.hytale-tools")
}

group = "dev.spikeui"
version = "0.1.0"

hytaleTools {
    javaVersion = 25
    manifestServerVersion = ">=0.6.8 <0.7.0"
    modId = "spikeui"
    mainClass = "dev.spikeui.SpikeUiPlugin"
    manifestDependencies = "Hytale:AssetModule=*"
    manifestOptionalDependencies = ""
    includesPack = true
    bundleAssetEditorRuntime = false
}

tasks.named<Jar>("jar") { archiveBaseName.set("SpikeUI") }
```

`spike/ui/src/main/java/dev/spikeui/SpikeUiPlugin.java` :

```java
package dev.spikeui;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.spikeui.api.SpikeUi;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/** Throwaway: the smallest library mod, only logs that it loaded. */
public final class SpikeUiPlugin extends JavaPlugin {
    public SpikeUiPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("SPIKE spikeui setup, data dir %s, %s", getDataDirectory(), SpikeUi.hello());
    }
}
```

`spike/ui/src/main/java/dev/spikeui/api/SpikeUi.java` :

```java
package dev.spikeui.api;

/** Throwaway: the API another mod calls; tells which class loader and which class copy answered. */
public final class SpikeUi {
    private SpikeUi() {}

    /** Names this class's loader and identity: two different answers mean two copies of the class. */
    public static String hello() {
        return "SpikeUi loader=" + SpikeUi.class.getClassLoader() + " id=" + System.identityHashCode(SpikeUi.class);
    }
}
```

Ressources de `spikeui` (sous `spike/ui/src/main/resources/`) :

`Common/UI/Custom/Pages/SpikeUI/Box.ui` (un modèle, et une texture relative à **son** dossier) :

```
@Box = Group {
  Anchor: (Width: 200, Height: 90);
  Background: (TexturePath: "Tex.png", Border: 4);

  Label {
    Text: "box template from spikeui";
    Style: (TextColor: #ffffff);
  }
};
```

`Common/UI/Custom/Pages/SpikeUI/Panel.ui` (un élément ajouté côté serveur par un autre mod) :

```
Label {
  Anchor: (Height: 30);
  Text: "panel appended from spikeui";
  Style: (TextColor: #ffff66);
}
```

`Common/UI/Custom/Pages/SpikeUI/Tex.png` : copie de la texture de slot existante.

```bash
mkdir -p spike/ui/src/main/resources/Common/UI/Custom/Pages/SpikeUI
cp plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Native/Slot.png spike/ui/src/main/resources/Common/UI/Custom/Pages/SpikeUI/Tex.png
```

`Server/Languages/en-US/spikeui.lang` :

```
hello = translated text from spikeui
```

- [ ] **Étape 4 : le mod `spikedomum`**

`spike/domum/build.gradle.kts` :

```kotlin
plugins {
    java
    id("com.azuredoom.hytale-tools")
}

group = "dev.spikedomum"
version = "0.1.0"

dependencies {
    compileOnly(project(":ui"))
}

hytaleTools {
    javaVersion = 25
    manifestServerVersion = ">=0.6.8 <0.7.0"
    modId = "spikedomum"
    mainClass = "dev.spikedomum.SpikeDomumPlugin"
    manifestDependencies = "Hytale:AssetModule=*,HyColony:spikeui=0.1.0"
    manifestOptionalDependencies = ""
    includesPack = false
    bundleAssetEditorRuntime = false
}

tasks.named<Jar>("jar") { archiveBaseName.set("SpikeDomum") }
```

`spike/domum/src/main/java/dev/spikedomum/SpikeDomumConfig.java` :

```java
package dev.spikedomum;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** Throwaway: a config with one counter, raised at each start, to see what survives a dev relaunch. */
final class SpikeDomumConfig {
    static final BuilderCodec<SpikeDomumConfig> CODEC = BuilderCodec.builder(
                    SpikeDomumConfig.class, SpikeDomumConfig::new)
            .append(new KeyedCodec<>("Counter", Codec.INTEGER), (c, v) -> c.counter = v, c -> c.counter)
            .add()
            .build();

    int counter;
}
```

`spike/domum/src/main/java/dev/spikedomum/SpikeDomumPlugin.java` :

```java
package dev.spikedomum;

import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import dev.spikedomum.api.SpikeDomum;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Throwaway: raises a config counter and appends a line to two data files (its data dir, the universe) at each start,
 * so that a second launch shows what the dev staging wiped.
 */
public final class SpikeDomumPlugin extends JavaPlugin {
    private final Config<SpikeDomumConfig> config;

    public SpikeDomumPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        this.config = withConfig("config", SpikeDomumConfig.CODEC);
    }

    @Override
    protected void setup() {
        config.get().counter++;
        var _ = config.save();
        int dataLines = appendLine(getDataDirectory().resolve("data.txt"));
        int universeLines = appendLine(Constants.UNIVERSE_PATH.resolve("spikedomum").resolve("data.txt"));
        getLogger()
                .at(Level.INFO)
                .log(
                        "SPIKE spikedomum setup, Counter=%d, dataDirLines=%d, universeLines=%d, %s",
                        config.get().counter,
                        dataLines,
                        universeLines,
                        SpikeDomum.hello());
    }

    /** Appends one line to file and returns how many it now holds; -1 if it could not. */
    private int appendLine(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "start\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return Files.readAllLines(file).size();
        } catch (IOException e) {
            getLogger().at(Level.WARNING).withCause(e).log("SPIKE spikedomum could not write %s", file);
            return -1;
        }
    }
}
```

`spike/domum/src/main/java/dev/spikedomum/api/SpikeDomum.java` :

```java
package dev.spikedomum.api;

import dev.spikeui.api.SpikeUi;

/** Throwaway: an API that itself calls its dependency's API. */
public final class SpikeDomum {
    private SpikeDomum() {}

    /** This class's loader, then what spikeui answers from here. */
    public static String hello() {
        return "SpikeDomum loader=" + SpikeDomum.class.getClassLoader() + " -> " + SpikeUi.hello();
    }
}
```

- [ ] **Étape 5 : le mod `spikecolony`**

`spike/colony/build.gradle.kts` :

```kotlin
plugins {
    java
    id("com.azuredoom.hytale-tools")
}

group = "dev.spikecolony"
version = "0.1.0"

dependencies {
    compileOnly(project(":ui"))
    compileOnly(project(":domum"))
}

hytaleTools {
    javaVersion = 25
    manifestServerVersion = ">=0.6.8 <0.7.0"
    modId = "spikecolony"
    mainClass = "dev.spikecolony.SpikeColonyPlugin"
    manifestDependencies = "Hytale:AssetModule=*,HyColony:spikeui=0.1.0,HyColony:spikedomum=0.1.0"
    manifestOptionalDependencies = ""
    includesPack = true
    bundleAssetEditorRuntime = false
}

tasks.named<Jar>("jar") { archiveBaseName.set("SpikeColony") }
```

`spike/colony/src/main/java/dev/spikecolony/SpikeColonyPlugin.java` :

```java
package dev.spikecolony;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.spikedomum.api.SpikeDomum;
import dev.spikeui.api.SpikeUi;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/** Throwaway: calls both dependencies' APIs at setup and registers /spike. */
public final class SpikeColonyPlugin extends JavaPlugin {
    public SpikeColonyPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getLogger()
                .at(Level.INFO)
                .log(
                        "SPIKE spikecolony setup, own loader=%s, %s, %s",
                        SpikeColonyPlugin.class.getClassLoader(),
                        SpikeUi.hello(),
                        SpikeDomum.hello());
        getCommandRegistry().registerCommand(new SpikeCommand());
    }
}
```

`spike/colony/src/main/java/dev/spikecolony/SpikeCommand.java` :

```java
package dev.spikecolony;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/** Throwaway: /spike opens the page that mixes the three packs. */
final class SpikeCommand extends AbstractPlayerCommand {
    SpikeCommand() {
        super("spike", "Opens the multi-mod spike page");
        setPermissionGroups("hytale:Adventurer");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        store.getComponent(ref, Player.getComponentType())
                .getPageManager()
                .openCustomPage(ref, store, new SpikePage(player));
    }
}
```

`spike/colony/src/main/java/dev/spikecolony/SpikePage.java` :

```java
package dev.spikecolony;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.BasicCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Throwaway: its own .ui, which includes spikeui's template, plus spikeui's panel appended from here. */
final class SpikePage extends BasicCustomUIPage {
    SpikePage(PlayerRef player) {
        super(player, CustomPageLifetime.CanDismiss);
    }

    @Override
    public void build(UICommandBuilder ui) {
        ui.append("Pages/SpikeColony/Page.ui");
        ui.append("#Host", "Pages/SpikeUI/Panel.ui");
    }
}
```

`spike/colony/src/main/resources/Common/UI/Custom/Pages/SpikeColony/Page.ui` (quatre vérifications visibles, numérotées) :

```
$C = "../../Common.ui";
$B = "../SpikeUI/Box.ui";

$C.@PageOverlay {
  LayoutMode: Middle;

  $C.@DecoratedContainer {
    Anchor: (Width: 500);

    #Content {
      Padding: (Full: 24);

      Label {
        Text: "1. template included from another pack:";
      }

      $B.@Box {}

      Label {
        Anchor: (Top: 10);
        Text: "2. texture of another pack, by relative path:";
      }

      Group {
        Anchor: (Width: 74, Height: 74);
        Background: (TexturePath: "../SpikeUI/Tex.png");
      }

      Label {
        Anchor: (Top: 10);
        Text: "3. panel appended by the server from another pack:";
      }

      Group #Host {
        LayoutMode: Top;
      }

      Label {
        Anchor: (Top: 10);
        Text: %spikeui.hello;
      }
    }
  }
}
```

La dernière ligne est la vérification 4 : une clé de traduction d'un autre mod. Si elle s'affiche brute (`spikeui.hello`), le préfixe de la clé est à noter dans `RESULTS.md` (Hytale préfixe peut-être la clé par le nom du fichier `.lang`, comme `hycolony.lang` → `hycolony.*`).

- [ ] **Étape 6 : compiler**

Run : `./gradlew -p spike build`
Attendu : BUILD SUCCESSFUL, trois jars : `spike/ui/build/libs/SpikeUI-0.1.0.jar`, `spike/domum/build/libs/SpikeDomum-0.1.0.jar`, `spike/colony/build/libs/SpikeColony-0.1.0.jar`, et trois `manifest.json` réécrits sous `spike/*/src/main/resources/`.

Si l'échec est « Duplicate workspace plugin identifier » : la surcharge `modId` par module ne suffit pas ; noter l'erreur dans `RESULTS.md` et chercher dans `HytaleExtensionDefaults.groovy` (sources en cache, l. 376-383) quelle propriété lire à la place. Toute autre erreur de compilation sur une API Hytale : la vérifier dans `build/vineflower/hytale-server` avant de corriger.

- [ ] **Étape 7 : vérifier les jars et les manifestes**

```bash
for j in spike/*/build/libs/*.jar; do echo "== $j"; unzip -l "$j" | awk '{print $4}' | grep -E '\.class$|manifest.json' ; done
cat spike/*/src/main/resources/manifest.json
```

Attendu :
- chaque jar ne contient que les classes de **son** paquet (`dev/spikeui/**`, `dev/spikedomum/**` ou `dev/spikecolony/**`) ;
- aucun `com/azuredoom/hytale/asseteditor/**` ;
- chaque manifeste a son propre `Name`, son `Main`, `Group: HyColony`, et les `Dependencies` attendues (`HyColony:spikeui` → `0.1.0`, etc.).

Tout écart va dans `RESULTS.md` (étape 8).

- [ ] **Étape 8 : ouvrir `RESULTS.md` et commiter**

`spike/RESULTS.md` :

```markdown
# Résultats de l'essai multi-mods (plan 1)

## Build (tâche 2)
- Build : <SUCCESS / erreur exacte>
- Contenu des jars : <conforme / écart>
- Manifestes : <Name, Group, Main, Dependencies de chacun>
```

```bash
git add spike/settings.gradle.kts spike/build.gradle.kts spike/gradle.properties spike/ui spike/domum spike/colony spike/RESULTS.md
git diff --cached --stat
git commit -m "$(cat <<'EOF'
chore(spike): three throwaway mods to test multi-mod loading

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

`git diff --cached --stat` ne doit lister aucun `build/`, `run/` ni `config.json`.

Pas de relecture indépendante pour le code jetable de l'essai : il n'est jamais fusionné. Les résultats, eux, sont relus à la tâche 5.

---

### Tâche 3 : l'essai en dev (`runAllMods`, lancé par l'utilisateur)

**Files :** `spike/RESULTS.md`.

- [ ] **Étape 1 : préparer et inspecter le staging (sans lancer de serveur)**

Run : `./gradlew -p spike stageAllModAssets`
Puis :

```bash
ls -la spike/run/mods/
ls -la spike/run/mods/HyColony_spikedomum/ spike/run/mods/HyColony_spikeui/
```

Noter dans `RESULTS.md` : les noms des dossiers (`HyColony_spikeui`, …), et la nature des entrées (lien symbolique `->`, lien dur ou copie ; `fsutil hardlink list <fichier>` sous Windows pour un lien dur).

- [ ] **Étape 2 : placer un `config.json` dans les ressources de `spikedomum` (piste 1 de la spec)**

`spike/domum/src/main/resources/config.json` :

```json
{
  "Counter": 100
}
```

Ne pas le commiter (règle des `config.json`). Relancer `./gradlew -p spike stageAllModAssets` et noter si `spike/run/mods/HyColony_spikedomum/config.json` est un lien vers ce fichier.

- [ ] **Étape 3 : premier lancement, par l'utilisateur**

Demander à l'utilisateur de lancer, depuis la racine du dépôt : `./gradlew -p spike runAllMods`, de se connecter, de taper `/spike`, de faire une capture de la page, puis d'arrêter le serveur. Il envoie :
- les lignes `SPIKE` du journal (`spike/run/logs/` ou la console) ;
- toute ligne `SEVERE` ou `WARN` qui cite `spike` ;
- la capture de la page (vérifications 1 à 4).

- [ ] **Étape 4 : état après le premier lancement**

```bash
cat spike/domum/src/main/resources/config.json
ls -la spike/run/mods/HyColony_spikedomum/
cat spike/run/mods/HyColony_spikedomum/config.json spike/run/mods/HyColony_spikedomum/data.txt 2>&1
cat spike/run/universe/spikedomum/data.txt
```

Noter : le compteur du journal (101 si le lien a été lu, 1 sinon), le contenu du `config.json` des ressources (resté à 100 ou passé à 101 : l'écriture a-t-elle traversé le lien ?), la présence d'un `.bak`, et si `run/mods/HyColony_spikedomum/config.json` est encore un lien.

Attention, deux sources se contredisent : la spec (d'après `BsonUtil.writeDocumentSync`) prévoit que l'écriture remplace le lien, alors que le commentaire de `.gitignore` (l. 15) dit que le serveur de dev écrit la config de HyColony **dans les ressources**, à travers le lien. L'observation tranche ; la noter telle quelle.

- [ ] **Étape 5 : second lancement, par l'utilisateur**

Même demande qu'à l'étape 3 (sans `/spike`). Puis relancer les commandes de l'étape 4.

Attendu par la spec, à confirmer ou infirmer :
- `Counter` repart de la valeur des ressources (101 à nouveau) : ce que le mod écrit dans `run/mods/<mod>/` est perdu au staging ;
- `dataDirLines = 1` : même perte pour les données du dossier du mod ;
- `universeLines = 2` : `run/universe/` survit.

- [ ] **Étape 6 : conclure la partie dev dans `RESULTS.md` et commiter**

Ajouter à `spike/RESULTS.md` :

```markdown
## Dev (tâche 3)
- Staging : <noms des dossiers, type d'entrées>
- Chargement : <les trois `SPIKE ... setup`, dans quel ordre ; SEVERE éventuels>
- Classes : <loaders et id affichés par SpikeUi.hello() depuis domum et colony>
- Page /spike : 1 <ok/ko> ; 2 <ok/ko> ; 3 <ok/ko> ; 4 <ok/ko, forme de la clé>
- Config, 1er lancement : <Counter, ressources, .bak, lien>
- Config, 2e lancement : <Counter, dataDirLines, universeLines>
```

```bash
git add spike/RESULTS.md
git commit -m "$(cat <<'EOF'
chore(spike): record the dev launch results

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Tâche 4 : l'essai en production (les jars dans `mods/`, lancé par l'utilisateur)

**Files :** `spike/RESULTS.md`.

- [ ] **Étape 1 : préparer le dossier de production (hors du dépôt)**

```bash
P=/c/Users/Ctuto/Desktop/HyColonySpikeProd
mkdir -p "$P/mods"
cp server/HytaleServer.jar "$P/"
cp spike/ui/build/libs/SpikeUI-0.1.0.jar spike/domum/build/libs/SpikeDomum-0.1.0.jar spike/colony/build/libs/SpikeColony-0.1.0.jar "$P/mods/"
ls -la "$P" "$P/mods"
```

Aucun `config.json` n'est copié : en production, chaque mod écrit le sien dans `mods/HyColony_<nom>/`.

- [ ] **Étape 2 : les trois jars ensemble, par l'utilisateur**

Demander à l'utilisateur de lancer, dans `Desktop\HyColonySpikeProd` :

```
java -jar HytaleServer.jar --assets=<le même Assets.zip que runServer> --allow-op --disable-sentry
```

(le chemin de l'`Assets.zip` est celui qu'affiche `runServer` : « Using extracted assets zip: … » ; si le serveur demande une authentification, la faire comme sur le serveur de dev). Puis se connecter, `/spike`, capture, arrêt. Il envoie les lignes `SPIKE`, les `SEVERE`/`WARN`, la capture.

Attendu :
- trois `SPIKE ... setup`, `spikeui` d'abord, `spikecolony` en dernier ;
- `SpikeUi.hello()` donne le **même** `loader` et le **même** `id` vu depuis `spikeui`, `spikedomum` et `spikecolony` (une seule copie) ; le loader est un `PluginClassLoader` propre au jar `SpikeUI` ;
- la page /spike identique à celle du dev ;
- les dossiers `mods/HyColony_spikedomum/config.json` et `data.txt` créés.

- [ ] **Étape 3 : second lancement de production**

Même lancement, sans `/spike`. Attendu : `Counter=2`, `dataDirLines=2`, `universeLines=2` (rien n'efface le dossier d'un mod en production).

- [ ] **Étape 4 : dépendance manquante, par l'utilisateur**

```bash
mkdir -p /c/Users/Ctuto/Desktop/HyColonySpikeProd/mods-off
mv /c/Users/Ctuto/Desktop/HyColonySpikeProd/mods/SpikeDomum-0.1.0.jar /c/Users/Ctuto/Desktop/HyColonySpikeProd/mods-off/
```

L'utilisateur relance. Attendu : `spikeui` se charge, `spikecolony` est refusé avec un message de dépendance manquante qui nomme `HyColony:spikedomum`, et le serveur démarre quand même. Il envoie les lignes qui citent `spikecolony` ou `spikedomum`. Remettre ensuite le jar :

```bash
mv /c/Users/Ctuto/Desktop/HyColonySpikeProd/mods-off/SpikeDomum-0.1.0.jar /c/Users/Ctuto/Desktop/HyColonySpikeProd/mods/
```

- [ ] **Étape 5 : conclure la partie production et commiter**

Ajouter à `spike/RESULTS.md` :

```markdown
## Production (tâche 4)
- Chargement : <ordre, SEVERE éventuels>
- Classes : <loader et id de SpikeUi vus des trois mods : une seule copie ?>
- Page /spike : <identique au dev ?>
- Données : <1er et 2e lancement : Counter, dataDirLines, universeLines>
- Dépendance manquante : <message exact, le serveur a-t-il démarré, spikeui chargé ?>
```

```bash
git add spike/RESULTS.md
git commit -m "$(cat <<'EOF'
chore(spike): record the production launch results

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Tâche 5 : résultats dans la recherche, décision dans la spec

**Files :**
- Modify : `docs/research/plugin-b-api.md` (nouvelle section `## 27. Mods multiples`, juste avant `## Could not verify`)
- Modify : `docs/superpowers/specs/2026-09-28-hycolony-split-hydomum-hyblockui-design.md`, § « Config et données en dev »

- [ ] **Étape 1 : revenir sur la branche de travail**

```bash
git switch sp0-foundations
git show spike/multi-mod:spike/RESULTS.md
```

- [ ] **Étape 2 : écrire `## 27. Mods multiples`**

Reprendre de `RESULTS.md` ce qui est vérifié, chaque point avec sa source (fichier et ligne du serveur ou du plugin Gradle, ou « vérifié en jeu le <date> ») :
- 27.1 Manifeste : `Dependencies` avec une version exacte (`0.1.0` accepté ou non), ordre de chargement, identité par module (`modId` surchargé, dossier `mods/HyColony_<nom>`) ;
- 27.2 Classes en production : loader de chaque jar, une seule copie d'une API partagée, jars sans `asseteditor` ;
- 27.3 Classes en dev : ce que `runAllMods` change (classpath commun) ;
- 27.4 Assets entre packs : modèle `.ui` inclus, texture relative, `.ui` ajouté par le serveur, clé de traduction (et sa forme exacte) ;
- 27.5 Dépendance manquante : message exact, comportement du serveur ;
- 27.6 Config et données en dev : ce que le staging efface, ce que fait l'écriture sur un lien.

Un point qui a échoué est écrit comme tel, avec l'erreur exacte, et le contournement s'il a été trouvé (ne jamais conclure « impossible » sans avoir essayé les variantes, cf. mémoire du projet).

- [ ] **Étape 3 : écrire la décision dans la spec**

Dans § « Config et données en dev », remplacer la phrase « Le plan 1 vérifie ce que ça fait … Il retient ensuite une solution et l'écrit dans cette spec avant le plan 2. Les pistes, dans l'ordre : » et la liste des trois pistes par :
- ce que l'essai a montré (renvoi à `plugin-b-api.md` § 27.6) ;
- **la solution retenue**, choisie ainsi :
  - si l'étape 5 de la tâche 3 montre que `run/universe/` survit et que seul `run/mods/<mod>/` est effacé : piste 3, une tâche de dev (dans `build-logic`, plan 2) qui recopie `<module>/run-config/config.json` (ignoré par git) vers `run/mods/HyColony_<nom>/config.json` **après** `stageAllModAssets` et avant `runAllMods` ; ce que le mod réécrit pendant la partie est perdu au lancement suivant, c'est accepté et écrit ;
  - si le lien de la piste 1 a été lu **et** que l'écriture n'a pas cassé le démarrage : piste 1 suffit (lecture seule), avec la même mise en garde ;
  - si ni l'une ni l'autre ne tient : piste 2 (`runServer` par module), et le signaler à l'utilisateur avant le plan 2 ;
- les chemins protégés qui en découlent (ceux que `guard.js` et `.gitignore` devront couvrir au plan 2).

Mettre aussi à jour, s'ils ont été contredits : § « Faits vérifiés » (ordre de chargement, versions exactes), § « Ce qui va où » (traductions entre mods), § « Identité de chaque mod ».

- [ ] **Étape 4 : relecture indépendante**

Lancer `hycolony-reviewer` sur les changements non commités : « les résultats de l'essai (spike/RESULTS.md sur la branche spike/multi-mod) sont fidèlement reportés dans plugin-b-api.md § 27, avec leurs sources ; la spec retient une solution de config de dev justifiée par ces résultats ; aucune affirmation non vérifiée ». Corriger, faire relire les corrections.

- [ ] **Étape 5 : commit**

```bash
git add docs/research/plugin-b-api.md docs/superpowers/specs/2026-09-28-hycolony-split-hydomum-hyblockui-design.md
git diff --cached --stat
git commit -m "$(cat <<'EOF'
docs: multi-mod spike results and the dev config decision

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Étape 6 : la branche d'essai et le dossier de production**

Demander à l'utilisateur s'il veut supprimer la branche `spike/multi-mod` (`git branch -D spike/multi-mod`) et `Desktop\HyColonySpikeProd`, ou les garder jusqu'à la fin du plan 4. Ne rien supprimer sans sa réponse.

Le plan 2 (`build-logic`) peut ensuite s'écrire.
