# Mort d'un citoyen

Question : porter la mort d'un citoyen de MineColonies. Conception validée : à la vraie mort, tout l'inventaire tombe au sol, le citoyen est retiré pour de bon, les joueurs reçoivent un message, son emploi et ses requêtes sont libérés. Le deuil attend SP4.

Sources, vérifiées le 2026-09-26 :

- MineColonies `version/main`, clone local au commit `6b3916a1` (abrégé `MC/` = `src/main/java/com/minecolonies/`) ;
- serveur Hytale 0.6.8 décompilé `build/vineflower/hytale-server/com/hypixel/hytale/` (abrégé `hs/`) ;
- assets `release-0.6.8-Assets.zip`.

## 1. MineColonies : le déroulé exact

### 1.1 `EntityCitizen.die(DamageSource)` (`MC/core/entity/citizen/EntityCitizen.java` l. 1547-1629)

Tout se passe dans `die`, seulement si la colonie et le `citizenData` existent. Ordre exact :

1. `getRaiderManager().onLostCitizen(citizenData)` (raids, hors sujet).
2. `citizenExperienceHandler.dropExperience()` : orbes d'XP (voir 1.3).
3. `this.remove(RemovalReason.KILLED)` : l'entité est retirée **avant** `super.die`.
4. Si le métier n'est pas garde : modificateur de bonheur `DEATH` (3.0, 3 jours) (`ExpirationBasedHappinessModifier`) → SP4.
5. `triggerDeathAchievement` (succès Minecraft, hors sujet).
6. Si le métier n'est pas garde : `updateCitizenMourn(citizenData, true)` (deuil → SP4). Cette méthode appelle aussi `citizen.onDeath(id)` sur **tous** les citoyens (`MC/core/colony/managers/CitizenManager.java` l. 640-657), qui retire l'id des enfants, frères et sœurs et du partenaire (`MC/core/colony/CitizenData.java` l. 2004-2012). HyColony n'a pas encore de famille : rien à porter.
7. Statistique `DEATH` incrémentée pour le jour de la colonie (`getStatisticsManager().increment(DEATH, day)`). Elle alimente le résumé « `%d citizen(s) died` » (`manual_en_us.json` l. 2986).
8. Objets (voir 1.2) : tombe ou chute au sol.
9. Message aux joueurs (voir 1.4).
10. `citizenData.getJob().onRemoval()` si le citoyen a un métier (voir 1.5).
11. `getCitizenManager().removeCivilian(citizenData)` (voir 1.5).
12. Journal de la colonie : `addEventDescription(new CitizenDiedEvent(blockPosition(), name, deathCause))` (voir 1.6).
13. `CitizenDiedModEvent(citizenData, damageSource)` sur le bus d'événements de MC.
14. `super.die(damageSource)`.

