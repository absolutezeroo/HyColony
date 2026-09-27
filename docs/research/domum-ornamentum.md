# Domum Ornamentum : analyse et faisabilité sur Hytale 0.6.8

Recherche seule, rien n'est implémenté. Question : faut-il porter Domum Ornamentum (DO), et comment ?

## Sources

- **DO** : `github.com/ldtteam/Domum-Ornamentum`, branche `version/latest`, commit `82729d6` (2026-08-30). C'est la branche 1.20.1 active (`gradle.properties` : `minecraftVersion=1.20.1`). `release/1.20.1` est figée depuis 2024-03. MineColonies `version/main` dépend de `domumOrnamentumVersion=1.20.1-1.0.288-snapshot` (MC `gradle.properties`). Les chemins `DO:` ci-dessous partent de `src/main/java/com/ldtteam/domumornamentum/`, `DO-api:` de `src/api/java/com/ldtteam/domumornamentum/` et `DO-gen:` de `src/datagen/generated/domum_ornamentum/data/domum_ornamentum/`.
- **MineColonies** : `github.com/ldtteam/minecolonies`, branche `version/main`, commit `03ceece`. Les chemins `MC:` partent de `src/main/java/com/minecolonies/`.
- **Hytale** : sources décompilées `build/vineflower/hytale-server/com/hypixel/hytale/` (préfixe `H:`) et `release-0.6.8-Assets.zip` (préfixe `zip:`).
- Scripts d'analyse (`bp.py`, `combos.py`, qui lisent le NBT des `.blueprint`) : scratchpad de la session, non commités.

## Partie A : Domum Ornamentum

### A.1 Familles de blocs

Tous les blocs sont enregistrés dans `DO: block/ModBlocks.java:89-148`. Un bloc « texturé par matériau » implémente `IMateriallyTexturedBlock` et déclare une liste de **composants** (`SimpleRetexturableComponent(id, tag des matériaux valides, bloc par défaut[, optionnel])`, `DO: block/components/SimpleRetexturableComponent.java:16-28`). Chaque composant est un emplacement de matériau. Le maximum est **2** : aucun bloc n'en déclare 3 (grep de `new SimpleRetexturableComponent`, tableau ci-dessous).

Les tags de matériaux sont générés dans `DO-gen: tags/blocks/*.json`. `#domum_ornamentum:default` compte 91 entrées : pierres, briques, terracotta, béton, cuivre, laine, etc. (`default.json`).

