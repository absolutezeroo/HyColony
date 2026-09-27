# SP3a entrepôt et livreurs : plan d'implémentation

> **Pour les agents :** sous-skill requis : superpowers:subagent-driven-development. Étapes à cocher (`- [ ]`).

**But :** entrepôt qui stocke et répond aux requêtes, livreurs qui livrent et ramassent, à l'identique de MineColonies.

**Architecture :**
- Le modèle de requête est généralisé pour accueillir `Delivery` et `Pickup`, qui ne sont pas des objets demandés.
- Nouveau domaine du cœur `logistics/` (`warehouse`, `courier`, `pickup`), branché sur `RequestManager` par des résolveurs et sur `CitizenAI` par un nouveau job.
- Côté plugin : les deux huttes, la météo, la vitesse et les fenêtres.

**Technique :** Java 21 (cœur), Hytale 0.6.8 (plugin), JUnit 5, Gradle.

**Spec :** `docs/superpowers/specs/2026-09-27-hycolony-sp3a-warehouse-courier-design.md`. **Recherches :** `docs/research/sp3a-mc-logistics-lumberjack.md` (§ 0 à 2, règles MC détaillées) et `docs/research/sp3a-warehouse-courier-hytale.md` (météo, recettes, prefabs, conteneurs).

## Contraintes globales

- `CLAUDE.md` lu en entier. Tailles : 400 lignes par fichier, 40 lignes par méthode, 5 paramètres ; au plus 15 fichiers par paquet (`construction/builder` et `kernel/port` sont déjà pleins : **ne pas y ajouter de fichier**, ajouter des méthodes aux ports existants). TDD, `./gradlew build` vert à chaque commit, `git add` explicites.
- Toutes les constantes, formules et transitions viennent de la recherche MC, citées dans la Javadoc (`MC EntityAIWorkDeliveryman.prepareDelivery`). Tout écart porte `Deviation from MC:` et va dans la spec § « Écarts ».
- Jamais de `Message` brut sur `.Text` (déconnecte le client) : une chaîne ou `Message.translation`, et `.TextSpans` pour un message imbriqué. Textes en en-US et fr-FR. `.ui` validés avec le cœur de l'éditeur de l'utilisateur (`C:\Users\Ctuto\Desktop\Hytale UI Editor`, script jetable dans le scratchpad, zéro diagnostic).
- Aucun état ne bloque pour toujours ; pas d'allocation par tick dans les chemins chauds ; parcours bornés.
- Le serveur n'est jamais lancé.

## Décisions déjà prises

