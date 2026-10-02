# Citoyens hors de la bordure et apparition des monstres

Recherche du 2026-10-02, à la demande : « éviter que les citoyens sortent de la bordure et que les monstres
apparaissent, car sur Hytale il n'y a pas de système de luminosité ».

Sources : MineColonies `version/main` copié dans `sources/minecolonies/` (chemins relatifs à
`src/main/java/com/minecolonies/`) ; serveur Hytale décompilé `build/vineflower/hytale-server/com/hypixel/hytale/`
(chemins relatifs à `server/`). `gradle.properties` épingle aujourd'hui **0.7.0-pre.5** (l. 19) ; les sources
décompilées datent du 2026-10-01 20:38, après le téléchargement du jar pre.5 (20:18). Assets lus dans
`pre-release-0.7.0-pre.5-Assets.zip`.

## 1. MineColonies : les citoyens restent-ils dans le territoire ?

**Non. MC ne borne jamais les déplacements d'un citoyen au territoire revendiqué.**

- Flânerie : `core/entity/ai/minimal/EntityAICitizenWander.java:259-291` (`decide`, toutes les 100 ticks, l. 82).
  5 % (`LEISURE_CHANCE`, l. 38) : un lieu de loisir au hasard (`getRandomLeisureSite`), sinon la maison, sinon le
  centre de la colonie (l. 269-279), où il flâne, s'assoit ou lit (l. 134-257). Sinon :
  `EntityNavigationUtils.walkToRandomPos(citizen, 10, speed)` (l. 289).
- Ce pas aléatoire part de la **position courante** : `MinecoloniesAdvancedPathNavigate.walkToRandomPos`
  (`core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:187-204`) crée un `PathJobRandomPos`
  sans boîte de restriction, dont la destination est `BlockPosUtil.getRandomPosAround(start, 10)`
  (`core/entity/pathfinding/pathjobs/PathJobRandomPos.java:61-73`). `isAtDestination` (l. 199-219) ne teste la boîte
  `restrictionBox` que si un constructeur à coins l'a posée ; seule la flânerie *au lieu de loisir* en passe une (les
  coins du bâtiment, `EntityAICitizenWander.java:169`). Aucune vérification de colonie ou de chunk revendiqué.
