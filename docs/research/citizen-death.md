# Mort d'un citoyen

Question : porter la mort d'un citoyen de MineColonies, avec le domaine 2 de `audit-monde-hytale.md` (C-14, C-17, C-18, C-19, C-20, C-21, C-23). Conception validée en 0.6.8 : à la vraie mort, l'inventaire tombe au sol, le citoyen est retiré pour de bon, les joueurs reçoivent un message, son emploi et ses requêtes sont libérés. Le deuil attendait SP4, qui a depuis porté le bonheur (SP4b).

Sources :

- première version (2026-09-26) : MineColonies au commit `6b3916a1`, serveur Hytale 0.6.8 ;
- **revérifiée le 2026-10-04** sur Hytale `0.7.0-pre.5` (`gradle.properties:19`), décompilé du 2026-10-01, assets `pre-release-0.7.0-pre.5-Assets.zip`, et sur la copie locale de MineColonies `sources/minecolonies/` (branche `version/main`, copie sans hash prise vers le 2026-10-01 ; son `gradle.properties:20-22` dit Minecraft 1.20.1, Forge 47).

Abréviations : `MC/` = `sources/minecolonies/src/main/java/com/minecolonies/` ; `HY/` = `build/vineflower/hytale-server/com/hypixel/hytale/` ; `C/` = `core/src/main/java/dev/hycolony/core/` ; `P/` = `plugin/src/main/java/dev/hycolony/plugin/` ; `R/` = `plugin/src/main/resources/` ; `zip:` = entrée du zip d'assets pre.5 ; `lang:` = `sources/minecolonies/src/main/resources/assets/minecolonies/lang/manual_en_us.json`.

## 1. MineColonies : le déroulé exact (revérifié)

### 1.1 `EntityCitizen.die(DamageSource)` (`MC/core/entity/citizen/EntityCitizen.java:1548-1629`)

Tout se passe dans `die`, seulement si la colonie (`getColonyOrRegister()`) et le `citizenData` existent. Ordre exact :

1. `getRaiderManager().onLostCitizen(citizenData)` (l. 1552, raids, hors sujet).
2. `citizenExperienceHandler.dropExperience()` (l. 1554) : orbes d'XP (1.3).
3. `this.remove(RemovalReason.KILLED)` (l. 1555) : l'entité est retirée **avant** `super.die`.
4. Si le métier n'est pas garde : `injectModifier(new ExpirationBasedHappinessModifier(DEATH, 3.0, new StaticHappinessSupplier(0.0), 3))` (l. 1556-1561). `CitizenManager.injectModifier` ajoute ce modificateur à **chaque** citoyen de la colonie (`MC/core/colony/managers/CitizenManager.java:552-558`).
5. `triggerDeathAchievement` (l. 1562, succès Minecraft, hors sujet).
6. Si le métier n'est pas garde : `updateCitizenMourn(citizenData, true)` (l. 1564-1567, voir 1.7).
7. `getStatisticsManager().increment(DEATH, colony.getDay())` (l. 1569 ; `DEATH = "death"`, `MC/api/util/constant/StatisticsConstants.java:9`). Affiché dans l'onglet Statistiques de la mairie : « `Citizen Deaths: %d` » (`lang:2576`, clé `com.minecolonies.coremod.gui.townhall.stats.death`).
8. Objets (1.2) : tombe ou chute au sol (l. 1571-1587).
9. Message aux joueurs (1.4, l. 1589-1615).
10. `citizenData.getJob().onRemoval()` si le citoyen a un métier (l. 1616-1619, voir 1.5).
11. `getCitizenManager().removeCivilian(citizenData)` (l. 1620, voir 1.5).
12. Journal : `addEventDescription(new CitizenDiedEvent(blockPosition(), name, deathCause))` (l. 1622-1624, voir 1.6).
13. `CitizenDiedModEvent(citizenData, damageSource)` sur le bus de MC (l. 1626).
14. `super.die(damageSource)` (l. 1628).

`remove` est surchargé (l. 1630-1635) et poste aussi `CitizenRemovedModEvent(colony, citizenId, reason)`.

