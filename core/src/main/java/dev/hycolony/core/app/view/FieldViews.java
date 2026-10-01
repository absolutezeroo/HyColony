package dev.hycolony.core.app.view;

import dev.hycolony.core.app.ui.FieldView;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Optional;
import java.util.UUID;

/** Builds a field block's view (MC WindowField.updateAll), with each side seen from the viewer's look now. */
final class FieldViews {
    private final ColonyContext ctx;

    FieldViews(ColonyContext ctx) {
        this.ctx = ctx;
    }

    /** The view of the field at {@code pos} for {@code viewer}; empty once the colony no longer has that field. */
    Optional<FieldView> of(Colony c, BlockPos pos, UUID viewer) {
        return c.registries().fields().get(pos).map(f -> of(c, f, pos, viewer));
    }

    private FieldView of(Colony c, FarmField f, BlockPos pos, UUID viewer) {
        Optional<String> farmer = f.owner()
                .flatMap(c.buildings()::at)
                .flatMap(b -> WorkerModule.firstWorker(c, b))
                .map(CitizenData::name);
        return new FieldView(
                pos,
                farmer,
                ctx.worldQuery().biome(pos),
                f.seed(),
                f.radii(),
                FieldView.sides(ctx.players().facing(viewer)),
                ctx.ports().farming().seeds(),
                ColonyAccess.allows(c, viewer, Action.MANAGE_HUTS));
    }
}
