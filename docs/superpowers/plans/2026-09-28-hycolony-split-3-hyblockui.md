# Séparation en trois mods, plan 3 : HyBlockUI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** sortir le kit d'interface d'inventaire de HyColony dans son propre mod, `HyColony:hyblockui`, dont HyColony dépend, sans rien changer de ce que voit le joueur.

**Architecture :**
- Nouveau projet Gradle `:blockui` (dossier `blockui/`, convention `hy.hytale-mod`, groupe Maven `dev.hyblockui`).
- Tout son code public vit dans `dev.hyblockui.api`, sa classe `Main` dans `dev.hyblockui`. Il contient :
  - les 10 classes de `plugin/inventory` ;
  - `ui/PageEvents` ;
  - un nouveau `Texts`, la traduction avec paramètres, dont `HytaleNotifier.toMessage` devient un simple relais.
- Son pack contient les panneaux du joueur (`Pages/HyBlockUI/*.ui`), toutes les textures natives (`Pages/HyBlockUI/Native/`) et `hyblockui.lang`.
- HyColony le déclare en `compileOnly` et dans `Dependencies` ; ses `.ui` pointent vers `../HyBlockUI/Native/…` (références entre packs vérifiées, `plugin-b-api.md` § 28.3).

**Tech Stack :** Java 25, Hytale 0.6.8, conventions `build-logic` (plan 2).

**Spec :** `docs/superpowers/specs/2026-09-28-hycolony-split-hydomum-hyblockui-design.md` (§ « Identité de chaque mod », « API des mods », « Ce qui va où », « Plans » point 3). Résultats de l'essai : `docs/research/plugin-b-api.md` § 28.

## Contraintes globales

- Identité : `HyColony:hyblockui`, `Main` = `dev.hyblockui.HyBlockUIPlugin`, `Dependencies` = `Hytale:AssetModule`, version 0.1.0. HyColony ajoute `HyColony:hyblockui==0.1.0` à ses `Dependencies`.
- Un mod n'embarque jamais un autre mod : `:plugin` → `:blockui` en `compileOnly`, jamais `bundled`.
- HyColony n'importe que `dev.hyblockui.api.*` (`checkModApis`).
- Rien ne change pour le joueur : mêmes fenêtres, mêmes textures, même titre « Inventaire ». Aucune règle de jeu ne bouge (tout ce qui déménage est du plugin).
- Les déplacements se font avec `git mv`, pour garder l'historique.
- Aucune session déverrouillée n'est nécessaire : aucun garde-fou n'est touché (le `config.json` de `blockui/` est déjà protégé et ignoré, plan 2).
- `./gradlew build` vert avant chaque commit ; relecture indépendante de chaque tâche ; commits `type(scope): description` avec `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`, `git add` de chemins explicites.
- **Une autre session travaille dans le même dossier.** Avant chaque tâche : `git status --short` ; si un fichier que la tâche déplace ou modifie (liste « Files ») a des changements non commités d'une autre session, s'arrêter et le signaler à l'utilisateur. N'indexer que ses propres fichiers.
- On ne lance jamais le serveur ; l'utilisateur teste en jeu.

## Review Focus

