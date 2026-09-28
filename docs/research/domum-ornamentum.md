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
  - Séquence essayée en dernier (commit `d67fe4c`, `--after=true`) : `addCommonAsset` (la PNG part), puis `BlockType.getAssetStore().loadAssets(..., AssetUpdateQuery.DEFAULT)` (`UpdateBlockTypes` part), puis mise en file de la pose, puis `RequestCommonAssetsRebuild`. Les « 15 réussites » rapportées venaient de 8 démarrages (1 à 4 essais chacun), qui couvraient plusieurs variantes du code (`d67fe4c` à `60c1f33`, d'après l'heure des démarrages), d'où l'illusion d'un ordre qui marche.
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

### B.9 Banc d'essai du 2026-09-28 : défauts et causes

Banc : commit `bd451ec`, générateur `tools/domum/`, sous-plugin `plugin/src/subplugins/DomumOrnamentum/`, modèles DO du commit `82729d6` (cache `build/domum-cache/`). Blockstates DO lus sur `raw.githubusercontent.com/ldtteam/Domum-Ornamentum/82729d6…/src/datagen/generated/domum_ornamentum/assets/domum_ornamentum/blockstates/*.json` (abrégé `DO bs:`). Les chemins `G:` sont relatifs à `plugin/src/subplugins/DomumOrnamentum/Common/Blocks/HyColony/DO/`, `zip:` renvoie à l'archive d'assets 0.6.8. Défauts signalés en jeu par l'utilisateur.

**Cause commune.** Le générateur convertit **chaque fichier de modèle DO en un objet statique**. Or un bloc DO est **assemblé par son blockstate** : pièces multipart, forme choisie selon les voisins, rotation par `facing`, moitié haute ou basse. Il prend aussi un **matériau par composant**. Le générateur ne reprend rien de cela (`tools/domum/source.py:49-66`, `generate.py:44-51`). La plupart des défauts en découlent.

1. **Textures qui scintillent : faces coplanaires qui se chevauchent (z-fighting), présentes dans la géométrie DO.**
   - Preuve : j'ai projeté toutes les faces de chaque `.blockymodel` généré dans le repère du bloc, avec l'orientation de chaque nœud, puis intersecté les polygones de même plan et de même sens.
     - **23 modèles** ont des faces exactement coplanaires qui se recouvrent avec une **image différente** : tous les bardeaux (`Shingle_*`, `ShingleSlab_One_Way`/`_Top`), `Barrel_*` (72 à 84 paires), `TimberFrame_Dynamic_Timberframe` (143), `FancyTrapdoor_Creeper`, `Trapdoor_Horizontal_Bars`/`_Vertical_Bars`.
     - **34 modèles** ont des faces parallèles à moins de 0,2 unité l'une de l'autre, qui se recouvrent. Cela vient des décalages de 0,01 px du modèle DO (0,02 unité), par exemple les poutres du colombage à `z = 0.01…0.99` devant le centre (`timber_frame/double_crossed_spec.json`, éléments 1 à 5). C'est aussi le cas des `AllBrick_*`, des `FramedLight_*`, des portes et des bardeaux.
   - Le convertisseur recopie la géométrie telle quelle (`convert.py:107-129`) : ces recouvrements viennent de DO. Ce qui est **[in-game]**, c'est de savoir pourquoi Minecraft les masque (précision de profondeur ? mêmes texels ?) et Hytale non.
   - Les bardeaux DO débordent aussi du bloc : `z` de -4 à 20 px (`shingle/straight_spec.json`). Deux bardeaux voisins superposent donc des éléments identiques, avec les mêmes UV (vérifié sur la paire `[0.82,3.29,16]` / `[0.82,3.29,-4]`).
   - `Opacity: "Transparent"` (`generate.py:75`) n'est **pas** la cause démontrée : 901 objets vanilla `DrawType: Model` l'utilisent (établis, meubles, portes). En revanche, les blocs de structure vanilla (toits `Wood_Softwood_Roof`, piliers `Rock_Stone_Brick_Pillar_*`) ne déclarent pas d'`Opacity` et restent donc à `Solid`, la valeur par défaut (`H: server/core/asset/type/blocktype/config/BlockType.java:875`).
   - Correctif : au moment de la conversion, supprimer ou fusionner les faces de même plan et de même sens qui se recouvrent, et pousser de ≥ 0,1 unité les faces presque coplanaires, ou retirer la face cachée. Essayer aussi `Solid` sur les formes de structure **[in-game]**.
2. **Icônes fausses.** `tools/domum/icon.py` est un rendu isométrique maison : vue depuis +X+Y+Z (`VIEW`, l.23), faces arrière écartées (l.33), tri du peintre sur le centre des faces (l.43, l.66). Il reproduit fidèlement les défauts du modèle, et en ajoute :
   - les bardeaux montrent leur dos (point 9) ;
   - un pilier sans dessus apparaît ouvert (point 4) ;
   - les pièces de mur de papier ne sont que des points ou des lattes (point 3) ;
   - le centre des lumières est en bois (point 7) ;
   - le tri par centre de face se trompe sur les formes imbriquées (`ShingleSlab_*`) ;
   - le cadrage « remplir la boîte » (`MARGIN`) agrandit un poteau fin autant qu'un cube.

   Vanilla ne dessine pas ses icônes à la main. `Icons/ItemsGenerated/*.png` (64×64) est produit par **l'éditeur d'assets du client**, avec la caméra `IconProperties` : bloc par défaut `Scale 0.58823`, `Translation (0, -13.5)`, `Rotation (22.5, 45, 22.5)` (`H: builtin/asseteditor/AssetSpecificFunctionality.java:537-585`). Le champ `Icon` porte `UIEditor.Icon("Icons/ItemsGenerated/{assetId}.png", 64, 64)` et `UIRebuildCaches(ITEM_ICONS)` (`H: server/core/asset/type/item/config/Item.java:99-103`). Le serveur ne sait pas rendre une icône : la génération par l'éditeur d'assets en jeu reste à essayer **[in-game]**. Correctif côté script : corriger d'abord les modèles (points 1, 3, 4, 8), puis garder `icon.py` ou passer par l'éditeur d'assets.
3. **Murs de papier (`paperwall`, `tiledpaperwall`) cassés : ce sont des pièces de multipart converties une à une.**
   - `DO bs: blockpaperwall.json` assemble toujours `blockpaperwall_post`, plus, pour chacune des 4 directions, `…_side_<dir>` si la direction est reliée ou `…_side_off_<dir>` sinon (`uvlock: true`). C'est une vitre MC (`AbstractBlockPane`).
   - La pièce `post` seule ne contient que deux cubes de 2×1×2 px, en bas et en haut (`paperwall/blockpaperwall_post_spec.json`). Chaque `side_off` est une face de 2×14 px, d'épaisseur nulle, sur un côté du poteau (`[7,1,7]→[9,15,7]`). Le convertisseur en fait un quad simple face (`convert.py:140-148`).
   - Le générateur en a fait **9 + 5 objets séparés** (`PaperWall_Post`, `PaperWall_Side_*`, `PaperWall_Side_Off_*`, `TiledPaperWall_*`), dont aucun n'est un bloc complet.
   - Correctif : composer les pièces avant la conversion. Au minimum : poteau + 4 `side_off` (isolé), et poteau + `side_north` + `side_south` (droit). Pour la connexion, passer par un `ConnectedBlockRuleSet` à états, comme les murs de village (`connected-blocks.md`).
4. **Piliers sans dessus ni dessous : DO omet les faces qu'un voisin cache.**
   - `DO bs: squarepillar.json` choisit `pillar_column`, `pillar_base`, `pillar_capital` ou `full_pillar` selon les piliers au-dessus et en dessous (`PillarBlock.getStateForPlacement` / `updateShape`, `…/block/decorative/PillarBlock.java:143-154, 254-268` de DO).
   - Le fût n'a donc **aucune face `up`/`down`** : `squarepillar_pillar_column_spec.json` n'a que N/E/S/O, et les 8 éléments de `blockpillar_pillar_column_spec.json` n'ont que des faces latérales. `base` et `capital` n'ont pas de face à l'extrémité du fût (compte des faces : `up 3 / down 1` et `up 1 / down 3`).
   - Le convertisseur n'écrit que les faces présentes (`convert.py:111-117, 138`), et Hytale ne dessine pas une face absente de `textureLayout`. C'est l'usage vanilla : 3 116 boîtes sur 7 306 des `Common/Blocks/**.blockymodel` ont moins de 6 faces. Les `Full_Pillar` sont fermés : le trou ne touche que `Column`, `Base` et `Capital`.
   - Vanilla ferme toujours les bouts exposés. `Pillar_Middle.blockymodel` est une seule boîte 26×32×26 avec ses 6 faces. `Pillar_Base.blockymodel` a le haut de son fût (`Top1` : pas de `bottom`, mais un `top`). Ce sont deux objets distincts, sans règle de connexion (`VariantRotation: DoublePipe`) (`zip: Common/Blocks/Structures/Pillars/`, `Server/Item/Items/Rock/Stone/Rock_Stone_Brick_Pillar_*.json`).
   - Correctif : ajouter les faces de bout manquantes, avec la texture et l'UV par défaut de l'élément. Attention à `blockpillar` : ses 8 lames se superposent en un 16-gone. Des dessus par lame seraient coplanaires, donc z-fighting (point 1). Il faut un seul couvercle.
