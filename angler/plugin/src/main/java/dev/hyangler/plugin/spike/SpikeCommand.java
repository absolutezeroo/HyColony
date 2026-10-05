package dev.hyangler.plugin.spike;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Throwaway (plan task 2): /hyanglerspike gives the two spike rods, for operators. Its own command, as /hyangler only
 * comes with task 16; its item ids are written here, not in the id-map (CLAUDE.md § 7): this code goes with task 14.
 */
public final class SpikeCommand extends AbstractPlayerCommand {
    public SpikeCommand() {
        super("hyanglerspike", "Give HyAngler's spike rods (operators, throwaway test)");
        // No group: only the auto-generated node, which the operators' "*" holds (HyColonyCommand.groups).
        setPermissionGroups();
    }

    /** Gives rods A (tip pitched with the look) and B (a long last piece); an inventory without room takes nothing. */
    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        Player.giveItem(new ItemStack("HyAngler_Spike_Rod", 1), ref, store);
        Player.giveItem(new ItemStack("HyAngler_Spike_Rod_B", 1), ref, store);
    }
}
