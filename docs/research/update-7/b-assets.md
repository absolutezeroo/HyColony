# Update 7 (0.7.0-pre.4) : axe B, pack d'assets, identifiants et schémas JSON

Recherche du 2026-09-29. Compare les assets et le serveur décompilé **0.6.8** (épinglé) avec **0.7.0-pre.4**, pour tout ce que HyColony, HyBlockUI et HyDomum livrent ou référencent.

## Sources et méthode

Abréviations :

- `Z6` : `%APPDATA%/Hytale/install/release/package/game/latest/Assets.zip` (60 695 entrées). Octet pour octet le même fichier que `~/.gradle/caches/hytale-assets/release-0.6.8-Assets.zip` (3 476 312 291 octets).
- `Z7` : `%APPDATA%/Hytale/install/pre-release/package/game/latest/Assets.zip` (65 575 entrées). Le jar décompilé porte `Implementation-Version: 0.7.0-pre.4` (`META-INF/MANIFEST.MF`).
- `HY6` : `build/vineflower/hytale-server/com/hypixel/hytale/`.
- `HY7` : sources U7 décompilées (scratchpad `u7/src/com/hypixel/hytale/`).
- `C6`, `C7` : `…/game/latest/Client/Data/` des deux installations.
- `PN` : notes de patch pre-release U7 (parties 1 à 4).

Contrôles effectués :

1. **Identifiants.** Chaque mot de nos fichiers (`plugin/src/main`, `plugin/src/subplugins`, `blockui/src/main`, `domum/plugin/src/main`, `domum/core/src/main` : 867 fichiers `.json`, `.java`, `.ui`, `.lang`) qui est le nom d'un fichier d'asset de Z6 a été cherché dans Z7. Pour chacun, on compare la liste de ses chemins `Server/…` entre les deux versions.
2. **Chemins.** Les 4 538 chemins cités (`.png`, `.blockymodel`, `.blockyanim`, `.ogg`, `.ui`, `.json`), variante `@2x` comprise, ont été cherchés dans Z7.
3. **Contenu.** Les 734 assets vanilla référencés ont été comparés en JSON normalisé entre Z6 et Z7.
4. **Codecs.** Les clés `KeyedCodec("…")` de toutes les classes de HY6 et HY7 ont été comparées. Un diff normalisé (sans Javadoc ni casts du décompilateur) a été fait sur les classes des types d'assets que nous livrons.

## Bloquant

**Aucun identifiant, chemin ou champ de schéma que nous utilisons n'a disparu en U7.**

- Contrôle 1 : aucun identifiant n'a perdu tous ses fichiers.
- Contrôle 2 : 0 chemin sur 4 538 a disparu.
- Contrôle 4 : aucune clé de codec d'un type que nous livrons n'est retirée ni devenue obligatoire.

Le seul point qui se voit au démarrage n'empêche pas le chargement : c'est la plage de version des manifests (voir « À migrer »).

## À migrer

### 1. `ServerVersion` des trois manifests : les mods seront signalés « obsolètes » (non bloquant)

Fichiers concernés :

- `plugin/src/main/resources/manifest.json:23` ;
- `blockui/src/main/resources/manifest.json:12` ;
- `domum/plugin/src/main/resources/manifest.json:12` ;
- la propriété `manifestServerVersion` de `gradle.properties:68`.

Tous valent `">=0.6.8 <0.7.0"`.

