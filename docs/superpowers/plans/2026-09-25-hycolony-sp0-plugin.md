# HyColony SP0 — Partie B : Plugin Hytale — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** brancher le core (partie A) sur le serveur Hytale 0.6.8, et atteindre les 6 critères de réussite de la spec § 1 en jeu.

**Architecture :** le module `plugin` implémente les ports du core avec l'API Hytale et crée un `ColonyManager` par monde. Il traduit les événements ECS (pose, casse, utilisation de bloc, chargement d'entité) en appels au core. Aucune règle de jeu ne vit ici.

**Tech Stack :** Java 25, serveur Hytale 0.6.8 (`com.hypixel.hytale:Server`), plugin Gradle `com.azuredoom.hytale-tools`, asset pack JSON.

**Spec :** `docs/superpowers/specs/2026-09-25-hycolony-sp0-fondations-design.md`
**Prérequis :** la partie A (`2026-09-25-hycolony-sp0-core.md`) est terminée et `./gradlew build` est vert.
**Référence API :** `docs/research/hytale-api-spike.md` et les sources décompilées dans `build/vineflower/hytale-server/com/hypixel/hytale/`. En cas de doute sur une signature, **lire la classe décompilée** plutôt que deviner.

## Global Constraints

- Tout ce qui est spécifique à Hytale reste dans `plugin/`. Le core n'est pas modifié dans cette partie. Une exception est possible : si une signature de port ne convient vraiment pas, arrêter et le signaler plutôt que de contourner.
- API blocs : **uniquement l'API par sections** (`ChunkStore`, `BlockSection`, `BlockOperations`, `BlockHarvestUtils`). Jamais `World.getBlock`, `WorldChunk.setBlock`/`breakBlock` ni les autres méthodes `@Deprecated`.
- Le core n'est appelé **que depuis le thread du monde**. Les systèmes ECS et les événements de monde y sont déjà. Sinon, passer par `world.execute(...)`.
- Identifiants d'assets : on n'écrit jamais un id Hytale en dur dans le code Java, on passe par `IdMap` (`hycolony/id-map.json`).
- Rôle PNJ : `HyColony_Citizen`. Composant persistant : `"HyColonyCitizen"`. Capteur : `"HyColonyTarget"`.
- **Les tâches B1, B5 et B6 exigent les assets du jeu.** L'utilisateur doit avoir lancé `./gradlew setupHytaleDev` et validé l'authentification Hytale. Vérification : `find ~/.gradle -name "Assets.zip"` doit trouver un fichier. Sans ça, s'arrêter et demander.
- Vérification de chaque tâche : `./gradlew :plugin:compileJava`, puis les étapes en jeu indiquées. Le plugin n'a pas de tests unitaires (spec § 7).
- Commits : même format que la partie A.

## Review Focus

1. **Double-clic sur « Confirmer » ou serveur lent** : une seule colonie est créée. La logique vit dans le core, où `confirmFoundation` retire l'attente avant de créer. Vérifié en jeu à l'étape B6 (test 1).
2. **Joueur qui casse le bloc hôtel de ville avant de confirmer** : la fondation en attente est annulée et aucune colonie n'est créée. Traité en Task B5 (`TownHallBlockSystems` : casse avec fondation en attente → `cancelFoundation`).
3. **Monde déchargé ou arrêté pendant une fondation** : les colonies modifiées sont sauvegardées, et les fondations en attente ne sont jamais écrites. Traité en Task B4 (`RemoveWorldEvent` / `ShutdownEvent` → `saveAll`).
4. **Citoyen dont le chunk se décharge puis se recharge** : le corps est délié puis relié, jamais dupliqué. Traité en Task B2 (`CitizenBodyLifecycleSystem` UNLOAD/LOAD) et vérifié en B6 (test 3).
5. **Id d'asset renommé par une mise à jour Hytale** : rapport clair au démarrage et désactivation propre, sans NPE à la première pose. Traité en Task B1 (`IdMap.validate`) et B4 (garde `runtime.enabled()`).

---

## Structure des fichiers

```
plugin/src/main/java/dev/hycolony/plugin/
  HyColonyPlugin.java              point d'entrée : setup, enregistrements, cycle de vie
  HyColonyConfig.java              config.json (codec Hytale) -> ColonyConfig
  IdMap.java                       clés logiques -> ids d'assets Hytale + validation
  WorldRuntime.java                un ColonyManager + ses adaptateurs, par monde
  WorldRuntimes.java               registre des WorldRuntime par nom de monde
  ColonyTickSystem.java            TickingSystem : accumulateur -> 20 ticks core/s
  adapter/HytaleGameClock.java
  adapter/HytaleWorldQuery.java
  adapter/HytalePlayerDirectory.java
  adapter/HytaleNotifier.java
  adapter/HytaleCitizenBodies.java
  adapter/HytaleUiPort.java
  adapter/HytaleBlocks.java        retrait d'un bloc avec drop (annulation de fondation)
  npc/CitizenTag.java              composant persistant {colonyId, citizenId}
  npc/MoveTarget.java              composant transitoire (cible de déplacement)
  npc/HyColonyComponents.java      ComponentTypes enregistrés
  npc/BuilderSensorHyColonyTarget.java
  npc/SensorHyColonyTarget.java
  npc/CitizenBodyLifecycleSystem.java
  block/TownHallBlockSystems.java  PlaceBlockEvent / BreakBlockEvent / UseBlockEvent.Pre
  block/ProtectionSystems.java     protection des blocs dans les colonies
  ui/FoundColonyPage.java
  ui/TownHallPage.java
  command/HyColonyCommand.java     /hycolony info|rank|delete|selftest
plugin/src/main/resources/
  hycolony/id-map.json
  Server/Item/Items/HyColony/HyColony_TownHall.json
  Server/NPC/Roles/HyColony/HyColony_Citizen.json
  Server/Languages/en-US/hycolony.lang
  Server/Languages/fr-FR/hycolony.lang
  Common/UI/Custom/Pages/HyColony/FoundColony.ui
  Common/UI/Custom/Pages/HyColony/TownHall.ui
docs/UPGRADING.md, docs/TESTING.md, README.md
```

---

### Task B1 : asset pack (bloc hôtel de ville, i18n) et table d'identifiants

**Files :**
- Create : `plugin/src/main/resources/Server/Item/Items/HyColony/HyColony_TownHall.json`, `plugin/src/main/resources/Server/Languages/en-US/hycolony.lang`, `plugin/src/main/resources/Server/Languages/fr-FR/hycolony.lang`, `plugin/src/main/resources/hycolony/id-map.json`, `plugin/src/main/java/dev/hycolony/plugin/IdMap.java`

**Interfaces :**
- Produces : `final class IdMap` avec :
  - `static IdMap loadBundled()` ;
  - `String itemId(String key)`, `String blockId(String key)`, `String npcRole(String key)` ;
  - `List<String> validate()`, qui renvoie la liste des erreurs, vide si tout va bien ;
  - les clés `hut.townhall` (item et bloc) et `npc.citizen` (rôle).

- [ ] **Step 1 : trouver un modèle d'objet-bloc vanilla**

```bash
A=$(find ~/.gradle -name "Assets.zip" | head -1); echo "$A"
unzip -l "$A" | grep -E "Server/Item/Items/.*(Bench|Workbench|Chest).*\.json" | head
```

Choisir un objet-bloc simple et non interactif, un établi de préférence, et l'extraire :

```bash
unzip -p "$A" "<chemin trouvé>.json" > /tmp/template-item.json
```

- [ ] **Step 2 : créer l'objet-bloc hôtel de ville**

Copier le modèle dans `plugin/src/main/resources/Server/Item/Items/HyColony/HyColony_TownHall.json`, puis :
- garder la clé `"BlockType"` et son modèle (visuel provisoire, remplacé plus tard) ;
- **retirer toute interaction d'ouverture** (clés `Use` / `Interactions` du bloc) : c'est le plugin qui ouvre la fenêtre ;
- mettre le nom affiché sur la clé de traduction `hycolony.item.townhall.name` (même clé que dans le modèle, par exemple `TranslationProperties`/`Name`) ;
- vérifier que le bloc **se drop lui-même** quand on le casse (section `Gathering`/`Breaking` avec `ItemId: "HyColony_TownHall"`). L'annulation de fondation en dépend (Task B5).

L'id de l'objet et du bloc est le nom du fichier : `HyColony_TownHall`.

- [ ] **Step 3 : vérifier la syntaxe des paramètres dans les `.lang`**

```bash
unzip -p "$A" "Server/Languages/en-US/server.lang" | grep -m5 "{"
```

Noter le format des paramètres (par exemple `{name}`). `HytaleNotifier` (Task B4) nomme les paramètres `p0`, `p1`… Les valeurs ci-dessous utilisent `{p0}`. **Adapter les accolades si la syntaxe relevée est différente.**

- [ ] **Step 4 : écrire les fichiers de langue**

`plugin/src/main/resources/Server/Languages/en-US/hycolony.lang` (clés préfixées `hycolony.` par le nom du fichier) :