Remarque : l'étape 3 retire l'entité avant `super.die`. Dans Minecraft vanilla, `LivingEntity.die` ne fait rien si `isRemoved()`. `dropEquipment()` (surchargé l. 1653-1665 pour vider l'inventaire) n'est donc **pas** appelé dans ce chemin. Cette déduction s'appuie sur le code vanilla de Minecraft, absent du clone : **non vérifiée ici**. Les objets ne tombent que par l'étape 8.

### 1.2 Ce qui tombe, et où

`EntityCitizen.java` l. 1571-1587 :

- **citoyen invisible** : rien ne tombe ;
- **citoyen dans la colonie** (`isCoordInColony`) : `GraveManager.createCitizenGrave(level, blockPosition(), citizenData)` (`MC/core/colony/managers/GraveManager.java` l. 254-331) :
  - si le citoyen est dans la lave, aucune tombe n'est posée, le message `WARNING_GRAVE_LAVA` part et **rien ne tombe** (l. 257-261) ;
  - sinon la méthode cherche un bloc d'air sur du solide dans un rayon de 10 (sous l'eau, jusqu'à 10 blocs au-dessus puis un rayon de 16). Une tombe y est posée. L'inventaire principal y est transféré (le surplus tombe au sol, l. 297-300). Les **armures** y sont ajoutées (le surplus tombe, l. 301-308). Les données du citoyen sont sérialisées dans la tombe pour une résurrection ;
  - sans place libre : `dropItemHandler(citizenData.getInventory(), …)` au sol (l. 326-329) ;
- **citoyen hors de la colonie** : `InventoryUtils.dropItemHandler(citizenData.getInventory(), level, (int) x, (int) y, (int) z)`.

`InventoryUtils.dropItemHandler` (`MC/api/util/InventoryUtils.java` l. 2586-2597) parcourt `handler.getSlots()`. Pour `InventoryCitizen`, cela ne couvre que les **27 slots principaux** (`MC/api/inventory/InventoryCitizen.java` l. 49-52, `getSlots` l. 159-162). Les 4 slots d'armure sont à part. Chaque pile passe par `spawnItemStack` (l. 2633-2648) : léger décalage aléatoire, pile découpée en paquets aléatoires, vitesse gaussienne.

L'**objet en main** n'est pas un slot à part. `mainItem`/`offhandItem` sont des **indices** dans l'inventaire principal (`InventoryCitizen.java` l. 55-58, `getHeldItem` l. 116-121), donc il tombe avec lui.

Conséquence pour HyColony (sans tombe, sans armure) : on vide les 27 slots de `CitizenData.inventory()` au sol, à la position du corps. C'est exactement la branche « hors colonie » de MC, qui devient la seule branche. **Deviation from MC** : pas de tombe (branche « dans la colonie »).

### 1.3 XP et compétences

`CitizenExperienceHandler.dropExperience()` (`MC/core/entity/citizen/citizenhandlers/CitizenExperienceHandler.java` l. 128-160) fait apparaître des orbes d'XP Minecraft d'une valeur de `getCitizenSkillHandler().getTotalXP()`. Il faut `recentlyHit > 0`, le loot autorisé et la règle `doMobLoot`. Suivent des particules d'explosion. Il n'y a **pas de transfert** d'XP vers un autre citoyen : les compétences disparaissent avec le citoyen. Hytale n'a pas d'orbes d'XP (non recherché plus avant) : à ignorer, écart à documenter.

### 1.4 Le message et ses destinataires

`EntityCitizen.java` l. 1589-1615, priorité `DANGER`, envoyé par `.sendTo(colony).forManagers()`. Il concatène :

1. `getCombatTracker().getDeathMessage()` : message de mort vanilla (« X was slain by Y ») puis `"! "` ;
2. `block.blockhuttownhall.messagecolonistgravelocation` = « `They died on the %s side of the colony. ` » (`manual_en_us.json` l. 294) :
   - `%s` = `BlockPosUtil.calcDirection(colony.getCenter(), blockPosition()).getLongText()` (`MC/api/util/BlockPosUtil.java` l. 872-937) : 8 secteurs de 45° par `atan2(xDiff, zDiff)`, plus `UP`/`DOWN`/`SAME` si X et Z sont égaux ;
   - textes (l. 649-659) : `North`, `South`, `East`, `West`, `Directly above`, `Directly below`, `Same location` ; les diagonales s'écrivent « `North/West` » (`DirectionResult`, l. 940-985) ;
   - au survol : `message.positiondist` = « `Position: %s %s %s Distance: %s blocks` » (l. 374) ;
3. si le citoyen n'est pas garde : `com.minecolonies.coremod.mourning` = « `Close family and friends will mourn %s tomorrow out of respect. ` » (l. 983) → **SP4, à omettre** ;
4. si une tombe est posée : `com.minecolonies.coremod.gravespawned` = « `A grave spawned, collect it before it decays!` » (l. 851) → sans objet.

Le français de MC n'est pas dans le dépôt (seul `manual_en_us.json` existe sous `assets/minecolonies/lang/`).

Destinataires (`MC/api/util/MessageUtils.java` l. 384-406, `MC/core/colony/Colony.java` l. 1375-1404) : `getImportantMessageEntityPlayers()`, c'est-à-dire l'union de :

- les abonnés proches (`getCloseSubscribers`) qui ont `Action.RECEIVE_MESSAGES` ;
- les joueurs importants (`getImportantColonyPlayers`) dont le rang est `isColonyManager()`.

Le préfixe `[NomColonie] ` est ajouté si le joueur est hors de la colonie. HyColony envoie déjà ses messages de colonie aux membres qui ont `RECEIVE_MESSAGES` (`core/.../construction/workorder/BuildCompletion.java` l. 44-48) : on reprend le même motif.

### 1.5 Emploi, bâtiment et requêtes libérés

- `AbstractJob.onRemoval()` (`MC/core/colony/jobs/AbstractJob.java` l. 426-467) :
  - `citizen.setJob(null)`, `getWorkerAI().onRemoval()` ;
  - retire le citoyen de chaque module `IAssignsJob` de son bâtiment de travail (`removeCitizen`) ;
  - remet `workBuilding`/`workModule` à null ;
  - range les armures et les mains dans l'inventaire (sans effet ici, l'inventaire est déjà vidé ou perdu).
