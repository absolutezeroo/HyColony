package dev.hycolony.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
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
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.block.HytaleBlockStates;
import dev.hycolony.plugin.subplugin.SubPlugins;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * /hycolony info|rank|delete and selftest. Operators run them all (their group holds "*"); non-operators run info,
 * rank and delete only when config Commands allows it (MC canPlayerUse...Command), selftest never.
 *
 * <p>Deviation from MC: a refused non-operator gets Hytale's own "no permission" answer instead of MC's "This command
 * is disabled in the config", since the config picks the command's permission group.
 */
public final class HyColonyCommand extends AbstractCommandCollection {
    private static final String PLAYERS = "hytale:Adventurer";

    public HyColonyCommand(WorldRuntimes runtimes, IdMap ids, ColonyConfig.Commands config, SubPlugins packs) {
        super("hycolony", "HyColony colony management");
        // No group on the collection: subcommands without one inherit it (putRecursivePermissionGroups).
        // Subcommands are dispatched before the collection's own permission is checked.
        addSubCommand(new Info(runtimes, config.canPlayerUseShowColonyInfoCommand()));
        addSubCommand(new Rank(runtimes, config.canPlayerUseAddOfficerCommand()));
        addSubCommand(new Delete(runtimes, config.canPlayerUseDeleteColonyCommand()));
        addSubCommand(new SelfTest(runtimes, ids, packs));
    }

