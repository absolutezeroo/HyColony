# Tapis et pots de fleurs « comme Minecraft »

Recherche du 2026-09-28, avant toute conception. Abréviations :

- `Z:` = entrée de `release-0.6.8-Assets.zip` (`$USERPROFILE/.gradle/caches/hytale-assets/`).
- `H:` = `build/vineflower/hytale-server/com/hypixel/hytale/`.
- Unités des `.blockymodel` : 32 unités = 1 bloc (`Z:Common/Blocks/Structures/Base_Shapes/QuarterBlock.blockymodel` : boîte `32×8×32` = quart de bloc). 1/16 de bloc = 2 unités.

## 1. Ce que Hytale 0.6.8 a déjà

### Tapis / « rugs »

| Id | Modèle / hauteur | Support | Couleurs | Remarque |
|---|---|---|---|---|
| `Cloth_Roof_<C>_Flat` (13 : Blue, Green, Hide, Leather, Orange, Red, White, Yellow, Modern_Blue/DarkGreen/Orange/Red/Yellow) | `Blocks/Structures/Roofs/Cloth_Roof_Horizontal.blockymodel` : une boîte `32×2×32`, offset y=1, donc **exactement 1/16 de bloc posé au sol** ; `HitboxType: Block_Flat` (0,05) | **Aucun** (`"Supporting": {}`, pas de `Support`) : flotte si on retire le dessous | 13 teintes de toile de tente (`Cloth_Roof_Textures/Tent_<C>.png`, 128×224) | C'est la forme exacte du tapis MC, mais c'est un toit : texture de tente, catégorie `RoofBlocks`, recette 1 laine → 1 (établi `Builders`/`Roof`). `Z:Server/Item/Items/Cloth/Roof_Red/Cloth_Roof_Red_Flat.json` |
| `Plant_Moss_Rug_{Green,Blue,Green_Dark,Lime,Pink,Red,Yellow}` | `Blocks/Foliage/Plants/Moss_Rug.blockymodel` (quads de mousse, pas une dalle) ; `HitboxType: Block_Quarter` (0,25) ; Lime : `Sorrel_Rug` + `Block_Flat` | `Support.Down: [{FaceType: Full}, {FluidId: Water_Source}]` | Vert : texture grise `Moss_Rug_GS*.png` + `"Tint": ["#84b338"]` ; les autres surchargent modèle/texture | Tapis de mousse (≈ moss carpet), pas de laine. `Z:Server/Item/Items/Plant/Moss/Plant_Moss_Rug_Green.json` |
| `Plant_Vine_Rug` | `Moss_Rug.blockymodel`, `Block_Quarter` | idem | 1 | `Z:Server/Item/Items/Plant/Plant_Vine_Rug.json` |
| `Furniture_Royal_Magic_Carpet` | `Royal_Magic/Carpet.blockymodel`, hitbox `Carpet_Large` = 2×0,15×2 | `Support.Down: Full` | 1 | Tapis 2×2 décoratif, `Quality: Developer`, non craftable. `Z:Server/Item/Items/Furniture/Grand_Wizard/Unique/Furniture_Royal_Magic_Carpet.json`, `Z:Server/Item/Block/Hitboxes/Furniture/Carpet_Large.json` |

Aucun bloc « tapis de laine 1/16 » vanilla. Les icônes `Prototype_Softwood_Carpet_*` et `Furniture_Grand_Wizard_Carpet` existent sans item (`Z:Common/Icons/ItemsGenerated/`).

Matière première : laine en **20 couleurs** : `Cloth_Block_Wool_<C>` (Black, Blue, Cyan, Gray, Green, Orange, Pink, Purple, Red, White, Yellow + variantes `_Light` sauf Black/White). Textures `Z:Common/BlockTextures/Cloth_<C>.png` en 32×32. Recette : `Cloth_Block_Wool_White` + pétale → couleur, à l'établi `Furniture_Bench`/`Furniture_Textiles` (`Z:Server/Item/Items/Cloth/Wool/Cloth_Block_Wool_Red.json`). Les demi-dalles `Cloth_Block_Wool_<C>_Half` montrent qu'un modèle custom (`HalfBlock.blockymodel`) accepte directement `BlockTextures/Cloth_<C>.png` en `CustomModelTexture`. Pas de marron, lime, magenta ni bleu clair au sens MC (le jeu de 20 teintes diffère des 16 de MC).

