# Update 7 (0.7.0-pre.4) : axe D2, monde, blocs, objets et systèmes de jeu

Recherche du 2026-09-29. Compare le serveur 0.6.8 épinglé (`build/vineflower/hytale-server`, Javadocs injectées ignorées : seul le code est comparé) au serveur Update 7 décompilé (noté **U7** ci-dessous, chemins relatifs à `com/hypixel/hytale/` du dossier `scratchpad/u7/src`). Assets : `release-0.6.8-Assets.zip` (même taille que `install/release/.../Assets.zip`, 60 695 entrées) contre `install/pre-release/.../Assets.zip`. Notes de version : `scratchpad/u7/notes.txt` (citées « notes, l. N »). Compilation de notre code contre U7 : `scratchpad/u7/compile.log`.

Tout ce qui n'est vérifié que dans le code, sans essai en jeu, est marqué **[in-game]**.

## Résumé

- **3 erreurs de compilation** (`compile.log`) et **1 erreur Error Prone à venir** (`@RestrictedApi`), toutes dans deux adaptateurs : fissures de blocs (`HytaleWorldEffects`), point d'apparition et pluie (`HytaleWorldQuery`).
- Tout le reste de l'axe D2 passe déjà par l'API de sections (`ChunkStore.getChunkSectionReferenceAtBlock`, `BlockSection`, `BlockOperations`), qui est précisément la cible de la migration U7. Il n'y a ni chargement forcé ni borne 0..319 dans notre code.
- **Sauvegardes** : un monde ouvert par U7 n'est plus lisible par 0.6.8 (`BlockSection` v7, `BlockChunk` v4). Sauvegarder `run/universe` avant tout essai U7.
- Les mondes par défaut **ne sont pas cubiques** en U7. Les écarts « toute hauteur » ne concernent que les mondes RocksDB ou `CubicTest`.

## Bloquant

### B1. Fissures des blocs (`WorldEffects.blockHit`) : `BlockHealthChunk` est remplacé par `BlockHealthSection`

- Notre code : `plugin/.../adapter/HytaleWorldEffects.java:149-168` (`crack`) lit `BlockHealthModule.get().getBlockHealthChunkComponentType()` sur la colonne, puis appelle `health.damageBlock(Instant, World, Vector3i, float)`. `compile.log` : « cannot find symbol » aux lignes 155 et 166, et avertissement de retrait à la ligne 154.
- Preuve U7 :
  - `server/core/modules/blockhealth/BlockHealthModule.java:59` n'expose plus que `getBlockHealthSectionComponentType()` ;
  - `BlockHealthSection.java` offre `getHealth(x,y,z)` (1.0 si intact) et `damage(x, y, z, float amount, Instant now)`, qui renvoie la nouvelle santé. `damage` **efface l'entrée** si la santé n'est plus « endommagée » (`BlockHealth.isDamaged`, `0 < h < 1`) ;
  - `BlockHealthSection.getComponentType()` est statique ;
  - la réplication vers les clients est faite par `BlockHealthSystems.ReplicateChanges` à partir des `pendingDeltas` : il n'y a plus de paquet à envoyer (notes, l. 481) ;
  - vanilla `BlockHarvestUtils.java:973-1004` : `getComponent(sectionRef, BlockHealthSection.getComponentType())`, `damage(...)`, puis `ChunkSection.markNeedsSaving()`.
- Correctif (même comportement) :
  ```java
  BlockHealthSection health = chunks.getComponent(sec, BlockHealthSection.getComponentType());
  ChunkSection section = chunks.getComponent(sec, ChunkSection.getComponentType());
  if (health == null || section == null) return;
  float current = health.getHealth(pos.x(), pos.y(), pos.z());
  float damage = current - Math.max(MIN_HEALTH, 1f - progress);
  if (damage > 0) { health.damage(pos.x(), pos.y(), pos.z(), damage, time.getNow()); section.markNeedsSaving(); }
  ```
  Le `MIN_HEALTH = 0.05f` reste indispensable : à 0, `damage` efface l'entrée et le bloc paraîtrait réparé. Le `new Vector3i` de la clé disparaît (index `short`), c'est une allocation de moins par coup. La régénération est inchangée : 5 s, puis 0.1/s (`BlockHealthSystems.java`, `tickRegeneration(now, dt, 5L, 0.1F)`). Affichage des fissures **[in-game]**.