```properties
item.townhall.name = Town Hall
hut.noTownHall = You must place a Town Hall first.
hut.tooFar = This is too far from your Town Hall.
hut.townHallExists = This colony already has a Town Hall.
colony.alreadyOwner = You already own a colony in this world.
colony.tooClose = Too close to another colony.
colony.invalidName = A colony name must be 1 to {p0} characters.
colony.created = Colony {p0} founded!
permission.placeHuts = You are not allowed to place huts in {p0}.
permission.denied = You are not allowed to do that in {p0}.
ui.found.title = Found a colony
ui.found.name = Colony name
ui.found.confirm = Found
ui.found.cancel = Cancel
ui.townhall.owner = Owner: {p0}
ui.townhall.day = Day {p0}
ui.townhall.citizens = Citizens
ui.townhall.rename = Rename
status.idle = Idle
status.wandering = Wandering
status.absent = Away
cmd.noColony = There is no colony here.
cmd.info = {p0} (#{p1}) - owner {p2} - {p3} - day {p4} - {p5} citizens
cmd.rankSet = Rank updated.
cmd.rankFailed = Could not change rank.
cmd.deleted = Colony deleted.
selftest.ok = [OK] {p0}
selftest.ko = [KO] {p0}: {p1}
```

`plugin/src/main/resources/Server/Languages/fr-FR/hycolony.lang` :

```properties
item.townhall.name = Hôtel de ville
hut.noTownHall = Vous devez d'abord poser un hôtel de ville.
hut.tooFar = C'est trop loin de votre hôtel de ville.
hut.townHallExists = Cette colonie a déjà un hôtel de ville.
colony.alreadyOwner = Vous possédez déjà une colonie dans ce monde.
colony.tooClose = Trop près d'une autre colonie.
colony.invalidName = Le nom d'une colonie doit faire de 1 à {p0} caractères.
colony.created = La colonie {p0} est fondée !
permission.placeHuts = Vous n'avez pas le droit de poser des cabanes dans {p0}.
permission.denied = Vous n'avez pas le droit de faire ça dans {p0}.
ui.found.title = Fonder une colonie
ui.found.name = Nom de la colonie
ui.found.confirm = Fonder
ui.found.cancel = Annuler
ui.townhall.owner = Propriétaire : {p0}
ui.townhall.day = Jour {p0}
ui.townhall.citizens = Citoyens
ui.townhall.rename = Renommer
status.idle = Inactif
status.wandering = Se promène
status.absent = Absent
cmd.noColony = Il n'y a pas de colonie ici.
cmd.info = {p0} (#{p1}) - propriétaire {p2} - {p3} - jour {p4} - {p5} citoyens
cmd.rankSet = Rang mis à jour.
cmd.rankFailed = Impossible de changer le rang.
cmd.deleted = Colonie supprimée.
selftest.ok = [OK] {p0}
selftest.ko = [KO] {p0} : {p1}
```

- [ ] **Step 5 : écrire la table d'identifiants**

`plugin/src/main/resources/hycolony/id-map.json` :

```json
{
  "items":  { "hut.townhall": "HyColony_TownHall" },
  "blocks": { "hut.townhall": "HyColony_TownHall" },
  "npcRoles": { "npc.citizen": "HyColony_Citizen" }
}
```

- [ ] **Step 6 : écrire `IdMap`**

`plugin/src/main/java/dev/hycolony/plugin/IdMap.java` :

```java
package dev.hycolony.plugin;

import com.google.gson.Gson;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Logical keys -> Hytale asset ids. The only place asset ids live (spec § 4.3). */
public final class IdMap {
    private record Data(Map<String, String> items, Map<String, String> blocks, Map<String, String> npcRoles) {}

    private final Data data;

    private IdMap(Data data) {
        this.data = data;
    }

    public static IdMap loadBundled() {
        try (InputStream in = IdMap.class.getResourceAsStream("/hycolony/id-map.json")) {
            return new IdMap(new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Data.class));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read hycolony/id-map.json", e);
        }
    }

    public String itemId(String key) { return require(data.items(), key); }
    public String blockId(String key) { return require(data.blocks(), key); }
    public String npcRole(String key) { return require(data.npcRoles(), key); }

    private static String require(Map<String, String> map, String key) {
        String id = map.get(key);
        if (id == null) {
            throw new IllegalArgumentException("Unknown id-map key " + key);
        }
        return id;
    }

    /** Every mapped id must exist in the loaded assets. Returns human-readable errors. */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        data.items().forEach((key, id) -> {
            if (Item.getAssetMap().getAsset(id) == null) {
                errors.add("item " + key + " -> " + id);
            }
        });
        data.blocks().forEach((key, id) -> {
            if (BlockType.getAssetMap().getIndex(id) == Integer.MIN_VALUE) {
                errors.add("block " + key + " -> " + id);
            }
        });
        data.npcRoles().forEach((key, id) -> {
            if (!NPCPlugin.get().hasRoleName(id)) {
                errors.add("npc role " + key + " -> " + id);
            }
        });
        return errors;
    }
}
```

Si l'import de `Item` ne compile pas, trouver le bon paquetage avec `find build/vineflower -name Item.java | grep asset` et corriger l'import.

- [ ] **Step 7 : compiler**

Run : `./gradlew :plugin:compileJava`
Attendu : `BUILD SUCCESSFUL`.

- [ ] **Step 8 : commit**

```bash
git add plugin
git commit -m "feat(plugin): town hall block asset, translations and id map with validation

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task B2 : corps des citoyens (composants, capteur PNJ, rôle, adaptateur)

**Files :**
- Create : `plugin/src/main/java/dev/hycolony/plugin/npc/{CitizenTag,MoveTarget,HyColonyComponents,BuilderSensorHyColonyTarget,SensorHyColonyTarget,CitizenBodyLifecycleSystem}.java`, `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleCitizenBodies.java`, `plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json`

**Interfaces :**
- Consumes : les ports `CitizenBodies`, `BodyId` et `NavStatus` du core.
- Produces :
  - `CitizenTag(int colonyId, int citizenId)` et `MoveTarget` (champs `Vector3d target` et `boolean active`) ;
  - `HyColonyComponents` avec `static void register(ComponentRegistryProxy<EntityStore>)`, `citizenTag()` et `moveTarget()` ;
  - `HytaleCitizenBodies(World world, String roleName)` qui implémente `CitizenBodies`, avec en plus `BodyId track(Ref<EntityStore>)` et `Optional<BodyId> untrack(Ref<EntityStore>)` ;
  - `CitizenBodyLifecycleSystem(WorldRuntimes)`, un `RefSystem` qui appelle `onBodyLoaded` et `onBodyUnloaded`.

- [ ] **Step 1 : écrire les composants**

`npc/CitizenTag.java` :

```java
package dev.hycolony.plugin.npc;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Persistent tag linking an NPC to its core citizen. Saved with the entity. */
public final class CitizenTag implements Component<EntityStore> {
    public static final BuilderCodec<CitizenTag> CODEC = BuilderCodec.builder(CitizenTag.class, CitizenTag::new)
            .append(new KeyedCodec<>("ColonyId", Codec.INTEGER), (t, v) -> t.colonyId = v, t -> t.colonyId).add()
            .append(new KeyedCodec<>("CitizenId", Codec.INTEGER), (t, v) -> t.citizenId = v, t -> t.citizenId).add()
            .build();

    private int colonyId;
    private int citizenId;

    public CitizenTag() {}

    public CitizenTag(int colonyId, int citizenId) {
        this.colonyId = colonyId;
        this.citizenId = citizenId;
    }

    public int colonyId() { return colonyId; }
    public int citizenId() { return citizenId; }

    @Override
    public Component<EntityStore> clone() {
        return new CitizenTag(colonyId, citizenId);
    }
}
```

`npc/MoveTarget.java` :

```java
package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/** Transient: where the core wants the citizen to walk. Read by SensorHyColonyTarget. */
public final class MoveTarget implements Component<EntityStore> {
    public final Vector3d target = new Vector3d();
    public boolean active;

    @Override
    public Component<EntityStore> clone() {
        MoveTarget copy = new MoveTarget();
        copy.target.set(target);
        copy.active = active;
        return copy;
    }
}
```

`npc/HyColonyComponents.java` :

```java
package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class HyColonyComponents {
    private static ComponentType<EntityStore, CitizenTag> citizenTag;
    private static ComponentType<EntityStore, MoveTarget> moveTarget;

    private HyColonyComponents() {}

    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        citizenTag = registry.registerComponent(CitizenTag.class, "HyColonyCitizen", CitizenTag.CODEC);
        moveTarget = registry.registerComponent(MoveTarget.class, MoveTarget::new);
    }

    public static ComponentType<EntityStore, CitizenTag> citizenTag() { return citizenTag; }
    public static ComponentType<EntityStore, MoveTarget> moveTarget() { return moveTarget; }
}
```

Si `ComponentRegistryProxy` n'est pas dans `com.hypixel.hytale.component`, le chercher : `find build/vineflower -name ComponentRegistryProxy.java`.

- [ ] **Step 2 : écrire le capteur PNJ, sur le modèle de `SensorReadPosition` / `BuilderSensorReadPosition`**

`npc/BuilderSensorHyColonyTarget.java` :

```java
package dev.hycolony.plugin.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.Feature;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

/** JSON: { "Type": "HyColonyTarget" }. Matches while the colony core has a move target. */
public final class BuilderSensorHyColonyTarget extends BuilderSensorBase {
    @Nonnull
    @Override
    public String getShortDescription() {
        return "Position chosen by the HyColony colony core";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return getShortDescription();
    }

    @Nonnull
    public Sensor build(@Nonnull BuilderSupport builderSupport) {
        return new SensorHyColonyTarget(this);
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        this.provideFeature(Feature.Position);
        return this;
    }
}
```

`npc/SensorHyColonyTarget.java` :

```java
package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hypixel.hytale.server.npc.sensorinfo.PositionProvider;
import javax.annotation.Nonnull;

