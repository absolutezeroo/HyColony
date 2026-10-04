# HyColony : nourriture et tags de métier ouverts aux autres mods

Date : 2026-10-04. Conception validée avec l'utilisateur dans la conversation du 2026-10-03/04. Premier des quatre sous-projets convenus : (1) ce sous-projet, (2) le Chef (`BuildingKitchen`), (3) le mod de nourriture de l'utilisateur (6e mod, jouable sans HyColony), (4) le boulanger.

## 1. Objectif

HyColony ne doit connaître aucun banc ni aucun plat par son nom quand une règle de MineColonies porte sur des objets. Un autre mod (celui de nourriture de l'utilisateur, ou n'importe quel mod Hytale) doit pouvoir, **sans dépendance de code** :

- faire manger ses aliments aux citoyens, avec leur valeur nutritive et leur palier ;
- faire apprendre ses produits aux métiers de HyColony, comme les tags de métier de MC (`cook_product`…) ;
- cuire ses aliments sur ses propres bancs de cuisson.

Critères de réussite : un plat d'un mod tiers, sans aucun fichier HyColony, nourrit un citoyen ; avec un fichier HyColony, il prend la valeur et le palier voulus ; un banc `Processing` d'un mod qui cuit un aliment sert de poste de cuisson à la salle à manger ; nos 50 aliments et nos règles de fabrication restent inchangés en jeu.

## 2. Décisions de l'utilisateur

- Le système de nourriture de l'utilisateur sera un **mod à part** ; HyColony le lit par ses données.
- Nos propres aliments passent **dans le type d'asset**, une seule source pour HyColony et les autres mods. Cela demande de modifier le § 7 de CLAUDE.md (garde-fou, § 8 ci-dessous).
- **Un banc de fabrication posé à la main n'entre pas dans une hutte**, comme dans MC (§ 3) : un banc d'un mod entre dans une hutte par un plan qui l'utilise. Les postes de cuisson gardent l'écart de SP4b (§ 3, § 7).

## 3. MineColonies

- **Aliments.** MC lit la nourriture de l'objet de Minecraft (`FoodProperties` : nutrition, saturation) et son palier (`IMinecoloniesFoodItem.getTier`, `MC/api/items/IMinecoloniesFoodItem.java:12`). Tout objet d'un autre mod qui a des `FoodProperties` nourrit un citoyen (`FoodUtils.EDIBLE`).
- **Tags de métier.** Ce qu'un artisan peut apprendre suit des tags d'objets que les packs de données complètent : `<artisan>_product`, `<artisan>_product_excluded`, `<artisan>_ingredient`, `<artisan>_ingredient_excluded`, et `reduceable_ingredient` / `reduceable_product_excluded` pour l'amélioration (`sources/minecolonies/src/datagen/generated/minecolonies/data/minecolonies/tags/items/`). Le nom `<artisan>` est une constante du module de fabrication (`MC/api/util/constant/TagConstants.java:58-72`), pas l'id du métier : le chef (`chef`) lit `cook_*` (`BuildingKitchen.java:133`), le planteur lit `plantation_*`. Un tag de Minecraft s'additionne entre packs et peut inclure un autre tag (`#cook_product` dans `baker_product_excluded`, `DefaultItemTagsProvider.java:211-214`). La règle : un produit exclu est refusé, un produit inclus est accepté, sinon la règle par défaut du module décide (`CraftingUtils.getProductValidatorBasedOnTags`, `isRecipeCompatibleBasedOnTags`). Le tag `excluded_food` retire un objet des aliments (`ItemStackUtils.java:151`).
- **Bancs d'une hutte.** `registerBlockPosition` n'est appelé que par la pose d'un plan (`MC/core/entity/ai/workers/util/BuildingStructureHandler.java:199`, `MC/api/util/CreativeBuildingStructureHandler.java:127`) ; `EventHandler.onBlockBreak` (`MC/core/event/EventHandler.java:551`) ne traite que les générateurs de monstres. Un banc posé à la main n'est jamais enregistré, un banc cassé est oublié quand l'IA le trouve absent. HyColony fait de même pour les bancs de fabrication (`RegisteredBlocks`, `removeWorkstation`). Seule exception, déjà là : un poste de cuisson posé par un joueur dans l'emprise d'une salle à manger s'y enregistre (`FurnaceUserModule.java:17-21`, écart de SP4b) ; avec ce sous-projet, il vaut pour tout poste de cuisson (§ 7).