| Famille (id) | Emplacements (id du composant → tag) | Formes et variantes | Source |
|---|---|---|---|
| Colombage (`plain`, `double_crossed`, `framed`, `side_framed`, `up_gated`, `down_gated`, `one_crossed_lr`, `one_crossed_rl`, `horizontal_plain`, `side_framed_horizontal`) | 2 : cadre `timber_frames_frame`, centre `timber_frames_center` | 10 blocs, un par `TimberFrameType` | `DO: block/decorative/TimberFrameBlock.java:53-54`, `block/types/TimberFrameType.java:13-22` |
| Colombage dynamique (`dynamic_timberframe`) | 2 : les mêmes | Le motif se calcule d'après les voisins (`DynamicTimberFrameBlockEntity` garde `offsets`, `centerBlock`, `frameBlock`) | `DO: block/decorative/DynamicTimberFrameBlock.java:52-53`, `entity/block/DynamicTimberFrameBlockEntity.java:70-96` |
| Bardeaux (`shingle`, `shingle_flat`, `shingle_flat_lower`, `shingle_steep`, `shingle_steep_lower`) | 2 : couverture `shingles_roof`, support `shingles_support` | 5 pentes ; chacune hérite des escaliers (`AbstractBlockStairs`) : droit, intérieur, extérieur | `DO: block/decorative/ShingleBlock.java:116-123`, `ModBlocks.java:97-101` |
| Demi-bardeau (`shingle_slab`) | 2 : `shingles_roof`, `shingles_support` | `SHAPE` : `top`, `one_way`, `two_way`, `three_way`, `four_way`, `curved` | `DO: block/decorative/ShingleSlabBlock.java:63, 277-283`, `block/types/ShingleSlabShapeType.java:18-23` |
| Mur de papier (`blockpaperwall`, `blocktiledpaperwall`) | 2 : cadre `paper_wall_frame`, centre `paper_wall_center` (défaut : verre) | Vitre (`AbstractBlockPane`) | `DO: block/decorative/PaperWallBlock.java:49-53` |
| Lumière encadrée (`vertical_light`, `crossed_light`, `framed_light`, `horizontal_light`, `fancy_light`, `four_light`, `center_light`) | 2 : cadre `timber_frames_frame`, centre `framed_light_center` (6 blocs lumineux) | 7 blocs | `DO: block/decorative/FramedLightBlock.java:88-95`, `block/types/FramedLightType.java:13-19` |
| Pilier (`blockpillar`, `blockypillar`, `squarepillar`) | 1 : `pillar_materials` | `COLUMN` : `pillar_base`, `pillar_capital`, `pillar_column`, `full_pillar` | `DO: block/decorative/PillarBlock.java:54, 60`, `block/types/PillarShapeType.java:11-14` |
| Poteau (`post`) | 1 : `post_materials` | `TYPE` : `plain`, `heavy`, `turned`, `pinched`, `double`, `quad` | `DO: block/decorative/PostBlock.java:53`, `block/types/PostType.java:16-21` |
| Panneau (`panel`) | 1 : `trapdoors_materials` | Trappe fixe ; `TYPE` = les 15 `TrapdoorType` | `DO: block/decorative/PanelBlock.java:59`, `block/types/TrapdoorType.java:13-27` |
| Porte (`vanilla_doors_compat`) | 1 : `doors_materials` | `TYPE` : `full`, `port_manteau`, `vertically_striped`, `waffle` | `DO: block/vanilla/DoorBlock.java:51-53`, `block/types/DoorType.java:13-16` |
| Porte ouvragée (`fancy_door`) | 2 : `fancy_doors_materials`, le second **optionnel** | `TYPE` : `full`, `creeper` | `DO: block/decorative/FancyDoorBlock.java:55-56` |
| Trappe (`vanilla_trapdoors_compat`) | 1 : `trapdoors_materials` (+ terracotta émaillée) | 15 `TrapdoorType` | `DO: block/vanilla/TrapdoorBlock.java:56` |
| Trappe ouvragée (`fancy_trapdoors`) | 2 : `fancy_trapdoors_materials` | `full`, `creeper` | `DO: block/decorative/FancyTrapdoorBlock.java:54-57` |
| Clôture, portillon, muret, escalier, dalle (`vanilla_fence_compat`, `vanilla_fence_gate_compat`, `vanilla_wall_compat`, `vanilla_stairs_compat`, `vanilla_slab_compat`) | 1 : `fence_materials`, `fence_gate_materials`, `wall_materials` (49), `stairs_materials` (47), `slab_materials` (47) | Formes vanilla (connexions, demi-dalles, escaliers) | `DO: block/vanilla/{Fence,FenceGate,Wall,Stair,Slab}Block.java` |
| « All brick » (`light_brick`, `dark_brick`, `light_brick_stair`, `dark_brick_stair`) | 1 : `all_brick_materials` (77) | Bloc plein, escalier | `DO: block/decorative/AllBrickBlock.java:49`, `AllBrickStairBlock.java:49` |
| **Sans matériau** : briques (`BrickType`, 10), blocs « extra » (`ExtraBlockType`, 27 : briques et ardoises colorées, chaume, papier, cactus), tapis flottants (16), tonneaux (2) | 0 : blocs ordinaires, sans entité de bloc | Aucune | `DO: block/decorative/BrickBlock.java:11`, `ExtraBlock.java:13`, `block/types/ExtraBlockType.java:21-47`, `BrickType.java:16-26` |

Les blocs « extra » et les briques DO servent eux-mêmes de matériaux (tags `shingles_roof`, `bricks`, défaut du bardeau = `brick_extra`, `ShingleBlock.java:116-122`).

### A.2 Fonctionnement technique d'un bloc texturé par matériau

