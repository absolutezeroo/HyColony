# HyColony — Sous-projets 1+2 : Requêtes & Construction

- **Date** : 2026-09-25
- **Statut** : validé en conversation. L'utilisateur a demandé d'enchaîner sans arrêt, avec un maximum de tests et d'optimisations.
- **Prérequis** : SP0 (`2026-09-25-hycolony-sp0-fondations-design.md`)
- **Références** :
  - `docs/research/minecolonies-analysis.md` (§5 à 7)
  - `docs/research/construction-research.md`
  - `docs/research/hytale-api-spike.md`
  - la source de MineColonies, que les plans citent par fichier.

## 1. Objectif et critères de réussite

Porter fidèlement **le système de requêtes** de MineColonies, ainsi que **le constructeur, les ordres de travail et la construction bloc par bloc**, avec le socle minimal des **métiers** dont le constructeur a besoin.

En jeu, les critères de réussite sont :
1. **La cabane du constructeur.** Le joueur pose une cabane de constructeur dans sa colonie. Un citoyen sans travail est **embauché automatiquement** (vérification toutes les 500 ticks). Il demande à construire sa propre cabane, qui passe du niveau 0 au niveau 1 dès que le joueur clique sur « Construire ».
2. **Un chantier complet.** Le constructeur :
   - se rend sur le site ;
   - **défriche** le terrain : il mine les blocs, garde les drops sauf les minerais, et ne touche pas au bloc de la cabane ;
   - **pose** le prefab bloc par bloc (solides, puis décoration) ;
   - met à jour le niveau, gagne de l'XP, écrit dans le journal et envoie un message au joueur.
3. **Le manque de matériaux.** Quand il manque des matériaux, le constructeur émet des **requêtes**. Elles sont résolues d'abord par le stock de sa cabane, puis réessayées 3 fois, puis réclamées au joueur. Le joueur les satisfait avec « Fournir » (depuis son inventaire vers l'inventaire du citoyen), avec « Ajouter » (depuis la fenêtre des ressources vers la cabane), ou en déposant les objets dans la cabane.
4. **Les ordres de travail.** Construire, Améliorer, Réparer, Déconstruire et Annuler, sur l'hôtel de ville, la cabane du constructeur et la résidence, en 2 styles (Outlander et Kweebec), sur 5 niveaux.
5. **Les claims.** Le territoire de l'hôtel de ville et des bâtiments s'agrandit selon leur niveau, avec les tables de rayons de MineColonies.
6. **La persistance.** Après un redémarrage en plein chantier, le chantier reprend là où il en était (étape et position), avec les requêtes et les inventaires intacts.

## 2. Principes

- **Même architecture que SP0.** La logique vit dans `core`, sans aucun import Hytale. `plugin` fournit les adaptateurs, et tout passe par des ports.
- **Nouveaux packages du core.** `item`, `request`, `job` et `construction`.
  - `request` ne dépend que de `kernel` et `item`. `request` ne connaît ni `building` ni `construction` : les bâtiments s'y branchent via les interfaces `Requester` et `ResolverProvider`.
  - `construction` dépend de `request`, `job`, `building`, `colony`, `citizen` et `item`.
  - `job` dépend de `building`, `citizen` et `colony`.
  - Ces règles sont vérifiées par ArchUnit. Au passage, le test ArchUnit générique de SP0 est remplacé par ces règles explicites.
- **Performances**, car des colonies pourront avoir des milliers de blocs par chantier :
  - le plan (blueprint) est précalculé **une fois par ordre de travail**, en listes triées par étape ;
  - la progression est un index dans la liste, jamais un rescan ;
  - on limite à **10 000 positions examinées par appel**, comme MineColonies ;
  - toutes les recherches de requêtes passent par des maps, jamais par un scan linéaire. On garde en particulier un index inverse requête → résolveur, là où MineColonies faisait un scan O(n) ;
  - le prefab est mis en cache côté adaptateur ;
  - le constructeur ne se déplace que si le bloc suivant est à plus de 10 blocs de sa position de travail, comme dans MineColonies.
- **Tests.** Chaque unité a ses tests. S'y ajoute une **simulation de bout en bout dans le core** (§ 9), qui fait tourner une colonie entière jusqu'à la fin d'un chantier sur des fakes.

## 3. Objets et inventaires (`core.item`)

- `record ItemKey(String id)` : identifiant opaque. C'est l'identifiant d'objet Hytale, fourni par les adaptateurs.
- `record ItemAmount(ItemKey item, int count, int damage)`, avec `count > 0` et `damage >= 0` : l'usure d'un outil en usages (MC `ItemStack.getDamageValue`, 1 par bloc cassé), 0 pour le reste. Un outil s'empile par 1, donc son usure est la sienne, où qu'il aille (inventaire d'un citoyen, coffre de hutte, entrepôt, livreur, joueur). `Inventory` ne fusionne que des piles de même usure, n'écrit `"damage"` que s'il dépasse 0 et le relit à 0 s'il manque ; `Inventory.damage(slot, n, durabilité)` use une case et la vide quand l'outil casse (MC `damageInventoryItem`).
- **`Inventory`** : un nombre fixe d'emplacements. Il fournit :
  - `insert(ItemAmount)` : renvoie le reste, et respecte `maxStack` via `ItemCatalog` ;
  - `extract(ItemKey, int max)` : renvoie ce qui a été retiré ;
  - `count(ItemKey)`, `isFull()`, `freeSlots()` ;
  - l'itération et la (dé)sérialisation JSON.
- **Inventaire des citoyens** : **27 emplacements**, la valeur par défaut de MineColonies. Il est ajouté à `CitizenData` et sauvegardé.
- **Port `ItemCatalog`** :
  - `int maxStack(ItemKey)`
  - `Optional<ItemKey> itemForBlock(BlockKey)` : l'objet à fournir pour poser ce bloc
  - `BlockKind kind(BlockKey)` : `AIR`, `SOLID`, `NON_SOLID`, `FLUID` ou `UNBREAKABLE`
  - `boolean isOre(BlockKey)`
  - `Optional<ToolType> toolFor(BlockKey)` : `PICKAXE`, `AXE` ou `SHOVEL`
  - `float hardness(BlockKey)`
  - `Optional<ToolInfo> tool(ItemKey)` : type, niveau et vitesse
- `record BlockKey(String id)` et `record BlockState(BlockKey key, int rotation)`.

## 4. Système de requêtes (`core.request`)

C'est un portage fidèle de `StandardRequestManager`, `RequestHandler` et des résolveurs (voir l'analyse, § 5), avec les simplifications déjà décidées dans l'analyse : plus de `TypeToken` ni de factories, un seul gestionnaire avec des maps directes, et une transition d'état explicite.

