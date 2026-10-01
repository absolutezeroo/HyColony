package dev.hycolony.core.citizen.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CitizenManager.onCitizenSleep: "All citizens are tucked into bed", once a night. */
class SleepNoticeTest {
    private final TestContexts t = new TestContexts();
    private final UUID owner = UUID.randomUUID();
    private final Colony c = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(owner, "A")));

    private CitizenData citizen(int id, boolean asleep) {
        CitizenData d = new CitizenData(id);
        d.setAsleep(asleep);
        c.citizens().restore(d);
        return d;
    }

    @Test
    void allAsleepNeedsEveryCitizenAsleep() {
        t.players.online.put(owner, new BlockPos(0, 64, 0));
        citizen(1, true);
        CitizenData awake = citizen(2, false);

        SleepNotice.onCitizenSleep(c);
        assertEquals(0, t.notifier.sent.size());

        awake.setAsleep(true);
        SleepNotice.onCitizenSleep(c);
        assertEquals(1, t.notifier.sent.size());
        assertEquals(
                Msg.of("hycolony.citizen.allAsleep"), t.notifier.sent.getFirst().msg());
    }

    @Test
    void hostileMembersHearNothing() {
        UUID hostile = UUID.randomUUID();
        c.permissions().setRank(hostile, "H", Permissions.HOSTILE);
        t.players.online.put(hostile, new BlockPos(0, 64, 0));
        citizen(1, true);

        SleepNotice.onCitizenSleep(c);

        assertTrue(t.notifier.sent.stream().noneMatch(s -> s.player().equals(hostile)));
    }

    @Test
    void announcedOnceToOnlineMembersUntilNightFalls() {
        citizen(1, true);
        SleepNotice.onCitizenSleep(c);
        assertEquals(0, t.notifier.sent.size(), "the owner is offline");

        t.players.online.put(owner, new BlockPos(0, 64, 0));
        SleepNotice.onCitizenSleep(c);
        assertEquals(0, t.notifier.sent.size(), "already announced this night");

        SleepNotice.onNightFall(c);
        SleepNotice.onCitizenSleep(c);
        assertEquals(1, t.notifier.sent.size());
    }
}
