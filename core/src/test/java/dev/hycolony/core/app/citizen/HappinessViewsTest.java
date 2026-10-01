package dev.hycolony.core.app.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.citizen.HappinessBar.Smiley;
import dev.hycolony.core.app.citizen.HappinessRows.Mood;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.HappinessEvents;
import dev.hycolony.core.citizen.happiness.HappinessIds;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CitizenWindowUtils.createHappinessBar and updateHappiness, WindowCitizenPage.fillHappinessList. */
class HappinessViewsTest {
    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));

    @Test
    void theBarShowsOneSmileyPerWholePointAndNeverAHalf() {
        assertEquals(Collections.nCopies(10, Smiley.EMPTY), HappinessBar.of(0.9));
        var bar = HappinessBar.of(3.7);
        assertEquals(3, Collections.frequency(bar, Smiley.FULL));
        assertEquals(0, Collections.frequency(bar, Smiley.HALF)); // MC casts before doubling
        assertEquals(Collections.nCopies(10, Smiley.FULL), HappinessBar.of(10));
    }

    @Test
    void moodsFollowMcThresholds() {
        assertEquals(Mood.POSITIVE, HappinessRows.mood(1.2));
        assertEquals(Mood.NEUTRAL, HappinessRows.mood(1.0));
        assertEquals(Mood.SLIGHTLY_NEGATIVE, HappinessRows.mood(0.8));
        assertEquals(Mood.NEGATIVE, HappinessRows.mood(0.75));
    }

    @Test
    void theTabListsOnlyFactorsThatAreNotNeutral() {
        CitizenData c = new CitizenData(1);
        colony.citizens().restore(c);
        HappinessEvents.greatFood(c);
        c.happiness().happiness(colony, c);
        var rows = HappinessRows.of(c.happiness());
        assertTrue(rows.contains(new HappinessRows.Row(HappinessIds.HADGREATFOOD, Mood.POSITIVE)));
        assertTrue(rows.contains(new HappinessRows.Row(HappinessIds.HOMELESSNESS, Mood.NEGATIVE)));
        assertTrue(rows.stream().noneMatch(r -> r.id().equals(HappinessIds.SCHOOL)));
    }

    @Test
    void theTownHallAveragesEveryModifierAndRoundsTheMeanUp() {
        colony.citizens().restore(new CitizenData(1));
        colony.citizens().restore(new CitizenData(2));
        var rows = HappinessRows.colony(colony);
        assertEquals(10, rows.size());
        assertTrue(rows.contains(new HappinessRows.Row(HappinessIds.SCHOOL, Mood.NEUTRAL)));
        // Two homeless, jobless adults: security 1 / (3 x 2/3) = 0.5, social (2 - 4) / 2, housing 0, unemployment 0.5.
        assertEquals("1", HappinessRows.overall(colony)); // 10 x (2 - 2 + 0 + 1) / 11 = 0.909..., rounded up
    }

    @Test
    void theMeanKeepsOneDecimalRoundedUp() {
        CitizenData c = new CitizenData(1);
        colony.citizens().restore(c);
        c.setJob(TestJobs.TYPE.factory().apply(c));
        // security 0.75 (w 4), social 0 (w 2), housing 0 (w 3), unemployment 0.5 (w 2): 10 x 4 / 11 = 3.63...
        assertEquals("3.7", HappinessRows.overall(colony));
    }
}
