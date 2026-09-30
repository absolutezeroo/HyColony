package dev.hylens.plugin.send;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import dev.hycolony.api.ApiText;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.send.SendTarget;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.watch.ApiMessages;
import javax.annotation.Nonnull;
import org.joml.Vector3i;
import org.jspecify.annotations.Nullable;

/**
 * /hylens send [--map] (spec 2026-09-30, § 6.6): sends the citizen the operator watches, else the one chosen in their
 * menu, onto the block they aim at; with --map, onto the place their next map "Teleport" chooses.
 */
public final class SendCommand extends AbstractPlayerCommand {
    /** How far the aimed block may be, in blocks. */
    private static final double REACH_BLOCKS = 64.0;

    private final FlagArg byMap = withFlagArg("map", "Send by the map's Teleport instead of the aimed block");
    private final Watches watches;
    private final Menus menus;
    private final MapSend map;

    public SendCommand(Watches watches, Menus menus, MapSend map) {
        super("send", "Send the watched or chosen citizen walking to the aimed block, or by the map (operators)");
        this.watches = watches;
        this.menus = menus;
        this.map = map;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef player,
            @Nonnull World world) {
        ApiText told;
        if (byMap.get(ctx)) {
            map.arm(player.getUuid());
            told = ApiText.of("hylens.send.armed");
        } else {
            @Nullable Vector3i block = TargetUtil.getTargetBlock(ref, REACH_BLOCKS, store);
            told = block == null
                    ? ApiText.of("hylens.send.noTarget", String.valueOf((int) REACH_BLOCKS))
                    : SendHere.watchedOrChosen(
                            world, watches, menus, player.getUuid(), SendTarget.standingOn(block.x, block.y, block.z));
        }
        player.sendMessage(ApiMessages.of(told));
    }
}
