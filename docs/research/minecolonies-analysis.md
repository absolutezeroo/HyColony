# MineColonies — analyse de référence

Source : https://github.com/ldtteam/minecolonies (branche `version/main`, Forge 1.20.1, GPL-3.0).
Analyse faite le 2026-09-25. Chemins relatifs à `src/main/java/com/minecolonies/`.
Ce document décrit les **mécaniques** à reproduire ; pas le code Minecraft.

---

## 1. Architecture générale

- `api` (interfaces + registres statiques) / `apiimp` (création des registres) / `core` (implémentations).
- **Registres d'entrées** : chaque type (bâtiment, job, module, effet de recherche, facteur de bonheur, type d'événement...) est un objet "entry" construit par builder avec des lambdas producteurs + un id. La sauvegarde stocke l'id ; au chargement on retrouve l'entry et on reconstruit.
  - Ex : `new BuildingEntry.Builder().setBuildingBlock(hut).setBuildingProducer(BuildingX::new).addBuildingModuleProducer(MODULE).createBuildingEntry()`
- **Data-driven (JSON)** : noms de citoyens, maladies, quêtes, recettes des artisans, recherches (+ effets), objets de recrutement, objets d'étude, visiteurs, camps/navires de raid. Blueprints : 23 "style packs", un fichier par niveau (`builder1..5.blueprint`) rangés par catégorie.
- **En code** : types de bâtiments/jobs, composition des modules, facteurs de bonheur, types d'effets de recherche, IA.

## 2. Colonie

- `Colony` compose des managers : WorkManager (ordres de travail), RegisteredStructureManager (bâtiments), CitizenManager, VisitorManager, GraveManager, RaidManager, EventManager, ReproductionManager, EventDescriptionManager (journal), StatisticsManager, QuestManager, ResearchManager, RequestManager, Permissions.
- **Création** : le joueur pose l'hôtel de ville → `createColony(world, pos, player, name, pack)` → propriétaire, claim des chunks.
- **Territoire** : claims par chunk. Rayon initial `initialColonySize` = 4 chunks. Chaque bâtiment ajoute un claim : 1 chunk (niv. 1-3), 2 chunks (niv. 4-5) + son emprise. Config `maxColonySize` 20, `minColonyDistance` 8.
- **Machine d'état de colonie** (réévaluée toutes les 100 ticks) :
  - `ACTIVE` : un joueur abonné est proche, ou > 40 chunks chargés et joueur important en ligne.
  - `UNLOADED` : joueur important en ligne ou timer de force-load.
  - `INACTIVE` : sinon.
