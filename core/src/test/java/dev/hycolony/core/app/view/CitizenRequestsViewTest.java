package dev.hycolony.core.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.core.app.ui.RequestsView.RequestRow;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC RequestWindowCitizen: the citizen's requests in its workplace, then the workplace's own, as a tree. */
class CitizenRequestsViewTest {
    private static final BlockPos HUT = new BlockPos(30, 64, 0);
    private static final ItemKey PLANK = new ItemKey("plank_item");
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony;
    private final Building hut;
    private final CitizenData bob;

    CitizenRequestsViewTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, alice);
        hut = colony.buildings().at(HUT).orElseThrow();
        hut.setLevel(1);
        hut.setBuilt(true);
        bob = new CitizenData(1);
        bob.setName("Bob");
        colony.citizens().restore(bob);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, bob));
    }

    private List<RequestRow> rows() {
        manager.windows().openCitizen(alice, colony.id(), bob.id());
        return ((CitizenView) t.ui.shown.get(alice)).requests();
    }

    private RequestToken ask(int citizenId, int count) {
        return colony.requests().createAndAssign(hut, new StackRequest(PLANK, count, count, true), citizenId);
    }

    @Test
    void theCitizensRequestsComeFirstThenTheHutsOwnAsMc() {
        RequestToken hutOwn = ask(Request.NO_CITIZEN, 2);
        RequestToken mine = ask(bob.id(), 3);
        CitizenData ann = new CitizenData(2);
        colony.citizens().restore(ann);
        ask(ann.id(), 4); // another citizen's: not listed

        assertEquals(
                List.of(mine, hutOwn), rows().stream().map(RequestRow::token).toList());
    }

    @Test
    void aRowNamesWhereItsRequesterIsAndWhoResolvesIt() {
        ask(bob.id(), 3);
        RequestRow row = rows().getFirst();
        assertEquals(Optional.of(HUT), row.requesterPos());
        assertEquals(Optional.of("Player"), row.resolver(), "MC StandardPlayerRequestResolver: \"Player\"");
        assertTrue(row.cancellable(), "MC isCancellable: a root of the tree");
    }

    @Test
    void aChildIsFulfillableOnlyWhenItsRequesterStandsAtTheWorkplaceAsMc() {
        RequestToken parent = ask(bob.id(), 3);
        RequestToken here =
                colony.requests().createChild(hut.resolvers().getFirst(), parent, new StackRequest(PLANK, 2, 2, true));
        BlockPos storePos = new BlockPos(0, 64, 30);
        manager.huts().place(colony, WarehouseBuilding.TYPE_ID, storePos, 0, alice);
        Building store = colony.buildings().at(storePos).orElseThrow();
        RequestToken away = colony.requests()
                .createChild(store.resolvers().getFirst(), parent, new StackRequest(PLANK, 1, 1, true));
        t.playerInventory.give(alice, new ItemAmount(PLANK, 5));

        List<RequestRow> rows = rows();
        RequestRow atHut =
                rows.stream().filter(r -> r.token().equals(here)).findFirst().orElseThrow();
        RequestRow elsewhere =
                rows.stream().filter(r -> r.token().equals(away)).findFirst().orElseThrow();
        assertTrue(atHut.fulfillable(), "MC: its requester's location is the hut's");
        assertFalse(elsewhere.fulfillable(), "MC: a child asked elsewhere is not the citizen's to receive");
        assertFalse(atHut.cancellable(), "MC isCancellable: roots only");
    }

    @Test
    void fulfillIsOfferedWhenThePlayerHoldsTheItemAsMcIsFulfillable() {
        ask(bob.id(), 3);
        assertFalse(rows().getFirst().fulfillable(), "nothing to hand over");
        t.playerInventory.give(alice, new ItemAmount(PLANK, 1));
        assertTrue(rows().getFirst().fulfillable());
    }
}
