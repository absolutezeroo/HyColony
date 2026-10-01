package dev.hycolony.plugin.ui.request;

import static dev.hycolony.plugin.ui.ColonyPage.buildingName;
import static dev.hycolony.plugin.ui.ColonyPage.itemName;
import static dev.hycolony.plugin.ui.ColonyPage.jobName;

import com.hypixel.hytale.server.core.Message;
import dev.hycolony.core.app.ui.NeedsPlayerNotice;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackList;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import java.util.Locale;

/**
 * The requests' texts in the player's language (MC StandardRequests display strings), for every window and chat line
 * that names a request.
 *
 * <p>Deviation from MC: a tool level is its number, where MC names the level's grade (wood, stone…), since Hytale's
 * tool tiers are not MC's.
 */
public final class RequestTexts {
    private RequestTexts() {}

    /** The chat line "{requester} ({job}) needs: {requestable}". */
    public static Message needsPlayer(NeedsPlayerNotice n) {
        return Message.translation("hycolony.request.needsPlayer")
                .param("p0", buildingName(n.requesterName()))
                .param("p1", jobName(n.jobId()))
                .param("p2", describe(n.requestable()));
    }

    /**
     * "64 x Stone", "Pickaxe (level 0 to 1)", "Pickaxe (level 0 or higher)", "Delivery: 10 x Stone", "Pickup", "8 x Oak
     * Trunk (or equivalent)" or "3 x Recipe: Wheat Seeds" (MC StandardRequests short display strings).
     */
    public static Message describe(Requestable requestable) {
        return switch (requestable) {
            case StackRequest s ->
                Message.translation("hycolony.ui.requests.stack")
                        .param("p0", String.valueOf(s.count()))
                        .param("p1", itemName(s.item().id()));
            case ToolRequest t -> describeTool(t);
            case Delivery d ->
                Message.translation("hycolony.ui.requests.delivery")
                        .param("p0", String.valueOf(d.stack().count()))
                        .param("p1", itemName(d.stack().item().id()));
            case Pickup _ -> Message.translation("hycolony.ui.requests.pickup");
            case StackList l -> describeStackList(l);
            // MC AbstractCraftingRequest.getShortDisplayString shows the minimum count.
            case Crafting c ->
                Message.translation("hycolony.ui.requests.crafting")
                        .param("p0", String.valueOf(c.minCount()))
                        .param("p1", itemName(c.stack().id()));
        };
    }

    /**
     * MC getShortDisplayString as a request tree shows it: "64 Stone" or "32-64 Stone" (minimum then count) for a
     * stack, the type alone for a tool, else as {@link #describe}.
     */
    public static Message describeShort(Requestable requestable) {
        return switch (requestable) {
            case StackRequest s
            when s.minCount() == s.count() ->
                Message.translation("hycolony.ui.requests.short.stack")
                        .param("p0", String.valueOf(s.count()))
                        .param("p1", itemName(s.item().id()));
            case StackRequest s ->
                Message.translation("hycolony.ui.requests.short.stackRange")
                        .param("p0", String.valueOf(s.minCount()))
                        .param("p1", String.valueOf(s.count()))
                        .param("p2", itemName(s.item().id()));
            case ToolRequest t -> toolName(t);
            default -> describe(requestable);
        };
    }

    /**
     * MC getLongDisplayString, a request's details: a tool with its minimal and maximal level (the maximal one only
     * below any level), else the short text.
     */
    public static Message describeLong(Requestable requestable) {
        if (!(requestable instanceof ToolRequest t)) {
            return describeShort(requestable);
        }
        return t.maxLevel() == Integer.MAX_VALUE
                ? Message.translation("hycolony.ui.requests.long.toolMin")
                        .param("p0", toolName(t))
                        .param("p1", String.valueOf(t.minLevel()))
                : Message.translation("hycolony.ui.requests.long.toolMinMax")
                        .param("p0", toolName(t))
                        .param("p1", String.valueOf(t.minLevel()))
                        .param("p2", String.valueOf(t.maxLevel()));
    }

    private static Message toolName(ToolRequest t) {
        return Message.translation("hycolony.ui.tool." + t.type().name().toLowerCase(Locale.ROOT));
    }

    /**
     * Deviation from MC: the first accepted item (a StackList accepts at least one) stands for the list, where MC shows
     * its description, a translation key.
     */
    private static Message describeStackList(StackList l) {
        return Message.translation("hycolony.ui.requests.stackList")
                .param("p0", String.valueOf(l.count()))
                .param("p1", itemName(l.accepted().getFirst().id()));
    }

    /** A max-level hut asks for any tool level (MC TOOL_LEVEL_MAXIMUM): "level 0 or higher", not "0 to 2147483647". */
    private static Message describeTool(ToolRequest t) {
        boolean anyLevel = t.maxLevel() == Integer.MAX_VALUE;
        Message tool = toolName(t);
        Message m = Message.translation(anyLevel ? "hycolony.ui.requests.toolAnyLevel" : "hycolony.ui.requests.tool")
                .param("p0", tool)
                .param("p1", String.valueOf(t.minLevel()));
        return anyLevel ? m : m.param("p2", String.valueOf(t.maxLevel()));
    }
}