public final class SensorHyColonyTarget extends SensorBase {
    private final PositionProvider positionProvider = new PositionProvider();

    public SensorHyColonyTarget(@Nonnull BuilderSensorHyColonyTarget builder) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull ExecutionSupport support, double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, support, dt, store)) {
            positionProvider.clear();
            return false;
        }
        MoveTarget target = store.getComponent(ref, HyColonyComponents.moveTarget());
        if (target == null || !target.active) {
            positionProvider.clear();
            return false;
        }
        positionProvider.setTarget(target.target);
        return true;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return positionProvider;
    }
}
```

- [ ] **Step 3 : écrire le rôle PNJ à partir d'un rôle humanoïde vanilla**

```bash
unzip -l "$A" | grep -E "Server/NPC/Roles/.*(Kweebec|Villager|Human|Feran).*\.json" | head
unzip -p "$A" "<un rôle Generic humanoïde>.json" > /tmp/template-role.json
```

Créer `plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json` à partir du modèle :
- garder `Type` (`Generic`), `Appearance`, `MotionControllerList` et `InitialMotionController` du modèle ;
- mettre `"Invulnerable": true` ;
- supprimer toute hostilité, attaque, fuite, `DropList` et tout ce qui touche au spawn naturel ;
- ajouter `"HiddenUIComponents": [...]` pour masquer la barre de vie, **si** la clé existe en 0.6.8 (`grep -rn "HiddenUIComponents" build/vineflower | head -1`). Sinon, l'omettre ;
- remplacer `Instructions` par ce qui suit. La seconde instruction est **l'instruction d'attente du modèle**, recopiée telle quelle :

```json
"Instructions": [
  {
    "Sensor": { "Type": "HyColonyTarget" },
    "BodyMotion": { "Type": "Seek", "StopDistance": 0.6, "SlowDownDistance": 1.5 }
  },
  "<<< copier ici l'instruction idle/immobile de la role vanilla, sans wander ni cible >>>"
]
```

Remplacer la ligne entre chevrons par l'objet JSON de l'instruction d'attente du modèle. Le fichier final ne doit contenir aucun texte entre chevrons.

- [ ] **Step 4 : écrire l'adaptateur des corps**

`adapter/HytaleCitizenBodies.java` :

```java
package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.NavState;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.plugin.npc.CitizenTag;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.MoveTarget;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import org.joml.Vector3d;

/** CitizenBodies over Hytale NPCs. World thread only. */
public final class HytaleCitizenBodies implements CitizenBodies {
    private final World world;
    private final String roleName;
    private final Map<Long, Ref<EntityStore>> refs = new HashMap<>();
    private final Map<Ref<EntityStore>, Long> ids = new IdentityHashMap<>();
    private long nextId = 1;

    public HytaleCitizenBodies(World world, String roleName) {
        this.world = world;
        this.roleName = roleName;
    }

    private Store<EntityStore> store() {
        return world.getEntityStore().getStore();
    }

    public BodyId track(Ref<EntityStore> ref) {
        Long existing = ids.get(ref);
        if (existing != null) {
            return new BodyId(existing);
        }
        long id = nextId++;
        refs.put(id, ref);
        ids.put(ref, id);
        return new BodyId(id);
    }

    public Optional<BodyId> untrack(Ref<EntityStore> ref) {
        Long id = ids.remove(ref);
        if (id == null) {
            return Optional.empty();
        }
        refs.remove(id);
        return Optional.of(new BodyId(id));
    }

    private Ref<EntityStore> ref(BodyId body) {
        Ref<EntityStore> ref = refs.get(body.value());
        return ref != null && ref.isValid() ? ref : null;
    }

    @Override
    public Optional<BodyId> spawn(WorldKey key, BlockPos near, int colonyId, int citizenId, String displayName) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore>[] spawned = new Ref[1];
        SpawnTestResult result = NPCPlugin.get().spawnNPCWithColumnProbe(store(), roleName, null, world,
                near.x() + 1, near.z(), near.y(), new Rotation3f(),
                (npc, ref, st) -> {
                    st.addComponent(ref, HyColonyComponents.citizenTag(), new CitizenTag(colonyId, citizenId));
                    st.addComponent(ref, HyColonyComponents.moveTarget(), new MoveTarget());
                    DisplayNameSupport.setDisplayName(ref, displayName, st);
                    spawned[0] = ref;
                });
        if (result != SpawnTestResult.TEST_OK || spawned[0] == null) {
            return Optional.empty();
        }
        return Optional.of(track(spawned[0]));
    }

    @Override
    public boolean isAlive(BodyId body) {
        return ref(body) != null;
    }

    @Override
    public Optional<Vec3> position(BodyId body) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return Optional.empty();
        }
        TransformComponent t = store().getComponent(ref, TransformComponent.getComponentType());
        Vector3d p = t.getPosition();
        return Optional.of(new Vec3(p.x, p.y, p.z));
    }

    @Override
    public void moveTo(BodyId body, Vec3 target) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return;
        }
        MoveTarget mt = store().getComponent(ref, HyColonyComponents.moveTarget());
        if (mt == null) {
            mt = new MoveTarget();
            store().addComponent(ref, HyColonyComponents.moveTarget(), mt);
        }
        mt.target.set(target.x(), target.y(), target.z());
        mt.active = true;
    }

    @Override
    public NavStatus navStatus(BodyId body) {
        Ref<EntityStore> ref = ref(body);
        if (ref == null) {
            return NavStatus.FAILED;
        }
        MoveTarget mt = store().getComponent(ref, HyColonyComponents.moveTarget());
        if (mt == null || !mt.active) {
            return NavStatus.IDLE;
        }
        NPCEntity npc = store().getComponent(ref, NPCEntity.getComponentType());
        NavState state = npc.getRole().getActiveMotionController().getNavState();
        NavStatus status = switch (state) {
            case AT_GOAL -> NavStatus.ARRIVED;
            case BLOCKED -> NavStatus.BLOCKED;
            case ABORTED -> NavStatus.FAILED;
            default -> NavStatus.MOVING;
        };
        if (status != NavStatus.MOVING) {
            mt.active = false;
        }
        return status;
    }

    @Override
    public void setDisplayName(BodyId body, String name) {
        Ref<EntityStore> ref = ref(body);
        if (ref != null) {
            DisplayNameSupport.setDisplayName(ref, name, store());
        }
    }

    @Override
    public void despawn(BodyId body) {
        Ref<EntityStore> ref = ref(body);
        if (ref != null) {
            untrack(ref);
            store().removeEntity(ref, RemoveReason.REMOVE);
        }
    }
}
```

Points à vérifier dans les sources décompilées si la compilation échoue :
- le paquetage de `NavState` (`find build/vineflower -name NavState.java`) ;
- `Ref.isValid()` ;
- `new Rotation3f()` comme `Rotation3fc` ;
- la signature exacte de `spawnNPCWithColumnProbe` avec `postSpawn`, dans `NPCPlugin.java` vers la ligne 1241 ;
- `TransformComponent.getComponentType()`.

Corriger les imports et appels en conséquence, sans changer le comportement.

- [ ] **Step 5 : écrire le système de cycle de vie des corps**

`npc/CitizenBodyLifecycleSystem.java` :

```java
package dev.hycolony.plugin.npc;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import javax.annotation.Nonnull;

/** Binds citizen NPCs loaded from chunks to the core; unbinds on unload. */
public final class CitizenBodyLifecycleSystem extends RefSystem<EntityStore> {
    private final WorldRuntimes runtimes;

    public CitizenBodyLifecycleSystem(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return HyColonyComponents.citizenTag();
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        if (reason != AddReason.LOAD) {
            return; // freshly spawned bodies are bound by HytaleCitizenBodies.spawn
        }
        WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
        if (rt == null || !rt.enabled()) {
            return;
        }
        CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
        rt.manager().onBodyLoaded(rt.bodies().track(ref), tag.colonyId(), tag.citizenId());
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
        if (rt == null) {
            return;
        }
        CitizenTag tag = store.getComponent(ref, HyColonyComponents.citizenTag());
        rt.bodies().untrack(ref).ifPresent(id -> rt.manager().onBodyUnloaded(id, tag.colonyId()));
    }
}
```

Ce fichier dépend de `WorldRuntime` et `WorldRuntimes` (Task B4). **La compilation de cette tâche se fait donc à la fin de B4.** L'ordre B2 → B4 est volontaire : on pourrait inverser B2 et B4, mais B4 a besoin de `HytaleCitizenBodies`.

Il faut aussi vérifier que `ComponentType` implémente `Query` (`grep -n "class ComponentType" build/vineflower/.../component/ComponentType.java`). Si ce n'est pas le cas, remplacer par `Query.and(HyColonyComponents.citizenTag())` ou l'équivalent trouvé dans les systèmes vanilla (`grep -rn "getQuery()" build/vineflower/.../builtin/mounts`).

- [ ] **Step 6 : commit (compilation validée en B4)**

```bash
git add plugin
git commit -m "feat(plugin): citizen NPC components, target sensor, role and body adapter

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task B3 : UI (fenêtres « Fonder une colonie » et « Hôtel de ville »)

**Files :**
- Create : `plugin/src/main/java/dev/hycolony/plugin/ui/{FoundColonyPage,TownHallPage}.java`, `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleUiPort.java`, `plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/{FoundColony,TownHall}.ui`

