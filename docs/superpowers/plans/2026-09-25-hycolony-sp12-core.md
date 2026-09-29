# HyColony SP1+2 — Partie A : Core (requêtes, métiers, construction)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** le core de HyColony gagne le système de requêtes de MineColonies, le socle des métiers, les ordres de travail et le constructeur qui bâtit bloc par bloc. Tout est testé sans Hytale, y compris par une simulation de bout en bout.

**Architecture :** on garde le même découpage ports et adaptateurs que SP0. On ajoute :
- les packages `kernel.item`, `request`, `job` et `construction` ;
- de nouveaux ports : `ItemCatalog`, `WorldBlocks`, `ContainerAccess`, `PlayerInventory` (dans `kernel.port`) et `BlueprintSource` (dans `construction`) ;
- `ColonyContext` gagne un champ `GamePorts`, avec une implémentation « indisponible » pour que le plugin compile tant que la partie B n'est pas faite.

**Tech Stack :** Java 21 (bytecode du core), JUnit 5, ArchUnit, Gson (`compileOnly`).

**Spec :** `docs/superpowers/specs/2026-09-25-hycolony-sp12-requests-construction-design.md`
**Références (à lire pour chaque tâche concernée) :**
- `docs/research/minecolonies-analysis.md` (§ 5 à 7)
- `docs/research/construction-research.md`
- la source de MineColonies, sous `C:\Users\Ctuto\AppData\Local\Temp\claude\C--Users-Ctuto-Desktop-HyColony\2db3263f-4710-4430-8965-3c0f3706ad64\scratchpad\minecolonies\src\main\java\com\minecolonies\` (notée `MC/`).

> **Note de forme (décision du contrôleur).** Vu l'ampleur, les tâches fixent **les interfaces exactes, le comportement attendu, avec des références MineColonies, et les tests à écrire (noms et assertions)**. Elles ne fixent pas chaque ligne de code. L'implémenteur écrit le code en TDD : d'abord les tests listés (RED), puis le code (GREEN). Les signatures listées dans **Interfaces** sont contractuelles : ne pas les renommer, car les tâches suivantes en dépendent.

## Global Constraints

- **Aucun import `com.hypixel`** dans `core/`. Le core ne dépend que du JDK et de Gson.
- **Mono-thread par monde** : pas de synchronisation.
- **Logs** : `System.getLogger`.
- **Tick du core : 20/s.** Les constantes de MineColonies restent en ticks.
- **Sauvegarde** : `schemaVersion` passe à **2** (migration v1 → v2 obligatoire, fixture v1 conservée).
- **Inventaire d'un citoyen : 27 emplacements.** Taille d'un seau = **27 − 9 = 18** piles.
- **Priorités des résolveurs** : Building **200**, Retrying **50**, Player **0**. Retrying : **1200** ticks × **3** tentatives. Tick du `RequestManager` : toutes les **11** ticks.
- **Constructeur** :
  - `ACTIONS_UNTIL_DUMP` **4096** ;
  - distance d'un ordre **100** blocs (3D, `distSq ≤ 10000`) ;
  - rayon de réutilisation de la position de travail **10** ;
  - distance de travail **4**.
- **Formules** :
  - pose : `15 × 10 / (primary/2 + 10)` ticks ;
  - casse : `500 × 0.85^(secondary/2) × hardness / toolSpeed × 0.5`, avec un minimum de 1 ;
  - XP : **0,05** par bloc et **+8** par bâtiment.
- **Rayons de claim (cellules)** :
  - hôtel de ville : `{1:1, 2:1, 3:2, 4:3, 5:5}` ;
  - défaut : `{1:1, 2:1, 3:1, 4:2, 5:2}` ;
  - niveau 0 : 0 ;
  - jamais au-delà de `maxColonySize` depuis le centre, et jamais en volant une cellule.
- **Git** :
  - les commits se terminent par une ligne vide, puis `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`, puis `Claude-Session: https://claude.ai/code/session_01J1tBHr3AzU7e8M4iYw6H5c` ;
  - on ajoute les fichiers avec `git add <chemins>`, jamais `git add -A` ;
  - `.mcp.json` n'est jamais touché.
- **Tests** : `./gradlew :core:test`. Le plugin doit **toujours compiler** (`./gradlew :plugin:compileJava`) : si une tâche change `ColonyContext` ou un port, elle met à jour `plugin/.../WorldRuntime.java` avec `GamePorts.unavailable()`.

## Review Focus

1. **Redémarrage en plein chantier** (étape SOLID, index au milieu, seau demandé) : reprise exacte, sans redemander un seau déjà livré, sans reposer un bloc existant. Tâche 11, `restartMidBuildResumesExactly`.
2. **Le joueur casse un bloc déjà posé pendant le chantier** : le constructeur le repose, parce que la règle de saut compare l'état réel du monde. Tâche 11, `playerBreaksPlacedBlockItIsRebuilt`.
3. **Cabane du constructeur détruite pendant un chantier** : l'ordre est libéré, les requêtes sont annulées, pas d'exception. Tâche 7, `builderHutRemovedReleasesOrderAndCancelsRequests`.
4. **Inventaire du constructeur plein de débris pendant CLEAR** : il dépose dans la cabane. Si la cabane est pleine, il ne boucle pas à l'infini : il garde, et les débris restants sont détruits avec un message dans le journal (écart documenté ; MineColonies crée une requête de ramassage, qui n'existe qu'en SP3). Tâche 9, `clearWithFullHutDoesNotLoop`.
5. **« Fournir » avec une quantité partielle** : la requête est clôturée, puis le constructeur redemande le reste. Tâche 4, `partialFulfilClosesAndBuilderRerequests`, et tâche 11.

---

## Structure des fichiers (nouveaux)

```
core/src/main/java/dev/hycolony/core/
  kernel/item/        ItemKey, ItemAmount, BlockKey, BlockState, BlockKind, ToolType, ToolInfo, Inventory
  kernel/port/        ItemCatalog, WorldBlocks, ContainerAccess, PlayerInventory (ajouts)
  colony/             GamePorts (+ modifs ColonyContext, Colony, ColonyManager, ColonySerializer)
  request/            RequestToken, Requestable, Deliverable, StackRequest, ToolRequest, RequestState,
                      RequesterId, Requester, Request, Resolver, ResolverProvider, RequesterRegistry,
                      RequestManager, RequestSerializer
  request/resolver/   RetryingResolver, PlayerResolver   (BuildingResolver est dans building/)
  job/                JobType, JobRegistry, Job, JobAI, HiringMode, WorkerModule, JobXp
  construction/       Blueprint, BlueprintEntry, BlueprintSource, StructurePlan, Stage, WorkOrder,
                      WorkOrderType, WorkManager, WorkOrderRefusal, ClaimRadius, NeededResources,
                      Buckets, BuildingResourcesModule, BuilderJob, BuilderAI, BuilderState,
                      BuilderTimings, LivingModule, ConstructionBuildingTypes
  app/ui/          BuildingView, BuilderResourcesView, RequestsView, WorkOrdersView (+ UiPort ajouts)
core/src/test/java/dev/hycolony/core/testing/  FakeCatalog, FakeWorldBlocks, FakeContainers,
                      FakePlayerInventory, FakeBlueprints (+ TestContexts étendu)
```

---

### Task 1 : objets, inventaires, ports de construction, schéma v2

