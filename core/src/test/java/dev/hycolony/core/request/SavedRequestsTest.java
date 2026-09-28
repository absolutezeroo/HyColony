package dev.hycolony.core.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.RequesterId;
import dev.hycolony.core.request.model.StackRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SavedRequestsTest {
    private final Map<String, Request> saved = new LinkedHashMap<>();

    private Request request() {
        Request r = new Request(
                new RequestToken(UUID.randomUUID()),
                new RequesterId("building:0,64,0"),
                new StackRequest(new ItemKey("Rock_Stone"), 1, 1, true),
                -1);
        saved.put(r.token().id().toString(), r);
        return r;
    }

    private static void link(Request parent, Request child) {
        child.setParent(parent.token());
        parent.addChild(child.token());
    }

    /** Reads the requests built above as if they came from a save, in creation order. */
    private SavedRequests.Loaded load() {
        JsonArray array = new JsonArray();
        saved.keySet().forEach(token -> {
            JsonObject o = new JsonObject();
            o.addProperty("token", token);
            array.add(o);
        });
        return SavedRequests.read(
                array, o -> Optional.of(saved.get(o.get("token").getAsString())));
    }

    @Test
    void anIntactFamilyLoadsWithoutRepair() {
        Request parent = request();
        link(parent, request());

        SavedRequests.Loaded loaded = load();

        assertEquals(2, loaded.requests().size());
        assertFalse(loaded.repaired());
    }

    @Test
    void aRequestThatIsItsOwnParentIsDropped() {
        Request self = request();
        link(self, self);
        Request other = request();

        SavedRequests.Loaded loaded = load();

        assertEquals(Set.of(other.token()), loaded.requests().keySet());
        assertTrue(loaded.repaired());
    }

    @Test
    void aParentCycleIsDroppedWholeAndTheRestLoads() {
        Request a = request();
        Request b = request();
        link(a, b);
        link(b, a);
        Request below = request();
        link(b, below);
        Request other = request();

        SavedRequests.Loaded loaded = load();

        assertEquals(Set.of(other.token()), loaded.requests().keySet());
        assertTrue(loaded.repaired());
    }

    @Test
    void aChildItsParentDoesNotListIsDropped() {
        Request parent = request();
        Request child = request();
        child.setParent(parent.token());

        SavedRequests.Loaded loaded = load();

        assertEquals(Set.of(parent.token()), loaded.requests().keySet());
        assertTrue(loaded.repaired());
    }

    @Test
    void aParentListingAChildThatPointsElsewhereIsDroppedWithItsFamily() {
        Request parent = request();
        Request child = request();
        parent.addChild(child.token());
        Request above = request();
        link(above, parent);

        SavedRequests.Loaded loaded = load();

        assertEquals(Set.of(child.token()), loaded.requests().keySet());
        assertTrue(loaded.repaired());
    }
}
