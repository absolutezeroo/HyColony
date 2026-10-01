package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC LivingBuildingView.getHoverWarningForLevel: what a residence lacks before each upgrade. */
class ResidenceWarningTest {
    private static final BlockPos HOUSE = new BlockPos(10, 64, 0);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony = found();

    private Colony found() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony c = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(c, ConstructionBuildingTypes.RESIDENCE.id(), HOUSE, 0, UUID.randomUUID());
        return c;
    }

    private Optional<String> warningAt(int level) {
        colony.buildings().at(HOUSE).orElseThrow().setLevel(level);
        manager.windows().openBuilding(alice, HOUSE);
        return ((BuildingView) t.ui.shown.get(alice)).upgradeWarning();
    }

    @Test
    void residenceWarnsBeforeEachUpgradeWithoutFoodBuildings() {
        assertEquals(Optional.of("hycolony.ui.residence.warning.2"), warningAt(1));
        assertEquals(Optional.of("hycolony.ui.residence.warning.3"), warningAt(2));
        assertEquals(Optional.of("hycolony.ui.residence.warning.4"), warningAt(3));
        assertEquals(Optional.of("hycolony.ui.residence.warning.5"), warningAt(4));
        assertEquals(Optional.empty(), warningAt(5));
        assertEquals(Optional.empty(), warningAt(0)); // MC's default: no warning to build it
    }

    @Test
    void aBuiltFarmLiftsTheFirstWarning() {
        BlockPos farm = new BlockPos(-10, 64, 0);
        manager.huts().place(colony, FarmerHut.TYPE_ID, farm, 0, UUID.randomUUID());
        assertEquals(Optional.of("hycolony.ui.residence.warning.2"), warningAt(1)); // level 0 farm: not yet
        colony.buildings().at(farm).orElseThrow().setLevel(1);

        assertEquals(Optional.empty(), warningAt(1));
    }

    @Test
    void otherHutsHaveNoUpgradeWarning() {
        manager.windows().openBuilding(alice, new BlockPos(0, 64, 0));
        assertEquals(Optional.empty(), ((BuildingView) t.ui.shown.get(alice)).upgradeWarning());
    }
}