**Files :**
- Create : `kernel/item/{ItemKey,ItemAmount,BlockKey,BlockState,BlockKind,ToolType,ToolInfo,Inventory}.java`, `kernel/port/{ItemCatalog,WorldBlocks,ContainerAccess,PlayerInventory}.java`, `colony/GamePorts.java`
- Create (test) : `testing/{FakeCatalog,FakeWorldBlocks,FakeContainers,FakePlayerInventory}.java`, `kernel/item/InventoryTest.java`, `app/persistence/SchemaV2MigrationTest.java`, fixture `src/test/resources/fixtures/colony-v2.json`
- Modify : `CitizenData` (+ `Inventory inventory`, 27 emplacements), `ColonyContext` (+ `GamePorts ports` en **dernier** champ), `TestContexts`, `ColonySerializer` (inventaire du citoyen et `schemaVersion` 2), `MigrationChain` (une méthode `sp1()` avec la migration 1 → 2), `ColonyManager` (utilise `MigrationChain.sp1()` par défaut), `plugin/.../WorldRuntime.java` (passe `GamePorts.unavailable()` et `MigrationChain.sp1()`), `ArchitectureTest` (règles de § 2 de la spec)

**Interfaces (produites) :**

```java
package dev.hycolony.core.kernel.item;
public record ItemKey(String id) {}                       // requireNonNull
public record ItemAmount(ItemKey item, int count) {}      // count > 0 else IAE; ItemAmount withCount(int)
public record BlockKey(String id) {}
public record BlockState(BlockKey key, int rotation) {}
public enum BlockKind { AIR, SOLID, NON_SOLID, FLUID, UNBREAKABLE }
public enum ToolType { PICKAXE, AXE, SHOVEL }
public record ToolInfo(ToolType type, int level, float speed) {}
public final class Inventory {
    public Inventory(int slots);
    public int size();
    public ItemAmount insert(ItemAmount amount, ToIntFunction<ItemKey> maxStack); // returns remainder, or null if all fit
    public int extract(ItemKey item, int max);            // removes up to max, returns removed
    public int count(ItemKey item);
    public boolean isFull();                              // no empty slot
    public int freeSlots();
    public List<ItemAmount> contents();                   // non-empty slots, in slot order
    public Optional<ItemAmount> slot(int index);
    public JsonArray write(); public static Inventory read(JsonArray a, int slots);
}
package dev.hycolony.core.kernel.port;
public interface ItemCatalog {
    int maxStack(ItemKey item);
    Optional<ItemKey> itemForBlock(BlockKey block);
    BlockKind kind(BlockKey block);
    boolean isOre(BlockKey block);
    Optional<ToolType> toolFor(BlockKey block);
    float hardness(BlockKey block);
    Optional<ToolInfo> tool(ItemKey item);
}
public interface WorldBlocks {
    boolean isLoaded(BlockPos pos);
    Optional<BlockState> get(BlockPos pos);               // empty if unloaded
    boolean place(BlockPos pos, BlockState state, boolean withContainer);
    List<ItemAmount> breakBlock(BlockPos pos);            // drops incl. container contents; empty list if air/unloaded
    void damageTool(BodyId body, ItemKey tool);           // wear 1 on the tool held by that citizen body; no-op if unknown
}
public interface ContainerAccess {
    int count(List<BlockPos> containers, ItemKey item);
    int extract(List<BlockPos> containers, ItemKey item, int max);
    ItemAmount insert(List<BlockPos> containers, ItemAmount amount); // remainder or null
    Map<ItemKey, Integer> contents(List<BlockPos> containers);
}
public interface PlayerInventory {
    int count(UUID player, ItemKey item);
    int take(UUID player, ItemKey item, int max);         // returns taken
    ItemAmount give(UUID player, ItemAmount amount);      // remainder or null
}
package dev.hycolony.core.colony;
public record GamePorts(ItemCatalog catalog, WorldBlocks blocks, ContainerAccess containers,
                                PlayerInventory playerInventory, BlueprintSource blueprints) {
    public static GamePorts unavailable(); // every method: empty/0/false/remainder=input; blueprints.load -> empty
}
```

`BlueprintSource` est créé dans cette tâche en tant qu'interface seule (`construction/blueprint/BlueprintSource.java`), avec le record `Blueprint` minimal défini en tâche 6. Pour que la tâche 1 compile, on crée maintenant `construction/blueprint/Blueprint.java` et `construction/blueprint/BlueprintEntry.java` avec exactement les signatures de la tâche 6.

**Comportement :**
- `Inventory.insert` complète d'abord les piles existantes du même objet (jusqu'à `maxStack`), puis remplit les emplacements vides.
- `extract` retire d'abord des **derniers** emplacements.
- `contents` renvoie les piles non vides, dans l'ordre des emplacements.
- Migration v1 → v2 :
  - `schemaVersion` passe à 2 ;
  - chaque citoyen reçoit `"inventory":[]` et `"job":null` ;
  - la colonie reçoit `"requests":{}`, `"workOrders":[]`, `"settings":{"autoHiring":true}` ;
  - chaque bâtiment reçoit `"containers":[]` et `"deconstructed":false`.
- `ColonySerializer` écrit et lit l'inventaire des citoyens. Les autres champs v2 sont écrits vides pour l'instant et seront remplis par les tâches suivantes, qui étendent le sérialiseur.
- **ArchitectureTest** : il remplace la règle générique des « futurs packages » par ces règles explicites :
  - `request..` ne dépend pas de `colony..`, `building..`, `construction..`, `job..`, `citizen..` ;
  - `job..` ne dépend pas de `construction..` ;
  - `kernel..` ne dépend que de `kernel..`, `java..`, `com.google.gson..`.

**Tests à écrire :**
- `InventoryTest` :
  - `insertMergesThenFillsEmpty` : maxStack 64, on insère 70 puis 30, on obtient deux piles, 64 et 36 ;
  - `insertReturnsRemainderWhenFull` : 1 emplacement, maxStack 10, on insère 15, le reste vaut 5 ;
  - `extractTakesFromLastSlotsFirst` ;
  - `countAndFreeSlots` ;
  - `roundTripJson`.
- `SchemaV2MigrationTest` :
  - `v1FixtureMigratesToV2` : la fixture v1 existante se charge, `schemaVersion` 2 est réécrit à la sauvegarde, et une sauvegarde v1 est conservée (`colony-1.v1.json`) ;
  - `v2FixtureLoads` ;
  - `citizenInventoryRoundTrip`.
- `ArchitectureTest` mis à jour, au vert.

- [ ] Écrire les tests, constater RED (`./gradlew :core:test`).
- [ ] Implémenter. Constater GREEN, puis `./gradlew :plugin:compileJava`.
- [ ] Commit : `feat(core): items, inventories, construction ports, schema v2`.

---

### Task 2 : modèle de requête

**Files :** Create `request/{RequestToken,Requestable,Deliverable,StackRequest,ToolRequest,RequestState,RequesterId,Requester,Request,Resolver,ResolverProvider,RequesterRegistry}.java`, test `request/RequestModelTest.java`

**Interfaces :**

