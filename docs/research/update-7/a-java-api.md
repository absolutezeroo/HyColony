# Update 7 (0.7.0-pre.4) : axe A, API Java du serveur

Recherche du 2026-09-29. Compare Hytale 0.6.8 (épinglé) et la pré-version Update 7 part 4 (`Implementation-Version: 0.7.0-pre.4` dans le `MANIFEST.MF` du jar U7).

Sources et abréviations :

- **S6** = `build/vineflower/hytale-server/com/hypixel/hytale/` ; **S7** = sources U7 décompilées (scratchpad `u7/src/com/hypixel/hytale/`). Jars : `~/.gradle/caches/modules-2/files-2.1/com.hypixel.hytale/Server/0.6.8/*/Server-0.6.8.jar` et `u7/u7-classes.jar`.
- **N** = notes de version U7 (`notes.txt`, https://hytale.com/news/2026/9/pre-release-patch-notes-update-7), numéro de ligne entre crochets.
- Méthode, pour que rien ne manque :
  1. `javap -protected` des 224 classes importées par `plugin/`, `blockui/`, `domum/` sur les deux jars : 35 classes ont une signature différente (liste plus bas) ;
  2. diff des sources S6/S7 après retrait des commentaires (S6 contient la Javadoc injectée) : 88 des 224 fichiers diffèrent, dont une moitié par simple bruit de décompilation (`<>`, casts, `HytaleLogger.Api`) ;
  3. compilation de **tous** les sources principaux (`core`, `plugin`, `blockui`, `domum/core`, `domum/plugin` de `HEAD`) par `javac -Xlint:deprecation,removal` contre le jar U7, puis contre le jar 0.6.8 pour la référence ;
  4. recherche des membres `@RestrictedApi` de S7 (6 fichiers : `IChunkAccessorSync`, `LocalCachedChunkAccessor`, `BlockChunk`, `WorldChunk`, `IWorldChunksAsync`, `World`) et de leurs appels dans notre bytecode (`javap -c`) ;
  5. correctifs proposés ci-dessous appliqués à une **copie** des sources dans le scratchpad : elle compile contre U7 sans erreur, avec seulement les deux avertissements déjà présents en 0.6.8, et plus aucune référence à un membre restreint.

Rappel : notre build lance Error Prone 2.50.0 sur les sources principales (`build-logic/src/main/kotlin/hy.java-checks.gradle.kts`). Son contrôle `RestrictedApi` est une **erreur** par défaut ; l'annotation est bien dans le bytecode U7 (`RuntimeInvisibleAnnotations: com.google.errorprone.annotations.RestrictedApi(...)` sur `BlockChunk.getEnvironment(III)I`, vu par `javap -v`). Un appel restreint fait donc échouer `./gradlew build`, même si `javac` seul passe.

## Bloquant (ne compile pas, ou ne passe pas Error Prone)

### A1. Fissures de bloc : `BlockHealthChunk` remplacé par `BlockHealthSection`

- **Chez nous** : `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldEffects.java:149-168` (`crack`). Erreurs `javac` l. 155 (`getBlockHealthChunkComponentType()` absent) et l. 166 (`damageBlock(Instant, World, Vector3i, float)` absent) ; avertissement `[removal]` l. 154.
- **Preuve U7** :
  - `server/core/modules/blockhealth/BlockHealthChunk.java:21` : `@Deprecated(forRemoval = true)`, la classe ne garde que son `CODEC` et `getBlockFragilityMap()` (N [481] : « BlockHealthChunk is decode only »). `BlockHealthModule` ne l'enregistre plus que comme composant hérité à migrer (`BlockHealthModule.java:41`, système `BlockHealthSystems.MigrateBlockHealthChunk`, l. 146).
  - Nouveau composant de **section** : `BlockHealthSection` (`server/core/modules/blockhealth/BlockHealthSection.java`), type par `BlockHealthSection.getComponentType()` (l. 51) ou `BlockHealthModule.get().getBlockHealthSectionComponentType()` (`BlockHealthModule.java:59`). Méthodes : `getHealth(int x, int y, int z)` (l. 59, 1.0 si intact), `damage(int x, int y, int z, float amount, Instant now)` (l. 88-114, rend la santé restante, efface l'entrée si elle tombe à 0 ou moins), `isFragile`, `makeFragile`, `clearHealth`. Coordonnées monde, réduites par `ChunkUtil.indexBlock` (`math/util/ChunkUtil.java:71-73`, `& 31`).
  - Le composant est posé sur **chaque** section chargée par `BlockHealthSystems.EnsureSection` (`BlockHealthSystems.java:61-75`, `holder.addComponent(sectionType, new BlockHealthSection())`) : il n'est `null` que sur une section absente.
  - La réplication n'est plus immédiate : `damage` note un delta, et `BlockHealthSystems.ReplicateChanges` (l. 271-345) envoie `UpdateBlockDamage` ou `UpdateBlockDamages` en fin de tick, **aux seuls joueurs qui ont la section chargée** (l. 336, `player.getChunkTracker().isLoaded(sectionX, sectionY, sectionZ)`). N [481] : « damage replicates on its own once it is written to the section ».
  - La guérison reste 5 s après le dernier coup, à 0,1 par seconde (`BlockHealthSystems.Tick`, l. 357-387, `tickRegeneration(now, dt, 5L, 0.1F)`), comme en 0.6.8 (`S6 …/BlockHealthModule.java:61-62`).
  - La casse efface toujours la santé : `BlockOperations.setBlock` appelle désormais `BlockHealthModule.onBlockReplaced` (`server/core/universe/world/chunk/BlockOperations.java:80`, `BlockHealthModule.java:63-71`), au lieu de `BlockHealthChunk.removeBlock` dans `BlockHarvestUtils.removeBlock` (S6 l. 1317 et suivantes).
  - Modèle vanilla : `BlockHarvestUtils.performBlockDamage` (`server/core/modules/interaction/BlockHarvestUtils.java`, diff : `blockHealthSection.getHealth(...)`, `blockHealthSection.damage(x, y, z, damage, timeResource.getNow())`, puis `originChunkSection.markNeedsSaving()`).
- **Correctif proposé** (vérifié par compilation contre U7) : lire le composant sur la **section** `sec` déjà en main, plus sur la colonne.

```java
BlockHealthSection health = chunks.getComponent(sec, BlockHealthSection.getComponentType());
if (health == null) {
    return;
}
float current = health.getHealth(pos.x(), pos.y(), pos.z());
float damage = current - Math.max(MIN_HEALTH, 1f - progress);
if (damage > 0) {
    TimeResource time = world.getEntityStore().getStore().getResource(TimeResource.getResourceType());
    health.damage(pos.x(), pos.y(), pos.z(), damage, time.getNow());
    section.markNeedsSaving(); // as BlockHarvestUtils.performBlockDamage does
}
```

  Imports à retirer ensuite : `BlockHealth`, `BlockHealthChunk`, `BlockHealthModule`, `org.joml.Vector3i`. La Javadoc de `crack` (l. 144-147) reste juste sur la guérison ; « naturallyRemoveBlock clears it » passe désormais par `BlockOperations.setBlock`. `MIN_HEALTH = 0.05f` (l. 41) garde la santé au-dessus de 0, donc `damage` ne supprime jamais l'entrée par erreur.
- **[in-game]** : les fissures s'affichent toujours (réplication en fin de tick, envoyée seulement aux joueurs qui ont la section chargée).

### A2. Point d'apparition : `ISpawnProvider.getSpawnPoint` devenu asynchrone

- **Chez nous** : `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldQuery.java:48` (`spawnPoint`), erreur `javac`. Appelé par le cœur dans `core/.../app/action/HutActions.java:97` (`spawnDistanceRefusal`, sur le thread du monde, pendant la pose d'une hutte). Seuls x et z servent.
- **Preuve U7** : `server/core/universe/world/spawn/ISpawnProvider.java:20-28` : les deux `getSpawnPoint` sont retirés (ainsi que `getSpawnPoint(Entity)`, déjà `forRemoval` en 0.6.8). À la place, `CompletableFuture<Transform> getSpawnPointAsync(World, UUID)` (abstraite) et `getSpawnPointAsync(Ref, ComponentAccessor)` (par défaut).
  - `GlobalSpawnProvider.java:36-37` et `IndividualSpawnProvider.java:45-46` rendent un `completedFuture`.
  - `FitToHeightMapSpawnProvider.java:41-50` enchaîne sur le fournisseur interne et, seulement si le y vaut la sentinelle `Integer.MIN_VALUE` (l. 52-54 ; N [325]), sur `chunkStore.getChunkReferenceAsync(index, 32)`. C'est le fournisseur par défaut d'un monde généré (`server/core/universe/world/worldgen/IWorldGen.java:26`, `new FitToHeightMapSpawnProvider(new IndividualSpawnProvider(...))`).
  - Le serveur lui-même compose désormais le futur (`World.java`, ajout d'un joueur : `spawnProvider.getSpawnPointAsync(this, uuid).thenComposeAsync(...)`).
- **Correctif proposé** (vérifié par compilation) : ne **jamais** attendre le futur sur le thread du monde (`join()` bloquerait : la suite de `FitToHeightMapSpawnProvider` peut devoir tourner sur ce même thread). Prendre la valeur si elle est prête, sinon « point inconnu » (ce que le port prévoit déjà) :

```java
Transform t = provider.getSpawnPointAsync(world, player).getNow(null);
if (t == null) {
    return Optional.empty(); // still fitting to the height map: the core treats the spawn as unknown
}
Vector3d p = t.getPosition();
```

  Avec `Global`, `Individual`, et `FitToHeightMap` sans sentinelle, le futur est déjà terminé : le comportement est celui de 0.6.8. Seul un point de génération non encore ajusté donne « inconnu », donc aucun refus de distance à ce moment-là. `import com.hypixel.hytale.math.vector.Transform;` à ajouter.

### A3. Pluie : `BlockChunk.getEnvironment(int, int, int)` restreint (Error Prone) et marqué pour retrait

- **Chez nous** : `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldQuery.java:72-80` (`isRainingAt`). `javac` ne donne qu'un avertissement `[removal]` (l. 80), mais **Error Prone refuse l'appel**, donc le build échoue.
- **Preuve U7** : `server/core/universe/world/chunk/BlockChunk.java:204-211` : `@Deprecated(forRemoval = true)` et `@RestrictedApi(explanation = "Resolve the section with ChunkStore.getChunkSectionReferenceAtBlock and read EnvironmentSection.get on its EnvironmentSection component. …", allowedOnPath = ".*/BuilderTools/.*")`. N [214] : « Removed BlockChunk.getEnvironment(Vector3d) and restricted the (x, y, z) overload to BuilderTools. Environment reads now go through the EnvironmentSection component … which answers at any Y ».
  - Remplaçant : `EnvironmentSection` (`server/core/universe/world/chunk/section/EnvironmentSection.java`), `getComponentType()` (l. 63), `get(int x, int y, int z)` en coordonnées monde (l. 88).
  - Modèle vanilla : `builtin/weather/components/WeatherTracker.java:170-175` (`chunkStore.getComponent(sectionRef, EnvironmentSection.getComponentType())` puis `environmentSection.get(blockX, blockY, blockZ)`), et `BlockHarvestUtils` (même remplacement dans le diff).
  - Nouveauté utile : `WeatherResource.getEffectiveWeatherIndex(int environmentId)` (`builtin/weather/resources/WeatherResource.java:43-46`) = météo forcée sinon celle de l'environnement, exactement notre logique actuelle (N [449]).
- **Correctif proposé** (vérifié par compilation) :

```java
Ref<ChunkStore> sec = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
EnvironmentSection env = sec == null
        ? null
        : world.getChunkStore().getStore().getComponent(sec, EnvironmentSection.getComponentType());
if (env == null) {
    return false;
}
int index = weather.getEffectiveWeatherIndex(env.get(pos.x(), pos.y(), pos.z()));
```

  Imports : retirer `BlockChunk` et `ChunkUtil` s'ils ne servent plus ailleurs dans le fichier (`ChunkUtil` sert encore à `isLoaded`, l. 37), ajouter `EnvironmentSection`. Différence de comportement : la lecture marche désormais à tout y (0.6.8 rendait l'environnement 0 hors de [0, 320[). **[in-game]** : `/weather set Rain` puis une tâche sensible à la pluie.

### A4. Manifestes : `ServerVersion` `">=0.6.8 <0.7.0"` refusé pour 0.7.0-pre.4 (avertissement, pas un refus de chargement)

- **Chez nous** : `plugin/src/main/resources/manifest.json`, `blockui/src/main/resources/manifest.json`, `domum/plugin/src/main/resources/manifest.json` : `"ServerVersion": ">=0.6.8 <0.7.0"`. Les sous-packs de HyColony en héritent (`plugin/src/main/java/dev/hycolony/plugin/subplugin/PackAssets.java:69`, `manifest.setServerVersion(owner.getServerVersion())`).
- **Preuve U7** :
  - `common/semver/Semver.java:73-121` : `0.7.0-pre.4` < `0.7.0` (une pré-version est plus petite que la version finale). Les deux comparateurs sont donc satisfaits.
  - Mais `common/semver/SemverRange.java:20-28, 44-62` applique la règle npm des pré-versions : une version avec suffixe n'est acceptée que si l'un des comparateurs porte lui-même une pré-version de même `major.minor.patch`. Aucun de nos comparateurs n'en porte : le résultat est `INCOMPATIBLE` (`common/plugin/PluginManifest.java:133-149`). Même règle en 0.6.8 (S6 `SemverRange.java:28`).
  - Effet : `PluginManager.validatePluginDeps` (`server/core/plugin/PluginManager.java:440-476`) et `AssetModule` (`server/core/asset/AssetModule.java:133-185`) journalisent un WARNING « targets server version range … which does not match », un SEVERE « One or more asset packs are targeting an older server version », et signalent les mods « outdated » aux joueurs qui ont `HytalePermissions.MODS_OUTDATED_NOTIFY`, sauf avec `-Dhytale.allow_outdated_mods`. **Le chargement continue.**
- **Correctif proposé** : `"ServerVersion": ">=0.7.0-pre.4 <0.8.0"` dans les trois manifestes, le jour où l'on épingle U7 (le comparateur `>=0.7.0-pre.4` porte une pré-version de même 0.7.0, il satisfait la règle ; la 0.7.0 finale passe aussi). Tant que l'on reste épinglé en 0.6.8, ne rien changer.

### A5. Un jar construit contre 0.6.8 et lancé sur U7 plante au premier appel

Conséquence directe de A1 et A2 : la JVM résout les méthodes au premier appel. `NoSuchMethodError` est une `Error`, pas une `RuntimeException` : les `catch (RuntimeException e)` de `HytaleWorldQuery.spawnPoint` (l. 50) et de `HytaleWorldEffects.blockHit` ne l'attrapent pas. Le jar actuel se charge (avertissements de A4), puis lève à la première pose de hutte (distance au spawn) et au premier coup d'un bâtisseur sur un bloc. `BlockChunk.getEnvironment(int, int, int)` existe encore en U7 : la pluie marcherait. Il faut donc recompiler avant tout essai sur U7.

## Déprécié (à migrer, ne casse rien aujourd'hui)

| Appel | Chez nous | État U7 | Remplaçant |
|---|---|---|---|
| `Player.getPlayerRef()` | `plugin/.../ui/highlight/HighlightMarkers.java:24` | `@Deprecated(forRemoval = true)`, **déjà en 0.6.8** (S7 `server/core/entity/entities/Player.java:737`) | Aucun meilleur dans `WorldMapManager.MarkerProvider.update(World, Player, MarkersCollector)` (S7 `WorldMapManager.java:510-511`) : le vanilla U7 l'appelle encore au même endroit (`markers/providers/OtherPlayersMarkerProvider.java:35, 37`). À garder. `Entity.getUuid()` est lui aussi `forRemoval` (`server/core/entity/Entity.java:150-151`). |
| `ItemStack.getMetadata()` | `plugin/.../ui/citizen/CitizenItemContainer.java:125` | `@Deprecated` simple, **déjà en 0.6.8** (S7 `server/core/inventory/ItemStack.java:127-128`) | Pas de lecture « a-t-il des métadonnées » non dépréciée (`getFromMetadataOrNull` exige une clé, l. 327-332). À garder. C'est l'avertissement « uses or overrides a deprecated API » du `compile.log`. |
| `BlockChunk.getEnvironment` | voir A3 | `forRemoval` et restreint | `EnvironmentSection.get` |
| `BlockHealthChunk` | voir A1 | `forRemoval`, décodage seul | `BlockHealthSection` |

Aucun autre membre déprécié ou restreint n'est appelé (lint `javac` complet et recherche dans le bytecode). En particulier, aucun des accès restreints cités par N [202], [206], [212], [215], [703], [704] (`World.getBlock`/`getBlockType`, `WorldChunk.*`, `getChunk*`, `LocalCachedChunkAccessor`, `getBlockBulkRelative`, `BlockChunk.getBlock`/`getHeight`/`getHeightmapColumn`) : nos lectures et écritures passent déjà par `BlockSection` et `BlockOperations` sur la référence de section (`HytaleBlocks.java:25` le documente). Les appels `event.getBlockType()` visent les événements ECS, pas `World`.

## Changement de comportement (compile, mais le sens change)

1. **Hitbox et `EntityScaleComponent`** (N [131], [233]) : `BoundingBox` garde une échelle et l'applique au cadre et aux boîtes de détail (`server/core/modules/entity/component/BoundingBox.java:59` `setScale`, `rescaleBoundingBox`, `rescaleDetailBoxes`). Un nouveau système `EntityScaleBoundingBoxSystem` (`server/core/modules/entity/system/EntityScaleBoundingBoxSystem.java:17`) la tient à jour.
   - Chez nous, seul `plugin/.../ui/highlight/GlowingBlock.java:70` pose un `EntityScaleComponent(1.05f)` (l. 39), sur une entité `Intangible` (l. 69) : la hitbox grandit de 5 %, sans effet visible.
   - `HytaleCitizenBodies.java:322-330` lit `BoundingBox.getBoundingBox()` pour `translateToAccessiblePosition`. Nos citoyens n'ont pas d'`EntityScaleComponent` : leur boîte ne change pas. **[in-game]** si un modèle de citoyen porte une échelle.
2. **Pas d'entités enfants chez nous ni dans le vanilla** (N [337]). `QuerySystem.getHierarchyScope()` vaut `HierarchyScope.ROOT` par défaut (`component/system/QuerySystem.java:15-16`). `ROOT` inclut les racines sans enfant et les parents, pas les enfants (`component/system/HierarchyScope.java` ; filtrage `Store.inScope`, `component/Store.java:2394-2400`).
   - Une entité ne devient enfant que par `Store/CommandBuffer.addEntity(..., parent)` ou `setParent`. Aucun appel hors du paquet `component` dans S7 (recherche de `.setParent(`, de `addEntity` avec parent et de `HierarchyScope.`), et aucun chez nous.
   - Nos 19 systèmes (`EntityEventSystem`, `RefSystem`, `TickingSystem`, `EntityTickingSystem`, `WorldEventSystem`, `DamageEventSystem`) voient donc les mêmes entités qu'en 0.6.8.
   - `removeEntity` supprime maintenant aussi les enfants (`Store.java:704-715`) : sans effet pour nous.
3. **Rendu des traductions serveur** (N [751]) : `I18nModule.getMessages` passe par `resolveLanguage` (`server/core/modules/i18n/I18nModule.java:387, 405-414`) : langue exacte, sinon sa langue de repli (`fallbacks`), sinon en-US. `plugin/.../ui/field/SeedPickerPage.java:78` (`I18nModule.get().getMessage(playerRef.getLanguage(), key)`) rendra donc le français pour un client `fr-CA` (avant : clé cherchée telle quelle). Mieux, rien à changer. Le français devient une langue officielle du jeu (N [35]).
4. **Fusion des objets au sol** (N [644], [708]) : `ItemMergeSystem.RADIUS` disparaît. Le rayon vient de `ItemComponent.getMergeRadius(accessor)` (`server/core/modules/entity/item/ItemComponent.java:132-137`) : l'`ItemEntityConfig` de l'objet, sinon celle du `GameplayConfig` du monde, 2,0 par défaut (`server/core/asset/type/item/config/ItemEntityConfig.java:71`). C'est le `RADIUS = 2.0F` de 0.6.8 (S6 `ItemMergeSystem.java:35`). Nos lâchers (`HytaleBlocks.java:75`, `ItemComponent.generateItemDrops`) fusionnent donc comme avant, sauf si un asset U7 change `MergeRadius` **[in-game]** (axe assets).
5. **Montée de niveau d'une table** : le vanilla U7 appelle en plus `BenchBlock.notifyTierUpgraded(world, pos, tier)` après `setBlockInteractionState` (`builtin/crafting/component/CraftingManager.java:860`). L'écouteur est posé par `AugmentBlocksPlugin` (`builtin/augmentblocks/AugmentBlocksPlugin.java:45, 51-61`, relève la progression des blocs `AugmentBlock`). Notre `plugin/.../block/BenchTiers.java:43-53`, qui imite `CraftingManager.finishTierUpgrade`, ne l'appelle pas. Sans effet sur nos tables (pas d'`AugmentBlock`) ; pour la fidélité au vanilla, ajouter `BenchBlock.notifyTierUpgraded(world, new Vector3i(pos.x(), pos.y(), pos.z()), tier)`.
6. **Journal du suivi d'entités** : `EntityTrackerSystems` journalise maintenant en SEVERE « Entity can't be removed and also receive an update! » quand une entité sort de `visible` alors qu'une mise à jour l'attendait (`server/core/modules/entity/tracker/EntityTrackerSystems.java:1019-1034`). Notre `GogglesSystems.Visibility` (`plugin/.../goggles/GogglesSystems.java:155-159`) retire des aperçus de `viewer.visible` dans `FIND_VISIBLE_ENTITIES_GROUP`, avant la mise en file des mises à jour : le cas ne devrait pas se produire. **[in-game]** : surveiller ce message dans le journal avec les lunettes.
7. **Monde sauvegardé en U7 = monde illisible en 0.6.8** : `BlockSection.VERSION` passe de 6 à 7 (`server/core/universe/world/chunk/section/BlockSection.java`, `codecVersion(7)`). N [260] (« Existing worlds will upgrade themselves the first time they load ») et N [336] (« a world loaded on this version cannot be opened on an older one »). Il faut **sauvegarder le monde de test avant** d'y lancer U7. Nos propres fichiers JSON de colonie ne sont pas touchés.
8. **Ticking posé à la main** (N [695]) : le vanilla U7 marque la section à sauver après un `BlockSection.setTicking` qui change quelque chose (`BlockOperations.java`, `setTicking` privé : `if (section.setTicking(...)) chunkSection.markNeedsSaving()`). Les blocs « ticking » sont sauvegardés (`BlockSection.java:877`, `tickingBlocks = BitSet.valueOf(...)`). `plugin/.../farming/FarmBlocks.java:96` ne marque rien, comme le vanilla 0.6.8. Suggestion : appeler `markNeedsSaving()` sur la `ChunkSection` quand `setTicking` rend `true`.
9. **Remplacement de bloc** : `BlockOperations.setBlock(ChunkStore, Ref, x, y, z, id, type, rotation, filler, settings)` (signature identique, `BlockOperations.java:132`) tient maintenant la carte de hauteur par `HeightmapColumn` au lieu de `BlockChunk.updateHeight`, et efface la santé du bloc remplacé (l. 80). Les drapeaux `SetBlockSettings` sont inchangés (classe identique). Pour nous : rien, sinon que la santé est bien effacée à la pose comme à la casse.
10. **Dispatch d'événements** (N [359]) : seul le bus **asynchrone** change (`event/AsyncEventBusRegistry.java`, `handle(...)` et `logConsumerFailure`). Le bus synchrone attrapait déjà chaque écouteur en 0.6.8 (S6 `event/SyncEventBusRegistry.java:144-147`). Tous nos écouteurs sont synchrones (`PlayerDisconnectEvent`, `PlayerReadyEvent`, `StartWorldEvent`, `RemoveWorldEvent`, `ShutdownEvent`, `LoadAssetEvent`) et attrapent déjà `RuntimeException`. `DrainPlayerFromWorldEvent` (N [234]) et `PlayerConnectEvent` passent en asynchrone (`World.java` et `Universe.java`, `dispatchForAsync`) : nous ne les écoutons pas.

## Rien à faire (vérifié, inchangé pour nous)

- **Signatures identiques** (`javap -protected`) pour les 189 autres classes importées, dont : `CustomUIPage`, `InteractiveCustomUIPage`, `PageManager`, `WindowManager`, `Window`, `ContainerWindow`, `ValidatedWindow`, `ContainerBlockWindow`, `UICommandBuilder`, `UIEventBuilder`, `EventData`, `CustomUIEventBindingType`, `CustomPageLifetime`, `ItemContainer`, `SimpleItemContainer`, `EmptyItemContainer`, `ItemStack`, `Message`, `PlayerRef`, `Player`, `Holder`, `Ref`, `Query`, `SystemGroup`, `SystemDependency`, `Order`, `ComponentRegistryProxy`, `EntityEventSystem`, `RefSystem`, `TickingSystem`, `EntityTickingSystem`, `WorldEventSystem`, `DamageEventSystem`, `BuilderCodec`, `KeyedCodec`, `Codec`, `ExtraInfo`, `Config`, `JavaPlugin`, `JavaPluginInit`, `PluginBase`, `PluginManifest`, `PluginIdentifier`, `Semver`, les commandes (`AbstractPlayerCommand`, `AbstractCommandCollection`, `CommandContext`, `RequiredArg`, `OptionalArg`), `PermissionsModule`, `HytalePermissionsProvider`, les événements ECS (`UseBlockEvent`, `PlaceBlockEvent`, `BreakBlockEvent`, `DamageBlockEvent`, `UseEntityEvent`, `InventoryChangeEvent`), `SetBlockSettings`, `ChunkSection`, `FluidSection`, `TransformComponent`, `Teleport`, `BlockType`, `PrefabPreview`, `EntityScaleComponent`, les classes PNJ que nous étendons ou appelons (`SensorBase`, `BuilderSensorBase`, `Builder`, `BuilderSupport`, `BuilderDescriptorState`, `Feature`, `InfoProvider`, `PositionProvider`, `Sensor`, `ExecutionSupport`, `NavState`, `DisplayNameSupport`, `InventoryHelper`, `NPCEntity`, `NPCPlugin`), et tous les paquets de protocole que nous construisons (`BlockChange`, `FluidChange`, `UpdateBlockTypes`, `protocol.BlockType`, `ConnectedBlockRuleSet`, `BenchRequirement`, `CustomPage`, `CustomUICommand`, `CustomUIEventBinding`).
- **Signature changée, sans effet pour nous** : ajouts purs dans `Store`, `CommandBuffer`, `ArchetypeChunk` (hiérarchie), `Item` (`getAbility`), `InventoryComponent` (sections -11 capacités et -12 sac à runes, nos tableaux `HOTBAR_FIRST` et `HOTBAR_STORAGE_BACKPACK` sont intacts), `ItemContainerBlock` (`StayOpenWhenEmpty`, faux par défaut), `EntityEffect`, `EffectControllerComponent` (surcharges avec `owner` ; notre `addEffect(ref, effect, accessor)` de `GlowingBlock.java:105` est inchangé), `ActiveEntityEffect`, `InteractionType` (`Ability4` inséré en 5 : nous n'utilisons que les noms, jamais l'ordinal ; `DynamicBlockTypeFactory` copie une `EnumMap`), `SoundUtil` (surcharges `String`), `ArgTypes`, `Page` (`Chapters`), `BenchBlock`, `Role` et `MotionController` (recul ; nous n'appelons que `getActiveMotionController`, `translateToAccessiblePosition`, `isValidPosition`, `getNavState`), `MapMarkerBuilder` (taille d'icône ; nos `withName`/`build` inchangés), `Velocity`, `DamageCause`, `Damage`, `WeatherResource`, `I18nModule`, `ItemComponent`, `BoundingBox`, `World` (`resendGameplayConfig` ajouté, `validate` retiré ; nos 14 méthodes de `World` appelées sont inchangées), `Universe` (`transferPlayerAsync` : non utilisé), `BlockSection` (`setChunkSection` et `getMaximumHitboxExtent` retirés, non utilisés), `BlockOperations` (surcharges internes ; nos `setBlock`, `testPlaceBlock`, `setBlockInteractionState` sont inchangés), `RawJsonReader` (renommage protégé ; `blockui/.../api/ConfigQuarantine.java:52` n'utilise que `fromPath`), `ChunkStore` (hérite désormais de `ChunkGrid`, où sont `getStore`, `getChunkReference`, `getChunkSectionReferenceAtBlock`, `S7 server/core/universe/world/storage/ChunkGrid.java:77, 417, 913`, même code qu'en 0.6.8), `BlockChunk` (hors A3, rien d'appelé).
- **Corps changés, mêmes effets pour nos appels** : `BlockHarvestUtils.naturallyRemoveBlock`, `getDrops`, `spawnDrops`, `playBlockSound` (la casse passe par `BlockOperations.setBlock` et `updateBlockArea`, l'environnement par `EnvironmentSection`, l'usure par la section de l'objet tenu). Aussi `InventoryUtils.moveItem` et `getSectionById` (règles propres aux sections de capacités -11 et -12, jamais les nôtres), `FillerBlockUtil` (carte de hauteur), `NPCPlugin.spawnNPCWithColumnProbe` (un rôle inconnu rend `FAIL_NOT_SPAWNABLE` au lieu de `FAIL_INVALID_POSITION` ; `HytaleCitizenBodies.java:121` journalise tout résultat non `TEST_OK`), `HytalePermissionsProvider` (valide noms de groupe et nœuds : `OP` et nos nœuds de commande passent `PermissionValidation`, `server/core/permissions/PermissionValidation.java`), `EntityTrackerSystems` (voir point 6).
- **Réflexion** : `domum/plugin/.../runtime/VariantAssets.java:195` lit le champ privé `CommonAssetModule.assets`. Il existe toujours, même type `CachedSupplier<Asset[]>` (S7 `server/core/asset/common/CommonAssetModule.java:77`). `CommonAssetModule.sendAsset` et `CommonAssetRegistry.addCommonAsset`/`getByName` ont la même signature.
- **Identifiants de dépendance** : `Hytale:AssetModule` reste `PluginManifest.corePlugin(AssetModule.class)` (`server/core/asset/AssetModule.java:72`, `common/plugin/PluginManifest.java:337-339`). Pour `Hytale:NPC`, le manifeste intégré n'est pas dans le jar de classes : non vérifié, mais le code de `NPCPlugin` n'a changé que par du bruit de décompilation.
- **Journal, SoundUtil** (N [479]) : les modificateurs de volume au-delà de 1,0 comptent désormais. Nous n'en passons aucun : `playSoundEvent3d(int, SoundCategory, x, y, z, accessor)` et `playSoundEvent2dToPlayer(PlayerRef, int, SoundCategory)` gardent (1, 1).
- **Arrêt** : `ShutdownEvent` et ses étapes sont identiques (`server/core/event/events/ShutdownEvent.java`, déjà `FLUSH_UNIVERSE_RESOURCES = -24` en 0.6.8). Notre `saveAll` sur `ShutdownEvent` reste valable.
- **Autres points des notes, non utilisés chez nous** (vérifié par recherche) : `ClientFeature` réduit (N [694]), `TickProcedure.onTick` (N [646]), `SimpleBlockCommand` (N [480]), `TargetUtil` (N [692]), `EventTitleUtil`/`ShowEventTitle` (N [683], [691]), `WorldChunk.getWorld`/`setBlock`/`breakBlock`/`setTicking` (N [477], [696], [704]), `ChunkAccessor.performBlockUpdate` (N [695]), `IChunkAccessorSync.getBaseBlock` (N [699]), `BlockDataProvider.read*` (N [700]), `WorldNotificationHandler.updateBlockDamage`/`getBlockDamagePacket` (N [481] ; nous n'appelons que `sendBlockParticle`, inchangé), `I18nModule.sendTranslations` (N [751]), `DurabilityOperator` (N [689]), `InstancesPlugin` (N [358]), `Semver` strict (zéros de tête refusés seulement en mode strict ; notre `Semver.fromString(String)` de `PackAssets.java:67` reste non strict, `common/semver/Semver.java:140-142`).

## Protocole et réseau (N)

Nous n'envoyons qu'**un** paquet construit à la main : `UpdateBlockTypes` (`domum/plugin/.../runtime/BlockTypeSynchronizer.java:126`), rempli de `BlockType.toPacket()`. Le constructeur et `protocol.BlockType` ont la même signature ; le contenu vient du serveur U7 à la recompilation. `HytalePreviewPort.java:144` crée des `BlockChange` pour le composant `PrefabPreview`, classe elle aussi inchangée.

Changements de fil U7 qui imposent un client U7 mais ne touchent aucun de nos paquets :

- `SetChunkEnvironments` par section, `SetColumn` sans environnements (N [333]) ;
- `ItemMovementSettings` (N [475]), `ShowEventTitle` (N [683]), `WorldGameplayConfig` (N [684]), beams (N [685]) ;
- `UpdateLanguage.Language` requis (N [743]) ;
- `AssetInitialize` doit annoncer la vraie taille, 256 Mio au plus par asset (N [200]). Le serveur la calcule lui-même depuis `getBlob()` (S7 `CommonAssetModule.java:551, 573, 600`), y compris pour nos textures `FileCommonAsset` de HyDomum ;
- `AssetEditor*` (N [334]) ;
- `UpdateBlockDamage(s)` pour les fissures, désormais envoyés par le serveur (voir A1).

Un client qui demande un asset inconnu est maintenant déconnecté au lieu de provoquer une `NullPointerException` (`CommonAssetModule.java:527-531`). À garder en tête pour HyDomum : `VariantAssets.register` doit inscrire la PNG avant tout envoi (`BlockTypeSynchronizer.publish`). **[in-game]**

## Ce qui reste incertain

- Tout ce qui est marqué **[in-game]** ci-dessus : fissures et réplication (A1), pluie par `EnvironmentSection` (A3), échelle des citoyens, message du suivi d'entités, rayon de fusion des assets U7.
- Le manifeste intégré de `Hytale:NPC` en U7 (hors du jar de classes).
- L'effet réel de A4 dans la console et chez les joueurs opérateurs (lu dans le code, pas vu).
