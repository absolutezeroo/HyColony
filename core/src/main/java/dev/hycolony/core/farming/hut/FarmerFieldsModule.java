package dev.hycolony.core.farming.hut;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingEventsModule;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.building.ProvidesTab;
import dev.hycolony.core.building.TickingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldJson;
import dev.hycolony.core.farming.field.FieldRegistry;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.logistics.pickup.KeepRule;
import dev.hycolony.core.logistics.pickup.KeepsItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The fields a farmer hut works (MC BuildingFarmer.FarmerFieldsModule over BuildingExtensionsModule): at most its
 * level of them, claimed one per colony tick unless assigned by hand, and the one to work next.
 */
public final class FarmerFieldsModule
        implements PersistentModule, TickingModule, BuildingEventsModule, KeepsItems, ProvidesTab {
    /** MC getRequiredItemsAndAmount: 64 of each owned field's seed stay with the farmer. */
    public static final int SEEDS_KEPT = 64;

    private boolean assignManually;
    private final FieldChoice choice = new FieldChoice();
    private final FieldWalk walk = new FieldWalk();

    /** MC shouldAssignManually; false (automatic) by default. */
    public boolean assignManually() {
        return assignManually;
    }

    public void setAssignManually(boolean manual) {
        assignManually = manual;
    }

    /** MC getMaxExtensionCount: the hut's level. */
    public int maxFields(Building b) {
        return b.level();
    }

    /** MC canAssignExtension: room left and a seed set on the field (MC canAssignExtensionOverride). */
    public boolean canAssign(Colony c, Building b, FarmField f) {
        return c.registries().fields().ownedBy(b.position()).size() < maxFields(b)
                && f.seed().isPresent();
    }

    /** MC assignExtension: the field becomes this hut's when it may; true on success. */
    public boolean assign(Colony c, Building b, FarmField f) {
        if (f.isTaken() || !canAssign(c, b, f)) {
            return false;
        }
        f.setOwner(Optional.of(b.position()));
        c.markDirty();
        return true;
    }

    /** MC freeExtension: the field is free again, and no longer current. */
    public void free(Colony c, Building b, FarmField f) {
        if (f.owner().filter(b.position()::equals).isEmpty()) {
            return;
        }
        f.setOwner(Optional.empty());
        if (choice.current(c.registries().fields(), b.position()).isEmpty()) {
            choice.reset(c.day());
        }
        c.markDirty();
    }

    /**
     * MC getExtensionToWorkOn. A field other than the last current one (that one broken, freed or done) starts its
     * pass from its first cell; with no field, no pass is in progress.
     */
    public Optional<FarmField> fieldToWorkOn(Colony c, Building b) {
        Optional<BlockPos> before = choice.currentPos();
        Optional<FarmField> picked = choice.pick(c.registries().fields(), b.position(), c.day());
        if (picked.isEmpty() || !picked.map(FarmField::pos).equals(before)) {
            walk.reset();
        }
        return picked;
    }

    /**
     * True when {@code f} got its pass today: the farmer comes back to it the next colony day (MC getExtensionToWorkOn).
     */
    public boolean doneToday(Colony c, FarmField f) {
        return choice.doneToday(f.pos(), c.day());
    }

    /** MC getCurrentExtension. */
    public Optional<FarmField> currentField(Colony c, Building b) {
        return choice.current(c.registries().fields(), b.position());
    }

    /** Where the farmer is in its pass over the current field. */
    public FieldWalk walk() {
        return walk;
    }

    /** MC resetCurrentExtension: the current field is done for today. */
    public void resetCurrentField(Colony c) {
        choice.reset(c.day());
        c.markDirty();
    }

    /** MC onColonyTick → claimExtensions: in automatic mode, the first free field that can be assigned is taken. */
    @Override
    public void onColonyTick(Colony colony, Building building) {
        if (assignManually) {
            return;
        }
        for (FarmField f : colony.registries().fields().free()) {
            if (assign(colony, building, f)) {
                return;
            }
        }
    }

    /** Deviation from MC: the hut's fields are freed at once, where MC waits for the next colony load. */
    @Override
    public void onRemoved(Colony colony, Building building) {
        FieldRegistry fields = colony.registries().fields();
        for (FarmField f : fields.ownedBy(building.position())) {
            f.setOwner(Optional.empty());
        }
        colony.markDirty();
    }

    /** 64 of each owned field's seed, in the hut and in the farmer's inventory. */
    @Override
    public List<KeepRule> keepRules(Colony colony, Building building) {
        List<KeepRule> rules = new ArrayList<>();
        for (FarmField f : colony.registries().fields().ownedBy(building.position())) {
            f.seed().ifPresent(seed -> rules.add(new KeepRule(seed::equals, SEEDS_KEPT, true)));
        }
        return rules;
    }

    /** MC FarmerFieldsModuleView: the Fields tab, with the hut's Request Fertilizer setting. */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        boolean fertilize = building.module(FarmerSettingsModule.class)
                .map(FarmerSettingsModule::fertilize)
                .orElse(true);
        return FieldsTab.of(colony, building, this, fertilize, viewer);
    }

    @Override
    public void write(JsonObject out) {
        out.addProperty("assignManually", assignManually);
        choice.write(out);
        walk.write(out);
    }

    @Override
    public void read(JsonObject in) {
        assignManually = FieldJson.bool(in.get("assignManually")).orElse(false);
        choice.read(in);
        walk.read(in);
    }
}
