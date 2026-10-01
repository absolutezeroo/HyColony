package dev.hycolony.core.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.FakeNotifier;
import dev.hycolony.core.testing.TestContexts;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Where a citizen's body appears near a spot (MC EntityUtils.getSpawnPoint, BlockPosUtil.findAround). */
class CitizenArrivalTest {
    private final TestContexts t = new TestContexts();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final UUID owner = UUID.randomUUID();
    private final Colony colony = colony();
    private final CitizenData data = new CitizenData(1);

    private Colony colony() {
        TerritoryIndex territory = new TerritoryIndex();
        territory.claimSquare(1, ClaimCell.of(hall), 4);
        Colony c = new Colony(
                t.context(), territory, new Colony.Founding(1, "Test", hall, Permissions.createDefault(owner, "A")));
        c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, hall, 0));
        return c;
    }

    private Vec3 spawnedAt(BlockPos near) {
        BodyId body = CitizenArrival.spawn(colony, data, near).orElseThrow();
        return t.bodies.position(body).orElseThrow();
    }

    @Test
    void columnsAreTheSpotItsFourNeighboursThenMcsRings() {
        List<BlockPos> columns = CitizenArrival.columns(new BlockPos(10, 64, 10));
        // MC findAround: the start, Direction.Plane.HORIZONTAL (north, east, south, west), then ring 1 from (-1, -1).
        assertEquals(
                List.of(
                        new BlockPos(10, 64, 10),
                        new BlockPos(10, 64, 9),
                        new BlockPos(11, 64, 10),
                        new BlockPos(10, 64, 11),
                        new BlockPos(9, 64, 10),
                        new BlockPos(11, 64, 9),
                        new BlockPos(11, 64, 11),
                        new BlockPos(9, 64, 11),
                        new BlockPos(9, 64, 9)),
                columns.subList(0, 9));
        assertEquals(columns.size(), new HashSet<>(columns).size(), "each column is tried once");
        // MC's rings walk [-steps, 1] around the start, out to SCAN_RADIUS.
        assertTrue(columns.contains(new BlockPos(5, 64, 5)));
        assertFalse(columns.contains(new BlockPos(12, 64, 10)));
    }

    @Test
    void aHutsOwnColumnIsSkippedSoTheBodyAppearsNorthOfIt() {
        // MC checkValidSpawn refuses the hut's cell (the hut block has a collision), then tries north first.
        assertEquals(Vec3.center(hall.offset(0, 0, -1)), spawnedAt(hall));
    }

    @Test
    void aSpotThatIsNoBuildingIsTriedFirst() {
        BlockPos last = new BlockPos(40, 64, 40);
        assertEquals(Vec3.center(last), spawnedAt(last));
    }

    @Test
    void aColumnWithoutRoomPassesToTheNextOne() {
        t.bodies.refuseSpawnAt.add(hall.offset(0, 0, -1));
        assertEquals(Vec3.center(hall.offset(1, 0, 0)), spawnedAt(hall));
    }

    @Test
    void noSpaceReachesTheOnlinePlayersAllowedColonyMessages() {
        UUID officer = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        UUID awayOfficer = UUID.randomUUID();
        colony.permissions().addPlayer(officer, "O", Permissions.OFFICER);
        colony.permissions().addPlayer(friend, "F", Permissions.FRIEND);
        colony.permissions().addPlayer(awayOfficer, "W", Permissions.OFFICER);
        t.players.online.put(owner, hall);
        t.players.online.put(officer, hall);
        t.players.online.put(friend, hall);

        CitizenArrival.tellNoSpace(colony, hall);

        assertEquals(
                List.of(owner, officer),
                t.notifier.sent.stream().map(FakeNotifier.Sent::player).toList());
        assertEquals(
                new Msg("hycolony.citizen.noArrivalSpace", List.of("0", "64", "0")),
                t.notifier.sent.getFirst().msg());
    }
}
