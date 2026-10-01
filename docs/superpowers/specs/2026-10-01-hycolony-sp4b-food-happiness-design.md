# HyColony SP4b : faim, nourriture, salle à manger et bonheur des citoyens

Date : 2026-10-01. Conception faite en autonomie, à la demande de l'utilisateur (« réfléchis au système des PNJ, bonheur, faim et tout ce qui va avec, sans rien oublier ; écris la spec et l'implémentation »). Source MineColonies : `sources/minecolonies/` (`version/main`, copiée vers le 2026-10-01).

Recherche, **à lire avant tout** :

- `docs/research/sp4b-hytale-food.md`, noté « HF § x » : la nourriture côté Hytale (objets, cuisson au feu de camp, soins, dégâts, sièges, nourrir à la main) ;
- `docs/research/sp4b-mc-restaurant-cook.md`, noté « RC § x » : la salle à manger et le serveur de MC ;
- MC lu directement pour la faim et le bonheur : `EntityCitizen` (saturation, soins), `CitizenData`, `CitizenAI.shouldEat`, `EntityAIEatTask`, `FoodUtils`, `CitizenFoodHandler`, `CitizenHappinessHandler`, `ModHappinessFactorTypeInitializer`, `HappinessConstants`, les modificateurs de `api/entity/citizen/happiness/`, `CitizenWindowUtils`, `WindowCitizenPage`.

## 0. Ce que MC fait, et ce que HyColony a aujourd'hui

MC donne à chaque citoyen une saturation (0 à 60), qui baisse avec le temps, le travail et la marche. Un citoyen affamé va manger : ce qu'il porte, puis ce que sa hutte de travail contient, puis à la salle à manger, où le serveur cuit et sert les plats du menu. La saturation règle les soins naturels et ralentit un citoyen à jeun. Ce qu'il a mangé (ses 10 derniers repas) donne la diversité et la qualité de son alimentation, que le niveau de sa maison rend plus ou moins exigeantes. Le bonheur pondère quinze facteurs (logement, emploi, nourriture, sommeil, sécurité, vie sociale…), dont certains s'aggravent de jour en jour ou expirent ; il fixe le plafond de compétences des nouveaux citoyens et s'affiche partout.

HyColony a déjà : `CitizenData.saturation` (60, sauvée, jamais modifiée), la barre de nourriture de la fenêtre du citoyen, l'XP coupée à saturation 0 (`JobXp`), le sommeil (SP4) et `PLACEHOLDER_HAPPINESS = 5.5`. Personne ne mange, rien ne baisse.

## 1. Portée

**Dedans**, livré en trois étapes testables en jeu une par une :

1. **La faim** (§ 2 à § 6) : le catalogue des aliments, la saturation et sa baisse, les soins et le ralentissement, l'état `EATING` (inventaire, hutte de travail), nourrir un citoyen à la main, la nourriture que garde une hutte et ce qu'une hutte refuse de laisser manger, l'historique des repas.
2. **Le bonheur** (§ 7 à § 9) : les quinze modificateurs et leur journée, le bonheur global, ses effets, la barre et l'onglet Bonheur du citoyen, la page Citoyens de l'hôtel de ville.
3. **La salle à manger** (§ 10 à § 13) : la hutte, son serveur, la cuisson au feu de camp, le menu, le combustible, le service aux citoyens et aux joueurs, les places assises, et le chemin « salle à manger » de l'état `EATING`.

**Dehors**, faute du système dont ils dépendent (chacun reste un sous-projet) :