5. **Portes qui ne s'ouvrent pas** (attendu). Ce qu'exige une porte vanilla (`zip: Server/Item/Items/Furniture/Crude/Furniture_Crude_Door.json`) :
   - `IsDoor: true`, `Interactions.Use: "Door"`, `HitboxType: "Door"` ;
   - des états `OpenDoorIn` / `OpenDoorOut` / `CloseDoorIn` / `CloseDoorOut` / `DoorBlocked`, avec `CustomModelAnimation` `Blocks/Animations/Door/Door_*.blockyanim`, `HitboxType` et `InteractionHitboxType` `Door_Open_*` ;
   - `ConnectedBlockRuleSet` `CustomTemplate` / `DoorConnectedBlockTemplate` (portes doubles), `VariantRotation: NESW`.

   Les animations visent des **noms de nœuds**. `Door_Open_In.blockyanim` anime `Door`, `Door2`, `Door-Knob`… et le nœud `Door` du modèle vanilla a sa position sur la charnière (`x = -16`, `z = 0`, porte centrée en Z, `Common/Blocks/Decorative_Sets/Crude/Door.blockymodel`). La porte DO convertie est à plat le long de X = 0…3 px (orientation « est » de MC) et ses nœuds s'appellent `E0…En`. Il faut regrouper ses éléments sous un nœud `Door` pivoté sur la charnière et la réorienter (point 8).

   Trappe vanilla : états `OpenDoorOut` / `CloseDoorOut`, animations `Trapdoor_*.blockyanim` sur le nœud `Door`, hitbox `Trapdoor` (Y 0,8…1). Le modèle est **en haut du bloc** (Y 26…35,7 unités, charnière à `z = -15`). Nos trappes et panneaux n'ont pas de `HitboxType` : leur collision est celle d'un cube plein (`generate.py:85-86`, porte seule).
6. **Établi de l'architecte présent** : `source.shapes` parcourt tout le cache (`rglob`, `source.py:53`) et retient `architectscutter.json` (racine, avec `elements`). `names.py:5` lui donne même un nom (`"": ("Cutter", …)`), d'où `HyColony_DO_Cutter`. Correctif : ignorer le dossier racine (`folder == ""`).
7. **Lumières encadrées sans lumière.**
   - Dans DO, le centre est un composant de matériau à part, `FRAMED_LIGHT_CENTER`, par défaut `block/glowstone`. Le bloc émet `lightLevel(state -> 15)` quel que soit le matériau (`…/block/decorative/FramedLightBlock.java:73, 87-95` de DO).
   - `convert.material` range tout ce qui n'est pas le cadre dans `light` (Lightwood) (`convert.py:23-36`) : le centre devient du bois. `item()` n'écrit aucun `Light` (`generate.py:67-86`).
   - Vanilla : `BlockType.Light` (`ColorLight`, `H: BlockType.java:245-252`), par exemple `"Light": {"Color": "#a72"}` et `CubeShadingMode: "Fullbright"` (`Build_Lightsource_Orange.json`), ou `{"Radius": 0, "Color": "#dca"}` (`Deco_Lantern.json`). Côté modèle, `shadingMode: "fullbright"` par forme existe (217 formes vanilla, par exemple `Furnace_Simple.blockymodel`).
   - Correctif : un troisième matériau « lumière » (texture claire), `shadingMode: "fullbright"` sur les éléments `#centre`, et `Light` sur le `BlockType`. Le rendu est **[in-game]**.
8. **Orientation à la pose incohérente : chaque famille DO a son orientation brute, et le générateur applique la même `VariantRotation: NESW` sans correction** (`generate.py:78`).
   - Orientation brute DO (variante sans `y` dans `DO bs:`) :
     - bardeaux : `facing=east` (`shingle.json` : `east → y 360`, `north → y 270`), côté haut en +X (`shingle/straight_spec.json` : mur `[14,2,0]→[16,14,16]`) ;
     - portes : `facing=east` (`vanilla_doors_compat.json` : `east` sans `y`, `north → y 270`) ;
     - trappes et panneaux : `facing=south` (`vanilla_trapdoors_compat.json`, `fancy_trapdoors.json`, `panel.json` : `south → y 360`, `north → y 180`).
   - Orientation brute vanilla : **côté haut en -Z** (`back`). J'ai mesuré sur les coins : `Stairs.blockymodel` (marche haute `z = -8`) et `Slope_Hay.blockymodel` (points les plus hauts à `z = -16`). La trappe vanilla a sa charnière en -Z, la porte est centrée en Z. Les toits sont en `NESW`, les escaliers en `UpDownNESW` (`H: VariantRotation.java:94-119`).
   - Nos bardeaux sont donc tournés de 90° par rapport aux toits vanilla, et les trappes de 180°. Les trappes ne gèrent pas non plus la moitié haute : DO `half=top` fait `x=180`, et la trappe vanilla est en haut du bloc. Les objets symétriques (colombages, panneaux pleins) masquent l'écart, d'où l'impression d'incohérence.
   - La rotation choisie à la pose vient du client (`BlockRotation` du paquet, `H: server/core/modules/interaction/BlockPlaceUtils.java:144`). Je n'ai pas vérifié en jeu quel lacet correspond à `None` **[in-game]**.
   - Correctif : lors de la conversion, appliquer la rotation `y` de la variante `facing=north` du blockstate de la famille (bardeaux et portes `y 270`, trappes et panneaux `y 180`). On obtient ainsi l'orientation vanilla (haut ou charnière en -Z). Prévoir `UpDownNESW` ou un état « haut » pour les trappes et les bardeaux inversés.
9. **Icônes des bardeaux vues de dos** : même cause que le point 8. `icon.py` regarde depuis +X+Z (`VIEW = (1,1,1)`, `iso()` « seen from +x +z »), et le bardeau brut a son côté haut en +X. On voit donc le mur arrière et le dessous. Dans ce repère, un escalier orienté comme vanilla (haut en -Z) montre sa face avant. Corriger l'orientation au point 8 corrige aussi l'icône, sans toucher à `VIEW`.

### B.10 Mécanismes Hytale pour chaque comportement DO

Recherche du 2026-09-28. Question : pour chaque comportement de DO, quel mécanisme natif de Hytale 0.6.8 fait la même chose, et peut-on le produire **en assets seuls** (générés au build, B.6) ou faut-il **du code de plugin** ? Sources DO au commit `82729d6`, abréviation `DO bs:` comme en B.9. Les chemins `H:` partent de `com/hypixel/hytale/`, les chemins `zip:` sont dans `release-0.6.8-Assets.zip`.

Rappel commun : une forme ou un état Hytale est un `BlockType` distinct (`State.Definitions`, `connected-blocks.md` § 1), et la texture est fixée par `BlockType` (B.5). Chaque forme ci-dessous se multiplie donc par le nombre de combinaisons de matériaux générées.

#### 1. Portes et trappes (y compris ouvragées)

- **DO** :
  - `vanilla_doors_compat` et `fancy_door` ont `facing`, `half`, `hinge`, `open`, `type` (`DO bs: vanilla_doors_compat.json`, 128 variantes ; `fancy_door.json`, 64). Les classes héritent de la porte MC (`DO: block/vanilla/DoorBlock.java:49`, `block/decorative/FancyDoorBlock.java:51`) ;
  - les trappes ont `facing`, `half` (`bottom`/`top`), `open`, `type` (`DO bs: vanilla_trapdoors_compat.json`, 240 parties).
