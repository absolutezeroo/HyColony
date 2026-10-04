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
- **Un banc posé à la main n'entre pas dans une hutte**, comme dans MC (§ 3) : un banc d'un mod entre dans une hutte par un plan qui l'utilise.

## 3. MineColonies

- **Aliments.** MC lit la nourriture de l'objet de Minecraft (`FoodProperties` : nutrition, saturation) et son palier (`IMinecoloniesFoodItem.getTier`, `MC/api/items/IMinecoloniesFoodItem.java:12`). Tout objet d'un autre mod qui a des `FoodProperties` nourrit un citoyen (`FoodUtils.EDIBLE`).
- **Tags de métier.** Ce qu'un artisan peut apprendre suit des tags d'objets que les packs de données complètent : `<métier>_product`, `<métier>_product_excluded`, `<métier>_ingredient`, `<métier>_ingredient_excluded`, et `reduceable_ingredient` / `reduceable_product_excluded` pour l'amélioration (`sources/minecolonies/src/datagen/generated/minecolonies/data/minecolonies/tags/items/`). Un tag de Minecraft s'additionne entre packs. La règle : un produit exclu est refusé, un produit inclus est accepté, sinon la règle par défaut du module décide (`CraftingUtils.getProductValidatorBasedOnTags`, `isRecipeCompatibleBasedOnTags`).
- **Bancs d'une hutte.** `registerBlockPosition` n'est appelé que par la pose d'un plan (`MC/core/entity/ai/workers/util/BuildingStructureHandler.java:199`, `MC/api/util/CreativeBuildingStructureHandler.java:127`) ; `EventHandler.onBlockBreak` (`MC/core/event/EventHandler.java:551`) ne traite que les générateurs de monstres. Un banc posé à la main n'est jamais enregistré, un banc cassé est oublié quand l'IA le trouve absent. HyColony fait déjà de même (`RegisteredBlocks`, `removeWorkstation`) : **rien ne change ici**.

## 4. Hytale (pre.5, vérifié)

- **Type d'asset d'un plugin** : `PluginBase.getAssetRegistry()` (`HY/server/core/plugin/PluginBase.java:211`) enregistre un `AssetStore` et le retire à l'arrêt (`HY/server/core/plugin/registry/AssetRegistry.java:18`) ; le magasin se construit par `HytaleAssetStore.builder` (`HY/server/core/asset/HytaleAssetStore.java:175`). Ses fichiers sont lus dans **chaque pack**, sous `<pack>/Server/<chemin>` (`HY/assetstore/AssetStore.java:755`) ; un pack chargé après un autre remplace un fichier de même nom. Exemple public : `FoodValue` de HytaleHungerMod (AGPL, idée seulement).
- **Aliments** : Hytale n'a ni faim ni valeur nutritive (`docs/research/sp4b-hytale-food.md` § 2). Il marque ses aliments par la catégorie `Items.Foods` (héritée de `Template_Food`, `zip:Server/Item/Items/Food/Template_Food.json`) et `Consumable: true`. Dans les assets pre.5 : 53 objets `Items.Foods` : nos 44 aliments de la table qui ne sont pas des champignons, 4 variantes et 5 ingrédients non consommables (`Ingredient_Dough`, `_Fishbone`, `_Flour`, `_Salt`, `_Spices`). Les 4 variantes (`Food_Fish_Raw_Uncommon/_Rare/_Epic/_Legendary`, `Variant: true`, `Item.isVariant`, `HY/server/core/asset/type/item/config/Item.java:1009`) ne sont pas de vrais aliments : elles portent une recette du plan de cuisine dont le résultat est `Food_Fish_Raw` (`zip:Server/Item/Items/Food/Fish/Food_Fish_Raw_Uncommon.json`, `PrimaryOutput`), et Hytale les cache de la liste des objets. Les 6 `Plant_Crop_Mushroom_Glowing_*`, toxiques, sont consommables sans être `Items.Foods`.
- **Qualité** : chaque objet a une `Quality` (`Common`, `Uncommon`, `Rare`, `Epic`, `Legendary`…, `zip:Server/Item/Qualities/`). Nos aliments : Common pour le cru et le feu de camp, Uncommon pour le pain, le fromage, le pop-corn, les brochettes et les salades, Rare pour les tartes et la salade César.
- **Bancs de cuisson** : un banc `Processing` déclare ses recettes et son emplacement de combustible par type de ressource (`Bench_Campfire.json:65-69`, `"Fuel": [{"ResourceTypeId": "Fuel"}]`). Le four (`Bench_Furnace`) est `Processing` mais ne cuit aucun aliment.

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

