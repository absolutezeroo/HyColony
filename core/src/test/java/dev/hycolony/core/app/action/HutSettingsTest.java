package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.SettingRow;
import dev.hycolony.core.building.module.SettingsView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.farming.hut.FarmerHut;
import dev.hycolony.core.farming.hut.FarmerSettingsModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC SettingsModuleWindow: the huts' settings rows in MC's order, and TriggerSettingMessage (MANAGE_HUTS). */
class HutSettingsTest {
    private static final BlockPos BUILDER = new BlockPos(30, 64, 0);
    private static final BlockPos FARM = new BlockPos(0, 64, 30);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony;

    HutSettingsTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), BUILDER, 0, alice);
        manager.huts().place(colony, FarmerHut.TYPE_ID, FARM, 0, alice);
    }

    private SettingsView settings(BlockPos pos) {
        manager.windows().openBuilding(alice, pos);
        return ((BuildingView) t.ui.shown.get(alice)).tab(SettingsView.class).orElseThrow();
    }

    private static List<String> ids(SettingsView v) {
        return v.rows().stream().map(SettingRow::id).toList();
    }

    @Test
    void builderRowsAreMcsInOrder() {
        SettingsView v = settings(BUILDER);
        assertEquals(List.of("mode", "recipemode", "buildmode", "fillblock"), ids(v));
        assertEquals(SettingRow.Kind.STRING, v.rows().get(0).kind());
        assertEquals("hycolony.ui.setting.value.automatic", v.rows().get(0).valueKey());
        assertEquals(SettingRow.Kind.BLOCK, v.rows().get(3).kind());
    }

    @Test
    void researchSettingsShowInactiveWithMcsReason() {
        SettingsView v = settings(BUILDER);
        SettingRow recipe = v.rows().get(1);
        SettingRow build = v.rows().get(2);
        assertFalse(recipe.active());
        assertFalse(build.active());
        assertEquals(
                "hycolony.ui.setting.research.warehousemaster",
                recipe.researchKey().orElseThrow());
        assertEquals(
                "hycolony.ui.setting.research.buildermodes", build.researchKey().orElseThrow());
        assertTrue(v.rows().get(0).active());
    }

    @Test
    void triggeringTheModeSwitchesIt() {
        assertTrue(manager.hutWindows().triggerSetting(alice, BUILDER, "mode"));
        Building b = colony.buildings().at(BUILDER).orElseThrow();
        assertEquals(
                BuilderSettingsModule.Mode.MANUAL,
                b.module(BuilderSettingsModule.class).orElseThrow().mode());
        assertEquals(
                "hycolony.ui.setting.value.manual",
                settings(BUILDER).rows().getFirst().valueKey());
    }

    @Test
    void anInactiveOrUnknownSettingIgnoresClicks() {
        assertFalse(manager.hutWindows().triggerSetting(alice, BUILDER, "buildmode"));
        assertFalse(manager.hutWindows().triggerSetting(alice, BUILDER, "nope"));
    }

    @Test
    void triggerNeedsManageHuts() {
        assertFalse(manager.hutWindows().triggerSetting(UUID.randomUUID(), BUILDER, "mode"));
        assertEquals(
                BuilderSettingsModule.Mode.AUTO,
                colony.buildings()
                        .at(BUILDER)
                        .orElseThrow()
                        .module(BuilderSettingsModule.class)
                        .orElseThrow()
                        .mode());
    }

    @Test
    void farmerRowsAreFertilizeThenRecipeMode() {
        SettingsView v = settings(FARM);
        assertEquals(List.of("fertilize", "recipemode"), ids(v));
        assertEquals(SettingRow.Kind.BOOL, v.rows().getFirst().kind());
        assertTrue(v.rows().getFirst().on());
        assertTrue(manager.hutWindows().triggerSetting(alice, FARM, "fertilize"));
        assertFalse(colony.buildings()
                .at(FARM)
                .orElseThrow()
                .module(FarmerSettingsModule.class)
                .orElseThrow()
                .fertilize());
    }
}
