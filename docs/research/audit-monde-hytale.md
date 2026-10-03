# Audit : les règles du monde de MineColonies face au monde de Hytale

Date : 2026-10-02. Spec : `docs/superpowers/specs/2026-10-02-hycolony-monde-hytale-design.md` (§ 5) ; règle : CLAUDE.md § 6. Quatre recherches parallèles (groupes A à D), fusionnées ici.

**Version lue.** `gradle.properties:19` épingle `0.7.0-pre.5`, alors que CLAUDE.md, les agents et la skill `hytale-api` disent encore pre.4. Les quatre groupes ont lu `pre-release-0.7.0-pre.5-Assets.zip` et le décompilé du 2026-10-01. Les faits Hytale cités par `fichier:ligne` du décompilé ou par entrée du zip ont été lus sur pre.5. En revanche, quand une entrée renvoie seulement à une recherche plus ancienne (pre.4 ou 0.6.8 : `sp3b-hytale-farming.md`, `sp4b-hytale-food.md`, `sp4-sleep-home.md`, `colony-bounds-and-mob-spawns.md`, `citizen-death.md`, `plugin-b-api.md`) pour une partie de son fait Hytale, cette partie n'a pas toujours été relue : B-1, B-5, B-7, B-9, B-10, B-16, B-17, B-19, B-23, C-8, C-11, C-17, D-2, D-3, D-14, D-19. Un écart au moins a été trouvé ainsi : `plugin-b-api.md` § 40 place `Seek` à `NPCPlugin.java:902`, qui est l. 894 en pre.5 (D-25).

**Lignes du code.** Les `fichier:ligne` de HyColony sont ceux du commit `db3541a3`. Une autre session modifiait en même temps, sans les avoir commités, `BuilderAI.java`, `EntryCost.java`, `ItemCatalog.java` et `HytaleItemCatalog.java` (correction de A-15 en cours) : leurs lignes dans l'arbre de travail ne sont plus celles citées ici.

## Bilan

| Groupe                            | Entrées | Conforme | À adapter | À retirer de HyVanilla | Casse le jeu | Incohérent | Cosmétique |
|-----------------------------------|---------|----------|-----------|------------------------|--------------|------------|------------|
| A. Outils, matériaux et plans     | 25      | 7        | 15        | 3                      | 2            | 6          | 17         |
| B. Artisanat, culture, nourriture | 30      | 18       | 12        | 0                      | 0            | 6          | 24         |
| C. Temps, mobs et combat          | 23      | 15       | 8         | 0                      | 2 (latents)  | 2 (latents)| 19         |
| D. Navigation et le reste         | 26      | 17       | 9         | 0                      | 0            | 4          | 22         |
| **Total**                         | **104** | **57**   | **44**    | **3**                  | **4**        | **18**     | **82**     |

« Latent » : le défaut ne se voit pas encore, parce que les citoyens sont invulnérables ; il apparaîtra avec le portage de leur mort (C-17) ou des raids.

Fusions : A-18 dans B-15 (coût d'amélioration d'un banc), C-5 dans B-24 (rythme de la faim) ; elles ne comptent qu'une fois. Hors Bilan : A-17, B-14 et C-17, passées dans « Hors du monde : système relevé en passant » (fin du document), avec huit autres points (système, commentaires faux, revue des écarts marqués). Ajouts de cette révision : A-23, A-24, B-25 à B-31, C-23 à C-25, D-25, D-26, et les rattachements notés « Rattaché : » dans B-16, B-19, D-15 et D-24.

Presque toutes les entrées *conformes* n'ont plus qu'à prendre le marquage `Deviation from MC (Hytale world)` : aucun écart du commit `db3541a3` ne l'a encore. Ce marquage se fait avec la correction de chaque domaine.

## Domaines à corriger, dans l'ordre proposé

Chaque entrée de l'audit figure dans une seule ligne. « Entrées » ne liste que les entrées *à adapter* (ou *à retirer de HyVanilla*) ; « marquage » liste les entrées *conformes* du domaine, qui n'ont au plus qu'un marquage `(Hytale world)` à prendre (certaines n'ont rien à faire). La colonne « Nombre » donne : à adapter / conformes / à retirer.

| #  | Domaine                    | Nombre   | Entrées                                                                                         | Gravité la plus haute | Pourquoi à ce rang                                                                                                                                                                                                                                                               |
|----|----------------------------|----------|-------------------------------------------------------------------------------------------------|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | Ressources du chantier     | 4 / 3 / 0  | A-15, A-16, A-20, A-21 (marquage : A-22, A-23, D-26)                                          | casse le jeu          | Un ordre de travail peut demander un objet sans source en survie : torches murales dès la mairie de niveau 1 (A-15), puis `Rock_Stone` et tonneau dès `builder2` et `townhall2` (A-16). Le bâtisseur attendrait pour toujours (déduit des assets et du code, à vérifier en jeu). |
| 2  | Vie et dégâts des citoyens | 4 / 2 / 0  | C-18, C-19, C-14, C-20 (marquage : C-21, C-23)                                                | casse le jeu (latent) | L'échelle de vie (20 contre 100) et l'étouffement sont à régler avant de porter la mort (système, « Hors du monde »).                                                                                                                                                                      |
| 3  | Outils                     | 10 / 4 / 0 | A-3, A-4, A-5, A-6, A-7, A-8, A-10, A-11, A-13, A-14 (marquage : A-1, A-2, A-9, A-12)         | incohérent            | Paliers requis ignorés, blocs « Metals » à mains nues, haches et pelles au palier 0, outil cassé détruit, cisailles absentes.                                                                                                                                                    |
| 4  | Navigation                 | 5 / 7 / 0  | D-4, D-5, D-6, D-3, D-7 (marquage : D-1, D-2, D-8, D-9, D-10, D-12, D-25)                     | incohérent            | Échelles et portes des plans inutilisables par les PNJ, eau ; hauteur de chute et routes à marquer.                                                                                                                                                                              |
| 5  | Artisanat et bancs         | 6 / 4 / 0  | B-26, B-12, B-15 (avec A-18), B-11, B-13, B-29 (marquage : A-19, B-10, B-27, B-28)            | incohérent            | Le fermier peut faire une graine que le monde refuse encore au joueur (B-26), la réduction de recette ne réduit rien (B-12), le bâtisseur demande un tronc précis (B-15, fusion de A-18).                                                                                        |
| 6  | Nourriture                 | 5 / 6 / 0  | B-20, B-19, B-21, B-22 et D-11 regroupées (marquage : B-16, B-17, B-18, B-23, B-24 avec C-5, B-25) | incohérent            | Poulet cru marqué toxique, nutrition calquée sur Minecraft ; le rythme de la faim (B-24) attend une décision.                                                                                                                                                                    |
| 7  | Chunks et monde            | 1 / 5 / 0  | D-14 (marquage : D-13, D-15, D-16, D-17, D-18)                                                | incohérent            | Une colonie passe active sur le seul chunk de son centre.                                                                                                                                                                                                                        |
| 8  | Mobs, raids et effets      | 2 / 7 / 0  | C-15, C-24 (marquage : C-9, C-10, C-11, C-12, C-13, C-16, C-22)                               | incohérent (latent)   | Au portage des raids : nuits par `NightFell`, cultures de MC → factions Hytale ; la lévitation devient `Root`.                                                                                                                                                                   |
| 9  | Culture                    | 3 / 9 / 0  | B-8, B-7, A-24 (marquage : B-1, B-2, B-3, B-4, B-5, B-6, B-9, B-30, B-31)                     | cosmétique            | Clôtures de pre.5 absentes de la liste, culture éternelle replantée au premier stade, bloc remplaçable.                                                                                                                                                                           |
| 10 | Temps et sommeil           | 2 / 6 / 0  | C-4, C-8 (marquage : C-1, C-2, C-3, C-6, C-7, C-25)                                           | cosmétique            | Marche jusqu'au lit, contrôle du lit.                                                                                                                                                                                                                                            |
| 11 | Sons et animations         | 2 / 4 / 0  | D-20, D-24 (marquage : D-19, D-21, D-22, D-23)                                                | cosmétique            | Sons de réussite et d'échec, animation selon l'outil.                                                                                                                                                                                                                            |
| 12 | HyVanilla                  | 0 / 0 / 3  | A-V1, A-V2, A-V3 (à retirer de HyVanilla)                                                     | cosmétique            | Décision 1 de l'utilisateur.                                                                                                                                                                                                                                                     |
| —  | Hors du monde (système)    | hors Bilan | A-17, B-14, C-17 et huit points (fin du document)                                             | incohérent            | Système de MC : presse-papiers qui mange l'outil (B-14), mort non portée (C-17), feuilles gratuites (A-17).                                                                                                                                                                      |

Total des lignes 1 à 12 : 44 à adapter, 57 conformes, 3 à retirer, soit les 104 entrées du Bilan.

