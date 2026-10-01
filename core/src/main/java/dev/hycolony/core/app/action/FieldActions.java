package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.FieldView;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldRadii;
import dev.hycolony.core.farming.hut.FarmerFieldsModule;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Optional;
import java.util.UUID;

/**
 * What players do with fields: place, break and open a field block (MC BlockScarecrow), change its seed and radii in
 * its window (MC FarmFieldUpdateSeedMessage, FarmFieldPlotResizeMessage), and the farmer hut's Fields tab buttons (MC
 * AssignmentModeMessage, AssignFieldMessage, the Request Fertilizer setting). Changes need MANAGE_HUTS; each re-shows
 * the window it came from. Built by its caller, like {@code CraftingActions}.
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
        show(manager.colonyAt(pos).orElseThrow(), pos, player);
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
        show(c, pos, player);
        return true;
    }

    private void show(Colony c, BlockPos pos, UUID player) {
        FarmField f = c.registries().fields().get(pos).orElseThrow();
        Optional<String> farmer = f.owner()
                .flatMap(c.buildings()::at)
                .flatMap(b -> WorkerModule.firstWorker(c, b))
                .map(CitizenData::name);
        manager.windows()
                .ui()
                .showField(
                        player,
                        new FieldView(
                                pos,
                                farmer,
                                manager.context().worldQuery().biome(pos),
                                f.seed(),
                                f.radii(),
                                FieldView.sides(manager.context().players().facing(player)),
                                manager.context().ports().farming().seeds(),
                                ColonyAccess.allows(c, player, Action.MANAGE_HUTS)));
    }
}
