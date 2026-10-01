package dev.hylens.plugin.perf;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.debug.PartTiming;
import dev.hylens.core.perf.PerfDump;
import dev.hylens.core.perf.PerfReport;
import dev.hylens.plugin.HyColonyAccess;
import dev.hylens.plugin.watch.ApiMessages;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * /hylens perf [--dump]: what costs the world's ticks over the last minute, Hytale's systems then HyColony's own parts
 * (spec 2026-09-30, § 6.7); with --dump, every system and part in a file, as /server dump writes Hytale's.
 */
public final class PerfCommand extends AbstractPlayerCommand {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final FlagArg dump = withFlagArg("dump", "Write every system and part to a file in HyLens's data folder");
    private final PluginBase owner;

    public PerfCommand(PluginBase owner) {
        super("perf", "What costs this world's ticks: Hytale's systems, then HyColony's parts (operators)");
        this.owner = owner;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        Optional<List<PartTiming>> colony =
                HyColonyAccess.world(world).map(w -> w.debug().timings());
        if (dump.get(ctx)) {
            player.sendMessage(ApiMessages.of(write(world, colony)));
            return;
        }
        PerfReport.lines(HytaleTimings.tick(world), HytaleTimings.systems(world), colony)
                .forEach(line -> player.sendMessage(ApiMessages.of(line)));
    }

    /**
     * Writes the dump to {@code <data folder>/perf/<world>_<time>.txt}, on the world's thread (a few kilobytes, as
     * /server dump writes its own); the line telling where, or why it failed.
     */
    private ApiText write(World world, Optional<List<PartTiming>> colony) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        // A world's name is not checked as a file name: anything else than a plain character becomes "_".
        String name = world.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            Path file = owner.getDataDirectory().resolve("perf").resolve(name + "_" + FILE_TIME.format(now) + ".txt");
            Files.createDirectories(file.getParent());
            Files.write(file, PerfDump.lines(HytaleTimings.snapshot(world, now.toString(), colony)));
            return ApiText.of("hylens.perf.dumped", file.toAbsolutePath().toString());
        } catch (IOException | InvalidPathException e) {
            return ApiText.of("hylens.perf.dumpFailed", String.valueOf(e.getMessage()));
        }
    }
}