- **Ce que fait le serveur.** `PluginManifest.checkServerVersionCompatibility` (`HY7 common/plugin/PluginManifest.java:133-149`) appelle `SemverRange.satisfies`. Numériquement, `0.7.0-pre.4 < 0.7.0` est vrai, mais `matchesPreReleaseRule` (`HY7 common/semver/SemverRange.java:44-62`) refuse ensuite une version pre-release : elle n'est acceptée que si un comparateur porte lui-même une pre-release du même `major.minor.patch`. Résultat : `INCOMPATIBLE`.
- **La conséquence.** Le serveur journalise un WARNING par mod (`HY7 server/core/plugin/PluginManager.java:440-476`, et `AssetModule.java:128-170` pour les packs), puis un SEVERE « One or more asset packs are targeting an older server version ». Il envoie aussi le message rouge `server.assetModule.outOfDatePacks` à chaque joueur qui a `MODS_OUTDATED_NOTIFY` (`AssetModule.java:172-196`). **Le mod se charge quand même.** Cette logique est la même qu'en 0.6.8.
- **Les sous-plugins.** Les packs internes (Decorations, Styles_*) recopient la plage de leur propriétaire (`plugin/src/main/java/dev/hycolony/plugin/subplugin/PackAssets.java:69`). Corriger le manifest de HyColony suffit pour eux.
- **Correctif proposé.** `">=0.7.0-pre.4 <0.8.0"`. Elle accepte les pre-releases 0.7.0 (le comparateur porte une pre-release) et la 0.7.0 finale. On peut aussi définir la propriété système `hytale.allow_outdated_mods`, qui ne coupe que le SEVERE et la notification.
- **Dépendances.** `"Hytale:AssetModule": "*"` et `"Hytale:NPC": "*"` restent valides : une plage `*` n'a aucun comparateur, donc `satisfies` renvoie vrai (`SemverRange.java:20-28`). Le module `NPC` existe toujours (`manifests.json` du jar U7 : `Hytale NPC 1.0.0`), et `AssetModule` est toujours un `corePlugin` (`HY7 server/core/asset/AssetModule.java:72`).

### 2. Générateurs de `tools/` pointés sur l'archive 0.6.8

- `tools/decorations/generate.py:21` et `tools/domum/tags.py:18` lisent `release-0.6.8-Assets.zip`. Ils sont à relancer sur Z7 pour que les assets dérivés suivent U7.
- **Pots de fleurs** (`plugin/src/subplugins/Decorations/Common/Blocks/HyColony/Flower_Pot/**`, `Atlas.png`). 8 sources vanilla ont changé de CRC entre Z6 et Z7 :
  - `Common/Blocks/Foliage/Flowers/Flower.blockymodel`, utilisé par `Plant_Flower_Common_Blue`, `_Cyan2`, `_Grey` et `_Yellow` ;
  - `Flower_Textures/Cyan.png` ;
  - `Plants/Boomshroom_Small.blockymodel` ;
  - `Plants/Boomshroom_Large_Texture.png` ;
  - `Plants/Mushroom_Pear_Texture.png`.

  Nos pots gardent l'ancienne forme et l'ancienne texture. L'écart est **cosmétique**. Les textures de laine, d'argile et de terre (`Cloth_*`, `Clay_Smooth_Orange`, `Soil_Dirt_Wet`) sont identiques.
- **HyDomum.** `tags.py` régénère la liste des matériaux. U7 ajoute des blocs cubes taggés `Type=Metal` : `Metal_Orbis_Copper` (« Enkindled Copper », fr « Cuivre embrasé »), `Metal_Orbis_Iron` et `Metal_Goblin_Iron`. Il ajoute aussi `Rock_Kenolite` (`Parent: Rock_Stone`) et le bois `Wood_Sadwillow_*`. Voir « Opportunités ».

### 3. Nouveau type de récolte `Metals` (et `GoblinMetal`) absent de notre table des outils

- **Chez nous.** `HytaleItemCatalog.toolType` (`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleItemCatalog.java:228-239`) associe `Rocks`, `VolcanicRocks` et `Ore*` à la pioche, `Woods` et `SoftWoods` à la hache, `Soils` à la pelle. Tout autre type renvoie `null`.
- **En U7.** Z7 ajoute `Server/Item/Unarmed/Gathering/Metals.json` (`Power 0.001`). 58 objets l'emploient : tout le set `Metal_Goblin_Iron_*` et `Throwable_Explosive_Barrel`. 13 objets emploient `GoblinMetal`, dont `Deco_Scrap_Pile_Shiny`. Les pioches reçoivent une entrée `Metals` (`IsIncorrect: true`, puissance 0.001) et une entrée `GoblinMetal` (0.34), par exemple `Z7 Server/Item/Items/Tool/Pickaxe/Tool_Pickaxe_Iron.json`.
- **Conséquence.** Un plan qui contiendrait du fer gobelin serait cassé par le bâtisseur **sans outil requis**, alors que MC exige la pioche pour un bloc de métal. Aucun des 38 prefabs vanilla de nos styles, ni de nos prefabs de fermier, n'en contient. L'impact est donc nul aujourd'hui, mais il faut ajouter `Metals` et `GoblinMetal` à la branche pioche.
- **Dureté.** `Metals` a un `Power` à mains nues de 0.001 : `hardness()` (l. 216-226) donnerait la dureté maximale bornée. **[in-game]** À valider si un tel bloc apparaît un jour dans un plan.

