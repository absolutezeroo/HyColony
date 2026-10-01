# MC : comment un bâtiment connaît ses conteneurs (étagères de l'entrepôt)

Question : par quels chemins MineColonies ajoute-t-il une position aux conteneurs d'un bâtiment (ceux que `TileEntityWareHouse.dumpInventoryIntoWareHouse` / `getRackForStack` remplissent) ? Un bloc déjà conforme au plan est-il enregistré ? Un coffre vanilla compte-t-il ? La liste est-elle persistée, reconstruite, vidée ?

Sources (clones partiels `--depth 1` de la branche `version/main`, lus le 2026-09-30) :
- `MC/` = `ldtteam/minecolonies`, commit `477ff1d29130a1680c9b08278304c05014109750`, préfixe `src/main/java/com/minecolonies/` ;
- `ST/` = `ldtteam/Structurize`, commit `7dd863c8d1c1e3bc4813ed0dd4b7f1b0bffe173d`, préfixe `src/main/java/com/ldtteam/structurize/`.

L'inventaire des appelants vient d'un `grep -rn "registerBlockPosition|addContainerPosition|removeContainerPosition|containerList.(add|clear|remove)"` sur les deux arbres complets.

## 1. La liste et ses deux points d'entrée

- `MC/core/colony/buildings/AbstractBuildingContainer.java` l. 46 : `protected final Set<BlockPos> containerList = new HashSet<>()`.
- `addContainerPosition` (l. 128-131) n'a **qu'un appelant** : `registerBlockPosition(Block, …)` l. 174.
- `getContainers()` (l. 140-145) renvoie `containerList` puis la position de la hutte (le bloc de hutte est toujours un conteneur : `MC/api/tileentities/AbstractTileEntityColonyBuilding.java` l. 26, `extends TileEntityRack`).
- `registerBlockPosition(Block, pos, world)` (l. 155-181) :
  - `AbstractBlockHut` : réglage du pack, du miroir et du parent de la hutte enfant (l. 157-171) ;
  - **`BlockMinecoloniesRack` seulement** : `addContainerPosition(pos)` puis `rackEntity.setBuildingPos(getID())` (l. 172-180).
  - Aucun autre bloc (coffre vanilla, tonneau…) n'est ajouté.
- `MC/core/colony/buildings/AbstractBuilding.java` l. 1415-1419 : la surcharge appelle `super`, puis `IModuleWithExternalBlocks.onBlockPlacedInBuilding` pour chaque module (postes de travail, lits…).
- `MC/core/colony/buildings/workerbuildings/BuildingWareHouse.java` l. 121-136 : pour un rack, `setInWarehouse(true)` et `upgradeRackSize()` jusqu'au niveau d'amélioration courant (`WarehouseModule.getStorageUpgrade()`), puis `super`.
- D'autres bâtiments surchargent `registerBlockPosition` (alchimiste, archerie, caserne, académie, composteur, bétonnière, fleuriste, cimetière, hôpital, bibliothèque, école, université), mais pour leurs blocs de travail : aucun n'appelle `addContainerPosition`.

## 2. Qui appelle `registerBlockPosition` (trois appelants, tous pilotés par le plan)

| Appelant | Quand | Source |
|---|---|---|
| `BuildingStructureHandler.triggerSuccess` | le constructeur traite une position du plan | `MC/core/entity/ai/workers/util/BuildingStructureHandler.java` l. 193-200 |
| `CreativeBuildingStructureHandler.triggerSuccess` | collage créatif d'une hutte (outil de construction) | `MC/api/util/CreativeBuildingStructureHandler.java` l. 95-128 (appel l. 127) |
| `ItemAssistantHammer` | un joueur pose avec le marteau d'assistant un bloc d'un ordre de travail | `MC/core/items/ItemAssistantHammer.java` l. 330-338 (appel l. 336) |

Point important : l'état passé est **celui du plan**, pas celui du monde (`getBluePrint().getBlockState(pos)`, `BuildingStructureHandler.java` l. 196 ; `blueprint.getBlockState(pos)`, `CreativeBuildingStructureHandler.java` l. 127). Une position n'est donc enregistrée que si **le plan** y met un rack.

### 2.1 Un bloc déjà conforme au plan est enregistré

`triggerSuccess` a deux appelants dans Structurize :
- `ST/placement/StructurePlacer.java` l. 364 : après une pose réelle, `triggerSuccess(localPos, requiredItems, true)` ;
- **`ST/placement/AbstractBlueprintIterator.java` l. 91-117 (`iterateWithCondition`)** : pour chaque position que l'itérateur parcourt, si elle n'est pas écartée par la condition de l'étape (l. 105) et que `!isRemoving() && IPlacementHandler.doesWorldStateMatchBlueprintState(...) && info.getEntities().length == 0` (l. 109), il appelle **`structureHandler.triggerSuccess(progressPos, Collections.emptyList(), false)`** (l. 111) et passe à la suivante.