**Interfaces :**
- Consumes : `UiPort`, `FoundColonyView`, `TownHallView`, et `ColonyManager` (`confirmFoundation`, `cancelFoundation`, `rename`).
- Produces : `HytaleUiPort(World, Supplier<ColonyManager>, HytaleBlocks)`, qui implémente `UiPort`.

- [ ] **Step 1 : prendre un modèle de page `.ui` vanilla avec un champ texte et des boutons**

```bash
unzip -l "$A" | grep -E "Common/UI/Custom/Pages/.*Teleporter.*\.ui"
unzip -p "$A" "<chemin>/TeleporterSettings*.ui" > /tmp/template.ui
```

Lire aussi `build/vineflower/hytale-server/com/hypixel/hytale/builtin/adventure/teleporter/page/TeleporterSettingsPage.java`, qui fait le lien entre ce `.ui` et le Java.

Confirmer que le chemin passé à `append(...)` est relatif à `Common/UI/Custom/` (spec § 11, point 1). **Si le vanilla utilise un autre dossier racine, adapter les chemins ci-dessous.**

- [ ] **Step 2 : écrire `FoundColony.ui`**

À partir du modèle, créer `Common/UI/Custom/Pages/HyColony/FoundColony.ui` avec exactement ces éléments et sélecteurs :
- un titre qui affiche `hycolony.ui.found.title` ;
- un champ texte `#NameInput` ;
- un bouton `#ConfirmButton` (`hycolony.ui.found.confirm`) ;
- un bouton `#CancelButton` (`hycolony.ui.found.cancel`).

Reprendre la syntaxe, les styles et la mise en page du modèle. Seuls les éléments et les ids changent.

- [ ] **Step 3 : écrire `TownHall.ui`**

Même méthode. Éléments :
- `#ColonyName` (texte) ;
- `#Owner` (texte) ;
- `#Day` (texte) ;
- `#RenameInput` (champ texte) ;
- `#RenameButton` (bouton), avec `#RenameInput` ;
- `#CitizenList` (conteneur vertical) dans lequel le Java insère une ligne par citoyen avec `appendInline`. Le gabarit de ligne est un élément texte `#CitizenRowTemplate`, ou l'équivalent du modèle pour les listes.

- [ ] **Step 4 : écrire `FoundColonyPage`**

`ui/FoundColonyPage.java` :

```java
package dev.hycolony.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ui.FoundColonyView;
import javax.annotation.Nonnull;

public final class FoundColonyPage extends InteractiveCustomUIPage<FoundColonyPage.Data> {
    public static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("@Name", Codec.STRING), (d, v) -> d.name = v, d -> d.name).add()
                .build();
        String action;
        String name;
    }

    public interface Handler {
        void confirm(String name);
        void cancel();
    }

    private final FoundColonyView view;
    private final Handler handler;
    private boolean answered;

    public FoundColonyPage(PlayerRef player, FoundColonyView view, Handler handler) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.view = view;
        this.handler = handler;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/FoundColony.ui");
        ui.set("#NameInput.Value", view.suggestedName());
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton",
                new EventData().append("Action", "confirm").append("@Name", "#NameInput.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton",
                new EventData().append("Action", "cancel"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        answered = true;
        if ("confirm".equals(data.action)) {
            handler.confirm(data.name);
        } else {
            handler.cancel();
        }
        sendUpdate(new UICommandBuilder(), false); // required: otherwise the client stays in a loading state
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!answered) {
            handler.cancel(); // closing the window = cancel (spec § 4.2)
        }
    }
}
```

Si un nom invalide est saisi, le core garde la fondation en attente et envoie un message. La fenêtre reste ouverte, et le joueur corrige puis reclique.

Vérifier dans les sources décompilées :
- les paquetages de `EventData`, `UICommandBuilder`, `UIEventBuilder` et `CustomUIEventBindingType` (dans `server/core/ui/…`) ;
- la signature de `onDismiss` et de `sendUpdate(UICommandBuilder, boolean)` (dans `CustomUIPage.java` et `InteractiveCustomUIPage.java`) ;
- la propriété `.Value` d'un champ texte (dans `TeleporterSettingsPage.java`).

- [ ] **Step 5 : écrire `TownHallPage`**

`ui/TownHallPage.java` :

```java
package dev.hycolony.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ui.CitizenRow;
import dev.hycolony.core.colony.ui.TownHallView;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

public final class TownHallPage extends InteractiveCustomUIPage<TownHallPage.Data> {
    public static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("@Name", Codec.STRING), (d, v) -> d.name = v, d -> d.name).add()
                .build();
        String name;
    }

    private final TownHallView view;
    private final Consumer<String> rename;

    public TownHallPage(PlayerRef player, TownHallView view, Consumer<String> rename) {
        super(player, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.view = view;
        this.rename = rename;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/TownHall.ui");
        ui.set("#ColonyName.Text", view.colonyName());
        ui.set("#Owner.Text", Message.translation("hycolony.ui.townhall.owner").param("p0", view.ownerName()));
        ui.set("#Day.Text", Message.translation("hycolony.ui.townhall.day").param("p0", String.valueOf(view.day())));
        for (CitizenRow row : view.citizens()) {
            ui.appendInline("#CitizenList",
                    "Label { Text: \"" + escape(row.name()) + " - \"; }"); // adapt to the .ui row syntax of the template
            // The status translation is appended as a second label for i18n:
            ui.set("#CitizenList[" + view.citizens().indexOf(row) + "].Text",
                    Message.translation("hycolony.status." + row.status()));
        }
        if (view.canRename()) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#RenameButton",
                    new EventData().append("@Name", "#RenameInput.Value"));
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Data data) {
        rename.accept(data.name); // core re-shows an updated TownHallView on success
        sendUpdate(new UICommandBuilder(), false);
    }
}
```

**Remarque pour l'implémenteur :** le rendu de la liste (`appendInline` + `set` indexé) doit suivre **la syntaxe de liste du modèle vanilla** relevée au Step 1. Le code ci-dessus fixe le contrat : une ligne par `CitizenRow`, avec le nom et le statut traduit via `hycolony.status.<status>`. La syntaxe exacte des sélecteurs vient du modèle. Si le modèle utilise un gabarit de ligne (`append("#CitizenList", "Pages/HyColony/CitizenRow.ui")` puis `set("#CitizenList[i] #Name.Text", …)`), créer `CitizenRow.ui` et utiliser cette forme.

- [ ] **Step 6 : écrire `HytaleUiPort` et `HytaleBlocks`**

