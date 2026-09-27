# HyColony SP3a : entrepôt et livreurs

Conception validée avec l'utilisateur le 2026-09-27 (« tant que c'est comme MineColonies »). Recherche : `docs/research/sp3a-mc-logistics-lumberjack.md` (§ 0, 1, 2), source MineColonies `version/main` commit `6b3916a`. Bûcheron et mineur exclus (plants Hytale à fabriquer avec de l'essence de vie).

## Objectif

La colonie stocke ses objets dans un entrepôt et des livreurs les portent aux huttes qui les demandent (d'abord le constructeur), puis rapportent à l'entrepôt ce que les huttes n'ont pas à garder. Le joueur n'a plus à livrer lui-même.

## Portée

- **Dedans :** entrepôt (stock, résolveurs, rattachement des livreurs, rangement), hutte du livreur et son IA, requêtes `Delivery` et `Pickup`, priorité de ramassage par hutte, fenêtres correspondantes, objets et plans des deux huttes.
- **Plus tard (backlog) :** stock minimum (`MinimumStockModule`), bouton « trier » (niveau 3), améliorations de stockage (niveau 5, `MAX_STORAGE_UPGRADE = 3`), bûcheron, mineur ou carrière.

## Règles de jeu

Toutes reprises de MineColonies ; les références `§` renvoient à la recherche.

### Entrepôt (`BuildingWareHouse`, § 1)

- Niveaux 1 à 5, pas d'employé, `canBeGathered = false`. Nombre par colonie non limité (MC actuel).
- **Rangements** = le bloc de hutte + chaque conteneur du plan posé par le constructeur (`BlueprintEntry.hasContainer`), équivalent des étagères MC (`registerBlockPosition`). Capacité = somme des cases.
- **Livreurs rattachés** (`CourierAssignmentModule`) : au plus `niveau × 2` ; à chaque tick de colonie, tout livreur sans entrepôt est rattaché si la place le permet ; un citoyen qui n'est plus livreur est détaché.
- **Résolveurs de stock** (priorité **150**, entre la hutte 200 et le livreur 100) : ne servent jamais l'entrepôt lui-même ; comptent le stock de tous les entrepôts ; résolvent si `total ≥ count` ou `≥ minCount` ; sinon une sous-requête pour le manque (`copyWithCount(count − available)`) dont l'entrepôt est le demandeur. À la complétion : une `Delivery(case source → demandeur, priorité 13)` **par case source** (`getFollowupRequestForCompletion`). Métrique `max(dist/10, 1) + taille de la file`. Le `leftOver` des listes (`StackList`) est gardé.
- **Rangement** (`dumpInventoryIntoWareHouse`, § 1.5) : pour chaque case du livreur, le premier rangement avec une case libre qui contient déjà l'objet, sinon un rangement vide, sinon celui qui a le plus de cases libres ; si aucun, message à la colonie (au plus toutes les 6 000 ticks, variante selon le niveau) et arrêt du rangement.

### Requêtes (§ 2.2)

- Priorités : `MAX_BUILDING_PRIORITY = 10`, `DEFAULT_DELIVERY_PRIORITY = 13`, `MAX_AGING_PRIORITY = 14`. `incrementPriorityDueToAging` : `min(14, p + 1)`.
- `Delivery(début, cible, pile, priorité)` : début = case de rangement, cible = hutte qui reçoit.
- `Pickup(priorité, jour, quantité)` : le demandeur est la hutte à vider ; `jour` = jour de colonie à partir duquel il est dû.
- Résolveurs du livreur (priorité **100**, un de chaque par entrepôt) : acceptent si l'entrepôt existe et a des livreurs ; `resolve` ajoute le jeton à la **file de l'entrepôt** ; la requête reste en cours jusqu'à ce qu'un livreur la termine. Métrique livraison `max(dist/10, 1) + file`, ramassage `dist`.
- Une livraison échouée passe par l'annulation standard (enfants frères annulés, parent réassigné), déjà en place dans `RequestManager`.

### Livreur (`BuildingDeliveryman`, `JobDeliveryman`, `EntityAIWorkDeliveryman`, § 2.1, 2.4, 2.5)

- Hutte niveaux 1 à 5, **1 livreur par hutte** ; compétences Agilité (principale) et Adaptabilité (secondaire).
- **Vitesse** : `+ Agilité × 0,003` sur la base 0,3 (`BONUS_SPEED_PER_LEVEL`).
- **Livraisons en parallèle** : `1 + Adaptabilité / 5`.
- **Limite de ramassage** (niveau `L` de sa hutte) : si `L < 5`, plus rien dès que l'inventaire contient `≥ 2^(L−1) + 1` piles ; niveau 5 sans limite.
- **Choix de la tâche** (modèle « pull », § 2.4) : sa propre file d'abord ; sinon la meilleure de la file de l'entrepôt selon `p = priorité ; −100 si ramassage pas encore dû ; + (taille − index) ; − ⌊√(manhattan(source, cible))⌋`, égalité au premier ; les entrées avant l'élue vieillissent de +1 ; celles de même cible la rejoignent jusqu'au parallèle maximal ; un ramassage passe en dernier.
- **États et délais** : IDLE (1) → START_WORKING (100 : sans entrepôt, pas de travail + interaction « pas d'entrepôt ») → PREPARE_DELIVERY (5), DELIVERY (5), PICKUP (5, marche 20), DUMPING (20). Détails de `decide`, `prepareDelivery`, `deliver`, `pickup`, `dump` et `finishRequest` : § 2.5, repris tels quels, y compris l'échange d'une pile hors requête quand la cible est pleine (`forceItemStackToItemHandler`).
- **XP** : +0,05 par ramassage, +1,5 par livraison.
- **Pluie** : ne travaille pas sous la pluie (`canWorkingDuringRain = false`), sauf hutte au niveau maximal (règle commune à tous les ouvriers, voir « Écarts »).
- **Inactivité** : après 36 000 ticks sans travail, ses tâches sont annulées ; à la reprise, les requêtes de livraison et ramassage non assignées sont relancées.
- Le livreur ne mange pas un objet qu'il livre ; il n'est pas soumis au dépôt générique des ouvriers.

### Ramassages (§ 2.6)

- Priorité de ramassage par hutte : défaut **5**, bornes **0 à 10**, ±1 dans la fenêtre ; **0 = jamais**.
- `createPickupRequest(quantité, forcé)` : priorité `forcé ? 10 : prioritéHutte` ; au plus **une** demande de ramassage ouverte par hutte ; jour = `jourColonie + max(0, (10 − prioritéHutte) − quantité / 16)`.
- Déclencheurs : le bouton « forcer un ramassage » (`createPickupRequest(64, true)`) ; hutte pleine (forcé) ; dépôt d'un ouvrier (non forcé).
- Le livreur prend, case par case (1 case / 5 ticks), ce que la hutte n'a pas à garder (`buildingRequiresCertainAmountOfItem`) : les objets `keepX`, ceux des livraisons de ses requêtes en cours, les modules « objets requis ».

## Écarts à MineColonies (à reporter dans la spec SP1+2 § 11)

- Rangements = conteneurs Hytale du plan (coffres) au lieu des étagères MC.
- Le constructeur est aujourd'hui le seul ouvrier : il demande un ramassage quand sa hutte est pleine et après un dépôt, selon la règle générique `AbstractEntityAIBasic` (§ 0.1). Aucun autre producteur tant que SP3 n'en ajoute pas.
- Pluie (tous les ouvriers, constructeur compris, dans `CitizenAI` comme MC `calculateNextState`) : un ouvrier s'arrête, même en pleine tâche, et flâne s'il pleut **ou neige** à la position de sa hutte (port `WorldQuery.isRainingAt`) ; MC teste une pluie globale au monde. Il travaille quand même si sa hutte est au niveau maximal (MC `WorkerBuildingModule.canWorkDuringTheRain`) ou si `Gameplay.WorkersAlwaysWorkInRain` (MC `workersAlwaysWorkInRain`, défaut `false`) est vrai. Le drapeau MC `canWorkingDuringRain` vaut `false` pour tous les ouvriers portés (constructeur, livreur) : pas de champ tant qu'aucun ne l'a à `true`. Pas de recherche `WORKING_IN_RAIN`, pas de statut « mauvais temps » ; un ouvrier sans hutte est laissé à son IA de métier (MC l'arrête).
- Ramassage après dépôt (`PickupRequests.afterDump`) : notre dépôt range tout l'inventaire en une passe, la hutte pleine est donc testée une fois après (MC teste avant chaque case : une hutte remplie par la toute dernière case y reçoit un ramassage non forcé, ici forcé). Pas d'interaction « coffre plein ».
- Requête terminée d'un bâtiment (`Building.onRequestComplete`, MC `AbstractBuilding.onRequestedRequestComplete`) : MC passe à RECEIVED toute requête de niveau bâtiment dès qu'elle est terminée. Ici, seules celles qui n'apportent pas d'objets (ramassage, livraison) le sont ; une requête d'objets de niveau bâtiment reste COMPLETED jusqu'à ce que l'ouvrier prenne ses objets, car notre constructeur range au niveau bâtiment les requêtes asynchrones que MC range sous son citoyen.
- Outils gardés (`KeepToolsModule`) : le constructeur garde pioche, pelle et hache ; houe et cisailles n'existent pas dans `ToolType`.
- Rangement (`WarehouseStorage`) : le 2e choix de MC (une étagère qui contient un objet « similaire », même onglet créatif) est sauté, les objets Hytale n'ont pas d'onglet créatif dans le cœur. Sans améliorations de stockage, un entrepôt de niveau 5 plein envoie toujours le message « amélioration maximale » de MC, jamais « payez un bloc d'émeraude ». Le premier message « entrepôt plein » n'est pas retardé : notre compteur de ticks repart de 0 au lancement du serveur (le temps de jeu de MC est sauvegardé).
- Résolveurs de stock (`WarehouseStockResolver`) : un seul résolveur au lieu du couple générique / concret de MC, qui ne se distinguent que par la façon de compter (NBT, usure contre prédicat) ; `Deliverable.matches` couvre les deux. Pas de `StackList` (`INonExhaustiveDeliverable`) dans nos requêtes, donc le `leftOver` gardé vaut toujours 0 ; pas de `MinimumStack`, donc la règle « pas pour le stock minimum d'un autre entrepôt » n'a rien à tester. Les deux arrivent avec le stock minimum (backlog).
- Choix de la tâche du livreur (`CourierTaskPicker`, MC `JobDeliveryman.getCurrentTask`) : un jeton en tête de la file du livreur dont la requête a disparu est retiré (MC le renvoie `null` indéfiniment) ; les jetons morts de la file de l'entrepôt sont retirés même quand aucune tâche n'est trouvée (MC ne les retire qu'avec une tâche) ; une source ou une cible inconnue compte pour une distance nulle au lieu de lever une exception. Le malus de −1000 d'une cible non chargée n'est pas porté : MC l'écrase par la priorité pour toute tâche de livreur, il ne s'applique donc jamais.
- IA du livreur (`DeliverymanAI`, MC `EntityAIWorkDeliveryman`) :
  - les états de travail (préparation, livraison, ramassage) ne lisent que la tête de la file propre du livreur ; seul START_WORKING (toutes les 100 ticks) tire une tâche de l'entrepôt (MC appelle `getCurrentTask` à chaque pas, qui tire aussi quand la file est vide ; un tirage note toute la file de l'entrepôt) ;
  - `finishRequest` sur une livraison en tête sans rien de chargé règle la tête seule (MC ne règle rien et garde la tête pour toujours) ;
  - pas d'interactions de chat (« pas d'entrepôt », « coffre plein »), pas de statut visible ni de sac à dos, pas de statistiques, pas de faim (`decreaseSaturationForContinuousAction`) : ces systèmes n'existent pas encore dans le cœur ;
  - l'échange d'une pile quand la cible est pleine (`forceItemStackToItemHandler`) passe par le port de conteneurs, par objet et non par case : l'extraction peut prendre l'objet dans une autre case du même conteneur ; ce qui n'a pas pu être échangé est remis ;
  - « objets de ses requêtes » de la cible (`isItemStackInRequest`) : toutes les requêtes ouvertes du bâtiment, pas seulement celles de ses citoyens, car notre constructeur demande ses matériaux au nom de la hutte ;
  - ramassage : le parcours case par case porte sur les cases non vides des conteneurs de la hutte (hutte d'abord) ; l'indice reste en place quand la pile est partie entière ;
  - vitesse : facteur de la vitesse de base donné au corps (`CitizenBodies.setMovementSpeed`), recalculé à chaque décision (MC `onLevelUp`) et remis à 1 quand l'IA de métier est abandonnée (MC retire le modificateur avec le métier) ;
  - pas de test de dimension (`isReachableFromLocation`) : un seul monde ;
  - objets sortis d'un conteneur qui n'y rentrent plus (rangement ou échange défait) : jetés au sol à cet endroit et journalisés (MC ne les remet pas) ;
  - entrepôt plein (exception assumée à CLAUDE.md § 4, comme MC) : un livreur chargé alterne DUMPING et START_WORKING et ses livraisons attendent ; la sortie est une action du joueur (libérer de la place), annoncée par le message « entrepôt plein » toutes les 5 minutes.

- Plugin (tâche 11) :
  - vitesse : Hytale n'a pas d'attribut de vitesse réglable sur un PNJ (le maximum du contrôleur `Walk` est final) ; le facteur passe par un effet d'entité infini (`HorizontalSpeedMultiplier`), arrondi au pas de 0,05 le plus proche (effets `HyColony_Speed_105` à `_200` de l'id-map ; plus près de 1 que de 1,05 : aucun effet) ; MC règle l'attribut exactement ;
  - plans provisoires (prefabs vanilla) : un générateur de coffre (`Block_Spawner_Block` dont la table contient un bloc à conteneur) devient un coffre vide obtenable du style (`Furniture_Crude_Chest_Small` Outlander, `Furniture_Kweebec_Chest_Small` Kweebec), pour les seuls niveaux marqués `spawnerChests` dans `styles.json` (entrepôt, livreur) ; les autres huttes ignorent toujours les générateurs (leurs chantiers en cours ne changent pas). Les coffres Outlander vanilla (`Furniture_Human_Ruins_Chest_Small`) ne sont pas obtenables, d'où un coffre fixe par style plutôt que le coffre tiré par la table.

## Architecture

- **Cœur**, nouveau domaine `logistics/` découpé par sous-domaine (15 fichiers au plus par paquet), seul le point d'entrée de chacun est public :
  - `logistics/warehouse` : bâtiment, module de rattachement des livreurs, file de requêtes, résolveurs de stock (générique et concret), rangement ;
  - `logistics/courier` : bâtiment, job, file du livreur, choix de la tâche, IA en états (collaborateurs séparés : préparation, livraison, ramassage, dépôt) ;
  - `logistics/pickup` : priorité de ramassage par hutte, création des demandes, calcul de ce qu'une hutte garde ;
  - `request/model` : `Delivery` et `Pickup` ;
  - persistance des nouveaux champs par `MigrationChain` avec fixture de l'ancienne version.
  - L'IA du livreur réutilise la marche, l'anti-blocage et l'évitement des dangers existants (`kernel/nav`).
- **Plugin** :
  - objets et recettes des huttes (recettes MC `blockhutwarehouse` et `blockhutdeliveryman` transposées en matériaux Hytale, table établie par la recherche du plan) ;
  - plans dans `hycolony/styles.json` : prefabs vanilla contenant des conteneurs, obtenables en survie ; **liste validée par l'utilisateur** avant intégration ;
  - fenêtres : entrepôt (livreurs rattachés, stock), hutte du livreur (onglet travail, liste de tâches), et sur chaque hutte les boutons ± de priorité de ramassage et « forcer un ramassage » ;
  - traductions en-US et fr-FR ; `.ui` validés avec l'éditeur de l'utilisateur.
- **Tests du cœur (TDD)** : chaque règle ci-dessus (résolveurs et métriques, livraisons par case source, rattachement `niveau × 2`, choix de la tâche et vieillissement, parallèle, limite de ramassage, jour et priorité des ramassages, une seule demande ouverte, ce qu'une hutte garde, rangement et message d'entrepôt plein, inactivité, pluie), plus une simulation de bout en bout : un constructeur demande des blocs, l'entrepôt les a, un livreur les apporte, le chantier avance.

## À vérifier (recherche du plan, puis en jeu)

- Météo Hytale lisible côté serveur (pluie à une position).
- Prefabs utilisables pour l'entrepôt et la hutte du livreur, par style, avec coffres, obtenables.
- Recettes MC exactes des deux huttes.

## Tests en jeu (`docs/TESTING.md`)

1. Poser un entrepôt et une hutte de livreur, les construire ; le livreur est embauché et rattaché.
2. Mettre des blocs dans l'entrepôt ; lancer un chantier : le livreur les apporte au constructeur.
3. Stock partiel : l'entrepôt livre ce qu'il a, le reste est demandé au joueur.
4. Forcer un ramassage sur la hutte du constructeur : le livreur vide ce qu'elle n'a pas à garder et le range à l'entrepôt.
5. Priorité de ramassage à 0 : aucun ramassage.
6. Entrepôt plein : message à la colonie.
7. Sous la pluie : le livreur et le constructeur (huttes sous le niveau maximal) s'arrêtent et flânent, puis reprennent quand elle cesse (si la météo est lisible).
