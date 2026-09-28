package dev.hycolony.core.farming.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC FarmField: owner, seed, radii and stage, saved with the colony. */
class FarmFieldTest {
    private static final BlockPos POS = new BlockPos(4, 64, -2);

    @Test
    void newFieldIsFreeSeedlessAndEmpty() {
        FarmField f = new FarmField(POS);
        assertTrue(f.owner().isEmpty());
        assertTrue(f.seed().isEmpty());
        assertEquals(FieldRadii.defaults(), f.radii());
        assertEquals(FieldStage.EMPTY, f.stage());
    }

    @Test
    void stageWrapsAfterPlanted() {
        FarmField f = new FarmField(POS);
        f.nextStage();
        f.nextStage();
        assertEquals(FieldStage.PLANTED, f.stage());
        f.nextStage();
        assertEquals(FieldStage.EMPTY, f.stage());
    }

    @Test
    void fieldSurvivesSaveAndLoad() {
        FarmField f = new FarmField(POS);
        f.setOwner(Optional.of(new BlockPos(0, 64, 0)));
        f.setSeed(Optional.of(new ItemKey("Plant_Seeds_Wheat")));
        f.setRadii(new FieldRadii(1, 2, 3, 4));
        f.nextStage();

        FarmField back = FarmField.read(f.write()).orElseThrow();

        assertEquals(f.pos(), back.pos());
        assertEquals(f.owner(), back.owner());
        assertEquals(f.seed(), back.seed());
        assertEquals(f.radii(), back.radii());
        assertEquals(FieldStage.HOED, back.stage());
    }

    @Test
    void unknownStageReadsAsEmpty() {
        JsonObject o = new FarmField(POS).write();
        o.addProperty("stage", "SOMETHING_NEW");
        assertEquals(FieldStage.EMPTY, FarmField.read(o).orElseThrow().stage());
    }

    @Test
    void fieldWithoutPositionIsNotRead() {
        assertTrue(FarmField.read(new JsonObject()).isEmpty());
    }

    @Test
    void unreadableRadiiAndPositionFallBack() {
        JsonObject o = new FarmField(POS).write();
        JsonArray radii = new JsonArray();
        radii.add("five");
        radii.add(5);
        radii.add(5);
        radii.add(5);
        o.add("radii", radii);
        JsonArray owner = new JsonArray();
        owner.add("x");
        owner.add(64);
        owner.add(0);
        o.add("owner", owner);

        FarmField f = FarmField.read(o).orElseThrow();

        assertEquals(FieldRadii.defaults(), f.radii());
        assertTrue(f.owner().isEmpty());
    }

    @Test
    void radiiOverTheBudgetAreRepaired() {
        JsonObject o = new FarmField(POS).write();
        JsonArray radii = new JsonArray();
        for (int r : new int[] {20, 20, -1, 5}) {
            radii.add(r);
        }
        o.add("radii", radii);

        assertEquals(FieldRadii.defaults(), FarmField.read(o).orElseThrow().radii());
    }

    @Test
    void radiiOverTheBudgetWithoutANegativeSideAreRepaired() {
        JsonObject o = new FarmField(POS).write();
        JsonArray radii = new JsonArray();
        for (int r : new int[] {20, 20, 0, 5}) {
            radii.add(r);
        }
        o.add("radii", radii);

        assertEquals(FieldRadii.defaults(), FarmField.read(o).orElseThrow().radii());
    }
}