`adapter/HytaleBlocks.java` (utilisé par l'annulation de fondation, sur le thread du monde) :

```java
package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.BlockPos;
import org.joml.Vector3i;

/** Section-API block helpers (no deprecated World/WorldChunk calls). */
public final class HytaleBlocks {
    private final World world;

    public HytaleBlocks(World world) {
        this.world = world;
    }

    /** Removes the block at pos and drops {@code dropItemId} x1, like a player break. */
    public void removeWithDrop(BlockPos pos, String dropItemId) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> section = cs.getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        if (section == null) {
            return;
        }
        BlockSection blocks = cs.getStore().getComponent(section, BlockSection.getComponentType());
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(pos.x(), pos.y(), pos.z()));
        BlockHarvestUtils.naturallyRemoveBlock(new Vector3i(pos.x(), pos.y(), pos.z()), type,
                blocks.getFiller(pos.x(), pos.y(), pos.z()), 1, dropItemId, null, 0, section,
                world.getEntityStore().getStore(), cs.getStore());
    }
}
```

Vérifier la signature de `naturallyRemoveBlock` dans `server/core/modules/interaction/BlockHarvestUtils.java` et le paquetage de `BlockSection` et `ChunkStore` (`find build/vineflower -name BlockSection.java -o -name ChunkStore.java`).

`adapter/HytaleUiPort.java` :

```java
package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.Page;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.colony.ui.UiPort;
import dev.hycolony.plugin.ui.FoundColonyPage;
import dev.hycolony.plugin.ui.TownHallPage;
import java.util.UUID;
import java.util.function.Supplier;

public final class HytaleUiPort implements UiPort {
    private final World world;
    private final Supplier<ColonyManager> manager;
    private final HytaleBlocks blocks;
    private final String townHallItemId;

    public HytaleUiPort(World world, Supplier<ColonyManager> manager, HytaleBlocks blocks, String townHallItemId) {
        this.world = world;
        this.manager = manager;
        this.blocks = blocks;
        this.townHallItemId = townHallItemId;
    }

    @Override
    public void showFoundColony(UUID player, FoundColonyView view) {
        open(player, pr -> new FoundColonyPage(pr, view, new FoundColonyPage.Handler() {
            @Override public void confirm(String name) {
                manager.get().confirmFoundation(player, name);
            }

            @Override public void cancel() {
                manager.get().cancelFoundation(player).ifPresent(pos -> blocks.removeWithDrop(pos, townHallItemId));
            }
        }));
    }

    @Override
    public void showTownHall(UUID player, TownHallView view) {
        open(player, pr -> new TownHallPage(pr, view, name -> manager.get().rename(player, view.colonyId(), name)));
    }

    @Override
    public void close(UUID player) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr == null) {
            return;
        }
        Ref<EntityStore> ref = pr.getReference();
        Store<EntityStore> store = ref.getStore();
        store.getComponent(ref, Player.getComponentType()).getPageManager().setPage(ref, store, Page.None);
    }

    private void open(UUID player, java.util.function.Function<PlayerRef, com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage> page) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr == null) {
            return;
        }
        Ref<EntityStore> ref = pr.getReference();
        Store<EntityStore> store = ref.getStore();
        store.getComponent(ref, Player.getComponentType()).getPageManager().openCustomPage(ref, store, page.apply(pr));
    }
}
```

Vérifier le paquetage de `Player`, `Page` et `PageManager.setPage`/`openCustomPage` (dans `entities/Player.java` et `pages/PageManager.java`).

- [ ] **Step 7 : commit (compilation validée en B4)**

```bash
git add plugin
git commit -m "feat(plugin): found-colony and town hall pages over core view models

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task B4 : runtime par monde, tick 20 Hz, adaptateurs restants, cycle de vie, sauvegarde

**Files :**
- Create : `plugin/src/main/java/dev/hycolony/plugin/{HyColonyConfig,WorldRuntime,WorldRuntimes,ColonyTickSystem}.java`, `plugin/src/main/java/dev/hycolony/plugin/adapter/{HytaleGameClock,HytaleWorldQuery,HytalePlayerDirectory,HytaleNotifier}.java`
- Modify : `plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java`

**Interfaces :**
- Consumes : tout le core, `IdMap` (B1), `HytaleCitizenBodies` et `CitizenBodyLifecycleSystem` (B2), `HytaleUiPort` et `HytaleBlocks` (B3).
- Produces :
  - `WorldRuntime` avec `manager()`, `bodies()`, `clock()`, `blocks()`, `world()`, `enabled()` et `void tickCore()` ;
  - `WorldRuntimes` avec `WorldRuntime of(World)`, `void create(World)`, `void remove(World)`, `Collection<WorldRuntime> all()` et `setEnabled(boolean)` ;
  - `HyColonyConfig.toCore()`.

- [ ] **Step 1 : écrire la config**

`HyColonyConfig.java` :

```java
package dev.hycolony.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;

/** mods/<group>_HyColony/config.json. Ranges clamped like MineColonies' config. */
public final class HyColonyConfig {
    public static final BuilderCodec<HyColonyConfig> CODEC = BuilderCodec.builder(HyColonyConfig.class, HyColonyConfig::new)
            .append(new KeyedCodec<>("InitialCitizenAmount", Codec.INTEGER), (c, v) -> c.initialCitizenAmount = v, c -> c.initialCitizenAmount).add()
            .append(new KeyedCodec<>("MaxCitizenPerColony", Codec.INTEGER), (c, v) -> c.maxCitizenPerColony = v, c -> c.maxCitizenPerColony).add()
            .append(new KeyedCodec<>("InitialColonySize", Codec.INTEGER), (c, v) -> c.initialColonySize = v, c -> c.initialColonySize).add()
            .append(new KeyedCodec<>("MinColonyDistance", Codec.INTEGER), (c, v) -> c.minColonyDistance = v, c -> c.minColonyDistance).add()
            .append(new KeyedCodec<>("MaxColonySize", Codec.INTEGER), (c, v) -> c.maxColonySize = v, c -> c.maxColonySize).add()
            .append(new KeyedCodec<>("EnableColonyProtection", Codec.BOOLEAN), (c, v) -> c.enableColonyProtection = v, c -> c.enableColonyProtection).add()
            .append(new KeyedCodec<>("AutosaveIntervalMinutes", Codec.INTEGER), (c, v) -> c.autosaveIntervalMinutes = v, c -> c.autosaveIntervalMinutes).add()
            .build();

    private int initialCitizenAmount = 4;
    private int maxCitizenPerColony = 250;
    private int initialColonySize = 4;
    private int minColonyDistance = 8;
    private int maxColonySize = 20;
    private boolean enableColonyProtection = true;
    private int autosaveIntervalMinutes = 5;

    public ColonyConfig toCore() {
        return new ColonyConfig(
                clamp(initialCitizenAmount, 1, 10),
                clamp(maxCitizenPerColony, 25, 500),
                clamp(initialColonySize, 1, 15),
                clamp(minColonyDistance, 1, 200),
                clamp(maxColonySize, 1, 250),
                enableColonyProtection,
                clamp(autosaveIntervalMinutes, 1, 60));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
```

- [ ] **Step 2 : écrire les adaptateurs restants**

`adapter/HytaleGameClock.java` :

```java
package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.kernel.port.GameClock;

/** Core tick counter (advanced by ColonyTickSystem) + Hytale day/night. */
public final class HytaleGameClock implements GameClock {
    /** Daytime window in game hours. Hytale day = 60% of 24h; verify at dawn/dusk in game (docs/TESTING.md). */
    static final int DAY_START_HOUR = 6, NIGHT_START_HOUR = 20;

    private final World world;
    private long tick;

    public HytaleGameClock(World world) {
        this.world = world;
    }

    public void advance() {
        tick++;
    }

    @Override
    public long currentTick() {
        return tick;
    }

    @Override
    public boolean isDaytime() {
        WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        int hour = time.getGameDateTime().getHour();
        return hour >= DAY_START_HOUR && hour < NIGHT_START_HOUR;
    }
}
```

Vérifier le paquetage de `WorldTimeResource` (`find build/vineflower -name WorldTimeResource.java`).

`adapter/HytaleWorldQuery.java` :

```java
package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkUtil;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldQuery;

public final class HytaleWorldQuery implements WorldQuery {
    private final World world;

    public HytaleWorldQuery(World world) {
        this.world = world;
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x(), pos.z())) != null;
    }
}
```

Vérifier le paquetage de `ChunkUtil` (`find build/vineflower -name ChunkUtil.java`).

`adapter/HytalePlayerDirectory.java` :

```java
package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.joml.Vector3d;

public final class HytalePlayerDirectory implements PlayerDirectory {
    private final World world;

    public HytalePlayerDirectory(World world) {
        this.world = world;
    }

    @Override
    public boolean isOnline(UUID player) {
        return Universe.get().getPlayer(player) != null;
    }

    @Override
    public Optional<BlockPos> position(UUID player) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr == null || !pr.getWorldUuid().equals(world.getWorldConfig().getUuid())) {
            return Optional.empty();
        }
        Ref<EntityStore> ref = pr.getReference();
        TransformComponent t = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
        Vector3d p = t.getPosition();
        return Optional.of(new BlockPos((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z)));
    }

    @Override
    public Collection<UUID> onlineIn(WorldKey key) {
        return world.getPlayerRefs().stream().map(PlayerRef::getUuid).toList();
    }
}
```

Vérifier comment obtenir l'UUID d'un monde (`grep -n "UUID" build/vineflower/.../universe/world/World.java | head`). S'il existe `world.getUuid()`, l'utiliser à la place de `getWorldConfig().getUuid()`.

`adapter/HytaleNotifier.java` :

```java
package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.Notifier;
import java.util.UUID;

public final class HytaleNotifier implements Notifier {
    /** Core Msg -> Hytale Message; params become p0, p1, ... (see .lang files). */
    public static Message toMessage(Msg msg) {
        Message m = Message.translation(msg.key());
        for (int i = 0; i < msg.params().size(); i++) {
            m = m.param("p" + i, msg.params().get(i));
        }
        return m;
    }

    @Override
    public void send(UUID player, Msg message) {
        PlayerRef pr = Universe.get().getPlayer(player);
        if (pr != null) {
            pr.sendMessage(toMessage(message));
        }
    }
}
```

- [ ] **Step 3 : écrire `WorldRuntime` et `WorldRuntimes`**

`WorldRuntime.java` :

```java
package dev.hycolony.plugin;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.plugin.adapter.HytaleBlocks;
import dev.hycolony.plugin.adapter.HytaleCitizenBodies;
import dev.hycolony.plugin.adapter.HytaleGameClock;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.adapter.HytalePlayerDirectory;
import dev.hycolony.plugin.adapter.HytaleUiPort;
import dev.hycolony.plugin.adapter.HytaleWorldQuery;
import java.util.Random;

/** One ColonyManager and its adapters for one Hytale world. World thread only. */
public final class WorldRuntime {
    private final World world;
    private final HytaleGameClock clock;
    private final HytaleCitizenBodies bodies;
    private final HytaleBlocks blocks;
    private final ColonyManager manager;
    private final long autosaveTicks;
    private boolean enabled = true;

    WorldRuntime(World world, ColonyConfig config, IdMap ids, CitizenNames names) {
        this.world = world;
        this.clock = new HytaleGameClock(world);
        this.bodies = new HytaleCitizenBodies(world, ids.npcRole("npc.citizen"));
        this.blocks = new HytaleBlocks(world);
        ColonyManager[] self = new ColonyManager[1];
        ColonyContext ctx = new ColonyContext(new WorldKey(world.getName()), config, clock, bodies,
                new HytaleWorldQuery(world), new HytaleNotifier(),
                new HytaleUiPort(world, () -> self[0], blocks, ids.itemId("hut.townhall")),
                new HytalePlayerDirectory(world), BuildingTypes.defaults(), names, new Random(), new EventBus());
        this.manager = new ColonyManager(ctx);
        self[0] = manager;
        manager.setStorage(new FileColonyStorage(world.getSavePath().resolve("hycolony")), MigrationChain.sp0());
        manager.loadAll();
        this.autosaveTicks = config.autosaveIntervalMinutes() * 60L * 20L;
    }

    /** One core tick (1/20 s). */
    public void tickCore() {
        if (!enabled) {
            return;
        }
        clock.advance();
        manager.tick();
        if (clock.currentTick() % autosaveTicks == 0) {
            manager.saveDirty();
        }
    }

