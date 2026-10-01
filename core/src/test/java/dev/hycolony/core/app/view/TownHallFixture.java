package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A colony for the town hall window tests: alice owns it, carol is a friend, a level 5 builder hut at (10, 64, 0)
 * employs Bob, and every blueprint is one stone block in styles "medieval" and "desert".
 */
final class TownHallFixture {
    static final BlockKey STONE = new BlockKey("hytale:stone");

    final TestContexts t = new TestContexts();
    final ColonyManager manager;
    final UUID alice = UUID.randomUUID();
    final UUID carol = UUID.randomUUID();
    final BlockPos hall = new BlockPos(0, 64, 0);
    final Colony colony;
    final Building builder;
    final CitizenData bob;

    TownHallFixture() {
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                BlockPos o = new BlockPos(1, 0, 0);
                return Optional.of(
                        new Blueprint("bp", List.of(new BlueprintEntry(o, new BlockState(STONE, 0), false)), o, o));
            }

            @Override
            public List<String> styles() {
                return List.of("medieval", "desert");
            }
        };
        manager = t.manager();
        manager.foundation().begin(alice, "Alice", hall, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        assertTrue(manager.administration().setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        bob = citizen(1, "Bob");
        builder = hut(ConstructionBuildingTypes.BUILDER, new BlockPos(10, 64, 0), 5);
        assertTrue(builder.module(WorkerModule.class).orElseThrow().hire(colony, builder, bob));
        t.notifier.sent.clear();
        t.ui.shown.clear();
    }

    CitizenData citizen(int id, String name) {
        CitizenData c = new CitizenData(id);
        c.setName(name);
        colony.citizens().restore(c);
        return c;
    }

    Building hut(BuildingType type, BlockPos pos, int level) {
        manager.huts().place(colony, type.id(), pos, 0, UUID.randomUUID());
        Building b = colony.buildings().at(pos).orElseThrow();
        b.setLevel(level);
        return b;
    }

    Building townHall() {
        return colony.buildings().townHall().orElseThrow();
    }

    /** The town hall window {@code player} sees once it opens it. */
    TownHallView townHallView(UUID player) {
        manager.windows().openTownHall(player, hall);
        return (TownHallView) t.ui.shown.get(player);
    }
}
