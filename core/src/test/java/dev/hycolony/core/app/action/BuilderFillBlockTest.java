package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.SettingRow;
import dev.hycolony.core.building.module.SettingsView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The builder hut's fill block setting (MC BUILDER_SETTINGS fillblock). */
class BuilderFillBlockTest {
    private static final BlockKey DIRT = new BlockKey("dirt");
    private static final BlockKey GRAVEL = new BlockKey("gravel");
    private static final BlockKey TORCH = new BlockKey("torch");

    private final TestContexts t = new TestContexts();
    private final ColonyManager manager;
    private final UUID alice = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID(); // friend: may look, not manage
    private final Building builder;

    BuilderFillBlockTest() {
        t.blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                return Optional.empty();
            }

            @Override
            public List<String> styles() {
                return List.of("medieval");
            }

            @Override
            public Optional<BlockKey> defaultFillBlock() {
                return Optional.of(DIRT);
            }

            @Override
            public List<BlockKey> fillBlockChoices() {
                return List.of(DIRT, GRAVEL);
            }
        };
        manager = t.manager();
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        Colony colony = manager.foundation().confirm(alice, "A").orElseThrow();
        assertTrue(manager.administration().setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        BlockPos pos = new BlockPos(10, 64, 0);
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), pos, 0, UUID.randomUUID());
        builder = colony.buildings().at(pos).orElseThrow();
        builder.setLevel(1);
        t.ui.shown.clear();
    }

    private BuilderSettingsModule settings() {
        return builder.module(BuilderSettingsModule.class).orElseThrow();
    }

    /** The Settings tab's fill block row. */
    private SettingRow view() {
        manager.windows().openBuilding(alice, builder.position());
        return ((BuildingView) t.ui.shown.get(alice))
                .tab(SettingsView.class).orElseThrow().rows().stream()
                        .filter(r -> r.id().equals(BuilderSettingsModule.FILL_BLOCK))
                        .findFirst()
                        .orElseThrow();
    }

    @Test
    void theViewShowsTheDefaultFillBlockAndTheChoices() {
        SettingRow v = view();
        assertEquals(DIRT, v.block().orElseThrow());
        assertEquals(List.of(DIRT, GRAVEL), v.choices());
    }

    @Test
    void choosingAFillBlockNeedsManageHutsAndOneOfTheChoices() {
        assertFalse(manager.huts().setFillBlock(carol, builder.position(), GRAVEL));
        assertFalse(manager.huts().setFillBlock(alice, builder.position(), TORCH), "not a choice");
        assertEquals(Optional.empty(), settings().fillBlock());

        assertTrue(manager.huts().setFillBlock(alice, builder.position(), GRAVEL));

        assertEquals(Optional.of(GRAVEL), settings().fillBlock());
        assertTrue(t.ui.shown.get(alice) instanceof BuildingView, "the hut window is shown again");
        assertEquals(GRAVEL, view().block().orElseThrow());
    }

    @Test
    void theFillBlockIsSavedAndAnOldSaveKeepsTheDefault() {
        settings().setFillBlock(GRAVEL);
        JsonObject saved = new JsonObject();
        settings().write(saved);

        BuilderSettingsModule loaded = new BuilderSettingsModule();
        loaded.read(saved);
        assertEquals(Optional.of(GRAVEL), loaded.fillBlock());

        BuilderSettingsModule old = new BuilderSettingsModule();
        old.read(new JsonObject());
        assertEquals(Optional.empty(), old.fillBlock());
    }

    @Test
    void aSavedFillBlockNoLongerAmongTheChoicesGivesTheDefault() {
        JsonObject saved = new JsonObject();
        saved.addProperty("fillBlock", "removed_block");
        settings().read(saved);

        assertEquals(DIRT, view().block().orElseThrow());
    }
}