- `AbstractAssignedCitizenModule.removeCitizen` (`MC/core/colony/buildings/modules/AbstractAssignedCitizenModule.java` l. 41-52) appelle `onRemoval(citizen)` même si le citoyen n'était pas assigné.
- `WorkerBuildingModule.onRemoval` (`MC/core/colony/buildings/modules/WorkerBuildingModule.java` l. 216-225) :
  - `building.cancelAllRequestsOfCitizenOrBuilding(citizen)` : **les requêtes sont annulées là** ;
  - `setVisibleStatus(null)`.
- `AbstractBuilding.cancelAllRequestsOfCitizenOrBuilding` (`MC/core/colony/buildings/AbstractBuilding.java` l. 1657-1690) :
  - passe chaque requête ouverte du citoyen à `RequestState.CANCELLED` ;
  - passe ses requêtes terminées à `RECEIVED` ;
  - nettoie les index.
- `CitizenManager.removeCivilian` (`MC/core/colony/managers/CitizenManager.java` l. 378-407) :
  - `citizens.remove(id)` ;
  - `removeCitizen` sur **chaque** `AbstractAssignedCitizenModule` de **chaque** bâtiment : travail et logement. `LivingBuildingModule.onRemoval` fait `setHomeBuilding(null)` et `calculateMaxCitizens()` (`MC/.../modules/LivingBuildingModule.java` l. 96-100) ;
  - `getWorkManager().clearWorkForCitizen(citizen)` ;
  - envoie `ColonyViewRemoveCitizenMessage` aux abonnés ;
  - `calculateMaxCitizens()`, `markDirty()`.
- `WorkManager.clearWorkForCitizen` (`MC/core/colony/workorders/WorkManager.java` l. 205-218) libère les ordres réclamés par le bâtiment de travail du citoyen. Mais `getWorkBuilding()` passe par le métier (`CitizenData.java` l. 912-919), déjà remis à null à l'étape 10 : **dans le chemin de la mort, cet appel ne libère rien**. L'ordre de travail reste réclamé par la hutte du constructeur, et le prochain constructeur embauché le reprend. À reproduire tel quel : ne pas libérer l'ordre.

Équivalent HyColony déjà présent : `WorkerModule.fire(Colony, Building, int)` (`core/.../job/WorkerModule.java` l. 87-97) annule `requests().cancelAllFrom(b.requesterId(), citizenId)`, puis remet le métier et le bâtiment de travail à null. C'est le même enchaînement (`onRemoval` → `cancelAllRequestsOfCitizenOrBuilding`) et il ne libère pas l'ordre de travail.

### 1.6 Journal de la colonie

`CitizenDiedEvent` (`MC/core/colony/eventhooks/citizenEvents/CitizenDiedEvent.java`) : id `minecolonies:citizen_died`, position, nom du citoyen, `deathCause`. La cause est le message de mort vanilla où le nom du citoyen est remplacé par `"Citizen"` (`EntityCitizen.java` l. 1622-1624). HyColony a `EventLog` (`colony.log().add(type, day, params…)`, déjà utilisé pour `citizenSpawned`) : ajouter `log.citizenDied` en en-US et fr-FR.

