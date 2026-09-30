# SP4 : résidence, lit et sommeil des citoyens

Recherche du 2026-09-27. Question de l'utilisateur : « pourquoi les citoyens ne rentrent pas dormir alors qu'il y a des résidences ? » et « les résidences servent à rien, je peux même pas en mettre dans une maison ».

Sources MineColonies : branche `version/main`, sous `https://raw.githubusercontent.com/ldtteam/minecolonies/version/main/src/main/java/com/minecolonies/` (abrégé `MC/`). Sources Hytale : `build/vineflower/hytale-server/com/hypixel/hytale/` (abrégé `HY/`) et `release-0.6.8-Assets.zip` (abrégé `zip:`).

## Réponse courte

1. **Aucun citoyen n'a de maison.** `CitizenData.homeBuilding` existe et est sauvegardé (`CitizenSerializer.java:41,65`), mais **personne n'appelle `setHomeBuilding`** (grep sur `core/src/main` et `plugin/src/main`). `LivingModule` ne contient qu'une méthode `capacity(b) = b.level()` avec la Javadoc « Used from SP4 on » (`core/.../construction/hut/LivingModule.java:6-10`). La fenêtre de la résidence est la fenêtre générique de hutte (`BuildingViews.of`) : sans `WorkerModule`, elle n'affiche ni habitants ni bouton d'attribution.
2. **Il n'y a pas de sommeil.** `CitizenState` n'a que `IDLE, WORKING` (l'errance se fait en `IDLE`, comme MC) (`core/.../citizen/CitizenState.java`). `ColonyEvents.DayStarted` / `NightFell` sont publiés par `Colony.checkDayTime` (`core/.../colony/Colony.java:127-136`) mais **aucun abonné** ne les écoute, et `GameClock.isDaytime()` n'est lu que par `Colony`. Le constructeur travaille donc toute la nuit (MC : les travailleurs rentrent dormir).

## A. MineColonies

### A.1 Décision de l'IA citoyen (`MC/core/entity/ai/workers/CitizenAI.java`)

- `decideAiTask` est une cible `AIEventTarget` de type `EVENT`, toutes les **10 ticks**, dans **n'importe quel état** (l. 65). Il appelle `calculateNextState` et ne change d'état que si le résultat diffère de `lastState` (ou si l'état courant est `IDLE`) (l. 112-127).
- `CitizenAIState` : `IDLE, FLEE, EATING, SICK, SLEEP, MOURN, WORK, WORKING, INACTIVE` (`MC/api/entity/ai/statemachine/states/CitizenAIState.java`).
- Ordre de priorité de `calculateNextState` (l. 134-261) :
  1. job de garde : `EATING` si faim, `SICK` si malade, sinon `WORK` (les gardes ne dorment jamais par ce chemin) ;
  2. malade qui dort à l'hôpital → `SICK` ;
  3. raid en cours → `SLEEP` (statut `RAIDED`) ;
  4. **sommeil** (voir A.2) ;
  5. malade ou blessé → `SICK` ;
  6. faim (`shouldEat`) → `EATING` ;
  7. deuil → `MOURN` ;
  8. pluie (sauf `shouldWorkWhileRaining`) → `IDLE` ;
  9. enfant élève après midi → `IDLE` ;
  10. job dont l'IA ne peut pas aller au repos, et pas de temps de loisir → `WORK` ;
  11. sinon `IDLE`.
- Le sommeil passe donc **avant** la maladie, la faim, le deuil, la pluie et le travail.

### A.2 Quand dormir, quand se réveiller

Constantes (`MC/api/util/constant/CitizenConstants.java:236,241`) : `NOON = 6000`, `NIGHT = 12600`. Une journée MC fait 24000 ticks, et `dayTime % 24000 = 0` correspond à 6 h du matin.

`WorldUtil.isPastTime(world, t)` renvoie `dayTime % 24000 <= t` (`MC/api/util/WorldUtil.java:176-179`), et `isDayTime` renvoie `dayTime % 24000 <= NIGHT` (l. 165-168).

Dans `calculateNextState` (l. 168-201) :

- **fenêtre du soir et de la nuit** : `!isPastTime(world, NIGHT - 2000)`, soit `dayTime % 24000 > 10600` ;
  - si `lastState == SLEEP`, il reste en `SLEEP`, avec le statut `SLEEP` et `setCurrentDelay(20 * 15)` : la décision n'est réévaluée que toutes les 15 s ;
  - sinon, si `CitizenSleepHandler.shouldGoSleep()` → `CitizenData.onGoSleep()` (plaintes « pas de garde près du travail / de la maison », 1 chance sur `NO_GUARD_COMPLAIN_CHANCE`), puis `SLEEP` ;
  - sinon, la suite de la décision s'applique (le travailleur continue de travailler) ;
- **journée** (`dayTime % 24000 <= 10600`, donc dès 0 = l'aube) : si le citoyen dort (`isAsleep()`), `onWakeUp()`. Exception : un malade ne se réveille que s'il n'a pas de lit ou s'il est à plus de `distSqr > 5` de son lit.

`CitizenSleepHandler.shouldGoSleep()` (`MC/core/entity/citizen/citizenhandlers/CitizenSleepHandler.java:231-281`) :

- `false` si `getHomePosition()` est `null` ou si le citoyen est invisible ;
- mineur sous terre (atelier à plus de 20 blocs au-dessus) : on part de l'atelier, avec une distance ajoutée `distance2D + |dy| * 3` ;
- distance pondérée : `sqrt(dx² + dz² + (|dy| * Y_DIFF_WEIGHT)²)`, avec `Y_DIFF_WEIGHT = 1.5` ;
- temps de trajet : `(distance + extra) * TIME_PER_BLOCK`, avec `TIME_PER_BLOCK = 6` ticks par bloc ;
- temps restant : `NIGHT - dayTime % 24000` (plus `WORK_LONGER * 1000` avec la recherche) ;
- résultat `true` si `timeLeft <= 0` ou `timeLeft - timeNeeded <= 0`. Autrement dit, le citoyen part juste à temps pour arriver à `NIGHT`, au plus tôt à 10600 ;
- si la distance maison-atelier dépasse `MAX_NO_COMPLAIN_DISTANCE = 160`, il se plaint (`com.minecolonies.coremod.gui.chat.hometoofar`).

Côté colonie (`MC/core/colony/Colony.java:633-652`), `checkDayTime` utilise `isDayTime` (seuil `NIGHT`). À la tombée de la nuit : `eventManager.onNightFall()`, `raidManager.onNightFall()`, `citizenManager.updateCitizenSleep(false)`. À l'aube : `citizenManager.onWakeUp()`, qui met à jour les entités et **efface le deuil** (`CitizenManager.java:~690`).

### A.3 Rentrer chez soi (`MC/core/entity/ai/minimal/EntityAISleep.java`)

Machine d'états `SleepState { WALKING_HOME, FIND_BED, SLEEPING }` (l. 75-96) :

| Transition | Cadence | Effet |
|---|---|---|
| `SLEEP` → `WALKING_HOME` | 20 ticks | `initAI()` : `usedBed = null`, `bedTicks = 0` |
| `WALKING_HOME` | 30 ticks | `walkHome()` |
| `FIND_BED` → `SLEEPING` | 30 ticks | quand `findBed()` renvoie `true` |
| `SLEEPING` | 30 ticks (`TICK_INTERVAL`) | `sleep()` |

`walkHome()` (l. 114-133) :

- **avec maison** : passe à `FIND_BED` dès que `homeBuilding.isInBuilding(pos)` (dans l'emprise du bâtiment) ; sinon statut `SLEEP` et `EntityNavigationUtils.walkToBuilding(citizen, home)` ;
- **sans maison** : passe à `FIND_BED` si `homePosition.distSqr(pos) <= RANGE_TO_BE_HOME`, avec `RANGE_TO_BE_HOME = 16` (`CitizenConstants.java:113`). C'est comparé à une distance **au carré**, donc 4 blocs. Sinon `walkToPos(homePosition, 4, true)` ;
- 1 chance sur 33 (`CHANCE`, `nextInt(33) <= 1`) de jouer le son `OFF_TO_BED` s'il a un travail.

**Repli sans maison** (`CitizenData.getHomePosition`, `MC/core/colony/CitizenData.java:2126-2150`) : la maison, sinon la **taverne** si son niveau est supérieur à 0, sinon l'**hôtel de ville**, sinon le centre de la colonie.

**Ce que fait un sans-abri** : `findBedAndTryToSleep` ne fait rien si `getHomeBuilding()` n'est pas un `AbstractBuilding` (l. 162). `bedTicks` ne monte donc jamais, et `findBed()` renvoie `false` tant que `bedTicks < MAX_BED_TICKS`. Le sans-abri **reste debout en `FIND_BED` près de l'hôtel de ville toute la nuit**, sans pose ni `isAsleep`. À l'aube, `decideAiTask` le fait passer à un autre état. C'est une conséquence du code, pas un comportement documenté.

### A.4 Le lit

**Enregistrement** : `BedHandlingModule` (`MC/core/colony/buildings/modules/BedHandlingModule.java`) garde un `Set<BlockPos> bedList`, persisté sous `TAG_BEDS`.

- `onBlockPlacedInBuilding` ajoute la position de la **tête** du lit (pour le pied, `pos.relative(FACING)`) (l. 59-71).
- C'est appelé par `AbstractBuilding.registerBlockPosition` (`MC/core/colony/buildings/AbstractBuilding.java:1415-1419`), lui-même appelé par `BuildingStructureHandler.triggerSuccess` à **chaque bloc de plan posé** par le constructeur (`MC/core/entity/ai/workers/util/BuildingStructureHandler.java:193-200`). Chez nous, c'est le même endroit que `addContainer` dans `BuilderBlockWork.java:175-177`.
- La résidence MC a les modules `HOME`, `LIVING` et `BED` (`MC/apiimp/initializer/ModBuildingsInitializer.java:245-253`, niveau max 5, schéma `"residence"`).

**Choix du lit** (`EntityAISleep.findBedAndTryToSleep`, l. 159-220) :

- le lit est **attribué par l'index** : `index = assignedCitizen.indexOf(citizen)`, puis `bedList.get(index)`. Attention, `bedList` vient d'un `HashSet`, donc l'ordre n'est pas garanti ;
- si la position n'est plus un lit : `removeBed(pos)` ;
- le lit convient si c'est la tête et si le bloc au-dessus est un lit, un panneau DO, une trappe ou un bloc non solide ;
- **sans lit valide** (index hors liste, bloc non chargé…) : `usedBed = homePos`, la position de la hutte ;
- le citoyen marche vers `usedBed` dans le bâtiment (`walkToPosInBuilding(…, 12)`). À l'arrivée, `bedTicks++` ;
  - si le lit est `OCCUPIED` et qu'une entité y dort déjà : `usedBed = homePos` ;
  - si `trySleep(usedBed)` échoue (pas un lit, par exemple la hutte) : `bedPos = ZERO` et `usedBed = null` ;
  - `happinessHandler.resetModifier(SLEPTTONIGHT)` est appelé à chaque arrivée, même sans lit ;
- `findBed()` passe à `SLEEPING` une fois endormi, ou après `MAX_BED_TICKS = 10` tentatives arrivées.

**En `SLEEPING`** (l. 225-243) : si `usedBed` est défini et que le citoyen en est à plus de 3 blocs (`distSqr > 9`), il retourne en `WALKING_HOME`. Sinon, la pose `SLEEPING` est réappliquée. Si `usedBed == null`, il retente `findBedAndTryToSleep()`. Dans tous les cas, des particules de sommeil sont envoyées toutes les 30 ticks. **Un citoyen sans lit reste donc debout dans la maison** et y retente sa chance toutes les 30 ticks.

**Se coucher** (`CitizenSleepHandler.trySleep`, l. 97-139) :

- refus si le bloc n'est pas un lit chargé ;
- `setPose(Pose.SLEEPING)` et arrêt de la navigation ;
- position au centre du lit (décalage de 0,5, sauf pour un enfant sur l'axe), à `y + 0.6875` ;
- `setSleepingPos(bed)`, vitesse mise à zéro ;
- **retrait de l'objet tenu** ;
- `setIsAsleep(true)`, avec `leisureTime = 0` (`CitizenData.setAsleep`, l. 1223-1227) ;
- interaction cachée « zZzz... » ;
- `CitizenData.setBedPos(bed)` ;
- `CitizenManager.onCitizenSleep()` : quand tous les non-gardes dorment, le message `ALL_CITIZENS_ARE_SLEEPING` est envoyé une fois aux joueurs de la colonie (`CitizenManager.java:672-688`).

### A.5 Réveil

`CitizenSleepHandler.onWakeUp()` (l. 144-218) :

- appelle `onWakeUp()` sur l'atelier, le job et la maison. Pour la maison, `BedHandlingModule.onWakeUp` remet `OCCUPIED = false` sur les têtes de lit ;
- si le citoyen dormait vraiment : `spawnCitizenFromBed()`, qui le replace au point d'apparition à côté du lit (`EntityUtils.getSpawnPoint`), et `bedPos = ZERO` ;
- pose `STANDING`, `clearSleepingPos()` et `isAsleep = false`.

Heure : le premier `decideAiTask` où `dayTime % 24000 <= 10600`, donc à 0 (6 h, l'aube MC), au pas de 10 ticks, ou de 15 s puisque `setCurrentDelay` le retarde. Ensuite, la décision normale reprend : `WORK` si le job a du travail, sinon `IDLE`.

### A.6 Attribution d'une maison

**Module** (`MC/core/colony/buildings/modules/LivingBuildingModule.java`, `AbstractAssignedCitizenModule.java`) :

- `getModuleMax() = building.getBuildingLevel()` : **1 habitant par niveau**, 5 au niveau 5. Au niveau 0, le max est 0 et la hutte est toujours pleine ;
- `assignCitizen` refuse un doublon, une hutte pleine ou `null`. Il ajoute le citoyen à la liste, puis `onAssignment` → `citizen.setHomeBuilding(building)` et `calculateMaxCitizens()` ;
- `removeCitizen` → `setHomeBuilding(null)` et `calculateMaxCitizens()` ;
- `onDestroyed` retire tous les habitants ;
- `CitizenData.onRemoveBuilding` oublie une maison retirée ;
- **déménagement** : `CitizenData.setHomeBuilding(new)` retire le citoyen de l'ancienne maison et remet `bedPos = ZERO` (`CitizenData.java:889-908`). C'est la seule règle de départ. Aucune règle n'expulse lors d'une baisse de niveau.
- **Attribution automatique** (`onColonyTick`, l. 67-86) : si la hutte n'est pas pleine, et que son mode est `AUTO`, ou `DEFAULT` avec le réglage d'hôtel de ville `AUTO_HOUSING_MODE` (défaut **true**, `BuildingModules.java:529`), elle capture les citoyens **sans maison** (`getHomeBuilding() == null`), dans l'ordre de la liste des citoyens. Elle ne déplace jamais un citoyen déjà logé.
- Modes (`MC/api/colony/buildings/HiringMode.java`) : `DEFAULT` (« Default (colony override) »), `AUTO`, `MANUAL`, `LOCKED` (« Locked (no kids) »). HyColony a déjà le même enum : `core/.../job/HiringMode.java`.
- `CitizenManager.calculateMaxCitizens` (l. 424-459) additionne `getModuleMax()` des modules de logement construits (niveau > 0). Une hutte `LOCKED` ne compte que ses habitants actuels. Ce total plafonne l'immigration et les naissances, que HyColony n'a pas encore.

**Fenêtre** :

- `WindowHutLiving` (`MC/core/client/gui/huts/WindowHutLiving.java`, `gui/windowhuthome.xml`) montre la liste des habitants (« Métier: Nom »), le libellé `com.minecolonies.coremod.gui.home.assigned` « Assigned Citizens: %d/%d », un bouton **assign** et un bouton **recall** (`RecallCitizenHutMessage`). Au niveau 0, le bouton assign affiche `…WORKERHUTS_LEVEL_0` et ne fait rien.
- `WindowAssignCitizen` (`MC/core/client/gui/WindowAssignCitizen.java`) a deux listes et un bouton de mode :
  - **non assignés** : tous les citoyens sauf ceux qui travaillent chez eux et ceux qui habitent déjà ici. Les sans-abri passent d'abord, puis le tri se fait par distance de l'atelier à cette maison. La ligne affiche « Works %d blocks from here », en vert si c'est plus près que l'actuel, et « Homeless » ou « Current work distance », en rouge au-delà de `FAR_DISTANCE_THRESHOLD = 300` ;
  - **assignés** : bouton « unassign » (`gui.hiring.buttonunassign`), désactivé si le citoyen voyage ;
  - **les deux boutons ne sont actifs qu'en mode manuel effectif** : mode de la hutte `MANUAL`, ou `DEFAULT` avec `AUTO_HOUSING_MODE` à false. Sinon ils sont grisés, avec l'infobulle `gui.home.hire.warning` : « Turn the hiring mode of this hut (or colony) to manual to remove this citizen or assign another one. » Avec les réglages par défaut de MC, **on ne peut donc pas attribuer à la main sans passer la résidence en Manuel** ;
  - le bouton de mode fait tourner `DEFAULT → AUTO → MANUAL → LOCKED`.
- Message serveur `AssignUnassignMessage` (`MC/core/network/messages/server/colony/building/home/AssignUnassignMessage.java`) :
  - pour une attribution, si le module n'est pas plein et que ce n'est pas déjà la maison du citoyen : il le retire de l'ancienne maison, puis `assignCitizen` ;
  - sinon, si le citoyen est assigné : `removeCitizen` ;
  - **permission** : `AbstractColonyServerMessage.permissionNeeded()` renvoie par défaut `Action.MANAGE_HUTS` (l. 61-63, vérifiée l. 122). `AssignUnassignMessage` ne la redéfinit pas.

### A.7 Effets secondaires du sommeil

| Effet | Source | Dépend d'un système absent ? |
|---|---|---|
| Pas de perte de saturation la nuit **ni** pendant le sommeil (`!level().isNight() && !isAsleep()`) | `EntityCitizen.decreaseIdleSaturation`, l. ~1977 | **oui, la faim** (SP faim) |
| Modificateur de bonheur `SLEPTTONIGHT` : poids 1.5, remis à zéro à chaque arrivée au lit, paliers `(0, 2.0) (2, 1.6) (3, 1.0)` jours | `EntityAISleep`, l. 213 ; `CitizenHappinessHandler`, l. 80 | **oui, le bonheur** |
| `leisureTime = 0` à l'endormissement | `CitizenData.setAsleep` | non : le loisir est porté (`CitizenData.tickLeisure`), à remettre à zéro avec le sommeil |
| Soin : **aucun bonus lié au sommeil**. `checkHeal` toutes les 100 ticks (`HEAL_CITIZENS_AFTER`) dépend seulement de la saturation | `EntityCitizen.java:885-910` | — |
| Immunité à l'étouffement (`IN_WALL`) pendant le sommeil, pas de poussée entre entités, pas de rebond | `EntityCitizen`, l. 1293, 1514, 1913 | non (utile si la pose enfonce le modèle dans le lit) |
| Objet tenu retiré | `trySleep` | non (`bodies.setHeldItem(body, empty)`) |
| Message « tous les citoyens dorment » | `CitizenManager.onCitizenSleep` | non |
| Le deuil est effacé à l'aube | `CitizenManager.onWakeUp` | le deuil n'est pas porté |
| Plaintes « pas de garde près de… » à l'endormissement | `CitizenData.onGoSleep` | **oui, les gardes** |
| Persistance : `bedPos` (`TAG_BEDS`) et `isAsleep` (`TAG_ASLEEP`) dans les données du citoyen ; au rechargement, si `bedPos == ZERO`, `onWakeUp()` | `CitizenData.java:1354-1355, 1479-1482, 574-577` | non |

## B. Hytale 0.6.8

### B.1 Heure du jour

- `WorldTimeResource` (`HY/server/core/modules/time/WorldTimeResource.java:43-49`) :
  - `DAYTIME_PORTION_PERCENTAGE = 0.6` ;
  - `DAYTIME_SECONDS = 51840` ;
  - `NIGHTTIME_SECONDS = 34560` ;
  - `SUNRISE_SECONDS = NIGHTTIME_SECONDS / 2 = 17280`.

  Le jour va donc de **04:48** (lever) à **19:12** (coucher), en heure de jeu. `tick()` (l. 117-137) fait avancer l'horloge à deux vitesses : `DaytimeDurationSeconds` réelles pour le jour, `NighttimeDurationSeconds` réelles pour la nuit.
- `zip:Server/GameplayConfigs/Default.json` → `World` :
  - `DaytimeDurationSeconds: 1728` et `NighttimeDurationSeconds: 1152`, soit 57 600 ticks réels par jour à 20 ticks/s, contre 24 000 dans MC ;
  - `Sleep.WakeUpHour: 4.79` ;
  - `Sleep.AllowedSleepHoursRange: [19.5, 4.79]`.
- `getGameDateTime()` renvoie un `LocalDateTime` (l. 310). `isDayTimeWithinRange(min, max)` travaille sur `getDayProgress()` (l. 517).
- **HyColony** : `HytaleGameClock.isDaytime()` utilise `isScaledDayTimeWithinRange(0.25, 0.75)`, soit exactement le lever (04:48) et le coucher (19:12) de `WorldTimeResource` (corrigé le 2026-09-29, `plugin-b-api.md` § 31). Avant, il testait `6 <= hour < 20`.
- Les joueurs peuvent sauter la nuit : `UpdateWorldSlumberSystem` fait avancer l'heure via `timeResource.setGameTime(wakeUpTime, …)` (`HY/builtin/beds/sleep/systems/world/UpdateWorldSlumberSystem.java:48-73`). Il ne touche qu'aux entités `PlayerSomnolence`, donc pas aux PNJ.

### B.2 Lits

- **Objets lits** (`zip:Server/Item/Items/Furniture/*/…_Bed.json`) : `Furniture_Ancient_Bed`, `Crude`, `Desert`, `Feran`, `Frozen_Castle`, `Royal_Magic`, `Human_Ruins`, `Jungle`, `Kweebec`, `Lumberjack`, `Tavern`, `Temple_Dark`, `Temple_Emerald`, `Temple_Light`, `Village`.
- Chacun a dans `BlockType` :
  - un tableau `Beds` avec **un seul** point de couchage, par exemple `{"Offset": {"X": 0.4, "Y": 0.4, "Z": 1.0}, "Yaw": 0}` (Crude : `-0.1, -0.4, 0.8` ; Royal_Magic : `0.9, 0.4, 1.5`) ;
  - `Interactions.Use = [{"Type": "Bed"}]` en 0.6.8. **Depuis Update 7 (0.7.0)**, tous les lits pointent sur une interaction racine partagée `Block_Bed` (`Server/Item/RootInteractions/Block/Block_Bed.json`, tag `Type=Bed`) : voir `update-7/b-assets.md`. La détection par `getBeds()` reste valable ;
  - `BlockEntity.Components.RespawnBlock` ;
  - `VariantRotation: "NESW"`.

  Un lit est un seul bloc « modèle » avec des cellules de remplissage (`filler`).
- **Ce qui rend un bloc lit** : `BlockType.getBeds() != null` (`HY/builtin/mounts/BlockMountAPI.java:77-82`). Un bloc qui a `getSeats()` est d'abord traité comme un siège.
- **Joueur** (`HY/builtin/beds/interactions/BedInteraction.java:52-130`) :
  - seul le propriétaire du `RespawnBlock` se couche ; les autres ouvrent la page de point de réapparition ;
  - `BlockMountAPI.mountOnBlock(ref, commandBuffer, pos, whereWasHit)`, puis `PlayerSomnolence = NoddingOff` ;
  - `EnterBedSystem` réagit à `MountedComponent` avec `BlockMountType.Bed` et ne vérifie les heures que pour les entités qui ont un `PlayerRef` (`EnterBedSystem.java:44,86-112`).

### B.3 Coucher un PNJ : ce que la source permet

1. **`BlockMountAPI.mountOnBlock` n'est pas propre au joueur** (`BlockMountAPI.java:29-112`). Pour toute entité, il :
   - refuse si elle a déjà un `MountedComponent` (`ALREADY_MOUNTED`) ;
   - lit le `BlockType` et la rotation ;
   - crée le `BlockMountComponent` du bloc ;
   - choisit un point libre (`findAvailableSeat`, `NO_MOUNT_POINT_FOUND` si le lit est pris) ;
   - **place et tourne l'entité** (`TransformComponent.setPosition` et `setRotation`, d'après le point de couchage) ;
   - ajoute `MountedComponent(blockRef, offset, BlockMountType.Bed)`.

   Le résultat est un type scellé `Mounted | DidNotMount{CHUNK_NOT_FOUND, CHUNK_REF_NOT_FOUND, BLOCK_REF_NOT_FOUND, INVALID_BLOCK, ALREADY_MOUNTED, UNKNOWN_BLOCKMOUNT_TYPE, NO_MOUNT_POINT_FOUND}`. Attention, la signature prend un `CommandBuffer<EntityStore>`.
2. **Envoi au client** : `MountSystems.TrackerUpdate` envoie `MountedUpdate(0, offset, BlockMount, BlockMount(type, pos, rot, blockTypeIndex))` à tous les spectateurs de **toute** entité visible qui a un `MountedComponent` (`MountSystems.java:840-930`). Rien ne le limite au joueur.
3. **Descendre du lit** : `store.tryRemoveComponent(ref, MountedComponent.getComponentType())`, comme `DismountCommand.java:31`. À la suppression, `TrackedMounted` appelle `handleMountedRemoval`, qui libère la place du lit, et `TrackerRemove` envoie `queueRemove(ref, ComponentUpdateType.Mounted)` (`MountSystems.java:74-96, 769-830`).
4. **Non persistant** : `MountedComponent` est enregistré sans codec (`MountPlugin.java:78-80`, constructeur par défaut qui lève une exception), et `RemoveMountedHolder` l'enlève au déchargement de l'entité (`MountSystems.java:630-651`). Un citoyen sauvegardé dans son lit revient donc debout, ce qui convient.
5. **Animations** du modèle `Player` (`zip:Server/Models/Human/Player.json`) : `Sleep` et `Sleep2` (`Characters/Animations/Flavor/Sleep*.blockyanim`, sans `Looping`), `Sit`, `Sit2`, `SitGround` (en boucle), `MountIdle`…. `PlayerTestModel_V`, le modèle de nos citoyens (`plugin/.../Server/NPC/Roles/HyColony/HyColony_Citizen.json:3`), a `Parent: Player` et ne redéfinit que `Crouch*`. Il hérite donc de `Sleep`.
6. **État de mouvement** : `MovementStates.sleeping` existe (`HY/protocol/MovementStates.java:39`) et est envoyé aux spectateurs de toute entité visible (`MovementStatesSystems.TickingSystem`, l. 118-184). `ModelSystems` bascule alors vers `Model.sleepingBoundingBox` (`ModelSystems.java:529-541`, `Model.java:279`). Le contrôleur de mouvement des PNJ ne réécrit **pas** `sleeping`, `sitting` ni `mounting` : `MotionControllerBase.java:301-415` ne touche que `climbing`, `onGround`, `idle`, `walking`, `running`, `swimming`, `falling`, `flying` et `jumping`.
7. Il existe aussi `AnimationUtils.playAnimation(ref, AnimationSlot.Status|Action, …, "Sleep", …)` (voir `plugin-b-api.md`, § Animation), qui ne joue qu'une fois.

**Recommandation** : `mountOnBlock` sur le PNJ (voie native : position, rotation, occupation du lit et libération), avec en complément `MovementStates.sleeping = true` si le client ne met pas le PNJ en pose couchée à cause du seul `MountedUpdate`. **[in-game]** Il reste à vérifier :

- que le client affiche la pose couchée pour un PNJ `PlayerTestModel_V` monté sur un lit ;
- que la physique ou le rôle PNJ ne fait pas glisser le corps hors du lit : il faut arrêter la navigation avant, et il n'existe pas de système qui fige un PNJ monté ;
- qu'un joueur ne peut pas se coucher dans un lit déjà pris par un PNJ. `findAvailableSeat` devrait le refuser, puisqu'il n'y a qu'un point de couchage.

### B.4 Plans de résidence actuels

`plugin/src/main/resources/hycolony/styles.json` → `hycolony:residence` utilise des maisons Outlander. Nombre de lits **principaux** (entrées sans `filler`) comptés dans `zip:Server/Prefabs/Npc/Outlander/Houses/…` :

| Niveau | Préfab | Lits | Capacité MC (= niveau) |
|---|---|---|---|
| 1 | `Tier0/Outlander_Houses_Tier0_005` | 2 `Furniture_Crude_Bed` | 1 |
| 2 | `Tier1/Outlander_Houses_Tier1_001` | 1 `Furniture_Lumberjack_Bed` | 2 |
| 3 | `Tier2/Outlander_Houses_Tier2_001` | 1 `Lumberjack` | 3 |
| 4 | `Tier2/Outlander_Houses_Tier2_003` | 4 `Lumberjack` | 4 |
| 5 | `Tier3/Outlander_Houses_Tier3_005` | 1 `Lumberjack` | 5 |

Aux niveaux 2, 3 et 5, il y a moins de lits que d'habitants. Les citoyens en trop dormiront **debout à la hutte**, comme dans MC quand l'index dépasse `bedList`. Le choix des préfabs est un sujet `styles.json` distinct.

## C. Notre code aujourd'hui

- `CitizenAI` (`core/.../citizen/CitizenAI.java`) : `IDLE` redécide le travail toutes les `DECIDE_INTERVAL_TICKS = 10` (la pluie puis `canGoIdle`) et flâne toutes les 100 ticks autour de la position du citoyen (juste au-delà de `WANDER_RADIUS = 10`), `WORKING` toutes les 1 (mis à jour le 2026-09-30). **Aucune notion de nuit.**
- `Colony.checkDayTime` (`Colony.java:81,127-136`, toutes les `DAYTIME_INTERVAL = 20` ticks) incrémente `day` et publie `DayStarted` / `NightFell`, sans aucun abonné.
- Le constructeur (`construction/builder/…`) ne consulte ni l'horloge ni ces événements. **Il travaille la nuit.**
- `BuildingEventsModule` n'a plus de point d'accroche `onWakeUp` : il a été retiré, car rien ne l'appelait. Le sous-projet 4 doit rajouter `onWakeUp(Colony, Building)`, appelé depuis l'équivalent de MC `AbstractBuilding.onWakeUp`.
- `CitizenBodies` (`kernel/port/CitizenBodies.java`) a déjà `moveTo`, `navStatus`, `teleport`, `setHeldItem`, `playAnimation(BUILD|MINE)` et `position`. Il manque « coucher dans le lit » et « lever ».
- `TownHallStats` note la déviation « no housing capacity (no housing system yet) » (l. 17). `ColonyConfig.maxCitizenPerColony` n'est lu par rien (l. 18).

## D. Proposition de périmètre (SP4)

**Étape 1 : la résidence sert enfin (attribution).**

1. `LivingModule` devient un module d'attribution, sur le modèle de `WorkerModule` et `HiringMode` :
   - liste ordonnée d'habitants, persistée ;
   - `max = level` ;
   - `assign`, `remove`, `isFull` ;
   - `onDestroyed` / `onRemoved` retire tous les habitants ;
   - mode d'embauche persisté.

   `CitizenData.setHomeBuilding` retire le citoyen de son ancienne maison. Chaque condition reprend celle de MC (A.6).
2. Attribution automatique dans le tick de la colonie : `AUTO`, ou `DEFAULT` avec un nouveau réglage de colonie `autoHousing`, défaut `true`, à côté de `ColonySettings.autoHiring`. Elle capture les sans-abri jusqu'à ce que la hutte soit pleine.
3. Onglet « Habitants » de la fenêtre de résidence (MC `WindowHutLiving` + `WindowAssignCitizen`) :
   - libellé « Assignés x/max » ;
   - liste des non-assignés (tri MC, distances) et des assignés ;
   - boutons Attribuer / Retirer, actifs seulement en mode manuel effectif, avec l'infobulle `hire.warning` sinon ;
   - bouton de mode ;
   - actions du cœur sous `Action.MANAGE_HUTS`.
4. `BedHandlingModule` : enregistrer les lits posés par le constructeur, au même endroit que `addContainer`. Un indicateur « lit » vient du catalogue (`BlockType.getBeds() != null`, côté plugin). Pour les résidences déjà construites avant SP4, proposer un rescan unique du plan au chargement (réparation), sinon elles n'auraient jamais de lits.

**Étape 2 : le sommeil.**

5. `GameClock` expose une heure « façon MC » `dayTicks()` dans [0, 24000). Proposition de correspondance par phase : le jour Hytale [04:48, 19:12) correspond à [0, 12600), la nuit Hytale à [12600, 24000). Ainsi `NIGHT` tombe au coucher du soleil et `NIGHT - 2000` à environ 16:55. **Choix à valider** : les ticks de `TIME_PER_BLOCK = 6` sont alors des ticks de journée MC, soit environ 2,4 ticks réels chacun, ce qui fait partir un peu plus tôt. Corriger aussi `HytaleGameClock.isDaytime` avec ce même lever et coucher.
6. `CitizenState` gagne `SLEEP`, avec les sous-états `WALKING_HOME`, `FIND_BED` et `SLEEPING` et les cadences 20/30/30/30. `CitizenAI` place la décision de sommeil **avant** la pluie et le travail, avec `shouldGoSleep` à l'identique :
   - distance pondérée, `Y_DIFF_WEIGHT = 1.5`, `TIME_PER_BLOCK = 6` ;
   - `MAX_NO_COMPLAIN_DISTANCE = 160` (message « hometoofar ») ;
   - pendant le sommeil, redécision toutes les 15 s.
7. Lit par index d'habitant, repli sur la hutte, `MAX_BED_TICKS = 10`, retour à `WALKING_HOME` à plus de 3 blocs. Sans maison : hôtel de ville (pas de taverne), à 4 blocs (`distSqr <= 16`), debout toute la nuit.
8. Port `CitizenBodies` : `sleepIn(body, bedPos)` renvoie `boolean` et `wakeUp(body)`. Le plugin fait :
   - `mountOnBlock`, et `sleeping = true` si nécessaire ;
   - `tryRemoveComponent(Mounted)` ;
   - `teleport` au point de sortie.

   L'objet tenu est retiré. `isAsleep` et `bedPos` sont persistés, et un rechargement réveille le citoyen (MC `CitizenData.java:574`).
9. Réveil : dès que `dayTicks <= 10600`, `onWakeUp`, qui appelle `BuildingEventsModule.onWakeUp(Colony, Building)` (à rajouter à l'interface, depuis l'équivalent de MC `AbstractBuilding.onWakeUp`) de la maison et de l'atelier. Message « tous les citoyens dorment » (clé en-US / fr-FR).

**Reporté, avec raison** :

- faim (saturation arrêtée la nuit, `EATING`) : pas de système de faim ;
- bonheur (`SLEPTTONIGHT`, `homelessness`) : pas de système de bonheur ;
- maladie et hôpital, raids (`SLEEP` pendant un raid), deuil : non portés ;
- taverne comme repli : pas de taverne ;
- plaintes « pas de garde » : pas de gardes ;
- règle des mineurs sous terre : pas de mineur ;
- recherche `WORK_LONGER` : pas de recherche ;
- son `OFF_TO_BED` et particules de sommeil : **[in-game]**, ressources à trouver ;
- plafond d'immigration `calculateMaxCitizens` : pas d'immigration au-delà des citoyens initiaux.

## Incertitudes

- **[in-game]** Pose couchée d'un PNJ monté sur un lit, stabilité du corps (glissement), et collision avec un joueur qui voudrait le même lit.
- **[in-game]** Heures réelles de lever et de coucher par rapport à `HytaleGameClock` (6 h–20 h aujourd'hui, 04:48–19:12 d'après la source).
- L'ordre de `bedList` dans MC vient d'un `HashSet`, donc l'attribution d'un lit à un citoyen n'y est pas stable. Nous pouvons garder l'ordre de pose, c'est un écart mineur à documenter.
- Je n'ai pas cherché d'autres appels de `registerBlockPosition` que le constructeur (par exemple un bloc posé par un joueur dans la hutte) : la recherche de code GitHub demande une authentification.
