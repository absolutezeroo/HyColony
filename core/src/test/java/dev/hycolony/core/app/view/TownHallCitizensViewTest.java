package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ui.CitizenRow;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC WindowCitizenPage: citizens sorted by name, each with its job and its skill levels. */
class TownHallCitizensViewTest {
    private final TownHallFixture f = new TownHallFixture();

    @Test
    void citizensAreSortedByName() {
        f.citizen(2, "Zoe");
        f.citizen(3, "Ann");
        assertEquals(
                List.of("Ann", "Bob", "Zoe"),
                f.townHallView(f.alice).citizens().stream()
                        .map(CitizenRow::name)
                        .toList());
    }

    @Test
    void aRowCarriesItsCitizenIdJobAndSkills() {
        CitizenRow bob = f.townHallView(f.alice).citizens().getFirst();
        assertEquals(f.bob.id(), bob.id());
        assertEquals(Optional.of("hycolony:builder"), bob.jobId());
        assertEquals(Skill.values().length, bob.skills().size());
        assertEquals(Skill.Athletics, bob.skills().getFirst().skill());
        assertEquals(
                f.bob.skills().level(Skill.Athletics), bob.skills().getFirst().level());
    }

    @Test
    void theVitalsShowItsBodysHealthElseAFullHundred() {
        f.bob.setSaturation(12.7);
        CitizenRow.Vitals bodiless =
                f.townHallView(f.alice).citizens().getFirst().vitals();
        assertEquals(100, bodiless.health()); // no body: MC's full health, on Hytale's scale
        assertEquals(100, bodiless.maxHealth());
        assertEquals(12, bodiless.saturation());

        BodyId body = f.t.bodies.existing(f.colony.id(), f.bob.id(), new Vec3(0, 64, 0));
        f.colony.citizens().onBodyLoaded(body, f.bob.id());
        f.t.bodies.bodies.get(body).health = 7.5;

        assertEquals(7, f.townHallView(f.alice).citizens().getFirst().vitals().health());
    }

    @Test
    void theSearchMatchesTheNameOrTheShownJobIgnoringCaseAsMc() {
        CitizenRow bob = f.townHallView(f.alice).citizens().getFirst();
        assertTrue(bob.matches("", "Builder"));
        assertTrue(bob.matches("bO", "Builder"));
        assertTrue(bob.matches("BUILD", "Builder"));
        assertFalse(bob.matches("farmer", "Builder"));
    }
}
