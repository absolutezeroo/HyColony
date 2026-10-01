# Plan SP4b : faim, nourriture, salle à manger et bonheur

Spec : `docs/superpowers/specs/2026-10-01-hycolony-sp4b-food-happiness-design.md` (notée « S § x »). Chaque tâche : test qui échoue, code, `./gradlew :core:test` ciblé, formatage fichier par fichier, relecture, commit avec chemins explicites (une autre session travaille dans le même dossier).

## Étape 1 : la faim

1. **Aliments** (S § 2) : `kernel/item/FoodInfo` ; `ItemCatalog.food`, `cooked` ; `FakeCatalog` ; `citizen/food/FoodRules` (+ test).
2. **Saturation** (S § 3) : `CitizenData` (`increaseSaturation`, `decreaseSaturation`, `justAte`, action en attente, marche) ; `citizen/food/FoodHistory` ; `Gameplay.foodModifier` (+ `GameplaySection`) ; `citizen/food/SaturationDecay` (1200 ticks) appelée par `CitizenManager` ; marche dans `tickData` ; `Job.saturationFactor`, `Job.incrementActionsAndDecSaturation`, `Job.decreaseSaturationForContinuousAction` et les points d'appel du constructeur, du fermier, du livreur et de l'artisan.
3. **Soins** (S § 4) : `CitizenBodies.health/maxHealth/heal/recentlyHurt/setStarving` ; `FakeBodies` ; `citizen/food/CitizenHealing` (100 ticks) ; `CitizenManager.onBodyHurt`.
4. **Repas** (S § 5) : `CitizenState.EATING` ; `citizen/food/EatDecision` ; `citizen/food/FoodChoice` ; port `citizen/food/DiningHall` ; `citizen/food/EatAI` ; branchement dans `CitizenAI.decide` ; `BodyAnimation.EAT`, `WorldEffects.eating`.
5. **À la main et huttes** (S § 6) : `citizen/food/HandFeeding` + action `app/action/CitizenFeedingActions` ; `WorkerModule.canEat` et surcharges ; `HutKeep` `keepFood`.
6. **Statut de travail** (S § 8) : `job/JobStatus`, poses (outil, fermier).
7. **Persistance** (S § 13) : schéma 8, fixture 7, migration, sérialiseur.
8. **Plugin** : table `foods` et `cookingBench` de l'id-map ; `HytaleItemCatalog.food/cooked` ; corps (vie, soin, régénération coupée, dégâts *inspect*, ralentissement + effets `HyColony_Speed_085/090/095`, animation `EAT`, particule) ; `CitizenUseSystem` (nourrir) ; `FoodModifier` dans la config ; textes.

## Étape 2 : le bonheur

9. **Modificateurs** (S § 7.1) : `citizen/happiness/HappinessModifier` (statique, temps, expiration), `HappinessFactors`, `CitizenHappiness` (calcul, cache, journée), `HappinessIds`.
10. **Branchements** : `CitizenData.happiness()` ; journée à la tombée de la nuit avec un joueur présent ; `slepttonight` au lit ; `greatfood` ; `damage` ; bonheur global ; plafond des nouveaux citoyens ; persistance (schéma 8, même étape).
11. **Vues** : `app/citizen/HappinessBar` ; `CitizenView` (bonheur, modificateurs, vie) ; `TownHallView` (global, sommes).
12. **Plugin** : barre et onglet Bonheur du citoyen (`Citizen.ui`, textures MC ×4), page Citoyens de l'hôtel de ville ; textes.
13. **API** : `CitizenWellbeing` `@Experimental`, `apiDump`, HyLens.

## Étape 3 : la salle à manger

14. **Hutte** (S § 10) : `crafting/restaurant/DiningHallHut` (type, modules), `MenuModule`, `FuelListModule`, `SeatModule`, `CustomerModule` ; enregistrement des feux de camp et sièges posés à la main dans l'emprise ; `ItemCatalog.isSeat/isFuel/isCookingStation`.
    - `RestaurantMenuModule` (MC du même nom) : menu ≤ 5 × niveau, `EDIBLE` seulement, requêtes toutes les 500 ticks (`StackRequest` du plat, puis de son cru quand la première n'a pas dépassé `IN_PROGRESS`, annulées à `delta ≤ 0`), garde `taillePile × niveau` du plat et du cru, sauvé.
    - `DiningRoomModule` implémente le port `DiningHall` : places enregistrées (comme les lits), `nextSeat` (3 tirages), clients (MC `storeCustomer`, y compris le premier remplissage par la salle la plus proche de chaque hutte de travail), `hasWaiter`, `keepsFood = false` (`EatingRule`), sauvé.
    - `FuelListModule` (MC `ItemListModule` `FUEL_LIST`) : liste sauvée, défaut de l'id-map ; garde 64 × niveau, jamais emporté par le livreur, au plus 64 sur le serveur.
    - `CookingStationsModule` (MC `FurnaceUserModule`) : feux de camp enregistrés, retirés paresseusement.
15. **Serveur** (S § 11) : port `CookingStations` ; `crafting/furnace/FurnaceUserAI` ; `crafting/restaurant/CookJob`, `CookAI` (service) ; chemin salle à manger de `EatAI`.
    - Machine (MC `AbstractEntityAIUsesFurnace`, sur `WorkerMachine`) : `IDLE → START_WORKING` (5), `START_WORKING` (60), `FILL_UP` (5), `RETRIEVING_END_PRODUCT` (5), `RETRIEVING_USED_FUEL` (5), accélération (événement, 20), `GATHERING_REQUIRED_MATERIALS`, `COOK_SERVE_FOOD_TO_CITIZEN` / `_TO_PLAYER` (30), vidage après chaque action.
    - `servesFood() = true` ; `EatAI` le fait déjà renoncer à sa propre salle.
16. **Places** : ports `BodySeats` (fait aux étapes 1-2).
17. **Plugin** : objet et bloc `HyColony_Hut_Cook`, styles, id-map, `HytaleCookingStations`, sièges, onglets Menu et Combustible ; textes ; enregistrement à la pose par un joueur (`PlaceBlockEvent`) d'un feu de camp ou d'un siège dans l'emprise.
18. **Avertissements de résidence** `warning.3` à `.5` sur les menus réels (S § 15).

## Fin

19. `docs/TESTING.md`, `docs/BACKLOG.md`, S § 18, `./gradlew build`, relecture finale (`hycolony-reviewer`, `mc-fidelity-checker`, `ui-lang-checker`).
