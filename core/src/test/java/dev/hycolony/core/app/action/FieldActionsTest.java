package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.app.ui.FieldView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldRadii;
import dev.hycolony.core.farming.hut.FarmerFieldsModule;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.farming.hut.FieldsView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.farming.FakeFarming;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The field block's window (MC WindowField) and the farmer hut's Fields tab (MC FarmFieldsModuleWindow). */
class FieldActionsTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final BlockPos NEAR = new BlockPos(14, 64, 0);
    private static final BlockPos FAR = new BlockPos(20, 64, 5);

    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID(); // friend: may look, not manage
    private final ColonyManager manager;
    private final Colony colony;
    private final Building hut;
    private final FieldActions fields;

    FieldActionsTest() {
        manager = t.manager();
        fields = new FieldActions(manager);
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        assertTrue(manager.administration().setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        manager.huts().place(colony, FarmerHut.TYPE_ID, HUT, 0, UUID.randomUUID());
        hut = colony.buildings().at(HUT).orElseThrow();
        hut.setLevel(2);
        hut.setBuilt(true);
        t.ui.shown.clear();
    }

    private FarmerFieldsModule module() {
        return hut.module(FarmerFieldsModule.class).orElseThrow();
    }

    private FarmField field(BlockPos pos) {
        return colony.registries().fields().get(pos).orElseThrow();
    }

    @Test
    void placingInsideTheColonyRegistersAField() {
        assertTrue(fields.placed(alice, NEAR));
        assertTrue(colony.registries().fields().get(NEAR).isPresent());
    }

    @Test
    void placingOutsideAColonyRegistersNothing() {
        assertFalse(fields.placed(alice, new BlockPos(5_000, 64, 5_000)));
    }

    @Test
    void openingAnUnknownFieldRegistersItAndShowsIt() {
        assertTrue(fields.open(carol, NEAR));
        FieldView view = assertInstanceOf(FieldView.class, t.ui.shown.get(carol));
        assertEquals(NEAR, view.pos());
        assertEquals(FieldRadii.defaults(), view.radii());
        assertFalse(view.canManage());
        assertEquals(t.farming.seeds(), view.seeds());
    }

    @Test
    void playerWithoutManageHutsCannotChangeTheSeed() {
        fields.placed(alice, NEAR);
        assertFalse(fields.setSeed(carol, NEAR, FakeFarming.WHEAT_SEEDS));
        assertTrue(field(NEAR).seed().isEmpty());
    }

    @Test
    void seedMustBeACropSeed() {
        fields.placed(alice, NEAR);
        assertFalse(fields.setSeed(alice, NEAR, new ItemKey("Rock_Stone")));
        assertTrue(fields.setSeed(alice, NEAR, FakeFarming.WHEAT_SEEDS));
        assertEquals(Optional.of(FakeFarming.WHEAT_SEEDS), field(NEAR).seed());
        assertInstanceOf(FieldView.class, t.ui.shown.get(alice), "window re-shown");
    }

    @Test
    void radiusButtonCycles() {
        fields.placed(alice, NEAR);
        assertTrue(fields.cycleRadius(alice, NEAR, FieldRadii.Direction.NORTH));
        assertEquals(1, field(NEAR).radii().north());
    }

    @Test
    void assignRefusedInAutomaticMode() {
        fields.placed(alice, NEAR);
        fields.setSeed(alice, NEAR, FakeFarming.WHEAT_SEEDS);
        assertFalse(fields.assign(alice, HUT, NEAR));
        assertTrue(fields.toggleMode(alice, HUT));
        assertTrue(fields.assign(alice, HUT, NEAR));
        assertTrue(field(NEAR).isTaken());
        assertTrue(fields.free(alice, HUT, NEAR));
        assertFalse(field(NEAR).isTaken());
    }

    @Test
    void rowsListOwnedFieldsFirstThenByDistance() {
        fields.placed(alice, FAR);
        fields.placed(alice, NEAR);
        fields.setSeed(alice, FAR, FakeFarming.WHEAT_SEEDS);
        fields.toggleMode(alice, HUT);
        fields.assign(alice, HUT, FAR);

        BuildingView view = assertInstanceOf(BuildingView.class, t.ui.shown.get(alice));
        FieldsView tab = view.tab(FieldsView.class).orElseThrow();

        assertEquals(
                List.of(FAR, NEAR),
                tab.rows().stream().map(FieldsView.Row::field).toList());
        assertTrue(tab.manual());
        assertEquals(1, tab.owned());
        assertEquals(2, tab.max());
        assertEquals(
                Optional.of("hycolony.ui.fields.refused.noseed"),
                tab.rows().get(1).refusal());
    }

    @Test
    void breakingAFieldRemovesIt() {
        fields.placed(alice, NEAR);
        fields.setSeed(alice, NEAR, FakeFarming.WHEAT_SEEDS);
        module().onColonyTick(colony, hut);

        fields.broken(NEAR);

        assertTrue(colony.registries().fields().get(NEAR).isEmpty());
        assertTrue(colony.registries().fields().ownedBy(HUT).isEmpty());
    }

    @Test
    void assignButtonsFollowTheModeOnlyAsMc() {
        assertTrue(fields.toggleMode(alice, HUT));
        manager.windows().openBuilding(carol, HUT);
        BuildingView view = assertInstanceOf(BuildingView.class, t.ui.shown.get(carol));
        assertTrue(view.tab(FieldsView.class).orElseThrow().canAssign(), "MC enables them in manual mode for all");
    }
}
