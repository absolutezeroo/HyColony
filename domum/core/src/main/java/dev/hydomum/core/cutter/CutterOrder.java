package dev.hydomum.core.cutter;

import static java.util.Map.entry;

import dev.hydomum.api.OrnamentShape;
import java.util.Comparator;
import java.util.Map;

/**
 * The order of the cutter's groups and of each group's shapes (MC DO {@code SortedBlocks.init}): DO's sorting index
 * per group id and per shape, keyed here by manifest shape id.
 */
final class CutterOrder {
    /** DO's {@code groupSortingIndex}; ilight and jbrick have no shape yet but keep their place. */
    private static final Map<String, Integer> GROUP_INDEX = Map.ofEntries(
            entry("avanilla", 1),
            entry("btimberframe", 2),
            entry("cshingle", 3),
            entry("etrapdoor", 4),
            entry("ddoor", 5),
            entry("fpanel", 6),
            entry("hpaperwall", 7),
            entry("ilight", 8),
            entry("gpillar", 9),
            entry("kpost", 10),
            entry("jbrick", 11));

    /** DO's {@code sortingIndex}; the paper walls have none in DO either. */
    private static final Map<String, Integer> SHAPE_INDEX = Map.ofEntries(
            entry("TimberFrame_Framed", 1),
            entry("TimberFrame_DoubleCrossed", 2),
            entry("TimberFrame_Plain", 3),
            entry("TimberFrame_SideFramed", 4),
            entry("TimberFrame_UpGated", 5),
            entry("TimberFrame_DownGated", 6),
            entry("TimberFrame_OneCrossedLr", 7),
            entry("TimberFrame_OneCrossedRl", 8),
            entry("TimberFrame_HorizontalPlain", 9),
            entry("TimberFrame_SideFramedHorizontal", 10),
            entry("Stairs", 11),
            entry("Slab", 12),
            entry("Fence", 13),
            entry("FenceGate", 14),
            entry("Wall", 15),
            entry("Shingle", 16),
            entry("Shingle_Flat", 17),
            entry("Shingle_FlatLower", 18),
            entry("Shingle_Steep", 19),
            entry("Shingle_SteepLower", 20),
            entry("ShingleSlab", 21),
            entry("FancyDoor_Full", 22),
            entry("Door_VerticallyStriped", 23),
            entry("Door_Waffle", 24),
            entry("Door_PortManteau", 25),
            entry("Door_Full", 26),
            entry("FancyDoor_Creeper", 27),
            entry("Trapdoor_Waffle", 27),
            entry("Trapdoor_HorizontallySquigglyStriped", 28),
            entry("FancyTrapdoor_Full", 29),
            entry("Trapdoor_VerticallyStriped", 30),
            entry("Trapdoor_HorizontallyStriped", 31),
            entry("Trapdoor_PortManteau", 32),
            entry("Trapdoor_VerticalBars", 33),
            entry("Trapdoor_HorizontalBars", 34),
            entry("Trapdoor_VerticallySquigglyStriped", 35),
            entry("Trapdoor_Full", 36),
            entry("FancyTrapdoor_Creeper", 37),
            entry("Trapdoor_Slot", 38),
            entry("Trapdoor_Porthole", 39),
            entry("Trapdoor_Moulding", 40),
            entry("Trapdoor_Coffer", 41),
            entry("Trapdoor_Boss", 42),
            entry("Trapdoor_Roundel", 43),
            entry("Panel_Full", 44),
            entry("Panel_Waffle", 45),
            entry("Panel_VerticallyStriped", 46),
            entry("Panel_HorizontallyStriped", 47),
            entry("Panel_PortManteau", 48),
            entry("Panel_Moulding", 49),
            entry("Panel_Coffer", 50),
            entry("Panel_VerticalBars", 51),
            entry("Panel_HorizontalBars", 52),
            entry("Panel_VerticallySquigglyStriped", 53),
            entry("Panel_HorizontallySquigglyStriped", 54),
            entry("Panel_Slot", 55),
            entry("Panel_Porthole", 56),
            entry("Panel_Roundel", 57),
            entry("Panel_Boss", 58),
            entry("Pillar_Square", 59),
            entry("Pillar_Voxel", 60),
            entry("Pillar_Round", 61),
            entry("Post_Plain", 73),
            entry("Post_Double", 74),
            entry("Post_Quad", 75),
            entry("Post_Heavy", 76),
            entry("Post_Turned", 77),
            entry("Post_Pinched", 78));

    /**
     * Groups by DO's index (MC DO {@code SortedBlocks.sortGroups}).
     *
     * <p>Deviation from MC: DO throws on a group it has no index for; here an unknown group goes last, by id.
     */
    static final Comparator<String> GROUPS = Comparator.<String>comparingInt(
                    group -> GROUP_INDEX.getOrDefault(group, Integer.MAX_VALUE))
            .thenComparing(Comparator.naturalOrder());

    /**
     * Shapes by DO's index, a shape without one last (MC DO {@code SortedBlocks.sortItems}: {@code Double.MAX_VALUE});
     * under a stable sort ({@code List.sort}), shapes without an index keep the manifest's order.
     */
    static final Comparator<OrnamentShape> SHAPES =
            Comparator.comparingInt(shape -> SHAPE_INDEX.getOrDefault(shape.id(), Integer.MAX_VALUE));

    private CutterOrder() {}
}
