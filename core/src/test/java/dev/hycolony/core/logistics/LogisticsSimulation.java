package dev.hycolony.core.logistics;

import static dev.hycolony.core.testing.FakeBlueprints.HUT_BLOCK;
import static dev.hycolony.core.testing.FakeBlueprints.state;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.HutPlacement;
import dev.hycolony.core.colony.ui.RequestsView;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.courier.DeliverymanJob;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.TestContexts;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * The logistics simulations' colony, played end to end through {@link ColonyManager#tick()}: a warehouse with one
 * rack, a courier hut and a builder hut. Only the fakes stand in for Hytale. No warning may be logged.
 */
abstract class LogisticsSimulation {
    static final BlockPos TOWN_HALL = new BlockPos(0, 64, 0);
    static final BlockPos WAREHOUSE = new BlockPos(20, 64, 0);
    static final BlockPos RACK = new BlockPos(22, 64, 0);
    static final BlockPos COURIER_HUT = new BlockPos(-20, 64, 0);
    static final BlockPos BUILDER_HUT = new BlockPos(40, 64, 0);
    static final int MAX_TICKS = 200_000;

    @TempDir
    Path dir;

    final TestContexts t = new TestContexts();
    final FakeBlueprints blueprints = new FakeBlueprints();
    final UUID alice = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    ColonyManager manager;
    Colony colony;
    /** Every resolver each request was seen with, from its creation on. */
    final Map<RequestToken, Set<String>> resolversSeen = new HashMap<>();
    /** Every request seen, by token, with its last state. */
    final Map<RequestToken, Request> seen = new LinkedHashMap<>();
    /** The player supplies the clipboard's requests every second. */
    boolean autoFulfil;

    /** Held strongly: LogManager only keeps loggers weakly. */
    private final Logger hycolonyLog = Logger.getLogger("dev.hycolony");