- **Cadences en ACTIVE** (ticks jeu, 20/s) : citoyens 60 · abonnés/vue 20 · requêtes 11 · work manager 20 · waypoints 100 · tick lent 500 (bâtiments, modules tickants, managers).
- Jour/nuit : à la tombée de la nuit → événements, raids, bonheur ; à l'aube → `day++`, réveil.
- **Pas de simulation hors chargement** : l'IA vit dans l'entité. Rattrapage uniquement via `processOfflineTime` (> 1 h hors ligne, sert surtout à l'université).
- **Permissions** : rangs OWNER, OFFICER, FRIEND, NEUTRAL, HOSTILE (+ rangs custom), masque de 27 actions (ACCESS_HUTS, PLACE_HUTS, BREAK_HUTS, EDIT_PERMISSIONS, MANAGE_HUTS, RECEIVE_MESSAGES, PLACE_BLOCKS, BREAK_BLOCKS, OPEN_CONTAINER, ATTACK_CITIZEN, RALLY_GUARDS, ...).
- **Événements** : bus interne (ColonyCreated, BuildingAdded, CitizenAdded...), journal de colonie (Built/Upgraded/Repaired/Deconstructed, Born/Died/GrownUp/Spawned, max 100 entrées), événements monde (raids).

## 3. Bâtiments

- Hiérarchie : `AbstractSchematicProvider` (position, niveau, pack, blueprint, rotation, coins) → `AbstractBuildingContainer` (conteneurs) → `AbstractBuilding` (modules, requêtes, résolveurs, `isBuilt`) → classes concrètes.
- **Niveaux 1-5**. Upgrade = ordre de travail pour le constructeur, puis `onUpgradeComplete`.
- **Modules** (composition, ~157 producteurs) ; un module implémente des interfaces "capacité" :
  `IPersistentModule`, `ITickingModule`, `IBuildingEventsModule`, `IAssignsCitizen`, `IAssignsJob`, `ICraftingBuildingModule`, `ICreatesResolversModule`, `IHasRequiredItemsModule`, `ISettingsModule`, `IItemListModule`, `IMinimumStockModule`...
  Principaux : WorkerBuildingModule (job + compétences primaire/secondaire + limite), CraftingWorkerBuildingModule, GuardBuildingModule, LivingBuildingModule (capacité = niveau), BedHandlingModule, SettingsModule, WarehouseModule, MinerBuildingModule, BuildingResourcesModule...
  Sauvegarde : un sous-objet `modules` indexé par clé de module.
- **Affectation** : `HiringMode` DEFAULT / AUTO / MANUAL / LOCKED. Max citoyens = somme des capacités des logements.
- **54 types de bâtiments** :
  - Ville/service : townHall, home, tavern, wareHouse, postBox, stash, builder, deliveryman, university, library, school, hospital, graveyard, mysticalSite
  - Militaire : barracks, barracksTower, guardTower, gateHouse, archery, combatAcademy
  - Ressources : farmer, fisherman, lumberjack, miner, simpleQuarry, mediumQuarry, largeQuarry, plantation, beekeeper, florist, netherWorker
  - Élevage : chickenHerder, cowboy, shepherd, swineHerder, rabbitHutch, stable
  - Artisans : bakery, blacksmith, cook, kitchen, crusher, sawmill, sifter, smeltery, stoneMason, stoneSmelter, enchanter, glassblower, dyer, fletcher, mechanic, concreteMixer, composter, alchemist

## 4. Citoyens

- **Données ≠ entité** : `CitizenData` (persistant : id, nom, genre, enfant, lit, maison, job, saturation, compétences, bonheur, partenaire...) ; `EntityCitizen` jetable, recréée depuis les données.
- Respawn : toutes les 5 min, si l'entité manque → réapparition à `nextRespawnPos` / dernière position / lieu de travail / maison.
- Citoyens initiaux : `initialcitizenamount` (4) apparaissent à l'hôtel de ville, genres équilibrés.
- **11 compétences** (complémentaire / adverse) : Athletics (Strength/Dexterity), Dexterity (Agility/Athletics), Strength (Athletics/Agility), Agility (Dexterity/Strength), Stamina (Knowledge/Mana), Mana (Focus/Stamina), Adaptability (Creativity/Focus), Focus (Mana/Adaptability), Creativity (Adaptability/Knowledge), Knowledge (Stamina/Creativity), Intelligence (—).
- **XP** : `xp × (1 + (niveauTravail + niveauMaison)/10) × (1 + Int/100) × (1 + recherche)` ; 0 si saturation = 0. Primaire 100 %, secondaire 50 %, complémentaire +10 %, adverse −10 %. XP niveau suivant = `1 + 5L + 0.005L³`. Plafond 99 et `(niveauMaison+1)×10` si maison non max.
- **Effet des compétences** : cassage `0.85^(skill/2)` ; pose `150 / (skill/2 + 10)` ticks ; craft `10 / min(skill/2+1, MAX)`.

### Compétences par job (primaire / secondaire)
builder Adaptability/Athletics · farmer Stamina/Athletics · composter Stamina/Athletics · lumberjack Strength/Focus · miner & quarrier Strength/Stamina · fisherman Focus/Agility · cook Adaptability/Knowledge · baker Knowledge/Dexterity · blacksmith Strength/Focus · stonemason Creativity/Dexterity · sawmill Knowledge/Dexterity · smelter Athletics/Strength · crusher Stamina/Strength · sifter Focus/Strength · courier Agility/Adaptability · healer Mana/Knowledge · researcher Knowledge/Mana · teacher Knowledge/Mana · pupil Knowledge/Mana · student Intelligence/Intelligence · undertaker Strength/Mana · enchanter Mana/Knowledge · alchemist Dexterity/Mana · dyer Creativity/Dexterity · fletcher Dexterity/Creativity · glassblower Creativity/Focus · planter Agility/Dexterity · florist Dexterity/Agility · beekeeper Dexterity/Adaptability · chickenHerder Adaptability/Agility · cowboy Athletics/Stamina · shepherd Focus/Strength · swineherder Strength/Athletics · rabbitHerder Agility/Athletics · mechanic Knowledge/Agility · concreteMixer Stamina/Dexterity · netherworker Adaptability/Strength · gardes : knight/huscarl/cavalry Adaptability/Stamina, ranger/marksman Agility/Adaptability, druid Mana/Focus.

## 5. Système de requêtes (cœur économique)

- Un `RequestManager` par colonie, tické toutes les 11 ticks.
- **Requête** : token (UUID), requester, requestable, état, résultat, parent, enfants, `deliveries` (piles à récupérer).
- **Requestables** : Stack, MinimumStack, StackList, RequestTag, Tool (type, niveau min/max), Food (count, nutrition min), Burnable, SmeltableOre, PublicCrafting / PrivateCrafting, Delivery (source → cible, priorité), Pickup (priorité, jour, quantité).
  Priorités livraison : MAX_BUILDING 10, DEFAULT 13, MAX_AGING 14, PLAYER_ACTION 15.
- **États** : CREATED, REPORTED, ASSIGNING, ASSIGNED, IN_PROGRESS, RESOLVED, FOLLOWUP_IN_PROGRESS, COMPLETED, OVERRULED, CANCELLED, RECEIVED, FINALIZING, FAILED.
  Chemin normal : CREATED → ASSIGNING → ASSIGNED → IN_PROGRESS → (enfants) → RESOLVED → FOLLOWUP_IN_PROGRESS (ex. livraison) → COMPLETED → le citoyen récupère → RECEIVED (nettoyage).
  - Enfant CANCELLED/FAILED → on annule les frères et on **réassigne le parent**.
  - OVERRULED (le joueur fournit l'objet) → annule enfants → COMPLETED.
- **Résolveurs** (fournis par les bâtiments via `ICreatesResolversModule`) ; priorités : Building 200, Warehouse 150, Crafting 125, Default 100, Retrying 50, Player 0.
- **Algorithme d'assignation** : candidats indexés par type de requestable (type le plus spécifique d'abord) ; tri par priorité décroissante ; le premier qui `canResolve` et dont `attemptResolve` ≠ null gagne provisoirement ; parmi la **même priorité**, un résolveur de métrique d'adéquation strictement meilleure le remplace (les enfants de la tentative précédente sont annulés) ; changer de priorité arrête la recherche. Les enfants héritent de la blacklist.
- **Résolveurs principaux** :
  - BuildingRequestResolver (200) : le stock du propre bâtiment du demandeur.
  - Warehouse (150) : stock de l'entrepôt (en gardant `leftOver`) → followup = requêtes `Delivery` vers le demandeur. Métrique = `max(dist/10, 1) + taille de file`.
  - Crafting (125) : trouve une recette, découpe en requêtes de craft (limité par la taille d'inventaire), refuse les cycles.
  - Production (100) : demande les ingrédients (Stack enfants), met la tâche dans la file de l'artisan le moins chargé ; followup = Delivery vers le demandeur.
  - Delivery / Pickup (100) : mettent la requête dans la file de l'entrepôt ; il faut des coursiers.
  - Retrying (50) : réessaie toutes les 1200 × 11 ticks, 3 tentatives, puis blacklist → tombe au joueur.
  - Player (0) : attend que le joueur fournisse (GUI ou dépôt dans un coffre de la cabane).
- **Coursiers** : modèle "pull". Score = `priorité (+ −100 si pickup futur) + (tailleFile − index) − sqrt(manhattan(source, cible))`. Les requêtes doublées prennent +1 de priorité (vieillissement, max 14). Livraisons groupées vers la même cible : `1 + secondarySkill/5`.
- **Recettes** : `IRecipeStorage` (entrées, sortie, bloc intermédiaire, sorties secondaires, outils), dédupliquées globalement.
- **À simplifier dans notre port** : TypeToken/réflexion → enum/sealed + ensembles pré-calculés ; 59 factories → un codec par type ; handlers + 5 stores → une classe avec maps directes (+ index inverse requête → résolveur) ; cascades récursives → fonction de transition explicite (même ordre de callbacks).
- **Bugs connus à décider** : pickups jamais fusionnés ; `getCurrentReassignmentAttempt` inversé ; pénalité "cible non chargée" écrasée ; `overruleRequest` appelle OVERRULED deux fois ; `FASTEST_FIRST` non implémenté.

## 6. Jobs et IA

- ~51 jobs. Un bâtiment ne référence pas de job : ses **modules** en fournissent.
- **Machine d'état à cadence** (`TickRateStateMachine`) : transitions `AITarget(état, condition, action→prochainÉtat, tickRate)` ; ordre d'évaluation : AI_BLOCKING → EVENT → STATE_BLOCKING → transitions de l'état courant ; **la première qui déclenche termine le tick**. Cadence par transition (1..12000), décalage initial réparti (mod 50). `null` = pas de transition.
- IA citoyen haut niveau (toutes les 10 ticks) : IDLE, FLEE, EATING, SICK, SLEEP, MOURN, WORK, WORKING, INACTIVE. En WORKING → tick de l'IA de job (cadence 5).
- ~130 états de travail (`AIWorkerState`), chacun avec `isOkayToEat`.
- **Transitions communes** : init ; délai de travail (`waitingForSomething`, le citoyen "frappe" pendant le délai) ; inventaire plein ou 32 actions → vider à la cabane (en gardant ce que le bâtiment exige ; si cabane pleine → pickup) ; `NEEDS_ITEM` quand requête sync ouverte/complétée → aller chercher les livraisons.
- **Outils** : outil le moins puissant suffisant, borné par le niveau max d'équipement de la cabane ; sinon cherche dans la cabane, sinon requête `Tool` + statut STUCK.
- **Déplacement** : jamais bloquant ; `walkTo...` renvoie vrai à l'arrivée, sinon on réévalue plus tard. Le constructeur choisit un point de travail et construit tout ce qui est à ~10 blocs sans bouger.
- **Cassage** : la mod casse elle-même les blocs (`WorldUtil.removeBlock`). Temps = `500 × 0.85^(skill/2) × dureté / vitesseOutil × (1 − recherche)` (constructeur ×0.5). Drops directement dans l'inventaire du citoyen (pas d'items au sol). Usure d'outil 1.

## 7. Construction

- Ordres de travail : BUILD, UPGRADE, REPAIR, REMOVE (+ décoration, mine, plantation). Assignation par le WorkManager toutes les 20 ticks : par priorité ; le constructeur doit avoir un niveau ≥ niveau cible (ou max, ou sa propre cabane) et être à ≤ 100 blocs.
- **Étapes** : nouveau bâtiment = CLEAR puis BUILD_SOLID, WEAK_SOLID, CLEAR_WATER, CLEAR_NON_SOLIDS, DECORATE, SPAWN (entités). Suppression = REMOVE_WATER, REMOVE. CLEAR/REMOVE de haut en bas, construction de bas en haut.
- **Matériaux** : calculés en parcourant le blueprint, regroupés en **seaux** (≤ taille d'inventaire − 9 piles) ; on demande le seau courant et le suivant.
- **Pas de construction** : 1 bloc par appel ; résultats FINISHED / LIMIT_REACHED / MISSING_ITEMS / BREAK_BLOCK. Délai de pose = `15 × 10 / (skill/2 + 10) × (1 − recherche)`. XP 0.05/bloc, 8 par bâtiment fini.
- Progression sauvegardée : position de l'itérateur (coordonnées locales au blueprint) + étape. Les besoins matériels sont recalculés au chargement.
- Dépend de la lib **Structurize** : format blueprint, itérateurs, placement handlers par type de bloc, placeur pas à pas.

## 8. Vie des citoyens

- **Priorité d'IA** (non-gardes) : malade à l'hôpital → raid (rentrer) → dormir la nuit → malade/blessé → manger → deuil → pluie (inactif sauf config/recherche) → écoliers rentrent l'après-midi → travail → idle.
- **Nourriture** : saturation max 60, moyenne 10, basse 6. Décroissance toutes les 1200 ticks (jour) : facteur par niveau de maison 0→0.3, 1→0.6, 2→0.725, 3→1.0, 4→1.2, 5→1.5 × facteur du job + coût des actions (0.2 / action). Enfants ÷2. Maison ≥ 3 : nutrition ≥ niveau+1. Diversité requise = niveau maison ; qualité = max(0, niveau−2). Historique des 10 derniers repas.
- **Bonheur** = Σ(facteur × poids) / Σpoids (facteurs = 1.0 ignorés) × (1 + recherche) × 10, plafonné à 10.
  - Statiques : school 1, security 4 (`min(gardes / (travailleurs × 2/3), 2)`), social 2, mystical 1, food 3.
  - Temporels : homelessness 3, unemployment 2, health 2, idleatjob 1, slepttonight 1.5 (seuils 7 j / 14 j).
  - Expirants : death 3 (3 j), damage 2 (1 j), hadgreatfood 2 (5 j), raidwithoutdeath 1 (3 j), quest 2.
- **Maladies** (JSON : nom, rareté, objets de soin) ; contagion 1 % au contact ; immunité 90 min ; hôpital + soigneur.
- **Naissances** : timer `(5 min + rand 10 min) × pop / max(4, max)` ; il faut de la place, ≥ 2 citoyens, homme + femme non apparentés, un lit libre. Compétences de l'enfant héritées des parents. Croissance des enfants, école (2 × niveau élèves).
- **Taverne** : visiteurs (3 × niveau) recrutables contre des objets.
- **Mort** : tombe si mort dans la colonie (décroît en 10 min), fossoyeur peut ressusciter ; deuil d'un jour pour les proches.

## 9. Recherche

- Branches JSON (`civilian`, `combat`, `technology`, `unlockable`) ; chaque recherche : branche, niveau, parent, `exclusiveChildResearch`, `requirements` (bâtiment niveau N), `costs` (objets), `effects` (id + niveau).
- Effets JSON : `{"effect":true,"levels":[0.05,0.1,0.25,0.5,1.0]}` ; 106 fichiers, 81 ids (happinessmultiplier, saturationmultiplier, citizencapaddition, levelingmultiplier, workinginrainunlock, guardcrit, ...).
- Durée = 72 × baseTime branche × 2^(profondeur−1) points ; +1 point par chercheur par tick colonie. Niveau de l'université = profondeur max et nombre de recherches simultanées.
- Déblocage de bâtiments : l'effet `effects/<bâtiment>` doit dépasser le niveau courant pour upgrader.

## 10. Défense

- Gardes : knight, ranger, marksman, huscarl, druid, cavalry. Tour de garde 1, tour de caserne = niveau, guérite 2. PV bonus 2 × niveau, vision 15 + 3 × niveau.
- Modes : PATROL, GUARD, FOLLOW, PATROL_MINE ; patrouille AUTO/MANUAL ; suivi TIGHT/LOOSE ; retraite.
- **Raids** : pas avant 10 nuits, forcé après 14 + 2, sinon probabilité 1/(moy − min).
  - Niveau de raid = Σ adultes (5 + Σcompétences/100) + Σ bâtiments construits (5 + niveau²/5) + 3 × recherches finies, × min(1, pop/max) ; minimum 75.
  - Nombre de raiders = `1 + min(maxRaiders, niveau/60 × modDifficulté × (1 + 0.05 × joueurs) × rand(0.85–1.15))`.
  - Difficulté interne 1–14 (départ 7), ajustée selon les pertes (> 15 % baisse, < 5 % hausse). Fin si pertes > 50 %.
  - Cultures : barbares, pirates (navires), égyptiens (désert), amazones (jungle), vikings (taïga), pirates noyés. Hordes : 10 % chefs, 30 % archers.

## 11. UI et interactions

- Le client ne voit que des **vues** (ColonyView, CitizenDataView, vues de bâtiment/module), synchronisées par deltas vers les joueurs proches ; le client envoie des messages d'action (Hire/Fire, TriggerSetting, TryResearch, UpdateRequestState...).
- Fenêtres : hôtel de ville (actions, citoyens, journal, permissions, stats, réglages), citoyen (principal, bonheur, job, famille, requêtes), bâtiment (principal + onglets par module), arbre de recherche, détail de requête, embauche.
- **Interactions** (le citoyen "demande" au joueur) : priorité HIDDEN / CHITCHAT / PENDING / IMPORTANT / BLOCKING ; réponses (ok/ignorer/rappel/passer) ; validées par prédicats (54 enregistrés).

## 12. Persistance

- Sérialisation hiérarchique (Colony → managers → bâtiments → modules → citoyens → job). Types polymorphes = id de registre.
- Fichiers miroir par colonie + sauvegardes zip.
- Versionnage minimal (`DATA_VERSION = 1`, conversions inline) → **à faire mieux dans HyColony**.

## 13. Config serveur notable

`initialcitizenamount` 4 · `maxcitizenpercolony` 250 · `workersalwaysworkinrain` false · `foodmodifier` 1.0 · `diseasemodifier` 5 · `maxColonySize` 20 · `minColonyDistance` 8 · `initialColonySize` 4 · `enablecolonyraids` true · `raidDifficulty` 5 · `maxRaiders` 80 · nuits entre raids 14 (min 10) · `enablecolonyprotection` true · `creativeresolve`.
