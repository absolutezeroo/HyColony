package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC WindowMainPage: the Home tab's ribbon (hut name and level), build button and colony pack. */
class TownHallHomeViewTest {
    private final TownHallFixture f = new TownHallFixture();

    @Test
    void homeShowsTheTownHallLevelAndPosition() {
        f.townHall().setLevel(3);
        TownHallView.Home home = f.townHallView(f.alice).home();
        assertEquals(3, home.townHallLevel());
        assertEquals(f.hall, home.townHallPos());
    }

    @Test
    void homeShowsTheTownHallsRunningOrder() {
        Building hall = f.townHall();
        hall.setLevel(1);
        hall.setBuilt(true);
        assertEquals(
                Optional.empty(), f.manager.workOrders().order(f.alice, f.hall, WorkOrderType.UPGRADE, "medieval"));
        assertEquals(
                Optional.of(WorkOrderType.UPGRADE),
                f.townHallView(f.alice).home().order());
    }

    @Test
    void homeHasNoOrderWhenNoneRuns() {
        assertEquals(Optional.empty(), f.townHallView(f.alice).home().order());
    }

    @Test
    void homeShowsTheFirstStyleForAColonyWithoutOne() {
        f.colony.settings().setStyle("");
        assertEquals("medieval", f.townHallView(f.alice).home().style());
    }

    @Test
    void homeOffersTheBlueprintStylesAndTheColonyStyle() {
        f.colony.settings().setStyle("desert");
        TownHallView.Home home = f.townHallView(f.alice).home();
        assertEquals(List.of("medieval", "desert"), home.styles());
        assertEquals("desert", home.style());
    }
}
