package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.plugin.api.HyColonyApi;
import dev.hylens.core.watch.CitizenPicker;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * /hylens watch &lt;name&gt;: the citizen of that name among the world's colonies; /hylens watch: the citizen in view,
 * as /spectate target picks it (spec 2026-09-30, § 6.1). Hytale picks a usage variant by its exact count of words
 * (AbstractCommand.checkForExecutingSubcommands), and a name has several: the name is this command's rest-of-line
 * argument, which takes one word or more, and the bare form is the variant of zero words.
 */
final class WatchCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> name =
            withRequiredArg("citizen", "The citizen's name, or part of it", ArgTypes.GREEDY_STRING);
    private final CitizenWatch watch;

    WatchCommand(CitizenWatch watch) {
        super("watch", "Follow the citizen named, or the one in view, and keep its history (operators)");
        this.watch = watch;
        addUsageVariant(new InView(watch));
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        Optional<ColonyWorld> colonies = colonies(player, world);
        if (colonies.isEmpty()) {
            return;
        }
        ColonyWorld w = colonies.get();
        List<CitizenSnapshot> citizens =
                w.colonies().stream().flatMap(c -> w.citizens(c.ref()).stream()).toList();
        String query = name.get(ctx);
        switch (CitizenPicker.pick(citizens, query)) {
            case CitizenPicker.Found(CitizenSnapshot c) -> watch.start(player, store, ref, c.ref(), c.name());
            case CitizenPicker.NotFound() -> Chat.tell(player, "hylens.watch.notFound", query);
            case CitizenPicker.Ambiguous(List<String> names) ->
                Chat.tell(player, "hylens.watch.ambiguous", String.join(", ", names));
        }
    }

    /** HyColony in {@code world}; empty, and the player told, where it does not run or has stopped. */
    static Optional<ColonyWorld> colonies(PlayerRef player, World world) {
        Optional<ColonyWorld> colonies = worldOf(world);
        if (colonies.isEmpty()) {
            Chat.tell(player, "hylens.notRunning");
        }
        return colonies;
    }

    /** HyColony in {@code world}; empty where it does not run or has stopped. World thread. */
    static Optional<ColonyWorld> worldOf(World world) {
        try {
            return HyColonyApi.get().world(world);
        } catch (IllegalStateException e) {
            return Optional.empty(); // HyColony stopped: its api holder is empty
        }
    }

    /** /hylens watch: the citizen in view within {@link #TARGET_RADIUS} blocks. */
    static final class InView extends AbstractPlayerCommand {
        /** Blocks within which the citizen in view is picked: /spectate target's radius. */
        private static final float TARGET_RADIUS = 32.0F;

        private final CitizenWatch watch;

        InView(CitizenWatch watch) {
            super("Follow the citizen in view, and keep its history (operators)");
            this.watch = watch;
        }

        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            Optional<ColonyWorld> colonies = colonies(player, world);
            if (colonies.isEmpty()) {
                return;
            }
            @Nullable Ref<EntityStore> target = TargetUtil.getTargetEntity(ref, TARGET_RADIUS, false, store);
            if (target == null) {
                Chat.tell(player, "hylens.watch.noTarget");
                return;
            }
            HyColonyApi.get()
                    .citizenOf(target, store)
                    .flatMap(c -> colonies.get().citizen(c))
                    .ifPresentOrElse(
                            c -> watch.start(player, store, ref, c.ref(), c.name()),
                            () -> Chat.tell(player, "hylens.watch.notCitizen"));
        }
    }
}
