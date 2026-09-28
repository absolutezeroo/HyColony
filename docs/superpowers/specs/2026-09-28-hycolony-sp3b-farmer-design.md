# HyColony SP3b-2 : le fermier

Conception validée avec l'utilisateur le 2026-09-28. Source MineColonies `version/main`, commit `6b4e03e337fcb886b4ab8cbe21f9b6731818769d`.

Recherches, **à lire avant tout** :
- `docs/research/sp3b-mc-farmer.md` (MC, § 1 à 7). Les renvois « MC § x » de cette spec pointent vers ce fichier ;
- `docs/research/sp3b-hytale-farming.md` (Hytale, § 0 à 6), noté « H § x » ;
- `docs/research/sp3a-mc-logistics-lumberjack.md` § 0 : les règles communes à tout travailleur (vidage, ramassage, outil manquant).

La spec s'appuie sur le socle de fabrication SP3b-1 (`docs/superpowers/specs/2026-09-28-hycolony-sp3b-crafting-design.md`), déjà fusionné.

## Objectif

Une hutte de fermier cultive les champs que le joueur pose, comme dans MineColonies. Sa récolte (cultures et **essence de vie**) va à la colonie, et le fermier fabrique ses propres graines avec l'essence, à l'établi de fermier de sa hutte. C'est la première source renouvelable d'essence de la colonie, et le premier métier construit sur la fabrication.

## Portée