Le `return SUCCESS` sans `triggerSuccess` de `StructurePlacer.java` l. 319-322 n'est qu'un second contrôle, pour une position que l'itérateur a déjà jugée non conforme (`NEW_BLOCK`), ou une position qui porte une entité.

Donc un rack déjà en place et conforme au plan est enregistré dès que le constructeur **parcourt** sa position, sans rien poser. Dans `BuildingStructureHandler.triggerSuccess`, `placement == false` saute seulement l'XP, les statistiques et la réduction des ressources (l. 202 et suivantes) ; l'enregistrement (l. 197-200) a lieu dans les deux cas.

Les étapes de pose couvrent toutes les positions du plan (`MC/core/entity/ai/workers/AbstractEntityAIStructure.java` l. 364-428) :
- `BUILD_SOLID` : `increment(this::skipBuilding)`, qui écarte `!canBlockFloatInAir || isDecoItem || DONT_TOUCH` (l. 554-560) ;
- `WEAK_SOLID` : écarte `!isWeakSolidBlock || DONT_TOUCH` (l. 375-381) ;
- `DECORATE` : `skipDecorate` écarte `(!isDecoItem && isAnySolid) || DONT_TOUCH` (l. 540-544).

Avec `isAnySolid = canBlockFloatInAir || isWeakSolidBlock` (`ST/util/BlockUtils.java` l. 858-861), chaque position est vue par au moins une de ces étapes, sauf `DONT_TOUCH_PREDICATE` (l. 109-116 : bloc `IBuilderUndestroyable`, bedrock, la hutte elle-même).

Le collage créatif suit le même itérateur (`ST/util/TickedWorldOperation.java` l. 233-257 : `increment(...)` par solidité), donc un rack déjà en place y est aussi enregistré par `CreativeBuildingStructureHandler.triggerSuccess`.

### 2.2 Chemins qui n'enregistrent rien

