package dev.hycolony.core.kernel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonFragmentsTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void fragmentKeysAreAddedInsideTheCoreSections() {
        JsonFragments ids = new JsonFragments(1);
        ids.add("core", json("{\"items\": {\"hut.builder\": \"A\"}, \"fireworks\": [\"F1\"]}"));

        List<JsonFragments.Conflict> conflicts =
                ids.add("Pack", json("{\"items\": {\"hut.farm\": \"B\"}, \"blocks\": {\"x\": \"C\"}}"));

        assertTrue(conflicts.isEmpty());
        assertEquals(
                json("{\"items\": {\"hut.builder\": \"A\", \"hut.farm\": \"B\"}, \"fireworks\": [\"F1\"],"
                        + " \"blocks\": {\"x\": \"C\"}}"),
                ids.merged());
    }

    @Test
    void aKeyDefinedTwiceKeepsTheFirstAndNamesBothSources() {
        JsonFragments ids = new JsonFragments(1);
        ids.add("core", json("{\"items\": {\"hut.builder\": \"A\"}}"));

        List<JsonFragments.Conflict> conflicts = ids.add("Pack", json("{\"items\": {\"hut.builder\": \"B\"}}"));

        assertEquals(List.of(new JsonFragments.Conflict("items/hut.builder", "core", "Pack")), conflicts);
        assertEquals(json("{\"items\": {\"hut.builder\": \"A\"}}"), ids.merged());
    }

    @Test
    void aConflictNamesThePackThatAddedTheWholeSection() {
        JsonFragments styles = new JsonFragments(2);
        styles.add("core", json("{}"));
        styles.add("Styles_Outlander", json("{\"outlander\": {\"hycolony:builder\": {\"1\": {\"prefab\": \"a\"}}}}"));

        List<JsonFragments.Conflict> conflicts =
                styles.add("Other", json("{\"outlander\": {\"hycolony:builder\": {\"1\": {\"prefab\": \"b\"}}}}"));

        assertEquals(
                List.of(new JsonFragments.Conflict("outlander/hycolony:builder/1", "Styles_Outlander", "Other")),
                conflicts);
    }

    @Test
    void aPackMayAddLevelsToAStyleAnotherSourceDefines() {
        JsonFragments styles = new JsonFragments(2);
        styles.add("core", json("{\"outlander\": {\"hycolony:builder\": {\"1\": {\"prefab\": \"a\"}}}}"));

        List<JsonFragments.Conflict> conflicts =
                styles.add("Pack", json("{\"outlander\": {\"hycolony:builder\": {\"2\": {\"prefab\": \"b\"}}}}"));

        assertTrue(conflicts.isEmpty());
        assertEquals(
                json("{\"outlander\": {\"hycolony:builder\": {\"1\": {\"prefab\": \"a\"}, "
                        + "\"2\": {\"prefab\": \"b\"}}}}"),
                styles.merged());
    }

    @Test
    void mergedListsKeepEachValueOnce() {
        JsonFragments ids = new JsonFragments(1);
        ids.add("core", json("{\"fireworks\": [\"F1\", \"F2\"]}"));

        ids.add("Pack", json("{\"fireworks\": [\"F2\", \"F3\"]}"));

        assertEquals(json("{\"fireworks\": [\"F1\", \"F2\", \"F3\"]}"), ids.merged());
    }

    @Test
    void aSectionOfAnotherKindIsAConflict() {
        JsonFragments ids = new JsonFragments(1);
        ids.add("core", json("{\"items\": {\"a\": \"A\"}}"));

        List<JsonFragments.Conflict> conflicts = ids.add("Pack", json("{\"items\": [\"a\"]}"));

        assertEquals(List.of(new JsonFragments.Conflict("items", "core", "Pack")), conflicts);
    }
}
