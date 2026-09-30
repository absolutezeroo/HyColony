package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.ApiVersion;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.plugin.api.HyColonyApi;
import dev.hylens.core.ApiCompatibility;
import dev.hylens.plugin.check.AutoCheckCommand;
import dev.hylens.plugin.check.CheckCommand;
import dev.hylens.plugin.send.MapSend;
import dev.hylens.plugin.send.SendCommand;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * /hylens and its subcommands, for operators only: no command sets a permission group, so each keeps the node
 * Hytale generates for it, which only the operators' "*" holds (AbstractCommand.setOwner, hasPermission).
 */
public final class HyLensCommand extends AbstractCommandCollection {
    public HyLensCommand(PluginBase owner, LensParts parts, MapSend map) {
        super("hylens", "HyLens, a debugging lens on HyColony (operators)");
        addSubCommand(new SelfTest());
        CitizenWatch watch = new CitizenWatch(owner, parts.watches(), parts.ids());
        addSubCommand(new WatchCommand(watch));
        addSubCommand(new UnwatchCommand(parts.watches()));
        addSubCommand(new MenuCommand(parts, watch, map));
        addSubCommand(new CheckCommand());
        addSubCommand(new AutoCheckCommand(parts.menus(), parts.alerts()));
        addSubCommand(new SendCommand(parts.watches(), parts.menus(), map));
    }

    /** Checks that HyColony's api answers in the player's world, and that HyLens runs with its version. */
    static final class SelfTest extends AbstractPlayerCommand {
        SelfTest() {
            super("selftest", "Check that HyColony's api answers HyLens in this world (operators)");
        }

        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            ApiVersion running = ApiVersion.CURRENT;
            if (ApiCompatibility.accepts(running)) {
                report(player, "api version", true, "");
            } else {
                Chat.tell(
                        player,
                        "hylens.selftest.versionMismatch",
                        running.toString(),
                        ApiCompatibility.BUILT_AGAINST.toString());
            }
            Optional<ColonyWorld> colonies;
            try {
                colonies = HyColonyApi.get().world(world);
            } catch (RuntimeException e) {
                report(player, "api", false, e.toString());
                return;
            }
            report(player, "api", colonies.isPresent(), "%hylens.notRunning");
        }

        /** Sends one line, OK or KO; {@code detail} is shown for a KO, translated when written "%key". */
        private static void report(PlayerRef player, String step, boolean ok, String detail) {
            if (ok) {
                Chat.tell(player, "hylens.selftest.ok", step);
            } else {
                Chat.tell(player, "hylens.selftest.ko", step, detail);
            }
        }
    }
}
