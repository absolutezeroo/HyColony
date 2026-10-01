# SP4 : résidence, lit et sommeil des citoyens

Recherche du 2026-09-27. Question de l'utilisateur : « pourquoi les citoyens ne rentrent pas dormir alors qu'il y a des résidences ? » et « les résidences servent à rien, je peux même pas en mettre dans une maison ».

Sources MineColonies : branche `version/main`, sous `https://raw.githubusercontent.com/ldtteam/minecolonies/version/main/src/main/java/com/minecolonies/` (abrégé `MC/`). Sources Hytale : `build/vineflower/hytale-server/com/hypixel/hytale/` (abrégé `HY/`) et `release-0.6.8-Assets.zip` (abrégé `zip:`).

**Mise à jour du 2026-10-01** : les sources de MineColonies sont maintenant copiées en local. `MC/` correspond à `sources/minecolonies/src/main/java/com/minecolonies/`, et les textes en-US à `sources/minecolonies/src/main/resources/assets/minecolonies/lang/manual_en_us.json`. La section E relit tout ce qui touche la maison, le lit et le sommeil dans ces sources, et les affirmations contredites plus bas sont corrigées sur place (marquées « corrigé le 2026-10-01 »).

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
- si la distance maison-atelier dépasse `MAX_NO_COMPLAIN_DISTANCE = 160`, il se plaint (`com.minecolonies.coremod.gui.chat.hometoofar`). Corrigé le 2026-10-01 : ce n'est pas un message de chat aux joueurs, mais une **interaction du citoyen** (`SimpleNotificationInteraction`, priorité `IMPORTANT`, `CitizenSleepHandler.java:266-276`) ;
- corrigé le 2026-10-01 : les écarts `xDiff`, `zDiff` et `yDiff` sont des `int`, et `yDiff` est tronqué **après** la multiplication par 1,5 (`CitizenSleepHandler.java:256-258`). Un citoyen invisible ne part jamais (l. 243).

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
- 2 chances sur 33 (`CHANCE`, `nextInt(33) <= 1`, corrigé le 2026-10-01) de jouer le son `OFF_TO_BED`, s'il a **à la fois** un atelier et un métier (`EntityAISleep.java:260-264`).

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
- le citoyen marche vers `usedBed` dans le bâtiment (`walkToPosInBuilding(…, 12)`). À l'arrivée, `bedTicks++`. Précisé le 2026-10-01 : « arrivé » veut dire à 1,5 bloc du lit, ou à 12 blocs une fois le chemin terminé (`EntityNavigationUtils.java:65-82`, distance euclidienne) ; tant qu'il n'est pas arrivé, `bedTicks` est **remis à 0** (`EntityAISleep.java:215-218`) ;
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
- **déménagement** : `CitizenData.setHomeBuilding(new)` retire le citoyen de l'ancienne maison et remet `bedPos = ZERO` (`CitizenData.java:889-908`). C'est la seule règle de départ. Aucune règle n'expulse lors d'une baisse de niveau. Corrigé le 2026-10-01 : MC ne sauvegarde pas la maison dans les données du citoyen ; au chargement, `LivingBuildingModule.deserializeNBT` (l. 23-50) rappelle `assignCitizen` pour chaque habitant, après la lecture du niveau. Les habitants au-delà de `max` sont donc **perdus au rechargement** (le refus « hutte pleine » s'applique), et redeviennent sans-abri.
- **Attribution automatique** (`onColonyTick`, l. 67-86) : si la hutte n'est pas pleine, et que son mode est `AUTO`, ou `DEFAULT` avec le réglage d'hôtel de ville `AUTO_HOUSING_MODE` (défaut **true**, `BuildingModules.java:529`), elle capture les citoyens **sans maison** (`getHomeBuilding() == null`), dans l'ordre de la liste des citoyens. Elle ne déplace jamais un citoyen déjà logé.
- Modes (`MC/api/colony/buildings/HiringMode.java`) : `DEFAULT` (« Default (colony override) »), `AUTO`, `MANUAL`, `LOCKED` (« Locked (no kids) »). HyColony a déjà le même enum : `core/.../job/HiringMode.java`.
- `CitizenManager.calculateMaxCitizens` (l. 424-459) additionne `getModuleMax()` des modules de logement construits (niveau > 0). Une hutte `LOCKED` ne compte que ses habitants actuels. Ce total plafonne l'immigration et les naissances, que HyColony n'a pas encore. Corrigé le 2026-10-01 : il sert aussi au compteur « citoyens x/max » des statistiques de l'hôtel de ville (`WindowStatsPage.java:97-123`, via `ColonyView.java:285-286`) et à `/colony info` (`CommandColonyInfo.java:52`), que HyColony a (voir E.3).

**Fenêtre** :

- `WindowHutLiving` (`MC/core/client/gui/huts/WindowHutLiving.java`, `gui/windowhuthome.xml`) montre la liste des habitants (« Métier: Nom »), le libellé `com.minecolonies.coremod.gui.home.assigned` « Assigned Citizens: %d/%d », un bouton **assign** et un bouton **recall** (`RecallCitizenHutMessage`). Au niveau 0, le bouton assign envoie `…WORKERHUTS_LEVEL_0` au joueur **dans le chat** et n'ouvre rien (`WindowHutLiving.java:115-124`, corrigé le 2026-10-01).
- `WindowAssignCitizen` (`MC/core/client/gui/WindowAssignCitizen.java`) a deux listes et un bouton de mode :
  - **non assignés** : tous les citoyens sauf ceux qui travaillent chez eux et ceux qui habitent déjà ici. Les sans-abri passent d'abord, puis le tri se fait par distance de l'atelier à cette maison. La ligne affiche « Works %d blocks from here », en vert si c'est plus près que l'actuel, et « Homeless » ou « Current work distance », en rouge au-delà de `FAR_DISTANCE_THRESHOLD = 300`. Précisé le 2026-10-01 dans E.2 (le citoyen sans atelier n'est pas toujours trié en dernier, et le rouge porte sur la distance **actuelle**) ;
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
| Pas de poussée entre entités (`doPush`), pas de rebond. Corrigé le 2026-10-01 : l'immunité à l'étouffement n'est **pas** liée au sommeil, tout dégât `IN_WALL` téléporte le citoyen et est ignoré (`EntityCitizen.java:1285-1291`), la clause `isAsleep` de la l. 1293 n'est jamais atteinte | `EntityCitizen`, l. 1285-1294, 1512-1517, 1911-1918 | non |
| Objet tenu retiré | `trySleep` | non (`bodies.setHeldItem(body, empty)`) |
| Message « tous les citoyens dorment » | `CitizenManager.onCitizenSleep` | non |
| Le deuil est effacé à l'aube | `CitizenManager.onWakeUp` | le deuil n'est pas porté |
| Plaintes « pas de garde près de… » à l'endormissement | `CitizenData.onGoSleep` | **oui, les gardes** |
| Persistance : `bedPos` (`TAG_BEDS`) et `isAsleep` (`TAG_ASLEEP`) dans les données du citoyen ; au rechargement, si `bedPos == ZERO`, `onWakeUp()` | `CitizenData.java:1354-1355, 1479-1482, 574-577` | non |

## B. Hytale 0.6.8, revérifié en 0.7.0-pre.4

**Revérifié en 0.7.0-pre.4 le 2026-10-01** : le détail, avec fichier:ligne, est dans `plugin-b-api.md` § 41. Ce qui change par rapport au texte de 0.6.8 ci-dessous :

- **Constantes.** `NIGHTTIME_SECONDS = 34559` et `SUNRISE_SECONDS = 17279`, pas 34560 / 17280 : le calcul se fait en `float`. Le lever est à 04:47:59 et le coucher à 19:11:59, à une seconde près.
- **Heure normalisée.** Le temps mis à l'échelle (0,25 au lever, 0,75 au coucher) est privé. On lit `getGameDateTime()` ou `getDayProgress()`, et les durées réelles par `World.getDaytimeDurationSeconds()` / `getNighttimeDurationSeconds()`, avec la surcharge du monde. La formule des ticks réels avant une heure cible est au § 41.
- **Passage de nuit.** Le réveil des joueurs est à **04:47:00**. Le saut n'arrive qu'après 3 s réelles ou plus, et il peut s'arrêter à mi-nuit si un joueur se réveille.
- **Monture.** `mountOnBlock` demande un `CommandBuffer`, qu'on n'obtient hors d'un système que par `Store.forEachChunk`. Elle doit recevoir le bloc de **base** du lit.
- **Effets de bord.** Une téléportation fait descendre du lit (`TeleportMountedEntity`). Une descente pose `PlayerSomnolence` sur le PNJ, qui sera descendu à la fin du prochain passage de nuit des joueurs.
- **Physique.** Le contrôleur de marche applique la gravité à chaque tick, même monté. `Frozen` l'arrête, mais il est sauvegardé.
- **Lits.** `Furniture_Goblin_Bed` s'ajoute. Les 20 lits HyVanilla ont `Beds`.

### B.1 Heure du jour

- `WorldTimeResource` (`HY/server/core/modules/time/WorldTimeResource.java:43-49`) :
  - `DAYTIME_PORTION_PERCENTAGE = 0.6` ;
  - `DAYTIME_SECONDS = 51840` ;
  - `NIGHTTIME_SECONDS = 34560` (en 0.7.0-pre.4 : 34559, calcul en `float`, voir l'encadré ci-dessus) ;
  - `SUNRISE_SECONDS = NIGHTTIME_SECONDS / 2 = 17280` (en 0.7.0-pre.4 : 17279).

  Le jour va donc de **04:48** (lever) à **19:12** (coucher), en heure de jeu. `tick()` (l. 117-137) fait avancer l'horloge à deux vitesses : `DaytimeDurationSeconds` réelles pour le jour, `NighttimeDurationSeconds` réelles pour la nuit.
- `zip:Server/GameplayConfigs/Default.json` → `World` :
  - `DaytimeDurationSeconds: 1728` et `NighttimeDurationSeconds: 1152`, soit 57 600 ticks réels par jour à 20 ticks/s, contre 24 000 dans MC ;
  - `Sleep.WakeUpHour: 4.79` ;
  - `Sleep.AllowedSleepHoursRange: [19.5, 4.79]`.
- `getGameDateTime()` renvoie un `LocalDateTime` (l. 310). `isDayTimeWithinRange(min, max)` travaille sur `getDayProgress()` (l. 517).
- **HyColony** : `HytaleGameClock.isDaytime()` utilise `isScaledDayTimeWithinRange(0.25, 0.75)`, soit exactement le lever (04:48) et le coucher (19:12) de `WorldTimeResource` (corrigé le 2026-09-29, `plugin-b-api.md` § 31). Avant, il testait `6 <= hour < 20`.
- Les joueurs peuvent sauter la nuit : `UpdateWorldSlumberSystem` fait avancer l'heure via `timeResource.setGameTime(wakeUpTime, …)` (`HY/builtin/beds/sleep/systems/world/UpdateWorldSlumberSystem.java:48-73`). Il ne touche qu'aux entités qui ont `PlayerSomnolence`. **En 0.7.0-pre.4**, un PNJ en reçoit un dès qu'il descend d'un lit (`WakeUpOnDismountSystem`, voir `plugin-b-api.md` § 41) : il est alors descendu de son lit à la fin d'un passage de nuit.

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
- son `OFF_TO_BED` et particules de sommeil : **[in-game]**, ressources à trouver. Corrigé le 2026-10-01 : les particules existent dans Hytale (E.3) ;
- plafond d'immigration `calculateMaxCitizens` : pas d'immigration au-delà des citoyens initiaux.

## Incertitudes

- **[in-game]** Pose couchée d'un PNJ monté sur un lit, stabilité du corps (glissement), et collision avec un joueur qui voudrait le même lit.
- **[in-game]** Heures réelles de lever et de coucher par rapport à `HytaleGameClock` (6 h–20 h aujourd'hui, 04:48–19:12 d'après la source).
- L'ordre de `bedList` dans MC vient d'un `HashSet`, donc l'attribution d'un lit à un citoyen n'y est pas stable. Nous pouvons garder l'ordre de pose, c'est un écart mineur à documenter.
- ~~Je n'ai pas cherché d'autres appels de `registerBlockPosition` que le constructeur.~~ Résolu le 2026-10-01 : il n'a que trois appelants, le constructeur (`BuildingStructureHandler.java:199`), la pose créative (`api/util/CreativeBuildingStructureHandler.java:127`) et le marteau d'assistant (`core/items/ItemAssistantHammer.java:336`, absent de HyColony). Un bloc posé par un joueur n'enregistre **pas** de lit.

## E. Audit sur les sources locales (2026-10-01)

Relecture de tout le code MC lié à la maison, au lit et au sommeil dans `sources/`, comparée ligne à ligne avec la spec `docs/superpowers/specs/2026-10-01-hycolony-sp4-home-sleep-design.md` (abrégée « S § x »). Chemins abrégés : `MC/` = `sources/minecolonies/src/main/java/com/minecolonies/`, `lang:` = `sources/minecolonies/src/main/resources/assets/minecolonies/lang/manual_en_us.json`. Les systèmes HyColony ont été vérifiés dans `core/src/main/java`. Y sont absents : les statuts visibles, les interactions de citoyen, les sons de citoyen, le rappel, le retrait d'un citoyen, la fenêtre de réglages de l'hôtel de ville et la boîte d'emprise des bâtiments.

### E.1 Ce qui a été lu

- Modules : `MC/core/colony/buildings/modules/LivingBuildingModule.java`, `AbstractAssignedCitizenModule.java`, `BedHandlingModule.java`, `HomeBuildingModule.java` ; vues `moduleviews/LivingBuildingModuleView.java`, `views/LivingBuildingView.java` ; déclaration `MC/apiimp/initializer/ModBuildingsInitializer.java:245-253` (modules `HOME`, `LIVING`, `BED`, niveau max 5) et `MC/core/colony/buildings/modules/BuildingModules.java:50,486-489,525-531` (`AUTO_HOUSING_MODE` vrai par défaut). `HOME` n'a ni vue ni persistance, et son `getMaxInhabitants` n'a aucun appelant dans ces sources.
- IA : `MC/core/entity/ai/workers/CitizenAI.java`, `MC/core/entity/ai/minimal/EntityAISleep.java`, `MC/core/entity/citizen/citizenhandlers/CitizenSleepHandler.java`, `MC/core/entity/pathfinding/navigation/EntityNavigationUtils.java`.
- Données : `MC/core/colony/CitizenData.java`, `MC/core/colony/managers/CitizenManager.java`, `MC/core/colony/Colony.java:633-655`, `MC/api/util/WorldUtil.java:165-179`, `MC/core/colony/buildings/AbstractBuilding.java:254-257,1415-1419`, `AbstractSchematicProvider.java:505-520`.
- Entité : `MC/core/entity/citizen/EntityCitizen.java`, `MC/api/entity/citizen/AbstractEntityCitizen.java`, `MC/core/util/TeleportHelper.java`, `MC/core/event/EventHandler.java`.
- Fenêtres et messages : `WindowHutLiving.java` et `gui/windowhuthome.xml`, `WindowAssignCitizen.java` et `gui/windowassigncitizen.xml`, `AssignUnassignMessage.java`, `RecallCitizenHutMessage.java`, `BuildingHiringModeMessage.java`, `WindowHireWorker.java`, `townhall/WindowStatsPage.java`, `WindowBuildBuilding.java`.
- Tous les appelants de `isAsleep`, `getBedPos`, `getBedLocation`, `getHomeBuilding`, `setHomeBuilding`, `getHomePosition`, `onGoSleep`, `onWakeUp`, `trySleep`, `registerBlockPosition` et `Pose.SLEEPING`.

### E.2 Dans la spec, mais différent de MC (b)

| S § | MC | Écart |
|---|---|---|
| 1 `remove` | `LivingBuildingModule.onRemoval` (l. 96-100) → `setHomeBuilding(null)` : `bedPos` est effacé, mais le citoyen **n'est pas réveillé**. Il reste couché jusqu'à l'aube, puis descend à côté du lit (`CitizenSleepHandler.java:178-218` lit la position du lit sur l'entité, pas `bedPos`). | La spec réveille un citoyen retiré. |
| 1 réparation, 8 | MC ne sauve pas la maison du citoyen. `LivingBuildingModule.deserializeNBT` (l. 23-50) rappelle `assignCitizen` pour chaque habitant, après la lecture du niveau (`AbstractBuilding.java:328-366`). Au-delà de `max`, les habitants sont refusés (hutte pleine, `AbstractAssignedCitizenModule.java:55-66`) et redeviennent sans-abri. | La spec garde logé un habitant au-delà de `max`. |
| 3 niveau 0 | `WindowHutLiving.java:115-124` : le bouton « Manage Housing » envoie `workerhuts.level0` dans le chat et n'ouvre rien. | La spec remplace le bouton par un libellé. |
| 3 fenêtre | Deux fenêtres. La principale (`windowhuthome.xml` : « Assigned Citizens: x/y », liste « Métier: Nom », boutons « Manage Housing » et « Recall Citizens ») ouvre `WindowAssignCitizen` : description « Administer the citizens living here. », assignés à gauche avec « Unassign », non assignés à droite avec « Assign », « Building Assignment Mode: » avec le bouton de mode, et une croix qui revient à la hutte. | La spec fusionne tout en un onglet : écart d'interface à noter. |
| 3 tri | `WindowAssignCitizen.java:196-210` : les sans-abri d'abord, puis la distance atelier → maison (euclidienne 3D, tronquée en `int`). Un citoyen **sans atelier et sans-abri** vaut 0 (en tête des sans-abri), un citoyen **sans atelier mais logé** vaut `Integer.MAX_VALUE` (en dernier). Le tri est stable, dans l'ordre des citoyens. | La spec met tout citoyen sans atelier après les autres. |
| 3 lignes | Non assignés (l. 248-299) : « Métier: Works N blocks from here. » (vert foncé si N est inférieur à la distance actuelle), puis « Current work distance: M blocks » (rouge si M > 300), ou « Homeless ». Un citoyen logé sans atelier n'a pas ce second texte. Sans métier : « Unemployed », un saut de ligne, puis ce texte. Assignés (l. 344-377) : « Métier: Works N blocks from here. », en rouge si N > 300, sinon « Unemployed ». | La spec ne dit pas sur quoi portent le rouge et le vert, ni le cas sans métier. |
| 3 boutons | En mode manuel, « Assign » est aussi grisé quand la hutte est pleine (l. 301-317). | Absent de la spec. |
| 3 actions | `AssignUnassignMessage.java:119-130` ne vérifie **pas** le mode côté serveur : seule la fenêtre grise les boutons. Un « assign » vers la maison actuelle du citoyen, ou sur une hutte pleine qui le compte déjà, tombe dans la branche « retirer ». | La spec refuse hors mode manuel : plus strict, écart à noter. Le cas « retirer » n'est pas atteignable depuis la fenêtre. |
| 4 | `WorldUtil.isDayTime` : `dayTime % 24000 <= NIGHT` (l. 165-168). | La spec écrit `dayTime() < NIGHT`. |
| 5.1 plainte | `CitizenSleepHandler.java:266-276` : interaction du citoyen (`SimpleNotificationInteraction`, `IMPORTANT`), pas un message aux joueurs. | La spec l'envoie aux joueurs : écart à noter, faute d'interactions dans HyColony. |
| 5.1 distance | `xDiff`, `zDiff` et `yDiff` sont des `int`, avec `yDiff = (int)(|dy| * 1.5)` (l. 256-260). | Troncatures absentes de la spec. |
| 5.3 choix du lit | `EntityAISleep.java:159-220` : le lit est **rechoisi par rang à chaque tentative** tant que `usedBed` est vide ou vaut la hutte (l. 165). Si le bloc n'est plus un lit, `removeBed`, puis **retour sans marcher** (l. 178-182) : la tentative suivante recalcule le rang sur la liste raccourcie, et peut tomber sur un autre lit. Le lit n'est retenu que si c'est la tête et si le bloc au-dessus est un lit, un panneau DO, une trappe ou un bloc non solide (l. 183-189). Un lit non chargé donne la hutte. Un lit `OCCUPIED` avec une entité couchée dans son volume donne la hutte (l. 199-205). | La spec dit que la tentative suivante « retombe sur la hutte », et omet le contrôle du bloc au-dessus (à porter, ou à noter comme écart Hytale). |
| 5.3 arrivée | « Arrivé » veut dire à 1,5 bloc, ou à 12 blocs une fois le chemin fini (`EntityNavigationUtils.java:65-82,95-108`). Pas encore arrivé : `bedTicks = 0` (l. 215-218). Échec de `trySleep` : `bedPos = ZERO` en plus de `usedBed = null` (l. 208-212). | Le rayon de 12, la remise à zéro de `bedTicks` et l'effacement de `bedPos` manquent. |
| 5.3 sommeil | `sleep()` réapplique la pose couchée toutes les 30 ticks (l. 225-243). | À reprendre si l'adaptateur doit réaffirmer l'état couché. |
| 5.4 message | Le drapeau « tous dorment » repasse à faux à la **tombée de la nuit** (`Colony.java:645`, `CitizenManager.updateCitizenSleep`, l. 666-669), pas au réveil. Tous les citoyens comptent, y compris ceux sans corps et les enfants (l. 672-689). | La spec dit « il repart après un réveil ». |
| 5.6 | `notifyCitizenHandlersOfWakeUp` (l. 160-176) appelle aussi `job.onWakeUp()`. Il s'exécute même pour un citoyen qui ne dormait pas, quand il est appelé depuis `CitizenData.initEntityValues` (l. 574-577). | Le crochet du métier manque. Son seul contenu MC est la nourriture (E.4). |
| 5.7 | `CitizenData.initEntityValues` (l. 542, 574-577) ne réveille que si `bedPos == ZERO`, et à **chaque** apparition du corps, pas seulement au chargement. | La spec réveille tout citoyen endormi au chargement : écart justifié par Hytale, à écrire en `Deviation from MC`. Elle oublie la réapparition d'un corps (E.3). |
| Dehors | `calculateMaxCitizens` sert aussi aux statistiques de l'hôtel de ville et à `/colony info` (E.3). | La spec le dit réservé à l'immigration et aux naissances. |

Conformes (a), pour mémoire :

- capacité = niveau, refus d'un doublon, d'une hutte pleine ou d'un citoyen absent, déménagement, `onRemoved` ;
- attribution automatique (`AUTO`, ou `DEFAULT` avec le réglage), modes et leur cycle ;
- permission `MANAGE_HUTS`, y compris pour le changement de mode (`BuildingHiringModeMessage`) ;
- lit enregistré par sa tête (son origine) sans doublon, `MAX_BED_TICKS = 10`, retour à `WALKING_HOME` au-delà de 3 blocs, sans-abri debout ;
- cadences 20/30/30/30, fenêtre `NIGHT - 2000`, délai de 15 s, départ « juste à temps », priorité du sommeil, `resetAI` au retour au travail ;
- objet tenu retiré, `leisureTime = 0`, réveil et sortie du lit.

Déjà portés, et qui s'activeront avec SP4 : le loisir selon le niveau de la maison (`CitizenManager.homeLevel`), l'expérience et le plafond de compétence selon la maison (`job/JobXp`, `citizen/Skills.addXp` ; MC `CitizenExperienceHandler.java:75-86`, `CitizenSkillHandler.java:184-192`). Un citoyen logé dépassera le plafond de compétence de 10 des sans-abri.

### E.3 Absent de la spec, portable maintenant (c)

1. **Boîte d'emprise de la hutte**, prérequis de S § 5.3 : MC `isInBuilding` teste les coins du plan élargis de 1 bloc sur les trois axes (`AbstractSchematicProvider.java:505-520`), et la marche vers le lit vise le centre de ces coins (`EntityNavigationUtils.java:95-108`). Le cœur HyColony n'a pas de coins de bâtiment (le note `HytaleWorldEffects.celebrate`).
2. **Capacité de la colonie** : `calculateMaxCitizens` (`CitizenManager.java:424-459`) et, sans recherche, `getMaxCitizens = max(1, min(somme, maxCitizenPerColony))` (l. 503-506). Elle est recalculée à l'attribution, au retrait et à l'amélioration (`LivingBuildingModule.java:89-119`), et affichée :
   - dans les statistiques de l'hôtel de ville, « x/max » en vert sous 90 %, en orange avec l'infobulle « Needs Housing » sous le plafond de la config, sinon en rouge avec « Reached Configured Limit » (`WindowStatsPage.java:97-123`, sans le cas de la recherche) ;
   - dans `/colony info` (`CommandColonyInfo.java:52`).

   Elle lève les écarts notés dans `TownHallStats` et `ColonyConfig.maxCitizenPerColony`.
3. **Bouton « Recall Citizens »** de la résidence (`RecallCitizenHutMessage.java:47-74`, `MANAGE_HUTS`) : chaque habitant est téléporté au point d'apparition de la hutte, un habitant sans corps y réapparaît, et un échec envoie `workerhuts.recallfail`. La téléportation réveille d'abord un citoyen endormi (`TeleportHelper.java:36-60`).
4. **Réveil avant toute téléportation** (`TeleportHelper.java:49-52`) : vaut aussi pour la téléportation de débogage `CitizenAI.teleport` (HyLens).
5. **Réveil à la réapparition d'un corps** (`CitizenData.initEntityValues`, l. 574-577) : `CitizenManager.updateBodyIfNecessary` doit remettre `asleep` à faux.
6. **À l'aube, réapparition de tout citoyen sans corps** (`CitizenManager.onWakeUp`, l. 691-697, `updateEntityIfNecessary`).
7. **Particules de sommeil**, toutes les 30 ticks en `SLEEPING`, à `(x, y + 1, z)`, même debout sans lit (`EntityAISleep.java:240`). Hytale a le système `Server/Particles/NPC/Emotions/Sleepy.particlesystem` (émetteur `Zzzz`, texture `Common/Particles/Textures/Shapes/Zzz.png`, durée 5 s) et `ParticleUtil.spawnParticleEffect(String, Vector3dc, ComponentAccessor)` (`HY/server/core/universe/world/ParticleUtil.java:50`), déjà employé par `HytaleWorldEffects`. **[in-game]** L'identifiant exact (`Sleepy`) reste à confirmer.
8. **Lit refusé au joueur** (`EventHandler.java:610-631`) : un joueur qui utilise un lit où dort un citoyen est refusé avec `block.minecraft.bed.occupied` (« This bed is occupied »). **[in-game]** Ce que fait Hytale seul avec un point de couchage déjà pris.
9. **Fenêtre d'embauche** (`WindowHireWorker.java:349-376,479-495`) : candidats triés par distance maison → hutte, arrondie à 40 blocs (sans-abri = 100), puis par nom ; libellé « Currently homeless », « Lives here », « Lives at current work building » ou « Lives %d blocks from here ». `BuildingViews.hireable` ne trie ni n'affiche rien de tel.
10. **Avertissements d'amélioration de la résidence** (`LivingBuildingView.java:93-130`, affichés en infobulle et en confirmation par `WindowBuildBuilding.java:130,207-211`) :
    - niveau 1 → 2 sans ferme ni pêcheur de niveau ≥ 1 : `residence.warning.2` ;
    - niveaux 2 → 5 : `warning.3` à `warning.5` tant qu'aucune cantine n'a de menu adapté, donc **toujours** dans HyColony, qui n'a pas de cantine.
11. **Pas de poussée entre entités** pour un citoyen endormi (`EntityCitizen.java:1512-1517`). **[in-game]** Hytale pousse-t-il un PNJ monté ?
12. **Repère « couché » au-dessus de la tête** : `VisibleCitizenStatus.SLEEP` est dessiné au-dessus du citoyen (`render = true`, `VisibleCitizenStatus.java:36-37`, `RenderBipedCitizen.java:110-124`), pendant la marche et le sommeil. Portable comme le « ! » de `CitizenNameplates`, sans le système de statuts.
13. **Constructeur** : `BuildingBuilder.onWakeUp` remet `purgedMobsToday` à faux (l. 91-94), et un constructeur dont la hutte est au moins au niveau `LEVEL_TO_PURGE_MOBS = 4` supprime une fois par jour les monstres dans l'emprise d'un chantier de type `BUILD` (`EntityAIStructureBuilder.java:55,163-186`). Ce n'est pas porté dans HyColony : à porter avec le crochet `onWakeUp`, ou à noter comme écart.
14. **Chemin trop long** : au-delà de 900 blocs, le citoyen est téléporté à `getHomePosition()` (`MinecoloniesAdvancedPathNavigate.java:310-330`). C'est une sûreté de navigation, hors du cœur du sujet.

### E.4 Absent, dépend d'un système absent (d)

- **Gardes** : ils ne dorment jamais par ce chemin (`CitizenAI.java:136-150`), sont exclus de « tous dorment », ont le module `BED` des tours et casernes, et provoquent les plaintes « pas de garde » (`CitizenData.onGoSleep`, l. 1883-1904 ; `lang:` `noguardnearwork`, `noguardnearhome`).
- **Raids** : `SLEEP` avec le statut `RAIDED` (`CitizenAI.java:161-166`).
- **Maladie et hôpital** : réveil retardé du malade (l. 186-200), lits d'hôpital (`EntityAISickTask`, `BuildingHospital`).
- **Faim** : saturation figée la nuit et pendant le sommeil (`EntityCitizen.java:1977`), nourriture selon le niveau de la maison (`FoodUtils`), `searchedForFoodToday` (`AbstractJob.onWakeUp`, l. 347-350), requêtes de la maison (`AbstractJob.onStackPickUp`, l. 322-337).
- **Bonheur** : `SLEPTTONIGHT`, `HOMELESSNESS`, fonction de logement niveau / 3 (`ModHappinessFactorTypeInitializer.java:44`).
- **Deuil** : `doesLiveWith`, effacement à l'aube.
- **Interactions** : « zZzz... » (`entity.citizen.sleeping`, `HIDDEN`), « hometoofar », demandes des sans-abri (`InteractionValidatorInitializer.java:266-270`).
- **Statuts visibles** : `SLEEP` (« Sleeping zZZ ») et `HOUSE` dans la fenêtre du citoyen (`MainWindowCitizen.java:41-52`). HyColony y affiche l'état de l'IA, ce qui suffit pour « SLEEP ».
- **Sons de citoyen** : `OFF_TO_BED` en rentrant et en dormant (`SoundUtils.java:115-118`, `AbstractEntityCitizen.java:208-215`), `BAD_HOUSING`.
- **Apparence selon la maison** : le modèle d'un citoyen sans métier (« settler », « citizen », « noble », « aristocrat ») dépend du niveau de sa maison (`CitizenJobHandler.java:48-90`), appelé par `setHomeBuilding` et `onUpgradeComplete`.
- **Autres** :
  - enfants et naissances (« Locked (no kids) », plafond de naissances, lit d'enfant décalé) ;
  - visiteurs et taverne (repli des sans-abri, `TavernLivingBuildingModule`, recrutement) ;
  - recherche (`WORK_LONGER`, `CITIZEN_CAP`) ;
  - mineur sous terre ;
  - voyage (« Unassign » grisé) ;
  - résurrection (`onResurrect`) ;
  - retrait d'un citoyen (`removeCivilian`, l. 378-404) ;
  - bâtiments où l'on vit au travail (`WorkAtHomeBuildingModule`) ;
  - fenêtre de réglages de l'hôtel de ville (bascule `AUTO_HOUSING_MODE`, absente comme celle d'`autoHiring`) ;
  - marteau d'assistant.

### E.5 Textes en-US de MC (`lang:`)

| Clé | Texte |
|---|---|
| `com.minecolonies.coremod.gui.home.assigned` | Assigned Citizens: %d/%d |
| `…gui.home.manage` | Manage Housing |
| `…gui.townhall.recall` | Recall Citizens |
| `…gui.assigning.description` | Administer the citizens living here. |
| `…gui.hiring.buttonassign`, `buttonunassign` | Assign, Unassign |
| `…gui.buildingassignmentmode` | Building Assignment Mode: |
| `…gui.hiringmode.default`, `auto`, `manual`, `locked` | Default (colony override), Automatic, Manual, Locked (no kids) |
| `…gui.home.new` | Works %d blocks from here. |
| `…gui.home.homeless` | Homeless |
| `…gui.home.currently` | Current work distance: %d blocks |
| `…gui.home.hire.warning` | Turn the hiring mode of this hut (or colony) to manual to remove this citizen or assign another one. |
| `…gui.home.travelling` | This citizen is currently travelling. You can not fire them. |
| `…gui.townhall.citizens.unemployed` | Unemployed |
| `…gui.workerhuts.level0` | You must construct this hut before you can hire a worker! |
| `…workerhuts.recallfail` | Recall failed. Please make more space around the location. |
| `…entity.citizen.sleep` | All citizens are tucked into bed. |
| `…entity.citizen.sleeping` | zZzz... I want to sleep... Why are you bothering me? |
| `…gui.chat.hometoofar` | I have to walk a long way home from work every day. It would be nice if I could live somewhere closer. |
| `…gui.townhall.population.totalcitizens.count` | %d/%d |
| `…totalcitizens.houselimited`, `configlimited` | Needs Housing, Reached Configured Limit |
| `com.minecolonies.gui.visiblestatus.sleep` | Sleeping zZZ |
| `com.minecolonies.core.gui.hiring.homeless`, `liveshere`, `livesatwork`, `distance` | Currently homeless, Lives here, Lives at current work building, Lives %d blocks from here |
| `com.minecolonies.core.gui.residence.warning.2` à `.5` | voir `lang:` (ferme ou pêcheur ; repas ; repas cuisinés variés ; production de nourriture) |
| `block.minecraft.bed.occupied` | texte vanilla de Minecraft, absent de `sources/` |
