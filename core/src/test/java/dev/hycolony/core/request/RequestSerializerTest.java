package dev.hycolony.core.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeContainers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequestSerializerTest {
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private static final ItemKey STONE = new ItemKey("Rock_Stone");
    private static final BlockPos HUT = new BlockPos(0, 64, 0);
    private final FakeContainers containers = new FakeContainers();

    /** A fresh manager with built-ins and one hut, as a colony builds it on load. */
    private final class World {
        final Map<RequesterId, Requester> registry = new HashMap<>();
        final RequestManager m = new RequestManager(id -> Optional.ofNullable(registry.get(id)), new FakeCatalog());
        final PlayerResolver player = new PlayerResolver(HUT);
        final RetryingResolver retrying = new RetryingResolver(HUT);
        final Building hut = Building.create(BuildingTypes.TOWN_HALL, HUT, 0);

        World() {
            m.registerBuiltIn(player);
            m.registerBuiltIn(retrying);
            hut.attachContainers(containers);
            registry.put(hut.requesterId(), hut);
            m.onProviderAdded(hut);
        }

        String resolverOf(RequestToken t) {
            return m.resolverOf(t).map(Resolver::resolverId).orElse("none");
        }
    }

    private static JsonObject roundTrip(JsonObject json) {
        return JsonParser.parseString(json.toString()).getAsJsonObject();
    }

    @Test
    void requestsRoundTripThroughSave() {
        World w = new World();
        containers.containers.computeIfAbsent(HUT, p -> new HashMap<>()).put(PLANKS, 10);
        RequestToken byBuilding = w.m.createAndAssign(w.hut, new StackRequest(PLANKS, 4, 4, true), 3);
        RequestToken retried = w.m.createAndAssign(w.hut, new StackRequest(STONE, 2, 1, true), -1);
        RequestToken child = w.m.createChild(w.retrying, retried, new StackRequest(STONE, 1, 1, false));
        RequestToken toPlayer = w.m.createAndAssign(w.hut, new ToolRequest(ToolType.PICKAXE, 0, 2), 5);
        w.m.onColonyUpdate(r -> r.token().equals(toPlayer));
        w.m.tick();
        w.m.tick();
        assertEquals("player", w.resolverOf(toPlayer));

        JsonObject json = roundTrip(RequestSerializer.write(w.m));
        World l = new World();
        RequestSerializer.read(json, l.m);

        assertEquals(w.m.all().size(), l.m.all().size());
        for (Request before : w.m.all()) {
            Request after = l.m.get(before.token()).orElseThrow();
            assertEquals(before.requester(), after.requester());
            assertEquals(before.requestable(), after.requestable());
            assertEquals(before.state(), after.state());
            assertEquals(before.parent(), after.parent());
            assertEquals(before.children(), after.children());
            assertEquals(before.deliveries(), after.deliveries());
            assertEquals(before.citizenId(), after.citizenId());
            assertEquals(before.blacklist(), after.blacklist());
            assertEquals(w.resolverOf(before.token()), l.resolverOf(before.token()));
        }
        assertEquals(List.of(new ItemAmount(PLANKS, 4)), l.m.get(byBuilding).orElseThrow().deliveries());
        assertEquals(List.of(child), l.m.get(retried).orElseThrow().children());
        assertEquals(Set.of("retrying"), l.m.get(toPlayer).orElseThrow().blacklist());
        assertEquals(w.retrying.delays(), l.retrying.delays());
        assertEquals(w.retrying.tries(), l.retrying.tries());
        assertTrue(l.retrying.delays().get(retried) < RetryingResolver.DELAY_TICKS);
        assertEquals(List.of(toPlayer), l.player.open().stream().map(Request::token).toList());
        assertEquals(l.m.byRequester(w.hut.requesterId()).size(), 3);
    }

    @Test
    void unknownResolverOnLoadIsReassigned() {
        World w = new World();
        RequestToken t = w.m.createAndAssign(w.hut, new StackRequest(PLANKS, 4, 4, true), -1);
        w.m.onColonyUpdate(r -> true);
        assertEquals("player", w.resolverOf(t));

        JsonObject json = roundTrip(RequestSerializer.write(w.m));
        JsonObject assignments = json.getAsJsonObject("assignments");
        assignments.add("ghost", assignments.remove("player"));
        json.add("player", new JsonArray());

        containers.containers.computeIfAbsent(HUT, p -> new HashMap<>()).put(PLANKS, 10);
        World l = new World();
        RequestSerializer.read(json, l.m);

        Request r = l.m.get(t).orElseThrow();
        assertEquals("building:0,64,0", l.resolverOf(t));
        assertEquals(RequestState.COMPLETED, r.state());
        assertEquals(List.of(new ItemAmount(PLANKS, 4)), r.deliveries());
    }
}
