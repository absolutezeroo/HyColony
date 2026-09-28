package dev.hycolony.core.farming.hut;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.TestContexts;
import java.util.Optional;
import java.util.UUID;

/** A colony with one farmer hut, for the farmer's tests; fields are added with or without a seed. */
final class FarmerColony {
    static final BlockPos HUT = new BlockPos(0, 64, 0);
    static final ItemKey WHEAT_SEEDS = new ItemKey("Plant_Seeds_Wheat");

    final TestContexts t = new TestContexts();
    final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    final Building hut;

    FarmerColony(int level) {
        hut = Building.create(FarmerHut.TYPE, HUT, 0);
        hut.setLevel(level);
        hut.setBuilt(true);
        colony.buildings().add(hut);
    }

    FarmerFieldsModule fields() {
        return hut.module(FarmerFieldsModule.class).orElseThrow();
    }

    /** A new field at {@code x, 0}, with the wheat seed when {@code seeded}. */
    FarmField field(int x, boolean seeded) {
        BlockPos pos = new BlockPos(x, 64, 0);
        colony.registries().fields().add(pos);
        FarmField f = colony.registries().fields().get(pos).orElseThrow();
        if (seeded) {
            f.setSeed(Optional.of(WHEAT_SEEDS));
        }
        return f;
    }

    void setDay(int day) {
        colony.setDay(day);
    }
}