- **Une texture qui ne s'affiche plus** après le déplacement (chemin relatif faux dans un `.ui` de HyColony) : tâche 3, étape 3 (recherche de tout `Native/` restant) et étape 7 (essai en jeu, fenêtre par fenêtre).
- **Le titre « Inventaire » affiché brut** (`hyblockui.inventory.title`) : tâche 3, étape 7, point 2.
- **Le jar de HyColony qui garde une copie** des classes déplacées : tâche 2, étape 6.
- **HyColony déployé sans HyBlockUI en production** : le serveur ne démarre pas (`plugin-b-api.md` § 28.4) ; tâche 3, étape 5 l'écrit dans `docs/TESTING.md`.
- **Un glisser-déposer ou un clic Maj cassé** par le changement de paquet (le codec d'`InventoryDrop` est enregistré par nom de classe ?) : tâche 2, étape 3 le vérifie dans le code, tâche 3, étape 7 en jeu.

## Fichiers

- Créer : `blockui/build.gradle.kts`, `blockui/src/main/java/dev/hyblockui/HyBlockUIPlugin.java`, `blockui/src/main/java/dev/hyblockui/api/Texts.java`, `blockui/src/main/resources/Server/Languages/{en-US,fr-FR}/hyblockui.lang`, `blockui/src/main/resources/manifest.json` (écrit par `updatePluginManifest`).
- Déplacer (`git mv`) :
  - `plugin/src/main/java/dev/hycolony/plugin/inventory/*.java` (10) et `plugin/src/main/java/dev/hycolony/plugin/ui/PageEvents.java` → `blockui/src/main/java/dev/hyblockui/api/` ;
  - `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/{PlayerCharacterPanel,PlayerStoragePanel}.ui` → `blockui/src/main/resources/Common/UI/Custom/Pages/HyBlockUI/` ;
  - `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Native/` → `blockui/src/main/resources/Common/UI/Custom/Pages/HyBlockUI/Native/`.
- Modifier :
  - `settings.gradle.kts`, `plugin/build.gradle.kts`, `gradle.properties` (`manifest_dependencies`) ;
  - les utilisateurs du code déplacé : `plugin/…/ornament/cutter/{CutterCrafting,CutterPage,CutterSlots}.java`, `plugin/…/ui/{ColonyPage,FoundColonyPage}.java`, `plugin/…/ui/citizen/{CitizenInventoryPanel,CitizenInventoryWindows,CitizenPage}.java`, `plugin/…/adapter/HytaleNotifier.java` ;
  - les `.ui` qui citent `Native/` : `Citizen.ui`, `Cutter.ui`, `FieldRow.ui`, `RecipeRow.ui` ;
  - `plugin/src/main/resources/Server/Languages/{en-US,fr-FR}/hycolony.lang` (retrait de `ui.inventory.title`) ;
  - docs : `docs/native-ui-textures.md`, `docs/research/plugin-b-api.md` (chemins de § 7), `docs/TESTING.md`.

---

### Tâche 1 : le mod `HyColony:hyblockui`, vide

**Files :**
- Create : `blockui/build.gradle.kts`, `blockui/src/main/java/dev/hyblockui/HyBlockUIPlugin.java`
- Modify : `settings.gradle.kts`, `plugin/build.gradle.kts`, `gradle.properties`

**Interfaces :**
- Produit : le projet `:blockui` (jar `HyBlockUI-0.1.0.jar`), dont `:plugin` dépend en `compileOnly` ; le manifeste de HyColony exige `HyColony:hyblockui` 0.1.0.

- [ ] **Étape 1 : le projet**

`settings.gradle.kts` : `include(":core", ":plugin")` devient `include(":core", ":plugin", ":blockui")`.

`blockui/build.gradle.kts` :

```kotlin
plugins { id("hy.hytale-mod") }

// HyBlockUI, the UI library (split spec § Identité de chaque mod): a mod of its own, like ldtteam's BlockUI.
group = "dev.hyblockui"

hytaleTools {
    modId = "hyblockui"
    mainClass = "dev.hyblockui.HyBlockUIPlugin"
    modDescription = "Inventory windows for Hytale mods: native-looking grids, drag and drop, the player's panels."
    modCredits = project.property("mod_author").toString()
    manifestDependencies = "Hytale:AssetModule=*"
    manifestOptionalDependencies = ""
}

tasks.named<Jar>("jar") { archiveBaseName.set("HyBlockUI") }
```

`blockui/src/main/java/dev/hyblockui/HyBlockUIPlugin.java` :

```java
package dev.hyblockui;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;

/**
 * HyBlockUI's entry point. Hytale loads every mod jar from its Main class (PendingLoadJavaPlugin); the library itself
 * registers nothing, its windows are opened by the mods that use it.
 */
public final class HyBlockUIPlugin extends JavaPlugin {
    public HyBlockUIPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }
}
```

- [ ] **Étape 2 : HyColony en dépend**

`plugin/build.gradle.kts`, bloc `dependencies` : ajouter `compileOnly(project(":blockui"))` sous `bundled(project(":core"))`, avec le commentaire `// Another mod: compiled against, never shipped (plugin-b-api.md § 28.2).`

`gradle.properties` : `manifest_dependencies = Hytale:AssetModule=*,Hytale:NPC=*` devient `manifest_dependencies = Hytale:AssetModule=*,Hytale:NPC=*,HyColony:hyblockui==0.1.0`.

- [ ] **Étape 3 : build et manifestes**

Run : `./gradlew build --console=plain`
Attendu : BUILD SUCCESSFUL. Puis :

```bash
cat blockui/src/main/resources/manifest.json
git diff plugin/src/main/resources/manifest.json
unzip -l blockui/build/libs/HyBlockUI-0.1.0.jar | awk '{print $4}' | grep -v '/$'
```

Attendu : le manifeste de HyBlockUI a `"Group": "HyColony"`, `"Name": "hyblockui"`, `"Main": "dev.hyblockui.HyBlockUIPlugin"`, `Dependencies` `Hytale:AssetModule`, sa description ; celui de HyColony gagne `"HyColony:hyblockui": "=0.1.0"`. Le jar contient `HyBlockUIPlugin.class` et `manifest.json`, rien d'autre.

Si `manifest.json` de HyBlockUI contient `"IncludesAssetPack": true` alors que le mod n'a pas encore d'assets, c'est attendu (la convention lit `includes_pack = true`) : la tâche 3 lui en donne.

- [ ] **Étape 4 : le workspace voit deux mods**

Run : `./gradlew stageAllModAssets --console=plain -q && ls run/mods/`
Attendu : `HyColony_hyblockui` et `HyColony_hycolony`.

- [ ] **Étape 5 : relecture indépendante, puis commit**

`hycolony-reviewer` : « nouveau mod vide HyColony:hyblockui (identité conforme à la spec), dépendance compileOnly et manifeste de HyColony ». Corriger, faire relire.

```bash
git status --short
git add settings.gradle.kts gradle.properties plugin/build.gradle.kts plugin/src/main/resources/manifest.json blockui/build.gradle.kts blockui/src/main/java blockui/src/main/resources/manifest.json
git diff --cached --stat
git commit -m "$(cat <<'EOF'
feat(blockui): empty HyBlockUI mod that HyColony depends on

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Tâche 2 : le code d'inventaire déménage dans `dev.hyblockui.api`

**Files :**
- Move : `plugin/src/main/java/dev/hycolony/plugin/inventory/{HeldWindows,InventoryDrop,InventoryGrids,InventoryMoves,InventoryWatch,PageRedraw,PlayerItems,PlayerPanels,PlayerSection,ReturningContainerWindow}.java`, `plugin/src/main/java/dev/hycolony/plugin/ui/PageEvents.java` → `blockui/src/main/java/dev/hyblockui/api/`
- Create : `blockui/src/main/java/dev/hyblockui/api/Texts.java`
- Modify : `plugin/…/adapter/HytaleNotifier.java`, et les 8 fichiers qui importent le code déplacé (liste dans « Fichiers »)

**Interfaces :**
- Produit, pour HyColony et HyDomum (plan 4) : les classes publiques `dev.hyblockui.api.{HeldWindows, InventoryDrop, InventoryGrids, InventoryMoves, InventoryWatch, PageRedraw, PlayerItems, PlayerPanels, PlayerSection, ReturningContainerWindow, PageEvents}` avec les **mêmes** signatures qu'aujourd'hui, et `Texts.translated(String key, List<String> params)` → `com.hypixel.hytale.server.core.Message`.

- [ ] **Étape 1 : déplacer et renommer le paquet**

```bash
mkdir -p blockui/src/main/java/dev/hyblockui/api
for f in HeldWindows InventoryDrop InventoryGrids InventoryMoves InventoryWatch PageRedraw PlayerItems PlayerPanels PlayerSection ReturningContainerWindow; do
  git mv plugin/src/main/java/dev/hycolony/plugin/inventory/$f.java blockui/src/main/java/dev/hyblockui/api/$f.java
done
git mv plugin/src/main/java/dev/hycolony/plugin/ui/PageEvents.java blockui/src/main/java/dev/hyblockui/api/PageEvents.java
sed -i 's/^package dev\.hycolony\.plugin\.inventory;/package dev.hyblockui.api;/; s/^package dev\.hycolony\.plugin\.ui;/package dev.hyblockui.api;/' blockui/src/main/java/dev/hyblockui/api/*.java
grep -rn "dev\.hycolony" blockui/src/main/java
```

Attendu : le dernier `grep` ne trouve rien (le code d'inventaire n'importait déjà rien de HyColony).

- [ ] **Étape 2 : les messages du journal ne disent plus « hycolony »**

```bash
grep -rn -i "hycolony" blockui/src/main/java
```

Remplacer chaque préfixe de journal `"hycolony: …"` par `"hyblockui: …"`, et, dans `PageEvents`, `"HyColony window %s: an action failed"` par `"Window %s: an action failed"`. Aucun autre texte ne cite HyColony ; les Javadoc qui citent une fenêtre de HyColony en exemple deviennent génériques (« a mod's window »).

- [ ] **Étape 3 : rien ne dépend du nom de paquet**

```bash
grep -rn "getName()\|getSimpleName()\|forName\|\"dev\.hycolony" blockui/src/main/java
```

Vérifier que ni le codec d'`InventoryDrop` ni les clés d'événement (`InventoryGrids.DROP_ACTION = "inventoryDrop"`, clé `Grid`) ne contiennent le nom de paquet ou de classe : ce sont des chaînes fixes, que le client renvoie telles quelles. Noter le résultat dans le compte rendu de la tâche.

- [ ] **Étape 4 : `Texts`**

`blockui/src/main/java/dev/hyblockui/api/Texts.java` :

```java
package dev.hyblockui.api;

import com.hypixel.hytale.server.core.Message;
import java.util.List;

/** Translated texts with parameters, written {p0}, {p1}… in the .lang files. */
public final class Texts {
    private Texts() {}

    /**
     * The translation of {@code key}, its params set as p0, p1…; a param written "%key" is itself translated, and a
     * label showing such a message takes it on .TextSpans, not .Text.
     */
    public static Message translated(String key, List<String> params) {
        Message m = Message.translation(key);
        for (int i = 0; i < params.size(); i++) {
            String p = params.get(i);
            m = p.startsWith("%") ? m.param("p" + i, Message.translation(p.substring(1))) : m.param("p" + i, p);
        }
        return m;
    }
}
```

`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleNotifier.java` : le corps de `toMessage` devient `return Texts.translated(msg.key(), msg.params());` (import `dev.hyblockui.api.Texts`), sa Javadoc devient `/** Core Msg -> Hytale Message, through HyBlockUI's {@link Texts#translated}. */`. `send` ne change pas.

- [ ] **Étape 5 : les imports de HyColony**

```bash
grep -rln "dev\.hycolony\.plugin\.inventory\.\|dev\.hycolony\.plugin\.ui\.PageEvents" plugin/src/main/java | xargs sed -i 's/dev\.hycolony\.plugin\.inventory\./dev.hyblockui.api./g; s/dev\.hycolony\.plugin\.ui\.PageEvents/dev.hyblockui.api.PageEvents/g'
grep -rn "plugin\.inventory\|plugin\.ui\.PageEvents" plugin/src
```

Puis, dans les fichiers de `plugin/…/ui/` (même paquet que l'ancien `PageEvents`, donc sans import) : `ColonyPage.java` et `FoundColonyPage.java` utilisent `PageEvents` sans l'importer ; leur ajouter `import dev.hyblockui.api.PageEvents;`. Faire de même pour tout fichier que la compilation signale.

Run : `./gradlew spotlessApply && ./gradlew build --console=plain`
Attendu : BUILD SUCCESSFUL (Spotless trie les imports déplacés ; `checkModApis` passe : HyColony n'importe que `dev.hyblockui.api`).

- [ ] **Étape 6 : chaque classe n'existe qu'une fois**

```bash
unzip -l plugin/build/libs/HyColony-0.1.0.jar | grep -E "hyblockui|plugin/inventory|PageEvents"
unzip -l blockui/build/libs/HyBlockUI-0.1.0.jar | awk '{print $4}' | grep '\.class$'
```

Attendu : la première commande ne trouve **rien** ; la seconde liste `dev/hyblockui/HyBlockUIPlugin.class` et les 12 classes de `dev/hyblockui/api/` (plus leurs classes internes éventuelles).

- [ ] **Étape 7 : relecture indépendante, puis commit**

`hycolony-reviewer` : « déplacement sans changement de comportement du module d'inventaire et de PageEvents vers dev.hyblockui.api ; Texts et relais de HytaleNotifier ; aucune copie dans le jar de HyColony ; aucun nom de paquet dans les clés d'événement ». Corriger, faire relire.

```bash
git status --short
git add blockui/src/main/java plugin/src/main/java/dev/hycolony/plugin/inventory plugin/src/main/java/dev/hycolony/plugin/ui/PageEvents.java <les fichiers modifiés à l'étape 5, un par un> plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleNotifier.java
git diff --cached --stat
git commit -m "$(cat <<'EOF'
refactor(blockui): the inventory kit and PageEvents move to dev.hyblockui.api

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Tâche 3 : les panneaux, les textures et la traduction déménagent

**Files :**
- Move : `PlayerCharacterPanel.ui`, `PlayerStoragePanel.ui`, `Native/` (de `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/` vers `blockui/src/main/resources/Common/UI/Custom/Pages/HyBlockUI/`)
- Create : `blockui/src/main/resources/Server/Languages/{en-US,fr-FR}/hyblockui.lang`
- Modify : `blockui/…/api/PlayerPanels.java:18-19`, `Citizen.ui`, `Cutter.ui`, `FieldRow.ui`, `RecipeRow.ui`, `hycolony.lang` (en-US, fr-FR), `docs/native-ui-textures.md`, `docs/research/plugin-b-api.md`, `docs/TESTING.md`

- [ ] **Étape 1 : déplacer**

```bash
D=blockui/src/main/resources/Common/UI/Custom/Pages/HyBlockUI
S=plugin/src/main/resources/Common/UI/Custom/Pages/HyColony
mkdir -p $D
git mv $S/PlayerCharacterPanel.ui $D/PlayerCharacterPanel.ui
git mv $S/PlayerStoragePanel.ui $D/PlayerStoragePanel.ui
git mv $S/Native $D/Native
```

Les deux panneaux gardent `$C = "../../Common.ui";` (même profondeur) et leurs `Native/…` relatifs (même dossier voisin).

- [ ] **Étape 2 : les chemins côté code**

`blockui/…/api/PlayerPanels.java` : `"Pages/HyColony/PlayerCharacterPanel.ui"` et `"Pages/HyColony/PlayerStoragePanel.ui"` deviennent `"Pages/HyBlockUI/PlayerCharacterPanel.ui"` et `"Pages/HyBlockUI/PlayerStoragePanel.ui"`.

- [ ] **Étape 3 : les chemins côté `.ui` de HyColony**

```bash
sed -i 's#"Native/#"../HyBlockUI/Native/#g' plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/{Citizen,Cutter,FieldRow,RecipeRow}.ui
grep -rn '"Native/' plugin/src/main/resources/Common
grep -rn 'HyBlockUI/Native/' plugin/src/main/resources/Common | wc -l
```

Attendu : le premier `grep` ne trouve rien ; le second compte autant de lignes qu'il y avait de `Native/` dans ces quatre fichiers avant (`git show HEAD:<fichier> | grep -c '"Native/'`, à sommer).

- [ ] **Étape 4 : la traduction**

`blockui/src/main/resources/Server/Languages/en-US/hyblockui.lang` :

```
inventory.title = Inventory
```

`blockui/src/main/resources/Server/Languages/fr-FR/hyblockui.lang` :

```
inventory.title = Inventaire
```

Dans `PlayerStoragePanel.ui` (déplacé) : `@Text = %hycolony.ui.inventory.title;` devient `@Text = %hyblockui.inventory.title;`. Retirer la ligne `ui.inventory.title = …` des deux `hycolony.lang` (l. 300). Vérifier qu'aucun autre fichier ne cite `ui.inventory.title` : `grep -rn "ui.inventory.title" plugin blockui`.

(Le skill `add-lang-key` décrit les fichiers de HyColony ; ici la clé est dans le pack de HyBlockUI, préfixe `hyblockui.` = nom du fichier `.lang`, vérifié par l'essai, `plugin-b-api.md` § 28.3.)

- [ ] **Étape 5 : les docs**

- `docs/native-ui-textures.md` : le chemin `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Native/` devient `blockui/src/main/resources/Common/UI/Custom/Pages/HyBlockUI/Native/` ; ajouter une phrase : « Les `.ui` des autres mods (HyColony, HyDomum) les citent par `../HyBlockUI/Native/<nom>.png`. »
- `docs/research/plugin-b-api.md` : `grep -n "plugin/inventory\|Pages/HyColony/Native\|PlayerStoragePanel\|PlayerCharacterPanel" docs/research/plugin-b-api.md` ; mettre à jour chaque chemin cité vers son nouvel emplacement (`blockui/…/api/…`, `Pages/HyBlockUI/…`), sans toucher au contenu vérifié.
- `docs/TESTING.md`, en tête (après la ligne 3), ajouter : « HyColony dépend du mod HyBlockUI. En dev, `runAllMods` charge les deux. En production, déposer `HyBlockUI-*.jar` et `HyColony-*.jar` ensemble dans `mods/` : sans HyBlockUI, le serveur entier ne démarre pas (`docs/research/plugin-b-api.md` § 28.4). »

- [ ] **Étape 6 : build et jars**

Run : `./gradlew build --console=plain` puis :

```bash
unzip -l blockui/build/libs/HyBlockUI-0.1.0.jar | awk '{print $4}' | grep -v '/$' | grep -v '\.class$'
unzip -l plugin/build/libs/HyColony-0.1.0.jar | grep -E "Native/|PlayerStoragePanel|PlayerCharacterPanel"
./gradlew stageAllModAssets --console=plain -q && ls run/mods/HyColony_hyblockui/Common/UI/Custom/Pages/HyBlockUI
```

Attendu : le jar de HyBlockUI contient `manifest.json`, les deux panneaux, les 17 textures `Native/*@2x.png`, les deux `hyblockui.lang` ; celui de HyColony n'en contient aucun ; le staging montre `Native`, `PlayerCharacterPanel.ui`, `PlayerStoragePanel.ui`.

- [ ] **Étape 7 : essai en jeu, par l'utilisateur**

Demander à l'utilisateur de lancer `./gradlew runAllMods`, puis de vérifier, en comparant avec avant :
1. le journal : `HyColony:hyblockui` chargé avant `HyColony:hycolony`, « HyColony runtime ready », aucun SEVERE ni `missing asset` ;
2. l'onglet Inventaire d'un citoyen : grille du citoyen, avatar et armure, inventaire du joueur avec le titre « Inventaire » (pas `hyblockui.inventory.title`), cases et barres de durabilité texturées ;
3. glisser-déposer et clic Maj entre le citoyen et le joueur, dans les deux sens ;
4. l'établi de l'architecte (si le sous-pack Domum Ornamentum est activé) : cases d'ingrédient grises puis vertes avec coche, rouges si manquantes ; dépôt d'un bloc dans une case ;
5. l'onglet Champs d'une hutte de fermier (icône de bloc `BlockIcon`) et l'onglet Recettes (flèches de liste, case vide) : textures présentes.

Chaque écart est corrigé (souvent un chemin relatif) et l'essai refait sur le point concerné.

- [ ] **Étape 8 : relecture indépendante, puis commit**

`hycolony-reviewer` : « panneaux, textures natives et titre de l'inventaire déménagent dans le pack de HyBlockUI ; les .ui de HyColony les citent par ../HyBlockUI/Native/ ; docs à jour ; essai en jeu de l'utilisateur : <résultat> ». Corriger, faire relire.

```bash
git status --short
git add blockui/src/main/resources plugin/src/main/resources/Common/UI/Custom/Pages/HyColony plugin/src/main/resources/Server/Languages/en-US/hycolony.lang plugin/src/main/resources/Server/Languages/fr-FR/hycolony.lang blockui/src/main/java/dev/hyblockui/api/PlayerPanels.java docs/native-ui-textures.md docs/research/plugin-b-api.md docs/TESTING.md
git diff --cached --stat
git commit -m "$(cat <<'EOF'
refactor(blockui): the player panels, native textures and inventory title move to HyBlockUI's pack

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
EOF
)"
```

Vérifier avant le commit que `git diff --cached --stat` ne contient ni `config.json` ni un fichier d'une autre session.

Le plan 4 (HyDomum et nettoyage) peut ensuite s'écrire.
