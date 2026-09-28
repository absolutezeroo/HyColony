package dev.hycolony.plugin.ornament.debug;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ornament.api.OrnamentMaterial;
import dev.hycolony.plugin.ornament.api.OrnamentShape;
import dev.hycolony.plugin.ornament.api.VariantKey;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import dev.hycolony.plugin.ornament.runtime.BlockTypeSynchronizer.Rebuild;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * /hyornament test &lt;primary&gt; &lt;secondary&gt; [--rebuild=none|editor|all] [--twice=true|false]
 * [--icon=generated|material|none] [--notify=true|false] [--iconrefresh=true|false], operators only: gets the timber
 * frame variant from the registry (created at runtime on first request) and gives the player a stack of its item.
 *
 * <p>Experiment for Domum Ornamentum (docs/research/domum-ornamentum.md B.11): does a client render a BlockType added
 * at runtime from an already loaded model and textures, without {@code RequestCommonAssetsRebuild}?
 */
public final class OrnamentCommand extends AbstractCommandCollection {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    /** @param registry where variants are created and cached */
    public OrnamentCommand(OrnamentVariantRegistry registry) {
        super("hyornament", "Runtime Domum Ornamentum variants (operators)");
        setPermissionGroups(new String[0]);
        addSubCommand(new Test(registry));
    }

    /** Sends the translated {@code key} with its parameters to the player (safe off the world thread). */
    static void say(PlayerRef player, String key, String... params) {
        player.sendMessage(HytaleNotifier.toMessage(Msg.of(key, params)));
    }

    private static final class Test extends AbstractPlayerCommand {
        private final OrnamentVariantRegistry registry;
        private final RequiredArg<String> primary;
        private final RequiredArg<String> secondary;
        private final DefaultArg<String> rebuildArg;
        private final DefaultArg<Boolean> twiceArg;
        private final DefaultArg<String> iconArg;
        private final DefaultArg<Boolean> notifyArg;
        private final DefaultArg<Boolean> iconRefreshArg;

        Test(OrnamentVariantRegistry registry) {
            super("test", "Create or reuse a timber frame variant and give it (operators)");
            this.registry = registry;
            this.primary = withRequiredArg("primary", "Frame material", ArgTypes.STRING);
            this.secondary = withRequiredArg("secondary", "Fill material", ArgTypes.STRING);
            // Which client caches UpdateBlockTypes asks to rebuild when the variant is new (in-game experiment).
            this.rebuildArg = withDefaultArg("rebuild", "none|editor|all", ArgTypes.STRING, "none", "none");
            // --twice=false sends UpdateBlockTypes once, to compare with the double-send workaround.
            this.twiceArg = withDefaultArg("twice", "Send UpdateBlockTypes twice", ArgTypes.BOOLEAN, true, "true");
            // --icon=material|none keeps a vanilla icon or none, to compare with the variant's generated icon.
            this.iconArg = withDefaultArg("icon", "generated|material|none", ArgTypes.STRING, "generated", "generated");
            // --notify=true registers a generated icon through addCommonAsset, with its "asset created" notification;
            // the silent default works in game (2026-09-28).
            this.notifyArg = withDefaultArg("notify", "Asset notification", ArgTypes.BOOLEAN, false, "false");
            // --iconrefresh=false sends UpdateItems without updateIcons: the icon then stays missing (in game,
            // 2026-09-28), so the short freeze of the refresh is its price.
            this.iconRefreshArg =
                    withDefaultArg("iconrefresh", "UpdateItems.updateIcons", ArgTypes.BOOLEAN, true, "true");
            setPermissionGroups(new String[0]);
        }

        /** Parses the arguments, requests the variant and gives it on the world thread once it exists. */
        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            Optional<OrnamentMaterial> frame = material(ctx.get(primary), player);
            Optional<OrnamentMaterial> fill = material(ctx.get(secondary), player);
            Optional<Rebuild> mode = rebuild(ctx.get(rebuildArg), player);
            Optional<OrnamentVariantRegistry.Icon> icon = icon(ctx.get(iconArg), player);
            if (frame.isEmpty() || fill.isEmpty() || mode.isEmpty() || icon.isEmpty()) {
                return;
            }
            VariantKey key = new VariantKey(OrnamentShape.TIMBER_FRAME, frame.get(), fill.get());
            VariantGift to = new VariantGift(player, ref);
            long start = System.nanoTime();
            OrnamentVariantRegistry.Creation creation = new OrnamentVariantRegistry.Creation(
                    mode.get(), ctx.get(twiceArg), icon.get(), ctx.get(notifyArg), ctx.get(iconRefreshArg));
            OrnamentVariantRegistry.Request request = registry.request(key, creation);
            var _ = request.variant().whenComplete((variant, error) -> {
                if (error != null) {
                    LOG.at(Level.SEVERE).withCause(error).log("hyornament: creating %s failed", key.id());
                    say(player, "hycolony.ornament.failed", "create");
                    return;
                }
                long ms = (System.nanoTime() - start) / 1_000_000;
                try {
                    world.execute(() -> to.give(variant, request.created(), ms, lower(mode.get())));
                } catch (RuntimeException e) { // the world no longer takes tasks (stopping)
                    LOG.at(Level.SEVERE).withCause(e).log("hyornament: cannot queue giving %s", key.id());
                    say(player, "hycolony.ornament.failed", "give");
                }
            });
        }

        /** The material named {@code name}; when unknown, tells the player the valid names and returns empty. */
        private static Optional<OrnamentMaterial> material(String name, PlayerRef player) {
            Optional<OrnamentMaterial> material = OrnamentMaterial.parse(name);
            if (material.isEmpty()) {
                say(player, "hycolony.ornament.badMaterial", name, OrnamentMaterial.names());
            }
            return material;
        }

        /** The rebuild mode named {@code name}; when unknown, tells the player the valid names and returns empty. */
        private static Optional<Rebuild> rebuild(String name, PlayerRef player) {
            Optional<Rebuild> mode = Arrays.stream(Rebuild.values())
                    .filter(r -> r.name().equalsIgnoreCase(name))
                    .findFirst();
            if (mode.isEmpty()) {
                String names = Arrays.stream(Rebuild.values()).map(Test::lower).collect(Collectors.joining(", "));
                say(player, "hycolony.ornament.badRebuild", name, names);
            }
            return mode;
        }

        /** The icon mode named {@code name}; when unknown, tells the player the valid names and returns empty. */
        private static Optional<OrnamentVariantRegistry.Icon> icon(String name, PlayerRef player) {
            Optional<OrnamentVariantRegistry.Icon> icon = Arrays.stream(OrnamentVariantRegistry.Icon.values())
                    .filter(i -> i.name().equalsIgnoreCase(name))
                    .findFirst();
            if (icon.isEmpty()) {
                say(player, "hycolony.ornament.badIcon", name, "generated, material, none");
            }
            return icon;
        }

        private static String lower(Rebuild mode) {
            return mode.name().toLowerCase(Locale.ROOT);
        }
    }
}