### 4. Recherche SP4 (sommeil) à corriger : le lit n'a plus d'interaction en ligne

`docs/research/sp4-sleep-home.md:189` indique `Interactions.Use = [{"Type": "Bed"}]`. En U7, chaque lit pointe sur une RootInteraction partagée :

- les lits déclarent `"Use": "Block_Bed"`, par exemple `Z7 Server/Item/Items/…/Furniture_Crude_Bed.json`, `Furniture_Lumberjack_Bed.json` et `Furniture_Kweebec_Bed.json` ;
- `Z7 Server/Item/RootInteractions/Block/Block_Bed.json` vaut `{"Interactions":[{"Type":"Bed"}],"Tags":{"Type":["Bed"]}}` (PN partie 4, « Every bed now shares one Block_Bed root interaction ») ;
- 16 objets le référencent.

La détection recommandée par la recherche SP4 (`BlockType.getBeds() != null`) ne dépend pas de ce champ et reste valable. Tout code qui chercherait un `BedInteraction` en ligne dans `Interactions.Use` échouerait. Aucun code HyColony ne le fait aujourd'hui : un grep de `bed` dans `plugin/src/main/java` ne trouve rien.

## Changement de comportement

### Récoltes et drops

- **La végétation posée par un joueur ne se rend plus elle-même** (PN partie 4, « UseDefaultDropWhenPlaced is gone from 45 foliage assets »). La comparaison Z6/Z7 trouve **47 fichiers** qui perdent `"UseDefaultDropWhenPlaced": true` :
  - `Deco_SpiderWeb`, `_Flat` et `_Full` ;
  - `Plant_Bush` ;
  - `Plant_Crop_Berry_Block`, `_Wet_Block` et `_Winter_Block` ;
  - `Plant_Grass_Lush` ;
  - `Plant_Leaves_Oak`, `_Palm_Arid`, `_Palm_Oasis`, `_Autumn_Floor`, `_Jungle_Floor` et `_Poisoned_Floor` ;
  - 25 `Plant_Moss_*` ;
  - `Plant_Vine` ;
  - 6 `Plant_Reeds_*` ;
  - `Plant_Seaweed_Grass`.

  Le moteur ne s'en sert qu'à deux endroits :
  1. `BlockHarvestUtils` (`HY7 server/core/modules/interaction/BlockHarvestUtils.java:963-969`) : si le bloc est marqué déco, la liste de drops est vidée, donc c'est l'objet lui-même qui tombe.
  2. `BlockType.canBePlacedAsDeco` (`HY7 …/blocktype/config/BlockType.java:1876-1878`), appelé par `BlockPlaceUtils` (`HY7 …/interaction/BlockPlaceUtils.java:438-440`), qui marque « déco » un bloc posé **par un joueur**. Un bloc déco est ignoré par la physique (`HY7 builtin/blockphysics/BlockPhysicsSystems.java:231-239`).

  **Impact sur HyColony : aucun changement.**
  - `HytaleBlocks.drops` (`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleBlocks.java:83-96`) lit `Breaking`, puis `Soft`, puis l'objet du bloc, sans jamais tenir compte du marquage déco. Nos ouvriers recevaient donc déjà la liste de drops normale.
  - Notre bâtisseur pose par `BlockOperations.setBlock` (`HytaleWorldBlocks.java:139`) et ne marque jamais un bloc déco. Ses feuilles et ses mousses étaient déjà soumises à la physique en 0.6.8.
  - U7 aligne donc la vanilla sur notre comportement.
  - La végétation des plans : 1 794 `Plant_Leaves_Oak`, 176 `Plant_Moss_Rug_Green`, 8 `Plant_Vine`… dans les prefabs des styles, et 18 `Plant_Moss_Rug_Green` dans nos prefabs de fermier. Elle reste obtenable comme l'indique `prefab-obtainability.md` : la cisaille (`Tools: [{"Type":"Shears"}]`) est présente dans les deux versions, et la mousse tapis se fabrique.
  - Aucune des 121 plantes des pots n'est dans la liste des 47.
