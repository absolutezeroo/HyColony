package dev.hyangler.plugin.spike;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Throwaway (animation spike, fishing-hytale.md § 7.6): /hyanglerspikeanim &lt;entry|stop&gt; plays an entry of the
 * HyAngler_Rod set on the player's Action slot from Java, sent to the player too, or stops it; for operators. Goes
 * with task 14, as SpikeCommand.
 */
public final class SpikeAnimCommand extends AbstractPlayerCommand {
    private static final String SET = "HyAngler_Rod";
    private final RequiredArg<String> entry;

    public SpikeAnimCommand() {
        super("hyanglerspikeanim", "Play an entry of HyAngler's rod animations on yourself (operators, throwaway)");
        this.entry =
                withRequiredArg("entry", "an entry of HyAngler_Rod (Idle, Cast, FightHeavy…) or stop", ArgTypes.STRING);
        setPermissionGroups();
    }

    /**
     * Plays the entry, sent unchecked (the Action slot skips the model check, AnimationUtils.java:44-48), or, for
     * "stop", clears the Action slot and gives the player's camera back (a cast's lock left behind, SpikeCamera).
     */
    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        String id = ctx.get(entry);
        if ("stop".equals(id)) {
            AnimationUtils.stopAnimation(ref, AnimationSlot.Action, true, store);
            SpikeCamera.release(ref, store);
        } else {
            AnimationUtils.playAnimation(ref, AnimationSlot.Action, SET, id, true, store);
        }
    }
}
