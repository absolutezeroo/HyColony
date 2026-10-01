package dev.hycolony.core.app.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.WandPacksView;
import dev.hycolony.core.app.ui.WandView;
import dev.hycolony.core.construction.blueprint.PackInfo;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.FakeBlueprints;
import dev.hycolony.core.testing.FakePreviews;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ST WindowSwitchPack and StructurePacks.ensureSelectedPack: the packs by owner, the filter, Select and Cancel. */
class WandPacksTest {
    private static final String BUILDER = ConstructionBuildingTypes.BUILDER.id();

    private final FakeBlueprints plans = new FakeBlueprints().put(BUILDER, 1, FakeBlueprints.hut(false));
    private final TestContexts t = contexts();
    private final ColonyManager manager = t.manager();
    private final FakePreviews previews = new FakePreviews();
    private final WandActions wand =
            new WandActions(manager, previews, k -> new ItemKey("item:" + k), k -> new BlockKey("block:" + k));
    private final UUID alice = UUID.randomUUID();
    private final BlockPos spot = new BlockPos(10, 64, 10);

    private TestContexts contexts() {
        TestContexts c = new TestContexts();
        plans.styleIds.addAll(List.of("nordic", "kweebec", "alpha", "omega"));
        plans.packs.put(FakeBlueprints.STYLE, new PackInfo("Medieval", "", List.of(), "", "minecolonies"));
        plans.packs.put("nordic", new PackInfo("Viking", "", List.of(), "", "hytale"));
        plans.packs.put("kweebec", new PackInfo("Kweebec", "", List.of(), "", "hytale"));
        plans.packs.put("alpha", new PackInfo("Alpha", "", List.of(), "", "aaa"));
        plans.packs.put("omega", new PackInfo("Omega", "", List.of(), "", "zzz"));
        c.blueprints = plans;
        return c;
    }

    private WandPacksView packs() {
        return assertInstanceOf(WandPacksView.class, t.ui.shown.get(alice));
    }

    private WandView view() {
        return assertInstanceOf(WandView.class, t.ui.shown.get(alice));
    }

    @Test
    void theFirstOpenPicksAPackAtRandom() {
        assertTrue(wand.open(alice, Optional.of(spot)));
        assertTrue(plans.styleIds.contains(view().style()), "ST StructurePacks.ensureSelectedPack");
    }

    @Test
    void withNoPackAtAllThePackWindowOpensEmptyAndCancelCloses() {
        plans.styleIds.clear();
        assertTrue(wand.open(alice, Optional.of(spot)));
        assertTrue(packs().groups().isEmpty());
        assertFalse(packs().hasStyle());
        assertTrue(wand.cancelPacks(alice));
        assertFalse(t.ui.shown.containsKey(alice));
    }

    @Test
    void packsAreGroupedByOwnerInNameOrder() {
        wand.open(alice, Optional.of(spot));
        assertTrue(wand.switchPack(alice));
        assertEquals(
                List.of("aaa", "hytale", "minecolonies", "zzz"),
                packs().groups().stream().map(WandPacksView.Group::owner).toList());
        assertEquals(2, packs().groups().get(1).packs().size());
        assertTrue(packs().hasStyle());
    }

    @Test
    void theFilterMatchesTheNameOrTheStyleIgnoringCase() {
        wand.open(alice, Optional.of(spot));
        wand.switchPack(alice);
        assertEquals(List.of("nordic"), packs().filtered("VIK").styles(), "by name");
        assertEquals(List.of("nordic"), packs().filtered("nor").styles(), "by style id");
        assertEquals(
                List.of("hytale"),
                packs().filtered("nor").groups().stream()
                        .map(WandPacksView.Group::owner)
                        .toList());
        assertEquals(packs(), packs().filtered(" "));
    }

    @Test
    void selectingAPackOpensTheBuildToolAndCancelGoesBack() {
        wand.open(alice, Optional.of(spot));
        wand.switchPack(alice);
        assertTrue(wand.selectStyle(alice, "nordic"));
        assertEquals("Viking", view().packName());
        wand.switchPack(alice);
        assertTrue(wand.cancelPacks(alice));
        assertEquals("nordic", view().style());
    }

    /** ST: Cancel and Select open a new WindowExtendedBuildTool, whose init enables the icons and hides the lists. */
    @Test
    void backFromThePackWindowTheBuildToolIsLaidOutAnew() {
        t.players.creative.add(alice);
        wand.open(alice, Optional.of(spot));
        wand.selectStyle(alice, FakeBlueprints.STYLE);
        wand.openCategory(alice, "fundamentals");
        wand.switchPack(alice);
        wand.cancelPacks(alice);
        assertEquals("", view().panel().disabledCategory());
        assertFalse(view().panel().back());
        wand.openCategory(alice, "fundamentals");
        wand.selectBuilding(alice, BUILDER);
        wand.openPlacement(alice);
        wand.switchPack(alice);
        wand.selectStyle(alice, FakeBlueprints.STYLE);
        assertFalse(view().panel().placing());
        assertEquals(BUILDER, view().buildingTypeId(), "the same pack keeps the hut");
    }

    @Test
    void anotherPackForgetsTheFolderTheHutAndTheRotation() {
        t.players.creative.add(alice);
        wand.open(alice, Optional.of(spot));
        wand.selectStyle(alice, FakeBlueprints.STYLE);
        wand.openCategory(alice, "fundamentals");
        wand.selectBuilding(alice, BUILDER);
        wand.rotate(alice, true);
        wand.switchPack(alice);
        wand.selectStyle(alice, "nordic");
        assertEquals("", view().depth());
        assertEquals("", view().buildingTypeId());
        assertEquals(0, view().rotation(), "ST RenderingCache.removeBlueprint");
        assertTrue(previews.of(alice).isEmpty(), "the ghost goes with the hut");
    }
}
