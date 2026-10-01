package dev.hycolony.core.citizen.happiness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.citizen.CitizenData;
import org.junit.jupiter.api.Test;

/** MC CitizenHappinessHandler.write/read and HappinessRegistry.loadFrom. */
class HappinessJsonTest {
    @Test
    void anExpiringModifierComesBackAndAnUnknownIdIsSkipped() {
        CitizenData hurt = new CitizenData(1);
        HappinessEvents.hurt(hurt);
        JsonArray saved = HappinessJson.write(hurt.happiness());
        JsonObject unknown = new JsonObject();
        unknown.addProperty("id", "bogus");
        unknown.addProperty("period", 3);
        saved.add(unknown);

        CitizenData loaded = new CitizenData(1);
        HappinessJson.read(saved, loaded.happiness());

        assertTrue(loaded.happiness().get(HappinessIds.DAMAGE).isPresent());
        assertTrue(loaded.happiness().get("bogus").isEmpty());
        assertEquals(
                hurt.happiness().modifiers().size(),
                loaded.happiness().modifiers().size());
    }
}