Un objet sans fichier est un aliment s'il est `Consumable`, n'est pas une variante (`isVariant`) et est de la catégorie d'aliments de l'id-map (`food.foodCategory` : `Items.Foods`). Sa valeur se déduit de sa qualité Hytale, par une règle **du cœur** (une décision de jeu ne vit pas dans un plugin), l'énumération `kernel/item/FoodQuality` :

| Rang du cœur | Qualités Hytale (id-map `food.qualityRanks`) | Nutrition | Palier | D'où vient le chiffre |
|---|---|---|---|---|
| `COMMON` | Common, et toute qualité absente de l'id-map | 3 | 0 | médiane des 30 aliments Common de notre table |
| `UNCOMMON` | Uncommon | 8 | 2 | médiane des 9 aliments Uncommon (nutrition 6 à 9, paliers 1 et 2) |
| `RARE` | Rare, Epic, Legendary | 12 | 3 | médiane des 4 aliments Rare |

- `Poisonous` : faux (Hytale ne marque pas un aliment toxique ; ses champignons toxiques ont un fichier).
- Écart : `Deviation from MC (Hytale world): MC reads an item's FoodProperties → a Hytale food without a HyColony file takes the median value of our table's foods of its Quality.`
- Les noms de qualité et la catégorie sont des ids d'assets Hytale : ils vivent dans l'id-map (§ 7), le cœur ne connaît que les trois rangs et leurs valeurs.

### 5.4 Le port et l'adaptateur

- Le port `FoodCatalog` ne change pas : le cœur ne voit aucune différence.
- `HytaleFoods` lit le magasin d'assets au premier usage (les assets sont chargés), puis, pour un objet absent, la valeur de `FoodQuality` de son rang (qualité lue par `food.qualityRanks`) si l'objet est un aliment Hytale. Le cache existant reste ; un rechargement d'assets demande toujours un redémarrage (comportement actuel).
- `plugin/food/FoodValueAsset` : la classe d'asset (`JsonAssetWithMap<String, DefaultAssetMap<String, FoodValueAsset>>`, codec `Nutrition`, `Tier`, `Poisonous`, clé = nom du fichier), enregistrée dans `setup()` par `getAssetRegistry()`.
- `IdMap.check` ne vérifie plus la table des aliments ; `FoodSelfTest` (nouveau, `/hycolony selftest`) affiche : le nombre d'aliments à fichier, le nombre d'aliments par défaut, et chaque fichier dont l'objet est inconnu.

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

- `Tag` : nom d'un tag de MC, `<métier>_product`, `<métier>_product_excluded`, `reduceable_ingredient` ou `reduceable_product_excluded`. `<métier>` est l'id du métier sans son espace de noms (`hycolony:farmer` → `farmer`).
- `Values` : ids d'objets, ou `res:<type de ressource>` (tous les objets de ce type, comme les ingrédients des recettes).
- **Tous les fichiers d'un même `Tag` s'additionnent**, comme les tags de MC : le nom du fichier ne sert qu'à l'unicité (un mod nomme les siens `MonMod_Chef_Product.json`). Un fichier qui porte le nom d'un des nôtres le remplace (règle des assets) : c'est le moyen de retirer une de nos valeurs.
- Un `Tag` inconnu, une valeur inconnue des assets : ignorés, journalisés une fois.
- Les tags `_ingredient` / `_ingredient_excluded` de MC ne sont pas lus : HyColony ne les a jamais portés (SP3b-1) ; ils rejoindront ce type d'asset le jour où ils le seront.

### 6.2 Ce qui change dans `crafting.json`

