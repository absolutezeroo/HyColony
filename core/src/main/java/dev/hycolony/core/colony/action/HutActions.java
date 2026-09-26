package dev.hycolony.core.colony.action;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.HutPlacement;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.view.ColonyWindows;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * What players do to huts: place and remove them (MC AbstractBlockHut), pick a deconstructed one up, and staff it
 * (hire, fire, hiring mode, builder mode). A managing action needs MANAGE_HUTS and re-shows the hut's window.
 */
public final class HutActions {
    private final ColonyManager manager;
    private final ColonyWindows windows;

    public HutActions(ColonyManager manager, ColonyWindows windows) {
        this.manager = manager;
        this.windows = windows;
    }

    /** Port of AbstractBlockHut.canPaste. */
    public HutPlacement checkPlacement(UUID player, BlockPos pos, String buildingTypeId) {
        boolean isTownHall = BuildingTypes.TOWN_HALL.id().equals(buildingTypeId);
        Optional<Colony> colony = manager.colonyAt(pos);
        if (colony.isEmpty()) {
            return checkOutsideColonies(player, pos, isTownHall);
        }
        Colony c = colony.get();
        if (!c.permissions().hasPermission(player, Action.PLACE_HUTS)) {
            return new HutPlacement.Denied(Msg.of("hycolony.permission.placeHuts", c.name()));
        }
        if (isTownHall && c.buildings().townHall().isPresent()) {
            return new HutPlacement.Denied(Msg.of("hycolony.hut.townHallExists"));
        }
        return new HutPlacement.Allowed(c);
    }

    /** Outside every colony only a town hall may stand, founding a new colony. */
    private HutPlacement checkOutsideColonies(UUID player, BlockPos pos, boolean isTownHall) {
        boolean owner = manager.ownedBy(player).isPresent();
        if (!isTownHall) {
            return new HutPlacement.Denied(Msg.of(owner ? "hycolony.hut.tooFar" : "hycolony.hut.noTownHall"));
        }
        if (!manager.persistence().available()) {
            return new HutPlacement.Denied(Msg.of("hycolony.storage.unavailable"));
        }
        if (owner) {
            return new HutPlacement.Denied(Msg.of("hycolony.colony.alreadyOwner"));
        }
        Optional<Msg> spawn = spawnDistanceRefusal(player, pos);
        if (spawn.isPresent()) {
            return new HutPlacement.Denied(spawn.get());
        }
        ColonyContext ctx = manager.context();
        if (!manager.territory()
                .isFreeForNewColony(
                        pos,
                        ctx.config().claims().initialColonySize(),
                        ctx.config().claims().minColonyDistance())) {
            return new HutPlacement.Denied(Msg.of("hycolony.colony.tooClose"));
        }
        return new HutPlacement.FoundNewColony();
    }

    /**
     * MC CreateColonyMessage: the refusal when {@code pos} is nearer to the world spawn (2D) than
     * {@code MinDistanceFromWorldSpawn} or farther than {@code MaxDistanceFromWorldSpawn}, with the blocks missing or
     * in excess; empty if in range or the spawn is unknown.
     */
    private Optional<Msg> spawnDistanceRefusal(UUID player, BlockPos pos) {
        Optional<BlockPos> spawn = manager.context().worldQuery().spawnPoint(player);
        if (spawn.isEmpty()) {
            return Optional.empty();
        }
        long dx = (long) pos.x() - spawn.get().x();
        long dz = (long) pos.z() - spawn.get().z();
        double distance = Math.sqrt(dx * dx + dz * dz);
        var claims = manager.context().config().claims();
        if (distance < claims.minDistanceFromWorldSpawn()) {
            int missing = (int) (claims.minDistanceFromWorldSpawn() - distance);
            return Optional.of(Msg.of("hycolony.colony.tooCloseToSpawn", String.valueOf(missing)));
        }
        if (distance > claims.maxDistanceFromWorldSpawn()) {
            int excess = (int) (distance - claims.maxDistanceFromWorldSpawn());
            return Optional.of(Msg.of("hycolony.colony.tooFarFromSpawn", String.valueOf(excess)));
        }
        return Optional.empty();
    }

    /** A building already registered at {@code pos} is stale (its block is gone): it is removed first, never a throw. */
    public void place(Colony colony, String buildingTypeId, BlockPos pos, int rotation) {
        BuildingType type =
                manager.context().buildingTypes().byId(buildingTypeId).orElseThrow();
        remove(colony, pos);
        Building building = Building.create(type, pos, rotation);
        colony.buildings().add(building);
        colony.log().add("buildingPlaced", colony.day(), type.id());
        colony.markDirty();
        manager.context().bus().post(new ColonyEvents.BuildingPlaced(colony, building));
    }

    public void onRemoved(BlockPos pos) {
        manager.colonyAt(pos).ifPresent(c -> remove(c, pos));
    }

    private void remove(Colony c, BlockPos pos) {
        c.buildings().remove(pos).ifPresent(b -> {
            c.log().add("buildingRemoved", c.day(), b.type().id());
            c.markDirty();
            manager.context().bus().post(new ColonyEvents.BuildingRemoved(c, b));
        });
    }

    public boolean hire(UUID player, BlockPos hutPos, int citizenId) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        WorkerModule w =
                h == null ? null : h.building().module(WorkerModule.class).orElse(null);
        CitizenData citizen =
                w == null ? null : h.colony().citizens().get(citizenId).orElse(null);
        if (citizen == null || citizen.isChild() || !w.hire(h.colony(), h.building(), citizen)) {
            return false;
        }
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    public boolean fire(UUID player, BlockPos hutPos, int citizenId) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        WorkerModule w =
                h == null ? null : h.building().module(WorkerModule.class).orElse(null);
        if (w == null || !w.workers().contains(citizenId)) {
            return false;
        }
        w.fire(h.colony(), h.building(), citizenId);
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    public boolean setHiring(UUID player, BlockPos hutPos, HiringMode mode) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        WorkerModule w =
                h == null ? null : h.building().module(WorkerModule.class).orElse(null);
        if (w == null || mode == null) {
            return false;
        }
        w.setHiringMode(mode);
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /** The builder hut's Settings tab (MC BuilderSettingsModule's mode setting); false for a hut without it. */
    public boolean setBuilderMode(UUID player, BlockPos hutPos, BuilderSettingsModule.Mode mode) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        BuilderSettingsModule s = h == null
                ? null
                : h.building().module(BuilderSettingsModule.class).orElse(null);
        if (s == null || mode == null) {
            return false;
        }
        s.setMode(mode);
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /**
     * MC's requestRemoval on a deconstructed hut (AbstractBuilding.pickUp): once every check passes, {@code giveItem}
     * gives the player the hut item (with its level) and says whether it fit. Only then does the building leave the
     * colony through the normal removal path (workers fired, requests and orders cancelled); the plugin removes the
     * block. A full inventory refuses and keeps the building.
     */
    public boolean pickUp(UUID player, BlockPos hutPos, BooleanSupplier giveItem) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null || !h.building().canBePickedUp()) {
            return false;
        }
        if (!giveItem.getAsBoolean()) {
            manager.context().notifier().send(player, Msg.of("hycolony.hut.pickupInventoryFull"));
            return false;
        }
        onRemoved(hutPos);
        manager.context().ui().close(player);
        return true;
    }
}