### 1.7 `CitizenDiedModEvent`

`MC/api/eventbus/events/colony/citizens/CitizenDiedModEvent.java` : `extends AbstractCitizenModEvent`, porte le `ICitizenData` et la `DamageSource` (`getDamageSource()`). C'est un simple événement du bus, sans effet propre. Équivalent HyColony : un record `CitizenDied(colony, data)` sur `ctx().bus()`, comme `CitizenSpawned`.

### 1.8 Nouveaux citoyens après une mort

Pas de minuterie propre à la mort. `CitizenManager.onColonyTick` (`MC/core/colony/managers/CitizenManager.java` l. 585-630) fait apparaître un citoyen initial tant que les conditions suivantes tiennent :

- `citizens.size() < initialCitizenAmount` (défaut 4, bornes 1-10, `MC/api/configuration/ServerConfiguration.java` l. 125) ;
- le réglage `MOVE_IN` est actif ;
- la colonie a un hôtel de ville.

La cadence : `respawnInterval -= 500 + 60 × niveau de l'hôtel de ville` à chaque tick de colonie (500 ticks) ; à ≤ 0, remise à `60 × 20` ticks et apparition. La valeur initiale est `30 × 20` (l. 95), jamais remise à zéro par une mort. Une mort qui fait passer sous `initialCitizenAmount` relance donc les arrivées.

HyColony le porte déjà (`core/.../citizen/CitizenManager.java` l. 99-105, sans `MOVE_IN`). Retirer le citoyen de `citizens` suffit. Au-delà du seuil initial, les nouveaux citoyens viennent de la taverne (recrutement), qui n'existe pas encore.

Le « `citizenRespawnTimer` » (5 min, l. 587-591) appelle `updateEntityIfNecessary` : il **réapparaît** le corps des citoyens **vivants** dont l'entité manque. Chez nous, c'est `updateBodyIfNecessary`. Il ne ressuscite pas un mort, puisque le mort n'est plus dans `citizens`.

### 1.9 Tombes (ignorées)

`GraveManager`, `TileEntityGrave`, `delayDecayTimer(GRAVE_DECAY_BONUS)`, résurrection par le fossoyeur (`JobUndertaker`) : **hors périmètre**.

### 1.10 Permission `MAP_DEATHS`

`Action.MAP_DEATHS(29)` (`MC/api/colony/permissions/Action.java` l. 42). Elle est donnée par défaut à Owner et Officer (`MC/core/colony/permissions/Permissions.java` l. 170-195), et à tout rang gestionnaire (l. 240-243). Son **seul** usage : afficher les tombes en points de passage JourneyMap côté client (`MC/core/compatibility/journeymap/ColonyDeathpoints.java` l. 90). Elle ne filtre **pas** le message de mort. HyColony l'a déjà dans `Action.java` (l. 30) ; rien à brancher sans tombes ni carte.

À noter (hors demande) : MC protège les citoyens par `Action.HURT_CITIZEN`. Un joueur sans cette permission ne peut infliger plus de 1 dégât (`EntityCitizen.checkIfValidDamageSource`, l. 1322-1344). Rendre nos citoyens mortels sans ce filtre les laisse tuables par n'importe qui (voir 2.4).

## 2. Hytale 0.6.8

### 2.1 Mort réelle ou déchargement ?

- `hs/component/RemoveReason.java` : `REMOVE, UNLOAD, BUILDER_TOOLS_UNDO`. Il n'y a **pas** de raison « tué ».
- La mort d'un PNJ se signale par l'ajout de `DeathComponent` (`hs/server/core/modules/entity/damage/DeathComponent.java`) :
  - `public static void tryAddComponent(Store<EntityStore>, Ref<EntityStore>, Damage)` et `tryAddComponent(CommandBuffer<EntityStore>, Ref<EntityStore>, Damage)` (l. 353-362) ajoutent le composant une seule fois ;
  - `public static ComponentType<EntityStore, DeathComponent> getComponentType()` (l. 109) ;
  - `public Damage getDeathInfo()` (l. 212), `null` après rechargement (non sérialisé) ;
  - `public DamageCause getDeathCause()` (l. 140), sérialisé.