- **Dedans :**
  - la hutte du fermier, avec ses modules :
    - travailleur ;
    - champs ;
    - réglages (« Demander de l'engrais ») ;
    - fabrication ;
  - le bloc « Champ » et son registre au niveau de la colonie ;
  - la fenêtre du champ : graine, rayons, fermier assigné ;
  - l'onglet Champs de la hutte ;
  - l'IA du fermier : labour, plantation, récolte et engrais, puis ses tâches de fabrication ;
  - la partie fermier de `crafting.json` ;
  - un port d'agriculture ;
  - la persistance (schéma 5), les plans de test de la hutte et les assets.
- **Hors portée :**
  - la recherche (`FARMING`, `GREEN_REVOLUTION`, `SOFT_SHOES`), en attendant l'université ;
  - l'arrosage ;
  - les statistiques de la hutte et de la colonie ;
  - le stock minimum ;
  - les vrais schémas de la hutte ;
  - les plants d'arbres, qui iront à un autre métier (herboriste ou planteur) ;
  - la lanterne de l'épouvantail de MC.

## Règles de jeu

Toutes reprises de MC, sauf les écarts listés plus bas. Chaque classe portée cite sa source MC dans sa Javadoc.

### Hutte (`BuildingFarmer`, MC § 1)

- Type `hycolony:farmer`, niveaux 1 à 5. Objet et bloc de hutte : `HyColony_Hut_Farmer` (clé d'id-map `hut.farmer`), sur le modèle des huttes existantes (`HyColony_Hut_Courier.json`).
- **Modules, dans cet ordre :**
  1. `WorkerModule(FarmerJob.TYPE, Stamina, Athletics, 1, false)` : 1 fermier, pas de travail sous la pluie (MC `FARMER_CRAFT`) ;
  2. `CraftingModule("hycolony:farmer", true)` (MC `FARMER_WORK`) ;
  3. `FarmerFieldsModule` ;
  4. `FarmerSettingsModule` : `fertilize` vaut `true` par défaut.
- **Niveau 0** : le fermier reste en préparation, bloqué (MC § 3.3, étape 1).
- **Champs possédés au plus** = niveau de la hutte.
- **Objets gardés** (`keepX` et `getRequiredItemsAndAmount`) : 1 houe, du niveau 0 au niveau d'équipement maximal de la hutte, et 64 de la graine de chaque champ possédé. **Pas de hache** (écart 5).
- **Nourriture** (MC `canEat`) : les citoyens ne mangent pas encore d'objets, donc rien à porter ici. La règle « ni les graines des champs, ni le blé » est notée au backlog, pour le système de faim (SP4).

### Bloc « Champ » (`BlockScarecrow`, MC § 2.1)

- Objet et bloc `HyColony_Field` (clé d'id-map `block.field`).
  - Il reprend le modèle, la texture, la hitbox (`Scarecrow`), l'échelle et l'icône de `Deco_Scarecrow`, avec `VariantRotation: NESW`.
  - Sa recette est celle de `Deco_Scarecrow` : 3 `Wood_All`, 3 `Ingredient_Tree_Sap`, 1 `Plant_Crop_Pumpkin_Item` et 10 essences, à l'établi de fermier, catégorie `Decorative`.
  - Un seul bloc, pas de moitié haute ni basse : la position du champ est celle du bloc.
- **Posé** dans une colonie : `FieldRegistry.add(pos)`, un nouveau champ sans propriétaire ni graine, rayons 5/5/5/5, étape `EMPTY`. Posé hors colonie : c'est un bloc comme un autre, qui n'enregistre rien.
- **Cassé** : le champ est retiré. S'il avait un propriétaire, son module de champs le libère.
- **Utilisé** (clic) : il est enregistré s'il manque (MC `addBuildingExtensionIfMissing`), puis sa fenêtre s'ouvre. Hors colonie, aucune fenêtre ne s'ouvre.
- **Nettoyage** au tick de colonie (toutes les 500 ticks, MC `cleanUpBuildings`) : un champ dont le chunk est chargé et qui est hors de la colonie, ou dont le bloc n'est plus `block.field`, est retiré.

### Champ (`FarmField`, MC § 2.2)

- **État :**
  - `pos` ;
  - `owner` (`Optional<BlockPos>` de la hutte) ;
  - `seed` (`Optional<ItemKey>`) ;
  - `radii` [S, W, N, E], 5 chacun par défaut ;
  - `stage` : `EMPTY`, `HOED` ou `PLANTED`. `nextStage` suit l'ordre et revient au début.
- **Rayons :**
  - `MAX_RANGE = 20` ;
  - un rayon négatif est refusé ;
  - un agrandissement est refusé si `somme − actuel + nouveau > 20` ;
  - un rayon est plafonné à 20.
- **Bouton de rayon** de la fenêtre : `reste = 20 − somme`, puis `nouveau = (actuel % min(actuel + reste, 20)) + 1`. Le rayon cycle 1, 2, …, `actuel + reste`, puis revient à 1.
- **Cases** : `(x, z)` avec `−W ≤ x ≤ E` et `−N ≤ z ≤ S`, sans le centre. Une case est exclue du champ (`isNoPartOfField`) si c'est de l'air, ou si le bloc au-dessus est une clôture, un portillon ou un muret. Ces blocs se reconnaissent par la liste `farming.fieldBarriers` de l'id-map : des identifiants de blocs ou des familles de tags, à choisir en Task plugin d'après les assets.
- Un champ **sans graine ne peut jamais être assigné** (MC `canAssignExtensionOverride`).
- **Graines acceptées** : les objets que le port d'agriculture classe comme graines de culture (`Plant_Seeds_<C>` et `_Eternal`, H § 3.1).

### Module de champs (`FarmerFieldsModule`, MC § 1.3)

- `assignManually` vaut `false` par défaut. Ce qui est persisté :
  - `currentField` ;
  - `checkedFields` (position du champ → jour de colonie du dernier passage utile).
- **Attribution automatique** à chaque tick de colonie de la hutte, si le mode n'est pas manuel : parmi les champs libres de la colonie, dans l'ordre du registre, le premier qui peut être assigné l'est. **Un seul par tick.**
- **Attribution manuelle** : les actions Assigner et Libérer de l'onglet Champs, seulement en mode manuel. Elles demandent la permission `MANAGE_HUTS`.
- **Champ à travailler** (`getExtensionToWorkOn`), ligne à ligne de MC § 1.3 :
  1. le champ courant, s'il existe encore ;
  2. sinon le premier champ possédé absent de `checkedFields`, qui devient le champ courant ;
  3. sinon celui dont le jour est le plus petit, et strictement inférieur au jour de la colonie ;
  4. sinon rien.
- `resetCurrentField` : `checkedFields[courant] = jour`, puis plus de champ courant.
- **Hutte retirée** : ses champs sont libérés **tout de suite** (écart 3).

### IA du fermier (`EntityAIWorkFarmer`, MC § 3)

**Structure.** `FarmerJob extends Job implements Crafter` possède un `CraftingTasks`, persisté. `FarmerAI implements JobAI` est une `TickRateStateMachine` construite comme l'IA d'artisan de test (`TestCrafters`, SP3b-1). Elle réutilise `CraftingWork` pour les états de fabrication et un nouveau composant `FarmWork` pour les états agricoles. Aucun héritage sous `Job` ni `JobAI` (`ArchitectureTest`).

**Constantes** (MC § 3.1) :
- `MAX_BLOCKS_MINED = 64` : actions avant vidage, et récompense d'une fabrication réussie ;
- `DEFAULT_DELAY = 40` ;
- `XP_PER_HARVEST = 0.5`, en plus de `XP_PER_BLOCK = 0.05` par bloc cassé ;
- `MAX_DEPTH = 5` ;
- `getLevelDelay = (int) max(1, 40 − Endurance / 2.0)`, en ticks, après chaque case visitée ;
- borne des cases : `(2 × 20 + 1)² = 1681`.

**États et délais.**
- `IDLE` → `START_WORKING` : toutes les 20 ticks, car `hasWorkToDo` est toujours vrai (MC).
- `START_WORKING` → `decide` : toutes les 5 ticks.
  - Aller à la hutte, et attendre le vidage si `actionsDone ≥ 64`.
  - Avec une tâche de fabrication : les états de `CraftingWork`.
  - Sinon → `PREPARING`.
- `PREPARING` → `prepareForFarming` : toutes les 20 ticks.
- `FARMER_HOE`, `FARMER_PLANT` et `FARMER_HARVEST` → `workAtField` : toutes les 5 ticks.
- États de fabrication : ceux de `CraftingWork` (`GET_RECIPE`, `QUERY_ITEMS`, `GATHERING_REQUIRED_MATERIALS`, `CRAFT`), aux délais de SP3b-1.
- `INVENTORY_FULL` : le vidage commun. Il se déclenche après chaque passage sur un champ (`shouldDumpInventory`) ou à 64 actions.

**`prepareForFarming`** (MC § 3.3), dans l'ordre :
1. Hutte absente ou de niveau < 1 → bloqué.
2. **Engrais**, en place de l'étape compost de MC (écart 1) :
   - aucun `Tool_Fertilizer` utilisable (pas usé, `ItemCatalog.wornOut`) ni dans la hutte ni dans l'inventaire, réglage activé, et pas de requête d'engrais ouverte → une `StackRequest(Tool_Fertilizer, 1, 1)` ;
   - un engrais dans la hutte mais pas dans l'inventaire → `GATHERING_REQUIRED_MATERIALS` pour 1 engrais.
3. Aucun champ possédé → bloqué, avec le message « Placez d'autres champs pour me faire travailler » (MC `entity.farmer.nofreefields`, par le mécanisme de message existant), puis `IDLE`.
4. Champ à travailler absent → `IDLE`.
5. Houe manquante → `ToolRequests.requestTool(HOE)`, bloqué.
6. Selon l'étape :
   - `PLANTED` et une case récoltable → `FARMER_HARVEST` ;
   - `HOED` → `canGoPlanting` (MC § 3.3) ;
   - `EMPTY` et une case à labourer → `FARMER_HOE` ;
   - sinon `nextStage`. Au 4e saut d'affilée (`skippedState`), `resetCurrentField`, puis `IDLE`.
7. `checkIfShouldExecute` avance `workingOffset` sur la première case qui passe le test.
   - Les tests de préparation **n'ont aucun effet de bord** : ni plante détruite, ni engrais dépensé (écart 2).
   - La destruction de la plante qui gêne et l'engrais se font pendant le passage.

**`canGoPlanting`** (MC § 3.3) :
- La graine du champ est dans l'inventaire → `FARMER_PLANT`.
- Sinon, aller à la hutte, en y prenant jusqu'à 64 graines.
- Sinon, une `StackRequest(graine, 64, 1)` asynchrone si aucune n'est ouverte, et l'étape passe à `PLANTED` sans planter.
- La requête de graines est résolue par la **fabrication de la hutte elle-même** (résolveur public de SP3b-1), avec l'essence de ses coffres, ou par l'entrepôt.

**`workAtField`** (MC § 3.4) :
- Aller à 4 blocs de la case (`scarecrow Y − 1`), houe en main.
- **Case à labourer** : l'éventuelle plante gênante est cassée, avec ses drops au sol, puis la case est labourée par le port. La houe perd 1 de durabilité. Houe cassée ou absente en plein passage : la case est sautée, comme MC.
- **Case à planter** : si la graine manque dans l'inventaire → `PREPARING`.
- **Case à récolter** : si c'est encore en cours → on reste.
- `setDelay(getLevelDelay)` après **chaque** case visitée.
- Passage fini :
  - `shouldDumpInventory` ;
  - `nextStage` ;
  - si `didWork` ou au 4e saut, `resetCurrentField` ;
  - puis `IDLE`.
- **Ordre des cases** : la spirale `nextValidCell` de MC § 3.4, recopiée à l'identique. `cell`, `workingOffset` et `prevPos` sont persistés sur la hutte.

**Actions par case** (MC § 3.5, adaptées à Hytale) :
- **Surface** (`getSurfacePos`) : la recherche de MC sur ±5 blocs.
  - « Solide » = `BlockKind.SOLID` (catalogue), ou un fluide.
  - Les cultures sont non solides : un bloc de culture n'est jamais la surface.
- **Labour** (`findHoeableSurface`) : rien à faire si :
  - il n'y a pas de surface ;
  - la case est exclue ;
  - une culture ou le bloc Champ est au-dessus ;
  - la surface n'est pas labourable (les 16 sols de H § 2.1) ;
  - ou elle est **déjà labourée**.
- **Labourer** :
  - casser le bloc au-dessus s'il est remplaçable : +1 action et +0,05 XP s'il n'est pas vide ;
  - `till(surface)` par le port ;
  - 1 de durabilité à la houe ;
  - `didWork`.
- **Engrais** (écart 1) : juste après le labour d'une case, ou sur une case déjà labourée pendant un passage de plantation. Si le réglage est activé et que la case n'est pas engraissée, on utilise 1 `Tool_Fertilizer` de l'inventaire : 1 de durabilité, puis `fertilize(surface)`. Aucun engrais dépensé en préparation.
- **Plantation** (`findPlantableSurface`) : rien à faire si :
  - il n'y a pas de surface ;
  - la case est exclue ;
  - une culture est déjà au-dessus ;
  - la surface est le bloc Champ ;
  - ou la surface **n'est pas labourée**.

  Sinon, planter : `plant(surface.above(), graine)` par le port, retirer 1 graine, `didWork`. Pas d'XP.
- **Récolte** (`findHarvestableSurface`) :
  - la culture au-dessus est **mûre** selon le port (stade final, H § 3.3) ;
  - `harvest` par le port : ses drops vont dans l'inventaire du fermier (le reste au sol s'il est plein, comme `WorkerStock.storeDrops`) ;
  - +1 action, +0,05 XP (bloc cassé), +0,5 XP (récolte), `didWork` ;
  - une culture **éternelle** repousse sur place (H § 3.4, voie 3), une culture normale laisse la case vide.

  Pas d'engrais instantané : Hytale n'en a pas (écart 1).
- **Fin de passage** : un vidage, qui garde la houe, 1 engrais et 64 graines par champ, puis la demande de ramassage commune.

### Fabrication du fermier (SP3b-1, `crafting.json`)

```json
"hycolony:farmer": {
  "allow": [ { "bench": "Farmingbench", "categories": ["Seeds", "Essence"] } ],
  "includeItems": [],
  "excludeItems": [],
  "custom": [
    { "id": "farmer_seeds_wheat", "hytaleRecipe": "<id Hytale de Plant_Seeds_Wheat>", "minBuildingLevel": 1, "maxBuildingLevel": 5 }
  ]
}
```

- **Recettes intégrées** (MC `CustomRecipe`) : une par graine normale de H § 5, avec le `minBuildingLevel` qui fait que la table du plan a le niveau requis.
  - Niveau de hutte L = niveau de table L.
  - Blé et laitue au niveau 1, carotte et maïs au 2, chou-fleur et navet au 3, aubergine et citrouille au 4, tomate et piment au 5.
  - Coton, riz, pomme de terre et oignon demandent une table de niveau 6 ou 7 : ils s'apprennent à la main si un jour la table le permet, sinon jamais.
- Les **identifiants Hytale réels** des recettes sont relevés dans le catalogue, en Task plugin (`/hycolony selftest` ou le journal). Les recettes d'objet s'appellent `<objet>_Recipe_Generated_0`.
- **Plans de test de la hutte** (`styles.json`, hors vrais schémas) :
  - chaque niveau L reprend un prefab vanilla du style, avec un `Bench_Farming` de niveau L ajouté par le script `tools/prefabs/add_bench.py` ;
  - ce script copie le prefab dans le pack HyColony et ajoute la case avec le composant `BenchBlock.TierLevel` ;
  - il place la table sur une case de sol intérieure, libre, près du bloc de hutte.

### Fenêtres

- **Onglet Champs** de la hutte (MC `FarmFieldsModuleWindow`, MC § 5.1) :
  - le bouton du mode d'attribution (manuel ou automatique) ;
  - « n/max champs utilisés » ;
  - une ligne par champ libre ou possédé, triée possédés d'abord, puis par distance. Chaque ligne a :
    - l'icône de la graine ;
    - la distance et la direction ;
    - l'étape actuelle et la suivante ;
    - le bouton Assigner/Libérer : actif seulement en mode manuel, grisé avec son motif (« limite atteinte » ou « pas de graine »).
- **Onglet Réglages** : « Demander de l'engrais ». S'il n'existe pas encore d'onglet de réglages générique, un bouton dans l'onglet Champs suffit.
- **Fenêtre du champ** (MC `WindowField`, MC § 5.2) :
  - « Fermier : <nom> » ou « Fermier : personne » ;
  - le bouton « Choisir la graine », qui ouvre la liste des graines du port avec leur icône et leur nom ;
  - l'icône de la graine actuelle ;
  - les 4 boutons de rayon, qui affichent le rayon et cyclent comme plus haut. Leur info-bulle donne la direction (nord, sud, ouest, est) ; la direction relative au joueur de MC est un écart (6).
  - La biome et le climat de MC sont sans objet (pas de biomes de culture dans Hytale).
- Chaque bouton appelle une action du cœur (`FieldActions`, `FarmerActions`), qui vérifie la permission `MANAGE_HUTS` et ré-affiche la vue (CLAUDE.md § 7).

### Port d'agriculture (`farming/FarmingAccess`, implémenté par `HytaleFarming`)

Un port ne lève jamais d'exception. Ses méthodes :
- `boolean isTillable(BlockPos)` et `boolean isTilled(BlockPos)` ;
- `boolean till(BlockPos)` : pose `Soil_Dirt_Tilled` sans le bit 2 (H § 4) ;
- `boolean isFertilized(BlockPos)` et `boolean fertilize(BlockPos)` (`TilledSoilBlock`, H § 4) ;
- `CropState crop(BlockPos)` : `NONE`, `GROWING` ou `MATURE` ;
- `boolean plant(BlockPos, ItemKey seed)` : pose le bloc de culture de la graine (`BlockTypeToPlace`), sans le bit 2 ;
- `List<ItemAmount> harvest(BlockPos)` : les drops de récolte, puis la case vide pour une culture normale, ou le retour au `Stage1` pour une éternelle (H § 3.4) ;
- `boolean isFieldBarrier(BlockPos)` ;
- `List<ItemKey> seeds()` : les graines de culture, triées.
- `boolean isFieldBlock(BlockPos)` : le bloc « Champ » est à cette position (pour le nettoyage) ;
- `ItemKey fertilizerItem()` : l'objet engrais (`Tool_Fertilizer`, depuis l'id-map).

Il vit dans `farming` (et non `kernel/port`, déjà à 15 fichiers), comme `RecipeCatalog`. `ConstructionPorts` le reçoit.

## Écarts à MineColonies

À documenter par `Deviation from MC:` et à reporter au § 11 de la spec SP1+2.

1. **Engrais.** `Tool_Fertilizer` (5 utilisations) engraisse la case pour de bon et double la vitesse de pousse. Il remplace le compost et la poudre d'os de MC, qui font mûrir tout de suite, parce que Hytale n'a pas d'équivalent en survie. La requête porte sur 1 `Tool_Fertilizer`, pas sur une `StackList` compost/poudre d'os de 64.
2. **Préparation sans effet de bord.** MC dépense de l'engrais et détruit des plantes pendant la simple préparation ; ici, tout se fait pendant le passage.
3. **Hutte retirée → champs libérés tout de suite.** MC ne les libère qu'au chargement suivant.
4. **Liste des champs du jour sauvegardée.** MC l'écrit et la relit sous deux clés différentes, et la perd donc au chargement.
5. **Pas de hache gardée** : le fermier de MC en garde une qu'il n'utilise jamais.
6. **Fenêtre du champ :**
   - pas de biome ni de climat, et pas de direction relative au joueur ;
   - le bloc Champ tient sur un seul bloc (pas de moitié haute) ;
   - pas de lanterne.
7. **Graines fabriquées par la hutte.** Une culture Hytale mûre ne rend pas sa graine (H § 0). Le fermier redemande des graines, que sa propre fabrication résout avec l'essence récoltée. Ses recettes de graines sont intégrées selon le niveau de sa table.
8. **Maturité et pousse de Hytale.** Les cultures poussent au temps (48 minutes réelles environ), sans lumière ni eau obligatoire. Le fermier ne fait que lire la maturité.
9. **Pas de recherche** (doublement de récolte, etc.) tant que l'université n'existe pas.

## Architecture

- **Cœur**, nouveau paquet racine `dev.hycolony.core.farming`, au plus 15 fichiers par sous-paquet :
  - `farming` : `FarmingAccess` (le port) et `CropState` ;
  - `farming/field` :
    - `FarmField` (état), `FieldStage`, `FieldRadii` (règles des rayons) ;
    - `FieldCells` (la spirale `nextValidCell`) ;
    - `FieldRegistry` : les champs de la colonie, leur JSON et leur nettoyage ;
  - `farming/hut` : `FarmerHut` (type de bâtiment), `FarmerFieldsModule`, `FarmerSettingsModule`, `FieldChoice` (`getExtensionToWorkOn`) ;
  - `farming/job` :
    - `FarmerJob`, `FarmerAI`, `FarmerState` ;
    - `FarmWork` (les étapes agricoles), `FieldScan` (surface et tests de case), `FieldPass` (le passage d'une case) ;
  - `colony/action/FieldActions` (fenêtre du champ, onglet Champs) ;
  - `colony/ui/FieldView` et `colony/ui/tab/FieldsView` ;
  - `UiPort.showField`.
- **Existant modifié :**
  - `Colony` : le registre en champ, accès `fields()`, sans nouveau fichier dans `colony` ;
  - `ColonySerializer` et `MigrationChain` : schéma 5, avec `"fields": []` et une fixture v4 ;
  - `ColonySerializer.heal` : un propriétaire disparu est libéré ;
  - `ConstructionPorts` : ajout de `farming` ;
  - `CoreFeatures` : enregistrement du type et du métier ;
  - `FarmerFieldsModule` implémente `BuildingEventsModule` : son `onRemoved` libère les champs de la hutte, comme `WorkerModule` renvoie ses travailleurs ;
  - `ToolType` gagne `HOE` (le plugin reconnaît les `Tool_Hoe_*`).
- **Plugin :**
  - `HytaleFarming` : le port ;
  - `block/FieldBlockSystems` : pose, casse et utilisation du bloc Champ, sur le modèle de `HutBlockSystems` et `FlowerPotSystem` ;
  - `ui/FieldPage` : la fenêtre du champ et son sélecteur de graines ;
  - `ui/hut/FieldsTab` : l'onglet Champs ;
  - les assets `HyColony_Hut_Farmer.json` et `HyColony_Field.json` ;
  - l'id-map et `styles.json` ;
  - le script `tools/prefabs/add_bench.py` et les prefabs générés ;
  - les clés de langue en-US et fr-FR ;
  - `crafting.json` ;
  - le selftest.

  Chaque API Hytale est vérifiée dans les sources décompilées et notée au `plugin-b-api.md` § 27.

## Test

- **Cœur (TDD)** : chaque règle a son test.
  - Rayons et cycle du bouton.
  - Spirale : les 8 premières cases du premier anneau dans l'ordre de MC ; toutes les cases d'un champ 5/5/5/5, et aucune hors du champ.
  - Choix du champ et verrou du jour ; attribution automatique (un par tick, jamais sans graine, pas au-delà du niveau).
  - Hutte retirée → champs libérés.
  - Étapes et `skippedState`.
  - Labour, plantation, récolte (normale, éternelle), engrais.
  - Requêtes de graines et d'engrais.
  - Vidage.
  - Persistance et migration v4 → v5.
- **Scénario complet**, avec `FakeFarming`, une hutte niveau 1 avec une table `Farmingbench` de niveau 1, un champ de blé et de l'essence dans la hutte : le fermier laboure, plante, attend la maturité (forcée dans le fake), récolte, puis refabrique ses graines avec l'essence récoltée quand elles manquent.
- **En jeu** : `docs/TESTING.md`, section « Fermier », qui déroule aussi les points 159 à 172 de la fabrication.