- `isCoordInColony` (`core/colony/Colony.java:1298-1308`, propriétaire du chunk) n'est appelé, côté citoyens, que
  pour la tombe à la mort (`core/entity/citizen/EntityCitizen.java:1574`) et pour les points de passage du proxy de
  marche (`core/entity/pathfinding/proxy/GeneralEntityWalkToProxy.java:36`). Aucun `restrictTo` sur un citoyen (le seul
  appel vise les chevaux du maître d'écurie, `EntityAIWorkStablemaster.java:594,691`).
- Aucun retour forcé : les seules téléportations d'un citoyen sont l'anti-blocage du chemin
  (`MinecoloniesAdvancedPathNavigate.java:159`, `PathingStuckHandler.java:283-295,391-395`) et la sortie d'un mur
  (`EntityCitizen.java:1287-1290`). Ce qui ramène un citoyen, c'est la branche loisir ci-dessus, le travail et le
  sommeil, pas une règle de territoire.
- Le travail peut sortir du territoire : le bûcheron cherche des arbres dans `SEARCH_RANGE = 50` (élargi jusqu'à
  `SEARCH_LIMIT`) autour de sa hutte, sauf zone restreinte choisie par le joueur (`BuildingLumberjack.RESTRICT`)
  (`core/entity/ai/workers/production/EntityAIWorkLumberjack.java:72,404-441`) ; les gardes ont un mode `FOLLOW`
  (`core/colony/buildings/modules/settings/GuardTaskSetting.java:33`).

Chez nous : `core/.../citizen/CitizenWander.java` porte le pas de 10 blocs, mais **sans la branche loisir**
(Javadoc de `wander()` : « no leisure branch yet »). C'est elle qui, chez MC, ramène 5 % des décisions vers un
bâtiment, la maison ou le centre ; sans elle, notre flânerie est une marche aléatoire libre qui dérive. (État au
2026-10-02 avant le portage : la branche loisir et le bornage au territoire sont depuis dans `citizen/wander/`,
spec `2026-10-02-hycolony-colony-bounds-design.md`.)

## 2. MineColonies : empêche-t-il l'apparition des monstres ?

**Seulement à l'intérieur des bâtiments construits, pas dans tout le territoire.**

- `core/event/EventHandler.java:424-459`, `MobSpawnEvent.PositionCheck` : pour une entité `Enemy`, hors
  `MobSpawnType.SPAWNER` et chunk chargé, si le chunk appartient à une colonie (`ColonyUtils.getOwningColony`), pour
  chaque bâtiment qui revendique ce chunk (`ColonyUtils.getAllClaimingBuildings`, `api/util/ColonyUtils.java:168-172`)
  de niveau ≥ 1 dont la boîte contient la position (`isInBuilding`), le résultat est `DENY`.
- `isInBuilding` (`core/colony/buildings/AbstractSchematicProvider.java:505-520`) : les coins du plan (plus l'extension
  `IAltersBuildingFootprint`), élargis de 1 bloc sur les trois axes.
- Aucune option de configuration ne règle cela (le refus est en dur). Les options de `api/configuration/ServerConfiguration.java`
  qui touchent aux monstres concernent les raids : `enablecolonyraids` (true, l. 172), `raidDifficulty`, `maxRaiders`
  (80), `raidersbreakblocks` (true), `averagenumberofnightsbetweenraids` (14), `minimumnumberofnightsbetweenraids` (10),
  `raidersbreakdoors` (true), `skyraiders` (false, l. 142), et `mobattackcitizens` (true, l. 178), qui ajoute aux
  monstres un but d'attaque des citoyens (`EventHandler.java:133-147`).
- Hors des bâtiments, MC s'en remet aux règles vanilla de Minecraft (lumière) et aux gardes. La règle de lumière
  vanilla n'est pas dans `sources/` : non vérifiée ici.

## 3. Hytale : la lumière est-elle une condition d'apparition ?

**Oui, mais seulement si l'asset d'apparition la déclare, et la plupart des monstres de surface ne la déclarent pas.**

- `NPCSpawn` a un champ optionnel `LightRanges` (`spawning/assets/spawns/config/NPCSpawn.java:64-82`), clés
  `Light`, `SkyLight`, `Sunlight`, `RedLight`, `GreenLight`, `BlueLight` (`spawning/assets/spawns/LightType.java`), en
  pourcentage 0-100. Absent : `FULL_LIGHT_RANGE` (l. 143, 223-231), donc aucun test.
- Le test : `SpawnWrapper.withinLightRange` (`spawning/wrappers/SpawnWrapper.java:108-120`) via `LightRangePredicate`
  (`spawning/util/LightRangePredicate.java:151-201` ; `Light` = max(lumière de bloc, ciel × facteur solaire), l. 227-232 ;
  une plage 0-100 complète n'est pas testée, l. 242-244). Appelé par l'apparition du monde
  (`spawning/world/system/WorldSpawnJobSystems.java:177`, rejet `OUTSIDE_LIGHT_RANGE`) et des balises
  (`spawning/util/FloodFillPositionSelector.java:658`).
- Assets pre.5 : **11 apparitions du monde sur 98** déclarent `LightRanges` (les 10 `Server/NPC/Spawn/World/Void/Tier*_Night_*`,
  par ex. `Light` 0-8, et `Zone2/Spawns_Zone2_Dungeon_Scarak.json`) ; **55 balises sur 96** (grottes, par ex.
  `Beacons/Zone1/Zone1_Cave_Tier1/Zone1_Cave_Plains_Aggro.json`, `Light` 0-2). Les autres sont filtrées par
  environnement (`Environments`), heure (`DayTimeRange`), phase de lune et bloc d'apparition : par exemple
  `World/Zone1/Wander_Zone1_Tier1.json` (squelettes, `DayTimeRange` 0-24, sans lumière),
  `Spawns_Zone1_Forests_Predator.json` (ours, araignées, 6-18 h), `Spawns_Zone1_Plains_Predator.json` (loups, 6-24 h).
- Conclusion : l'affirmation est vraie en pratique pour la surface (éclairer n'empêche pas squelettes, loups,
  araignées), fausse au sens strict (le moteur sait tester la lumière, et les larves du Vide la nuit ou les monstres
  des grottes y obéissent).
- Interrupteur global par monde : `WorldConfig.IsSpawningNPC` (`core/universe/world/WorldConfig.java:152,275`), lu par
  `WorldSpawningSystem.java:62`, `SpawnJobSystem.java:25`, `SpawnControllerSystem.java:15`. Tout le monde, pas une zone.

## 4. Hytale : bloquer l'apparition naturelle dans une zone

### 4.1 Pas d'événement annulable

Aucun événement d'apparition annulable. Les seuls `*Event` liés aux PNJ sont `LoadedNPCEvent` (chargement d'un rôle,
`spawning/LoadedNPCEvent.java`), `AllNPCsLoadedEvent` et des capteurs de rôle ; les événements ECS annulables du
serveur sont `RespawnEvent`, `UseEntityEvent`, `Damage`, `KillFeedEvent`, `PrefabPlaceEntityEvent`.