- Qui ajoute `DeathComponent` :
  - `DamageSystems.ApplyDamage` (l. 255-275), quand la vie tombe au minimum ;
  - `DamageSystems.OutOfWorldDamage` (l. 1397-1433), **directement** si `y < -32`, sans passer par les filtres de dégâts ;
  - `EntityStatsSystems` l. 126, si la vie passe au minimum par une stat ;
  - `KillCommand` (joueurs seulement), `ActionDie` (action de PNJ), `RemoveEntitiesEffect`, `HardcoreGameOver`.
- Retrait du cadavre : `DeathSystems.CorpseRemoval` (l. 189-237) appelle `commandBuffer.removeEntity(ref, RemoveReason.REMOVE)` une fois la `DeathInteraction` finie et le `DeferredCorpseRemoval` écoulé (`DeathAnimationTime`, 1,5 s par défaut). **C'est la même raison `REMOVE` que notre `HytaleCitizenBodies.despawn`** : `onEntityRemove` ne distingue donc pas une mort d'un despawn volontaire. Un déchargement de chunk arrive avec `UNLOAD`.
- **Détecteur recommandé** : un `DeathSystems.OnDeathSystem` (`extends RefChangeSystem<EntityStore, DeathComponent>`, l. 412-432 ; `componentType()` déjà fourni, `onComponentSet`/`onComponentRemoved` vides). Il suffit de surcharger :
  - `Query<EntityStore> getQuery()` → `HyColonyComponents.citizenTag()` ;
  - `void onComponentAdded(Ref<EntityStore> ref, DeathComponent component, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer)`.

  Exemple vanilla : `DeathSystems.KillFeed` (l. 358-405). Enregistrement comme nos autres systèmes (`getEntityStoreRegistry().registerSystem(...)`, `HyColonyPlugin.java` l. 46-50).
- `onComponentAdded` n'est déclenché que par l'**ajout** du composant à une entité existante (`hs/component/Store.java` l. 2034-2060, `datachunk_addComponent`), pas au chargement d'une entité qui l'a déjà. Un chunk déchargé ne produit jamais de mort. Si le chunk se décharge pendant l'animation de mort, la mort a déjà été traitée. Au rechargement, le corps porte un `CitizenTag` dont le citoyen n'existe plus : `CitizenManager.onBodyLoaded` le despawn déjà (`data == null`).

### 2.2 Faire tomber des objets au sol

- `hs/server/core/modules/entity/item/ItemComponent.java` :
  - `public static Holder<EntityStore>[] generateItemDrops(ComponentAccessor<EntityStore> accessor, List<ItemStack> itemStacks, Vector3d position, Rotation3fc rotation)` (l. 430-445) : une pile tombe à la verticale (vitesse Y 3,25) ; plusieurs piles partent en cercle de rayon 3 ;
  - `public static Holder<EntityStore> generateItemDrop(ComponentAccessor<EntityStore>, ItemStack, Vector3d, Rotation3fc, float vx, float vy, float vz)` (l. 475-499) : `@Nullable` ; journalise un WARNING et renvoie `null` si la pile est vide ou invalide. Ajoute `ItemComponent`, `TransformComponent`, `Velocity`, `PhysicsValues`, `UUIDComponent`, `Intangible` et un `DespawnComponent` ;
  - la durée de vie vient de `computeLifetimeSeconds` (l. 218-223) : `ItemEntityConfig.Lifetime` de l'objet, sinon celui du `GameplayConfig`, sinon 120 s. `Server/GameplayConfigs/Default.json` (assets) donne `"ItemEntity": { "Lifetime": 600.0 }`, soit **10 minutes** (Minecraft : 5 min).