## 4. Hytale (pre.5, vérifié)

- **Type d'asset d'un plugin** : `PluginBase.getAssetRegistry()` (`HY/server/core/plugin/PluginBase.java:211`) enregistre un `AssetStore` et le retire à l'arrêt (`HY/server/core/plugin/registry/AssetRegistry.java:18`) ; le magasin se construit par `HytaleAssetStore.builder` (`HY/server/core/asset/HytaleAssetStore.java:175`). Ses fichiers sont lus dans **chaque pack**, sous `<pack>/Server/<chemin>` (`HY/assetstore/AssetStore.java:755`) ; un pack chargé après un autre remplace un fichier de même nom. Exemple public : `FoodValue` de HytaleHungerMod (AGPL, idée seulement).
- **Aliments** : Hytale n'a ni faim ni valeur nutritive (`docs/research/sp4b-hytale-food.md` § 2). Il marque ses aliments par la catégorie `Items.Foods` (héritée de `Template_Food`, `zip:Server/Item/Items/Food/Template_Food.json`) et `Consumable: true`. Dans les assets pre.5 : 56 objets `Items.Foods` : nos 44 aliments de la table qui ne sont pas des champignons, 3 gabarits (`Template_Food`, `Template_Fruit`, `Template_Crop_Item`, qualité `Template`, cachée de la recherche : `ItemQuality.isHiddenFromSearch`, `HY/server/core/asset/type/item/config/ItemQuality.java:260`), 4 variantes et 5 ingrédients non consommables (`Ingredient_Dough`, `_Fishbone`, `_Flour`, `_Salt`, `_Spices`). Les 4 variantes (`Food_Fish_Raw_Uncommon/_Rare/_Epic/_Legendary`, `Variant: true`, `Item.isVariant`, `HY/server/core/asset/type/item/config/Item.java:1009`) ne sont pas de vrais aliments : elles portent une recette du plan de cuisine dont le résultat est `Food_Fish_Raw` (`zip:Server/Item/Items/Food/Fish/Food_Fish_Raw_Uncommon.json`, `PrimaryOutput`), et Hytale les cache de la liste des objets. Les 6 `Plant_Crop_Mushroom_Glowing_*`, toxiques, sont consommables sans être `Items.Foods`.
- **Qualité** : chaque objet a une `Quality` (`Common`, `Uncommon`, `Rare`, `Epic`, `Legendary`…, `zip:Server/Item/Qualities/`). Nos aliments : Common pour le cru et le feu de camp, Uncommon pour le pain, le fromage, le pop-corn, les brochettes et les salades, Rare pour les tartes et la salade César.
- **Bancs de cuisson** : un banc `Processing` déclare ses recettes et son emplacement de combustible par type de ressource (`Bench_Campfire.json:65-69`, `"Fuel": [{"ResourceTypeId": "Fuel"}]`). Les bancs `Processing` de pre.5 sont le feu de camp et le four (avec combustible), le banc de recyclage et la tannerie (sans). Le four ne cuit aucun aliment. Le banc de recyclage, lui, a une recette dont la sortie est un aliment : `zip:Server/Item/Recipes/Salvage/Salvage_Weapon_Bomb_Popberry.json` rend des baies rouges (les recettes autonomes de `Server/Item/Recipes/` s'ajoutent à celles des fichiers d'objets).

## 5. Partie 1 : les aliments, type d'asset de HyColony

### 5.1 Le fichier

`Server/HyColony/Foods/<id d'objet>.json`, un fichier par aliment, nommé par l'id de l'objet Hytale :

```json
{ "Nutrition": 8, "Tier": 0, "Poisonous": false }
```

- `Nutrition` : entier ≥ 1, `Tier` : 0 à 3 (0 = aliment ordinaire, 1 à 3 = plat « de MC »), `Poisonous` : défaut `false`. Mêmes bornes que la table actuelle (`FoodIds`).
- Un fichier hors bornes est ignoré et journalisé en WARNING une fois ; un id d'objet inconnu des assets est ignoré et journalisé une fois (comportement actuel de `HytaleFoods`).
- Un pack chargé après HyColony peut remplacer notre valeur en fournissant le même nom de fichier (règle des assets de Hytale).

### 5.2 Nos fichiers

