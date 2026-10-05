package dev.hyangler.plugin.cast;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * The rod's use, interaction type HyAngler_Cast (spec § 7.2), in two places of the rod's chain. With Reel, first: a
 * cast in progress is reeled in at once and the chain fails, so no charge starts; the client waits for the server's
 * answer (WaitForDataFrom.Server, as CheckUniqueItemUsageInteraction). Without, at the end of the Charging: the
 * bobber is thrown at the charged Power.
 */
public final class CastInteraction extends SimpleInstantInteraction {
    public static final BuilderCodec<CastInteraction> CODEC = BuilderCodec.builder(
                    CastInteraction.class, CastInteraction::new, SimpleInstantInteraction.CODEC)
            .appendInherited(
                    new KeyedCodec<>("Power", Codec.DOUBLE),
                    (o, v) -> o.power = v,
                    o -> o.power,
                    (o, p) -> o.power = p.power)
            .add()
            .appendInherited(
                    new KeyedCodec<>("Reel", Codec.BOOLEAN),
                    (o, v) -> o.reel = v,
                    o -> o.reel,
                    (o, p) -> o.reel = p.reel)
            .add()
            .build();
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static volatile boolean failed;

    private double power = 1.0;
    private boolean reel;

    /** The reeling step waits for the server's answer: the client must not start a charge the server refuses. */
    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return reel ? WaitForDataFrom.Server : super.getWaitForDataFrom();
    }

    /**
     * Reels in or throws for the user holding an unbroken rod; nothing before HyAngler's setup. Never throws: Hytale
     * removes the entity whose interaction fails (InteractionSystems.java:227-229), here the player.
     */
    @Override
    protected void firstRun(
            @Nonnull InteractionType type, @Nonnull InteractionContext ctx, @Nonnull CooldownHandler cooldown) {
        try {
            if (use(ctx)) {
                ctx.getState().state = InteractionState.Failed; // reeled in: no charge follows
            }
        } catch (RuntimeException e) {
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyAngler: the rod's use failed");
            failed = true;
        }
    }

    /** Reels in the user's cast in progress (true), or, at the charge's end, throws a new one (false). */
    private boolean use(InteractionContext ctx) {
        CommandBuffer<EntityStore> buffer = ctx.getCommandBuffer();
        Ref<EntityStore> user = ctx.getEntity();
        ItemStack rod = ctx.getHeldItem();
        Optional<CastParts.Parts> parts = CastParts.get();
        if (buffer == null || !user.isValid() || rod == null || rod.isBroken() || parts.isEmpty()) {
            return false;
        }
        UUIDComponent id = buffer.getComponent(user, UUIDComponent.getComponentType());
        return id != null && act(parts.get(), id.getUuid(), ctx, rod, buffer);
    }

    /** Reels in the player's cast here (true); else, at the charge's end and with no cast at all, throws one. */
    private boolean act(
            CastParts.Parts parts,
            UUID player,
            InteractionContext ctx,
            ItemStack rod,
            CommandBuffer<EntityStore> buffer) {
        Ref<EntityStore> user = ctx.getEntity();
        ActiveCast active = parts.casts().running(player);
        if (active != null && ours(active, user, rod)) {
            parts.landing().reel(active, ctx, buffer);
            return true;
        }
        if (active == null && !reel) {
            parts.thrower().cast(player, user, rod, power, buffer);
        }
        return false;
    }

    /**
     * Whether the cast is this user's here, with this rod: a cast left in another world (it ends on that world's
     * thread) or made with another rod is not reeled in from here.
     */
    private static boolean ours(ActiveCast cast, Ref<EntityStore> user, ItemStack rod) {
        return user.getStore().equals(cast.angler.getStore()) && rod.getItemId().equals(cast.rod.itemId());
    }

    /** Nothing in simulation: an entity without a remote client runs both (InteractionManager.java:509-530). */
    @Override
    protected void simulateFirstRun(
            @Nonnull InteractionType type, @Nonnull InteractionContext ctx, @Nonnull CooldownHandler cooldown) {}
}
