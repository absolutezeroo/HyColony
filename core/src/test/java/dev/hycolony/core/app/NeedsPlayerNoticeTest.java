package dev.hycolony.core.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.app.ui.NeedsPlayerNotice;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.FakeUi;
import dev.hycolony.core.testing.TestContexts;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NeedsPlayerNoticeTest {
    private static final StackRequest PLANKS = new StackRequest(new ItemKey("Wood_Planks"), 4, 4, true);
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID(); // owner
    private final UUID bob = UUID.randomUUID(); // officer
    private final UUID carol = UUID.randomUUID(); // friend
    private final UUID dave = UUID.randomUUID(); // officer, offline
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final Colony colony;
    private final Building hut;

    NeedsPlayerNoticeTest() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        hut = colony.buildings().at(hall).orElseThrow();
        CitizenData citizen = new CitizenData(1);
        citizen.setName("Jean");
        colony.citizens().restore(citizen);
        colony.permissions().setRank(bob, "Bob", Permissions.OFFICER);
        colony.permissions().setRank(carol, "Carol", Permissions.FRIEND);
        colony.permissions().setRank(dave, "Dave", Permissions.OFFICER);
        for (UUID p : List.of(alice, bob, carol)) {
            t.players.online.put(p, hall);
        }
    }

    private void toPlayer(RequestToken token) {
        colony.requests().reassign(token, Set.of(RetryingResolver.ID));
        assertEquals(
                PlayerResolver.ID,
                colony.requests().resolverOf(token).orElseThrow().resolverId());
    }

    @Test
    void requestReachingThePlayerIsAnnouncedOnceToOnlineOfficersAndOwner() {
        RequestToken token = colony.requests().createAndAssign(hut, PLANKS, 1);
        assertEquals(List.of(), t.ui.notices, "the retrying resolver holds it: nobody is told yet");

        toPlayer(token);
        NeedsPlayerNotice n = new NeedsPlayerNotice("Jean", "", PLANKS);
        assertEquals(List.of(new FakeUi.Notice(alice, n), new FakeUi.Notice(bob, n)), t.ui.notices);

        colony.requests().reassign(token, Set.of(PlayerResolver.ID)); // retried...
        toPlayer(token); // ...and back to the player
        assertEquals(2, t.ui.notices.size(), "announced once per request");
    }

    @Test
    void requestAlreadyWithThePlayerIsNotAnnouncedAgainWhenTheColonyLoads(@TempDir Path dir) {
        manager.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp4());
        toPlayer(colony.requests().createAndAssign(hut, PLANKS, 1));
        colony.markDirty();
        manager.persistence().saveAll();
        TestContexts loading = new TestContexts();
        loading.players.online.put(alice, hall);
        ColonyManager reloaded = loading.manager();
        reloaded.persistence().setStorage(new FileColonyStorage(dir), MigrationChain.sp4());

        reloaded.persistence().loadAll();

        assertEquals(
                1, reloaded.byId(colony.id()).orElseThrow().requests().all().size());
        assertEquals(List.of(), loading.ui.notices, "its officers were told before the save");
    }

    @Test
    void buildingRequestIsAnnouncedWithTheBuildingName() {
        toPlayer(colony.requests().createAndAssign(hut, PLANKS, -1));
        assertEquals(
                new NeedsPlayerNotice(hut.displayName(), "", PLANKS),
                t.ui.notices.get(0).notice());
    }
}
