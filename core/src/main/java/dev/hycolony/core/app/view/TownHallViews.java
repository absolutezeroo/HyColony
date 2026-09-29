package dev.hycolony.core.app.view;

import dev.hycolony.core.app.ui.CitizenRow;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Builds the town hall window's view (MC WindowTownHall): the colony, its work orders, citizens and statistics. */
final class TownHallViews {
    private final ColonyContext ctx;

    TownHallViews(ColonyContext ctx) {
        this.ctx = ctx;
    }

    TownHallView of(Colony c, UUID viewer) {
        List<CitizenRow> rows = c.citizens().all().stream()
                .map(d -> new CitizenRow(d.name(), d.gender(), status(c, d)))
                .toList();
        return new TownHallView(
                c.id(),
                c.name(),
                c.permissions().ownerName(),
                c.day(),
                rows,
                c.permissions().rankOf(viewer).isColonyManager(),
                WorkOrderViews.of(c, viewer),
                TownHallStats.of(c));
    }

    /** "absent" without a live body, else the AI state: "idle", "wandering" or "working". */
    String status(Colony c, CitizenData d) {
        boolean present = c.citizens().bodyOf(d.id()).map(ctx.bodies()::isAlive).orElse(false);
        return !present
                ? "absent"
                : c.citizens()
                        .aiState(d.id())
                        .map(s -> s.name().toLowerCase(Locale.ROOT))
                        .orElse("idle");
    }
}
