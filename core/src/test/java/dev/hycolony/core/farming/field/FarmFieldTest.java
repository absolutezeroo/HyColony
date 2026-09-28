package dev.hycolony.core.farming.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