B-22 et D-11 ne sont pas des doublons : ce sont deux règles du même effet (couleur des miettes, hauteur d'où elles partent), **regroupées** dans le domaine Nourriture pour être corrigées ensemble.

## Décisions qui reviennent à l'utilisateur

1. **HyVanilla** (A-V1 à A-V3). Aucun des trois blocs n'est nécessaire à un système : un lit Hytale 1×2 existe (`Furniture_Kweebec_Bed`), les tapis ont un équivalent de même forme mais en toile (`Cloth_Roof_<C>_Flat`, 8 couleurs), et Hytale n'a toujours pas de pot garni. Ce sont des ajouts que l'utilisateur avait demandés : les garder, ou les retirer après conversion des plans et migration (spec § 6).
2. **L'eau** (D-6) : (a) les citoyens respirent sous l'eau (`BreathesInWater`) et y marchent, l'équivalent le plus proche de la nage ; ou (b) les cases dont les pieds sont dans un fluide sont exclues des points d'arrivée.
3. **Le rythme de la faim** (B-24, fusion de C-5) : la règle de la zone grise garde une baisse toutes les 1 200 ticks de jour, mais le jour de Hytale (1 728 s, soit 34 560 ticks) est bien plus long que celui de MC (environ 12 600 ticks : seuil de `WorldUtil.isDayTime`, alors que MC appelle `level().isNight()`, règle vanilla non vérifiée) : une colonie mange environ 2,7 fois plus par journée, alors que le fermier ne passe qu'une fois par jour sur chaque champ. Garder la cadence de MC, ou la compenser par `Gameplay.FoodModifier`.
4. **Les tempêtes** (C-9) : compter `Sand_Storm` et `Ash_Storm` comme de la pluie (arrêt du travail), ou non.

Le système relevé en passant (A-17, B-14, C-17, `CLEAR_WATER`, commentaires faux, version) est regroupé à la fin du document : « Hors du monde : système relevé en passant ».

---

## A. Outils, matériaux et plans

Audit du 2026-10-02 (spec `docs/superpowers/specs/2026-10-02-hycolony-monde-hytale-design.md` § 2 et § 5), groupe A : paliers et durabilité des outils, blocs cassables, ressources du chantier, `styles.json`, `id-map.json`, et chaque bloc de HyVanilla.

Sources :
- **MC** = `sources/minecolonies/src/main/java/com/minecolonies/` ; **ST** = `sources/structurize/src/main/java/com/ldtteam/structurize/` (copies locales, `version/main`). La table bloc → objet de Minecraft vanilla (`GameData.getBlockItemMap`) n'est pas dans `sources/` : quand une entrée s'y appuie, elle le dit.
- **HY** = sources décompilées `build/vineflower/hytale-server/com/hypixel/hytale/` (générées le 2026-10-01, sans numéro de version dans les fichiers).
- **zip** = `~/.gradle/caches/hytale-assets/pre-release-0.7.0-pre.5-Assets.zip`. `gradle.properties:19` épingle `hytale_version = 0.7.0-pre.5` (la consigne disait pre.4) : c'est ce zip qui a été lu. Les chiffres de `builder-tools-durability-breaking.md` (0.6.8) ont été relus sur pre.5 et ont changé (pioche Crude : 200 de durabilité au lieu de 150).
- **HC** = `core/src/main/java/dev/hycolony/core/` ; **HP** = `plugin/src/main/java/dev/hycolony/plugin/` ; **HR** = `plugin/src/main/resources/` ; **HV** = `vanilla/` ; plans = `plugin/src/subplugins/Styles_MedievalOak/` (le `hycolony/styles.json` du cœur est vide, `{}`).

Lecture des verdicts : *conforme* = la règle suit déjà Hytale ; sa gravité dit ce qui reste (le plus souvent le marquage `Deviation from MC (Hytale world): …` de la spec § 3, que les écarts existants n'ont pas encore). *À adapter* = la règle recopie le monde de Minecraft, ou s'appuie sur un fait Hytale périmé ou incomplet. *À retirer de HyVanilla* = un équivalent Hytale existe, ou le système s'en passe (CLAUDE.md § 6).

Renvois hors groupe A, vus en cherchant les `Deviation from MC` : outil d'engrais (`HC/farming/hut/FarmerSettingsModule.java:57`, `HC/farming/job/FieldPass.java:194`) → B-5 ; fluide retiré une fois (`HC/construction/builder/BuilderBlockWork.java:182`) → D-26 ; hache non gardée par le fermier (`HC/farming/hut/FarmerHut.java:40`) → B-31 (du monde : la hache de MC ne sert qu'à miner citrouilles et melons). L'eau non vidée (`HC/construction/workorder/Stage.java:14`, MC `CLEAR_WATER`) est du système : « Hors du monde ». `HP/ui/citizen/CitizenItemContainer.java:119` (métadonnées non gardées) est une limite du modèle d'objet, pas une règle du monde. `HC/colony/permission/BlockUse.java:12` relève des permissions (système).

| Entrée | Sujet                                             | Verdict                | Gravité      |
|--------|---------------------------------------------------|------------------------|--------------|
| A-1    | Outil requis par un bloc                          | conforme               | cosmétique   |
| A-2    | Palier d'une pioche, plafond de la hutte          | conforme               | cosmétique   |
| A-3    | Palier requis par le bloc                         | à adapter              | incohérent   |
| A-4    | Blocs « Metals » cassés à mains nues              | à adapter              | incohérent   |
| A-5    | Haches et pelles toutes au palier 0               | à adapter              | incohérent   |
| A-6    | Vitesse de minage hors du type de l'outil         | à adapter              | cosmétique   |
| A-7    | Usure : 1 usage par bloc, quel que soit le bloc   | à adapter              | cosmétique   |
| A-8    | Outil cassé détruit                               | à adapter              | incohérent   |
| A-9    | Conversion de la durabilité, réparation           | conforme               | cosmétique   |
| A-10   | Pioche de ferraille acceptée comme outil          | à adapter              | incohérent   |
| A-11   | Cisailles absentes                                | à adapter              | incohérent   |
| A-12   | Houes : paliers et usure                          | conforme               | cosmétique   |
| A-13   | Nom du palier dans les requêtes                   | à adapter              | cosmétique   |
| A-14   | Ce qui est un minerai                             | à adapter              | cosmétique   |
| A-15   | Objet demandé pour poser un bloc                  | à adapter              | casse le jeu |
| A-16   | Blocs des plans sans source en survie             | à adapter              | casse le jeu |
| A-17   | Blocs gratuits (feuilles) : système, déplacée     | (hors du monde)        | —            |
| A-18   | Amélioration d'un banc : fusionnée dans B-15      | (fusionnée)            | —            |
| A-19   | Bancs à palier dans le coût d'une case            | conforme               | cosmétique   |
| A-20   | Case de fluide satisfaite par un fluide qui coule | à adapter              | cosmétique   |
| A-21   | « Bon sol » des placeholders                      | à adapter              | cosmétique   |
| A-22   | Bloc de remplissage                               | conforme               | cosmétique   |
| A-23   | Ruban de chantier : forme et case libre           | conforme               | cosmétique   |
| A-24   | Bloc remplaçable sur une case à labourer          | à adapter              | cosmétique   |
| A-V1   | HyVanilla : lits 1×2                              | à retirer de HyVanilla | cosmétique   |
| A-V2   | HyVanilla : tapis                                 | à retirer de HyVanilla | cosmétique   |
| A-V3   | HyVanilla : pots de fleurs                        | à retirer de HyVanilla | cosmétique   |

### Outils : paliers, durabilité, blocs qu'ils cassent

#### A-1. Outil requis par un bloc
1. **MC** : `MC/core/util/WorkerUtil.java:142-171` (`getBestToolForBlock`) : pour un bloc de dureté > 0, le premier outil d'essai dont `isCorrectToolForDrops` accepte le bloc (tags `mineable/<outil>` de Minecraft) ; sinon `none`. `MC/core/entity/ai/workers/AbstractEntityAIBasic.java:1336-1352` (`holdEfficientTool`) : sans outil requis, main vide.
2. **HC** : `HP/adapter/HytaleItemCatalog.java:313-327` (`toolType`) : `GatherType` du bloc `Ore*`, `Rocks`, `VolcanicRocks`, `GoblinMetal` → pioche, `Woods`, `SoftWoods` → hache, `Soils` → pelle, le reste sans outil ; `HC/construction/builder/BuilderBlockWork.java:106-110`. L'outil d'un objet vient de son `PlayerAnimationsId` (`HP/item/HytaleItemInfo.java:74-81`). Écart marqué en tête de `HC/kernel/item/ToolScale.java:3-8`, sans le préfixe `(Hytale world)`.
3. **Hytale** : chaque bloc a `Gathering.Breaking.GatherType` (`zip:Server/Item/Items/Rock/Stone/Rock_Stone.json` : `Rocks`) ; chaque outil a une `Spec` par type (`zip:Server/Item/Items/Tool/Pickaxe/Tool_Pickaxe_Crude.json`, `Tool.Specs`) ; le coup prend la spec du type du bloc (`HY/server/core/modules/interaction/BlockHarvestUtils.java:672-716`, `getSpecPowerDamageBlock`). `PlayerAnimationsId` est hérité du `Parent` (`HY/server/core/asset/type/item/config/Item.java:236-237`, `appendInherited`), donc `Tool_Pickaxe_Iron` reste une pioche.
4. **Verdict** : conforme.
5. **Proposition** : reprendre le marquage en `Deviation from MC (Hytale world): MC's mineable tags → Hytale's GatherType per block and tool spec (BlockHarvestUtils.getSpecPowerDamageBlock)`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### A-2. Palier d'une pioche, plafond de la hutte
1. **MC** : paliers `TOOL_LEVEL_WOOD_OR_GOLD = 0`, `BASIC_TOOL_LEVEL = 1` (pierre), puis fer 2, diamant 3, netherite 4 (`MC/api/util/constant/EquipmentLevelConstants.java:14-26, 68` ; noms `sources/minecolonies/src/main/resources/assets/minecolonies/lang/manual_en_us.json:913-918`). Plafond de la hutte : `MC/api/colony/buildings/IBuilding.java:447-458` (`getMaxEquipmentLevel` : 1 au niveau 0, le niveau ensuite, illimité au niveau max). Choix : l'outil du plus bas palier dans `[requis, plafond]` (`AbstractEntityAIBasic.java:1405-1431`).
2. **HC** : `HC/kernel/item/ToolScale.java:27-29` (`level = max(0, Quality - 1)` de la spec `Rocks` de la pioche, `HP/item/HytaleItemInfo.java:58-71`) ; plafond `HC/building/Building.java:140-145` (copie de MC) ; choix `HC/job/work/WorkerStock.java:192-224`.
3. **Hytale** : `Quality` de la spec `Rocks` (zip `Server/Item/Items/Tool/Pickaxe/*.json`) : Wood, Crude, Scrap 1 ; Copper 2 ; Iron 3 ; Cobalt, Thorium 4 ; Adamantite 5 ; Mithril, Onyxium 6. D'où les paliers 0 à 5, alignés sur MC : Crude ≈ bois, Copper ≈ pierre, Iron ≈ fer, Cobalt/Thorium ≈ diamant, Adamantite ≈ netherite, Mithril/Onyxium au-delà. C'est la qualité que Hytale compare au bloc (`BlockHarvestUtils.java:692-708`).
4. **Verdict** : conforme.
5. **Proposition** : marquage `(Hytale world)` sur `ToolScale.level`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### A-3. Palier requis par le bloc
1. **MC** : `MC/core/util/WorkerUtil.java:179-198` (`getCorrectHarvestLevelForBlock`, tier du tag du bloc) ; `AbstractEntityAIBasic.java:1425` (`level >= required`) ; si la hutte plafonne sous le requis, interaction « niveau de bâtiment trop bas » (`:1365-1374`) et requête d'outil au palier requis pour la pioche (`:1377`, `updateToolFlag` `:1386-1396`).
2. **HC** : aucun palier requis. `WorkerStock.toolInInventory` (`HC/job/work/WorkerStock.java:192-204`) prend tout outil du bon type ≤ plafond ; `ToolRequests.requestTool` demande toujours `ToolRequest(type, 0, plafond)` (`HC/job/work/ToolRequests.java:34-41`). Copie partielle de MC, sans marquage.
3. **Hytale** : deux verrous. (a) `Breaking.Quality` du bloc contre la `Quality` de la spec : en dessous, la spec est refusée et le coup ne fait rien (`BlockHarvestUtils.java:692-708`). Blocs concernés dans le zip : `Rock_Basalt`, `Rock_Lime`, `Rock_Shale_Cobble` et petites stalactites (1, toute pioche), grandes stalactites `Rock_*_Stalactite_Large` (2, Copper+), `Ore_Mithril_Stone` (5, Adamantite+), `Ore_Adamantite_Magma` (4, sur `OreAdamantite` dont aucune spec n'a de `Quality`). (b) `IsIncorrect` sur la spec d'un type (`BlockHarvestUtils.java:1098-1115`) : `GoblinMetal` est incorrect pour Wood, Crude et Scrap, correct dès Copper (`Tool_Pickaxe_Copper.json` : 0,17).
4. **Verdict** : à adapter.
5. **Proposition** : `ItemCatalog.requiredLevel(BlockKey)` = le plus bas palier de pioche dont la spec du type du bloc n'est ni `IsIncorrect` ni sous `Breaking.Quality` ; le cœur reprend alors le `level >= required`, l'interaction « niveau trop bas » et la requête `ToolRequest(type, requis, plafond)` de MC. Sauvegarde : rien (une `ToolRequest` garde déjà ses bornes, `HC/request/model/ToolRequest.java:12`). Config : aucune. Clé de traduction pour l'interaction.
- **Gravité** : incohérent (une pioche Crude ouvre `Ore_Mithril_Stone`, que Hytale lui refuse).

#### A-4. Blocs « Metals » cassés à mains nues
1. **MC** : un bloc dont l'outil requis dépasse le plafond bloque l'ouvrier sur l'interaction « niveau trop bas » (A-3) ; MC ne casse jamais un bloc sans l'outil correct (`WorkerUtil.java:149-170`).
2. **HC** : `HytaleItemCatalog.java:321` exclut `Metals` (« every U7 tool lists it IsIncorrect ») : pas d'outil, donc le bâtisseur le casse à mains nues, à la dureté plafonnée 3 (`ToolScale.java:14, 22-24` ; `unarmed` `Metals` vaut 0,001).
3. **Hytale** : en pre.5, `Tool_Pickaxe_Mithril.json` a `Metals` à 0,5 sans `IsIncorrect` ; toutes les autres pioches, haches et pelles l'ont `IsIncorrect` à 0,001 ; mains nues 0,001 (`zip:Server/Item/Unarmed/Gathering/Metals.json`). 58 blocs ont ce type (`Metal_Goblin_Iron*`, coffre gobelin…). Le commentaire de `:321` est donc périmé.
4. **Verdict** : à adapter.
5. **Proposition** : `Metals` → pioche, avec le palier requis de A-3 (Mithril, palier 5) ; à défaut de A-3, le traiter comme `UNBREAKABLE` comme `Unbreakable` (`HytaleItemCatalog.java:289-291`). Ni sauvegarde ni config.
- **Gravité** : incohérent.

#### A-5. Haches et pelles toutes au palier 0
1. **MC** : le plafond de la hutte vaut pour chaque type d'outil, hache et pelle comprises (`MC/core/colony/buildings/workerbuildings/BuildingBuilder.java:71-75`, `hasEquipmentLevel(…, TOOL_LEVEL_WOOD_OR_GOLD, getMaxEquipmentLevel())`).
2. **HC** : `HytaleItemInfo.java:58-71` lit le palier sur la spec `Woods` ou `Soils`, qui n'a jamais de `Quality` : toute hache et toute pelle valent 0 (dit dans `HytaleItemCatalog.java:41-44`). Une hutte de niveau 1 accepte donc une hache d'Adamantite, et `toolInInventory` départage les haches par l'ordre des cases.
3. **Hytale** : `Tool_Hatchet_*.json` et `Tool_Shovel_*.json` n'ont de `Quality` sur aucune spec (relevé sur les 10 haches et 5 pelles du zip). Les matériaux sont ceux des pioches (Crude, Copper, Iron, Cobalt, Thorium, Adamantite, Mithril, Onyxium). `ItemLevel` n'est pas fiable : il est hérité du `Parent` (`Item.java:137-138`), si bien qu'`Onyxium` et les pelles `Copper`, `Cobalt`, `Thorium` héritent celui de Crude. La rareté `Quality` (`Item.java:159`, non héritée) met Copper au rang de Crude (`Common`).
4. **Verdict** : à adapter.
5. **Proposition** : une table `toolLevels` dans `hycolony/id-map.json`, sur le modèle de `farming.hoes` (`HR/hycolony/id-map.json:182`), qui donne à chaque hache et pelle le palier de la pioche du même matériau (Crude 0, Copper 1, Iron 2, Cobalt et Thorium 3, Adamantite 4, Mithril et Onyxium 5). Ni sauvegarde ni config.
- **Gravité** : incohérent.

#### A-6. Vitesse de minage hors du type de l'outil
1. **MC** : `MC/core/entity/ai/workers/AbstractEntityAIInteract.java:334-342` : délai = `500 × 0,85^(niveau/2) × dureté du bloc / getDestroySpeed(outil, bloc)`, la vitesse de l'outil **sur ce bloc**.
2. **HC** : `HC/construction/shared/BuilderTimings.java:20-24` (formule copiée) avec `WorkerStock.toolSpeed` (`HC/job/work/WorkerStock.java:227-229`) : la vitesse de l'outil sur **son propre** type (`Rocks` pour une pioche, `HytaleItemInfo.java:59-67`), quel que soit le bloc. Dureté `0,05 / puissance à mains nues`, plafonnée à 3 (`ToolScale.java:14, 22-24`). Le rapport « dureté / vitesse = 0,05 / puissance » annoncé (`HytaleItemCatalog.java:45-47`) ne tient que sur le type de l'outil.
3. **Hytale** : une pioche a une spec par minerai (`Tool_Pickaxe_Crude.json` : `Rocks` 0,25, `OreIron` 0,084, `VolcanicRocks` 0,084) ; chaque coup retire la puissance de la spec du bloc (`BlockHarvestUtils.java:894-910`), soit `ceil(1/0,084) = 12` coups pour du fer contre 4 pour de la pierre. Avec HyColony, le fer prend `3 / 7,14` contre `1,43 / 7,14` pour la pierre : 2,1 fois plus long au lieu de 3.
4. **Verdict** : à adapter.
5. **Proposition** : `ItemCatalog.toolSpeed(ItemKey, BlockKey)` = puissance de la spec du type du bloc sur celle des mains nues, comme `getDestroySpeed(outil, bloc)`. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### A-7. Usure : 1 usage par bloc, quel que soit le bloc
1. **MC** : `MC/core/util/citizenutils/CitizenItemUtils.java:183` (`damageItemInHand(…, 1)`) : 1 point par bloc cassé, quel que soit le bloc.
2. **HC** : `HC/construction/builder/BuilderBlockWork.java:190-193` use l'outil d'1 usage ; un usage vaut un bloc du type propre de l'outil, `maxDurability / (perte par coup × ceil(1/puissance))` (`ToolScale.java:43-49`, `HytaleItemInfo.java:97-108`) : Crude 200 pierres, Iron 1 000 (zip pre.5 : 200 et 500 de durabilité, 0,25 par coup). C'est l'équivalent de MC en unités Hytale.
3. **Hytale** : la perte suit chaque coup, selon le bloc (`BlockHarvestUtils.java:718-765`, `calculateDurabilityUse` : entrée `DurabilityLossBlockTypes` du set du bloc, sinon `DurabilityLossOnHit`, 0 pour un bloc `Soft`). Un minerai de fer coûte à une pioche Crude 12 coups × 0,25 = 3 points, contre 1 pour la pierre ; HyColony compte 1 usage dans les deux cas.
4. **Verdict** : à adapter.
5. **Proposition** : user de `ceil(1/puissance du bloc) × perte du set du bloc`, converti en usages (`ItemCatalog.wear(ItemKey, BlockKey)`), au lieu de 1. Sauvegarde : rien (`damage` reste en usages). Config : aucune.
- **Gravité** : cosmétique.

#### A-8. Outil cassé détruit
1. **MC** : `MC/api/inventory/InventoryCitizen.java:366-380` (`damageInventoryItem` → `hurtAndBreak`, la pile disparaît) ; `CitizenItemUtils.java:236-240` vide la main.
2. **HC** : `HC/kernel/item/Inventory.java:96-109` (`damage`) vide la case à `durability` usages. Copie de MC, sans marquage ; `builder-tools-durability-breaking.md` § A.2 avait choisi de garder la règle de MC.
3. **Hytale** : l'objet reste à 0 de durabilité (`HY/server/core/entity/ItemUtils.java:169-196` : message `server.general.repair.itemBroken` et son `SFX_Item_Break`) ; il mine avec la pénalité `BrokenPenalties` (`BlockHarvestUtils.java:897-900`) et se répare (`HY/server/core/entity/entities/player/pages/itemrepair/RepairItemInteraction.java:40-42`, max réduit).
4. **Verdict** : à adapter.
5. **Proposition** : `Inventory.damage` garde la pile à `damage == durability` au lieu de la retirer ; elle est déjà « usée » partout (`ItemCatalog.wornOut`, `HC/kernel/port/ItemCatalog.java:69-72` ; jamais choisie, jamais gardée, `HC/logistics/pickup/HutKeep.java:85-91`). Le dépôt la ramène à la hutte, où le joueur la répare. La requête d'outil de MC reste la seule annonce. Sauvegarde : rien (une pile usée se lit déjà). Config : aucune.
- **Gravité** : incohérent (l'outil d'un joueur disparaît là où Hytale le laisse réparer).

#### A-9. Conversion de la durabilité, réparation
1. **MC** : `ItemStack.getDamageValue` (`InventoryCitizen.java:373`) : des points entiers, 1 par bloc.
2. **HC** : `HC/kernel/item/DurabilityScale.java:16-36` et `HP/item/HytaleStacks.java:24-48` : usages ↔ points Hytale, arrondi vers l'usure ; le max qu'une réparation retire compte comme usure.
3. **Hytale** : `durability` et `maxDurability` en `double` sur la pile (`HY/server/core/inventory/ItemStack.java:139, 192-210`) ; une réparation baisse le max de la pile (`RepairItemInteraction.java:42`).
4. **Verdict** : conforme.
5. **Proposition** : marquage `(Hytale world)` sur `DurabilityScale`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### A-10. Pioche de ferraille acceptée comme outil
1. **MC** : un outil compte s'il est l'outil correct du bloc (`WorkerUtil.java:153-165`) ; à palier égal, MC n'a pas d'outil inutile.
2. **HC** : `HytaleItemInfo.java:58-71` fait de `Tool_Pickaxe_Scrap` une pioche de palier 0 (spec `Rocks` de `Quality` 1) et de vitesse `0,001 / 0,035 ≈ 0,03` (`ToolScale.java:32-37`). Elle répond à une `ToolRequest` (`HC/request/model/ToolRequest.java:21-25`) et `toolInInventory` peut la tenir à égalité avec Crude ; un bloc de pierre prend alors environ `500 × 1,43 / 0,03 × 0,5 ≈ 12 000` ticks.
3. **Hytale** : `Tool_Pickaxe_Scrap.json` : toutes ses specs à 0,001, aucune recette, obtenue sur `Drop_Goblin_Miner` (`zip:Server/Drops/NPCs/Intelligent/Goblin/Drop_Goblin_Miner.json`) et faite pour être recyclée (`zip:Server/Item/Recipes/Salvage/Salvage_Tool_Pickaxe_Scrap.json`).
4. **Verdict** : à adapter.
5. **Proposition** : le catalogue ne tient pas pour outil un objet dont la puissance sur son type ne dépasse pas celle des mains nues (`speed <= 1`). Ni sauvegarde ni config.
- **Gravité** : incohérent.

#### A-11. Cisailles absentes
1. **MC** : le bâtisseur garde des cisailles (`BuildingBuilder.java:75`) et a le réglage « Use Shears », faux par défaut (`MC/core/colony/buildings/modules/BuildingModules.java:438`, clé `MC/core/colony/buildings/AbstractBuilding.java:110`) ; activé, les blocs `IForgeShearable` (feuilles…) se coupent aux cisailles (`WorkerUtil.java:144-147`). Le bûcheron s'en sert aussi (`MC/core/entity/ai/workers/production/EntityAIWorkLumberjack.java:295`).
2. **HC** : écart marqué « no shears, HyColony has no such tool type yet » (`HC/construction/hut/ConstructionBuildingTypes.java:33-38`) et « no shears in HyColony » (`HC/construction/shared/BuilderSettingsModule.java:85-88`). `ToolType` n'a que pioche, hache, pelle, houe (`HC/kernel/item/ToolType.java`).
3. **Hytale** : `zip:Server/Item/Items/Tool/Shears/Tool_Shears_Basic.json` (`PlayerAnimationsId: Shears`, spec `SoftBlocks` 1,0, recette 1 `Ingredient_Bar_Iron` au `Farmingbench`, sans `MaxDurability`). Les blocs qui veulent des cisailles le disent par `Gathering.Tools: [{Type: Shears}]` (`HY/server/core/asset/type/blocktype/config/BlockGathering.java:42, 131-151`) : feuilles (`Server/Item/Items/Plant/Leaves`), mousses, roseaux, `Plant_Grass_Lush`… ; casser avec l'outil nommé donne l'objet ou l'état de cette entrée (`BlockHarvestUtils.java:1006-1030`).
4. **Verdict** : à adapter (l'écart n'a plus de raison).
5. **Proposition** : `ToolType.SHEARS` (`PlayerAnimationsId` `Shears`), gardé par le bâtisseur comme MC, réglage `useshears` (faux) dans `BuilderSettingsModule`, et `toolFor` = cisailles pour un bloc à `Tools` `Shears` quand le réglage est vrai. Sauvegarde : une clé de réglage de plus, lue avec son défaut (lecture tolérante, § 5 ; passer par la skill `add-migration` si l'on change le schéma). Config : aucune. Clés de traduction du réglage.
- **Gravité** : incohérent.

#### A-12. Houes : paliers et usure
1. **MC** : la houe est un outil à palier comme les autres (`BuildingBuilder.java:74`) et s'use d'1 par usage (`CitizenItemUtils.java:183, 211-243`).
2. **HC** : paliers dans `HR/hycolony/id-map.json:182` (`Tool_Hoe_Crude` 0, `Copper` 1, `Iron` 2, `Thorium` 3), lus par `HP/item/HytaleItemInfo.java:30-38`, avec 1 usage par labour et autant d'usages que de durabilité.
3. **Hytale** : les houes n'ont pas de `Tool.Specs` (zip `Server/Item/Items/Tool/Hoe/*.json`) ; `Hoe_Till` retire 1 de durabilité par labour (`zip:Server/Item/Interactions/Weapons/Hoe/Attacks/Till/Hoe_Till.json`, `AdjustHeldItemDurability: -1`). La table suit les paliers des pioches du même matériau (A-2).
4. **Verdict** : conforme.
5. **Proposition** : marquage `(Hytale world)` sur `HytaleItemInfo.hoe` ; fondre la table dans le `toolLevels` de A-5. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### A-13. Nom du palier dans les requêtes
1. **MC** : `MC/api/util/ItemStackUtils.java:436-444` (`swapToolGrade`) nomme le palier : « Wood or Gold », « Stone », « Iron », « Diamond », « Netherite », « Better than Netherite » (`manual_en_us.json:913-918`).
2. **HC** : écart marqué « a tool level is its number » (`HP/ui/request/RequestTexts.java:22-23, 115-118`). L'icône d'une requête d'outil est l'outil Crude du type (`HP/IdMap.java:130-133`, `id-map.json:14-17`) et l'objet montré le plus bas palier (`ToolRequest.java:32-39`, écart « no creative tab order », conforme).
3. **Hytale** : les paliers ont des matériaux (A-2) ; les noms traduits des outils existent (`server.items.Tool_Pickaxe_<Matériau>.name`, ex. `Tool_Pickaxe_Crude.json`, `TranslationProperties`).
4. **Verdict** : à adapter.
5. **Proposition** : une clé par palier (« Bois ou fruste », « Cuivre », « Fer », « Cobalt ou thorium », « Adamantite », « Mithril ou onyxium ») dans en-US et fr-FR, à la place du nombre, comme `swapToolGrade`. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

### Matériaux et blocs cassables

#### A-14. Ce qui est un minerai
1. **MC** : `MC/api/compatibility/CompatibilityManager.java:495-503, 697-709` : un bloc du tag `forge:ores` (ou `breakable_ore`, `raw_ore`) ; le bâtisseur ne garde pas ce qu'il donne (`MC/core/entity/ai/workers/builder/EntityAIStructureBuilder.java:190-192`).
2. **HC** : `HytaleItemCatalog.java:296` : `GatherType` commençant par `Ore` ; `BuilderBlockWork.java:187-189` copie la règle des gouttes.
3. **Hytale** : le set de blocs `Ores` (`zip:Server/Item/Block/Sets/Ores.json`, `IncludeBlockTypes: ["Ore*"]`) est celui dont Hytale se sert pour l'usure (`BlockHarvestUtils.java:750-758`) ; `HY/server/core/modules/blockset/BlockSetModule.java:207` (`blockInSet(int, String)`). Quatre blocs `Ore_*` ont le `GatherType` `Rocks` et échappent à la règle de HyColony : `Ore_Mithril_Stone`, `Ore_Adamantite_Magma_Cracked`, `Ore_Cobalt_Slate_Cracked`, `Ore_Iron_Basalt_Cracked`.
4. **Verdict** : à adapter.
5. **Proposition** : `isOre` = appartenance au set `Ores`. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

### Ressources du chantier et plans

#### A-15. Objet demandé pour poser un bloc
1. **MC** : `ST/util/BlockUtils.java:505-518` (`getItemStackFromBlockState`) et `:330-373` (`getItem`) : l'objet qui **pose** le bloc (`GameData.getBlockItemMap`, table de Minecraft vanilla hors `sources/` : la torche murale demande une torche), la terre pour la terre labourée et le chemin, la graine pour une culture ; `ST/placement/handlers/placement/PlacementHandlers.java:404-436` : l'herbe demande de la terre.
2. **HC** : `HC/construction/resources/EntryCost.java:32-35` demande `itemForBlock`, soit `BlockType.getItem()` (`HytaleItemCatalog.java:284-285`) : l'objet qui **contient** le bloc. Pas de marquage.
3. **Hytale** : `BlockType.getItem()` rend l'asset conteneur (`HY/server/core/asset/type/blocktype/config/BlockType.java:1350-1357`). Or plusieurs blocs se posent avec un autre objet : variantes de pose (`BlockPlacementSettings.java:41, 118-126` ; `Furniture_Crude_Torch.json:88` `WallPlacementOverrideBlockId: Wood_Torch_Wall`, `Deco_Lantern.json:38` `CeilingPlacementOverrideBlockId: Deco_Lantern_Ceiling`), blocs connectés (`BlockType.java:1578` ; `Furniture_Crude_Chest_Small.json:56-61` : deux petits coffres forment `Furniture_Crude_Chest_Large` ; `Deco_Iron_Bars.json:63` : `Deco_Iron_Bars_Corner`), troncs pleins (`Wood_Oak_Trunk_Full.json` : aucune recette, cassé donne `Wood_Oak_Trunk`). Aucun de ces objets n'a de recette, de liste de gouttes ni de troc qui le produise (recherche de `"ItemId": "<id>"` dans `Server/Item`, `Server/Drops`, `Server/BarterShops`). Dans les plans MedievalOak : `Wood_Torch_Wall` 350 cases dans 34 plans, dont `builder1` et `townhall1` ; `Furniture_Crude_Chest_Large` 105 (dont `warehouse1`) ; `Wood_Oak_Trunk_Full` 76 (dont `cook1`, `farmer1`) ; `Deco_Lantern_Ceiling` 72 ; `Deco_Iron_Bars_Corner` 15.
4. **Verdict** : à adapter.
5. **Proposition** : `itemForBlock` rend l'objet qui pose le bloc, comme `getBlockItemMap` : (a) l'objet dont une `*PlacementOverrideBlockId` nomme le bloc ; (b) pour une variante de bloc connecté, l'objet de base, autant de fois que de cellules d'origine (le port renvoie alors une `ItemAmount`) ; (c) sinon, si l'objet conteneur n'a aucune source, l'unique goutte du bloc (`prefab-obtainability.md` § 5.1, règle A). Un test `selftest` peut lister les objets demandés par les plans qui n'ont aucune source. Sauvegarde : les besoins sont recalculés à chaque reprise (`HC/construction/resources/BuildingResourcesModule.java:24-28`), mais les requêtes déjà ouvertes pour l'ancien objet restent dans la sauvegarde et sont à annuler au chargement. Config : aucune.
- **Gravité** : casse le jeu (le bâtisseur attendrait pour toujours un objet introuvable en survie, dès la mairie de niveau 1 ; déduit des assets et du code) **[in-game]**.

#### A-16. Blocs des plans sans source en survie
1. **MC** : le bâtisseur demande chaque objet du plan (`MC/core/entity/ai/workers/AbstractEntityAIStructure.java:750-857`, `hasListOfResInInvOrRequest`) ; dans Minecraft, tout bloc de construction vanilla s'obtient en survie (fait du monde Minecraft, hors `sources/`).
2. **HC** : les plans de `Styles_MedievalOak` sont convertis de MC par `tools/blueprint/`, avec la table `tools/blueprint/data/default-block-overrides.csv` (ex. `:47, 150, 174` : andésite, diorite, granite → `Rock_Stone`). Aucun contrôle de source dans le convertisseur ni dans le plugin.
3. **Hytale** (zip pre.5) : sans recette, sans goutte qui les donne, sans troc :
   - `Rock_Stone` : 530 cases dans `builder2-3`, `townhall2-5`, `farmer4-5` ; cassé, il donne `Rock_Stone_Cobble` (`Rock_Stone.json`, `Gathering.Breaking.ItemId`) ;
   - `Furniture_Tavern_Barrel` : 78 cases, 14 plans, dont `cook2` et `residence2` ; sa goutte est la liste de butin `Barrels` (`zip:Server/Drops/Items/Barrels.json`).
   - À vérifier, sans recette ni source vue hors génération du monde (non parcourue ici) : `Furniture_Village_Bookcase` (37, sept plans de niveau 4-5), `Rock_Runic_Dark_Brick*` (14, `builder5`), `Rock_Stone_Cobble_Mossy` (53), `Metal_Iron_Pipe_Large_Mouthpiece` (1), et les fleurs `Plant_Flower_*` dont la casse donne une liste `*_Harvest`.
   - Équivalents vérifiés : `Rock_Stone_Brick_Smooth` (recette `Builders`/`Smooth`, 1 `Rock_Stone` en type de ressource), `Furniture_Ancient_Bookshelf` (recette `Builders`/`Bookshelf`), `Furniture_Crude_Chest_Small` (recette `Workbench`, 10 troncs) pour un tonneau-conteneur.
4. **Verdict** : à adapter.
5. **Proposition** : corriger la table de conversion avec les équivalents ci-dessus, reconvertir les plans, et ajouter au convertisseur une vérification de source (le modèle de `prefab-obtainability.md` § 1.2) qui échoue sur un objet introuvable. Sauvegarde : un plan changé change les besoins des ordres en cours (recalculés) ; les ordres déjà commencés gardent les cases posées. Config : aucune.
- **Gravité** : casse le jeu (ordre bloqué dès `builder2` et `townhall2`) **[in-game]**.

#### A-17. Blocs gratuits (feuilles)

Déplacée dans « Hors du monde : système relevé en passant » (fin du document) : `isBlockFree` est une règle du système
de MC, non portée.

#### A-18. Amélioration d'un banc : un objet précis

Fusionnée dans B-15, qui porte seule le coût d'amélioration d'un banc demandé par type de ressource (avec les lignes de
`Bench_Farming.json`).

#### A-19. Bancs à palier dans le coût d'une case
1. **MC** : un bloc coûte son objet (`ST/util/BlockUtils.java:505-518`) ; les bancs de MC n'ont pas de palier.
2. **HC** : `EntryCost.java:30-47`, `HC/construction/builder/PlannedBlocks.java:92-96`, `HC/kernel/item/Workstation.java:7` : le banc coûte son objet plus les améliorations jusqu'au palier du plan. Écarts marqués (spec SP3b-1), sans `(Hytale world)`.
3. **Hytale** : les recettes demandent un palier de banc (`RequiredTierLevel`, ex. `Furniture_Village_Planter.json`) et le banc monte par `TierLevels[].UpgradeRequirement.Material` (`zip:Server/Item/Items/Bench/Bench_Farming.json:63-127` ; matériaux détaillés dans B-15).
4. **Verdict** : conforme.
5. **Proposition** : marquage `(Hytale world)`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### A-20. Case de fluide satisfaite par un fluide qui coule
1. **MC** : `ST/placement/handlers/placement/PlacementHandlers.java:196-228` (`FluidSubstitutionPlacementHandler`) : l'eau est gratuite, la lave demande un seau ; la case est faite par un solide ou une **source**.
2. **HC** : `HC/construction/blueprint/StructurePlan.java:248-272`, écart marqué `:254` : « any fluid counts, as the core cannot tell a source from a flowing fluid ». Fluide du plan : `Water_Source` (`HR/hycolony/id-map.json:84`), gratuit (aucun objet).
3. **Hytale** : la source et l'écoulement sont deux fluides distincts, `Water_Source` et `Water` (`zip:Server/Item/Block/Fluids/Water.json` : `Parent: Water_Source`, `MaxFluidLevel: 8`, `SupportedBy: Water_Source`) ; le cœur reçoit la clé `~fluid:<id>` (`HP/block/HytaleBlockStates.java:21`, `:94`). La raison de l'écart est périmée.
4. **Verdict** : à adapter.
5. **Proposition** : une case de fluide est faite par un solide ou un fluide `*_Source` (un `ItemCatalog.isFluidSource`). Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### A-21. « Bon sol » des placeholders
1. **MC** : `ST/util/BlockUtils.java:846-866` (`isGoodFullBlock` : forme de collision égale au cube ; `isGoodFloorBlock`).
2. **HC** : écart marqué `HP/adapter/HytaleItemCatalog.java:161-162` : « Hytale has no such shape on the server », remplacée par `DrawType` `Cube`/`CubeWithModel`, matériau `Solid`, groupe autre que `Leaves` (`:164-179`).
3. **Hytale** : le serveur a la forme : `BlockType.getHitboxType()`/`getHitboxTypeIndex()` (`BlockType.java:1629-1635`) vers `BlockBoundingBoxes`, dont `UNIT_BOX` `"Full"` est le cube (`HY/server/core/asset/type/blockhitbox/BlockBoundingBoxes.java:29`), et `isFullySupportive()` (`BlockType.java:1732`). La raison de l'écart est périmée.
4. **Verdict** : à adapter.
5. **Proposition** : bon sol = hitbox `Full`, matériau `Solid`, hors set `Leaves` (ou terre labourée), au plus près de Structurize. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### A-22. Bloc de remplissage
1. **MC** : défaut `Items.DIRT` (`BuildingModules.java:439`, `BuildingMiner.FILL_BLOCK`) ; le collage créatif prend le bloc du générateur du monde à la case (`BlockUtils.getSubstitutionBlockAtWorld`, cité par `HC/app/wand/WandPaste.java:80-83`).
2. **HC** : `blueprint.fillBlock` = `Soil_Dirt` (`HR/hycolony/id-map.json:39`, `HP/prefab/HytaleBlueprintSource.java:66, 83`) ; choix possibles = blocs de base Hytale posables, cassables et bons sols (`HP/prefab/FillBlocks.java:36-44`). Collage : bloc par défaut, écart marqué `WandPaste.java:80-83`.
3. **Hytale** : `Soil_Dirt` existe et se ramasse (relevé des plans : il se donne lui-même) ; le générateur ne s'interroge qu'en régénérant un chunk, de façon asynchrone (`HY/server/core/universe/world/worldgen/IWorldGen.java:19`, `generate(…)` → `CompletableFuture<GeneratedChunk>`), hors du fil du monde (CLAUDE.md § 4).
4. **Verdict** : conforme (l'écart du collage reste forcé).
5. **Proposition** : marquage `(Hytale world)` sur `WandPaste.plan`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### A-23. Ruban de chantier : forme et case libre
1. **MC** : `ConstructionTapeHelper.placeConstructionTape` (`MC/core/entity/ai/workers/util/ConstructionTapeHelper.java:77-144`) pose chaque ruban avec la forme que lui donnent les rubans déjà posés autour (`BlockConstructionTape.getPlacementState`, l. 106, 113, 122, 129, 142) ; il descend jusqu'au premier sol solide dont la case du dessus n'est pas solide et se remplace (`firstValidPosition`, l. 154-169 : `upState.canBeReplaced() || upState.isAir()`, l. 162).
2. **HC** : `HC/construction/tape/TapeLayout.java:14-22`, écart marqué l. 18-21 : chaque ruban reçoit d'un coup la forme que MC atteint une fois la bordure finie (droit le long d'un bord, coin à chaque angle), et une case est libre « as Hytale's own placement sees it (empty, or a block of material Empty: NON_SOLID) » ; test `free`, l. 88-93 (air, `NON_SOLID` ou fluide).
3. **Hytale** : ce que Minecraft appelle « remplaçable », Hytale le décide par le matériau : la pose ne casse et ne remplace qu'un bloc de matériau `Empty` (`HY/server/core/modules/interaction/BlockPlaceUtils.java:457-471`, `breakAndDropReplacedBlock` : retour immédiat si `getMaterial() != BlockMaterial.Empty`), et un bloc qui ne déclare pas de `Material` est `Empty` (`HY/server/core/asset/type/blocktype/config/BlockType.java:873`). Le cœur reçoit ce matériau en `BlockKind.NON_SOLID` (`HP/adapter/HytaleItemCatalog.java:292`). L'équivalent Hytale de `getPlacementState` est la règle de blocs connectés (`BlockType.getConnectedBlockRuleSet()`, `BlockType.java:1577-1579`), que l'asset du ruban déclare (`HR/Server/Item/Items/HyColony/HyColony_Construction_Tape.json:54-55`, `ConnectedBlockRuleSet` `CustomTemplate`) ; qu'elle joue pour un bloc posé par le serveur n'est pas vérifié (**[in-game]**).
4. **Verdict** : conforme : la case libre suit la règle de pose de Hytale, et la forme posée d'un coup est l'état final de MC.
5. **Proposition** : marquage `Deviation from MC (Hytale world): canBeReplaced → Hytale's material Empty (BlockPlaceUtils.breakAndDropReplacedBlock)`. Ni sauvegarde (le ruban n'est pas sauvé, il se repose) ni config.
- **Gravité** : cosmétique (marquage).

#### A-24. Bloc remplaçable sur une case à labourer
1. **MC** : avant de labourer, le fermier détruit la plante du dessus si elle est remplaçable (`MC/core/entity/ai/workers/production/agriculture/EntityAIWorkFarmer.java:390-394`, `aboveState.canBeReplaced()` puis `world.destroyBlock(position.above(), true)`, les gouttes tombent) ; sinon il la mine comme un ouvrier (`mineBlock(position.above())`, l. 603, dans `hoeIfAble`).
2. **HC** : `HC/farming/job/FieldPass.java:109-115` (`hoe`), écart marqué l. 113-114 : tout bloc sur la case est traité comme une plante remplaçable de MC, sans action ni XP, « the core has no "replaceable" flag ».
3. **Hytale** : le drapeau existe : le matériau `Empty` de A-23 (`BlockPlaceUtils.java:457-471`, `BlockType.java:873`), reçu par le cœur en `BlockKind.NON_SOLID` (`HP/adapter/HytaleItemCatalog.java:292`) et déjà lu par le ruban (`HC/construction/tape/TapeLayout.java:88-93`). Dans Hytale, les fleurs sont `Empty` (`zip:Server/Item/Items/Plant/Flowers/Plant_Flower_Common_White.json`, sans `Material`, parent des autres fleurs comme `Plant_Flower_Common_Red.json:5`), la torche aussi (`zip:Server/Item/Items/Furniture/Crude/Unique/Furniture_Crude_Torch.json:133`, `"Material": "Empty"`) : là où MC mine une fleur ou une torche, la pose de Hytale les remplace.
4. **Verdict** : à adapter : la raison de l'écart est périmée. Le bon partage est celui de Hytale : `NON_SOLID` (fleurs, herbes, torches) est remplaçable, un bloc `SOLID` posé sur la case ne l'est pas et se mine.
5. **Proposition** : `NON_SOLID` → branche « remplaçable » de MC (cassé, gouttes au sol, comme aujourd'hui) ; `SOLID` → branche `mineBlock` (action, XP, gouttes dans l'inventaire), avec `Deviation from MC (Hytale world): canBeReplaced → Hytale's material Empty`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (un bloc solide sur un champ est rare ; seuls l'XP et le rangement des gouttes changent).

### HyVanilla : un équivalent Hytale existe-t-il ?

Ce que HyVanilla livre (`HV/plugin/src/main/resources/Server/Item/Items/HyVanilla/`) : 20 `HyVanilla_Bed_<C>`, 20 `HyVanilla_Carpet_<C>`, 16 `HyVanilla_Flower_Pot_<C>` (états garnis : `hyvanilla/id-map.json`, `flowerPots`), plus le code des pots (`HV/core/.../FlowerPot.java`, `FlowerPotBlocks.java`, `HV/plugin/.../block/FlowerPotSystem.java`, `FlowerPotUse.java`). Les plans s'en servent par `tools/blueprint/hyvanilla.py`. MineColonies et Structurize n'ajoutent aucun de ces blocs : ce sont des blocs vanilla de Minecraft présents dans les schémas (`carpets-flower-pots.md` § 2). Le système ne touche qu'au lit (le citoyen y dort : `HP/adapter/HytaleItemCatalog.java:204-208`, `isBed` = `BlockType.getBeds() != null`, tout lit Hytale).

Selon CLAUDE.md § 6, HyVanilla n'ajoute une chose de Minecraft que s'il n'existe rien de comparable **et** que le système ne peut pas s'en passer. Un retrait n'a lieu qu'après le passage des plans à l'équivalent et avec une migration des colonies qui l'utilisent (spec § 6). Les trois blocs ont été des ajouts demandés par l'utilisateur (`FlowerPot.java:9`, `FlowerPotBlocks.java:11`, spec `2026-09-29-hyvanilla-beds-design.md`) : le choix lui revient.

#### A-V1. Lits 1×2
1. **MC** : un lit de couleur, 1×2, posé par le plan ; la maison compte ses lits (fait de Minecraft hors `sources/` ; HyColony : `isBed`).
2. **HV** : 20 lits `HyVanilla_Bed_<C>` (spec `2026-09-29-hyvanilla-beds-design.md`), 27 cases dans 12 plans MedievalOak.
3. **Hytale** : deux lits de 1×2 existent. `Furniture_Kweebec_Bed` : hitbox `Bed_Kweebec` (X 0,05-1,0, Z 0-1,8, haut 1,05 : `zip:Server/Item/Block/Hitboxes/Furniture/Bed/Bed_Kweebec.json`), `Beds` défini, recette 3 `Wood_All` + 4 `Ingredient_Fibre` au `Furniture_Bench`/`Furniture_Beds` (`zip:Server/Item/Items/Furniture/Kweebec/Furniture_Kweebec_Bed.json`). `Furniture_Crude_Bed` : paillasse (`Bed_Crude`, 0,8 × 1,95, haut 0,15), recette `Fieldcraft`/`Workbench`. Les autres lits font 2×2 à 2×3.
4. **Verdict** : à retirer de HyVanilla.
5. **Proposition** : convertir les lits MC en `Furniture_Kweebec_Bed` (la couleur est perdue), puis retirer les lits de HyVanilla. Sauvegarde : les lits déjà posés dans le monde restent des blocs HyVanilla ; une colonie qui en loge des citoyens a besoin d'une migration (lits des résidences remplacés ou oubliés), à concevoir avec la skill `add-migration`. Config : aucune.
- **Gravité** : cosmétique.

#### A-V2. Tapis
1. **MC** : tapis de laine 1/16, 16 couleurs (`carpets-flower-pots.md` § 2) ; aucun système de MC n'y touche.
2. **HV** : 20 `HyVanilla_Carpet_<C>`, 269 cases dans 14 plans.
3. **Hytale** : `Cloth_Roof_<C>_Flat` a la forme exacte (modèle `Blocks/Structures/Roofs/Cloth_Roof_Horizontal.blockymodel`, hitbox `Block_Flat`), recette 1 laine → 1 au `Builders`/`Roof` (`zip:Server/Item/Items/Cloth/Roof_Red/Cloth_Roof_Red_Flat.json`), 8 couleurs (`Cloth_Roof_*_Flat.json`), texture de toile de tente et aucun `Support` (il flotte). Les tapis de mousse `Plant_Moss_Rug_*` (7) existent aussi.
4. **Verdict** : à retirer de HyVanilla (le système s'en passe ; `Cloth_Roof_<C>_Flat` est comparable).
5. **Proposition** : convertir les tapis en `Cloth_Roof_<C>_Flat` de la couleur la plus proche, puis retirer les tapis. Sauvegarde : aucune donnée de colonie ne les nomme ; les blocs posés restent dans le monde. Config : aucune.
- **Gravité** : cosmétique (texture et couleurs différentes).

#### A-V3. Pots de fleurs
1. **MC** : pot garni, un bloc par plante ; Structurize demande le pot et la plante (`ST/util/BlockUtils.java:356-358`, `PlacementHandlers.java:668-705`) ; aucun système de MC n'y touche.
2. **HV** : 16 pots × 121 plantes, code d'interaction (`FlowerPotSystem`) ; 34 cases dans 18 plans.
3. **Hytale** : toujours aucun pot garni en pre.5 (aucun objet `*Flower_Pot*` ni `potted` dans `Server/Item/Items`). Le plus proche : les jardinières `Furniture_<Famille>_Planter` (tag `SubType: Planter`, la fleur se pose au-dessus, sur deux cases), dont `Furniture_Tavern_Planter` et `Furniture_Village_Planter` ont une recette (le second au `Farmingbench` palier 2) ; les pots décoratifs (`Furniture_Village_Pot`…) n'ont pas de recette.
4. **Verdict** : à retirer de HyVanilla (rien de comparable, mais le système s'en passe).
5. **Proposition** : convertir les pots en jardinière + fleur au-dessus quand la case du dessus est libre, sinon les retirer du plan ; puis retirer pots et code. Sauvegarde : aucune donnée de colonie ne les nomme. Config : aucune.
- **Gravité** : cosmétique.

### Bilan du groupe A

| Verdict                | Casse le jeu       | Incohérent                                | Cosmétique                                          | Total  |
|------------------------|--------------------|-------------------------------------------|-----------------------------------------------------|--------|
| Conforme               | 0                  | 0                                         | 7 (A-1, A-2, A-9, A-12, A-19, A-22, A-23)           | 7      |
| À adapter              | 2 (A-15, A-16)     | 6 (A-3, A-4, A-5, A-8, A-10, A-11)        | 7 (A-6, A-7, A-13, A-14, A-20, A-21, A-24)          | 15     |
| À retirer de HyVanilla | 0                  | 0                                         | 3 (A-V1, A-V2, A-V3)                                | 3      |
| **Total**              | **2**              | **6**                                     | **17**                                              | **25** |

A-17 est passée dans « Hors du monde » (système) ; A-18 est fusionnée dans B-15.

### Ce qui reste incertain

- Le blocage des ordres par les objets introuvables (A-15, A-16) est déduit des assets et du code, pas vu en jeu **[in-game]**.
- A-23 : que la règle de blocs connectés du ruban joue pour un bloc posé par le serveur **[in-game]**.
- A-16 : la génération du monde (`Server/World`, `Server/HytaleGenerator`) n'a pas été parcourue ; les objets « à vérifier » peuvent avoir une source naturelle. La recherche de producteurs couvre `Server/Item` (recettes d'objet et `Server/Item/Recipes`), `Server/Drops` et `Server/BarterShops`.
- Les sources décompilées n'indiquent pas leur version : elles sont supposées être celles de `gradle.properties` (0.7.0-pre.5).

## B. Artisanat, culture, nourriture

Audit du 2026-10-02 (spec `docs/superpowers/specs/2026-10-02-hycolony-monde-hytale-design.md` § 2 et § 5), groupe B : bancs et recettes, fermier et champs, cuisson, nourriture et ses effets.

Sources :
- **MC** = `sources/minecolonies/src/main/java/com/minecolonies/` (copie locale, `version/main`), abrégé `MC/` dans un chemin, ou cité par le seul nom de fichier ; Structurize = `sources/structurize/`.
- **HY** = sources décompilées `build/vineflower/hytale-server/com/hypixel/hytale/`.
- **zip** = `~/.gradle/caches/hytale-assets/pre-release-0.7.0-pre.5-Assets.zip`. `gradle.properties` épingle aujourd'hui `hytale_version = 0.7.0-pre.5` (CLAUDE.md dit encore pre.4) : c'est ce zip qui a été lu. Les recherches `sp3b-hytale-farming.md` (0.6.8) et `sp4b-hytale-food.md` (pre.4) ont été recoupées sur pre.5 pour chaque fait cité ici.
- **HC** = code HyColony (`core/src/main/java/dev/hycolony/core/`, `plugin/src/main/java/dev/hycolony/plugin/`, ressources `plugin/src/main/resources/`).

Lecture des verdicts : *conforme* = la règle suit déjà Hytale (la gravité dit alors ce qui reste, le plus souvent un marquage `Deviation from MC (Hytale world)` à reprendre, spec § 3) ; *à adapter* = la règle recopie le monde Minecraft ou s'écarte de Hytale sans raison ; *à retirer de HyVanilla* = aucun cas dans ce groupe (HyVanilla n'ajoute ni culture, ni banc, ni aliment).

Hors groupe B, renvoyés aux autres groupes : paliers des houes (`id-map.json:182`, domaine Outils), pluie qui arrête le fermier (`FarmerHut`, MC `canWorkingDuringRain=false`, domaine Météo), passage du jour de colonie (`Colony.java:128-139`, domaine Temps).

### Culture

#### B-1. Labourer : quels sols, quel sol labouré
1. **MC** : `EntityAIWorkFarmer.java:372-412` (`findHoeableSurface`) n'accepte qu'un bloc du tag `DIRT` (ou une terre labourée) que la houe change en `Blocks.FARMLAND` (`getToolModifiedState(…, HOE_TILL)`) ; `:625-633` (`createCorrectFarmlandForSeed`) pose la terre préférée de la graine.
2. **HC** : `plugin/.../farming/HytaleFarming.java:52-65` (`isTillable`, `till`) avec la liste `farming.tillable`/`tilled` de `hycolony/id-map.json:174-179` ; `core/.../farming/job/FieldScan.java:180-188`. Copie des sols de Hytale, sans marquage.
3. **Hytale** : `zip:Server/Item/Interactions/Weapons/Hoe/Attacks/Till/Hoe_Till.json`, `Changes` = les 16 mêmes sols → `Soil_Dirt_Tilled`, son `SFX_Hoe_T1_Till` ; toute culture tient sur tout `Type=Soil` (`sp3b-hytale-farming.md` § 3.1), il n'y a donc pas de terre propre à une graine.
4. **Verdict** : conforme.
5. **Proposition** : ajouter le marquage `Deviation from MC (Hytale world): MC's DIRT tag → FARMLAND → Hytale's Hoe_Till soils → Soil_Dirt_Tilled` sur `FarmingAccess.isTillable`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### B-2. Le sol labouré qui revient
1. **MC** : le cycle du champ `EMPTY → HOED → PLANTED` revient à labourer tout ce qui n'est plus de la terre labourée (`EntityAIWorkFarmer.java:372-412`) ; la terre de Minecraft qui sèche est une règle vanilla, absente de `sources/`.
2. **HC** : `Soil_Mud_Dry` est dans la liste labourable (`id-map.json:177`), donc la passe de labour le reprend ; l'engrais perdu est remis car `FieldPass.java:194-209` teste `isFertilized` à chaque cellule.
3. **Hytale** : `zip:Server/Item/Items/Soil/Dirt/Soil_Dirt_Tilled.json:49-54`, `SoilConfig.Lifetime` 103 680–129 600 s de jeu, `TargetBlock: Soil_Mud_Dry` ; `tickSoil` (`HY/builtin/adventure/farming/FarmingSystems.java:562`) cherche une culture au-dessus (`hasCropAbove`, l. 591) et ne fait vieillir le sol labouré que sans culture (`if (soilBlock.isPlanted() && !hasCrop)`, l. 605) ; détail dans `sp3b-hytale-farming.md` § 2.2.
4. **Verdict** : conforme.
5. **Proposition** : rien. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### B-3. Une culture mûre
1. **MC** : `CropBlock.isMaxAge` (`EntityAIWorkFarmer.java:797-826`, `findHarvestableSurface`).
2. **HC** : `HytaleFarming.java:80-91` → `FarmBlocks.java:49-53` (`BlockGathering.isHarvestable()` du type exact du bloc).
3. **Hytale** : seul l'état `StageFinal` déclare `Gathering.Harvest` : `zip:Server/Item/Items/Plant/Crop/Wheat/Plant_Crop_Wheat_Block.json:84-99` (`StageFinal`, `Gathering.Harvest.DropList: Drops_Plant_Crop_Wheat_StageFinal_Harvest`), là où `Stage1` à `Stage3` n'ont que `Gathering.Soft` (l. 29-32, 54, 76) ; `BlockGathering.isHarvestable()` (`HY/server/core/asset/type/blocktype/config/BlockGathering.java:108`) ; voir aussi `sp3b-hytale-farming.md` § 3.3.
4. **Verdict** : conforme.
5. **Proposition** : rien. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### B-4. La croissance (durée, conditions) et le rythme du fermier
1. **MC** : la croissance est celle de Minecraft (ticks aléatoires, aucune constante dans MineColonies) ; le fermier ne revient sur un champ qu'au jour de colonie suivant (`BuildingExtensionsModule.java:145-160`, `:307`).
2. **HC** : aucune durée de croissance dans le code ; `core/.../farming/hut/FieldChoice.java:24-45` reprend le « une passe par jour de colonie », jour compté par `Colony.java:128-139` sur l'horloge du monde.
3. **Hytale** : `zip:Server/Item/Items/Plant/Crop/Wheat/Plant_Crop_Wheat_Block.json` : stades 10 500 + 3 × 25 300 = 86 400 s de jeu, `ActiveGrowthModifiers` `Fertilizer`, `Water`, `LightLevel` ; journée par défaut 1 728 s de jour + 1 151 s de nuit (`HY/server/core/asset/type/gameplay/WorldConfig.java:102-103`), soit 86 400 s de jeu (`WorldTimeResource.getSecondsPerTick`, `WorldTimeResource.java:64-69`). Une culture mûrit donc en une journée Hytale à ×1, une demi-journée avec l'engrais.
4. **Verdict** : conforme (la croissance est entièrement celle de Hytale).
5. **Proposition** : rien. À garder en tête pour le domaine Temps : le rythme « une passe par jour » colle au cycle d'une culture Hytale tant que le jour de colonie suit la journée Hytale. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### B-5. Le compost et la poudre d'os deviennent l'engrais de Hytale
1. **MC** : `EntityAIWorkFarmer.java:224-239` demande 64 compost ou poudre d'os (`StackList`, réglage `FERTILIZE`) ; `:297-303` (`isCompost`) ; `:797-826` les dépense sur une culture pas mûre (`growCrops`, saut de stades).
2. **HC** : `core/.../farming/job/FarmWork.java:188-210` demande un `Tool_Fertilizer` ; `FieldPass.java:194-209` fertilise le sol au labour et au semis ; `FieldScan.java:199-205` ne fait rien mûrir ; `FarmerSettingsModule.java:57`. Écart marqué `Deviation from MC` (sans « Hytale world »).
3. **Hytale** : `zip:Server/Item/Items/Tool/Feedbag/Tool_Fertilizer.json` (5 utilisations) ; `HY/builtin/adventure/farming/interactions/FertilizeSoilInteraction.java:127` pose `TilledSoilBlock.setFertilized(true)`, que rien ne remet à faux ; modificateur ×2. Aucun objet de survie ne fait sauter un stade (seul l'outil de développement `Tool_Growth_Potion`, `sp3b-hytale-farming.md` § 2.4).
4. **Verdict** : conforme.
5. **Proposition** : reformuler les trois commentaires au format `Deviation from MC (Hytale world): compost/bone meal (growCrops) → Tool_Fertilizer on the soil (FertilizeSoilInteraction)`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### B-6. Les graines : une culture mûre ne rend pas sa graine
1. **MC** : la récolte rend la graine, et le module de fabrication du fermier fait les produits du tag `farmer` (`DefaultItemTagsProvider.java:319-333`) et deux recettes intégrées, citrouille sculptée et boue (`DefaultFarmerCraftingProvider.java:41-47`) ; `canGoPlanting` prend ou demande 64 graines (`EntityAIWorkFarmer.java:337` et suivantes).
2. **HC** : `plugin/src/main/resources/hycolony/crafting.json:3-17` : le fermier apprend au `Farmingbench` (catégories `Seeds`, `Essence`) et reçoit les recettes de graines selon le niveau de la hutte (L1 blé, laitue ; L2 carotte, maïs ; L3 chou-fleur, navet ; L4 aubergine, citrouille ; L5 tomate, piment) ; `FarmWork.java:166-186` (64 graines).
3. **Hytale** : la récolte ne rend pas la graine ordinaire : `zip:Server/Drops/Crop/Wheat/Drops_Plant_Crop_Wheat_StageFinal_Harvest.json:2-31` donne `Plant_Crop_Wheat_Item` (1-2), `Ingredient_Life_Essence` (2-4) et, avec `Weight: 0.5`, une `Plant_Seeds_Wheat_Eternal` (l. 21-28 ; le sens exact de ce poids dans un conteneur `Multiple` n'est pas vérifié), jamais `Plant_Seeds_Wheat` (voir aussi `sp3b-hytale-farming.md` § 1.2). Recettes `Plant_Seeds_<C>` en essence, `RequiredTierLevel` 2 pour la carotte (`zip:Server/Item/Items/Plant/Crop/Carrot/Plant_Seeds_Carrot.json:48`), 3 (chou-fleur), 4 (aubergine), 5 (tomate, piment), 6 (coton), 7 (pomme de terre), lues dans le zip pre.5. Les graines éternelles ont en plus un niveau de souvenirs requis (B-26).
4. **Verdict** : conforme (paliers des recettes = niveaux de hutte).
5. **Proposition** : rien. Ni sauvegarde ni config. Remarque : coton, riz, pomme de terre et oignon (palier 6-7) ne sont jamais fabriqués par le fermier (niveau max 5) ; un champ de ces graines dépend de l'entrepôt ou d'un joueur.
- **Gravité** : cosmétique.

#### B-7. Récolter une culture éternelle
1. **MC** : `harvestIfAble` casse la culture (`EntityAIWorkFarmer.java:661-680`), le semis suivant la replace (`plantCrop`, `:747-775`).
2. **HC** : `plugin/.../farming/HytaleFarming.java:100-123` casse le bloc puis replace la culture éternelle **à son premier stade** (`default`). Écart marqué `Deviation from vanilla Hytale`.
3. **Hytale** : `HY/builtin/adventure/farming/FarmingUtil.java:296-345` (`harvest0`) : pour un `StageSetAfterHarvest`, la plante reste, `FarmingBlock` passe au jeu `Harvested` (progression 0, génération + 1, `scheduleTick`) et applique son premier stade, qui reprend à `Stage1` (`sp3b-hytale-farming.md` § 3.4). HyColony refait donc le stade `default` (10 500 s de jeu, environ 6 minutes réelles à ×1) que Hytale saute.
4. **Verdict** : à adapter.
5. **Proposition** : dans `FarmBlocks`, reproduire la branche `harvest0` (`FarmingUtil.harvest` lui-même jette les objets au sol quand `ref == null`, `FarmingUtil.java:361`, ce qui ne convient pas au fermier). Ni sauvegarde ni config. **[in-game]** : la repousse d'une culture éternelle récoltée par le fermier.
- **Gravité** : cosmétique.

#### B-8. Les limites d'un champ
1. **MC** : `MC/core/colony/buildingextensions/FarmField.java:221-223` (`isNoPartOfField` : une case vide, ou sous un délimiteur, n'est pas du champ), `:232-235` (`isValidDelimiter` : tout `FenceBlock`, `FenceGateBlock`, `WallBlock`).
2. **HC** : `plugin/.../farming/HytaleFarming.java:125-143` (`isFieldBarrier`, `barrierKey`) avec la liste `fieldBarriers` de `plugin/src/main/resources/hycolony/id-map.json:183-205` : 77 ids, dont 3 de HyDomum (`HyDomum_Fence`, `HyDomum_FenceGate`, `HyDomum_Wall`) et 74 de Hytale.
3. **Hytale** : liste revérifiée sur le zip pre.5. Les 74 ids Hytale de la liste existent tous. Cinq clôtures et murs du zip manquent : `Metal_Goblin_Iron_Fence` (`Parent: Metal_Iron_Fence`, `zip:Server/Item/Items/Metal/Goblin_Iron/Metal_Goblin_Iron_Fence.json:2`), `Metal_Orbis_Copper_Fence` (`HitboxType: Fence_Thin`, `zip:Server/Item/Items/Metal/Orbis_Copper/Metal_Orbis_Copper_Fence.json:29`), `Soil_Clay_Brick_Wall` (`HitboxType: Fence`, `zip:Server/Item/Items/Soil/Clay_Brick/Soil_Clay_Brick_Wall.json:43`), `Soil_Clay_Ocean_Brick_Wall` (`Fence_Thin`, `zip:Server/Item/Items/Soil/Clay_Ocean_Brick/Soil_Clay_Ocean_Brick_Wall.json:44`) et `Soil_Snow_Brick_Wall` (`Fence`, `zip:Server/Item/Items/Soil/Snow/Soil_Snow_Brick_Wall.json:44`). Les autres ids du zip qui contiennent `Fence`, `_Wall` ou `Gate` ne sont pas des clôtures (`Wood_Torch_Wall`, `Plant_Vine_Wall`, `Deco_Bone_Skulls_Wall`, `Instance_Gateway`, les plateformes `Prototype_Wood_*_Fence_Platform`, `VFX_Dungeon_Goblin_Electric_Fence`, `Spawn_Temple_Goblin_Fence`…). Hytale n'a pas de classe de bloc « clôture », mais il a une marque lisible côté serveur : le `HitboxType` (`BlockType.getHitboxType()`, `HY/server/core/asset/type/blocktype/config/BlockType.java:1629`). Sur les 79 blocs Hytale relevés (74 listés et 5 manquants), 78 ont un premier `HitboxType` de la famille `Fence` (`Fence`, `Fence_Thin`, `Fence_Thick`, `Fence_Corner*`, `Fence_Gate*`) et un n'en déclare pas lui-même (hérité de son `Parent`).
4. **Verdict** : à adapter : la règle suit déjà Hytale (ses clôtures et murs), mais la liste est incomplète en pre.5.
5. **Proposition** : ajouter les cinq ids manquants à `fieldBarriers` ; ou, plus près de `instanceof FenceBlock/WallBlock`, tenir pour délimiteur tout bloc dont la hitbox est de la famille `Fence`, la liste ne gardant que HyDomum (à vérifier sur les états ouverts des portillons, `Fence_Gate_Open_In`). Marquage `Deviation from MC (Hytale world): FenceBlock/FenceGateBlock/WallBlock → Hytale's fences and walls (id-map fieldBarriers)`. Ni sauvegarde ni config (l'id-map est une ressource du plugin).
- **Gravité** : cosmétique (cinq blocs rares ; la case sous l'un d'eux compte comme du champ).

#### B-9. Ce que le fermier ne mange pas
1. **MC** : `BuildingFarmer.java:144-161` : ni la graine d'un champ de la hutte, ni `Items.WHEAT`.
2. **HC** : `core/.../farming/hut/FarmerFieldsModule.java:140-147` : la graine seulement ; le blé n'a pas de règle « car le blé de Hytale n'est pas un aliment ».
3. **Hytale** : `Plant_Crop_Wheat_Item` remplace le `Secondary` de son modèle par une simple `Condition` sans chaîne de consommation, n'a aucun ResourceType et est rangé dans `Items.Ingredients` (`zip:Server/Item/Items/Plant/Crop/Wheat/Plant_Crop_Wheat_Item.json:9-37` ; `sp4b-hytale-food.md` § 1.4) ; il n'est pas dans la table des aliments.
4. **Verdict** : conforme.
5. **Proposition** : marquer `Deviation from MC (Hytale world)` sur cette Javadoc. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### B-30. Citrouilles sans tige
1. **MC** : un champ de melon ou de citrouille laisse une case libre entre deux semis, car la tige pousse son fruit à côté (`MC/core/entity/ai/workers/production/agriculture/EntityAIWorkFarmer.java:763`, `seed == Items.MELON_SEEDS || seed == Items.PUMPKIN_SEEDS`, case précédente non vide) ; le fruit se récolte comme un bloc (`:792`, `Blocks.PUMPKIN`, `Blocks.MELON`).
2. **HC** : `core/.../farming/job/FieldPass.java:143-148` (`plant`), écart marqué l. 144-145 : « no melon/pumpkin gap (Hytale's pumpkin has no stem) ».
3. **Hytale** : la citrouille pousse sur place, comme le blé : `zip:Server/Item/Items/Plant/Crop/Pumpkin/Plant_Crop_Pumpkin_Block.json` a des états `Stage1` à `Stage3` puis `StageFinal` (l. 16, 37, 58, 79), qui seul a `Gathering.Harvest` (l. 92-93), et une croissance par `Stages` (l. 107-147). Aucun bloc de tige dans `zip:Server/Item/Items/Plant/Crop/Pumpkin/` (`Plant_Crop_Pumpkin_Block`, `_Block_Eternal`, `_Item`, `Plant_Seeds_Pumpkin`, `_Eternal`).
4. **Verdict** : conforme : sans tige, l'espacement n'a plus d'objet.
5. **Proposition** : marquage `Deviation from MC (Hytale world): melon/pumpkin gap → none, Hytale's pumpkin grows in place (Plant_Crop_Pumpkin_Block stages)`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### B-31. La hache du fermier
1. **MC** : la hutte du fermier garde une houe et une hache (`MC/core/colony/buildings/workerbuildings/BuildingFarmer.java:108-109`, `keepX`). L'IA du fermier ne nomme jamais la hache (aucun `ModEquipmentTypes.axe` dans `MC/core/entity/ai/workers/production/agriculture/EntityAIWorkFarmer.java`), mais elle récolte par `mineBlock(position.above())` (`EntityAIWorkFarmer.java:666`, `harvestIfAble`), qui prend l'outil efficace du bloc (`holdEfficientTool`, `MC/core/entity/ai/workers/AbstractEntityAIInteract.java:293` ; `WorkerUtil.getBestToolForBlock`, voir A-1). Une citrouille ou un melon de Minecraft se récolte ainsi à la hache, si leur étiquette `mineable/axe` de Minecraft vanilla est bien celle-là (hors `sources/`, **non vérifié**).
2. **HC** : `HC/farming/hut/FarmerHut.java:40-41` : la hutte ne garde que la houe ; écart marqué « no axe, which MC's farmer keeps but never uses ». La récolte casse le bloc mûr sans outil (`HP/farming/HytaleFarming.java:100-123`, `blocks.breakBlock(pos)` l. 114).
3. **Hytale** : une culture mûre se récolte sans outil : `zip:Server/Item/Items/Plant/Crop/Pumpkin/Plant_Crop_Pumpkin_Block.json:91-98` (`StageFinal` : `Gathering.Harvest` et `Gathering.Soft`, sans `Breaking` ni `GatherType`), comme le blé (B-3).
4. **Verdict** : conforme : la hache de MC ne sert qu'à miner les fruits de Minecraft ; dans Hytale, rien de ce que fait le fermier ne la demande. Ce n'est donc pas un choix du système, mais la raison écrite (« never uses ») est inexacte.
5. **Proposition** : réécrire le commentaire en `Deviation from MC (Hytale world): no axe; MC's farmer mines pumpkins and melons with it (mineBlock → holdEfficientTool), Hytale crops are harvested without a tool (Gathering.Harvest/Soft)`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

### Artisanat

#### B-10. Les bancs de Hytale à la place des « intermediate blocks »
1. **MC** : `AbstractCraftingBuildingModule.java:1032-1035` (une recette de grille a `getIntermediate() == Blocks.AIR`), `:1069-1072` (`Blocks.FURNACE`) ; filtre par tags `crafterProduct`.
2. **HC** : `core/.../crafting/module/RecipeCompatibility.java:17-24`, `crafting/recipe/BenchRequirement.java:22-27`, `kernel/item/Workstation.java:5-9`, `crafting/recipe/CraftingRules.java:10-16` : banc, catégories et palier de la recette Hytale ; `Fieldcraft` = la main (MC `AIR`). Écarts marqués `Deviation from MC`.
3. **Hytale** : `HY/server/core/asset/type/item/config/CraftingRecipe.java` (`getBenchRequirement()`, palier requis comparé au palier du banc, `sp3b-hytale-farming.md` § 5).
4. **Verdict** : conforme.
5. **Proposition** : passer ces marquages au format `(Hytale world)`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### B-11. Une recette que plusieurs bancs font
1. **MC** : une recette n'a qu'un bloc intermédiaire (`AbstractCraftingBuildingModule.java:1032-1035`).
2. **HC** : `plugin/.../crafting/RecipeConversion.java:30-31` ne garde que le **premier** banc `Crafting`. Écart marqué `Deviation from MC`.
3. **Hytale** : dans le zip pre.5, 10 recettes d'objet lisibles listent deux bancs `Crafting` : 8 `Fieldcraft` puis `Workbench` (`Bench_Builders`, `Bench_Campfire`, `Furniture_Crude_Bed`, `Furniture_Crude_Chest_Small`, `Furniture_Crude_Torch`, `Tool_Hatchet_Crude`, `Tool_Pickaxe_Crude`, `Weapon_Sword_Crude`), et 2 `Workbench` puis `Weapon_Bench` (`Weapon_Arrow_Crude`, `Weapon_Shortbow_Crude`). Les 8 premières se font déjà à la main ; les 2 dernières sont refusées à une hutte qui n'a qu'un `Weapon_Bench`.
4. **Verdict** : à adapter.
5. **Proposition** : `BenchRequirement` porte la liste des bancs, et la compatibilité en accepte un. **Sauvegarde** : le registre des recettes est sauvegardé avec la colonie (`RecipeRegistry`), donc le format d'une recette change : migration (skill `add-migration`). Pas de config. Peut attendre un métier qui travaille au `Weapon_Bench`.
- **Gravité** : cosmétique.

#### B-12. Les ingrédients qu'une amélioration de recette peut réduire
1. **MC** : `DefaultItemTagsProvider.java:537-575` : tag `crafterIngredient[reduceable]` (livre, papier, sucre, poissons, viandes crues, pomme de terre, bâton, cuir, verre, blé, ficelle, pépites, lingots, pierre, pavé, gravier, sable, poudres, gemmes, laine, bûches, planches, briques de pierre) et ses exclusions.
2. **HC** : `crafting.json:21-23`, listes `ingredients` et `excludedProducts` **vides** ; `core/.../crafting/module/RecipeImprovement.java:108-119` ne réduit donc jamais rien. Le système d'amélioration de MC est porté mais ne se déclenche pas.
3. **Hytale** : les équivalents existent : `Ingredient_Stick`, `Ingredient_Leather_Light` (et autres cuirs), `Ingredient_Bar_Iron` (et autres lingots), `Ingredient_Fibre` (`zip:Server/Item/Items/Ingredient/…`), les viandes du ResourceType `Meats`, `Food_Fish_Raw`, `Plant_Crop_Wheat_Item`, `Plant_Crop_Potato_Item`, les pierres et troncs (ResourceTypes `Rock`, `Wood_Trunk`, utilisés par les recettes des huttes).
4. **Verdict** : à adapter.
5. **Proposition** : remplir `reduceable` avec la liste Hytale correspondante, objet par objet (`RecipeImprovement` exige que chaque objet d'un ResourceType soit listé). Aucune sauvegarde touchée (les recettes améliorées naissent en jeu). `crafting.json` est une ressource du plugin, pas `config.json`.
- **Gravité** : incohérent (une mécanique de MC portée ne sert jamais).

#### B-13. Les recettes des objets de MC (huttes, outil de construction, champ)
1. **MC** : `DefaultRecipeProvider.java:284-297` : une hutte = 7 planches + l'outil de construction + un objet propre (`:74` porte en bois pour le constructeur, `:76` torche pour la résidence, `:80` pomme pour la cuisine, `:82` bottes en cuir pour le coursier, `:86` houe en bois pour le fermier, `:110` outil de construction pour l'hôtel de ville, `:111` coffre pour l'entrepôt) ; l'outil revient (`sources/structurize/.../items/ItemBuildTool.java:53-70`). Outil : `sources/structurize/src/main/resources/data/structurize/recipes/sceptergold.json` (1 pierre + 2 bâtons). Champ (épouvantail) : `DefaultRecipeProvider.java:169-177` (botte de foin ou citrouille, cuir, bâtons).
2. **HC** : `plugin/src/main/resources/Server/Item/Items/HyColony/*.json` : toutes à l'établi (`Workbench`, `Workbench_Crafting`) ; huttes = 10 `Wood_Trunk` + 2 `Rock` + un objet (constructeur et résidence : aucun ; cuisine : `Bench_Campfire` ; coursier : 2 `Ingredient_Leather_Light` ; fermier : `Tool_Hoe_Crude` ; entrepôt : `Furniture_Crude_Chest_Small` ; hôtel de ville : 6 troncs + 3 pierres), sans outil de construction ; outil = 3 pavés + 6 bâtons ; champ = la recette de `Deco_Scarecrow` au `Farmingbench`.
3. **Hytale** : bancs `Workbench`, `Farmingbench` ; `zip:Server/Item/Items/Deco/Deco_Scarecrow.json` (même recette que `HyColony_Field.json`) ; équivalents des objets propres de MC présents dans le zip pre.5 : `Furniture_Crude_Door`, `Furniture_Crude_Torch`, `Plant_Fruit_Apple`, `Furniture_Crude_Chest_Small`, `Tool_Hoe_Crude` ; pas de bottes (armures de cuir `Armor_Leather_*_Legs`, pas de pied) ; ResourceType `Wood_All` (icône `Wood_Planks.png`), sans fichier `Wood_Planks.json`.
4. **Verdict** : conforme pour les bancs et les matériaux (Hytale) ; à adapter pour l'objet propre de chaque hutte, qui n'est pas l'équivalent de celui de MC.
5. **Proposition** : garder les bancs ; reprendre l'objet propre de MC par son équivalent Hytale vérifié (porte, torche, pomme, coffre, houe ; pour le coursier, un équivalent à choisir) et, si l'utilisateur le veut, l'outil de construction rendu en sortie comme le font les lunettes. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### B-14. La recette du presse-papiers mange l'outil de construction

Déplacée dans « Hors du monde : système relevé en passant » (fin du document) : rendre l'outil après la recette est une
règle du système de MC (`ItemBuildTool.getCraftingRemainingItem`), pas du monde.

#### B-15. Les paliers de banc dans le coût d'un chantier (fusion de A-18)
1. **MC** : un bloc du plan coûte son objet (`sources/structurize/src/main/java/com/ldtteam/structurize/util/BlockUtils.java:505-518`, voir A-15) et les bancs de MC n'ont pas de palier ; le bâtisseur demande l'objet exact (`MC/core/entity/ai/workers/AbstractEntityAIStructure.java:750-857`, `hasListOfResInInvOrRequest`). Quand plusieurs objets conviennent, MineColonies demande une `StackList` (exemple : compost ou poudre d'os du fermier, `MC/core/entity/ai/workers/production/agriculture/EntityAIWorkFarmer.java:234` ; porté pour l'artisanat, `core/.../crafting/request/IngredientRequests.java:11`). Écarts de palier déjà décrits dans la spec SP3b-1 (écarts 2 et 3).
2. **HC** : `core/.../construction/resources/EntryCost.java:32-47` ajoute au banc ses améliorations du palier 1 au palier du plan (l. 41 ; écart marqué l. 30) ; `plugin/.../crafting/BenchIndex.java:83-90` (`upgradeCost`) remplace un matériau donné par ResourceType par **le premier objet** de ce type (`itemsOfResourceType`), écart marqué : « one precise trunk where Hytale takes any trunk of the family (the Farmingbench upgrades) ». Mise à jour 2026-10-03 : ce premier objet était `Plant_Fern_Jungle_Trunk` pour `Wood_Hardwood_Trunk` (palier 3 → 4), introuvable en survie (`plugin-b-api.md` § 51) ; `ResourceTypeIndex.items` met désormais en tête les objets qui ont une source.
3. **Hytale** : `zip:Server/Item/Items/Bench/Bench_Farming.json:63-127` (`TierLevels`) : l'amélioration du palier 1 demande, dans `UpgradeRequirement.Material` (l. 66-67), 50 `Ingredient_Life_Essence`, 5 `Plant_Crop_Wheat_Item`, 5 `Plant_Crop_Lettuce_Item` et 5 `ResourceTypeId: Wood_Softwood_Trunk` (l. 81-82) ; celle du palier 2, 10 `ResourceTypeId: Wood_Lightwood_Trunk` (l. 106-107), à côté d'objets précis. La clé `Material` est lue par `BenchUpgradeRequirement` (`HY/server/core/asset/type/blocktype/config/bench/BenchUpgradeRequirement.java:15`, `getInput()` l. 40) en `MaterialQuantity`, qui accepte un `ResourceTypeId` (`HY/server/core/inventory/MaterialQuantity.java:34`) ; le joueur paie l'amélioration par `canRemoveMaterials` sur ces matériaux (`HY/builtin/crafting/component/CraftingManager.java:763-781`), comme une recette. N'importe quel tronc du type convient donc au joueur.
4. **Verdict** : à adapter.
5. **Proposition** : faire porter au coût un ingrédient par ResourceType, demandé comme `StackList` des objets du type (ce que fait déjà `crafting/request/IngredientRequests` pour l'artisanat). **Sauvegarde** : une `StackList` est un type de requête déjà sérialisé (`RequestableJson`) ; les requêtes ouvertes du constructeur pour l'ancien objet précis sont sauvegardées, et il faut décider à la conception s'il faut une migration ou si elles se recréent (les besoins sont recalculés à chaque reprise, `BuildingResourcesModule.java:24-28`). Pas de config. Recoupe le domaine « Ressources du chantier ».
- **Gravité** : incohérent (le constructeur peut attendre un tronc précis alors que le joueur en a d'autres de la même famille).

#### B-26. Recettes réservées : connaissance, souvenirs du monde, augments du banc
1. **MC** : une recette s'apprend si la hutte peut la faire (`isRecipeCompatible`, `MC/core/colony/buildings/modules/AbstractCraftingBuildingModule.java:1032-1035`), parmi les recettes de Minecraft que donne la grille (`MC/api/inventory/container/ContainerCrafting.java:211`) ; hors recherche (non portée), rien ne la réserve à un joueur ou à un état du monde.
2. **HC** : `core/.../crafting/module/RecipeCompatibility.java:61-71` (`knownBy`), écart marqué l. 62-63 : une recette `KnowledgeRequired` ne s'enseigne que par un joueur qui la connaît (`plugin/.../crafting/RecipeConversion.java:58` lit `isKnowledgeRequired()`, `plugin/.../crafting/KnownRecipes.java:11-26` lit `PlayerConfigData.getKnownRecipes`). `RequiredMemoriesLevel` et les augments ne sont lus nulle part : `grep -i memories` sur `core/src/main/java` et `plugin/src/main/java` ne trouve rien, `grep -i augment` seulement la Javadoc de `plugin/.../block/BenchTiers.java:19`.
3. **Hytale** : `CraftingManager.isValidBenchForRecipe` (`HY/builtin/crafting/component/CraftingManager.java:453-497`) refuse une recette (a) `KnowledgeRequired` que le joueur ne connaît pas (l. 461), (b) dont le `RequiredMemoriesLevel` dépasse le niveau de souvenirs du monde (l. 463-466, `MemoriesPlugin.getMemoriesLevel`, `HY/builtin/adventure/memories/MemoriesPlugin.java:217`), (c) si le banc n'a pas les `RequiredAugmentTags` de l'exigence (l. 478, `benchHasRequiredAugmentTags` l. 907). Dans `zip:Server/Item/` (pre.5) : 19 fichiers ont `KnowledgeRequired: true` (armures, tartes, salade, par ex. `Items/Food/Food_Pie_Apple.json:87`) ; 39 ont un `RequiredMemoriesLevel`, dont 15 au `Farmingbench`, parmi lesquels les graines éternelles (`Items/Plant/Crop/Wheat/Plant_Seeds_Wheat_Eternal.json:75-85` : `Farmingbench` palier 2, catégorie `Seeds`, `RequiredMemoriesLevel: 2`) ; 50 ont des `RequiredAugmentTags`, aucun au `Farmingbench`.
4. **Verdict** : à adapter : `KnowledgeRequired` est déjà suivi (conforme), mais pas le niveau de souvenirs. Le fermier apprend toute recette `Farmingbench` des catégories `Seeds` et `Essence` (`plugin/src/main/resources/hycolony/crafting.json:3-4`) : il peut donc apprendre et faire une graine éternelle qu'aucun joueur de ce monde ne peut encore fabriquer **[in-game]**. Les augments ne touchent aucun métier porté aujourd'hui.
5. **Proposition** : `RecipeConversion` garde `RequiredMemoriesLevel` et les augments de chaque exigence ; une recette ne s'apprend et ne se fait que si le niveau de souvenirs du monde l'atteint (un port qui lit `MemoriesPlugin.getMemoriesLevel(world.getGameplayConfig())`) et si un banc de la hutte a les augments. Marquage `Deviation from MC (Hytale world): MC recipes need nothing of the world → Hytale's RequiredMemoriesLevel and bench augments (CraftingManager.isValidBenchForRecipe)`. **Sauvegarde** : le registre des recettes est sauvé avec la colonie (B-11) : un champ de plus dans une recette passe par la skill `add-migration`, sauf à le relire des assets au chargement ; une recette apprise puis refusée devient non valide, ce que `RecipeChoice.firstRecipe` saute déjà (`core/.../crafting/module/RecipeChoice.java:47-50`, `RecipeCompatibility.stillValid` l. 79). Config : aucune.
- **Gravité** : incohérent (le fermier fait ce que le monde refuse encore au joueur).

#### B-27. Ingrédients donnés par type de ressource ou par étiquette
1. **MC** : une recette de MineColonies garde la liste exacte de ses objets (`input`, `List<ItemStorage>`, `MC/api/crafting/RecipeStorage.java:56`), comparés par `ItemStorage.equals` (`MC/api/crafting/ItemStorage.java:255`) ; la grille fixe l'objet de chaque case au moment où la recette est enseignée (`MC/api/inventory/container/ContainerCrafting.java:117-121`, `:211`).
2. **HC** : six écarts marqués `Deviation from MC:`, sans `(Hytale world)`, tous issus de la même règle :
   - `core/.../crafting/recipe/Ingredient.java:7-9` : un ingrédient peut être « any item of a resource type or tag » ;
   - `core/.../crafting/recipe/RecipeMatching.java:8-9` : il accepte tout objet que le catalogue range sous ce type ou cette étiquette ;
   - `core/.../crafting/request/CraftingCycles.java:52-54` : une `StackList` n'est jamais « la même chose » qu'une `StackRequest`, d'où une boucle bornée seulement par `MAX_CRAFTING_CYCLE_DEPTH` ;
   - `core/.../crafting/request/IngredientRequests.java:10-13` : un tel ingrédient est demandé comme `StackList` de ses objets ;
   - `core/.../crafting/module/RecipesView.java:49-51` : la fenêtre le montre par son premier objet ;
   - `core/.../crafting/module/RecipeImprovement.java:108-111` : il n'est réductible que si tous ses objets le sont (B-12).
   À côté, `plugin/.../crafting/RecipeConversion.java:27-31` laisse de côté l'unique recette à étiquette, le cœur ne sachant pas nommer une étiquette à partir de son index.
3. **Hytale** : un ingrédient de recette est un `MaterialQuantity` qui nomme un `ItemId`, un `ResourceTypeId` ou un `ItemTag` (`HY/server/core/inventory/MaterialQuantity.java:27`, `:34`, `:38`), lu en liste par `CraftingRecipe` (`Input`, `HY/server/core/asset/type/item/config/CraftingRecipe.java:38`) et payé par n'importe quel objet qui y répond (`CraftingManager.getInputMaterials`, `HY/builtin/crafting/component/CraftingManager.java:654-663`). Exemples : les huttes de HyColony demandent des `Wood_Trunk` et des `Rock` (B-13), le lit kweebec 3 `Wood_All` (A-V1).
4. **Verdict** : conforme : la grille qui fixait un objet précis est un trait du monde Minecraft ; les recettes de Hytale nomment un type, et HyColony les suit.
5. **Proposition** : passer les six commentaires au format `Deviation from MC (Hytale world): MC's grid fixes each item → Hytale recipes ask a resource type or tag (MaterialQuantity)`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### B-28. Apprendre une recette : la liste des recettes du banc au lieu de la grille
1. **MC** : le joueur enseigne une recette en la posant dans une grille 2 × 2 ou 3 × 3 (`MC/api/inventory/container/ContainerCrafting.java:117-121`), que le `RecipeManager` de Minecraft reconnaît (l. 211, 424) ; l'ajout passe par `AddRemoveRecipeMessage` (`MC/core/network/messages/server/colony/building/worker/AddRemoveRecipeMessage.java:35`).
2. **HC** : `core/.../crafting/module/CraftingModule.java:28-29` et `core/.../crafting/module/RecipesView.java:14-15`, écarts marqués : la recette se choisit dans l'onglet des recettes, parmi celles du jeu que la hutte peut tenir.
3. **Hytale** : pas de grille d'artisanat : une recette nomme ses bancs et leurs catégories (`BenchRequirement`, `HY/server/core/asset/type/item/config/CraftingRecipe.java:65`), et le joueur la choisit au banc, qui vérifie son type, son id et son palier (`HY/builtin/crafting/component/CraftingManager.java:468-484`) ; sans bloc, c'est `Fieldcraft`, l'artisanat de poche (l. 468-469).
4. **Verdict** : conforme : choisir une recette dans une liste est la façon de Hytale.
5. **Proposition** : marquage `Deviation from MC (Hytale world): teaching by crafting grid → choosing among the bench's recipes (CraftingRecipe.BenchRequirement)` sur les deux Javadocs. Ni sauvegarde ni config.
- **Gravité** : cosmétique (marquage).

#### B-29. L'artisan au banc, une seule main
1. **MC** : `AbstractEntityAICrafting` mène l'artisan à son bâtiment (`walkToBuilding`, `MC/core/entity/ai/workers/crafting/AbstractEntityAICrafting.java:279`), lui met l'outil ou un ingrédient en main principale et le produit en main secondaire (l. 538-546), puis frappe le bloc de la hutte (`hitBlockWithToolInHand(building.getPosition())`, l. 548).
2. **HC** : `core/.../crafting/job/CrafterHands.java:18-24`, écart marqué l. 21-23 : l'artisan travaille au banc de la recette (le premier que la hutte a enregistré pour elle), ou au bloc de la hutte pour une recette `Fieldcraft`, et ne tient qu'un objet, « the body having one hand for it ».
3. **Hytale** : une recette se fait au banc qu'elle nomme (`HY/builtin/crafting/component/CraftingManager.java:468-484`), `Fieldcraft` sans bloc (l. 468-469). Un rôle de PNJ peut avoir une main secondaire : `OffHandSlots`, de 0 à 4, défaut 0 (`HY/server/npc/role/builders/BuilderRole.java:338-346`), garnie par `OffHandItems` (l. 362) ; notre rôle n'en déclare pas (`plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json`, aucune clé `OffHand*`). Qu'un objet en main secondaire s'affiche sur un PNJ est **[in-game]**.
4. **Verdict** : à adapter : le banc suit Hytale (conforme), mais « une main » n'est pas une limite de Hytale, seulement un réglage de notre rôle.
5. **Proposition** : donner au rôle `OffHandSlots: 1` et tenir le produit en main secondaire comme MC, si l'affichage se vérifie en jeu ; marquer la part du banc `Deviation from MC (Hytale world): the hut block → the recipe's Hytale bench (CraftingManager.isValidBenchForRecipe)`. Sauvegardes : aucun effet (asset du rôle). Config : aucune.
- **Gravité** : cosmétique.

### Nourriture et cuisson

#### B-16. La cuisson : le four devient le feu de camp
1. **MC** : `ItemStackUtils.java:162` (`ISCOOKABLE` : le résultat de four est un aliment) ; `FurnaceUserModule.java:136` (les `FurnaceBlock` de la hutte) ; `FoodUtils.java:60-73` (`canEatLevel` refuse un `ItemCrop`).
2. **HC** : `id-map.json:87` (`cookingBench: "Campfire"`) ; `plugin/.../food/HytaleFoods.java:56-90` lit les recettes `Processing` du feu de camp ; `HytaleCookingCatalog.java:42` ; `core/.../citizen/food/FoodRules.java:25-40` (un légume Hytale se cuit, il n'est donc jamais « comestible » au sens de MC) ; `crafting/furnace/FurnaceUserModule.java:19` (écart marqué : un feu de camp posé dans l'emprise compte). Rattaché : `core/.../crafting/restaurant/FurnaceWork.java:104-110` (`stationToFill`), écart marqué l. 108-110 : un four de MC s'allume seul, un banc de Hytale doit être allumé, et un joueur ou une sortie pleine l'éteint ; un banc éteint qui a combustible et nourriture est rallumé. Chez MC, l'IA ne fait que lire `furnace.isLit()` (`MC/core/entity/ai/workers/AbstractEntityAIUsesFurnace.java:140`, `:297`), l'allumage étant celui du four de Minecraft (hors `sources/`).
3. **Hytale** : `zip:Server/Item/Items/Bench/Bench_Campfire.json` (banc `Processing` `Campfire`, combustible `res:Fuel`) ; `zip:Server/Item/Items/Food/Food_Wildmeat_Cooked.json`, recette `Meats` ×1 → plat cuit au `Campfire`, 2 s ; même chose pour `Food_Vegetable_Cooked` (`Vegetables`) et `Food_Fish_Grilled` (`sp4b-hytale-food.md` § 3.2). Un banc de transformation naît éteint (`active = false`, sauvé sous `Active`, `HY/builtin/crafting/component/ProcessingBenchBlock.java:86`, `:121`), s'allume par `setActive` (l. 312-328) et s'éteint seul, par exemple sans recette en cours (l. 539-540, 597-598) ou faute de combustible (`HY/builtin/crafting/system/BenchSystems.java:412-435`).
4. **Verdict** : conforme (le rattachement aussi : allumer le banc est le geste du monde de Hytale).
5. **Proposition** : reformuler les marquages en `(Hytale world)`, celui de `FurnaceWork` compris (`Deviation from MC (Hytale world): a MC furnace lights itself → a Hytale processing bench must be turned on (ProcessingBenchBlock.setActive)`). Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### B-17. Les combustibles
1. **MC** : `BuildingModules.java:74` (liste par défaut : charbon et charbon de bois) ; `Burnable.java:116` (`FurnaceBlockEntity.isFuel`).
2. **HC** : `id-map.json:89` (`defaultFuels: ["Ingredient_Charcoal"]`) ; `HytaleCookingCatalog.java:58`, `:73` (les objets du ResourceType `Fuel`) ; `crafting/furnace/FuelListModule.java:22-26`.
3. **Hytale** : `zip:Server/Item/ResourceTypes/Fuel.json` ; dans le zip pre.5, 366 objets portent ce type (bois, meubles…) ; `FuelQuality` de `Ingredient_Charcoal` = 6 ; aucun objet « charbon » de minerai, seulement `Ingredient_Charcoal`. Durée = quantité × `FuelQuality` (`sp4b-hytale-food.md` § 3.3).
4. **Verdict** : conforme.
5. **Proposition** : marquer `(Hytale world)` sur `FoodIds.defaultFuels`. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### B-18. Le serveur accélère les feux
1. **MC** : `AbstractEntityAIUsesFurnace.java:80`, `:283-290` (toutes les secondes, (compétence / 10) × 2 ticks de four, combustible compris).
2. **HC** : `core/.../crafting/restaurant/FurnaceWork.java:263-271` ; `plugin/.../food/HytaleCookingStations.java:26-35`, `:114-121` n'avance que la progression (écart marqué, **[in-game]**).
3. **Hytale** : `HY/builtin/crafting/system/BenchSystems.java:296-305` : la progression est en secondes réelles (`effectiveDt` = secondes de jeu écoulées / `getSecondsPerTick`) ; 1 tick de MC = 1/20 s, comme le code le suppose. Pas de méthode publique qui joue un tick de banc complet.
4. **Verdict** : conforme.
5. **Proposition** : marquage `(Hytale world)`. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### B-19. La valeur nourrissante des aliments
1. **MC** : la `nutrition` des `FoodProperties` de Minecraft nourrit les formules du système : `FoodUtils.java:60-73` (`canEatLevel` : nutrition ≥ niveau + 1 dès le niveau 3), `:92-105` (`getFoodValue`), `:124` (`getFoodTier`).
2. **HC** : table `food.foods` de `id-map.json:86-142`, valeurs **calquées sur l'aliment de Minecraft le plus proche** (spec SP4b § 2.2 ; `HytaleFoods.java:24-27`, écart marqué) : fruits 4, légumes 1 (carotte 3), viandes crues 3 (poulet 2), poisson cru 2, gibier cuit 8, poisson grillé 5, légumes cuits 5, puis 6 / 8-9 / 12-13 pour les plats. Rattaché : `core/.../citizen/food/FoodRules.java:90-96` (`tier`), écart marqué l. 91-93 : MC donne aussi le palier 1 à un aliment ordinaire de nutrition ≥ 12 et de modificateur de saturation ≥ 0,8 (`MC/api/util/FoodUtils.java:124-138`, `getFoodTier`), alors que HyColony le refuse, « no Hytale food of the table comes close and saturation modifiers are not ported ». Ce seuil porte sur les `FoodProperties` de Minecraft : sa valeur suit celle de la table.
3. **Hytale** : pas de faim (`sp4b-hytale-food.md` § 2). Les effets, lus dans le zip pre.5, ne distinguent pas ce que la table distingue : les 9 fruits et les 12 légumes ont tous `Food_Instant_Heal_T1` + `HealthRegen_TierCheck_Raw` (`zip:Server/Item/Items/Plant/Fruit/Template_Fruit.json`, `zip:Server/Item/Items/Plant/Crop/_Template/Template_Crop_Item.json`) ; les 3 plats du feu de camp ont tous `Food_Instant_Heal_T1` + `HealthRegen_TierCheck_T1` (+ `Meat_TierCheck_T1` ou `FruitVeggie_TierCheck_T1`) (`zip:Server/Item/Items/Food/Food_Wildmeat_Cooked.json`, `Food_Fish_Grilled.json`, `Food_Vegetable_Cooked.json`) ; `Food_Bread` n'a que `Food_Instant_Heal_Bread`. Les paliers 1 à 3 (plats du `Cookingbench`) suivent déjà `HealthRegen_TierCheck_T2/T3` (`sp4b-hytale-food.md` § 1.5). La table est complète pour pre.5 (aucun aliment des assets n'en manque).
4. **Verdict** : à adapter (les paliers sont conformes, les valeurs non).
5. **Proposition** : une valeur par classe d'effet Hytale (cru, T1 cuit, pain, T2, T3), sur l'échelle de MC pour que `canEatLevel` garde son sens (un foyer de niveau 5 doit trouver des aliments de nutrition ≥ 6). Valeurs à fixer à la conception. Pour le palier 1 d'un aliment ordinaire (rattaché ci-dessus) : garder le seuil 12 de MC sur la nouvelle échelle, ou, si aucune classe ne l'atteint, le dire dans le marquage `(Hytale world)` de `FoodRules.tier` plutôt que « no Hytale food comes close ». **Sauvegarde** : aucune (l'historique des repas garde des ids d'objets). Pas de config ; régénérer les infobulles (`tools/food/generate.py`, `hycolony_food.lang`).
- **Gravité** : incohérent.

#### B-20. Les aliments toxiques
1. **MC** : `DefaultItemTagsProvider.java:178-182` (tag `poisonous_food` : pomme de terre empoisonnée, **poulet cru**, œil d'araignée, chair putréfiée) ; `FoodUtils.java:45` ; `EntityCitizen.java:492-509` (nourri à la main : maladie, 400 ticks d'attente).
2. **HC** : `id-map.json:105` (`Food_Chicken_Raw` `poisonous: true`, choix du tag MC) et `:119-124` (champignons lumineux) ; `core/.../citizen/food/HandFeeding.java:44-47` (issue `POISONED` : l'objet est pris, rien n'est gagné) ; infobulle « trop cru » (`Server/Languages/en-US/hycolony_food.lang:11-12`).
3. **Hytale** : `zip:Server/Item/Items/Food/Food_Chicken_Raw.json` n'a que `Food_Instant_Heal_T1` (aucun effet négatif) ; seuls les 6 `Plant_Crop_Mushroom_Glowing_*` appliquent `Poison` (2 s), par exemple `zip:Server/Item/Items/Plant/Crop/Plant_Crop_Mushroom_Glowing_Red.json:27-32` ; `Plant_Fruit_Poison` hérite de l'effet T1 de `Template_Fruit`.
4. **Verdict** : à adapter.
5. **Proposition** : `Food_Chicken_Raw` → `poisonous: false`. Les champignons lumineux restent toxiques. **Sauvegarde** : aucune. Pas de config ; régénérer l'infobulle du poulet.
- **Gravité** : incohérent (un joueur qui tend du poulet cru le perd sans effet).

#### B-21. Les effets de l'aliment sur le citoyen
1. **MC** : `ItemStackUtils.java:931-937` (`consumeFood`) ajoute la saturation puis appelle `foodStack.finishUsingItem(level, citizen)`, qui joue les effets de l'aliment sur l'entité dans Minecraft (code vanilla, absent de `sources/`, non vérifié ici).
2. **HC** : `core/.../citizen/food/Meals.java:11-14` : écart marqué, les effets de Hytale (soin, régénération, bonus) ne sont pas appliqués ; la vie suit la saturation (`HungerTicks`), la régénération des PNJ est coupée (`plugin/.../npc/body/BodyVitals.java:86-92`).
3. **Hytale** : la variable `Effect` de chaque aliment (`Food_Instant_Heal_T1/T2/T3`, `HealthRegen_Buff_*`, `Meat_Buff_*`, `FruitVeggie_Buff_*`, `zip:Server/Entity/Effects/Food/`) ; pose possible par `EffectControllerComponent.addEffect` (`HY/server/core/entity/effect/EffectControllerComponent.java:85-152`). Les `*_TierCheck_*` sont des interactions, pas des effets : les rejouer sur un PNJ est **[in-game]**.
4. **Verdict** : à adapter.
5. **Proposition** : appliquer les effets Hytale de l'aliment mangé, en plus de la saturation de MC (comme `finishUsingItem` s'ajoute à la saturation chez MC). Tant que le rôle citoyen est `Invulnerable` (`Server/NPC/Roles/HyColony/HyColony_Citizen.json:16`), seul le bonus d'endurance ou de vie max se verrait. Ni sauvegarde ni config.
- **Gravité** : cosmétique aujourd'hui ; incohérent quand les citoyens pourront être blessés.

#### B-22. Les miettes du repas
1. **MC** : `EntityAIEatTask.java:203` et `EntityCitizen.java:608`, `:639` (`ItemParticleEffectMessage` avec l'objet mangé).
2. **HC** : `plugin/.../adapter/HytaleWorldEffects.java:217-224` : `Food_Eat` générique (écart marqué).
3. **Hytale** : chaque aliment donne la couleur de ses miettes (`Food_Chicken_Raw.json`, `Particles[0].Color = #F8C9C9`) ; `ParticleUtil.spawnParticleEffect` prend une `Color` (`HY/server/core/universe/world/ParticleUtil.java:103-110`).
4. **Verdict** : à adapter.
5. **Proposition** : passer l'aliment à `WorldEffects.eating` et lire sa couleur `Food_Eat` dans l'adaptateur. Ni sauvegarde ni config. **[in-game]** : le rendu. Regroupée avec D-11 (hauteur d'où partent les miettes) : deux règles distinctes du même effet, corrigées ensemble.
- **Gravité** : cosmétique.

#### B-23. Les places de la salle à manger
1. **MC** : `BuildingCook.java:84-92` (`getNextSittingPosition` : balises `sit`, `sit_in`, `sit_out` du plan).
2. **HC** : `core/.../crafting/restaurant/DiningRoomModule.java:23-28` : les blocs à sièges de l'emprise (écart marqué) ; `plugin/.../npc/body/HytaleBodySeats.java`.
3. **Hytale** : 32 meubles portent `Seats` (`sp4b-hytale-food.md` § 6) ; `BlockMountAPI.mountOnBlock` (`HY/builtin/mounts/BlockMountAPI.java:73-83`).
4. **Verdict** : conforme.
5. **Proposition** : marquage `(Hytale world)`. Ni sauvegarde ni config.
- **Gravité** : cosmétique.

#### B-24. La faim au fil d'une journée (fusion de C-5)
1. **MC** : `decreaseIdleSaturation` tourne toutes les `SATURATION_DECREASE_AFTER = 20 * 60` ticks (`MC/api/util/constant/CitizenConstants.java:63`, transition `MC/core/entity/citizen/EntityCitizen.java:288`) et ne fait rien quand `level().isNight()` ou quand le citoyen dort (`EntityCitizen.java:1977`). Une journée de MC dure 24 000 ticks. La règle exacte de `Level.isNight()` est celle de Minecraft vanilla, absente de `sources/` : **non vérifiée**. Le seul seuil du jour que MineColonies écrit est `WorldUtil.isDayTime`, `dayTime % 24000 <= NIGHT` avec `NIGHT = 12600` (`MC/api/util/WorldUtil.java:165-168`, `CitizenConstants.java:241`), soit 12 600 ticks de jour et 10,5 baisses par journée. Ces 12 600 ticks sont donc une **approximation** du jour de `isNight()`.
2. **HC** : `core/.../citizen/food/HungerTicks.java:17-18` (cadence de MC, 1 200 ticks) et `:26-34` (rien hors du jour, `clock().isDaytime()`, ni pour un citoyen endormi) ; le jour est celui de Hytale, du lever au coucher (`plugin/.../adapter/HytaleGameClock.java:46-50`, `isScaledDayTimeWithinRange(0.25, 0.75)`, voir C-1). `Gameplay.FoodModifier` existe déjà (`core/.../kernel/config/ColonyConfig.java:26`).
3. **Hytale** : jour par défaut de 1 728 s réelles (`HY/server/core/asset/type/gameplay/WorldConfig.java:102`, `DEFAULT_DAYTIME_DURATION_SECONDS`), soit 34 560 ticks à 20 ticks/s et 28,8 baisses par journée ; la nuit (1 151 s, l. 103) n'en compte aucune.
4. **Verdict** : conforme à la règle de la zone grise (spec § 2 : un délai propre au système reste en ticks ; « la nuit » suit Hytale). Par minute réelle de jour, un citoyen a faim comme chez MC ; par journée, la faim ne baissant que le jour, il a faim 34 560 / 12 600 ≈ 2,7 fois plus (et non 2,4, rapport des journées entières, 2 880 s contre 1 200 s, qui compterait la nuit). Le rapport hérite de l'approximation du point 1.
5. **Proposition** : rien dans le code tant que l'utilisateur n'a pas tranché (décision 3 de l'en-tête) : garder la cadence de MC en ticks (règle de la zone grise), ou ramener la faim d'une journée Hytale à celle d'une journée MC par `Gameplay.FoodModifier` (clé et bornes de MC, déjà lues), sachant que le fermier ne fait qu'une passe par champ et par jour (B-4). Sauvegardes : aucun effet. Config : seulement la valeur de `FoodModifier` si l'utilisateur la change.
- **Gravité** : incohérent (équilibrage, à arbitrer).

#### B-25. Le joueur que le serveur sert
1. **MC** : le serveur de la salle à manger sert un joueur présent dont `getFoodData().getFoodLevel() < LEVEL_TO_FEED_PLAYER`, soit 10 sur 20, et qui a `MANAGE_HUTS` (`MC/core/entity/ai/workers/service/EntityAIWorkCook.java:69`, `:341-345`, `checkForImportantJobs`).
2. **HC** : `core/.../crafting/restaurant/CookService.java:24-29`, écart marqué : « Hytale has no hunger, so a player is served below PLAYER_HEALTH_PERCENT of its health instead of MC's food level 10 [in-game] » ; `PLAYER_HEALTH_PERCENT = 50` (l. 32-33).
3. **Hytale** : aucune faim chez le joueur : les statistiques d'entité du zip sont `Ammo`, `DeployablePreview`, `GlidingActive`, `Health`, `Immunity`, `MagicCharges`, `Mana`, `Oxygen`, `SignatureCharges`, `SignatureEnergy`, `Stamina` et `StaminaRegenDelay` (`zip:Server/Entity/Stats/`). La nourriture de Hytale rend de la vie (`Food_Instant_Heal_*`, B-19), sur une vie de 100 (`zip:Server/Entity/Stats/Health.json:2-4`).
4. **Verdict** : conforme : la vie, que la nourriture de Hytale remplit, est l'équivalent le plus proche de la barre de faim, et 50 % reprend le 10 sur 20 de MC. Un pourcentage ne dépend pas de l'échelle de vie (C-18).
5. **Proposition** : marquage `Deviation from MC (Hytale world): food level < 10 → health below 50 % (Hytale has no hunger stat, Server/Entity/Stats)`. **[in-game]** : un joueur blessé est servi. Ni sauvegarde ni config (MC code le seuil en dur).
- **Gravité** : cosmétique (marquage).

### Bilan du groupe B

| Verdict                | Casse le jeu | Incohérent                              | Cosmétique                                                                        | Total  |
|------------------------|--------------|-----------------------------------------|-----------------------------------------------------------------------------------|--------|
| Conforme               | 0            | 1 (B-24)                                | 17 (B-1 à B-6, B-9, B-10, B-16 à B-18, B-23, B-25, B-27, B-28, B-30, B-31)        | 18     |
| À adapter              | 0            | 5 (B-12, B-15, B-19, B-20, B-26)        | 7 (B-7, B-8, B-11, B-13, B-21, B-22, B-29)                                        | 12     |
| À retirer de HyVanilla | 0            | 0                                       | 0                                                                                 | 0      |
| **Total**              | **0**        | **6**                                   | **24**                                                                            | **30** |

B-13 est compté « à adapter » pour l'objet propre de chaque hutte ; ses bancs et matériaux sont conformes. B-15 porte aussi l'ancienne A-18 ; B-24 porte aussi l'ancienne C-5 ; B-14 est passée dans « Hors du monde » (système). Rattachés sans entrée propre : `FurnaceWork.java:108` à B-16, `FoodRules.java:91` à B-19. Ordre proposé pour les corrections du groupe : B-20 (une ligne d'id-map), B-26, B-12, B-19, B-15, puis les cosmétiques (B-8 et B-29 d'abord).

Ce qui reste **[in-game]** : la repousse d'une culture éternelle récoltée (B-7), l'accélération des feux par la seule progression (B-18), les effets de nourriture rejoués sur un PNJ (B-21), la couleur des miettes (B-22), le joueur blessé servi (B-25), la graine éternelle apprise par le fermier (B-26), l'objet en main secondaire d'un PNJ (B-29).

## C. Temps, mobs et combat

Audit du 2026-10-02, spec `docs/superpowers/specs/2026-10-02-hycolony-monde-hytale-design.md` § 2 et § 5. Groupe C :
jour et nuit, sommeil, pluie et loisirs, apparition des monstres, raids, gardes, dégâts et mort des citoyens.

**Version.** La consigne cite 0.7.0-pre.4, mais `gradle.properties:19` épingle aujourd'hui `hytale_version =
0.7.0-pre.5`. Les faits Hytale ci-dessous viennent donc des sources décompilées du 2026-10-01 20:38 (après le jar
pre.5) et de `pre-release-0.7.0-pre.5-Assets.zip`.

**Abréviations.**
- `MC/` = `sources/minecolonies/src/main/java/com/minecolonies/` ;
- `C/` = `core/src/main/java/dev/hycolony/core/`, `P/` = `plugin/src/main/java/dev/hycolony/plugin/`,
  `R/` = `plugin/src/main/resources/` ;
- `HY/` = `build/vineflower/hytale-server/com/hypixel/hytale/` ;
- `zip:` = `%USERPROFILE%/.gradle/caches/hytale-assets/pre-release-0.7.0-pre.5-Assets.zip`.

**Gravité.** Elle mesure ce qui se passe en jeu si la règle reste telle quelle, y compris quand le système qui la
porte sera activé (citoyens mortels, raids). « Latent » signale un problème qui ne se voit pas encore aujourd'hui.

**Marquage.** La spec (§ 3) veut `Deviation from MC (Hytale world): …` pour un écart dû au monde. Plusieurs écarts
conformes ci-dessous portent encore le simple `Deviation from MC:` ; la proposition le dit à chaque fois. Ce
remarquage ne change ni le comportement, ni les sauvegardes, ni la configuration.

### Temps

#### C-1. Le jour et la nuit de la colonie

1. **MC** : `WorldUtil.isDayTime` : `dayTime % 24000 <= NIGHT` (`MC/api/util/WorldUtil.java:165-168`), avec
   `NIGHT = 12600` (`MC/api/util/constant/CitizenConstants.java:241`). `Colony.checkDayTime`
   (`MC/core/colony/Colony.java:633-655`) : à la tombée de la nuit, événements, raids, bonheur (avec un joueur
   proche) et sommeil ; à l'aube, `day++` et `citizenManager.onWakeUp()`.
2. **HyColony** : `C/colony/Colony.java:127-139` reprend la bascule : `day++` et `citizens.onWakeUp()` à l'aube,
   `HappinessEvents.onNightFall` et `SleepNotice.onNightFall` à la nuit, toutes les `DAYTIME_INTERVAL` ticks. Le jour
   vient du port `GameClock.isDaytime` (`C/kernel/port/GameClock.java:11-12`), branché sur
   `P/adapter/HytaleGameClock.java:46-50` : `isScaledDayTimeWithinRange(0.25, 0.75)`, c'est-à-dire du lever au
   coucher de Hytale.
3. **Hytale** : `HY/server/core/modules/time/WorldTimeResource.java:37-40` : `DAYTIME_PORTION_PERCENTAGE = 0.6`,
   `DAYTIME_SECONDS`, `NIGHTTIME_SECONDS`, `SUNRISE_SECONDS = NIGHTTIME_SECONDS / 2` (lever 04:48, coucher 19:12) ;
   `isScaledDayTimeWithinRange` l. 319. Durées réelles par défaut : `zip:Server/GameplayConfigs/Default.json`,
   `World.DaytimeDurationSeconds: 1728`, `NighttimeDurationSeconds: 1152` (l. 88-90), soit 2 880 s par jour contre
   24 000 ticks = 1 200 s chez MC.
4. **Verdict** : Conforme : la colonie suit le jour de Hytale, et le compteur `day` compte des jours de Hytale.
5. **Proposition** : Rien. Sauvegardes : aucun effet. Config : aucune.
- **Gravité** : cosmétique (rien à faire).

#### C-2. L'heure « façon MC » posée par phases sur le jour de Hytale

1. **MC** : Toutes les heures de MC se lisent sur `dayTime % 24000` (`MC/api/util/WorldUtil.java:165-179`) : 0 à
   l'aube, `NOON = 6000`, `NIGHT = 12600` (`CitizenConstants.java:236,241`).
2. **HyColony** : `C/kernel/port/GameClock.java:4-21` : `dayTime()` dans [0, 24000[, le jour de Hytale (lever →
   coucher) sur [0, 12600[ et sa nuit sur [12600, 24000[ ; `realTicksUntil` donne les ticks réels avant une heure
   cible. `P/adapter/HytaleGameClock.java:52-88` le calcule depuis `getGameDateTime()` et les durées du monde
   (`getDaytimeDurationSeconds`, `getNighttimeDurationSeconds`), et rend `Long.MAX_VALUE` si le temps est figé.
   Écart marqué l. 13-14 : pendant une interpolation de l'heure par commande, `realTicksUntil` est faux.
3. **Hytale** : `WorldTimeResource.getGameDateTime` (`HY/server/core/modules/time/WorldTimeResource.java:204`),
   `World.getDaytimeDurationSeconds` / `getNighttimeDurationSeconds` (`HY/server/core/universe/world/World.java:458,463`),
   `WorldConfig.IsGameTimePaused` (`HY/server/core/universe/world/WorldConfig.java:114`). Le passage de nuit des
   joueurs avance l'heure d'un coup (`HY/builtin/beds/sleep/systems/world/UpdateWorldSlumberSystem.java:60`,
   `setGameTime(wakeUpTime, …)`) : l'horloge du cœur relit l'heure du jeu, donc la colonie se réveille aussi.
4. **Verdict** : Conforme : c'est exactement la règle de la zone grise (une heure de MC liée au monde devient une
   fraction du jour de Hytale).
5. **Proposition** : Rien, sauf remarquer l'écart de la l. 13-14 en `Deviation from MC (Hytale world)`. Sauvegardes :
   aucun effet (l'heure n'est pas sauvée par le cœur). Config : aucune.
- **Gravité** : cosmétique (marquage).

#### C-3. Heure du coucher et du réveil

1. **MC** : `CitizenAI.calculateNextState` (`MC/core/entity/ai/workers/CitizenAI.java:168-200`) : passé
   `NIGHT - 2000`, un citoyen en `SLEEP` y reste (redécision toutes les `20 * 15` ticks) et un autre se couche si
   `shouldGoSleep` ; avant, un citoyen endormi se réveille.
2. **HyColony** : `C/citizen/sleep/SleepDecision.java:15-19` (`NIGHT = 12600`, `EVENING = NIGHT - 2000`,
   `SLEEP_DECIDE_DELAY_TICKS = 20 * 15`) et `decide`, l. 39-48. `EVENING` est lu sur l'horloge par phases (C-2).
3. **Hytale** : Même horloge que C-2. Avec le jour de 1 728 s réelles, `EVENING` tombe 2000 / 12600 × 1728 ≈ 274 s
   réelles avant le coucher, vers 16:55 heure de jeu ; chez MC, 2 000 ticks = 100 s. Le réveil (`dayTime <= EVENING`,
   donc dès 0) tombe au lever de 04:48, à côté de l'heure de réveil des joueurs, `Sleep.WakeUpHour: 4.79`
   (`zip:Server/GameplayConfigs/Default.json:92-93`).
4. **Verdict** : Conforme (fraction du jour de Hytale). Le délai de 15 s pendant le sommeil est un délai du système, et
   il reste en ticks, comme il se doit.
5. **Proposition** : Rien. Sauvegardes : aucun effet. Config : aucune.
- **Gravité** : cosmétique (rien à faire).

#### C-4. Temps de marche jusqu'au lit (`TIME_PER_BLOCK`)

1. **MC** : `CitizenSleepHandler.shouldGoSleep` : `timeNeeded = distance pondérée × TIME_PER_BLOCK`, avec
   `TIME_PER_BLOCK = 6` (« the rough time traveling one block takes, in ticks ») et `Y_DIFF_WEIGHT = 1.5`
   (`MC/core/entity/citizen/citizenhandlers/CitizenSleepHandler.java:37-44,255-266`). Six ticks par bloc, c'est
   3,33 blocs par seconde : la vitesse de marche d'un citoyen de Minecraft.
2. **HyColony** : `C/citizen/sleep/SleepDecision.java:21-23` garde `TIME_PER_BLOCK = 6`, et l. 50-69 compte la marche
   en ticks réels (écart marqué l. 54-55, `Deviation from MC:` : avec le jour par phases, temps restant et marche sont
   tous deux en ticks réels).
3. **Hytale** : La vitesse du corps vient du rôle : `R/Server/NPC/Roles/HyColony/HyColony_Citizen.json:8`,
   `"MaxWalkSpeed": 3` (« Maximum horizontal speed », `HY/server/npc/movement/controllers/builders/BuilderMotionControllerWalk.java:95`,
   unité non écrite dans la source). Si c'est 3 blocs par seconde, un bloc prend environ 6,7 ticks, et non 6.
4. **Verdict** : À adapter. Compter la marche en ticks réels est juste (déjà fait), mais la valeur 6 mesure la vitesse
   d'un citoyen de Minecraft, c'est-à-dire la navigation des PNJ, qui relève du monde. Avec un corps plus lent, le
   citoyen part un peu trop tard et arrive après `NIGHT` (environ 10 % de retard sur la marche, plus les détours).
5. **Proposition** : Mesurer **[in-game]** la vitesse réelle d'un citoyen (vitesse du métier 1), puis fixer
   `TIME_PER_BLOCK` à `20 / vitesse` (environ 7), avec `Deviation from MC (Hytale world): MC's 6 ticks per block (its
   citizen's walk) → our body's MaxWalkSpeed (HyColony_Citizen.json)`. Sauvegardes : aucun effet. Config : aucune
   (MC code la valeur en dur).
- **Gravité** : cosmétique (un retard de quelques secondes au coucher).

#### C-5. Faim : pas de perte la nuit

Fusionné dans B-24, qui porte seul la cadence de la faim, sa pause de nuit et son rapport à la journée de Hytale (une
seule gravité).

#### C-6. Durées du bonheur comptées en jours

1. **MC** : Les modificateurs de bonheur comptent des jours de colonie, fermés à la tombée de la nuit
   (`MC/core/colony/Colony.java:638-643`, `checkCitizensForHappiness` avec un joueur proche). Exemple :
   `ExpirationBasedHappinessModifier(DAMAGE, 2.0, …, 1)` à chaque blessure (`EntityCitizen.java:1428`).
2. **HyColony** : `C/citizen/happiness/HappinessEvents.java:10-36` (`DAMAGE_DAYS = 1`, `GREAT_FOOD_DAYS = 5`,
   `onNightFall`) ; `C/citizen/happiness/CitizenHappiness.java:24-27` (`COMPLAIN_DAYS = 7`, `DEMANDS_DAYS = 14`) et
   l. 46-50 (`SLEPTTONIGHT`, paliers 0/2/3 jours) ; fin du jour par `dayEnd`, l. 105.
3. **Hytale** : Nuit de C-1. Sept jours font 7 × 2 880 s ≈ 5 h 36 réelles, contre 7 × 1 200 s = 2 h 20 chez MC.
4. **Verdict** : Conforme : une durée en jours est une durée du monde, comptée en jours de Hytale.
5. **Proposition** : Rien. Sauvegardes : aucun effet. Config : aucune.
- **Gravité** : cosmétique (rien à faire).

#### C-7. Temps de loisir

1. **MC** : `CitizenData.update` (`MC/core/colony/CitizenData.java:1648-1656`) : une pause de `20 * 60 * 3` ticks,
   tirée avec la chance `nextInt(20 * 60 * (60 / (niveau maison / 2)) / tickRate) <= 0`. Le sommeil la remet à 0
   (l. 1226).
2. **HyColony** : `C/citizen/CitizenData.java:20-21` (`LEISURE_TICKS = 20 * 60 * 3`), remise à 0 l. 131-134 ; la pause
   bloque le travail dans `C/citizen/CitizenAI.java` (`onBreak`).
3. **Hytale** : Sans objet : ce sont des minutes réelles, pas une heure du jour.
4. **Verdict** : Conforme : délais du système, gardés en ticks.
5. **Proposition** : Rien. Sauvegardes : aucun effet. Config : aucune.
- **Gravité** : cosmétique (rien à faire).

### Sommeil

#### C-8. Le lit : bloc libre au-dessus

1. **MC** : `EntityAISleep.findBedAndTryToSleep` : un lit ne sert que si c'est la tête et si le bloc au-dessus est un
   lit, un panneau Domum, une trappe ou un bloc non solide (`MC/core/entity/ai/minimal/EntityAISleep.java:184-189`).
   C'est la place qu'il faut à un joueur ou à un citoyen couché dans Minecraft.
2. **HyColony** : Les lits sont ceux de Hytale (`ItemCatalog.isBed`, `P/adapter/HytaleItemCatalog.java:206-208`), et le
   coucher passe par la monture native (`P/npc/CitizenBeds.java`, Javadoc de classe et `sleepIn`). Mais
   `C/citizen/sleep/SleepAI.java:179-198` (`pickBed`) recopie le contrôle du bloc du dessus (l. 194-197 : un lit ou un
   bloc non solide), sinon le citoyen dort à la hutte.
3. **Hytale** : `BlockMountAPI.mountOnBlock` (`HY/builtin/mounts/BlockMountAPI.java:29-110`) ne refuse que pour une
   monture déjà prise, un chunk ou un bloc absent, un bloc sans `Beds`/`Seats` ou un point de couchage occupé
   (`NO_MOUNT_POINT_FOUND`, l. 92-94). Il ne regarde jamais le bloc du dessus. Un lit de Hytale est un seul bloc
   modèle avec ses cellules de remplissage (`docs/research/sp4-sleep-home.md` B.2).
4. **Verdict** : À adapter : la place au-dessus est une règle de Minecraft. Dans Hytale, un lit sous un toit bas, une
   mezzanine ou un lit superposé refuserait le citoyen alors qu'un joueur s'y couche.
5. **Proposition** : Retirer le contrôle du bloc du dessus et s'en remettre au refus de `mountOnBlock` (déjà un
   `false` de `sleepIn`, qui fait dormir à la hutte comme MC). `Deviation from MC (Hytale world): MC's free block above
   the bed head → BlockMountAPI.mountOnBlock's own checks`. Sauvegardes : aucun effet (les lits enregistrés restent).
   Config : aucune.
- **Gravité** : cosmétique (le citoyen dort alors à la hutte).

#### C-25. Le citoyen sorti de son lit par le monde de Hytale

1. **MC** : un citoyen ne quitte son lit que par MineColonies : l'IA le réveille (`MC/core/entity/ai/workers/CitizenAI.java:193`,
   `:198`), la maladie aussi (`MC/core/entity/ai/minimal/EntityAISickTask.java:348`) ; au chargement de l'entité, il
   n'est réveillé que sans position de lit (`MC/core/colony/CitizenData.java:574-577`, `if (getBedPos().equals(BlockPos.ZERO))`).
2. **HyColony** : trois écarts marqués `Deviation from MC:` (sans `(Hytale world)`) :
   - `C/citizen/sleep/CitizenSleep.java:67-73` : un corps qui apparaît réveille toujours son citoyen, « as Hytale saves
     no NPC lying in a bed » ;
   - `C/citizen/sleep/SleepHandler.java:68-76` (`leftBed`) : un citoyen sorti du lit sans nous (lit cassé, nuit sautée
     par les joueurs, téléportation) est éveillé et sans lit, sans les crochets du réveil ;
   - `C/citizen/sleep/SleepAI.java:201-209` (appels l. 219 et 224) : près de son lit mais pas couché, il se recouche en
     entier.
3. **Hytale** : le coucher est une monture, et `MountedComponent` n'est pas sauvegardé : il est enregistré sans codec
   (`HY/builtin/mounts/MountPlugin.java:78-80`) et `RemoveMountedHolder` le retire au déchargement de l'entité
   (`HY/builtin/mounts/MountSystems.java:630-651`). Le passage de nuit des joueurs avance l'heure d'un coup
   (`HY/builtin/beds/sleep/systems/world/UpdateWorldSlumberSystem.java:60`). Que la casse du lit ou une téléportation
   démonte le corps n'est lu que dans le port (`C/kernel/port/CitizenBodies.java:63`, `isInBed`), pas dans le décompilé :
   **[in-game]**.
4. **Verdict** : conforme : ce sont des façons propres au monde de Hytale de sortir un dormeur de son lit, et HyColony
   les suit au lieu de garder un état de sommeil que le monde a perdu.
5. **Proposition** : passer les trois commentaires au format `Deviation from MC (Hytale world): MC's sleeper leaves its
   bed only through its AI → Hytale's mount is not saved (MountPlugin) and the world can dismount it`. Sauvegardes :
   aucun effet (`asleep` et `bedPos` sont déjà réécrits par `leftBed`). Config : aucune.
- **Gravité** : cosmétique (marquage).

### Pluie et loisirs

#### C-9. La pluie arrête le travail

1. **MC** : `CitizenAI.calculateNextState` : s'il pleut (`Level.isRaining()`, un drapeau pour tout le monde), hors du
   Nether et sans `shouldWorkWhileRaining`, le citoyen passe en `IDLE` (`MC/core/entity/ai/workers/CitizenAI.java:232-242`).
2. **HyColony** : `C/citizen/CitizenAI.java:294-312` (`rainStopsWork`, à la hutte de travail, avec
   `workersAlwaysWorkInRain` et `WorkerModule.canWorkDuringTheRain`) ; `P/adapter/HytaleWorldQuery.java:73-105`
   (`isRainingAt` : météo forcée, sinon météo de l'environnement du bloc ; pluie si la particule est dans la liste de
   l'id-map). Liste : `R/hycolony/id-map.json:55`, `Rain`, `Rain_Light`, `Rain_Heavy`, `Snow_Light`, `Snow_Heavy`,
   `Snow_Storm`. Écart marqué l. 79-81 (`Deviation from MC:`).
3. **Hytale** : `HY/builtin/weather/resources/WeatherResource.java:35,39` (`getWeatherIndexForEnvironment`,
   `getForcedWeatherIndex`). Dans `zip:Server/Weathers/**`, les particules de précipitation sont exactement les six de
   la liste (par ex. `Zone1/Zone1_Rain.json` → `Rain`, `Zone1/Zone1_Storm.json` → `Rain_Heavy`, `Zone3/Zone3_Snow.json`
   → `Snow_Light`, `Zone3/Zone3_Snow_Storm.json` → `Snow_Storm`). Les autres particules sont des effets d'ambiance
   (`Fireflies_GS`, `Fog`, `Embers`, `Ash`, `Ash_Storm`, `Sand_Storm`, `Magic_Sparks_*`…).
4. **Verdict** : Conforme : la météo par zone est celle de Hytale, et la neige compte comme chez MC. Reste un choix :
   `Sand_Storm` (`Zone2/Zone2_Sand_Storm.json`) et `Ash_Storm` (`Zone4/Zone4_AshWastes_Storm.json`) sont les
   « mauvais temps » du désert et des terres cendrées, et n'arrêtent pas le travail. Chez MC, `isRaining` reste vrai
   dans un désert, donc tout s'arrête (`CitizenAI.java:232`).
5. **Proposition** : Remarquer l'écart en `Deviation from MC (Hytale world): MC's world-wide Level.isRaining → the
   weather of the hut's environment (WeatherResource)`. Ajouter `Sand_Storm` et `Ash_Storm` à `precipitationParticles`
   est une décision d'équilibrage à soumettre à l'utilisateur. Sauvegardes : aucun effet. Config : l'id-map seulement ;
   `Gameplay.WorkersAlwaysWorkInRain` reste la clé de MC.
- **Gravité** : cosmétique (marquage ; les tempêtes sont une décision de l'utilisateur).

#### C-10. Loisirs sous la pluie

1. **MC** : `RegisteredStructureManager.getRandomLeisureSite` : sous la pluie, ni site mystique, ni lieu de loisir en
   plein air ; l'hôtel de ville en dernier recours (`MC/core/colony/managers/RegisteredStructureManager.java:386-433`).
   La flânerie au lieu de loisir préfère les places couvertes s'il pleut (`MC/core/entity/ai/minimal/EntityAICitizenWander.java:169,191`).
   Les places assises de restaurant et d'atelier changent aussi avec la pluie (`MC/core/colony/buildings/workerbuildings/BuildingCook.java:96`,
   `MC/core/entity/ai/workers/crafting/AbstractEntityAICrafting.java:205-240`).
2. **HyColony** : `C/citizen/wander/LeisureSites.java:19-43` lit la pluie au centre de la colonie (écart marqué
   l. 22-24) ; `C/citizen/wander/LeisureWalk.java:18-30` n'a pas de préférence de pluie (écart marqué) ;
   `C/crafting/restaurant/DiningRoomModule.java:27` note que `sit_out` n'a pas d'équivalent.
3. **Hytale** : Météo par environnement (C-9). Les plans de HyColony n'ont pas les étiquettes `sit_in`/`sit_out` de
   Structurize.
4. **Verdict** : Conforme : la pluie est celle de Hytale ; les places étiquetées manquent pour une raison de plans, pas
   de monde.
5. **Proposition** : Remarquer l'écart de `LeisureSites` en `(Hytale world)`. Sauvegardes : aucun effet. Config :
   aucune.
- **Gravité** : cosmétique (marquage).

### Mobs

#### C-11. Pas de monstre né dans le territoire

1. **MC** : `EventHandler.on(MobSpawnEvent.PositionCheck)` refuse un `Enemy` seulement dans la boîte d'un bâtiment de
   niveau 1 ou plus, et laisse passer les générateurs (`MobSpawnType.SPAWNER`) (`MC/core/event/EventHandler.java:424-459`).
   Ailleurs, MC compte sur la lumière de Minecraft (règle vanilla, absente de `sources/`).
2. **HyColony** : `C/app/ColonyProtection.java:33-43` (`allowsHostileSpawn` : aucun monstre dans une cellule
   revendiquée, écart demandé et marqué l. 37-39) ; `P/npc/spawn/HostileSpawns.java:21-73` (retrait à la disparition
   vanilla, `setDespawning`) ; `P/npc/spawn/HostileSpawnSystems.java:23-63` (apparition du monde, balises, marqueurs,
   rechargement ; écart marqué l. 61-62).
3. **Hytale** : Une apparition ne teste la lumière que si son asset déclare `LightRanges`
   (`HY/server/spawning/assets/spawns/config/NPCSpawn.java:66`) ; les apparitions de surface filtrent surtout par
   `DayTimeRange` (l. 47) et environnement. Détail et comptes : `docs/research/colony-bounds-and-mob-spawns.md` § 3-6.
4. **Verdict** : Conforme : sans règle de lumière en surface, refuser tout le territoire est l'équivalent Hytale le
   plus proche de « éclairer sa colonie ».
5. **Proposition** : Remarquer l'écart de `allowsHostileSpawn` en `Deviation from MC (Hytale world): MC's refusal in
   buildings plus Minecraft's light rule → no hostile spawn in claimed cells (Hytale's surface spawns have no light
   condition, NPCSpawn.LightRanges)`. Sauvegardes : aucun effet. Config : aucune (MC n'en a pas).
- **Gravité** : cosmétique (marquage).

#### C-12. Qui est un monstre : le groupe `HyColony_Hostile`

1. **MC** : Un monstre est une entité `Enemy` (`MC/core/event/EventHandler.java:426`).
2. **HyColony** : `R/Server/NPC/Groups/HyColony/HyColony_Hostile.json:1-19` : groupes `Aggressive`, `Outlander`,
   `Scarak`, rôles `Wraith*`, `Hound_Bleached`, `Golem_Firesteel`, `Spirit_*`, `Eye_Void_Surge`, `Void_Spawn_Surge`,
   `Slug_Magma`, sans `Horse_Skeleton*` ; nommé par `R/hycolony/id-map.json:42`.
3. **Hytale** : un groupe de PNJ est un asset `NPCGroup` à `IncludeRoles`, `ExcludeRoles`, `IncludeGroups`,
   `ExcludeGroups` (`HY/builtin/tagset/config/NPCGroup.java:24-41`), testé par `TagSetPlugin.tagInSet`
   (`HY/builtin/tagset/TagSetPlugin.java:72`). Le groupe vanilla `Aggressive` réunit `Trork`, `Goblin`, `Skeleton`,
   `Void`, `Zombie`, `Vermin`, `Predators`, `PredatorsBig` (`zip:Server/NPC/Groups/LivingWorld/Aggressive.json:2-11`).
   Ses trous : `docs/research/colony-bounds-and-mob-spawns.md` § 6.1. L'hostilité de `Molerat`, `Larva_Silk`,
   `Lizard_Sand`, `Bat`, `Cow_Undead` et `Pig_Undead` n'y est pas vérifiée.
4. **Verdict** : Conforme : `Enemy` devient le groupe Hytale le plus proche.
5. **Proposition** : Vérifier ces six rôles (gabarit et attitude dans `zip:Server/NPC/Roles/**`) et ajouter au groupe
   ceux qui sont hostiles. Sauvegardes : aucun effet. Config : aucune.
- **Gravité** : cosmétique (six rôles à vérifier, marquage).

#### C-13. Failles gobelines et « nature sauvage »

1. **MC** : Pas d'équivalent : c'est un événement propre à Hytale, que la règle de C-11 touche (des monstres qui
   apparaissent dans la colonie).
2. **HyColony** : `C/app/ColonyProtection.java:45-62` (`isWilderness`, écart marqué l. 47-48) ;
   `P/npc/spawn/ColonyWildernessTracker.java:13-40` remplace le suivi de nature sauvage de Hytale.
3. **Hytale** : `zip:Server/WorldEvent/Stage/Goblin/Breach/Goblin_Breach_Stage1.json:13` (condition
   `"Type": "WildernessLocation"`), lieu d'événement défini par `HY/builtin/adventure/wilderness/component/WildernessLocation.java:25-26`
   (`ID = "WildernessLocation"`) ; détail dans `docs/research/colony-bounds-and-mob-spawns.md` § 7.
4. **Verdict** : Conforme : rien n'est ajouté, on empêche seulement le monde de Hytale de contredire la règle de C-11.
5. **Proposition** : Remarquer l'écart en `(Hytale world)`. Sauvegardes : aucun effet. Config : aucune.
- **Gravité** : cosmétique (marquage).

#### C-14. Les monstres attaquent les citoyens (`mobAttackCitizens`)

1. **MC** : Avec `mobattackcitizens` (vrai par défaut, `MC/api/configuration/ServerConfiguration.java:178`), chaque
   `Enemy` reçoit un but « attaquer le citoyen le plus proche » (`MC/core/event/EventHandler.java:133-147`).
2. **HyColony** : Rien : pas de clé de config (recherche de `mobattack` dans `C/kernel/config/` et `P/config/` : aucune),
   pas de système. Le rôle du citoyen n'est dans aucun groupe de PNJ (`docs/research/colony-bounds-and-mob-spawns.md`
   § 6.1) et il est `Invulnerable` (`R/Server/NPC/Roles/HyColony/HyColony_Citizen.json:16`).
3. **Hytale** : L'attitude d'un PNJ envers un autre passe par `AttitudeView`
   (`HY/server/npc/blackboard/view/attitude/AttitudeView.java:38-47`) : la carte d'attitude des rôles (assets
   `zip:Server/NPC/Attitude/Roles/**`, par ex. `Intelligent/Aggressive/Goblin/Goblin.json` : hostile à `Spiders` et
   `Skeleton`), puis `DefaultNPCAttitude`, qui vaut `NEUTRAL` par défaut
   (`HY/server/npc/asset/builder/SupportConfigBuilder.java:95-104`). Aucun rôle de `zip:Server/NPC/Roles/**` ne fixe
   `DefaultNPCAttitude` (recherche vide). Les monstres de Hytale sont donc neutres envers nos citoyens.
4. **Verdict** : À adapter, avec le portage de la mort (C-17, système). Aujourd'hui, un monstre passe à côté d'un citoyen sans le
   regarder (et C-11 en retire la plupart du territoire).
5. **Proposition** : Au portage de la mort : un groupe de PNJ pour le citoyen (asset `Server/NPC/Groups/HyColony/`),
   puis rendre les rôles du groupe `HyColony_Hostile` hostiles envers lui. Le moyen reste à vérifier : surcharger des
   attitudes vanilla depuis le pack d'un mod, ou ajouter un fournisseur à la carte d'attitude
   (`NPCPlugin.getAttitudeMap()`, `HY/server/npc/NPCPlugin.java:1458`, lue par `AttitudeView` l. 38-39). **[in-game]** pour l'un comme pour l'autre. Clé
   `MobAttackCitizens` (défaut `true`, comme MC) dans la section MC correspondante. Sauvegardes : aucun effet.
- **Gravité** : incohérent (latent : sans mort portée, une attaque ne changerait rien).

### Raids et gardes

#### C-15. Les raids

1. **MC** : Raids de nuit : `RaidManager.onNightFall` compte les nuits depuis le dernier raid
   (`MC/core/colony/events/raid/RaidManager.java:738-778`) ; config `enablecolonyraids`, `raidDifficulty`,
   `maxRaiders`, `raidersbreakblocks`, `averagenumberofnightsbetweenraids` (14), `minimumnumberofnightsbetweenraids`
   (10) (`ServerConfiguration.java:172-177`), `raidersbreakdoors`, `skyraiders` (l. 179, 142). Cultures : barbares,
   pirates, Égyptiens, Nordiques, Amazones (`MC/core/colony/events/raid/`), entités de MC.
2. **HyColony** : Non porté. Restes : l'identifiant `RAIDWITHOUTDEATH` (`C/citizen/happiness/HappinessIds.java:17`) et
   la mention « raids are not ported » (`C/citizen/CitizenAI.java:31`).
3. **Hytale** : Factions de PNJ intelligents : `zip:Server/NPC/Roles/Intelligent/Faction/` {`Feran`, `Goblin`,
   `Kweebec`, `Outlander`, `Scarak`, `Trork`, `Tuluk`} ; événement du monde `zip:Server/WorldEvent/Event/Goblin/Goblin_Breach_Event.json`.
4. **Verdict** : À adapter au portage : le raid est un système de MC (fréquence, difficulté, vagues), mais ses
   assaillants et leurs entités sont le monde.
5. **Proposition** : Au portage : les nuits sont celles de `ColonyEvents.NightFell` (C-1) ; les cultures de MC
   deviennent des factions hostiles de Hytale (Trork, Goblin, Outlander, Scarak…), à choisir et à vérifier dans les
   rôles ; les raids par navire (pirates) prennent l'équivalent Hytale le plus proche, ou sont retirés, sur décision.
   Les clés de config de MC arrivent avec le système. Sauvegardes : nouvel état de raid à persister (migration le
   moment venu). Aujourd'hui : rien.
- **Gravité** : incohérent (latent : au portage des raids).

#### C-16. Les gardes

1. **MC** : Gardes (`AbstractJobGuard` : chevalier, archer, druide…) : facteur de bonheur `security`, sommeil à part,
   repas pressé la nuit (`MC/core/entity/ai/minimal/EntityAIEatTask.java:297-298`), plaintes « pas de garde » au
   coucher.
2. **HyColony** : Non portés : `C/job/Job.java:80-86` (`isGuard()` rend toujours `false`). Les points d'accroche
   existent déjà avec les valeurs de MC sans gardes : `C/citizen/happiness/HappinessFactors.java:31-42` (facteur
   `security`), l. 113-116 (`sleptTonight`), `C/citizen/sleep/SleepNotice.java:14`,
   `C/citizen/wander/CitizenWander.java:113`, `C/citizen/food/EatAI.java:24`.
3. **Hytale** : Sans objet tant que le métier n'existe pas. Les armes et armures d'un garde seront des objets Hytale
   (à inventorier au portage).
4. **Verdict** : Conforme : rien de Minecraft n'est recopié.
5. **Proposition** : Au portage des gardes : armes, armures et cibles du monde de Hytale (groupe `HyColony_Hostile`).
   Sauvegardes et config : aucun effet aujourd'hui.
- **Gravité** : cosmétique (rien à faire aujourd'hui).

### Dégâts et mort

#### C-17. Mortalité des citoyens

Déplacée dans « Hors du monde : système relevé en passant » (fin du document) : la mort non portée et
l'invulnérabilité du rôle sont des écarts du système, pas des règles du monde. Ce qu'elle a du monde est dans C-14
(attitude des monstres envers les citoyens), C-18 (échelle de vie), C-19 (étouffement) et C-23 (dégâts d'un blocage
complet).

#### C-18. Échelle de vie : 20 points dans un monde à 100

1. **MC** : `BASE_MAX_HEALTH = 20D` (`MC/api/util/constant/CitizenConstants.java:84`, appliqué
   `MC/api/entity/citizen/AbstractEntityCitizen.java:159`). Un coup est plafonné à 20 % de la vie maximale
   (`handleDamagePerformed`, `EntityCitizen.java:1356-1358` : « so citizens need a certain amount of hits to die »).
   Soin `checkHeal` : 2, 1 ou `saturation / 20 / 2` points toutes les 100 ticks (`EntityCitizen.java:889-910`). Filtre
   `HURT_CITIZEN` : plus de 1 dégât refusé (l. 1331, 1344). Toutes ces valeurs sont sur l'échelle de Minecraft.
2. **HyColony** : `R/Server/NPC/Roles/HyColony/HyColony_Citizen.json:15` (`"MaxHealth": 20`) ;
   `C/kernel/port/body/BodyHealth.java:10-14` (« on MC's scale ») ; `C/citizen/food/HungerTicks.java:53-82`
   (`healAmount` 2 / 1 / `saturation / 20 / 2`) ; `P/npc/body/BodyVitals.java:16-21` (vie 20, régénération de Hytale
   coupée). Le plafond de 20 % n'est pas porté.
3. **Hytale** : La vie d'une entité est sur 100 : `zip:Server/Entity/Stats/Health.json:2-4` (`InitialValue: 100`,
   `Max: 100`). Les dégâts sont à cette échelle (`zip:Server/NPC/Roles/…`) : morsure de `Creature/Mammal/Wolf_Black.json`
   27 (l. 94-101), massue de `Intelligent/Faction/Goblin/Goblin_Scrapper.json` 12, 16 et 10 (l. 18, 47, 76),
   `Undead/Skeleton/Skeleton/Skeleton_Fighter.json` 16 (l. 76), `Intelligent/Faction/Trork/Trork_Warrior.json` 23
   (l. 22). Étouffement 20 et noyade 10 par seconde (`DamageSystems.CanBreathe`, `DamageSystems.java:621-672`) ; sous
   y = 0, 50 par coup (l. 1355-1361).
4. **Verdict** : À adapter : 20 points de vie mesurent le monde Minecraft (celui de ses joueurs). Avec 20 points,
   un loup, un trork ou une seconde d'étouffement tuent un citoyen d'un coup tant que le plafond de 20 % n'est pas
   porté ; avec le plafond, chacun des coups relevés ci-dessus (10 à 27) est ramené à 4, si bien qu'un gobelin et un
   loup tuent en cinq coups chacun. Les soins (1 à 2 points) et le seuil de 1 du filtre sont à la même échelle.
5. **Proposition** : Passer le rôle à `"MaxHealth": 100`, l'échelle des joueurs de Hytale (`Health.json`), et
   multiplier par `maxHealth / 20` (soit 5) les soins de `HungerTicks.healAmount` et le seuil du filtre
   `HURT_CITIZEN`, avec `Deviation from MC (Hytale world): MC's 20 health points → Hytale's 100 (Health.json); heal and
   HURT_CITIZEN threshold scaled by 5`. Garder le plafond de 20 % de MC, qui est relatif, au portage de la mort.
   `BodyHealth` cesse alors de parler de « MC's scale ». Sauvegardes : la vie n'est pas dans la sauvegarde de la
   colonie (`C/app/persistence/CitizenSerializer.java` n'en a pas) ; un corps déjà sauvé par Hytale garde sa valeur
   actuelle et remonte par les soins (**[in-game]**). Pas de migration. Config : aucune (MC n'en a pas).
- **Gravité** : casse le jeu (latent : un coup tuerait un citoyen dès que la mort sera portée).

#### C-19. Étouffement et noyade

1. **MC** : Un dégât `IN_WALL` (étouffement dans un bloc) n'est jamais subi : le citoyen est téléporté hors du mur
   (`EntityCitizen.handleInWallDamage`, `MC/core/entity/citizen/EntityCitizen.java:1285-1291`). La noyade n'a pas de
   règle propre dans ce code.
2. **HyColony** : Rien (recherche de `suffocat` et `IN_WALL` dans `C/` et `P/` : aucune) ; sans effet tant que le
   citoyen est `Invulnerable`.
3. **Hytale** : `DamageCause` `Suffocation` (`zip:Server/Entity/Damage/Suffocation.json`) et `Drowning` ;
   `DamageSystems.CanBreathe` inflige 20 (étouffement) ou 10 (noyade) par seconde une fois l'oxygène épuisé, à toute
   entité qui a un `BreathingComponent` (`HY/server/core/modules/entity/damage/DamageSystems.java:621-672`). Que nos
   corps aient ce composant n'est pas vérifié (**[in-game]**).
4. **Verdict** : À adapter avec la mortalité : sans l'équivalent de `IN_WALL`, un citoyen coincé dans un bloc posé par
   le constructeur mourrait en une seconde (en cinq avec les 100 points de C-18).
5. **Proposition** : Un système de filtre de dégâts (comme `P/npc/CitizenFireImmunitySystems.java`, classe `Guard`)
   qui annule `Suffocation` et téléporte le corps hors du bloc (port `CitizenBodies.teleport`), avec la source MC
   `EntityCitizen.handleInWallDamage`. Laisser la noyade à Hytale, comme MC. Sauvegardes et config : aucun effet.
- **Gravité** : casse le jeu (latent : un citoyen emmuré mourrait en quelques secondes).

#### C-20. Blessure : causes ignorées et soin bloqué après un coup

1. **MC** : Une blessure ajoute le modificateur `DAMAGE`, sauf feu et foudre (`DamageTypeTags.IS_FIRE`,
   `IS_LIGHTNING`, `MC/core/entity/citizen/EntityCitizen.java:1418-1429`). Le soin attend `getLastHurtByMob() == null`
   (`checkHeal`, l. 891). Combien de temps Minecraft se souvient de l'attaquant n'est pas dans `sources/` : la mémoire
   de 100 ticks est une règle de Minecraft vanilla, **non vérifiée**.
2. **HyColony** : `P/npc/CitizenHurtSystem.java:60-75` (causes ignorées de `R/hycolony/id-map.json:85`, `Fire` et
   `Lightning` ; un coup d'une entité seulement, `Damage.EntitySource`, arme la mémoire, l. 70-71) ;
   `P/npc/body/BodyVitals.java:23-24` (`HURT_MEMORY_TICKS = 100`, Javadoc « MC LivingEntity.getLastHurtByMob: an
   attacker is remembered for 100 ticks »), lu par `recentlyHurt` (l. 72-76). C'est une copie de la valeur de
   Minecraft, sans marquage.
3. **Hytale** : Les causes `Fire` et `Lightning` existent (`zip:Server/Entity/Damage/Fire.json`, `Lightning.json`) ;
   `Lava_Burn` passe par `Fire` (`P/npc/CitizenFireImmunitySystems.java:39-42`). Pour un PNJ, Hytale attend 15 s sans
   dégât avant de régénérer : `zip:Server/Entity/Stats/Health.json:9-28` (régénération « NPC », conditions `Alive`,
   `IsPlayer` inversé, `NoDamageTaken` `Delay: 15`, `RegenHealth`). Le délai est en secondes
   (`HY/server/core/modules/entity/condition/NoDamageTakenCondition.java:21`, `Codec.DURATION_SECONDS`) et compté depuis
   le dernier dégât de toute cause (`getLastDamageTime`, l. 35-36) ; que l'horloge comparée soit le temps réel ou celui
   du jeu n'est pas vérifié. HyColony coupe cette régénération au profit des soins de MC (`BodyVitals.java:16-21`).
4. **Verdict** : À adapter : les causes ignorées sont celles de Hytale (conforme), mais le délai qui suit un coup est une
   durée du monde Minecraft (sa mémoire d'attaquant), alors que Hytale fixe la sienne pour ses PNJ : 15 s.
5. **Proposition** : `HURT_MEMORY_TICKS` = 15 s, soit 300 ticks du cœur, avec `Deviation from MC (Hytale world):
   Minecraft's 100-tick attacker memory → Health.json's NoDamageTaken delay (15 s)`. Garder la règle « seul un
   attaquant bloque le soin » de MC (système), ce qui reste plus étroit que la condition de Hytale (toute cause). Le
   délai étant une règle de jeu, il passe du plugin au cœur avec le soin (CLAUDE.md § 1). Sauvegardes : aucun effet (la
   mémoire n'est pas sauvée). Config : aucune (MC n'en a pas).
- **Gravité** : cosmétique (latent) : tant que les citoyens sont invulnérables, aucun coup ne les touche ; ensuite, seul
  le temps avant la reprise des soins change (5 s ou 15 s), sans état bloqué ni mort de plus.

#### C-23. Dégâts d'un blocage complet

1. **MC** : le gestionnaire de blocage des citoyens est créé `withTakeDamageOnStuck(0.2f)`
   (`MC/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:159`) ; sur un blocage complet,
   `completeStuckAction` inflige `getMaxHealth() * damagePct`, soit 20 % de la vie maximale, sous la cause
   `STUCK_DAMAGE` (`MC/core/entity/pathfinding/navigation/PathingStuckHandler.java:299-301`, drapeau l. 664-667).
2. **HyColony** : `C/kernel/nav/StuckHandler.java:29-31` : « MC's citizens also take 20 % of their maximum health on a
   full stuck […]; ours take none », écart marqué `Deviation from MC:` (l. 25). Le corps est de toute façon
   `Invulnerable` (`R/Server/NPC/Roles/HyColony/HyColony_Citizen.json:16`).
3. **Hytale** : la vie est sur 100 (`zip:Server/Entity/Stats/Health.json:2-4`) ; 20 % de la vie maximale reste 20 %
   quelle que soit l'échelle (C-18). Aucune cause de dégât « blocage » : les causes de `zip:Server/Entity/Damage/` sont
   `Bludgeoning`, `Command`, `Crush`, `Drowning`, `Earth`, `Elemental`, `Environment`, `Environmental`, `Fall`, `Fire`,
   `Ice`, `Lightning`, `OutOfWorld`, `Physical`, `Poison`, `Projectile`, `Slashing`, `Suffocation`, `Water`, `Wind`.
4. **Verdict** : conforme pour le monde : la règle est relative à la vie maximale, donc indépendante de l'échelle de
   Hytale. Son absence vient de la mort non portée (C-17, système), pas du monde.
5. **Proposition** : au portage de la mort, infliger 20 % de la vie maximale sur un blocage complet, sous l'équivalent
   Hytale le plus proche de `STUCK_DAMAGE` (`Suffocation` ou `Environment`, à choisir et à vérifier), marqué
   `Deviation from MC (Hytale world): STUCK_DAMAGE → <cause Hytale>`. Corriger dès maintenant le commentaire
   (« ours take none » parce que le corps est invulnérable, C-17). Sauvegardes : aucun effet. Config : aucune (MC code
   le taux en dur).
- **Gravité** : cosmétique (latent, avec C-17).

#### C-21. Immunité au feu

1. **MC** : Un citoyen brûle comme toute entité vivante : rien ne l'en protège, et sa blessure par le feu est même
   traitée à part pour le bonheur (`MC/core/entity/citizen/EntityCitizen.java:1418`, `damageSource.is(DamageTypeTags.IS_FIRE)`
   n'ajoute pas le modificateur `DAMAGE`, mais le dégât a lieu).
2. **HyColony** : `P/npc/CitizenFireImmunitySystems.java:25-32` : écart demandé par l'utilisateur, marqué
   (`Immunity_Fire`, et braises du feu de camp éteint).
3. **Hytale** : `zip:Server/Entity/Effects/Immunity/Immunity_Fire.json:2-10` (`Infinite: true`, `DamageResistance` `Fire`
   100 %, `CalculationType: Percent`) ; gabarit de brûlure `zip:Server/Entity/Effects/Status/Burn_Template.json`, que
   la Javadoc de `CitizenFireImmunitySystems` cite l. 36-43 et 100-105.
4. **Verdict** : Conforme : écart demandé, pas une règle de Minecraft recopiée.
5. **Proposition** : Rien. Sauvegardes et config : aucun effet.
- **Gravité** : cosmétique (rien à faire).

#### C-22. Explosions dans la colonie

1. **MC** : `turnoffexplosionsincolonies` (`DAMAGE_ENTITIES` par défaut, `MC/api/configuration/ServerConfiguration.java:187`)
   protège blocs et entités (`MC/core/colony/permissions/ColonyPermissionEventHandler.java:277-330`).
2. **HyColony** : `C/app/ColonyProtection.java:120-128` (`explosionSparesBlock`) ; `P/block/ExplosionProtectionSystem.java:13-24`
   (écart marqué l. 21-22 : la partie entités n'est pas portée).
3. **Hytale** : `DamageBlockEvent` n'est créé que par `BlockHarvestUtils` (`HY/server/core/modules/interaction/BlockHarvestUtils.java:972`),
   appelé sans entité par `HY/server/core/entity/ExplosionUtils.java` (`processTargetBlocks`).
4. **Verdict** : Conforme : la protection suit les explosions de Hytale.
5. **Proposition** : Rien pour le monde. Sauvegardes : aucun effet. Config : `Permissions.TurnOffExplosionsInColonies`
   reste la clé de MC.
- **Gravité** : cosmétique (rien à faire pour le monde).

### Effets sur les joueurs

#### C-24. Lévitation après dix refus

1. **MC** : quand une colonie refuse une action à un joueur, il n'est prévenu qu'une fois toutes les 10 s ; au-delà de
   10 refus dans cette fenêtre, il reçoit `MobEffects.LEVITATION` pendant `TICKS_SECOND * 10`, et le compte repart à 0
   (`MC/core/colony/permissions/ColonyPermissionEventHandler.java:183-203` : message et remise à 0 l. 184-191, lévitation
   l. 196-201).
2. **HyColony** : `C/colony/permission/DenialNotices.java:8-13` reprend la fenêtre de 10 s (`INTERVAL_TICKS`, l. 17) ;
   écart marqué l. 12-13 : « no levitation […] (Hytale has no levitation effect we could verify) ».
3. **Hytale** : aucun effet de lévitation. Les effets du zip pre.5 (`zip:Server/Entity/Effects/**`) sont des capacités,
   soins, immunités, potions, états, dégâts de test et armes ; aucun ne soulève. Le format ne le permet d'ailleurs pas :
   `ApplicationEffects` n'a que teintes, animation, particules, effet d'écran, `HorizontalSpeedMultiplier`,
   `KnockbackMultiplier`, sons, `ModelVFXId`, `MovementEffects`, `AbilityEffects` et sensibilité de la souris
   (`HY/server/core/asset/type/entityeffect/config/ApplicationEffects.java:24-135`), et `MovementEffects` que des
   interdictions (`DisableAll`, `DisableForward`… `DisableJump`, `DisableCrouch`) et un `SpeedMultiplier`
   (`HY/server/core/asset/modifiers/MovementEffects.java:14-78`) : ni gravité, ni poussée verticale. L'effet le plus
   proche, punitif et de même durée : `zip:Server/Entity/Effects/Status/Root.json` (`Duration: 10`,
   `MovementEffects.DisableAll: true`, `KnockbackMultiplier: 0`) ; `Status/Stun.json` (10 s, tout mouvement et les
   attaques coupés) va plus loin. Une poussée vers le haut existerait par `Velocity.addInstruction`
   (`HY/server/core/modules/physics/component/Velocity.java:103-110`), mais son effet sur un joueur est **[in-game]**.
   Un effet se pose par `EffectControllerComponent.addEffect` (`HY/server/core/entity/effect/EffectControllerComponent.java:85-152`).
4. **Verdict** : à adapter : la lévitation est un effet du monde Minecraft ; Hytale n'en a pas, mais a un équivalent
   proche (immobiliser 10 s), alors que l'écart actuel ne fait rien.
5. **Proposition** : au-delà de 10 refus en 10 s, poser `Root` au joueur pour 10 s (le compte repart à 0, comme MC),
   marqué `Deviation from MC (Hytale world): MobEffects.LEVITATION → Hytale's Root effect (Server/Entity/Effects/Status/Root.json)`.
   Le compte des refus est une règle du système, à porter dans `DenialNotices` ; le port de permissions pose l'effet.
   Sauvegardes : aucun effet (le compte n'est pas sauvé). Config : aucune (MC n'en a pas).
- **Gravité** : cosmétique (une dissuasion contre un joueur qui insiste ; le refus lui-même marche déjà).

### Règles de MC du groupe sans code HyColony

Non portées, donc rien à auditer aujourd'hui ; à classer en système ou monde au moment de leur portage :
- élève qui rentre après midi (`NOON`, `MC/core/entity/ai/workers/CitizenAI.java:244`) : pas d'école ;
- enchanteur avant 6000 (`MC/core/entity/ai/workers/service/EntityAIWorkEnchanter.java:130`) et jour du Nether
  (`MC/core/colony/buildings/workerbuildings/BuildingNetherWorker.java:138,219-221`) : métiers absents ;
- sons de citoyen le jour (`MC/core/entity/citizen/EntityCitizen.java:918`) : pas de sons de citoyen ;
- deuil effacé à l'aube et maladie : non portés (`C/citizen/CitizenAI.java:31`) ;
- pluie sur la terre agricole (`MC/core/blocks/MinecoloniesFarmland.java:150,165`) : relève du groupe culture.

### Bilan du groupe C

| Verdict                | Casse le jeu            | Incohérent                     | Cosmétique                     | Total  |
|------------------------|-------------------------|--------------------------------|--------------------------------|--------|
| Conforme               | 0                       | 0                              | 15                             | 15     |
| À adapter              | 2 (C-18, C-19, latents) | 2 (C-14, C-15, latents)        | 4 (C-4, C-8, C-20, C-24)       | 8      |
| À retirer de HyVanilla | 0                       | 0                              | 0                              | 0      |
| **Total**              | **2**                   | **2**                          | **19**                         | **23** |

Entrées conformes : C-1, C-2, C-3, C-6, C-7, C-9, C-10, C-11, C-12, C-13, C-16, C-21, C-22, C-23, C-25 ; parmi elles,
C-2, C-9, C-10, C-11, C-13 et C-25 demandent de remarquer leur écart en `(Hytale world)`, C-12 de vérifier six rôles.
C-5 est fusionnée dans B-24 ; C-17 est passée dans « Hors du monde » (système). HyVanilla n'ajoute rien dans ce groupe.

Ordre proposé : C-18 (échelle de vie), puis C-19, C-14, C-20 et C-23 avec le portage de la mort (C-17, système), C-15
au portage des raids, enfin les cosmétiques C-4, C-8 et C-24.

## D. Navigation et le reste

Audit du 2026-10-02, groupe D de la spec `docs/superpowers/specs/2026-10-02-hycolony-monde-hytale-design.md` (§ 2 et § 5) : déplacement et blocage des PNJ, échelles, portes, eau, distances, gabarit du corps, hauteur du monde, chunks, sons, particules, animations, défauts de `config.json` qui mesurent le monde.

Sources :
- MineColonies : `sources/minecolonies/src/main/java/com/minecolonies/` (abrégé `MC/` ci-dessous), branche `version/main` ;
- Hytale : décompilé `build/vineflower/hytale-server/com/hypixel/hytale/` (abrégé `H/`) et assets `pre-release-0.7.0-pre.5-Assets.zip`. **Version** : `gradle.properties` épingle `hytale_version = 0.7.0-pre.5` (ligne 19), et non pre.4 comme le dit encore CLAUDE.md : c'est ce zip qui a été lu ;
- HyColony : chemins relatifs à la racine du dépôt.

Hors groupe, renvoyé aux autres : immunité au feu des citoyens (`CitizenFireImmunitySystems`, C-21), dégâts de 20 % de MC sur un blocage complet (C-23), météo par environnement (`HytaleWorldQuery.isRainingAt`, C-9), son du labour (B-1) et miettes du repas (B-22, regroupée avec D-11), places assises des loisirs lues dans les étiquettes du plan (`LeisureWalk.java:22`, plans, C-10). L'étape `CLEAR_WATER` de MC non portée (`Stage.java:14`) est du système : elle est dans « Hors du monde » à la fin du document.

Gravité : pour une entrée *conforme*, elle mesure ce qui reste à faire (en général un marquage `Deviation from MC (Hytale world)`), donc *cosmétique*.

---

#### D-1. Blocs dangereux et détours

1. **MC** : `PathfindingUtils.isDangerous` (`MC/core/entity/pathfinding/PathfindingUtils.java:447-458`) : feu, feu de camp, magma, baies sucrées, neige poudreuse, chaudron de lave et l'étiquette `dangerousBlocks`. Le chemin n'y passe jamais (`AbstractPathJob.isPassable`, `SurfaceType`).
2. **HyColony** : `ItemCatalog.isHarmful` (`core/src/main/java/dev/hycolony/core/kernel/port/ItemCatalog.java:29-33`) ; l'adaptateur le lit dans les données Hytale (`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleItemCatalog.java:180-201` pour les fluides, `:281` pour les blocs : `getDamageToEntities() > 0 || isTrigger()`). `DangerousCells` (`core/.../kernel/nav/DangerousCells.java:9-18`, écart marqué : 1 bloc de marge), `SafeRoute` (`core/.../kernel/nav/SafeRoute.java:6-15`, écart marqué : le cœur trace ses propres détours), `ClearTarget`.
3. **Hytale** : la navigation des PNJ n'évite que `DamageToEntities` (`H/server/npc/movement/controllers/MotionControllerWalk.java:2835`) ; feu, feu de camp et brasero blessent par leur interaction de collision, exposée par `BlockType.isTrigger()` (`H/server/core/asset/type/blocktype/config/BlockType.java:1662`) et `Fluid.isTrigger()` (`H/server/core/asset/type/fluid/Fluid.java:350`).
4. **Verdict** : *conforme*. La liste des blocs dangereux vient déjà des assets Hytale, pas de la liste Minecraft de MC.
5. **Proposition** : réécrire les deux commentaires en `Deviation from MC (Hytale world): MC's isDangerous block list → Hytale's DamageToEntities or collision trigger (BlockType.isTrigger)`. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-2. Hauteur de montée et escalade d'un rebord

1. **MC** : `PathingConstants.MAX_JUMP_HEIGHT = 1.3` (`MC/api/util/constant/PathingConstants.java:44`).
2. **HyColony** : `HyColony_Citizen.json` `MaxClimbHeight: 3` (`plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json:11`, écart marqué l. 2) et `CitizenMantleSystem` qui montre l'escalade de rebord du joueur (`plugin/src/main/java/dev/hycolony/plugin/npc/motion/CitizenMantleSystem.java`, écart marqué).
3. **Hytale** : défaut du contrôleur `Walk` 1,3 (`H/server/npc/movement/controllers/builders/BuilderMotionControllerWalk.java:164-170`) ; le joueur saute 1 à 2 blocs et escalade un rebord de 3 (`builtin/mantling/MantlingPlugin`, animation `Common/Characters/Animations/Mantle/Mantle_Up.blockyanim`), détail dans `plugin-b-api.md` § 39.
4. **Verdict** : *conforme* : la physique suit celle du joueur de Hytale, réglage que le rôle expose.
5. **Proposition** : passer le commentaire du rôle et de `CitizenMantleSystem` au format `Deviation from MC (Hytale world): MAX_JUMP_HEIGHT 1.3 → a Hytale player's climb (3-block mantle, MantlingPlugin)`. Rien d'autre ; ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-3. Hauteur de chute

1. **MC** : `AbstractPathJob.checkDrop` descend jusqu'à 9 blocs : boucle `for (int i = 2; i <= (pathingOptions.canDrop ? 10 : 2); i++)`, retour `y - i + 1` (`MC/core/entity/pathfinding/pathjobs/AbstractPathJob.java:1609-1641`), `canDrop = true` par défaut (`MC/core/entity/pathfinding/PathingOptions.java:125`).
2. **HyColony** : le rôle ne fixe pas `MaxDropHeight` (`HyColony_Citizen.json:7-12`), donc le défaut de Hytale, 3. L'écart n'est consigné que dans `plugin-b-api.md` § 39, **pas dans le code**.
3. **Hytale** : `MaxDropHeight` défaut 3,0 (`H/server/npc/movement/controllers/builders/BuilderMotionControllerWalk.java:344-350`) ; il arrête aussi le mouvement au bord du vide (`plugin-b-api.md` § 39).
4. **Verdict** : *à adapter* (marquage seul) : le comportement suit Hytale, mais l'écart n'est pas marqué.
5. **Proposition** : ajouter au `$Comment` du rôle `Deviation from MC (Hytale world): checkDrop 9 blocks → Hytale's MaxDropHeight default 3`. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-4. Échelles

1. **MC** : les citoyens grimpent aux échelles : nœuds d'échelle de l'A\* (`MC/core/entity/pathfinding/pathjobs/AbstractPathJob.java:867`, `:908-910`, coût `:1099-1101`, départ sur une échelle `:319-321`), sans chute depuis une échelle (`:1611`).
2. **HyColony** : rien. Les plans portés de MC en contiennent : `Furniture_Village_Ladder` apparaît 48 fois dans 12 prefabs de `plugin/src/subplugins/Styles_MedievalOak/Server/Prefabs/MedievalOak/fundamentals/` (hôtel de ville 1 à 3, bâtisseur 3 à 5, résidence 1, 3, 4, 5…). Un étage desservi par une seule échelle n'est atteint que par la téléportation de l'anti-blocage (`StuckHandler`, D-8), après 100 puis 200 ticks sans progrès.
3. **Hytale** : `Furniture_Village_Ladder` a `Material: Solid`, `HitboxType: Ladder`, `MovementSettings.IsClimbable: true` (`Server/Item/Items/Furniture/Village/Furniture_Village_Ladder.json`). `BlockMovementSettings.isClimbable()` (`H/server/core/asset/type/blocktype/config/BlockMovementSettings.java:147`) n'a **aucun appelant** dans le serveur ; seul le paquet client copie le champ (l. 132). Un PNJ ne grimpe donc pas ; le joueur, si (`ClimbSpeed 0.035`, `Server/Entity/MovementConfig/Default.json`). Le contrôleur `Walk` a une animation de montée `Climb` (`H/server/npc/movement/controllers/MotionControllerWalk.java:2910-2915`, `AscentAnimationType`) et le jeu d'animations du joueur a `ClimbUp`/`ClimbDown` (`Server/Item/Animations/Default.json`).
4. **Verdict** : *à adapter*. Hytale n'a pas d'équivalent pour un PNJ, et le système en a besoin : les plans de MC mettent des étages derrière des échelles.
5. **Proposition** : faire grimper le corps comme un joueur de Hytale. Quand une marche s'arrête au pied d'une colonne d'échelle qui mène plus près de la cible, le plugin déplace le corps le long de la colonne, à la vitesse d'échelle du joueur, avec l'animation `Climb`. Une variante plus simple : une téléportation au sommet de la colonne. Les deux sont **[in-game]**. À défaut, marquer l'écart (`Deviation from MC (Hytale world): MC citizens climb ladders → Hytale NPCs cannot (isClimbable unread); the stuck handler teleports`). Ni sauvegarde ni configuration.
- **Gravité** : incohérent (le citoyen finit par arriver, téléporté). Vérifier en jeu qu'un lit à l'étage d'une résidence est atteint **[in-game]**.
- **Suivi (2026-10-03)** : la mécanique d'escalade existe (`CitizenClimbSystem`, essayée par `/hycolony selftest`) ; le passage par les échelles attend la refonte de la recherche de chemin (portage de l'A\* de MC), spec `2026-10-03-hycolony-nage-echelles-design.md` § 5.

#### D-5. Portes, portillons et trappes

1. **MC** : les citoyens ouvrent et ferment portes, portillons et trappes : `EntityAIInteractToggleAble(this, FENCE_TOGGLE, TRAP_TOGGLE, DOOR_TOGGLE)` (`MC/core/entity/citizen/EntityCitizen.java:352`, classe `MC/core/entity/ai/minimal/EntityAIInteractToggleAble.java:34-61`). Le navigateur passe les portes (`setCanPassDoors`, `setCanOpenDoors`, `MC/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:151-155`).
2. **HyColony** : rien dans `kernel/nav` ni dans le plugin (`grep -i door` : seulement les permissions, `colony/permission/BlockUse.java:16`). Les plans en posent : 132 `Furniture_Village_Door` fermées dans les prefabs MedievalOak.
3. **Hytale** : une porte fermée est `Material: Solid`, `HitboxType: Door` (`Server/Item/Items/Furniture/Crude/Furniture_Crude_Door.json`), et ses états `OpenDoorIn`/`OpenDoorOut` ont leur propre boîte (`Door_Open_In`…). Seule l'interaction du joueur les change (`H/server/core/modules/interaction/DoorBlockUtils.java:23-26`, `interaction/config/server/DoorInteraction.java`). Aucun fichier de `H/server/npc/` ne parle de porte : un PNJ de Hytale n'en ouvre jamais.
4. **Verdict** : *à adapter*. Sans équivalent pour un PNJ, alors que le système en a besoin : les huttes ont des portes.
5. **Proposition** : porter `EntityAIInteractToggleAble` dans le cœur (porte, portillon ou trappe dans les 2 blocs devant le corps → ouvrir, refermer derrière), avec un port qui pose l'état `OpenDoorIn`/`OpenDoorOut` du bloc comme `DoorInteraction` (son `InteractionSoundEventId` compris). Que la navigation passe ensuite la porte ouverte est **[in-game]**. Ni sauvegarde ni configuration.
- **Gravité** : incohérent : une hutte fermée ne s'atteint que par la téléportation de l'anti-blocage **[in-game]**.

#### D-6. Eau : nage et cases mouillées

1. **MC** : les citoyens nagent : `setCanFloat(true)`, `setCanSwim(true)` (`MC/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:156-157`) et `EntityAIFloat` (`MC/core/entity/citizen/EntityCitizen.java:351`). Une case de fin dans l'eau coûte 50 de plus (`MC/core/entity/pathfinding/pathjobs/PathJobMoveCloseToXNearY.java:77-84`).
2. **HyColony** :
   - `BlockApproach` recopie la pénalité (`core/src/main/java/dev/hycolony/core/colony/BlockApproach.java:26-27`, `:92`) : une case de fin dans un fluide reste donc possible ;
   - `WorkSpot.fits` accepte des pieds dans 1 bloc de fluide (`core/.../construction/builder/WorkSpot.java:134-144`) ;
   - le rôle a un seul contrôleur `Walk` et ne fixe pas `BreathesInWater` (`HyColony_Citizen.json:5-14`).
3. **Hytale** :
   - un PNJ qui respire l'air (défaut `BreathesInAir` vrai, `BreathesInWater` faux, `H/server/npc/role/builders/BuilderRole.java:729-730`) a une profondeur de contrainte de `min(0.25, yeux × 0.5)` (`H/server/npc/util/WalkFluidDepth.java`, appel `MotionControllerWalk.java:457`) ;
   - une position est valide seulement s'il respire 0,25 bloc au-dessus des pieds (`MotionControllerWalk.java:2865-2878`) ;
   - il n'entre donc pas dans l'eau ; il n'en sort que si la contrainte `WADE` est relâchée, ce qui arrive quand il y est déjà (l. 1680-1684) ;
   - avec `BreathesInWater: true`, il marche sous l'eau (`MovementMode.UNDERWATER_WALK`, l. 64 et 214-221), comme `Server/NPC/Roles/Undead/Skeleton/Risen_Knight.json` ;
   - aucun rôle vanilla n'a à la fois `Walk` et `Dive` (recherche sur `Server/NPC/`).
4. **Verdict** : *à adapter* : la nage de MC est recopiée dans le choix des cases, mais Hytale ne la permet pas.
5. **Proposition**, deux options à trancher :
   - (a) **l'équivalent Hytale le plus proche de la nage** : `BreathesInWater: true` sur le rôle (marche dans et sous l'eau), en gardant la pénalité de 50 ;
   - (b) une case dont les pieds sont dans un fluide n'est jamais une case de fin (`WorkSpot.fits`, `BlockApproach.standable`), et la pénalité disparaît.

   Les deux se marquent `Deviation from MC (Hytale world)`. (a) change le comportement en jeu **[in-game]** : traverser une rivière, ne jamais se noyer, mais le rôle est déjà `Invulnerable`. Ni sauvegarde ni configuration.
- **Gravité** : incohérent : une colonie coupée par une rivière ne se traverse que par téléportation **[in-game]**.
- **Suivi (2026-10-03)** : ni (a) ni (b) ; la nage de MC est portée avec le contrôleur `Dive` de Hytale (rôle `Walk` + `Dive`, bascule par `CitizenSwimSystem`, `RelaxedConstraints` `Wade` et `Breathe`), spec `2026-10-03-hycolony-nage-echelles-design.md` § 4, `plugin-b-api.md` § 52.

#### D-7. Préférence des routes

1. **MC** : un nœud posé sur un bloc de l'étiquette `pathblocks` (briques de pierre, planches, béton, tapis…, `sources/minecolonies/src/datagen/generated/minecolonies/data/minecolonies/tags/blocks/pathblocks.json`) coûte `onPathCost = 1/6` (`MC/core/entity/pathfinding/PathingOptions.java:28`, `AbstractPathJob.java:864`, `:1039-1041`) : les citoyens suivent les routes.
2. **HyColony** : rien ; la navigation de Hytale choisit le chemin.
3. **Hytale** : le coût de l'A\* est la seule distance (`H/server/npc/navigation/AStarBase.java:562-564`, `measureWalkCost` = `waypointDistance`) : aucun coût par bloc.
4. **Verdict** : *à adapter* (marquage seul). Sans recherche de chemin à soi, le cœur ne peut pas favoriser une route (même raison que `BlockApproach.java:17`).
5. **Proposition** : marquer l'omission dans la Javadoc de `BodyWalker` : `Deviation from MC (Hytale world): onPathCost on pathblocks → Hytale's A* costs distance only (AStarBase.measureWalkCost)`. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-8. Anti-blocage

1. **MC** : le gestionnaire des citoyens est `PathingStuckHandler.createStuckHandler().withTakeDamageOnStuck(0.2f).withTeleportSteps(6).withTeleportOnFullStuck()` (`MC/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:159`). Casser des blocs, poser des échelles et des ponts de feuilles y restent **désactivés** : `canBreakBlocks`, `canPlaceLadders` et `canBuildLeafBridges` valent `false` (`MC/core/entity/pathfinding/navigation/PathingStuckHandler.java:86-96`). Constantes `MIN_TP_DELAY 120*20`, `MIN_DIST_FOR_TP 10`, `timePerBlockDistance 200` (l. 45-66).
2. **HyColony** : `StuckHandler` (`core/src/main/java/dev/hycolony/core/kernel/nav/StuckHandler.java:6-49`) reprend ces constantes, en ticks. L'écart marqué (l. 25-31) dit « no … ladders or block breaking », ce qui laisse croire à un écart, alors que MC les désactive aussi pour les citoyens.
3. **Hytale** : la téléportation d'un PNJ est permise. Le rôle est `Invulnerable` (`HyColony_Citizen.json:16`), donc les 20 % de dégâts sont sans objet aujourd'hui (C-23).
4. **Verdict** : *conforme* : ce sont des délais du système, en ticks.
5. **Proposition** : rien pour le monde. La phrase fausse du commentaire (échelles et cassage, que MC désactive aussi) est du système : elle est relevée dans « Hors du monde ». Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-9. Vitesse de marche

1. **MC** : `BASE_MOVEMENT_SPEED = 0.3` (`MC/api/util/constant/CitizenConstants.java:38`, attribut posé `MC/api/entity/citizen/AbstractEntityCitizen.java:160`), multiplié par les facteurs du métier et par `MOVEMENT_SLOWDOWN` quand le citoyen a faim.
2. **HyColony** : `MaxWalkSpeed: 3` (`HyColony_Citizen.json:8`). Les facteurs de MC passent par des effets Hytale (`plugin/src/main/java/dev/hycolony/plugin/npc/body/CitizenSpeed.java:13-21`, écart marqué : paliers de 0,05 ; `BodySpeeds.java:9-16`).
3. **Hytale** : 3,0 est le défaut du contrôleur `Walk` (`H/server/npc/movement/controllers/builders/BuilderMotionControllerWalk.java:95`). Un joueur marche à `BaseSpeed 5.5 × ForwardWalkSpeedMultiplier 0.3` ≈ 1,65 bloc/s et court à 5,5 (`Server/Entity/MovementConfig/Default.json`).
4. **Verdict** : *conforme* : la vitesse de base est celle d'un PNJ de Hytale ; les facteurs relatifs restent ceux de MC (système).
5. **Proposition** : noter dans le `$Comment` du rôle que `MaxWalkSpeed 3` est le défaut de Hytale (`Deviation from MC (Hytale world): MOVEMENT_SPEED 0.3 → Hytale Walk default 3`). Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-10. Gabarit du corps et portées en blocs

1. **MC** : citoyen de 0,6 × 1,8 (`MC/api/util/constant/CitizenConstants.java:92-96`). Portées : `BUILDING_REACH_DIST 4`, `WOKR_IN_BUILDING_DIST 7`, `REACHED_DIST 1.5` (`MC/core/entity/pathfinding/navigation/EntityNavigationUtils.java:20-30`) et `DEFAULT_RANGE_FOR_DELAY 4` (`CitizenConstants.java:161`).
2. **HyColony** :
   - deux blocs libres au-dessus du sol pour se tenir (`BlockApproach.java:113-116`, `WorkSpot.java:134-149`, `ClearTarget.java:62-65`) ;
   - `RouteSearch.BODY_RADIUS 0.35` tiré du modèle Hytale (`core/.../kernel/nav/RouteSearch.java:19-20`) ;
   - les portées de MC recopiées (`BlockApproach.java:22-25`, `BodyWalker.java:21-22`, `BuilderGestures.java:25-26`).
3. **Hytale** : boîte du modèle `Player` de ±0,325 × 1,85 de haut (`Server/Models/Human/Player.json`, `HitBox`) ; `PlayerTestModel_V` en hérite (`Parent: Player`). Un bloc de Hytale mesure un bloc, comme dans Minecraft (même unité de position, `ChunkUtil.SIZE` en blocs).
4. **Verdict** : *conforme* : deux blocs suffisent à un corps de 1,85, et les portées sont des distances du système, en blocs.
5. **Proposition** : aucune. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-11. Hauteur des yeux (miettes du repas)

1. **MC** : les miettes partent de `getEyeHeight()` du citoyen (`MC/core/entity/citizen/EntityCitizen.java:608`, `:639`).
2. **HyColony** : `EYE_HEIGHT = 1.62`, « MC Player eye height » (`core/src/main/java/dev/hycolony/core/citizen/food/Meals.java:16-17`), constante du corps de Minecraft.
3. **Hytale** : `EyeHeight: 1.6` du modèle `Player` (`Server/Models/Human/Player.json`), déjà lu côté plugin par `ModelComponent.getEyeHeight` (`plugin/src/main/java/dev/hycolony/plugin/npc/body/BodyGestures.java:63`).
4. **Verdict** : *à adapter* : une mesure du corps de Minecraft, alors que le modèle Hytale donne la sienne.
5. **Proposition** : passer la hauteur à 1,6 (`Deviation from MC (Hytale world): eye height → Player model EyeHeight 1.6`), ou bien laisser le port d'effets placer les miettes aux yeux du corps. Entrée commune avec le groupe B (nourriture). Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-12. Apparition d'un corps

1. **MC** : `EntityUtils.getSpawnPoint` (`MC/api/util/EntityUtils.java:175-203`, `SCAN_RADIUS 5` l. 48) : recherche en 3D, ±3 niveaux, deux blocs libres sur un bloc praticable.
2. **HyColony** : `CitizenArrival` (`core/src/main/java/dev/hycolony/core/citizen/CitizenArrival.java:16-25`, écart marqué) parcourt les colonnes dans l'ordre de MC et confie chaque colonne à la sonde de Hytale.
3. **Hytale** : `NPCPlugin.spawnNPCWithColumnProbe` (`H/server/npc/NPCPlugin.java:1135`) ne teste qu'une colonne ; `SpawningContext` y cherche l'espace libre à ±16 blocs (`H/server/spawning/SpawningContext.java:56`, `:530`).
4. **Verdict** : *conforme*.
5. **Proposition** : préfixer l'écart `(Hytale world)`. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-13. Cellules de revendication et section `Claims`

1. **MC** : `maxColonySize 20` (1-250), `minColonyDistance 8` (1-200), `initialColonySize 4` (1-15), en chunks Minecraft de 16 blocs (`MC/api/configuration/ServerConfiguration.java:164-166`).
2. **HyColony** : `ClaimCell.SIZE = 16` (`core/src/main/java/dev/hycolony/core/colony/territory/ClaimCell.java:6-8`) ; `ColonyConfig.Claims` en cellules, mêmes défauts et bornes (`core/.../kernel/config/ColonyConfig.java:34-46`, `:128`) ; `config.json` (`plugin/src/main/resources/config.json:7-13`).
3. **Hytale** : chunks de 32 blocs (`H/math/util/ChunkUtil.java:9`, `SIZE = 32`).
4. **Verdict** : *conforme*. Le chunk de Hytale n'est qu'une unité de stockage ; la cellule de 16 blocs garde le territoire de MC en blocs (`pieges-portage.md` § 1.10, spec SP0 § 3.2).
5. **Proposition** : documenter dans `ClaimCell` que c'est un choix du monde : `Deviation from MC (Hytale world): MC chunk (16) → 16-block claim cell, Hytale chunks being 32 (ChunkUtil.SIZE)`. Ni migration ni configuration.
- **Gravité** : cosmétique.

#### D-14. Colonie active : « plus de 40 chunks chargés »

1. **MC** : `Colony.updateState` : ACTIVE si un joueur proche est abonné, ou si `loadedChunks.size() > 40` avec un joueur important en ligne (`MC/core/colony/Colony.java:427`). `loadedChunks` = les chunks de la colonie chargés (`addLoadedChunk`, l. 1860 ; appel `MC/core/util/ChunkDataHelper.java:81`, `:398`). 40 chunks Minecraft font 10 240 blocs².
2. **HyColony** : `ColonyState.of` (`core/src/main/java/dev/hycolony/core/colony/ColonyState.java:12-27`, écart marqué) ne teste que le chunk du centre.
3. **Hytale** : chunks de 32 × 32 (`ChunkUtil.SIZE`), chargés autour des joueurs selon leur distance de vue, en chunks (`Player.getViewRadius`, `plugin-b-api.md` § 48).
4. **Verdict** : *à adapter* : la règle mesure une surface chargée, et le seul chunk du centre (1 024 blocs²) en est dix fois moins.
5. **Proposition** : compter les cellules de 16 de la colonie chargées (`WorldQuery.isLoaded` sur un coin de chaque cellule revendiquée) et exiger `> 40`, la surface de MC. Le parcours est borné par le nombre de cellules, et l'évaluation n'a lieu qu'aux changements d'état. Marquer `Deviation from MC (Hytale world): 40 MC chunks → 40 loaded 16-block claim cells`. Ni sauvegarde ni configuration. Les options futures `forceloadcolony`, `loadtime` et `colonyloadstrictness` (`ServerConfiguration.java:137-139`), en chunks, suivront la même conversion quand elles seront portées.
- **Gravité** : incohérent (une colonie passe ACTIVE plus tôt que dans MC).

#### D-15. Chunks non chargés

1. **MC** : lire un bloc charge son chunk (règle de Minecraft vanilla, hors `sources/`). Le ruban de chantier se retire en lisant chaque case de la colonne, `world.getBlockState(newBlock)` puis `world.removeBlock` (`MC/core/entity/ai/workers/util/ConstructionTapeHelper.java:267-272`, `removeTapeIfNecessary`). Le bâtisseur, lui, n'attend que si la position de l'ordre n'est pas chargée (`MC/core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java:495`, `checkIfCanceled`), et passe alors en `IDLE` (`MC/core/entity/ai/workers/AbstractEntityAIStructure.java:158`).
2. **HyColony** : une case non chargée est attendue, jamais sautée (`pieges-portage.md` § 1.1). Le ruban d'une colonne non chargée reste en place (`core/src/main/java/dev/hycolony/core/construction/tape/ConstructionTape.java:53-60`, écart marqué l. 57). Rattaché : `core/src/main/java/dev/hycolony/core/construction/builder/BuilderAI.java:226-233`, écart marqué l. 227-230 : le bâtisseur attend chaque case non chargée du plan (sinon l'ordre finirait avec des trous) et reste en `BUILDING_STEP`, sans pause, là où MC passe en `IDLE`.
3. **Hytale** : la lecture synchrone d'une section ne charge rien et rend `null` quand le chunk n'est pas chargé (`H/server/core/universe/world/storage/ChunkGrid.java:927-930`, `@Nullable getChunkSectionReferenceAtBlock`) ; le chargement passe par les variantes asynchrones, qui rendent un `CompletableFuture` (l. 932-940). `HytaleSections.section` s'en sert (« Never loads a chunk », `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleSections.java:12-21`), et `HytaleWorldBlocks.isLoaded` lit la section (`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldBlocks.java:68-80`). Voir aussi `pieges-portage.md` § 1.1.
4. **Verdict** : *conforme* : sans chargement à l'accès, attendre la case est l'équivalent Hytale de « lire charge le chunk ». Que le bâtisseur ne prenne pas de pause pendant l'attente est un choix du système, dit dans le commentaire.
5. **Proposition** : préfixer les deux écarts `(Hytale world)` (`ConstructionTape.java:57`, `BuilderAI.java:227`) : `Deviation from MC (Hytale world): Minecraft loads a chunk on access → Hytale's sync section lookup never loads (ChunkGrid.getChunkSectionReferenceAtBlock)`. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-16. Hauteur du monde

1. **MC** : la bordure de colonie se dessine de y 0 à `CHUNK_HEIGHT = 256` (`MC/core/client/render/worldevent/ColonyBorderRenderer.java:31`, `:188-203`).
2. **HyColony** : `ColonyBorder.HEIGHT_BLOCKS = 320` (`core/src/main/java/dev/hycolony/core/app/wand/ColonyBorder.java:20-21`, écart marqué) ; une case hors de [0, 320) compte comme chargée (`HytaleWorldBlocks.java:68-72`), pour qu'aucune attente ne dure toujours.
3. **Hytale** : `ChunkUtil.MIN_Y = 0`, `HEIGHT = 320` (`H/math/util/ChunkUtil.java:22`, `:30`).
4. **Verdict** : *conforme*.
5. **Proposition** : préfixer l'écart de `ColonyBorder` `(Hytale world)`. Ni sauvegarde (la bordure n'est pas sauvée) ni configuration.
- **Gravité** : cosmétique.

#### D-17. Distance de vue de la bordure

1. **MC** : fenêtre dessinée = `max(clientRenderDist - RENDER_DIST_THRESHOLD, 2)` en chunks de 16 (`MC/core/client/render/worldevent/ColonyBorderRenderer.java:29`, `:57`).
2. **HyColony** : `viewCells = getViewRadius() × CELLS_PER_CHUNK (2)` (`plugin/src/main/java/dev/hycolony/plugin/ui/wand/ColonyBorderSystem.java:47-48`, `:154`), puis les seuils de MC en cellules (`ColonyBorder.java:22-25`).
3. **Hytale** : `Player.getViewRadius()` borne la distance de vue du client par le maximum du serveur (`H/server/core/entity/entities/Player.java:672-674`, `Math.clamp(clientViewRadius, 0, getMaxViewRadius())`, défaut 6, l. 147). C'est un nombre de chunks de 32 blocs (`ChunkUtil.SIZE = 32`, `H/math/util/ChunkUtil.java:9`) : le serveur lui-même la convertit en blocs par `getViewRadius() * 2 * 32` (`H/server/core/universe/world/worldmap/markers/user/UserMarkerValidator.java:77`). Voir aussi `plugin-b-api.md` § 48.
4. **Verdict** : *conforme* : la distance de vue de Hytale est convertie en cellules de 16.
5. **Proposition** : aucune, sauf le marquage `(Hytale world)` de `CELLS_PER_CHUNK`. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-18. Distance au point d'apparition du monde

1. **MC** : `maxdistancefromworldspawn 30000` (1000-max), `mindistancefromworldspawn 0` (0-1000), en blocs (`MC/api/configuration/ServerConfiguration.java:167-168`), vérifiées à la fondation (`MC/core/network/messages/server/CreateColonyMessage.java:147`).
2. **HyColony** : `HutActions.spawnDistanceRefusal` (`core/src/main/java/dev/hycolony/core/app/action/HutActions.java:97-120`), point d'apparition lu par `HytaleWorldQuery.spawnPoint` (`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldQuery.java:50-71`, `ISpawnProvider`) ; défauts dans `config.json:11-12`.
3. **Hytale** : aucune bordure du monde dans le serveur (`grep -i worldborder` ne trouve rien dans `H/`) ; le point d'apparition vient du `ISpawnProvider` du monde.
4. **Verdict** : *conforme* : une distance en blocs, choix du serveur.
5. **Proposition** : aucune ; ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-19. Sons et éclats d'un coup, son de pose

1. **MC** : un coup joue le son `getHitSound` du bloc à (v + 1) × 0,125 et p × 0,5, envoie les éclats et balance le bras (`MC/core/util/citizenutils/CitizenItemUtils.java:153-205`, son l. 199). Une pose joue `getPlaceSound` à (v + 1) × 0,5 et p × 0,8 (`MC/core/entity/ai/workers/util/BuildingStructureHandler.java:229`). Portées de 16 blocs (`CitizenConstants.java:72-76`).
2. **HyColony** : `HytaleWorldEffects.blockHit` (`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleWorldEffects.java:86-110`, écart marqué : modificateurs de Hytale et fissures) et `blockPlaced` (l. 112-128) : jeu de sons `BlockSoundSet` du bloc, événements `Hit` et `Build`, et particules `Hit`.
3. **Hytale** : `SoundUtil.playSoundEvent3d` (`H/server/core/universe/world/SoundUtil.java:147-185`), portée fixée par l'événement sonore lui-même ; le coup d'un joueur utilise (1, 1) (`plugin-b-api.md` § 19).
4. **Verdict** : *conforme* : sons, volumes et portées sont ceux de Hytale.
5. **Proposition** : préfixer l'écart `(Hytale world)`. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-20. Sons de réussite et d'échec

1. **MC** : `SoundUtils.playSuccessSound` (`NOTE_BLOCK_BELL`) et `playErrorSound` (`NOTE_BLOCK_DIDGERIDOO`) (`MC/api/util/SoundUtils.java:192-231`), sons vanilla de Minecraft, joués par exemple à la pose d'une hutte refusée (`MC/core/placementhandlers/main/SurvivalHandler.java:96`, `:108`).
2. **HyColony** : aucun. Écarts marqués : `WandPlacement.java:126-127` (un message de discussion à la place) et `CraftingActions.java:20` (« no success or error sound »). Le cœur n'a pas de port de son.
3. **Hytale** : des sons d'interface existent, par exemple `SFX_Incorrect_Tool` (`Server/Audio/SoundEvents/SFX/UI/SFX_Incorrect_Tool.json`) ; aucun son de cloche ni de didgeridoo.
4. **Verdict** : *à adapter* : le son de MC est un son du monde Minecraft, et Hytale a des sons d'interface.
5. **Proposition** : un port `Sounds` (réussite, échec) dont l'adaptateur joue les événements de l'id-map, choisis parmi les sons d'interface de Hytale. Le choix d'un son de réussite reste **[in-game]**. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-21. Voix des citoyens

1. **MC** : les citoyens parlent (`SoundUtils.playRandomSound`, `CITIZEN_SOUND_EVENTS`, `MC/api/util/SoundUtils.java:24`, `:98`), réglage client `enablecitizenvoices`, vrai par défaut (`MC/api/configuration/ClientConfiguration.java:26`), lu par `MC/core/event/ClientEventHandler.java:151` ; inventaire dans `config-inventory.md` § 1.8.
2. **HyColony** : aucune voix.
3. **Hytale** : les PNJ intelligents n'ont que des sons de blessure et de mort (`Server/Audio/SoundEvents/SFX/NPC/Intelligent/Outlander/SFX_Outlander_Hurt.json`, `…/Feran/SFX_Feran_Death.json`), et aucune voix (recherche sur `Server/Audio/SoundEvents/`).
4. **Verdict** : *conforme* : sans voix, comme un PNJ de Hytale. Les sons de MC sont des assets du mod ; les porter relèverait d'une décision de l'utilisateur, pas de cet audit.
5. **Proposition** : aucune ; noter le choix dans `config-inventory.md` quand `enablecitizenvoices` sera traité. Sauvegardes : aucun effet. Config : la clé `enablecitizenvoices` de MC (section `Client`) n'a pas lieu d'être tant qu'il n'y a pas de voix.
- **Gravité** : cosmétique.

#### D-22. Feux d'artifice d'un niveau terminé

1. **MC** : `FireworkUtils.spawnFireworksAtAABBCorners` (`MC/api/util/FireworkUtils.java:41`), appelé par `MC/core/colony/buildings/AbstractSchematicProvider.java:558` : une fusée (entité de Minecraft) à chaque coin du bâtiment.
2. **HyColony** : `HytaleWorldEffects.celebrate` (`HytaleWorldEffects.java:62-84`, écart marqué) joue des systèmes de particules de l'id-map (`plugin/src/main/resources/hycolony/id-map.json:81`) aux coins d'un carré fixe de ±3.
3. **Hytale** : `Server/Particles/Spell/Fireworks/Firework_Mix2|3|4.particlesystem`, envoyés par `ParticleUtil.spawnParticleEffect` à 75 blocs (`H/server/core/universe/world/ParticleUtil.java:24`).
4. **Verdict** : *conforme* pour le monde.
5. **Proposition** : préfixer l'écart `(Hytale world)` pour les particules à la place des fusées. Ni sauvegarde ni configuration. La raison fausse de l'écart (« the core has no building box ») est du système : elle est relevée dans « Hors du monde ».
- **Gravité** : cosmétique.

#### D-23. Particules du sommeil

1. **MC** : `SleepingParticleMessage` à y + 1 du citoyen (`MC/core/entity/ai/minimal/EntityAISleep.java:240`).
2. **HyColony** : `SleepAI` à y + 1 du lit (`core/src/main/java/dev/hycolony/core/citizen/sleep/SleepAI.java:235`), puis `HytaleWorldEffects.sleeping` (l. 211-215) avec `sleepParticle: "Sleepy"` (`id-map.json:83`).
3. **Hytale** : `Server/Particles/NPC/Emotions/Sleepy.particlesystem`, l'émotion « zZz » des PNJ.
4. **Verdict** : *conforme*.
5. **Proposition** : aucune. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-24. Animation de travail selon l'outil

1. **MC** : chaque coup balance le bras (`citizen.swing`, `MC/core/util/citizenutils/CitizenItemUtils.java:162`). Minecraft n'a qu'un seul geste, quel que soit l'outil.
2. **HyColony** : `BodyAnimation.MINE` joue toujours `Pickaxe`/`Mine` (`plugin/src/main/java/dev/hycolony/plugin/npc/body/BodyGestures.java:36-47`), que le bâtisseur tienne une hache ou une pelle (`core/src/main/java/dev/hycolony/core/construction/builder/BuilderBlockWork.java:161-165`). La durée d'un coup, `MINE_ANIMATION_TICKS = 6` (`BuilderGestures.java:20-24`), vient de `Mine.blockyanim`. Rattaché : `core/src/main/java/dev/hycolony/core/construction/builder/BuilderGestures.java:52-59`, écart marqué l. 55-58 : MC balance le bras à chaque tour de l'IA, toutes les 5 ticks (`MC/core/entity/ai/workers/AbstractEntityAIBasic.java:218`, `waitingForSomething` l. 497-507 qui appelle `hitBlockWithToolInHand`) ; les animations de Hytale durent plus longtemps, donc `Build` se joue une fois par bloc et `Mine` n'est relancée qu'une fois la précédente finie.
3. **Hytale** : chaque outil a son jeu d'animations (`PlayerAnimationsId`) :
   - `Hatchet` → `Chop` (`Server/Item/Items/Tool/Hatchet/Tool_Hatchet_Crude.json`) ;
   - `Shovel` → `Dig` ;
   - `Pickaxe` → `Mine`.

   Les jeux sont dans `Server/Item/Animations/Hatchet.json`, `Shovel.json` et `Pickaxe.json`. Un joueur de Hytale joue l'animation de l'objet qu'il tient. `Pickaxe.json:5-10` : `Mine` → `Characters/Animations/Items/Main_Handed/Pickaxe/Attacks/Mine/Mine.blockyanim`, `Speed: 1`, `BlendingDuration: 0.05` ; ce fichier dure 14 images (`"duration": 14`, `Common/Characters/Animations/Items/Main_Handed/Pickaxe/Attacks/Mine/Mine.blockyanim:2`), soit bien plus qu'un balancement de bras de Minecraft (la cadence de 60 images/s que retient `BuilderGestures.java:21-22` n'est pas lue dans le décompilé).
4. **Verdict** : *à adapter* : le geste suit l'objet tenu dans Hytale.
5. **Proposition** : choisir le jeu d'animations du `PlayerAnimationsId` de l'objet tenu (`Pickaxe`/`Mine` à mains nues ou sans jeu connu), et la durée du coup de chaque animation dans son `.blockyanim` (**[in-game]**). Le choix revient au plugin, le cœur garde `MINE`. Le rythme des gestes rattaché ci-dessus suit déjà Hytale : il prend le marquage `(Hytale world)`, et sa durée devient celle de l'animation choisie. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique.

#### D-25. Sans recherche de chemin à soi : cases choisies dans le monde

1. **MC** : chaque marche est un travail de recherche de chemin de MineColonies, qui choisit sa case d'arrivée parmi les cases atteignables : la flânerie par `walkToRandomPos` (`MC/core/entity/ai/minimal/EntityAICitizenWander.java:289`, `PathJobRandomPos`, `MC/core/entity/pathfinding/pathjobs/PathJobRandomPos.java:18`) attend la fin de la navigation (`isDone`, `EntityAICitizenWander.java:295`) ; l'approche d'un bloc par `PathJobMoveCloseToXNearY` et son score de fin (`MC/core/entity/pathfinding/pathjobs/PathJobMoveCloseToXNearY.java:69`) ; l'arrivée n'est comptée qu'une fois la navigation finie (`MC/core/entity/pathfinding/navigation/EntityNavigationUtils.java:67`, puis `REACHED_DIST` l. 78). Le gestionnaire de blocage garantit une fin à toute navigation (`MIN_TP_DELAY = 120 * 20`, `MC/core/entity/pathfinding/navigation/PathingStuckHandler.java:45`, `completeStuckAction` l. 179-191) et téléporte sans vérifier la case.
2. **HyColony** : sept écarts marqués `Deviation from MC:`, sans `(Hytale world)`, tous dus à la navigation de Hytale :
   - `core/src/main/java/dev/hycolony/core/citizen/wander/CitizenWander.java:26-32` (`WANDER_TIMEOUT_TICKS`) : une navigation que nos marcheurs ne suivent pas est attendue 120 s, puis laissée ;
   - `CitizenWander.java:47-52` (`WANDER_TRIES`) et `:165-171` (`wanderTarget`) : sans recherche de chemin, une case tirée à 11 blocs, jusqu'à 10 essais ;
   - `core/src/main/java/dev/hycolony/core/construction/builder/WorkSpot.java:22` : la case de travail est choisie dans le monde et le plan ; `:58-59` : jamais de téléportation sur une case non vérifiée ;
   - (retiré le 2026-10-03) `BodyWalker` comptait `REACHED_DIST` pendant la navigation : il ne compte plus qu'avant la marche, comme MC ;
   - `core/src/main/java/dev/hycolony/core/colony/BlockApproach.java:17-19` : la case près d'un bloc est choisie dans le monde, pas parmi les cases atteignables.
3. **Hytale** : le chemin appartient au mouvement du PNJ. `Seek` est `BuilderBodyMotionFind` (`H/server/npc/NPCPlugin.java:894` ; `plugin-b-api.md` § 40 dit l. 902, numéro périmé), qui garde son A\* dans `protected final PathFollower pathFollower`, sans accesseur (`H/server/npc/corecomponents/movement/BodyMotionFindBase.java:63`) ; l'A\* ne coûte que la distance (`H/server/npc/navigation/AStarBase.java:562-564`). La fin est le test `isGoalReached` du mouvement (`BodyMotionFindBase.java:187`, `:603`) avec sa `StopDistance` (`H/server/npc/corecomponents/movement/builders/BuilderBodyMotionFind.java:75`), pas une liste de cases atteignables que le cœur pourrait lire.
4. **Verdict** : *conforme* : sans recherche de chemin qui lui appartienne, le cœur ne peut que choisir ses cases dans le monde et borner ses attentes ; c'est l'équivalent le plus proche des travaux de chemin de MC.
5. **Proposition** : passer les sept commentaires au format `Deviation from MC (Hytale world): MC's path jobs (PathJobRandomPos, PathJobMoveCloseToXNearY) → Hytale's nav owns the path (BodyMotionFindBase.pathFollower)`. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique (marquage).

#### D-26. Fluide qui revient pendant le nettoyage du chantier

1. **MC** : l'eau est retirée case par case, une fois par couche, par `clearWaterStep` (`sources/structurize/src/main/java/com/ldtteam/structurize/placement/StructurePlacer.java:484-524` : source ramassée, ou `LiquidBlock` remplacé par de l'air), pendant l'étape `CLEAR_WATER` qui vient après les blocs solides (`MC/core/entity/ai/workers/AbstractEntityAIStructure.java:383-385`, ordre des étapes l. 711 et 718).
2. **HyColony** : `core/src/main/java/dev/hycolony/core/construction/builder/BuilderBlockWork.java:181-185`, écart marqué l. 182-183 : pendant `CLEAR`, une case de fluide n'est vidée qu'une fois, car une source voisine peut la remplir à nouveau et boucler dessus ne finirait jamais ; le fluide revenu après `CLEAR` est laissé (`SOLID` l'écrase, les décorations y restent).
3. **Hytale** : une source répand de l'eau qui coule (`zip:Server/Item/Block/Fluids/Water_Source.json:16-18`, `Ticker.SpreadFluid: Water`, `CanDemote: false`) ; l'eau qui coule a 8 niveaux et ne tient que tant qu'une source la nourrit (`zip:Server/Item/Block/Fluids/Water.json:2-7`, `MaxFluidLevel: 8`, `SupportedBy: Water_Source`, `CanDemote: true`). Une case vidée à côté d'une source se remplit donc à nouveau, comme dans Minecraft.
4. **Verdict** : *conforme* : vider chaque case une fois est ce que fait `clearWaterStep`, et le retour du fluide est la physique de Hytale. L'étape `CLEAR_WATER` elle-même, non portée, est du système (« Hors du monde »).
5. **Proposition** : passer l'écart au format `Deviation from MC (Hytale world): a cleared fluid cell may refill from a neighbouring source (Water_Source SpreadFluid) → cleared once per pass`. Ni sauvegarde ni configuration.
- **Gravité** : cosmétique (marquage).

---

### Bilan du groupe D

| Verdict                | Casse le jeu | Incohérent                     | Cosmétique                                                                                   | Total  |
|------------------------|--------------|--------------------------------|----------------------------------------------------------------------------------------------|--------|
| Conforme               | 0            | 0                              | 17 (D-1, D-2, D-8, D-9, D-10, D-12, D-13, D-15 à D-19, D-21 à D-23, D-25, D-26)              | 17     |
| À adapter              | 0            | 4 (D-4, D-5, D-6, D-14)        | 5 (D-3, D-7, D-11, D-20, D-24)                                                               | 9      |
| À retirer de HyVanilla | 0            | 0                              | 0                                                                                            | 0      |
| **Total**              | **0**        | **4**                          | **22**                                                                                       | **26** |

Aucune entrée ne casse le jeu : l'anti-blocage téléporte toujours. HyVanilla n'ajoute rien qui touche la navigation, les sons ou les particules.

Ordre proposé : D-5 et D-4 d'abord, car les plans de MC posent 132 portes fermées et 48 échelles. Ensuite D-6, puis D-14, puis les marquages `(Hytale world)` en un seul lot (D-1, D-2, D-3, D-7, D-9, D-12, D-13, D-15, D-16, D-17, D-19, D-22, D-24 pour son rythme, D-25, D-26), enfin D-11, D-20 et D-24. Aucune entrée ne demande de migration ni de changement de `config.json`.

---

## Hors du monde : système relevé en passant

Ce qui suit est apparu pendant l'audit mais relève du **système** de MineColonies (CLAUDE.md § 6, spec § 2) : une règle de MC non portée ou mal portée, un commentaire faux. Ces points ne comptent pas dans le Bilan ; ils se corrigent comme tout écart du système, en comparant avec MC.

### A-17. Blocs gratuits (feuilles)
1. **MC** : `MC/core/entity/ai/workers/AbstractEntityAIStructure.java:934-940` (`isBlockFree` : eau, feuilles `BlockTags.LEAVES`, placeholder de décoration), appliqué avant toute requête (`:758-761`). La liste `FREE_TO_PLACE_BLOCKS` de `sources/structurize/src/main/java/com/ldtteam/structurize/util/BlockUtils.java:69-76` n'est lue nulle part.
2. **HyColony** : seul un bloc sans objet est gratuit (`core/.../construction/resources/EntryCost.java:33-35`) : les fluides (`plugin/.../adapter/HytaleItemCatalog.java:265-266`, `FLUID` sans objet) et les placeholders. Les feuilles sont demandées. Pas de marquage.
3. **Hytale** : le set `Leaves` existe (`zip:Server/Item/Block/Sets/Leaves.json`, `*Leaves`) et sert à reconnaître une feuille ; une feuille s'obtient aux cisailles (`Gathering.Tools` `Shears`, A-11). Les plans MedievalOak n'ont aucune feuille (relevé des 35 plans).
4. **Verdict** : système non porté : `isBlockFree` est une règle de MC, et le monde ne fait que fournir le moyen de reconnaître une feuille.
5. **Proposition** : porter `isBlockFree` tel quel, la feuille étant reconnue par le set `Leaves`. Ni sauvegarde ni config.
- **Gravité** : cosmétique (aucun plan livré n'a de feuilles).

### B-14. La recette du presse-papiers mange l'outil de construction
1. **MC** : `DefaultRecipeProvider.java:614-622` (7 bâtons, cuir, outil) ; l'outil revient (`sources/structurize/.../items/ItemBuildTool.java:53-70`, `getCraftingRemainingItem`).
2. **HyColony** : `plugin/src/main/resources/Server/Item/Items/HyColony/HyColony_Clipboard.json:16-26` prend `HyColony_Build_Tool` en entrée **sans** `Output` ; les lunettes, elles, le rendent (`HyColony_Build_Goggles.json:23-26`).
3. **Hytale** : sans `Output`, la recette d'un objet ne sort que l'objet lui-même (`HY/server/core/asset/type/item/config/Item.java:1291-1302`) : l'outil est consommé. Le banc (`Workbench`) est déjà celui de Hytale (B-13).
4. **Verdict** : système mal porté : rendre l'outil est une règle de MC (et de Structurize), que l'asset oublie.
5. **Proposition** : ajouter `Output` = `HyColony_Clipboard` + `HyColony_Build_Tool`, comme les lunettes. Ni sauvegarde ni config.
- **Gravité** : incohérent (le joueur perd son outil de construction).

### C-17. Mortalité des citoyens
1. **MC** : un citoyen se blesse et meurt (`EntityCitizen.hurt`, `MC/core/entity/citizen/EntityCitizen.java:1235-1270` ; `die`, l. 1548 et suivantes, déroulé dans `docs/research/citizen-death.md` § 1). Un joueur sans `HURT_CITIZEN` ne fait pas plus de 1 dégât (l. 1304-1344).
2. **HyColony** : le rôle est `Invulnerable` (`plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json:16`), sans `Deviation from MC` ; la mort n'est pas portée (aucun `onBodyDied` dans `core/` ni `plugin/`) ; le filtre `HURT_CITIZEN` non plus (l'action existe, `core/.../colony/permission/Action.java:27`). La blessure et les soins sont prêts mais ne servent pas (`plugin/.../npc/CitizenHurtSystem.java:23-29`, `docs/BACKLOG.md:139`).
3. **Hytale** : `Invulnerable` (`HY/server/npc/role/builders/BuilderRole.java:719`) fait annuler tout dégât par `DamageSystems.FilterUnkillable` (`HY/server/core/modules/entity/damage/DamageSystems.java:1134`). Seul `OutOfWorldDamage` passe outre : sous y = −32, il pose directement `DeathComponent` (l. 1317, 1354-1361) ; le corps est alors refait par `updateBodyIfNecessary` (`citizen-death.md` § 2.4, **[in-game]**). La mort se lit par `DeathComponent` et les objets tombent par `ItemComponent.generateItemDrops` (`citizen-death.md` § 2.1-2.2) ; durée de vie au sol `ItemEntity.Lifetime: 600.0` (`zip:Server/GameplayConfigs/Default.json:17-18`).
4. **Verdict** : système non porté. La mort et le filtre `HURT_CITIZEN` sont des règles de MC ; l'invulnérabilité est un écart du système (rien du monde de Hytale ne l'impose). Ce que la mort touche du monde est audité à part : C-14 (attitude des monstres), C-18 (échelle de vie), C-19 (étouffement), C-20 (soin après un coup), C-23 (dégâts d'un blocage).
5. **Proposition** : porter la mort selon `citizen-death.md` § 3, après C-18 et avec C-14, C-19, C-20 et C-23, avec le filtre `HURT_CITIZEN` (seuil de C-18). D'ici là, marquer l'invulnérabilité dans le rôle (`$Comment` : `Deviation from MC: citizens are invulnerable until citizen death is ported`). Sauvegardes : une mort retire le citoyen (le chemin de suppression existe déjà) ; pas de migration. Config : aucune nouvelle clé (MC n'en a pas pour la mort).
- **Gravité** : incohérent (ni monstres, ni chutes, ni lave ne comptent).

### Autres points
- **Étape `CLEAR_WATER` non portée.** MC vide l'eau du chantier dans une étape à part, après les blocs solides (`MC/core/entity/ai/workers/AbstractEntityAIStructure.java:383-385`, ordre des étapes l. 711 et 718), par `clearWaterStep` (`sources/structurize/src/main/java/com/ldtteam/structurize/placement/StructurePlacer.java:484-524`). HyColony ne la porte pas (`core/.../construction/workorder/Stage.java:14`, écart marqué) ; le fluide revenu après `CLEAR` reste (D-26). À porter comme système.
- **Commentaire faux de `StuckHandler`.** `core/.../kernel/nav/StuckHandler.java:25-31` présente « no … ladders or block breaking » comme un écart, alors que MC les désactive aussi pour les citoyens (`canBreakBlocks`, `canPlaceLadders`, `canBuildLeafBridges` à `false`, `MC/core/entity/pathfinding/navigation/PathingStuckHandler.java:86-96`). La phrase sur les 20 % de dégâts (« ours take none », l. 29-31) doit dire que c'est l'invulnérabilité qui les empêche (C-23, C-17).
- **Commentaire faux de `HytaleWorldEffects.celebrate`.** `plugin/.../adapter/HytaleWorldEffects.java:62-66` justifie ses coins fixes par « the core has no building box », alors que `HutFootprint.of` existe (`core/.../colony/HutFootprint.java:26`, déjà utilisé par `BlockApproach.java:106`). Les coins du vrai bâtiment seraient plus fidèles à MC (`FireworkUtils.spawnFireworksAtAABBCorners`, D-22).
- **Commentaire périmé de `RecipeConversion`.** `plugin/.../crafting/RecipeConversion.java:30-31` dit « 28 recipes in 0.6.8 » ; en pre.5, 10 recettes listent deux bancs `Crafting` (recompte sur `zip:Server/Item`, B-11).
- **Règle de jeu dans le plugin.** `HURT_MEMORY_TICKS` vit dans `plugin/.../npc/body/BodyVitals.java:24`, alors qu'une décision de jeu dans un plugin est un bug (CLAUDE.md § 1) ; à déplacer dans le cœur avec C-20.
- **Version.** La version Hytale écrite dans CLAUDE.md, les agents et la skill `hytale-api` (pre.4) n'est plus celle de `gradle.properties:19` (pre.5).
- **Numéro périmé dans `plugin-b-api.md` § 40.** `Seek` est enregistré `NPCPlugin.java:894` en pre.5, et non l. 902.
- **Revue complète des écarts marqués.** `grep -rn "Deviation from"` sur `core/src/main`, `plugin/src/main` et `vanilla` (commit `db3541a3`) donne 295 lignes, dont 286 « Deviation from MC » (277 dans les seuls `.java`) ; les autres (Structurize, CLAUDE.md, « vanilla Hytale », `.ui`) ne cachent aucune règle du monde. Chaque écart dû au monde a maintenant une entrée ou un rattachement. Les autres sont du système, ou des contraintes de la plateforme qui ne sont pas des règles du monde au sens de la spec § 2 : pas d'icône au-dessus d'un PNJ (`core/.../colony/CitizenNameplates.java:20`), pas de niveaux d'opérateur (`core/.../colony/ColonyAccess.java:30`), pas de cache de profils (`core/.../app/action/PermissionActions.java:36`, `plugin/.../adapter/HytalePlayerDirectory.java:62`), pas de rappel par case d'inventaire (`core/.../app/action/CitizenInventoryActions.java:56`), pas de configuration côté client (`core/.../kernel/config/ColonyConfig.java:85`), formes de débogage (`plugin/.../ui/wand/BorderShapes.java:21`), case cliquée absente de `PlaceBlockEvent` (`core/.../app/wand/HutHandPlacement.java:47`), touches et interface (`plugin/.../ui/wand/WandPage.java:30`, `WandInteraction.java:74`), compte d'un `ItemIcon` (`plugin/.../ui/request/RequestTree.java:111`), rendu des lunettes (`core/.../app/goggles/BuildGoggles.java:21`).