- Ces fonctions ne créent que des `Holder`. On les ajoute avec `Store.addEntities(Holder<EntityStore>[], AddReason)` (`hs/component/Store.java` l. 459) ou `CommandBuffer.addEntities(Holder<ECS_TYPE>[], AddReason)` (`hs/component/CommandBuffer.java` l. 70), avec `AddReason.SPAWN`.
- Modèle vanilla exact : `NPCDamageSystems.DropDeathItems` (`hs/server/npc/systems/NPCDamageSystems.java` l. 167-249). Position `transform.getPosition() + (0, 1, 0)`, rotation de `HeadRotation`, `generateItemDrops(store, items, dropPosition, new Rotation3f(headRotation))`, puis `commandBuffer.addEntities(drops, AddReason.SPAWN)`.
- Construction des piles : `new ItemStack(String itemId, int quantity)` (`hs/server/core/inventory/ItemStack.java` l. 134), déjà utilisé par `HytaleContainerAccess` l. 132.

### 2.3 Ce que fait déjà Hytale à la mort de notre PNJ

`NPCDamageSystems.DropDeathItems` ne fait tomber que deux sources :

- `InventoryComponent.Storage`, si le rôle a `PickupDropOnDeath` ;
- la `DropList` du rôle.

`DeathComponent.itemsLossMode` vaut `ALL` par défaut pour un PNJ (l. 104). Seuls les joueurs reçoivent le `DeathConfig` du monde (`DeathSystems.PlayerDropItemsConfig`, l. 651-675).

Réglages de `BuilderRole` (`hs/server/npc/role/builders/BuilderRole.java`) :

| Clé | Défaut | Ligne |
|---|---|---|
| `Invulnerable` | `false` | l. 723 |
| `PickupDropOnDeath` | `false` | l. 726 |
| `DeathAnimationTime` | 1,5 | l. 729 |
| `DeathParticles` | `Effect_Death` | l. 739 |
| `DropDeathItemsInstantly` | `false` | l. 749 |
| `DeathInteraction` | `null` | l. 758 |
| `DespawnAnimationTime` | 0,8 | l. 768 |
| `DropList` | aucune | l. 409 |

Notre rôle (`plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json`) ne fixe ni `PickupDropOnDeath` ni `DropList`. Vanilla ne fait donc **rien tomber** : pas de double chute avec notre inventaire du cœur. L'objet affiché en main (hotbar slot 0, `HytaleCitizenBodies.setHeldItem`) n'est qu'un reflet du cœur. Il n'est pas dans `Storage` et ne tombe pas.

### 2.4 Nos citoyens prennent-ils des dégâts ?

**Non, sauf chute hors du monde.**

- Le rôle a `"Invulnerable": true` et `"MaxHealth": 20`.
- `RoleBuilderSystem` (`hs/server/npc/systems/RoleBuilderSystem.java` l. 184-186) ajoute alors le composant `Invulnerable`.
- `DamageSystems.FilterUnkillable` (l. 1219-1255) annule tout dégât si l'entité a `Invulnerable`, `Intangible` ou `DeathComponent`.
- Seul `OutOfWorldDamage` (`y < -32`) ajoute `DeathComponent` sans passer par ce filtre.

Aujourd'hui, un citoyen tombé sous le monde meurt vraiment côté Hytale. `CitizenBodyLifecycleSystem.onEntityRemove` le voit comme un déchargement, puis `updateBodyIfNecessary` le fait réapparaître. **[in-game]** Non observé.

Pour que les citoyens puissent mourir, il faut retirer `"Invulnerable": true`. Il faut aussi, pour rester fidèle à MC (1.10), un filtre de dégâts : un `DamageEventSystem` dans `DamageModule.get().getFilterDamageGroup()`, comme `NPCDamageSystems.FilterDamageSystem` (l. 252-285), qui annule (`damage.setCancelled(true)`) les dégâts > 1 d'un joueur sans `HURT_CITIZEN`. Ce filtre est un sujet à part : à valider avant d'ouvrir les dégâts.

## 3. Découpage recommandé

### Cœur (`core/`, testable avec des `Fake*`)