- **`IsWeaponBreakable: false`** est ajouté au `Soft` des buissons, fleurs, mousses et champignons (PN « Foliage … can no longer be broken with weapons », diff Z6/Z7 de `Plant_Bush`, `Plant_Flower_Orchid_Blue` et `Plant_Crop_Mushroom_Cap_*`). Sans effet chez nous : la casse de nos ouvriers ne passe pas par le contrôle d'arme de `BlockHarvestUtils`.
- **Cisaille.** `Plant_Leaves_Autumn_Floor`, `_Jungle_Floor` et `_Poisoned_Floor` gagnent une entrée `Shears` (diff Z6/Z7). Aucun de nos prefabs ni aucun prefab de style n'en contient.

### Cultures et pluie

- **Nos assets de culture sont inchangés.** Le JSON est identique entre Z6 et Z7 pour toutes les graines et cultures de l'id-map (`Plant_Seeds_*`, `Plant_Crop_*_Block` et leurs variantes `_Eternal`), ainsi que pour `Soil_Dirt_Tilled`, `Tool_Hoe_*`, `Tool_Fertilizer`, `Bench_Farming` et `Bench_WorkBench`. Les codecs `FarmingData`, `FarmingStageData` et `GrowthModifierAsset` ont un diff normalisé vide.
- **`WaterGrowthModifierAsset`** (`HY7 builtin/adventure/farming/config/modifiers/WaterGrowthModifierAsset.java:204-218`) décide maintenant de l'exposition à la pluie par `HeightmapColumn.getHeight(x, z) <= worldY`, au lieu de chercher un bloc non vide au-dessus jusqu'à y=320 (`HY6` même classe). Un bloc non opaque au-dessus d'une culture ne la prive plus de pluie (PN partie 4). Nos champs ne portent pas cette règle : la croissance reste celle de la vanilla (`FarmingBlock`). Un toit transparent au-dessus d'un champ laisse désormais passer la pluie. **[in-game]**
- **Pluie pour les ouvriers.** Les systèmes de particules de météo sont identiques. La liste `precipitationParticles` (`hycolony/id-map.json:39`) reste complète : Z7 ne crée aucun nouveau système de précipitation, seulement `Goblin_Void_Anomaly(_Storm)` et d'autres particules d'ambiance.

### Sons, effets et divers