    /** Every player, or operators only: an empty group list leaves only the auto-generated node, held by "*". */
    private static String[] groups(boolean players) {
        return players ? new String[] {PLAYERS} : new String[0];
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

        Info(WorldRuntimes runtimes, boolean players) {
            super("info", "Colony at your position");
            this.runtimes = runtimes;
            setPermissionGroups(groups(players));
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

        Rank(WorldRuntimes runtimes, boolean players) {
            super("rank", "Set a player's rank in the colony you stand in");
            this.runtimes = runtimes;
            this.target = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.rank = withRequiredArg("rank", "officer|friend|neutral|hostile", ArgTypes.STRING);
            setPermissionGroups(groups(players)); // the colony's EDIT_PERMISSIONS check is done by the core
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
            if (rt == null || colony.isEmpty()) {
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
                    && rt.manager()
                            .administration()
                            .setRank(player.getUuid(), colony.get().id(), t.getUuid(), t.getUsername(), rankId);
            say(player, ok ? "hycolony.cmd.rankSet" : "hycolony.cmd.rankFailed");
        }
    }

    /** Deletes a colony by id; the core checks the sender is an operator or a manager of that colony. */
    static final class Delete extends AbstractPlayerCommand {
        private final WorldRuntimes runtimes;
        private final RequiredArg<Integer> id;

        Delete(WorldRuntimes runtimes, boolean players) {
            super("delete", "Delete a colony (operators, or its managers if the config allows)");
            this.runtimes = runtimes;
            this.id = withRequiredArg("id", "Colony id", ArgTypes.INTEGER);
            setPermissionGroups(groups(players));
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
            if (rt == null || rt.manager().byId(colonyId).isEmpty()) {
                say(player, "hycolony.cmd.noColony");
            } else if (rt.manager().administration().delete(player.getUuid(), colonyId)) {
                say(player, "hycolony.cmd.deleted");
            } else {
                say(player, "hycolony.cmd.deleteFailed");
            }
        }
    }

    /** Exercises each port against the live server (spec § 4.5). Operators only. */
    static final class SelfTest extends AbstractPlayerCommand {

        private final WorldRuntimes runtimes;
        private final IdMap ids;
        private final SubPlugins packs;

        SelfTest(WorldRuntimes runtimes, IdMap ids, SubPlugins packs) {
            super("selftest", "Check HyColony against this server (operators)");
            this.runtimes = runtimes;
            this.ids = ids;
            this.packs = packs;
            setPermissionGroups(groups(false));
        }

        @Override
        protected void execute(
                @Nonnull CommandContext ctx,
                @Nonnull Store<EntityStore> store,
                @Nonnull Ref<EntityStore> ref,
                @Nonnull PlayerRef player,
                @Nonnull World world) {
            SelfTestReport out = (step, ok, detail) -> report(player, step, ok, detail);
            WorldRuntime rt = runtimes.of(world);
            List<String> idErrors = ids.validate();
            out.line("asset ids", idErrors.isEmpty(), String.join(", ", idErrors));
            SubPluginsSelfTest.run(out, packs);
            if (rt == null) {
                out.line("runtime", false, "no HyColony runtime for this world");
                return;
            }
            BlockPos at = where(store, ref);
            storage(out);
            construction(player, rt, at, ids);
            blockKeys(player, ids);
            HutTypesSelfTest.run(out, rt, ids);
            LogisticsSelfTest.run(out, rt, at);
            BodySelfTest.run(out, rt, world, at);
        }

        /** A colony file written then read back in a temporary folder. */
        private static void storage(SelfTestReport out) {
            try {
                FileColonyStorage storage = new FileColonyStorage(Files.createTempDirectory("hycolony-selftest"));
                storage.save(1, "{\"ok\":true}");
                out.line(
                        "storage",
                        storage.load(1).map(o -> o.get("ok").getAsBoolean()).orElse(false),
                        "round trip");
            } catch (Exception e) {
                out.line("storage", false, e.toString());
            }
        }

        /**
         * Blueprint load, then place / container round trip / break of a vanilla chest 3 blocks above the player. Not
         * a hut: breakBlock leaves huts alone (only the hut systems remove them), so a hut would stay there.
         */
        private static void construction(PlayerRef player, WorldRuntime rt, BlockPos at, IdMap ids) {
            ConstructionPorts ports = rt.manager().context().ports();
            try {
                blueprint(player, ports);
                BlockPos test = at.offset(0, 3, 0);
                boolean air = ports.blocks()
                        .get(test)
                        .map(st -> ports.catalog().kind(st.key()) == BlockKind.AIR)
                        .orElse(false);
                if (!air) {
                    report(player, "blocks", false, "the cell 3 blocks above you must be loaded air");
                    return;
                }
                BlockState chest = new BlockState(new BlockKey(ids.blockId("selftest.chest")), 0);
                boolean placed = ports.blocks().place(test, chest, true)
                        && ports.blocks()
                                .get(test)
                                .map(st -> st.key().equals(chest.key()))
                                .orElse(false);
                report(player, "place", placed, "place " + chest.key().id());
                if (!placed) {
                    return;
                }
                containerThenBreak(player, ports, test, ids);
            } catch (RuntimeException e) {
                report(player, "construction", false, e.toString());
            }
        }

        /** Loads the first style's level 1 builder hut. */
        private static void blueprint(PlayerRef player, ConstructionPorts ports) {
            // Styles come from sub-plugins that may all be disabled: then there is nothing to load.
            String style = ports.blueprints().styles().stream().findFirst().orElse("(no style)");
            Optional<Blueprint> bp = ports.blueprints().load(style, ConstructionBuildingTypes.BUILDER.id(), 1, 0);
            report(
                    player,
                    "blueprint",
                    bp.isPresent() && !bp.get().entries().isEmpty(),
                    bp.map(b -> b.key() + " (" + b.entries().size() + " blocks)")
                            .orElse(style + " builder 1 missing"));
        }

        /** Container round trip in the chest placed at {@code test}, then its break and drops. */
        private static void containerThenBreak(PlayerRef player, ConstructionPorts ports, BlockPos test, IdMap ids) {
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
            report(player, "break", gone && !drops.isEmpty(), "drops " + drops);
        }

        /** A stair corner keeps its variant id through blockKey; an open door reads as its base block. */
        private static void blockKeys(PlayerRef player, IdMap ids) {
            String corner = ids.blockId("selftest.stair_corner");
            String door = ids.blockId("selftest.door_open");
            BlockType cornerType = BlockType.getAssetMap().getAsset(corner);
            BlockType doorType = BlockType.getAssetMap().getAsset(door);
            boolean ok = cornerType != null
                    && doorType != null
                    && corner.equals(HytaleBlockStates.blockKey(cornerType))
                    && ids.blockId("selftest.door").equals(HytaleBlockStates.blockKey(doorType));
            report(player, "block keys", ok, corner + " kept, " + door + " normalized");
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