- `includeItems` et `excludeItems` de chaque métier, et la section `reduceable`, **quittent** `crafting.json` : ce sont les tags de MC, ils vivent désormais dans `JobTags`. Ils sont vides aujourd'hui : aucun fichier `JobTags` n'est créé.
- `allow` (bancs et catégories, la règle par défaut de chaque métier, écart déjà documenté de SP3b-1) et `custom` (recettes données par niveau) restent dans `crafting.json` : ce sont des réglages du système, pas des tags d'objets. Le Chef (sous-projet 2) ajoutera sa propre règle par défaut (« le résultat est un aliment », MC `FoodUtils.EDIBLE`).
- La lecture reste tolérante (CLAUDE.md § 5) : une ancienne clé `includeItems`, `excludeItems` ou `reduceable` est ignorée et journalisée une fois.

### 6.3 Le cœur

- `crafting/recipe/JobTags` (record) : `Map<String, Set<ItemKey>>`, tag → objets, déjà fusionnés et développés par l'adaptateur ; `product(jobId)`, `excluded(jobId)`, `reduceable()`, `excludedFromReduction()`.
- `CraftingRules` reçoit `JobTags` au lieu de lire ces listes dans le JSON ; `allows` garde l'ordre de MC : exclu → refusé, inclus → accepté, sinon `allow`. `isReduceable` et `isExcludedFromReduction` lisent les tags.
- L'adaptateur `plugin/crafting/HytaleJobTags` lit le magasin `JobTags`, développe les `res:` par le catalogue d'objets et passe les fichiers au cœur (`JobTags.merge`, qui fusionne par `Tag`). `crafting.json` est lu au `setup()`, avant le chargement des assets ; les tags sont donc ajoutés à la création des ports d'un monde, assets chargés (`CraftingRules.withTags`, dans `WorldPorts`).

## 7. Partie 3 : les postes de cuisson

- Un **poste de cuisson** est un bloc dont le banc est de type `Processing` et a au moins une recette dont le résultat principal est un aliment (partie 1). Le feu de camp l'est ; le four ne l'est pas ; le four d'un mod qui cuit un aliment l'est.
- Ses **combustibles** sont les objets des types de ressource de son emplacement de combustible (`Fuel` du banc), au lieu du seul type `Fuel` du feu de camp.
- Ce qu'un objet **devient en cuisant** (`cookedFrom`, `rawFor`) se lit sur les recettes de tous les postes de cuisson, au lieu du seul feu de camp ; à entrée égale, la première recette par id de banc puis par id de recette gagne (ordre stable).
- `food.cookingBench` quitte l'id-map ; `HytaleCookingCatalog` et `HytaleFoods` perdent leur banc fixe.
- Le port `CookingCatalog` ne change pas. Les combustibles par défaut (`food.defaultFuels`) restent ceux de l'id-map.
- Écart : aucun nouveau. La règle « un poste de cuisson = le four de MC » (SP4b) devient « tout banc qui cuit un aliment », plus proche de MC, où tout four cuit.

## 8. Garde-fou : § 7 de CLAUDE.md

Le § 7 dit : « Les identifiants d'assets Hytale ne vivent que dans l'id-map de chaque mod ». Texte proposé, à poser dans une session lancée avec `HYCOLONY_GUARDRAILS_UNLOCKED=1`, avec l'accord de l'utilisateur :

> Les identifiants d'assets Hytale ne vivent que dans l'id-map de chaque mod (`hycolony/id-map.json`, …), sauf dans les types d'assets que HyColony ouvre aux autres mods (`Server/HyColony/Foods/`, `Server/HyColony/JobTags/`), où un fichier nomme l'objet qu'il décrit. Les plans de bâtiments sont dans `hycolony/styles.json`.

L'agent `ui-lang-checker` (qui vérifie la règle des id-maps) et la skill `hytale-api` (qui la rappelle) reçoivent la même exception ; `AGENTS.md` ne la cite pas. Cette étape vient en premier dans le plan : sans elle, la relecture refuserait les fichiers d'aliments.

## 9. Écarts

| Écart | Marquage |
|---|---|
| Valeur d'un aliment sans fichier, par sa qualité Hytale (§ 5.3) | `Deviation from MC (Hytale world)` dans `FoodQuality` |
| Tags de MC lus dans des assets Hytale au lieu des tags d'objets de Minecraft | `Deviation from MC (Hytale world)` dans `HytaleJobTags` et `JobTags` |

