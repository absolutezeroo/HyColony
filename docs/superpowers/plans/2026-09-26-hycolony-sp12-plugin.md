# HyColony SP1+2 — Plan B : plugin (requêtes et construction en jeu)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** brancher le cœur SP1+2 (terminé, 276 tests) sur Hytale 0.6.8 pour qu'un joueur puisse poser une cabane de constructeur, commander un bâtiment et voir un PNJ le construire à partir d'un prefab vanilla.

**Architecture:** uniquement des adaptateurs dans `:plugin` (ports & adapters). Le cœur ne change que pour des corrections révélées par le branchement, toujours avec un test.

**Tech stack:** Java 25, Gradle 9.5.1, Hytale Server 0.6.8 (release, épinglé).

**Spec:** `docs/superpowers/specs/2026-09-25-hycolony-sp12-requests-construction-design.md` § 7-9.
**API vérifiée :** `docs/research/plugin-b-api.md` (à suivre ; ce qui y est marqué **[in-game]** est à confirmer en jeu).
**À savoir :** `.superpowers/sdd/2026-09-25-hycolony-sp12-core/plan-b-must-know.md` (contraintes issues des relectures du cœur).

## Global Constraints

- Hytale 0.6.8, API par sections uniquement (pas de méthodes dépréciées `World`/`WorldChunk`).
- Tout appel au cœur et aux ports se fait **sur le thread du monde**. Les événements de page UI arrivent déjà sur ce thread (comme en SP0).
- Les ports **ne lèvent jamais d'exception** : chunk non chargé → 0 / vide / `false` / reste complet.
- Les identifiants d'assets ne vivent que dans `hycolony/id-map.json` (validés au démarrage et par `selftest`).
- `./gradlew build` doit rester vert (cœur + compilation du plugin).
- On ne lance **jamais** le serveur Hytale : c'est l'utilisateur qui relance et teste en jeu.
- Commits : chemins explicites uniquement ; fin de message avec les deux lignes de trailer de la session.

## Review Focus

1. Chunk déchargé pendant un chantier : `get` vide, `place`/`breakBlock` sans effet, pas d'exception, le constructeur attend.
2. Outil usé dans un coffre ou l'inventaire du joueur : l'extraction se fait par emplacement et par identifiant, jamais par `ItemStack` égal.
3. Coffre cassé par le constructeur : son contenu revient au constructeur (vidé avant la casse), rien ne tombe au sol.
4. Fenêtre « Stockage » fermée : on la retire de la table des fenêtres du bloc (sinon elle ne se rouvre plus) et on appelle `onContainerChanged`.
5. Prefab introuvable ou style inconnu : `load` renvoie vide → refus `NO_BLUEPRINT`, pas de plantage.

---

### Task B1 : cabanes Builder / Residence, systèmes de cabane génériques, clés de langue

**Files :**
- Create : `Server/Item/Items/HyColony/HyColony_Hut_Builder.json`, `HyColony_Hut_Residence.json`, `Server/Item/Interactions/HyColony/HyColony_Hut_Use.json`, `Server/Item/RootInteractions/HyColony/HyColony_Hut_Use.json` (modèle : cheat sheet § 8, capacité 27, sans `ConnectedBlockRuleSet`).
- Modify : `HyColony_TownHall.json` (ajoute le `BlockEntity` `ItemContainerBlock` : la cabane est le premier conteneur du bâtiment), `hycolony/id-map.json` (`hut.builder`, `hut.residence`, items et blocs), `.lang` en-US et fr-FR (noms des cabanes, `hycolony.workorder.refused.<enum en minuscules>` pour chaque `WorkOrderRefusal`, `hycolony.workorder.created`, `hycolony.build.complete`, `hycolony.storage.unavailable`, les types de journal `buildingBuilt/Upgraded/Repaired/Deconstructed`, `debrisLost`, `buildingPlaced`, `buildingRemoved`).
- Rename : `block/TownHallBlockSystems` → `block/HutBlockSystems`, générique : table `blockId → BuildingType` construite depuis `BuildingTypes`/`hutBlockKey` et l'`IdMap`.
  - Place : `checkHutPlacement(player, pos, type.id())` ; `FoundNewColony` seulement pour l'hôtel de ville ; `Allowed` → `placeHut`.
  - Break : comme SP0, pour toute cabane.
  - Use (`UseBlockEvent.Pre`, annulé) : hôtel de ville → `openTownHall`, autre → `openBuilding`.