### 4.2 La suppression d'apparition (mécanisme natif, utilisable par un plugin)

- Asset `SpawnSuppression` (`spawning/assets/spawnsuppression/SpawnSuppression.java:20-67`), chargé depuis
  `NPC/Spawn/Suppression` (`spawning/SpawningPlugin.java:222-232`) : `SuppressionRadius` (défaut 10, > 0),
  `SuppressedGroups` (ids de `NPCGroup` ; absent = **tous** les rôles, `ChunkSuppressionEntry.SuppressionSpan.includesRole`,
  l. 88-89), `SuppressSpawnMarkers`. Vanilla : `Spawn_Camp.json` (45, `Aggressive`/`Passive`/`Neutral`, marqueurs
  coupés), `Trork_Tier_3.json`, `Test.json`. Le groupe `LivingWorld/Aggressive.json` inclut `Trork`, `Goblin`,
  `Skeleton`, `Void`, `Zombie`, `Vermin`, `Predators`, `PredatorsBig`.
- Activation : une entité portant `SpawnSuppressionComponent` (id de l'asset) + `TransformComponent` +
  `UUIDComponent`, ajoutée avec `AddReason.SPAWN` (`spawning/suppression/system/SpawnSuppressionSystems.java:293-360`).
  Modèle vanilla : la commande `/spawning suppression add` (`spawning/commands/SpawnSuppressionCommand.java:85-103`)
  ajoute aussi `HiddenFromAdventurePlayers`, un modèle de marqueur et un `Nameplate`. `store.addEntity` : thread du monde.
- Effet (`SpawnSuppressionSystems.suppressSpawns`, l. 63-146) : chaque **chunk Hytale (32 × 32, `>> 5`)** touché même
  en partie par le rayon reçoit en X/Z une plage Y `position.y ± rayon`. Le grain est donc le chunk de 32, pas la
  cellule de 16 de la colonie (`ClaimCell.SIZE = 16`), et rayon horizontal et hauteur sont liés : un petit rayon
  (au centre d'un chunk, < 16) ne couvre qu'un chunk mais seulement ± rayon en hauteur ; plusieurs suppresseurs
  empilés en Y peuvent élargir la hauteur.
- Respectée par l'apparition du monde (`WorldSpawnJobSystems.java:112-125`, `SuppressionSpanHelper`,
  `SpawningContext.setEnvironmentColumn`) et par les balises (`FloodFillPositionSelector.java:232-238`) ; les
  marqueurs dans le rayon sont coupés si `SuppressSpawnMarkers` (l. 128-144). Non consultée par
  `NPCPlugin.spawnNPC*`/`spawnEntity` : nos citoyens ne sont pas concernés.
- Persistance : la ressource `SpawnSuppressionController` sauvegarde `SpawnSuppressorMap` (`SpawnSuppressionController.java:19-28`)
  et la recharge au démarrage (`SpawnSuppressionSystems.Load.onSystemAddedToStore`, l. 190-205). Retrait : seulement
  sur `RemoveReason.REMOVE` de l'entité (l. 362-447), pas au déchargement.
- Limites : ne retire pas les monstres déjà là ni ceux qui entrent en marchant. **[in-game]** : chargement d'un asset
  `SpawnSuppression` depuis le pack d'un mod, rendu côté client d'un suppresseur sans modèle.

### 4.3 Retrait après coup (repli)

Un PNJ du monde porte l'index de sa configuration d'apparition (`WorldSpawnJobSystems.preAddToWorld`, l. 333-342 ;
`NPCEntity.getSpawnConfiguration()`, `npc/entities/NPCEntity.java:423`, `Integer.MIN_VALUE` sinon, l. 124). Un
`RefSystem` du plugin sur `NPCEntity` pourrait retirer, à l'ajout, ceux qui tombent dans une cellule revendiquée.
Thread du monde (système ECS). Le PNJ est créé puis retiré dans le même tick : **[in-game]** pour un éventuel
clignotement et pour les membres de meute (`FlockPlugin.trySpawnFlock`, l. 296-307).

## 5. Hytale : restreindre un PNJ à une zone

- Point d'attache : `NPCEntity.leashPoint` (`npc/entities/NPCEntity.java:86-100,133-136,307-342`), posé à l'apparition
  par `NPCPlugin.spawnEntity` (`npc/NPCPlugin.java:1506`), modifiable (`setLeashPoint`) ou par l'action
  `SetLeashPosition` (`npc/corecomponents/world/ActionSetLeashPosition.java`).
- Capteur `Leash` : vrai quand le PNJ est à plus de `Range` du point d'attache, et fournit ce point comme cible
  (`npc/corecomponents/world/SensorLeash.java:28-45`, `BuilderSensorLeash.java:47`).
- Corps `WanderInCircle` (`Radius`, défaut 10) et `WanderInRect` (`Width`/`Depth`, défaut 10) flânent autour du point
  d'attache (`BodyMotionWanderInCircle.java:101`, `BodyMotionWanderInRect.java:44-55`, `BuilderBodyMotionWanderInCircle.java:42`).
  Un cercle ou un rectangle, pas une forme de cellules.
- Notre rôle `plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json` n'a qu'une instruction
  `HyColonyTarget` + `HyColonySeek` : la cible vient du cœur (`CitizenWander`), donc ni le point d'attache ni les corps
  `Wander*` ne jouent. La restriction se décide dans le cœur, au choix de la cible.

## 6. Retirer après l'apparition : hostilité, crochet, origine (2026-10-02)

Choix de l'utilisateur : aucun monstre hostile né naturellement dans les cellules revendiquées (écart à MC, qui ne
refuse que dans les bâtiments, § 2), en retirant le PNJ après son apparition (cellules exactes de 16, au lieu des
chunks de 32 de `SpawnSuppression`).