    public World world() { return world; }
    public ColonyManager manager() { return manager; }
    public HytaleCitizenBodies bodies() { return bodies; }
    public HytaleGameClock clock() { return clock; }
    public HytaleBlocks blocks() { return blocks; }
    public boolean enabled() { return enabled; }
    void setEnabled(boolean enabled) { this.enabled = enabled; }
}
```

L'autosave écrit sur le thread du monde. Il reste rare (toutes les 5 minutes) et les fichiers sont petits : c'est acceptable pour le sous-projet 0. Si le profilage montre un pic, déplacer `storage.save` sur un exécuteur, en gardant la sérialisation sur le thread du monde.

`WorldRuntimes.java` :

```java
package dev.hycolony.plugin;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.kernel.config.ColonyConfig;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry of per-world runtimes (map is concurrent: worlds live on different threads). */
public final class WorldRuntimes {
    private final Map<String, WorldRuntime> byWorld = new ConcurrentHashMap<>();
    private final ColonyConfig config;
    private final IdMap ids;
    private final CitizenNames names = CitizenNames.loadDefault();
    private volatile boolean enabled = true;

    public WorldRuntimes(ColonyConfig config, IdMap ids) {
        this.config = config;
        this.ids = ids;
    }

    public WorldRuntime of(World world) {
        return world == null ? null : byWorld.get(world.getName());
    }

    public void create(World world) {
        WorldRuntime rt = new WorldRuntime(world, config, ids, names);
        rt.setEnabled(enabled);
        byWorld.put(world.getName(), rt);
    }

    public void remove(World world) {
        WorldRuntime rt = byWorld.remove(world.getName());
        if (rt != null) {
            rt.manager().saveAll();
        }
    }

    public Collection<WorldRuntime> all() {
        return byWorld.values();
    }

    /** Disabled = nothing ticks and nothing is written (vital asset id missing). */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        byWorld.values().forEach(rt -> rt.setEnabled(enabled));
    }
}
```

- [ ] **Step 4 : écrire le système de tick avec un accumulateur**

`ColonyTickSystem.java` :

```java
package dev.hycolony.plugin;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Drives the core at exactly 20 ticks/s whatever the server tick rate. DelayedSystem is not used on
 * purpose: it resets its timer instead of carrying the remainder, which would drift.
 */
public final class ColonyTickSystem extends TickingSystem<EntityStore> {
    private static final float CORE_TICK_SECONDS = 0.05f;
    /** After a long stall, drop the backlog instead of fast-forwarding (MineColonies loses ticks too). */
    private static final int MAX_CATCH_UP = 10;

    private final WorldRuntimes runtimes;
    private final Map<String, Float> accumulators = new ConcurrentHashMap<>();

    public ColonyTickSystem(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
        if (rt == null) {
            return;
        }
        String key = rt.world().getName();
        float acc = accumulators.getOrDefault(key, 0f) + dt;
        int steps = 0;
        while (acc >= CORE_TICK_SECONDS && steps < MAX_CATCH_UP) {
            acc -= CORE_TICK_SECONDS;
            rt.tickCore();
            steps++;
        }
        if (steps == MAX_CATCH_UP) {
            acc = 0f;
        }
        accumulators.put(key, acc);
    }
}
```

- [ ] **Step 5 : brancher le cycle de vie dans `HyColonyPlugin`**

Remplacer le contenu de `HyColonyPlugin.java` :

```java
package dev.hycolony.plugin;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.hycolony.plugin.npc.BuilderSensorHyColonyTarget;
import dev.hycolony.plugin.npc.CitizenBodyLifecycleSystem;
import dev.hycolony.plugin.npc.HyColonyComponents;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public final class HyColonyPlugin extends JavaPlugin {
    private final Config<HyColonyConfig> config;
    private final IdMap ids = IdMap.loadBundled();
    private WorldRuntimes runtimes;

    public HyColonyPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        this.config = withConfig("config", HyColonyConfig.CODEC);
    }

    @Override
    protected void setup() {
        config.save();
        runtimes = new WorldRuntimes(config.get().toCore(), ids);

        HyColonyComponents.register(getEntityStoreRegistry());
        NPCPlugin.get().registerCoreComponentType("HyColonyTarget", BuilderSensorHyColonyTarget::new);

        getEntityStoreRegistry().registerSystem(new ColonyTickSystem(runtimes));
        getEntityStoreRegistry().registerSystem(new CitizenBodyLifecycleSystem(runtimes));

        getEventRegistry().registerGlobal(StartWorldEvent.class, e -> e.getWorld().execute(() -> runtimes.create(e.getWorld())));
        getEventRegistry().registerGlobal(RemoveWorldEvent.class, e -> runtimes.remove(e.getWorld()));
        getEventRegistry().register(ShutdownEvent.class, e -> runtimes.all().forEach(rt -> rt.manager().saveAll()));
        getEventRegistry().register(PlayerDisconnectEvent.class, e -> {
            var uuid = e.getPlayerRef().getUuid();
            runtimes.all().forEach(rt -> rt.world().execute(() -> rt.manager().onPlayerLeft(uuid)));
        });
        getEventRegistry().register(LoadedAssetsEvent.class, BlockType.class, e -> validateIds());

        getLogger().at(Level.INFO).log("HyColony setup complete");
    }

    private void validateIds() {
        List<String> errors = ids.validate();
        if (errors.isEmpty()) {
            runtimes.setEnabled(true);
            return;
        }
        for (String error : errors) {
            getLogger().at(Level.SEVERE).log("HyColony: missing asset id %s", error);
        }
        getLogger().at(Level.SEVERE).log("HyColony disabled: vital asset ids are missing (see above). Saves are untouched.");
        runtimes.setEnabled(false);
    }

    public WorldRuntimes runtimes() {
        return runtimes;
    }
}
```

Si `register(Class, Consumer)` et `register(Class, key, Consumer)` n'ont pas ces formes, lire `EventRegistry` (`find build/vineflower -name EventRegistry.java`) et utiliser l'équivalent. Même chose pour `LoadedAssetsEvent` avec clé de type d'asset : l'exemple vanilla est `BlockSetModule.java:44`.

- [ ] **Step 6 : compiler tout le plugin (y compris B2 et B3)**

Run : `./gradlew :plugin:compileJava`
Attendu : `BUILD SUCCESSFUL`. Pour chaque erreur de signature, lire la classe décompilée et corriger l'appel **sans changer le comportement**.

- [ ] **Step 7 : premier lancement**

Run : `./gradlew :plugin:runServer`
Attendu dans les logs : `HyColony setup complete`, et **aucune** ligne `missing asset id`. Si des ids manquent, corriger les JSON de B1 et B2 (noms de fichiers et dossiers).

- [ ] **Step 8 : commit**

```bash
git add plugin
git commit -m "feat(plugin): per-world runtime, 20 Hz core tick, adapters, lifecycle and autosave

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task B5 : événements de blocs (hôtel de ville, protection)

**Files :**
- Create : `plugin/src/main/java/dev/hycolony/plugin/block/{TownHallBlockSystems,ProtectionSystems}.java`
- Modify : `HyColonyPlugin.setup()` (enregistrer les systèmes)

**Interfaces :**
- Consumes : `ColonyManager.checkHutPlacement`, `beginFoundation`, `placeHut`, `onHutRemoved`, `cancelFoundation`, `openTownHall`, `isAllowed`, `protectionEnabled`, ainsi que `HytaleNotifier.toMessage` et `IdMap`.
- Produces : les systèmes ECS enregistrés dans `setup()`.

- [ ] **Step 1 : écrire les systèmes de l'hôtel de ville**

`block/TownHallBlockSystems.java` :

```java
package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Action;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.HutPlacement;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Player-caused town hall place / break / use. Queries PlayerRef so only players trigger these. */
public final class TownHallBlockSystems {
    private TownHallBlockSystems() {}

    static BlockPos pos(Vector3i v) {
        return new BlockPos(v.x, v.y, v.z);
    }

    static PlayerRef player(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        return store.getComponent(ref, PlayerRef.getComponentType());
    }

    public static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final WorldRuntimes runtimes;
        private final String hutItemId;

        public Place(WorldRuntimes runtimes, IdMap ids) {
            super(PlaceBlockEvent.class);
            this.runtimes = runtimes;
            this.hutItemId = ids.itemId("hut.townhall");
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull PlaceBlockEvent event) {
            if (event.getItemInHand() == null || !hutItemId.equals(event.getItemInHand().getItemId())) {
                return;
            }
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            PlayerRef player = player(index, chunk, store);
            if (rt == null || !rt.enabled() || player == null) {
                event.setCancelled(true);
                return;
            }
            ColonyManager m = rt.manager();
            BlockPos pos = pos(event.getTargetBlock());
            HutPlacement result = m.checkHutPlacement(player.getUuid(), pos, BuildingTypes.TOWN_HALL.id());
            switch (result) {
                case HutPlacement.Denied denied -> {
                    event.setCancelled(true);
                    player.sendMessage(HytaleNotifier.toMessage(denied.reason()));
                }
                case HutPlacement.FoundNewColony f ->
                        m.beginFoundation(player.getUuid(), player.getUsername(), pos, event.getRotation().index());
                case HutPlacement.Allowed allowed ->
                        m.placeHut(allowed.colony(), BuildingTypes.TOWN_HALL.id(), pos, event.getRotation().index());
            }
        }
    }

    public static final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final WorldRuntimes runtimes;
        private final String hutBlockId;

        public Break(WorldRuntimes runtimes, IdMap ids) {
            super(BreakBlockEvent.class);
            this.runtimes = runtimes;
            this.hutBlockId = ids.blockId("hut.townhall");
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull BreakBlockEvent event) {
            if (!hutBlockId.equals(event.getBlockType().getId())) {
                return;
            }
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            PlayerRef player = player(index, chunk, store);
            if (rt == null || !rt.enabled() || player == null) {
                return;
            }
            ColonyManager m = rt.manager();
            BlockPos pos = pos(event.getTargetBlock());
            if (m.cancelFoundation(player.getUuid()).isPresent()) {
                return; // broke its own pending town hall: foundation cancelled, normal drop
            }
            if (m.colonyAt(pos).isPresent() && !m.isAllowed(player.getUuid(), pos, Action.BREAK_HUTS)) {
                event.setCancelled(true);
                player.sendMessage(HytaleNotifier.toMessage(Msg.of("hycolony.permission.denied", m.colonyAt(pos).get().name())));
                return;
            }
            m.onHutRemoved(pos);
        }
    }

    public static final class Use extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
        private final WorldRuntimes runtimes;
        private final String hutBlockId;

        public Use(WorldRuntimes runtimes, IdMap ids) {
            super(UseBlockEvent.Pre.class);
            this.runtimes = runtimes;
            this.hutBlockId = ids.blockId("hut.townhall");
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull UseBlockEvent.Pre event) {
            if (!hutBlockId.equals(event.getBlockType().getId())) {
                return;
            }
            WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
            PlayerRef player = player(index, chunk, store);
            if (rt == null || !rt.enabled() || player == null) {
                return;
            }
            event.setCancelled(true);
            rt.manager().openTownHall(player.getUuid(), pos(event.getTargetBlock()));
        }
    }
}
```

