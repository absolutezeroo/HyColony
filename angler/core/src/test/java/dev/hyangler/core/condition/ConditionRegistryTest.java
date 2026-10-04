package dev.hyangler.core.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.hyangler.api.condition.Condition;
import dev.hyangler.core.testing.Contexts;
import org.junit.jupiter.api.Test;

class ConditionRegistryTest {
    private final ConditionRegistry registry = new ConditionRegistry();

    private Condition parse(String json) {
        return registry.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void allAnyAndNotCombine() {
        Condition c = parse("{\"Type\":\"All\",\"Conditions\":["
                + "{\"Type\":\"Any\",\"Conditions\":[{\"Type\":\"Zone\",\"Ids\":[\"Zone1\"]},"
                + "{\"Type\":\"Zone\",\"Ids\":[\"Zone3\"]}]},"
                + "{\"Type\":\"Not\",\"Condition\":{\"Type\":\"Time\",\"From\":0,\"To\":6}}]}");
        assertTrue(c.test(Contexts.hour(12)));
        assertFalse(c.test(Contexts.hour(3)));
    }

    @Test
    void theShortFormOfAFileNamesItsListUnderAll() {
        Condition c = registry.parseAll(JsonParser.parseString(
                "{\"All\":[{\"Type\":\"Zone\",\"Ids\":[\"Zone1\"]},{\"Type\":\"Time\",\"From\":6,\"To\":20}]}"));
        assertTrue(c.test(Contexts.hour(12)));
        assertFalse(c.test(Contexts.hour(21)));
    }

    @Test
    void noConditionsMeansAlways() {
        assertTrue(registry.parseAll(null).test(Contexts.base()));
    }

    @Test
    void anUnknownTypeIsRefusedWithItsName() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("{\"Type\":\"Season\"}"));
        assertTrue(e.getMessage().contains("Season"));
    }

    @Test
    void anotherModsTypeIsReadOnceRegistered() {
        registry.register("Season", spec -> {
            String season = spec.string("Is").orElseThrow(() -> new IllegalArgumentException("Is"));
            return ctx -> season.equals("Summer");
        });
        assertTrue(parse("{\"Type\":\"Season\",\"Is\":\"Summer\"}").test(Contexts.base()));
        assertTrue(registry.types().contains("Season"));
    }

    @Test
    void aTypeIsRegisteredOnce() {
        assertThrows(IllegalArgumentException.class, () -> registry.register("Time", spec -> ctx -> true));
    }

    @Test
    void aTimeWithoutItsBoundsIsRefused() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> parse("{\"Type\":\"Time\",\"From\":5}"));
        assertEquals("Time needs From and To, from 0 to 24", e.getMessage());
    }
}