Les 50 entrées de `id-map.json` (`food.foods`) deviennent 50 fichiers, valeurs inchangées (spec SP4b § 2.2). La section `food.foods` et le champ `food.cookingBench` (partie 3) quittent l'id-map ; `food.defaultFuels` et `food.eatParticle` y restent ; `food.foodCategory` et `food.qualityRanks` (§ 5.3) y entrent.

### 5.3 Valeur par défaut (règle du cœur)

Un objet sans fichier est un aliment s'il est `Consumable`, n'est pas une variante (`isVariant`), n'a pas une qualité cachée de la recherche (les gabarits, `ItemQuality.isHiddenFromSearch`) et est de la catégorie d'aliments de l'id-map (`food.foodCategory` : `Items.Foods`). Sa valeur se déduit de sa qualité Hytale, par une règle **du cœur** (une décision de jeu ne vit pas dans un plugin), l'énumération `kernel/item/FoodQuality` :

| Rang du cœur | Qualités Hytale (id-map `food.qualityRanks`) | Nutrition | Palier | D'où vient le chiffre |
|---|---|---|---|---|
| `COMMON` | Common, et toute qualité absente de l'id-map | 3 | 0 | médiane des 30 aliments Common de notre table |
| `UNCOMMON` | Uncommon | 8 | 0 | médiane des 9 aliments Uncommon (nutrition 6 à 9) |
| `RARE` | Rare, Epic, Legendary | 12 | 0 | médiane des 4 aliments Rare |

- **Palier 0** pour tous (revu après la relecture de fidélité) : chez MC, l'aliment d'un autre mod n'est jamais un plat de MC (`FoodUtils.getFoodTier`, `FoodUtils.java:124-137` : palier 0, ou 1 si nutrition ≥ 12 et saturation ≥ 0,8, saturation non portée) et ne reçoit pas le bonus ×2. Un mod qui veut un plat donne son palier dans un fichier.
- `Poisonous` : faux. Hytale n'a pas de marque « toxique » (ses champignons toxiques appliquent un effet `Poison` quand on les mange) ; nos champignons et ceux d'un mod se marquent par un fichier.
- Un fichier hors bornes est écarté : son objet prend alors la valeur de sa qualité, comme un aliment sans fichier, et le selftest le liste.
- MC `excluded_food` : un objet du tag `excluded_food` (§ 6) n'est jamais un aliment, avec ou sans fichier.
- Écart : `Deviation from MC (Hytale world): MC reads an item's FoodProperties nutrition → a Hytale food without a HyColony file takes the median nutrition of our table's foods of its Quality.`
- Les noms de qualité et la catégorie sont des ids d'assets Hytale : ils vivent dans l'id-map (§ 7), le cœur ne connaît que les trois rangs et leurs valeurs.

### 5.4 Le port et l'adaptateur

- Le port `FoodCatalog` ne change pas : le cœur ne voit aucune différence.
- `HytaleFoods` lit le magasin d'assets au premier usage (les assets sont chargés), puis, pour un objet absent, la valeur de `FoodQuality` de son rang (qualité lue par `food.qualityRanks`) si l'objet est un aliment Hytale. Le cache existant reste ; un rechargement d'assets demande toujours un redémarrage (comportement actuel).
- `plugin/food/FoodValueAsset` : la classe d'asset (`JsonAssetWithMap<String, DefaultAssetMap<String, FoodValueAsset>>`, codec `Nutrition`, `Tier`, `Poisonous`, clé = nom du fichier), enregistrée dans `setup()` par `getAssetRegistry()`.
- `IdMap.check` ne vérifie plus la table des aliments ; `FoodSelfTest` (nouveau, `/hycolony selftest`) affiche : le nombre de fichiers pris sur le nombre de fichiers lus, le nombre d'aliments par défaut, et chaque fichier écarté (objet inconnu, hors bornes ou `excluded_food`), d'après l'ensemble des fichiers que `FoodTable` a pris (`HytaleFoods.fromFiles`).

### 5.5 Les infobulles

- `tools/food/generate.py` lit les fichiers `Server/HyColony/Foods/*.json` au lieu de `food.foods`, et décide du « trop cru » par la partie 3 (l'objet est l'entrée d'une recette de cuisson d'un banc qui cuit un aliment) au lieu de `food.cookingBench`.
- `checkFoodTooltips` (`plugin/build.gradle.kts`) compare les patchs aux fichiers d'aliments au lieu de l'id-map ; son en-tête `# cookingBench=…` disparaît.
- Un aliment sans fichier (valeur par défaut) n'a pas d'infobulle HyColony : les patchs sont écrits au build, pour nos fichiers seulement. Un mod tiers qui veut l'infobulle ajoute son fichier (et son patch).

