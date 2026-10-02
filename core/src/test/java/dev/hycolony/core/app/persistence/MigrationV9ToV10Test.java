package dev.hycolony.core.app.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.testing.TestContexts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schema 10 (citizen inventory as MC): each citizen's armour and held slots (MC InventoryCitizen, CitizenData). */
class MigrationV9ToV10Test {
    private static final String FIXTURE = "colony-v9-equipment.json";
    private static final ItemKey HELMET = new ItemKey("Armor_Iron_Head");

    @TempDir
    Path dir;

    private static String fixture() throws IOException {
        try (InputStream in = MigrationV9ToV10Test.class.getResourceAsStream("/fixtures/" + FIXTURE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ColonyManager load() {
        ColonyManager m = new TestContexts().manager();
        m.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp4());
        m.persistence().loadAll();
        return m;
    }

    private CitizenData aela(ColonyManager m) {
        return m.byId(1).orElseThrow().citizens().get(1).orElseThrow();
    }

    @Test
    void v9ToV10GivesEachCitizenEmptyArmourAndHands() throws IOException {
        JsonObject migrated =
                MigrationChain.sp4().migrate(JsonParser.parseString(fixture()).getAsJsonObject());

        assertEquals(
                ColonySerializer.SCHEMA_VERSION,
                migrated.get(MigrationChain.VERSION_KEY).getAsInt());
        JsonObject citizen = migrated.getAsJsonArray("citizens").get(0).getAsJsonObject();
        assertEquals(0, citizen.getAsJsonArray("armor").size());
        assertEquals(CitizenEquipment.NO_SLOT, citizen.get("heldMain").getAsInt());
        assertEquals(CitizenEquipment.NO_SLOT, citizen.get("heldOff").getAsInt());
    }

    @Test
    void equipmentSurvivesASave() throws IOException {
        Files.writeString(dir.resolve("colony-1.json"), fixture());
        ColonyManager m = load();
        CitizenData d = aela(m);
        d.equipment().armor().set(0, Optional.of(new ItemAmount(HELMET, 1, 7)));
        d.equipment().hold(Hand.MAIN, 0);
        m.byId(1).orElseThrow().markDirty();
        m.persistence().saveAll();

        CitizenData again = aela(load());

        assertEquals(
                Optional.of(new ItemAmount(HELMET, 1, 7)),
                again.equipment().armor().slot(0));
        assertEquals(0, again.equipment().held(Hand.MAIN));
        assertEquals(CitizenEquipment.NO_SLOT, again.equipment().held(Hand.OFF));
    }

    @Test
    void aBrokenSaveHeals() throws IOException {
        JsonObject doc = JsonParser.parseString(fixture()).getAsJsonObject();
        doc.addProperty(MigrationChain.VERSION_KEY, ColonySerializer.SCHEMA_VERSION);
        JsonObject citizen = doc.getAsJsonArray("citizens").get(0).getAsJsonObject();
        citizen.addProperty("heldMain", 99);
        citizen.addProperty("heldOff", -7);
        String piece = "{\"item\":\"Armor_Iron_Head\",\"count\":1}";
        citizen.add(
                "armor", JsonParser.parseString("[" + String.join(",", java.util.Collections.nCopies(6, piece)) + "]"));
        Files.writeString(dir.resolve("colony-1.json"), doc.toString());

        CitizenData d = aela(load());

        assertEquals(CitizenEquipment.NO_SLOT, d.equipment().held(Hand.MAIN), "out of the 27 slots");
        assertEquals(CitizenEquipment.NO_SLOT, d.equipment().held(Hand.OFF));
        assertEquals(4, d.equipment().armor().size());
        assertTrue(d.equipment().armor().slot(3).isPresent(), "the first 4 pieces are kept");
    }
}