### 6.1 Reconnaître un PNJ hostile

- **Par groupe (`NPCGroup`)**. Index du groupe : `NPCGroup.getAssetMap().getIndex("Aggressive")`
  (`builtin/tagset/config/NPCGroup.java:68-70` ; `IndexedLookupTableAssetMap.getIndex` rend `Integer.MIN_VALUE` pour
  une clé inconnue, `assetstore/map/IndexedLookupTableAssetMap.java:30-47`). L'id est le nom du fichier
  (`LivingWorld/Aggressive.json` → `"Aggressive"`, comme dans `Spawn_Camp.json`). Test :
  `TagSetPlugin.get(NPCGroup.class).tagInSet(group, npc.getRoleIndex())` (`builtin/tagset/TagSetPlugin.java:57-80` ;
  lève `IllegalArgumentException` si le groupe n'existe pas, l. 72-77 ; `getSet` rend `null`, l. 82-84). Raccourci
  vanilla : `WorldSupport.hasTagInGroup(group, roleIndex)` (`npc/role/support/WorldSupport.java:287-289`). Même motif
  dans `UseCaptureCrateInteraction.java:125-131`.
- Les ensembles sont aplatis à partir des index de rôles (`NPCPlugin.putNPCGroups`, `npc/NPCPlugin.java:1380-1393`,
  avec `builderManager.getNameToIndexMap()`). `IncludeRoles`/`ExcludeRoles` acceptent des motifs glob
  (`builtin/tagset/TagSetLookupTable.java:108-113`, `StringUtil.isGlobMatching`), et `IncludeGroups`/`ExcludeGroups`
  d'autres groupes (`NPCGroup.java:23-46`). La suppression d'apparition fait le même calcul (`SpawnSuppressionSystems.java:80-88`
  puis `SuppressionSpan.includesRole`).
- **Couverture de `Aggressive`** (calcul glob sur les rôles cités par les 98 apparitions du monde, 96 balises et
  marqueurs du zip pre.5) : couvre squelettes (`*Skeleton*`), zombies, Vide (`Crawler_Void*`, `Larva_Void*`, `Eye_Void`,
  `Void_Spectre*`…), gobelins, trorks, `Vermin` (`Rat*`, `Snake*`, `Spider*`, `Scorpion*`), `Predators` (`Fox*`,
  `Hyena*`, `Toad*`, `Spark*`, `Fen_Stalker`), `PredatorsBig` (`Bear*`, `Wolf*`, `Yeti`, `Emberwulf`, `Leopard_Snow`,
  `Tiger_Sabertooth`, `Crocodile`, `Raptor_Cave`, `Rex_Cave`).
  - **Trous** parmi les apparitions du monde ou des balises : `Outlander_Berserker`, `Outlander_Hunter`,
    `Scarak_Fighter`, `Scarak_Seeker` (les groupes `Outlander` et `Scarak` existent sous `Groups/Intelligent/Aggressive/`
    mais `Aggressive` ne les inclut pas), `Wraith`, `Hound_Bleached` (gabarit `Template_Predator`, attitude `Hostile`),
    `Golem_Firesteel`, `Spirit_*`, `Eye_Void_Surge`, `Void_Spawn_Surge`, `Slug_Magma`, `Snail_Magma`, `Molerat`,
    `Larva_Silk`, `Lizard_Sand`, `Bat`, `Cow_Undead`, `Pig_Undead` (hostilité de ces derniers non vérifiée).
  - Vérifié depuis : `Snail_Magma` est passif (`Template_Beasts_Passive_Critter`, il fuit), à ne pas mettre dans un
    groupe hostile ; `Slug_Magma` est un prédateur (`Template_Predator`).
  - **Faux positif** : `Horse_Skeleton` (monture) entre par `*Skeleton*` (aussi dans `Neutral`).
  - Animaux passifs : dans `Neutral` (`Prey`, `PreyBig`) ou `Passive` (`Critters`, `Birds`, `Aquatic`), pas dans
    `Aggressive`. `HyColony_Citizen` n'entre dans aucun des trois.
  - Un groupe propre au mod (asset `Server/NPC/Groups/...` du pack, `IncludeGroups` `Aggressive`, `Outlander`,
    `Scarak` + rôles nommés) fermerait les trous. **[in-game]** : chargement d'un `NPCGroup` depuis le pack d'un mod
    (le magasin lit `NPC/Groups`, `TagSetPlugin.java:33-45`).
- **Par attitude**. `WorldSupport` est un composant de l'entité (`Role.java:202,219` ; `WorldSupport.getComponentType()`,
  l. 64) ; `getDefaultPlayerAttitude()` (l. 167) rend un `core/asset/type/attitude/Attitude` (`IGNORE`, `HOSTILE`,
  `NEUTRAL`, `FRIENDLY`, `REVERED`). **Peu fiable seul** : la valeur par défaut est `HOSTILE`
  (`npc/asset/builder/SupportConfigBuilder.java:86-95`), et `Template_Birds_Passive`, `Template_Edible_Critter`,
  `Template_Swimming_Passive` (`Server/NPC/Roles/_Core/Templates/`) ne la fixent pas ; notre `HyColony_Citizen.json` non
  plus. `Template_Animal_Neutral` et `Template_Livestock` la calculent (`PlayerDefaultAttitude`, défaut `Neutral`),
  `Template_Predator` et `Template_Flying_Aggressive` la fixent à `Hostile`.

