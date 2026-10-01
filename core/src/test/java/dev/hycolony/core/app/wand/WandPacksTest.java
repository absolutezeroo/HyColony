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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ST WindowSwitchPack: the packs by owner, the filter, Select and Cancel. */
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
        plans.styleIds.addAll(List.of("nordic", "kweebec"));
        plans.packs.put(FakeBlueprints.STYLE, new PackInfo("Medieval", "", List.of(), "", "minecolonies"));
        plans.packs.put("nordic", new PackInfo("Nordic", "", List.of(), "", "hytale"));
        plans.packs.put("kweebec", new PackInfo("Kweebec", "", List.of(), "", "hytale"));
        c.blueprints = plans;
        return c;
    }

    private WandPacksView packs() {
        return assertInstanceOf(WandPacksView.class, t.ui.shown.get(alice));
    }

    @Test
    void openingWithoutAPackShowsThePackWindow() {
        assertTrue(wand.open(alice, Optional.of(spot)));
        assertFalse(packs().hasStyle());
        assertEquals(Set.of(FakeBlueprints.STYLE, "nordic", "kweebec"), Set.copyOf(packs().styles()));
    }

    @Test
    void packsAreGroupedByOwnerInNameOrder() {
        wand.open(alice, Optional.of(spot));
        assertEquals(
                List.of("hytale", "minecolonies"),
                packs().groups().stream().map(WandPacksView.Group::owner).toList());
        assertEquals(2, packs().groups().getFirst().packs().size());
    }

    @Test
    void theFilterKeepsThePacksWhoseNameContainsItIgnoringCase() {
        wand.open(alice, Optional.of(spot));
        WandPacksView filtered = packs().filtered("NOR");
        assertEquals(List.of("nordic"), filtered.styles());
        assertEquals(
                List.of("hytale"),
                filtered.groups().stream().map(WandPacksView.Group::owner).toList());
        assertEquals(List.of("medieval"), packs().filtered("evAL").styles(), "the name, ignoring case");
        assertEquals(packs(), packs().filtered(" "));
    }

    @Test
    void selectingAPackOpensTheBuildTool() {
        wand.open(alice, Optional.of(spot));
        assertTrue(wand.selectStyle(alice, "nordic"));
        WandView view = assertInstanceOf(WandView.class, t.ui.shown.get(alice));
        assertEquals("Nordic", view.packName());
    }

    @Test
    void cancelClosesWithoutAPackAndGoesBackWithOne() {
        wand.open(alice, Optional.of(spot));
        assertTrue(wand.cancelPacks(alice));
        assertFalse(t.ui.shown.containsKey(alice));
        wand.open(alice, Optional.empty());
        wand.selectStyle(alice, FakeBlueprints.STYLE);
        assertTrue(wand.switchPack(alice));
        assertTrue(packs().hasStyle());
        assertTrue(wand.cancelPacks(alice));
        assertInstanceOf(WandView.class, t.ui.shown.get(alice));
    }

    @Test
    void anotherPackForgetsTheFolderAndTheHut() {
        t.players.creative.add(alice);
        wand.open(alice, Optional.of(spot));
        wand.selectStyle(alice, FakeBlueprints.STYLE);
        wand.openCategory(alice, "fundamentals");
        wand.selectBuilding(alice, BUILDER);
        wand.switchPack(alice);
        wand.selectStyle(alice, "nordic");
        WandView view = assertInstanceOf(WandView.class, t.ui.shown.get(alice));
        assertEquals("", view.depth());
        assertEquals("", view.buildingTypeId());
        assertTrue(previews.of(alice).isEmpty(), "the ghost goes with the hut");
    }
}
