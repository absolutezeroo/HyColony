package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.NeedsPlayerNotice;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackList;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.clipboard.ClipboardItem;
import dev.hycolony.plugin.ui.request.RequestTree;
import dev.hycolony.plugin.ui.request.RequestTreeEvents;
import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * The clipboard's window (MC WindowClipBoard): the requests only a player can serve as a request tree, and the "!"
 * button, whose state the clipboard item keeps (MC ItemSettingMessage). Also the requests' texts in the player's
 * language, for every window that names a request.
 */
public final class RequestsPage extends ColonyPage {
    private final RequestsView view;
    private final IdMap ids;
    private final RequestTreeEvents tree;

    public RequestsPage(PlayerRef playerRef, RequestsView view, ColonyManager manager, IdMap ids) {
        super(playerRef, manager);
        this.view = view;
        this.ids = ids;
        this.tree = new RequestTreeEvents(
                manager,
                playerRef,
                view.colonyId(),
                view.rows(),
                ids,
                () -> manager.windows().openRequests(player, view.colonyId(), view.showImportant()));
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Requests.ui");
        String important = view.showImportant() ? "#ImportantOn" : "#ImportantOff";
        ui.set(important + ".Visible", true);
        bind(events, important, "important");
        RequestTree.render(ui, events, "#Tree", view.rows(), ids);
    }

    /** The chat line "{requester} ({job}) needs: {requestable}". */
    public static Message needsPlayer(NeedsPlayerNotice n) {
        return Message.translation("hycolony.request.needsPlayer")
                .param("p0", buildingName(n.requesterName()))
                .param("p1", jobName(n.jobId()))
                .param("p2", describe(n.requestable()));
    }

    /**
     * "64 x Stone", "Pickaxe (level 0 to 1)", "Pickaxe (level 0 or higher)", "Delivery: 10 x Stone", "Pickup", "8 x Oak
     * Trunk (or equivalent)" or "3 x Recipe: Wheat Seeds" (MC StandardRequests short display strings), in the player's
     * language.
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
        Message tool = Message.translation("hycolony.ui.tool." + t.type().name().toLowerCase(Locale.ROOT));
        Message m = Message.translation(anyLevel ? "hycolony.ui.requests.toolAnyLevel" : "hycolony.ui.requests.tool")
                .param("p0", tool)
                .param("p1", String.valueOf(t.minLevel()));
        return anyLevel ? m : m.param("p2", String.valueOf(t.maxLevel()));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if (!"important".equals(act.action())) {
            tree.handle(ref, store, this, act);
            return;
        }
        // MC toggleImportant: the flag flips, the item keeps it, the list is drawn again.
        boolean on = !view.showImportant();
        ClipboardItem.lastUsed(player).ifPresent(item -> ClipboardItem.used(player, item.withShowImportant(on)));
        manager.windows().openRequests(player, view.colonyId(), on);
    }
}