Hitboxes minces disponibles (`Z:Server/Item/Block/Hitboxes/Block/`) : `Block_Flat` (Max.Y 0,05), `Block_One_Eighth` (0,125), `Block_Quarter` (0,25). Pas de 0,0625 ; un pack peut en ajouter un.

### Pots / jardinières

- Pots décoratifs : `Furniture_{Village,Feran,Jungle,Ancient,Frozen_Castle,Human_Ruins,Temple_*,Royal_Magic}_Pot*`. Ce sont des jarres presque pleine taille (ex. `Village/Pot.blockymodel` : boîtes `28×23×28`, `20×32×20`), sans hitbox dédiée (bloc plein), `Support.Down: Full`, casse → `Container_Pot_Clay`. **Aucun ne contient de plante ni ne change d'état.** `Z:Server/Item/Items/Furniture/Village/Unique/Furniture_Village_Pot.json`.
- Jardinières : `Furniture_{Village,Feran,Jungle,Tavern,Human_Ruins,Temple_Scarak}_Planter`, avec `Tags.SubType: ["Planter"]` et `Supporting.Up`. Les fleurs acceptent comme support `TagId: "Type=Soil"` **ou** `TagId: "SubType=Planter"` (`Z:Server/Item/Items/Plant/Flowers/Plant_Flower_Common_White.json`, bloc `Support.Down`). On pose donc une fleur **sur** une jardinière, dans le bloc du dessus : deux blocs, pas un pot garni.
- Aucun bloc « pot + plante » (`potted`, `Flower_Pot` : rien dans la liste du zip).

Plantes candidates : 66 fichiers `Z:Server/Item/Items/Plant/Flowers/`, ~29 `Plant_Sapling_*`, dossiers `Cactus`, `Bushes`, `Grass`, champignons à recenser.

## 2. Comportement MC à reproduire (minecraft.wiki)

- **Tapis** (`https://minecraft.wiki/w/Carpet`) : hitbox 1/16 couvrant tout le bloc ; 16 couleurs (white, light gray, gray, black, brown, red, orange, yellow, lime, green, cyan, light blue, blue, purple, magenta, pink) ; « can be placed on any block, including non-solid blocks, except air » ; si le bloc dessous disparaît, le tapis tombe en objet ; recette 2 laines → **3** tapis (depuis 13w17a) ; reteinture d'un tapis avec un colorant ; inflammable.
- **Tapis de mousse** (`https://minecraft.wiki/w/Moss_Carpet`) : 2 blocs de mousse → 3. Le résumé obtenu est flou sur le support ; non revérifié en source.
- **Pot de fleurs** (`https://minecraft.wiki/w/Flower_Pot`) : 3 briques → 1 pot ; 3/8 de bloc de haut ; en Java, posable sur n'importe quel bloc **ou au-dessus du vide** (pas de support requis). Contenus : fleurs d'un bloc de haut, pousses, fougères, buissons morts, cactus, bambou, azalées, propagule, racines et champignons du Nether, champignons. « Utiliser » l'objet sur un pot vide le plante ; « utiliser » un pot garni rend la plante, **sauf** si le joueur tient une plante plantable (alors rien). Une seule plante. Casse d'un pot garni : le pot et la plante tombent séparément. Chaque combinaison est un bloc distinct (`potted_dandelion`, `potted_oak_sapling`…).
- **MineColonies/Structurize n'ajoutent ni tapis ni pot** : `ModBlocks.java` (`raw.githubusercontent.com/ldtteam/minecolonies/version/main/src/main/java/com/minecolonies/api/blocks/ModBlocks.java`) ne déclare que huttes, blocs utilitaires (stash, rack, barrel, gates…), farmland et cultures. Ce sont des blocs vanilla MC utilisés dans les schematics.

## 3. Ajouter ces blocs dans Hytale 0.6.8

### Champs `BlockType` vérifiés