- `CitizenManager.onBodyDied(BodyId body, Vec3 at)` : point d'entrée de la mort. Il retrouve le citoyen lié au corps ; un corps inconnu ou déjà délié ne fait rien. Il délègue à une classe dédiée (par exemple `CitizenDeath`, MC `EntityCitizen.die`) pour ne pas grossir `CitizenManager`. Ordre MC :
  1. `ctx().ports()…drop(at, data.inventory().contents())`, puis inventaire vidé ;
  2. message aux membres qui ont `RECEIVE_MESSAGES`, avec la clé `citizen.died` et les paramètres nom et direction (8 secteurs + dessus/dessous/même, port de `BlockPosUtil.calcDirection` depuis `colony.center()`). **Deviation from MC** : pas de message de mort vanilla (la cause Hytale est un `Message` serveur qui ne traverse pas le cœur), pas de phrase de deuil (SP4), pas de tombe ;
  3. `WorkerModule.fire(...)` sur le bâtiment de travail : requêtes annulées, métier et bâtiment remis à null. L'ordre de travail n'est **pas** libéré, comme MC ;
  4. `homeBuilding` remis à null (logement futur) ;
  5. `citizens.remove(id)`, `bodies.remove(id)`, `ais.remove(id)` ;
  6. `colony.log().add("citizenDied", colony.day(), name)` ;
  7. `ctx().bus().post(new CitizenDied(colony, data))`, `colony.markDirty()`.

  Ignorés et documentés : XP, statistique `DEATH`, bonheur et deuil (SP4), raids, succès.
- Nouveau port (ou méthode sur un port existant) pour la chute d'objets, par exemple `WorldItems.drop(Vec3 at, List<ItemAmount> items)`. Il ne lève jamais d'exception ; un chunk non chargé perd les objets et journalise, voir « Incertain ».
- Test d'abord : un corps « mort » dans `FakeCitizenBodies` retire le citoyen, fait tomber ses objets, annule ses requêtes, envoie le message et n'est **pas** recréé par `onColonyTick`. `onBodyUnloaded` garde le comportement actuel (respawn).

### Plugin (`plugin/`)

- `CitizenDeathSystem extends DeathSystems.OnDeathSystem`, requête `citizenTag()`. Dans `onComponentAdded` : attrape `RuntimeException` (SEVERE), trouve le runtime du monde, `bodies().track(ref)` ou l'id existant, lit la position (`TransformComponent`), puis appelle `manager().onBodyDied(id, pos)` (le cœur retrouve la colonie par le tag, comme `onBodyLoaded`).
- `HytaleWorldItems.drop` : convertit en `ItemStack` et appelle `ItemComponent.generateItemDrops(store, stacks, pos + (0, 1, 0), rotation)`. L'ajout passe par `world.execute(() -> store.addEntities(drops, AddReason.SPAWN))` : le cœur est appelé pendant le traitement du store, où les changements structurels lèvent une exception (même contrainte que `despawn`/`teleport`).
- Le cadavre reste géré par vanilla (`CorpseRemoval`, animation 1,5 s). Son `onEntityRemove` (`REMOVE`) arrive ensuite. Si le cœur a déjà délié le corps, `onBodyUnloaded` ne trouve rien et ne fait rien. `HytaleCitizenBodies.isAlive` doit rester `true` jusque-là : sans effet, puisque le citoyen n'est plus dans le cœur.
- Rôle : retirer `"Invulnerable": true` seulement avec le filtre `HURT_CITIZEN` (2.4).
- Clés de langue en-US et fr-FR : `citizen.died`, `log.citizenDied` et les directions (skill `add-lang-key`).

## Incertain / [in-game]

- **[in-game]** Rendu et dispersion de `generateItemDrops` pour 27 piles (cercle de rayon 3), et ramassage par les joueurs.
- **[in-game]** Un citoyen qui tombe sous `y < -32` : les objets tombent aussi sous le monde et sont perdus. Même chose dans MC (le vide détruit les objets). Acceptable, à confirmer.
- **[in-game]** Animation de mort du modèle `PlayerTestModel_V` (le modèle joueur a `Death`, `plugin-b-api.md` § 290).
- Chute d'objets dans un chunk non chargé : ne devrait pas arriver, puisque le corps meurt là où il est chargé, mais le port doit l'accepter sans lever d'exception.
- Le fait que MC ne passe jamais par `dropEquipment` repose sur le `LivingEntity.die` de Minecraft vanilla, absent du clone.
