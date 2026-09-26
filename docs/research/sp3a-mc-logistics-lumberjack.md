# SP3a research: warehouse, courier, lumberjack (MineColonies `version/main`)

Source: `ldtteam/minecolonies`, branch `version/main`, commit `6b3916a` (2026-09-23). Paths are shortened:
`api/…` = `src/main/java/com/minecolonies/api/…`, `core/…` = `src/main/java/com/minecolonies/core/…`.
Ticks: 20 ticks = 1 s. "Delay" means the tick interval of an `AITarget` in the `TickRateStateMachine`.

---

## 0. Shared rules these systems depend on

| Rule | Value | Source |
|---|---|---|
| Default max hut level | `CONST_DEFAULT_MAX_BUILDING_LEVEL = 5`; warehouse, courier and lumberjack are all 5 | `api/util/constant/BuildingConstants.java` |
| Resolver priorities | Building 200, **Warehouse 150**, Crafting 125, Default 100 (**Delivery and Pickup resolvers**), Retrying 50, Player 0 | `api/util/constant/RSConstants.java`, `AbstractRequestResolver.getPriority()` |
| Suitability metric | lower is better (the same as our `RequestManager.assignNow`) | |
| Every hut is a container | the hut block is a 27-slot rack (`AbstractTileEntityColonyBuilding extends TileEntityRack`). `getContainers()` = registered racks + hut position | `core/colony/buildings/AbstractBuildingContainer.java` |
| Rack registration | `registerBlockPosition`: a `BlockMinecoloniesRack` is added to `containerList` and gets `setBuildingPos`. Called for each block the builder places | same |
| Rack size | `DEFAULT_SIZE = 27`, `SLOT_PER_LINE = 9`; an upgraded rack has `27 + 9·upgrades` slots | `api/util/constant/Constants.java`, `core/tileentities/TileEntityRack.java` |
| Max tool level of a worker hut | L0 → 1, L1..L4 → level, L5 (max) → unlimited (`WOOD_HUT_LEVEL = 0`). MC tool levels: 0 wood/gold, 1 stone, 2 iron, 3 diamond, 4 netherite | `api/colony/buildings/IBuilding.getMaxEquipmentLevel()` |
| Tool check | `checkForToolOrWeapon(type)` uses min level `TOOL_LEVEL_WOOD_OR_GOLD = 0` and max `building.getMaxEquipmentLevel()`. It first looks in the inventory, then the hut racks (`retrieveToolInHut`). If neither has one and no open or completed Tool request exists, it creates `Tool(type, min, max(maxEquip, min))`. It sets JobStatus STUCK and returns true (the caller goes back to START_WORKING / IDLE). `delay += DELAY_RECHECK (10)` | `core/entity/ai/workers/AbstractEntityAIBasic.java` |
| Rain | courier and lumberjack `WorkerBuildingModule(..., canWorkingDuringRain=false, ...)` | `core/colony/buildings/modules/BuildingModules.java` |
| Citizen inventory | 27 slots (`InventoryCitizen.DEFAULT_INV_SIZE`); research can add more | `api/inventory/InventoryCitizen.java` |
| Base move speed | `BASE_MOVEMENT_SPEED = 0.3` | `api/util/constant/CitizenConstants.java` |

### 0.1 The generic worker dump and pickup trigger (`AbstractEntityAIBasic`)
The lumberjack (and every producer) uses this. **The courier is excluded** (`&& !(job instanceof JobDeliveryman)`).

- Event `STATE_BLOCKING inventoryNeedsDump → INVENTORY_FULL`, checked every 100 ticks. It fires when `state != INVENTORY_FULL && state.isOkayToEat()` and one of these holds: the inventory is full, `job.actionsDone >= getActionsDoneUntilDumping()` (default `ACTIONS_UNTIL_DUMP = 32`), or `wantInventoryDumped()`.
- `INVENTORY_FULL → dumpInventory`, delay 20. The worker walks to `getBuildingToDump()` (its own hut) and dumps **one slot per call**. The amount per slot is `building.buildingRequiresCertainAmountOfItem(stack, alreadyKept, inventory=true)`: everything except what `getRequiredItemsAndAmount()` says to keep (see 2.7). Near the end of the inventory, if fewer than `MIN_OPEN_SLOTS·2 = 10` slots are open, a slot is dumped whole anyway: with a 50 % chance if its count is below `CHANCE_TO_DUMP_50 = 16`, otherwise with probability `CHANCE_TO_DUMP (8) / count`. Each dumped amount is added to `dumpedItems`.
- If `InventoryUtils.isBuildingFull(hut)` (no container has a free slot): the worker shows the "inventory full chest" interaction and calls `createPickupRequest(dumpedItems, force=true)` when `pickUpPriority > 0`. The dump then ends.
- After a normal dump, if `isAfterDumpPickupAllowed()` (true, or `currentRequest == null` for crafters), `pickUpPriority > 0` and `dumpedItems > 0`: `createPickupRequest(dumpedItems, false)`. Then `clearActionsDone()` and go to IDLE, or PAUSED.

---

## 1. Warehouse

Files: `core/colony/buildings/workerbuildings/BuildingWareHouse.java`, `core/colony/buildings/modules/{WarehouseModule,WarehouseRequestQueueModule,CourierAssignmentModule}.java`, `core/colony/requestsystem/resolvers/{WarehouseRequestResolver,WarehouseConcreteRequestResolver}.java`, `core/colony/requestsystem/resolvers/core/AbstractWarehouseRequestResolver.java`, `core/tileentities/TileEntityWareHouse.java`, `core/network/messages/server/colony/building/warehouse/UpgradeWarehouseMessage.java`, `core/client/gui/modules/building/WarehouseOptionsModuleWindow.java`.