- **Entité de bloc** : `MateriallyTexturedBlockEntity` porte un seul champ, `MaterialTextureData textureData` (`DO: entity/block/MateriallyTexturedBlockEntity.java:26`). `MaterialTextureData` est une `Map<ResourceLocation, Block>` : id du composant → bloc matériau (`DO-api: client/model/data/MaterialTextureData.java:18`).
- **Ce qui est sauvegardé par bloc posé** : le NBT `textureData` = `{ "<id composant>": "<id du bloc matériau>" }`, par exemple `{"minecraft:block/oak_planks":"minecraft:spruce_planks","minecraft:block/dark_oak_planks":"minecraft:white_terracotta"}` (`MaterialTextureData.java:55-65`, `MateriallyTexturedBlockEntity.java:69-73`). Au chargement, les clés qui ne sont pas des composants du bloc sont éliminées (l.76-101). Le type (porte, trappe, poteau…) est une propriété d'état du bloc, pas de l'entité.
- **Synchronisation client** : l'entité envoie tout son NBT (`getUpdateTag` = `saveWithId`, `ClientboundBlockEntityDataPacket`, l.44-66). Le client le passe au modèle par `ModelData` (`MATERIAL_TEXTURE_PROPERTY`, l.103-110).
- **Rendu (client)** : `MateriallyTexturedBakedModel` retexture à la volée le modèle de base. Chaque quad dont le sprite porte l'id d'un composant (par exemple le sprite `block/oak_planks` du modèle source) reçoit le sprite du bloc matériau choisi (`RetexturedBakedModelBuilder.java:95-190`, `needsRetexturing` l.132). Le résultat est mis en cache par couple (`MaterialTextureData`, `RenderType`) (`MateriallyTexturedBakedModel.java:45-60, 216-251`). Les ids de composants sont donc des **noms de textures placeholder** du modèle, et non des noms de matériaux.
- **Objet** : l'`ItemStack` porte le même NBT sous `tag.textureData`, plus `tag.type` pour les variantes. La pose copie le NBT de l'objet dans l'entité (`TimberFrameBlock.setPlacedBy`, `TimberFrameBlock.java:133-143`). La casse et le pick-block recréent l'objet depuis l'entité (`util/BlockUtils.java:65-77`). Un objet sans `textureData` reçoit des matériaux **aléatoires** à la pose (`MateriallyTexturedBlockEntity.java:37-39`).

### A.3 L'Architect's Cutter (établi de l'architecte)

- **Fabrication de l'établi** : recette façonnée : 1 lingot de fer, 3 dalles de pierre, 3 bûches (`DO-gen: recipes/architectscutter.json`).
- **Emplacements d'entrée** : autant que le plus grand nombre de composants d'un bloc, soit **2** (`DO: container/ArchitectsCutterContainer.java:49`, `MateriallyTexturedBlockManager.getMaxTexturableComponentCount`). Un emplacement n'accepte que les blocs du tag du composant de même rang, pour au moins un bloc DO, ou pour la variante choisie (l.118 ; `MateriallyTexturedBlockManager.java:62-91`).
- **Interface** (`client/screens/ArchitectsCutterScreen.java`, `util/GuiConstants.java`) : fond de 242 × 202. Une rangée défilante de **groupes** (onglets : `avanilla`, `btimberframe`, `cshingle`, `ddoor`, `etrapdoor`, `fpanel`, `gpillar`, `hpaperwall`, `ilight`, `jbrick`, `kpost`, triés par `SortedBlocks`), puis une rangée défilante de **variantes** du groupe (10 visibles). Les 2 entrées sont à gauche, la sortie à droite. Les libellés sont `domum_ornamentum.group`, `.variant` et la description du matériau attendu par emplacement (l.188-251). Le choix d'un groupe ou d'une variante passe par `clickMenuButton` (l.196).
- **Liste des sorties** : `getOrComputeItemGroups` parcourt tous les objets `IDoItem`, les regroupe par `getGroup()` et leur met les matériaux par défaut (`ModBlocks.java:370-427`). Les sorties possibles sont donc **toutes les variantes**, et les matériaux posés en entrée seulement les texturent.
- **Recette** (`DO-api: recipe/architectscutter/ArchitectsCutterRecipe.java`) :
  - `matches` vérifie que chaque emplacement de composant contient un bloc du tag valide (l.58-86) ;
  - `assemble` construit l'objet avec `textureData` = {composant i → bloc de l'emplacement i}, ajoute le NBT `type` de la variante, puis fixe la quantité à `max(nombre de composants, count)` (l.88-134). `count` vaut -1 s'il est absent du JSON (`ArchitectsCutterRecipeSerializer.java:36, 44`) ;
  - un composant optionnel peut rester vide (l.108-109), mais `matches` ne le tolère pas (l.70-83). Incohérence de DO à vérifier en jeu si on la reproduit.
- **Consommation** : on retire **1** de chaque emplacement requis par prise de résultat (l.134-160, `remove(1)` l.152).
- **Quantités produites** (82 recettes `architects_cutter` générées, `DO-gen: recipes/*.json`) :

| Sortie | Entrées | Quantité |
|---|---|---|
| Colombage (chaque type), bardeaux (5 pentes), demi-bardeau | 2 | 4 |
| Mur de papier (2) | 2 | 6 |
| Colombage dynamique, lumière encadrée, porte ouvragée, trappe ouvragée | 2 | 2 |
| Panneau (chaque type) | 1 | 4 |
| Dalle | 1 | 2 |
| Pilier (3), poteau (chaque type), all brick (et escalier), porte, trappe, clôture, portillon, muret, escalier | 1 | 1 |