- **Hytale** : ce qu'exige une porte vanilla est en B.9 point 5. Les mécanismes :
  - **ouverture** : `DoorInteraction` passe à `CLOSED` si la porte est ouverte, sinon à `OPENED_OUT` si le joueur est devant, sinon à `OPENED_IN` (`H: server/core/modules/interaction/interaction/config/server/DoorInteraction.java:359-366`). Noms d'états : `H: server/core/modules/interaction/DoorBlockUtils.java:31-34`, plus `DoorBlocked` (`DoorInteraction.java:67`) ;
  - **porte de deux blocs** : la boîte `Door` fait 1 × **2** × 0,2 (`zip: Server/Item/Block/Hitboxes/Furniture/Door/Door.json`, `Max.Y = 2`). `BlockOperations.setBlock` pose seul les blocs `filler` d'après la boîte (`H: server/core/universe/world/chunk/BlockOperations.java:104-107`, `FillerBlockUtil.setFillerBlocksAt`). Il n'y a pas de propriété `half` : un seul `BlockType` couvre les deux blocs ;
  - **portes doubles** : `DoorInteraction.checkForDoubleDoor` ouvre aussi la porte voisine tournée de 180° (`DoorInteraction.java:70-90, 233-268`). À la pose, `DoorConnectedBlockTemplate` tourne de 180° une porte posée à côté d'une autre (`"YawToApplyAddReplacedBlockType": "OneEighty"`, `zip: Server/Item/CustomConnectedBlockTemplates/DoorConnectedBlockTemplate.json`) ;
  - **charnière** : pas de propriété. La porte vanilla est centrée dans l'épaisseur (`Min.Z = 0.4`, `Max.Z = 0.6`). Une rotation de 180° la garde donc dans le même plan et met la charnière de l'autre côté : `hinge=right` de MC correspond à une porte tournée de 180°. Condition : le modèle DO doit être recentré en Z (B.9 point 5). Le rendu reste **[in-game]** ;
  - **trappes** : `"Use": "Door_Horizontal"` (`zip: Server/Item/Interactions/Door/Door_Horizontal.json`, `"Horizontal": true`), états `OpenDoorOut`/`CloseDoorOut`, boîtes `Trapdoor` et `Trapdoor_Open`. Les 15 trappes vanilla sont toutes en `VariantRotation: NESW` : il n'existe **pas de trappe en bas du bloc**. Pour `half=bottom`, deux voies en assets : `UpDownNESW` (retournement par tangage, animation retournée **[in-game]**), ou un second `BlockType` `_Bottom` avec une boîte à Y 0…0,2 et ses propres états ;
  - **panneau** (`panel`, trappe fixe) : le même modèle, sans `Interactions.Use`.
- **Verdict : assets seuls.**

#### 2. Clôtures, portillons, murets, murs de papier

- **DO** :
  - clôture : `north/east/south/west` (`DO bs: vanilla_fence_compat.json`) ;
  - muret : `up`, plus `low`/`tall` par côté (`vanilla_wall_compat.json`) ;
  - portillon : `facing`, `in_wall`, `open` (`vanilla_fence_gate_compat.json`) ;
  - mur de papier : `north/east/south/west` (`blockpaperwall.json`). C'est une vitre : `AbstractBlockPane` étend `IronBarsBlock` (`DO: block/AbstractBlockPane.java:11`).
- **Hytale** : `ConnectedBlockRuleSet` `CustomTemplate` + `WallConnectedBlockTemplate`, que 76 objets vanilla utilisent.
  - Formes : `Straight` (défaut), `Corner`, `T_Junction`, `Cross_Junction`, `Gate`. Chacune choisit aussi la **rotation** (`"AllowedPatternTransformations": {"IsCardinallyRotatable": true}`).
  - Exemple : `zip: Server/Item/Items/Wood/Softwood/Wood_Softwood_Fence.json`, avec les états `Corner`, `T`, `Cross`.
  - Une règle de gabarit teste sur le voisin des `FaceTags`, des `Shapes`, des `BlockTypes` ou des `BlockTypeLists` (`H: server/core/universe/world/connectedblocks/ConnectedBlockPatternRule.java:24-60`).
  - Le portillon est une porte (`"Use": "Door"`, états de porte, `zip: .../Wood_Softwood_Fence_Gate.json`), déclarée comme forme `Gate` du gabarit.
- **Écarts de forme** :
  - le gabarit vanilla n'a ni « bout » (1 voisin) ni « poteau seul » (0 voisin) : ces cas tombent sur `Straight`. Pour la vitre MC (demi-vitre au bout), il faut un **gabarit à nous** avec deux formes de plus. C'est un asset : store `Item/CustomConnectedBlockTemplates` (`H: .../connectedblocks/ConnectedBlocksModule.java:66`) ;
  - `in_wall` (portillon abaissé entre deux murets) n'a pas d'équivalent. Une forme de gabarit dédiée est possible en principe ; son effet sur les états de porte est **[in-game]**.
- **Verdict : assets seuls.**

#### 3. Bardeaux (formes d'escalier)

- **DO** : `facing`, `half` (`bottom`/`top`), `shape` (`straight`, `inner_left`, `inner_right`, `outer_left`, `outer_right`) (`DO bs: shingle.json`). Ces propriétés viennent de l'escalier MC (`DO: block/AbstractBlockStairs.java:9`). La forme se calcule d'après les voisins, comme pour un escalier vanilla.
- **Hytale** : `ConnectedBlockRuleSet` `Roof` (`zip: Server/Item/Items/Wood/Softwood/Wood_Softwood_Roof.json`).
  - `Regular` porte les 5 formes : `Straight` (= `default`), `Corner_Left/Right` (extérieur) et `Inverted_Corner_Left/Right` (intérieur). Chacune est un état avec son modèle et sa boîte.
  - `StairConnectedBlockRuleSet` choisit la forme d'après les voisins de même `MaterialName`, à la pose et quand un voisin change (`connected-blocks.md` § 1-2).
  - `Hollow` et `Topper` sont **facultatifs** : seul `Regular` est obligatoire (`H: .../connectedblocks/builtin/RoofConnectedBlockRuleSet.java:42-47`). On peut les omettre pour coller à DO.
- **Moitié haute** : `VariantRotation: UpDownNESW`, soit 4 lacets × tangage 0 ou 180° (`H: server/core/asset/type/blocktype/config/VariantRotation.java:105-142`). 78 escaliers vanilla l'utilisent, ainsi que des toits `Shallow` et `Steep`. La rotation vient du client (`clientState.blockRotation`, `H: .../interaction/config/client/PlaceBlockInteraction.java:122`).
- **Pentes** : DO en a 5 (`shingle`, `_flat`, `_flat_lower`, `_steep`, `_steep_lower`), Hytale a les modèles `Roof`, `Roof_Shallow`, `Roof_Steep` et `Roof_Flat`. Les versions `_lower` n'ont pas de modèle vanilla. Il faut soit les convertir depuis DO (B.9), soit les produire.
- **Verdict : assets seuls.**

#### 4. Demi-bardeau, pilier, poteau, colombage dynamique

- **Demi-bardeau** (`DO bs: shingle_slab.json` : `facing` × `shape`).
  - DO : `ShingleSlabBlock.getSlabShape` compte les voisins horizontaux qui sont des `ShingleSlabBlock`, tous matériaux confondus (`DO: block/decorative/ShingleSlabBlock.java:163-257`). Il est appelé à la pose et à chaque changement de voisin (l.102-124). Résultat selon le nombre de voisins :
    - 0 → `top` ;
    - 1 → `one_way`, tourné vers le voisin ;
    - 2 opposés → `two_way` ;
    - 2 en angle → `curved` ;
    - 3 → `three_way` ;
    - 4 → `four_way`.
  - Hytale : même logique qu'une clôture. Il faut un gabarit `CustomTemplate` à nous, à 6 formes (`Top`, `One_Way`, `Two_Way`, `Curved`, `Three_Way`, `Four_Way`), avec `IsCardinallyRotatable`. Pour que tous les demi-bardeaux se relient quel que soit leur matériau : un `FaceTags` commun à tous les blocs générés et `ConnectsToOtherMaterials: true` (`H: .../connectedblocks/CustomConnectedBlockTemplateAsset.java:53`).
  - **Assets seuls.**