### 1.1 Building
- Schematic `"warehouse"`, `MAX_LEVEL = 5`, `canBeGathered() = false`. It is **not a worker hut**: nobody is employed there. Couriers are *attached* to it.
- Modules, from `apiimp/initializer/ModBuildingsInitializer.java`:
  - `WAREHOUSE_COURIERS` = `CourierAssignmentModule`.
  - `WAREHOUSE_OPTIONS` = `WarehouseModule`: holds `storageUpgrade`, an int.
  - `MIN_STOCK` = `MinimumStockModule`.
  - `WAREHOUSE_REQUEST_QUEUE` = `WarehouseRequestQueueModule`: holds `List<IToken> requestList`, persisted.
- Resolvers (`createResolvers`, on top of the standard building ones):
  - `WarehouseRequestResolver`
  - `WarehouseConcreteRequestResolver`
  - `DeliveryRequestResolver`
  - `PickupRequestResolver`

  All four are located at the warehouse.
- **How many warehouses:** current MC has **no placement limit**. `RegisteredStructureManager` keeps `List<IWareHouse> wareHouses`, `AbstractBlockHut.canPlaceAt` returns true, and the stock resolvers sum stock across warehouses. The old "one warehouse per colony" rule no longer exists in the code. If HyColony wants a single warehouse, that is a deliberate deviation and must be enforced at hut placement.

### 1.2 Level rules
| Level | Effect |
|---|---|
| each | courier slots `CourierAssignmentModule.getModuleMax() = level × 2` (L0 → 0, so no courier resolver works) |
| each | minimum-stock entries = `level × STOCK_PER_LEVEL(5) × (1 + research)` (`MinimumStockModule`) |
| ≥ 3 | "sort" button (`DEFAULT_REQUIRED_SORT_LEVEL = 3`, `canSort()`) |
| 3 | a research requirement ("warehouse master") references warehouse L3 (`DefaultResearchProvider`) |
| 5 only | storage upgrades: `MAX_STORAGE_UPGRADE = 3`. The GUI allows one only when `level >= maxLevel`; each costs **1 emerald block** from the player, not in creative. `upgradeContainers`: when `storageUpgrade < 3`, every rack in `getContainers()` except the hut gets `upgradeRackSize()` (+9 slots), then `storageUpgrade++` |
| — | racks registered later are upgraded up to `storageUpgrade` on registration. Every rack gets `setInWarehouse(true)`, which is also done again on `requestRepair` |

Capacity = 27 (hut) + Σ racks × (27 + 9·storageUpgrade). The number of racks comes from the blueprint of each level.

### 1.3 Courier attachment (`CourierAssignmentModule`, `onColonyTick`)
- If not full and `BuildingUtils.canAutoHire(building, hiringMode, delivery)`: every citizen whose job is `JobDeliveryman`, who is not assigned here and whose `findWareHouse()` is null gets assigned. Couriers are hired by their **courier hut** first; the warehouse only links existing couriers.
- Assigned citizens whose job is no longer `JobDeliveryman` are removed.
- `canAccessWareHouse(citizen)` = the citizen is assigned here.
- `JobDeliveryman.findWareHouse()` = the first warehouse whose CourierAssignmentModule contains the citizen.

### 1.4 Stock resolvers (priority 150, type `IDeliverable`)
`WarehouseRequestResolver` counts only **non-concrete** deliverables: `getWarehouseInternalCount` returns 0 for `IConcreteDeliverable`. It counts `hasBuildingEnoughElseCount(wh, predicate, count)`. That walks the containers in order, sums the matching items in each rack, and returns early once `≥ count`. The loop covers tools, tags, food and the like.

`WarehouseConcreteRequestResolver` counts **concrete** deliverables only (`Stack`, `StackList`). For each `possible` in `getRequestedItems()` it sums, honouring `matchNBT` / `matchDamage`. For an `INonExhaustiveDeliverable` (only `StackList`) it adds `max(0, count(possible, need=count+leftOver) − leftOver)`, so the warehouse keeps `leftOver` of each item. It returns early once `≥ count`.

Common logic, `AbstractWarehouseRequestResolver`:
- `canResolveRequest` is false if any of these holds:
  - the requester location equals this warehouse (it never serves itself);
  - the building is missing;
  - the request is a `MinimumStack` from another warehouse.
- Otherwise it sets `total = internalCount(this)` and returns false if `total <= 0`. It then adds the counts of the other warehouses and returns true as soon as `total >= count`. Finally it returns `total >= minCount`.
- `isRequestChainValid` walks to the root and always returns true: a no-op.
- `attemptResolveRequest`:
  - It sets `toKeep = leftOver` (INonExhaustive, otherwise 0) and scans `getMatchingItemStacksInWarehouse(matches)`. That returns (stack, rackPos) tuples, one per slot, over loaded non-empty racks, the hut included.
  - Per item type, the first `toKeep` items are skipped and the rest go to `available`.
  - If `available >= count || available >= minCount`, it returns `[]` and resolves now. Otherwise it returns one child request `copyWithCount(count − available)`, whose requester is the warehouse resolver. The parent waits for it and then resolves; the child's items end up in the warehouse.
- `resolveRequest` → state `RESOLVED`.
- **`getFollowupRequestForCompletion`** is how the warehouse fulfils a request. It re-scans the matching stacks with the same `leftOver` logic, rack by rack. For each stack it takes `count = min(remaining, stack − kept)`, calls `completedRequest.addDelivery(copy·count)`, and creates **`Delivery(start = rack position, target = requester.location, stack·count, priority = DEFAULT_DELIVERY_PRIORITY 13)`** with the warehouse resolver as requester. It stops when `remaining <= 0`. So there is **one Delivery per source slot**, and a 64-item request split over three slots makes three Deliveries. If none were created it returns null.
- `getSuitabilityMetric = max(dist/10, 1) + warehouseQueue.size()`, where `dist` is the Euclidean distance requester → warehouse, as an int.
- The four `on…Cancelled` / `on…Complete` callbacks are no-ops.