| Absent | Ce qui en dépend ici | Valeur prise |
|---|---|---|
| Maladie, hôpital, guérisseur | facteur `health`, `isSick` dans le facteur social et `shouldEat`, maladie par aliment empoisonné, modificateur de maladie de la nourriture | jamais malade (facteur 1, comme MC sans corps : `data.getEntity().isPresent() ? … : 1.0`) |
| Mort des citoyens (`citizen-death.md`) et deuil | modificateur `death` (injecté à tous à la mort d'un non-garde) | jamais injecté |
| Gardes, raids | facteur `security`, modificateur `raidwithoutdeath`, garde dans `slepttonight` | aucun garde : `security = min(1 / (travailleurs × 2/3), 2)` comme MC sans garde |
| École, élèves, enfants | facteur `school`, enfants nourris aux cookies | adulte : 1 |
| Site mystique | facteur `mysticalsite` | `max(1, 0 / 2) = 1` |
| Quêtes | modificateur `quest` | jamais ajouté |
| Recherche | effets `HAPPINESS`, `SATURATION`, `SATLIMIT`, `REGENERATION`, `MIN_ORDER`, `WORKING_IN_RAIN` | 0 |
| Interactions de citoyen (bulles et réponses) | plaintes `no.*`/`demands.*`, `RAW_FOOD`, `BETTER_FOOD`, `NO_RESTAURANT`, `FURNACE_USER_NO_FUEL`, `FURNACE_USER_NO_FOOD`, diversité et qualité | aucune bulle ; le § 14 liste chaque interaction avec son validateur, pour les brancher le jour où le système existera |
| Chef et cuisine, boulanger (plats de MC) | la « qualité » des plats | les plats du plan de cuisine de Hytale tiennent lieu de plats de MC (§ 2.2) ; un joueur les met au menu |
| Statistiques de production | `FOOD_SERVED`, onglet Statistiques de la salle à manger | rien n'est compté |
| Sons de citoyen | sons de repas, `SoundUtils` (humeur) | aucun son |
| Statut visible au-dessus de la tête | `VisibleCitizenStatus.COOK`, `HOUSE`… | la plaque de nom ne change pas pour la faim |

Les dégâts et les soins sont portés (§ 4), mais **le rôle citoyen est aujourd'hui invulnérable** (HF § 5.c) : tant que la mort des citoyens n'est pas portée, un citoyen n'est jamais blessé, donc jamais soigné, et le modificateur `damage` ne se déclenche pas. Le code est prêt et testé ; seul le filtre de dégâts manque (`citizen-death.md` § 2.4).

## 2. Le catalogue des aliments

### 2.1 Ce que le cœur demande (MC `FoodProperties`, `ItemStackUtils.ISFOOD/ISCOOKABLE`, tag `poisonous_food`, `IMinecoloniesFoodItem`)

`kernel/item/FoodInfo` (record) : `nutrition` (int ≥ 1), `tier` (0 = aliment ordinaire, 1 à 3 = plat « de MC »), `poisonous`.

`ItemCatalog` reçoit :

- `Optional<FoodInfo> food(ItemKey)` : vide si l'objet ne se mange pas (MC `ISFOOD` faux) ;
- `Optional<ItemKey> cooked(ItemKey)` : ce que donne la cuisson de l'objet au feu de camp (MC résultat de four), vide sinon. `ISCOOKABLE` = `cooked` présent **et** aliment.

Le cœur en tire tout le reste (`citizen/food/FoodRules`, MC `FoodUtils`) :

- `EDIBLE` = aliment **et** non cuisinable (MC `EDIBLE`) ;
- `canEatLevel(item, niveau)` : niveau < 3 → aliment ; sinon `nutrition ≥ niveau + 1` (MC). Les cultures (`ItemCrop`) ne sont jamais mangées : dans Hytale, toutes les cultures se cuisent, elles sont donc déjà exclues par `EDIBLE` ;
- `canEat(item, maison, atelier)` : `EDIBLE`, non empoisonné, `canEatLevel(niveau équivalent de la maison, 0 sans maison)`, et `atelier == null || atelier.canEat(item)` (§ 6.2) ;
- `foodValue(item)` = `nutrition × (tier > 0 ? 2 : 1) × (1 + 0)` (recherche `SATURATION` absente) ;
- `foodTier(item)` = `tier` ; MC donne aussi le palier 1 à un aliment ordinaire de nutrition ≥ 12 et saturation ≥ 0,8 : aucun aliment de la table n'en a, `Deviation from MC` documentée, la saturation n'est pas portée ;
- `buildingLevelForFood(item) = max(2, min(nutrition − 1, 5))` ;
- exigences de diversité et de qualité : `niveau` et `max(0, niveau − 2)` ;
- `consumptionFactor(niveau)` : 0,3 ; 0,6 ; 0,725 ; 1,0 ; 1,2 ; 1,5 (niveaux 0 à 5).

### 2.2 La table Hytale (id-map `foods`, HF § 1, § 3.2, § 8)

Hytale n'a pas de faim ni de nutrition (HF § 2). La table est donc **un choix de HyColony**, calqué sur les valeurs de MC pour les aliments équivalents, et documenté dans `docs/research/sp4b-hytale-food.md` § 8 et dans la Javadoc de l'adaptateur (`Deviation from MC`). Elle vit dans `hycolony/id-map.json`, section `foods` : `id → { "nutrition": n, "tier": t, "poisonous": bool }`. Un id absent n'est pas un aliment. Un id inconnu des assets est ignoré et journalisé une fois.

Règle de correspondance : la cuisson au feu de camp de Hytale tient lieu du four de MC (aliments ordinaires, palier 0), les plats du plan de cuisine de Hytale tiennent lieu des plats du chef et du boulanger de MC (paliers 1 à 3, selon le buff de régénération de l'objet, HF § 1.5).

| Objets | nutrition | palier | équivalent MC |
|---|---|---|---|
| 9 fruits `Plant_Fruit_*` | 4 | 0 | pomme 4 |
| `Food_Egg` | 2 | 0 | — |
| `Food_Candy_Cane` | 2 | 0 | cookie 2 |
| 4 viandes crues `Food_*_Raw` (bœuf, porc, poulet, gibier) | 3 (poulet 2, empoisonné comme le poulet cru de MC) | 0 | bœuf cru 3, poulet cru 2 |
| `Food_Fish_Raw` | 2 | 0 | morue crue 2 |
| 12 légumes `Plant_Crop_*_Item` | 1 (carotte 3, pomme de terre 1…) | 0 | pomme de terre 1, carotte 3 |
| 6 champignons lumineux `Plant_Crop_Mushroom_Glowing_*` | 1, **empoisonnés** | 0 | pomme de terre empoisonnée |
| `Food_Wildmeat_Cooked` | 8 | 0 | bœuf cuit 8 |
| `Food_Fish_Grilled` | 5 | 0 | morue cuite 5 |
| `Food_Vegetable_Cooked` | 5 | 0 | pomme de terre cuite 5 |
| `Food_Bread`, `Food_Cheese`, `Food_Popcorn` | 6 | 1 | pain de manchet, cheddar 6 |
| 4 brochettes `Food_Kebab_*`, `Food_Salad_Berry`, `Food_Salad_Mushroom` | 8 (salades 9) | 2 | kebab 8, pottage 9 |
| `Food_Salad_Caesar`, `Food_Pie_Apple`, `Food_Pie_Pumpkin` | 12 | 3 | steak dinner 12 |
| `Food_Pie_Meat` | 13 | 3 | hand pie 13 |

Les ingrédients (`Ingredient_*`), les boissons et les potions ne sont pas de la nourriture (HF § 1.6). Aucun aliment solide de Hytale ne rend de contenant : le retour du bol de MC (`consumeFood`) n'a pas d'objet, `Deviation from MC`.

### 2.3 La cuisson (`cooked`)

L'adaptateur lit les recettes du banc `Processing` `Campfire` (`CraftingPlugin.getBenchRecipes`, HF § 3.3) au premier usage : pour chaque recette, chaque objet qui satisfait l'entrée (un objet, ou tous ceux d'un `ResourceType`) donne la sortie principale. Les champignons non comestibles (`Vegetables`) sont donc cuisinables, comme un aliment cru de MC qui n'est pas lui-même de la nourriture. L'id du banc vient de l'id-map (`cookingBench: "Campfire"`).

## 3. La saturation (MC `CitizenData`, `EntityCitizen`, `CitizenConstants`)

Constantes (MC `CitizenConstants`) : `MAX_SATURATION = 60` (déjà), `AVERAGE_SATURATION = 10`, `LOW_SATURATION = 6`, `FULL_SATURATION = 60`, `SATURATION_DECREASE_AFTER = 1200` ticks, `BIG_SATURATION_FACTOR = 0.2`, `SATURATION_DECREASE_FACTOR = 0.02`, `ACTIONS_EACH_BLOCKS_WALKED = 25`, `HEAL_CITIZENS_AFTER = 100`, `SEEK_DOCTOR_HEALTH = 6`.

`CitizenData` reçoit :

- `increaseSaturation(x)` : `min(60, s + |x|)` ;
- `decreaseSaturation(x, foodModifier)` : `max(0, s − |x × foodModifier|)` et `justAte = false` (MC : seulement si la colonie est active ; les ticks qui l'appellent ne tournent que colonie `ACTIVE`) ;
- `justAte` (sauvé, MC `TAG_JUST_ATE`) ;
- l'historique des repas (§ 5.3, sauvé) ;
- le statut de travail `JobStatus` (`IDLE`, `WORKING`, `STUCK`, sauvé, MC `TAG_JOB_STATUS`, § 8.2) ;
- **runtime seulement** (MC le garde sur l'entité) : la baisse en attente due aux actions (`decreaseSaturationForAction` +0,2, `…ForContinuousAction` +0,02) et la distance de marche cumulée.

**Réglage** : `Gameplay.foodModifier` (MC `foodmodifier`, défaut 1,0, bornes 0,1 à 100) dans `config.json` (`FoodModifier`).

### 3.1 La baisse au repos (MC `EntityCitizen.decreaseIdleSaturation`, toutes les 1200 ticks)

Pour chaque citoyen au corps vivant, **le jour seulement** (`clock.isDaytime()`, MC `!level().isNight()`) et **éveillé** :

1. `baisse = consumptionFactor(niveau équivalent de la maison, 0 sans maison)` ;
2. × `job.saturationFactor()` (1,0 par défaut ; 1,2 pour le livreur, le fermier, et plus tard bûcheron, mineur, carrier, gardes : MC) ;
3. + `min(baisse / 2, baisseEnAttente)`, puis la baisse en attente revient à 0 ;
4. ÷ 2 pour un enfant ;
5. `decreaseSaturation(baisse, foodModifier)`.

`CitizenManager` le fait sur une cible de colonie toutes les 1200 ticks (MC : par entité, même période). *Écart* : la phase est commune à tous les citoyens.

### 3.2 La baisse au travail (MC `incrementActionsDoneAndDecSaturation`, `decreaseSaturationForContinuousAction`)

`Job.incrementActionsAndDecSaturation()` = action en attente +0,2, puis `incrementActions()`. Les points d'appel suivent MC un par un, pour les métiers portés :

| MC | HyColony |
|---|---|
| `AbstractEntityAIStructure.completeBuild` (l. 265) : action + saturation | `BuilderAI.completeBuild` |
| `AbstractEntityAIStructure` minage d'un bloc (l. 671) : continu | `BuilderBlockWork`, bloc retiré |
| `AbstractEntityAICrafting` outil cassé (l. 625) : action + saturation | `CraftingWork` (le commentaire « saturation is not ported » tombe) |
| `EntityAIWorkFarmer` labour (l. 610) et semis (l. 769) : continu | `FieldPass` labour et semis |
| `EntityAIWorkDeliveryman` ramassage (l. 185), livraison (l. 453, 462) : continu | `CourierPickup`, `CourierDelivery` (noms réels au plan) |
| `AbstractEntityAIUsesFurnace` extraction (l. 411) : action + saturation ; serveur l. 120, 245, 302 | IA du serveur (§ 11) |

### 3.3 La marche (MC `EntityCitizen.decreaseWalkingSaturation`)

Tous les 25 de distance de marche (MC `walkDist`, distance horizontale × 0,6), une baisse continue (+0,02). *Écart* : la distance est cumulée à partir des positions relevées par `CitizenManager.tickData` toutes les 60 ticks (MC : à chaque pas), ce qui sous-estime un peu les trajets tortueux.

## 4. Soins et ralentissement (MC `EntityCitizen.updateHealing`, `checkHeal`, toutes les 100 ticks)

Pour chaque corps vivant :

- **Soins** : si `vie < max` (max / 3 s'il est malade : jamais ici) et qu'il n'a pas été blessé par un ennemi depuis 100 ticks (MC `getLastHurtByMob() == null`) :
  - saturation ≥ 60 → +2 ; saturation < 6 → `saturation / 60 / 2` ; sinon +1 (MC, recherches à 0).
- **Ralentissement** : saturation ≤ 0 → ralenti (MC `MOVEMENT_SLOWDOWN` niveau 0, −15 %) ; sinon plus ralenti.

Port `CitizenBodies` :

- `double health(BodyId)` et `double maxHealth(BodyId)` (0 sur un corps inconnu) ; notre rôle a `MaxHealth 20`, la vie d'un citoyen est donc à l'échelle de MC, sans conversion (HF § 2) ;
- `void heal(BodyId, double)` (`EntityStatMap.addStatValue(health, n)`, HF § 5.b) ;
- `boolean recentlyHurt(BodyId)` : blessé depuis moins de 100 ticks ;
- `void setStarving(BodyId, boolean)` : l'adaptateur multiplie la vitesse du métier par 0,85 (`CitizenSpeed`, avec des paliers < 1 ajoutés à `speedEffects` : `HyColony_Speed_085`, `_090`, `_095`, HF § 5.d).

L'adaptateur coupe la régénération naturelle des PNJ (`HealthRegenState.setRegenEnabled(false)`, HF § 2) à l'apparition du corps : la santé ne dépend plus que de la nourriture, comme dans MC **[in-game]**.

**Dégâts** (pour `shouldEat`, les soins et le modificateur `damage`) : un `DamageEventSystem` du groupe *inspect* (HF § 5.c) appelle `CitizenManager.onBodyHurt(body, cause)`. Le feu et la foudre ne comptent pas (MC `hurt`, `IS_FIRE`/`IS_LIGHTNING`). Aujourd'hui inactif (§ 1).

## 5. Manger (MC `CitizenAI.shouldEat`, `EntityAIEatTask`)

### 5.1 La décision

`CitizenState` gagne `EATING`. La décision suit l'ordre de MC : sommeil, (maladie : absente), **faim**, (deuil : absent), pluie, travail. Elle tourne toutes les 10 ticks dans le même événement que le sommeil (`CitizenAI.decide`).

`shouldEat()` (MC, à l'identique) :

1. faux si `justAte` ou saturation ≥ 60 ;
2. faux si le métier ne peut pas être interrompu (`JobAI.canBeInterrupted`) ;
3. vrai si l'état est déjà `EATING` ;
4. pour le serveur (§ 11) : faux 199 fois sur 200 (`random.nextInt(200) > 0`) ;
5. (malade et endormi : absent) ;
6. vrai si saturation ≤ 10 **et** (saturation ≤ 2,5 `RESTAURANT_LIMIT` **ou** (saturation < 6 **et** vie < 6)).

Un citoyen qui part manger perd son IA de métier (`dropJobAI`), comme une sortie vers `IDLE`.

### 5.2 L'IA du repas (`citizen/food/EatAI`, MC `EntityAIEatTask`)

Machine à états, chaque transition toutes les 20 ticks, créée à l'entrée en `EATING` (`reset`) :

| État | Rôle (MC) |
|---|---|
| `CHECK_FOR_FOOD` | `hasFood()` → `EAT`, sinon `GO_TO_HUT` |
| `GO_TO_HUT` | sans atelier ou atelier non chargé → `SEARCH_RESTAURANT` ; marche vers l'atelier ; meilleur aliment de l'inventaire (menu de l'atelier s'il est une salle à manger, puis sans menu) → `EAT` ; sinon prend dans l'atelier le meilleur aliment (`checkForFoodInBuilding`) → `EAT` ; sinon le serveur → `DONE` ; sinon `SEARCH_RESTAURANT` |
| `SEARCH_RESTAURANT` | salle à manger la plus proche **avec serveur**, sinon la plus proche ; le métier passe « pas au travail » ; aucune → saturation ≥ 10 : `justAte`, `DONE` ; sinon (plainte `NO_RESTAURANT`, § 14) `CHECK_FOR_FOOD` |
| `GO_TO_RESTAURANT` | dans l'emprise → `WAIT_FOR_FOOD` ; sinon marche |
| `WAIT_FOR_FOOD` | salle la plus proche de l'atelier (ou du corps) ; s'inscrit comme client ; hors emprise → `GO_TO_RESTAURANT` ; une place libre → `GO_TO_EAT_POS` ; de la nourriture → `EAT` ; saturation ≥ 10 → `justAte`, `DONE` |
| `GO_TO_EAT_POS` | marche à 2 blocs de la place puis s'assoit 1200 ticks ; sans place ou après 400 tentatives : de la nourriture → `EAT`, sinon attend 2 min (120 transitions) puis `GET_FOOD_YOURSELF` |
| `GET_FOOD_YOURSELF` | marche à la salle ; prend dans ses contenants le meilleur plat du menu, en quantité `ceil(⌊max(1, (60 − s) / valeur)⌋ × 1,5)` (MC tronque d'abord) → `EAT` ; sinon `WAIT_FOR_FOOD` |
| `EAT` | tient l'aliment, joue l'animation de repas et la particule ; 5 transitions (`REQUIRED_TIME_TO_EAT`) ; consomme (§ 5.3) ; tant que saturation < 60 et qu'il reste de ce plat dans la case : recommence ; sinon note les plats mangés, `justAte`, `IDLE` |
| `DONE` | `IDLE` |

- **`hasFood()`** : meilleur aliment de l'inventaire selon le menu de la salle visitée (s'il y en a une), sinon rien. Les plaintes `BETTER_FOOD` et `RAW_FOOD` sont au § 14.
- **Meilleur aliment** (`citizen/food/FoodChoice`, MC `getBestFoodForCitizen`, `checkForFoodInBuilding`, `hasBestOptionInInv`) : score = rang dans l'historique (`checkLastEaten`, −1 s'il n'y est pas) × (plat de palier > 0 ? 1 : 2), plus petit = meilleur ; arrêts anticipés et retour à la salle à manger exactement comme MC (le tirage `RANDOM.nextInt(max(1, tier + 2 − niveau))` passe par `ColonyContext.random`).
- **Marche** : `BlockApproach` et `BodyWalker`, comme le sommeil ; « marcher jusqu'à la hutte » = dans l'emprise (`HutFootprint`).
- **Animation** : `BodyAnimation.EAT` (`"Item"`, `"Consume"`, HF § 5.a) avec l'aliment en main ; particule `WorldEffects.eating(at)` (`Food_Eat`, HF § 5.e) **[in-game]**.
- `reset()` : vide la main, oublie salle, place et plats.

### 5.3 Consommer (MC `ItemStackUtils.consumeFood`) et l'historique (MC `CitizenFoodHandler`)

Consommer un aliment : −1 dans l'inventaire, `increaseSaturation(foodValue)`, et pour un plat de palier ≥ 3 le modificateur `greatfood` (§ 7). MC applique aussi les effets de l'aliment au citoyen (`finishUsingItem`) : *écart*, les buffs Hytale ne sont pas appliqués (la santé suit la saturation, § 4).

Historique (`citizen/food/FoodHistory`) : file des 10 derniers objets mangés (`FOOD_QUEUE_SIZE`), sauvée. `addLastEaten`, `lastEaten`, `checkLastEaten` (dernier index, −1), `stats()` = (`max(1, objets distincts)`, nombre de plats de palier > 0), `isFull()`. MC note le premier plat d'un repas au restaurant, puis à la fin chaque plat différent du dernier noté (`EntityAIEatTask.eat`), à l'identique. Le modificateur de maladie de l'historique n'est pas porté (pas de maladie).

## 6. Nourrir à la main, et la nourriture des huttes

### 6.1 Nourrir un citoyen (MC `EntityCitizen.mobInteract`, `eatFoodInteraction`)

La touche *Use* sur un citoyen avec un aliment en main le nourrit au lieu d'ouvrir sa fenêtre (HF § 7 : le clic droit fait manger le joueur). Le cœur décide (`citizen/food/HandFeeding`) :

- délai d'interaction en cours (`interactionCooldown > 0`) → message `notnow` (« %s : je ne peux pas faire ça maintenant »), rien n'est pris ;
- aliment empoisonné → 1 objet pris, délai 400 ticks ; MC rend alors le citoyen malade (*écart* : pas de maladie, rien d'autre) ;
- sinon : 1 objet pris, `addLastEaten`, consommation (§ 5.3), délai 100 ticks, particule de repas.

Le délai décompte par tick (MC `onTickDecrements`) : il est tenu par l'IA du citoyen (runtime). Un enfant ne mange que les cookies dans MC : HyColony n'a pas d'enfants, ce chemin n'est pas porté. La pomme dorée, le livre, le cactus et la poudre lumineuse (autres objets d'interaction de MC) ne sont pas de la nourriture et restent hors de ce chantier.

Plugin : `CitizenUseSystem` lit l'objet tenu dans le contexte de `UseEntityEvent.Pre` ; si `ItemCatalog.food` le connaît, il appelle l'action et retire l'objet si le cœur l'a pris **[in-game]**.

### 6.2 Ce qu'une hutte refuse de laisser manger (MC `IBuilding.canEat`)

Un module de hutte qui implémente `citizen/food/EatingRule` (`canEat(colony, hut, item)`, vrai par défaut) a son mot à dire, comme les surcharges de MC pour les huttes portées :

- constructeur (`BuildingResourcesModule`) : refuse ce que son chantier exige (MC `BuildingBuilder.canEat`) ;
- fermier (`FarmerFieldsModule`) : refuse les graines de ses champs (MC `BuildingFarmer.canEat`) ; le blé, que MC refuse aussi, n'est pas un aliment dans Hytale ;
- livreur (`CourierTaskListModule`) : refuse l'objet de la livraison en cours (MC `BuildingDeliveryman.canEat`).

### 6.3 La nourriture qu'une hutte garde (MC `AbstractBuilding.keepFood`)

`HutKeep` gagne la règle de MC : chaque hutte garde `niveau × 2` aliments que `canEat(objet, null, hutte)` accepte, inventaire du travailleur compris ; pas la salle à manger (`keepFood = false`). L'écart « no keepFood rule » de `HutKeep` disparaît. Le livreur ne les emporte plus, et le travailleur en garde autant sur lui au vidage.

## 7. Le bonheur (MC `CitizenHappinessHandler`)

`citizen/happiness/` : un `CitizenHappiness` par citoyen, et les modificateurs.

### 7.1 Les modificateurs

Trois sortes (MC `api/entity/citizen/happiness`) :

- **statique** (`StaticHappinessModifier`) : facteur = fonction ;
- **dans le temps** (`TimeBasedHappinessModifier`) : jours comptés ; si le facteur de base < 1, il est multiplié par la valeur du dernier palier `(jours, ×)` atteint ; `dayEnd` : jours +1 si la règle de passage est vraie (par défaut : facteur < 1), sinon remis à 0 ; `reset` remet à 0 ;
- **qui expire** (`ExpirationBasedHappinessModifier`) : vaut sa valeur fixe tant que `0 < jours ≤ période`, sinon 1 ; `dayEnd` : jours −1 ; `reset` : jours = période.

| id | sorte | poids | facteur (MC `ModHappinessFactorTypeInitializer`) |
|---|---|---|---|
| `school` | statique | 1 | enfant : élève 2, sinon 0 ; adulte 1 |
| `security` | statique | 4 | `min(gardes / (autres × 2/3), 2)`, gardes et autres comptés à partir de 1 |
| `social` | statique | 2 | `(n − (sans emploi adultes + sans-abri + malades + affamés ≤ 1)) / n` |
| `mysticalsite` | statique | 1 | `max(1, niveau max des sites / 2)` |
| `food` | statique | 3 | sans maison ou historique incomplet : 1 ; sinon `(min(5, diversité / niveau) + min(5, qualité / max(1, niveau − 2))) / 2` |
| `homelessness` | temps | 3 | `niveau de la maison / 3`, 0 sans maison ; paliers (7 j, ×0,75), (14 j, ×0,5) |
| `unemployment` | temps | 2 | enfant 1 ; sans atelier 0,5 ; atelier de niveau > 3 : 2 ; sinon 1 ; paliers (7, ×0,75), (14, ×0,5) |
| `health` | temps | 2 | malade 0,5, sinon 1 ; paliers (7, ×0,5), (14, ×0,1) |
| `idleatjob` | temps | 1 | `STUCK` 0,5, sinon 1 ; paliers (7, ×0,5), (14, ×0,1) |
| `slepttonight` | temps | 1,5 | garde 1, sinon 0,5 ; passage toujours vrai ; paliers (0, ×2), (2, ×1,6), (3, ×1) |
| `damage` | expire | 2 | valeur 0, 1 jour, ajouté à chaque blessure (hors feu et foudre) |
| `greatfood` | expire | 2 | valeur 2, 5 jours, ajouté par un plat de palier ≥ 3 |
| `death`, `raidwithoutdeath`, `quest` | expire | 3, 1, 2 | jamais ajoutés ici (§ 1) |

- `addModifier` remplace le modificateur de même id (MC `put`) et vide le cache.
- `resetModifier(slepttonight)` à chaque arrivée au lit, même sans lit (MC `EntityAISleep:213`, SP4 R § E) : branché dans `SleepAI`.

### 7.2 Le calcul (MC `getHappiness`)

`Σ(facteur × poids) / Σ poids` sur les modificateurs dont le facteur ≠ 1, × (1 + 0), × 10, plafonné à 10. Le résultat est mis en cache et n'est recalculé qu'après un ajout, une remise à zéro ou une fin de journée, comme MC : un citoyen qui reçoit une maison voit son bonheur changer au plus tard à la tombée de la nuit. Chaque modificateur garde aussi la dernière valeur calculée de sa fonction, que les fenêtres affichent (MC `getFactor(null)`). *Cas limite* : quand tous les facteurs valent 1, MC divise 0 par 0 et obtient `NaN` ; HyColony renvoie 10 (`Deviation from MC: an all-neutral citizen is fully happy instead of NaN`).

Bonheur global de la colonie (MC `Colony.getOverallHappiness`) : moyenne des citoyens, 5,5 sans citoyen. Il remplace `PLACEHOLDER_HAPPINESS`.

### 7.3 La journée (MC `Colony.checkDayTime` → `checkCitizensForHappiness`)

À la tombée de la nuit, **si un joueur est dans la colonie** (MC : abonnés proches), chaque citoyen fait `dayEnd` de chacun de ses modificateurs à jours, puis vide son cache. Les plaintes quotidiennes (`no.<id>`, `demands.<id>`) sont des interactions (§ 14).

### 7.4 Les effets du bonheur

- **Nouveau citoyen** (MC `initForNewCivilian`) : plafond de compétences = `(int) bonheur global × 2`, au moins 5 tant que la colonie a moins que `initialCitizenAmount` citoyens.
- Le reste (sons, recherche, plafond d'Intelligence des élèves) dépend de systèmes absents. Le bonheur ne change ni la vitesse de travail ni les départs dans MC.

### 7.5 Persistance

Par citoyen, `happiness` : pour chaque modificateur à jours, `{ "id", "days" }` ; pour chaque modificateur qui expire, `{ "id", "weight", "value", "days", "period" }`. Un id inconnu est ignoré (MC `VALID_HAPPINESS_MODIFIERS`).

## 8. Le statut de travail (MC `JobStatus`)

### 8.1 Valeurs

`CitizenData.jobStatus` : `IDLE` (défaut, MC `initEntityValues` à l'attribution), `WORKING`, `STUCK`.

### 8.2 Qui le pose (MC)

- `checkForToolOrWeapon` : outil manquant → `STUCK`, sinon `WORKING` (l. 971-975) : l'équivalent HyColony est la vérification d'outil partagée des métiers (constructeur, fermier) ;
- `holdEfficientTool` : `WORKING` (l. 1341, 1347) ;
- fermier (`prepareForFarming`) : `IDLE` au départ, `STUCK` sans hutte construite, sans champ ou sans houe, `WORKING` sinon (l. 211-262).

Il nourrit le facteur `idleatjob`. Il est sauvé.

## 9. Les fenêtres du bonheur et de la faim

### 9.1 Fenêtre du citoyen (MC `main.xml`, `happiness.xml`, `CitizenWindowUtils`)

- **Barre de bonheur** (MC `happinessBar` en (55, 63), 120 × 11) : 10 smileys vides, puis `(int) bonheur × 2` en demi-smileys (règle de `createHappinessBar` : plein tant que `restant > 1`, puis un demi). Textures de MC `happiness_icons` copiées et agrandies ×4 (CLAUDE.md § 7). La règle est dans le cœur (`app/citizen/HappinessBar`, comme `HealthBar` et `SaturationBar`).
- **Onglet Bonheur** (sceau `side2`, icône `happiness`, en y = 236 de `nav.xml`) : titre « Modificateurs de bonheur » en (45, 32), puis une ligne par modificateur dont le facteur ≠ 1, à partir de y = 62, tous les 12 : icône 11 × 11 en x = 45 (heureux > 1 ; neutre = 1 ; insatisfait > 0,75 ; malheureux sinon) et nom en x = 70, avec en infobulle la description (`gui.townhall.happiness.desc.<id>`) et le sens (Positif, Neutre, Légèrement négatif, Négatif).
- L'écart « no Happiness tab » de `CitizenPage` disparaît. Les deux lignes « Métier / activité » gardées à la demande de l'utilisateur restent sous la barre de bonheur.

### 9.2 Hôtel de ville, page Citoyens (MC `layoutcitizens.xml`, `WindowCitizenPage`)

- **Page de gauche** : « Bonheur global : x » (arrondi au dixième supérieur, MC `DecimalFormat("#.#")` `CEILING`) en (100, 61), puis la liste `happinessList` : pour chaque modificateur, la **moyenne** de ses facteurs sur tous les citoyens (MC `somme / getCitizenCount()`), avec l'icône et le nom.
- **Détail du citoyen choisi** : vie `x/max`, bonheur `x/10`, saturation `x/20` (MC écrit « /20 » alors que le maximum est 60 : repris tel quel) avec leurs trois icônes.
- L'écart « no happiness, health nor food system yet » de la spec de l'hôtel de ville disparaît.

### 9.3 Vues et API

- `CitizenView` gagne le bonheur, la liste des modificateurs (id, facteur) et la vie (déjà en pourcentage : la vie absolue et le maximum s'ajoutent).
- `TownHallView.Citizens` gagne le bonheur global et la moyenne par modificateur.
- **API** (`dev.hycolony.api`) : l'instantané du citoyen gagne `saturation` et `happiness` (record : nouvelle composante = **rupture**, spec 2026-09-30 § 4.1). Comme une rupture demande une version majeure, on ne touche pas aux records existants : on ajoute une méthode `@Experimental` `ColonyWorld.wellbeing(CitizenRef) → Optional<CitizenWellbeing>` (nouveau record `read/CitizenWellbeing` : saturation, maximum, bonheur, facteur de chaque modificateur ; `@since 1.1`, `ApiVersion` 1.1.0), à côté de `citizen(ref)` plutôt que sur `HyColonyApi`, car toutes les lectures d'un monde y sont ; `apiDump` régénéré (les parties expérimentales n'entrent pas dans `api.txt`). HyLens l'affiche dans son HUD de suivi (une ligne « Saturation : x/60, bonheur : y/10 », le maximum venant de l'API) et se déclare construit contre 1.1.

## 10. La salle à manger (MC `BuildingCook`, RC § 1)

- Type `hycolony:cook`, clé de hutte `hut.cook`, niveau max 5, objet et bloc `HyColony_Hut_Cook` (copiés du fermier), styles Outlander et Kweebec (`styles.json` des sous-plugins : des maisons avec sièges, RC § 5 ; le choix exact est fait au plan).
- Paquet : `crafting/restaurant` (le cœur interdit un nouveau paquet de premier niveau, `FeatureDependenciesTest` ; le serveur est un artisan qui use d'un four dans MC). Le contrat dont l'IA du repas a besoin est un port du cœur, `citizen/food/DiningHall` (module de hutte : menu, serveur présent, place libre, client), car `citizen` ne voit pas `crafting`.
- Modules (MC `ModBuildingsInitializer:143-153`) : travailleur `hycolony:cook` (Adaptabilité / Savoir, travaille sous la pluie, **1** serveur à tous les niveaux), fours (`FurnaceUserModule` : les feux de camp enregistrés), liste de combustibles (`ITEMLIST_FUEL`), menu (`RESTAURANT_MENU`), et les places assises. `keepFood = false`. Pas de statistiques (§ 1).
- **Fours** : un feu de camp (`Bench_Campfire`, banc `Processing` `Campfire`) est l'équivalent du four de MC. Le constructeur et la baguette l'enregistrent déjà comme poste de travail (`RegisteredBlocks.addWorkstation`). *Écart forcé* : aucun préfabriqué de PNJ de Hytale n'a de feu de camp (recherche dans les 8089 préfabriqués de 0.7.0-pre.4), donc **un feu de camp posé par un joueur dans l'emprise de la salle à manger est aussi enregistré** ; MC ne l'enregistre que posé par le plan. Un poste qui n'est plus un feu de camp est retiré au passage, comme MC.
- **Places** : MC lit les étiquettes `sit`, `sit_in`, `sit_out` du plan ; Hytale n'en a pas. *Écart* : les places sont les blocs à sièges (`ItemCatalog.isSeat`, `BlockType.getSeats() != null`, HF § 6) posés par le plan ou par un joueur dans l'emprise, enregistrés comme les lits. `sit_out` (dehors, ignoré sous la pluie) n'a pas d'équivalent : toutes les places sont « dedans ». `nextSeat()` : 3 tirages uniformes parmi les places libres (port `CitizenBodies.isSeatTaken`), sinon aucune.

### 10.1 Le menu (MC `RestaurantMenuModule`, RC § 1.3)

- Ensemble d'aliments `EDIBLE`, au plus `niveau × 5` (`STOCK_PER_LEVEL`), sauvé ; un aliment non `EDIBLE` est refusé à l'ajout, et retiré au premier tick de colonie après la lecture (*écart* : MC le filtre en lisant, un module lit sans le catalogue).
- Toutes les 500 ticks (`onColonyTick`), hutte chargée : pour chaque plat, `cible = taillePile × niveau` ; `compte` = ce plat dans les contenants de la hutte (MC `hasBuildingEnoughElseCount`) + son aliment cru s'il se cuit en ce plat ; `delta = cible − compte` ; si `delta > 0` et pas de requête ouverte de ce plat : requête `StackRequest(plat, min(64, taillePile, delta), 1, canBeResolvedByBuilding = false)` ; si une requête du plat existe, qu'elle n'est pas plus loin que `IN_PROGRESS` et qu'aucune de l'aliment cru n'existe : la même pour l'aliment cru ; si `delta ≤ 0` : annule les deux requêtes. *Écart* : MC fait un `MinimumStack` ; HyColony n'a pas ce type, `StackRequest` avec `canBeResolvedByBuilding = false` se comporte pareil. Une requête est « ouverte » tant qu'elle n'est pas terminée : MC la retire des requêtes ouvertes dès qu'elle est livrée, même si personne ne l'a encore reçue.
- Retirer un plat annule sa requête ouverte. *Écart* : MC la cherche sous le type `Stack` alors que ses requêtes sont des `MinimumStack`, et ne la trouve jamais (bug de MC corrigé).
- La hutte garde `taillePile × niveau` de chaque plat et de son cru (`alterItemsToBeKept`, `HutKeep`).
- **Onglet Menu** (MC `layoutfoodstock.xml`, ×2) : à gauche le menu (dégradé de palier or / argent / bronze, icône, nom, bouton X), l'avertissement « choisissez la nourriture… » si vide, l'avertissement rouge « un menu sans plats de qualité… » s'il n'a aucun plat de palier ≥ 2 ; à droite les aliments proposés (`EDIBLE` de nutrition ≥ niveau − 1, triés par `palier × −100 − nutrition` puis nom), un filtre (25 caractères) et le bouton `<<`. Ajouter et retirer exigent `MANAGE_HUTS` et ré-affichent la fenêtre. La liste des ingrédients et la consommation journalière (§ RC 1.3) suivent MC (ingrédients = la recette Hytale du plat, sur 5 niveaux au plus comme `getRecipeFromStack(…, 5)` ; la consommation s'affiche en décimal, comme MC). Sur un menu plein, `<<` porte l'infobulle « Limite atteinte » de MC (MC le grise puis le réactive aussitôt : seule l'infobulle reste).

### 10.2 Le combustible (MC `ItemListModule` `FUEL_LIST`)

Liste d'objets autorisés, sauvée, par défaut les combustibles « charbon » de Hytale (`Ingredient_Charcoal`, id-map `defaultFuels`) ; onglet Combustible : les objets du `ResourceType` `Fuel` (port `ItemCatalog.isFuel`), cochables. `MANAGE_HUTS`. Le livreur n'emporte jamais de combustible autorisé de la hutte ; le serveur en garde 64 sur lui quand il vide son inventaire (MC `BuildingCook.buildingRequiresCertainAmountOfItem` ; *écart* : MC garde des piles entières tant qu'il en garde moins de 64, jusqu'à 127).

## 11. Le serveur (MC `JobCook`, `EntityAIWorkCook`, `AbstractEntityAIUsesFurnace`, RC § 2, § 3)

Métier `hycolony:cook` (« Serveur »), sans facteur de saturation propre. Son IA, `crafting/restaurant/CookAI`, délègue le travail aux feux de camp à `FurnaceWork` (MC `AbstractEntityAIUsesFurnace`) et le service à `CookService` ; un futur fondeur extraira la partie générique de `FurnaceWork` vers `crafting/furnace`.

### 11.1 Les feux de camp (port `CookingStations`)

Pour une position enregistrée : `isStation`, `input`, `fuel`, `output` (objet et nombre), `insertInput`, `insertFuel`, `extractOutput`, `extractFuel`, `isLit`, `light` (`ProcessingBenchBlock.setActive`), `accelerate(secondes)` (`setInputProgress`). Ne lève jamais d'exception (CLAUDE.md § 4). **[in-game]** : un feu de camp rempli et allumé par le plugin, sans fenêtre ouverte, avance seul (HF § 3.3).

### 11.2 La décision (`startWorking`, toutes les 60 ticks, dans l'ordre de MC)

1. Marche vers la hutte.
2. Liste de combustibles vide → plainte chez MC (§ 14, non portée), on continue.
3. Aucun feu de camp → on reste (plainte chez MC ; MC attend sans fin, sans autre sortie : un feu de camp posé débloque).
4. Travaux importants du serveur (§ 11.3) ; s'ils renvoient autre chose que `START_WORKING`, on y va.
5. Un feu de camp contient un combustible retiré de la liste → `RETRIEVING_USED_FUEL`.
6. Un feu de camp est à vider (éteint avec un résultat, ou résultat > 10, ou résultat sans entrée) → `RETRIEVING_END_PRODUCT`.
7. Comptages des cuisinables (cru dont le plat cuit est au menu) et des combustibles, hutte et inventaire.
8. Aucun cuisinable et `!reachedMaxToKeep` → MC se plaint (`FURNACE_USER_NO_FOOD`) ; non porté, ni `reachedMaxToKeep` qui ne sert qu'à cette plainte (§ 18).
9. Aucun combustible et pas de requête « Combustible » ouverte (pas encore livrée) → `StackList(combustibles autorisés, « Combustible », 64 × feux, 1)` ; rien si aucun combustible n'est autorisé (*écart* : une `StackList` demande au moins un objet).
10. Cuisinables ou combustible dans la hutte mais pas sur lui → les ramasser (`GATHERING_REQUIRED_MATERIALS`, le ramassage existant des métiers).
11. Premier feu de camp à remplir (entrée sans combustible, combustible sans entrée, ou vide quand on a les deux) → `FILL_UP` ; un poste dont le bloc chargé n'est plus un feu de camp est retiré ; un feu de camp chargé et éteint est rallumé au passage (*écart* : un four MC s'allume seul).

`FILL_UP` (5 ticks) : à 64 cuisinables dans l'entrée vide, 64 combustibles dans l'emplacement vide, puis allume. `RETRIEVING_END_PRODUCT` (5 ticks) : tout le résultat dans l'inventaire s'il y tient en entier (sinon il reste dans le feu), +2 XP, deux fois « action + saturation » (MC). `RETRIEVING_USED_FUEL` : reprend le combustible, de même. Accélération chaque seconde, dans tous les états : `(Adaptabilité / 10) × 2` ticks de feu en plus, convertis en `/ 20` seconde de progression (MC `accelerateFurnaces`) **[in-game]**. Vidage de l'inventaire après chaque action (`actionsUntilDump = 1`).

### 11.3 Servir (MC `checkForImportantJobs`, `serveFoodToCitizen`, `serveFoodToPlayer`, toutes les 30 ticks)

- **Joueurs** : ceux dans l'emprise qui ont `MANAGE_HUTS`. Hytale n'a pas de faim (HF § 2) : *écart*, la condition MC « faim < 10 » est remplacée par « vie < 50 % » **[à valider en jeu]** ; le serveur leur donne des plats du menu jusqu'à `niveau × 16` de valeur nutritive, message « Tenez, gouverneur ». Un joueur qui porte déjà un plat du menu, ou qui ne peut rien prendre (inventaire plein), est passé, et le serveur sert le suivant. *Écart* : un joueur déjà en file n'y est pas remis (MC l'ajoute à chaque passage).
- **Citoyens** : ceux dans l'emprise, pas serveurs, `!isWorking`, saturation ≤ 10 et `!justAte`, sans plat du menu sur eux. S'il a le meilleur choix sur lui (`hasBestOptionInInv`) → file de service ; sinon s'il est dans les contenants → le chercher ; puis `SERVE_CITIZEN`, sinon `SERVE_PLAYER`.
- **Service d'un citoyen** : sorti de l'emprise → suivant ; marche jusqu'à lui ; inventaire plein → le nourrit directement (au plus 10 bouchées, la première toujours donnée, `increaseSaturation`, sans bonus ni historique, MC) ; il a déjà un plat du menu → rien ; sinon lui donne `ceil(⌊max(1, (60 − s) / valeur)⌋ × 1,5)` du meilleur plat s'il tient en entier dans son inventaire (sinon rien, MC), +2 XP, baisse continue.
- **Clients** (MC `storeCustomer`) : chaque salle tient l'ensemble des ids de citoyens qu'elle sert (sauvé) ; un citoyen ne l'est que d'une salle.

## 12. Ports et adaptateurs (récapitulatif)

| Port | Ajout | Adaptateur |
|---|---|---|
| `ItemCatalog` | `food`, `cooked`, `isSeat`, `isFuel`, `isCookingStation` | id-map `foods`, recettes `Campfire`, `getSeats`, `ResourceType Fuel` |
| `BodyHealth` (nouveau, `kernel/port/body`, voir § 18) | `health`, `maxHealth`, `heal`, `recentlyHurt`, `setStarving` | `EntityStatMap`, système de dégâts *inspect*, `CitizenSpeed` |
| `BodySeats` (nouveau, `kernel/port/body`) | `sitOn`, `isSeatTaken`, `standUp` | `BlockMountAPI` (sièges, comme les lits) |
| `BodyAnimation` | `EAT` | `"Item"`, `"Consume"` |
| `WorldEffects` | `eating(Vec3)` | particule `Food_Eat` (id-map `eatParticle`) |
| `CookingStations` (nouveau) | § 11.1 | `ProcessingBenchBlock` |
| `PlayerInventory` | `giveFood` (service au joueur) et lecture de la vie du joueur | inventaire et `EntityStatMap` du joueur |

## 13. Persistance (schéma 8)

`ColonySerializer.SCHEMA_VERSION` 7 → 8 (skill `add-migration`), fixture du schéma 7. La migration écrit les défauts : citoyen `justAte = false`, `jobStatus = "IDLE"`, `foodHistory = []`, `happiness = []`. Lecture tolérante (clé absente = défaut, valeur inconnue = repli). Nouveaux modules sauvés par la salle à manger : `menu`, `fuels`, `customers`, `seats`. Les postes (feux de camp) le sont déjà. La migration n'a rien d'autre à faire : la salle à manger est un type nouveau.

## 14. Interactions de MC à brancher plus tard

Le système d'interactions (bulles, réponses, priorités) manque (§ 1). Les validateurs suivants sont repris dans la spec pour le jour où il existera ; aucun n'est codé maintenant (YAGNI) :

- `RAW_FOOD`, `BETTER_FOOD`, `BETTER_FOOD_CHILDREN`, `NO_RESTAURANT` (RC § 7, l. 59-71) ;
- `no.foodquality(.urgent)`, `no.fooddiversity(.urgent)` (l. 287-341) ;
- `no.<id>`, `demands.<id>` pour `homelessness`, `unemployment`, `idleatjob` (seuils 7 et 14 jours) et `no.slepttonight` (l. 263-285) ;
- `furnaceuser.nofuel`, `furnaceuser.nofood`, `bakery.nofurnace` (l. 54-57, 190-200, 221) ;
- les plaintes du serveur `POOR_MENU_INTERACTION` (salle de niveau ≥ 3 sans plat de MC au menu, `EntityAIWorkCook:350-366`) et `POOR_RESTAURANT_INTERACTION` (citoyen logé plus haut que la salle + 1, `:231-234`).

En attendant, le serveur sans combustible, sans feu de camp ou au menu vide attend sans rien dire ; *écart* documenté (`FurnaceWork`).

Autre omission : MC laisse le serveur ramasser les objets au sol (`setCanPickUpLoot(true)`) ; les citoyens de HyColony ne ramassent rien au sol.

## 15. Textes (en-US et fr-FR, `hycolony.lang`, skill `add-lang-key`)

Repris de MC (`manual_en_us.json`, RC § 7) : modificateurs (nom et description), sens (Négatif…), « Modificateurs de bonheur », « Bonheur global : {p0} », onglet Bonheur, `notnow`, salle à manger, serveur, menu (titre, options, avertissements, limite, palier, consommation, ingrédients), combustible, `cook.serve.player`, statut « Affamé » (« Hungry », MC) de l'état `EATING` dans la fenêtre du citoyen, avertissements de résidence `warning.2` à `.5` (§ RC 7 : branchés sur l'existant de SP4 § 1.3, avec les vraies conditions : fermier présent, plats de palier au menu).

## 16. Tests (TDD, cœur)

- **Catalogue et règles** : `EDIBLE`, cuisinable exclu, empoisonné exclu, `canEatLevel` aux niveaux 0 à 5, `canEat` refusé par l'atelier, `foodValue` ×2 pour un plat, `buildingLevelForFood`, facteurs de consommation.
- **Saturation** : baisse au repos (jour, nuit, endormi, maison, métier, actions plafonnées à la moitié, enfant, `foodModifier`), `justAte` remis à faux, marche tous les 25, points d'appel des métiers (constructeur, fermier, livreur, artisan, serveur).
- **Soins** : trois paliers, blessé récemment, malade (absent), ralentissement à 0 et levé ensuite.
- **Décision** : chaque condition de `shouldEat`, ordre sommeil > faim > pluie > travail, métier non interruptible, serveur 1/200.
- **Repas** : chaque état de `EatAI` (inventaire, hutte, refus de la hutte, salle avec et sans serveur, place, attente 2 min, se servir, quantités, plusieurs bouchées, historique noté comme MC, `justAte`), meilleur aliment (scores, arrêts anticipés, retour vers la salle).
- **Nourrir à la main** : délai, empoisonné, consommation, refus d'un non-aliment.
- **Huttes** : `keepFood` par niveau, salle à manger exclue, `canEat` du constructeur, du fermier et du livreur.
- **Bonheur** : chaque fonction, chaque sorte de modificateur (paliers, passage, expiration, remplacement), moyenne pondérée, tout-neutre = 10, plafond, cache, journée seulement avec un joueur présent, `slepttonight` remis au lit, `greatfood`, `damage` (hors feu), bonheur global, plafond des nouveaux citoyens.
- **Statut de travail** : outil manquant, fermier sans champ, persistance.
- **Salle à manger** : menu (limite, refus, requêtes, cru, annulation, garde), combustible, clients uniques, places (3 tirages, prises), enregistrement d'un feu de camp et d'un siège posés à la main dans l'emprise et pas ailleurs.
- **Serveur** : chaque étape de la décision dans l'ordre, remplissage, extraction (XP, actions), combustible retiré, accélération, `reachedMaxToKeep`, service d'un citoyen (quantité, inventaire plein, déjà servi), service d'un joueur.
- **Vues** : barre de bonheur (smileys et demis), onglet (icônes et sens), hôtel de ville (moyenne, arrondi), menu (tri, filtre, couleurs, limite).
- **Persistance** : migration de la fixture 7, aller-retour de chaque nouvelle clé, lecture d'une sauvegarde sans ces clés.

## 17. À vérifier en jeu (`docs/TESTING.md`)

- La saturation baisse le jour, pas la nuit ; un citoyen à 2,5 part manger ; il mange ce qu'il porte, puis ce que sa hutte contient ; la touche *Use* avec un aliment le nourrit.
- Un citoyen à jeun est ralenti.
- Animation et particule de repas.
- L'onglet Bonheur, la barre de smileys, la page Citoyens de l'hôtel de ville.
- La salle à manger : feu de camp posé à la main enregistré, menu, combustible, le serveur remplit et allume le feu, vide le résultat, sert les citoyens assis, sert un joueur blessé.
- Un citoyen s'assoit sur une chaise et se relève.

## 18. Décisions prises à la réalisation

- **Trois ports pour les corps.** Ajouter la vie et les sièges à `CitizenBodies` lui donnait trop de méthodes (PMD) et `kernel/port` dépassait 15 fichiers : ils sont deux ports à part, `kernel/port/body/BodyHealth` et `BodySeats`, passés à `ColonyContext` après `bodies` (les tests passent le même `FakeBodies` trois fois). Côté plugin, `HytaleBodyHealth` et `HytaleBodySeats` (`npc/body`) ne lèvent jamais d'exception (§ 4 de CLAUDE.md) et sont créés par `HytaleCitizenBodies`, avec qui ils partagent les stats de vie (`BodyVitals`) et les vitesses (`BodySpeeds` : le ralentissement de la faim multiplie la vitesse du métier). Les aides du corps (`BodyGestures`, `BodySpeeds`, `BodyVitals`, `CitizenSeats`, `CitizenSpeed`) quittent `npc` (plein) pour `npc/body`.
- **Paquets.** `citizen/food` est plein : le port de la salle à manger et sa recherche vont dans `citizen/food/hall` (`DiningHall`, `DiningHalls`).
- **L'IA du repas en trois classes.** `EatAI` garde la machine à états, la hutte de travail et les bouchées ; `DiningVisit` porte la partie salle à manger de `EntityAIEatTask` (recherche, trajet, client, place, attente, se servir, `reset`) ; `EatingTable` porte la nourriture choisie, les bouchées et le compteur `waitingTicks` partagé par les attentes et les bouchées, comme MC. La bouchée n'incrémente ce compteur qu'après le contrôle `canEat`, dans l'ordre de MC.
- **La marche** est comptée par `CitizenData.moved` toutes les 60 ticks (`tickData`), sur la distance horizontale depuis la dernière position ; un saut de plus de 32 blocs est une téléportation, ignorée. *Écart* : MC compte chaque pas (`walkDist`).
- **La blessure** passe par `HappinessEvents.hurt(colony, body)` (le citoyen est retrouvé par `CitizenManager.citizenOf`), appelé par `CitizenHurtSystem` après `HytaleBodyHealth.hurt`.
- **Fenêtre du citoyen.** La barre de smileys prend la place de MC (55, 63) ×2 ; les deux lignes « Métier / activité » gardées par l'utilisateur descendent en y = 164 et 180, les compétences commencent en y = 196.
- **Configuration.** Le modèle `plugin/src/main/resources/config.json` est protégé par le garde-fou : `FoodModifier` (section `Gameplay`, défaut 1.0, bornes 0.1 à 100) prend son défaut par le codec quand la clé manque. L'utilisateur l'ajoute au modèle et à son `config.json`.
- **Citoyens invulnérables.** Le rôle du citoyen est `Invulnerable` (spec de la mort des citoyens à venir) : les soins et la blessure sont en place mais ne se voient en jeu qu'avec un citoyen blessé.
- **Corrections après relecture.**
  - La décision de manger regarde la dernière décision (MC `lastState == EATING`, `CitizenAI.decidedEating`) : un repas fini sans `justAte` (un serveur sans nourriture) repart aussitôt. Sortir d'EATING mène droit au travail dans la même décision, comme MC.
  - Le statut de travail repasse à IDLE à chaque nouveau métier (`CitizenData.setJob`) et à chaque corps lié d'un citoyen qui a un métier (`CitizenManager.bind`), comme MC `onJobChanged` → `initEntityValues` ; un licenciement le laisse tel quel, comme MC.
  - « Blessé récemment » ne compte qu'une attaque d'une entité ou de son projectile (MC `getLastHurtByMob`), sur l'horloge du cœur (20 ticks/s, le monde Hytale en a 30). Toute blessure hors feu et foudre rend malheureux.
  - *Écarts* documentés dans le code : la faim et les soins tournent dans la colonie (un tick sauté toutes les 100, ~1 %) ; `timeOutWalking` repart à 0 à chaque repas (MC ne le remet jamais à zéro) ; `FoodTransfer` prend dans plusieurs contenants et accepte un transfert partiel ; sans salle, `getFoodYourself` revient à `WAIT_FOR_FOOD` comme MC.
- **Étape 3, la salle à manger.**
  - Paquets : `crafting/furnace` (catalogue et port des postes de cuisson, `FurnaceUserModule`, `FuelListModule`, sa vue, la requête de combustible `FuelRequests`) et `crafting/restaurant` (la hutte, le métier, l'IA du serveur, le menu, la salle) ; les actions des fenêtres dans `app/restaurant/RestaurantActions`, exposées par `HutWindowActions.restaurant()`. Les ports de cuisson forment un neuvième composant de `GamePorts` (`CookingSetup`).
  - *Écart* : aucun plan Hytale n'a de four ni de tag `sit` ; un feu de camp et un siège (chaise, tabouret, banc) posés **par un joueur** dans l'emprise s'enregistrent (`BuildingEventsModule.onBlockPlacedByPlayer`, appelé par `ProtectionSystems.Place`), comme les blocs du plan. Tous les sièges sont « dedans » (le `sit_out` de MC, évité sous la pluie, n'a pas d'équivalent).
  - *Écart* : le feu de camp de Hytale doit être allumé (`setActive`, refusé sans combustible) ; l'accélération du serveur n'avance que la cuisson, pas la combustion.
  - *Écart* : un joueur est servi sous 50 % de sa vie (Hytale n'a pas de faim), avec la permission `MANAGE_HUTS` ; ce qu'il ne peut pas prendre reste au serveur (MC l'échange contre un objet non comestible).
  - *Écart* : les plaintes du serveur (pas de combustible choisi, pas de four, menu vide) sont des interactions, absentes de HyColony : il attend. `requestSmeltable` (qui ne sert qu'à ces plaintes) et `reachedMaxToKeep` ne sont pas portés.
  - *Écart* : le menu demande ses plats par une `StackRequest` de minimum 1 que la salle ne résout pas elle-même (HyColony n'a pas de `MinimumStack`). Les plats de même palier et de même valeur nutritive se trient par id (MC trie par nom affiché, connu du seul client).
  - *Écart* : les deux pages de MC (menu, plats possibles) s'affichent l'une après l'autre dans l'onglet, avec un bouton pour passer de l'une à l'autre.
  - *Écarts* relevés à la relecture et documentés dans le code : un feu de camp chargé, éteint mais plein, est rallumé au passage ; un poste ou un siège n'est oublié que si son bloc chargé n'en est plus un ; retirer un plat annule sa requête (MC ne la trouve jamais, bug corrigé) ; le serveur garde exactement 64 combustibles (MC des piles entières, jusqu'à 127) ; aucune requête de combustible quand aucun n'est autorisé ; le serveur ramasse dans les contenants de la salle comme un seul stock.
  - Corrigé à la relecture : une requête de la salle livrée mais pas encore reçue ne l'empêche plus de redemander (MC la sort des requêtes ouvertes dès sa livraison) ; un joueur à l'inventaire plein est passé sans vider la file ; un plat n'est donné à un citoyen que s'il tient en entier, et un produit retiré du feu seulement s'il tient en entier dans l'inventaire du serveur ; la première bouchée est toujours donnée ; un client inconnu ne compte pas dans la consommation, affichée en décimal comme MC ; l'infobulle « Limite atteinte » de MC sur `<<`.
  - Les plans de la salle reprennent ceux de la résidence dans les deux styles (Kweebec, Outlander), faute de plans propres. Le module de statistiques de MC n'est pas porté (HyColony n'a pas encore de statistiques).
- **Infobulles des aliments** (ajout demandé par l'utilisateur le 2026-10-01, recherche `docs/research/food-tooltips.md`). *Écart* : MC n'affiche pas la valeur d'un aliment, la barre de faim de Minecraft la montre ; Hytale n'en a pas. Chaque aliment de la table garde sa description vanilla (`<msg key>`), puis un trait imitant le séparateur du jeu (18 `―`, le texte n'a pas de balise de séparateur), puis, en gris, « Nourrit un citoyen : 12 (2 gigots) », « Mangé jusqu'au niveau de résidence : n » (texte MC du menu) et le texte de palier de MC (`core.item.food.tooltip.tier.<n>`, le palier 0 prend « Les citoyens préfèrent une cuisine plus raffinée ! ») ; un aliment qui se cuit au feu de camp dit en rouge « trop cru » (MC `wrongfood`), un aliment empoisonné « Empoisonné ». Les lignes de MC qui dépendent du citoyen regardé (« ne convient pas », « pas au menu ») ne peuvent pas entrer dans un texte fixe. Mise en œuvre : un patch Hytalor par aliment (`Server/Patch/HyColony/Food/<id>.json`, qui ne change que `TranslationProperties.Description`) et `hycolony_food.lang` en en-US et fr-FR, écrits par `tools/food/generate.py` depuis l'id-map et les assets ; la tâche `:plugin:checkFoodTooltips` fait échouer le build quand la table change sans eux. Hytalor est une dépendance **optionnelle** (`manifest_opt_dependencies`) : sans lui, HyColony démarre et les infobulles restent vanilla.
