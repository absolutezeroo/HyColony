package dev.hylens.plugin.command;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.Pos;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.send.SendTarget;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.command.book.MenuRender;
import dev.hylens.plugin.send.MapSend;
import dev.hylens.plugin.send.SendHere;
import dev.hylens.plugin.watch.ApiMessages;
import java.util.Optional;

/**
 * The menu's "send here" (spec 2026-09-30, § 6.6): to the cell typed, or by the map, the citizen watched else the one
 * chosen. Keeps the cell last typed while its page is open: clicks on the Citizens tab carry the fields, so a redraw
 * shows them again; other clicks leave them as they were. World thread.
 */
final class MenuSend {
    /** The cell's fields, as typed. */
    record Typed(String x, String y, String z) {}

    private final MapSend map;
    private final Watches watches;
    private final Menus menus;
    private Optional<Typed> typed = Optional.empty();

    MenuSend(MapSend map, Watches watches, Menus menus) {
        this.map = map;
        this.watches = watches;
        this.menus = menus;
    }

    /** Keeps the fields {@code data} carries; a click from a page without them changes nothing. */
    void remember(MenuPage.Data data) {
        if (data.x != null && data.y != null && data.z != null) {
            typed = Optional.of(new Typed(data.x, data.y, data.z));
        }
    }

    /**
     * What the Citizens tab shows: the fields as last typed, else the operator's {@code feet} when the page opens, and
     * whether their camera is free ({@code cameraFree}).
     */
    MenuRender.Watch shown(Pos feet, boolean cameraFree) {
        Typed cell = typed.orElseGet(
                () -> new Typed(String.valueOf(feet.x()), String.valueOf(feet.y()), String.valueOf(feet.z())));
        return new MenuRender.Watch(cell.x(), cell.y(), cell.z(), cameraFree);
    }

    /** Arms {@code player}'s map, and tells them to open it. */
    void byMap(PlayerRef player) {
        map.arm(player.getUuid());
        player.sendMessage(ApiMessages.of(ApiText.of("hylens.send.armed")));
    }

    /** Sends the citizen walking to the cell last typed, in {@code player}'s name; the text to show, cell included. */
    ApiText toCell(PlayerRef player, World world) {
        Optional<Pos> cell = typed.flatMap(t -> SendTarget.parse(t.x(), t.y(), t.z()));
        if (cell.isEmpty()) {
            return ApiText.of("hylens.send.badCell");
        }
        return SendHere.watchedOrChosen(world, watches, menus, player.getUuid(), cell.get());
    }
}
