package dev.hycolony.core.app.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.wand.ColonyBorder.Colour;
import dev.hycolony.core.app.wand.ColonyBorder.Line;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.testing.TestContexts;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC ColonyBorderRenderer: the claimed cells' borders drawn around a player holding the build tool. */
class ColonyBorderTest {
    private static final int OWN = 1;
    private static final int OTHER = 2;
    /** A view of 20 cells: cells up to 16 away on each axis are drawn. */
    private static final int VIEW = 20;

    private static final ClaimCell AWAY = new ClaimCell(0, 0);

    private final TerritoryIndex territory = new TerritoryIndex();

    private List<Line> lines(ClaimCell player, int view, boolean teamBorders) {
        return ColonyBorder.lines(territory, OWN, player, view, teamBorders);
    }

    private static Line line(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Line(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2), Colour.WHITE);
    }

    @Test
    void aLoneCellIsBoxedByFourPostsAndARingEvery16Blocks() {
        territory.claimSquare(OWN, new ClaimCell(5, 5), 0);

        List<Line> lines = lines(AWAY, VIEW, true);

        assertEquals(4 + 4 * 19, lines.size(), "4 posts, then 19 rings (16 to 304) on each side");
        assertTrue(lines.contains(line(80, 0, 80, 80, 320, 80)), "the north-west post, the world's full height");
        assertTrue(lines.contains(line(96, 0, 96, 96, 320, 96)), "the south-east post");
        assertTrue(lines.contains(line(80, 16, 80, 96, 16, 80)), "the north side's lowest ring");
        assertTrue(lines.contains(line(96, 304, 80, 96, 304, 96)), "the east side's highest ring");
    }

    @Test
    void twoCellsOfTheSameColonyShareNoLineAndTheirCommonPostIsDrawnOnce() {
        territory.claimSquare(OWN, new ClaimCell(5, 5), 0);
        territory.claimSquare(OWN, new ClaimCell(6, 5), 0);

        List<Line> lines = lines(AWAY, VIEW, true);

        assertFalse(lines.contains(line(96, 16, 80, 96, 16, 96)), "no ring between the two cells");
        assertEquals(1, Collections.frequency(lines, line(96, 0, 80, 96, 320, 80)), "both cells draw that post");
        assertEquals(6 + 6 * 19, lines.size());
    }

    /** MC PLAYER_CHUNK_STEP: in the player's column and row of cells, a post and a ring every 4 blocks. */
    @Test
    void theSidesInThePlayersColumnAndRowAreDrawnEvery4Blocks() {
        territory.claimSquare(OWN, new ClaimCell(5, 5), 0);

        List<Line> lines = lines(new ClaimCell(5, 5), VIEW, true);

        assertTrue(lines.contains(line(84, 0, 80, 84, 320, 80)), "a north post 4 blocks in");
        assertTrue(lines.contains(line(80, 4, 80, 96, 4, 80)), "a north ring 4 blocks up");
        assertTrue(lines.contains(line(80, 316, 96, 96, 316, 96)), "the south's highest ring");
        assertTrue(lines.contains(line(80, 0, 92, 80, 320, 92)), "a west post 12 blocks in");
        assertEquals(4 + 4 * (3 + 79), lines.size());
    }

    @Test
    void onlyTheNearestColonyIsDrawnDenseInThePlayersColumn() {
        territory.claimSquare(OTHER, new ClaimCell(5, 5), 0);

        List<Line> lines = lines(new ClaimCell(5, 5), VIEW, true);

        assertEquals(4 + 4 * 19, lines.size());
    }

    /** MC RENDER_DIST_THRESHOLD: within max(view - 3, 2) cells of the player's, strictly, on each axis. */
    @Test
    void onlyCellsWellWithinTheViewAreDrawn() {
        territory.claimSquare(OWN, new ClaimCell(4, 0), 0);
        territory.claimSquare(OWN, new ClaimCell(-4, 0), 0);
        territory.claimSquare(OWN, new ClaimCell(0, 4), 0);
        territory.claimSquare(OWN, new ClaimCell(0, -4), 0);
        territory.claimSquare(OWN, new ClaimCell(5, 0), 0);
        territory.claimSquare(OWN, new ClaimCell(-5, 0), 0);
        territory.claimSquare(OWN, new ClaimCell(0, 5), 0);
        territory.claimSquare(OWN, new ClaimCell(0, -5), 0);

        List<Line> lines = lines(AWAY, 8, true);

        assertTrue(lines.contains(line(64, 16, 0, 64, 16, 16)), "4 cells east, under 8 - 3");
        assertTrue(lines.contains(line(-48, 16, 0, -48, 16, 16)), "4 cells west, its east side");
        assertTrue(lines.contains(line(0, 16, 64, 16, 16, 64)), "4 cells south");
        assertTrue(lines.contains(line(0, 16, -48, 16, 16, -48)), "4 cells north, its south side");
        assertFalse(lines.contains(line(96, 16, 0, 96, 16, 16)), "5 cells east is out");
        assertFalse(lines.contains(line(-80, 16, 0, -80, 16, 16)), "5 cells west is out");
        assertFalse(lines.contains(line(0, 16, 96, 16, 16, 96)), "5 cells south is out");
        assertFalse(lines.contains(line(0, 16, -80, 16, 16, -80)), "5 cells north is out");
    }

    /** MC: the window keeps 2 cells however small the view, so the cells next to the player's are drawn. */
    @Test
    void aTinyViewStillDrawsTheCellsNextToThePlayers() {
        territory.claimSquare(OWN, new ClaimCell(1, 1), 0);
        territory.claimSquare(OWN, new ClaimCell(2, 0), 0);

        List<Line> lines = lines(AWAY, 1, true);

        assertTrue(lines.contains(line(16, 16, 16, 32, 16, 16)), "a cell next to the player's");
        assertFalse(lines.contains(line(32, 16, 0, 32, 16, 16)), "two cells away is out");
    }

    /** MC draw: a side is a border when its neighbour is another colony's, so both colonies draw their frontier. */
    @Test
    void twoNeighbouringColoniesEachDrawTheirFrontier() {
        territory.claimSquare(OWN, new ClaimCell(5, 5), 0);
        territory.claimSquare(OTHER, new ClaimCell(6, 5), 0);

        List<Line> lines = lines(AWAY, VIEW, false);

        assertTrue(lines.contains(line(96, 16, 80, 96, 16, 96)), "the own colony's east side, white");
        assertTrue(lines.contains(new Line(new BlockPos(96, 16, 80), new BlockPos(96, 16, 96), Colour.RED)));
    }

    @Test
    void withoutTeamBordersOtherColoniesAreRed() {
        territory.claimSquare(OTHER, new ClaimCell(5, 5), 0);
        territory.claimSquare(OWN, new ClaimCell(7, 5), 0);

        List<Line> lines = lines(AWAY, VIEW, false);

        assertTrue(lines.contains(new Line(new BlockPos(80, 0, 80), new BlockPos(80, 320, 80), Colour.RED)));
        assertTrue(lines.contains(line(112, 0, 80, 112, 320, 80)), "the nearest colony stays white");
    }

    /** MC: a colony's team colour, white by default; HyColony has no team colours yet. */
    @Test
    void withTeamBordersEveryColonyIsWhite() {
        territory.claimSquare(OTHER, new ClaimCell(5, 5), 0);

        assertTrue(lines(AWAY, VIEW, true).stream().allMatch(l -> l.colour() == Colour.WHITE));
    }

    /** MC getClosestColonyView: the colony owning the player's cell, else the one whose centre is nearest in 2D. */
    @Test
    void theNearestColonyOwnsThePlayersCellElseHasTheNearestCentre() {
        ColonyManager manager = new TestContexts().manager();
        Colony west = found(manager, UUID.randomUUID(), new BlockPos(0, 64, 0));
        Colony east = found(manager, UUID.randomUUID(), new BlockPos(2000, 2000, 0)); // high up: 3D would flip it
        BlockPos outpost = new BlockPos(1900, 64, 0);
        manager.territory().claimSquare(west.id(), ClaimCell.of(outpost), 0);

        assertEquals(Optional.of(west), ColonyBorder.nearest(manager, new BlockPos(900, 64, 0)));
        assertEquals(Optional.of(east), ColonyBorder.nearest(manager, new BlockPos(1050, 64, 0)), "in 2D");
        assertEquals(Optional.of(west), ColonyBorder.nearest(manager, outpost), "its cell, though nearer east");
        assertTrue(ColonyBorder.nearest(new TestContexts().manager(), new BlockPos(0, 64, 0))
                .isEmpty());
    }

    /** Client.ColonyTeamBorders off: the other colonies are red (MC colonyteamborders). */
    @Test
    void theTeamBordersSettingIsReadFromTheConfig() {
        TestContexts t = new TestContexts();
        ColonyConfig d = ColonyConfig.defaults();
        t.config = new ColonyConfig(
                d.gameplay(),
                d.claims(),
                d.permissions(),
                d.commands(),
                new ColonyConfig.Client(50, false),
                d.hycolony(),
                d.structurize());
        ColonyManager manager = t.manager();
        manager.territory().claimSquare(OTHER, new ClaimCell(5, 5), 0);

        List<Line> lines = ColonyBorder.lines(manager, OWN, AWAY, VIEW);

        assertTrue(lines.stream().allMatch(l -> l.colour() == Colour.RED));
        assertFalse(lines.isEmpty());
    }

    private static Colony found(ColonyManager manager, UUID owner, BlockPos hall) {
        manager.foundation().begin(owner, "O", hall, 0);
        return manager.foundation().confirm(owner, "C" + hall.x()).orElseThrow();
    }
}
