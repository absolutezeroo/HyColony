package dev.hycolony.core.citizen.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/** A citizen's saved equipment (schema 10) and which saves its read repairs, to be written again (CLAUDE.md § 5). */
class EquipmentJsonTest {
    private static final String PIECE = "{\"item\":\"Armor_Iron_Head\",\"count\":1}";

    private static JsonObject saved(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void aSoundSaveNeedsNoRepair() {
        assertFalse(EquipmentJson.needsRepair(saved("{\"armor\":[" + PIECE + "],\"heldMain\":3,\"heldOff\":-1}")));
        assertFalse(EquipmentJson.needsRepair(saved("{}")), "missing keys take their defaults");
    }

    @Test
    void aHandOutsideTheInventoryIsRepaired() {
        JsonObject o = saved("{\"heldMain\":27}");

        assertTrue(EquipmentJson.needsRepair(o));
        assertEquals(CitizenEquipment.NO_SLOT, EquipmentJson.read(o).held(Hand.MAIN));
    }

    @Test
    void aHandThatIsNoNumberIsRepaired() {
        JsonObject o = saved("{\"heldOff\":\"left\"}");

        assertTrue(EquipmentJson.needsRepair(o));
        assertEquals(CitizenEquipment.NO_SLOT, EquipmentJson.read(o).held(Hand.OFF));
    }

    @Test
    void moreThanFourArmourPiecesAreRepaired() {
        JsonObject o = saved("{\"armor\":[" + String.join(",", Collections.nCopies(5, PIECE)) + "]}");

        assertTrue(EquipmentJson.needsRepair(o));
        assertEquals(4, EquipmentJson.read(o).armor().size());
    }
}