Vérifier :
- que `ComponentType` sert bien de `Query`. Sinon, utiliser la forme de `TriggerVolumeBlockEventSystems.java` ;
- que `RotationTuple.index()` existe (dans `PlaceBlockEvent`, `getRotation()` renvoie `RotationTuple`) ;
- que `org.joml.Vector3i` expose bien les champs `x`, `y` et `z`.

- [ ] **Step 2 : écrire les systèmes de protection**

`block/ProtectionSystems.java` :

```java
package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.Action;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import javax.annotation.Nonnull;

/** PLACE_BLOCKS / BREAK_BLOCKS inside colonies (spec § 3.2). Hut blocks are handled by TownHallBlockSystems. */
public final class ProtectionSystems {
    private ProtectionSystems() {}

    private static boolean deny(WorldRuntimes runtimes, Store<EntityStore> store, PlayerRef player, BlockPos pos, Action action) {
        WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
        if (rt == null || !rt.enabled() || player == null) {
            return false;
        }
        ColonyManager m = rt.manager();
        if (!m.protectionEnabled() || m.isAllowed(player.getUuid(), pos, action)) {
            return false;
        }
        player.sendMessage(HytaleNotifier.toMessage(Msg.of("hycolony.permission.denied", m.colonyAt(pos).get().name())));
        return true;
    }

    public static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final WorldRuntimes runtimes;
        private final String hutItemId;

        public Place(WorldRuntimes runtimes, IdMap ids) {
            super(PlaceBlockEvent.class);
            this.runtimes = runtimes;
            this.hutItemId = ids.itemId("hut.townhall");
        }

        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull PlaceBlockEvent event) {
            if (event.getItemInHand() != null && hutItemId.equals(event.getItemInHand().getItemId())) {
                return;
            }
            if (deny(runtimes, store, TownHallBlockSystems.player(index, chunk, store),
                    TownHallBlockSystems.pos(event.getTargetBlock()), Action.PLACE_BLOCKS)) {
                event.setCancelled(true);
            }
        }
    }

    public static final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final WorldRuntimes runtimes;
        private final String hutBlockId;

        public Break(WorldRuntimes runtimes, IdMap ids) {
            super(BreakBlockEvent.class);
            this.runtimes = runtimes;
            this.hutBlockId = ids.blockId("hut.townhall");
        }

        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull BreakBlockEvent event) {
            if (hutBlockId.equals(event.getBlockType().getId())) {
                return;
            }
            if (deny(runtimes, store, TownHallBlockSystems.player(index, chunk, store),
                    TownHallBlockSystems.pos(event.getTargetBlock()), Action.BREAK_BLOCKS)) {
                event.setCancelled(true);
            }
        }
    }
}
```

`OPEN_CONTAINER` (spec § 3.2) : les conteneurs s'ouvrent via `UseBlockEvent.Pre`. Ajouter à `ProtectionSystems` une classe `Use` identique à `Break`, sur `UseBlockEvent.Pre`. Elle ignore le bloc hôtel de ville et vérifie `Action.OPEN_CONTAINER` **uniquement** si le bloc ciblé a un conteneur. Le test est : `BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, x, y, z) != null` (voir `docs/research/hytale-api-spike.md`, section Conteneurs). Écrire cette classe sur le modèle exact de `Break` ci-dessus.

- [ ] **Step 3 : enregistrer les systèmes dans `HyColonyPlugin.setup()`**

Ajouter, après l'enregistrement de `CitizenBodyLifecycleSystem` :

```java
        getEntityStoreRegistry().registerSystem(new dev.hycolony.plugin.block.TownHallBlockSystems.Place(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new dev.hycolony.plugin.block.TownHallBlockSystems.Break(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new dev.hycolony.plugin.block.TownHallBlockSystems.Use(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new dev.hycolony.plugin.block.ProtectionSystems.Place(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new dev.hycolony.plugin.block.ProtectionSystems.Break(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new dev.hycolony.plugin.block.ProtectionSystems.Use(runtimes, ids));
```

- [ ] **Step 4 : compiler et tester en jeu**

Run : `./gradlew :plugin:compileJava`, puis `./gradlew :plugin:runServer`