- **Joueur qui pose un rack** : `MC/core/blocks/BlockMinecoloniesRack.java` n'a ni `setPlacedBy` ni `onPlace` (méthodes l. 83-296 : `updateShape`, `use`, `newBlockEntity`, `onRemove`…). Les `setPlacedBy` de MC (`grep`) concernent la hutte, le portail, la bannière, le panneau, le contrôleur de décor, les tombes, le champ, l'épouvantail. `MC/core/event/EventHandler.java` n'écoute aucun événement de pose (seulement `BlockEvent.BreakEvent` l. 551 et `FarmlandTrampleEvent` l. 806). `BlockEvent.EntityPlaceEvent` n'est écouté que par `ColonyPermissionEventHandler` l. 102 (permissions) et `QuestObjectiveEventHandler` l. 120 (quêtes).
- **Le rack lui-même** : `AbstractTileEntityRack.updateWarehouseIfAvailable` (`MC/api/tileentities/AbstractTileEntityRack.java` l. 122-152) prévient les requêtes ou le bâtiment quand son contenu change, mais n'ajoute pas de position. `updateShape` du bloc (l. 152-217) ne fait que fusionner deux racks voisins en rack double.
- **Fin de niveau** : `AbstractBuilding.onUpgradeComplete` (l. 940-976) ne touche pas `containerList` (revendication des chunks, coins, requêtes d'outils bloquées, modules, prestige).
- **Hutte posée sur un bâtiment déjà construit** : `RegisteredStructureManager.addNewBuilding` (`MC/core/colony/managers/RegisteredStructureManager.java` l. 562-573) appelle `upgradeBuildingLevelToSchematicData` (`AbstractSchematicProvider.java` l. 529-566), qui lit le niveau dans les données du plan de la hutte et appelle `onUpgradeComplete(null, level)` **sans parcourir le plan** : aucun rack n'est enregistré. Il faudra un passage du constructeur (réparation, amélioration). `BuildingWareHouse.requestRepair` (l. 65-80) le suggère : « To ensure that the racks are all set to in the warehouse when repaired ».

## 3. Coffres vanilla

- **Ils ne deviennent jamais des conteneurs d'un bâtiment** : le seul `addContainerPosition` est derrière `block instanceof BlockMinecoloniesRack` (`AbstractBuildingContainer.java` l. 172-174).
- **L'entrepôt ne remplit que des racks** (`MC/core/tileentities/TileEntityWareHouse.java`) :
  - `dumpInventoryIntoWareHouse` (l. 128-165) prend `getRackForStack` ; s'il ne trouve rien, il envoie le message « entrepôt plein » (au plus une fois toutes les 5 min, l. 139-160) ;
  - `getRackForStack` (l. 173-184) : un rack avec le même objet, puis un rack avec un objet semblable, puis le rack le plus vide ;
  - les deux premiers filtrent `instanceof AbstractTileEntityRack` (l. 196-205 et 223-232), `searchMostEmptyRack` filtre `instanceof TileEntityRack` (l. 246-270).
  - La hutte en fait partie (elle est un `TileEntityRack`), un coffre vanilla jamais.
- Un joueur qui pose un coffre ou un rack dans l'entrepôt ne l'enregistre donc pas (§ 2.2). Un rack posé **là où le plan met un rack** sera enregistré au prochain passage du constructeur (§ 2.1). Un rack posé ailleurs ne l'est jamais.
- Restes : `AbstractBuilding.forceTransferStack` (l. 1233-1245) et `MC/core/entity/ai/workers/AbstractEntityAIBasic.java` (l. 1129) testent `ChestBlockEntity` sur les positions de `containerList`. Aucun chemin actuel n'y met de coffre ; ce sont des branches mortes, ou pour de vieilles sauvegardes (hypothèse, non vérifiée).

## 4. Persistance, retrait, amélioration

- **Persistée** : `serializeNBT` écrit `containerList` sous `TAG_CONTAINERS = "Containers"` (`AbstractBuildingContainer.java` l. 102-109 ; `MC/api/util/constant/NbtTagConstants.java` l. 135). `deserializeNBT` la relit (l. 79-84). Elle est aussi envoyée à la vue client (`AbstractBuilding.java` l. 703-706).
- **Jamais reconstruite** par un scan au chargement ou à l'amélioration, et **jamais vidée** : aucun `containerList.clear()` dans MC (le `clear` de `AbstractBuildingView.java` l. 439 est la copie côté client). Une amélioration garde les anciennes positions et y ajoute les racks que le nouveau plan fait parcourir (§ 2.1).
- **Seul retrait** : `TileEntityColonyBuilding.getCapability` (`MC/core/tileentities/TileEntityColonyBuilding.java` l. 624-660). Quand il construit l'inventaire combiné de la hutte, pour chaque position chargée : si c'est un `AbstractTileEntityRack`, il l'ajoute et refait `setBuildingPos` ; si c'est **un autre bloc à entité** (un coffre vanilla par exemple), `removeContainerPosition(pos)` ; **sans entité** (air, bloc simple), la position reste. Ce cache est invalidé l. 455-458.
- **Amélioration de stockage** (`BuildingWareHouse.upgradeContainers`, l. 165-180) : +9 cases à chaque `TileEntityRack` de `getContainers()` sauf la hutte ; les racks enregistrés plus tard sont remis au même niveau par `registerBlockPosition` (l. 121-136).

## 5. Comparaison avec HyColony

- `BuilderBlockWork.place` (`core/.../construction/builder/BuilderBlockWork.java` l. 200-214) n'enregistre un conteneur ou un banc qu'**après une pose**. `StructureScan.needsWork` (`StructureScan.java` l. 52-82) saute la position déjà conforme sans rien enregistrer. `PasteQueue.placeNext` (`core/.../app/wand/PasteQueue.java` l. 91-108) fait de même (`return true` l. 100, avant `placed`/`register`).
- MC, lui, enregistre aussi la position **parcourue et déjà conforme** (`AbstractBlueprintIterator.java` l. 109-111 → `triggerSuccess(..., false)` → `registerBlockPosition`), avec les modules (bancs). Écart corrigé le 2026-10-01 : `PlannedBlocks.foundAsPlanned` (constructeur) et `PasteQueue.registerFound` (collage) enregistrent le conteneur et le banc du plan quand la position est trouvée déjà satisfaite, aux étapes de pose (SOLID, DECORATE) seulement, comme MC (`!isRemoving()`), sans rien écrire dans le monde : un banc trouvé garde le niveau qu'il a (`WorldBlocks.benchTier`), et un banc déjà enregistré garde son entrée.
- Correspondance Hytale : chez nous, `hasContainer` couvre tout bloc à `ItemContainerBlock` (`sp3a-warehouse-courier-hytale.md` § 4.1), donc un coffre Hytale du plan joue le rôle du rack de MC. Ce choix est déjà acté ; MC n'enregistre que ses racks.
- Comme chez MC, un coffre posé par le joueur hors plan n'est pas un conteneur du bâtiment. Le symptôme rapporté (« entrepôt plein » alors que le bâtiment contient des coffres) colle avec des coffres du plan **déjà présents** quand le constructeur ou le collage est passé. C'est le cas d'un collage par-dessus un bâtiment existant, d'une amélioration qui garde les coffres, d'un chantier repris. Ce diagnostic n'est pas reproduit **[in-game]**.
- Piste à vérifier : deux petits coffres Hytale voisins deviennent un `*_Chest_Large` (`connected-blocks.md`). Si le plan pose deux petits coffres côte à côte, l'état du monde peut ne plus être « satisfait » ou n'avoir d'entité qu'à une des deux positions. Non vérifié **[in-game]**.
