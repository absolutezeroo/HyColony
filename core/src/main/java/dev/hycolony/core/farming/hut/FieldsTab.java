package dev.hycolony.core.farming.hut;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The Fields tab of a farmer hut as a viewer sees it (MC FarmerFieldsModuleView and FarmFieldsModuleWindow): the
 * fields free or owned by the hut, owned first, then nearest first (MC FieldsComparator).
 */
final class FieldsTab {
    /**
     * The directions after north counter-clockwise ({@code hycolony.ui.direction.<key>}), each up to its
     * {@link #SECTOR_ENDS} bound in whole degrees (MC calcDirection: 22 < NW < 67 <= W <= 112 < SW < 157...).
     */
    private static final String[] DIRECTIONS = {"nw", "w", "sw", "s", "se", "e", "ne"};

    private static final int[] SECTOR_ENDS = {66, 112, 156, 202, 246, 292, 337};

    private FieldsTab() {}

    static FieldsView of(Colony c, Building hut, FarmerFieldsModule module) {
        int owned = c.registries().fields().ownedBy(hut.position()).size();
        List<FieldsView.Row> rows = c.registries().fields().all().stream()
                .filter(f -> f.owner().isEmpty() || f.owner().get().equals(hut.position()))
                .sorted(Comparator.comparing((FarmField f) -> !f.isTaken())
                        .thenComparingInt(f -> distance(hut.position(), f.pos())))
                .map(f -> row(c, hut, module, f))
                .toList();
        return new FieldsView(module.assignManually(), owned, module.maxFields(hut), rows);
    }

    private static FieldsView.Row row(Colony c, Building hut, FarmerFieldsModule module, FarmField f) {
        return new FieldsView.Row(
                f.pos(),
                f.seed(),
                distance(hut.position(), f.pos()),
                direction(hut.position(), f.pos()),
                f.stage(),
                f.isTaken(),
                f.isTaken() || module.canAssign(c, hut, f) ? Optional.empty() : Optional.of(refusal(c, hut, module)),
                f.isTaken() && module.doneToday(c, f));
    }

    /** MC FarmFieldsModuleWindow tooltips: the field count reached, else no seed set. */
    private static String refusal(Colony c, Building hut, FarmerFieldsModule module) {
        return c.registries().fields().ownedBy(hut.position()).size() >= module.maxFields(hut)
                ? "hycolony.ui.fields.refused.limit"
                : "hycolony.ui.fields.refused.noseed";
    }

    /** MC BlockPosUtil.getDistance: the whole Euclidean distance. */
    static int distance(BlockPos a, BlockPos b) {
        return (int) Math.sqrt((double) a.distSq(b));
    }

    /**
     * MC BlockPosUtil.calcDirection: the direction key of {@code to} seen from {@code from}, north being -z; "up",
     * "down" or "same" on the same column. The angle is truncated to whole degrees before MC's sector bounds.
     */
    static String direction(BlockPos from, BlockPos to) {
        if (to.x() == from.x() && to.z() == from.z()) {
            return to.y() > from.y() ? "up" : to.y() < from.y() ? "down" : "same";
        }
        int degree = (int) (Math.atan2(from.x() - to.x(), from.z() - to.z()) * 180 / Math.PI);
        if (degree < 0) {
            degree += 360;
        }
        if (degree <= 22 || degree >= 338) {
            return "n";
        }
        int i = 0;
        while (degree > SECTOR_ENDS[i]) {
            i++;
        }
        return DIRECTIONS[i];
    }
}
