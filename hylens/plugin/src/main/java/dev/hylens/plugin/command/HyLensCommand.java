package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.Texts;
import dev.hycolony.api.ApiVersion;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.plugin.api.HyColonyApi;
import dev.hylens.core.ApiCompatibility;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * /hylens and its subcommands, for operators only: no command sets a permission group, so each keeps the node
 * Hytale generates for it, which only the operators' "*" holds (AbstractCommand.setOwner, hasPermission).
 */
public final class HyLensCommand extends AbstractCommandCollection {
    public HyLensCommand() {
        super("hylens", "HyLens, a debugging lens on HyColony (operators)");
        addSubCommand(new SelfTest());
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
            report(
                    player,
                    "api version",
                    ApiCompatibility.accepts(running),
                    "HyColony's api " + running + ", HyLens built against " + ApiCompatibility.BUILT_AGAINST);
            Optional<ColonyWorld> colonies;
            try {
                colonies = HyColonyApi.get().world(world);
            } catch (RuntimeException e) {
                report(player, "api", false, e.toString());
                return;
            }
            report(player, "api", colonies.isPresent(), "%hylens.selftest.notRunning");
        }

        /** Sends one line, OK or KO; {@code detail} is shown for a KO, translated when written "%key". */
        private static void report(PlayerRef player, String step, boolean ok, String detail) {
            player.sendMessage(
                    ok
                            ? Texts.translated("hylens.selftest.ok", List.of(step))
                            : Texts.translated("hylens.selftest.ko", List.of(step, detail)));
        }
    }
}
