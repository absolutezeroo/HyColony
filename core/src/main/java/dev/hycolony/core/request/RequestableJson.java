package dev.hycolony.core.request;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
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

    /** The saved requestable; empty for a type (or tool type) this build does not know. */
    static Optional<Requestable> read(JsonObject o) {
        return switch (o.get("type").getAsString()) {
            case "stack" ->
                Optional.of(new StackRequest(
                        new ItemKey(o.get("item").getAsString()),
                        o.get("count").getAsInt(),
                        o.get("minCount").getAsInt(),
                        o.get("canBeResolvedByBuilding").getAsBoolean()));
            case "tool" ->
                enumOf(ToolType.values(), o.get("tool").getAsString())
                        .map(t -> new ToolRequest(
                                t,
                                o.get("minLevel").getAsInt(),
                                o.get("maxLevel").getAsInt()));
            case "delivery" ->
                Optional.of(new Delivery(
                        blockPos(o.getAsJsonObject("start")),
                        new RequesterId(o.get("target").getAsString()),
                        new ItemAmount(
                                new ItemKey(o.get("item").getAsString()),
                                o.get("count").getAsInt()),
                        o.get("priority").getAsInt()));
            case "pickup" ->
                Optional.of(new Pickup(
                        o.get("priority").getAsInt(),
                        o.get("day").getAsInt(),
                        o.get("quantity").getAsInt()));
            case "stackList" -> readStackList(o);
            case "crafting" -> readCrafting(o);
            default -> Optional.empty();
        };
    }

    /** Empty without accepted items ({@link StackList} accepts one at least); a missing description reads as none. */
    private static Optional<Requestable> readStackList(JsonObject o) {
        if (!(o.get("accepted") instanceof JsonArray saved) || saved.isEmpty()) {
            return Optional.empty();
        }
        List<ItemKey> accepted = new ArrayList<>(saved.size());
        for (JsonElement item : saved) {
            accepted.add(new ItemKey(item.getAsString()));
        }
        String description = o.get("description") instanceof JsonPrimitive d ? d.getAsString() : "";
        return Optional.of(new StackList(
                accepted,
                description,
                o.get("count").getAsInt(),
                o.get("minCount").getAsInt()));
    }

    /** Empty without its recipe. */
    private static Optional<Requestable> readCrafting(JsonObject o) {
        if (!(o.get("recipe") instanceof JsonPrimitive recipe)) {
            return Optional.empty();
        }
        return Optional.of(new Crafting(
                new ItemKey(o.get("item").getAsString()),
                o.get("count").getAsInt(),
                o.get("minCount").getAsInt(),
                recipe.getAsString(),
                o.get("public").getAsBoolean()));
    }

    /** The constant named {@code name} among {@code values}; empty for a name this build does not know. */
    static <E extends Enum<E>> Optional<E> enumOf(E[] values, String name) {
        for (E value : values) {
            if (value.name().equals(name)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static JsonObject blockPos(BlockPos p) {
        JsonObject o = new JsonObject();
        o.addProperty("x", p.x());
        o.addProperty("y", p.y());
        o.addProperty("z", p.z());
        return o;
    }

    private static BlockPos blockPos(JsonObject o) {
        return new BlockPos(
                o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
    }
}
