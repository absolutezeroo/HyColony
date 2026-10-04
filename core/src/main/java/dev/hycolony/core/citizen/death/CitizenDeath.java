package dev.hycolony.core.citizen.death;

import dev.hycolony.core.building.module.AssignedCitizenModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.happiness.ExpirationModifier;
import dev.hycolony.core.citizen.happiness.HappinessIds;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.stats.ColonyStatistics;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A citizen's death (MC EntityCitizen.die), in MC's order. Guards, raids, graves and achievements are not ported:
 * their branches are left out.
 */
public final class CitizenDeath {
    /** MC die: injectModifier(new ExpirationBasedHappinessModifier(DEATH, 3.0, ..., 3)). */
    static final double DEATH_WEIGHT = 3.0;

    static final int DEATH_DAYS = 3;

    private CitizenDeath() {}

    /**
     * Citizen {@code citizenId} of {@code colony} died at {@code at} of {@code cause}: every other citizen is
     * saddened and its housemates will mourn it, the death is counted, its job and home are freed, what it carried
     * falls where it died, the colony is told and logs it, and the citizen goes for good; then {@link CitizenDied} is
     * posted. False, changing nothing, for a citizen the colony does not have (already dead).
     */
    public static boolean die(Colony colony, int citizenId, Vec3 at, DeathCause cause) {
        CitizenData dead = colony.citizens().get(citizenId).orElse(null);
        if (dead == null) {
            return false;
        }
        BlockPos pos = at.toBlockPos();
        sadden(colony, dead);
        colony.registries().statistics().increment(ColonyStatistics.DEATH, colony.day());
        free(colony, dead);
        drop(colony, dead, pos);
        DeathNotice.send(colony, dead, pos, cause);
        colony.citizens().remove(citizenId);
        colony.log()
                .addAt(
                        pos,
                        "citizenDied",
                        colony.day(),
                        dead.name(),
                        cause.cause(),
                        cause.killer().orElse(""));
        colony.markDirty();
        colony.context().bus().post(new CitizenDied(colony, dead, cause));
        return true;
    }

    /**
     * MC injectModifier(DEATH) on every citizen, then updateCitizenMourn: those living with {@code dead} will mourn it.
     * Deviation from MC: no families (MC isRelatedTo) nor undertakers yet.
     */
    private static void sadden(Colony colony, CitizenData dead) {
        for (CitizenData other : colony.citizens().all()) {
            if (other.id() == dead.id()) {
                continue;
            }
            other.happiness().add(new ExpirationModifier(HappinessIds.DEATH, DEATH_WEIGHT, 0.0, DEATH_DAYS));
            if (dead.homeBuilding() != null && Objects.equals(dead.homeBuilding(), other.homeBuilding())) {
                other.mourning().addDeceased(dead.name());
            }
        }
    }

    /**
     * MC job.onRemoval then CitizenManager.removeCivilian: every hut lets it go ({@link AssignedCitizenModule}; a
     * workplace also cancels its requests), and a job held without a hut is removed too ({@link WorkerModule#free}).
     */
    private static void free(Colony colony, CitizenData dead) {
        AssignedCitizenModule.removeEverywhere(colony, dead.id());
        if (dead.job().isPresent()) {
            WorkerModule.free(colony, dead);
        }
    }

    /**
     * MC die: its inventory falls where it died. Deviation from MC: no grave (MC GraveManager.createCitizenGrave in the
     * colony), so it all falls, armour included, everywhere; no experience (Hytale world: Hytale has none).
     */
    private static void drop(Colony colony, CitizenData dead, BlockPos pos) {
        List<ItemAmount> items = new ArrayList<>();
        collect(dead.inventory(), items);
        collect(dead.equipment().armor(), items);
        if (!items.isEmpty()) {
            colony.context().ports().blocks().drop(pos, items);
        }
    }

    private static void collect(Inventory inventory, List<ItemAmount> into) {
        for (int i = 0; i < inventory.size(); i++) {
            inventory.slot(i).ifPresent(into::add);
            inventory.set(i, Optional.empty());
        }
    }
}