### 6.2 Le crochet

- `RefSystem<EntityStore>` (`component/system/RefSystem.java:10-18`) de requête `NPCEntity`, enregistré dans le
  registre d'entités du plugin. `Store.addEntity` (`component/Store.java:428-498`) : `assertThread()` (l. 437, thread du
  monde), puis les `HolderSystem.onEntityAdd` sur le holder (l. 449-458 ; le rôle et `WorldSupport` y sont construits
  par `RoleBuilderSystem`, `npc/systems/RoleBuilderSystem.java:59,77-81`), puis les `RefSystem.onEntityAdded`
  (l. 475-487), puis `commandBuffer.consume()` ; si l'entité a été retirée, `addEntity` rend `null` (l. 497).
- **Ordre** : `NPCPlugin.spawnEntity` appelle `preAddToWorld` **avant** `store.addEntity(holder, AddReason.SPAWN)`
  (`npc/NPCPlugin.java:1525-1530`), et `postSpawn` après (l. 1536-1538). L'apparition du monde passe son
  `preAddToWorld` (`WorldSpawnJobSystems.java:278-286,333-342`) : `spawnConfiguration`, `environment` et
  `spawnRoleIndex` sont donc lisibles dans `onEntityAdded`, quel que soit l'ordre des systèmes.
- **Meutes** : `FlockPlugin.trySpawnFlock` crée chaque membre par `spawnEntity` avec le même `preAddToWorld`
  (`flock/FlockPlugin.java:174-224` ; appel `WorldSpawnJobSystems.java:296-307`) : chaque membre passe par le crochet.