- **`SFX_Hoe_T1_Till`** (`id-map.json:115`) perd `"Volume": 6` et `MinVolume` : le son de labour de nos fermiers sera plus faible. `SFX_Chest_Wooden_Open` (nos huttes) passe à `"Volume": -2`. Les portes `SFX_Door_Desert_*` (HyDomum) changent aussi de volume. Nos appels `SoundUtil.playSoundEvent3d` ne passent pas de multiplicateur de volume (`HytaleWorldEffects.java:104,131`), donc le correctif U7 des multiplicateurs supérieurs à 1.0 ne nous touche pas.
- **Effets de vitesse `HyColony_Speed_*`** (`Server/Entity/Effects/HyColony/*.json`, `ApplicationEffects.HorizontalSpeedMultiplier`) : le codec est inchangé. PN partie 4 : « Adjustments to the horizontal movement speed from entity effects are now applied when in the air, swimming, climbing, or rolling ». Nos citoyens accélérés le sont donc aussi en l'air et en nageant. **[in-game]**
- **`Survival_Trap_Snapjaw`** étourdit maintenant 2 s (`Parent: Stun`, au lieu de 0.2 s de vitesse nulle). Son `Locale` reste `snapjaw` : le filtre de `CitizenFireImmunitySystems.Guard` (l. 110-118) reste correct.
- **`Soil_Mud`** (labourable, `id-map.json`) : `HorizontalSpeedMultiplier` passe de 0.7 à 1, avec `Drag` 0.94. Le déplacement dans la boue change légèrement, sans effet sur le labour.
- **`Stats/Mana`** : `InitialValue` et `Max` passent de 0 à 100. `Mana` n'est chez nous qu'une icône de compétence (`id-map.json`).
- **Set d'or retexturé.** `Rock_Gold_Brick*` a maintenant 2 ou 3 textures pondérées (`Side02`, `Side03`…). HyDomum prend `faces[0].getNorth()` (`domum/…/runtime/MaterialCatalog.java:57-66`), qui reste la texture principale. Ses PNG en cache portent l'empreinte de la texture source (`VariantAssets.java:99-120`) et se redessinent seuls.
- **Langue française côté client.** C6 n'a pas `Shared/Language/fr-FR` (langues : en-US, pt-BR, ru-RU, uk-UA, zh-CN). C7 l'ajoute (`meta.lang` : `name = Français`), et Z7 ajoute `Server/Languages/fr-FR/server.lang` (8 861 lignes). En 0.6.8, un joueur ne pouvait donc pas choisir le français : **nos fichiers `fr-FR/*.lang` s'afficheront pour la première fois en jeu**. **[in-game]** Relire leur rendu. Le serveur résout la langue par `I18nModule.resolveLanguage` (`HY7 …/i18n/I18nModule.java:365-375`) : la langue exacte, sinon `fallback.lang` (`fr-CA`, `fr-BE`, `fr-CH`, `fr-LU` et `fr-MC` vers `fr-FR`, identique en Z6), sinon `en-US`.
- **Monde par défaut.** `GameplayConfigs/Default.json` active `Wilderness.Enabled` et ajoute des événements mondiaux globaux gobelins (`Goblin_Initial_Breach_Event`, `Goblin_Breach_Event`). Wilderness ne fait que du suivi : aucun consommateur hors de `builtin/adventure/wilderness`. Une brèche peut survenir près d'une colonie, comme n'importe où. **[in-game]**

## Opportunités

Chaque ligne donne l'équivalent MineColonies servi.

