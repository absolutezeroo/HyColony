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
bâtiment, la maison ou le centre ; sans elle, notre flânerie est une marche aléatoire libre qui dérive.

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
