package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.building.ModuleProducer;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.BlockUse;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.ui.FoundColonyView;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.persist.ColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ColonyManagerTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = new ColonyManager(t.context());
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private static final String TOWN_HALL = BuildingTypes.TOWN_HALL.id();

    private Colony found(UUID owner, String name, BlockPos pos) {
        manager.foundation().begin(owner, "Owner", pos, 0);
        return manager.foundation().confirm(owner, name).orElseThrow();
    }

    @Test
    void townHallOutsideColoniesStartsFoundationFlow() {
        assertInstanceOf(HutPlacement.FoundNewColony.class, manager.huts().checkPlacement(alice, hall, TOWN_HALL));
        manager.foundation().begin(alice, "Alice", hall, 0);
        assertInstanceOf(FoundColonyView.class, t.ui.shown.get(alice));
        assertEquals("Alice's Colony", ((FoundColonyView) t.ui.shown.get(alice)).suggestedName());
        Colony c = manager.foundation().confirm(alice, "Rivendell").orElseThrow();
        assertEquals("Rivendell", c.name());
        assertEquals(81, manager.territory().claimedCount(c.id()));
        assertTrue(c.buildings().townHall().isPresent());
        assertEquals(alice, c.permissions().owner());
        assertEquals("colonyCreated", c.log().entries().getFirst().type());
    }

    @Test
    void otherHutOutsideColonyIsDenied() {
        manager.huts().checkPlacement(alice, hall, TOWN_HALL);
        HutPlacement p = manager.huts().checkPlacement(alice, hall, "test:other");
        assertEquals(
                "hycolony.hut.noTownHall", ((HutPlacement.Denied) p).reason().key());
        found(alice, "A", hall);
        HutPlacement far = manager.huts().checkPlacement(alice, new BlockPos(5000, 64, 0), "test:other");
        assertEquals("hycolony.hut.tooFar", ((HutPlacement.Denied) far).reason().key());
    }

    @Test
    void onePlayerOwnsOneColony() {
        found(alice, "A", hall);
        HutPlacement p = manager.huts().checkPlacement(alice, new BlockPos(5000, 64, 0), TOWN_HALL);
        assertEquals(
                "hycolony.colony.alreadyOwner",
                ((HutPlacement.Denied) p).reason().key());
    }

    @Test
    void newColonyTooCloseIsDenied() {
        found(alice, "A", hall);
        HutPlacement p = manager.huts().checkPlacement(bob, new BlockPos(16 * 16, 64, 0), TOWN_HALL);
        assertEquals(
                "hycolony.colony.tooClose", ((HutPlacement.Denied) p).reason().key());
        assertInstanceOf(
                HutPlacement.FoundNewColony.class,
                manager.huts().checkPlacement(bob, new BlockPos(17 * 16, 64, 0), TOWN_HALL));
    }

    @Test
    void insideColonyNeedsPlaceHutsAndOneTownHall() {
        Colony c = found(alice, "A", hall);
        HutPlacement strangers = manager.huts().checkPlacement(bob, hall.offset(5, 0, 5), TOWN_HALL);
        assertEquals(
                "hycolony.permission.placeHuts",
                ((HutPlacement.Denied) strangers).reason().key());
        HutPlacement second = manager.huts().checkPlacement(alice, hall.offset(5, 0, 5), TOWN_HALL);
        assertEquals(
                "hycolony.hut.townHallExists",
                ((HutPlacement.Denied) second).reason().key());
        manager.huts().onRemoved(hall);
        assertTrue(c.buildings().townHall().isEmpty());
        assertTrue(manager.byId(c.id()).isPresent()); // colony persists
        assertInstanceOf(
                HutPlacement.Allowed.class, manager.huts().checkPlacement(alice, hall.offset(5, 0, 5), TOWN_HALL));
    }

    @Test
    void placeHutOverAStaleBuildingReplacesItInsteadOfThrowing() {
        Colony c = found(alice, "A", hall);
        manager.huts().place(c, TOWN_HALL, hall, 2); // the core still holds the hall: its block vanished unseen
        assertEquals(1, c.buildings().all().size());
        assertEquals(2, c.buildings().at(hall).orElseThrow().rotation());
        assertEquals(
                "buildingRemoved",
                c.log().entries().get(c.log().entries().size() - 2).type());
    }

    @Test
    void rejectsBlankOrTooLongName() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        assertTrue(manager.foundation().confirm(alice, "   ").isEmpty());
        assertTrue(manager.foundation().confirm(alice, "x".repeat(33)).isEmpty());
        assertEquals(
                "hycolony.colony.invalidName", t.notifier.sent.getLast().msg().key());
        assertTrue(manager.foundation().confirm(alice, "  Ok  ").isPresent());
        assertEquals("Ok", manager.ownedBy(alice).orElseThrow().name());
    }

    @Test
    void cancelReturnsPositionAndPlayerLeavingCancels() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        assertEquals(hall, manager.foundation().cancel(alice).orElseThrow());
        assertTrue(manager.all().isEmpty());
    }

    @Test
    void cancelSurvivesUiCloseReenteringCancel() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        t.ui.onClose = p -> manager.foundation().cancel(p); // Esc: close -> onDismiss -> cancel again
        assertEquals(hall, manager.foundation().cancel(alice).orElseThrow());
        assertTrue(manager.foundation().pendingPositionOf(alice).isEmpty());
    }

    @Test
    void breakingAnotherPlayersPendingTownHallCancelsTheirFoundation() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        assertEquals(alice, manager.foundation().cancelAt(hall).orElseThrow());
        assertTrue(manager.foundation().pendingPositionOf(alice).isEmpty());
        assertFalse(t.ui.shown.containsKey(alice));
        assertTrue(manager.foundation().confirm(alice, "Late").isEmpty());
    }

    @Test
    void breakingElsewhereKeepsYourPendingFoundation() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        assertTrue(manager.foundation().cancelAt(hall.offset(1, 0, 0)).isEmpty());
        assertEquals(hall, manager.foundation().pendingPositionOf(alice).orElseThrow());
        assertTrue(t.ui.shown.containsKey(alice));
    }

    @Test
    void confirmOnSpotThatBecameInvalidDropsPendingAndClosesUi() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        found(bob, "B", new BlockPos(16 * 16, 64, 0)); // too close to alice's spot
        assertTrue(manager.foundation().confirm(alice, "A").isEmpty());
        assertTrue(manager.foundation().pendingPositionOf(alice).isEmpty());
        assertFalse(t.ui.shown.containsKey(alice));
        assertEquals("hycolony.colony.tooClose", t.notifier.sent.getLast().msg().key());
    }

    @Test
    void playerLeavingCancelsPendingFoundation() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        manager.foundation().cancel(alice); // what the disconnect handler does
        assertTrue(manager.foundation().confirm(alice, "Late").isEmpty());
        assertTrue(manager.all().isEmpty());
    }

    @Test
    void protectionFollowsPermissions() {
        Colony c = found(alice, "A", hall);
        BlockPos inside = hall.offset(3, 0, 3);
        assertTrue(manager.isAllowed(alice, inside, Action.BREAK_BLOCKS));
        assertFalse(manager.isAllowed(bob, inside, Action.BREAK_BLOCKS));
        assertTrue(manager.isAllowed(bob, new BlockPos(9000, 64, 0), Action.BREAK_BLOCKS));
        assertTrue(manager.administration().setRank(alice, c.id(), bob, "Bob", Permissions.OFFICER));
        assertTrue(manager.isAllowed(bob, inside, Action.BREAK_BLOCKS));
        assertFalse(manager.administration()
                .setRank(bob, c.id(), UUID.randomUUID(), "Eve", Permissions.OFFICER)); // no EDIT_PERMISSIONS
    }

    @Test
    void usingABlockInAColonyFollowsTheUsersRank() {
        Colony c = found(alice, "A", hall);
        BlockPos inside = hall.offset(3, 0, 3);
        BlockUse pot = new BlockUse(false, false, false, BlockUse.Held.NOTHING);
        assertEquals(Optional.empty(), refusedUse(alice, inside, pot));
        assertEquals(Optional.of(Action.RIGHTCLICK_BLOCK), refusedUse(bob, inside, pot));
        assertEquals(Optional.empty(), refusedUse(bob, new BlockPos(9000, 64, 0), pot));
        assertTrue(manager.administration().setRank(alice, c.id(), bob, "Bob", Permissions.OFFICER));
        assertEquals(Optional.empty(), refusedUse(bob, inside, pot));
    }

    /** How the plugin asks: the rank's permissions at that spot, everything allowed outside colonies. */
    private Optional<Action> refusedUse(UUID player, BlockPos pos, BlockUse use) {
        return use.refused(action -> manager.isAllowed(player, pos, action), manager.protectionEnabled());
    }

    @Test
    void townHallViewRequiresAccessAndRenameRequiresManager() {
        Colony c = found(alice, "A", hall);
        manager.windows().openTownHall(bob, hall);
        assertEquals(
                "hycolony.permission.denied", t.notifier.sent.getLast().msg().key());
        manager.windows().openTownHall(alice, hall);
        TownHallView view = (TownHallView) t.ui.shown.get(alice);
        assertEquals("A", view.colonyName());
        assertTrue(view.canRename());
        assertFalse(manager.administration().rename(bob, c.id(), "Hacked"));
        assertTrue(manager.administration().rename(alice, c.id(), "Renamed"));
        assertEquals("Renamed", c.name());
    }

    @Test
    void deleteDespawnsCitizensAndFreesTerritory() {
        Colony c = found(alice, "A", hall);
        for (int i = 0; i < 20; i++) {
            c.citizens().onColonyTick();
        }
        assertTrue(t.bodies.aliveCount() > 0);
        manager.deleteColony(c.id());
        assertEquals(0, t.bodies.aliveCount());
        assertTrue(manager.colonyAt(hall).isEmpty());
    }

    @Test
    void bodyOfUnknownColonyIsDespawned() {
        BodyId stray = t.bodies.existing(42, 1, new Vec3(0, 64, 0));
        manager.onBodyLoaded(stray, 42, 1);
        assertFalse(t.bodies.isAlive(stray));
    }

    @Test
    void loadAllWithoutStorageLoadsNothing() {
        manager.persistence().loadAll();
        assertTrue(manager.all().isEmpty());
        assertTrue(manager.persistence().available());
    }

    @Test
    void storageListingFailureRefusesFoundingAndWritesNothing() {
        FailingStorage storage = new FailingStorage();
        storage.failHighestId = true;
        manager.persistence().setStorage(storage, MigrationChain.sp0());
        manager.persistence().loadAll();
        assertFalse(manager.persistence().available());

        HutPlacement placement = manager.huts().checkPlacement(alice, hall, TOWN_HALL);
        assertEquals(
                "hycolony.storage.unavailable",
                ((HutPlacement.Denied) placement).reason().key());

        manager.foundation().begin(alice, "Alice", hall, 0);
        assertTrue(manager.foundation().confirm(alice, "Rivendell").isEmpty());
        assertEquals(
                "hycolony.storage.unavailable", t.notifier.sent.getLast().msg().key());
        assertTrue(storage.saved.isEmpty());
        assertTrue(manager.all().isEmpty());
    }

    @Test
    void saveAllStillSavesOtherColonyAfterOneFailsToSerialize() {
        BuildingType throwing = new BuildingType(
                "test:throwing", "hut.throwing", 1, List.of(new ModuleProducer("boom", ThrowingModule::new)));
        manager.context().buildingTypes().register(throwing);

        Colony a = found(alice, "A", hall); // founded first: saved first in saveAll()
        Colony b = found(bob, "B", new BlockPos(2000, 64, 0));
        manager.huts().place(a, throwing.id(), hall.offset(5, 0, 5), 0);

        FailingStorage storage = new FailingStorage();
        manager.persistence().setStorage(storage, MigrationChain.sp0());
        manager.persistence().saveAll();

        assertTrue(a.isDirty());
        assertFalse(b.isDirty());
        assertTrue(storage.saved.containsKey(b.id()));
        assertFalse(storage.saved.containsKey(a.id()));
    }

    @Test
    void deleteColonyArchivesFirstAndKeepsColonyIfArchivingFails() {
        Colony c = found(alice, "A", hall);
        FailingStorage storage = new FailingStorage();
        storage.failArchive = true;
        manager.persistence().setStorage(storage, MigrationChain.sp0());

        assertFalse(manager.deleteColony(c.id()));
        assertTrue(manager.byId(c.id()).isPresent());
        assertTrue(manager.colonyAt(hall).isPresent());

        storage.failArchive = false;
        assertTrue(manager.deleteColony(c.id()));
        assertTrue(manager.byId(c.id()).isEmpty());
    }

    /** In-memory ColonyStorage double whose calls can be made to fail, for error-handling tests. */
    private static final class FailingStorage implements ColonyStorage {
        boolean failHighestId;
        boolean failArchive;
        final Map<Integer, String> saved = new HashMap<>();

        @Override
        public List<Integer> colonyIds() {
            return List.copyOf(saved.keySet());
        }

        @Override
        public int highestIdEverUsed() throws IOException {
            if (failHighestId) {
                throw new IOException("test failure");
            }
            return saved.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        @Override
        public Optional<JsonObject> load(int id) {
            return Optional.empty();
        }

        @Override
        public void save(int id, String json) {
            saved.put(id, json);
        }

        @Override
        public void backupVersion(int id, int schemaVersion, String json) {}

        @Override
        public void archive(int id) throws IOException {
            if (failArchive) {
                throw new IOException("test failure");
            }
        }
    }

    /** A building module whose write() always throws, to simulate one colony failing to serialize. */
    private static final class ThrowingModule implements PersistentModule {
        @Override
        public void write(JsonObject out) {
            throw new RuntimeException("boom");
        }

        @Override
        public void read(JsonObject in) {}
    }
}
