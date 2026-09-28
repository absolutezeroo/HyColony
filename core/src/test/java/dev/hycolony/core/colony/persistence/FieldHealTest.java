package dev.hycolony.core.colony.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC colony load: a field whose owner is no longer a farmer hut of the colony is freed. */
class FieldHealTest {
    private static final BlockPos HUT = new BlockPos(0, 64, 0);
    private static final BlockPos KEPT = new BlockPos(10, 64, 0);
    private static final BlockPos ORPHAN = new BlockPos(20, 64, 0);

    @Test
    void ownerOfAMissingHutIsFreedOnLoad() {
        TestContexts t = new TestContexts();
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", HUT, Permissions.createDefault(UUID.randomUUID(), "A")));
        Building hut = Building.create(FarmerHut.TYPE, HUT, 0);
        hut.setLevel(2);
        c.buildings().add(hut);
        c.registries().fields().add(KEPT);
        c.registries().fields().add(ORPHAN);
        c.registries().fields().get(KEPT).orElseThrow().setOwner(Optional.of(HUT));
        c.registries().fields().get(ORPHAN).orElseThrow().setOwner(Optional.of(new BlockPos(99, 64, 99)));

        Colony back = ColonySerializer.read(ColonySerializer.write(c), t.context(), new TerritoryIndex());

        assertTrue(back.registries().fields().get(KEPT).orElseThrow().isTaken());
        assertFalse(back.registries().fields().get(ORPHAN).orElseThrow().isTaken());
        assertTrue(back.isDirty(), "the healed state is written at the next save");
    }
}
