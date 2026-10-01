package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.colony.EventLog;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC WindowInfoPage.fillEventsList: the colony's events of MC's kinds, in log order, with their position. */
class TownHallInfoViewTest {
    private final TownHallFixture f = new TownHallFixture();

    @Test
    void infoListsMcEventsInLogOrderWithTheirPositions() {
        BlockPos res = new BlockPos(20, 64, 0);
        f.colony.log().addAt(f.hall, "citizenSpawned", 1, "Ann");
        f.colony.log().add("buildingPlaced", 1, "hycolony:residence"); // HyColony's own: not shown
        f.colony.log().addAt(res, "buildingBuilt", 2, "hycolony:residence", "1");

        List<TownHallView.EventRow> events = f.townHallView(f.alice).info().events();

        assertEquals(
                List.of("citizenSpawned", "buildingBuilt"),
                events.stream().map(TownHallView.EventRow::type).toList());
        assertEquals(Optional.of(res), events.get(1).pos());
        assertEquals(List.of("hycolony:residence", "1"), events.get(1).params());
        assertEquals(2, events.get(1).day());
    }

    @Test
    void infoCarriesTheColonyDay() {
        f.colony.setDay(12);
        assertEquals(12, f.townHallView(f.alice).info().day());
    }

    @Test
    void anArrivingCitizenIsLoggedAtTheTownHall() {
        for (int i = 0; i < 20; i++) {
            f.colony.citizens().onColonyTick();
        }
        EventLog.Entry spawned = f.colony.log().entries().stream()
                .filter(e -> e.type().equals("citizenSpawned"))
                .findFirst()
                .orElseThrow();
        assertEquals(Optional.of(f.hall), spawned.pos());
    }

    @Test
    void anIntervalKeepsTheEventsOfItsLastDaysAsMc() {
        f.colony.log().add("citizenSpawned", 3, "Old");
        f.colony.log().add("citizenSpawned", 9, "Yesterday");
        f.colony.log().add("citizenSpawned", 10, "Today");
        f.colony.setDay(10);
        TownHallView.Info info = f.townHallView(f.alice).info();
        assertEquals(List.of("Yesterday", "Today"), names(info.within(1)));
        assertEquals(List.of("Old", "Yesterday", "Today"), names(info.within(7)));
        assertEquals(3, info.within(-1).size());
    }

    private static List<String> names(List<TownHallView.EventRow> events) {
        return events.stream().map(e -> e.params().getFirst()).toList();
    }
}