    private final List<LogRecord> warnings = new ArrayList<>();
    private final Handler logSpy = new Handler() {
        @Override
        public void publish(LogRecord r) {
            if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(r);
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @BeforeEach
    void foundColony() {
        hycolonyLog.addHandler(logSpy);
        t.bodies.instant = true;
        t.blueprints = blueprints;
        FakeBlueprints.registerBlocks(t.catalog);
        blueprints.put(ConstructionBuildingTypes.BUILDER.id(), 1, FakeBlueprints.hut(false));
        t.players.online.put(alice, TOWN_HALL.offset(3, 0, 3));
        manager = newManager();
        manager.foundation().begin(alice, "Alice", TOWN_HALL, 0);
        colony = manager.foundation().confirm(alice, "Simulation").orElseThrow();
        watch();
        t.blocks.blocks.put(TOWN_HALL, state(HUT_BLOCK));
    }

    @AfterEach
    void noWarnings() {
        hycolonyLog.removeHandler(logSpy);
        assertTrue(
                warnings.isEmpty(),
                () -> "logged: "
                        + warnings.stream()
                                .map(r -> r.getLevel() + " " + r.getMessage() + " " + r.getThrown())
                                .toList());
    }

    ColonyManager newManager() {
        ColonyManager m = new ColonyManager(t.context());
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp1());
        return m;
    }

    void watch() {
        colony.requests().setCreationListener(r -> seen.put(r.token(), r));
    }

    Building placeHut(String typeId, BlockPos pos) {
        assertInstanceOf(HutPlacement.Allowed.class, manager.huts().checkPlacement(alice, pos, typeId));
        t.blocks.blocks.put(pos, state(HUT_BLOCK));
        manager.huts().place(colony, typeId, pos, 0);
        return colony.buildings().at(pos).orElseThrow();
    }

    /** A hut we have no plan for, standing at level 1 as if built. */
    Building builtHut(String typeId, BlockPos pos) {
        Building b = placeHut(typeId, pos);
        b.setLevel(1);
        b.setBuilt(true);
        return b;
    }

    /** The warehouse (its rack stocked with {@code stock}) and the courier hut, the courier hired and attached. */
    Building openWarehouse(Map<ItemKey, Integer> stock) {
        Building warehouse = builtHut(WarehouseBuilding.TYPE_ID, WAREHOUSE);
        warehouse.addContainer(RACK);
        t.containers.containers.put(RACK, new LinkedHashMap<>(stock));
        Building courierHut = builtHut(DeliverymanHut.TYPE_ID, COURIER_HUT);
        runUntil(() -> worker(courierHut).isPresent() && !couriers(warehouse).isEmpty(), 20_000);
        return warehouse;
    }

    /** The builder hut, its builder hired. */
    Building builderHut() {
        Building hut = placeHut(ConstructionBuildingTypes.BUILDER.id(), BUILDER_HUT);
        runUntil(() -> worker(hut).isPresent(), 20_000);
        return hut;
    }

    void order(BlockPos pos, WorkOrderType type) {
        assertEquals(Optional.empty(), manager.workOrders().order(alice, pos, type, ""));
    }

    static List<Integer> couriers(Building warehouse) {
        return warehouse.module(CourierAssignmentModule.class).orElseThrow().couriers();
    }

    Optional<CitizenData> worker(Building b) {
        return b.module(WorkerModule.class)
                .flatMap(w -> w.workers().stream().findFirst())
                .flatMap(colony.citizens()::get);
    }

    CitizenData courier() {
        return worker(colony.buildings().at(COURIER_HUT).orElseThrow()).orElseThrow();
    }

    DeliverymanJob courierJob() {
        return (DeliverymanJob) courier().job().orElseThrow();
    }

    void tick() {
        t.clock.tick++;
        manager.tick();
        for (Request r : colony.requests().all()) {
            seen.put(r.token(), r);
            colony.requests()
                    .resolverOf(r.token())
                    .ifPresent(res -> resolversSeen
                            .computeIfAbsent(r.token(), k -> new HashSet<>())
                            .add(res.resolverId()));
        }
        if (autoFulfil && t.clock.tick % 20 == 0) {
            fulfilAll();
        }
    }

    private void fulfilAll() {
        manager.windows().openRequests(alice, colony.id());
        RequestsView view = (RequestsView) t.ui.shown.get(alice);
        for (RequestsView.RequestRow row : view.rows()) {
            if (row.canSupply()) {
                manager.requestActions().fulfil(alice, colony.id(), row.token());
            }
        }
    }

    void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick();
        }
    }

    void runUntil(BooleanSupplier done, int max) {
        for (int i = 0; i < max && !done.getAsBoolean(); i++) {
            tick();
        }
        assertFalse(colony.isSuspended(), "the colony threw");
        assertTrue(
                done.getAsBoolean(),
                () -> "not reached in " + max + " ticks; requests "
                        + colony.requests().all()
                        + ", containers " + t.containers.containers + ", courier "
                        + colony.citizens().all().stream()
                                .filter(c -> c.job().orElse(null) instanceof DeliverymanJob)
                                .map(c -> c.id() + " " + c.inventory().contents())
                                .toList()
                        + ", tick " + t.clock.tick);
    }

    int stored(BlockPos pos, ItemKey item) {
        return t.containers.containers.getOrDefault(pos, Map.of()).getOrDefault(item, 0);
    }

    /**
     * Every {@code item} in the game: containers, citizens' inventories, the player, placed blocks and the ground. A
     * run that neither duplicates nor loses keeps it constant.
     */
    int total(ItemKey item) {
        int n = t.playerInventory.count(alice, item);
        for (Map<ItemKey, Integer> c : t.containers.containers.values()) {
            n += c.getOrDefault(item, 0);
        }
        for (CitizenData c : colony.citizens().all()) {
            n += c.inventory().count(item);
        }
        n += (int) t.blocks.blocks.values().stream()
                .filter(s -> item.equals(t.catalog.itemForBlock.get(s.key())))
                .count();
        for (List<ItemAmount> ground : t.blocks.dropped.values()) {
            n += ground.stream()
                    .filter(a -> a.item().equals(item))
                    .mapToInt(ItemAmount::count)
                    .sum();
        }
        return n;
    }
}