- **Pilier** (`DO bs: blockpillar.json` : `column` = `pillar_base`, `pillar_capital`, `pillar_column`, `full_pillar`).
  - DO : `PillarBlock` regarde le bloc au-dessus et au-dessous (`getBlock() == this`, même famille, tout matériau) (`DO: block/decorative/PillarBlock.java:143-155, 254-283`) :
    - pilier au-dessus seulement → `pillar_base` ;
    - pilier au-dessous seulement → `pillar_capital` ;
    - les deux → `pillar_column`.
  - Bug de DO : sans voisin, le résultat de `blockState.setValue(COLUMN, FULL_PILLAR)` est jeté (l.270). L'état par défaut est déjà `FULL_PILLAR` (l.85), donc le résultat reste juste.
  - Hytale : `zip: Server/Item/CustomConnectedBlockTemplates/PillarConnectedBlockTemplate.json` fait la même chose en vertical, avec le `FaceTags` `PillarConnection`. Formes : `Base` (défaut, pilier au-dessus), `Base_Inverted` (pilier au-dessous), `Middle` (les deux).
  - Aucun objet vanilla ne l'utilise : les piliers `Rock_*_Brick_Pillar_*` n'ont pas de règle (B.9 point 4).
  - Il faut une copie à 4 formes, avec en plus `Full` (ni dessus ni dessous).
  - **Assets seuls.**
- **Poteau** (`DO bs: post.json` : `type` × `facing` 6 directions × `conditional`).
  - `type` vient de l'objet (`DO: block/decorative/PostBlock.java:61, 126`) : il faut un `BlockType` par type.
  - `facing` se traduit par la `VariantRotation` (`Pipe`, `DoublePipe` ou `All`).
  - **Assets seuls.**
- **Colombage dynamique** (`DO bs: dynamic_timberframe.json` : une seule variante).
  - Le motif n'est pas un état. L'entité de bloc garde 14 booléens de voisins : 6 faces et 8 diagonales verticales (`DO: block/decorative/DynamicTimberFrameBlock.java:76-91`). Elle **retexture chaque face par morceau** (`entity/block/DynamicTimberFrameBlockEntity.java:271-470`).
  - Hytale n'a pas de retexture par bloc (B.5). Il faudrait un `BlockType` par combinaison de voisins (jusqu'à 2^14) et par matériaux. **Ni les assets ni le code ne le rendent fidèlement.**
  - Rappel A.4 : le constructeur MC demande de toute façon ce bloc sous la forme `framed`. Écart probable : pas de colombage dynamique, ou seulement quelques motifs fixes.

#### 5. Lumière encadrée

- **DO** : 7 blocs, avec une lumière **fixe de 15**, quel que soit le centre (`DO: block/decorative/FramedLightBlock.java:73`). Centres valides : `glowstone`, `sea_lantern`, `ochre_froglight`, `pearlescent_froglight`, `verdant_froglight`, `shroomlight` (`DO-gen: tags/blocks/framed_light_center.json`). La lumière de bloc MC n'a pas de couleur.
- **Hytale** : le champ `Light` du `BlockType` vaut `{"Color": "#rgb", "Radius": n}` (B.9 point 7 ; codec `COLOR_LIGHT`, `H: server/core/codec/ProtocolCodecs.java:59-69`).
  - Chaque chiffre de `#rgb` est un niveau de 0 à 15 par canal. En `#rrggbb`, chaque valeur est divisée par 17 (`H: server/core/asset/util/ColorParseUtil.java:354-363`).
  - Exemples : `Build_Lightsource_White` = `#eee` sur un bloc `Cube` ; `Deco_Lantern` = `#dca`, `Radius: 0`.
  - Équivalent fidèle d'un niveau 15 sans couleur : `"Color": "#fff"`. Une teinte par centre serait un écart.
- **Verdict : assets seuls.**

#### 6. Architect's Cutter

- **DO** (A.3) : on choisit un groupe, puis une variante, puis on pose 1 ou 2 matériaux ; la sortie est calculée.
- **Établis Hytale** : 4 types (`H: protocol/BenchType.java` : `Crafting`, `Processing`, `DiagramCrafting`, `StructuralCrafting`).
- **`StructuralCrafting`** (`Bench_Builders`, `zip: Server/Item/Items/Bench/Bench_Builders.json`) :
  - **un seul** emplacement d'entrée (`H: builtin/crafting/window/StructuralCraftingWindow.java:59`) ;
  - au plus **64** sorties (l.44, 62) ;
  - seulement les recettes à **un ingrédient** (`inputMaterials.size() == 1`, l.311) ;
  - sorties triées dans l'ordre des `Categories` de l'établi (l.84-111).

  Il convient aux familles DO à un matériau : pilier, poteau, panneau, porte, trappe, clôture, portillon, muret, escalier, dalle, « all brick ». Cela fait environ 50 variantes par matériau, sous la limite de 64. Il **ne convient pas** aux familles à deux matériaux.
- **`DiagramCrafting`** (`H: builtin/crafting/window/DiagramCraftingWindow.java`) : **le plus proche du cutter**.
  - L'établi déclare des catégories : `CraftingBench.BenchCategory` avec `Id`, `Name`, `Icon`, `ItemCategories` (`H: server/core/asset/type/blocktype/config/bench/CraftingBench.java:17-70`).
  - Chaque catégorie a des sous-catégories : `BenchItemCategory` avec `Id`, `Name`, `Icon`, `Diagram` (svg), `Slots`, `SpecialSlot` (l.119-140).
  - Le joueur choisit la catégorie et la sous-catégorie (`UpdateCategoryAction`, `DiagramCraftingWindow.java:127-134`). La fenêtre ouvre alors 1 emplacement principal et `Slots` emplacements secondaires (l.211).
  - Elle cherche les recettes de l'établi rangées sous `"<catégorie>.<sous-catégorie>"` (l.360). Leurs ingrédients doivent correspondre **dans l'ordre** aux emplacements (l.293-318).
  - La sortie n'apparaît que si **une seule** recette correspond (l.260-266).
  - Correspondance avec DO : groupe = catégorie, variante = sous-catégorie, `Slots: 1` = les 2 emplacements du cutter. Il faut une recette générée par combinaison, dont la sortie est l'objet généré et dont `OutputQuantity` reprend les quantités de A.3.
  - **Réserves** :
    - aucun établi `DiagramCrafting` vanilla n'a de recette. Seul `Bench_Armory` déclare ce type, sans recette pour le fabriquer ni objet qui le vise. L'affichage côté client n'a donc jamais été vu : **[in-game]** ;
    - un seul objet par clic (`queueCraft(..., 1, ...)`, l.150) ;
    - une recette « à connaître » s'apprend au premier craft (l.161).
- **`Crafting`** (`Bench_Furniture`) : des onglets `Categories` avec icône (`Icons/CraftingCategories/...`, validateur `ICON_CRAFTING`, `CraftingBench.java:64`). Les recettes y ont plusieurs ingrédients, pris dans l'inventaire, sans emplacements dédiés. C'est un repli sûr, déjà utilisé en vanilla, mais il liste toutes les combinaisons.
- **Ingrédients** : un `ItemId` exact ou un `ResourceTypeId` (`H: builtin/crafting/component/CraftingManager.java:670-690`). La sortie est un objet distinct par combinaison (B.7) : il faut donc **une recette par combinaison**.
- **Verdict : assets seuls** pour un cutter fondé sur `DiagramCrafting` (un établi et des recettes générées), sous réserve du test en jeu. Une fenêtre de plugin ne sert que dans deux cas : si `DiagramCrafting` s'affiche mal chez le client, ou pour reproduire la liste « toutes les variantes » de DO avec l'aperçu de la sortie avant de poser les matériaux.

#### 7. Orientation à la pose et boîtes de collision

- **Orientation** : la `VariantRotation` du `BlockType` (`None`, `Wall`, `UpDown`, `Pipe`, `DoublePipe`, `NESW`, `UpDownNESW`, `All` ; `H: server/core/asset/type/blocktype/config/VariantRotation.java:9-150`).
  - Nombre d'objets vanilla : `NESW` 523, `UpDownNESW` 236, `DoublePipe` 128, `Wall` 75, `Pipe` 58, `UpDown` 44.
  - Les coins de toit déclarent aussi un `FlipType` (`Orthogonal`, `OrthogonalInverse`).
  - L'orientation brute des modèles DO est en B.9 point 8.
