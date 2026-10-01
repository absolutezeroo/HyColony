package dev.hycolony.core.construction.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.testing.FakeBlueprints;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What a blueprint source without packs.json gives: Structurize's defaults. */
class BlueprintSourceDefaultsTest {
    private final BlueprintSource source = new BlueprintSource() {
        @Override
        public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
            return level == 1 ? Optional.of(FakeBlueprints.hut(false)) : Optional.empty();
        }

        @Override
        public List<String> styles() {
            return List.of("medieval");
        }
    };

    @Test
    void aStyleWithoutMetadataIsNamedAfterItsIdAndOwnedByHyColony() {
        PackInfo pack = source.pack("medieval");
        assertEquals(new PackInfo("medieval", "", List.of(), "", PackInfo.DEFAULT_OWNER), pack);
    }

    @Test
    void aHutWithoutAFolderGoesToFundamentals() {
        assertEquals("fundamentals", source.category("hycolony:builder"));
    }

    @Test
    void aPlanExistsWhenItLoads() {
        assertTrue(source.hasPlan("medieval", "hycolony:builder", 1));
        assertFalse(source.hasPlan("medieval", "hycolony:builder", 2));
    }
}
