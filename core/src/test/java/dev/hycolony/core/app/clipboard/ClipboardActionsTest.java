package dev.hycolony.core.app.clipboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC ItemClipboard and WindowClipBoard: the clipboard notes a colony from one of its huts, then shows its requests. */
class ClipboardActionsTest {
    private static final BlockPos HUT = new BlockPos(30, 64, 0);
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final ClipboardActions clipboard = new ClipboardActions(manager);
    private final Colony colony;
    private final Building hut;

    ClipboardActionsTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "Avalon").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, alice);
        hut = colony.buildings().at(HUT).orElseThrow();
    }

    @Test
    void usedOnAHutItNotesTheColonyAndSaysSo() {
        assertEquals(Optional.of(colony.id()), clipboard.register(alice, HUT));
        assertEquals(
                "hycolony.clipboard.registered", t.notifier.sent.getLast().msg().key());
        assertEquals("Avalon", t.notifier.sent.getLast().msg().params().getFirst());
    }

    @Test
    void usedElsewhereItNotesNothing() {
        int before = t.notifier.sent.size();
        assertEquals(Optional.empty(), clipboard.register(alice, new BlockPos(5, 64, 5)));
        assertEquals(before, t.notifier.sent.size());
    }

    @Test
    void withoutAColonyItSaysToNoteOne() {
        clipboard.open(alice, Optional.empty(), false);
        assertEquals(
                "hycolony.clipboard.needcolony", t.notifier.sent.getLast().msg().key());
        assertFalse(t.ui.shown.containsKey(alice));
    }

    @Test
    void theImportantButtonHidesOnlyTheJobsAsyncRequestsAsMcDoes() {
        RequestToken materials = colony.requests()
                .createAndAssign(hut, new StackRequest(new ItemKey("plank"), 3, 3, true), Request.NO_CITIZEN);
        RequestToken async = colony.requests().createAsync(hut, new StackRequest(new ItemKey("seed"), 4, 1, true));

        clipboard.open(alice, Optional.of(colony.id()), false);
        RequestsView hidden = (RequestsView) t.ui.shown.get(alice);
        assertFalse(hidden.showImportant());
        assertTrue(hidden.rows().stream().anyMatch(r -> r.token().equals(materials)), "MC shows the hut's own");
        assertTrue(hidden.rows().stream().noneMatch(r -> r.token().equals(async)), "MC: async requests hidden");

        clipboard.open(alice, Optional.of(colony.id()), true);
        RequestsView all = (RequestsView) t.ui.shown.get(alice);
        assertTrue(all.showImportant());
        assertTrue(all.rows().stream().anyMatch(r -> r.token().equals(async)));
    }
}
