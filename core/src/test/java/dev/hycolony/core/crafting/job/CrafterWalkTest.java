package dev.hycolony.core.crafting.job;

import static dev.hycolony.core.crafting.job.CrafterRig.ESSENCE;
import static dev.hycolony.core.crafting.job.CrafterRig.SEEDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import org.junit.jupiter.api.Test;

/** MC walkToTaggedWorkPos: the crafter works by the recipe's bench, or by its hut (walkToBuilding) without one. */
class CrafterWalkTest {
    private static final BlockKey FLOOR = new BlockKey("Soil_Dirt");

    /** Floor around the hut (10, 64, 0) and its bench (11, 64, 0), both solid blocks; the crafter 20 blocks away. */
    private static void standingAway(CrafterRig rig) {
        for (int x = 7; x <= 14; x++) {
            for (int z = -3; z <= 3; z++) {
                rig.t.blocks.blocks.put(new BlockPos(x, 63, z), new BlockState(FLOOR, 0));
            }
        }
        rig.t.blocks.blocks.put(rig.h.hut.position(), new BlockState(FLOOR, 0));
        rig.t.blocks.blocks.put(rig.bench, new BlockState(FLOOR, 0));
        rig.t.bodies.bodies.get(rig.body).position = new Vec3(30, 64, 0);
        rig.t.bodies.moves.clear();
    }

    @Test
    void benchRecipeIsMadeBesideTheBench() {
        CrafterRig rig = new CrafterRig();
        rig.stock(ESSENCE, 2);
        rig.task(rig.ask(1));
        assertEquals(CraftingStep.CRAFT, rig.toCraft());
        standingAway(rig);

        rig.work.craft();

        assertEquals(Vec3.center(rig.bench.offset(0, 0, -1)), rig.t.bodies.moves.getFirst());
    }

    @Test
    void handRecipeIsMadeBesideTheHut() {
        CrafterRig rig = new CrafterRig(RecipeFixtures.fieldcraft("Seeds", SEEDS.id()));
        rig.stock(ESSENCE, 2);
        rig.task(rig.ask(1));
        assertEquals(CraftingStep.CRAFT, rig.toCraft());
        standingAway(rig);

        rig.work.craft();

        assertEquals(Vec3.center(rig.h.hut.position().offset(0, 0, -1)), rig.t.bodies.moves.getFirst());
    }
}
