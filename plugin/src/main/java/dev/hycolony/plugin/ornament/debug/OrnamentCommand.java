package dev.hycolony.plugin.ornament.debug;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.ornament.OrnamentShape;
import dev.hycolony.core.ornament.VariantKey;
import dev.hycolony.core.ornament.VariantRequests;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * /hyornament give &lt;shape&gt; &lt;material&gt; [--second &lt;material&gt;] and /hyornament shapes, operators
 * only: any Domum Ornamentum variant, created at runtime on first request, checked against DO's material tags.
 */
public final class OrnamentCommand extends AbstractCommandCollection {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Materials listed at most in a refusal: a stone tag holds hundreds. */
    private static final int LISTED = 12;

    /** @param registry where variants are created and cached */
    public OrnamentCommand(OrnamentVariantRegistry registry) {
        super("hyornament", "Domum Ornamentum variants (operators)");
        setPermissionGroups(new String[0]);
        addSubCommand(new Give(registry));
        addSubCommand(new Shapes(registry));
    }

    /** Sends the translated {@code key} with its parameters to the player (safe off the world thread). */
    static void say(PlayerRef player, String key, String... params) {
        player.sendMessage(HytaleNotifier.toMessage(Msg.of(key, params)));
    }

    /** The catalogs, or empty after telling the player the variants are not loaded. */
    private static Optional<OrnamentVariantRegistry.Catalogs> catalogs(OrnamentVariantRegistry registry, PlayerRef p) {
        Optional<OrnamentVariantRegistry.Catalogs> catalogs = registry.catalogs();
        if (catalogs.isEmpty()) {
            say(p, "hycolony.ornament.failed", "load");
        }
        return catalogs;
    }

    private static final class Give extends AbstractPlayerCommand {
        private final OrnamentVariantRegistry registry;
        private final RequiredArg<String> shapeArg;
        private final RequiredArg<String> firstArg;
        private final OptionalArg<String> secondArg;

        Give(OrnamentVariantRegistry registry) {
            super("give", "Give a Domum Ornamentum shape in chosen materials (operators)");
            this.registry = registry;
            this.shapeArg = withRequiredArg("shape", "Shape id (/hyornament shapes)", ArgTypes.STRING);
            this.firstArg = withRequiredArg("material", "First material (block id)", ArgTypes.STRING);
            this.secondArg = withOptionalArg("second", "Second material (block id)", ArgTypes.STRING);
            setPermissionGroups(new String[0]);
        }

        /** Checks the request, then requests the variant and gives it on the world thread once it exists. */
        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            Optional<OrnamentVariantRegistry.Catalogs> catalogs = catalogs(registry, player);
            if (catalogs.isEmpty()) {
                return;
            }
            Optional<OrnamentShape> shape = catalogs.get().shapes().shape(ctx.get(shapeArg));
            if (shape.isEmpty()) {
                say(player, "hycolony.ornament.unknownShape", ctx.get(shapeArg));
                return;
            }
            List<String> materials = new ArrayList<>(List.of(ctx.get(firstArg)));
            if (ctx.provided(secondArg)) {
                materials.add(ctx.get(secondArg));
            }
            switch (VariantRequests.check(
                    shape.get(), materials, catalogs.get().materials().tags())) {
                case VariantRequests.Refused refused -> refuse(player, shape.get(), refused);
                case VariantRequests.Accepted accepted -> give(player, ref, world, accepted.key());
            }
        }

        /**
         * Requests key's variant off the world thread, then hands it over on it; a failed creation or a stopping
         * world is logged and reported to the player.
         */
        private void give(PlayerRef player, Ref<EntityStore> ref, World world, VariantKey key) {
            boolean created = !registry.known(key);
            long start = System.nanoTime();
            VariantGift to = new VariantGift(player, ref);
            var _ = registry.request(List.of(key)).whenComplete((variants, error) -> {
                if (error != null) {
                    LOG.at(Level.SEVERE).withCause(error).log("hyornament: creating %s failed", key.id());
                    say(player, "hycolony.ornament.failed", "create");
                    return;
                }
                long ms = (System.nanoTime() - start) / 1_000_000;
                try {
                    world.execute(() -> to.give(variants.getFirst(), created, ms));
                } catch (RuntimeException e) { // the world no longer takes tasks (stopping)
                    LOG.at(Level.SEVERE).withCause(e).log("hyornament: cannot queue giving %s", key.id());
                    say(player, "hycolony.ornament.failed", "give");
                }
            });
        }

        /** Tells why the request is refused: the count a shape takes, or the slot and some materials it accepts. */
        private static void refuse(PlayerRef player, OrnamentShape shape, VariantRequests.Refused refused) {
            if (refused.slot() < 0) {
                // An optional second slot may be left out: one or two materials.
                String count = shape.optionalSecond() ? "1-" + shape.slotCount() : String.valueOf(shape.slotCount());
                say(player, refused.reasonKey(), shape.id(), count);
                return;
            }
            String allowed = refused.allowed().stream().sorted().limit(LISTED).collect(Collectors.joining(", "));
            String more = refused.allowed().size() > LISTED ? ", ..." : "";
            say(player, refused.reasonKey(), String.valueOf(refused.slot() + 1), allowed + more);
        }
    }

    private static final class Shapes extends AbstractPlayerCommand {
        private final OrnamentVariantRegistry registry;

        Shapes(OrnamentVariantRegistry registry) {
            super("shapes", "List the Domum Ornamentum shapes and their material tags (operators)");
            this.registry = registry;
            setPermissionGroups(new String[0]);
        }

        /** One line per shape: its id and the DO tag of each slot. */
        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            catalogs(registry, player)
                    .ifPresent(c -> c.shapes()
                            .all()
                            .forEach(shape -> say(
                                    player,
                                    "hycolony.ornament.shapes",
                                    shape.id(),
                                    String.join(" + ", shape.slotTags()))));
        }
    }
}
