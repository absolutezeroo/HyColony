package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.app.view.CitizenInventoryView;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.item.HytaleStacks;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Fills the side panel of a citizen's inventory page (CitizenInventory.ui #CitizenPanel) from the core's view: its
 * name, the silhouettes of its empty armour slots, the items its hands hold and its stats, as the client's character
 * panel shows the player's.
 */
final class CitizenSidePanel {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The first unknown held item is a WARNING, the next ones FINE (CLAUDE.md § 4). */
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private static final String[] ARMOR_ICONS = {
        "#ArmorIconHead", "#ArmorIconChest", "#ArmorIconHands", "#ArmorIconLegs"
    };

    private CitizenSidePanel() {}

    /** Sets the panel's texts and held slots from {@code view}; an armour slot's silhouette shows while it is empty. */
    static void draw(UICommandBuilder ui, CitizenInventoryView view, ItemContainer armor, HytaleStacks stacks) {
        ui.set("#Name.Text", view.name());
        ui.set("#Health.Text", view.health() + "/" + view.maxHealth());
        ui.set("#Defense.Text", view.defensePercent() + "%");
        ui.set("#Hunger.Text", view.saturation() + "/" + view.maxSaturation());
        ui.set("#HeldSlots.ItemStacks", new ItemStack[] {stack(view.mainHand(), stacks), stack(view.offHand(), stacks)
        });
        for (short i = 0; i < ARMOR_ICONS.length; i++) {
            ItemStack piece = i < armor.getCapacity() ? armor.getItemStack(i) : null;
            ui.set(ARMOR_ICONS[i] + ".Visible", piece == null || piece.isEmpty());
        }
    }

    /**
     * The stack of {@code held}, an empty one for nothing (ItemStacks takes no null) or for an item the game no longer
     * knows (a removed pack: toStack throws), so that the page still opens.
     */
    private static ItemStack stack(Optional<ItemAmount> held, HytaleStacks stacks) {
        try {
            return held.map(stacks::toStack).orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            LOG.at(WARNED.getAndSet(true) ? Level.FINE : Level.WARNING).withCause(e).log(
                    "HyColony: a citizen's held item is unknown to the game, shown empty");
            return ItemStack.EMPTY;
        }
    }
}
