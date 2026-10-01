# SP4b : la salle à manger (restaurant) et le serveur (cook) de MineColonies

Recherche du 2026-10-01, pour concevoir faim, nourriture et bonheur des citoyens.

**Sources.** `MC/` = `sources/minecolonies/src/main/java/com/minecolonies/` (copie locale de `version/main`, voir CLAUDE.md § 6) ; `MCR/` = `sources/minecolonies/src/main/resources/` ; `MCD/` = `sources/minecolonies/src/datagen/generated/minecolonies/` ; `ST/` = `sources/structurize/src/main/java/com/ldtteam/structurize/`. Les textes anglais viennent de `MCR/assets/minecolonies/lang/manual_en_us.json`, le seul fichier de langue anglais des sources (il n'y a pas de `en_us.json`).

Déjà lus par l'appelant et seulement cités ici : `EntityAIEatTask`, `FoodUtils`, `CitizenFoodHandler`, `CitizenHappinessHandler`, `EntityAIWorkCook`, `BuildingCook`, `RestaurantMenuModule`.

## Réponse courte

1. **Modules de la salle à manger** (`MC/apiimp/initializer/ModBuildingsInitializer.java:143-153`) : `COOK_WORK`, `FURNACE`, `ITEMLIST_FUEL`, `RESTAURANT_MENU`, `STATS_MODULE`. **Pas de `MIN_STOCK`, pas de module de réglages, pas de module d'artisanat.** `COOK_WORK` = `NoPrivateCrafterWorkerModule(cook, Adaptability, Knowledge, canWorkDuringRain = true, 1 serveur à tous les niveaux)`. `RESTAURANT_MENU` = `RestaurantMenuModule(canCook = true, expectedStock = niveau du bâtiment)`. `ITEMLIST_FUEL` a pour défaut charbon et charbon de bois. Niveau max 5.
2. **`FOOD_EXCLUSION_LIST = "food"`** ne sert plus qu'à nommer l'icône de l'onglet menu (`textures/gui/modules/food.png`). Il n'y a plus de liste d'exclusion : le menu (≤ 5 × niveau plats) l'a remplacée. `IBuilding.canEat` vaut `true` par défaut, et certaines huttes le surchargent pour protéger leurs matières (blé, graines, remèdes, ingrédients de la recette en cours…).
3. **`AbstractEntityAIUsesFurnace`** n'a que 4 états propres : `START_WORKING` (décision), `FILL_UP_FURNACES`, `RETRIEVING_END_PRODUCT_FROM_FURNACE` et `RETRIEVING_USED_FUEL_FROM_FURNACE`, plus un événement `AI_BLOCKING` qui accélère les fours chaque seconde. Les états `START_USING_FURNACE` et `ADD_FUEL_TO_FURNACE` n'existent pas dans ce code. Les fours sont enregistrés quand le constructeur pose un bloc `FurnaceBlock` du plan (`registerBlockPosition`). Sans four, le serveur reste en `START_WORKING` sans rien faire.
4. **Le serveur ne fabrique aucun plat.** Il ne fait que cuire au four les aliments crus dont le produit cuit est au menu, et il sert. Les plats `IMinecoloniesFoodItem` sont faits par le **Chef** (`BuildingKitchen`, « Chef's Kitchen ») : modules d'artisanat et de fonte filtrés par les tags `cook_*`, et 15 recettes `crafterrecipes/chef` au niveau 4. Le Boulanger fait les pains et gâteaux.
5. **`Food` (requestable) est du code mort dans `version/main`.** Le seul `new Food(...)` est dans `EntityAIWorkCook.getSmeltAbleClass`, que personne n'appelle. Le menu demande des `MinimumStack` par plat.
6. **S'asseoir** : `SittingEntity.sitDown(pos, mob, durée)` crée une entité invisible que le citoyen chevauche, pour au plus `durée` ticks (repas : `TICKS_SECOND * 60` = 1 200 ticks). Les places sont des **positions étiquetées du plan** (`sit`, `sit_in`, `sit_out`). Les places `sit_out` sont ignorées sous la pluie.
7. **Deux interactions du serveur ne s'affichent jamais dans MC (déduit du source, [in-game])** : `POOR_RESTAURANT_INTERACTION` et `POOR_MENU_INTERACTION` n'ont aucun validateur enregistré, et `triggerInteraction` les refuse. De même, le validateur « pas de four » est réécrit pour le seul boulanger, donc un serveur sans four ne se plaint pas.

## 1. Bâtiment, modules et fenêtre

### 1.1 Entrée du bâtiment

`ModBuildingsInitializer.java:143-153` (salle à manger, id `cook`, bloc `blockHutCook`, vue `BuildingCook.View`) :

| Module | Producteur (`MC/core/colony/buildings/modules/BuildingModules.java`) | Paramètres |
|---|---|---|
| `COOK_WORK` (clé `"cook_craft"`) | l. 447-449 | `new NoPrivateCrafterWorkerModule(ModJobs.cook, Skill.Adaptability, Skill.Knowledge, true, b -> 1)`, vue `WorkerBuildingModuleView` |
| `FURNACE` (`"furnace"`) | l. 51 | `FurnaceUserModule::new`, **aucune vue** (pas d'onglet) |
| `ITEMLIST_FUEL` (`"itemlist_fuel"`) | l. 73-77 | `new ItemListModule(FUEL_LIST, coal, charcoal)`. Vue `ItemListModuleView(FUEL_LIST, REQUESTS_TYPE_BURNABLE, inverted=false, toutes les matières combustibles du CompatibilityManager)` |
| `RESTAURANT_MENU` (`"restaurant_menu"`) | l. 85-86 | `new RestaurantMenuModule(true, ICommonBuilding::getBuildingLevel)`, vue `RestaurantMenuModuleView` |
| `STATS_MODULE` | l. 62-64 | `BuildingStatisticsModule` |

- La signature du constructeur est `(JobEntry, primary, secondary, canWorkingDuringRain, Function<IBuilding,Integer> sizeLimit)` (`WorkerBuildingModule.java:67-79`). Le serveur **travaille sous la pluie** et il y a **un seul serveur**, quel que soit le niveau.
- `NoPrivateCrafterWorkerModule` (`NoPrivateCrafterWorkerModule.java:19-35`) ajoute seulement un `BuildingRequestResolver` sur la hutte. Il résout les requêtes avec l'inventaire de la hutte elle-même, sauf celles marquées `canBeResolvedByBuilding() == false` (`MC/core/colony/requestsystem/resolvers/BuildingRequestResolver.java:72`). Les requêtes du menu sont justement marquées ainsi (`RestaurantMenuModule.java:161, 169`).
- Les ingrédients du four n'ont **pas de module de réglages** (`SETTINGS_CRAFTER_RECIPE` est absent) ni de `MIN_STOCK`.
- `BuildingCook` : `MAX_BUILDING_LEVEL = 5` (l. 55), schéma `"cook"` (l. 45, 133-136), `keepFood() = false` (l. 68-72).
- `NETHERMINER_MENU` réutilise la même classe : `new RestaurantMenuModule(false, b -> 1)` (`BuildingModules.java:88-89`). C'est la raison d'être de `canCook` : le mineur du Nether n'a pas de four, donc on ne demande pas l'aliment cru.

### 1.2 Fenêtre et onglets

- `AbstractBuildingView.getWindow` (`MC/core/colony/buildings/views/AbstractBuildingView.java:391-398`) : un bâtiment qui a une `WorkerBuildingModuleView` ouvre `WindowHutWorkerModulePlaceholder`. `BuildingCook.View` ne la surcharge pas. Les onglets sont les vues de modules dans l'ordre de l'entrée : travailleur (`COOK_WORK`), combustible (`ITEMLIST_FUEL`), menu (`RESTAURANT_MENU`), statistiques. `FURNACE` n'a pas de vue.
- Le wiki (`https://minecolonies.com/wiki/buildings/cook/`) montre les mêmes 4 pages : principale, Fuel, Food, Statistics. Il décrit cependant un onglet Food à bascules on/off, qui correspond à l'**ancienne** liste d'exclusion. Le code actuel a un menu à deux colonnes (ci-dessous) ; c'est le code qui fait foi. Le wiki dit aussi que le combustible est « off » par défaut, alors que le code active charbon et charbon de bois (`BuildingModules.java:74`, `ItemListModule.java:59-64`).
- `BuildingCook.View` (l. 259-295) ajoute seulement la liste des clients (`customers`), qui sert au calcul de consommation du menu. Remarque : `View.deserialize` ne vide pas `customerList` avant de la relire (l. 278-286), ce qui ressemble à un bug sans effet visible (c'est un ensemble d'entiers).

### 1.3 Onglet menu (`RestaurantMenuModuleWindow`)

Mise en page : `MCR/assets/minecolonies/gui/layouthuts/layoutfoodstock.xml` (380 × 255, deux pages `builder_paper.png` de 190 × 244).

- **Page gauche, « Menu »** (`com.minecolonies.core.menu.title`) :
  - liste `resourcesstock` (164 × 90 en 13,45), avec par ligne un dégradé de tier, une icône, un nom et un bouton `X` rouge (`removeStock`) ;
  - texte `warning` (« Select the Food… ») quand le menu est vide, et `poorwarning` en rouge (« A menu lacking quality food… ») quand le menu n'a **aucun** `IMinecoloniesFoodItem` de tier ≥ 2 (`RestaurantMenuModuleWindow.java:207-233`) ;
  - titre « Ingredients » et liste `ingredientslist` (164 × 85 en 13,145).
- **Page droite, « Food Options »** (`com.minecolonies.core.food_list.title`) : champ de filtre `input` (maxlength 25) et liste `resources` (164 × 180), avec par ligne un bouton `<<` (`switch`), une icône et un nom en blanc sur le dégradé de tier.
- **Contenu de la liste de droite** : `CompatibilityManager.getEdibles(niveau - 1)` (l. 116), c'est-à-dire tous les objets `EDIBLE` dont la nutrition est ≥ niveau − 1 (`MC/api/compatibility/CompatibilityManager.java:425-440, 792-802`). Le filtre porte sur l'id de description ou le nom affiché, en minuscules. Il est appliqué 10 ticks après la dernière frappe (l. 118-125, 169-176, 441-458).
- **Tri** : score = `tier × −100 − nutrition`, puis nom (l. 466-477).
- **Couleur de tier** : tier 3 or (255,215,0), tier 2 argent (211,211,211), tier 1 bronze (205,127,50), sinon transparent (l. 264-285, 519-538). Infobulle `FOOD_QUALITY_TOOLTIP(getBuildingLevelForFood)`, et en plus `VANILLA_FOOD_QUALITY_TOOLTIP` pour un aliment qui n'est pas de MC et de tier 0 (l. 287-301).
- **Ajout** (`switchClicked`, l. 183-196) : refusé si `hasReachedLimit()` (taille du menu ≥ niveau × `STOCK_PER_LEVEL`, soit **5 par niveau** : `RestaurantMenuModuleView.java:71-74`, `RestaurantMenuModule.java:45`). Le bouton est désactivé quand l'objet est déjà au menu. À la limite, il reçoit l'infobulle « Limit Reached » (`com.minecolonies.coremod.gui.warehouse.limitreached`), mais l'`else enable()` qui suit le réactive (l. 555-571) : seul le contrôle de `switchClicked`, et celui du serveur (`RestaurantMenuModule.java:98`), bloquent l'ajout.
- **Retrait** : bouton `X`, message `removeMenuItem`, qui annule aussi la requête ouverte de ce plat (l. 151-158 ; `RestaurantMenuModule.java:111-122`).
- **Réseau** : `AlterRestaurantMenuItemMessage` (`MC/core/network/messages/server/colony/building/AlterRestaurantMenuItemMessage.java:96-110`). La permission est celle par défaut, `MANAGE_HUTS` (`AbstractColonyServerMessage.java:63`). Le serveur refuse un objet non `EDIBLE` (`RestaurantMenuModule.java:92-96`).
- **Ingrédients et consommation** (l. 128-142, 305-356) :
  - `avgCustomerConsumption` = moyenne, sur les clients, de `computeSaturationConsumptionFactor(niveau de leur maison)` ;
  - pour chaque plat, les ingrédients viennent de `getRecipeFromStack(plat, cache, profondeur 5)`, qui suit les `CustomRecipe` puis les recettes vanilla, en ignorant bols et bouteilles (l. 359-436) ;
  - `consumption = avg × 10 × nbClients / Σ getFoodValue(plats)` ;
  - l'infobulle de chaque ingrédient affiche `FOOD_CONSUMPTION_TOOLTIP(consumption × qté, consumption × qté × 1,5)`. Attention : le code écrit `(int) consumption*resource.getValue()`, donc le cast porte sur `consumption` seul (l. 353).
- Icône de l'onglet `textures/gui/modules/food.png` (via `FOOD_EXCLUSION_LIST = "food"`), libellé `REQUESTS_TYPE_FOOD` = « Menu » (`RestaurantMenuModuleView.java:50-60`).

### 1.4 `canEat` et règle de stock de nourriture (`keepFood`)

- `IBuilding.canEat(stack)` vaut `true` par défaut (`MC/api/colony/buildings/IBuilding.java:505-508`). `FoodUtils.canEat(stack, home, work)` = `canEatLevel(stack, niveau de la maison)` **et** `work == null || work.canEat(stack)` (`MC/api/util/FoodUtils.java:43-51`).
- Surcharges (toutes dans `MC/core/colony/buildings/workerbuildings/`) :
  - `BuildingBaker:91-125` : refuse le blé, l'entrée nettoyée et la sortie de la recette en cours ;
  - `BuildingKitchen:77-105` : refuse l'entrée et la sortie de la recette en cours ;
  - `BuildingFarmer:144-161` : refuse les graines des champs possédés et le blé ;
  - `BuildingDeliveryman:52-69` : refuse l'objet de la livraison en cours ;
  - `BuildingBuilder:201-207` : refuse ce que le chantier exige ;
  - `BuildingHospital:320-327` : refuse les remèdes ;
  - `BuildingPlantation:175-195` : refuse l'objet de chaque champ de plantation possédé ;
  - `BuildingBeekeeper:134-140` : refuse la bouteille de miel ;
  - nourriture des animaux : `BuildingCowboy:107-113` et `BuildingShepherd:82-88` refusent le blé, `BuildingSwineHerder:55-61` et `BuildingRabbitHutch:48-54` la carotte.
  - `BuildingCook` n'a **pas** de surcharge.
- `AbstractBuilding.getRequiredItemsAndAmount` (`MC/core/colony/buildings/AbstractBuilding.java:1102-1145`) : si `keepFood()` (vrai par défaut, l. 1219-1222), la hutte garde `stack -> FoodUtils.canEat(stack, null, this)` en quantité `niveau × 2`, avec `inventory = true` (l. 1134-1137). Avec `home = null`, `canEatLevel` reçoit le niveau 0 (voir `FoodUtils`).
  - `keepFood() = false` pour la salle à manger (`BuildingCook:68-72`), la cuisine (`BuildingKitchen:71`) et la boulangerie (`BuildingBaker:85`).
- `buildingRequiresCertainAmountOfItem` (`AbstractBuilding.java:1050-1092`) lit cette table. Avec `inventory = true`, les entrées dont `B == false` sont ignorées. Deux appelants :
  - le vidage d'inventaire du travailleur, avec `inventory = true` : il garde jusqu'à 2 × niveau de nourriture sur lui (`MC/core/entity/ai/workers/AbstractEntityAIBasic.java:1271`) ;
  - le ramassage du coursier, avec `inventory = false` : il laisse ces 2 × niveau dans la hutte (`EntityAIWorkDeliveryman.java:286-289`).
- Combustible : `FurnaceUserModule.alterItemsToBeKept` garde `64 × niveau` de combustible autorisé, avec `inventory = false`, donc dans la hutte seulement (`FurnaceUserModule.java:92-96`). `BuildingCook.buildingRequiresCertainAmountOfItem` (l. 145-166) fait plus :
  - le serveur garde au plus 64 combustibles sur lui ;
  - le coursier ne prend **jamais** de combustible dans la salle à manger (il renvoie 0 quand `!inventory`).
- Personne ne livre de nourriture aux huttes de travail de façon systématique. La nourriture arrive par les requêtes de `EntityAIEatTask` et du menu. La règle `keepFood` empêche seulement de la vider.

## 2. `AbstractEntityAIUsesFurnace` (`MC/core/entity/ai/workers/AbstractEntityAIUsesFurnace.java`)

### 2.1 Cibles et constantes

| Cible (l. 76-82) | Action | Délai |
|---|---|---|
| `IDLE → START_WORKING` | | `STANDARD_DELAY` = 5 ticks (`AbstractEntityAIBasic.java:100`) |
| `START_WORKING` | `startWorking` | **60** ticks |
| `FILL_UP_FURNACES` | `fillUpFurnace` | 5 |
| `AIEventTarget(AI_BLOCKING)` | `accelerateFurnaces` | `TICKS_SECOND` = 20 |
| `RETRIEVING_END_PRODUCT_FROM_FURNACE` | `retrieveSmeltableFromFurnace` | 5 |
| `RETRIEVING_USED_FUEL_FROM_FURNACE` | `retrieveUsedFuel` | 5 |

- `AI_BLOCKING` est évalué quel que soit l'état (`AIBlockingEventType.java:9-12`). `accelerateFurnaces` renvoie `null`, ce qui ne change pas l'état et ne bloque rien (`MC/api/entity/ai/statemachine/basestatemachine/BasicStateMachine.java:199-213`).
- Le serveur y ajoute `COOK_SERVE_FOOD_TO_CITIZEN` et `COOK_SERVE_FOOD_TO_PLAYER` (`EntityAIWorkCook.java:96-97`).
- Constantes : `BASE_XP_GAIN = 2` (l. 55), `RETRIEVE_SMELTABLE_IF_MORE_THAN = 10` (l. 60), `STORAGE_BUFFER = 3` (l. 66). Emplacements du four : `SMELTABLE_SLOT = 0`, `FUEL_SLOT = 1`, `RESULT_SLOT = 2` (`MC/api/util/constant/Constants.java:118-128`). `STACKSIZE = 64` (l. 23).
- `getActionsDoneUntilDumping() = 1` : vidage après chaque action (l. 376-380, et aussi `EntityAIWorkCook.java:428-431`).

### 2.2 `startWorking`, dans l'ordre (l. 182-261)

1. Marche vers la hutte (`walkToBuilding`), sinon reste dans l'état.
2. Statut visible `WORKING`.
3. Liste de combustible vide → interaction `FURNACE_USER_NO_FUEL` (`BLOCKING`). Le code **continue**.
4. Aucun four enregistré → interaction `BAKER_HAS_NO_FURNACES_MESSAGE` (`BLOCKING`), et **retour à `START_WORKING`** : on attend sans fin, sans autre délai que 60 ticks.
5. `checkForImportantJobs()`. Le serveur le surcharge (service des clients, `EntityAIWorkCook.java:339`). Si le résultat n'est pas `START_WORKING`, on part dans cet état.
6. Un four contient un combustible **qui n'est plus dans la liste autorisée** → `walkTo` = ce four, puis `RETRIEVING_USED_FUEL_FROM_FURNACE` (l. 156-173).
7. Un four est à vider (l. 129-149) → `RETRIEVING_END_PRODUCT_FROM_FURNACE`. Un four est à vider si :
   - il est éteint avec un résultat > 0 ;
   - ou son résultat est > 10 ;
   - ou son résultat est > 0 et son entrée vide.
8. Comptages : cuisinables dans la hutte et sur le travailleur (`isSmeltable`), combustibles dans la hutte et sur le travailleur.
9. Aucun cuisinable et `!reachedMaxToKeep()` → `requestSmeltable()`.
10. Aucun combustible et aucune requête ouverte « Fuel » du travailleur → `createRequestAsync(new StackList(combustibles autorisés, chacun en pile pleine, "Fuel", 64 × nbFours, min 1))` (l. 242-247, 268-278).
11. Cuisinables dans la hutte mais pas sur lui → `needsCurrently = (isSmeltable, 64)`, puis `GATHERING_REQUIRED_MATERIALS`. Sinon, même chose pour le combustible.
12. `checkIfAbleToSmelt(totalCombustible, totalCuisinable)` (l. 320-348). On choisit le premier four qui a :
    - une entrée sans combustible, si on a du combustible ;
    - ou un combustible sans entrée, si on a des cuisinables ;
    - ou ni l'un ni l'autre, si on a les deux.
    Ce four devient `walkTo`, puis `FILL_UP_FURNACES`. Une position enregistrée dont le bloc n'est plus un `FurnaceBlock` est **retirée** du module. Sinon, `checkForAdditionalJobs()` (= `START_WORKING` par défaut).

### 2.3 Autres actions

- `fillUpFurnace` (l. 452-499) :
  - liste de fours vide → interaction « pas de four », puis `START_WORKING` ;
  - `walkTo` nul, ou bloc qui n'est **pas exactement `Blocks.FURNACE`** → `START_WORKING` (un fumoir ou un haut fourneau seraient enregistrés, mais jamais remplis) ;
  - une fois arrivé : 64 cuisinables au plus dans l'entrée si le four n'a pas d'entrée, puis 64 combustibles au plus dans l'emplacement de combustible s'il n'en a pas.
- `retrieveSmeltableFromFurnace` (l. 388-413) : marche, puis `extractFromFurnace(four)` et `incrementActionsDoneAndDecSaturation()`. Le serveur prend tout le résultat dans son inventaire et gagne 2 points d'expérience. Il ré-incrémente aussi les actions, ce qui fait **deux** incréments par extraction (`EntityAIWorkCook.java:114-121`).
- `retrieveUsedFuel` (l. 421-445) : marche, puis prend l'emplacement de combustible dans son inventaire.
- `accelerateFurnaces` (l. 283-306) : `ticksEnPlus = (niveau de compétence primaire / 10) × 2`, en division entière. La compétence primaire du serveur est l'Adaptabilité. Chaque four chargé **et allumé** reçoit ce nombre d'appels supplémentaires à `serverTick`, à chaque seconde.
- `reachedMaxToKeep` de base (l. 117-121) : vrai si la hutte a ≤ 3 cases vides. Le serveur le surcharge (`EntityAIWorkCook.java:137-147`) : vrai aussi si la hutte contient plus de `max(1, niveau²) × 9` aliments `EDIBLE` et `canEatLevel(niveau − 1)`, chacun compté au plus à `maxStack × 6`.

### 2.4 Fumable, requêtes et `Food` chez le serveur

- `isSmeltable` du serveur (`EntityAIWorkCook.java:130-134`) : `ISCOOKABLE` (le résultat au four est un `ISFOOD` : `MC/api/util/ItemStackUtils.java:162`) **et** ce résultat cuit est au menu.
- `requestSmeltable` du serveur (l. 150-157) **ne crée aucune requête**. Il déclenche `FURNACE_USER_NO_FOOD` si le menu est vide. Les requêtes viennent de `RestaurantMenuModule.onColonyTick` : un `MinimumStack` par plat, et aussi un pour l'aliment cru si `canCook` et si le plat a une recette de four.
- `getSmeltAbleClass()` (l. 434-437) renvoie `new Food(64, niveau)`, mais **aucun appelant** dans `AbstractEntityAIUsesFurnace` ni chez le serveur. Seul le fondeur appelle sa propre version (`EntityAIWorkSmelter.java:149, 203, 210`).

### 2.5 Enregistrement des fours (`FurnaceUserModule`)

- Le module garde une liste `furnaces`. NBT : `furnaces[] { pos }`, avec lecture de l'ancien `furnacePos` du boulanger (`FurnaceUserModule.java:37-90`).
- `onBlockPlacedInBuilding` ajoute la position si le bloc est un `FurnaceBlock` (l. 133-140). On y arrive par `AbstractBuilding.registerBlockPosition` (`AbstractBuilding.java:1415-1418`), qui a trois appelants :
  - le constructeur, quand il pose un bloc (`MC/core/entity/ai/workers/util/BuildingStructureHandler.java:199`) ;
  - le collage créatif (`MC/api/util/CreativeBuildingStructureHandler.java:127`) ;
  - le marteau d'assistant (`MC/core/items/ItemAssistantHammer.java:336`).
- Un four posé à la main par le joueur n'est **pas** enregistré par ce chemin (déduit du source : aucun autre appelant).
- Le retrait se fait paresseusement dans `checkIfAbleToSmelt`. HyColony a déjà un équivalent pour les lits : `RegisteredBlocks`, `BuildingEventsModule`, `BedModule` (`core/src/main/java/dev/hycolony/core/building/`).

## 3. JobCook, Chef et Kitchen

### 3.1 `JobCook` (`MC/core/colony/jobs/JobCook.java:12-46`)

- Il ne surcharge que `getModel()` (`ModModelTypes.COOK_ID`) et `generateAI()`.
- Tout le reste vient d'`AbstractJob` : `canAIBeInterrupted()` délègue à l'IA (`AbstractJob.java:353-361`), `allowsAvoidance() = true`, `getDiseaseModifier() = 1` (l. 414-423).
- Il n'y a pas de facteur de saturation propre au métier.
- Enregistrement : `ModJobs.cook`, id `minecolonies:cook`, vue `DefaultJobView` (`ModJobsInitializer.java:82-86` ; `ModJobs.java:22`). Le nom affiché est **« Waiter »**.

### 3.2 Qui fait les plats ?

- **Le serveur ne fait pas d'artisanat.** Il n'a ni module d'artisanat ni `CRAFT_TASK_VIEW`. Il cuit au four seulement les aliments crus dont le produit cuit est au menu. Le texte d'aide le confirme (« will cook meat and potatoes, as well as other cooked food that only needs a furnace », `info.cook.0`).
- **Le Chef** (`JobChef extends AbstractJobCrafter`, modèle `COOK_ID`, sons feu et cuivre : `MC/core/colony/jobs/JobChef.java:15-54`) travaille dans la **Kitchen** (« Chef's Kitchen », `ModBuildingsInitializer.java:661-674`). Modules de la cuisine :
  - `MIN_STOCK`, `CRAFT_TASK_VIEW` ;
  - `CHEF_WORK` : `CraftingWorkerBuildingModule(chef, Creativity, Knowledge, pluie = false, 1, vitesse = Knowledge, amélioration = Creativity)` (`BuildingModules.java:450-457`) ;
  - `CHEF_CRAFT`, `CHEF_SMELT`, `FURNACE`, `ITEMLIST_FUEL`, `STATS_MODULE`.
- IA : `EntityAIWorkChef extends AbstractEntityAIRequestSmelter`. Elle compte ses fontes dans `ITEMS_BAKED_DETAIL` et ses fabrications dans `FOOD_COOKED_DETAIL` (`EntityAIWorkChef.java:14-49`).
- Recettes acceptées (`BuildingKitchen.java:108-168`) :
  - `CraftingModule` : tags `cook_ingredient` / `cook_product` (et leurs `_excluded`), sinon une sortie `EDIBLE` ou dont la fonte est `EDIBLE` ;
  - `SmeltingModule` : tags, sinon une sortie `EDIBLE`.
  - Les tags sont dans `MCD/data/minecolonies/tags/items/cook_*.json`. `cook_product` liste 43 plats MC (pottage, cheddar, pasta, soupes, raviolis…). `cook_product_excluded` liste pain, gâteau, cookie, tarte à la citrouille, pizza, cheesecake, apple pie et cornmeal.
- Recettes fixes `MCD/data/minecolonies/crafterrecipes/chef/*.json` : 15 plats (borscht, eggplant_dolma, fish_dinner, hand_pie, lamb_stew, pita_hummus, ramen, schnitzel, spicy_eggplant, steak_dinner, stew_trencher, stuffed_pepper, stuffed_pita, sushi_roll, tacos), tous avec `"crafter": "chef_crafting"` et `"min-building-level": 4`.
- `crafterrecipes/baker/` : 25 recettes de boulangerie (pains, pâtes, gâteaux, muffins…).
- `ModJobs.cookassistant` (`minecolonies:cookassistant`) produit aussi un `JobChef` : c'est un id hérité (`ModJobsInitializer.java:298-302`).
- **Kitchen et Dining Hall** : la cuisine **produit** (artisanat public, résout des requêtes). La salle à manger **stocke, cuit le cru au four et sert**. L'infobulle des plats MC le dit : « Ask a Chef in a Chef's Kitchen to cook this for you » (`com.minecolonies.core.item.food.tooltip.chef`).

## 4. Le requestable `Food` (`MC/api/colony/requestsystem/requestable/Food.java`)

- Champs : `count`, `minNutrition`, `exclusionList` (`List<ItemStorage>`), `result` (l. 39-45). NBT : `Count`, `Result`, `Exclusion`, `MinNutrition` (l. 33-36).
- `matches(stack)` (l. 181-187) : il faut `ISFOOD`, que l'objet ne soit pas exclu, que son produit cuit ne soit pas exclu s'il est cuisinable, et **soit** qu'il soit cuisinable, **soit** que sa nutrition soit ≥ `minNutrition`. Il n'y a pas de test `canEatLevel`.
- `getMinimumCount() = 1`, `canBeResolvedByBuilding() = false` (l. 207-264). `equals` compare le nombre et le résultat (l. 225-244).
- Type de requête : `StandardRequests.FoodRequest`, libellé « Menu » (`REQUESTS_TYPE_FOOD`), exemples = tous les objets comestibles (`StandardRequests.java:622-673`). Il est enregistré dans `RequestSystemInitializer.java:23`, et sa fabrique est dans `StandardRequestFactories.java:780-813`.
- Résolution : c'est un `IDeliverable`, donc les résolveurs génériques d'objets (entrepôt, joueur) le prennent. `AbstractCraftingRequestResolver.canResolveForBuilding` traite à part une requête `Food` : un artisan ne la résout qu'avec une recette **non fondue** (`recipe.getIntermediate() != Blocks.FURNACE`) (`MC/core/colony/requestsystem/resolvers/core/AbstractCraftingRequestResolver.java:146-153`).
- **Créateurs** : aucun appelant effectif dans `version/main`. Le seul `new Food(` hors de la classe est `EntityAIWorkCook.java:436`, dans une méthode jamais appelée (grep de `getSmeltAbleClass` et de `new Food(` sur `MC/`). Les autres usages de `Food` (`FoodRequest`, fabrique, résolveur d'artisanat) ne servent qu'à manipuler des requêtes `Food` existantes.

## 5. S'asseoir

- `SittingEntity` (`MC/core/entity/other/SittingEntity.java`) est une entité invisible, sans physique ni gravité, invulnérable et non sélectionnable (l. 44-79). Elle n'est pas sauvegardée (l. 81-91).
  - `tick` (l. 112-131) : si elle n'a plus de passager, ou si `maxLifeTime-- < 0` (défaut 100), elle éjecte et disparaît.
  - Le passager voit sa hauteur divisée par deux pendant qu'il est assis (l. 133-158).
  - Au lever, il descend sur `EntityUtils.getSpawnPoint(sittingpos)` (l. 160-171).
- `isSittingPosOccupied(pos, world)` (l. 199-202) consulte un ensemble **statique** de positions par dimension. `remove` en retire la position (l. 105-110).
- `sitDown(pos, mob, maxLifeTime)` (l. 211-261) :
  - déjà monté → renvoie `true` sans rien faire ;
  - position occupée → `false` ;
  - sinon : réserve la place, crée l'entité au centre du bloc à `y + minY(boîtes de collision) − hauteur/2 − 0,1` (`minY` = 0 si le bloc n'a pas de collision), stoppe la navigation et monte. Sur un escalier, le citoyen regarde vers l'avant pendant 40 ticks.
- Durées des appelants :
  - repas : `EntityAIEatTask.java:308`, 60 s = 1 200 ticks ;
  - errance : `EntityAICitizenWander.java:247`, 30 s ;
  - repos d'un artisan : `AbstractEntityAICrafting.java:201`, 20 s ;
  - visiteur : `EntityAIVisitor.java:214`, `actionTimeoutCounter` ;
  - élève : `EntityAIWorkPupil.java:150` ; enseignant : `EntityAIWorkTeacher.java:136` ; garde qui dort : `AbstractEntityAIGuard.java:284`.
- **Étiquettes** (`MC/api/util/constant/SchematicTagConstants.java:11-14`) : `TAG_SITTING = "sit"`, `TAG_SIT_IN = "sit_in"`, `TAG_SIT_OUT = "sit_out"`, ainsi que `TAG_WORK = "work"`, `TAG_STAND_IN`/`TAG_STAND_OUT`.
  - `sit_in` et `sit_out` sont proposées pour toutes les huttes, `sit` seulement pour l'école (`MC/core/MineColonies.java:136-152`). Les vieux plans de restaurant portent quand même `sit`, d'où le test des trois.
  - Ce sont des positions de blocs étiquetées dans le plan, rangées dans l'entité de bloc de la hutte (`blueprintDataProvider`, `ST/blockentities/interfaces/IBlueprintDataProviderBE.java:26, 258`). Elles sont converties en positions du monde et mises en cache (`MC/api/tileentities/AbstractTileEntityColonyBuilding.java:217-240`), puis lues par `getLocationsFromTag` (`MC/core/colony/buildings/AbstractBuildingContainer.java:196-203`).
- `BuildingCook.getNextSittingPosition` (l. 84-129) : tirage aléatoire uniforme sur `sit + sit_in + (sit_out sauf s'il pleut)`, en **3 essais** au plus, et la place doit être libre ; sinon `null`. Un plan sans aucune étiquette journalise une erreur et renvoie `null`.
- HyColony ne gère pas encore les étiquettes de plan : aucune occurrence de `positionedTags` / `blueprintDataProvider` dans `core/` ni `plugin/`. `structurize-placeholders.md` mentionne `blueprintDataProvider` à propos des blocs de substitution.

## 6. Autres chemins de l'alimentation

### 6.1 `ItemStackUtils.consumeFood(food, citizen, playerInv)` (`MC/api/util/ItemStackUtils.java:931-977`)

1. `increaseSaturation(FoodUtils.getFoodValue(food, citizen))`.
2. `food.finishUsingItem(level, citizen)` : comportement vanilla (consomme 1, applique les effets de l'aliment, son de manger). L'objet rendu est forcé à une bouteille vide pour la bouteille de miel et à un bol pour `ItemBowlFood`.
3. Si l'objet rendu n'est pas vide et diffère de l'aliment : il va dans l'inventaire du citoyen. Si cet inventaire est plein, ou si `playerInv` refuse l'ajout, l'objet tombe au sol. Remarque : quand `playerInv != null`, on essaie d'abord `inventory.add`, et l'objet va **en plus** chez le citoyen si cela réussit, ce qui ressemble à un bug.
4. `IMinecoloniesFoodItem` de tier ≥ 3 → modificateur `HADGREATFOOD` (`"greatfood"`) : `ExpirationBasedHappinessModifier(2.0, StaticHappinessSupplier(2.0), 5 jours)`.
5. Déclenche l'avancement `CITIZEN_EAT_FOOD` ; `markDirty(60)`.

Appelants de `consumeFood` :

| Appelant | Contexte |
|---|---|
| `EntityAIEatTask.java:224` | repas normal (`playerInv = null`) |
| `EntityAIWorkNether.java:826-838` | le mineur du Nether mange en expédition, parmi les aliments de son menu |
| `EntityCitizen.java:597-626` | enfant nourri par le joueur : **cookie seulement**, plus Vitesse 300 ticks ; sinon l'objet est rejeté, et `MESSAGE_INTERACTION_COOKIE` |
| `EntityCitizen.java:633-648` | adulte nourri par le joueur (`ISFOOD`, sauf pomme dorée, et pas malade : l. 513-528). `addLastEaten` puis `consumeFood` ; attente de 100 ticks |
| `VisitorCitizen.java:453-466` | visiteur nourri par le joueur ; `MESSAGE_INTERACTION_VISITOR_FOOD` |

- Pendant l'attente d'interaction (`interactionCooldown > 0`), un objet d'interaction donne `WARNING_INTERACTION_CANT_DO_NOW` (`EntityCitizen.java:459-468`).

Appelants directs de `increaseSaturation` (hors `consumeFood`) :

- `EntityAIWorkCook.java:198`. Le serveur se nourrit lui-même quand l'inventaire de la hutte est plein : jusqu'à 10 bouchées, `extractItem` puis `increaseSaturation(getFoodValue)`, **sans** `consumeFood` (ni bol rendu, ni `HADGREATFOOD`, ni effets). Il compte `FOOD_SERVED`.
- `CommandCitizenModify.java:66` (commande).

### 6.2 Statistiques

`MC/api/util/constant/StatisticsConstants.java` :

- `FOOD_SERVED = "food_served"` (l. 17) : statistique de colonie par jour, incrémentée par le serveur aux l. 199, 242 et 297 de `EntityAIWorkCook` ;
- `FOOD_SERVED_DETAIL` (l. 63) : statistique de bâtiment, par objet ;
- `FOOD_COOKED_DETAIL` (l. 117) : chef ;
- `ITEMS_COOKED = "items_cooked"` (l. 26).

### 6.3 Visiteurs

La taverne fait asseoir ses visiteurs aux places `sit*` de `TavernBuildingModule.java:377-387` (`EntityAIVisitor.java:214`). Ils ne mangent que si le joueur les nourrit (6.1).

### 6.4 Effet du niveau de la salle à manger

- Liste des plats proposés : nutrition ≥ niveau − 1.
- Taille du menu : 5 × niveau.
- Stock visé par plat : `maxStack × niveau` (expectedStock).
- Plafond de `reachedMaxToKeep` : `canEatLevel(niveau − 1)` et `niveau² × 9`.

## 7. Interactions, validateurs et textes (en-US)

Les validateurs sont dans `MC/apiimp/initializer/InteractionValidatorInitializer.java`. Le registre est une `Map` : un second `registerStandardPredicate` sur la même clé **remplace** le premier (`InteractionValidatorRegistry.java:73-76`). `triggerInteraction` n'ajoute une interaction que si `isValid` est vrai (`MC/core/colony/CitizenData.java:1713-1724`). Une `StandardInteraction` sans validateur ni parent n'est jamais valide (`ServerCitizenInteraction.java:104-107`).

| Constante | Clé | Texte anglais (ligne de `manual_en_us.json`) | Validateur |
|---|---|---|---|
| `FURNACE_USER_NO_FUEL` | `com.minecolonies.coremod.furnaceuser.nofuel` | « Please tell me what kind of fuel I should use in my furnace! » (1446) | hutte avec `FURNACE` et liste de combustible vide (l. 54 et 190, identiques) |
| `BAKER_HAS_NO_FURNACES_MESSAGE` | `com.minecolonies.coremod.bakery.nofurnace` | « My work hut doesn't have any furnaces! Please repair it so I have some I can use! » (795) | l. 57 (toute hutte `FURNACE` sans four), **remplacé l. 221 par `instanceof BuildingBaker`** : jamais affiché pour le serveur ni le chef [in-game] |
| `FURNACE_USER_NO_FOOD` | `com.minecolonies.coremod.furnaceuser.nofood` | « Please add some food options to the menu so I can start serving food to the citizens! » (1448) | `BuildingCook` et menu vide (l. 192-200) |
| `POOR_MENU_INTERACTION` | `com.minecolonies.core.restaurant.poormenu` | « The dining hall menu is lacking variety and is unlikely to satisfy citizens. Please consider adding more Minecolonies food options to improve happiness. » (2978) | **aucun** : refusé par `triggerInteraction` [in-game] |
| `POOR_RESTAURANT_INTERACTION` | `com.minecolonies.core.restaurant.poorrestaurant` | « My dining hall needs an upgrade to meet the expectations of my guests. It currently falls short of their standards! » (2979) | **aucun** [in-game] |
| `RAW_FOOD` | `com.minecolonies.coremod.ai.wrongfood` | « The food I have is too raw to eat! » (1180) | a un cuisinable et aucun aliment mangeable selon `FoodUtils.canEat` (l. 59-63) |
| `BETTER_FOOD` | `com.minecolonies.coremod.ai.betterfood` | « Can I have better food please? » (1184) | saturation 0, adulte, `needsBetterFood()` (l. 64-65) |
| `BETTER_FOOD_CHILDREN` | `com.minecolonies.coremod.ai.betterfood.children` | « Ew! I don't want to eat that! » (1185) | idem, enfant (l. 66-67) |
| `NO_RESTAURANT` | `com.minecolonies.coremod.ai.norestaurant` | « Please build a dining hall or get me something to eat! » (866) | saturation ≤ `LOW_SATURATION` (6), pas de salle à manger (`getBestBuilding`), aucun `ISFOOD` sur lui (l. 68-71) |
| `NO + FOOD_QUALITY` | `com.minecolonies.coremod.entity.citizen.no.foodquality` | « I wish the dining hall menu would contain some better food options » (2873) | historique plein, niveau de maison > 2, `L−3 ≤ qualité < L−2` (l. 315-328) |
| `… + URGENT` | `….no.foodquality.urgent` | « I haven't had a decent meal in a while! The dining hall menu should have better options! » (2876) | qualité < L−3 (l. 287-299) |
| `NO + FOOD_DIVERSITY` | `com.minecolonies.coremod.entity.citizen.no.fooddiversity` | « I wish the dining hall menu would contain a bit more variety » (2874) | L > 1, `L/2 ≤ diversité < L` (l. 330-341) |
| `… + URGENT` | `….no.fooddiversity.urgent` | « I feel like I'm eating the same food everyday! The dining hall menu should contain more options. » (2877) | diversité < L/2 (l. 301-313) |

Autres textes :

| Clé | Texte anglais (ligne) |
|---|---|
| `block.minecolonies.blockhutcook` / `.name`, `com.minecolonies.building.cook` | « Dining Hall » (2071-2072, 2358) |
| `com.minecolonies.job.cook` | « Waiter » (2279) |
| `com.minecolonies.gui.visiblestatus.cook` | « Cooking » (1427) |
| `block.minecolonies.blockhutkitchen`, `com.minecolonies.building.kitchen` | « Chef's Kitchen » (2150, 2366) |
| `com.minecolonies.building.kitchen.desc` | « Prepares unique and tasty dishes your citizens will appreciate » (2563) |
| `com.minecolonies.job.chef` | « Chef » (2789) |
| `com.minecolonies.coremod.request.food` (`REQUESTS_TYPE_FOOD`) | « Menu » (879) |
| `com.minecolonies.coremod.request.burnable` (`REQUESTS_TYPE_BURNABLE`) | « Fuel » (880) |
| `com.minecolonies.core.menu.title` | « Menu » (2899) |
| `com.minecolonies.core.food_list.title` | « Food Options » (2900) |
| `com.minecolonies.core.menu.warning` | « Select the Food Citizens will eat at this dining hall » (2901) |
| `com.minecolonies.core.poor.menu.warning` | « A menu lacking quality food may not satisfy your citizens! » (2980) |
| `com.minecolonies.core.menu.ingredients` | « Ingredients » (3068) |
| `com.minecolonies.coremod.gui.warehouse.limitreached` | « Limit Reached » (1796) |
| `com.minecolonies.core.gui.restaurant.foodquality` | « Eaten up to Residence level: %d » (2883) |
| `com.minecolonies.core.gui.restaurant.foodconsumption` | « Citizens in this dining hall consume between %d and %d of this daily. » (2884) |
| `com.minecolonies.core.gui.restaurant.vanillafoodquality` | « Citizens prefer more sophisticated food! » (2977) |
| `com.minecolonies.coremod.cook.serve.player` (`MESSAGE_INFO_CITIZEN_COOK_SERVE_PLAYER`) | « %s: Here Governor, take this food! » (1160) |
| `com.minecolonies.coremod.interaction.nocookie` | « %s: I want cookie! » (1822) |
| `com.minecolonies.coremod.interaction.notnow` | « %s: I can't do that right now » (1823) |
| `com.minecolonies.coremod.interaction.visitor.food` | « Tasty food? I guess I'll stay a bit longer here » (1825) |
| `com.minecolonies.coremod.interaction.poison` | « Ugh! What was that? I'm not feeling very well now. » (1826) |
| `com.minecolonies.coremod.item.tooltip.wrongfood` | « This food is too raw for your citizens to eat » (1181) |
| `com.minecolonies.coremod.item.tooltip.needbetterfood` | « This food is not suitable for this citizen » (1182) |
| `com.minecolonies.coremod.item.tooltip.nomenu` | « This food cannot be eaten because it's not on the dining hall's menu. » (1183) |
| `com.minecolonies.core.item.food.tooltip.tier.1` / `.2` / `.3` | « This food is somewhat acceptable for your citizens. » / « This food will keep your citizens satisfied. » / « This food will make your citizens happy for sure! » (2879-2881) |
| `com.minecolonies.core.item.food.tooltip.chef` | « Ask a Chef in a Chef's Kitchen to cook this for you » (2870) |
| `com.minecolonies.coremod.gui.townhall.happiness.greatfood` / `.desc.greatfood` | « Food Factor » / « Had a tier 3 meal recently. » (2888, 2886) |
| `com.minecolonies.coremod.gui.townhall.happiness.saturation` / `.desc.saturation` | « Hunger » / « Increases happiness as citizens are well-fed. » (1837, 1854) |
| `com.minecolonies.coremod.gui.townhall.stats.food_served` | « Served Food: %d » (2588) |
| `….stats.food_served_detail` / `….stats.food_cooked_detail` | « Food Served: %d %s » / « Cooked: %d %s » (2634-2635) |
| `com.minecolonies.coremod.gui.chat.okay` / `.ignore` / `.remindmelater` / `.skipchitchat` | « I will work on it! » / « Please don't mention it again! » / « Please remind me later! » / « Skip the chit-chat » (1410-1412, 1993) |
| `com.minecolonies.coremod.info.cook.0` / `.1` / `.2` | textes d'aide du serveur (1562-1566) |

Les infobulles `tier.N`, `nomenu` et `needbetterfood` s'affichent dans l'inventaire d'un citoyen (`MC/core/event/ClientEventHandler.java:235-273`).

**Avertissements de résidence** (`com.minecolonies.core.gui.residence.warning.N`, l. 3061-3064). Ils s'affichent en infobulle du bouton de construction et dans une confirmation avant l'amélioration (`WindowBuildBuilding.java:130, 207-209`). `LivingBuildingView.getHoverWarningForLevel` (l. 93-129) renvoie `warning.(niveau+1)` :

- niveau 1 → `.2` « Warning: No food production detected. Build a Farm or Fisher's Hut before adding more residents. », si la colonie n'a ni pêcheur ni fermier de niveau ≥ 1 ;
- niveau 2 → `.3` « Warning: Higher-level residents expect regular meals. Build a Dining Hall and setup Minecolonies Food on the menu before upgrading. », si aucun menu de salle à manger n'a de plat MC de tier ≥ 1 (`checkColonyMenu`, l. 138-154) ;
- niveaux 3 et 4 → `.4` « Warning: Higher-level residents demand varied, cooked meals. Ensure you have a chef alongside your dining hall and a full menu before upgrading. » et `.5` « Warning: Your colony may struggle to feed residents at this level. Consider expanding food production and your Dining Hall menu first. », si aucun plat de tier ≥ 2 n'est au menu.
- Il n'y a pas de `warning.1`.

## Incertitudes

- Les trois interactions « mortes » (pauvre menu, pauvre salle à manger, pas de four pour le serveur) sont déduites de la lecture du source : registre `Map` et `isValid`. Elles ne sont pas vérifiées en jeu dans MC **[in-game]**.
- Le bug de l'objet rendu en double quand `playerInv != null` (6.1, étape 3) et le réactivage du bouton `<<` à la limite (1.3) sont aussi déduits du source **[in-game]**.
- Hytale n'a pas été étudié ici : équivalent du four (banc de transformation et combustible), entité de siège et étiquettes de plan. C'est à rechercher avant la conception côté plugin.
- Sources MC copiées vers le 2026-10-01 depuis `version/main`, sans hash de commit.
