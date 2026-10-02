package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * MC ColonyBorderRenderer: the borders of the claimed cells around a player holding the build tool, as lines, and the
 * colony they are drawn for.
 */
public final class ColonyBorder {
    /** Deviation from MC: its CHUNK_HEIGHT 256 is Minecraft's world height; Hytale's is 320 (ChunkUtil.HEIGHT). */
    static final int HEIGHT_BLOCKS = 320;
    /** MC RENDER_DIST_THRESHOLD, in cells: the drawn window is that much smaller than the view. */
    private static final int RENDER_DIST_THRESHOLD = 3;
    /** MC: the drawn window keeps at least that many cells. */
    private static final int MIN_RENDER_DIST = 2;
    /** MC PLAYER_CHUNK_STEP, in blocks: the step of the posts and rings in the player's column and row of cells. */
    private static final int PLAYER_CELL_STEP = ClaimCell.SIZE / 4;

    /** A line's colour: MC's white, and its red (255, 70, 70) for the other colonies. */
    public enum Colour {
        WHITE,
        RED
    }

    /** A straight line from {@code from} to {@code to}, in block corners. */
    public record Line(BlockPos from, BlockPos to, Colour colour) {}

    private final TerritoryIndex territory;
    private final int nearestId;
    private final ClaimCell player;
    private final boolean teamBorders;
    /** A line two neighbouring cells both draw is kept once: the same picture as MC, with half the shapes. */
    private final Set<Line> out = new LinkedHashSet<>();

    private ColonyBorder(TerritoryIndex territory, int nearestId, ClaimCell player, boolean teamBorders) {
        this.territory = territory;
        this.nearestId = nearestId;
        this.player = player;
        this.teamBorders = teamBorders;
    }

    /**
     * MC ColonyManager.getClosestColonyView: the colony owning {@code pos}'s cell, else the one whose centre is
     * nearest in 2D; empty without colonies.
     */
    public static Optional<Colony> nearest(ColonyManager manager, BlockPos pos) {
        Optional<Colony> owner = manager.colonyAt(pos);
        if (owner.isPresent()) {
            return owner;
        }
        return manager.all().stream().min(Comparator.comparingLong(c -> distanceSquared2d(c.center(), pos)));
    }

    /**
     * The borders to draw for a player in cell {@code player} who sees {@code viewCells} cells away, drawn for colony
     * {@code nearestId} (MC ColonyBorderRenderer.render then draw), coloured as config Client.ColonyTeamBorders says.
     */
    public static List<Line> lines(ColonyManager manager, int nearestId, ClaimCell player, int viewCells) {
        boolean teamBorders = manager.context().config().client().colonyTeamBorders();
        return lines(manager.territory(), nearestId, player, viewCells, teamBorders);
    }

    /** {@link #lines(ColonyManager, int, ClaimCell, int)} over {@code territory}. */
    static List<Line> lines(
            TerritoryIndex territory, int nearestId, ClaimCell player, int viewCells, boolean teamBorders) {
        ColonyBorder border = new ColonyBorder(territory, nearestId, player, teamBorders);
        int window = Math.max(viewCells - RENDER_DIST_THRESHOLD, MIN_RENDER_DIST);
        for (int x = player.x() - window + 1; x < player.x() + window; x++) {
            for (int z = player.z() - window + 1; z < player.z() + window; z++) {
                ClaimCell cell = new ClaimCell(x, z);
                OptionalInt owner = territory.colonyAt(cell);
                if (owner.isPresent()) {
                    border.drawCell(cell, owner.getAsInt());
                }
            }
        }
        return List.copyOf(border.out);
    }

    /** MC draw for one claimed cell: each side whose neighbour is not the same colony's is a border. */
    private void drawCell(ClaimCell cell, int colonyId) {
        // Deviation from MC: with team borders MC takes the colony's team colour, white by default; HyColony has no
        // team colours yet, so white.
        Colour colour = teamBorders || colonyId == nearestId ? Colour.WHITE : Colour.RED;
        boolean playerColumn = colonyId == nearestId && cell.x() == player.x();
        boolean playerRow = colonyId == nearestId && cell.z() == player.z();
        BlockPos nw = new BlockPos(cell.x() * ClaimCell.SIZE, 0, cell.z() * ClaimCell.SIZE);
        BlockPos ne = nw.offset(ClaimCell.SIZE, 0, 0);
        BlockPos sw = nw.offset(0, 0, ClaimCell.SIZE);
        BlockPos se = nw.offset(ClaimCell.SIZE, 0, ClaimCell.SIZE);
        if (!owns(cell.x(), cell.z() - 1, colonyId)) {
            side(nw, ne, playerColumn, colour);
        }
        if (!owns(cell.x(), cell.z() + 1, colonyId)) {
            side(sw, se, playerColumn, colour);
        }
        if (!owns(cell.x() - 1, cell.z(), colonyId)) {
            side(nw, sw, playerRow, colour);
        }
        if (!owns(cell.x() + 1, cell.z(), colonyId)) {
            side(ne, se, playerRow, colour);
        }
    }

    /**
     * One border side from {@code a} to {@code b} (at y 0): a post at both ends (MC draws a corner's post when either
     * of its sides is a border), and a ring every 16 blocks up, or, when {@code dense}, a ring and a post every 4.
     */
    private void side(BlockPos a, BlockPos b, boolean dense, Colour colour) {
        post(a.x(), a.z(), colour);
        post(b.x(), b.z(), colour);
        int step = dense ? PLAYER_CELL_STEP : ClaimCell.SIZE;
        if (dense) {
            int dx = Integer.signum(b.x() - a.x());
            int dz = Integer.signum(b.z() - a.z());
            for (int shift = step; shift < ClaimCell.SIZE; shift += step) {
                post(a.x() + dx * shift, a.z() + dz * shift, colour);
            }
        }
        for (int y = step; y < HEIGHT_BLOCKS; y += step) {
            out.add(new Line(a.offset(0, y, 0), b.offset(0, y, 0), colour));
        }
    }

    /** A post the world's full height at block corner ({@code x}, {@code z}). */
    private void post(int x, int z, Colour colour) {
        out.add(new Line(new BlockPos(x, 0, z), new BlockPos(x, HEIGHT_BLOCKS, z), colour));
    }

    private boolean owns(int x, int z, int colonyId) {
        OptionalInt owner = territory.colonyAt(new ClaimCell(x, z));
        return owner.isPresent() && owner.getAsInt() == colonyId;
    }

    private static long distanceSquared2d(BlockPos a, BlockPos b) {
        long dx = (long) a.x() - b.x();
        long dz = (long) a.z() - b.z();
        return dx * dx + dz * dz;
    }
}