Note: the stock resolvers do **not** check for couriers. A warehouse with stock but no couriers still wins the request. Its Deliveries then find no courier resolver and fall to Retrying or Player.

### 1.5 Storing items (`TileEntityWareHouse.dumpInventoryIntoWareHouse`)
For each non-empty courier slot, pick a rack, in order:
1. the first rack with a free slot that already holds the same item (`hasItemStack(stack, 1, true)`);
2. the first rack with a free slot that has a "similar" stack;
3. the first completely empty rack, otherwise the rack with the most free slots.

Then transfer the slot. If no rack is found, send a colony message and **stop dumping** (`return`). The message goes out at most every `TICKS_FIVE_MIN = 6000` ticks: "full, upgrade possible" at L5 with fewer than 3 upgrades, "full, max upgrade" at L5 with 3, "full" below L5.

---

## 2. Courier (deliveryman)

Files: `core/colony/buildings/workerbuildings/BuildingDeliveryman.java`, `core/colony/buildings/modules/DeliverymanAssignmentModule.java`, `core/colony/jobs/JobDeliveryman.java`, `core/entity/ai/workers/service/EntityAIWorkDeliveryman.java`, `api/colony/requestsystem/requestable/deliveryman/{AbstractDeliverymanRequestable,Delivery,Pickup}.java`, `core/colony/requestsystem/resolvers/{DeliverymenRequestResolver,DeliveryRequestResolver,PickupRequestResolver}.java`, `core/colony/requestsystem/data/StandardRequestSystemDeliveryManJobDataStore.java`, `core/colony/buildings/AbstractBuilding.java` (`createPickupRequest`), `core/colony/buildings/AbstractBuildingContainer.java` (pickup priority), `core/network/messages/server/colony/building/{ForcePickupMessage,ChangeDeliveryPriorityMessage}.java`.

### 2.1 Hut and job
- `BuildingDeliveryman`: schematic `"deliveryman"`, max level 5. Modules: `COURIER_WORK` = `DeliverymanAssignmentModule(delivery, primary=Agility, secondary=Adaptability, rain=false, size=b->1)`, which gives **1 courier per hut**. Also `COURIER_TASK_VIEW` and `STATS_MODULE`. It overrides `canEat`: a courier never eats an item it is currently delivering. On removal the speed modifier is taken off.
- `JobDeliveryman`:
  - The task queue lives in a request-system data store: `LinkedList<IToken> queue` plus `Set<IToken> ongoingDeliveries`, persisted.
  - `getSaturationFactor = 1.2`.
  - `getInactivityLimit = 600`, counted in citizen-data updates, which run every 60 ticks: **36,000 ticks**.
  - `triggerActivityChangeAction(active)`: `true` → `requestManager.onColonyUpdate(req is Delivery or Pickup)` retries unassigned ones; `false` → `cancelAssignedRequests()` (every queued token → FAILED and removed). The same happens on job removal, which also deletes the data store.
