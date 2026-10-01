package dev.hycolony.core.app;

import dev.hycolony.core.app.action.ColonyAdministration;
import dev.hycolony.core.app.action.HutActions;
import dev.hycolony.core.app.ui.FoundColonyView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.shared.UpgradeCompletion;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A player founding a colony: the town hall they placed waits, unconfirmed, until they name the colony (a new
 * colony) or cancel (the adapter removes the block).
 */
public final class ColonyFoundation {
    /**
     * The town hall waiting for its colony: its block, rotation, style ("" for the pack's first) and level (0 when
     * placed, the pasted level after a creative paste).
     */
    public record TownHall(BlockPos pos, int rotation, String style, int level) {}

    private record Pending(String playerName, TownHall hall) {}

    private final ColonyManager manager;
    private final HutActions huts;
    private final Map<UUID, Pending> pending = new HashMap<>();

    ColonyFoundation(ColonyManager manager, HutActions huts) {
        this.manager = manager;
        this.huts = huts;
    }

    /** Hand placement: no style chosen, so the town hall gets the pack's first one (MC default). */
    public void begin(UUID player, String playerName, BlockPos pos, int rotation) {
        begin(player, playerName, new TownHall(pos, rotation, "", 0));
    }

    /** Build tool placement: {@code style} is carried to the town hall once the colony is confirmed. */
    public void begin(UUID player, String playerName, BlockPos pos, int rotation, String style) {
        begin(player, playerName, new TownHall(pos, rotation, style, 0));
    }

    /**
     * Opens the founding window for {@code hall}. A pasted town hall is founded at its pasted level, as MC
     * RegisteredStructureManager.addNewBuilding syncs it to the pasted blueprint (upgradeBuildingLevelToSchematicData).
     */
    public void begin(UUID player, String playerName, TownHall hall) {
        pending.put(player, new Pending(playerName, hall));
        manager.windows().ui().showFoundColony(player, new FoundColonyView(playerName + "'s Colony"));
    }

    /** Empty if nothing is pending, the name is invalid, or the spot became invalid meanwhile. */
    public Optional<Colony> confirm(UUID player, String rawName) {
        Pending p = pending.get(player);
        if (p == null) {
            return Optional.empty();
        }
        Optional<String> validated = ColonyAdministration.validName(manager.context(), player, rawName);
        if (validated.isEmpty()) {
            return Optional.empty();
        }
        HutPlacement check = huts.checkPlacement(player, p.hall().pos(), BuildingTypes.TOWN_HALL.id());
        if (check instanceof HutPlacement.Denied(var reason)) {
            // Same as a cancel; the adapter sees pendingPositionOf go empty and removes the block.
            cancel(player);
            manager.context().notifier().send(player, reason);
            return Optional.empty();
        }
        pending.remove(player);
        return Optional.of(found(player, p, validated.get()));
    }

    /** Creates, registers and saves the colony named {@code name} around the pending town hall {@code p}. */
    private Colony found(UUID player, Pending p, String name) {
        TownHall hall = p.hall();
        ColonyContext ctx = manager.context();
        manager.windows().ui().close(player);
        Colony colony = new Colony(
                ctx,
                manager.territory(),
                new Colony.Founding(
                        manager.allocateId(), name, hall.pos(), Permissions.createDefault(player, p.playerName())));
        manager.register(colony);
        colony.log().add("colonyCreated", colony.day(), name);
        ctx.bus().post(new ColonyEvents.ColonyCreated(colony, Optional.of(player)));
        huts.place(colony, BuildingTypes.TOWN_HALL.id(), hall.pos(), hall.rotation(), player);
        Building townHall = colony.buildings().at(hall.pos()).orElseThrow();
        if (!hall.style().isEmpty()) {
            // MC CreateColonyMessage: the pack the town hall is built in becomes the colony's.
            colony.settings().setStyle(hall.style());
            townHall.setStyle(hall.style());
        }
        if (hall.level() > 0) {
            UpgradeCompletion.reach(colony, townHall, hall.level(), Optional.of(player));
        }
        ctx.notifier().send(player, Msg.of("hycolony.colony.created", name));
        manager.persistence().save(colony);
        return colony;
    }

    /** Returns where the unconfirmed town hall stands, so the adapter can remove it. */
    public Optional<BlockPos> cancel(UUID player) {
        // Remove before closing, and close only once: closing may re-enter here (window dismiss = cancel).
        Pending p = pending.remove(player);
        if (p == null) {
            return Optional.empty();
        }
        manager.windows().ui().close(player);
        return Optional.of(p.hall().pos());
    }

    public Optional<BlockPos> pendingPositionOf(UUID player) {
        return Optional.ofNullable(pending.get(player)).map(p -> p.hall().pos());
    }

    /** Cancels whichever player's unconfirmed town hall stands at {@code pos}; returns that player. */
    public Optional<UUID> cancelAt(BlockPos pos) {
        Optional<UUID> owner = pending.entrySet().stream()
                .filter(e -> e.getValue().hall().pos().equals(pos))
                .map(Map.Entry::getKey)
                .findFirst();
        owner.ifPresent(this::cancel);
        return owner;
    }
}