### B2. Point d'apparition (`WorldQuery.spawnPoint`) : `ISpawnProvider` devient asynchrone

- Notre code : `plugin/.../adapter/HytaleWorldQuery.java:48` appelle `provider.getSpawnPoint(world, player)` (« cannot find symbol »). Appelant : `core/.../app/action/HutActions.java:96-113` (MC `CreateColonyMessage`, distance 2D au spawn).
- Preuve U7 : `server/core/universe/world/spawn/ISpawnProvider.java` ne déclare plus que `CompletableFuture<Transform> getSpawnPointAsync(World, UUID)`. `GlobalSpawnProvider:36` et `IndividualSpawnProvider:45` rendent un futur déjà complété. `FitToHeightMapSpawnProvider` ne recale Y que si Y vaut la sentinelle `Integer.MIN_VALUE` et compose alors avec `getChunkReferenceAsync(..., 32)`.
- Correctif : `Transform t = provider.getSpawnPointAsync(world, player).getNow(null);`, puis `Optional.empty()` si le futur n'est pas encore complet. Seuls X et Z servent (distance 2D), et le recalage ne touche que Y. Ne **jamais** faire `join()` : on est sur le thread du monde, et le futur peut attendre un chargement de chunk sur ce même thread.

### B3. Pluie (`WorldQuery.isRainingAt`) : `BlockChunk.getEnvironment(x,y,z)` est `@RestrictedApi`

- Notre code : `HytaleWorldQuery.java:72-80`. Ce n'est qu'un avertissement javac dans `compile.log`, mais c'est une **erreur Error Prone** une fois B1 et B2 corrigés. Error Prone tourne dans notre build (`build-logic/src/main/kotlin/hy.java-checks.gradle.kts:25-36`), et `RestrictedApi` y est en erreur par défaut.
- Preuve U7 : `server/core/universe/world/chunk/BlockChunk.java:203-210` porte `@RestrictedApi(allowedOnPath = ".*/BuilderTools/.*")` et renvoie 0 hors de `0 <= y < 320`. Le remplacement est `server/core/universe/world/chunk/section/EnvironmentSection.java:88` `get(x,y,z)`. `WeatherResource.java:43` `getEffectiveWeatherIndex(environmentId)` fait exactement notre logique « forcée sinon celle de l'environnement ». Vanilla : `WeatherTracker.java:170` et `WaterGrowthModifierAsset.java:235`.
- Correctif :
  ```java
  Ref<ChunkStore> sec = HytaleSections.section(world, pos);
  EnvironmentSection env = sec == null ? null : store.getComponent(sec, EnvironmentSection.getComponentType());
  if (env == null) return false;
  int index = weather.getEffectiveWeatherIndex(env.get(pos.x(), pos.y(), pos.z()));
  ```
  Cela retire l'import `BlockChunk`. Les ids de `precipitationParticles` (id-map) sont toujours ceux des météos U7 : 25 systèmes, aucune pluie ni neige non listée. Seuls `Ash_Storm`, `Sand_Storm` et le nouveau `Goblin_Void_Anomaly_Storm` restent hors liste, à juste titre.

## À migrer (compile encore, mais marqué pour retrait ou incomplet)