Clés du codec (`H:server/core/asset/type/blocktype/config/BlockType.java`) : `DrawType` (l. 126), `CustomModel` (l. 173), `CustomModelTexture`, `Material` (l. 192), `Opacity` (l. 200), `HitboxType` (l. 461), `Interactions` (l. 494, `EnumMap<InteractionType, RootInteraction>`), `InteractionSoundEventId` (l. 556), `SupportDropType` (l. 575, défaut `BREAK` l. 937 ; valeurs `BREAK/DESTROY/FALL`, `SupportDropType.java`), `MaxSupportDistance` (l. 583), `Support` (l. 597-617, doc : « If met, the block won't fall off from block physics checks. If this field is empty the block is automatically considered supported »), `Supporting`, `IgnoreSupportWhenPlaced` (l. 636), `Tint`/`TintUp…` (l. 648), `BiomeTint` (l. 707), `State` (l. 765). **Un seul `CustomModel` et une seule texture (tirage pondéré) par bloc ou par état** : pas de modèle composé de plusieurs textures.

Conditions de support (`H:server/core/asset/type/blocktype/config/RequiredBlockFaceSupport.java:21-66`) : `FaceType` (chaîne libre comparée au `Supporting` du voisin ; « A LOT of blocks use 'Full' »), `SelfFaceType`, `BlockSetId`, `BlockTypeId`, `FluidId`, `TagId`, `MatchSelf`, `Support`, `AllowSupportPropagation`, `Rotate`, `Filler`. Aucune valeur « tout bloc non-air » : `FaceType: Full` refuse escaliers, dalles, barrières (écart avec MC, à accepter ou compléter par d'autres `FaceType`).

### Tapis : pur asset

Par couleur, un item `HyColony_Carpet_<C>` dans le pack :

- `Common/Blocks/HyColony/Carpet.blockymodel` : copie de `Cloth_Roof_Horizontal.blockymodel` (boîte `32×2×32`, offset y=1) avec des UV remappés sur une texture 32×32 (le layout vanilla pointe en (32,46) dans une texture 128×224).
- `BlockType` : `DrawType: Model`, `Opacity: Transparent`, `CustomModel` ci-dessus, `CustomModelTexture: BlockTextures/Cloth_<C>.png` (textures vanilla réutilisées, comme les demi-dalles), `HitboxType: Block_Flat` (ou un `Server/Item/Block/Hitboxes/HyColony/Carpet.json` à 0,0625), `Support.Down: [{FaceType: Full}]`, sons `Cloth`, `PhysicalMaterialId: Wool`.
- Recette `Cloth_Block_Wool_<C>` ×2 → `OutputQuantity: 3`, établi `Furniture_Bench`/`Furniture_Textiles`.
- Clé de langue `server.items.HyColony_Carpet_<C>.name` en en-US et fr-FR, icône.

Alternative à une seule texture : texture grise + `Tint` par item (précédent `Plant_Moss_Rug_Green`, `"Tint": ["#84b338"]`), avec `Parent` commun (précédent `Plant_Moss_Rug_Pink` → `"Parent": "Plant_Moss_Rug_Green"`).

**[in-game]** : chute effective du tapis quand on casse le bloc dessous, et objet rendu (`SupportDropType` `BREAK` par défaut) ; collision réelle du hitbox `Block_Flat`.

### Pot : précédents vanilla d'objet utilisé sur un bloc

- `Tool_Fertilizer_Crystal` : `Interactions.Secondary: "Fertilizer_Crystal_Use"` → `ChangeBlock` (`Changes: { "Soil_Dirt_Tilled": "Soil_Dirt_Crystal", "*Soil_Dirt_Tilled_State_Definitions_Fertilized": … }`) → `Next: ModifyInventory { AdjustHeldItemQuantity: -1 }` (`Z:Server/Item/Interactions/Tools/Fertilizer_Crystal_Use.json`). Un objet tenu change le bloc visé et se consomme : exactement « planter dans le pot ».
- Cristaux → lanternes : `Rock_Crystal_Red_Small` a `Secondary: "Lantern_Red"` ; `Lantern_Red` = `BlockCondition` (bloc `Furniture_Human_Ruins_Lantern`) → `ChangeState { default|White|…: "Red" }`, `Failed: "Block_Secondary"` (repli : pose normale) (`Z:Server/Item/Interactions/Block/Lantern/Lantern_Red.json`).
- Demi-dalle → bloc plein : `BlockCondition` → `ChangeState {default: Block}` → `ModifyInventory -1` (`Z:Server/Item/Interactions/Block/Half_Block.json`).
- Lanterne : `BlockType.Interactions.Use` → `ChangeState {default: Off, On: Off, Off: On}` ; l'état `Off` surcharge `CustomModelAnimation`, `Light`… (`Z:Server/Item/Items/Deco/Deco_Lantern.json:118-140`).

APIs correspondantes :

- `ChangeBlockInteraction` (`H:server/core/modules/interaction/interaction/config/client/ChangeBlockInteraction.java:52-129`) : map bloc → bloc, garde la rotation, échoue si le bloc visé n'est pas une clé.
- `ChangeStateInteraction` (même dossier, l. 46-140) : map état → état (`"default"` = état initial). **Piège** : après le changement, si l'état cible n'a pas d'`InteractionSoundEventId`, l'interaction passe `Failed` (l. 128-133), donc `Next` (consommation) ne s'enchaîne pas. Donner un `InteractionSoundEventId` à chaque état. **[in-game]**
- `ModifyInventoryInteraction` (`…/config/server/ModifyInventoryInteraction.java:38-95, 116-150`) : `AdjustHeldItemQuantity` (<0 retire de la main, échoue si impossible), `ItemToAdd` (ajoute ou jette au sol), `ItemToRemove` (cherche dans barre + sac, **pas** la main), `RequiredGameMode`.
- `BlockConditionInteraction` (`…/client/BlockConditionInteraction.java:54-306`) : matchers sur `Block.Id`, `Block.State`, `Block.Tag`, `Face`. **Aucune interaction ne teste l'objet tenu** (inventaire des classes de `config/client`, `none`, `server`).
- `ReplaceInteraction` (`…/config/none/ReplaceInteraction.java:29-100`) : exécute la `RootInteraction` nommée par `InteractionVars[Var]` ; les variables viennent de l'**objet tenu** (`InteractionContext.defaultGetVars` → `originalItemType.getInteractionVars()`, `H:server/core/entity/InteractionContext.java:871-874` ; clé item `InteractionVars`, `H:server/core/asset/type/item/config/Item.java:475`).
- `Block_Secondary` (clic droit avec un bloc en main, et `Unarmed/Interactions/Block.json`) = `UseBlock`, repli `PlaceModeSelect` (`Z:Server/Item/Interactions/Block_Secondary.json`). `UseBlockInteraction.doInteraction` exécute `blockType.getInteractions().get(type)` du bloc visé, avec le **même** type (`Secondary`) ; si le bloc n'a rien pour ce type, `Failed` et **pas d'événement** (`…/client/UseBlockInteraction.java:62-73`). Sinon `UseBlockEvent.Pre` (annulable), exécution, puis `UseBlockEvent.Post` (l. 76-96).

### Pot : deux voies

**A. Tout en assets.** Un bloc `HyColony_Flower_Pot` (modèle neuf, ~`12×12×12` unités, hitbox dédiée) avec un `State.Definitions.<Plante>` par plante ; chaque état surcharge `CustomModel` + `CustomModelTexture` (modèle pot + plante, **une texture atlas par état**, cf. limite d'une texture), `InteractionSoundEventId`, et ses drops.

- Planter : `Interactions.Secondary` du pot = `Replace { Var: "HyColony_Pot" }`. Chaque plante porte `InteractionVars: { "HyColony_Pot": "HyColony_Pot_Plant_<P>" }` → `ChangeState { default: "<P>" }` → `ModifyInventory { AdjustHeldItemQuantity: -1 }`.
- Reprendre : `Interactions.Use` (ou `Secondary` dans les états garnis) = `ChangeState { <P>: default }` → `ModifyInventory { ItemToAdd: { Id: <P>, Quantity: 1 } }`, défini dans chaque état.
- **Coût** : `InteractionVars` doit être ajouté aux **items vanilla** des plantes, donc les redéfinir dans notre pack. Le dernier pack chargé remplace l'asset entier (`H:assetstore/map/DefaultAssetMap.java:271-303`, la valeur retenue est le dernier maillon de la chaîne). Il faut recopier chaque JSON de plante (~100 fichiers) : fragile aux mises à jour (version épinglée, mais conflit possible avec d'autres mods). **[in-game]** : que `originalItemType` soit bien la plante tenue quand la racine du pot s'exécute via `UseBlock`.

**B. Assets + petit code plugin.** Même bloc et mêmes états, mais le pot déclare `Interactions.Secondary`/`Use` (une racine `Simple`, nécessaire pour que `UseBlockEvent` soit émis, cf. l. 71-73). Un système `UseBlockEvent.Pre` (motif `TownHallBlockSystems.Use`, `docs/research/plugin-b-api.md` § « Use ») lit `event.getContext().getHeldItem()` (`InteractionContext.java:421`), consulte une table plante → état, pose l'état (`World.setBlock(x, y, z, "*HyColony_Flower_Pot_State_Definitions_<P>")` ou `BlockOperations.setBlock`, `plugin-b-api.md` l. 63 et 511), retire 1 de la main (`getHeldItemContainer()`/`getHeldItemSlot()`, l. 412-416) ou rend la plante, puis annule l'événement. Aucun item vanilla modifié.

Casse d'un pot garni : `Gathering` par état avec une `DropList` pot + plante (à écrire ; format `DropList.Container` vu dans `Cloth_Block_Wool_Red_Half.json`). **[in-game]**

## 4. Intérêt pour le portage

- Rien n'est mappé aujourd'hui : aucune occurrence de `carpet`, `flower_pot` ou `potted` dans `plugin/src/main/resources/hycolony/id-map.json`, les fragments `plugin/src/subplugins/*/hycolony/id-map.json`, ni le code.
- HyColony **n'importe pas les schematics MC** : les plans sont des prefabs Hytale (`styles.json`, prefabs vanilla Outlander/Kweebec), et la spec SP0 dit que schematics et textures MC ne sont pas repris (`docs/superpowers/specs/2026-09-25-hycolony-sp0-fondations-design.md:6`). Les tapis et pots des schematics MC ne « tombent » donc pas : ils n'existent simplement pas dans nos plans. Ces blocs servent aux futurs styles faits main et au décor des joueurs, pas à une conversion.

## 5. Recommandation

- **Tapis : pur assets**, sous-pack de décor (`plugin/src/subplugins/<Nom>/` avec `Common/` + `Server/` + langues). Un modèle, 20 items (un par laine vanilla, textures réutilisées), recette 2 → 3. Pas de code, pas d'entrée `id-map.json` tant qu'aucun plan ne les référence.
- **Pot : voie B** (assets + un système `UseBlockEvent.Pre`), plus petite et plus sûre que redéfinir ~100 items vanilla. Elle sort du pur-asset : le code irait dans le module plugin, pas dans un sous-pack d'assets (le mécanisme en cours ne prévoit que des assets et des fragments de données, à confirmer). Ce n'est pas une règle de colonie, donc pas de conflit avec § 1 de `CLAUDE.md`. Sans code, repli le plus proche : un pot étiqueté `SubType=Planter` sur lequel on pose la fleur au-dessus (précédent jardinières), mais ce n'est pas le pot MC.
- Chaque plante en pot demande son propre modèle et sa propre texture atlas : c'est le vrai coût (art), pas le code.

### Questions pour l'utilisateur

1. Couleurs de tapis : les 20 laines Hytale (recommandé, textures existantes) ou les 16 de MC (4 à créer : marron, lime, magenta, bleu clair) ?
2. Tapis de mousse : réutiliser `Plant_Moss_Rug_*` vanilla (déjà présent) ou en faire un 1/16 ?
3. Support : `FaceType: Full` seulement (vanilla) ou aussi dalles/escaliers/barrières (plus proche de MC « tout sauf l'air ») ?
4. Pot : quelles plantes (toutes les fleurs + pousses, ou une sélection) ? Posable sur le vide comme en Java, ou `Support.Down: Full` ?
5. Art : modèles pot + plante générés au build (fusion automatique modèle/texture) ou faits à la main ?
6. Accepter du code plugin pour le pot (voie B) ?

## 6. Inventaire retenu pour le pot (implémentation, 2026-09-28)

Liste exacte dans `tools/decorations/flower_pots.py` (`PLANTS`), reprise dans le fragment `plugin/src/subplugins/Decorations/hycolony/id-map.json` (section `flowerPots` : pot → objet plante → bloc `*HyColony_Flower_Pot_<C>_State_Definitions_<plante>`). Liste MC Java de référence (`https://minecraft.wiki/w/Flower_Pot`) : fleurs d'un bloc, pousses, champignons rouge et brun, fougère, buisson mort, cactus, bambou, azalées, propagule de palétuvier, racines et champignons du Nether, eyeblossoms.

**121 plantes Hytale retenues :**

| Catégorie MC | Équivalent Hytale |
|---|---|
| Fleurs d'un bloc (pissenlit, coquelicot, tulipes…) | les 60 `Plant_Flower_*` hors `Plant_Flower_Water_*` : `Bushy_*` (11), `Common_*` (24), `Flax_*` (6), `Hemlock`, `Orchid_*` (9), `Poisoned_Orange`, `Tall_*` (8). Les « Tall » de Hytale tiennent dans un bloc, contrairement aux grandes fleurs MC (tournesol…) qui ne vont pas en pot. |
| Pousses d'arbre (et bambou) | les 33 `Plant_Sapling_*`, dont `Plant_Sapling_Bamboo`. |
| Champignons rouge et brun | 15 `Plant_Crop_Mushroom_*` posés au sol : `Boomshroom_Small`, `Cap_*` (5), `Common_*` (3), `Flatcap_*` (2), `Glowing_Blue/Green/Red/Violet` (leur `Light` est recopié dans l'état). |
| Fougère | `Plant_Fern`, `Plant_Fern_Arid`, `Plant_Fern_Tall`. |
| Buisson mort | `Plant_Bush_Dead`, `Plant_Bush_Dead_Twisted`. |
| Cactus | `Plant_Cactus_1/2/3`, `Plant_Cactus_Ball_1`, `Plant_Cactus_Flat_1/2/3`, `Plant_Cactus_Flower`. |

**Sans équivalent Hytale :** azalée et azalée fleurie (les `Plant_Bush_*` vivants sont des buissons génériques, plus proches du `bush` MC, qui ne va pas en pot), propagule de palétuvier, racines et champignons carmin et biscornus (Nether), eyeblossoms, rose de Wither, torchflower (ces fleurs particulières sont couvertes par la catégorie « fleurs » ci-dessus, sans correspondance une à une).

**Plantes Hytale écartées :**

- Modèle qui descend loin sous le sol et sortirait sous le pot : `Plant_Bush_Dead_Tall` (`Shrub.blockymodel`, y min -57 unités), `Plant_Crop_Mushroom_Glowing_Orange` et `_Purple` (`Mushroom_Balls.blockymodel`).
- Fougères à texture grise teintée par le biome (`Plant_Fern_Forest`, `_Wet`, `_Wet_Big`, `_Winter` : `*_GS.png` + `BiomeTint`) : un état de bloc n'a qu'une teinte, qui colorerait aussi le pot. Géantes et troncs (`Plant_Fern_Giant`, `_Jungle`, `*_Trunk`) : plusieurs blocs.
- Champignons muraux `Plant_Crop_Mushroom_Shelve_*`, grands `Boomshroom_Large`, nénuphars `Plant_Flower_Water_*`, herbes `Plant_Grass_*` (l'herbe ne va pas en pot dans MC), `Prototype_*` (qualité développeur).

**Génération** (`python tools/decorations/generate.py`) : le modèle vanilla de la plante (plus haute variante pondérée de sa texture, `Tint` statique cuit dans la texture) est placé sur la terre du pot (y = 8 unités) et réduit uniformément par `min(0,75 × CustomModelScale, 24 / hauteur, 24 / largeur)` (MC `flower_pot_cross` : 16 px ramenés à 12). Les modèles « tapis » qui couvrent tout le bloc (`PATCHES`) sont ramenés à 16 unités de large. L'échelle multiplie `position`, `shape.offset` et `shape.stretch` de chaque nœud ; les UV restent en pixels de la texture, placée en (0, 0) de l'atlas, l'argile (`Clay_Smooth_Orange`) et la terre (`Soil_Dirt_Wet`) en dessous. **[in-game]** : que `stretch` ne s'applique qu'à la forme de son nœud (hypothèse tirée des modèles vanilla, où parent et enfant répètent leur miroir `-1`), et que les atlas non carrés (64×128…) s'affichent.

**Pas d'outil de validation** : l'éditeur `Hytale UI Editor` de l'utilisateur ne traite que les `.ui`, pas les `.blockymodel`. Les modèles générés ont été vérifiés par un rendu isométrique de contrôle (hors dépôt) et doivent être vus en jeu.

**Interaction (voie B)** : un `UseBlockEvent.Pre` **annulé** fait échouer `UseBlock` (`UseBlockInteraction.java:79-82`), ce qui déclenche le repli de l'objet tenu : `Block_Secondary` → `PlaceModeSelect` (pose la plante tenue à côté du pot), `Empty.Use` → `UseEntity` puis `BreakBlock` `Harvest`. Le système du pot **n'annule donc pas** l'événement : la racine `Simple` du pot s'exécute (état `Finished`, aucun repli). Un objet tenu reçoit `Use` de `UnarmedInteractions` `Empty` et, s'il a `PlayerAnimationsId: Block` (plantes), `Secondary` = `Block_Secondary` (`Item.java:1270-1285`, `InteractionContext.getRootInteractionId`, l. 626-660). Le pot déclare donc `Use` (touche d'interaction, main vide ou non) et `Secondary` (clic droit avec une plante).

### 6.1 Pots colorés et atlas partagés (2026-09-28, ajout demandé)

- **16 couleurs**, une par argile teinte `Soil_Clay_Smooth_<C>` (le pendant du terracotta de Minecraft, craftée avec des pétales) : `Black`, `Blue`, `Cyan`, `Green`, `GreenDark`, `Grey`, `Grey2`, `Lime`, `Orange`, `Pink`, `Purple`, `Red`, `Red2`, `White`, `Yellow`, `Yellow2`. Recette : 3 argiles lisses de la couleur → 1 `HyColony_Flower_Pot_<C>`, établi `Workbench`. Les argiles brutes `Soil_Clay_<C>` (15, non craftables, ramassées dans le monde) n'ont pas été retenues. Minecraft n'a qu'un pot : c'est un ajout demandé.
- **Textures** : un atlas par couleur (`Common/Blocks/HyColony/Flower_Pot/Atlas_<C>.png`, 512×1024), même disposition partout : les 117 textures distinctes des 121 plantes (262 144 px, exactement 512×512), puis l'argile et la terre, d'où 1024 de haut (puissance de deux). **Modèles** : un par plante et un vide, communs à toutes les couleurs. Chaque état ne change que `CustomModel` et reprend la texture de la couleur.
- **Héritage vérifié** : `CustomModel` et `CustomModelTexture` sont deux champs `appendInherited` indépendants (`BlockType.java:163-181`) ; les `State.Definitions` sont décodées avec `ContainedAssetCodec.Mode.INJECT_PARENT` (`StateData.java:94-106`) : un état hérite de son bloc tout ce qu'il ne redéfinit pas. Les états répètent quand même `CustomModelTexture`.
- **Limites de taille** : aucune côté serveur (`CommonAssetValidator` ne contrôle que l'extension et le dossier racine, `CommonAssetValidator.java:15-35`). La plus grande texture de bloc vanilla fait 256×256 (`ArcadeMachine_Texture.png`), 160×352 pour les toits. Notre 512×1024 est donc hors des tailles vues en vanilla : **[in-game]** affichage correct par le client (atlas de blocs côté client, limite inconnue).
- **UV dans l'atlas** : le décalage d'une face est son pivot ; le miroir retourne le rectangle par-dessus, puis l'angle le tourne autour (déduit des modèles vanilla : `Nettle` `SmallLeave` (32, 1) à 90° dans une texture 32 px de large lit u ∈ [17, 32]). Le générateur échoue si une face lit hors de sa texture (dans un atlas, elle lirait la voisine) : aucune des 121 ne le fait avec cette règle.
- Taille : 16 atlas d'environ 310 Ko, contre une texture par plante auparavant ; le zip du pack passe d'environ 1,0 Mo à environ 5 Mo.
