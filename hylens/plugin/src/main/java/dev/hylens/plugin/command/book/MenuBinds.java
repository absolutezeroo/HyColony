package dev.hylens.plugin.command.book;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

/**
 * Binds the HyLens menu's buttons. Each click sends an "Action" and in "Index" the id of its colony or citizen, its
 * layer's or tab's name; on the Citizens tab it also carries the "send here" fields as "@X", "@Y" and "@Z", so the
 * redraw that follows keeps what was typed. Other tabs have no such fields, so their clicks carry none.
 */
record MenuBinds(UIEventBuilder events, boolean cell) {
    /** Binds the button at {@code selector} to {@code action} on {@code index}. */
    void on(String selector, String action, String index) {
        EventData data = EventData.of("Action", action).append("Index", index);
        if (cell) {
            data = data.append("@X", "#SendX.Value")
                    .append("@Y", "#SendY.Value")
                    .append("@Z", "#SendZ.Value");
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, data, false);
    }
}