- Modify : `HyColonyPlugin` (enregistre les systèmes renommés), `ProtectionSystems` si elle référence l'hôtel de ville en dur.

- [ ] Compile, commit `feat(plugin): builder and residence huts, generic hut systems, lang keys`.

### Task B2 : `HytaleWorldBlocks` et `HytaleItemCatalog`

**Files :** Create `adapter/HytaleWorldBlocks.java`, `adapter/HytaleItemCatalog.java`.

- `WorldBlocks` : cheat sheet § 1 (lecture sans chargement, fluides en pseudo-clé `~fluid:<id>`, états `*…` normalisés, pose via `testPlaceBlock`+`setBlock`, casse : vider le conteneur, calculer les drops, `naturallyRemoveBlock(NO_DROP_ITEMS)`).
  - **Chaud** : ≤ 10 000 appels à `get` par pas du constructeur. Mettre en cache les `BlockState` par (id runtime, rotation) (tableau ou `HashMap<Long, Optional<BlockState>>`), zéro allocation par appel une fois chaud.
  - `place` avec une `BlockKey` fluide : pose le fluide via `FluidSection.setFluid`.
  - `breakBlock` sur un fluide : l'enlève, aucun drop.
- `ItemCatalog` : cheat sheet § 2.
  - `maxStack` : `Item.getMaxStack()`, 1 si item inconnu.
  - `itemForBlock` : `BlockType.getItem()` (après normalisation d'état) ; vide pour AIR/fluide/inconnu.
  - `kind`, `isOre`, `toolFor` (via `GatherType`), `hardness` (heuristique `0.05 / puissanceMainsNues`, bornée à [0.05, 3]), `tool` (catégorie par `getPlayerAnimationsId`, niveau = `Quality` de la spec correspondante), `durability` = `(int)(maxDurability / pertePourCeTypeDeBloc)`, 0 si ≤ 0.
  - Niveaux d'outil : le plus bas doit valoir 0 (règle core : une cabane niveau 0 accepte `ToolRequest(type, 0, hutLevel)`). Si `Quality` commence à 1, soustraire 1 (documenter).
  - Mettre en cache par clé (`ConcurrentHashMap` inutile : thread du monde ; `HashMap`).

- [ ] Compile, commit `feat(plugin): world blocks and item catalog adapters`.

### Task B3 : `HytaleContainerAccess` et `HytalePlayerInventory`

**Files :** Create `adapter/HytaleContainerAccess.java`, `adapter/HytalePlayerInventory.java`.
- Cheat sheet § 3-4 : extraction et prise **par emplacement** (outils usés) ; `insert`/`give` renvoient le reste ou `null` ; joueur hors ligne ou dans un autre monde → 0 / vide / reste complet.
- `contents` : `LinkedHashMap` dans l'ordre de l'inventaire.

- [ ] Compile, commit `feat(plugin): container and player inventory adapters`.

### Task B4 : `HytaleBlueprintSource` et `styles.json`

**Files :** Create `adapter/HytaleBlueprintSource.java`, `resources/hycolony/styles.json`.
- `styles.json` : `{ "<style>": { "<buildingTypeId>": { "<level>": { "prefab": "...", "hutOffset": [x,y,z] } } } }` pour `outlander` et `kweebec` (tableau du cheat sheet § 5), les trois types (`hycolony:townhall`, `hycolony:builder`, `hycolony:residence`) partageant la même progression pour l'instant. `hutOffset` absent → centre de la face au sol (plus bas Y non vide, centre X/Z de la boîte non tournée).
- `load(style, type, level, rotation)` : prefab via `PrefabStore` + `PrefabBufferUtil.getCached`, `PrefabBufferCall(new Random(0), PrefabRotation.VALUES[rotation & 3])`, entrées `filler == 0`, sans `Empty`, `Block_Spawner_Block`, `Editor_*` ; décalage = position − hutOffset tourné ; exclut l'entrée à la position de la cabane ; `hasContainer` = le `BlockType` a un `ItemContainerBlock` dans son block entity ; bornes tournées relatives à la cabane.
- Cache `(style, type, level, rotation) → Blueprint` ; erreurs (prefab absent, style inconnu) → `Optional.empty()` + un avertissement journalisé une seule fois.
- `styles()` : clés du JSON, `outlander` en premier.
- Rotation : l'entier stocké par SP0 est `yaw().ordinal()` ; vérifier que `PrefabRotation.VALUES[i]` suit le même ordre (sinon passer par `PrefabRotation.fromRotation(Rotation.values()[i])`).

- [ ] Compile, commit `feat(plugin): blueprint source over vanilla prefabs`.

### Task B5 : objet tenu et animations du PNJ

**Files :** Modify `adapter/HytaleCitizenBodies.java` (cheat sheet § 6). `Action` + `"Block"/"Build"` et `"Pickaxe"/"Mine"` ; repli documenté `Default/SwingRight` si le rendu en jeu échoue.

- [ ] Compile, commit `feat(plugin): citizen held item and work animations`.

### Task B6 : fenêtres

**Files :** Create `ui/{BuildingPage,BuilderResourcesPage,RequestsPage,WorkOrdersPage}.java` et leurs `.ui` sous `Common/UI/Custom/Pages/HyColony/` (+ lignes `WorkerRow.ui`, `ResourceRow.ui`, `RequestRow.ui`, `OrderRow.ui`) ; Modify `HytaleUiPort` (remplace les « à venir »), `TownHallPage` + `TownHall.ui` (boutons « Bâtiment », « Ordres », « Requêtes »).
- `BuildingPage` : type, niveau/max, construit, travailleurs (+ Renvoyer), embauchables (+ Embaucher), mode d'embauche (bascule), ordre en cours (type, cible, constructeur, %), un bouton par action de `allowed` + Annuler s'il y a un ordre, sélecteur de style (bouton qui fait défiler `styles`), « Ressources » (cabane de constructeur), « Stockage », « Ramasser » si `canPickUp`.
- « Stockage » : cheat sheet § 8 (`ContainerBlockWindow`, `setPageWithWindows`, `registerCloseEvent` → retirer de `getWindows()` **et** `manager.onContainerChanged(pos)`).
- « Ramasser » : `pickUpBuilding(player, pos, giveItem)` où `giveItem` donne l'item de cabane au joueur via `PlayerInventory.give` et renvoie `true` s'il est entré ; ensuite le plugin retire le bloc **sans drop**.
- `BuilderResourcesPage` : lignes (icône `ItemIcon`, nom, `disponible / nécessaire`, couleur selon `Status` : DONT_HAVE rouge, NEED_MORE orange, HAVE_ENOUGH vert, NOT_NEEDED gris), bouton « Ajouter » → `addToHut(player, hut, item, needed − available)`, progression et étape.
- `RequestsPage` : lignes (description, demandeur, ce que le joueur a) + « Fournir » → `fulfil`.
- `WorkOrdersPage` : lignes (type, bâtiment, cible, priorité, constructeur) + haut/bas/supprimer si `canManage`.
- Toutes les chaînes affichées passent par des clés `hycolony.ui.*` : le rapport de la tâche liste les clés ajoutées (en-US et fr-FR).

- [ ] Compile, commit `feat(plugin): building, resources, requests and work order windows`.

### Task B7 : branchement, selftest, documentation

**Files :** Modify `WorldRuntime` (vrais ports à la place de `GamePorts.unavailable()`), `HyColonyCommand` (selftest étendu : charger un plan outlander niveau 1, poser puis casser un bloc de test à côté du joueur, aller-retour d'un objet dans un conteneur temporaire), `docs/TESTING.md` (items 13+ : cabane de constructeur, embauche, commande BUILD, livraisons, construction visible, redémarrage en plein chantier, retrait, stockage, ramassage), `README.md`, `docs/UPGRADING.md` (API instables : `BlockOperations.setBlock`, `PrefabBufferCall`), amendement du spec SP0 § 2.4 (écritures synchrones conservées).

- [ ] `./gradlew build` vert, commit `feat(plugin): wire construction ports, extended selftest, testing checklist`.

### Relecture finale Plan B, puis test en jeu par l'utilisateur.
