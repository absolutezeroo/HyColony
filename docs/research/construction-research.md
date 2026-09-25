# Construction et requêtes : recherche complémentaire (2026-09-25)

Ce document complète `minecolonies-analysis.md` (§5 requêtes, §6-7 jobs et construction) et `hytale-api-spike.md`.

## MineColonies : règles côté joueur

### Commander une construction
- La fenêtre de la cabane contient un bouton Construire / Améliorer / Réparer / Déconstruire. Quand un ordre existe déjà, ce bouton sert à **l'annuler**.
- **Permission** : tous les messages exigent `MANAGE_HUTS`.
- **Upgrade** :
  - il faut `level < maxLevel` (5) ;
  - pour un bâtiment enfant : `level < parent.level`, ou parent au niveau max ;
  - la recherche peut plafonner le niveau (hors périmètre ici) ;
  - **aucune règle « niveau ≤ hôtel de ville »**.
- **Création d'un ordre de travail**, refusée dans ces cas :
  - un ordre existe déjà à cette position ;
  - aucun constructeur employé n'a `niveau ≥ cible` et le bâtiment n'est pas la propre cabane du constructeur (`BUILDER_NECESSARY`) ;
  - aucun constructeur n'est à moins de 100 blocs (`BUILDER_TOO_FAR_AWAY`) ;
  - l'emprise n'est pas entièrement dans le territoire (`OUT_OF_COLONY`).
  Une fois l'ordre créé, un message est envoyé aux joueurs.
- **Priorité** : tri par priorité décroissante, puis par id croissant. L'onglet info de l'hôtel de ville permet de monter ou descendre un ordre, ou de le supprimer.
- **Attribution** : toutes les 20 ticks, les ordres déjà réclamés passent en premier. Chaque ordre va au **premier** constructeur employé, inactif, pas en mode MANUAL, qui vérifie `canBuild`. Ce n'est pas forcément le plus proche.
- Le joueur peut choisir un constructeur précis (liste déroulante). La cabane du constructeur a un mode AUTO/MANUAL ; en MANUAL, le constructeur choisit lui-même dans la liste des ordres.

### Constructeur
- **Embauche automatique** toutes les 500 ticks, si le module n'est pas plein (1 constructeur) et que le mode d'embauche est AUTO, ou DEFAULT avec l'embauche auto de l'hôtel de ville (vrai par défaut). Le citoyen choisi est le premier sans travail et non enfant.
- `canAssignCitizens` vaut `level > 0 && isBuilt`, **sauf pour la cabane du constructeur**, qui peut toujours embaucher, même au niveau 0.
- **Règle de réclamation d'un ordre** : `niveauConstructeur ≥ cible`, ou constructeur niveau 5, ou sa propre cabane. `canBeBuiltByBuilder(n)` vaut `n == level + 1` pour sa propre cabane.
- **Distance** : 100 blocs en 3D (`MAX_DISTANCE_SQ = 10000`) pour la réclamation automatique.
- **Cabane posée** : elle devient un bâtiment de niveau 0, non construit, avec un rayon de claim de 0. Le constructeur **construit d'abord sa propre cabane**.
- **Nouveau bâtiment** : étapes CLEAR, BUILD_SOLID, WEAK_SOLID, CLEAR_WATER, CLEAR_NON_SOLIDS, DECORATE, SPAWN. L'upgrade et la réparation sautent CLEAR.

### Ressources et requêtes
- La fenêtre des ressources affiche pour chaque objet la quantité dispo / nécessaire (inventaire du constructeur + cabane), le manque, ce que le joueur a sur lui, et un bouton « ajouter ». Code couleur : rouge = le joueur n'en a pas, orange = insuffisant, vert = suffisant.
- **Besoins** : 4 passes (SOLID, WEAK_SOLID, DECO, ENTITIES), et chaque bloc posé les décrémente de 1.
- **Seaux** : taille maximale = slots de l'inventaire du constructeur − 9 piles. On ne demande que le seau courant et le suivant, et rien pendant CLEAR.
- **Bouton « ajouter »** : il transfère `min(manque, ce que le joueur possède)` vers l'inventaire de la cabane, puis appelle `overruleNextOpenRequestWithStack`.
- **Bouton « Fournir »** (fenêtre des requêtes du citoyen) : il apparaît si le joueur a un objet correspondant. Il donne `min(demandé, possédé)` à l'**inventaire du citoyen**, puis passe la requête en OVERRULED. Une quantité partielle clôt quand même la requête, et le constructeur redemande le reste.
- Les objets déposés dans la cabane ne sont pas liés à une requête. Le constructeur se sert dans la cabane, et `BuildingRequestResolver` résout les requêtes à partir de ce stock.
- **Résolveur retrying** : 3 essais espacés de 1200 ticks, avant de passer au joueur.