| Où | Quoi | Preuve U7 | Action |
|---|---|---|---|
| `HytaleWorldQuery.java:36-38` `isLoaded` | Teste la **colonne** (`getChunkReference(indexChunkFromBlock)`) | `ChunkGrid.java:508` : les sections ont leur propre table (`chunkSections`), `highestLoadedSectionAbove` | Tester la section (`HytaleSections.section(world,pos) != null`). Ça ne change rien en monde non cubique. En monde cubique, `CitizenManager.java:162` pourrait sinon faire apparaître un corps dans une section absente. |
| `HytaleItemCatalog.java:228-239` `toolType` | Les nouveaux `GatherType` `Metals` (30 blocs : `Metal_Goblin_Iron*`…) et `GoblinMetal` (`Deco_Scrap_Pile_Shiny`) donnent `null` : le bâtisseur casse sans outil | Assets U7 : les spécifications des `Tool_Pickaxe_*` incluent `Metals` et `GoblinMetal` (absents en 0.6.8) | Ajouter `Metals` et `GoblinMetal` → `PICKAXE` |
| `farming/FarmBlocks.java:91-98` `tick` | `BlockSection.setTicking` sans `markNeedsSaving` | notes, l. 695 : « BlockSection.setTicking with ChunkSection.markNeedsSaving for one cell » (vanilla `FertilizeSoilInteraction` ne le fait pas non plus) | Facultatif : marquer la section, pour que le tick survive au déchargement |
| `block/BenchTiers.java:23-54` | Imite `CraftingManager.finishTierUpgrade`, qui en U7 appelle aussi `BenchBlock.notifyTierUpgraded(world, pos, tier)` (`CraftingManager.java:860`), écouté par `AugmentBlocksPlugin.java:45-61` | `BenchBlock.java` (nouveau `TierUpgradedListener`) | Appeler `BenchBlock.notifyTierUpgraded` après `setBlockInteractionState`, sinon la progression des blocs d'augmentation n'avance pas avec une amélioration faite par le bâtisseur **[in-game]**. `ProcessingBenchBlock.setupSlots`, déjà présent en 0.6.8 et absent chez nous, est un écart antérieur à U7. |
| imports `BlockHealthChunk`, `BlockChunk` | `@Deprecated(forRemoval)` | `compile.log` | Disparaissent avec B1 et B3 |

## Changement de comportement (sans changement de code chez nous)