- **Speed:** `onLevelUp` adds a MOVEMENT_SPEED modifier (ADDITION) of `Agility level × BONUS_SPEED_PER_LEVEL (0.003)` to the base 0.3. At Agility 50 the speed is 0.45, +50 %.
- **Parallel deliveries** = `1 + Adaptability level / 5` (integer division, from the hut's first worker).
- **Pickup carry limit** (courier hut level `L`): if `L < 5`, the courier cannot hold more once its inventory holds `>= 2^(L−1) + 1` stacks. L1 → 2 stacks, L2 → 3, L3 → 5, L4 → 9, L5 → no limit (the 27-slot inventory). Deliveries are limited only by parallel count and a full inventory.
- XP: pickup +0.05, each delivery +1.5. Each also calls `decreaseSaturationForContinuousAction`.

### 2.2 Requestables
`AbstractDeliverymanRequestable(priority)`, with `scaledPriority(p) = p`:
- `MAX_BUILDING_PRIORITY = 10`
- `DEFAULT_DELIVERY_PRIORITY = 13`
- `MAX_AGING_PRIORITY = 14`
- `PLAYER_ACTION_PRIORITY = 15`: defined but unused by the pickup paths.

`incrementPriorityDueToAging()` sets `priority = min(14, priority + 1)`. `equals` compares by priority.

- `Delivery(ILocation start, ILocation target, ItemStack stack, int priority)`: `start` is the **rack position** holding the items and `target` is the receiving building. Equality needs the same start, target and item (count ignored) and the same priority.
- `Pickup(int priority, int day, int quantity)`: the requester is the building to empty. `day` is the colony day from which it is "due".

Neither is an `IDeliverable`. They are plain `IRequestable`s, so the stock resolvers never see them.

### 2.3 Resolvers (priority 100)
`DeliverymenRequestResolver<R>`, the base of `DeliveryRequestResolver` (type DELIVERY) and `PickupRequestResolver` (type PICKUP). One of each per warehouse.
- `canResolveRequest`:
  - false on the client;
  - false if the requester is a **different** warehouse;
  - otherwise `hasCouriers()`: the warehouse exists and its CourierAssignmentModule is non-empty.
  - The Delivery and Pickup subclasses also require the warehouse to exist.
- `attemptResolveRequest`: `null` without couriers, otherwise `[]` (no children).
- `resolveRequest`: **appends the token to the warehouse `WarehouseRequestQueueModule`** if the warehouse has couriers. The request stays `IN_PROGRESS` until a courier finishes it.
- Metric:
  - Delivery: `max(dist/10, 1) + queue.size()`, where dist = requester → warehouse. For a Delivery the requester is the warehouse resolver itself, so this is `1 + queue size`.
  - Pickup: `dist` only.
- `onAssignedRequestCancelled`: finds the courier whose queue contains the token and calls `job.onTaskDeletion(token)`, which removes it. It also removes the token from the warehouse queue.
- `getFollowupRequestForCompletion` = null.

A failed Delivery goes through FAILED, which MC treats as cancelled (`StandardRequestManager` → `RequestHandler.onRequestCancelled`). As a child it triggers `onChildRequestCancelled`: the parent's deliveries are reset, all sibling children are cancelled, and the parent is reassigned. Our `RequestManager.onCancelled` already does exactly this.

### 2.4 Task assignment, pull model (`JobDeliveryman.getCurrentTask`)
Called at every AI step.
1. If the courier's own queue is non-empty, return the request at its head.
2. Otherwise take the warehouse queue, which all couriers of that warehouse share. Drop tokens whose request is gone. For each token compute:
   ```
   p = 1
   if target chunk not loaded: p -= 1000          // BUG: overwritten by the next line for any Delivery/Pickup
   p = requestable.priority
   if Pickup && pickup.day > colony.day: p -= 100 // not due yet
   p += queue.size() - queue.indexOf(token)        // FIFO bonus: older = higher
   p -= (int) sqrt(manhattan(source, target))      // source: Delivery.start, Pickup → warehouse pos
                                                   // target: Delivery.target, Pickup → requester pos
   ```
   Pick the maximum; the first one wins on ties.
3. If the chosen request is a Delivery, add its token to the courier queue first.
4. Walk the warehouse queue in order:
   - each entry **before** the chosen index gets `incrementPriorityDueToAging()` (+1, capped at 14);
   - any other entry with the **same target** as the chosen one (Delivery or Pickup) is moved into the courier queue, `extendedReqs++`;
   - stop when `extendedReqs >= maxParallelDeliveries`.
5. If the chosen request is a Pickup, add it last, after its same-target companions.
6. Remove all moved or dead tokens from the warehouse queue and mark it dirty. Return the chosen request. Note that the head of the courier queue can differ from the returned request when it is a Pickup.

Couriers therefore compete by pulling: whoever calls first takes the best task.

### 2.5 State machine (`EntityAIWorkDeliveryman`)
Constants:
- `DECISION_DELAY = 100` ticks
- `STANDARD_DELAY = 5`
- `PICKUP_DELAY = 5`
- `WALK_DELAY = 20`
- `PRIORITY_FORCING_DUMP = 10`
- `MIN_DISTANCE_TO_WAREHOUSE = 5` (unused)

The courier picks up item drops (`setCanPickUpLoot(true)`) and shows a backpack when its inventory is non-empty.

| State (okToEat) | Delay | Behaviour |
|---|---|---|
| IDLE | 1 | → START_WORKING |
| START_WORKING (✓) | 100 | Guard `checkIfExecute`: warehouse found → `setWorking(true)`, and false if the warehouse tile entity is missing. No warehouse → `setWorking(false)` plus a BLOCKING "no warehouse" interaction → false. Then `decide`, below |
| PREPARE_DELIVERY (✓) | 5 | load the items at the source racks |
| DELIVERY (✗) | 5 | walk to the target and insert |
| PICKUP (✓) | 5, then `WALK_DELAY` / 5 set inside | empty a building one slot at a time |
| DUMPING (✗) | 20 | walk to the warehouse and store everything |

**decide:**
- No task: walk to the warehouse (`WALK_DELAY` while walking → START_WORKING). Once there, go to DUMPING if the inventory is non-empty, otherwise START_WORKING.
- Delivery task: DUMPING if the inventory is non-empty (it must start empty), otherwise PREPARE_DELIVERY.
- Pickup task: PICKUP.

**prepareDelivery:**
- Not a Delivery → START_WORKING.
- `taskList = getTaskListWithSameDestination(current)`: the current Delivery plus the queued Deliveries with the same target and the same start, or both starts being container positions of one warehouse.
- Iterate with a running `alreadyInInv` tally. The first task whose item is not yet covered by the inventory is `nextPickUp`; `parallelDeliveryCount` = its 1-based index.
- If there is no `nextPickUp` **or** `parallelDeliveryCount > maxParallel` → DELIVERY.
- Walk to a safe position next to `nextPickUp.start`, staying in PREPARE_DELIVERY while walking. A full inventory → DUMPING.
- `addConcurrentDelivery(id)`. Then `gatherIfInTileEntity`: if the tile entity at `start` (a hut or rack) holds `>= count` of the item, transfer exactly `count` into the inventory and stay in PREPARE_DELIVERY (loop to the next task).
- If it is not there: when `parallelDeliveryCount > 1`, `removeConcurrentDelivery` and go to DELIVERY with what is already carried. Otherwise `finishRequest(false)` (FAILED), `removeConcurrentDelivery`, and START_WORKING.

**deliver:**
- Not a Delivery → DUMPING.
- Target building missing → `finishRequest(true)` and START_WORKING.
- Walk to the target (`WALK_DELAY`, stay in DELIVERY).
- For each inventory slot whose item matches one of the same-destination tasks: extract it all, then `forceItemStackToItemHandler(target, stack, keep = target.isItemStackInRequest)`. That inserts normally; if the target is full it **swaps out** a stack that is not part of an open request of the target's citizens, and the courier carries the swapped stack back.
  - If the leftover equals the whole stack (nothing fitted): `success = false`, an IMPORTANT "chest full" interaction, and the stack goes back in the courier's inventory.
  - The courier records statistics.
- Nothing matched: `finishRequest(false)` and START_WORKING.
- Otherwise +1.5 XP, `finishRequest(true)`, then START_WORKING if successful, DUMPING if not.

**pickup** (`setDelay(WALK_DELAY)` at the start of each call):
- Not a Pickup → START_WORKING.
- `cannotHoldMoreItems()` → reset slot and kept list, DUMPING.
- Building gone → `finishRequest(false)` and START_WORKING.
- Walk to it.
- `pickupFromBuilding`: over the building's combined item handler (hut + racks), skip empty slots from `currentSlot`. Past the last slot → return true (done). Otherwise `amount = building.buildingRequiresCertainAmountOfItem(stack, alreadyKept, inventory=false)`: what the building does **not** need to keep. Extract that amount into the courier inventory and return false.
- When done: +0.05 XP, `finishRequest(true)`. Go to DUMPING if the pickup priority is `>= 10`, otherwise START_WORKING (the courier then continues with other tasks before dumping).
- Inventory without an open slot → DUMPING.
- Otherwise `setDelay(5)`, `currentSlot++`, stay in PICKUP. The walk is 1 slot per 5 ticks.

**dump:** walk to the warehouse (`WALK_DELAY`), `dumpInventoryIntoWareHouse`, then START_WORKING.

**finishRequest(successful)** on the courier queue head:
- A null request pops the head.
- A Delivery: the legacy `ongoingDeliveries` int is always 0 in current code (only read from old NBT). So each token in the data store's `ongoingDeliveries` set that is IN_PROGRESS goes to RESOLVED or FAILED, and is removed from the queue and the set. This resolves every item loaded in PREPARE_DELIVERY at once.
- A Pickup: remove it and set RESOLVED or FAILED.
- Anything else: set the state and pop.

### 2.6 Pickup requests from buildings
- `pickUpPriority`, stored as `unscaledPickUpPriority` in `AbstractBuildingContainer`: **default 5**, range `[0, MAX_BUILDING_PRIORITY = 10]`, changed ±1 from the GUI (`ChangeDeliveryPriorityMessage`). **0 means never** (the old "Pickup: Never").
- `AbstractBuilding.createPickupRequest(qty, force)`:
  ```
  priority = force ? 10 : pickUpPriority
  if the building has any open PICKUP request:
      (intended: merge qty into the IN_PROGRESS pickup and pull its day earlier;
       BUG: the loop tests `token instanceof Pickup`, which is never true, so nothing merges)
      return false
  day = colony.day + max(0, (10 - pickUpPriority) - qty / 16)   // uses pickUpPriority even when forced
  createRequest(new Pickup(priority, day, qty), async=true); return true
  ```
  So there is **at most one open pickup per building**. Large dumps are due sooner; at the default priority 5, qty ≥ 80 is due today.
- Callers:
  - the worker dump (0.1): after a dump, non-forced;
  - hut full: forced, priority 10;
  - the GUI "force pickup" (`ForcePickupMessage`): `createPickupRequest(64, true)`;
  - crafters' secondary outputs are sent as `Delivery(hut → closest warehouse, priority 10)` (`AbstractEntityAICrafting.afterDump`).
- What gets picked up is decided by `buildingRequiresCertainAmountOfItem` / `getRequiredItemsAndAmount()`. The building keeps:
  - its `keepX` entries (the lumberjack keeps 1 axe and 1 shears);
  - the items in `request.getDeliveries()` of requests made by its resolvers;
  - food, `level × 2`, when `keepFood()`;
  - `IHasRequiredItemsModule` entries;
  - `IAltersRequiredItems` entries, which include min stock.
  - Better equipment than the kept item is not counted as kept.

### 2.7 Timing summary
Courier decisions: every 100 ticks in START_WORKING, 5 in the working states, 20 while walking and dumping. A pickup extracts 1 slot per 5 ticks. The inactivity cancel fires after 36,000 ticks not working.

---

## 3. Lumberjack

Files:
- `core/colony/buildings/workerbuildings/BuildingLumberjack.java`
- `core/colony/buildings/modules/LumberjackAssignmentModule.java`
- `core/colony/jobs/JobLumberjack.java`
- `core/entity/ai/workers/production/EntityAIWorkLumberjack.java`
- `core/entity/ai/workers/util/Tree.java`
- `core/entity/pathfinding/pathjobs/PathJobFindTree.java`
- `core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.walkToTree`
- `core/entity/ai/workers/AbstractEntityAIInteract.java` (`mineBlock`)
- `api/configuration/ServerConfiguration.java` (`maxTreeSize`)

### 3.1 Building and job
- Schematic `"lumberjack"`, max level 5, `canBeGathered = true`.
- Modules:
  - `FORESTER_WORK` = `LumberjackAssignmentModule(lumberjack, primary=Strength, secondary=Focus, rain=false, size=1, craftSpeed=Focus, recipeImprove=Strength)`.
  - `FORESTER_CRAFT`: custom crafting, only log stripping (`DefaultLumberjackCraftingProvider`); players cannot add recipes.
  - `FORESTER_SETTINGS`.
  - `FORESTER_TOOL`: the lumberjack scepter, which sets the area.
  - `ITEMLIST_SAPLING`.
  - `CRAFT_TASK_VIEW`, `MIN_STOCK`, `STATS_MODULE`.
- Settings (defaults):

  | Setting | Default | Meaning |
  |---|---|---|
  | `REPLANT` | true | |
  | `RESTRICT` | false | auto-toggled: forced false if the area is not fully set; `setRestrictedArea` sets it to "area defined" |
  | `DEFOLIATE` | false | break the leaves too |
  | `USE_SHEARS` | false | |
  | `DYNAMIC_TREES_SIZE` | | mod compatibility |
  | `RECIPE_MODE` | | |

- `ITEMLIST_SAPLING` is an **inverted** list (`ItemListModuleView(..., inverted=true, ...)`): the saplings **listed are the tree types NOT to cut**. The path search receives it as `excludedTrees`.
- `keepX`: 1 axe (level 0..maxEquip) and 1 shears. `getRequiredItemsAndAmount` also keeps **64 of every sapling type not excluded**. The hoe is not kept, so it is dumped and re-fetched from the hut (a quirk).
- Nether: mushroom and fungus plantings are remembered in `netherTrees`. On each colony tick one of them may be bonemealed, with chance `10 + ceil(Strength × 0.9)` %. This can be skipped for Hytale.
- `JobLumberjack` (an `AbstractJobCrafter`): persists the current `Tree`, dropped on load if `!isTree()`. Saturation factor 1.2. Speed modifier `(Focus / 2) × 0.003`.

### 3.2 Tool requirements
`PREPARING` checks the **axe** and then **(USE_SHEARS ? shears : hoe)** through `checkForToolOrWeapon`. `chopWood` re-checks the axe every call and goes to IDLE if it is missing. The allowed tool level range is `[0, getMaxEquipmentLevel()]` (section 0: L1 stone, L2 iron, L3 diamond, L4 netherite, L5 any). A missing tool creates a Tool request to the hut, which the warehouse, a crafter or the player then fills.

### 3.3 Constants (`EntityAIWorkLumberjack`)
| Name | Value | Use |
|---|---|---|
| `SEARCH_RANGE` | 50 | initial tree search range |
| `SEARCH_INCREMENT` | 5 | range added after an empty search |
| `SEARCH_LIMIT` | 150 | stop growing once `range > 150`, so the largest range searched is 155. **`searchIncrement` is never reset** (only when the AI is recreated) |
| `WAIT_BEFORE_INCREMENT` | 20 ticks | delay before the next, wider search |
| `WAIT_BEFORE_SEARCH` | 400 ticks | wait in NO_TREES_FOUND |
| `MAX_BLOCKS_MINED` | 32 | `getActionsDoneUntilDumping()` |
| `WAIT_BEFORE_SAPLING` | 50 ticks | pause once the tree is gone |
| `MAX_WAITING_TIME` | 50 | sapling planting timeout counter, +10 per try |
| `TIMEOUT_DELAY` | 10 ticks | after planting |
| `GATHERING_DELAY` | 3 s (60 ticks) | before gathering drops |
| `RANGE_HORIZONTAL_PICKUP` / `VERTICAL` | 5 / 2 | drop search box around the tree |
| `MIN_WORKING_RANGE` | 2 | 2-D distance to `workFrom` that counts as arrived |
| `XP_PER_TREE` | 1.0 | + `XP_PER_BLOCK = 0.05` for every block mined (logs and leaves) |
| `NUMBER_OF_LEAVES` (Tree) | 3 | minimum leaves to count as a tree |
| `LEAVES_WIDTH` (Tree) | 4 | leaf scan half-width |
| `maxTreeSize` (config) | 400 (range 1..1000) | max logs per tree |
| `PASSING_COST` | 3 | path cost multiplier through leaves |
| `BLOCK_MINING_DELAY` / `LEVEL_MODIFIER` | 500 / 0.85 | mining time, below |

### 3.4 State machine
Base: `AbstractEntityAICrafting` gives `IDLE --hasWorkToDo(=true)--> START_WORKING` (20 ticks) and `START_WORKING → decide` (5 ticks). On top of that come the generic dump events from 0.1. The lumberjack targets are all **20 ticks**.

| State (okToEat) | Behaviour |
|---|---|
| START_WORKING → `decide()` | If stuck, unstuck (mine leaves or huge mushrooms along the path). Walk to the hut (START_WORKING while walking). If `actionsDone >= 32`, stay and let the dump event fire. If at least one sapling type is allowed: no crafting task → LUMBERJACK_START_WORKING. Otherwise, or with a crafting task, the crafting flow (`getNextCraftingState`: IDLE / QUERY_ITEMS / GET_RECIPE) |
| LUMBERJACK_START_WORKING (✓) | walk to the hut → PREPARING |
| PREPARING (✓) | tool checks (3.2): a missing tool → START_WORKING; otherwise → LUMBERJACK_SEARCHING_TREE |
| LUMBERJACK_SEARCHING_TREE (✓) | if `job.tree != null` → CHOP_TREE. If the crafting queue is non-empty → START_WORKING. Otherwise `findTree()` |
| LUMBERJACK_CHOP_TREE (✗) | `chopWood`, below |
| LUMBERJACK_GATHERING (✓) | collect the drops around the tree, then → SEARCHING_TREE |
| LUMBERJACK_NO_TREES_FOUND (✓) | `pathResult = null`; wait 400 ticks (`hasNotDelayed`), then → GATHERING_2 |
| LUMBERJACK_GATHERING_2 (✓) | only with RESTRICT: pick up **saplings, mushrooms and fungi** in the area box inflated by (5, 2, 5). Then `rand(100) <= 10` (11 %) → START_WORKING, else → SEARCHING_TREE |

**findTree:**
- While a path search is computing, stay.
- Start an async `PathJobFindTree` (3.5), using the restricted variant when RESTRICT is on, otherwise `range = 50 + searchIncrement`.
- When done → `setNewTree`. A failed or cancelled search → NO_TREES_FOUND.
- `setNewTree`:
  - no tree found: if not restricted and `50 + inc <= 150`, `inc += 5` and delay 20 (search again); otherwise NO_TREES_FOUND.
  - found: `job.tree = new Tree(world, loc, restrict ? null : colony)`. If `isTree()`, call `findLogs` → CHOP_TREE; otherwise drop it.
  - Either way `pathResult = null`.

**chopTree:**
1. `breakLeaves = DEFOLIATE || tree.isNetherTree`.
2. With RESTRICT on and the tree location outside the area: drop the tree → START_WORKING.
3. While logs remain (or leaves, when breaking leaves) or `checkedInHut`: walk to `workFrom`, which is next to the first stump, or the base if no stumps. `workFrom` = a safe spawn position around the target (`findSpawnPosAround`), recomputed if it is on a sapling. The walk uses `PathJobMoveToWithPassable` with range 50, treating leaves and huge mushrooms as passable. The worker stays in the state until it is within 2 blocks (2-D).
4. **Tree finished** (no logs, and no leaves to break):
   - wait 50 ticks once;
   - if REPLANT, `plantSapling()`; otherwise drop the tree;
   - the `TREE_CUT` statistic, +1 XP, `incrementActionsDoneAndDecSaturation`;
   - `workFrom = null`, delay 60 → **GATHERING**.
5. If standing on a sapling, move the spawn point.
6. Break leaves first, when applicable: `mineBlock(peekNextLeaf, workFrom)`, then `pollNextLeaf` once it is broken. Otherwise the next log: `mineBlock(peekNextLog, workFrom)`, then `pollNextLog` and `decreaseSaturationForContinuousAction`. One block is attempted per call.

**mineBlock** (`AbstractEntityAIInteract`):
- Air or unbreakable → done.
- Otherwise equip the best tool (`holdEfficientTool`) and walk within 2 of the standing position.
- Wait `hasNotDelayed(miningTime)` with `miningTime = (int)(500 × 0.85^(primarySkill/2) × blockHardness / toolDestroySpeed × (1 − researchBreakSpeed))`, where the primary skill is Strength. With no tool it waits `hardness`.
- Then collect the drops into the inventory (fortune, `onBlockDropReception` stats), break the block, damage the tool, add +0.05 XP and `actionsDone++`.

The lumberjack's `increaseBlockDrops` only matters for nether wart blocks, which get a 4 % fungus drop.

**plantSapling(location):**
- If the base is not air, a sapling or replaceable, count it as planted.
- Otherwise find an inventory slot with the tree's sapling (`ItemStack.isSameItem(tree.sapling)`) and place it on **every stump position**. A stump is removed if the block below cannot sustain it or it is already planted; one sapling is consumed per placed stump.
- If still unplanted and `timeWaited >= 25` and not yet `checkedInHut`: walk to the hut and pull the sapling from it (`checkAndTransferFromHut`).
- Finished when there are no stumps left or `timeWaited >= 50`: +1 action, delay 10, then the tree is cleared. Otherwise `timeWaited += 10` and it is retried from the chop state after the 50-tick wait.

**Gathering** (`gatherItems`): walk (`walkToPos` within 2) to the closest item in the list. An item is dropped from the list if the worker is stuck for more than `STUCK_WAIT_TICKS = 20`. The pickup itself is vanilla loot pickup (`setCanPickUpLoot(true)`). In GATHERING, `isItemWorthPickingUp` accepts everything; in GATHERING_2, only saplings, mushrooms and fungi.

**Dumping:** after 32 actions (logs + leaves + trees + plantings), or a full inventory, the generic INVENTORY_FULL dump to the hut runs (0.1). It keeps the axe, the shears and 64 of each allowed sapling. The dump then creates a Pickup request for the courier (0.1 and 2.6). The lumberjack itself **never delivers to the warehouse**; couriers carry the logs away.

### 3.5 Tree search (`PathJobFindTree`, A\*)
- **Unrestricted:** `start` = the worker, or the hut if the worker is more than `range × 4` (2-D) from the hut. The heuristic and end score use Manhattan distance to the hut (`searchTowards = hut`). The box is `start ± 1.3·range` on X/Z, and `maxNodes = min(8000, range²) × config.pathNodeLimitMultiplier`.
- **Restricted:** the range is `dist(worker, areaCentre) + dist(areaCorner1, areaCorner2)`. `searchTowards` = the area centre. Nodes outside the box are not destinations and cost ×2.
- A node is a destination when it has a parent, is next to a valid tree, and stands on walkable ground. "Next to" means the block ahead in the direction of travel, or the two blocks to the sides of it. The first tree found wins: `treeLocation` = that log position.
- Leaves, dynamic trunk shells and huge mushrooms are passable at cost ×3.
- **Valid tree** = `Tree.checkTree(world, pos, excludedTrees, dynSize) && Tree.checkIfInColony(pos, colony, world, restricted)`:
  - The block is in the `minecolonies:tree` tag, or slime or dynamic.
  - `getBottomAndTopLog`: a recursive walk over the 26 neighbours through tree blocks, up to `maxTreeSize` logs. It follows only the first unvisited neighbour, so it traces a chain rather than doing a full flood fill.
  - The block below the bottom log is solid and **not cobblestone**, so pillars are not trees.
  - `hasEnoughLeavesAndIsSupposedToCut(top)`: scan x/z ±1, y −3..+3 around the top log, with +8 more height for mangrove or dynamic trees. Count leaves, huge-mushroom blocks and wart blocks. The **first leaf found** decides the type: it fails if the leaf is `PERSISTENT` (player-placed) or its sapling (from the leaf→sapling compatibility map) is in the excluded list. The check needs **≥ 3** leaves (1 for dynamic leaves).
  - `checkIfInColony`: the chunk belongs to the colony, and the position is not inside any building, unless restricted or dynamic.

### 3.6 The `Tree` object
Built in `new Tree(world, log, colony)`, only if the log is in the tree tag, or slime or dynamic.
- `isTree = true`. Note that `checkTree(topLog)` never sets it to false, so any log becomes a tree at this stage; validity was already enforced by the search.
- `addAndSearch(log)`: a flood fill over the 26 neighbours of logs in the tree tag. It skips positions **inside any colony building**, when a colony is passed (not in restricted mode). It stops at `maxTreeSize`. Same-tree rule: the same block, the `extraTree` tag, the same log prefix (the name minus `_log|_wood|_stem|_hyphae`), or mangrove only with mangrove. It tracks `location` = the lowest log (the base) and `topLog` = the highest.
- Leaves: every leaf, huge-mushroom, wart or shroomlight block in x/z `base ± 4` and y from `base+1` to the world top. **Non-persistent leaves only.**
- The sapling (`calcSapling`) comes from the first leaf found within 10 blocks above the top log, otherwise any leaf. It is taken from the leaf loot table (up to 99 rolls, luck 100, a wooden axe as tool); mangrove leaves give the propagule. The leaf→sapling mapping is recorded in the compatibility manager. A mushroom or fungus sapling means `netherTree`.
- `woodBlocks` is then cleared. `findLogs()` re-runs the flood fill and **sorts the logs by squared distance to the base, ascending**. `pollNextLog()` takes the **last** one, so logs are chopped **farthest first** (canopy and top), down to the stump.
- Stumps = the logs at base Y (2×2 trees give 4). Mangrove replaces them with their mean position.
- Persisted: logs, stumps, the sapling, `isTree`, `netherTree`, leaves.

### 3.7 When the lumberjack creates requests
- Tools: an axe, and a hoe or shears (3.2).
- Pickup requests after dumps (0.1). The hut min-stock module can also create Stack requests.
- It never requests saplings. They come from leaf drops it gathers, or from the hut (`checkAndTransferFromHut`).

---

## 4. Hut blueprints involved
MC structure packs ship `warehouse1..5`, `deliveryman1..5` and `lumberjack1..5` (plus `rack` blocks). No schematic tags are used by these three buildings. The warehouse **must contain racks**: its capacity is its containers. For HyColony: add `hycolony:warehouse`, `hycolony:deliveryman` and `hycolony:lumberjack`, levels 1–5, to `plugin/src/main/resources/hycolony/styles.json` for each style (outlander, kweebec). Each warehouse level must place storage containers, so that `BuilderAI` registers them through `Building.addContainer`.

---

## 5. Mapping onto HyColony core

**Maps directly:**
- `RequestManager` already implements the MC flow: priority bands, then the lowest metric; `attemptResolve` children; `resolve`; `followups` on RESOLVED; `FOLLOWUP_IN_PROGRESS` → COMPLETED; FAILED = cancel; child cancel → parent reset and reassign; `onColonyUpdate(predicate)`. The warehouse followup Delivery pattern fits `Resolver.followups`.
- `BuildingResolver` (200) stays as is. Warehouse stock resolvers are new `Resolver`s at priority 150 using `ContainerAccess.contents(building.containers())`. There is one resolver for our two deliverables: `StackRequest` is concrete, `ToolRequest` is not, and `leftOver` = 0 since we have no `StackList`.
- `TickRateStateMachine` / `AITarget` / `AIEventTarget` (kernel/ai) handle the courier and lumberjack state tables as they are. The delays are those given above.
- `WorkerModule` (job, primary and secondary skill, `maxWorkers = 1`, hiring mode, auto-hire) = `COURIER_WORK` (Agility / Adaptability) and `FORESTER_WORK` (Strength / Focus).
- `Job.actionsDone`, `JobXp`, `Skills`, `Colony.day()`, `GameClock`, and `Building.containers()` (hut first) are already in place.
- `ToolRequest(type, min, max)` matches MC `Tool`. The max-equipment-level formula from section 0 is needed on `Building`.

**New pieces:**
1. **Requestables beyond `Deliverable`.** `Requestable` is `sealed permits Deliverable`. We need `Delivery(start, target, item, count, priority)` and `Pickup(priority, day, qty)` as non-deliverable requestables. This widens `Resolver.handles(...)`, `followups(...)` (`List<Deliverable>` → `List<Requestable>`), `createChild` and `RequestSerializer`. `Request.addDelivery` is only used by deliverables.
2. **Per-building-type resolvers.** `Building.attachContainers` hard-codes `[BuildingResolver]`. We need a module hook such as MC `ICreatesResolversModule` or `createResolvers()` per type, so the warehouse adds stock (150), Delivery (100) and Pickup (100) resolvers.
3. **Warehouse building type:** `WarehouseQueueModule` (a persisted token list), `CourierAssignmentModule` (links citizens who already have the courier job; max = level × 2; auto-link on colony tick; not a `WorkerModule`, because it does not give a job), and `WarehouseModule` (storage upgrades). Hytale containers probably have fixed sizes, so a storage upgrade may have to become "swap container block" or be dropped; that needs a decision. Plus the warehouse dump rack-selection policy.
4. **Courier job and AI:** a per-courier task queue plus an `ongoingDeliveries` set, persisted (MC keeps them in a request-system data store; a field on the job is enough). Also `getCurrentTask` pull and scoring with aging, the prepare, deliver, pickup and dump states, the carry limits, the parallel-delivery formula, the speed modifier, and inactivity → fail the queue. `forceItemStackToItemHandler` (swap out non-requested stacks) needs a `ContainerAccess` extension, since today it only has insert and extract per item, not per slot.
5. **Generic worker base** (`AbstractEntityAIBasic` subset): the INVENTORY_FULL dump with the keep rules (`getRequiredItemsAndAmount` / `buildingRequiresCertainAmountOfItem`), the `pickUpPriority` field with its GUI, `createPickupRequest`, `checkForToolOrWeapon` with `retrieveToolInHut`, and the building's `keepX`. Today the dump logic exists only inside `BuilderAI`, so it should be extracted.
6. **Lumberjack:** `Tree` (flood fill, leaves, stumps, sapling-from-leaf), the async tree search with Hytale pathing (the plugin needs a "find nearest valid tree within range R from the hut" port; a scan plus the existing walker is an acceptable port of the A\* search if documented), the leaf→sapling mapping for Hytale blocks, `mineBlock` timing (needs tool destroy speed and block hardness from the `ItemCatalog` / `WorldBlocks` ports), sapling placement validity, the settings module (replant, restrict + area, defoliate, shears), the excluded-saplings list module, and a **ground-item pickup port** (find item entities in a box, walk and collect), which does not exist yet.
7. **Blueprints:** warehouse, deliveryman and lumberjack L1–5 for both styles.

**Deliberately skippable for SP3a:** the lumberjack crafting module (log stripping), nether fungi bonemeal, Dynamic Trees and slime trees, minimum stock, the multi-warehouse summing (it collapses to a single warehouse), statistics, and the sort button.

**MC bugs to decide on (port faithfully or fix, and note it):**
- the `createPickupRequest` merge never runs, because it tests a token with `instanceof Pickup`;
- the unloaded-target −1000 penalty in the courier score is overwritten;
- the lumberjack `searchIncrement` never resets;
- `Tree.checkTree` in the constructor is a no-op;
- the hoe is not in `keepX`.