- **Retrait immédiat** (motif vanilla `FailedSpawnSystem`, `npc/systems/FailedSpawnSystem.java:14-32` :
  `commandBuffer.removeEntity(ref, RemoveReason.REMOVE)` dans `onEntityAdded`, signature `CommandBuffer.java:230`) :
  l'entité disparaît avant la fin d'`addEntity`, donc avant tout envoi au client (**[in-game]** : aucun fantôme
  attendu). Mais l'appelant reçoit `null` :
  - apparition du monde : `NPCPlugin` journalise WARNING (`NPCPlugin.java:1531-1533`) et `WorldSpawnJobSystems`
    SEVERE « The spawned entity returned null » (l. 287-292), meute non créée ;
  - balise : `npcPair.first()` sur `null` → exception attrapée, WARNING (`SpawnBeaconSystems.java:744-772`) ;
  - marqueur : WARNING puis `fail(... INVALID_ROLE)` (`SpawnMarkerEntity.java:447-460`) ; des échecs répétés
    **suppriment le marqueur** (`fail`, l. 991-1016, `setValidationFailed`).
- **Retrait différé** (chemin vanilla de disparition) : `npc.setDespawning(true)` puis `npc.setDespawnRemainingSeconds(0)`
  (`NPCEntity.java:200-205`), comme `NPCPreTickSystem` le fait pour `shouldNPCDespawn` (`npc/systems/NPCPreTickSystem.java:106-110`) ;
  au tick suivant le même système retire l'entité en `RemoveReason.REMOVE`, après l'animation `Despawn` si le modèle en
  a une (l. 81-96). Pas de `null` pour l'appelant, ni de journal, ni d'échec de marqueur. **[in-game]** : le client voit
  sans doute le PNJ un tick (ou son animation de disparition).
- Le retrait `REMOVE` « dé-compte » le PNJ de l'apparition du monde (`WorldSpawnTrackingSystem.onEntityRemove`,
  `spawning/world/system/WorldSpawnTrackingSystem.java:166-200`) : le générateur réessaiera dans ces chunks (boucle
  apparition-retrait bornée par son budget, **[in-game]** pour le coût).

### 6.3 Apparition naturelle ou non

| Origine | Marque | Quand |
|---|---|---|
| Monde (`WorldSpawnJobSystems`) | `NPCEntity.getSpawnConfiguration() != Integer.MIN_VALUE` (`NPCEntity.java:124,423`), `getEnvironment()` (l. 419) | avant l'ajout |
| Enfant d'un PNJ (`ActionSpawn`) | copie le `spawnConfiguration` du parent (`npc/corecomponents/lifecycle/ActionSpawn.java:255`) | — |
| Balise | composant `SpawnBeaconReference` (`LegacySpawnBeaconEntity.java:305`, via `notifySpawn`, l. 214-229) ; `postSpawn` ne fixe que `spawnRoleIndex` (`SpawnBeaconSystems.java:802-814`) | après l'ajout |
| Marqueur | composant `SpawnMarkerReference` + `WorldGenId` dans `postSpawn` (`SpawnMarkerEntity.java:439-447`) | après l'ajout |
| `/npc spawn`, nos citoyens (`NPCPlugin.spawnNPC*`) | aucune de ces marques | — |

`RoleChangeSystem` remet `spawnConfiguration`, `environment` et `spawnRoleIndex` à `Integer.MIN_VALUE` lors d'un
changement de rôle (`npc/systems/RoleChangeSystem.java:122-124`). Les marques des balises et marqueurs n'existent pas
encore dans `onEntityAdded` : il faut les lire plus tard (au tick suivant, ou par un système sur l'ajout de ces
composants, **[in-game]**).

### 6.4 Systèmes vanilla qui retirent un PNJ juste après son apparition

- `FailedSpawnSystem` (ci-dessus) : retrait dans `onEntityAdded` d'un PNJ marqué `FailedSpawnComponent` par
  `RoleBuilderSystem` (`RoleBuilderSystem.java:250`) quand son rôle ne se construit pas.
- La disparition par l'heure (`SpawnWrapper.shouldDespawn`, `SpawningPlugin.shouldNPCDespawn`, `SpawningPlugin.java:555-566`)
  passe par `setDespawning` + `NPCPreTickSystem` (§ 6.2), vérifiée toutes les 30 s (`NPCPreTickSystem.java:97-99`).