- **Identifiant** : `RequestToken(UUID)`.
- **Ce qu'on peut demander** (`sealed interface Requestable`) :
  - `Deliverable` : `matches(ItemAmount)`, `count()`, `minCount()`, `withCount(int)`, `canBeResolvedByBuilding()`
  - `StackRequest(ItemKey, int count, int minCount, boolean canBeResolvedByBuilding)`
  - `ToolRequest(ToolType, int minLevel, int maxLevel)` : correspond à un objet outil du bon type et de niveau compris entre les bornes (via `ItemCatalog`)
- **Requester** : `id()`, `location()`, `displayName()`, `onRequestComplete(RequestManager, token)` et `onRequestCancelled(RequestManager, token)`. Les bâtiments en sont via `BuildingRequester`, et chaque résolveur en est un aussi.
- **`Request`** : token, requester, requestable, **state**, parent, enfants, `deliveries` (`List<ItemAmount>`), et la clé du requester.
- **`RequestState`** : les 13 états, dans l'ordre de MineColonies.
- **`Resolver`**, avec les mêmes contrats que `IRequestResolver` :
  - `handles(Requestable)` et `canResolve(mgr, req)` ;
  - `attemptResolve(mgr, req)`, qui renvoie `Optional<List<Requestable>>` : vide signifie « ne peut pas », sinon ce sont les enfants à créer ;
  - `resolve(mgr, req)` et `followups(mgr, req)` ;
  - `onAssigned`, `onCancelled` et `onColonyUpdate(pred)` ;
  - `suitability(mgr, req)` (plus bas = meilleur), `priority()`, `location()`, `resolverId()`.
- **`ResolverProvider`** : un bâtiment qui expose ses résolveurs. On le branche avec `onProviderAdded`/`onProviderRemoved`. Retirer un provider réassigne ses requêtes en mettant ses résolveurs sur liste noire.
- **`RequestManager`** (un par colonie). Il fournit :
  - `create`, `assign`, `createAndAssign` et `reassign(token, blacklist)` ;
  - `updateState(token, state)`, `overrule(token, deliveries)` et `onColonyUpdate(pred)` ;
  - `tick()`, appelé toutes les 11 ticks : c'est le résolveur retrying qui avance ;
  - des requêtes : par requester, par état, par citoyen.
  - **Algorithme d'attribution**, identique à `assignRequestDefault` :
    - candidats triés par priorité décroissante, puis par spécificité ;
    - à priorité égale, la meilleure métrique d'adéquation l'emporte (les enfants de la tentative précédente sont annulés) ;
    - on s'arrête au changement de priorité ;
    - les enfants héritent de la liste noire.
  - **Transitions** : fidèles à § 5 de l'analyse. RESOLVED → suites, sinon COMPLETED ; COMPLETED → requester notifié, puis RECEIVED ou résolution du parent ; CANCELLED/FAILED d'un enfant → réassignation du parent ; OVERRULED ; RECEIVED → nettoyage. Elles sont écrites comme une **fonction de transition non réentrante** : une file d'événements est traitée dans l'ordre de MineColonies.
- **Résolveurs du lot** :
  - `BuildingResolver` (priorité 200) : utilise le stock des conteneurs de la cabane du requester, via `ContainerAccess`. Il résout en ajoutant les piles aux `deliveries`, et le citoyen vient les chercher.
  - `RetryingResolver` (priorité 50) : délai de 1200 ticks, 3 tentatives, puis liste noire.
  - `PlayerResolver` (priorité 0) : attend le joueur.
  - `onColonyUpdate` est déclenché quand le joueur dépose des objets dans une cabane (événement de conteneur côté plugin) : les requêtes bloquées sont réassignées.
- **Côté joueur** :
  - **« Fournir »** : `fulfil(player, token)`. On prend `min(demandé, possédé)` dans l'inventaire du joueur, on le met dans l'**inventaire du citoyen** demandeur, puis la requête passe en OVERRULED. Une quantité partielle clôt quand même la requête, et le constructeur redemande le reste, comme dans MineColonies.
  - **« Ajouter »** (fenêtre des ressources) : transfère le manque, borné à ce que possède le joueur, dans les conteneurs de la cabane, puis appelle `overruleNextOpenRequestWithStack`.
- **Données par bâtiment** : requêtes ouvertes par citoyen (−1 pour le bâtiment) et requêtes complétées par citoyen. C'est ce que le citoyen vient récupérer.
- **Bugs connus de MineColonies non reproduits** (analyse § 5) :
  - `overrule` appelait OVERRULED deux fois ;
  - le compteur de tentatives était inversé ;
  - `FASTEST_FIRST` n'était qu'un bouchon.

## 5. Métiers (`core.job`)

- **`JobType`** (registre) : `id` et `factory`.
- **`Job`** : son citoyen, son bâtiment de travail, `createAI(ctx)` qui renvoie une `JobAI` (avec `tick()`, `state()` et `canBeInterrupted()`), `write`/`read` en JSON, et le compteur `actionsDone`.
- **`WorkerModule`** (module de bâtiment, persistant et tickant) :
  - il porte : `jobType`, `maxWorkers`, les compétences primaire et secondaire, `HiringMode` (`DEFAULT`, `AUTO`, `MANUAL`, `LOCKED`) et les citoyens affectés ;
  - `canAssignCitizens` vaut `level > 0 && isBuilt`, sauf si le type de bâtiment surcharge cette règle (cas du constructeur) ;
  - **à chaque tick lent**, si le module n'est pas plein et que l'embauche automatique est possible (`AUTO`, ou `DEFAULT` avec l'option de l'hôtel de ville `autoHiring`, vraie par défaut), il embauche le **premier citoyen sans travail et non enfant**, par ordre d'id ;
  - `hire`, `fire` et `setHiringMode` sont appelés depuis l'UI et exigent `MANAGE_HUTS`.