## 10. Architecture

- **Cœur** : `kernel/item/FoodQuality` (nouveau) ; `crafting/recipe/JobTags` (nouveau) ; `CraftingRules`, `CraftingRulesJson` (listes retirées, clés anciennes ignorées). Aucun changement de sauvegarde : aucune migration.
- **Plugin** : `food/FoodValueAsset`, `food/HytaleFoods`, `food/HytaleCookingCatalog`, `food/FoodIds` (ne garde que combustibles et particule), `crafting/JobTagAsset`, `crafting/HytaleJobTags` (nouveaux ou modifiés) ; `IdMap` ; `subplugin/SubPlugins` ; `command/FoodSelfTest` (nouveau) ; enregistrement des deux magasins dans `setup()`.
- **Ressources** : `Server/HyColony/Foods/*.json` (50 fichiers) ; `hycolony/id-map.json` ; `hycolony/crafting.json`.
- **Outils** : `tools/food/generate.py` ; `checkFoodTooltips`.
- **Packs internes** (`hycolony/packs.json`, aucun aujourd'hui) : un pack ajoute ses aliments et ses tags comme fichiers d'assets de son dossier ; un pack désactivé n'enregistre pas ses assets, donc ses aliments ne comptent pas.
- Chaque paquet reste sous 15 fichiers (`plugin/food` en a 3 aujourd'hui ; `plugin/crafting` est à vérifier au plan).

## 11. Tests

- **Cœur (TDD)** :
  - `FoodQuality` : la valeur de chaque rang ;
  - `JobTags` et `CraftingRules` : un produit exclu par un tag est refusé même si un autre tag l'inclut ; un produit inclus est accepté sans banc autorisé ; sans tag, `allow` décide ; deux tags du même nom fusionnés ; `reduceable` lu dans les tags ;
  - `CraftingRulesJson` : une ancienne clé `includeItems` est ignorée avec un avertissement ;
  - les tests existants de `CraftingRules` passent par `JobTags` au lieu des listes JSON.
- **Plugin** (`/hycolony selftest` et `docs/TESTING.md`, à partir du point 382) :
  - la ligne aliments du selftest : 50 aliments à fichier, 0 inconnu ;
  - un repas : un citoyen mange un aliment à fichier, à la même valeur qu'avant ; l'infobulle d'un aliment est inchangée ; la liste « Plats possibles » de la salle à manger ne montre aucune variante (`Food_Fish_Raw_Rare`…) ;
  - un aliment sans fichier (`Food_Bread.json` retiré un instant : en dev, les assets sont lus dans `src/main/resources`, `docs/research/plugin-b-api.md`) nourrit un citoyen à la valeur de sa qualité ;
  - la salle à manger cuit toujours au feu de camp ; le four n'est pas pris pour un poste de cuisson.

## 12. À vérifier pendant le plan (sources décompilées)

- Le moment de l'enregistrement : **vérifié**, les plugins de Hytale enregistrent leurs types d'assets dans `setup()` par `getAssetRegistry().register(...)`, avec `.loadsAfter(Item.class)` (`HY/builtin/adventure/shop/ShopPlugin.java:29-41`) ; reste à voir en jeu que nos 50 fichiers sont lus (selftest).
- Que l'enregistrement et la lecture ne se fassent jamais sur le thread du monde avec le verrou d'assets (`docs/research/plugin-b-api.md`, piège `loadAssets`).
- L'ordre des packs quand deux packs fournissent le même nom de fichier.
- La lecture de la catégorie `Items.Foods`, de `Consumable` et de `Quality` d'un `Item` (héritage par `Parent`).
- Le type de ressource de l'emplacement de combustible d'un banc `Processing` (`ProcessingBench` côté serveur).

## 13. Hors portée

- Les bancs posés à la main (§ 3) : comme MC, non enregistrés.
- Les tags `_ingredient` de MC.
- Le Chef, sa règle « le résultat est un aliment », sa cuisson sur requête : sous-projet 2.
- Le mod de nourriture de l'utilisateur : sous-projet 3, sa propre conception.
