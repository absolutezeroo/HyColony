package dev.hycolony.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ConstructionPorts;
import dev.hycolony.core.colony.Permissions;
import dev.hycolony.core.construction.Blueprint;
import dev.hycolony.core.construction.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.kernel.port.NavStatus;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** /hycolony info|rank (all players) and delete|selftest (operators: default permission node). */
public final class HyColonyCommand extends AbstractCommandCollection {
    private static final String PLAYERS = "hytale:Adventurer";

    public HyColonyCommand(WorldRuntimes runtimes, IdMap ids) {
        super("hycolony", "HyColony colony management");
        // No group on the collection: subcommands without one inherit it (putRecursivePermissionGroups).
        // Subcommands are dispatched before the collection's own permission is checked.
        addSubCommand(new Info(runtimes));
        addSubCommand(new Rank(runtimes));
        addSubCommand(new Delete(runtimes));
        addSubCommand(new SelfTest(runtimes, ids));
    }

    private static BlockPos where(Store<EntityStore> store, Ref<EntityStore> ref) {
        Vector3d p =
                store.getComponent(ref, TransformComponent.getComponentType()).getPosition();
        return new Vec3(p.x, p.y, p.z).toBlockPos();
    }

    private static void say(PlayerRef player, String key, String... params) {
        player.sendMessage(HytaleNotifier.toMessage(Msg.of(key, params)));
    }

    static final class Info extends AbstractPlayerCommand {
        private final WorldRuntimes runtimes;

