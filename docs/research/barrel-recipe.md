# Recette du tonneau de taverne (`Furniture_Tavern_Barrel`)

Vérifié le 2026-10-02 sur Hytale 0.7.0-pre.5 : sources décompilées (`HY/` = `build/vineflower/hytale-server/com/hypixel/hytale/`) et `pre-release-0.7.0-pre.5-Assets.zip` (`zip:`). Rien n'a été essayé en jeu : tout ce qui suit est **[in-game]** sauf mention contraire.

Contexte : les plans MedievalOak posent 78 `Furniture_Tavern_Barrel` dans 14 plans (`audit-monde-hytale.md` A-16), venus de `minecraft:barrel` et des deux `domum_ornamentum:blockbarreldeco_*` (`tools/blueprint/tables.py:153-155`). En survie, aucune recette, aucune goutte et aucun troc ne donnent ce tonneau.

## 1. Ajouter une recette à un objet vanilla

### 1.1 Recette autonome (`Server/Item/Recipes/**`) : la voie sûre

- Le store `CraftingRecipe` lit `Server/Item/Recipes` dans **chaque** pack (`HY/server/core/asset/AssetRegistryLoader.java:637-645`, `setPath("Item/Recipes")`, `loadsAfter(Item, BlockType)`). La clé est le nom du fichier (`HY/assetstore/AssetStore.java:210-213`, `decodeFilePathKey`). Le dossier ne compte pas.
- Le type de banc n'est pas limité au recyclage. `CraftingPlugin.onRecipeLoad` range chaque recette chargée, quelle que soit son origine, dans le registre du banc nommé par chacune de ses `BenchRequirement` (`HY/builtin/crafting/CraftingPlugin.java:149, 196-211`). Les recettes d'objet (`Item.Recipe`) passent par le même chemin : `onItemAssetLoad` les recharge dans le store `CraftingRecipe` (l. 163-184). `getBenchRecipes` filtre ensuite par type, id et catégorie (l. 240-261).
- Pour une recette autonome, `processConfig` ne fait que copier `PrimaryOutput` dans `Output` si `Output` est vide (`HY/server/core/asset/type/item/config/CraftingRecipe.java:236-239`). Il ne déduit jamais `PrimaryOutput`, alors que la recette d'un objet le reçoit (`HY/server/core/asset/type/item/config/Item.java:1292-1309`). Il faut donc écrire `Output` **et** `PrimaryOutput`, comme le fait HyVanilla (`tools/vanilla/beds.py:57`, par exemple `HyVanilla_Bed_Black_Dye`). `KnowledgeRequired` vaut `false` par défaut (`CraftingRecipe.java:115-116, 159`).
- Précédent : HyVanilla ajoute déjà des recettes autonomes de type `Crafting` au `Furniture_Bench` (recoloration des lits). `docs/TESTING.md` 196 doit le vérifier en jeu (**[in-game]**).
- Ce qu'elle casse : rien. L'objet vanilla n'est pas touché. Un autre mod peut ajouter sa propre recette du même objet sans conflit, puisque les clés diffèrent. Après une mise à jour du jeu, la recette ne casse que si l'identifiant `Furniture_Tavern_Barrel`, le banc ou une catégorie disparaît.

### 1.2 Surcharger l'objet vanilla : comment les packs se résolvent

- **Ordre des packs.** Au `LoadAssetEvent`, `AssetModule.loadAllAssetPacks` trie les packs par `Mod.calculateLoadOrder`, puis charge chaque pack dans cet ordre (`HY/server/core/asset/AssetModule.java:462-493`). Chaque pack non `Hytale` dépend implicitement des packs cœur du groupe `Hytale` (`HY/common/plugin/Mod.java:48-52, 76-78`). L'ordre suit ensuite les dépendances, `OptionalDependencies` et `LoadBefore` du manifeste (l. 59-89), et `ModLoadOrder` de la config du serveur (l. 92-106 ; `HY/server/core/HytaleServerConfig.java:114, 417`). Entre deux packs sans lien, l'ordre est alphabétique par identifiant (`Mod.java:133`). Notre pack passe donc toujours après le vanilla.
- **Même clé dans deux packs = remplacement complet.** `DefaultAssetMap` garde une chaîne par clé, avec une entrée par pack. L'asset actif est le **dernier** de la chaîne (`HY/assetstore/map/DefaultAssetMap.java:254-310`, l. 303-304). Il n'y a ni fusion ni avertissement : la détection des doublons est désactivée (`AssetStore.java:67`, `DETECT_DUPLICATE_ASSETS = false` ; l. 1399-1411). Le chemin du fichier ne compte pas : seul le nom compte.
- **Fusion par `"Parent": "super"`.** Ce parent désigne l'asset de même clé du pack précédent (`AssetStore.java:793, 1168` ; `DefaultAssetMap.java:77-108`). Seuls les champs `appendInherited` sont hérités. Pour un objet, `Quality` (`Item.java:159`), `Recipe` (l. 291) et `HudUI` (l. 527) ne le sont pas. Piège déjà vu en jeu : avec `super`, le `BlockType` contenu cherche un parent nommé « super » et se charge seul (`connected-blocks.md` l. 161, essai du 2026-10-01). C'est à proscrire pour un bloc.
- **Copie complète du JSON vanilla** dans notre pack (même nom de fichier, `BlockType` compris). Elle fonctionne, mais :
  - elle **fige** l'objet : une correction ultérieure du tonneau par Hytale est masquée tant qu'on ne recopie pas ;
  - si un autre mod surcharge aussi `Furniture_Tavern_Barrel`, le dernier chargé gagne, selon l'ordre ci-dessus. L'un des deux perd tout, sans message ;
  - Hytalor (`food-tooltips.md` § 1) écrit lui aussi des copies complètes dans un pack chargé après tous les autres. Un patch Hytalor du même objet écraserait la copie.
