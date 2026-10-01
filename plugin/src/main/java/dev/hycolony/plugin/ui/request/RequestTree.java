package dev.hycolony.plugin.ui.request;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ui.RequestsView.RequestRow;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackList;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.Optional;

/**
 * A request tree (MC RequestTreeWindowModule, layoutrequeststree.xml), drawn into a list: each request, its children
 * moved right one step per level, with its item, short text, requester, the detail button, and Fulfill and Cancel
 * where the core offers them. Each event names its request's token, so a list redrawn meanwhile still targets it.
 */
public final class RequestTree {
    /** MC moves a child 2 px right per level, doubled. */
    private static final int INDENT = 4;

    private static final int LINE_WIDTH = 316;
    private static final int LINE_HEIGHT = 80;

    public static final String DETAIL = "requestDetail";
    public static final String FULFILL = "requestFulfill";
    public static final String CANCEL = "requestCancel";

    private RequestTree() {}

    /** Appends {@code rows} into {@code list} (a TopScrolling group 316 wide). */
    public static void render(
            UICommandBuilder ui, UIEventBuilder events, String list, List<RequestRow> rows, IdMap ids) {
        for (int i = 0; i < rows.size(); i++) {
            RequestRow r = rows.get(i);
            String row = list + "[" + i + "]";
            ui.append(list, "Pages/HyColony/Mc/RequestLine.ui");
            if (r.depth() > 0) {
                Anchor anchor = new Anchor();
                anchor.setLeft(Value.of(INDENT * r.depth()));
                anchor.setWidth(Value.of(LINE_WIDTH - INDENT * r.depth()));
                anchor.setHeight(Value.of(LINE_HEIGHT));
                ui.setObject(row + ".Anchor", anchor);
            }
            // MC sets the requester's line only beside an item (a request with display stacks).
            if (icon(ui, row + " ", r, ids)) {
                ui.set(row + " #Requester.TextSpans", ColonyPage.buildingName(r.requesterName()));
            }
            text(ui, row + " ", r.requestable(), RequestTexts.describeShort(r.requestable()));
            String ref = r.token().id().toString();
            ColonyPage.bindRef(events, row + " #Detail", DETAIL, ref);
            if (r.fulfillable()) {
                ui.set(row + " #Fulfill.Visible", true);
                ColonyPage.bindRef(events, row + " #Fulfill", FULFILL, ref);
            }
            if (r.cancellable()) {
                ui.set(row + " #Cancel.Visible", true);
                ColonyPage.bindRef(events, row + " #Cancel", CANCEL, ref);
            }
        }
    }

    /** The row an event names by its token; empty for an unknown or gone request. */
    public static Optional<RequestRow> row(ColonyPage.Act act, List<RequestRow> rows) {
        return rows.stream()
                .filter(r -> r.token().id().toString().equals(act.ref()))
                .findFirst();
    }

    /**
     * MC: the request's item (its display stacks), else its logo (a courier task's or a crafting task's, which have no
     * display stacks) with its resolver as tooltip; true when an item shows. Deviation from MC: the first of the stacks
     * MC cycles through; a tool request shows the type's crude tool (IdMap toolIcon); the logo's tooltip is the
     * resolver's name, where MC writes "From:" and the queue position. {@code scope} prefixes the selectors: a row and
     * a space, or nothing at the page's root.
     */
    static boolean icon(UICommandBuilder ui, String scope, RequestRow r, IdMap ids) {
        Optional<String> item = item(r.requestable(), ids);
        if (item.isPresent()) {
            ui.set(scope + "#Item.Visible", true);
            ui.set(scope + "#Item.ItemId", item.get());
            return true;
        }
        String logo = r.requestable() instanceof Crafting ? "#CraftingIcon" : "#Icon";
        ui.set(scope + logo + ".Visible", true);
        r.resolver().ifPresent(name -> ui.set(scope + logo + ".TooltipTextSpans", ColonyPage.buildingName(name)));
        return false;
    }

    /** MC getDisplayStacks: none for a courier task (delivery, pickup) or a crafting task. */
    private static Optional<String> item(Requestable requestable, IdMap ids) {
        return switch (requestable) {
            case StackRequest s -> Optional.of(s.item().id());
            case ToolRequest t -> Optional.of(ids.toolIcon(t.type()));
            case StackList l -> Optional.of(l.accepted().getFirst().id());
            case Delivery _, Pickup _, Crafting _ -> Optional.empty();
        };
    }

    /**
     * MC: a stack-based task's prefix, its item and its count, else {@code plain} (the short or long description);
     * {@code scope} as icon's. Deviation from MC: the count is a label beside the item (a Hytale ItemIcon draws none).
     */
    static void text(UICommandBuilder ui, String scope, Requestable requestable, Message plain) {
        Optional<ItemAmount> stack = StackTasks.stack(requestable);
        if (stack.isEmpty()) {
            ui.set(scope + "#Short.TextSpans", plain);
            return;
        }
        ui.set(scope + "#Short.TextSpans", StackTasks.prefix(requestable));
        ui.set(scope + "#TaskItem.Visible", true);
        ui.set(scope + "#TaskItem.ItemId", stack.get().item().id());
        ui.set(scope + "#TaskCount.Text", String.valueOf(stack.get().count()));
    }

    /** "x, y, z" of a request's requester, or nothing. */
    static Message place(RequestRow r) {
        return r.requesterPos()
                .map(p -> Message.raw(p.x() + ", " + p.y() + ", " + p.z()))
                .orElse(Message.raw(""));
    }
}
