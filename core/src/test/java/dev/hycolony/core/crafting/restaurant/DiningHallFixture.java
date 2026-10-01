package dev.hycolony.core.crafting.restaurant;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.crafting.furnace.FuelListModule;
import dev.hycolony.core.crafting.furnace.FurnaceUserModule;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** A colony with a level-1 dining hall at {@link #HALL} whose footprint spans 5 blocks around it. */
class DiningHallFixture {
    static final BlockPos HALL = new BlockPos(0, 64, 0);
    static final BlockPos STATION = new BlockPos(2, 64, 0);
    static final ItemKey MEAT = new ItemKey("meat");
    static final ItemKey CHARCOAL = new ItemKey("charcoal");
    static final int MAX_TICKS = 3000;

    final TestContexts t = contexts();
    final ItemKey steak = t.catalog.food("steak", 8, 0);
    final ItemKey pie = t.catalog.food("pie", 12, 3);
    final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", HALL, Permissions.createDefault(UUID.randomUUID(), "A")));
    final Building hall = hall();
    final CitizenData cook = new CitizenData(1);
    BodyId cookBody;
    JobAI ai;

    private static TestContexts contexts() {
        TestContexts t = new TestContexts();
        t.bodies.instant = true;
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String type, int level, int rotation) {
                return Optional.of(new Blueprint("k", List.of(), new BlockPos(-5, 0, -5), new BlockPos(5, 4, 5)));
            }

            @Override
            public List<String> styles() {
                return List.of("s");
            }
        };
        t.cooking.fuels.add(CHARCOAL);
        t.cooking.defaultFuels.add(CHARCOAL);
        return t;
    }

    DiningHallFixture() {
        t.catalog.cooked.put(MEAT, steak);
        t.catalog.food("meat", 3, 0);
        t.cooking.raws.put(steak, MEAT);
    }

    private Building hall() {
        Building b = Building.create(DiningHallHut.TYPE, HALL, 0);
        b.setLevel(1);
        b.setBuilt(true);
        b.setStyle("s");
        colony.buildings().add(b);
        return b;
    }

    RestaurantMenuModule menu() {
        return hall.module(RestaurantMenuModule.class).orElseThrow();
    }

    DiningRoomModule room() {
        return hall.module(DiningRoomModule.class).orElseThrow();
    }

    FuelListModule fuel() {
        return hall.module(FuelListModule.class).orElseThrow();
    }

    FurnaceUserModule furnaces() {
        return hall.module(FurnaceUserModule.class).orElseThrow();
    }

    /** A campfire at {@link #STATION}, registered with the hall. */
    void station() {
        t.cooking.station(STATION);
        furnaces().addStation(STATION);
    }

    /** Puts {@code count} of {@code item} in the hall's block (its first container). */
    void stock(ItemKey item, int count) {
        Map<ItemKey, Integer> content = t.containers.containers.computeIfAbsent(HALL, p -> new HashMap<>());
        content.merge(item, count, Integer::sum);
    }

    int stocked(ItemKey item) {
        return t.containers.count(hall.containers(), item);
    }

    /** Hires {@link #cook} as the hall's waiter, its body at the hall, and starts its AI. */
    void hire() {
        cook.setSaturation(CitizenData.MAX_SATURATION);
        colony.citizens().restore(cook);
        assertTrue(hall.module(WorkerModule.class).orElseThrow().hire(colony, hall, cook));
        cookBody = t.bodies.existing(1, cook.id(), Vec3.center(HALL));
        colony.citizens().onBodyLoaded(cookBody, cook.id());
        ai = cook.job().orElseThrow().createAI(colony, cookBody);
    }

    void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            t.clock.tick++;
            ai.tick();
        }
    }

    /** A hungry citizen (id 2) standing in the hall, not working. */
    CitizenData guest(double saturation) {
        CitizenData guest = new CitizenData(2);
        guest.setSaturation(saturation);
        colony.citizens().restore(guest);
        BodyId body = t.bodies.existing(1, 2, Vec3.center(HALL.offset(2, 0, 2)));
        colony.citizens().onBodyLoaded(body, 2);
        return guest;
    }

    void runUntil(BooleanSupplier done) {
        for (int i = 0; i < MAX_TICKS && !done.getAsBoolean(); i++) {
            run(1);
        }
        assertTrue(done.getAsBoolean(), "never happened; waiter in " + ai.stateName());
    }
}