- **IA citoyen** : un nouvel état `WORKING`. Un citoyen qui a un métier travaille (via l'IA de son métier), sinon il erre. La nuit, la pluie et la faim arrivent avec SP4 ; pour l'instant, il travaille toujours.
- **Compétences** : l'XP passe par `Skills.addXp` (déjà écrit en SP0), avec la répartition de MineColonies :
  - primaire 100 %, secondaire 50 % ;
  - complémentaire +10 %, adverse −10 % ;
  - un multiplicateur `(1 + (niveauTravail + niveauMaison)/10) × (1 + Int/100)`.
  C'est une fonction pure à tester.

## 6. Construction (`core.construction`)

### Bâtiments
- **Hôtel de ville** : rayons de claim 1 → 1, 2 → 1, 3 → 2, 4 → 3, 5 → 5.
- **Cabane du constructeur** (`hycolony:builder`) :
  - `WorkerModule(builder, Adaptability, Athletics, max 1)` ;
  - `canAssignCitizens` vaut toujours vrai ;
  - `canBeBuiltByBuilder(n)` vaut `n == level + 1` ;
  - un réglage de mode AUTO/MANUAL ;
  - un `BuildingResourcesModule` ;
  - rayons de claim par défaut (1-3 → 1, 4-5 → 2).
- **Résidence** (`hycolony:residence`) : `LivingModule(capacité = niveau)` (utilisé en SP4) et rayons par défaut.
- **Claims** : à chaque montée de niveau, le bâtiment revendique un carré de cellules de ce rayon autour de sa cellule. On ne dépasse jamais `maxColonySize` depuis le centre, et on ne vole jamais une cellule.
- **Conteneurs d'un bâtiment** : le bloc de cabane lui-même (le plugin en fait un coffre), plus tous les blocs à conteneur **posés par le constructeur** depuis le plan. Leurs positions sont enregistrées dans le bâtiment et persistées. Un coffre posé à la main n'en fait pas partie.

### Plans (blueprints)
- **Port `BlueprintSource.load(String style, String buildingTypeId, int level, int rotation)`**. Il renvoie `Optional<Blueprint>`, qui contient :
  - les entrées `BlueprintEntry(BlockPos offsetFromHut, BlockState state, boolean hasContainer)`, **déjà tournées**, relatives au **bloc de cabane**, en excluant la position du bloc de cabane et les entrées filler ;
  - les bornes `min` et `max`, relatives à la cabane.
- `styles()` renvoie la liste des styles disponibles.
- Les données (style → type → niveau → clé de prefab et décalage du bloc de cabane) sont un fichier JSON du **plugin**. Le core ne connaît que des noms de style.
- **Précalcul par ordre de travail** : `StructurePlan` construit, une seule fois, trois listes :
  - `clearList` : toutes les positions de la boîte, **de haut en bas** ;
  - `solidList` : les entrées `SOLID`, **de bas en haut**, par couche Y, puis X, puis Z ;
  - `decoList` : les entrées `NON_SOLID` et `FLUID`, de bas en haut.

### Ordres de travail
- **`WorkOrder`** :
  - `id`, `type` (`BUILD`, `UPGRADE`, `REPAIR`, `REMOVE`), `buildingPos`, `targetLevel`, `style`, `rotation` ;
  - `priority`, `claimedBy` (position de la cabane du constructeur) ;
  - `stage` (`CLEAR`, `SOLID`, `DECORATE`, `REMOVE`, `DONE`), `progressIndex`, `requested`.
- **`WorkManager`** (par colonie, tické toutes les 20 ticks). Il reprend `WorkManager` et `WorkOrderBuilding` de MineColonies :
  - **Création**, par `requestWorkOrder(player, buildingPos, type, style, builderPos?)`. Les refus, avec leur message, sont :
    - permission `MANAGE_HUTS` absente ;
    - ordre déjà existant ;
    - niveau maximal atteint ;
    - REPAIR au niveau 0 ;
    - aucun constructeur éligible (`BUILDER_NECESSARY`) ;
    - aucun à moins de 100 blocs (`BUILDER_TOO_FAR_AWAY`) ;
    - emprise hors du territoire (`OUT_OF_COLONY`).
    Si les vérifications passent, un message est envoyé et l'événement `WorkOrderCreated` est émis.
  - **Tri** : priorité décroissante, puis id croissant. Le joueur peut monter, descendre ou supprimer un ordre.
  - **Attribution** : les ordres déjà réclamés passent d'abord. Chaque ordre libre va au **premier** constructeur employé, inactif, pas en mode MANUAL, qui vérifie `canBuild` : `niveau ≥ cible`, ou niveau 5, ou sa propre cabane, et une distance 3D ≤ 100.
  - **Annulation** : les requêtes du constructeur liées à l'ordre sont annulées, la progression est remise à zéro, et les blocs déjà posés restent.

### Étapes et besoins
- **Étapes** :
  - `BUILD` d'un bâtiment de niveau 0 et `REPAIR` : `CLEAR`, puis `SOLID`, puis `DECORATE` ;
  - `UPGRADE` : `SOLID`, puis `DECORATE`, puis `CLEAR_LEFTOVERS` (restes du niveau précédent), sans `CLEAR` ;
  - `REMOVE` : `REMOVE`, qui enlève de haut en bas les positions du plan au niveau actuel, sans matériaux.
  - Écarts assumés par rapport à MineColonies :
    - `WEAK_SOLID` est fusionné dans `SOLID` (Hytale n'a pas de blocs « faibles ») ;
    - `CLEAR_WATER` et `CLEAR_NON_SOLIDS` sont fusionnés dans `CLEAR` et `DECORATE` ;
    - `SPAWN` n'existe pas, puisque les entités des prefabs sont ignorées.
- **Règle de saut** (`skip`) : on passe une entrée dont le bloc du monde est déjà l'état voulu, même clé et même rotation. `CLEAR` ignore l'air, les blocs `UNBREAKABLE` et le bloc de cabane.
- **Besoins** :
  - on parcourt `solidList` et `decoList`, on retient les entrées qui ne sont pas déjà en place, et on convertit chacune via `itemForBlock` ;
  - la liste des besoins est décrémentée de 1 par pose ;
  - on la recalcule au chargement plutôt que de la persister, comme MineColonies.
- **Seaux** : on découpe les besoins en seaux de **27 − 9 = 18 piles** au plus, en utilisant `maxStack`. On demande le seau courant et le suivant, et rien pendant `CLEAR` ni `REMOVE`.
  - On soustrait ce que contiennent déjà la cabane et l'inventaire du constructeur.
  - Les requêtes sont des `StackRequest` émises par le bâtiment au nom du constructeur, de façon synchrone : le constructeur passe en `NEEDS_ITEM`.

### IA du constructeur
C'est un portage des états de `AbstractEntityAIStructure` et `EntityAIStructureBuilder` sur la `TickRateStateMachine` de SP0, cadence 5.
- **États** :
  - `IDLE`, `START_WORKING` (aller à la cabane, prendre l'ordre) ;
  - `LOAD_STRUCTURE` (plan et besoins, calculés de façon incrémentale) ;
  - `GATHERING_REQUIRED_MATERIALS` (prendre le seau dans la cabane) ;
  - `NEEDS_ITEM` (attendre et récupérer les livraisons) ;
  - `BUILDING_STEP` et `MINE_BLOCK` ;
  - `INVENTORY_FULL` (vider dans la cabane en gardant le nécessaire) ;
  - `COMPLETE_BUILD`.
- **Transitions communes** :
  - `INVENTORY_FULL` quand l'inventaire est plein ou après 4096 actions (valeur du constructeur) ;
  - `NEEDS_ITEM` quand une requête synchrone est ouverte ou complétée ;
  - l'attente `delay` (le constructeur frappe pendant le délai).
- **Pas d'avancement** : il traite une entrée par appel. Pour une pose, il vérifie les objets dans son inventaire. S'il lui en manque, il passe par `NEEDS_ITEM` ou `GATHERING`. Sinon il pose via `WorldBlocks.place`, retire l'objet, réduit le besoin, gagne 0,05 d'XP et applique le délai. Pour une casse, il passe à `MINE_BLOCK`.
- **Déplacement** : il va à une position de travail à 4 blocs ou moins du bloc courant, et la réutilise tant que le bloc suivant est à 10 blocs ou moins (`CitizenBodies.moveTo` et `navStatus`, déjà en SP0).
- **Temps**, en ticks, en reprenant les formules de MineColonies :
  - pose : `15 × 10 / (skillPrimaire/2 + 10)` ;
  - casse : `500 × 0,85^(skillSecondaire/2) × dureté / vitesseOutil × 0,5`, avec la dureté via `ItemCatalog`.
- **Outils** : pour une casse qui en exige un, il prend le meilleur outil adapté de son inventaire ou de la cabane, borné par le niveau max d'équipement de la cabane. **Niveau max = niveau de la cabane**, une simplification documentée. À défaut, il émet un `ToolRequest` et son statut passe à STUCK.
  - L'usure est de 1 par bloc cassé (MC `damageItemInHand`), sur la case que le bâtisseur tient (MC `setHeldItem(hand, slot)`) : la première case du meilleur outil. À sa durabilité (`ItemCatalog.durability`), l'outil casse et disparaît, sans message ; le bloc suivant redemande un outil. Les ports (`ContainerAccess.extractStacks`, `PlayerInventory.takeStacks`, `insert`, `stacks`, `breakBlock`, `drop`) portent l'usure dans les deux sens. Côté Hytale, `HytaleStacks` la convertit : un usage vaut `maxDurability / durability` points ; à la relecture, un usage entamé compte en entier, pour ne jamais réparer.
- **Drops** : `WorldBlocks.breakBlock` renvoie les drops, **conteneurs compris**. Ils vont dans son inventaire, sauf les minerais pendant `CLEAR`, qui sont détruits.
- **Fin de chantier** :
  - `level = cible`, `isBuilt = true` (pour REMOVE : `deconstructed = true`, niveau conservé) ;
  - claims, conteneurs enregistrés, +8 d'XP ;
  - journal (`buildingBuilt` / `Upgraded` / `Repaired` / `Deconstructed`), message aux membres, événement `BuildingLevelChanged`.
- **Animations** : le port `CitizenBodies` gagne `setHeldItem(body, Optional<ItemKey>)` et `playAnimation(body, Animation{BUILD, MINE})`.

## 7. Ports ajoutés

| Port | Méthodes |
|---|---|
| `WorldBlocks` | `Optional<BlockState> get(BlockPos)`, `boolean place(BlockPos, BlockState, boolean withContainer)`, `List<ItemAmount> breakBlock(BlockPos)`, `boolean isLoaded(BlockPos)` |
| `ItemCatalog` | § 3 |
| `BlueprintSource` | § 6 |
| `ContainerAccess` | `int count(List<BlockPos> containers, ItemKey)`, `int extract(containers, ItemKey, int max)`, `ItemAmount insert(containers, ItemAmount)` (renvoie le reste), `Map<ItemKey,Integer> contents(containers)` |
| `PlayerInventory` | `int count(UUID, ItemKey)`, `int take(UUID, ItemKey, int max)`, `ItemAmount give(UUID, ItemAmount)` |
| `CitizenBodies` (ajout) | `setHeldItem`, `playAnimation` |

## 8. Plugin

- **Nouveaux blocs de cabane**, hôtel de ville compris : `Builder` et `Residence`. Ce sont des blocs avec `ItemContainerBlock`, calqués sur les caisses vanilla, et avec une interaction « Use ».
  - La table d'identifiants passe à `hut.<type>` pour les items et les blocs.
  - `TownHallBlockSystems` devient `HutBlockSystems`, générique par clé de cabane. Les règles de pose de SP0 s'appliquent à toutes les cabanes.
- **Styles** : `hycolony/styles.json` décrit style → type → niveau → `{ prefab, hutOffset: [x, y, z] }`, pour les styles `outlander` et `kweebec` (progressions de la recherche). **Le décalage du bloc de cabane** est la position dans le prefab où se trouve le bloc de cabane. Par défaut, c'est le centre de la face au sol, le plus bas non-air.
  - `HytaleBlueprintSource` charge `IPrefabBuffer` (avec un cache), tourne via `PrefabBufferCall`, filtre `filler != 0`, `Block_Spawner_Block`, `Editor_*` et `Empty`, puis traduit les coordonnées par rapport au décalage de la cabane.
- **`HytaleWorldBlocks`** : API par sections uniquement.
  - `place` : `testPlaceBlock`, puis `setBlock` (settings 0). Un bloc à conteneur reçoit son conteneur par défaut (le contenu du prefab n'est pas recopié : pas d'objets gratuits).
  - `breakBlock` : il vide d'abord le conteneur éventuel, puis appelle `naturallyRemoveBlock(NO_DROP_ITEMS)` et `getDrops`.
- **`HytaleItemCatalog`** : il s'appuie sur `Item.getMaxStack` ; sur le `BlockType` (matériau, `getGathering`, type de récolte) pour l'outil et la dureté ; et sur un item outil (catégorie et niveau). Tout ce qui est incertain est ramené à des valeurs par défaut documentées.
- **`HytaleContainerAccess`** passe par `ItemContainerBlock` ; **`HytalePlayerInventory`** par `InventoryComponent.getCombined(HOTBAR_FIRST)`.
- **Événement de conteneur** : quand un joueur ferme ou modifie le conteneur d'une cabane, le plugin appelle `requests.onColonyUpdate` pour ce bâtiment.
- **Fenêtres** (des modèles de vue côté core, rendus par `InteractiveCustomUIPage`) :
  - `BuildingView` : type, niveau/5, construit ou non, travailleurs et embauche/renvoi, mode d'embauche, état de l'ordre, boutons autorisés, styles ;
  - `BuilderResourcesView` : objet, nécessaire, disponible, ce que le joueur possède, statut couleur, bouton Ajouter, progression ;
  - `RequestsView` : les requêtes ouvertes de la colonie assignées au joueur, avec « Fournir » ;
  - `WorkOrdersView` : priorité haut/bas et suppression.
  - La fenêtre de l'hôtel de ville reçoit trois boutons : « Bâtiment », « Ordres », « Requêtes ».
- **Selftest** étendu : charger un plan, poser puis casser un bloc de test, faire un aller-retour d'objet dans un conteneur.

## 9. Tests

- **Unitaires** pour chaque unité :
  - inventaire : piles, reste, extraction ;
  - requêtes : toutes les transitions, l'algorithme d'attribution (priorité, métrique, liste noire), parent/enfant, retrying (3 × 1200), overrule partiel, retrait d'un provider, `onColonyUpdate`, persistance ;
  - embauche ;
  - ordres : création et chaque refus, tri, attribution, annulation ;
  - plan : listes triées, sauts ;
  - besoins et seaux ;
  - formules de temps et d'XP ;
  - claims par niveau.
- **Simulation de bout en bout** (`ConstructionSimulationTest`), sur un monde factice (grille de blocs en mémoire), un plan factice de 3×3×3, un catalogue factice et un joueur factice :
  1. fonder, poser la cabane du constructeur, embaucher ;
  2. ordonner BUILD sur la cabane, faire tourner jusqu'à ce que le constructeur réclame des matériaux, les fournir, et faire tourner jusqu'à la fin ;
  3. vérifier que le monde correspond exactement au plan, que le niveau est 1, l'XP gagnée, le journal écrit, les claims appliqués, et que la requête est passée par l'état RECEIVED ;
  4. un redémarrage au milieu du chantier (sauvegarde puis rechargement) reprend exactement là où il en était ;
  5. REMOVE retire les blocs ;
  6. **performances** : un plan de 20 000 blocs reste sous 2 ms par tick de colonie en moyenne.
- **Persistance** :
  - `schemaVersion` passe à **2** ; puis à **3** avec l'usure sur la pile : la migration v2→v3 reporte l'ancien compteur `toolUses` du bâtisseur (usages par identifiant d'outil) sur la dernière case qui contient cet outil, celle qu'une casse retirait, et supprime le compteur (fixture `colony-v2-tooluses.json`) ;
  - la migration v1 → v2 ajoute `requests`, `workOrders`, et les inventaires et métiers vides ;
  - la fixture v1 de SP0 doit se charger via la migration ;
  - une nouvelle fixture v2 est ajoutée.
- **Plugin** : compilation, `selftest` étendu, et la checklist en jeu dans `docs/TESTING.md`.

## 10. Hors périmètre

- Entrepôt, coursiers, artisans, recettes : SP3, qui ajoutera de nouveaux résolveurs.
- Recherche : plafond de niveau par recherche.
- Besoins des citoyens : SP4.
- Aperçu fantôme du bâtiment.
- Bâtiments enfants.
- Déplacement d'une cabane (ramasser et reposer).
- Mode MANUAL de sélection d'ordre dans l'UI : le mode existe dans le core, mais il n'a pas de fenêtre de choix pour l'instant.

## 11. Écarts à MineColonies

Chaque écart porte un commentaire `Deviation from MC:` dans le code (CLAUDE.md § 6). La liste ci-dessous les reprend.

**Déplacements du constructeur**
- **Anti-blocage** (`kernel/nav/StuckHandler`) : Hytale calcule lui-même les chemins, donc il n'y a ni nœuds de chemin à sauter, ni recul, ni échelles, ni blocs cassés. Les niveaux de MineColonies se réduisent à trois actions : relancer la marche, téléporter près de la cible, abandonner. Le progrès se mesure à la position du corps (1 bloc parcouru), et non aux nœuds de chemin franchis. Après une téléportation, la marche est aussi abandonnée si le PNJ tourne en rond pendant le délai global (au moins 2 400 ticks) : chez MineColonies le citoyen atterrit à côté de sa cible, alors qu'une téléportation Hytale peut le poser sous un toit à deux blocs de là.
- **Emplacement de travail** (`construction/builder/WorkSpot`) : sans recherche de chemin, l'emplacement d'où le constructeur travaille est choisi d'après le monde et le plan, à 2 à 4 blocs du bloc : vers l'extérieur du chantier d'abord, puis sur les côtés, puis vers l'intérieur. Les pieds et la tête ne sont jamais dans une case que le plan va remplir. L'emplacement n'est jamais enterré ni dans un fluide profond de 2 blocs ou plus (pas de place au fond d'un lac), mais le constructeur peut se tenir dans une eau qui ne lui arrive qu'aux pieds, jamais dans un fluide dangereux (lave, feu : `ItemCatalog.isHarmful`). Comme `PathfindingUtils.isDangerous` de MineColonies, ni le sol, ni les pieds, ni la tête ne sont un bloc dangereux (feu, feu de camp éteint, brasero, cactus : `kernel/nav/DangerousCells`). La même règle vaut que le sol soit sous le bloc ou au-dessus. Faute d'emplacement sur un sol convenable, le constructeur se replie sur une colonne sans aucun sol (terrain non chargé ou vide), jamais sur une colonne dont le sol ne convient pas (au-dessus de la lave, d'un lac). Si aucune des 12 colonnes ne convient, il vise la case à 2 blocs vers l'extérieur et 1 au-dessus du bloc, marquée non vérifiée : elle peut être au-dessus ou dans la lave. La marche vers cette case ne bloque jamais : elle s'arrête quand la navigation échoue ou quand l'anti-blocage abandonne, et le constructeur travaille d'où il est arrivé. L'anti-blocage ne téléporte **jamais** sur une case non vérifiée : là où il téléporterait (après la relance, ou au délai global), il abandonne la marche. MineColonies, lui, téléporte toujours (`PathingStuckHandler`). La téléportation vers une case vérifiée ne change pas. Que la navigation de Hytale évite la lave n'est pas vérifié.
- **Marge d'un bloc autour des blocs dangereux** (`kernel/nav/DangerousCells.near`, `kernel/nav/ClearTarget`) : `PathfindingUtils.isDangerous` de MineColonies refuse seulement la case dangereuse. Ici, un citoyen qui s'arrête en garde aussi 1 bloc (diagonales comprises) : arrêté au bord, le guidage de Hytale et la poussée des voisins le font toucher le bloc, et un bloc bas (feu de camp éteint, brasero `Furniture_Crude_Brazier` de 0,3 bloc) s'enjambe comme une marche. Les points d'errance et les emplacements de travail préfèrent une case à 1 bloc de tout danger, et reviennent à l'ancienne règle (la colonne seule) s'il n'y en a aucune. `DetouringBodies.moveTo` déplace aussi toute cible au bord d'un danger vers la case dégagée la plus proche, à 2 blocs au plus et 1 bloc plus haut ou plus bas au plus, ou la garde telle quelle s'il n'y en a pas. La marche se termine donc à moins de 3 blocs de la cible demandée : l'appelant la voit arriver (statut de la navigation), et l'anti-blocage abandonne au lieu de téléporter.
- **Portée** (`construction/builder/BuilderWalker`) : MineColonies travaille encore une fois le bloc hors de portée avant de se déplacer. Ici le constructeur se déplace d'abord, et ne travaille donc jamais un bloc à plus de 5 blocs (distance `|dx| + |dz|`).

**Construction**
- **Retrait des fluides pendant CLEAR** (`construction/builder/BuilderBlockWork`) : comme MineColonies, CLEAR vide l'emprise de l'eau et de la lave, mais chaque case de fluide n'est retirée qu'une fois. Une source voisine peut la remplir de nouveau : boucler dessus ne finirait jamais. Ce qui revient après CLEAR reste en place (SOLID l'écrase, les décorations s'y posent).
- **Vérification finale** (`construction/builder/BuilderAI.stageDone`) : l'itérateur de MineColonies ne fait qu'avancer. Ici, une fois par ordre chargé, SOLID et DECORATE sont parcourus de nouveau avant la fin, pour reposer un bloc cassé derrière le constructeur.
- **Réparation avec déblaiement** (`construction/workorder/WorkOrder.initialStage`) : MineColonies saute `CLEAR` pour un bâtiment déjà construit (`AbstractEntityAIStructure.loadStructure`). Ici une réparation commence par `CLEAR`, comme une construction : les joueurs posent des blocs au mauvais endroit, et la réparation doit les retirer. Dans la boîte du plan (à partir du niveau de la hutte), tout bloc que le plan ne veut pas à cet endroit est miné, ses drops vont à la hutte ; la hutte et les blocs `UNBREAKABLE` sont épargnés.
- **Restes du niveau précédent** (`construction/workorder/Stage.CLEAR_LEFTOVERS`) : chez MineColonies, les niveaux d'un bâtiment partagent la même emprise, et `CLEAR_NON_SOLIDS` (`AbstractEntityAIStructure.structureStep`) mine seulement les cases où le nouveau plan a de l'air. Nos niveaux sont des prefabs distincts, d'emprises différentes : après `DECORATE`, une amélioration mine chaque bloc du plan du niveau précédent (même style, même rotation) encore dans son état d'origine et que le nouveau plan ne veut pas à cet endroit (absent, air ou autre bloc). Un bloc qui ne vient pas de l'ancien plan (posé ou changé par un joueur) reste. Les drops vont à la hutte ; la hutte et les blocs `UNBREAKABLE` sont épargnés. La vérification finale passe après cette étape et ne reconstruit donc rien de ce qu'elle a retiré.
- **Message de fin de chantier** (`construction/workorder/BuildCompletion`) : mêmes clés que MineColonies (construction et amélioration, réparation, déconstruction) et mêmes destinataires (membres avec `RECEIVE_MESSAGES`), mais le message donne le nouveau niveau au lieu de la direction depuis le centre de la colonie, et la déconstruction n'a pas le rappel du bouton « Pick Up ».
- **Feux d'artifice** (`plugin/adapter/HytaleWorldEffects`) : comme MineColonies (`AbstractSchematicProvider.upgradeBuildingLevelToSchematicData`), seulement quand le niveau monte (construction, amélioration). MineColonies tire une fusée à chaque coin de la boîte du bâtiment, avec autant d'explosions que le niveau, si le ciel est visible. Ici, quatre systèmes de particules vanilla (`Firework_Mix2/3/4`) à 8 blocs au-dessus des coins d'un carré de 6 blocs centré sur la hutte, sans test du ciel ni lien au niveau : le cœur ne connaît pas la boîte du bâtiment.
- **Coups pendant le minage** (`construction/builder/BuilderGestures`, `plugin/adapter/HytaleWorldEffects.blockHit`) : comme MineColonies (`AbstractEntityAIBasic.waitingForSomething`, `CitizenItemUtils.hitBlockWithToolInHand`), un son de coup et des éclats tous les 5 ticks, à moins de 4 blocs. Le son se joue aux modificateurs d'un coup de joueur Hytale (1, 1) au lieu de `(v + 1) × 0,125` et `p × 0,5`, qui sont les valeurs du joueur Minecraft. La casse garde le son et les particules de `naturallyRemoveBlock` (modificateurs 1, 1 au lieu de `× 0,5` et `× 0,8`). **Ajout** : des fissures progressives (`BlockHealthChunk.damageBlock`, santé jamais sous 0,05) ; MineColonies n'en montre pas pour ses ouvriers.
- **Ordres gratuits d'un opérateur en créatif** (`construction/workorder/WorkManager.isFree`) : ajout demandé. Avec l'option `HyColony.CreativeOperatorFreeBuilds`, un ordre passé par un opérateur en mode créatif se construit sans matériaux. `HyColony.BuilderInfiniteResources` rend tout ordre gratuit : c'est aussi un ajout, car MineColonies n'a que la constante `Constants.BUILDER_INF_RESOURECES` (false), pas une option de configuration. Un ordre REMOVE n'est jamais gratuit.
- **Animations de travail** (`construction/builder/BuilderGestures`) : MineColonies fait balancer le bras à chaque tick d'IA (5 ticks de jeu), ce qui donne un mouvement continu parce que le geste est court. Les animations Hytale (Block/Build, Pickaxe/Mine) durent plus longtemps : les relancer tous les 5 ticks les jouerait deux fois. La pose joue Build une fois par bloc, et le minage ne relance le coup de pioche qu'une fois le précédent terminé.
- **Blocs connectés et états** (`plugin/adapter/HytaleWorldBlocks.blockKey`) : comme MineColonies, qui pose l'état exact du plan (forme d'escalier comprise), le constructeur pose la variante de forme du prefab (coins d'escalier et de toit, faîtière, toit creux, coin, T et croisement de clôture ou de mur), sans calcul des voisins, comme le collage d'un prefab vanilla. Les autres états Hytale (porte, coffre ou portillon ouverts) sont ramenés à leur bloc de base, dans le plan comme dans le monde : un joueur qui ouvre une porte ne la fait pas reconstruire. Limite connue : un escalier posé par un joueur à côté d'un bâtiment fini peut changer la forme d'un escalier du constructeur (règle vanilla) ; avant la vérification finale, le constructeur la rétablit, après, il la laisse.

**Citoyens**
- **Pas de temps libre** (`citizen/CitizenAI`) : comme MineColonies (`CitizenAI.calculateNextState`), un travailleur dont l'IA de métier peut se reposer (`canGoIdle`, par exemple un constructeur sans ordre réclamé) erre au lieu de travailler. Le temps libre (`getLeisureTime`) n'existe pas encore : un travailleur qui a du travail ne fait jamais de pause.
- **Marqueur « ! »** (`colony/CitizenNameplates`) : MineColonies affiche une icône au-dessus de la tête d'un citoyen qui attend un joueur. Les PNJ Hytale n'ont pas cette surcouche, donc le nom l'affiche : « ! Nom ».
- **Immunité au feu** (`plugin/npc/CitizenFireImmunitySystems`) : ajout demandé. Un citoyen ne brûle jamais (ni dégâts, ni teinte d'écran, ni particules), et ce indépendamment de l'invulnérabilité du rôle : `Grant` lui donne l'effet Hytale permanent `Immunity_Fire`, qui bloque nativement `Burn`/`Lava_Burn` (`Burn_Template.ApplyConditions`) ; `Guard` annule en plus les dégâts de contact des braises d'un feu de camp éteint (`Block_Damage`, un effet à cause `Physical` sans lien avec `Immunity_Fire`). MineColonies, lui, laisse un citoyen brûler comme toute entité vivante.

**Requêtes**
- **Fourniture par le joueur** (`request/RequestManager.overrule`) : appliquée une seule fois, alors que MineColonies l'exécutait deux fois.
- **Résolveur disparu au chargement** (`request/RequestManager.reassignLoaded`) : comme dans MineColonies (`ResolverHandler.removeResolverWithAssignedRequests`), les enfants sont annulés et la requête réassignée. Deux différences : le déclencheur (au chargement, quand le résolveur a disparu entre la sauvegarde et le chargement, au lieu du retrait d'un fournisseur) et la liste d'exclusion (`req.blacklist()` au lieu des résolveurs du fournisseur retiré, qui ne sont plus connus au chargement).

**Fenêtres** (comparaison complète : `docs/research/ui-vs-minecolonies.md`)
- **Fournir dans le presse-papiers** (`colony/ui/RequestsView`) : la fenêtre « Requêtes » propose « Fournir » sur chaque ligne. MineColonies ne le propose que dans l'onglet Requêtes du citoyen et dans la fenêtre de détail d'une requête (`ClipboardRequestTreeWindowModule` laisse `isFulfillable` à faux). Gardé à la demande de l'utilisateur, faute d'objet presse-papiers et de fenêtre de détail.
- **Compétences du citoyen** (`colony/view/SkillRows`) : la page principale de MineColonies liste les onze compétences dans un ordre fixe, avec le niveau seul. Ici les compétences principale et secondaire du métier passent en tête et en doré (l'ordre de `WindowHireWorker`), et chaque ligne a son XP et une barre. Gardé à la demande de l'utilisateur.
- **Tri des ressources** (`colony/view/BuilderResourcesViews.RESOURCE_ORDER`) : `ResourceComparator` trie par statut puis par **nom affiché** ; le cœur ne connaît pas les noms traduits, il trie par identifiant d'objet.
- **Étapes « X/Y »** (`colony/view/BuilderResourcesViews.stages`) : MineColonies compte ses `BuildingProgressStage` (6 pour un bâtiment neuf, 5 pour une amélioration, 2 pour une démolition). Ici nos étapes : 4 pour une construction ou une réparation (déblaiement, structure, décoration, retrait des restes), 3 pour une amélioration, 1 pour une démolition. La vérification finale repasse par la structure : l'étape affichée peut redescendre.
- **Couleur « rien à livrer »** (`plugin/ui/BuilderResourcesTab`) : noir chez MineColonies, gris ici (le noir ne se lit pas sur les panneaux sombres de Hytale).
- **Sélection manuelle d'un ordre** (`construction/workorder/ManualSelection`) : `BuildingBuilder.setWorkOrder` ne vérifie pas le niveau quand le constructeur a déjà un ordre (il met le nouveau en file). Ici le niveau est toujours vérifié. Comme chez MineColonies, le serveur ne vérifie pas le mode Manuel : seul l'onglet réserve « Sélectionner » à ce mode.
- **Ordre actif d'un constructeur** (`construction/workorder/WorkManager.claimedBy`) : MineColonies garde l'identifiant de l'ordre en cours sur la hutte (`AbstractBuildingStructureBuilder.workOrderId`) et le choisit au tick de la colonie. Ici l'ordre porte un drapeau `active` sauvegardé, posé à la première lecture quand le constructeur n'en a pas : le premier ordre réclamé dans l'ordre de `WORK_ORDER_COMPARATOR`, en préférant, pour une ancienne sauvegarde, un ordre déjà commencé. Les autres ordres réclamés restent en file.
- **Ramasser** (`plugin/ui/BuildOptionsPanel`) : MineColonies montre « Ramasser » au niveau 0 et pour une hutte déconstruite ; ici seulement pour une hutte déconstruite (règle du cœur `Building.canBePickedUp`, inchangée).
- **Hôtel de ville en onglets** (`plugin/ui/townhall/TownHallPage`) : Accueil, Informations, Citoyens, Statistiques, dans l'ordre de `AbstractWindowTownHall`. Pas d'onglets Permissions, Alliances ni Réglages (pas de fenêtre pour ces systèmes). Accueil (`TownHallActionsTab`) : en plus de MineColonies, le propriétaire, le jour et le bouton « Requêtes » (faute d'objet presse-papiers) ; ni carte, ni mercenaires, ni bannière, couleurs ou styles. Informations (`WorkOrderListTab`) : la liste des ordres seule, sans journal d'événements. La fenêtre séparée « Ordres » disparaît. Citoyens (`TownHallCitizensTab`) : la liste avec l'état, sans recherche, sélection, santé, bonheur ni rappel. Statistiques (`colony/view/TownHallStats`) : sans capacité de logement ni sa couleur et son avertissement (pas de logement), sans statistiques de production ; les métiers sont triés par identifiant, MineColonies par clé de traduction.
- **Citoyen en onglets** (`plugin/ui/citizen/CitizenPage`) : Principal, Requêtes, Inventaire, Métier (si le citoyen a un lieu de travail). Pas d'onglets Bonheur, Famille ni Debug. L'onglet Inventaire liste les objets et un bouton « Ouvrir l'inventaire » ouvre le conteneur (voir « Inventaire du citoyen » ci-dessous) ; MineColonies ouvre le conteneur dès le clic sur l'onglet. L'onglet Principal garde le métier, le lieu de travail et l'activité.
- **Inventaire du citoyen** (`colony/action/CitizenInventoryActions`, `plugin/ui/citizen/CitizenItemContainer`) : comme `ContainerCitizenInventory`, le joueur (avec `MANAGE_HUTS`) et l'IA partagent le même inventaire, sans limite de distance. Écarts :
  - il s'ouvre même si le corps du citoyen n'est pas chargé (MineColonies a besoin de l'entité) : l'inventaire vit dans le cœur ;
  - un objet avec des métadonnées est refusé, car le cœur ne garde que l'identifiant, le nombre et l'usure. Un outil usé est accepté, avec son usure, comme dans MineColonies ;
  - Hytale n'a pas d'appel « case posée » : une case vide qui reçoit une pile, ou dont l'objet change, pendant un déplacement du joueur compte comme une pose (`overruleNextOpenRequestOfCitizenWithStack`, avec la pile entière de la case, comme `Slot.set`). Une pile seulement complétée ne compte pas (MineColonies : `moveItemStackTo` la fait grossir et appelle `setChanged`). MineColonies appelle aussi `set` avec le reste d'une pile sortie par Maj+clic, ce qui ne résout rien ici ;
  - pas de repli « artisan » (enfants des tâches d'un `AbstractJobCrafter`) : il n'y a pas encore de métier d'artisan ;
  - pas de 4 cases d'armure ;
  - la fenêtre se ferme quand la colonie est supprimée, seul cas où un citoyen disparaît aujourd'hui.
- **Onglets rafraîchis à l'action** : MineColonies rafraîchit les onglets Ressources et Ordres toutes les 20 images. Ici la fenêtre montre l'état à son ouverture et se rafraîchit après chaque action (changer d'onglet ne relit pas le cœur).

**Lunettes de constructeur** (`construction/goggles`, spec `2026-09-26-hycolony-build-goggles-design.md`)
- Seuls les ordres **réclamés par un constructeur** sont affichés. MineColonies affiche tous les ordres de la colonie, la seule boîte pour une démolition et l'ancre des huttes de niveau 0 sans ordre.
- Le fantôme ne montre que les **blocs qui restent à poser** (pour une démolition, ceux qui restent à retirer), au lieu du plan complet.
- Pas de contours, pas de mode accroupi, pas de touche d'activation : porter les lunettes suffit.
- Rafraîchissement par paliers : entrées et sorties de zone vérifiées toutes les 20 ticks, contenu recréé au plus toutes les 100 ticks s'il a changé. MineColonies redessine à chaque image depuis un cache reconstruit tous les 12,5 blocs parcourus ; un aperçu Hytale se renvoie en entier.
- Recette : Hytale 0.6.8 n'a ni verre ni pépite d'or. Recette à l'établi : 1 baguette de construction, 1 lingot de fer, 2 cristaux cyan (les verres), 2 cuirs légers. MineColonies : pépites d'or, lingot de fer, vitres, Build Tool, cuir.

**Baguette de construction** (`construction/wand`, `plugin/ui/wand`, spec `2026-09-26-hycolony-build-tool-design.md`)
- **Pas de raccourcis clavier** (flèches, Maj+flèches, M, Entrée) : Hytale n'envoie pas les touches au serveur. Seuls les boutons de la fenêtre existent (`WandPage`).
- **Pas de décalage de sol** par les tags du plan : nos prefabs Hytale n'en ont pas. L'ancre est le bloc au-dessus du bloc cliqué (`WandInteraction.anchor`) : l'interaction `OpenCustomUI` n'attend pas les données du client, la face visée n'est donc pas connue. Structurize ancre sur la face visée.
- **Recette** : les pierres de MineColonies sont remplacées par leurs équivalents Hytale : pierre (`Rock_Stone_Cobble`), pierre noire (`Rock_Basalt_Cobble`), ardoise des profondeurs (`Rock_Slate_Cobble`), plus 6 bâtons (`Ingredient_Stick`).
- **Collage créatif** (`construction/wand/WandPaste`, `construction/wand/PasteQueue`, recherche `docs/research/build-goggles-and-wand.md`, section « Collage créatif ») :
  - seul « Pretty » existe (bouton « Coller ») ; « Complete » est omis, car nos prefabs n'ont pas de blocs substituts et les deux poseraient les mêmes blocs ;
  - la hutte collée prend le **niveau choisi**, l'intention du code de MineColonies (sa lecture du chemin donnerait le niveau 1) ;
  - les règles de l'hôtel de ville (`checkHutRules`) vérifient la distance aux colonies et au point d'apparition dès le collage ; MineColonies ne les vérifie qu'à la création de la colonie ;
  - `Structurize.MaxOperationsPerTick` a un minimum de 1 au lieu de 0 : à 0, un collage n'avancerait jamais ;
  - les coffres collés sont vides : nos plans remplacent les coffres des prefabs par un coffre vide, MineColonies collerait leur contenu ;
  - pas d'annuler/refaire ni de phase des entités.

**Entrepôt et livreurs** (`logistics/`, `colony/view/LogisticsViews`, `plugin/ui/logistics`, spec `2026-09-27-hycolony-sp3a-warehouse-courier-design.md`, section « Écarts » : liste complète)
- **Rangements** : les coffres Hytale posés par le constructeur remplacent les étagères MC ; pas de 2e choix « objet similaire » (onglet créatif) ; pas d'amélioration de stockage.
- **Résolveurs de stock** : un seul résolveur au lieu du couple générique / concret ; pas de `StackList` ni de stock minimum.
- **Pluie** : un ouvrier s'arrête s'il pleut **ou neige** à la position de sa hutte (MC : pluie globale) ; le constructeur aussi, comme MC.
- **Livreur** : pas d'interactions de chat, de faim, de sac à dos ni de statistiques ; vitesse par effet d'entité arrondie à 0,05 ; objets qui ne rentrent plus jetés au sol ; entrepôt plein : le livreur attend une action du joueur (exception assumée à CLAUDE.md § 4).
- **Plans provisoires** : prefabs vanilla, générateurs de coffre remplacés par un coffre vide obtenable, pour ces deux huttes seulement.
- **Fenêtres** : liste des livreurs de l'entrepôt en lecture seule, stock toujours trié par quantité, enfants des requêtes marqués « > » au lieu d'être décalés, tâche en cours encadrée de vert.
- **Outil cassé par Hytale** : Hytale garde un outil cassé à 0 de durabilité (MineColonies le détruit). Le port garde la règle de MineColonies (l'outil disparaît à sa durabilité) ; un outil cassé qui arrive d'ailleurs (un joueur le donne) compte comme usé jusqu'au bout (`ItemCatalog.wornOut`), donc comme s'il n'existait plus : aucune requête ne le prend (`Deliverable.matches` sur une pile, pour la hutte comme pour l'entrepôt), le livreur ne l'emporte pas pour une livraison, le bâtisseur ne le sort pas de sa hutte, ne le prend ni ne le garde comme outil, et le range à sa hutte au dépôt suivant.
- **Usure des objets que le cœur n'use pas** (armes, armures) : leur durabilité Hytale voyage aussi, à raison d'un « usage » par point, pour qu'aucun transfert ne les répare.
