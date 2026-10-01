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
    void homeShowsTheTownHallLevel() {
        f.townHall().setLevel(3);
        assertEquals(3, f.townHallView(f.alice).home().townHallLevel());
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
    void homeOffersTheBlueprintStylesAndTheColonyStyle() {
        f.colony.settings().setStyle("desert");
        TownHallView.Home home = f.townHallView(f.alice).home();
        assertEquals(List.of("medieval", "desert"), home.styles());
        assertEquals("desert", home.style());
    }
}
