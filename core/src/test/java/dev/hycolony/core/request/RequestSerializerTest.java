package dev.hycolony.core.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.RequesterId;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeContainers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
            hut.attachResolvers(containers, List.of(), (r, item) -> 0);
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
        assertEquals(
                List.of(new ItemAmount(PLANKS, 4)),
                l.m.get(byBuilding).orElseThrow().deliveries());
        assertEquals(List.of(child), l.m.get(retried).orElseThrow().children());
        assertEquals(Set.of("retrying"), l.m.get(toPlayer).orElseThrow().blacklist());
        assertEquals(w.retrying.delays(), l.retrying.delays());
        assertEquals(w.retrying.tries(), l.retrying.tries());
        assertTrue(l.retrying.delays().get(retried) < RetryingResolver.DELAY_UPDATES);
        assertEquals(
                List.of(toPlayer), l.player.open().stream().map(Request::token).toList());
        assertEquals(l.m.byRequester(w.hut.requesterId()).size(), 3);
    }

    @Test
    void deliveryAndPickupRequestsRoundTripThroughSave() {
        World w = new World();
        Delivery deliveryRequestable =
                new Delivery(HUT, w.hut.requesterId(), new ItemAmount(PLANKS, 4), Delivery.DEFAULT_DELIVERY_PRIORITY);
        Pickup pickupRequestable = new Pickup(5, 12, 20);
        RequestToken delivery = w.m.createAndAssign(w.hut, deliveryRequestable, -1);
        RequestToken pickup = w.m.createAndAssign(w.hut, pickupRequestable, -1);

        JsonObject json = roundTrip(RequestSerializer.write(w.m));
        assertEquals("delivery", requestableJson(json, delivery).get("type").getAsString());
        assertEquals("pickup", requestableJson(json, pickup).get("type").getAsString());

        World l = new World();
        RequestSerializer.read(json, l.m);

        assertEquals(deliveryRequestable, l.m.get(delivery).orElseThrow().requestable());
        assertEquals(pickupRequestable, l.m.get(pickup).orElseThrow().requestable());
    }

    private static JsonObject requestableJson(JsonObject root, RequestToken token) {
        for (JsonElement el : root.getAsJsonArray("requests")) {
            JsonObject r = el.getAsJsonObject();
            if (r.get("token").getAsString().equals(token.id().toString())) {
                return r.getAsJsonObject("requestable");
            }
        }
        throw new AssertionError("No request found for " + token);
    }

    /** A save of every request shape (building, retrying with a child, player, blacklist) loads and saves unchanged. */
    @Test
    void savedRequestsFixtureLoadsAndSavesIdentically() throws IOException {
        JsonObject fixture;
        try (var in = getClass().getResourceAsStream("/fixtures/requests-v2.json")) {
            fixture = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
        World l = new World();
        RequestSerializer.read(fixture.deepCopy(), l.m);

        // Compared in Gson's default form, which leaves out the null parents the fixture omits.
        assertEquals(fixture, JsonParser.parseString(new Gson().toJson(RequestSerializer.write(l.m))));
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

    @Test
    void openRequestWithAnUnreadableAssignmentIsReassignedAndTheSaveRewritten() {
        World w = new World();
        RequestToken t = w.m.createAndAssign(w.hut, new StackRequest(PLANKS, 4, 4, true), Request.NO_CITIZEN);
        w.m.onColonyUpdate(r -> true);
        JsonObject json = roundTrip(RequestSerializer.write(w.m));
        JsonArray garbage = new JsonArray();
        garbage.add("garbage");
        JsonObject assignments = new JsonObject();
        assignments.add("player", garbage);
        json.add("assignments", assignments);
        json.add("player", new JsonArray());

        containers.containers.computeIfAbsent(HUT, p -> new HashMap<>()).put(PLANKS, 10);
        World l = new World();
        assertTrue(RequestSerializer.read(json, l.m));

        assertEquals("building:0,64,0", l.resolverOf(t), "an open request without a resolver would wait forever");
    }

    @Test
    void reportedRequestWithoutResolverIsTriedAgainWithoutRewritingTheSave() {
        World w = new World();
        RequestToken t = w.m.createAndAssign(w.hut, new StackRequest(PLANKS, 4, 4, true), Request.NO_CITIZEN);
        JsonObject json = roundTrip(RequestSerializer.write(w.m));
        json.getAsJsonArray("requests").get(0).getAsJsonObject().addProperty("state", "REPORTED");
        json.add("assignments", new JsonObject());
        json.add("player", new JsonArray());

        containers.containers.computeIfAbsent(HUT, p -> new HashMap<>()).put(PLANKS, 10);
        World l = new World();
        assertFalse(RequestSerializer.read(json, l.m), "REPORTED without a resolver is a legitimate state");

        assertEquals("building:0,64,0", l.resolverOf(t));
    }

    @Test
    void malformedSavedRequestIsLeftOutAndMissingValuesTakeTheirDefault() {
        World w = new World();
        RequestToken t = w.m.createAndAssign(w.hut, new StackRequest(PLANKS, 4, 4, true), 3);
        JsonObject json = roundTrip(RequestSerializer.write(w.m));
        JsonArray requests = json.getAsJsonArray("requests");
        JsonObject saved = requests.get(0).getAsJsonObject();
        JsonObject noToken = saved.deepCopy();
        noToken.remove("token");
        JsonObject noCount = saved.deepCopy();
        noCount.addProperty("token", new UUID(0, 9).toString());
        noCount.getAsJsonObject("requestable").remove("count");
        requests.add(noToken);
        requests.add(noCount);
        saved.addProperty("deliveries", "none");
        saved.addProperty("blacklist", 3);
        saved.remove("citizenId");
        json.addProperty("retrying", "none");

        World l = new World();
        RequestSerializer.read(json, l.m);

        assertEquals(1, l.m.all().size());
        assertEquals(Request.NO_CITIZEN, l.m.get(t).orElseThrow().citizenId());
    }

    @Test
    void inconsistentSaveIsHealedOnLoad() {
        World w = new World();
        containers.containers.computeIfAbsent(HUT, p -> new HashMap<>()).put(PLANKS, 4);
        RequestToken done = w.m.createAndAssign(w.hut, new StackRequest(PLANKS, 4, 4, true), -1);
        RequestToken retried = w.m.createAndAssign(w.hut, new StackRequest(STONE, 2, 2, true), -1);
        RequestToken atPlayer = w.m.createAndAssign(w.hut, new StackRequest(STONE, 3, 3, true), -1);
        w.m.onColonyUpdate(r -> r.token().equals(atPlayer));
        assertEquals("player", w.resolverOf(atPlayer));

        JsonObject json = roundTrip(RequestSerializer.write(w.m));
        JsonObject assignments = json.getAsJsonObject("assignments");
        assignments.add("ghost", assignments.remove("building:0,64,0"));
        json.getAsJsonObject("retrying")
                .getAsJsonObject("delays")
                .remove(retried.id().toString());
        json.getAsJsonObject("retrying")
                .getAsJsonObject("tries")
                .remove(retried.id().toString());
        json.add("player", new JsonArray());

        World l = new World();
        RequestSerializer.read(json, l.m);

        Request d = l.m.get(done).orElseThrow();
        assertEquals(RequestState.COMPLETED, d.state(), "a finished orphan is not re-resolved");
        assertEquals("none", l.resolverOf(done));
        assertEquals(List.of(new ItemAmount(PLANKS, 4)), d.deliveries());

        assertEquals("retrying", l.resolverOf(retried));
        assertEquals(RetryingResolver.DELAY_UPDATES, l.retrying.delays().get(retried));
        assertEquals(1, l.retrying.tries().get(retried));
        assertEquals(
                List.of(atPlayer), l.player.open().stream().map(Request::token).toList());
    }

    @Test
    void directDeliveryFlagSurvivesSave() {
        World w = new World();
        RequestToken t = w.m.createAndAssign(w.hut, new StackRequest(STONE, 2, 2, true), 1);
        w.m.overrule(t, List.of(new ItemAmount(STONE, 2)), true);

        World l = new World();
        RequestSerializer.read(roundTrip(RequestSerializer.write(w.m)), l.m);

        assertTrue(l.m.get(t).orElseThrow().deliveredToCitizen());
    }

    @Test
    void asyncFlagSurvivesSave() {
        World w = new World();
        RequestToken t = w.m.createAsync(w.hut, new StackRequest(STONE, 2, 2, true));

        World l = new World();
        RequestSerializer.read(roundTrip(RequestSerializer.write(w.m)), l.m);

        assertTrue(l.m.get(t).orElseThrow().async());
        assertEquals(Request.NO_CITIZEN, l.m.get(t).orElseThrow().citizenId(), "no worker waits for it");
    }
}