### Stockage, défrichage, fin de chantier
- **Conteneurs d'une cabane** = le bloc de la cabane + les racks **posés par le constructeur** (un rack posé à la main n'est pas enregistré).
- **Minage** : les drops vont dans l'inventaire du constructeur. Il les dépose dans la cabane quand il est plein ou toutes les 4096 actions, en gardant les objets nécessaires.
- **CLEAR** : les blocs sont minés et leurs drops **gardés, sauf les minerais (détruits)**, avec un temps de minage ×0,5. Il ignore l'air, les fluides, les blocs indestructibles et le bloc de la cabane.
- **Rotation** : elle est calculée à partir de l'orientation du bloc de la cabane. L'ancre du plan est le bloc de la cabane (`primaryOffset`), et l'origine monde = position de la cabane − offset.
- **Fin de chantier** :
  - XP : +8, plus 0,05 par bloc posé ;
  - entrée dans le journal (Built / Upgraded / Repaired / Deconstructed) ;
  - message au joueur avec direction et distance ;
  - niveau mis à jour, `isBuilt` à vrai ;
  - claims recalculés.
- **Rayons de claim** (en chunks) :
  - défaut : niveau 1-3 → 1, niveau 4-5 → 2 ;
  - **hôtel de ville** : niveau 1-2 → 1, 3 → 2, 4 → 3, 5 → 5 ;
  - tour de garde : niveau 1 → 2, 2-3 → 3, 4 → 4, 5 → 5.
- **REPAIR** : même niveau, pas de CLEAR. **REMOVE** : pas de matériaux, le bâtiment passe à « déconstruit » en gardant son niveau. **Annulation** : les requêtes et la progression sont annulées, les blocs posés restent.

## Hytale 0.6.8 : API de construction

- **Prefab** :
  ```java
  Path p = PrefabStore.get().findAssetPrefabPath("Npc/Outlander/Houses/Tier1/Outlander_Houses_Tier1_001.prefab.json");
  IPrefabBuffer b = PrefabBufferUtil.getCached(p);
  b.forEach(IPrefabBuffer.iterateAllColumns(), (x,y,z,blockId,holder,support,rotation,filler,call,fluidId,fluidLevel) -> ..., null, null, new PrefabBufferCall(random, PrefabRotation.ROTATION_90));
  ```
  - Les coordonnées sont **relatives à l'ancre et déjà tournées**. La position monde = origine + (x, y, z).
  - La rotation d'un bloc est un index de `RotationTuple`.
  - Bornes : `getMinX/MaxX/MinZ/MaxZ(rot)` et `getMinY/MaxY()`.
- **Pose d'un bloc** : `BlockOperations.testPlaceBlock(...)` puis `BlockOperations.setBlock(chunkStore, sectionRef, x, y, z, id, type, rotationIndex, 0, 0)`, en posant **uniquement les entrées `filler == 0`** (les fillers sont générés automatiquement).
  - Block entity du prefab (coffre avec contenu) : `BlockEntity.setBlockEntity(store, sectionRef, blockComponentSection, x, y, z, type, rotation, holder.clone())`.
  - Filtrer `Block_Spawner_Block` et `Editor_*`.
- **Casse avec drops** : `naturallyRemoveBlock(..., SetBlockSettings.NO_DROP_ITEMS /*2048*/, ...)`, puis `BlockHarvestUtils.getDrops(type, qty, itemId, dropListId)`, avec les valeurs de `type.getGathering().getBreaking()`.
  - **Attention** : le contenu d'un conteneur tombe au sol quoi qu'il arrive. Il faut le vider avant de casser.
- **Objets** :
  - `ItemStack(id, qty)`, `getItem().getMaxStack()` ;
  - `ItemContainer.addItemStack` → `getRemainder()`, `removeItemStack(new ItemStack(id, n))`, `countItemStacks(pred)`, `forEach` ;
  - coffre : `BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, x, y, z).getItemContainer()`.
- **Inventaire du joueur** : `InventoryComponent.getCombined(accessor, ref, InventoryComponent.HOTBAR_FIRST)`. L'ancien `Inventory` est déprécié.
- **Inventaire d'un PNJ** : clés `InventorySize` et `HotbarSize` du rôle. Objet en main : `InventoryHelper.setHotbarItem` + `setHotbarSlot`.
- **Animations** : `AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Block", "Build", acc)` ou `"Pickaxe"` / `"Mine"`. Le résultat côté client reste à vérifier en jeu.
- **Aperçu** : `DebugUtils.addCube/addLine` (visible par tous dans le monde ; à vérifier hors créatif) ou des particules.
- **Maisons vanilla** (clé = chemin sous `Server/Prefabs/`), progression Outlander :

  | Niveau | Prefab | Blocs |
  |---|---|---|
  | 1 | `Npc/Outlander/Houses/Tier0/..._005` | 768 |
  | 2 | `Tier1/..._001` | 997 |
  | 3 | `Tier2/..._001` | 1470 |
  | 4 | `Tier2/..._003` | 2575 |
  | 5 | `Tier3/..._005` | 7029 |

  Kweebec Redwood : `Small_Plot_House_001` (334) → `002` (650) → `Normal_Plot_House_001` (841) → `002` (1830) → `Medium_House_002` (≈6300).
