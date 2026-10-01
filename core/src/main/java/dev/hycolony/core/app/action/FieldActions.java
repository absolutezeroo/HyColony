package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldRadii;
import dev.hycolony.core.farming.hut.FarmerFieldsModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do with fields: place, break and open a field block (MC BlockScarecrow), change its seed and radii in
 * its window (MC FarmFieldUpdateSeedMessage, FarmFieldPlotResizeMessage), and the farmer hut's Fields tab buttons (MC
 * AssignmentModeMessage, AssignFieldMessage, the Request Fertilizer setting). Changes need MANAGE_HUTS; each re-shows
 * the window it came from. Built by its caller, like {@code CraftingActions}.
 *
 * <p>Deviation from MC: a radius change needs MANAGE_HUTS too, where MC's FarmFieldPlotResizeMessage checks no right,
 * so that a stranger cannot reshape a colony's field; the window disables its buttons without it, where MC leaves
 * them on. A field block outside any colony opens no window, where MC shows one with its radii only.
 */
public final class FieldActions {
    private final ColonyManager manager;

    public FieldActions(ColonyManager manager) {
        this.manager = manager;
    }

    /** MC setPlacedBy: a field block placed in a colony becomes one of its fields; false outside any colony. */
    public boolean placed(UUID player, BlockPos pos) {
        Optional<Colony> c = manager.colonyAt(pos);
        c.ifPresent(colony -> {
            colony.registries().fields().add(pos);
            colony.markDirty();
        });
        return c.isPresent();
    }

    /** MC playerWillDestroy: the field is gone, and so is its hut's hold on it. */
    public void broken(BlockPos pos) {
        manager.colonyAt(pos).ifPresent(colony -> {
            if (colony.registries().fields().remove(pos).isPresent()) {
                colony.markDirty();
            }
        });
    }

    /** MC use: the field is registered if missing (MC addBuildingExtensionIfMissing), then its window shown. */
    public boolean open(UUID player, BlockPos pos) {
        if (!placed(player, pos)) {
            return false;
        }
        manager.windows().showField(manager.colonyAt(pos).orElseThrow(), pos, player);
        return true;
    }

    /** MC FarmFieldUpdateSeedMessage: {@code seed} must be a crop seed of the game. */
    public boolean setSeed(UUID player, BlockPos pos, ItemKey seed) {
        Optional<FarmField> f = managedField(player, pos);
        if (f.isEmpty() || !manager.context().ports().farming().seeds().contains(seed)) {
            return false;
        }
        f.get().setSeed(Optional.of(seed));
        return changed(player, pos);
    }

    /** MC WindowField radius button (FarmFieldPlotResizeMessage with the cycled radius). */
    public boolean cycleRadius(UUID player, BlockPos pos, FieldRadii.Direction dir) {
        Optional<FarmField> f = managedField(player, pos);
        f.ifPresent(field -> field.setRadii(field.radii().cycled(dir)));
        return f.isPresent() && changed(player, pos);
    }

    /** MC AssignmentModeMessage: manual assignment on or off. */
    public boolean toggleMode(UUID player, BlockPos hutPos) {
        Optional<Farm> farm = farm(player, hutPos);
        farm.ifPresent(f -> f.fields().setAssignManually(!f.fields().assignManually()));
        return farm.isPresent() && shown(farm.get(), player);
    }

    /** MC AssignFieldMessage(assign): only in manual mode. */
    public boolean assign(UUID player, BlockPos hutPos, BlockPos field) {
        Optional<Farm> farm = farm(player, hutPos).filter(f -> f.fields().assignManually());
        Optional<FarmField> f =
                farm.flatMap(x -> x.hut().colony().registries().fields().get(field));
        if (f.isEmpty()
                || !farm.get()
                        .fields()
                        .assign(farm.get().hut().colony(), farm.get().hut().building(), f.get())) {
            return false;
        }
        return shown(farm.get(), player);
    }

    /** MC AssignFieldMessage(free): only in manual mode. */
    public boolean free(UUID player, BlockPos hutPos, BlockPos field) {
        Optional<Farm> farm = farm(player, hutPos).filter(f -> f.fields().assignManually());
        Optional<FarmField> f =
                farm.flatMap(x -> x.hut().colony().registries().fields().get(field));
        f.ifPresent(x -> farm.get()
                .fields()
                .free(farm.get().hut().colony(), farm.get().hut().building(), x));
        return f.isPresent() && shown(farm.get(), player);
    }

    /** A farmer hut the player may manage, with its fields module. */
    private record Farm(ManagedHut hut, FarmerFieldsModule fields) {}

    private Optional<Farm> farm(UUID player, BlockPos hutPos) {
        return ManagedHut.find(manager, player, hutPos)
                .flatMap(h -> h.building().module(FarmerFieldsModule.class).map(m -> new Farm(h, m)));
    }

    private boolean shown(Farm farm, UUID player) {
        farm.hut().colony().markDirty();
        manager.windows().showBuilding(farm.hut().colony(), farm.hut().building(), player);
        return true;
    }

    private Optional<FarmField> managedField(UUID player, BlockPos pos) {
        return manager.colonyAt(pos)
                .filter(c -> ColonyAccess.allows(c, player, Action.MANAGE_HUTS))
                .flatMap(c -> c.registries().fields().get(pos));
    }

    private boolean changed(UUID player, BlockPos pos) {
        Colony c = manager.colonyAt(pos).orElseThrow();
        c.markDirty();
        manager.windows().showField(c, pos, player);
        return true;
    }
}