- **Marqueur de carte plus grand** (MC : le surlignage de bloc de « Locate » et du scroll de ressources, qui montre où aller). `MapMarkerBuilder.withIconSize(MapMarkerIconSize)` et `withCompassImage(String)` sont nouveaux (`HY7 …/worldmap/markers/MapMarkerBuilder.java`, diff normalisé). `Major` s'affiche en 64 px (PN partie 4). Notre `HighlightMarkers.java:29` crée un `MapMarkerBuilder(KEY, "Coordinate.png", …)`, dont l'icône existe toujours (`Z7 Common/UI/WorldMap/MapMarkers/Coordinate.png`) : ajouter `withIconSize(Major)` et une image de boussole rendrait le surlignage plus visible. **[in-game]** Pour les marqueurs de bloc, `IconSize` et `CompassIcon` existent aussi sur `BlockMapMarker` et `BlockMapMarkersResource` (contrôle 4 des codecs), si un jour la mairie a un marqueur permanent (MC : la mairie sur la carte).
- **Barre de vie cachée au-dessus des citoyens** (MC : un citoyen n'affiche que son nom, sans barre de vie). La nouvelle clé de rôle `HiddenUIComponents` (`HY7 server/npc/role/builders/BuilderRole.java:286`, `getHiddenUIComponents` l. 1119) prend des identifiants `Server/Entity/UI/*`, par exemple `["Healthbar"]`. À ajouter à `plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json`, dont le citoyen est `Invulnerable: true`. **[in-game]**
- **Textures d'emplacement d'ingrédient désormais vanilla.** Z7 ajoute `Common/UI/Custom/Common/IngredientSlot@2x.png` et `IngredientSlotValid@2x.png`, identiques octet pour octet à nos copies (`blockui/…/HyBlockUI/Native/IngredientSlot@2x.png` md5 `344645e8`, `IngredientSlotValid@2x.png` md5 `de64066d`). `domum/…/Pages/HyDomum/Cutter.ui:81,93,99,111` pourrait pointer sur la vanilla et supprimer deux copies de HyBlockUI. Les 15 autres copies de `Native/` restent absentes de Z7 : elles viennent de C6/C7 `Game/Interface/…`, où elles sont inchangées (md5 identiques).
- **Nouveaux matériaux pour HyDomum et le bûcheron** (MC Domum Ornamentum accepte tout bloc, et le bûcheron de MC gère chaque essence) :
  - `Metal_Orbis_Copper` et `Metal_Orbis_Iron` (sets complets : Decorative, Ornate, Smooth, Half, Roof…) ;
  - `Metal_Goblin_Iron` ;
  - `Rock_Kenolite` (four) ;
  - l'essence `Sadwillow` : `Wood_Sadwillow_Trunk(_Full, _Half, _Stairs)`, `Plant_Leaves_Sadwillow`, `Plant_Sapling_Sadwillow` et `Plant_Seeds_Sadwillow`.

  Les métaux n'ont **aucune recette** (`Recipe` absent) : ils ne s'obtiennent pas en survie. Les tests `Glass_Test_*` (`Tags.Type: ["Editor"]`) ne sont pas un vrai verre : `tools/domum/tags.py:29`, qui note l'absence de verre en 0.6.8, reste vrai.
- **Styles « runiques » dans `Common.ui`** (`Z7 Common/UI/Custom/Common.ui`, ajout uniquement) : `@RunicContainer`, `@RunicPanel`, `@RunicTitleStyle`, `@RunicHeadlineStyle` et `@RunicBodyStyle`. Aucun équivalent MC : utile seulement pour un habillage de fenêtre, aucun besoin identifié.
- **Page `BlockLore`** (`"Page": {"Id": "BlockLore"}` sous `Interactions.Use`, `HY7 …/pages/BlockLorePage.java`, `Z7 Common/UI/Custom/Pages/BlockLorePage.ui`) : elle affiche le nom et la description d'un bloc. Nos huttes et le champ ont déjà leur propre `Use` : pas d'usage MC direct.
- **`StayOpenWhenEmpty`** (`ItemContainerBlock`) : à ne **pas** activer sur les huttes, dont la fermeture est voulue. Cité pour mémoire.

## Vérifié sans impact

### Identifiants et chemins

- **id-map et crafting de HyColony** (`plugin/src/main/resources/hycolony/id-map.json`, `crafting.json`) : tous présents en Z7.
  - Seuls six identifiants ont un contenu changé : `Rock_Gold_Brick_Wall` (icône), `Soil_Mud`, `SFX_Hoe_T1_Till`, `Mana`, `Stamina` et `Weapon_Daggers_Iron` (`DurabilityLossOnHit` 0.1 → 0.06, icône de compétence seulement). Leurs effets sont décrits plus haut.
  - Les états `*Rock_Stone_Brick_Stairs_State_Definitions_Corner_Right` et `*Furniture_Crude_Door_State_Definitions_OpenDoorIn` (l. 23-24) sont inchangés : `Rock_Stone_Brick_Stairs`, `Furniture_Crude_Door` et `Furniture_Crude_Chest_Small` ont un JSON identique.
  - Les recettes générées `Plant_Seeds_*_Recipe_Generated_0` sont stables : graines inchangées, codec `Item` seulement enrichi de `Ability`.
- **`styles.json`** : le fichier principal vaut `{}`. Les styles vivent dans `plugin/src/subplugins/Styles_Kweebec` et `Styles_Outlander`, qui citent **38 prefabs vanilla, tous de CRC identique** entre Z6 et Z7. Tous leurs blocs existent en Z7 (hors l'entrée spéciale `Empty`). Leurs marqueurs d'apparition (`Kweebec_Merchant`, `Kweebec`, `Kweebec_*_Patrol`, `Outlander_*`) existent toujours.
- **Prefabs de fermier** (`plugin/src/main/resources/Server/Prefabs/HyColony/Farmer/*.prefab.json` et `.lpf`) :
  - les 76 identifiants de blocs, états compris, et les 111 chaînes des `.lpf` existent en Z7 ;
  - `BlockSpawnerId` `Zone1_Kweebec_Tier1`, `Zone3_*` : le fichier passe de `Server/Item/Block/Spawners/New/` à `Server/Item/Block/Spawners/`, et l'identifiant reste le nom de fichier ;
  - `SelectionPrefabSerializer.VERSION = 8` et `BinaryPrefabBufferCodec.VERSION = 21` dans les deux versions ;
  - aucune nouvelle migration de bloc (`Server/Item/Block/Migrations/0..10.json`, liste identique) : notre `blockIdVersion: 11` reste au-delà de la dernière.
- **Identifiants codés en dur en Java** : aucun n'a disparu. Cela couvre `Immunity_Fire`, `Farmingbench`, `Block_Spawner_Block`, `Tool_Fertilizer`, `Soil_Dirt_Tilled`, les types de récolte, et les animations `Block/Build`, `Pickaxe/Mine`, `Hoe/Till` et `Item/SwingRight`, qui existent dans les deux versions. `Server/Item/Animations/Block.json` ne gagne que `HeavyThrow`, et `Item.json` les animations de consommation.
- **Pack HyColony** :
  - huttes : `CustomModel` et textures `Blocks/Decorative_Sets/*/Chest_Small*`, animations `Blocks/Animations/Chest/*` ;
  - hitbox `Bench_Architect` et `Scarecrow` ;
  - `ISS_*`, jeux de sons, particules et matériaux `Wood` et `Stone` ;
  - `ResourceTypes` `Wood_Trunk`, `Wood_All` et `Rock` ;
  - récoltes à mains nues `Woods` et `Benches` ;
  - modèle `PlayerTestModel_V` ;
  - effet `Drop_Legendary` : son ModelVFX n'a pas de `Parent`, donc le changement `appendInherited` des ModelVFX ne le touche pas.

  Tous ces fichiers sont identiques en Z7.
- **HyDomum** :
  - `hydomum/id-map.json` : seul le set d'or change ;
  - modèles `CustomConnectedBlockTemplates` (`DoorConnectedBlockTemplate`, `WallConnectedBlockTemplate`), `Half_Block`, hitbox `Door*` et `Stairs*`, RootInteraction `Door` : identiques ;
  - codecs des blocs connectés : diff normalisé vide, hors bruit du décompilateur.
- **UI** :
  - les 18 styles `$C.@…` que nous utilisons existent toujours. `Common.ui` n'a que des ajouts, et `Sounds.ui` est identique ;
  - aucune propriété `.ui` employée par la vanilla en 0.6.8 n'a disparu en U7. Les 9 propriétés de nos `.ui` absentes de la vanilla (`DurabilityBar*`, `DisplayItemQuantity`…) l'étaient déjà en 0.6.8. **[in-game]** Le client U7 accepte toujours ces propriétés : ce n'est vérifiable qu'en jeu, faute de schéma UI dans C7 ;
  - `Coordinate.png`, `Tab@2x.png`, `InputIconKey*_White@2x.png` et `Sounds/ButtonsLightHover.ogg` sont présents.

### Schémas et codecs

- **Codecs des types que nous livrons : aucune clé retirée ni rendue obligatoire.** Nouveautés facultatives seulement :
  - `Item` (+`Ability`) ;
  - `ItemContainerBlock` (+`StayOpenWhenEmpty`) ;
  - `EntityEffect` (+`OutgoingDamage`) ;
  - `ItemEntityConfig` (+`MergeRadius`) ;
  - `ItemGlider` (+`Dive*`), que nous n'utilisons pas.

  Diff normalisé vide pour :
  - `BlockType` (79 clés), `BlockGathering`, `BlockBreakingDropType`, `SoftBlockDropType`, `HarvestingDropType` ;
  - `ItemDropList`, `CraftingRecipe`, `Bench`, `ItemTranslationProperties`, `ItemCategory`, `ResourceType` ;
  - `ItemArmor` (cast seulement), `ApplicationEffects` ;
  - `SimpleInteraction`, `OpenCustomUIInteraction`, `EquipItemInteraction` ;
  - `ModelAsset`, `ItemSoundSet`, `BlockSoundSet`, `BlockParticleSet`, `BlockBoundingBoxes` ;
  - `ItemPlayerAnimations`, `PluginManifest`.
- **`RootInteraction`** : les tags partent au client développés (`getExpandedTagIndexes`). Nos deux RootInteractions n'ont pas de tags.
- **`UseBlockInteraction`** (`HY7 …/client/UseBlockInteraction.java:67-89`) : consulte la nouvelle liste `WorldConfig.BlockedRootInteractionTags` avant d'exécuter le `Use` d'un bloc. Liste vide par défaut, et nos RootInteractions n'ont pas de tags, donc elles restent autorisées.
- **Enum `InteractionType`** : `Ability4` est inséré avant `Use`, ce qui décale les ordinaux. Nous ne l'utilisons que par nom (`InteractionType.Use`), et le JSON se lit par nom.
- **Rôle PNJ `HyColony_Citizen`** :
  - la clé `Class` n'est obligatoire que pour les rencontres. Un rôle sans `Class` vaut `Role` (`HY7 server/npc/asset/builder/BuilderManager.java:415,434-446`) ;
  - `BuilderRole` garde `Appearance`, `MaxHealth`, `Invulnerable`, `Instructions`, `NameTranslationKey` et `MotionControllerList` ;
  - `Walk` garde `MaxWalkSpeed`, `Gravity`, `MaxFallSpeed` et `Acceleration`, désormais en « holders » qui acceptent toujours une valeur littérale (`BuilderMotionControllerWalk`). Les rôles vanilla U7 écrivent encore des nombres littéraux, par exemple `"Gravity": 10` dans `Z7 Server/NPC/Roles/Creature/Hunt/Template_Bear_Voidtaken_Tentacle.json` ;
  - `Seek` (= `BuilderBodyMotionFind`, `HY7 server/npc/NPCPlugin.java:893`) a un diff vide. Nous n'utilisons pas `ChargeAcceleration`.
- **Rencontres, audio (`Duration {Ms}`, `DefaultSyncTo`, `VolumeDb`), planeur, `ChargeAcceleration`, objectif de carte au trésor, `Spectre_Void` et `Remobed_Block_Set`** : nous ne livrons aucun de ces assets et n'en référençons aucun. Contrôles 1 et 2 : aucun de ces identifiants n'apparaît dans nos fichiers.

### Langues

- **Chargement des langues** : `I18nModule` garde le préfixe tiré du nom de fichier (`hycolony.lang` donne `hycolony.*`, `getPrefix` : diff normalisé vide). Le changement porte sur l'envoi groupé (`queueTranslations`) et `resolveLanguage`. Format des `.lang` et des paramètres `{p0}` inchangé.
- **Vocabulaire français** : les noms officiels fr-FR sont désormais disponibles, par exemple `items.Bench_Farming.name = Établi du fermier`, `items.Tool_Shears_Basic.name = Cisailles rudimentaires`, `items.Tool_Hoe_Crude.name = Houe rudimentaire` et `items.Tool_Fertilizer.name = Sac d’engrais` (`Z7 Server/Languages/fr-FR/server.lang`). Hytale emploie l’apostrophe typographique (’), nos `.lang` l’apostrophe droite ('). Point de cohérence, sans rien de cassé.

### Taille et outils

- **Protocole** : un asset est plafonné à 256 Mio (PN partie 4). Notre plus gros fichier fait 330 Ko (`Flower_Pot/Atlas.png`).
- **Outils des ouvriers** : pour chaque `Tool_Pickaxe_*`, `Tool_Hatchet_*`, `Tool_Shovel_*`, `Tool_Hoe_*` et `Tool_Shears_*`, les specs `Rocks`, `Woods`, `Soils` et `SoftBlocks` (puissance, qualité), `MaxDurability`, `DurabilityLossOnHit` et `DurabilityLossBlockTypes` sont identiques entre Z6 et Z7. Les niveaux et vitesses que calcule `HytaleItemCatalog.computeItem` (l. 256-293) ne bougent pas. Seules s'ajoutent les entrées `Metals` et `GoblinMetal` (voir « À migrer » 3).

## Reste incertain

- Le rendu en jeu des propriétés `.ui` propres à HyBlockUI sous le client U7. Le client ne publie pas de schéma UI : seul un test en jeu tranche.
- L'effet visuel exact de `HiddenUIComponents` et de `withIconSize(Major)` sur nos entités et marqueurs.
- L'affichage des `fr-FR/*.lang` pour un joueur francophone : première exposition réelle.
