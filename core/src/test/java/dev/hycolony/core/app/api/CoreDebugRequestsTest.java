package dev.hycolony.core.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.api.ActionResult;
import dev.hycolony.api.Actor;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The api's fulfilRequest and resetRequests (MC's request window Fulfill, /mc colony requestsystem-reset). */
class CoreDebugRequestsTest {
    private static final BlockPos HALL = new BlockPos(0, 64, 0);
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private static final Actor PLUGIN = new Actor.Plugin("Tests:HyLens");

    /** One colony with its town hall and citizen 1, its players allowed to reset the request system or not. */
    private record Fixture(TestContexts t, DebugAccess debug, Colony colony, ColonyRef ref, UUID owner) {
        static Fixture of(boolean playersMayReset) {
            TestContexts t = new TestContexts();
            ColonyConfig d = ColonyConfig.defaults();
            t.config = new ColonyConfig(
                    d.gameplay(),
                    d.claims(),
                    d.permissions(),
                    new ColonyConfig.Commands(true, true, false, false, playersMayReset),
                    d.client(),
                    d.hycolony(),
                    d.structurize());
            ColonyManager manager = t.manager();
            UUID owner = UUID.randomUUID();
            manager.foundation().begin(owner, "Owner", HALL, 0);
            Colony colony = manager.foundation().confirm(owner, "Rivendell").orElseThrow();
            colony.citizens().restore(new CitizenData(1));
            return new Fixture(
                    t,
                    new CoreColonyWorld(manager, () -> true).debug(),
                    colony,
                    new ColonyRef("world", colony.id()),
                    owner);
        }
    }

    private final Fixture f = Fixture.of(false);
    private final TestContexts t = f.t();
    private final DebugAccess debug = f.debug();
    private final UUID owner = f.owner();
    private final Colony colony = f.colony();
    private final ColonyRef ref = f.ref();
    private final Building hut = colony.buildings().at(HALL).orElseThrow();
    private final CitizenData citizen = colony.citizens().get(1).orElseThrow();

    private RequestToken request() {
        return colony.requests().createAndAssign(hut, new StackRequest(PLANKS, 5, 5, true), citizen.id());
    }

    private static String id(RequestToken token) {
        return token.id().toString();
    }

    @Test
    void aPluginFulfilsForFreeAsMcsConsole() {
        RequestToken token = request();

        assertEquals(new ActionResult.Done(), debug.fulfilRequest(PLUGIN, ref, id(token)));

        assertEquals(5, citizen.inventory().count(PLANKS));
        assertEquals(
                RequestState.COMPLETED,
                colony.requests().get(token).orElseThrow().state());
    }

    @Test
    void aPlayerInCreativeFulfilsForFreeOneInSurvivalFromTheirInventory() {
        RequestToken free = request();
        t.players.creative.add(owner);
        assertEquals(new ActionResult.Done(), debug.fulfilRequest(new Actor.Player(owner), ref, id(free)));

        t.players.creative.remove(owner);
        RequestToken paid = request();
        t.playerInventory.give(owner, new ItemAmount(PLANKS, 2));
        assertEquals(new ActionResult.Done(), debug.fulfilRequest(new Actor.Player(owner), ref, id(paid)));
        assertEquals(0, t.playerInventory.count(owner, PLANKS), "2 of the 5 asked, from the inventory");
    }

    @Test
    void nothingToHandOverIsUnavailable() {
        RequestToken token = request();

        assertEquals(new ActionResult.Unavailable(), debug.fulfilRequest(new Actor.Player(owner), ref, id(token)));
        RequestToken tool = colony.requests().createAndAssign(hut, new ToolRequest(ToolType.SHOVEL, 0, 3), 1);
        assertEquals(new ActionResult.Unavailable(), debug.fulfilRequest(PLUGIN, ref, id(tool)), "no tool to show");
    }

    @Test
    void aPlayerWithoutManageHutsIsRefusedAndTheColonyToo() {
        RequestToken token = request();

        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.permission.denied", "Rivendell")),
                debug.fulfilRequest(new Actor.Player(UUID.randomUUID()), ref, id(token)));
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.colony")),
                debug.fulfilRequest(new Actor.Colony(), ref, id(token)));
    }

    @Test
    void anUnknownMalformedOrClosedRequestIsNotFound() {
        RequestToken token = request();
        debug.fulfilRequest(PLUGIN, ref, id(token));

        assertEquals(new ActionResult.NotFound(), debug.fulfilRequest(PLUGIN, ref, id(token)), "closed");
        assertEquals(
                new ActionResult.NotFound(),
                debug.fulfilRequest(PLUGIN, ref, UUID.randomUUID().toString()));
        assertEquals(new ActionResult.NotFound(), debug.fulfilRequest(PLUGIN, ref, "not-a-uuid"));
        assertEquals(new ActionResult.NotFound(), debug.fulfilRequest(PLUGIN, new ColonyRef("world", 99), id(token)));
    }

    @Test
    void anOperatorResetsTheRequestSystem() {
        UUID op = UUID.randomUUID();
        t.players.operators.add(op);
        request();

        assertEquals(new ActionResult.Done(), debug.resetRequests(new Actor.Player(op), ref));

        assertTrue(colony.requests().all().isEmpty());
    }

    @Test
    void aPlayerWhoIsNotAnOperatorNeedsTheServerSettingEvenTheOwnerAsMc() {
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.config")),
                debug.resetRequests(new Actor.Player(owner), ref));

        Fixture allowed = Fixture.of(true);
        assertEquals(
                new ActionResult.Done(),
                allowed.debug().resetRequests(new Actor.Player(UUID.randomUUID()), allowed.ref()),
                "MC checks no membership: any player, once the server allows it");
    }

    @Test
    void aPluginResetsTheColonyMayNotAndAnUnknownColonyIsNotFound() {
        assertEquals(new ActionResult.Done(), debug.resetRequests(PLUGIN, ref));
        assertEquals(
                new ActionResult.Refused(ApiText.of("hycolony.debug.refused.colony")),
                debug.resetRequests(new Actor.Colony(), ref));
        assertEquals(new ActionResult.NotFound(), debug.resetRequests(PLUGIN, new ColonyRef("world", 99)));
    }
}
