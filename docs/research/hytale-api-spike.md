# Hytale — spike API serveur

Version analysée : **0.6.8** (patchline `release`, Update 6), 2026-09-25.
Source : jar décompilé via `./gradlew decompileServerJar injectServerJavadocsIntoDecompiledSources`
→ `build/vineflower/hytale-server/com/hypixel/hytale/` (noté `…/`).
Rien n'a été testé en jeu : ces conclusions viennent de la lecture du code.

## Contexte des versions

- Update 5 (mai 2026) et Update 6 (août 2026) ont chacune cassé des API plugin. Update 6 : protocole `hytale/2` → `hytale/3`.
- **Update 7** en pré-release (depuis le 2026-09-03) : refonte de l'accès aux blocs vers les **sections** (`WorldChunk.setBlock/breakBlock`, `World.getBlock` supprimés ou restreints).
- En 0.6.8, **la nouvelle API par sections existe déjà** et les anciennes méthodes sont `@Deprecated`. → On écrit directement contre la nouvelle API.
- Bibliothèques fournies par le serveur : **Gson, JOML, Guava, BSON, fastutil, Flogger**.

## PNJ (citoyens)

- **Apparition** : `NPCPlugin.get().spawnNPC(store, roleName, null, position, rotation)` → `Pair<Ref<EntityStore>, INonPlayerCharacter>` (null si rôle inconnu). `spawnEntity(..., preAddToWorld, postSpawn)` permet d'attacher nos composants avant l'ajout au monde.
- **Rôle JSON** dans notre asset pack : `Server/NPC/Roles/**.json`, nom du rôle = nom du fichier. Clés : `Appearance`, `MaxHealth`, `MotionControllerList`, `Instructions`, `InteractionInstruction`, `StateTransitions`, `DisplayNames`, `HotbarSize`, `InventorySize`...
- **Éléments custom** : `NPCPlugin.get().registerCoreComponentType("Nom", BuilderX::new)` dans `setup()` (comme MountPlugin, NPCObjectives...).
  - Action : builder `extends BuilderActionBase` + runtime `extends ActionBase` (`execute(...)` renvoie true quand fini).
  - Sensor : builder `extends BuilderSensorBase` (+ `provideFeature(Feature.Position)`) + runtime `extends SensorBase` (`matches(...)`, `getSensorInfo()` → `PositionProvider.setTarget(...)`).
- **Aller à une position choisie par code** (approche retenue) : sensor custom `"HyColonyTarget"` qui lit la cible dans notre composant ECS + BodyMotion natif `"Seek"` (`BodyMotionFind`, A*).
  - Arrivée / échec : `npc.getRole().getActiveMotionController().getNavState()` → `INIT, PROGRESSING, BLOCKED, DEFER, AT_GOAL, ABORTED` (aussi le sensor JSON `"Nav"`).
  - Limites par défaut du pathfinding : `MaxPathLength` 200, `MaxOpenNodes` 200, `MaxTotalNodes` 900, `StepsPerTick` 50. Portée d'environ ±512 blocs. Pas de budget global.
- **État** : `StateSupport.setState(ref, "State", subState, accessor)`. Changement de rôle : `RoleChangeSystem.requestRoleChange(...)` (appliqué plus tard). État, flags et timers **ne sont pas sauvegardés** → à réappliquer depuis le core.
- **Blocs** : action `"PlaceBlock"` existe ; **aucune action pour casser un bloc** → le plugin casse lui-même (comme MineColonies).
- **Animation** : `NPCEntity.playAnimation(ref, AnimationSlot, animId, accessor)`.
- **Inventaire / objet en main** : `InventoryHelper.useItem(ref, itemId, accessor)`, hotbar ≤ 8, inventaire ≤ 36.
- **Nom** : `DisplayNameSupport.setDisplayName(ref, name, accessor)`.
- **Persistance** : les PNJ sont sauvegardés avec les chunks. Notre composant est sauvegardé s'il est enregistré avec un id et un codec : `getEntityStoreRegistry().registerComponent(Cls.class, "HyColonyCitizen", CODEC)`.
- Non vérifié : despawn automatique éventuel des PNJ créés par plugin.

## Blocs

- Lecture : `ChunkStore cs = world.getChunkStore(); Ref<ChunkStore> sec = cs.getChunkSectionReferenceAtBlock(x,y,z)` (null si non chargé) → `BlockSection.get(x,y,z)`, `getRotationIndex`, `getFiller` → `BlockType.getAssetMap().getAsset(id)`.
- Écriture : `BlockOperations.setBlock(chunkStore, sectionRef, x, y, z, id, blockType, rotation, filler, settings)` (**non stable**, signature appelée à changer). `testPlaceBlock(...)` pour vérifier la place.
  - Id : `BlockType.getAssetMap().getIndex("Id")` (`Integer.MIN_VALUE` si inconnu). Rotation : `RotationTuple.of(yaw, pitch, roll).index()`.
  - `SetBlockSettings` : `NO_NOTIFY=1 … NO_DROP_ITEMS=2048 …`.
- Casser avec drops : `BlockHarvestUtils.getDrops(blockType, qty, itemId, dropListId)` (depuis `blockType.getGathering().getBreaking()`), puis mettre les objets dans l'inventaire du citoyen et retirer le bloc avec `NO_DROP_ITEMS` via `naturallyRemoveBlock(...)`.
- **Threads** : un thread par monde (`World implements Executor`). `world.execute(r)`, `world.scheduleAfter(r, delay, unit)`, `world.isInThread()`. Les systèmes ECS et les handlers d'événements tournent déjà sur le thread du monde.

