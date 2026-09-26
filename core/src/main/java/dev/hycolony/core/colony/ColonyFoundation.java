package dev.hycolony.core.colony;

import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.action.ColonyAdministration;
import dev.hycolony.core.colony.action.HutActions;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.ui.FoundColonyView;
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
    private record Pending(String playerName, BlockPos pos, int rotation) {}

    private final ColonyManager manager;
    private final HutActions huts;
    private final Map<UUID, Pending> pending = new HashMap<>();

    ColonyFoundation(ColonyManager manager, HutActions huts) {
        this.manager = manager;
        this.huts = huts;
    }

    public void begin(UUID player, String playerName, BlockPos pos, int rotation) {
        pending.put(player, new Pending(playerName, pos, rotation));
        manager.context().ui().showFoundColony(player, new FoundColonyView(playerName + "'s Colony"));
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
        String name = validated.get();
        HutPlacement check = huts.checkPlacement(player, p.pos(), BuildingTypes.TOWN_HALL.id());
        if (check instanceof HutPlacement.Denied denied) {
            // Same as a cancel; the adapter sees pendingPositionOf go empty and removes the block.
            cancel(player);
            manager.context().notifier().send(player, denied.reason());
            return Optional.empty();
        }
        pending.remove(player);
        ColonyContext ctx = manager.context();
        ctx.ui().close(player);
        Colony colony = new Colony(
                ctx,
                manager.territory(),
                new Colony.Founding(
                        manager.allocateId(), name, p.pos(), Permissions.createDefault(player, p.playerName())));
        manager.register(colony);
        colony.log().add("colonyCreated", colony.day(), name);
        ctx.bus().post(new ColonyEvents.ColonyCreated(colony));
        huts.place(colony, BuildingTypes.TOWN_HALL.id(), p.pos(), p.rotation());
        ctx.notifier().send(player, Msg.of("hycolony.colony.created", name));
        manager.persistence().save(colony);
        return Optional.of(colony);
    }

    /** Returns where the unconfirmed town hall stands, so the adapter can remove it. */
    public Optional<BlockPos> cancel(UUID player) {
        // Remove before closing, and close only once: closing may re-enter here (window dismiss = cancel).
        Pending p = pending.remove(player);
        if (p == null) {
            return Optional.empty();
        }
        manager.context().ui().close(player);
        return Optional.of(p.pos());
    }

    public Optional<BlockPos> pendingPositionOf(UUID player) {
        return Optional.ofNullable(pending.get(player)).map(Pending::pos);
    }

    /** Cancels whichever player's unconfirmed town hall stands at {@code pos}; returns that player. */
    public Optional<UUID> cancelAt(BlockPos pos) {
        Optional<UUID> owner = pending.entrySet().stream()
                .filter(e -> e.getValue().pos().equals(pos))
                .map(Map.Entry::getKey)
                .findFirst();
        owner.ifPresent(this::cancel);
        return owner;
    }
}