- **Patch Hytalor** (`Server/Patch/**`, dépendance optionnelle déjà déclarée dans `plugin/src/main/resources/manifest.json`, déjà utilisé pour les aliments) : les objets fusionnent clé par clé (`food-tooltips.md` § 1). Hytalor recopie le JSON vanilla courant, donc il suit les mises à jour. En revanche, sans Hytalor installé, le patch ne fait rien.

**Conclusion.** La recette passe par un asset autonome (§ 1.1), jamais par une surcharge de l'objet. Une surcharge ne se justifie que pour changer la casse (§ 3), et alors de préférence par un patch Hytalor.

## 2. Le banc de mobilier

- Bloc `Bench_Furniture` (`zip:Server/Item/Items/Bench/Bench_Furniture.json`). Banc `"Type": "Crafting"`, `"Id": "Furniture_Bench"` (l. 45-46, 93). Il est fabriqué à l'établi (`Workbench` [`Workbench_Crafting`], 6 `RT:Wood_Trunk` + 4 `RT:Rock`, l. 6-27).
- **Catégories** (l. 47-88) : `Furniture_Storage`, `Furniture_Beds`, `Furniture_Lighting`, `Furniture_Pottery`, `Furniture_Textiles`, `Furniture_Village_Walls`, `Furniture_Misc`, `Furniture_Seasonal`.
- **Paliers** : aucun. Il n'y a pas de `TierLevels`, et aucune recette de ce banc n'a de `RequiredTierLevel` (relevé de tous les `Recipe` du zip). Nombre de recettes d'objet par catégorie : Textiles 30, Lighting 24, Seasonal 20, Misc 19, Storage 18, Pottery 17, Village_Walls 16, Beds 7.
- **Catégorie naturelle : `Furniture_Storage`.** Le tonneau est rangé dans `Furniture.Containers`, sa hitbox est `Chest_Small` et sa famille est `Tavern` (`zip:Server/Item/Items/Furniture/Tavern/Unique/Furniture_Tavern_Barrel.json:6-8, 27, 70-72`). C'est la catégorie de tous les petits coffres de famille. Le tonneau n'est pourtant **pas** un conteneur : son `BlockType` n'a ni `State` ni `BlockEntity`.
- Recettes vanilla de comparaison :
  - `Furniture_Tavern_Chest_Small` (même famille `Tavern`, même catégorie d'objet) : 3 `Wood_Darkwood_Planks` + 2 `Ingredient_Bar_Iron` @ `Furniture_Bench` [`Furniture_Storage`], sans palier (`zip:Server/Item/Items/Furniture/Tavern/Furniture_Tavern_Chest_Small.json:108-128`) ;
  - `Furniture_Lumberjack_Chest_Small` : 3 `Wood_Redwood_Planks` + 1 `Ingredient_Bar_Iron` @ `Furniture_Bench` [`Furniture_Storage`] ;
  - `Furniture_Tavern_Bed` : 3 `Wood_Darkwood_Planks` + 4 `Ingredient_Fibre` + 2 `Cloth_Block_Wool_Red` + 1 `Cloth_Block_Wool_White` @ `Furniture_Bench` [`Furniture_Beds`] (`zip:…/Tavern/Furniture_Tavern_Bed.json:10-37`) ;
  - `Deco_Mug` : 1 `RT:Wood_Planks` + 1 `Ingredient_Stick` @ `Furniture_Bench` [`Furniture_Misc`] (`zip:Server/Item/Items/Deco/Deco_Mug.json:9-27`).
- Les planches de bois sombre se font aux `Builders` [`WoodPlanks`] à partir de `RT:Wood_Darkwood_Trunk`, que donne par exemple `Wood_Cedar_Trunk`. Les lingots de fer se font au four, à partir de `Ore_Iron`.
- Côté MineColonies : le tonneau décoratif de Domum se fabrique avec 3 planches et 6 bâtons, et sa casse rend le bloc lui-même (`sources/domum-ornamentum/src/datagen/generated/domum_ornamentum/data/domum_ornamentum/recipes/blockbarreldeco_standing.json:4-19`, `loot_tables/blocks/blockbarreldeco_standing.json:11-16`).
- **Propositions d'ingrédients** :
  - (A) la recette du coffre de taverne, 3 `Wood_Darkwood_Planks` + 2 `Ingredient_Bar_Iron` (les cerclages). C'est la plus cohérente avec la famille, mais 78 tonneaux coûtent 156 lingots ;
  - (B) plus légère et proche de Domum : 3 `RT:Wood_Planks` + 1 `Ingredient_Bar_Iron`, avec le coût en métal du coffre `Lumberjack`.

Exemple (B), au format de HyVanilla :

```json
{
  "Input": [
    { "ResourceTypeId": "Wood_Planks", "Quantity": 3 },
    { "ItemId": "Ingredient_Bar_Iron", "Quantity": 1 }
  ],
  "Output": [ { "ItemId": "Furniture_Tavern_Barrel", "Quantity": 1 } ],
  "PrimaryOutput": { "ItemId": "Furniture_Tavern_Barrel", "Quantity": 1 },
  "BenchRequirement": [
    { "Type": "Crafting", "Id": "Furniture_Bench", "Categories": [ "Furniture_Storage" ] }
  ]
}
```

## 3. Le butin à la casse

- La casse du tonneau suit `Gathering.Soft.DropList: "Barrels"` (`Furniture_Tavern_Barrel.json:22-26`). Comme un `DropList` est présent, `getDrops` ne rend **pas** le bloc (`HY/server/core/modules/interaction/BlockHarvestUtils.java:647-667`).
- `zip:Server/Drops/Items/Barrels.json` : un seul `Choice` (1 tirage par défaut, `HY/server/core/asset/type/item/config/container/ChoiceItemDropContainer.java:39-57`), avec des poids sur un total de 1 095 :

| Résultat | Poids | Probabilité | Quantité |
|---|---:|---:|---|
| `Plant_Fruit_Apple` | 100 | 9,1 % | 1 |
| `Plant_Fruit_Berries_Red` | 100 | 9,1 % | 1 |
| `Ingredient_Hide_Light` | 50 | 4,6 % | 1 |
| `Weapon_Arrow_Crude` | 25 | 2,3 % | 1 à 5 |
| `Ingredient_Salt` | 20 | 1,8 % | 1 |
| rien (`Empty`) | 800 | 73,1 % | — |

- **Pas de ferme rentable** : fabriquer puis casser un tonneau détruit ses planches et son lingot, contre 27 % de chances d'un objet commun. Le sel se fabrique d'ailleurs à partir de `Rock_Salt`, 1 donne 5 (`zip:Server/Item/Items/Ingredient/Ingredient_Salt.json:43-56`). Le vrai piège est inverse : **un tonneau fabriqué puis cassé est perdu**, que ce soit par un joueur ou par un bâtisseur qui démolit.
- La liste `Barrels` sert aussi à `Furniture_Ancient_Barrel`, `Furniture_Ancient_Crate`, `Furniture_Ancient_Sack` et `Furniture_Village_Crate` (leurs `Gathering`). Il ne faut donc **pas** la modifier.
- **Hytale distingue les blocs posés par un joueur.** `BlockGathering.UseDefaultDropWhenPlaced`, documenté « If this is set then player placed blocks will use the default drop behaviour instead of using the droplists » (`HY/server/core/asset/type/blocktype/config/BlockGathering.java:48-55, 120-122`) :
  - à la pose par un joueur, `BlockPlaceUtils` marque la case « deco » quand `blockType.canBePlacedAsDeco()` est vrai (`HY/server/core/modules/interaction/BlockPlaceUtils.java:438-440`). Cette méthode renvoie vrai avec `IgnoreSupportWhenPlaced` ou `UseDefaultDropWhenPlaced` (`HY/server/core/asset/type/blocktype/config/BlockType.java:1876-1878`). Le marquage n'a pas lieu si le joueur a `isOverrideBlockPlacementRestrictions()` ;
  - à la casse, si `UseDefaultDropWhenPlaced` est vrai et que la case est deco, `itemId` et `dropListId` sont mis à null (`BlockHarvestUtils.java:959-965`), et `getDrops` rend alors l'objet du bloc. Cela vaut pour la casse `Soft` comme pour `Breaking`, puisque le test vient après les deux branches (l. 905-956) ;
  - le marquage est la valeur de support 15 de `BlockPhysics` (`HY/server/core/blocktype/component/BlockPhysics.java:33, 176-183`). Ce composant de section est sauvegardé (`HY/server/core/blocktype/BlockTypeModule.java:49`). La case deco ne tombe plus faute de support.
  - Un bloc généré par le monde n'est pas marqué. `BlockOperations.setBlock` remet le support à zéro (`HY/server/core/universe/world/chunk/BlockOperations.java:120-126`) : un tonneau de ruine garde donc son butin.
  - Précédent vanilla : 37 objets, dont tout le mobilier gobelin, qui a une recette, une goutte `Empty` pour les exemplaires du monde et `UseDefaultDropWhenPlaced: true` (`zip:Server/Item/Items/Furniture/Goblin/Furniture_Goblin_Stool.json:59-65`).
- **Ce qu'il faut pour le tonneau** : `"BlockType": {"Gathering": {"UseDefaultDropWhenPlaced": true}}`, de préférence par un patch Hytalor (§ 1.2). Sans Hytalor, il faut une copie complète du JSON, avec ses défauts.
- **Notre bâtisseur ne marque pas ses poses.** `HytaleWorldBlocks.place` appelle `BlockOperations.setBlock` (`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldBlocks.java:145-146`), qui remet le support à zéro. Un tonneau posé par le bâtisseur rendrait donc le butin, comme un tonneau du monde. Pour imiter la pose d'un joueur, il faudrait appeler `BlockPhysics.markDeco(store, sec, x, y, z)` après le `setBlock` quand `type.canBePlacedAsDeco()` est vrai. C'est ce que fait `BlockPlaceUtils.java:438-440`. Ce constat dépasse le tonneau : aujourd'hui, un meuble gobelin posé par le bâtisseur ne rend rien quand on le casse. **[in-game]**
- Autre option, à écarter : remplacer le `Gathering` du tonneau par `Soft` sans `DropList`, pour qu'il se rende partout. Cela touche aussi les tonneaux du monde, qui perdent leur butin, et demande la même surcharge.

## 4. Autres tonneaux vanilla

- Seuls deux objets utilisent le modèle `Blocks/Decorative_Sets/Village/Barrel.blockymodel` :
  - `Furniture_Ancient_Barrel` (`zip:…/Ancient/Unique/Furniture_Ancient_Barrel.json:13-23`), avec une autre texture, deux variantes, sans recette et avec la même liste `Barrels` ;
  - `Debug_Falling_Barrel`, de qualité `Developer`, sans recette.
- `Throwable_Explosive_Barrel` est un autre modèle (`Barrel_Explosive_Goblin`), sans recette.
- Aucun objet nommé `*Keg*` ou `*Cask*`.
- **Aucun tonneau vanilla n'a de recette** : la recette n'est pas redondante. Le dossier `Unique/` de la famille Tavern regroupe des objets en partie sans recette (`Barrel`, `Chest_Large`), d'autres en ont une (`Bench`, `Candle`, `Planter`, `Platform`).

## 5. Recommandation

1. Ajouter une recette **autonome** `plugin/src/main/resources/Server/Item/Recipes/HyColony/HyColony_Tavern_Barrel.json`, avec `Output` et `PrimaryOutput` = `Furniture_Tavern_Barrel`, au `Furniture_Bench` [`Furniture_Storage`], ingrédients (A) ou (B). Cela ne casse rien et ne dépend de personne.
2. Ajouter un patch Hytalor `UseDefaultDropWhenPlaced: true` sur le tonneau, pour qu'un tonneau posé se rende. Les tonneaux de ruine gardent leur butin. Sans Hytalor, le tonneau cassé redonne le butin, ce qui n'est pas une exploitation, seulement une perte.
3. Faire marquer « deco » les poses du bâtisseur (`BlockPhysics.markDeco` quand `canBePlacedAsDeco()`), pour qu'il pose comme un joueur. Sans cela, le point 2 ne vaut que pour les poses d'un joueur.

Tout est **[in-game]** : affichage de la recette autonome au banc, casse d'un tonneau posé avec le patch, effet de `markDeco` sur une pose du bâtisseur.