## 6. Partie 2 : les tags de métier, type d'asset de HyColony

### 6.1 Le fichier

`Server/HyColony/JobTags/<nom libre>.json` :

```json
{ "Tag": "farmer_product", "Values": ["Plant_Seeds_Wheat", "res:Meats"] }
```

- `Tag` : nom d'un tag de MC, `<artisan>_product`, `<artisan>_product_excluded`, `reduceable_ingredient`, `reduceable_product_excluded` ou `excluded_food`. `<artisan>` est le nom de MC du module de fabrication (`TagConstants.CRAFTING_*`), une constante du type de hutte (`CraftingModule.crafter`, `FarmerHut.CRAFTER` = `farmer`) : le futur chef lira `cook_*`, comme chez MC.
- `Values` : ids d'objets, `res:<type de ressource>` (tous les objets de ce type ; `res:` est la convention de HyColony pour le `ResourceTypeId` d'une recette) ou `#<tag>` (les objets d'un autre tag, comme MC ; des tags qui s'incluent l'un l'autre s'arrêtent à la boucle).
- **Tous les fichiers d'un même `Tag` s'additionnent**, comme les tags de MC : le nom du fichier ne sert qu'à l'unicité (un mod nomme les siens `MonMod_Chef_Product.json`). Un fichier qui porte le nom d'un des nôtres le remplace (règle des assets) : c'est le moyen de retirer une de nos valeurs.
- Un `Tag` inconnu, une valeur inconnue des assets : ignorés, journalisés une fois.
- Les tags `_ingredient` / `_ingredient_excluded` de MC ne sont pas lus : HyColony ne les a jamais portés (SP3b-1), écart marqué dans `CraftingRules.allows` ; un fichier de ce tag est écarté comme tag inconnu. Ils rejoindront ce type d'asset le jour où ils le seront.
- Les listes `reduceable_*` de MC (une trentaine d'objets) ne sont pas encore traduites en objets Hytale : c'est l'entrée B-12 de l'audit du monde (`docs/research/audit-monde-hytale.md`, domaine « Artisanat et bancs »), qui pourra maintenant les écrire en fichiers `JobTags` avec `res:`.

### 6.2 Ce qui change dans `crafting.json`

- `includeItems` et `excludeItems` de chaque métier, et la section `reduceable`, **quittent** `crafting.json` : ce sont les tags de MC, ils vivent désormais dans `JobTags`. Ils sont vides aujourd'hui : aucun fichier `JobTags` n'est créé.
- `allow` (bancs et catégories, la règle par défaut de chaque métier, écart déjà documenté de SP3b-1) et `custom` (recettes données par niveau) restent dans `crafting.json` : ce sont des réglages du système, pas des tags d'objets. Le Chef (sous-projet 2) ajoutera sa propre règle par défaut (« le résultat est un aliment », MC `FoodUtils.EDIBLE`).
- La lecture reste tolérante (CLAUDE.md § 5) : une ancienne clé `includeItems`, `excludeItems` ou `reduceable` est ignorée et journalisée une fois.

### 6.3 Le cœur

- `crafting/recipe/JobTags` (record) : `Map<String, Set<ItemKey>>`, tag → objets ; `merge` fusionne les fichiers par tag puis ajoute les tags inclus ; `products(crafter)`, `excludedProducts(crafter)`, `get(tag)`.
- `CraftingRules` reçoit `JobTags` au lieu de lire ces listes dans le JSON ; `allows(jobId, crafter, recipe)` garde l'ordre de MC : exclu → refusé, inclus → accepté, sinon `allow` du métier. `isReduceable` et `isExcludedFromReduction` lisent les tags. `CraftingModule` porte le nom de son artisan, que `RecipeCompatibility` passe à `allows`.
- L'adaptateur `plugin/crafting/HytaleJobTags` lit le magasin `JobTags`, développe les `res:` par le catalogue d'objets, sépare les `#tag` et passe les fichiers au cœur (`JobTags.merge`). `WorldPorts` le lit une fois par monde, pour les règles de fabrication et pour `excluded_food` (`HytaleFoods`). `crafting.json` est lu au `setup()`, avant le chargement des assets ; les tags sont donc ajoutés à la création des ports d'un monde, assets chargés (`CraftingRules.withTags`, dans `WorldPorts`).

## 7. Partie 3 : les postes de cuisson

- Un **poste de cuisson** est un bloc dont le banc est de type `Processing`, a un emplacement de combustible (il brûle, comme le four de MC) et a au moins une recette dont le résultat principal est un aliment (partie 1). Le feu de camp l'est ; le four ne l'est pas (aucun aliment) ; le banc de recyclage ne l'est pas (il ne brûle rien, même s'il tire des baies d'une bombe) ; le four d'un mod qui cuit un aliment l'est. Seules les recettes de ces bancs comptent pour la cuisson.
- Ses **combustibles** sont les objets des types de ressource de son emplacement de combustible (`Fuel` du banc), au lieu du seul type `Fuel` du feu de camp.
- Ce qu'un objet **devient en cuisant** (`cookedFrom`, `rawFor`) se lit sur les recettes de tous les postes de cuisson, au lieu du seul feu de camp ; à entrée égale, la première recette par id de banc puis par id de recette gagne (ordre stable).
- `food.cookingBench` quitte l'id-map ; `HytaleCookingCatalog` et `HytaleFoods` perdent leur banc fixe.
- Le port `CookingCatalog` ne change pas. Les combustibles par défaut (`food.defaultFuels`) restent ceux de l'id-map.
- Écart : `Deviation from MC (Hytale world): MC FurnaceUserModule.java:136 takes any FurnaceBlock → a processing bench with a fuel slot that cooks a food (Bench_Campfire.json Fuel)`, dans `CookingBenches`. La règle de SP4b (« le feu de camp ») devient « tout banc qui brûle et cuit un aliment ».
- Un poste posé par un joueur dans l'emprise d'une salle à manger s'y enregistre, comme le feu de camp depuis SP4b (`FurnaceUserModule.java:17-21`, écart de SP4b) : cela vaut maintenant pour tout poste de cuisson.
- `tools/food/generate.py` applique la même règle (bancs avec `BlockType.Bench.Fuel`) et lit aussi les recettes autonomes de `Server/Item/Recipes/`.

## 8. Garde-fou : § 7 de CLAUDE.md

Le § 7 dit : « Les identifiants d'assets Hytale ne vivent que dans l'id-map de chaque mod ». Texte proposé, à poser dans une session lancée avec `HYCOLONY_GUARDRAILS_UNLOCKED=1`, avec l'accord de l'utilisateur :

> Les identifiants d'assets Hytale ne vivent que dans l'id-map de chaque mod (`hycolony/id-map.json`, …), sauf dans les types d'assets que HyColony ouvre aux autres mods (`Server/HyColony/Foods/`, `Server/HyColony/JobTags/`), où un fichier nomme l'objet qu'il décrit. Les plans de bâtiments sont dans `hycolony/styles.json`.

L'agent `ui-lang-checker` (qui vérifie la règle des id-maps) et la skill `hytale-api` (qui la rappelle) reçoivent la même exception ; `AGENTS.md` ne la cite pas. Cette étape vient en premier dans le plan : sans elle, la relecture refuserait les fichiers d'aliments.

## 9. Écarts

| Écart | Marquage |
|---|---|
| Valeur d'un aliment sans fichier, par sa qualité Hytale, palier 0 (§ 5.3) | `Deviation from MC (Hytale world)` dans `FoodQuality` |
| Valeurs de nos aliments : fichiers de HyColony, Hytale n'ayant ni faim ni valeur nutritive | `Deviation from MC (Hytale world)` dans `HytaleFoods` |
| Tags de MC lus dans des assets Hytale au lieu des tags d'objets de Minecraft | `Deviation from MC (Hytale world)` dans `HytaleJobTags` et `JobTags` |
| Tags d'ingrédients de MC non lus | `Deviation from MC` dans `CraftingRules.allows` |
| Poste de cuisson : banc `Processing` qui brûle et cuit un aliment, au lieu du four de MC (§ 7) | `Deviation from MC (Hytale world)` dans `CookingBenches` |

## 10. Architecture

- **Cœur** : `kernel/item/FoodQuality` (nouveau) ; `crafting/recipe/JobTags` (nouveau) ; `CraftingRules`, `CraftingRulesJson` (listes retirées, clés anciennes ignorées). Aucun changement de sauvegarde : aucune migration.
- **Plugin** : `food/FoodValueAsset`, `food/HytaleFoods`, `food/HytaleCookingCatalog`, `food/FoodIds` (ne garde que combustibles et particule), `crafting/JobTagAsset`, `crafting/HytaleJobTags` (nouveaux ou modifiés) ; `IdMap` ; `subplugin/SubPlugins` ; `command/FoodSelfTest` (nouveau) ; enregistrement des deux magasins dans `setup()`.
- **Ressources** : `Server/HyColony/Foods/*.json` (50 fichiers) ; `hycolony/id-map.json` ; `hycolony/crafting.json`.
- **Outils** : `tools/food/generate.py` ; `checkFoodTooltips`.
- **Packs internes** (`hycolony/packs.json`, aucun aujourd'hui) : un pack ajoute ses aliments et ses tags comme fichiers d'assets de son dossier ; un pack désactivé n'enregistre pas ses assets, donc ses aliments ne comptent pas.
- Chaque paquet reste sous 15 fichiers (`plugin/food` en a 3 aujourd'hui ; `plugin/crafting` est à vérifier au plan).

## 11. Tests

- **Cœur (TDD)** :
  - `FoodQuality` : la nutrition de chaque rang, palier 0 ;
  - `JobTags` et `CraftingRules` : un produit exclu par un tag est refusé même si un autre tag l'inclut ; un produit inclus est accepté sans banc autorisé ; sans tag, `allow` décide ; deux tags du même nom fusionnés ; un tag qui en inclut un autre, une boucle d'inclusions, une inclusion inconnue ; les tags sont ceux de l'artisan, pas du métier ; `reduceable` et `excluded_food` lus dans les tags ;
  - `CraftingRulesJson` : une ancienne clé `includeItems` est ignorée avec un avertissement ;
  - les tests existants de `CraftingRules` passent par `JobTags` au lieu des listes JSON.
- **Plugin** (`/hycolony selftest` et `docs/TESTING.md`, à partir du point 382) :
  - la ligne aliments du selftest : 50 fichiers pris sur 50, 0 aliment par défaut (ni gabarit, ni variante) ;
  - un repas : un citoyen mange un aliment à fichier, à la même valeur qu'avant ; l'infobulle d'un aliment est inchangée ; la liste « Plats possibles » de la salle à manger ne montre ni variante (`Food_Fish_Raw_Rare`…) ni gabarit (`Template_Food`…) ;
  - un aliment sans fichier (`Food_Bread.json` retiré un instant : en dev, les assets sont lus dans `src/main/resources`, `docs/research/plugin-b-api.md`) nourrit un citoyen à la valeur de sa qualité (8, palier 0) ;
  - la salle à manger cuit toujours au feu de camp ; ni le four ni le banc de recyclage ne sont pris pour un poste de cuisson.

## 12. À vérifier pendant le plan (sources décompilées)

- Le moment de l'enregistrement : **vérifié**, les plugins de Hytale enregistrent leurs types d'assets dans `setup()` par `getAssetRegistry().register(...)`, avec `.loadsAfter(Item.class)` (`HY/builtin/adventure/shop/ShopPlugin.java:29-41`) ; reste à voir en jeu que nos 50 fichiers sont lus (selftest).
- Que l'enregistrement et la lecture ne se fassent jamais sur le thread du monde avec le verrou d'assets (`docs/research/plugin-b-api.md`, piège `loadAssets`).
- L'ordre des packs quand deux packs fournissent le même nom de fichier.
- La lecture de la catégorie `Items.Foods`, de `Consumable` et de `Quality` d'un `Item` (héritage par `Parent`).
- Le type de ressource de l'emplacement de combustible d'un banc `Processing` (`ProcessingBench` côté serveur).

## 13. Hors portée

- Les bancs de fabrication posés à la main (§ 3) : comme MC, non enregistrés.
- Les tags `_ingredient` de MC.
- Les listes `reduceable_*` traduites en objets Hytale : audit B-12.
- Une infobulle pour l'aliment d'un mod : `generate.py` n'écrit les patchs que de nos fichiers ; un mod qui la veut fournit son propre patch.
- Le Chef, sa règle « le résultat est un aliment », sa cuisson sur requête : sous-projet 2.
- Le mod de nourriture de l'utilisateur : sous-projet 3, sa propre conception.
