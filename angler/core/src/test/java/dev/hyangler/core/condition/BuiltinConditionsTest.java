package dev.hyangler.core.condition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.hyangler.api.WaterKind;
import dev.hyangler.api.condition.Condition;
import dev.hyangler.core.testing.Contexts;
import org.junit.jupiter.api.Test;

class BuiltinConditionsTest {
    private final ConditionRegistry registry = new ConditionRegistry();

    private Condition condition(String json) {
        return registry.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void environmentMatchesOneOfItsIds() {
        Condition c = condition("{\"Type\":\"Environment\",\"Ids\":[\"Env_Zone1_Forests\",\"Env_Zone1_Plains\"]}");
        assertTrue(c.test(Contexts.base()));
        assertFalse(c.test(Contexts.at("Env_Zone3_Tundra", "Zone3", WaterKind.FRESH)));
    }

    @Test
    void zoneMatchesTheEnvironmentsZone() {
        Condition c = condition("{\"Type\":\"Zone\",\"Ids\":[\"Zone3\"]}");
        assertTrue(c.test(Contexts.at("Env_Zone3_Tundra", "Zone3", WaterKind.FRESH)));
        assertFalse(c.test(Contexts.base()));
    }

    @Test
    void saltWaterIsNotFreshWater() {
        Condition salt = condition("{\"Type\":\"Water\",\"Kind\":\"Salt\"}");
        assertTrue(salt.test(Contexts.at("Env_Zone1_Shores", "Zone1", WaterKind.SALT)));
        assertFalse(salt.test(Contexts.base()));
    }

    @Test
    void troutIsNotCaughtAtMidnight() {
        Condition c = condition("{\"Type\":\"Time\",\"From\":5,\"To\":22}");
        assertTrue(c.test(Contexts.hour(12)));
        assertFalse(c.test(Contexts.hour(0.5)));
    }

    @Test
    void depthNeedsTheColumnWithinItsBounds() {
        Condition c = condition("{\"Type\":\"Depth\",\"Min\":2}");
        assertTrue(c.test(Contexts.water(2, true, true)));
        assertFalse(c.test(Contexts.water(1, true, true)));
        Condition shallow = condition("{\"Type\":\"Depth\",\"Max\":1}");
        assertTrue(shallow.test(Contexts.water(1, true, true)));
        assertFalse(shallow.test(Contexts.water(4, true, true)));
    }

    @Test
    void weatherMatchesItsIdsOrTheRain() {
        Condition storm = condition("{\"Type\":\"Weather\",\"Ids\":[\"Zone2_Storm\"]}");
        assertTrue(storm.test(Contexts.weather("Zone2_Storm", true)));
        assertFalse(storm.test(Contexts.weather("Zone2_Sunny", false)));
        Condition rain = condition("{\"Type\":\"Weather\",\"Rain\":true}");
        assertTrue(rain.test(Contexts.weather("Zone1_Rain", true)));
        assertFalse(rain.test(Contexts.base()));
    }

    @Test
    void moonMatchesItsPhases() {
        Condition c = condition("{\"Type\":\"Moon\",\"Phases\":[0,4]}");
        assertTrue(c.test(Contexts.moon(4)));
        assertFalse(c.test(Contexts.moon(2)));
    }

    @Test
    void openWaterAndSkyReadTheContext() {
        assertTrue(condition("{\"Type\":\"OpenWater\"}").test(Contexts.water(3, true, true)));
        assertFalse(condition("{\"Type\":\"OpenWater\"}").test(Contexts.water(3, false, true)));
        assertFalse(condition("{\"Type\":\"Sky\",\"Visible\":true}").test(Contexts.water(3, true, false)));
    }
}