- Rangements de l'entrepôt : bloc de hutte plus conteneurs enregistrés par le constructeur (`BuilderBlockWork` → `addContainer`, déjà en place).
- **Prefabs vanilla provisoires** (l'utilisateur prépare les siens) : dans les plans de ces deux huttes, un générateur de coffre (`Block_Spawner_Block`, aujourd'hui ignoré par `HytaleBlueprintSource`) devient un coffre vide obtenable (`Furniture_Kweebec_Chest_Small` ou l'id exact de la recherche § 3). Aucun butin.
- Liste des prefabs validée par l'utilisateur : recherche § 3.3 à 3.6.
- Météo : le livreur ne travaille pas s'il pleut **ou neige** à la position de sa hutte (`Deviation from MC:` MC teste une pluie globale).
- Recettes : la base des huttes actuelles plus 1 `Furniture_Crude_Chest_Small` (entrepôt), ou plus 2 `Ingredient_Leather_Light` (livreur) ; recherche § 2.
- Schéma de sauvegarde : pas de nouvelle version tant qu'aucune clé existante ne change de sens (les nouvelles clés sont lues avec une valeur par défaut). Si une tâche doit changer une clé existante, elle ajoute une migration et une fixture.

## Points à surveiller en relecture

1. Une livraison dont la case source a été vidée entre-temps (par le joueur) : le livreur échoue proprement, le parent est réassigné, pas de blocage.
2. Un livreur retiré ou mort (hutte cassée, renvoi) pendant une livraison : ses tâches retournent à la file ou échouent, jamais perdues.
3. Un entrepôt cassé avec des requêtes en file : tout est annulé ou réassigné, aucune requête orpheline (`cancelOrphans`).
4. Objets jamais dupliqués ni perdus : le livreur plein, la cible pleine (échange), l'entrepôt plein (message, arrêt).
5. Ancienne sauvegarde (fixture v2) chargée sans erreur, entrepôt absent.

---

### Tâche 1 : généraliser le modèle de requête (sans changer le comportement)

**Fichiers :** `request/model/Requestable.java`, `Deliverable.java`, `request/Request.java`, `request/Resolver.java`, les résolveurs existants (`PlayerResolver`, `RetryingResolver`, `building/BuildingResolver`), `RequestSerializer`, et leurs appelants.

- [ ] `Request.requestable()` devient un `Requestable` ; `Resolver.handles(Requestable)`. Les résolveurs existants ne prennent que des `Deliverable` : même comportement qu'avant. Les appels qui ont besoin d'un `Deliverable` (compte, `withCount`, `matches`) passent par un test de type, ou par un accès typé `deliverable()` qui renvoie un `Optional`.
- [ ] Les tests existants restent verts sans modification de leurs assertions. Ajouter `playerResolverNeverTakesANonDeliverable` (avec un `Requestable` de test).
- [ ] Commit : `refactor(core): requests carry any requestable, not only deliverables`.

### Tâche 2 : requêtes `Delivery` et `Pickup`

**Fichiers :** `request/model/Delivery.java`, `Pickup.java`, `Requestable` (permits), `RequestSerializer`.

- [ ] `record Delivery(BlockPos start, RequesterId target, ItemAmount stack, int priority)` et `record Pickup(int priority, int day, int quantity)`, avec les constantes `MAX_BUILDING_PRIORITY = 10`, `DEFAULT_DELIVERY_PRIORITY = 13`, `MAX_AGING_PRIORITY = 14` et `withAgedPriority()` → `min(14, p + 1)`. Égalité comme MC (§ 2.2).
- [ ] Sérialisation : `"type": "delivery"` et `"type": "pickup"`, lecture tolérante.
- [ ] Tests : aller-retour de sérialisation ; vieillissement plafonné à 14 ; une fixture v2 sans ces types se charge.
- [ ] Commit : `feat(core): delivery and pickup requests (MC AbstractDeliverymanRequestable)`.

### Tâche 3 : priorité de ramassage et demandes de ramassage de chaque hutte

**Fichiers :** `logistics/pickup/` (nouveau) ; `building/Building` pour le champ persistant, ou un module ajouté à tous les types de bâtiment (choisir le plus simple et correct, en justifiant).

- [ ] Priorité par hutte : défaut 5, bornes 0 à 10, ±1, persistée (§ 2.6).
- [ ] `createPickupRequest(Colony, Building, int qty, boolean force)` : priorité `force ? 10 : prioritéHutte`, **une seule** demande ouverte par hutte, `jour = jourColonie + max(0, (10 − prioritéHutte) − qty / 16)`, rien si la priorité vaut 0 et que la demande n'est pas forcée.
- [ ] `amountToKeep` / `buildingRequiresCertainAmountOfItem` : les `keepX` du bâtiment, les livraisons de ses requêtes en cours, les modules « objets requis ». Pour le constructeur : outils (un par type) et ressources du chantier en cours (`BuilderStock.dump` sert de référence).
- [ ] Tests : `defaultPickupPriorityIsFive`, `priorityIsClampedToZeroAndTen`, `atMostOneOpenPickupPerBuilding`, `pickupDayFollowsMcFormula`, `forcedPickupHasPriorityTen`, `priorityZeroNeverCreatesAPickup`, `buildingKeepsItsToolsAndOpenRequestItems`.
- [ ] Commit : `feat(core): pickup priority and pickup requests (MC AbstractBuilding.createPickupRequest)`.

### Tâche 4 : bâtiment entrepôt, rattachement des livreurs, rangement

**Fichiers :** `logistics/warehouse/` ; enregistrement du type comme `ConstructionBuildingTypes` (`hycolony:warehouse`, clé de hutte `hut.warehouse`, niveau max 5).

- [ ] Module de rattachement : au plus `niveau × 2` livreurs ; au tick lent, rattache les livreurs sans entrepôt, détache ceux qui ne sont plus livreurs (§ 1.3).
- [ ] File de requêtes de l'entrepôt, persistée (liste de jetons).
- [ ] `store(courierInventory)` : règle de rangement § 1.5 ; entrepôt plein → message à la colonie, au plus toutes les 6 000 ticks, variante selon le niveau, puis arrêt.
- [ ] Tests : `attachesUpToTwoCouriersPerLevel`, `levelZeroAttachesNone`, `detachesACitizenNoLongerCourier`, `storesIntoTheRackAlreadyHoldingTheItem`, `thenIntoAnEmptyRackThenTheFreest`, `fullWarehouseSendsAMessageAtMostEveryFiveMinutes`, `queueSurvivesSaveAndLoad`.
- [ ] Commit : `feat(core): warehouse building, courier attachment and storage`.

### Tâche 5 : résolveurs de stock de l'entrepôt

**Fichiers :** `logistics/warehouse/` (résolveurs générique et concret, base commune).

- [ ] Priorité 150 ; `canResolve` : jamais pour l'entrepôt lui-même, stock de tous les entrepôts, `total ≥ count` ou `≥ minCount` ; `attemptResolve` : `[]` si assez, sinon un enfant pour le manque ; `followups` : une `Delivery(case source → demandeur, 13)` par case source, avec `leftOver` respecté ; métrique `max(dist/10, 1) + taille de la file` (§ 1.4).
- [ ] Tests : `neverServesItself`, `resolvesWithEnoughStockAcrossWarehouses`, `partialStockCreatesAChildForTheMissingCount`, `oneDeliveryPerSourceSlot`, `stackListKeepsItsLeftOver`, `suitabilityIsDistanceOverTenPlusQueue`, `resolvesEvenWithoutCouriersLikeMc`.
- [ ] Commit : `feat(core): warehouse stock resolvers (MC AbstractWarehouseRequestResolver)`.

### Tâche 6 : résolveurs de livraison et de ramassage

- [ ] Priorité 100, un de chaque par entrepôt ; acceptent si l'entrepôt existe et a des livreurs ; `resolve` met le jeton dans la file de l'entrepôt ; métriques § 2.3 ; une annulation retire le jeton de la file et de la file du livreur.
- [ ] Tests : `noCourierNoResolve`, `resolveQueuesAtTheWarehouse`, `cancelRemovesFromBothQueues`, `failedDeliveryReassignsTheParent`.
- [ ] Commit : `feat(core): delivery and pickup resolvers queue at the warehouse`.

### Tâche 7 : hutte du livreur, job, choix de la tâche

**Fichiers :** `logistics/courier/` ; type `hycolony:deliveryman`, clé `hut.deliveryman`, niveau max 5, `WorkerModule(DeliverymanJob.TYPE, Agility, Adaptability, 1, …)`.

- [ ] Job : file du livreur et livraisons en cours, persistées ; parallèle = `1 + Adaptabilité / 5` ; limite de ramassage `2^(L−1) + 1` piles si `L < 5` ; inactivité de 36 000 ticks (annulation, puis relance à la reprise) ; retrait du job → ses tâches échouent (§ 2.1).
- [ ] Choix de la tâche : formule § 2.4 exacte, avec vieillissement, regroupement par même cible et ramassage placé en dernier.
- [ ] Tests : `ownQueueFirst`, `picksHighestScoreFifoAndDistance`, `pickupNotYetDueLosesHundred`, `entriesBeforeTheChosenOneAge`, `sameTargetJoinsUpToParallel`, `pickupGoesLast`, `carryLimitPerHutLevel`, `inactivityCancelsTasks`.
- [ ] Commit : `feat(core): courier hut, job and task selection (MC JobDeliveryman)`.

### Tâche 8 : IA du livreur

**Fichiers :** `logistics/courier/` (IA et collaborateurs : préparation, livraison, ramassage, dépôt) ; ports : ajouter `boolean isRainingAt(BlockPos)` à un port existant de requête du monde, et `setMovementSpeed(BodyId, double)` (ou équivalent) à `CitizenBodies`, avec fakes.

- [ ] États et délais § 2.5 ; `prepareDelivery`, `deliver` (échange quand la cible est pleine), `pickup` (1 case par 5 ticks, ce que la hutte ne garde pas), `dump` (rangement de la tâche 4), `finishRequest` ; XP de 0,05 et 1,5 ; pas de travail sous la pluie ; vitesse de base + Agilité × 0,003 ; le livreur n'est pas soumis au dépôt générique.
- [ ] Tests : un test par branche de `decide` et chaque cas d'échec de la § 2.5 (source vidée, cible disparue, cible pleine, inventaire plein), `doesNotWorkInTheRain`, `speedGrowsWithAgility`.
- [ ] Commit(s) : `feat(core): courier AI (MC EntityAIWorkDeliveryman)`.

### Tâche 9 : le constructeur déclenche les ramassages

- [ ] Après un dépôt dans sa hutte : ramassage non forcé de la quantité déposée ; hutte pleine : ramassage forcé (§ 0.1). Sans nouveau fichier dans `construction/builder` (15/15) : passer par `logistics/pickup`.
- [ ] Tests : `builderDumpCreatesAPickup`, `fullHutForcesAPickup`.
- [ ] Commit : `feat(core): builder dumps request a pickup (MC AbstractEntityAIBasic)`.

### Tâche 10 : simulation de bout en bout

- [ ] Sur le modèle de `ConstructionSimulationTest` : colonie avec entrepôt approvisionné, hutte de livreur et constructeur ; un chantier est lancé ; le livreur apporte les blocs ; le chantier se termine ; forcer un ramassage de la hutte du constructeur ramène le surplus à l'entrepôt. Plus : casser l'entrepôt en cours de route n'entraîne ni blocage ni perte (points de relecture 1 à 4).
- [ ] Commit : `test(core): warehouse and courier end-to-end simulation`.

### Tâche 11 : plugin, huttes, météo et vitesse

- [ ] Objets et recettes des deux huttes (décision de recettes ci-dessus), `id-map.json`, `HUT_TYPES`, `styles.json` avec les prefabs validés, et remplacement générateur de coffre → coffre vide dans `HytaleBlueprintSource`, limité à ces huttes ou général si c'est plus sûr : trancher et documenter.
- [ ] `isRainingAt` : `WeatherResource` → environnement du bloc → particule de la météo `Rain*` ou `Snow*` ; recherche § 1. Ne lève jamais d'exception ; renvoie `false` si inconnu.
- [ ] Vitesse du PNJ : vérifier dans vineflower comment changer la vitesse de marche d'un PNJ à l'exécution (contrôleur de mouvement, modificateur de stat). Si c'est impossible : `Deviation from MC:`, méthode sans effet documentée.
- [ ] `docs/research/plugin-b-api.md` mis à jour ; `./gradlew build` vert.
- [ ] Commit(s) : `feat(plugin): warehouse and courier huts, weather and speed`.

### Tâche 12 : plugin, fenêtres, docs

- [ ] Fenêtre d'entrepôt : livreurs rattachés et stock. Hutte du livreur : onglet de travail et liste des tâches. Chaque hutte : boutons ± de priorité de ramassage et « forcer un ramassage », qui appellent des actions du cœur vérifiant `MANAGE_HUTS`. Vues du cœur d'abord (tests), puis la page.
- [ ] Traductions, `.ui` validés, `docs/TESTING.md` (tests 1 à 7 de la spec), spec SP1+2 § 11 (écarts), `docs/BACKLOG.md` (stock minimum, tri, améliorations de stockage).
- [ ] Commits : `feat(core): logistics views and actions`, `feat(plugin): warehouse and courier windows`, `docs: ...`.

### Tâche 13 : relecture finale

- [ ] `hycolony-reviewer` sur toute la branche SP3a, et `mc-fidelity-checker` sur `logistics/` face à la recherche MC. Corriger les vraies erreurs en un seul lot, avec une relecture ciblée des corrections. Feu vert à l'utilisateur.