```java
public record RequestToken(UUID id) { public static RequestToken random(); }
public sealed interface Requestable permits Deliverable {}
public sealed interface Deliverable extends Requestable permits StackRequest, ToolRequest {
    boolean matches(ItemKey item, ItemCatalog catalog);
    int count(); int minCount();
    Deliverable withCount(int count);
    boolean canBeResolvedByBuilding();
    String describe();                                    // e.g. "64 x Wood_Oak_Trunk"
}
public record StackRequest(ItemKey item, int count, int minCount, boolean canBeResolvedByBuilding) implements Deliverable {}
public record ToolRequest(ToolType type, int minLevel, int maxLevel) implements Deliverable {} // count=minCount=1, matches: catalog.tool(item) type equal and level in [min,max]
public enum RequestState { CREATED, REPORTED, ASSIGNING, ASSIGNED, IN_PROGRESS, RESOLVED, FOLLOWUP_IN_PROGRESS,
                           COMPLETED, OVERRULED, CANCELLED, RECEIVED, FINALIZING, FAILED }   // MC order: ordinal saved
public record RequesterId(String value) {}
public interface Requester {
    RequesterId requesterId(); BlockPos location(); String displayName();
    void onRequestComplete(RequestManager manager, Request request);
    void onRequestCancelled(RequestManager manager, Request request);
}
public final class Request {
    public RequestToken token(); public RequesterId requester(); public Deliverable requestable();
    public RequestState state(); public Optional<RequestToken> parent(); public List<RequestToken> children();
    public List<ItemAmount> deliveries(); public int citizenId();   // -1 = the building itself
    // package-private mutators used by RequestManager
}
public interface Resolver extends Requester {
    String resolverId();                                  // stable, persisted, e.g. "building:1,64,2" / "player" / "retrying"
    int priority();
    boolean handles(Deliverable requestable);
    boolean canResolve(RequestManager m, Request r);
    Optional<List<Deliverable>> attemptResolve(RequestManager m, Request r); // empty = cannot; list = children to create
    void resolve(RequestManager m, Request r);
    default List<Deliverable> followups(RequestManager m, Request r) { return List.of(); }
    default void onAssigned(RequestManager m, Request r) {}
    default void onCancelling(RequestManager m, Request r) {}
    default void onCancelled(RequestManager m, Request r) {}
    default void onColonyUpdate(RequestManager m, Predicate<Request> which) {}
    double suitability(RequestManager m, Request r);      // lower is better
    default void tick(RequestManager m) {}
}
public interface ResolverProvider { String providerId(); List<Resolver> resolvers(); }
public interface RequesterRegistry { Optional<Requester> find(RequesterId id); }
```

**Tests :**
- `stackMatchesOnlySameItem` ;
- `toolRequestMatchesTypeAndLevelRange` (avec `FakeCatalog`) ;
- `withCountKeepsOtherFields` ;
- `stateOrdinalsMatchMineColonies` : vérifie l'ordre des 13 constantes ;
- `requestTokenRandomUnique`.

- [ ] RED, puis GREEN, puis commit : `feat(core): request model`.

---

### Task 3 : `RequestManager` (attribution, transitions, réassignation)

C'est le portage de `MC/core/colony/requestsystem/management/handlers/RequestHandler.java` et de `StandardRequestManager`. **Il faut lire ces fichiers et le § 5 de l'analyse avant de coder.** Modèle d'implémentation recommandé : opus.

**Files :** Create `request/RequestManager.java`, test `request/RequestManagerTest.java` (avec des résolveurs de test : `FixedResolver(priority, suitability, canResolve, children, followups)`)

**Interfaces :**

```java
public final class RequestManager {
    public RequestManager(RequesterRegistry requesters, ItemCatalog catalog);
    public void registerBuiltIn(Resolver r);                    // player, retrying: never removed
    public void onProviderAdded(ResolverProvider p);
    public void onProviderRemoved(ResolverProvider p);          // reassign its requests with its resolvers blacklisted, then drop them
    public RequestToken createAndAssign(Requester requester, Deliverable what, int citizenId);
    public RequestToken createChild(Resolver parentResolver, RequestToken parent, Deliverable what);
    public void assign(RequestToken token, Set<String> blacklist);
    public void reassign(RequestToken token, Set<String> blacklist);
    public void updateState(RequestToken token, RequestState newState);   // public transitions (RESOLVED, COMPLETED, CANCELLED, FAILED, RECEIVED)
    public void overrule(RequestToken token, List<ItemAmount> delivered);
    public void onColonyUpdate(Predicate<Request> which);
    public void tick();                                         // ticks every registered resolver's tick()
    public Optional<Request> get(RequestToken token);
    public Optional<Resolver> resolverOf(RequestToken token);   // O(1): inverse index
    public List<Request> byRequester(RequesterId id);
    public List<Request> assignedTo(String resolverId);
    public Collection<Request> all();
    public void addDelivery(RequestToken token, ItemAmount amount);
    public ItemCatalog catalog();
}
```