- **Boîtes** : un `HitboxType` par `BlockType` et par état (`zip: Server/Item/Block/Hitboxes/**`).
  - Les toits n'ont **pas de pente physique** : leurs boîtes sont **en marches**. `Stairs` = une dalle basse et un demi-bloc arrière, soit 2 boîtes (`zip: .../Hitboxes/Structure/Stairs/Stairs.json`). Les coins ont leurs propres boîtes (`Roofs/Roof_Corner_Left.json`, `Stairs_Inverted_Corner_*`), ainsi que `Stairs_Shallow`, `Stairs_Steep` et `Stairs_Thin`.
  - Les bardeaux DO ont eux aussi la collision d'un escalier MC (ils héritent de `StairBlock`). Le comportement est donc le même : on monte une marche. La hauteur de marche que le joueur franchit sans sauter est **[in-game]**.
  - Le demi-bardeau DO a une boîte de dalle de 16 × 8 × 16 px (`ShingleSlabBlock.java:150-153`). Son équivalent Hytale est `Block_Half` (`zip: .../Hitboxes/Block/Block_Half.json`).
- **Verdict : assets seuls.**

#### 8. Icônes

- `Item.Icon` est un chemin PNG sous `Icons/ItemsGenerated` ou `Icons/Items` (validateur `ICON_ITEM`, `H: server/core/asset/common/CommonAssetValidator.java:25`). Le fichier doit exister (l.103-111). Sinon la validation échoue, et le serveur s'arrête si le pack est immuable (`plugin-b-api.md` § 23).
- Un `Icon` **absent** (`null`) passe le validateur (`CommonAssetValidator.java:82`). Le client ne reçoit alors que `IconProperties` (`H: protocol/ItemBase.java:38-40`). Sait-il dessiner l'icône à partir du modèle ? C'est **[in-game]**, et on ne peut pas le vérifier côté serveur.
- **Tous** les blocs vanilla sans `Parent` ont une icône PNG : le scan de `zip: Server/Item/Items/**` ne trouve aucune exception. Toits et portes utilisent `Icons/ItemsGenerated/<id>.png`, produite par l'éditeur d'assets à partir de `IconProperties` (B.9 point 2).
- **Verdict : il faut des PNG**, une par objet généré, rendue par le générateur ou par l'éditeur d'assets.

#### 9. Onglets de l'inventaire créatif

- **Assets** : fichiers `Server/Item/Category/CreativeLibrary/*.json`, store `ItemCategory` au chemin `Item/Category/CreativeLibrary` (`H: server/core/asset/AssetRegistryLoader.java:542-551`). La clé est le nom du fichier.
  - Vanilla a 4 onglets de premier niveau : `Blocks` (`Order` 0), `Furniture` (1), `Items` (2) et `Tool` (3).
  - Chaque onglet a une `Icon` et un `Order`, **sans `Name`**, et des `Children`.
  - Un enfant a un `Id`, un `Name` (clé de traduction), une `Icon` et, en option, des `SubCategories` (`Id`, `Name`, `Description`, `Order`). Exemple : `zip: Server/Item/Category/CreativeLibrary/Blocks.json`, enfant `Wood` avec les sous-catégories `BlockSets` et `Trees`.
  - Champs du codec : `Id`, `Name`, `Icon`, `InfoDisplayMode` (défaut `Tooltip`), `Order`, `SubCategories`, `Children` (`H: server/core/asset/type/item/config/ItemCategory.java:38-65, 188-212`).
- **Objet** : `"Categories": ["Blocks.Wood"]` désigne l'onglet puis l'enfant, et `"SubCategory": "BlockSets"` l'en-tête dans l'enfant (`Wood_Softwood_Roof.json` ; `H: server/core/asset/type/item/config/Item.java:104-120`).
- **Icônes** : validateur `ICON_ITEM_CATEGORIES`, qui exige une PNG sous `Icons/ItemCategories` (`CommonAssetValidator.java:26`, `ItemCategory.java:42-43`). Il s'applique aussi aux `Children`, qui ont le même codec.
  - Les onglets vanilla ont une paire d'icônes `X.png` / `XActive.png` (`zip: Common/Icons/ItemCategories/Natural.png` et `NaturalActive.png`, `FurnitureActive.png`, `ItemsActive.png`, `EditorActive.png`).
  - Le client utilise probablement `…Active.png` pour l'onglet sélectionné **[in-game]**. Il faut donc fournir les deux.
- **Ajout par un pack** : un fichier `Server/Item/Category/CreativeLibrary/DomumOrnamentum.json` dans notre pack ajoute une clé de plus au même store. Le paquet `UpdateItemCategories` envoie toutes les catégories (`H: server/core/asset/type/item/ItemCategoryPacketGenerator.java`).
  - Structure proposée : un onglet `DomumOrnamentum` (`Order` 4) ;
  - un enfant par groupe du cutter : `Timberframe`, `Shingle`, `Door`, `Trapdoor`, `Panel`, `Pillar`, `Paperwall`, `Light`, `Brick`, `Post`, `Vanilla` ;
  - des `SubCategories` par forme ou par matériau.

  L'affichage d'un 5ᵉ onglet (sa place, son libellé sans `Name`) est **[in-game]**.
- **Robustesse** :
  - une catégorie **inconnue** dans `Item.Categories` n'arrête rien. Son validateur est `addValidatorLate` (`Item.java:110`), et les validateurs tardifs sont sautés au chargement (`H: codec/builder/BuilderField.java:248`). Les objets HyColony utilisent d'ailleurs déjà `"Workbench_Crafting"`, qui n'est pas une catégorie créative ;
  - en revanche, une **icône manquante ou hors racine** dans un asset `ItemCategory` fait échouer la validation (`CommonAssetValidator.java:93-111`). Pour un pack immuable, le serveur s'arrête (`plugin-b-api.md` § 23). Le build doit vérifier ces icônes, comme `checkSubpluginAssets` le fait déjà.
- **Verdict : assets seuls.**

#### Récapitulatif

| Comportement DO | Mécanisme Hytale | Assets seuls ? |
|---|---|---|
| Porte, trappe : ouverture, 2 blocs, portes doubles | `Interactions.Use: Door` / `Door_Horizontal`, états `OpenDoorIn/Out`, boîte de 2 de haut (fillers), `DoorConnectedBlockTemplate` | Oui. Charnière = rotation de 180°. Trappe du bas : `UpDownNESW` ou bloc `_Bottom` **[in-game]** |
| Clôture, muret, portillon, mur de papier | `CustomTemplate` + `WallConnectedBlockTemplate`, ou un gabarit à nous pour les bouts de vitre | Oui |
| Bardeaux (5 formes, haut ou bas) | `Roof` (`Regular` seul, sans `Hollow`/`Topper`), `UpDownNESW` | Oui (plus les modèles des pentes `_lower`) |
| Demi-bardeau (6 formes) | gabarit `CustomTemplate` à nous : 6 formes, `IsCardinallyRotatable` | Oui |
| Pilier (4 formes) | copie de `PillarConnectedBlockTemplate` avec une forme `Full` en plus | Oui |
| Poteau | un `BlockType` par type, `VariantRotation` | Oui |
| Colombage dynamique | aucun (la retexture par face est impossible) | Non portable fidèlement (écart) |
| Lumière encadrée | `Light: {"Color": "#fff"}` | Oui |
| Cutter | `DiagramCrafting` (catégorie = groupe, sous-catégorie = variante, `Slots: 1`) ; `StructuralCrafting` pour un seul matériau | Oui, **[in-game]** pour `DiagramCrafting` ; plugin en repli seulement |
| Rotation, boîtes | `VariantRotation`, `HitboxType` par état (marches, pas de pente) | Oui |
| Icônes | PNG obligatoires (`Icons/ItemsGenerated` ou `Icons/Items`) | Oui (PNG générées) |
| Onglet créatif DO | `Server/Item/Category/CreativeLibrary/DomumOrnamentum.json` et les `Categories` des objets | Oui ; icônes `X`/`XActive` vérifiées au build |

**Aucun comportement n'exige de code de plugin**, à deux exceptions près :
- le colombage dynamique, que ni les assets ni le code ne peuvent rendre ;
- une fenêtre de cutter sur mesure, si `DiagramCrafting` ne fonctionne pas en jeu.

Le vrai coût est combinatoire. Chaque forme × état × combinaison de matériaux devient un `BlockType`, avec sa texture composée, son icône et sa recette.

### B.11 Variantes à l'exécution sans nouvelle texture (prototype `/hyornament`, 2026-09-28)

Nouvelle piste, différente de B.6 : **aucun asset commun n'est ajouté**. La variante réutilise un modèle et des textures que le client a déjà. Seul un `BlockType` est créé. Sources : `H:` = `build/vineflower/hytale-server/com/hypixel/hytale/`, `zip:` = assets 0.6.8.