- Les balises retirent leurs PNJ hors rayon ou à l'heure de disparition par `commandBuffer.removeEntity(..., REMOVE)`
  (`SpawnBeaconSystems.java:120-135`).

## 7. Les failles d'Update 7 et la « nature sauvage » (2026-10-02)

Constat en jeu : des failles gobelines (`Goblin_Breach_Event`) s'ouvrent dans la colonie. Choix de l'utilisateur : le
territoire d'une colonie n'est pas de la nature sauvage.

- **Où s'ouvre une faille.** `Server/WorldEvent/Stage/Goblin/Breach/Goblin_Breach_Stage1.json` (zip pre.5) : condition
  `LocationCondition` de type `WildernessLocation` (8-60 blocs du joueur à l'horizontale, 0-16 à la verticale,
  `SearchRadius` 16), puis le prefab `Orbis/WorldEvent/Goblins/Goblin_World_Event_Portal` collé à l'étape 3. Les
  gobelins sortent des 8 marqueurs `SpawnMarkerComponent` (`Goblin_Scrapper`…) de ce prefab : ils passent par le
  crochet des marqueurs du § 6.
- **`WildernessLocation.find`** (`builtin/adventure/wilderness/component/WildernessLocation.java:38-79`) : rien si le
  tracker est désactivé ; sinon un composant `Wilderness` de joueur tiré au hasard, et 3 positions au hasard dans
  chacun de ses chunks sauvages (chunks 3D de 32, `ChunkUtil.chunkCoordinate`).
- **Composant `Wilderness`** (`component/Wilderness.java`) : sur chaque joueur (`WildernessEntitySystems.AddSystem`,
  rayon `PlayerTrackerChunkRadius`/`Y`). `move` (l. 126-165), à chaque tick (`WildernessEntitySystems.TickSystem`),
  recalcule ses bits seulement si le joueur change de chunk ou si `tracker.generation()` change, en appelant
  `tracker.isWildernessChunk(Vector3i)` (l. 157). **C'est le seul appel du tracker hors de sa classe** (recherche de
  `isWilderness`, `isHome`, `collect*Chunks` dans les sources : aucun autre).
- **`WildernessTracker`** (`resource/WildernessTracker.java`, ressource du `ChunkStore`, classe et méthodes non
  finales) : un chunk est « maison » s'il est dans l'ellipsoïde d'un `RespawnBlock` (lit) : `OwnedHomeChunkRadius`
  autour d'un lit qui a un propriétaire, `UnownedHomeChunkRadius` sinon (`addHomeChunk`, l. 193-212). Constructeur de
  copie public (l. 51-58, copie réglages et chunks, pas `generation`), `generation` protégé (`AtomicLong`).
- **Recréé par Hytale** : `WildernessTrackerSystems.reload` (`system/WildernessTrackerSystems.java:29-54`) construit un
  nouveau tracker et fait `store.replaceResource` ; appelé au `StartWorldEvent` et au rechargement de la
  `GameplayConfig` du monde (`WildernessPlugin.java:96-107`), sur le thread du monde. `Store.replaceResource`
  (`component/Store.java:1327-1332`) : `assertThread`, aucun contrôle du type concret.
- **Réglages vanilla** (`Server/GameplayConfigs/Default.json`, l. 52-60) : `Enabled` true, `OwnedHomeChunkRadius` 8,
  `OwnedHomeChunkRadiusY` 4, `UnownedHomeChunkRadius` 4, `UnownedHomeChunkRadiusY` 2, `PlayerTrackerChunkRadius` 3,
  `PlayerTrackerChunkRadiusY` 1. Plugin `Hytale:Wilderness`.
- **Choix** : une sous-classe du tracker (`plugin/.../npc/spawn/ColonyWildernessTracker`) redéfinit
  `isWildernessChunk(Vector3i)` (sauvage pour Hytale et ne touchant aucune cellule revendiquée,
  `ColonyProtection.isWilderness`) et `generation()` (plus `TerritoryIndex.revision()`, pour que les joueurs
  recalculent quand le territoire change) ; `WildernessTrackerSystem` la remet en place à chaque tick si Hytale a
  recréé le tracker. Ce système n'est pas ordonné par rapport à `WildernessEntitySystems.TickSystem` : après un
  `reload`, les joueurs peuvent garder la nature sauvage vanilla un tick (démarrage du monde, rechargement de la
  config seulement). **[in-game]** : `/wilderness debug` devrait montrer comme « maison » les chunks du territoire
  proches du joueur (la teinte ne couvre que son rayon de suivi, `WildernessDebugMapSystem.java:88-96`).
