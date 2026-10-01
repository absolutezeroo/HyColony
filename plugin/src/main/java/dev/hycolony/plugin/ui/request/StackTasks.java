package dev.hycolony.plugin.ui.request;

import com.hypixel.hytale.server.core.Message;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Requestable;
import java.util.Optional;

/**
 * MC IStackBasedTask: a courier delivery or a crafting request is shown as a prefix and its item with its count, as
 * the task lists and the request trees draw it.
 */
public final class StackTasks {
    private StackTasks() {}

    /** The task's item and count; empty for a request that is not a stack-based task. */
    public static Optional<ItemAmount> stack(Requestable requestable) {
        return switch (requestable) {
            case Delivery d -> Optional.of(d.stack());
            case Crafting c -> Optional.of(new ItemAmount(c.stack(), c.count()));
            default -> Optional.empty();
        };
    }

    /** MC getDisplayPrefix: "Delivery of:", or "%d * Recipe:" with the crafting request's minimum count. */
    public static Message prefix(Requestable requestable) {
        return requestable instanceof Crafting c
                ? Message.translation("hycolony.ui.tasks.prefix.crafting").param("p0", String.valueOf(c.minCount()))
                : Message.translation("hycolony.ui.tasks.prefix.delivery");
    }
}