1. **Feuillage posé par un joueur** (notes, l. 83 et 182) : `UseDefaultDropWhenPlaced` est retiré de 47 assets (buissons, baies, herbe, feuilles, mousses, vignes, roseaux, algues, toiles d'araignée). Un joueur qui casse un feuillage de colonie obtient désormais bâtons ou baies, plus la plante. Nos citoyens n'étaient pas concernés : `HytaleBlocks.drops` (`adapter/HytaleBlocks.java:84`) ignore ce drapeau et donnait déjà la liste normale. Les feuilles au sol `Plant_Leaves_{Autumn,Jungle,Poisoned}_Floor` gagnent une entrée `Shears` dans `Gathering.Tools` (diff des assets).
2. **Outil de `Wood_Oak_Trunk_Stairs`** : `GatherType` passe de `Rocks` à `Woods` (diff de l'asset, notes, l. 125). Le bâtisseur prend donc une hache au lieu d'une pioche. Les toits `Metal_{Bronze,Copper,Iron,Zinc}_Roof` passent de `SoftBlocks` à `Rocks`, donc à la pioche. Deux drapeaux passent de `Woods` à `Benches` (aucun outil chez nous).
3. **Cultures et pluie** (notes, l. 118 et 226) : `WaterGrowthModifierAsset` regarde désormais la heightmap. Il ne cherche plus un bloc quelconque au-dessus de la culture jusqu'à y = 320. Une culture sous verre ou sous feuilles reçoit donc la pluie. Le diff 0.6.8 → U7 remplace la boucle `searchY < 320` par `heightmapColumn.getHeight(x,z) <= worldY`. C'est l'inverse de Minecraft, où `Level.isRainingAt` s'arrête au premier bloc `MOTION_BLOCKING`, verre compris (connaissance de Minecraft vanilla, non vérifiée dans ce dépôt). Il n'y a rien à changer : la croissance est celle de Hytale, et notre `isRainingAt` (citoyens abrités, MC `Level.isRaining`) raisonne par environnement, pas par bloc.
4. **Explosions et monde sans casse** :
   - U7 `ExplosionUtils.java:199-201` saute les blocs si `!worldConfig.isBlockBreakingAllowed(blockType)`, **avant** `performBlockDamage` ;
   - notre `ExplosionProtectionSystem` (`block/ExplosionProtectionSystem.java:35`) n'est donc plus appelé dans ces mondes, ce qui revient au même ;
   - les entités y sont désormais blessées (notes, l. 418) ;
   - l'hypothèse de notre Javadoc (l. 17-19) tient toujours : `DamageBlockEvent` sans entité ne vient que de `ExplosionUtils.java:208`, les deux appels de `BreakBlockInteraction` passent `ref`.
5. **Santé des blocs remise à zéro à chaque remplacement** : U7 `BlockOperations.setBlock` appelle `BlockHealthModule.onBlockReplaced` (`BlockOperations.java:31-127`, `BlockHealthModule.java:63-70`). Une fissure disparaît donc aussi si un citoyen pose un bloc par-dessus (`placeQuietly`). En 0.6.8, seul `removeBlock` l'effaçait.
6. **Casse d'un multi-bloc tourné et voisins** : `BlockHarvestUtils.removeBlock` lit la rotation **avant** la suppression et passe par `BlockOperations.updateBlockArea`, qui traverse les sections de façon asynchrone. Il n'agit que si `PERFORM_BLOCK_UPDATE (256)` est posé. Nos cassées de citoyens ne le posent pas (`HytaleWorldBlocks.java:180,186`, `HutPickUp.java:82`) : aucun changement pour elles. Les cassées de joueurs sur des blocs de colonie mettent désormais bien à jour leurs voisins **[in-game]**.
7. **Établis améliorés** : « Upgraded workbenches … keep their new look » (notes, l. 579). Un établi monté par `BenchTiers` ne devrait plus reprendre son ancien aspect quand un joueur s'en sert **[in-game]**.
8. **Français côté client** (notes, l. 35) : U7 livre `Server/Languages/fr-FR/server.lang` (8 861 lignes, contre 11 986 en en-US). 0.6.8 n'avait pas de fr-FR vanilla, seulement pt-BR, ru-RU, uk-UA et zh-CN. Nos clés `hycolony.lang` fr-FR deviennent donc visibles pour un client français. Les clés absentes retombent sur en-US (`I18nModule.getMessages(Map, String)`, fusion avec en-US ; `fallback.lang` : `fr-CA = fr-FR`, etc.). `SeedPickerPage.java:78` (`I18nModule.getMessage(lang, key)`, inchangée) affichera les noms vanilla en français. Contrôle en jeu des textes fr **[in-game]**.
9. **Écouteurs d'événements** (notes, l. 359) : si un autre plugin lève une exception, nos écouteurs `StartWorldEvent`, `RemoveWorldEvent` et `ShutdownEvent` (`HyColonyPlugin.java:87-98`) sont quand même appelés. C'est plus robuste, sans action de notre part.

## Hauteur du monde (mondes cubiques)

- **Grep** de `core/`, `plugin/`, `domum/` et `blockui/` : pas de 319, 320, `WORLD_HEIGHT`, `maxY` ou `minY` de monde, ni de balayage du haut vers le bas. Les seules constantes de hauteur sont locales (`FieldScan.MAX_DEPTH = 5` pour MC `getSurfacePos`, `RouteSearch.HALF_HEIGHT`, `HytaleWorldEffects.HEIGHT = 8` pour les feux d'artifice). Les claims sont en X/Z comme dans MC.
- Tous nos accès aux blocs passent par la section (`HytaleSections.section`, `BlockOperations.setBlock/testPlaceBlock/setBlockInteractionState`, `BlockHarvestUtils.naturallyRemoveBlock`, `BlockModule.getComponent`). Ils répondent donc à toute hauteur en U7. Seules exceptions : `isLoaded` (colonne, voir « À migrer ») et `getEnvironment` (B3, plafonné à 0..320).
- **Quand un monde est cubique** : `ChunkGrid.supportsCubicSections()` (U7 `ChunkGrid.java:103`) est vrai si le chargeur implémente `IChunkLoader.Cubic` (seul `RocksDbChunkStorageProvider.Loader`, l. 903) ou si le générateur est `IWorldGen.Cubic` (seul `CubicTestWorldGenProvider`). Le stockage par défaut reste `IndexedStorageChunkStorageProvider` (`DefaultChunkStorageProvider`), donc un monde normal garde les sections Y 0..9 (`World.java:1061-1063` : `JoinWorld(minSectionY = 0, maxSectionY = 9)`).

## Sauvegardes

- **Un monde ouvert en U7 n'est plus lisible en 0.6.8.**
  - `BlockSection` passe de `codecVersion(6)` à `(7)` (U7 `BlockSection.java:59-63`) et `BlockChunk` de 3 à 4 (grep `codecVersion`). Nouveaux composants : `EnvironmentSection` et `BlockHealthSection` (v0).
  - 0.6.8 `BuilderCodec.decodeVersion` (`codec/builder/BuilderCodec.java:658-659`) lève `IllegalArgumentException("Version 7 is newer than expected version 6")`. `ChunkStore` 0.6.8 journalise alors « Failed to load chunk! » (l. 1309, 1327).
  - Les notes le confirment (l. 336 : « a world loaded on this version cannot be opened on an older one » ; l. 260 : les mondes « upgrade themselves the first time they load »). La conséquence exacte en 0.6.8 (trou, régénération ou refus) est **[in-game]**.
- **Conseil** : avant le premier lancement en U7, copier `run/universe` (et `run/prefabs` si l'on y a édité des prefabs) hors du dépôt. Tester U7 sur une copie, jamais sur le monde de travail 0.6.8. Ne jamais rouvrir en 0.6.8 un monde passé par U7.
- **Notre persistance JSON** : les colonies sont dans `world.getSavePath().resolve("hycolony")` (`WorldRuntime.java:122`, `FileColonyStorage`). U7 ne touche pas ces fichiers. Les positions sont des `BlockPos` entiers, sans borne de Y. Il n'y a pas de migration à écrire, et `schemaVersion` et `MigrationChain` restent tels quels. La sauvegarde sur `RemoveWorldEvent` et `ShutdownEvent` est inchangée : mêmes constantes d'étapes `ShutdownEvent` (-56 à -24) dans les deux versions.
- **HyDomum** : `variants.json` (hors monde) n'est pas touché. Les blocs dynamiques sont enregistrés par clé dans le `BlockSection` : ils suivent la même règle v7.

## Opportunités, par système MineColonies

| Système MC | Ce que U7 apporte | Verdict |
|---|---|---|
| **Outils du bâtisseur et du bûcheron** (MC `WorkerUtil.getBestToolForBlock:142-146` : `IForgeShearable` → `shears` si `USE_SHEARS`, `AbstractBuilding.java:110`, par défaut `true` via `getSettingValueOrDefault`) | Le feuillage posé ne se rend plus lui-même sans cisailles. Les feuilles au sol acceptent `Shears`. | **À faire** pour la fidélité : ajouter `SHEARS` à `ToolType` (`core/.../kernel/item/ToolType.java`), le choisir pour les blocs dont `Gathering.Tools` contient `Shears`, et faire rendre à `HytaleBlocks.drops` l'entrée `Tools` de l'outil tenu (`BlockGathering.getToolData()`, inchangée). Sinon, raser un décor feuillu ne rend pas les feuilles au stock du bâtisseur. |
| **Voisins après une cassée ou une pose du bâtisseur** (MC `setBlock(..., UPDATE_ALL)`) | `updateBlockArea` correct à travers les sections et pour les multi-blocs tournés | À étudier, et **antérieur à U7** : nos cassées et poses n'ont pas `PERFORM_BLOCK_UPDATE`, alors que le joueur l'a (`BlockHarvestUtils.java:427-428`, `BlockPlaceUtils.java:418-421`). Ajouter 256 exige aussi `BlockPhysics.markDeco` pour les blocs `canBePlacedAsDeco` (comme `BlockPlaceUtils.java:437`), sinon le feuillage du bâtisseur pourrait tomber **[in-game]**. |
| **Résidence et lits** (MC `BedHandlingModule`, `docs/research/sp4-sleep-home.md` § lits) | Tous les lits partagent la racine `Block_Bed` (`Server/Item/RootInteractions/Block/Block_Bed.json`, `Tags.Type = ["Bed"]`, 16 lits l'utilisent) | Utile pour SP4 : reconnaître un lit par l'interaction `Use` = `Block_Bed` (ou par le tag `Type=Bed`), plutôt que par une liste d'ids |
| **Carte** (MC n'a pas de carte, mais une intégration JourneyMap : `core/compatibility/journeymap/v6/JourneymapOptions.java:35-47`, bordures de colonie, nom de colonie, noms des colons, points de mort) | `MapMarkerBuilder.withIconSize(MapMarkerIconSize.Major)` et `withCompassImage` (diff `MapMarkerBuilder`). `BlockMapMarker` gagne `IconSize` et `CompassIcon` (déclaratif dans `BlockEntity.Components`, exemple `Goblin_Breach_Portal.json`). `MapMarkerOverride.DisplayName`. Teinte de chunks par joueur montrée par `WildernessDebugMapSystem` (`UpdateWorldMap` + `MapImage`). | Ajout possible, à valider par l'utilisateur : un marqueur `Major` à l'hôtel de ville avec le nom de la colonie, via un `MarkerProvider` comme `HighlightMarkers.java:24-31`, et des bordures de claim teintées. `getImageIfInMemory` et `clearChunks` existent déjà en 0.6.8 : seul l'exemple est nouveau. |
| **Protection et claims** (MC permissions par rang) | `WorldConfig.BlockBreakingBypassTag` (`WorldConfig.java:30, 117-122`) et `BlockedRootInteractionTags` agissent sur tout le monde, pas par joueur | Pas d'usage fidèle. La « pick up » de hutte et les cassées de citoyens passent par `naturallyRemoveBlock` et ignorent déjà `AllowBlockBreaking`. |
| **Explosions** (MC `TurnOffExplosionsInColonies` : `DAMAGE_PLAYERS` et `DAMAGE_NOTHING` protègent aussi les entités) | `ExplosionConfig.EntityDamageCause` (`ExplosionUtils.java:341-343`) | L'écart reste : aucun asset vanilla ne renseigne `EntityDamageCause` (grep des assets U7), donc toute explosion blesse avec `Environment`, sans source distincte. |
| **Stockage et coursier** | `ItemContainerBlock.StayOpenWhenEmpty` ; `ItemComponent.get/setMergeRadius` | Pas d'intérêt : ça ne concerne que l'état visuel d'un conteneur vide à la fermeture de la fenêtre et le rayon de fusion des objets au sol. Le défaut reste 2.0 (`ItemEntityConfig.java:71` contre `ItemMergeSystem.RADIUS = 2.0F` en 0.6.8), et nous ne ramassons pas encore d'objets au sol. |
| **Événements de colonie** (MC : messages de chat, `MessageUtils`) | `EventTitleUtil` avec `EventTitleStyle` (les surcharges booléennes sont dépréciées) | Pas de besoin : nous n'utilisons pas `EventTitleUtil`, et MC passe par le chat |
| **Météo** (MC : un seul drapeau `isRaining` pour tout le monde) | `WeatherTracker.tryClaimOverride` et `releaseOverride` (météo forcée **par joueur**) | Pas d'usage fidèle. `getEffectiveWeatherIndex` simplifie B3. |
| **Aperçu et pose de plan** (MC Build Tool) | `BlockSelection.computePlacementBounds` | Pas d'usage : nos bornes viennent de `IPrefabBuffer.getMin/Max` (`HytaleBlueprintSource.java:189-191`) et nous ne manipulons pas de `BlockSelection` |
| **Blocs de hutte et de champ** | `TickProcedure` (nouvelle signature, tout Y), `BlockSection.forEachTicking` | Pas d'usage : les huttes MC ne tiquent pas comme blocs, c'est la colonie qui les fait avancer. Nous n'avons aucune `TickProcedure`. |
| **Traductions** | `queueTranslations`, `resolveLanguage` | Rien à faire : nous n'envoyons pas de traductions nous-mêmes |

**Contournements que U7 rend natifs** : aucun chargement forcé, aucune aide de section maison au-delà de `HytaleSections` (deux méthodes, toujours utiles). Seul changement de méthode : la météo à une position. La paire « forcée sinon environnement » de `isRainingAt` devient `WeatherResource.getEffectiveWeatherIndex`.

## Performance

- Gains côté serveur sans action de notre part : tick des blocs, fluides et terres labourées (notes, l. 406) ; cassée côté serveur sans chargement de chunk (l. 562) ; tampons de sauvegarde en pool (l. 355) ; carte chargée par morceaux (l. 561) ; `FarmingSystems.Ticking` passe par un `TickContext` en `ThreadLocal` au lieu d'un lambda capturant (diff).
- Chez nous : B1 supprime l'allocation d'un `Vector3i` par coup de fissure. Nos chemins chauds (`HytaleWorldBlocks.get/place`) sont déjà sans lookup de `WorldChunk`.
- Coût ajouté par U7 dans `BlockOperations.setBlock` : `HeightmapColumn.onBlockChanged` (heightmap en arbre) et un `getComponent` de `BlockHealthSection` par pose. C'est négligeable à notre cadence **[in-game]**.

## Vérifié sans impact

- **Signatures inchangées** (compilation et diff du code) :
  - `ChunkStore.getChunkSectionReferenceAtBlock` (déplacé dans `ChunkGrid.java:913`, sans chargement) ;
  - `BlockOperations.setBlock` à 10 arguments, `testPlaceBlock`, `setBlockInteractionState` ;
  - `BlockSection.get`, `getRotationIndex`, `getFiller`, `setTicking` : aucun n'est `@RestrictedApi` ;
  - `BlockHarvestUtils.naturallyRemoveBlock`, `getDrops`, `performPickupByInteraction` (diff nul hors blancs) ;
  - `BlockModule.getBlockEntity/getComponent` ; `BlockGathering` ; `FillerBlockUtil.unpackX/Y/Z` ; `ItemContainerBlock` (seul `StayOpenWhenEmpty` s'ajoute) ;
  - `PrefabStore`, `PrefabBufferUtil.getCached`, `IPrefabBuffer`, `PrefabBufferCall`, `PrefabRotation`, `BlockSpawner` et `BlockSpawnerTable` (diffs de décompilation seulement) ;
  - `WorldTimeResource` ; `MapMarkerBuilder` et `MarkerProvider.update` ; `I18nModule.getMessage` ; `BlockType`, dont le constructeur de copie utilisé par HyDomum (diff vide) ;
  - lieux de déclenchement de `BreakBlockEvent`, `PlaceBlockEvent`, `UseBlockEvent` et `DamageBlockEvent` (mêmes méthodes).
- **Pot de fleurs** : `FlowerPotUse.java:31` `SWAP_SETTINGS = 260` est toujours la valeur de vanilla `ChangeStateInteraction` (`SET_SETTINGS = 260`). La correction « ChangeState sans son » ne nous concerne pas.
- **Hutte ramassée** (`HutPickUp`) : même chemin `naturallyRemoveBlock` + `NO_DROP_ITEMS`. La note « Picking up blocks … no longer trigger the interaction » vise les blocs portables, pas ce chemin.
- **Terres labourées et engrais** : `BlockModule.getComponent(TilledSoilBlock…)` et `ensureBlockEntity` sont identiques dans les deux versions.
- **Plans** : les 28 prefabs vanilla de nos `styles.json` (`plugin/src/subplugins/*/hycolony/styles.json`) existent en U7 **avec la même taille** que 0.6.8. Les 10 prefabs `HyColony/Farmer/*` sont les nôtres. Aucun n'utilise un id retiré par U7 (`Furniture_Ancient_Chest_Large_Treasure`, `Glider`, etc.).
- **id-map** : tous les ids d'assets existent en U7. Seuls deux assets de la section `farming` changent : `Soil_Mud` (mouvement) et `Rock_Gold_Brick_Wall` (icône). Rien qui touche le fermier.
- **Rien d'utilisé chez nous** : `TickProcedure`, `SimpleBlockCommand`, `TargetUtil`, `WorldChunk`, `IChunkAccessorSync.*`, `ClientFeature`, `DurabilityOperator`, `ItemMergeSystem.RADIUS`, `EventTitleUtil`, `WeatherTracker`, `ChunkPreLoadProcessEvent`, `DrainPlayerFromWorldEvent`, chargement forcé de chunk (grep), et les clés d'assets retirées (`IsMajor`, `ChargeAcceleration`, `Carryable_Block`, `TreasureMap`…).
- **Dossier d'installation** : U7 refuse de démarrer si l'univers est dans le dossier d'installation du launcher (notes, l. 665). Notre `run/` est dans le dépôt, donc pas concerné.
- **HyDomum et HyBlockUI** compilent contre U7 (`compile.log` : `domum-plugin:compileJava` et `blockui:compileJava` passent, Error Prone compris).
