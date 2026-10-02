package dev.hylens.plugin.command.book;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.api.Pos;
import dev.hylens.core.menu.MenuView;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Draws the HyLens menu's Requests tab (spec 2026-10-02 lot 3, § 6): the chosen colony's open requests, the chosen one
 * with MC's request window "Fulfill", and the request system's reset.
 */
final class RequestsTab {
    static final String PAGE = "Pages/HyLens/Book/Requests.ui";
    private static final String ROW = "Pages/HyLens/Book/RequestRow.ui";
    /** The request kinds with a text of their own; another kind shows as the api names it. */
    private static final Set<String> KINDS = Set.of("stack", "tool", "delivery", "pickup", "stack_list", "crafting");

    private RequestsTab() {}

    /** Fills the tab appended just before with {@code v}; binds every button. */
    static void render(UICommandBuilder ui, MenuBinds binds, MenuView v) {
        List<MenuView.RequestRow> requests = v.requests();
        for (int i = 0; i < requests.size(); i++) {
            MenuView.RequestRow r = requests.get(i);
            String row = "#Requests[" + i + "]";
            ui.append("#Requests", ROW);
            ui.set(row + " #Name.TextSpans", what(r));
            ui.set(row + " #State.TextSpans", state(r.state()));
            binds.on(row + " #Choose", "request", r.id());
        }
        Optional<MenuView.RequestRow> chosen =
                requests.stream().filter(MenuView.RequestRow::chosen).findFirst();
        ui.set(
                "#RequestItem.TextSpans",
                chosen.map(RequestsTab::what)
                        .orElse(Message.translation(
                                requests.isEmpty() ? "hylens.menu.requestsNone" : "hylens.menu.requestNoneChosen")));
        chosen.ifPresent(r -> details(ui, r));
        ui.set("#FulfilButton.Visible", chosen.isPresent());
        binds.on("#FulfilButton", "fulfil", "");
        binds.on("#ResetRequestsButton", "resetRequests", "");
    }

    /** The chosen request's asker, state and resolver. */
    private static void details(UICommandBuilder ui, MenuView.RequestRow r) {
        Message asker = r.citizen()
                .map(name -> Message.translation("hylens.menu.request.citizen").param("p0", name))
                .or(() -> r.building().map(RequestsTab::hut))
                .orElse(Message.translation("hylens.menu.request.colony"));
        ui.set("#RequestAsker.TextSpans", asker);
        ui.set(
                "#RequestState.TextSpans",
                Message.translation("hylens.menu.request.state").param("p0", state(r.state())));
        ui.set(
                "#RequestResolver.TextSpans",
                Message.translation("hylens.menu.request.resolver")
                        .param("p0", r.resolver().orElse("-")));
    }

    /** What the request asks for: its item's name and count, else its kind. */
    private static Message what(MenuView.RequestRow r) {
        Message thing = r.item().map(RequestsTab::item).orElse(kind(r.kind()));
        return Message.translation("hylens.menu.request.what")
                .param("p0", thing)
                .param("p1", String.valueOf(r.count()));
    }

    /** The hut that asks, by its block. */
    private static Message hut(Pos p) {
        return Message.translation("hylens.menu.request.hut")
                .param("p0", String.valueOf(p.x()))
                .param("p1", String.valueOf(p.y()))
                .param("p2", String.valueOf(p.z()));
    }

    /** {@code id}'s name in the player's language (the game's item key); its id for an item the game lacks. */
    private static Message item(String id) {
        Item asset = Item.getAssetMap().getAsset(id);
        return asset == null ? Message.raw(id) : Message.translation(asset.getTranslationKey());
    }

    private static Message kind(String kind) {
        return KINDS.contains(kind) ? Message.translation("hylens.menu.request.kind." + kind) : Message.raw(kind);
    }

    /** The state's text: one key per open state (MenuRequests.OPEN), the only ones the tab lists. */
    private static Message state(String state) {
        return Message.translation("hylens.menu.request.state." + state.toLowerCase(Locale.ROOT));
    }
}