        Info(WorldRuntimes runtimes) {
            super("info", "Colony at your position");
            this.runtimes = runtimes;
            setPermissionGroups(PLAYERS);
        }

        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            WorldRuntime rt = runtimes.of(world);
            Optional<Colony> colony =
                    rt == null ? Optional.empty() : rt.manager().colonyAt(where(store, ref));
            if (colony.isEmpty()) {
                say(player, "hycolony.cmd.noColony");
                return;
            }
            Colony c = colony.get();
            say(
                    player,
                    "hycolony.cmd.info",
                    c.name(),
                    String.valueOf(c.id()),
                    c.permissions().ownerName(),
                    c.state().name().toLowerCase(Locale.ROOT),
                    String.valueOf(c.day()),
                    String.valueOf(c.citizens().all().size()));
        }
    }

    static final class Rank extends AbstractPlayerCommand {
        private final WorldRuntimes runtimes;
        private final RequiredArg<PlayerRef> target;
        private final RequiredArg<String> rank;

        Rank(WorldRuntimes runtimes) {
            super("rank", "Set a player's rank in the colony you stand in");
            this.runtimes = runtimes;
            this.target = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.rank = withRequiredArg("rank", "officer|friend|neutral|hostile", ArgTypes.STRING);
            setPermissionGroups(PLAYERS); // the colony's EDIT_PERMISSIONS check is done by the core
        }

        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            WorldRuntime rt = runtimes.of(world);
            Optional<Colony> colony =
                    rt == null ? Optional.empty() : rt.manager().colonyAt(where(store, ref));
            if (colony.isEmpty()) {
                say(player, "hycolony.cmd.noColony");
                return;
            }
            int rankId = switch (ctx.get(rank).toLowerCase(Locale.ROOT)) {
                case "officer" -> Permissions.OFFICER;
                case "friend" -> Permissions.FRIEND;
                case "neutral" -> Permissions.NEUTRAL;
                case "hostile" -> Permissions.HOSTILE;
                default -> -1;
            };
            PlayerRef t = ctx.get(target);
            boolean ok = rankId >= 0
                    && rt.manager().setRank(player.getUuid(), colony.get().id(), t.getUuid(), t.getUsername(), rankId);
            say(player, ok ? "hycolony.cmd.rankSet" : "hycolony.cmd.rankFailed");
        }
    }

    /** Operators only (empty permission group list: needs the auto-generated node). */
    static final class Delete extends AbstractPlayerCommand {
        private final WorldRuntimes runtimes;
        private final RequiredArg<Integer> id;

        Delete(WorldRuntimes runtimes) {
            super("delete", "Delete a colony (operators)");
            this.runtimes = runtimes;
            this.id = withRequiredArg("id", "Colony id", ArgTypes.INTEGER);
            setPermissionGroups(); // explicit: no group ever grants it
        }

        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            WorldRuntime rt = runtimes.of(world);
            int colonyId = ctx.get(id);
            if (rt != null && rt.manager().byId(colonyId).isPresent()) {
                rt.manager().deleteColony(colonyId);
                say(player, "hycolony.cmd.deleted");
            } else {
                say(player, "hycolony.cmd.noColony");
            }
        }
    }

    /** Exercises each port against the live server (spec § 4.5). Operators only. */
    static final class SelfTest extends AbstractPlayerCommand {
        private final WorldRuntimes runtimes;
        private final IdMap ids;

        SelfTest(WorldRuntimes runtimes, IdMap ids) {
            super("selftest", "Check HyColony against this server (operators)");
            this.runtimes = runtimes;
            this.ids = ids;
            setPermissionGroups(); // explicit: no group ever grants it
        }

        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            WorldRuntime rt = runtimes.of(world);
            List<String> idErrors = ids.validate();
            report(player, "asset ids", idErrors.isEmpty(), String.join(", ", idErrors));
            if (rt == null) {
                report(player, "runtime", false, "no HyColony runtime for this world");
                return;
            }

            try {
                Path tmp = Files.createTempDirectory("hycolony-selftest");
                FileColonyStorage storage = new FileColonyStorage(tmp);
                storage.save(1, "{\"ok\":true}");
                report(
                        player,
                        "storage",
                        storage.load(1).map(o -> o.get("ok").getAsBoolean()).orElse(false),
                        "round trip");
            } catch (Exception e) {
                report(player, "storage", false, e.toString());
            }

            construction(player, rt, where(store, ref), ids);

            // Tag (-1, -1): if this body survives a crash, onBodyLoaded finds no colony -1 and despawns it.
            Optional<BodyId> body = rt.bodies().spawn(null, where(store, ref).offset(2, 0, 0), -1, -1, "SelfTest");
            report(player, "spawn", body.isPresent(), "spawnNPCWithColumnProbe");
            body.ifPresent(b -> {
                Vec3 start = rt.bodies().position(b).orElseThrow();
                rt.bodies().moveTo(b, new Vec3(start.x() + 3, start.y(), start.z()));
                long[] waited = {0};
                Runnable[] poll = new Runnable[1];
                poll[0] = () -> {
                    NavStatus s = rt.bodies().navStatus(b);
                    waited[0] += 500;
                    if (s == NavStatus.MOVING && waited[0] < 15_000) {
                        world.scheduleAfter(poll[0], 500, TimeUnit.MILLISECONDS);
                        return;
                    }
                    report(player, "move", s == NavStatus.ARRIVED, s.name());
                    rt.bodies().despawn(b);
                };
                world.scheduleAfter(poll[0], 500, TimeUnit.MILLISECONDS);
            });
        }

        /** Blueprint load, then place / container round trip / break of a builder hut 3 blocks above the player. */
        private static void construction(PlayerRef player, WorldRuntime rt, BlockPos at, IdMap ids) {
            ConstructionPorts ports = rt.manager().context().ports();
            try {
                Optional<Blueprint> bp =
                        ports.blueprints().load("outlander", ConstructionBuildingTypes.BUILDER.id(), 1, 0);
                report(
                        player,
                        "blueprint",
                        bp.isPresent() && !bp.get().entries().isEmpty(),
                        bp.map(b -> b.key() + " (" + b.entries().size() + " blocks)")
                                .orElse("outlander builder 1 missing"));

                BlockPos test = at.offset(0, 3, 0);
                boolean air = ports.blocks()
                        .get(test)
                        .map(st -> ports.catalog().kind(st.key()) == BlockKind.AIR)
                        .orElse(false);
                if (!air) {
                    report(player, "blocks", false, "the cell 3 blocks above you must be loaded air");
                    return;
                }
                BlockState hut =
                        new BlockState(new BlockKey(ids.blockId(ConstructionBuildingTypes.BUILDER.hutBlockKey())), 0);
                boolean placed = ports.blocks().place(test, hut, true)
                        && ports.blocks()
                                .get(test)
                                .map(st -> st.key().equals(hut.key()))
                                .orElse(false);
                report(player, "place", placed, "place " + hut.key().id());
                if (!placed) {
                    return;
                }
                List<BlockPos> box = List.of(test);
                ItemKey item = new ItemKey(ids.itemId(ConstructionBuildingTypes.BUILDER.hutBlockKey()));
                ItemAmount rest = ports.containers().insert(box, new ItemAmount(item, 1));
                boolean roundTrip = rest == null
                        && ports.containers().count(box, item) == 1
                        && ports.containers().extract(box, item, 1) == 1
                        && ports.containers().count(box, item) == 0;
                report(player, "container", roundTrip, "insert / count / extract");
                List<ItemAmount> drops = ports.blocks().breakBlock(test);
                boolean gone = ports.blocks()
                        .get(test)
                        .map(st -> ports.catalog().kind(st.key()) == BlockKind.AIR)
                        .orElse(false);
                report(player, "break", gone, "drops " + drops);
            } catch (RuntimeException e) {
                report(player, "construction", false, e.toString());
            }
        }

        private static void report(PlayerRef player, String step, boolean ok, String detail) {
            if (ok) {
                say(player, "hycolony.selftest.ok", step);
            } else {
                say(player, "hycolony.selftest.ko", step, detail);
            }
        }
    }
}
