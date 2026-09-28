package dev.hycolony.core.colony.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.persistence.ColonySerializer;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.warehouse.TaskRows;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestCyclesOnLoadTest {
    private static final BlockPos HUT = new BlockPos(4, 64, 4);
    private static final StackRequest STONE = new StackRequest(new ItemKey("Rock_Stone"), 1, 1, true);
    private final TestContexts t = new TestContexts();
    private final UUID owner = UUID.randomUUID();
    private final Colony saved = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(owner, "A")));
    private final Building hut = Building.create(BuildingTypes.TOWN_HALL, HUT, 0);

    RequestCyclesOnLoadTest() {
        saved.buildings().add(hut);
    }

    @Test
    void aParentCycleInASaveIsDroppedAtLoadAndTheColonyRewritten() {
        RequestToken a = saved.requests().createAndAssign(hut, STONE, -1);
        RequestToken b = saved.requests().createAndAssign(hut, STONE, -1);
        RequestToken kept = saved.requests().createAndAssign(hut, STONE, -1);
        JsonObject json = ColonySerializer.write(saved);
        link(json, a, b);
        link(json, b, a);

        Colony loaded = assertLoadsAndViewsPromptly(json, List.of(a, b, kept));

        assertTrue(loaded.requests().get(a).isEmpty());
        assertTrue(loaded.requests().get(b).isEmpty());
        assertTrue(loaded.requests().get(kept).isPresent());
        assertTrue(loaded.isDirty());
    }

    @Test
    void aRequestThatIsItsOwnParentInASaveIsDroppedAtLoad() {
        RequestToken self = saved.requests().createAndAssign(hut, STONE, -1);
        JsonObject json = ColonySerializer.write(saved);
        link(json, self, self);

        Colony loaded = assertLoadsAndViewsPromptly(json, List.of(self));

        assertTrue(loaded.requests().get(self).isEmpty());
        assertTrue(loaded.isDirty());
    }

    /** Loads {@code json}, then builds the clipboard and a courier task list over {@code tokens}, within 2 s. */
    private Colony assertLoadsAndViewsPromptly(JsonObject json, List<RequestToken> tokens) {
        return assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Colony loaded = ColonySerializer.read(json, t.context(), new TerritoryIndex());
            new RequestViews(t.context()).of(loaded, owner);
            assertEquals(
                    tokens.stream()
                            .filter(tk -> loaded.requests().get(tk).isPresent())
                            .count(),
                    TaskRows.of(loaded, tokens).size());
            return loaded;
        });
    }

    /** Makes {@code parent} the parent of {@code child} in the saved requests, and its only child. */
    private static void link(JsonObject colony, RequestToken child, RequestToken parent) {
        for (var el : colony.getAsJsonObject("requests").getAsJsonArray("requests")) {
            JsonObject r = el.getAsJsonObject();
            String token = r.get("token").getAsString();
            if (token.equals(child.id().toString())) {
                r.addProperty("parent", parent.id().toString());
            }
            if (token.equals(parent.id().toString())) {
                JsonArray children = new JsonArray();
                children.add(child.id().toString());
                r.add("children", children);
            }
        }
    }
}
