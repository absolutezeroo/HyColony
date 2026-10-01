package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Gender;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC MainWindowCitizen: the name, the health and food bars, the gender seal and the creative skill buttons. */
class CitizenWindowViewTest {
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony;
    private final CitizenData ann;

    CitizenWindowViewTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        ann = new CitizenData(1);
        ann.setName("Ann");
        colony.citizens().restore(ann);
    }

    private CitizenView view() {
        manager.windows().openCitizen(alice, colony.id(), ann.id());
        return (CitizenView) t.ui.shown.get(alice);
    }

    @Test
    void healthIsTheBodysShareOfTwentyAsMcCitizens() {
        colony.citizens().respawnBody(ann.id());
        t.bodies.bodies.values().iterator().next().healthPercent = 59;
        assertEquals(11, view().health());
    }

    @Test
    void aDeadBodyCountsAsNoBody() {
        colony.citizens().respawnBody(ann.id());
        var body = t.bodies.bodies.values().iterator().next();
        body.healthPercent = 10;
        body.alive = false;
        assertEquals(20, view().health(), "a body gone counts as MC's missing entity: MAX_HEALTH");
    }

    @Test
    void aCitizenWithoutABodyShowsFullHealthAsMc() {
        assertEquals(20, view().health(), "MC CitizenDataView.getHealth: MAX_HEALTH without its entity");
    }

    @Test
    void theViewCarriesSaturationGenderAndWhetherTheViewerIsCreative() {
        ann.setSaturation(33);
        ann.setGender(Gender.FEMALE);
        CitizenView v = view();
        assertEquals(33, v.saturation());
        assertEquals(Gender.FEMALE, v.gender());
        assertFalse(v.creative());
        t.players.creative.add(alice);
        assertTrue(view().creative());
    }
}