Les briques et blocs « extra » ont des recettes vanilla : par exemple `beige_bricks` = 2 gravier + 2 briques → 4, sans forme (`DO-gen: recipes/beige_bricks.json`).

### A.4 Dépendance de MineColonies à DO

**Usage dans les styles.** Analyse du NBT des 9 505 `.blueprint` de `src/main/resources/blueprints/minecolonies/` (script `bp.py`) :
- **6 458 plans sur 9 505 (68 %)** contiennent au moins un bloc DO ;
- **1 149 197 blocs DO sur 7 730 850 blocs non vides, soit 14,9 %** ;
- par style : `ancientathens` 52,6 %, `sandstone` 36,1 %, `lostcity` 34,9 %, `pagoda` 27,9 %, `colonial` 27,1 %, `caledonia` 20,2 %, `acacia` 17,8 %, `original` 17,1 %, `medieval*` environ 11 %, mais `darkoak` 0,7 %, `truedwarven` 1,1 %, `nordic` 2,8 % ;
- blocs les plus posés : `shingle` 245 026, `panel` 122 452, `vanilla_stairs_compat` 70 881, `framed` 65 724, `plain` 52 245, `double_crossed` 49 113, `vanilla_slab_compat` 42 715, `blockpaperwall` 41 587, puis les briques beiges et sable (sans matériau) ;
- **3 580 combinaisons distinctes (bloc DO, matériaux)**, sans compter les variantes `type`, avec **261 matériaux distincts** (script `combos.py`). Par bloc : `framed` 325, `shingle` 321, `double_crossed` 296, `shingle_slab` 215, `panel` 199, `fancy_door` 172…

