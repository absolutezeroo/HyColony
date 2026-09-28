package dev.hycolony.core.logistics.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.colony.ui.tab.TaskRow;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.RequestSerializer;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskRowsTest {
    private static final BlockPos HUT = new BlockPos(4, 64, 4);
    private final TestContexts t = new TestContexts();

    private Colony colonyWithHut() {
        Colony c = new Colony(
                t.context(),
                new TerritoryIndex(),
                new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
        c.buildings().add(Building.create(BuildingTypes.TOWN_HALL, HUT, 0));
        return c;
    }

    @Test
    void aParentCycleInACorruptedSaveEndsTheRequesterClimb() {
        Colony saved = colonyWithHut();
        Building hut = saved.buildings().at(HUT).orElseThrow();
        StackRequest stone = new StackRequest(new ItemKey("Rock_Stone"), 1, 1, true);
        RequestToken a = saved.requests().createAndAssign(hut, stone, -1);
        RequestToken b = saved.requests().createAndAssign(hut, stone, -1);
        JsonObject json = RequestSerializer.write(saved.requests());
        JsonArray requests = json.getAsJsonArray("requests");
        linkTo(requests.get(0).getAsJsonObject(), b);
        linkTo(requests.get(1).getAsJsonObject(), a);
        Colony loaded = colonyWithHut();
        RequestSerializer.read(json, loaded.requests());

        List<TaskRow> rows = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> TaskRows.of(loaded, List.of(a)));

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).forRequester().isPresent());
    }

    /** Makes {@code other} both the parent and the only child of the saved request. */
    private static void linkTo(JsonObject request, RequestToken other) {
        request.addProperty("parent", other.id().toString());
        JsonArray children = new JsonArray();
        children.add(other.id().toString());
        request.add("children", children);
    }
}
