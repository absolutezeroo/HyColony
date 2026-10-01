package dev.hycolony.core.app.clipboard;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Optional;
import java.util.UUID;

/**
 * The clipboard item (MC ItemClipboard): used on a hut it notes that hut's colony, used anywhere else it opens the
 * noted colony's requests (WindowClipBoard). The plugin keeps the colony and the "!" state in the item.
 */
public final class ClipboardActions {
    private final ColonyManager manager;

    public ClipboardActions(ColonyManager manager) {
        this.manager = manager;
    }

    /**
     * MC ItemClipboard.useOn: on a hut ({@code pos} holds one of a colony's buildings), the colony to note, after
     * telling {@code player} {@code clipboard.registered} with its name; empty anywhere else (the item then opens).
     */
    public Optional<Integer> register(UUID player, BlockPos pos) {
        Optional<Colony> colony =
                manager.colonyAt(pos).filter(c -> c.buildings().at(pos).isPresent());
        colony.ifPresent(
                c -> manager.context().notifier().send(player, Msg.of("hycolony.clipboard.registered", c.name())));
        return colony.map(Colony::id);
    }

    /**
     * MC ItemClipboard.openWindow: without a noted colony, {@code clipboard.needcolony}; else the colony's requests
     * with the "!" state (nothing shows for a colony gone or one the player may not see, as MC finds no view).
     * Deviation from MC: the message goes to the chat, where MC shows it above the hotbar (the notifier has the chat
     * only).
     */
    public void open(UUID player, Optional<Integer> colonyId, boolean showImportant) {
        if (colonyId.isEmpty()) {
            manager.context().notifier().send(player, Msg.of("hycolony.clipboard.needcolony"));
            return;
        }
        manager.windows().openRequests(player, colonyId.get(), showImportant);
    }
}