## Chunks

- Chargé ? `world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(x, z)) != null`.
- Chargement async : `getChunkReferenceAsync(index, GetChunkFlags...)`.
- Garder chargé : `WorldChunk.addKeepLoaded()` / `removeKeepLoaded()` (compteur de références).
- Événements : `ChunkPreLoadProcessEvent` (global), `ChunkUnloadEvent` (ECS, annulable), `ChunkSaveEvent`.

## Prefabs (blueprints)

- Asset pack : `Server/Prefabs/<clé>` (`.prefab.json` lisible, ou `.lpf` compressé). `PrefabStore.get().getAssetPrefabFromAnyPack(key)`.
- Itération pour la pose bloc par bloc : `IPrefabBuffer buf = PrefabBufferUtil.getCached(path); buf.forEach(iterateAllColumns(), (x,y,z,blockId,holder,support,rotation,filler,call,fluidId,fluidLevel) -> ..., null, null, new PrefabBufferCall(random, PrefabRotation.ROTATION_90))` → coordonnées **déjà tournées**, relatives à l'ancre.
- Modèle de pose : `PrefabUtil.paste(...)` (testPlaceBlock → setBlock → block entity). On ne pose que les entrées `filler == 0`.
- Événement : `PrefabPasteEvent`.

## Conteneurs (coffres)

- `BlockModule.getBlockEntity(world, x, y, z)` → composant `ItemContainerBlock.getItemContainer()`.
- `ItemContainer` : `addItemStack(s)` (vérifier le reste dans la transaction), `removeItemStack`, `getItemStack(slot)`, `forEach`, `countItemStacks(pred)`, `registerChangeEvent`.
- `ItemStack` immuable : `new ItemStack(itemId, qty)`, `withQuantity`, `getItemId`.

## Événements, systèmes, cycle de vie

- `JavaPlugin` : `setup()`, `start()`, `shutdown()`. Registres : `getEventRegistry()`, `getEntityStoreRegistry()`, `getChunkStoreRegistry()`, `getCommandRegistry()`, `getTaskRegistry()`, `getCodecRegistry(Interaction.CODEC)`.
- Les événements de gameplay sont des **événements ECS** : `PlaceBlockEvent` (annulable), `BreakBlockEvent`, `UseBlockEvent.Pre/Post`, `Damage`, mort via `DeathSystems.OnDeathSystem`. Handler : `extends EntityEventSystem<EntityStore, PlaceBlockEvent>`, enregistré par `registerSystem`.
- Événements globaux : `PlayerReadyEvent`, `PlayerConnect/DisconnectEvent`, `AddPlayerToWorldEvent`, `StartWorldEvent`, `AllWorldsLoadedEvent`, `ShutdownEvent`, `LoadedAssetsEvent`. `PlayerInteractEvent` est déprécié.
- Tick : `TickingSystem` (par monde), `EntityTickingSystem` (par entité), `DelayedSystem(intervalSec)`.
- Un listener qui lève une exception n'arrête plus les autres (Update 7).

## UI

- `InteractiveCustomUIPage<T>(playerRef, lifetime, eventCodec)` : `build(ref, UICommandBuilder, UIEventBuilder, store)`, `handleDataEvent(ref, store, data)`, `sendUpdate(...)` (obligatoire après un événement, sinon le client reste en chargement).
- Ouverture : `player.getPageManager().openCustomPage(ref, store, page)`.
- Layout `.ui` : `commandBuilder.append("Pages/X.ui")` ; probablement sous `Common/UI/Custom/` dans l'asset pack (déduit du code, pas vérifié).
- Liaison : `eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#Button", new EventData().append("@Field", "#Input.Value"))`.
- HUD : `CustomUIHud` + `player.getHudManager().addCustomHud(...)`.
- **Noesis** : aucune API serveur en 0.6.8. Un protocole MVVM "Serverside UI" existe (`Page.Serverside`, packets 1200/1202, DataContext/Command), mais rien n'y est branché → **isoler l'UI derrière des modèles de vue**.

## Persistance

- `getDataDirectory()` = `mods/<group>_<name>/`. `withConfig(name, codec)` → `<dataDir>/<name>.json`.
- `BsonUtil.writeDocument(path, doc, backup)` : écriture atomique avec `.tmp` + `.bak` ; `readDocument(path, backup)` se rabat sur le `.bak`.
- Ressource par monde : `getEntityStoreRegistry().registerResource(Cls.class, "Id", CODEC)` → `<worldSave>/resources/`. Ressource globale : `Universe.registerResource(...)`.
- `BuilderCodec` versionné : `.versioned().codecVersion(v)`, `setVersionRange(min, max)` par champ, `afterDecode(...)` pour migrer.

## Commandes, messages, i18n, logs

- `AbstractPlayerCommand.execute(ctx, store, ref, playerRef, world)` ; `AbstractCommandCollection` pour les sous-commandes ; `withRequiredArg(name, desc, ArgTypes.X)`. Permission auto : `<group>.<name>.command.<cmd>`.
- `Message.translation(key).param(...)`, `playerRef.sendMessage(msg)`, `NotificationUtil.sendNotification(...)`.
- i18n : `<pack>/Server/Languages/<lang>/**/*.lang` ; clé = chemin.fichier.clé (ex. `hycolony.colony.created`).
- Logs : `getLogger().at(Level.INFO).log("x=%d", x)`.