**Code DO dans MC** (19 fichiers importent `com.ldtteam.domumornamentum`) :
- **Pose par le constructeur** : `MC: core/placementhandlers/DoBlockPlacementHandler.java` gère tout `IMateriallyTexturedBlock` (sauf l'étagère MC). Il pose l'état, puis recharge l'entité de bloc du plan et appelle `setPlacedBy` avec l'objet reconstitué. `getRequiredItems` demande **l'objet matérialisé depuis l'entité de bloc du plan** (l.129). `doesWorldStateMatchBlueprintState` compare aussi les données de l'entité (`compareBEData`).
- **Simplification pour le constructeur** : `getCorrectDOItem(item, state, complete)` avec `complete = !fancyPlacement()` (l.129, 158). Or `BuildingStructureHandler.fancyPlacement()` renvoie toujours `true` (`MC: core/entity/ai/workers/util/BuildingStructureHandler.java:344-347`). Le constructeur demande donc la trappe, le panneau et la trappe ouvragée en `FULL` (l.170-178), le poteau en `PLAIN`, et **tout colombage (dynamique compris) comme le colombage `framed`** (`getTimberFrames().get(2)`, l.186), toujours avec les matériaux du plan. Les portes gardent leur type. En contrepartie, la pose remet le vrai état du plan.
- **Requête et comparaison** : la requête est `new Stack(stack, n, 1)`, donc `matchNBT = true` (`MC: core/colony/buildings/modules/BuildingResourcesModule.java:372`, `api/colony/requestsystem/requestable/Stack.java:101-104`). La comparaison `ItemStackUtils.compareItemStacksIgnoreStackSize` ne compare que les clés NBT listées par objet dans `CHECKED_NBT_KEYS` (`MC: api/util/ItemStackUtils.java:573-605`). Pour DO, ce sont `textureData` et ses enfants (les ids de composants), plus `type` pour les blocs à variantes (`src/datagen/generated/minecolonies/data/minecolonies/compatibility/itemnbtmatching.json`, entrées `domum_ornamentum:shingle`, `vanilla_trapdoors_compat`), générées par `MC: core/generation/ItemNbtCalculator.java:66-74`. Un bardeau argile + chêne ne satisfait donc pas une requête de bardeau argile + sapin.
- **Qui fabrique** : un module `AbstractDOCraftingBuildingModule` (type de fabrication `domum_ornamentum:architects_cutter`, `MC: core/colony/buildings/modules/AbstractDOCraftingBuildingModule.java:19-45`) chez 5 artisans. Chacun ne prend que les recettes dont un ingrédient passe son validateur (`BuildingModules.java:312-373`) :
  - scierie : planches et bûches ;
  - tailleur de pierre, souffleur de verre, archer : leurs tags ;
  - mécanicien : tout le reste (`BuildingMechanic.java:114-127`).
- **Apprentissage** : la fenêtre `DOCraftingWindow` permet au joueur de choisir une requête DO ouverte (par exemple celle du constructeur) pour pré-remplir la recette (`MC: core/client/gui/modules/building/DOCraftingWindow.java:110-135`). `DomumOrnamentumUtils` lit `textureData` d'une pile ou d'un NBT (`MC: core/util/DomumOrnamentumUtils.java:24-96`). Les fenêtres d'artisanat vanilla refusent les requêtes DO (`WindowCrafting.java:201`, `WindowFurnaceCrafting.java:157`).
- **Divers** : `SchemAnalyzerUtil.java:145-152` compte les matériaux des blocs DO dans le coût d'un plan. `WorkerUtil.java:155` choisit l'outil d'un bloc DO. `TileEntityRack` (étagère MC) est elle-même un bloc texturé par matériau.

## Partie B : faisabilité sur Hytale 0.6.8

### B.5 Texture choisie par bloc posé : **impossible** (hors teinte de biome par colonne)

- **Le client ne reçoit, par bloc, qu'un id, un `filler` et une `rotation`.**
  - Le paquet de pose `ServerSetBlock` contient `x, y, z, blockId, filler, rotation` (`H: protocol/packets/world/ServerSetBlock.java:22-27`) ; `SetBlockCmd` est identique (`SetBlockCmd.java:17-20`).
  - Le paquet de tronçon `SetChunk` sérialise 3 palettes : blocs, filler et rotation (`H: server/core/universe/world/chunk/section/BlockSection.java:769-797`).
  - Aucun paquet ne porte de texture ou de donnée de rendu par bloc : les seuls paquets `packets/world` liés à une position de bloc sont `UpdateBlockDamage` et `SetBlockMusicEmitter`.
- **La texture et la teinte sont des propriétés du `BlockType`, envoyées une fois pour toutes.**
  - Côté protocole, `BlockType` porte `model`, `modelTexture[]`, `cubeTextures[]`, `cubeSideMaskTexture`, `tint` et `biomeTint` (`H: protocol/BlockType.java:38-87`).
  - `Tint` vaut une couleur par face (`Tint.java:17-22`). Côté serveur, `Tint*` est un tableau de couleurs, mais seul l'élément `[0]` est envoyé (`H: server/core/asset/type/blocktype/config/BlockType.java:648-655, 1229-1239`).
  - Les tableaux `Textures` et `CustomModelTexture` pondérés (`Weight`) sont des **variantes aléatoires** : aucun champ du serveur ne choisit l'entrée pour un bloc donné. Le choix est fait par le client **[in-game]** pour le critère exact (position ?).
  - Un modèle (`CustomModel`) n'a qu'une texture à la fois (`ModelTexture` = `texture` + `weight`, `H: protocol/ModelTexture.java:19-20`).
- **Seule donnée variable par position** : la teinte de biome, **par colonne (x, z)** et non par bloc (`BlockChunk.tint`, `getTint(x, z)` et `SetChunkTintmap`, `H: server/core/universe/world/chunk/BlockChunk.java:64, 299-306, 506`). Elle n'est pas utilisable pour choisir un matériau.
- **Données de bloc persistées (équivalent de l'entité de bloc)** : elles existent côté serveur, mais ne sont pas rendues.
  - Un `BlockType` peut déclarer `BlockEntity` (un `Holder<ChunkStore>` de composants, `BlockType.java:772-777`).
  - La pose depuis un objet copie `metadata.BlockHolder` de l'`ItemStack` dans les composants du bloc (`H: server/core/modules/interaction/BlockPlaceUtils.java:300-311`). On peut donc stocker un `textureData` à la DO **côté serveur**, mais rien ne le transmet au rendu du client.
- **Les états (`State.Definitions`)** créent des `BlockType` distincts (`*<id>_State_Definitions_<nom>`, voir `connected-blocks.md`). Ce sont des ids de plus, pas une texture paramétrée.
- **Contournement théorique par entité** : une entité peut porter un `Model` avec sa propre `texture` et des `attachments` (`H: protocol/Model.java:23-44`). Poser une entité par bloc DO (jusqu'à 245 000 bardeaux dans les plans MC) n'est pas réaliste. Collisions, éclairage et performances sont inconnus **[in-game]**.

**Verdict : impossible** de choisir la texture d'un bloc posé à l'exécution. Une combinaison de matériaux se représente uniquement par **un `BlockType` distinct** (un id par combinaison et par forme).

### B.6 Générer un asset par combinaison

- **Rien ne l'interdit dans le code consulté.**
  - L'id global de bloc est un `int` sur le réseau : la palette `Short` d'une section stocke `externalId` en `INT_LE` (`H: server/core/universe/world/chunk/section/palette/ShortSectionPalette.java:345-354`).
  - La limite de 65 536 (`MAX_SIZE`, l.27-28) vaut **par section**, pas pour l'ensemble des blocs.
  - `UpdateBlockTypes` porte `maxId` et une `Map<Integer, BlockType>` (`H: protocol/packets/assets/UpdateBlockTypes.java:29-36`).
  - Je n'ai trouvé aucune limite du nombre de `BlockType`. Le jeu de base compte déjà **3 657** objets JSON (`zip: Server/Item/Items/**`), plus les états générés.
- **Le motif vanilla est exactement celui-là** : un même modèle pour toute une famille, et une texture par matériau. Par exemple `Wood_Softwood_Roof` = `CustomModel: Blocks/Structures/Roofs/Slope_Hay.blockymodel` + `CustomModelTexture: Slope_Hay_Textures/Softwood.png`, avec des états `Corner_Right`, `Corner_Left`… (`zip: Server/Item/Items/Wood/Softwood/Wood_Softwood_Roof.json`). Une texture de toit pèse environ 8 Ko (`Slope_Hay_Textures/Softwood.png` : 7 855 octets).
- **Deux matériaux dans un bloc** : un modèle n'a qu'une texture (B.5). Il faut donc **composer une PNG par combinaison** au moment de la génération : cadre du matériau A et remplissage du matériau B. Pour un cube, `Textures` accepte 6 faces, chacune d'une seule image. Il faut aussi une **icône par objet** (`Icon`, par exemple `Icons/ItemsGenerated/*.png`). Ordre de grandeur pour les 3 580 combinaisons des plans MC : quelques dizaines de Mo de PNG, et des milliers de `BlockType` de plus avec les états. La tenue de l'atlas de textures du client est inconnue **[in-game]**.
- **Génération à l'exécution (comme DO, à la demande)** : les mécanismes existent.
  - `CommonAssetModule.addCommonAsset(pack, asset)` diffuse l'asset aux joueurs connectés (`H: server/core/asset/common/CommonAssetModule.java:187-221`) et `sendAssets` l'envoie en `AssetInitialize`/`AssetPart`/`AssetFinalize` (l.542-561).
  - `AssetStore.loadAssets(packKey, List<T>)` charge des assets à chaud (`H: assetstore/AssetStore.java:464-474`), et `UpdateType.AddOrUpdate` existe (`H: protocol/UpdateType.java:6-8`).
  - Mais `addCommonAsset` envoie une **notification à tout l'univers** à chaque ajout (l.203-208) : c'est l'outil de rechargement de l'éditeur d'assets. Les seules implémentations de `CommonAsset` sont `FileCommonAsset` et `ResourceCommonAsset` : aucune ne vient de la mémoire. La **génération au build** du pack d'assets du plugin (`IncludesAssetPack: true`, `plugin/src/main/resources/manifest.json`) reste possible.
- **Résultat en jeu (2026-09-27, expérience `/hycolony dotest`, retirée depuis) : la génération à l'exécution n'est pas fiable.** Après chaque démarrage du serveur, seuls **les un ou deux premiers** blocs générés s'affichent sans reconnexion ; les suivants restent roses et noirs (journaux du 2026-09-27, par exemple 7 essais : 2 bons puis des cubes roses). Après une reconnexion, **tous** s'affichent. La cause est côté client (code fermé), non trouvée.
  - Séquence essayée en dernier (commit `d67fe4c`, `--after=true`) : `addCommonAsset` (la PNG part), puis `BlockType.getAssetStore().loadAssets(..., AssetUpdateQuery.DEFAULT)` (`UpdateBlockTypes` part), puis mise en file de la pose, puis `RequestCommonAssetsRebuild`. Les « 15 réussites » rapportées étaient réparties sur 8 démarrages (1 à 4 essais chacun), d'où l'illusion d'un ordre qui marche.
  - Autres séquences essayées, qui échouent dès le premier bloc :
    - aucune demande de reconstruction ;
    - demande envoyée juste après la texture, avant le `BlockType`, même avec 2 à 3 s d'écart (`--delay`) et des PNG au hash unique ;
    - `sendAsset(asset, true)` (texture et demande dans le même lot), suivi d'un `BlockType` chargé avec seulement `blockTextures` et `modelTextures`.
  - Chaque ajout fait scintiller l'image (reconstruction complète des assets du client) et envoie une notification d'asset. Pour DO, il faudra regrouper les créations.
  - Le chargement doit se faire **hors du thread du monde** : `World.tick` tient le verrou de lecture de `AssetRegistry.ASSET_LOCK` et `loadAssets` demande son verrou d'écriture (blocage définitif, voir `docs/research/plugin-b-api.md` § 17).

### B.7 Données propres à une pile d'objets

**Oui.**
- `ItemStack` porte un `BsonDocument metadata` (`H: server/core/inventory/ItemStack.java:53, 76`), avec le constructeur `ItemStack(itemId, quantity, metadata)` (l.96) et `withMetadata(...)` (l.544-577).
- Deux piles de métadonnées différentes **ne s'empilent pas** : `isStackableWith` et `isEquivalentType` comparent `metadata` (l.622-661).
- Le nom et la description affichés peuvent venir des métadonnées (`ItemDisplayMetadata`, clé `ItemDisplay`, « Per-instance display overrides », `H: server/core/asset/type/item/config/metadata/ItemDisplayMetadata.java` ; `ItemStack.getDisplayName` l.694-697). Le protocole transmet `metadata` au client (`H: protocol/ItemWithAllMetadata.java:19-26`).
- L'**icône** et le **modèle** de l'objet restent ceux de l'`itemId` : aucun champ par pile. Un « bardeau chêne + brique » ne peut donc pas avoir sa propre icône sans avoir son propre `itemId`.
- Rappel (`plugin-b-api.md`) : les retraits de HyColony comparent par `isStackableWith`, et le cœur ne garde pas les métadonnées (`citizen-inventory-window.md`).

### B.8 Blocs vanilla qui couvrent déjà les familles DO

D'après les noms de `zip: Server/Item/Items/**` et la liste des catégories de l'établi de construction (`zip: Server/Item/Items/Bench/Bench_Builders.json`). Cet établi est un établi `StructuralCrafting`, catégories `Roof`, `Beam`, `Pillar`, `Wall`, `Door`, `Trapdoor`, `Window`, `VillageWall`… : c'est l'**équivalent vanilla du cutter**.

| Famille DO | Équivalent Hytale 0.6.8 | Couverture |
|---|---|---|
| Bardeaux (5 pentes) | `*_Roof`, `*_Roof_Flat`, `*_Roof_Shallow`, `*_Roof_Steep`, `*_Roof_Hollow`, avec des états de coins. Existent pour 11 bois (`Wood_*_Roof*`), les briques et pavés de roche (`Rock_*_Brick_Roof*`, `Rock_*_Cobble_Roof*`), le tissu (`Cloth_Roof*`), le métal (`Metal_*_Roof`) et le `Build_*` coloré | Bonne pour les formes, **un seul matériau** (pas de support distinct) |
| Demi-bardeau | Aucun équivalent direct (`*_Roof_Flat` s'en approche) | Partielle |
| Colombage (10 motifs + dynamique) | `Wood_Village_Wall_*_Full` : 16 couleurs d'enduit, bois fixe, `ConnectedBlockRuleSet` à états `Bottom`/`Middle`/`Top` (`zip: Server/Item/Items/Wood/Village/Wood_Village_Wall_White_Full.json`) | Partielle : un seul bois, pas de croix ni de diagonales |
| Pilier | `Rock_*_Brick_Pillar_Base` / `_Middle` (16 roches), `Rock_*_Cobble_Pillar_*` (4) | Bonne pour la pierre, rien en bois |
| Poteau | `Wood_*_Beam`, `Rock_*_Beam` (71 poutres) | Partielle (un profil) |
| Panneau (15 motifs) | Rien d'équivalent. Les trappes `Furniture_*_Trapdoor` (15, une par style) sont les plus proches | Faible |
| Porte, trappe (+ ouvragées) | `Furniture_*_Door` / `_Trapdoor` par style (Crude, Tavern, Lumberjack, Kweebec, Desert…) | Bonne en nombre, matériaux fixes par style |
| Clôture, portillon | `Wood_*_Fence`, `Wood_*_Fence_Gate` (11 bois), `Metal_*_Fence`, `Build_*_Fence` | Bonne |
| Muret | `Rock_*_Brick_Wall`, `Rock_*_Cobble_Wall` (15 chacun) | Bonne pour la pierre |
| Escalier, dalle | `*_Stairs` (163), `*_Half` (212) sur bois, roches, laine, `Build_*` | Bonne |
| Mur de papier (vitre) | `Furniture_*_Window`. **Pas de verre en 0.6.8** (`plugin-b-api.md`) | Faible |
| Lumière encadrée | `Furniture_*_Lantern` (15) | Faible (pas de bloc plein lumineux encadré) |
| Briques DO, blocs « extra » | Briques `Rock_*_Brick` (16 roches), `Soil_Clay_*`, laine colorée | Moyenne (teintes différentes) |
| Tapis flottant, tonneau | `Furniture_Royal_Magic_Carpet`, `Furniture_*_Barrel` (non craftables, voir `prefab-obtainability.md`) | Faible |

## Synthèse

**Verdict.** Un portage **fidèle** de DO est **impossible** sur Hytale 0.6.8. Le cœur de DO est un matériau choisi par bloc posé et rendu par retexture côté client. Or le client Hytale ne reçoit par bloc qu'un id, une rotation et un filler (B.5). Chaque combinaison devrait devenir un `BlockType` distinct, avec texture composée et icône.

L'enjeu est pourtant réel dans MC : 68 % des plans et 14,9 % des blocs des styles utilisent DO, avec 3 580 combinaisons. Mais **HyColony n'utilise pas les plans MC**. Ses styles sont des prefabs Hytale en blocs vanilla (`hycolony/styles.json`), qui ne contiennent aucun bloc DO. Le constructeur n'a donc aujourd'hui **aucune requête DO** à satisfaire.

**Options réalistes.**

1. **Ne pas porter DO (recommandé à court terme).** Les styles restent des prefabs vanilla, et l'établi de construction vanilla (`Bench_Builders`, `StructuralCrafting`) joue le rôle du cutter pour les toits, poutres, piliers, murs de village, portes et trappes. Écart à documenter (`Deviation from MC: no Domum Ornamentum; styles use vanilla Hytale block sets`).
   - Avantages : aucun coût, aucun risque client.
   - Inconvénient : les artisans MC qui fabriquent au cutter (scierie, tailleur de pierre, mécanicien…) n'ont pas cette part de recettes. On peut la remplacer par les recettes `StructuralCrafting` vanilla, à étudier avec le système d'artisanat.
2. **DO restreint, généré au build.** Un générateur (script de build) produit, pour une liste **fermée** de combinaisons (par exemple celles dont nos futurs styles ont besoin), les `BlockType` + PNG composées + icônes + recettes d'un « établi d'architecte » HyColony. On réutilise les modèles vanilla (toits, poutres, murs de village) et on ne compose que les textures. Les objets sont des `itemId` distincts, sans métadonnées : les requêtes du constructeur restent de simples ids, sans comparaison de NBT.
   - Avantages : fidèle en esprit (cadre + remplissage, quantités du cutter reprises de A.3), robuste.
   - Inconvénients : le nombre de combinaisons est fixé d'avance (pas de « n'importe quel matériau »), le pack grossit, et il faut produire des modèles pour les formes sans équivalent (panneaux, demi-bardeaux, 10 motifs de colombage).
3. **DO complet, généré à l'exécution.** On crée un `BlockType` et sa texture à la première fabrication d'une combinaison (`AssetStore.loadAssets`, `CommonAssetModule.addCommonAsset`). C'est le plus proche de DO. Mais le rechargement à chaud est un outil d'éditeur : notifications à tous les joueurs, reconstruction des assets côté client, persistance des ids générés entre redémarrages. **Écarté après le test en jeu du 2026-09-27** (B.6) : seuls les un ou deux premiers blocs de chaque session s'affichent sans reconnexion.
4. **Métadonnées seules** (`BlockHolder` + `textureData` côté serveur, rendu d'un bloc générique). La logique MC (requêtes par matériaux) est fidèle, mais le bloc posé n'a **pas l'apparence** du matériau. Peu d'intérêt pour un mod de décoration.

**Questions à tester en jeu.**
- Le client tient-il plusieurs milliers de `BlockType` et de textures de plus (temps de chargement, atlas) ? **[in-game]**
- Une variante pondérée de `Textures` / `CustomModelTexture` est-elle choisie par position (stable) côté client ? **[in-game]**
- `AssetStore.loadAssets` + `CommonAssetModule.addCommonAsset` en cours de partie : le nouveau bloc s'affiche-t-il sans reconnexion, et à quel coût ? **[in-game]**
- Un objet sans icône propre (icône d'un autre `itemId`) est-il accepté par la validation des assets ? **[in-game]**
