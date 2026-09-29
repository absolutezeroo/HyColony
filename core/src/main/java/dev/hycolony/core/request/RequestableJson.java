package dev.hycolony.core.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.persist.SavedJson;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.RequesterId;
import dev.hycolony.core.request.model.StackList;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One {@link Requestable} to and from JSON, by its {@code type}; a type this build does not know reads as empty. */
final class RequestableJson {
    private RequestableJson() {}

    /** Requestable's JSON form; exhaustive over the sealed hierarchy, so every kind is always saved. */
    static JsonObject write(Requestable r) {
        JsonObject o = new JsonObject();
        switch (r) {
            case StackRequest s -> {
                o.addProperty("type", "stack");
                o.addProperty("item", s.item().id());
                o.addProperty("count", s.count());
                o.addProperty("minCount", s.minCount());
                o.addProperty("canBeResolvedByBuilding", s.canBeResolvedByBuilding());
            }
            case ToolRequest t -> {
                o.addProperty("type", "tool");
                o.addProperty("tool", t.type().name());
                o.addProperty("minLevel", t.minLevel());
                o.addProperty("maxLevel", t.maxLevel());
            }
            case Delivery d -> {
                o.addProperty("type", "delivery");
                o.add("start", blockPos(d.start()));
                o.addProperty("target", d.target().value());
                o.addProperty("item", d.stack().item().id());
                o.addProperty("count", d.stack().count());
                o.addProperty("priority", d.priority());
            }
            case Pickup p -> {
                o.addProperty("type", "pickup");
                o.addProperty("priority", p.priority());
                o.addProperty("day", p.day());
                o.addProperty("quantity", p.quantity());
            }
            case StackList l -> writeStackList(o, l);
            case Crafting c -> writeCrafting(o, c);
        }
        return o;
    }

    private static void writeStackList(JsonObject o, StackList l) {
        o.addProperty("type", "stackList");
        JsonArray accepted = new JsonArray();
        l.accepted().forEach(item -> accepted.add(item.id()));
        o.add("accepted", accepted);
        o.addProperty("description", l.description());
        o.addProperty("count", l.count());
        o.addProperty("minCount", l.minCount());
    }

    /** Deviation from MC: the minimum count is saved too, where MC PublicCrafting.serialize drops it. */
    private static void writeCrafting(JsonObject o, Crafting c) {
        o.addProperty("type", "crafting");
        o.addProperty("item", c.stack().id());
        o.addProperty("count", c.count());
        o.addProperty("minCount", c.minCount());
        o.addProperty("recipe", c.recipeId());
        o.addProperty("public", c.isPublic());
    }

    /**
     * The saved requestable; empty for a type (or tool type) this build does not know, or when the item or positive
     * count it asks for cannot be read (§ 5). A missing optional value takes its default.
     */
    static Optional<Requestable> read(JsonObject o) {
        return switch (SavedJson.stringOr(o.get("type"), "")) {
            case "stack" ->
                stack(o).map(s -> new StackRequest(
                        s.item(),
                        s.count(),
                        SavedJson.intOr(o.get("minCount"), s.count()),
                        SavedJson.boolOr(o.get("canBeResolvedByBuilding"), true)));
            case "tool" ->
                SavedJson.enumOf(ToolType.class, o.get("tool"))
                        .map(t -> new ToolRequest(
                                t,
                                SavedJson.intOr(o.get("minLevel"), 0),
                                SavedJson.intOr(o.get("maxLevel"), ToolRequest.ANY_LEVEL)));
            case "delivery" -> readDelivery(o);
            case "pickup" ->
                Optional.of(new Pickup(
                        SavedJson.intOr(o.get("priority"), 0),
                        SavedJson.intOr(o.get("day"), 0),
                        SavedJson.intOr(o.get("quantity"), 0)));
            case "stackList" -> readStackList(o);
            case "crafting" -> readCrafting(o);
            default -> Optional.empty();
        };
    }

    /** The saved {@code item} and {@code count}; empty without an item or a positive count. */
    private static Optional<ItemAmount> stack(JsonObject o) {
        String item = SavedJson.stringOr(o.get("item"), "");
        int count = SavedJson.intOr(o.get("count"), 0);
        return item.isEmpty() || count <= 0 ? Optional.empty() : Optional.of(new ItemAmount(new ItemKey(item), count));
    }

    /** Empty without its start position or its stack. */
    private static Optional<Requestable> readDelivery(JsonObject o) {
        Optional<BlockPos> start = SavedJson.tryPos(o.get("start"));
        Optional<ItemAmount> stack = stack(o);
        if (start.isEmpty() || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Delivery(
                start.get(),
                new RequesterId(SavedJson.stringOr(o.get("target"), "")),
                stack.get(),
                SavedJson.intOr(o.get("priority"), Delivery.DEFAULT_DELIVERY_PRIORITY)));
    }

    /** Empty without accepted items ({@link StackList} accepts one at least); a missing description reads as none. */
    private static Optional<Requestable> readStackList(JsonObject o) {
        if (!(o.get("accepted") instanceof JsonArray saved) || saved.isEmpty()) {
            return Optional.empty();
        }
        List<ItemKey> accepted = new ArrayList<>(saved.size());
        for (JsonElement item : saved) {
            String id = SavedJson.stringOr(item, "");
            if (!id.isEmpty()) {
                accepted.add(new ItemKey(id));
            }
        }
        if (accepted.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new StackList(
                accepted,
                SavedJson.stringOr(o.get("description"), ""),
                SavedJson.intOr(o.get("count"), 1),
                SavedJson.intOr(o.get("minCount"), 1)));
    }

    /** Empty without its recipe. */
    private static Optional<Requestable> readCrafting(JsonObject o) {
        String recipe = SavedJson.stringOr(o.get("recipe"), "");
        if (recipe.isEmpty()) {
            return Optional.empty();
        }
        return stack(o).map(s -> new Crafting(
                s.item(),
                s.count(),
                SavedJson.intOr(o.get("minCount"), s.count()),
                recipe,
                SavedJson.boolOr(o.get("public"), false)));
    }

    private static JsonObject blockPos(BlockPos p) {
        JsonObject o = new JsonObject();
        o.addProperty("x", p.x());
        o.addProperty("y", p.y());
        o.addProperty("z", p.z());
        return o;
    }
}