En jeu (mode créatif, obtenir l'objet avec `/give <soi> HyColony_TownHall`, ou la commande équivalente du serveur) :
1. Poser l'hôtel de ville : la fenêtre « Fonder une colonie » s'ouvre. Confirmer : le message « Colonie … fondée ! » s'affiche.
2. Dans les 1 à 2 minutes, **4 citoyens apparaissent un par un** et errent. Le délai suit les minuteurs de MineColonies : le premier arrive environ 50 s après la fondation, puis un toutes les 75 s environ.
3. Clic sur l'hôtel de ville : la fenêtre affiche le nom, le propriétaire, le jour et les citoyens.

- [ ] **Step 5 : commit**

```bash
git add plugin
git commit -m "feat(plugin): town hall place/break/use and colony block protection

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task B6 : commandes, documentation, vérification finale

**Files :**
- Create : `plugin/src/main/java/dev/hycolony/plugin/command/HyColonyCommand.java`, `docs/UPGRADING.md`, `docs/TESTING.md`
- Modify : `README.md` (remplacé), `HyColonyPlugin.setup()` (enregistrement de la commande)

**Interfaces :**
- Consumes : `ColonyManager` (`colonyAt`, `setRank`, `deleteColony`), `HytaleCitizenBodies`, `FileColonyStorage`, `IdMap.validate`.
- Produces : `/hycolony info|rank|delete|selftest`.

- [ ] **Step 1 : écrire la commande**

`command/HyColonyCommand.java`. Les signatures viennent de `AbstractPlayerCommand` et `AbstractCommandCollection`. Relire `server/core/command/system/basecommands/*.java` et un exemple vanilla avec arguments (`grep -rln "withRequiredArg" build/vineflower | head -3`) avant d'écrire.

```java
package dev.hycolony.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.Permissions;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.core.kernel.port.Msg;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public final class HyColonyCommand extends AbstractCommandCollection {
    public HyColonyCommand(WorldRuntimes runtimes, IdMap ids) {
        super("hycolony", "HyColony colony management");
        addSubCommand(new Info(runtimes));
        addSubCommand(new Rank(runtimes));
        addSubCommand(new Delete(runtimes));
        addSubCommand(new SelfTest(runtimes, ids));
    }

    private static BlockPos where(Store<EntityStore> store, Ref<EntityStore> ref) {
        Vector3d p = store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
        return new Vec3(p.x, p.y, p.z).toBlockPos();
    }

    private static void say(PlayerRef player, String key, String... params) {
        player.sendMessage(HytaleNotifier.toMessage(Msg.of(key, params)));
    }

    static final class Info extends AbstractPlayerCommand {
        private final WorldRuntimes runtimes;

        Info(WorldRuntimes runtimes) {
            super("info", "Colony at your position");
            this.runtimes = runtimes;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            WorldRuntime rt = runtimes.of(world);
            Optional<Colony> colony = rt == null ? Optional.empty() : rt.manager().colonyAt(where(store, ref));
            if (colony.isEmpty()) {
                say(player, "hycolony.cmd.noColony");
                return;
            }
            Colony c = colony.get();
            say(player, "hycolony.cmd.info", c.name(), String.valueOf(c.id()), c.permissions().ownerName(),
                    c.state().name().toLowerCase(Locale.ROOT), String.valueOf(c.day()), String.valueOf(c.citizens().all().size()));
        }
    }

    static final class Rank extends AbstractPlayerCommand {
        private final WorldRuntimes runtimes;
        private final RequiredArg<PlayerRef> target;
        private final RequiredArg<String> rank;

        Rank(WorldRuntimes runtimes) {
            super("rank", "Set a player's rank in the colony you stand in");
            this.runtimes = runtimes;
            this.target = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.rank = withRequiredArg("rank", "officer|friend|neutral|hostile", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            WorldRuntime rt = runtimes.of(world);
            Optional<Colony> colony = rt == null ? Optional.empty() : rt.manager().colonyAt(where(store, ref));
            if (colony.isEmpty()) {
                say(player, "hycolony.cmd.noColony");
                return;
            }
            int rankId = switch (ctx.get(rank).toLowerCase(Locale.ROOT)) {
                case "officer" -> Permissions.OFFICER;
                case "friend" -> Permissions.FRIEND;
                case "neutral" -> Permissions.NEUTRAL;
                case "hostile" -> Permissions.HOSTILE;
                default -> -1;
            };
            PlayerRef t = ctx.get(target);
            boolean ok = rankId >= 0 && rt.manager().setRank(player.getUuid(), colony.get().id(), t.getUuid(), t.getUsername(), rankId);
            say(player, ok ? "hycolony.cmd.rankSet" : "hycolony.cmd.rankFailed");
        }
    }

    static final class Delete extends AbstractPlayerCommand {
        private final WorldRuntimes runtimes;
        private final RequiredArg<Integer> id;

        Delete(WorldRuntimes runtimes) {
            super("delete", "Delete a colony (operators)");
            this.runtimes = runtimes;
            this.id = withRequiredArg("id", "Colony id", ArgTypes.INTEGER);
            // Operators only: keep the auto-generated permission node (<group>.<name>.command.hycolony.delete)
            // and do not grant it to default players.
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            WorldRuntime rt = runtimes.of(world);
            if (rt != null && rt.manager().byId(ctx.get(id)).isPresent()) {
                rt.manager().deleteColony(ctx.get(id));
                say(player, "hycolony.cmd.deleted");
            } else {
                say(player, "hycolony.cmd.noColony");
            }
        }
    }

    /** Exercises each port against the live server (spec § 4.5). */
    static final class SelfTest extends AbstractPlayerCommand {
        private final WorldRuntimes runtimes;
        private final IdMap ids;

        SelfTest(WorldRuntimes runtimes, IdMap ids) {
            super("selftest", "Check HyColony against this server (operators)");
            this.runtimes = runtimes;
            this.ids = ids;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef player, @Nonnull World world) {
            WorldRuntime rt = runtimes.of(world);
            List<String> idErrors = ids.validate();
            report(player, "asset ids", idErrors.isEmpty(), String.join(", ", idErrors));

            try {
                Path tmp = Files.createTempDirectory("hycolony-selftest");
                FileColonyStorage storage = new FileColonyStorage(tmp);
                storage.save(1, "{\"ok\":true}");
                report(player, "storage", storage.load(1).map(o -> o.get("ok").getAsBoolean()).orElse(false), "round trip");
            } catch (Exception e) {
                report(player, "storage", false, e.toString());
            }

            BlockPos here = where(store, ref);
            Optional<BodyId> body = rt.bodies().spawn(null, here.offset(2, 0, 0), -1, -1, "SelfTest");
            report(player, "spawn", body.isPresent(), "spawnNPCWithColumnProbe");
            body.ifPresent(b -> {
                Vec3 start = rt.bodies().position(b).orElseThrow();
                rt.bodies().moveTo(b, new Vec3(start.x() + 3, start.y(), start.z()));
                // Poll NavStatus for up to 15 s on the world thread, then despawn.
                long[] waited = {0};
                Runnable[] poll = new Runnable[1];
                poll[0] = () -> {
                    NavStatus s = rt.bodies().navStatus(b);
                    waited[0] += 500;
                    if (s == NavStatus.MOVING && waited[0] < 15_000) {
                        world.scheduleAfter(poll[0], 500, TimeUnit.MILLISECONDS);
                        return;
                    }
                    report(player, "move", s == NavStatus.ARRIVED, s.name());
                    rt.bodies().despawn(b);
                };
                world.scheduleAfter(poll[0], 500, TimeUnit.MILLISECONDS);
            });
        }

        private static void report(PlayerRef player, String step, boolean ok, String detail) {
            if (ok) {
                say(player, "hycolony.selftest.ok", step);
            } else {
                say(player, "hycolony.selftest.ko", step, detail);
            }
        }
    }
}
```

Le corps de test porte le tag `(-1, -1)`. Au rechargement, `onBodyLoaded` ne trouve pas la colonie -1 et le supprime. Aucun corps orphelin ne peut donc survivre à un crash pendant le selftest.

`delete` et `selftest` sont réservés aux opérateurs. Vérifier le mécanisme de permission dans `AbstractCommand` (`requirePermission`, `setPermissionGroups`, voir `docs/research/hytale-api-spike.md`, section Commandes) et l'appliquer aux deux sous-commandes. Vérifier aussi les paquetages de `RequiredArg` et `ArgTypes` (`find build/vineflower -name ArgTypes.java -o -name RequiredArg.java`) et `world.scheduleAfter`.

Enregistrer dans `setup()` :

```java
        getCommandRegistry().registerCommand(new dev.hycolony.plugin.command.HyColonyCommand(runtimes, ids));
```

- [ ] **Step 2 : écrire `docs/UPGRADING.md`**

```markdown
# Mettre à jour Hytale

1. Lire les notes de patch (sections plugins/API, NPC, blocs, UI).
2. Changer `hytale_version`, `server_version`, `manifestServerVersion` dans `gradle.properties`.
3. `./gradlew build --refresh-dependencies` puis `./gradlew decompileServerJar injectServerJavadocsIntoDecompiledSources`.
4. Corriger les erreurs de compilation dans `plugin/` uniquement. Le core ne doit pas bouger.
5. `./gradlew :core:test`.
6. `./gradlew :plugin:runServer` : aucune ligne `missing asset id` dans les logs.
7. En jeu : `/hycolony selftest` → tout OK.
8. Charger une sauvegarde de la version précédente (copier un monde de test existant).
9. Dérouler `docs/TESTING.md`.
10. Commit `build: bump Hytale to X.Y.Z`.
```

- [ ] **Step 3 : écrire `docs/TESTING.md` (checklist manuelle, spec § 1)**

```markdown
# Checklist de test en jeu — SP0

Serveur de dev : `./gradlew :plugin:runServer`. Deux comptes : A (propriétaire) et B (étranger).

1. **Fondation** : A pose l'hôtel de ville → fenêtre « Fonder une colonie » → nom « Test » → Fonder.
   Attendu : message de fondation ; un double-clic ne crée qu'une colonie (`/hycolony info` : 1 colonie).
2. **Annulation** : A pose un hôtel de ville ailleurs (autre monde ou après suppression) → Annuler.
   Attendu : le bloc disparaît et l'objet tombe au sol.
3. **Citoyens** : dans les 2 minutes, 4 citoyens nommés apparaissent un par un et errent.
   Décharger la zone (s'éloigner loin) puis revenir : toujours 4 citoyens, aucun doublon.
4. **Redémarrage** : arrêter le serveur, relancer. Attendu : colonie, 4 citoyens, mêmes noms ; aucun doublon.
5. **Protection** : B casse/pose un bloc dans la colonie → refusé avec message. A : `/hycolony rank B officer` → B peut.
6. **Fenêtre** : clic sur l'hôtel de ville → nom, propriétaire, jour, citoyens ; A renomme → nouveau nom affiché.
7. **Jour/nuit** : `/hycolony info` avant et après une aube → le jour augmente de 1 (ajuster
   `HytaleGameClock.DAY_START_HOUR/NIGHT_START_HOUR` si l'aube réelle diffère).
8. **Selftest** : `/hycolony selftest` → toutes les lignes `[OK]`.
9. **Fichiers** : `<sauvegarde du monde>/hycolony/colony-1.json` existe et contient `"schemaVersion":1`.
```

- [ ] **Step 4 : remplacer `README.md`**

```markdown
# HyColony

Colony management for Hytale, recreating MineColonies' systems: found a colony with a Town Hall, citizens
who live and work, buildings built by a builder, a logistics request system, jobs, needs, research and defense.

**Status:** sub-project 0 (foundations & colony) — Town Hall, citizens, permissions, persistence.

- Server: Hytale 0.6.8 (see `docs/UPGRADING.md`)
- License: GPL-3.0 (game mechanics ported from [MineColonies](https://github.com/ldtteam/minecolonies), GPL-3.0; no MineColonies assets)

## Build

    ./gradlew build              # core tests + plugin jar (plugin/build/libs/HyColony-*.jar)
    ./gradlew setupHytaleDev     # once: download assets (Hytale login)
    ./gradlew :plugin:runServer  # local dev server

## Layout

- `core/` — pure Java game logic (no Hytale imports), fully unit-tested
- `plugin/` — Hytale adapter + asset pack
- `docs/` — research, specs, plans, upgrade and test checklists
```

- [ ] **Step 5 : vérification finale**

Run : `./gradlew build`
Attendu : `BUILD SUCCESSFUL`, tous les tests du core passent.

Puis dérouler **toute** la checklist `docs/TESTING.md` sur le serveur de dev. Si un point échoue, corriger le code, recompiler et reprendre depuis le point concerné. Si la cause est un des points listés en spec § 11, noter la solution retenue dans `docs/research/hytale-api-spike.md`.

- [ ] **Step 6 : commit**

```bash
git add -A
git commit -m "feat(plugin): /hycolony commands and selftest; docs: upgrade and test checklists, README

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

## Fin du sous-projet 0

Le sous-projet 0 est terminé quand les 6 critères de la spec § 1 passent en jeu (`docs/TESTING.md`). Le sous-projet 1 (système de requêtes) commencera par sa propre spec.