**Deux matériaux dans un bloc sans composer de PNG : `DrawType.CubeWithModel`.**
- `DrawType` vaut `Empty`, `GizmoCube`, `Cube`, `Model` ou `CubeWithModel` (`H: protocol/DrawType.java`). En `CubeWithModel`, le client dessine le cube (`Textures`, 6 faces) **et** le modèle (`CustomModel` + `CustomModelTexture`). Ce sont deux textures indépendantes.
- 18 blocs vanilla l'utilisent : 17 minerais et `Rock_Volcanic_Cracked_Incandescent`. Par exemple `Ore_Copper_Stone` = cube `BlockTextures/Rock_Stone.png` + modèle `Resources/Ores/Ore_Large.blockymodel` texturé `Resources/Ores/Ore_Textures/Copper.png` (`zip: Server/Item/Items/Ore/Copper/Ore_Copper_Stone.json`).
- Une texture de cube sert aussi de texture de modèle : 376 objets vanilla ont un `CustomModelTexture` en `BlockTextures/*.png` (par exemple `Build_Black_Half` = `Base_Shapes/HalfBlock.blockymodel` + `BlockTextures/Dev_Black_Top.png`). Les textures de bloc font 32×32 px, un bloc fait 32 unités de modèle.
- Donc `TimberFrame(Oak, Stone)` = cube `Rock_Stone_Brick.png` (remplissage) + modèle unique `Blocks/HyColony/Ornament/TimberFrame.blockymodel` (12 poutres d'arête) texturé `Wood_Hardwood_Planks.png` (cadre). **Un seul `.blockymodel`, zéro PNG créée, zéro texture dupliquée.**
- Limite : c'est **une texture pour tout le modèle et une pour le cube** (6 faces au plus). Un 3ᵉ matériau, ou deux matériaux sur un même modèle, reste impossible sans PNG composée (B.5).

**Ce que fait l'Asset Editor pour un `BlockType`.**
- Il applique les commandes JSON, puis `jsonTypeHandler.loadAssetFromDocument(..., new AssetUpdateQuery(rebuildCacheBuilder.build()), ...)` (`H: builtin/asseteditor/AssetEditorPlugin.java:1226-1256`). Les drapeaux viennent des métadonnées `UIRebuildCaches` des champs modifiés. Par défaut (`AssetStoreTypeHandler.getDefaultUpdateQuery`, l. 93-127), ce sont ceux du schéma.
- Drapeaux déclarés dans `BlockType.CODEC` (`H: server/core/asset/type/blocktype/config/BlockType.java`) :
  - `DrawType` : `MODELS`, `BLOCK_TEXTURES`, `MODEL_TEXTURES` (l. 132) ;
  - `Textures` : `MODELS`, `BLOCK_TEXTURES` (l. 142) ;
  - `CustomModelTexture` : `MODELS`, `BLOCK_TEXTURES` (l. 169) ;
  - `CustomModel` : `MODELS` (l. 179).
- **Il n'envoie jamais `RequestCommonAssetsRebuild` pour un `BlockType`.** Seul le gestionnaire des fichiers communs le fait (`CommonAssetTypeHandler.java:56-61, 103` : `commonAssetsRebuild = true`). Le drapeau `commonAssetsRebuild` de `RebuildCache` n'est lu nulle part ailleurs.
- Le paquet : `BlockTypePacketGenerator.generateUpdatePacket` (`H: server/core/asset/type/blocktype/BlockTypePacketGenerator.java:43-66`) = `UpdateBlockTypes` `AddOrUpdate`, `maxId = getNextIndex()`, les seuls types chargés (`index -> toPacket()`), et les 4 drapeaux `updateBlockTextures`, `updateModelTextures`, `updateModels`, `updateMapGeometry` pris de la requête. `ItemPacketGenerator` lit `itemIcons` pour `UpdateItems.updateIcons`.
- **Précédent vanilla d'ajout à chaud sans reconstruction** : `BlockType.getBlockIdOrUnknown` (l. 2242-2257) charge un bloc « Unknown » sous une clé inconnue avec `AssetUpdateQuery.DEFAULT_NO_REBUILD` (tous les drapeaux à `false`).

**Ids, joueurs, persistance.**
- Id : `BlockTypeAssetMap.putAll0` (`H: assetstore/map/BlockTypeAssetMap.java:137-195`) donne `nextIndex++` à une clé nouvelle et garde l'index d'une clé connue. Les ids ne valent que pour la session : les tronçons s'enregistrent **par clé** (`BlockSection.deserialize`, `H: server/core/universe/world/chunk/section/BlockSection.java:851-890`, clé → `getBlockIdOrUnknown`). Je n'ai trouvé aucune limite du nombre de `BlockType` (B.6) : l'id est un `int`.
- Joueur connecté : `HytaleAssetStore.handleRemoveOrUpdate` (`H: server/core/asset/HytaleAssetStore.java:88-111`) diffuse le paquet à tout l'univers et remet à zéro `cachedInitPackets`.
- Joueur qui arrive plus tard : il reçoit le paquet `Init` refait depuis toute la table, variantes comprises (`sendAssets`, l. 113-125). Il n'a donc besoin d'aucune mise à jour à chaud **[in-game]**.
- `loadAssets` par code n'envoie **pas** de notification d'asset : `sendReloadedNotification` n'est appelé que par la surveillance de fichiers (l. 278-285).
- Redémarrage : un tronçon qui contient une clé inconnue la charge en « Unknown » (bloc rose), sous **cette clé**. Si la variante est chargée plus tard sous la même clé, elle remplace l'« Unknown » au même index (`AddOrUpdate`). Le prototype recrée les variantes enregistrées (`universe/hycolony/ornament-variants.json`) pendant `LoadAssetEvent` à la priorité `PRIORITY_LOAD_LATE` (64) : après le chargement des registres (`AssetModule`, -16), sur le fil de démarrage, avant le démarrage des plugins et des mondes (`H: server/core/HytaleServer.java:342-395`).
- Verrou : la création se fait hors du thread du monde (`plugin-b-api.md` § 17).

**Prototype** (`plugin/.../ornament/`) :
- modèle `Common/Blocks/HyColony/Ornament/TimberFrame.blockymodel` et gabarit `HyColony_Ornament_TimberFrame` (`CubeWithModel`), chargés au démarrage, donc connus du client ;
- `/hyornament test <cadre> <remplissage> [--rebuild=none|editor|all] [--twice=true|false] [--icon=generated|material|none] [--notify=true|false] [--iconrefresh=true|false]` (opérateurs). La commande a d'abord posé le bloc devant le joueur ; elle donne maintenant 16 objets de la variante. Toutes les options ne valent que pour une combinaison **nouvelle** : le cache ne tient compte que de `VariantKey`, et une icône déjà publiée garde son mode de `--notify`. `--rebuild` choisit les drapeaux de `UpdateBlockTypes` :
  - `none` (défaut) : aucun drapeau, comme l'« Unknown » vanilla ;
  - `editor` : les drapeaux de l'éditeur (textures de bloc, modèles, textures de modèle) ;
  - `all` : tous les drapeaux, comme l'essai du 27/09 ;
  - matériaux : `oak`, `birch`, `spruce`, `redwood`, `stone`, `plaster`, `clay`, `sandstone`, dans les deux emplacements (64 combinaisons, donc assez d'essais sans redémarrer) ;
- cache `VariantKey` → future du `BlockType` (`OrnamentVariantRegistry`) : une deuxième demande identique réutilise le même bloc, même pendant sa création.

**Résultat en jeu (2026-09-28, `--rebuild=none`, un seul envoi)** :
- Une variante nouvelle s'affiche **sans reconnexion, sans `RequestCommonAssetsRebuild`, sans scintillement**. Sauf une exception : **la première de chaque connexion** reste rose et noire.
- Poser un bloc à côté ne la répare pas. La variante suivante, et toutes les autres, sont correctes.
- Après une reconnexion, le bloc rose s'affiche bien (le type arrive alors dans le paquet `Init`), et les anciennes variantes aussi. Mais la première variante **nouvelle** après la reconnexion est de nouveau rose.
- Hypothèse (client fermé, déduite des observations) : le client rate le premier `UpdateBlockTypes` `AddOrUpdate` reçu à chaud par une connexion ; le type envoyé serait correct, puisque le même type s'affiche une fois reçu dans `Init` **[in-game]**.
- Contournement : envoyer deux fois le même paquet (`--twice=true` par défaut, `--twice=false` pour comparer). **Vérifié en jeu le 2026-09-28** : avec le double envoi, la première variante de la connexion s'affiche aussi correctement, et le témoin `--twice=false` après reconnexion reste rose ; toutes les variantes créées à chaud s'affichent sans reconnexion ni scintillement.

**Objets des variantes** (ajoutés au prototype après le test du double envoi) :
- `Item` n'expose que des champs `protected` : la variante est une sous-classe de l'objet gabarit, avec `data = null` (même piège que § 17), `id` = `blockId` = la clé de la variante, et comme icône l'icône vanilla du matériau de remplissage (`Icons/ItemsGenerated/*.png`, déjà connue du client). `Item.toPacket` tolère `data` null (`H: server/core/asset/type/item/config/Item.java:898`), de même que les lecteurs de tags (`InternalContainerUtilTag.java:94-156`).
- Ordre obligatoire : le `BlockType` d'abord, puis l'`Item`. `Item.toPacket` envoie `blockId` = l'index du bloc, et `getIndexOrDefault(blockId, 1)` donnerait le bloc 1 si le bloc n'était pas encore chargé (l. 773-777). Ensuite, `Item.getAssetStore().loadAssets` envoie `UpdateItems` `AddOrUpdate` (`ItemPacketGenerator.generateUpdatePacket` : `updateModels` = textures de bloc ou modèles, `updateIcons` = icônes, tous à `false` ici).
- Côté bloc : `BlockType.getItem()` n'est pas `final`. La variante la redéfinit pour rendre son propre objet, ce qui fait que la casser le donne (`BlockHarvestUtils.getDrops`, l. 817-821). Elle redéfinit aussi `toPacket` pour que `packet.item` nomme cet objet, même si le paquet a été construit avant l'enregistrement de l'objet (le paquet est mis en cache).
- `/hyornament test` donne 16 objets de la variante, jetés aux pieds du joueur si son inventaire est plein. **Vérifié en jeu (2026-09-28)** : l'objet en main et le bloc posé ont les bonnes textures, et casser le bloc rend l'objet.
- Limites :
  - toutes les variantes portent le nom du gabarit (`translationProperties` copié) ;
  - le constructeur de copie d'`Item` (l. 677-728) ne recopie ni la qualité, ni le réticule, ni la durabilité, ni le carburant, ni le planeur, ni la musique, ni les réglages de conteneur. L'objet gabarit ne doit donc pas les utiliser ;
  - deux lecteurs lèvent une `NullPointerException` sur un objet sans `data` : `TagFilter.test` (`inventory/container/filter/TagFilter.java:15`) et `InternalContainerUtilTag.testRemoveTagFromSlot` (l. 138). Aucun des deux n'est appelé hors du paquet des inventaires ;
  - le paquet `UpdateItems` n'est envoyé qu'une fois. S'il subit le même défaut du client que les blocs, l'icône ou l'objet tenu de la première variante d'une connexion manquera **[in-game]**.
- `--icon=none` crée l'objet **sans** `Icon` (le client reçoit seulement les `IconProperties` vanilla copiées du gabarit). Côté serveur, rien ne lit `Item.getIcon()`. Résultat ci-dessous : un « ? ».
- Après un redémarrage, les blocs posés et les objets de l'inventaire restent corrects (vérifié en jeu le 2026-09-28).

**Icônes des variantes** (2026-09-28) :
- Un objet **sans** `Icon` s'affiche avec un « ? » dans l'inventaire (vérifié en jeu, `--icon=none`) : le client ne dessine pas l'icône à partir du modèle et d'`IconProperties`. Question de B.10 § 8 tranchée.
- **Hytale n'a pas de générateur d'icônes côté serveur.**
  - Les PNG `Icons/ItemsGenerated/*` sont dessinées par le client de l'Asset Editor, puis téléversées. Le serveur ne fait que les enregistrer : `CommonAssetTypeHandler.loadAsset` crée un `FileCommonAsset` et appelle `CommonAssetRegistry.addCommonAsset` (`H: builtin/asseteditor/assettypehandler/CommonAssetTypeHandler.java:35-44`). La requête par défaut d'un asset commun demande `commonAssetsRebuild` (l. 100-106).
  - `AssetEditorUpdateModelPreview` envoie le `Model` ou le `BlockType` à dessiner **par le client** (`H: protocol/packets/asseteditor/AssetEditorUpdateModelPreview.java:25-29`).
  - Aucune classe du serveur ne dessine d'image : pas de `renderIcon`, de générateur, ni d'autre référence à `ItemsGenerated` que le validateur et `Item.CODEC`.
- **Solution du prototype** : dessiner nous-mêmes un PNG 64×64 (`runtime/VariantIconRenderer`).
  - C'est un cube isométrique : les 3 faces visibles portent la texture de remplissage, avec une bordure de 4 px tirée de la texture du cadre.
  - La géométrie est mesurée sur l'icône vanilla `Rock_Stone_Brick` : sommet en (32, 2), côtés en x = 5 et x = 59, bas en (32, 62). Les faces gauche et droite sont assombries.
  - Les textures sont lues dans le registre (`CommonAssetRegistry.getByName(...).getBlob()`).
  - Ce n'est pas un moteur de rendu `.blockymodel` : chaque forme aura son propre dessin.
- **Envoi ciblé** (`runtime/VariantIconPublisher`) :
  - le PNG est écrit dans `universe/hycolony/ornament-icons/`, puis enveloppé dans un `FileCommonAsset` sous le nom `Icons/ItemsGenerated/<clé>.png` ;
  - `CommonAssetModule.addCommonAsset(pack, asset, false)` l'enregistre, invalide la liste des assets requis à la connexion, puis `sendAsset(asset, false)` envoie **ce seul fichier** (`AssetInitialize`, `AssetPart`, `AssetFinalize`) **sans** `RequestCommonAssetsRebuild` (`H: server/core/asset/common/CommonAssetModule.java:187-221, 588-611`). Effet de bord : une notification « asset créé » s'affiche chez les joueurs connectés (l. 203-208) ;
  - ensuite, l'`Item` est chargé avec `itemIcons = true`, ce qui envoie `UpdateItems.updateIcons = true` (`ItemPacketGenerator.java:44`) ;
  - le cache par `VariantKey` garantit qu'une icône n'est dessinée qu'une fois par démarrage ;
  - au démarrage, les icônes des variantes enregistrées sont redessinées et enregistrées avant toute connexion, puis envoyées avec les autres assets.
- Ne pas toucher à `universe/hycolony/ornament-icons/` pendant que le serveur tourne : quand la référence faible vers les octets est perdue, `FileCommonAsset` relit le fichier (`FileCommonAsset.java:30-32`). Un fichier supprimé ferait échouer le téléchargement des assets d'un joueur qui se connecte.
- `/hyornament test` prend `--icon=generated` (défaut), `material` (icône vanilla du remplissage) ou `none`. Si le rendu échoue, l'objet prend l'icône du matériau.
- **Vérifié en jeu (2026-09-28)** : l'icône générée apparaît chez le joueur déjà connecté, sans reconnexion ni scintillement. Deux défauts restent : la notification « asset créé » et un petit freeze de quelques millisecondes.
- Essais pour les supprimer (`/hyornament test`, sur une combinaison neuve) :
  - `--iconrefresh=false` : l'`Item` part sans `updateIcons`. Le client charge-t-il quand même l'icône par son chemin, et le freeze disparaît-il ? **[in-game]**
  - `--notify=false` : l'asset est ajouté par `CommonAssetRegistry.addCommonAsset`, puis `sendAsset(asset, false)` l'envoie aux joueurs connectés, sans `addCommonAsset`, donc sans notification. Aucune méthode publique n'invalide la liste des assets requis à la connexion (`CommonAssetModule.assets`, privée, invalidée seulement l. 125, 214, 490, 765). Le prototype l'invalide **par réflexion** (version épinglée 0.6.8) ; si ça échoue, un avertissement est journalisé, et les joueurs qui se connectent avant le prochain redémarrage n'ont pas l'icône. **[in-game]** : pas de notification, et un 2ᵉ joueur qui se connecte ensuite voit l'icône.
- Depuis cet essai, `/hyornament test` ne pose plus de bloc : il donne seulement les objets.
- **Résultats en jeu (2026-09-28)** :
  - `--notify=false` : l'icône apparaît, sans notification. C'est devenu le défaut.
  - `--iconrefresh=false` : l'icône **n'apparaît pas**. Le client ne prend en compte une nouvelle icône qu'avec `UpdateItems.updateIcons = true`, et le petit freeze de quelques millisecondes est le prix de ce rafraîchissement ; l'utilisateur le juge négligeable.
  - Joueur qui se connecte après la création : **vérifié en jeu (2026-09-28)** par une reconnexion. Les icônes sont là, donc l'invalidation par réflexion de la liste des assets requis fonctionne.
- **Redémarrages (vérifié en jeu le 2026-09-28, à chaque version du prototype)** : les blocs posés restent, et les objets de l'inventaire gardent leur icône, sans « Unknown ». Les variantes enregistrées sont recréées pendant `LoadAssetEvent` (priorité 64), avant le chargement des tronçons.

**Textures composées : deux matériaux sur un même modèle** (bardeau, 2026-09-28) :
- **Problème** : Hytale ne donne qu'une texture à un modèle. `CubeWithModel` ne couvre donc que les formes dont un matériau remplit un cube (colombages). Les bardeaux (couverture et support, tous deux sur la pente), les murs de papier et les portes ou trappes ouvragées ont besoin d'**une texture par combinaison**. Aucun bloc DO n'a plus de 2 matériaux (A.1).
- **Prototype** : `OrnamentShape.SHINGLE` réutilise le modèle vanilla `Blocks/Structures/Roofs/Slope_Hay.blockymodel`, sans nouveau modèle.
  - `runtime/VariantTextureComposer` part de la texture vanilla `Slope_Hay_Textures/Softwood.png` (64×128). Chaque pixel opaque prend le pixel correspondant de la texture de bloc du matériau, répétée : la couverture (1ᵉʳ matériau) dans la zone 56×32 en haut à gauche, le support (2ᵉ matériau) ailleurs.
  - Cette zone est lue à l'œil sur la texture vanilla ; elle est approximative. Les UV du modèle ne se projettent pas simplement sur la texture 64×128 (échelle non élucidée).
  - La texture est publiée comme les icônes (`runtime/VariantAssets` : PNG sur disque, inscription silencieuse, `sendAsset(asset, false)`), **avant** le `UpdateBlockTypes` qui la nomme.
- **Résultat en jeu (2026-09-28)** :
  - avec `--rebuild=none`, le bloc n'est pas rose, mais il montre une **autre région de l'atlas**. Hypothèse (client fermé) : sans ce drapeau, le client ne place pas la nouvelle texture dans son atlas de textures de bloc ;
  - avec `--rebuild=editor` (`updateBlockTextures`, `updateModels`, `updateModelTextures`), la texture est **correcte**, sans `RequestCommonAssetsRebuild`, et les variantes fausses créées avant sont corrigées du même coup (atlas reconstruit).
- Depuis, une forme composée utilise d'office le mode `textures` (`updateBlockTextures` seul) quand `--rebuild` vaut `none`. **Vérifié en jeu (2026-09-28)** : ce drapeau seul suffit.
- Coût : chaque reconstruction de l'atlas fait **scintiller l'écran une fois**. Avec le double envoi, les deux paquets portaient le drapeau, d'où deux scintillements. Désormais, le 1ᵉʳ paquet part sans drapeau et seul le 2ᵉ le porte : que le client garde l'un ou l'autre, il ne reconstruit qu'une fois. **Vérifié en jeu : un seul scintillement.** L'`Item` part sans drapeau de reconstruction (sauf `updateIcons` pour une icône générée).
- À la création d'une variante composée neuve, le prix est donc un scintillement. Plusieurs créations regroupées dans un seul paquet n'en feraient qu'un (à prévoir pour l'établi de l'architecte).

**États et règles de connexion d'une variante** (coins de bardeau, 2026-09-28) :
- Côté serveur, un état n'est que des données. `StateData` associe chaque nom d'état à la clé de son `BlockType`, et vanilla ne la remplit qu'en décodant `State.Definitions`. La clé d'un état est `*<clé>_State_Definitions_<état>` (`StateData.generateBlockKey`), celle que les tronçons enregistrent. `BlockType.toPacket` envoie au client la table état → id, plus `default` (l. 1330-1336).
- `StateData` n'est pas `final`, son constructeur est `protected`, et ses lecteurs (`getBlockForState`, `getStateForBlock`, `getStateNames`, `toPacket`) sont redéfinissables. Idem pour `BlockType.getDefaultStateKey()`. `runtime/VariantStateData` fournit donc la table d'une variante.
- `DynamicBlockTypeFactory.create` crée le bloc principal et **un bloc par état du gabarit** (copie de `template.getBlockForState(état)`). Tous partagent la table d'états et **une copie de la règle de connexion**, comme vanilla partage une règle par famille (`appendInherited` et `INJECT_PARENT`). La famille entière est enregistrée en un seul `loadAssets`, à la création comme au démarrage.
- **Pièges** :
  - une règle met en cache les ids de son bloc (`updateCachedBlockTypes`, appelé par `ConnectedBlocksModule.onBlockTypesChanged`). Partager celle du gabarit ferait pointer l'un vers l'autre : chaque variante en reçoit une copie, faite par un aller-retour de `ConnectedBlockRuleSet.CODEC` ;
  - **`ConnectedBlockOutput.resolve` écrit la clé qu'il a résolue dans son champ `Block`**. La copie encodée nommait donc les coins du gabarit : vu en jeu, le coin d'une variante devenait un coin du gabarit, en paille. La copie retire donc `Block` de chaque sortie qui déclare un `State` ;
  - le premier `UpdateBlockTypes` est construit avant que `LoadedAssetsEvent` résolve la règle (`AssetStore.loadAssets0`), avec des ids à -1, et ce paquet reste en cache. `VariantBlockType.toPacket` reconstruit donc la règle à chaque envoi. Le double envoi porte ainsi la bonne règle ; sans lui (`--twice=false`), le client garde -1 jusqu'à reconnexion, mais le serveur, qui calcule les connexions, reste juste.
- Tous les bardeaux partagent le `MaterialName` `HyColonyShingle` : ils se relient quel que soit leur matériau, comme dans DO.
- **Vérifié en jeu (2026-09-28)** : deux bardeaux posés en angle forment un coin aux textures de la variante.

**Protocole de test en jeu** (à dérouler sur des combinaisons neuves, résultats à reporter ici) :
1. `/hyornament test oak stone` : les objets arrivent avec leur icône générée, et posés, ils montrent le cadre en bois sur de la pierre, sans scintillement.
2. `/hyornament test birch plaster`, puis une 3ᵉ et une 4ᵉ variante : même question (l'essai du 27/09 cassait dès la 2ᵉ ou la 3ᵉ).
3. `/hyornament test oak stone` à nouveau : le message dit « réutilisé », avec le même id.
4. Un 2ᵉ joueur qui se connecte ensuite voit-il les variantes et leurs icônes ?
5. Redémarrer le serveur : les blocs posés et les objets sont-ils toujours corrects, sans « Unknown » dans le journal ?
- Attendu, pas un défaut : une variante garde les particules, les sons et la couleur de carte du gabarit en pierre.
- Les messages `hyornament:` du journal donnent chaque étape : cache, clé, id attribué, `maxId` avant et après, drapeaux et durée.

#### État final (DO-1, 2026-09-28)

Le prototype est devenu le moteur générique de DO-1 (`plugin/ornament`) : les formes viennent du manifeste généré (`hycolony/ornament/shapes.json`), les matériaux des tags DO (`ornamentTags` du fragment d'id-map, lus sur les `BlockType` vanilla par `MaterialCatalog`), les icônes des cartes d'icône générées au build (`hycolony/ornament/icons/<forme>.png`, lues par `IconMap`). Les réglages vérifiés en jeu ci-dessus sont fixés dans le code : envoi double avec les drapeaux sur le second, `TEXTURES` seulement quand une nouvelle texture de paire est générée, inscription silencieuse des PNG, `UpdateItems` avec `updateIcons`. Les variantes demandées ensemble sont créées en un seul lot. Les gabarits `HyColony_Ornament_*` du prototype sont supprimés. Vérifications en jeu : `docs/TESTING.md`, section « Sous-plugin Domum Ornamentum (DO-1) ».

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
- ~~Génération en cours de partie : le nouveau bloc s'affiche-t-il sans reconnexion ?~~ Tranché le 2026-09-27 : pas de façon fiable (B.6).
- Un objet sans icône propre (icône d'un autre `itemId`) est-il accepté par la validation des assets ? **[in-game]**