L'étape 3 retire l'entité avant `super.die`. `dropEquipment()` (surchargé l. 1653-1665 pour vider l'inventaire) n'est donc vraisemblablement **pas** appelé : cela repose sur le `LivingEntity.die` de Minecraft vanilla, absent de `sources/`, **non vérifié**.

### 1.2 Ce qui tombe, et où

`EntityCitizen.java:1571-1587` :

- **citoyen invisible** : rien ne tombe ;
- **citoyen dans la colonie** (`isCoordInColony`) : `GraveManager.createCitizenGrave(level, blockPosition(), citizenData)` (`MC/core/colony/managers/GraveManager.java:254-331`) :
  - dans la lave : aucune tombe, message `WARNING_GRAVE_LAVA` (« `Could not spawn a grave because all remains already burned.` », `lang:2496`) aux gestionnaires, **rien ne tombe** (l. 257-261) ;
  - sinon une tombe est posée sur un bloc d'air au-dessus d'un solide (rayon 10, sous l'eau jusqu'à 10 blocs plus haut puis rayon 16). L'inventaire principal y est transféré (surplus au sol, l. 299) ; les **armures** aussi ; les données du citoyen y sont sérialisées pour une résurrection ;
  - sans place libre : `dropItemHandler(citizenData.getInventory(), …)` au sol (l. 328) ;
- **citoyen hors de la colonie** : `InventoryUtils.dropItemHandler(citizenData.getInventory(), level, x, y, z)` (l. 1581).

`InventoryUtils.dropItemHandler` (`MC/api/util/InventoryUtils.java:2592`) parcourt `handler.getSlots()`, qui ne couvre que l'inventaire principal (`MC/api/inventory/InventoryCitizen.java:159-162`, `mainInventory.size()`). Les 4 armures sont à part et ne tombent pas dans cette branche. Chaque pile passe par `spawnItemStack` (l. 2639) : léger décalage aléatoire, pile découpée, vitesse gaussienne. L'objet en main n'est qu'un **indice** dans l'inventaire principal (`InventoryCitizen.java:57`, `getHeldItem` l. 116-121) : il tombe avec lui.

### 1.3 XP et compétences

`CitizenExperienceHandler.dropExperience()` (`MC/core/entity/citizen/citizenhandlers/CitizenExperienceHandler.java:128-160`) fait apparaître des orbes d'XP Minecraft valant `getCitizenSkillHandler().getTotalXP()`, si `recentlyHit > 0`, le butin autorisé et la règle `doMobLoot` active ; puis des particules d'explosion. **Aucun transfert** vers un autre citoyen : les compétences disparaissent.

### 1.4 Le message et ses destinataires

Priorité `DANGER`, `.sendTo(colony).forManagers()` (l. 1589-1615). Il concatène :

1. `getCombatTracker().getDeathMessage()` (message de mort vanilla, « X was slain by Y ») puis `"! "` ;
2. `block.blockhuttownhall.messagecolonistgravelocation` = « `They died on the %s side of the colony. ` » (`lang:294`, constante `COLONIST_DEATH_LOCATION`, `MC/api/util/constant/TranslationConstants.java:452`) ; `%s` = `BlockPosUtil.calcDirection(colony.getCenter(), blockPosition()).getLongText()` (`MC/api/util/BlockPosUtil.java:872`, `getLongText` l. 989) ; au survol `message.positiondist` = « `Position: %s %s %s Distance: %s blocks` » (`lang:374`) ;
3. si le citoyen n'est pas garde : `com.minecolonies.coremod.mourning` = « `Close family and friends will mourn %s tomorrow out of respect. ` » (`lang:983`) ;
4. si une tombe est posée : `com.minecolonies.coremod.gravespawned` (`lang:851`).

Le français de MC n'est pas dans la copie (seul `manual_en_us.json` sous `assets/minecolonies/lang/`).

Destinataires : `forManagers()` → `colony.getImportantMessageEntityPlayers()` (`MC/api/util/MessageUtils.java:384-387`, `MC/core/colony/Colony.java:1392-1404`), union de :

- `getMessagePlayerEntities()` (l. 1375-1388) : les abonnés proches (`getCloseSubscribers`) qui ont `Action.RECEIVE_MESSAGES` ;
- les joueurs importants (`getImportantColonyPlayers`) dont le rang `isColonyManager()`.

### 1.5 Emploi, bâtiment et requêtes libérés

- `AbstractJob.onRemoval()` (`MC/core/colony/jobs/AbstractJob.java:426-467`) : `citizen.setJob(null)`, `getWorkerAI().onRemoval()`, `removeCitizen` sur chaque module `IAssignsJob` du bâtiment de travail, `workBuilding`/`workModule` à null, armures et mains rangées dans l'inventaire.
- `AbstractAssignedCitizenModule.removeCitizen` (`MC/core/colony/buildings/modules/AbstractAssignedCitizenModule.java:41-52`) appelle `onRemoval(citizen)` même si le citoyen n'était pas assigné.
- `WorkerBuildingModule.onRemoval` (`MC/core/colony/buildings/modules/WorkerBuildingModule.java:216-226`) : rappelle `citizen.getJob().onRemoval()` si le métier existe encore (déjà null dans le chemin de la mort), puis `building.cancelAllRequestsOfCitizenOrBuilding(citizen)` (**les requêtes sont annulées là**) et `setVisibleStatus(null)`.
- `AbstractBuilding.cancelAllRequestsOfCitizenOrBuilding` (`MC/core/colony/buildings/AbstractBuilding.java:1657-1690`) : requêtes ouvertes → `CANCELLED`, terminées → `RECEIVED`, index nettoyés.
- `CitizenManager.removeCivilian` (`MC/core/colony/managers/CitizenManager.java:378-407`) : `citizens.remove(id)` ; `removeCitizen` sur **chaque** `AbstractAssignedCitizenModule` de **chaque** bâtiment (sous-classes : `WorkerBuildingModule`, `LivingBuildingModule`, `CourierAssignmentModule`, `QuarryModule`) ; `getWorkManager().clearWorkForCitizen` ; `ColonyViewRemoveCitizenMessage` aux abonnés ; `calculateMaxCitizens()`, `markDirty()`. `LivingBuildingModule.onRemoval` fait `setHomeBuilding(null)` et `calculateMaxCitizens()` (`LivingBuildingModule.java:95-100`).
- `WorkManager.clearWorkForCitizen` (`MC/core/colony/workorders/WorkManager.java:205-218`) libère les ordres réclamés par le bâtiment de travail, mais `getWorkBuilding()` passe par le métier (`MC/core/colony/CitizenData.java:910-919`), déjà null : **dans le chemin de la mort, aucun ordre n'est libéré**. Le prochain constructeur embauché le reprend. À reproduire tel quel.

### 1.6 Journal et résumé du soir

`CitizenDiedEvent` (`MC/core/colony/eventhooks/citizenEvents/CitizenDiedEvent.java`) : position, nom, `deathCause` = message de mort vanilla où le nom est remplacé par `"Citizen"` (`EntityCitizen.java:1622-1623`). Il est créé avec `includeInSummary = true` (l. 40-42 ; `CitizenSpawnedEvent` avec `false`, l. 37).

**Correction** : le résumé « `%d citizen(s) died` » (`lang:2986`) ne vient pas de la statistique `DEATH`, mais du journal. À la tombée de la nuit, `Colony` appelle `eventDescManager.computeNews()` (`MC/core/colony/Colony.java:635-646`). Cette méthode compte les événements du jour qui ont `includeInSummary()` par clé de résumé et envoie « préfixe + liste + ! » à `getImportantMessageEntityPlayers()` (`MC/core/colony/managers/EventDescriptionManager.java:119-145` ; clé `CitizenDiedEvent.getSummaryTranslationKey`, l. 134-137).

### 1.7 Deuil (`updateCitizenMourn`, maintenant dans le périmètre)

- `CitizenManager.updateCitizenMourn(data, true)` (`CitizenManager.java:639-657`) : pour chaque citoyen, s'il n'est ni garde ni fossoyeur (`JobUndertaker`) **et** s'il est parent du mort (`isRelatedTo`) ou vit avec lui (`doesLiveWith`, même `homeBuilding`, `MC/core/colony/CitizenData.java:798-801`), le nom du mort entre dans son `CitizenMournHandler` (`addDeceasedCitizen`). Puis `citizen.onDeath(id)` sur tous : retire l'id des enfants, frères et sœurs et du partenaire (`CitizenData.java:2003-2012`).
- `CitizenMournHandler` (`MC/core/entity/citizen/citizenhandlers/CitizenMournHandler.java`) : un ensemble de noms, `shouldMourn()` = ensemble non vide (l. 87-91), drapeau `isMourning` sauvegardé (l. 43, 54).
- `CitizenManager.onWakeUp` (l. 690-707) : à chaque réveil, un citoyen en deuil cesse de l'être (noms effacés) ; un citoyen qui a des noms commence son deuil. Le deuil dure donc **le jour qui suit la mort**.
- `CitizenAI.decideAiTask` (`MC/core/entity/ai/workers/CitizenAI.java:216-229`), après la maladie et le repas, avant la pluie et le travail : en deuil, interaction « `I'm still processing %ss death, I can't work today.` » (`lang:1821`), statut visible `MOURNING`, état `MOURN` : **pas de travail ce jour-là**.
- `EntityAIMournCitizen` (`MC/core/entity/ai/minimal/EntityAIMournCitizen.java`, enregistré `CitizenAI.java:73`) : `DECIDE` toutes les 20 ticks ; une chance sur deux de regarder un citoyen à 3 blocs (`STARING`, fin avec une chance sur `AVERAGE_STARE_TIME` = 200 par passage) ; sinon cimetière s'il en existe un (hors périmètre sans tombes), sinon marche vers la mairie (ou la maison sans mairie) si elle est à plus de 15 blocs (`MIN_DESTINATION_TO_LOCATION = 225`, distance 2D au carré), sinon errance de 10 blocs (l. 68-70, 99-112, 230-346).

### 1.8 Nouveaux citoyens après une mort

Inchangé : `CitizenManager.onColonyTick` (`CitizenManager.java:585-630`) ; arrivée tant que `citizens.size() < initialCitizenAmount` (défaut 4, bornes 1-10, `MC/api/configuration/ServerConfiguration.java:125`), `MOVE_IN` actif et mairie présente ; `respawnInterval -= 500 + 60 × niveau de la mairie`, remis à `60 × 20` (l. 95, 594-600). Le minuteur de 5 min (l. 100, 587-589) ne ressuscite pas un mort, retiré de `citizens`.

### 1.9 Tombes (ignorées)

`GraveManager`, `TileEntityGrave`, résurrection par le fossoyeur (`JobUndertaker`), cimetière : **hors périmètre** (pas de fossoyeur porté).

### 1.10 Permission `MAP_DEATHS`

`Action.MAP_DEATHS(29)` (`MC/api/colony/permissions/Action.java:42`), donnée à Owner et Officer et à tout rang gestionnaire (`MC/core/colony/permissions/Permissions.java:178, 190, 242`). Seul usage : les points de passage JourneyMap. Elle ne filtre **pas** le message de mort.

### 1.11 Blessure (`EntityCitizen.hurt`, l. 1235-1281)

Ordre : `handleInWallDamage` (l. 1285-1295 : sur `IN_WALL`, `TeleportHelper.teleportCitizen(this, level, blockPosition())` puis le dégât est refusé) ; `checkIfValidDamageSource` (l. 1304-1344 : un citoyen de la même colonie ne blesse pas ; un joueur pendant un raid non plus ; **un joueur sans `HURT_CITIZEN` ne fait pas plus de 1 dégât**) ; `job.ignoresDamage` (seuls les gardes et le mineur avec une recherche le surchargent) ; puis `handleDamagePerformed` (l. 1356-1433) :

- dégât plafonné à `getMaxHealth() * 0.2f` (l. 1358), **avant** l'armure (appliquée par `super.hurt`, l. 1408) ;
- sauf chute (`DamageTypes.FALL`) : `performMoveAway(attaquant)` (l. 1379-1382, 1440-1464) :
  - dégât sans attaquant vivant (environnement) : `walkAwayFrom(position, 5, INITIAL_RUN_SPEED_AVOID)` ;
  - attaquant, citoyen non garde : transition vers `CitizenAIState.FLEE`, `callForHelp` (gardes), `walkAwayFrom(attaquant, 15)` ;
- `setLastHurtMob`, menace ajoutée, puis (sauf feu et foudre) usure des armures et modificateur `DAMAGE` (2.0, 1 jour).

Fuite : `EntityAICitizenAvoidEntity` (`MC/core/entity/ai/minimal/EntityAICitizenAvoidEntity.java`, ajouté avec `Monster.class`, `DISTANCE_OF_ENTITY_AVOID = 5`, vitesses 0,8 / 1,1 : `MC/core/entity/ai/workers/CitizenAI.java:68`, `MC/api/util/constant/CitizenConstants.java:121-129`). On n'y entre **que** par `FLEE` (seule occurrence : `EntityCitizen.java:1462`), donc après un coup : le citoyen ne fuit pas un monstre qui ne l'a pas touché. Il cherche un `Monster` vivant en vue dans une boîte de ±5 (±3 en hauteur), s'éloigne de 5 + 7, 15 ou 20 blocs selon sa vie (≥ max − 4, ≥ max / 2, sinon), se croit sauf après 40 contrôles sans monstre (toutes les 5 ticks, soit 10 s) puis revient à son point de départ (l. 13-30, 93-260).

Monstres : avec `mobattackcitizens` (vrai par défaut, `ServerConfiguration.java:178`), chaque `Mob` `Enemy` hors liste noire reçoit `NearestAttackableTargetGoal<EntityCitizen>` en priorité 6 (`MC/core/event/EventHandler.java:133-147`).

Blocage complet : `withTakeDamageOnStuck(0.2f)` (`MC/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:159`) → `entity.hurt(STUCK_DAMAGE, getMaxHealth() * damagePct)` (`MC/core/entity/pathfinding/navigation/PathingStuckHandler.java:299-302`). `stuckdamage` (`sources/minecolonies/src/datagen/generated/minecolonies/data/minecolonies/damage_type/stuckdamage.json`) : `exhaustion 0.1`, `scaling always`, absent de `bypasses_armor` (qui ne liste que `wakeywakey`, `guardpvp`, `pierce`) : **l'armure le réduit**. Ce coup passe lui aussi par `hurt` : plafond de 20 %, fuite « environnement » de 5 blocs, modificateur `DAMAGE`.

Soin : `checkHeal` (`EntityCitizen.java:889-910`) : si la vie est sous le maximum (le tiers s'il est malade) et `getLastHurtByMob() == null` ; 2 si saturation pleine, `saturation / FULL_SATURATION / 2` sous `LOW_SATURATION` (6), sinon 1. `BASE_MAX_HEALTH = 20` (`CitizenConstants.java:84`).

## 2. pre.5 : réponses

### 2.1 Ce qui a changé côté MineColonies depuis `6b3916a1`

Rien sur le fond. `die` n'a bougé que d'une ligne (l. 1547 → 1548) et appelle `getColonyOrRegister()`. Les autres numéros relus : `dropItemHandler` l. 2586 → 2592, `spawnItemStack` l. 2633 → 2639, `getImportantMessageEntityPlayers` l. 1392, `WorkerBuildingModule.onRemoval` rappelle `job.onRemoval()` s'il reste un métier (sans effet ici). Deux **corrections** de la première version :

- le résumé « `%d citizen(s) died` » vient du journal (`computeNews`, 1.6), pas de la statistique `DEATH`, affichée seulement dans l'onglet Statistiques (`lang:2576`) ;
- le modificateur `DEATH` touche **tous** les citoyens (`injectModifier`), et le deuil n'est pas que « famille » : il touche aussi ceux qui **vivent avec** le mort (1.7). HyColony a des logements, donc ce point le concerne.

`ServerConfiguration` n'a **aucune** clé propre à la mort (`grep -i "death|grave"` : rien). Les clés qui la touchent : `mobattackcitizens` (l. 178), `pvp_mode` (l. 182), `initialcitizenamount` (l. 125).

### 2.2 Ce que HyColony a déjà, et ce qui manque

Déjà là :

- **Blessure** : `P/npc/CitizenHurtSystem.java:32-87`, dans le groupe « inspect », donc seulement les dégâts réellement appliqués. Il ignore les causes de `R/hycolony/id-map.json:85` (`Fire`, `Lightning`). Un `Damage.EntitySource` arme la mémoire de l'attaquant (`BodyVitals.hurt`, l. 72-74). Il appelle `HappinessEvents.hurt` (`C/citizen/happiness/HappinessEvents.java:34-45`, `DAMAGE` 2.0 pendant 1 jour, comme MC) et `ArmorWear.onHurt` si la cause use la durabilité. Sa Javadoc (l. 29-30) dit que rien n'arrive tant que le rôle est `Invulnerable`.
- **Vie** : `P/npc/body/BodyVitals.java` lit le stat `Health`, coupe la régénération de Hytale à chaque lecture (`HealthRegenState.setRegenEnabled(false)`, l. 172-181) et garde `HURT_MEMORY_TICKS = 100` (l. 23-24, C-20 : règle de jeu dans le plugin). Le port `C/kernel/port/body/BodyHealth.java:10-14` parle encore de l'« échelle de MC (20) ».
- **Soin** : `C/citizen/food/HungerTicks.java:57-83` (`updateHealing`, `healAmount` 2 / 1 / `saturation / 60 / 2`), sur l'échelle de 20.
- **Feu** : `P/npc/CitizenFireImmunitySystems.java` (`Grant` : effet `Immunity_Fire` ; `Guard` : filtre qui annule les braises), indépendant de `Invulnerable` (l. 28-31), écart demandé (C-21). À la mort, `DeathSystems.ClearEntityEffects` retire tous les effets (`HY/server/core/modules/entity/damage/DeathSystems.java:114-128`) : sans importance pour un mort.
- **Blocage** : `C/kernel/nav/StuckHandler.java:25-31` : écart marqué, « ours take none » (aucun dégât).
- **Rôle** : `R/Server/NPC/Roles/HyColony/HyColony_Citizen.json` : `"MaxHealth": 20` (l. 32), `"Invulnerable": true` (l. 33, et non l. 15-16 comme le dit l'audit), contrôleurs `Walk` et `Dive`, `"RelaxedConstraints": ["Wade", "Breathe"]` pour `HyColonySeek` (l. 44). Ni `PickupDropOnDeath`, ni `DropList`, ni `BreathesInWater`.
- **Corps** : `P/npc/CitizenBodyLifecycleSystem.java` : lie un corps chargé (`LOAD`) au cœur via `world.execute` (l. 38-65) ; tout retrait d'une entité suivie appelle `onBodyUnloaded` (l. 83-102). `HytaleCitizenBodies.despawn` désuit d'abord le corps puis le retire avec `REMOVE` dans `world.execute` (`P/adapter/HytaleCitizenBodies.java:232-244`) : un despawn volontaire ne repasse donc pas par `onBodyUnloaded`. `isAlive` = « le corps est suivi » (l. 143-145).
- **Cœur** : `C/citizen/CitizenManager.java` (un par colonie) : `onBodyLoaded` (l. 248-261 : despawn d'un corps sans citoyen), `onBodyUnloaded` (l. 296-304), `updateBodyIfNecessary` (l. 196). Aucune méthode ne retire un citoyen vivant (seul retrait : le retour arrière de `spawnForced`, l. 178).
- **Libération** : `C/job/WorkerModule.java:130-137` (`fire` : retire le travailleur, annule ses requêtes `cancelAllFrom(requesterId, citizenId)`, puis `free`), `free` l. 145-152 (MC `AbstractJob.onRemoval` : métier, armure rendue à l'inventaire, mains vides, IA). `C/citizen/home/LivingModule.java:76` (`removeCitizen`, MC `LivingBuildingModule.onRemoval`). `C/logistics/warehouse/CourierAssignmentModule.java` et `C/app/action/CourierHiring.java:33` (`fire`).
- **Inventaire** : 27 cases (`C/citizen/CitizenData.java:23`) et 4 armures (`C/citizen/inventory/CitizenEquipment.java`, `ARMOR_SLOTS = 4`), portées par le corps dans `InventoryComponent.Armor` (lu par `P/npc/body/BodyDefense.java:24`).
- **Chute d'objets** : le port existe : `WorldBlocks.drop(BlockPos, List<ItemAmount>)` (`C/kernel/port/WorldBlocks.java:40-41`), implémenté par `P/adapter/HytaleWorldBlocks.java:221-230`, puis `P/adapter/HytaleBlocks.java:62-74` (`ItemComponent.generateItemDrops` au centre bas de la case, `store.addEntities(…, AddReason.SPAWN)`). Il appelle `store.addEntities` **directement** : depuis un système de mort (store en cours de traitement), il faut l'appeler dans `world.execute`.
- **Bonheur et deuil (SP4b)** : `HappinessIds.DEATH = "death"` existe (`C/citizen/happiness/HappinessIds.java:16`) mais rien ne l'ajoute. Pas de gestionnaire de deuil : `C/citizen/CitizenAI.java:31-32` dit « sickness, mourning and raids are not ported ».
- **Journal** : `colony.log().add/addAt(type, day, params…)` (`C/colony/EventLog.java:23, 28`) ; `Newcomers` écrit `citizenSpawned` (`C/citizen/Newcomers.java:21`). La mairie n'affiche que les types de `C/app/view/TownHallViews.java:33` (`citizenSpawned`, `buildingBuilt`…) : `citizenDied` devra y entrer. Pas de résumé du soir (`computeNews`) : aucun événement « résumé » n'existe encore.
- **Statistiques** : aucune (`C/app/view/TownHallStats.java:18`, « no statistics manager yet »).
- **Messages** : le motif « propriétaire + membres `RECEIVE_MESSAGES` » existe (`C/citizen/CitizenArrival.java:75-90`, `C/citizen/sleep/SleepNotice.java:41-46`, `C/construction/workorder/BuildCompletion.java:44-48`). L'union avec les gestionnaires (`isColonyManager`) de `forManagers` n'a pas d'aide commune.
- **Permissions** : `Action.HURT_CITIZEN(26)` et `MAP_DEATHS(29)` (`C/colony/permission/Action.java:27, 30`), rangs par défaut (`Permissions.java:92, 114, 123`). Le filtre `HURT_CITIZEN` n'est pas branché.
- **API** : événements publics `api/src/main/java/dev/hycolony/api/event/` : `CitizenSpawned`, pas de `CitizenDied`.
- **Fenêtres** : `P/ui/citizen/CitizenInventoryWindows.java:106` suppose qu'un citoyen ne part qu'avec sa colonie (« no death yet »).

Ce qui manque : la détection de la mort et `onCitizenDied` ; le retrait (MC `removeCivilian`) ; le message ; le journal ; le modificateur `DEATH` sur tous ; le deuil (gestionnaire, état `MOURN`, IA de deuil) ; la statistique `DEATH` et le résumé du soir ; le filtre `HURT_CITIZEN` ; le plafond de 20 % ; l'étouffement (`IN_WALL`) ; la fuite (`performMoveAway`, `FLEE`) ; l'hostilité des monstres et la clé `MobAttackCitizens` ; les dégâts de blocage ; l'échelle de vie 100.

### 2.3 Hytale pre.5 : comment un PNJ meurt

- **Le signal** : l'ajout du composant `DeathComponent` (`HY/server/core/modules/entity/damage/DeathComponent.java`), enregistré **avec un codec** sous `"Death"` (`DamageModule.java:47`), donc sauvé avec l'entité. API : `getComponentType()` (l. 87), `getDeathCause()` (l. 99-100, `@Nullable`, depuis l'id sauvé), `getDeathInfo()` (l. 153-156, le `Damage` complet, **transitoire**, `null` après un rechargement), `tryAddComponent(CommandBuffer|Store, Ref, Damage)` (l. 195-206, une seule fois).
- **Qui l'ajoute** (`grep DeathComponent.tryAddComponent`) :
  - `DamageSystems.ApplyDamage`, quand la vie tombe au minimum (`HY/server/core/modules/entity/damage/DamageSystems.java:160-182`) ; il arrondit aussi le dégât (`Math.round`) ;
  - `DamageSystems.OutOfWorldDamage` (l. 1317-1370) : sous y = 0, 50 dégâts `OutOfWorld` par seconde par le circuit normal ; **sous y = −32, `DeathComponent` directement**, sans filtre ;
  - `EntityStatsSystems` (l. 124-129) : vie au minimum par une variation de stat, cause `Command` ;
  - `KillCommand`, `HardcoreGameOver`, `ActionDie` (action de PNJ, `Physical`), `RemoveEntitiesEffect`, `DeployableOwnerComponent`.
- **Ce qui se passe ensuite** :
  - `DeathSystems.ClearHealth` met la vie à 0, `ClearInteractions`, `ClearEntityEffects`, `DeathAnimation` joue l'animation de mort de la cause, sinon `Death` (`DeathSystems.java:96-111, 114-260` ; `Entity.DefaultAnimations.getDeathAnimationIds`, `HY/server/core/entity/Entity.java:314-321`) ;
  - PNJ : `NPCSystems.OnDeathSystem` ajoute `DeferredCorpseRemoval(DeathAnimationTime, DeathParticles)` (`HY/server/npc/systems/NPCSystems.java:474-498`) ; `TickCorpseRemoval` le décompte (`DeathSystems.java:811-840`) ; `CorpseRemoval` retire le cadavre avec **`RemoveReason.REMOVE`** (l. 181-229), aussitôt s'il n'y a pas de `DeferredCorpseRemoval`, avec les particules sinon ;
  - `DeferredCorpseRemoval` est enregistré **sans codec** (`DamageModule.java:48`) : un cadavre déchargé puis rechargé n'en a plus et `CorpseRemoval` le retire au premier tick (`SpawnedDeathAnimation` rejoue l'animation au chargement, l. 747-775) ;
  - fil de mort : `NPCSystems.KillFeedDecedentEventSystem` **annule** le message du mort pour tout PNJ (l. 315-338) : la mort d'un citoyen n'est pas annoncée aux joueurs du monde (`DeathSystems.KillFeed`, l. 350-402).
- **Ce qui tombe** (`NPCDamageSystems.DropDeathItems`, `HY/server/npc/systems/NPCDamageSystems.java:159-242`) : si `itemsLossMode == ALL` (défaut d'un PNJ, `DeathComponent.java:82`) et à la fin du `DeferredCorpseRemoval` (sauf `DropDeathItemsInstantly`), le `InventoryComponent.Storage` si `PickupDropOnDeath`, plus la `DropList`, à `position + (0, 1, 0)`. Notre rôle n'a ni l'un ni l'autre : **vanilla ne fait rien tomber**, et ni l'`Armor` du corps ni sa `Hotbar` (l'objet affiché en main) ne tombent. Pas de double chute avec l'inventaire du cœur.
- **Réglages du rôle** (`HY/server/npc/role/builders/BuilderRole.java`) : `MaxHealth` (obligatoire, l. 262), `DropList` (l. 405), `Invulnerable` (l. 719, défaut `false`), `BreathesInAir` / `BreathesInWater` (l. 729-730, `true` / `false`), `PickupDropOnDeath` (l. 731), `DeathAnimationTime` (l. 734-742, 1,5 s), `DeathParticles` (l. 744, `Effect_Death`), `DropDeathItemsInstantly` (l. 754), `DeathInteraction` (l. 763), `DespawnAnimationTime` (l. 773, 0,8 s).
- **L'événement à écouter** : un `DeathSystems.OnDeathSystem` (`DeathSystems.java:404-424`, `RefChangeSystem<EntityStore, DeathComponent>`, `componentType()` fourni, `onComponentSet`/`onComponentRemoved` vides). Il suffit de surcharger `getQuery()` (`HyColonyComponents.citizenTag()`) et `onComponentAdded(Ref, DeathComponent, Store, CommandBuffer)`. `onComponentAdded` n'est appelé que par `Store.datachunk_addComponent` (`HY/component/Store.java:2349-2373`), donc à l'**ajout**, jamais au chargement d'une entité qui l'a déjà. Ce rappel tourne pendant le traitement du store : toute écriture structurelle doit passer par `world.execute` (comme `CitizenBodyLifecycleSystem.bind`).
- **Mort ou déchargement** : `RemoveReason` n'a que `REMOVE`, `UNLOAD`, `BUILDER_TOOLS_UNDO` (`HY/component/RemoveReason.java`). Le retrait du cadavre arrive avec `REMOVE`, comme notre `despawn`, et un déchargement avec `UNLOAD`. La seule distinction fiable est donc le `DeathComponent` vu par le système de mort, pas `onEntityRemove`. Un chunk déchargé ne produit jamais de mort.

### 2.4 Retirer `Invulnerable` : ce qui change

- `RoleBuilderSystem` **ajoute** le composant `Invulnerable` si `role.isInvulnerable()` (`HY/server/npc/systems/RoleBuilderSystem.java:151-153`, `holder.ensureComponent`) et ne le retire jamais. Or `Invulnerable` est enregistré **avec un codec** (`HY/server/core/modules/entity/EntityModule.java:379`, `Invulnerable.CODEC`) : il est sauvé avec l'entité. **Les corps déjà sauvés resteront invulnérables** après le retrait de la clé du rôle. Le plugin doit retirer le composant des corps chargés : par exemple un `HolderSystem`/`RefSystem` sur `citizenTag()` qui fait `tryRemoveComponent(ref, Invulnerable.getComponentType())`, comme `EntityInvulnerableCommand` (`HY/server/core/command/commands/world/entity/EntityInvulnerableCommand.java:29`). Autre voie : faire réapparaître les corps (`respawnBody`). **[in-game]**
- `DamageSystems.FilterUnkillable` (`DamageSystems.java:1134-1172`) annule tout dégât si l'entité a `Invulnerable`, `Intangible` ou `DeathComponent`, ou un effet qui rend invulnérable. `DamageSystems.InvulnerableBreathing` (l. 1259-1315) recalcule la respiration quand `Invulnerable` change ; `Role.canBreathe` rend `true` pour un rôle invulnérable (`HY/server/npc/role/Role.java:1142-1152`).
- Une fois `Invulnerable` retiré, les citoyens prennent :
  - les **coups** (filtre PNJ : `NPCDamageSystems.FilterDamageSystem`, l. 244-283, n'annule que les coups d'un groupe de `DisableDamageGroups`, vide par défaut, `HY/server/npc/asset/builder/SupportConfigBuilder.java:65-76` ; et ceux du même troupeau, `DisableDamageFlock` vrai par défaut, l. 56-63 ; `CombatSupport.getCanCauseDamage`, `HY/server/npc/role/support/CombatSupport.java:86-106`) ;
  - les **chutes** : `DamageSystems.FallDamageNPCs` (l. 882-960), si le monde les active (`isFallDamageEnabled`), à l'atterrissage hors fluide au-delà de `MinFallSpeedToEngageRoll` : `floor(maxHealth / 100 × ((0,58 × (v − vmin))² + 10))`, cause `Fall`, soit **au moins 10 % de la vie maximale** par chute qui compte ;
  - **l'étouffement et la noyade** (2.5) ;
  - **le vide** : 50 par seconde sous y = 0, mort directe sous −32 ;
  - les **pièges** (pics, mâchoires), les plantes (`Environmental`), le poison ; le feu reste annulé par `Immunity_Fire` (C-21).
- `CombatConfig.DisableNPCIncomingDamage` (défaut `false`, `HY/server/core/asset/type/gameplay/CombatConfig.java:52-55, 74`) annule tout dégât fait à un PNJ (`DamageSystems.FilterNPCWorldConfig`, l. 1070-1100) : un serveur qui l'active rend aussi nos citoyens immortels.

### 2.5 Respiration, étouffement et noyade

- **Nos corps ont un `BreathingComponent`** : `NPCSystems.AddedSystem` l'assure pour tout PNJ ajouté (`HY/server/npc/systems/NPCSystems.java:254`).
- `DamageSystems.BreathingSystem` (l. 578-620, toutes les secondes, groupe « gather ») appelle `EntityUtils.processEntityBreathing` (`HY/server/core/entity/EntityUtils.java:99-121`). Celui-ci lit le matériau et le fluide à hauteur des yeux (`LivingEntity.getPackedMaterialAndFluidAtBreathingHeight`, `HY/server/core/entity/LivingEntity.java:51-56` ; `BlockMaterial` = `Empty` ou `Solid`). Quand ils changent, il invoque un **`BreathingCheckEvent`** (`HY/server/core/event/events/ecs/BreathingCheckEvent.java`) sur l'entité. Pour un PNJ, `NPCSystems.BreathingCheckEventSystem` (l. 285-313) y écrit `role.canBreathe(material, fluid)` : dans l'air oui, dans un bloc solide non, dans un fluide seulement si `BreathesInWater` (faux pour nous).
- L'oxygène : `zip:Server/Entity/Stats/Oxygen.json` (100, −3 toutes les 0,5 s en étouffant, +25 toutes les 0,5 s sinon) : **environ 16,7 s** de réserve. Ensuite `DamageSystems.CanBreathe` (l. 622-683) inflige, toutes les secondes, **10 `Drowning`** dans un fluide ou **20 `Suffocation`** sinon. Sur 100 points : 5 s d'étouffement, 10 s de noyade.
- `zip:Server/Entity/Damage/Suffocation.json` : enfant de `Environment` (`BypassResistances: true`, `DurabilityLoss: true`, `zip:Server/Entity/Damage/Environment.json`).
- **Porter `handleInWallDamage`** (MC : téléporter hors du mur, ne jamais subir `IN_WALL`), deux points d'accroche :
  1. un `DamageEventSystem` du groupe « filter » (`DamageModule.get().getFilterDamageGroup()`, `DamageModule.java:122-124`), requête `citizenTag()`, comme `CitizenFireImmunitySystems.Guard` : si `event.getCause()` est `Suffocation` (`DamageCause.SUFFOCATION`, `DamageCause.java:81`), `event.setCancelled(true)` et téléportation du corps ;
  2. plus proche du `IN_WALL` de MC, qui frappe dès que la tête est dans un bloc : un `EntityEventSystem<EntityStore, BreathingCheckEvent>` sur `citizenTag()` qui téléporte quand `getBreathingMaterial() == Solid && getFluidId() == 0`, sans attendre l'épuisement de l'oxygène. **[in-game]** : ordre par rapport au système PNJ, cas des demi-blocs et des portes.
  Le filtre (1) suffit pour la fidélité (aucun dégât d'étouffement), le (2) évite 17 s coincé. La téléportation existe : `BodyTeleport.teleport(ref, cible)` (`P/npc/BodyTeleport.java:49`) cherche la position accessible la plus proche (`MotionController.translateToAccessiblePosition`, ±10 blocs) et passe déjà par `world.execute`. Appelée avec la position actuelle du corps, elle joue le rôle de `TeleportHelper.teleportCitizen(this, level, blockPosition())` (`MC/core/util/TeleportHelper.java:36-63`, qui réveille aussi un dormeur, comme `CitizenAI.teleport`, `C/citizen/CitizenAI.java:143-151`).
- **La noyade reste à Hytale**, comme MC. Avec `"RelaxedConstraints": ["Wade", "Breathe"]`, `HyColonySeek` accepte des chemins où l'on ne respire pas : un citoyen qui nage longtemps sous l'eau pourra se noyer (**[in-game]**).

### 2.6 Les monstres et les citoyens (C-14)

- **Lecture de l'attitude** : `AttitudeView` (`HY/server/npc/blackboard/view/attitude/AttitudeView.java:21-68`) interroge des fournisseurs par priorité croissante. Le premier qui ne rend pas `null` l'emporte :
  - 0 : surcharge par PNJ (`WorldSupport.getOverriddenAttitude`, `overrideAttitude(target, attitude, duration)`, `HY/server/npc/role/support/WorldSupport.java:209-217`) ;
  - 100 : même lignée d'apparition → `FRIENDLY` ;
  - 200 : la carte des groupes d'attitude (`NPCPlugin.get().getAttitudeMap()`, `NPCPlugin.java:1458`) ;
  - `Integer.MAX_VALUE` : `DefaultPlayerAttitude` (défaut `HOSTILE`) ou `DefaultNPCAttitude` (défaut `NEUTRAL`) du rôle **source** (`SupportConfigBuilder.java:86-105`).
- `AttitudeMap.getAttitude` (`HY/server/npc/blackboard/view/attitude/AttitudeMap.java:34-61`) prend le groupe d'attitude du rôle source (clé de rôle `"AttitudeGroup"`, `SupportConfigBuilder.java:106-114`, par ex. `zip:Server/NPC/Roles/Creature/Hunt/Bear_Voidtaken.json:28`). Ce groupe est un asset `zip:Server/NPC/Attitude/Roles/**` qui associe des **groupes de PNJ** (`zip:Server/NPC/Groups/**`, ensembles de rôles) à une attitude (`Goblin.json` : `Hostile: [Spiders, Skeleton]`). La cible est retrouvée par l'index de son rôle. `AttitudeMap` n'a **pas** de fournisseur à ajouter : elle se reconstruit au chargement des assets (`NPCPlugin.java:1271-1300`) et ne propose que `updateAttitudeGroup`.
- Aujourd'hui, aucun groupe d'attitude ne cite nos citoyens, dont le rôle n'est dans aucun groupe de PNJ. Les monstres tombent donc sur `DefaultNPCAttitude` = `NEUTRAL`.
- **Trois moyens**, du plus sûr au moins sûr :
  1. **Un fournisseur sur `AttitudeView`** : `PrioritisedProviderView.registerProvider(int priority, T provider)` est public (`HY/server/npc/blackboard/view/PrioritisedProviderView.java:13-16`) ; `IAttitudeProvider` = `Attitude getAttitude(Ref source, int sourceRoleIndex, Ref target, ComponentAccessor)` (`IAttitudeProvider.java`). La vue est un singleton par monde, créé par `Blackboard.init(world)` (`HY/server/npc/blackboard/Blackboard.java:39-45`), lui-même appelé par `BlackboardSystems.InitSystem.onSystemAddedToStore` (`HY/server/npc/systems/BlackboardSystems.java:82-97`). On l'obtient par `store.getResource(Blackboard.getResourceType()).forEachView(AttitudeView.class, v -> v.registerProvider(…))`, depuis un `StoreSystem` du plugin ordonné après `InitSystem`. Le fournisseur, en priorité **150** (après la surcharge par PNJ et la lignée, avant la carte vanilla), rend `HOSTILE` si la cible porte `CitizenTag` et que le rôle source est dans `HyColony_Hostile`, sinon `null`. Le test d'appartenance existe déjà : `TagSetPlugin.get(NPCGroup.class).tagInSet(groupIndex, roleIndex)` (`P/npc/spawn/HostileSpawns.java:77-81`). Il couvre tous les rôles du groupe, y compris ceux sans `AttitudeGroup`, ne remplace aucun asset vanilla et peut suivre la clé `MobAttackCitizens`. **Recommandé.**
  2. **Surcharger les groupes d'attitude vanilla** depuis notre pack : un asset de même clé chargé plus tard remplace l'asset vanilla en entier, sans fusion ni avertissement (`barrel-recipe.md`, `DefaultAssetMap.java:303-304`). Il faudrait recopier chaque groupe (`Goblin`, `Skeleton`, `Trork`, `LivingWorld/Aggressive`…) en y ajoutant un groupe de PNJ `HyColony_Citizens` (`Server/NPC/Groups/HyColony/HyColony_Citizens.json`, `{"IncludeRoles": ["HyColony_Citizen"]}`, format de `zip:Server/NPC/Groups/Intelligent/Aggressive/Goblin/Goblin_ChaseNPCs.json`). C'est fragile (mises à jour de Hytale, autres mods), ça ne couvre que les rôles qui ont un `AttitudeGroup`, et on ne peut pas suivre la clé de config.
  3. `WorldSupport.overrideAttitude` par paire et par durée : inadapté (par PNJ, temporaire).
- **Le monstre attaquera-t-il ?** Le capteur standard (`zip:Server/NPC/Roles/_Core/Components/Sensors/Component_Sensor_Standard_Detection.json:108-205`) détecte aussi les PNJ (`"Type": "Mob"`, `GetNPCs` vrai) et les classe par attitude. Mais il ne le fait que si un joueur est à moins de 50 blocs (`Component_Sensor_NPC_Detection_Player_LOD.json`, `NPCDetectionPlayerLODRange` 50). Les gobelins attaquent déjà des PNJ hostiles (araignées). Que chaque rôle de `HyColony_Hostile` passe à l'attaque contre un PNJ `HOSTILE` dépend de sa machine d'états : **[in-game]**.
- **Fuir ou se défendre** : un citoyen non garde **fuit, après un coup seulement** (1.11). Rien n'est porté (`grep flee|walkAwayFrom` dans `C/` : rien). Il faudra dans le cœur : `performMoveAway` (s'éloigner de 5 blocs après un dégât sans attaquant, sauf chute) et l'état `FLEE` avec `EntityAICitizenAvoidEntity` (monstre en vue à ±5 blocs, ±3 en hauteur, 10 s de calme). Un port devra trouver « le monstre le plus proche en vue » (membre de `HyColony_Hostile`, C-12) et « un point à N blocs à l'opposé ». Les gardes, qui se défendent, ne sont pas portés (C-16).

### 2.7 Échelle de vie (`MaxHealth: 100`) et fenêtres

- `zip:Server/Entity/Stats/Health.json:2-4` : `InitialValue` 100, `Max` 100. `BalancingInitialisationSystem` (`HY/server/npc/systems/BalancingInitialisationSystem.java:52-66`) pose à **chaque** ajout d'un PNJ (y compris un chargement) le modificateur `NPC_Max` = `MaxHealth − 100`, et ne remplit la vie qu'à l'apparition (`SPAWN`). Avec `MaxHealth: 100`, le modificateur vaut 0 ; un corps déjà sauvé à 20/20 se retrouve à **20/100** et remonte par les soins du cœur (**[in-game]**).
- Fenêtres :
  - **cœurs** de la fenêtre du citoyen : `C/app/view/CitizenViews.java:100-114` convertit déjà la part de vie en 20 points de MC (`healthPercent × 20 / 100`, écart marqué « Hytale world »), et `C/app/citizen/HealthBar.java` dessine les 10 cœurs de MC. **Indépendant de l'échelle.**
  - **libellés « vie / max »** : le panneau d'inventaire (`C/app/view/CitizenInventoryView.java:31-36`, affiché `P/ui/citizen/CitizenSidePanel.java:33`) et l'onglet Citoyens de la mairie (`C/app/view/TownHallViews.java:99-105`, affiché `P/ui/townhall/TownHallCitizensTab.java:97`) prennent la vie **brute** du corps. Sans corps, ils montrent `MC_MAX_HEALTH` = 20 (`C/citizen/CitizenData.java:27`). Avec `MaxHealth: 100`, un citoyen vivant afficherait « 100/100 » et un citoyen sans corps « 20/20 ». Pour rester sur l'échelle de MC comme les cœurs, il faut convertir ces deux libellés comme `CitizenViews.health`.
- À mettre à l'échelle (C-18) : `HungerTicks.healAmount` (× `maxHealth / 20`), le seuil du filtre `HURT_CITIZEN` (1 → 5), la Javadoc de `BodyHealth` et de `BodyVitals` (« MaxHealth 20, MC's scale »). Restent relatifs, donc inchangés : le plafond de 20 % par coup, les 20 % du blocage, les seuils de fuite (`max − 4` est absolu chez MC : × 5 aussi, soit `max − 20`).

### 2.8 `NoDamageTaken` (15 s) : temps de quel horloge ?

- `zip:Server/Entity/Stats/Health.json:9-28` : régénération « NPC » de 5 % toutes les 0,5 s si `Alive`, pas joueur, `NoDamageTaken` (`Delay: 15`) et `RegenHealth`.
- `NoDamageTakenCondition.eval0` (`HY/server/core/modules/entity/condition/NoDamageTakenCondition.java:32-37`) compare `DamageDataComponent.getLastDamageTime()` à l'instant `currentTime` passé par `EntityStatsSystems` (`HY/server/core/modules/entitystats/EntityStatsSystems.java:428`, `store.getResource(TimeResource.getResourceType()).getNow()`). `DamageSystems.TrackLastDamage` écrit ce même `TimeResource.getNow()` à chaque dégât, de toute cause (`DamageSystems.java:1621-1651`).
- `TimeResource` (`HY/server/core/modules/time/TimeResource.java`) est avancé par `TimeSystem.tick` de `dt` à chaque tick du monde (`TimeSystem.java:18-22`) et sauvé avec le monde (`TimeModule.java:35`, `"Time"`). Ce n'est ni l'horloge murale ni l'heure du jour (`WorldTimeResource`) : c'est le **temps simulé du monde**, en secondes. Il égale le temps réel quand le monde tient sa cadence, s'arrête avec lui et ne suit pas l'accélération du cycle jour/nuit.
- Conséquence pour C-20 : 15 s de temps simulé = **300 ticks du cœur** (20 par seconde, cadencés sur les ticks du monde). La proposition de l'audit tient.

### 2.9 La cause de dégât la plus proche de `STUCK_DAMAGE`

Causes pre.5 (`zip:Server/Entity/Damage/`) : `Bludgeoning`, `Command`, `Crush`, `Drowning`, `Earth`, `Elemental`, `Environment`, `Environmental`, `Fall`, `Fire`, `Ice`, `Lightning`, `OutOfWorld`, `Physical`, `Poison`, `Projectile`, `Slashing`, `Suffocation`, `Water`, `Wind`. Aucune « blocage ».

- **Pas `Suffocation`** : le portage de `handleInWallDamage` (2.5) l'annule, il annulerait donc aussi les dégâts de blocage. Elle ignore en plus l'armure (`Environment`, `BypassResistances: true`), alors que MC laisse l'armure réduire `STUCK_DAMAGE` (1.11).
- **`Crush`** (`zip:Server/Entity/Damage/Crush.json` : enfant de `Physical`, qui ne contourne pas les résistances, use la durabilité, `zip:Server/Entity/Damage/Physical.json`) : l'armure la réduit, comme chez MC (`DamageSystems.ArmorDamageReduction`, l. 348-400, lit l'`InventoryComponent.Armor` de tout être vivant, donc nos corps). Le sens (écrasé, coincé) est le plus proche. **Recommandé.**
- Alternative : `Environment`, qui contourne l'armure (écart de plus).
- Mise en œuvre : `DamageSystems.executeDamage(ref, commandBuffer, new Damage(source, DamageCause, amount))` (`DamageSystems.java:125-137`), `Damage(Source, DamageCause, float)` (`HY/server/core/modules/entity/damage/Damage.java:63`), source `Damage.NULL_SOURCE` (l. 54) ou `new Damage.EnvironmentSource(deathMessageKey)` (l. 184-197). `DamageCause.getAssetMap().getIndex("Crush")` comme `CitizenFireImmunitySystems.resolvePhysicalCauseIndex`. Le dégât traverse ensuite nos filtres et `CitizenHurtSystem` (modificateur `DAMAGE`), comme chez MC où il passe par `hurt`.

### 2.10 Lâcher l'inventaire au sol, et l'XP

- **Objets** : le port existe, `WorldBlocks.drop(BlockPos, List<ItemAmount>)` (2.2). Hytale : `ItemComponent.generateItemDrops(accessor, stacks, position, rotation)` (`HY/server/core/modules/entity/item/ItemComponent.java:225-240`). Une pile tombe droit (vitesse Y 3,25) ; plusieurs partent en cercle de rayon 3. `generateItemDrop` (l. 242-…) rend `null` pour une pile vide ou invalide, et pose `Intangible` et un `DespawnComponent`. La durée de vie : `computeLifetimeSeconds` (l. 147-152 : celle de l'objet, sinon celle du `GameplayConfig`, sinon 120 s), avec `zip:Server/GameplayConfigs/Default.json:17-18` (`ItemEntity.Lifetime: 600.0`, **10 min** ; Minecraft : 5 min). Ajout par `Store.addEntities(Holder[], AddReason)` (`HY/component/Store.java:502`) ou `CommandBuffer.addEntities` (`HY/component/CommandBuffer.java:70`). Modèle vanilla : `NPCDamageSystems.DropDeathItems` lâche à `position + (0, 1, 0)` avec la rotation de la tête.
- Ce qu'on lâche : les 27 cases **et** les 4 armures. MC met les armures dans la tombe (branche « dans la colonie », la plus courante), et c'est la tombe que nous remplaçons par la chute. `Deviation from MC: no grave; the inventory and the armour drop where the citizen died`.
- **XP** : Hytale n'a **aucun** système d'expérience : `grep -rli "experience|xporb"` sur tout le décompilé ne rend rien, et le zip n'a aucune entrée `experience` ou `xp_orb`. `dropExperience` est sans équivalent : `Deviation from MC (Hytale world): MC's XP orbs worth the citizen's total skill XP → nothing, Hytale has no experience`. Les compétences disparaissent avec le citoyen, comme chez MC.

### 2.11 Messages de mort de Hytale

Les messages de cause de Hytale sont à la deuxième personne (« `You fell to your death!` », « `You suffocated!` » : `zip:Server/Languages/en-US/server.lang:5740-5759`, clés `general.deathCause.*`). Ils ne remplacent pas le « X was slain by Y » de MC. HyColony écrira ses propres clés par cause (`citizen.deathCause.<id de cause>`, avec le nom de l'attaquant pour une `Damage.EntitySource`). La cause se lit par `DeathComponent.getDeathCause().getId()`, l'attaquant par `getDeathInfo().getSource()` (`Damage.EntitySource.getRef()`), au moment de la mort seulement (transitoire).

## 3. Découpage recommandé

Ordre : C-18 (échelle) d'abord, puis la mort avec C-14, C-19, C-20, C-23 et le filtre `HURT_CITIZEN`, dans la même modification que le retrait de `Invulnerable`.

### Cœur (`core/`, testable avec des `Fake*`)

- **`CitizenDeath`** (MC `EntityCitizen.die`), appelé par `CitizenManager.onCitizenDied(int citizenId, BodyId body, Vec3 at, DeathCause cause)`. On passe l'**id du citoyen lu sur le `CitizenTag`**, pas seulement le corps, pour qu'un déchargement traité avant la mort, dans le même tick, n'empêche pas de retrouver le citoyen. Inconnu : rien. Ordre MC :
  1. modificateur `DEATH` (3.0, valeur 0, 3 jours) sur **tous** les citoyens si le mort n'est pas garde (`isGuard()` est toujours faux, C-16) ;
  2. deuil : `updateCitizenMourn` (co-résidents, pas de famille dans HyColony) ;
  3. statistique `DEATH` du jour (nouveau compteur journalier, affiché dans l'onglet Statistiques de la mairie, MC `lang:2576`) ;
  4. chute : `ctx().ports().blocks().drop(BlockPos.of(at), inventaire + armures)`, puis inventaire et armures vidés ;
  5. message (`citizen.died` + `citizen.deathCause.<id>` + direction `calcDirection` depuis `colony.center()` + deuil) aux membres `RECEIVE_MESSAGES` **et** aux gestionnaires (MC `forManagers`) ;
  6. `WorkerModule.fire` sur le bâtiment de travail, `CourierHiring.fire` si coursier, `LivingModule.removeCitizen` sur le logement (MC `removeCivilian` sur chaque module), **sans** libérer l'ordre de travail ;
  7. `citizens.remove`, `bodies.remove`, `ais.remove` ;
  8. journal `citizenDied` (nom, cause), ajouté à la liste de `TownHallViews.java:33`, compté dans le résumé du soir s'il est porté (`computeNews`) ;
  9. événement `CitizenDied(colony, data)` sur `ctx().bus()`, puis événement d'API `CitizenDied` (`ApiEvents`, nouveau type public : `apiDump`) ; `colony.markDirty()`.
- **Deuil** : `MournState` dans `CitizenData` (noms, drapeau, sauvegardés : migration `add-migration`), bascule à `onWakeUp` (MC l. 690-707), état `MOURN` dans `CitizenAI.decideAiTask` après le repas et avant la pluie, `MournAI` (regarder un voisin, marcher vers la mairie au-delà de 15 blocs, errer 10 blocs). Le cimetière est sans objet.
- **Blessure** (MC `hurt`) : règles de `HURT_CITIZEN` (seuil 5 sur 100), plafond de 20 % de la vie maximale, `performMoveAway` et `FLEE`/`AvoidEntity`. Les décisions vont dans le cœur, et le plugin ne fait que transmettre. Le cœur rend « annuler / plafonner à N » pour un dégât décrit par un record (cause, montant, attaquant joueur ou PNJ hostile).
- **Dégâts de blocage** : `StuckHandler` renvoie déjà `TELEPORT`. L'appelant inflige en plus 20 % de la vie maximale par un nouveau `BodyHealth.damage(BodyId, double amount)`, sous `Crush` côté plugin. Corriger la Javadoc (l. 25-31).
- **Échelle** : `healAmount × maxHealth / 20` ; `HURT_MEMORY_TICKS` = 300 dans le cœur (C-20) ; libellés « vie / max » convertis en 20 points (2.7).
- Tests d'abord : mort d'un corps dans `FakeCitizenBodies` → citoyen retiré, objets et armures lâchés, requêtes annulées, logement libéré, message envoyé, journal écrit, `DEATH` sur les autres, deuil des co-résidents au réveil, citoyen non recréé par `onColonyTick`. Déchargement (`onBodyUnloaded`) inchangé.

### Plugin (`plugin/`)

- **`CitizenDeathSystem extends DeathSystems.OnDeathSystem`**, requête `citizenTag()`. Dans `onComponentAdded` (`RuntimeException` attrapée, SEVERE), on lit `CitizenTag`, `TransformComponent`, la cause et l'attaquant, on désuit le ref (comme `despawn`, pour que le retrait du cadavre ne repasse pas par `onBodyUnloaded`), puis `world.execute(() -> manager.onCitizenDied(...))`. Le cadavre reste à vanilla (animation 1,5 s, `CorpseRemoval`).
- **`HytaleBlocks.drop`** appelé hors traitement du store (déjà vrai via `world.execute`).
- **Rôle** : retirer `"Invulnerable": true`, passer à `"MaxHealth": 100` ; **retirer le composant `Invulnerable` des corps sauvés** (2.4).
- **Filtres** (groupe « filter », `CitizenFireImmunitySystems.Guard` comme modèle) : `HURT_CITIZEN` + plafond (ordonné **avant** `DamageSystems.ArmorDamageReduction` par `SystemDependency`, puisque MC plafonne avant l'armure) ; `Suffocation` annulée + `BodyTeleport.teleport(position actuelle)`. En option, le `BreathingCheckEvent` pour téléporter dès l'entrée dans un bloc.
- **Attitude** : `StoreSystem` après `BlackboardSystems.InitSystem`, fournisseur `AttitudeView` en priorité 150 (2.6), selon la clé `MobAttackCitizens` (défaut `true`, `Gameplay`, comme MC).
- **Fuite** : un port « monstre hostile le plus proche en vue » (réutilise `HostileSpawns`) et « s'éloigner de ».
- **Fenêtres** : fermer les pages du citoyen mort (`CitizenInventoryWindows.java:106`).
- Clés en-US et fr-FR (skill `add-lang-key`) : `citizen.died`, `citizen.deathCause.*`, directions, `citizen.mourning`, `log.citizenDied`, statistique des morts.

## 4. Incertain [in-game]

- **[in-game]** Retrait de `Invulnerable` sur les corps déjà sauvés : le composant reste tant que le plugin ne l'enlève pas (vérifié dans le code, effet en jeu non observé).
- **[in-game]** Un rôle de `HyColony_Hostile` attaque-t-il vraiment un PNJ `HOSTILE` ? Cela dépend de chaque machine d'états, et la détection des PNJ n'a lieu qu'avec un joueur à moins de 50 blocs.
- **[in-game]** Ordre de notre fournisseur d'attitude face aux vues recréées (changement de monde, rechargement d'assets) : `SingletonBlackboardViewManager.clear()` ne fait rien et la vue vit avec le store, mais ce n'est pas observé.
- **[in-game]** `BreathingCheckEvent` : ordre face à `NPCSystems.BreathingCheckEventSystem`, demi-blocs et portes à hauteur des yeux.
- **[in-game]** Noyade en suivant un chemin `Breathe` relâché.
- **[in-game]** Rendu de `generateItemDrops` pour une trentaine de piles (cercle de rayon 3) et ramassage.
- **[in-game]** Mort sous y = −32 : objets lâchés sous le monde, perdus (comme le vide de MC).
- **[in-game]** Animation de mort de `PlayerTestModel_V` : son parent `Player` a `Death`, `DeathFall`, `DeathDrown`… (`zip:Server/Models/Human/Player.json:755-856`), mais le modèle redéfinit `AnimationSets` (`zip:Server/Models/Human/PlayerTestModel_V.json:53`) : héritage non vérifié.
- **[in-game]** Corps sauvé à 20/20 qui se recharge à 20/100.
- Non vérifié : que `LivingEntity.die` de Minecraft saute `dropEquipment` après `remove(KILLED)` (code vanilla absent de `sources/`).
