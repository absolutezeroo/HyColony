package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC CommandDeleteColony (IMCColonyOfficerCommand): an operator or a manager of the colony deletes it. */
class DeleteColonyTest {
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final Colony colony;

    DeleteColonyTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
    }

    @Test
    void playerWhoIsNeitherOperatorNorColonyManagerCannotDeleteIt() {
        assertFalse(manager.administration().delete(bob, colony.id()));
        assertTrue(manager.byId(colony.id()).isPresent());

        assertTrue(manager.administration().setRank(alice, colony.id(), bob, "Bob", Permissions.FRIEND));
        assertFalse(manager.administration().delete(bob, colony.id()));
        assertTrue(manager.byId(colony.id()).isPresent());
    }

    @Test
    void officerDeletesTheColony() {
        assertTrue(manager.administration().setRank(alice, colony.id(), bob, "Bob", Permissions.OFFICER));
        List<ColonyEvents.ColonyDeleted> deleted = t.heard(ColonyEvents.ColonyDeleted.class);

        assertTrue(manager.administration().delete(bob, colony.id()));
        assertEquals(Optional.of(bob), deleted.getFirst().player(), "deleted by the officer");
        assertTrue(manager.byId(colony.id()).isEmpty());
    }

    @Test
    void operatorDeletesAnyColony() {
        t.players.operators.add(bob);

        assertTrue(manager.administration().delete(bob, colony.id()));
        assertTrue(manager.byId(colony.id()).isEmpty());
    }

    @Test
    void unknownColonyIsNotDeleted() {
        t.players.operators.add(bob);

        assertFalse(manager.administration().delete(bob, 99));
    }
}
