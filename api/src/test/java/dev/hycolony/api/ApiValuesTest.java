package dev.hycolony.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The api's values: immutable, and refusing what an addon cannot mean. */
class ApiValuesTest {
    @Test
    void textKeepsItsOwnCopyOfItsParams() {
        List<Object> params = new ArrayList<>(List.of("Bob"));
        ApiText text = new ApiText("hycolony.citizen.name", params);

        params.add(3);

        assertEquals(List.of("Bob"), text.params());
        assertEquals(new ApiText("hycolony.citizen.name", List.of("Bob")), ApiText.of("hycolony.citizen.name", "Bob"));
    }

    @Test
    void textParamsAreStringsOrTexts() {
        ApiText nested = ApiText.of("hycolony.job.courier");

        assertEquals(
                List.of("Bob", nested),
                ApiText.of("hycolony.citizen.job", "Bob", nested).params());
        assertThrows(IllegalArgumentException.class, () -> ApiText.of("k", 3), "a number is formatted by the caller");
        assertThrows(IllegalArgumentException.class, () -> ApiText.of("k", new ArrayList<>()), "mutable");
        assertThrows(NullPointerException.class, () -> ApiText.of("k", (Object) null));
        assertThrows(NullPointerException.class, () -> ApiText.of(null));
    }

    @Test
    void valuesRefuseAMissingPart() {
        assertThrows(NullPointerException.class, () -> new ColonyRef(null, 1));
        assertThrows(NullPointerException.class, () -> new CitizenRef(null, 1));
        assertThrows(NullPointerException.class, () -> new Actor.Player(null));
        assertThrows(NullPointerException.class, () -> new Actor.Plugin(null));
        assertThrows(NullPointerException.class, () -> new ActionResult.Refused(null));
    }

    @Test
    void pluginActorIsNamedByItsIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> new Actor.Plugin(" "));
        assertEquals("HyColony:hylens", new Actor.Plugin("HyColony:hylens").name());
    }

    @Test
    void anActionIsDoneRefusedNotFoundOrUnavailable() {
        assertEquals("done", describe(new ActionResult.Done()));
        assertEquals("refused k", describe(new ActionResult.Refused(ApiText.of("k"))));
        assertEquals("not found", describe(new ActionResult.NotFound()));
        assertEquals("unavailable", describe(new ActionResult.Unavailable()));
    }

    /** An exhaustive switch: it stops compiling if a case is added, which the version policy calls a major change. */
    private static String describe(ActionResult result) {
        return switch (result) {
            case ActionResult.Done d -> "done";
            case ActionResult.Refused r -> "refused " + r.reason().key();
            case ActionResult.NotFound n -> "not found";
            case ActionResult.Unavailable u -> "unavailable";
        };
    }

    @Test
    void citizenKnowsItsColony() {
        ColonyRef colony = new ColonyRef("world", 3);

        assertEquals(colony, new CitizenRef(colony, 7).colony());
    }

    @Test
    void anActorIsAPlayerAPluginOrTheColonyItself() {
        UUID id = UUID.randomUUID();

        assertEquals("player " + id, describe(new Actor.Player(id)));
        assertEquals("plugin HyColony:hylens", describe(new Actor.Plugin("HyColony:hylens")));
        assertEquals("colony", describe(new Actor.Colony()));
    }

    /** An exhaustive switch: it stops compiling if a case is added, which the version policy calls a major change. */
    private static String describe(Actor actor) {
        return switch (actor) {
            case Actor.Player p -> "player " + p.id();
            case Actor.Plugin p -> "plugin " + p.name();
            case Actor.Colony c -> "colony";
        };
    }

    @Test
    void currentVersionIsOnePointTwo() {
        assertEquals(new ApiVersion(1, 2, 0), ApiVersion.CURRENT);
        assertEquals("1.2.0", ApiVersion.CURRENT.toString());
    }
}
