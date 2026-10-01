package dev.hycolony.core.citizen;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Where a citizen's body appears near a spot (MC EntityUtils.getSpawnPoint over BlockPosUtil.findAround): the spot's
 * column, its four neighbours, then MC's rings out to {@link #SCAN_RADIUS}, each column tried with the bodies port.
 * A building's own column is skipped: MC's checkValidSpawn refuses the hut's cell, whose block has a collision.
 *
 * <p>Deviation from MC: MC tests each cell of a 3D search, three levels up and down, keeping a level before moving
 * up or down, for two free blocks over a walkable one with a free neighbour. Hytale's column probe
 * (NPCPlugin.spawnNPCWithColumnProbe) picks the free span nearest the spot's height within 16 blocks and checks the
 * room for the model, so the search walks columns only and asks for no free neighbour.
 */
final class CitizenArrival {
    /** MC EntityUtils.SCAN_RADIUS, in blocks. */
    private static final int SCAN_RADIUS = 5;

    private CitizenArrival() {}

    /** The body that appeared in the first column around {@code near} that took it; empty if none did. */
    static Optional<BodyId> spawn(Colony colony, CitizenData data, BlockPos near) {
        ColonyContext ctx = colony.context();
        String name = colony.nameplates().nameFor(data);
        boolean building = colony.buildings().at(near).isPresent();
        for (BlockPos column : columns(near)) {
            if (building && column.equals(near)) {
                continue;
            }
            Optional<BodyId> body = ctx.bodies().spawn(ctx.world(), column, colony.id(), data.id(), name);
            if (body.isPresent()) {
                return body;
            }
        }
        return Optional.empty();
    }

    /**
     * MC findAround's order, flattened to columns: {@code near}, its neighbours (north, east, south, west), then for
     * each ring 1 to {@link #SCAN_RADIUS} the border MC walks from its north-west corner, each column once. MC's loops
     * cover [-ring, 1] on both axes, so the search leans north-west, as in MC.
     */
    static List<BlockPos> columns(BlockPos near) {
        Set<BlockPos> out = new LinkedHashSet<>();
        out.add(near);
        out.add(near.offset(0, 0, -1));
        out.add(near.offset(1, 0, 0));
        out.add(near.offset(0, 0, 1));
        out.add(near.offset(-1, 0, 0));
        for (int steps = 1; steps <= SCAN_RADIUS; steps++) {
            BlockPos at = near.offset(-steps, 0, -steps);
            for (int[] dir : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                for (int i = 0; i <= steps; i++) {
                    at = at.offset(dir[0], 0, dir[1]);
                    out.add(at);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * MC WARNING_COLONY_NO_ARRIVAL_SPACE, from spawnOrCreateCivilian when a new citizen finds no room at the town hall
     * {@code hall}: told to the colony's online owner and members allowed RECEIVE_MESSAGES, the project's reading of
     * MC's sendTo(colony).forAllPlayers (MC sends to the subscribers near the colony with that right).
     */
    static void tellNoSpace(Colony colony, BlockPos hall) {
        Msg msg = Msg.of(
                "hycolony.citizen.noArrivalSpace",
                String.valueOf(hall.x()),
                String.valueOf(hall.y()),
                String.valueOf(hall.z()));
        Set<UUID> to = new LinkedHashSet<>();
        to.add(colony.permissions().owner());
        to.addAll(colony.permissions().members().keySet());
        for (UUID player : to) {
            if (colony.permissions().hasPermission(player, Action.RECEIVE_MESSAGES)
                    && colony.context().players().isOnline(player)) {
                colony.context().notifier().send(player, msg);
            }
        }
    }
}