- Limite : le portail se pose dans un rayon de recherche de 16 blocs autour du point tiré ; une faille tirée juste
  au-delà de la bordure peut déborder de quelques blocs.

## 8. Monstres rechargés et marqueurs qui réapparaissent (2026-10-02)

- **Constat en jeu** (diagnostic temporaire, journal complet `run/logs/2026-10-02_20-38-42_server.log`) : 184 PNJ
  hostiles ajoutés en `AddReason.LOAD`, 21 en `SPAWN`, 5 marqués par un marqueur. Les `SPAWN` dans le territoire
  étaient bien mis à disparaître (`despawning=true`) ; ceux qui restaient dans la colonie étaient tous des `LOAD`
  (6 `Goblin_Scrapper`, 5 `Rat`, avec configuration d'apparition, et 1 `Skeleton_Archmage` sans, qui ne naît que de
  marqueurs, `Server/NPC/Spawn/Markers/Undead/Skeleton/Skeleton_Archmage*.json`). Un PNJ est enregistré avec son
  chunk (`EntitySection.java:289`, `addEntities(..., AddReason.LOAD)`) ; sa configuration d'apparition est sauvegardée
  par son nom (`NPCEntity.java:73-76`), ses marques `SpawnMarkerReference`/`SpawnBeaconReference` aussi
  (`SpawningPlugin.java:256,258`).
- **`AddReason`** (`component/AddReason.java`) : `SPAWN` ou `LOAD`. `LOAD` sert au rechargement d'un chunk, à un
  marqueur qui restaure ses PNJ en réserve (`StoredFlock.restoreNPCs:57`, appelé par `SpawnMarkerSystems.java:611`),
  au changement de monde (`TeleportSystems.java:158`), au changement de rôle (`RoleChangeSystem.java:128`), au
  collage d'un prefab (`BlockSelection.java:1526`), aux outils de construction (`BuilderToolsPlugin.java:4858`,
  `EntityRemoveSnapshot.java:34`) et à `EncounterManagerPlugin.java:192`.
- **Au rechargement**, les marques arrivent avec le PNJ : `RefChangeSystem.onComponentAdded` ne part que d'un ajout
  de composant (`Store.java:2373`), jamais d'`addEntity`. Il faut donc les lire à l'ajout de l'entité.
- **Marqueur qui restaure ses PNJ** : `restoreNPCs` (`SpawnMarkerSystems.java:609-625`) ajoute le PNJ avant de le
  ramener au point du marqueur ; la vérification lit la position où il a été mis en réserve. Un PNJ mis en réserve
  dans la colonie est donc retiré même si son camp est dehors (le camp reste vide jusqu'à son délai), et un PNJ
  mis en réserve dehors d'un marqueur situé dans la colonie y reste. Cas rares, acceptés.
- **Un marqueur réapparaît-il ?** Oui, après un délai, une fois ses PNJ partis (morts ou disparus,
  `SpawnMarkerEntity.completeSpawnedNpcRemoval`, l. 271-286 ; si le chunk du marqueur n'est pas chargé au retrait,
  il le constate après 35 s, `SpawnMarkerEntity.java:305-306`, `SpawnMarkerSystems.java:642-650`). Le délai :
  `SpawnAfterGameTime` en temps de jeu, ou `RealtimeRespawnTime` si `RealtimeRespawn`
  (`assets/spawnmarker/config/SpawnMarker.java:84,352-356`). Entrées des 295 marqueurs du zip pre.5 : en temps de
  jeu, 289 `P1D` (un jour, par ex. `Intelligent/Goblin/Goblin_Scrapper.json`), 15 `PT1H`, 11 `PT1S`, 9 `PT15M`,
  8 `PT30M`, 4 `PT10M`, 2 `PT20M` ; 29 en temps réel : 13 à 5 s, 8 à 1 s, 5 à 420 s, 1 à 30 s, 1 à 10 s, 1 à 2 s.
  Un marqueur ne fait rien tant qu'un joueur est dans son `ExclusionRadius` (`SpawnMarkerEntity.java:421-428` ; 10
  pour le gobelin), et se désactive au-delà de `DeactivationDistance` (40 par défaut, `SpawnMarker.java:236` ; 150
  pour le gobelin).
