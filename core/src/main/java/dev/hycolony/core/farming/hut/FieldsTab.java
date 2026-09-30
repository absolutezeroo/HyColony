package dev.hycolony.core.farming.hut;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Fields tab of a farmer hut as a viewer sees it (MC FarmerFieldsModuleView and FarmFieldsModuleWindow): the
 * fields free or owned by the hut, owned first, then nearest first (MC FieldsComparator).
 */
final class FieldsTab {
    /** The eight short directions, from north clockwise ({@code hycolony.ui.direction.<key>}). */
    private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private FieldsTab() {}

    static FieldsView of(Colony c, Building hut, FarmerFieldsModule module, boolean fertilize, UUID viewer) {
        int owned = c.registries().fields().ownedBy(hut.position()).size();
        List<FieldsView.Row> rows = c.registries().fields().all().stream()
                .filter(f -> f.owner().isEmpty() || f.owner().get().equals(hut.position()))
                .sorted(Comparator.comparing((FarmField f) -> !f.isTaken())
                        .thenComparingInt(f -> distance(hut.position(), f.pos())))
                .map(f -> row(c, hut, module, f))
                .toList();
        return new FieldsView(
                module.assignManually(),
                owned,
                module.maxFields(hut),
                fertilize,
                rows,
                ColonyAccess.allows(c, viewer, Action.MANAGE_HUTS));
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

    /** The short direction from {@code from} to {@code to}, north being -z. */
    static String direction(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.x() - from.x(), from.z() - to.z()));
        int sector = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        return DIRECTIONS[sector];
    }
}
