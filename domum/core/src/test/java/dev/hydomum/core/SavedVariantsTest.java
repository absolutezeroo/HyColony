package dev.hydomum.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hydomum.api.ShapeCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

class SavedVariantsTest {
    @Test
    void savedVariantsOfAMissingShapeAreKeptAndSkipped() {
        SavedVariants saved = SavedVariants.parse(
                "{\"schemaVersion\":1,\"variants\":[\"Shingle|Rock_Stone_Brick|Wood_Hardwood_Planks\",\"Gone|X\"]}");
        ShapeCatalog catalog = ShapeCatalog.parse("""
                {"schemaVersion": 1, "shapes": [{"id": "Shingle", "template": "HyDomum_Shingle", "group": "c",
                 "slots": ["a", "b"], "optionalSecond": false, "cutterQuantity": 4}]}""");
        assertEquals(1, saved.keys(catalog).size());
        assertTrue(saved.toJson().contains("Gone|X"));
    }

    @Test
    void newerFileIsReadOnly() {
        assertTrue(SavedVariants.parse("{\"schemaVersion\":2,\"variants\":[]}").readOnly());
    }

    @Test
    void addingAKnownIdChangesNothing() {
        SavedVariants saved = SavedVariants.parse("{\"schemaVersion\":1,\"variants\":[\"A|x\"]}");
        assertEquals(saved, saved.with("A|x"));
    }

    @Test
    void prototypeBareArrayAndForeignEntriesSurviveARewrite() {
        SavedVariants saved = SavedVariants.parse("[\"A|x\", {\"old\": 1}]").with("B|y");
        assertEquals(List.of("A|x", "B|y"), saved.ids());
        assertEquals("{\"schemaVersion\":1,\"variants\":[\"A|x\",\"B|y\",{\"old\":1}]}", saved.toJson());
    }

    @Test
    void duplicateIdsAreReadOnce() {
        assertEquals(List.of("A|x"), SavedVariants.parse("[\"A|x\", \"A|x\"]").ids());
    }

    @Test
    void readOnlyListStaysReadOnly() {
        assertTrue(SavedVariants.parse("{\"schemaVersion\":2,\"variants\":[]}")
                .with("A|x")
                .readOnly());
    }

    @Test
    void unreadableFileIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SavedVariants.parse("{broken"));
        assertThrows(IllegalArgumentException.class, () -> SavedVariants.parse("{\"schemaVersion\":1}"));
    }
}