**Comportement (identique à MineColonies, analyse § 5) :**
- **Attribution** :
  1. ASSIGNING ;
  2. candidats = résolveurs enregistrés, avec `handles(requestable)` vrai et hors liste noire ;
  3. tri par `priority` décroissante (priorités égales : ordre d'enregistrement) ;
  4. le premier dont `canResolve` est vrai **et** `attemptResolve` non vide gagne provisoirement ;
  5. les suivants **de même priorité** dont la `suitability` est strictement inférieure sont essayés : s'ils réussissent, les enfants de la tentative précédente, déjà créés, sont annulés et ils la remplacent ;
  6. le premier changement de priorité arrête la recherche ;
  7. `resolve()` : ASSIGNED, `onAssigned`, création des enfants (le requester des enfants est le résolveur, et ils héritent de la liste noire), attribution de chaque enfant ;
  8. si l'état est encore < IN_PROGRESS, passage à IN_PROGRESS, et s'il n'y a **pas d'enfants**, appel de `resolver.resolve(request)`.
  - Aucun candidat : l'état reste REPORTED. Ne jamais arriver là : le résolveur Player accepte tout.
- **Transitions** : `updateState` enfile un événement. Une boucle de traitement non réentrante les traite en FIFO, dans l'ordre de MineColonies :
  - RESOLVED → `followups` : s'il y en a, FOLLOWUP_IN_PROGRESS, et les suites sont créées comme enfants puis attribuées ; sinon → COMPLETED.
  - COMPLETED → `requester.onRequestComplete`. S'il y a un parent : l'enfant passe RECEIVED (nettoyé), est retiré des enfants du parent, et si le parent n'a plus d'enfants :
    - parent IN_PROGRESS → `resolverOf(parent).resolve(parent)` ;
    - parent FOLLOWUP_IN_PROGRESS → COMPLETED.
  - OVERRULED (via `overrule`) → annuler directement les enfants, `onCancelling`, `deliveries = delivered`, COMPLETED, `onCancelled`. **Une seule fois.**
  - CANCELLED ou FAILED :
    - avec un parent : les `deliveries` du parent sont vidées, tous les enfants du parent sont annulés, et le parent est **réassigné** ;
    - sans parent : tout le sous-arbre est annulé récursivement, `onRequestCancelled` est appelé, puis nettoyage.
  - RECEIVED → nettoyage : retrait de toutes les maps.
- **Retrait d'un provider** : pour chaque requête assignée à l'un de ses résolveurs, on annule ses enfants et on la réassigne avec **tous** les résolveurs du provider sur liste noire.
- `onColonyUpdate(pred)` délègue à chaque résolveur.

**Tests (au minimum) :**
- `assignsHighestPriorityThatCanResolve` ;
- `samePriorityBetterSuitabilityWinsAndCancelsPreviousChildren` ;
- `priorityChangeStopsSearch` ;
- `blacklistIsInheritedByChildren` ;
- `noChildrenResolvesImmediately` ;
- `childrenCompleteThenParentResolved` ;
- `followupsThenCompleted` ;
- `childFailureReassignsParentWithBlacklistPreserved` ;
- `cancelWithoutParentCancelsSubtreeAndNotifiesRequester` ;
- `overruleCompletesOnceAndCancelsChildren` : vérifie que `onRequestComplete` n'est appelé qu'une fois ;
- `receivedCleansAllIndexes` : `get`, `resolverOf`, `byRequester` et `assignedTo` sont vides ;
- `providerRemovedReassignsToOthers` ;
- `reentrantUpdateFromCallbackIsQueued` : un requester qui appelle `updateState` depuis `onRequestComplete` ne corrompt pas l'état ;
- `thousandRequestsAssignInUnder50ms` : performance, 1000 requêtes et 5 résolveurs.

- [ ] RED, puis GREEN, puis commit : `feat(core): request manager with MineColonies assignment and transitions`.

---

### Task 4 : résolveurs, persistance des requêtes, intégration à la colonie

**Files :**
- Create : `request/resolver/{RetryingResolver,PlayerResolver}.java`, `building/BuildingResolver.java` (dans `building`, car `request` ne doit pas dépendre de `building`), `request/RequestSerializer.java`
- Modify : `Colony` (champ `RequestManager requests`, créé dans le constructeur avec `PlayerResolver` et `RetryingResolver` enregistrés, plus un tick toutes les **11** ticks en ACTIVE), `Building` (implémente `Requester` et `ResolverProvider` : `requesterId = "building:x,y,z"`, `location = position`, `resolvers = [new BuildingResolver(this, containers)]`, la `ContainerAccess` étant injectée par `Colony` à l'ajout du bâtiment, `containers()` qui renvoie `List<BlockPos>` avec la position de la cabane, puis les conteneurs enregistrés), `BuildingManager` (`add`/`remove` appellent `onProviderAdded`/`onProviderRemoved` via un listener fourni par `Colony`), `ColonyManager` (`fulfil`, `addToHut`, `onContainerChanged`), `ColonySerializer` (requêtes)
- Test : `request/resolver/ResolversTest.java`, `request/RequestSerializerTest.java`, `app/action/FulfilTest.java`

**Interfaces :**

```java
public final class BuildingResolver implements Resolver {         // package building ; priority 200
    public BuildingResolver(Building building, ContainerAccess containers);
}
public final class RetryingResolver implements Resolver {         // priority 50, handles all Deliverable
    public static final int DELAY_TICKS = 1200, MAX_TRIES = 3;
}
public final class PlayerResolver implements Resolver {           // priority 0, handles all
    public List<Request> open();                                  // assigned & not finished, in creation order
}
// ColonyManager additions
public boolean fulfil(UUID player, int colonyId, RequestToken token); // Fournir
public int addToHut(UUID player, BlockPos hutPos, ItemKey item, int wanted); // Ajouter -> returns moved
public void onContainerChanged(BlockPos containerPos);            // player put items in a hut container
```

**Comportement :**
- **BuildingResolver** (`MC/.../resolvers/BuildingRequestResolver.java`) :
  - il traite seulement les requêtes dont le requester est **son propre bâtiment**, et si `canBeResolvedByBuilding()` ;
  - `canResolve` : le stock des conteneurs est ≥ `minCount`, en soustrayant ce qui est déjà réservé dans les `deliveries` des autres requêtes ouvertes du bâtiment ;
  - `attempt` : liste vide d'enfants ;
  - `resolve` : ajoute aux `deliveries` les piles `min(count, stock)`, sans les retirer du coffre, puis RESOLVED (le citoyen viendra les prendre) ;
  - `suitability` 0.
- **RetryingResolver** (`MC/.../StandardRetryingRequestResolver.java`) :
  - `attempt` : liste vide ;
  - `resolve` : `delay[token] = 1200`, `tries[token]++` ;
  - `tick()` : il est appelé toutes les 11 ticks par la colonie, et décrémente chaque délai de **11** ;
  - quand un délai atteint 0 : `reassign(token, tries >= 3 ? {"retrying"} : {})`. Si le nouveau résolveur n'est plus « retrying », on oublie ce token ;
  - `onColonyUpdate(pred)` : les requêtes qui correspondent sont réassignées immédiatement, avec « retrying » sur liste noire.
- **PlayerResolver** :
  - `resolve` : il mémorise le token, sans rien faire d'autre ;
  - `onColonyUpdate` : comme retrying ;
  - `open()` : la liste pour la fenêtre des requêtes.
- **fulfil** :
  - la permission `ACCESS_HUTS` est requise ;
  - on prend `n = min(requestable.count, playerInventory.count(item correspondant))` au joueur, avec, pour un `ToolRequest`, le premier objet outil qui correspond ;
  - on donne `n` au **citoyen** demandeur. Si `citizenId == -1`, on le met dans les conteneurs de la cabane. Le reste éventuel est rendu au joueur ;
  - `overrule(token, [ItemAmount(item, n)])` ;
  - renvoie false si `n == 0`.
- **addToHut** :
  - on transfère `min(wanted, possédé)` du joueur vers les conteneurs de la cabane, et le reste revient au joueur ;
  - puis `overruleNextOpenRequestWithStack` : la première requête ouverte de ce bâtiment qui est détenue par le Player ou le Retrying, et dont l'objet correspond, est OVERRULED avec la quantité ajoutée.
- **onContainerChanged(pos)** : on trouve le bâtiment qui possède ce conteneur, puis `requests.onColonyUpdate(r -> r.requester() == building)`.
- **Persistance** (`RequestSerializer`) :
  - les requêtes (token, requester, requestable avec un type discriminant `"stack"`/`"tool"`, state par **nom**, parent, children, deliveries, citizenId) ;
  - les attributions `resolverId → tokens` ;
  - l'état du retrying (delays, tries) et la liste du player.
  - Au chargement, les providers se réenregistrent (bâtiments), puis une requête dont le résolveur est introuvable est **réassignée** au lieu d'être supprimée : c'est une amélioration sur MineColonies, pour ne pas perdre de requête.

**Tests :**
- `buildingResolverResolvesFromOwnHutStockOnly` ;
- `buildingResolverSubtractsReservedDeliveries` ;
- `retryingRetriesThreeTimesEvery1200TicksThenPlayer` : vérifie qu'on arrive au player après 3 × 1200 ticks, à ±11 ;
- `colonyUpdateReassignsStuckRequestsToBuildingWhenStockArrives` ;
- `fulfilMovesItemsToCitizenAndOverrules` ;
- `partialFulfilClosesAndBuilderRerequests` (côté requêtes : la requête est OVERRULED avec une quantité partielle) ;
- `fulfilWithoutItemsReturnsFalse` ;
- `addToHutMovesAndOverrulesNextOpen` ;
- `requestsRoundTripThroughSave` ;
- `unknownResolverOnLoadIsReassigned`.

- [ ] RED, puis GREEN, puis commit : `feat(core): building/retrying/player resolvers, request persistence, fulfil`.

---

### Task 5 : socle des métiers et embauche

**Files :**
- Create : `job/{JobType,JobRegistry,Job,JobAI,HiringMode,WorkerModule,JobXp}.java`
- Modify : `CitizenData` (`Optional<Job> job` et `workBuilding`), `CitizenAI` (état `WORKING`), `Colony` (`settings().autoHiring()`), `ColonySerializer` (le job d'un citoyen, et le `WorkerModule` persisté via `PersistentModule`)
- Test : `job/WorkerModuleTest.java`, `job/JobXpTest.java`, `citizen/CitizenAIWorkTest.java`

**Interfaces :**

```java
public record JobType(String id, Function<CitizenData, Job> factory) {}
public final class JobRegistry { public void register(JobType t); public Optional<JobType> byId(String id); public static JobRegistry defaults(); }
public abstract class Job {
    protected Job(JobType type, CitizenData citizen);
    public JobType type(); public CitizenData citizen();
    public abstract JobAI createAI(Colony colony, BodyId body);
    public int actionsDone(); public void incrementActions(); public void clearActions();
    public JsonObject write(); public void read(JsonObject o);
}
public interface JobAI { void tick(); String stateName(); boolean canBeInterrupted(); }
public enum HiringMode { DEFAULT, AUTO, MANUAL, LOCKED }
public final class WorkerModule implements PersistentModule, TickingModule, BuildingEventsModule {
    public WorkerModule(JobType job, Skill primary, Skill secondary, int maxWorkers, boolean assignableAtLevel0);
    public List<Integer> workers(); public HiringMode hiringMode(); public void setHiringMode(HiringMode m);
    public boolean canAssignCitizens(Building b);     // assignableAtLevel0 || (b.level() > 0 && b.isBuilt())
    public boolean hire(Colony c, Building b, CitizenData citizen);   // fails if full or cannot assign
    public void fire(Colony c, Building b, int citizenId);
    public Skill primary(); public Skill secondary(); public JobType job();
}
public final class JobXp { public static void award(CitizenData c, Skill primary, Skill secondary, double xp, int workLevel, int homeLevel); }
```

`TickingModule.onColonyTick(Building)` ne reçoit pas la colonie. On étend donc l'interface avec une méthode par défaut `onColonyTick(Colony colony, Building building)`, qui appelle l'ancienne, et c'est elle que `BuildingManager.onColonyTick(Colony)` utilise. Il faut mettre à jour l'appel dans `Colony`.

**Comportement :**
- **Embauche automatique** (`MC/.../WorkerBuildingModule.onColonyTick`, `BuildingUtils.canAutoHire`), à chaque tick lent :
  - condition : pas plein, `canAssignCitizens`, et (`AUTO`, ou `DEFAULT` avec `colony.settings().autoHiring()`) ;
  - elle prend le premier citoyen **par id croissant** qui n'a pas de bâtiment de travail et n'est pas un enfant.
- **hire** : `citizen.setJob(type.factory.apply(citizen))`, `workBuilding = b.position()`, ajout aux travailleurs, `markDirty`.
- **fire** : le job est retiré et l'IA remise à l'errance.
- **JobXp.award** :
  - multiplicateur `xp × (1 + (workLevel + homeLevel)/10) × (1 + Intelligence/100)`, et 0 si la saturation vaut 0 ;
  - primaire 100 %, secondaire 50 % ;
  - complémentaire du primaire +10 %, adverse du primaire −10 % ;
  - complémentaire du secondaire +5 %, adverse du secondaire −5 % ;
  - on passe par `Skills.addXp`, qui applique les plafonds.
- **CitizenAI** :
  - nouvel état `WORKING`. Depuis `IDLE`, si le citoyen a un job et un corps vivant, on passe en `WORKING` ;
  - en `WORKING`, `jobAI.tick()` est appelé à chaque tick. L'IA du métier gère elle-même sa cadence avec sa propre state machine (cadence 5, comme MineColonies) ;
  - si le job disparaît, retour en `IDLE`.

**Tests :**
- `autoHiresFirstJoblessByIdEverySlowTick` ;
- `manualAndLockedNeverAutoHire` ;
- `defaultHiresOnlyWhenColonyAutoHiring` ;
- `level0BuildingCannotHireUnlessAssignableAtLevel0` ;
- `fireReturnsCitizenToWander` ;
- `jobXpSplitMatchesMineColonies` : valeurs exactes pour primary = Adaptability et secondary = Athletics ;
- `noXpWhenStarving` ;
- `workerModulePersists`.

- [ ] RED, puis GREEN, puis commit : `feat(core): job framework and auto-hiring`.

---

### Task 6 : plans, structure précalculée, claims par niveau, types de bâtiments

**Files :**
- Create : `construction/{Blueprint,BlueprintEntry,BlueprintSource,StructurePlan,Stage,ClaimRadius,LivingModule,ConstructionBuildingTypes}.java`
- Modify : `Building` (`List<BlockPos> containers`, `deconstructed`, `registerContainer`), `BuildingTypes.defaults()` (enregistre `builder` et `residence` via `ConstructionBuildingTypes`), `TerritoryIndex` (`claimSquareBounded(colonyId, ClaimCell center, int radius, ClaimCell colonyCenter, int maxSize)`)
- Test : `construction/blueprint/StructurePlanTest.java`, `construction/shared/ClaimRadiusTest.java`

**Interfaces :**

```java
public record BlueprintEntry(BlockPos offset, BlockState state, boolean hasContainer) {}   // offset from hut block, already rotated
public record Blueprint(String key, List<BlueprintEntry> entries, BlockPos min, BlockPos max) {} // min/max relative to hut
public interface BlueprintSource {
    Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation);
    List<String> styles();
}
public enum Stage { CLEAR, SOLID, DECORATE, REMOVE, DONE }
public final class StructurePlan {
    public static StructurePlan build(Blueprint bp, BlockPos hut, ItemCatalog catalog);
    public List<BlockPos> clearList();        // every pos in [hut+min, hut+max] top-down (y desc, then x, z asc), excluding hut
    public List<BlueprintEntry> solidList();  // kind SOLID, bottom-up (y asc, x asc, z asc)
    public List<BlueprintEntry> decoList();   // NON_SOLID and FLUID, bottom-up
    public List<BlockPos> removeList();       // all non-AIR entry positions, top-down
    public BlockPos worldPos(BlueprintEntry e);
    public boolean isDone(BlueprintEntry e, WorldBlocks world);   // world state equals entry.state (key and rotation)
}
public final class ClaimRadius { public static int of(String buildingTypeId, int level); }
public final class LivingModule implements BuildingModule { public int capacity(Building b); } // = level
public final class ConstructionBuildingTypes {
    public static final BuildingType BUILDER, RESIDENCE;  // ids "hycolony:builder", "hycolony:residence"; hut keys "hut.builder", "hut.residence"
    public static void register(BuildingRegistry r);
}
```

`BUILDER` a les modules `worker` (`WorkerModule(builder job, Adaptability, Athletics, 1, true)`) et `resources` (`BuildingResourcesModule`, tâche 8). En tâche 6, il ne déclare que les modules existants. La tâche 8 ajoute le module `resources` à `BUILDER` via `ConstructionBuildingTypes`. Le job `hycolony:builder` est enregistré en tâche 9. En tâche 6, `BUILDER` peut référencer un `JobType` défini dans `ConstructionBuildingTypes.BUILDER_JOB`, dont la factory est remplacée en tâche 9.

**Comportement :**
- `StructurePlan.build` ne s'exécute **qu'une fois par ordre**.
- Tris stables : `clearList` y descendant ; `solidList` et `decoList` y montant, puis x, puis z.
- `clearList` exclut la position de la cabane et les positions de la boîte dont l'entrée du plan est identique à l'état actuel. **Ce filtre ne se fait pas au précalcul** : la règle de saut au moment du pas regarde le monde.
- `ClaimRadius` suit les tables des Global Constraints. Les autres types reprennent la table par défaut, et le niveau 0 vaut 0.
- À chaque changement de niveau (tâche 9), on appelle `claimSquareBounded` avec la cellule du bâtiment et le rayon, borné à `maxColonySize` autour du centre de la colonie. Aucune cellule n'est volée.

**Tests :**
- `solidAndDecoSortedBottomUp` ;
- `clearListTopDownExcludesHut` ;
- `removeListOnlyNonAir` ;
- `isDoneComparesKeyAndRotation` ;
- `claimRadiusTables` : toutes les valeurs ;
- `claimBoundedByMaxColonySize` ;
- `twentyThousandEntryPlanBuildsUnder100ms`.

- [ ] RED, puis GREEN, puis commit : `feat(core): blueprints, structure plan, level claim radii, builder and residence types`.

---

### Task 7 : ordres de travail et `WorkManager`

**Files :**
- Create : `construction/workorder/{WorkOrder,WorkOrderType,WorkManager,WorkOrderRefusal}.java`
- Modify : `Colony` (champ `WorkManager work`, tick toutes les **20** ticks en ACTIVE), `ColonySerializer` (`workOrders`), `ColonyManager` (`requestWorkOrder`, `moveWorkOrder`, `deleteWorkOrder`), `BuildingManager` (au retrait d'une cabane de constructeur : libère ses ordres et annule ses requêtes)
- Test : `construction/workorder/WorkManagerTest.java`

**Interfaces :**

```java
public enum WorkOrderType { BUILD, UPGRADE, REPAIR, REMOVE }
public final class WorkOrder {
    public int id(); public WorkOrderType type(); public BlockPos buildingPos(); public int targetLevel();
    public String style(); public int rotation(); public int priority(); public void setPriority(int p);
    public Optional<BlockPos> claimedBy(); public Stage stage(); public int progressIndex(); public boolean requested();
    // package-private setters for claim/stage/progress/requested
    public JsonObject write(); public static WorkOrder read(JsonObject o);
}
public enum WorkOrderRefusal { NO_PERMISSION, ALREADY_EXISTS, MAX_LEVEL, NOT_BUILT, BUILDER_NECESSARY, BUILDER_TOO_FAR_AWAY, OUT_OF_COLONY, NO_BLUEPRINT }
public final class WorkManager {
    public WorkManager(Colony colony);
    public Either<WorkOrder, WorkOrderRefusal> request(UUID player, BlockPos buildingPos, WorkOrderType type, String style, Optional<BlockPos> builder);
    public void cancel(int orderId);                           // releases claim, cancels builder requests of this order, resets progress
    public void move(int orderId, int delta);                  // priority +/-1 relative to neighbour, as MC WindowInfoPage
    public List<WorkOrder> ordered();                          // priority desc, id asc
    public Optional<WorkOrder> byId(int id); public Optional<WorkOrder> claimedBy(BlockPos builderHut);
    public void tick();                                        // every 20 ticks: assignment
    public void complete(WorkOrder o);                         // removes it
    public static boolean canBuild(Building builderHut, WorkOrder o, int builderLevel); // level>=target || level==5 || own hut ; distSq<=10000
}
public sealed interface Either<L, R> { record Left<L,R>(L value) implements Either<L,R> {} record Right<L,R>(R value) implements Either<L,R> {} }
```

`Either` va dans `kernel` (`kernel/Either.java`).

**Comportement** (`MC/core/colony/buildings/AbstractBuilding.requestWorkOrder`, `MC/core/colony/managers/WorkManager.java`, `MC/core/colony/workorders/WorkOrderBuilding.java`) :
- **Refus, dans cet ordre** :
  1. `MANAGE_HUTS` absente → `NO_PERMISSION`.
  2. Un ordre existe déjà à cette position → `ALREADY_EXISTS`.
  3. BUILD/UPGRADE au niveau maximal → `MAX_LEVEL`.
  4. REPAIR au niveau 0 → `NOT_BUILT`.
  5. Pour tout ordre sauf REMOVE, aucun constructeur employé (cabane avec un travailleur) n'a `niveau ≥ cible`, et ce n'est pas `canBeBuiltByBuilder` → `BUILDER_NECESSARY`. **Exception** : l'ordre concerne une cabane de constructeur et `cible == niveau + 1`.
  6. Aucun constructeur n'est à ≤ 100 blocs et aucun n'a été choisi → `BUILDER_TOO_FAR_AWAY`.
  7. `BlueprintSource` renvoie vide pour (style, type, cible) → `NO_BLUEPRINT`.
  8. Une position du plan est hors territoire → `OUT_OF_COLONY`.
- **Cibles** :
  - BUILD : `cible = niveau + 1` (niveau 0) ;
  - UPGRADE : `niveau + 1` ;
  - REPAIR : `niveau` ;
  - REMOVE : `niveau`, avec le plan du niveau actuel.
- **En cas de succès** : message `hycolony.workorder.created` aux membres, et événement `WorkOrderCreated`.
- **Rotation** : `building.rotation()`. **Style** : celui passé en paramètre, sinon celui du bâtiment, sinon le premier style de `blueprints.styles()`.
- **tick()** : on trie les ordres réclamés d'abord, puis par `ordered()`. Chaque ordre non réclamé va au **premier** bâtiment constructeur (dans l'ordre de `BuildingManager`) qui a un travailleur, qui n'a pas d'ordre, dont le mode n'est pas MANUAL, et pour lequel `canBuild` est vrai.
- **Constructeur retiré** : ses ordres sont libérés (`claimedBy` vide, étape et progression remises à zéro), et ses requêtes ouvertes sont annulées.

**Tests :**
- une création réussie ;
- chaque refus, un test par valeur de l'enum ;
- `builderCanOrderOwnNextLevelAtLevel0` ;
- `sortedByPriorityThenId` ;
- `moveSwapsPriorityWithNeighbour` ;
- `assignsToFirstEligibleIdleBuilder` ;
- `level1BuilderCannotTakeLevel2Order` ;
- `builderAtLevel5TakesAnything` ;
- `tooFarBuilderNotAssigned` ;
- `cancelReleasesAndCancelsRequests` ;
- `builderHutRemovedReleasesOrderAndCancelsRequests` ;
- `workOrdersPersist`.

- [ ] RED, puis GREEN, puis commit : `feat(core): work orders and work manager with MineColonies rules`.

---

### Task 8 : besoins, seaux, module de ressources

**Files :**
- Create : `construction/resources/{NeededResources,Buckets,BuildingResourcesModule}.java`
- Modify : `ConstructionBuildingTypes.BUILDER` (+ module `resources`)
- Test : `construction/workorder/ResourcesTest.java`

**Interfaces :**

```java
public final class NeededResources {
    public static NeededResources compute(StructurePlan plan, WorldBlocks world, ItemCatalog catalog); // SOLID then DECO not-yet-done entries -> item counts (insertion order kept)
    public Map<ItemKey, Integer> remaining();   // LinkedHashMap
    public void reduce(ItemKey item, int n);    // on placement
    public int total();
}
public final class Buckets {
    public static final int BUCKET_STACKS = 27 - 9;
    public static List<Map<ItemKey,Integer>> split(Map<ItemKey,Integer> needs, ToIntFunction<ItemKey> maxStack); // each bucket <= 18 stacks; an item spanning buckets is split
}
public final class BuildingResourcesModule implements PersistentModule {
    public void start(WorkOrder o, NeededResources needs);         // resets buckets
    public Optional<Map<ItemKey,Integer>> currentBucket(); public Optional<Map<ItemKey,Integer>> nextBucket();
    public void advanceBucketIfSatisfied(Inventory builderInv, int hutCount /* via function */);
    public NeededResources needs();
    public Map<ItemKey,Integer> missingForCurrentAndNext(Inventory builderInv, java.util.function.ToIntFunction<ItemKey> hutCount); // what to request
    public int orderId(); public Stage stage(); public int progressIndex(); public void progress(Stage s, int index);
    // persisted: orderId, stage, progressIndex (needs are recomputed on load, as MC)
}
```

**Comportement :**
- `compute` ne compte que les entrées que `isDone` renvoie fausses, et pour lesquelles `itemForBlock` existe.
- `split` remplit les seaux dans l'ordre d'insertion. Un objet de 200 unités avec `maxStack` 64 occupe 4 piles.
- `missingForCurrentAndNext` : pour chaque objet du seau courant et du suivant, on calcule `besoin − (inventaire + cabane)`, borné à 0, et on ne garde que les valeurs strictement positives.
- On ne demande rien pendant `CLEAR` ni `REMOVE`. Ce sont la tâche 9 et `BuilderAI` qui appliquent cette règle.

**Tests :**
- `computeSkipsAlreadyPlacedAndItemless` ;
- `splitRespectsEighteenStacks` ;
- `itemSpanningBucketsIsSplit` ;
- `missingSubtractsInventoryAndHut` ;
- `progressPersistsNeedsRecomputed`.

- [ ] RED, puis GREEN, puis commit : `feat(core): needed resources and builder buckets`.

---

### Task 9 : métier et IA du constructeur, fin de chantier

C'est le portage de `MC/core/entity/ai/workers/AbstractEntityAIStructure.java`, `AbstractEntityAIStructureWithWorkOrder.java`, `builder/EntityAIStructureBuilder.java` et `AbstractEntityAIBasic.java` (vidage, `NEEDS_ITEM`). Il faut lire ces fichiers et l'analyse, § 6-7. Modèle d'implémentation recommandé : opus.

**Files :**
- Create : `construction/builder/{BuilderJob,BuilderAI,BuilderState,BuilderTimings}.java`
- Modify : `kernel/port/CitizenBodies` (ajoute `setHeldItem(BodyId, Optional<ItemKey>)` et `playAnimation(BodyId, BodyAnimation)`, avec `enum BodyAnimation { BUILD, MINE }` dans `kernel/port`), `FakeBodies`, `plugin/.../HytaleCitizenBodies` (implémentations **no-op minimales**, les vraies arrivent en partie B), `ColonyEvents` (`BuildingLevelChanged(Colony, Building, int oldLevel, int newLevel)`)
- Test : `construction/builder/BuilderAITest.java`, `construction/builder/BuilderTimingsTest.java`

**Interfaces :**

```java
public enum BuilderState implements IState { IDLE, START_WORKING, LOAD_STRUCTURE, GATHERING_REQUIRED_MATERIALS, NEEDS_ITEM,
                                              BUILDING_STEP, MINE_BLOCK, INVENTORY_FULL, COMPLETE_BUILD }
public final class BuilderJob extends Job { public static final JobType TYPE; /* id "hycolony:builder" */ }
public final class BuilderAI implements JobAI { public BuilderAI(Colony colony, CitizenData citizen, BodyId body); }
public final class BuilderTimings {
    public static int placeDelay(int primarySkill);                                   // 15*10/(p/2+10), int, >=1
    public static int breakDelay(int secondarySkill, float hardness, float toolSpeed); // 500*0.85^(s/2)*h/speed*0.5, >=1
}
```

**Comportement** (cadence de la state machine = 5) :
- **Transitions communes** :
  - `AI_BLOCKING` delay : si `delay > 0`, on joue l'animation, on retire la cadence, et on ne change pas d'état.
  - `STATE_BLOCKING` : si l'inventaire est plein ou si `actionsDone ≥ 4096`, on passe à `INVENTORY_FULL`.
  - `AI_BLOCKING` : si une requête synchrone du constructeur est ouverte ou complétée, on passe à `NEEDS_ITEM`.
- **IDLE / START_WORKING** : aller à la cabane (`walkTo`, avec les mêmes helpers que l'errance, et arrivée à ≤ 2 blocs), puis prendre `work.claimedBy(hut)`. Sans ordre, on reste en IDLE.
- **LOAD_STRUCTURE** : `blueprints.load(...)`, `StructurePlan.build`, `NeededResources.compute`, `resources.start`. L'étape initiale est `CLEAR` pour un BUILD de niveau 0, `REMOVE` pour REMOVE, `SOLID` sinon. On repart de la progression persistée si l'ordre correspond.
- **GATHERING_REQUIRED_MATERIALS** :
  - aller à la cabane et en extraire les objets du seau courant vers l'inventaire ;
  - puis créer les requêtes `StackRequest(item, n, n, true)` synchrones pour `missingForCurrentAndNext`, via `colony.requests().createAndAssign(building, req, citizenId)`, **seulement s'il n'existe pas déjà une requête ouverte pour cet objet**.
- **NEEDS_ITEM** (analyse, § 6) :
  - pour chaque requête complétée de ce citoyen, aller à `resolver.location()` (la cabane pour `BuildingResolver`) ;
  - prendre les `deliveries` : extraire des conteneurs vers l'inventaire (si elles sont déjà dans l'inventaire du citoyen, via fulfil, ne rien extraire) ;
  - puis `updateState(RECEIVED)`.
  - Une livraison manquante (le coffre a été vidé entre-temps) est redemandée.
- **BUILDING_STEP**, pour l'étape en cours, à partir de `progressIndex` ; on examine au plus 10 000 positions par appel :
  - `CLEAR` : position non AIR, pas `UNBREAKABLE`, pas la cabane, et différente de l'entrée du plan à cet endroit → `MINE_BLOCK`.
  - `SOLID` / `DECORATE` :
    - on saute si `isDone` ;
    - sinon, s'il y a un bloc différent, non AIR et non `UNBREAKABLE` à cette position → `MINE_BLOCK` d'abord ;
    - sinon, l'objet est-il dans l'inventaire ?
      - non → `GATHERING_REQUIRED_MATERIALS`, en gardant `progressIndex` ;
      - oui → `blocks.place(pos, state, hasContainer)`, `inventory.extract(item, 1)`, `needs.reduce`, XP 0.05 via `JobXp`, `incrementActions`, `delay = placeDelay(primary)`, animation BUILD. Si l'entrée a un conteneur, `building.registerContainer(pos)`.
  - `REMOVE` : chaque position non AIR de `removeList` → `MINE_BLOCK`. Aucun matériau n'est demandé.
  - En fin de liste, on passe à l'étape suivante (`CLEAR` → `SOLID` → `DECORATE` → `DONE`, ou `REMOVE` → `DONE`), puis `COMPLETE_BUILD`.
  - **Déplacement** : si `distSq(workPos, bloc) > 10²`, on choisit une nouvelle `workPos` à ≤ 4 blocs du bloc (au-dessus et à côté) et on y marche. Tant qu'on n'est pas arrivé, on garde l'état.
  - La progression est persistée à chaque changement : `resources.progress(stage, index)` + `markDirty`.
- **MINE_BLOCK** :
  - outil requis (`catalog.toolFor`) ? On prend le meilleur outil de ce type dans l'inventaire (niveau ≤ niveau de la cabane), sinon dans la cabane. S'il n'y en a pas, on crée une `ToolRequest(type, 0, hutLevel)` et on passe à `NEEDS_ITEM` ;
  - premier passage : `delay = breakDelay(secondary, hardness, toolSpeed or 1)`, avec `setHeldItem` et l'animation MINE ;
  - passage suivant : `drops = blocks.breakBlock(pos)` ;
  - si l'étape est `CLEAR` et que `isOre` est vrai, les drops sont jetés. Sinon ils sont insérés dans l'inventaire, et **le reste est inséré dans la cabane, sinon jeté avec une entrée de journal `debrisLost`** ;
  - puis `damageTool`, XP 0.05, `incrementActions`, et retour en `BUILDING_STEP`.
- **INVENTORY_FULL** : aller à la cabane et déposer tout ce qui n'est pas dans le seau courant, puis `clearActions`. Si la cabane est pleine, on s'arrête à ce qui a pu être déposé, sans boucle infinie : on repasse en `BUILDING_STEP`, et le prochain surplus est jeté au minage.
- **COMPLETE_BUILD** :
  - BUILD / UPGRADE / REPAIR : `level = cible`, `built = true`, `deconstructed = false`. Pour REMOVE : `deconstructed = true`, niveau conservé ;
  - claims (`ClaimRadius` via `claimSquareBounded`), +8 d'XP ;
  - journal `buildingBuilt` / `buildingUpgraded` / `buildingRepaired` / `buildingDeconstructed` ;
  - message `hycolony.build.complete` (nom et niveau) aux membres ;
  - événement `BuildingLevelChanged` ;
  - `work.complete(order)`, réinitialisation des ressources, retour en IDLE.

**Tests :**
- `BuilderTimingsTest` :
  - `placeDelayFormula` : avec primary 1, 20 et 99, valeurs exactes calculées ;
  - `breakDelayFormulaAndMinimum`.
- `BuilderAITest`, sur `TestContexts` avec les fakes :
  - `idleWithoutOrderStaysIdle` ;
  - `takesClaimedOrderAndLoadsStructure` ;
  - `clearMinesNonAirKeepsDropsVoidsOres` ;
  - `solidBeforeDecoBottomUp` : vérifie l'ordre des poses dans `FakeWorldBlocks` ;
  - `skipsAlreadyCorrectBlocks` ;
  - `requestsOnlyMissingOfCurrentAndNextBucketOnce` ;
  - `gathersFromHutBeforeRequesting` ;
  - `fullInventoryDumpsKeepingBucketItems` ;
  - `clearWithFullHutDoesNotLoop` : 10 000 ticks, pas d'exception, progression > 0 ;
  - `missingToolCreatesToolRequest` ;
  - `completionSetsLevelClaimsXpLogAndEvent` ;
  - `removeOrderDeconstructsKeepingLevel` ;
  - `movesOnlyWhenNextBlockBeyondTenBlocks`.

- [ ] RED, puis GREEN, puis commit : `feat(core): builder job and AI with MineColonies structure steps`.

---

### Task 10 : modèles de vue et actions de la colonie

**Files :**
- Create : `app/ui/{BuildingView,BuilderResourcesView,RequestsView,WorkOrdersView}.java`
- Modify : `app/ui/UiPort` (`showBuilding`, `showBuilderResources`, `showRequests`, `showWorkOrders`), `FakeUi`, `plugin/.../HytaleUiPort` (implémentations **temporaires** qui envoient un message « à venir » ; les vraies fenêtres arrivent en partie B), `ColonyManager`
- Test : `app/view/ViewsTest.java`

**Interfaces :**

```java
public record BuildingView(int colonyId, BlockPos pos, String typeId, int level, int maxLevel, boolean built, boolean deconstructed,
        List<WorkerRow> workers, List<WorkerRow> hireable, HiringMode hiringMode,
        Optional<OrderRow> order, Set<WorkOrderType> allowed, List<String> styles, String style, boolean canManage) {
    public record WorkerRow(int citizenId, String name) {}
    public record OrderRow(int id, WorkOrderType type, int targetLevel, Optional<String> builderName, int percent) {}
}
public record BuilderResourcesView(int colonyId, BlockPos hut, List<ResourceRow> rows, int percent, String stage) {
    public enum Status { DONT_HAVE, NEED_MORE, HAVE_ENOUGH, NOT_NEEDED }       // red / orange / green / black
    public record ResourceRow(ItemKey item, int needed, int available, int playerHas, Status status) {}
}
public record RequestsView(int colonyId, List<RequestRow> rows) {
    public record RequestRow(RequestToken token, String description, String requesterName, int playerHas) {}
}
public record WorkOrdersView(int colonyId, List<OrderLine> orders, boolean canManage) {
    public record OrderLine(int id, WorkOrderType type, String buildingName, int targetLevel, int priority, Optional<String> builderName) {}
}
// ColonyManager actions (all check permissions, return boolean or Optional refusal, and re-show the view)
public void openBuilding(UUID player, BlockPos hutPos);          // ACCESS_HUTS; town hall hut opens TownHallView as before (it gets a "building" action)
public Optional<WorkOrderRefusal> orderWork(UUID player, BlockPos hutPos, WorkOrderType type, String style);
public boolean cancelWork(UUID player, BlockPos hutPos);          // MANAGE_HUTS
public boolean hire(UUID player, BlockPos hutPos, int citizenId); public boolean fire(UUID player, BlockPos hutPos, int citizenId);
public boolean setHiring(UUID player, BlockPos hutPos, HiringMode mode);
public void openBuilderResources(UUID player, BlockPos hutPos);
public void openRequests(UUID player, int colonyId);
public void openWorkOrders(UUID player, int colonyId);
public boolean moveWorkOrder(UUID player, int colonyId, int orderId, int delta);
public boolean deleteWorkOrder(UUID player, int colonyId, int orderId);
```

**Comportement :**
- `allowed` :
  - BUILD si niveau 0 et non déconstruit ;
  - UPGRADE si 0 < niveau < max ;
  - REPAIR si niveau > 0 ou déconstruit (« Construire » pour un bâtiment déconstruit) ;
  - REMOVE si niveau > 0 et non déconstruit ;
  - vide si un ordre existe déjà (le bouton devient Annuler).
- **Statut d'une ressource** (`MC/.../BuildingBuilderResource.getAvailabilityStatus`) :
  - `available ≥ needed` → HAVE_ENOUGH ;
  - `playerHas == 0` → DONT_HAVE ;
  - `available + playerHas < needed` → NEED_MORE ;
  - sinon HAVE_ENOUGH (le joueur peut compléter) ;
  - `needed == 0` → NOT_NEEDED.
- `percent` = objets posés / total du plan.

**Tests :**
- `allowedActionsByLevelAndOrder` ;
- `resourceStatusColours` ;
- `requestsViewListsPlayerAssignedOnly` ;
- `actionsCheckPermissions` : un Neutral est refusé partout ;
- `orderWorkReturnsRefusal` ;
- `hireFireFromView`.

- [ ] RED, puis GREEN, puis commit : `feat(core): building, resources, requests and work order views with actions`.

---

### Task 11 : simulation de bout en bout, performances, fixtures

**Files :**
- Create : `construction/ConstructionSimulationTest.java`, `testing/FakeBlueprints.java` (plan 3×3×3 : sol SOLID, murs SOLID, une torche NON_SOLID, un coffre avec conteneur, et un plan de 20 000 entrées pour la perf), fixture `colony-v2-midbuild.json`
- Modify : si la simulation révèle des bugs, corriger dans les unités concernées, **avec un test unitaire qui reproduit le bug**.

**Tests :**
- `fullBuilderHutBuild` :
  1. fonder, poser la cabane du constructeur, faire tourner les ticks lents jusqu'à l'embauche ;
  2. `orderWork(BUILD)` ;
  3. faire tourner (au maximum 200 000 ticks) ;
  4. à la première requête du joueur, faire `fulfil` avec un `FakePlayerInventory` rempli ;
  5. vérifier : le monde est exactement conforme au plan, le niveau vaut 1, `built`, l'XP a augmenté, le journal contient `buildingBuilt`, les claims sont étendus, et au moins une requête est passée par RECEIVED.
- `restartMidBuildResumesExactly` : au milieu de `SOLID`, sauvegarde, rechargement dans un nouveau `ColonyManager` (même monde factice), fin du chantier. Vérifier :
  - aucun bloc reposé deux fois (compteur de `place` par position ≤ 1, sauf casse par le joueur) ;
  - aucun seau redemandé.
- `playerBreaksPlacedBlockItIsRebuilt`.
- `partialFulfilClosesAndBuilderRerequests` : le joueur fournit la moitié, la requête est close, et une nouvelle requête pour le reste apparaît.
- `upgradeThenRemove` : de 1 vers 2 (plan différent), puis REMOVE, puis `deconstructed`.
- `twoBuildersTwoOrders` : chaque ordre revient à un constructeur différent.
- `perfTwentyThousandBlockPlan` : ticks de colonie moyens < 2 ms mesurés sur 20 000 ticks, après un échauffement ; `@Tag("perf")`, et il tourne dans `:core:test`.
- `midbuildFixtureLoadsAndCompletes` : la fixture v2 à mi-chantier se charge et le chantier se termine.

- [ ] Écrire les tests, les faire passer (corriger les bugs révélés en TDD).
- [ ] `./gradlew build`, au vert : core et compilation du plugin.
- [ ] Commit : `test(core): end-to-end construction simulation, perf and midbuild fixture`.
