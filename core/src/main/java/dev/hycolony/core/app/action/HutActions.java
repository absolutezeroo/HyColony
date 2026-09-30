package dev.hycolony.core.app.action;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ColonySpacing;
import dev.hycolony.core.app.HutPlacement;
import dev.hycolony.core.app.view.ColonyWindows;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyAccess;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
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

    /** Port of AbstractBlockHut.canPaste: PLACE_HUTS inside a colony, then {@link #checkHutRules}. */
    public HutPlacement checkPlacement(UUID player, BlockPos pos, String buildingTypeId) {
        Optional<Colony> colony = manager.colonyAt(pos);
        if (colony.isPresent() && !ColonyAccess.allows(colony.get(), player, Action.PLACE_HUTS)) {
            return new HutPlacement.Denied(
                    Msg.of("hycolony.permission.placeHuts", colony.get().name()));
        }
        return checkHutRules(player, pos, buildingTypeId);
    }

    /**
     * The town hall and colony rules of {@link #checkPlacement}, without its permission check, for a caller that
     * checks its own (MC SurvivalHandler checks MANAGE_HUTS only): one town hall per colony, the founding rules
     * outside colonies, and no other hut outside a colony.
     */
    public HutPlacement checkHutRules(UUID player, BlockPos pos, String buildingTypeId) {
        boolean isTownHall = BuildingTypes.TOWN_HALL.id().equals(buildingTypeId);
        Optional<Colony> colony = manager.colonyAt(pos);
        if (colony.isEmpty()) {
            return checkOutsideColonies(player, pos, isTownHall);
        }
        Colony c = colony.get();
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
        if (!ColonySpacing.isFarEnoughFromColonies(manager, pos)) {
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
        double distance = Math.sqrt((double) (dx * dx + dz * dz));
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

    /**
     * Registers the hut {@code player} placed. A building already registered at {@code pos} is stale (its block is
     * gone): the colony removes it first, never a throw.
     */
    public void place(Colony colony, String buildingTypeId, BlockPos pos, int rotation, UUID player) {
        BuildingType type =
                manager.context().buildingTypes().byId(buildingTypeId).orElseThrow();
        remove(colony, pos, Optional.empty());
        Building building = Building.create(type, pos, rotation);
        colony.buildings().add(building);
        colony.log().add("buildingPlaced", colony.day(), type.id());
        colony.markDirty();
        manager.context().bus().post(new ColonyEvents.BuildingPlaced(colony, building, Optional.of(player)));
    }

    /** {@code player} broke or picked up the hut block at {@code pos}: its building leaves the colony. */
    public void onRemoved(BlockPos pos, UUID player) {
        manager.colonyAt(pos).ifPresent(c -> remove(c, pos, Optional.of(player)));
    }

    /**
     * A player breaks the hut block at {@code pos} (MC ColonyPermissionEventHandler.on(BreakEvent), hut part): an
     * unconfirmed town hall only cancels its foundation; a hut block without its building, or any hut while colony
     * protection is off, breaks whoever breaks it; else a player without BREAK_HUTS is told and nothing changes.
     * Returns false when the break is refused.
     */
    public boolean breakBy(UUID player, BlockPos pos) {
        if (manager.foundation().cancelAt(pos).isPresent()) {
            return true;
        }
        boolean hasBuilding = manager.colonyAt(pos)
                .filter(c -> c.buildings().at(pos).isPresent())
                .isPresent();
        if (hasBuilding && manager.protection().refuses(player, pos, Action.BREAK_HUTS)) {
            return false;
        }
        onRemoved(pos, player);
        return true;
    }

    private void remove(Colony c, BlockPos pos, Optional<UUID> player) {
        c.buildings().remove(pos).ifPresent(b -> {
            c.log().add("buildingRemoved", c.day(), b.type().id());
            c.markDirty();
            manager.context().bus().post(new ColonyEvents.BuildingRemoved(c, b, player));
        });
    }

    /**
     * MC {@code AbstractWindowWorkerModuleBuilding.hireClicked}: refuses with a
     * {@code com.minecolonies.coremod.gui.workerhuts.level0}-style chat message when the hut cannot assign citizens
     * yet ({@link WorkerModule#canAssignCitizens}), instead of the silent failure of {@link WorkerModule#hire}. Any
     * other refusal still re-shows the window: auto-hiring may have filled the hut behind a stale one.
     */
    public boolean hire(UUID player, BlockPos hutPos, int citizenId) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null) {
            return false;
        }
        WorkerModule w = h.building().module(WorkerModule.class).orElse(null);
        if (w == null) {
            return false;
        }
        if (!w.canAssignCitizens(h.building())) {
            manager.context().notifier().send(player, Msg.of("hycolony.hut.notBuiltYet"));
            return false;
        }
        CitizenData citizen = h.colony().citizens().get(citizenId).orElse(null);
        boolean hired = citizen != null && !citizen.isChild() && w.hire(h.colony(), h.building(), citizen);
        windows.showBuilding(h.colony(), h.building(), player);
        return hired;
    }

    /**
     * MC {@code HireFireMessage} (fire): the citizen leaves the hut. A citizen who no longer works here is ignored
     * without a message, as in MC, but the window is still re-shown, as MC's client redraws its hire window.
     */
    public boolean fire(UUID player, BlockPos hutPos, int citizenId) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null) {
            return false;
        }
        WorkerModule w = h.building().module(WorkerModule.class).orElse(null);
        if (w == null) {
            return false;
        }
        boolean fired = w.workers().contains(citizenId);
        if (fired) {
            w.fire(h.colony(), h.building(), citizenId);
        }
        windows.showBuilding(h.colony(), h.building(), player);
        return fired;
    }

    public boolean setHiring(UUID player, BlockPos hutPos, HiringMode mode) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null) {
            return false;
        }
        WorkerModule w = h.building().module(WorkerModule.class).orElse(null);
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
        if (h == null) {
            return false;
        }
        BuilderSettingsModule s =
                h.building().module(BuilderSettingsModule.class).orElse(null);
        if (s == null || mode == null) {
            return false;
        }
        s.setMode(mode);
        h.colony().markDirty();
        windows.showBuilding(h.colony(), h.building(), player);
        return true;
    }

    /**
     * The builder hut's fill block (MC BUILDER_SETTINGS fillblock), one of the source's choices; false for another
     * block, a hut without the setting or a player who may not manage it.
     */
    public boolean setFillBlock(UUID player, BlockPos hutPos, BlockKey block) {
        ManagedHut h = ManagedHut.find(manager, player, hutPos).orElse(null);
        if (h == null
                || !manager.context().ports().blueprints().fillBlockChoices().contains(block)) {
            return false;
        }
        BuilderSettingsModule s =
                h.building().module(BuilderSettingsModule.class).orElse(null);
        if (s == null) {
            return false;
        }
        s.setFillBlock(block);
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
        onRemoved(hutPos, player);
        manager.windows().ui().close(player);
        return true;
    }
}
