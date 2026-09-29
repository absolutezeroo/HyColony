package dev.hycolony.core.app.goggles;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.PlayerDirectory;
import dev.hycolony.core.kernel.port.PreviewPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The previews of every build goggles wearer in one world (MC ItemBuildGoggles + ColonyBlueprintRenderer). A site's
 * preview appears when the goggles go on or the player comes within range, is recreated every
 * {@link #REFRESH_TICKS} if its remaining blocks changed, and goes when its order ends or the goggles come off.
 *
 * <p>Deviation from MC: MC redraws every frame from a cache rebuilt when the player moves 12.5 blocks; a Hytale
 * preview is resent whole, so it is rebuilt in steps, never at every block.
 */
public final class BuildGoggles {
    /** How often wearers are checked for sites entering or leaving range, in ticks. */
    static final int CHECK_INTERVAL_TICKS = 20;
    /** Minimum age of a shown preview before its remaining blocks are compared again, in ticks. */
    static final int REFRESH_TICKS = 100;

    private record Shown(List<PreviewPort.Block> blocks, long tick) {}

    private final GogglesView view;
    private final PlayerDirectory players;
    private final PreviewPort previews;
    private final Map<UUID, Map<String, Shown>> wearers = new HashMap<>();
    private long ticks;

    public BuildGoggles(ColonyManager manager, PreviewPort previews) {
        this.view = new GogglesView(manager);
        this.players = manager.context().players();
        this.previews = previews;
    }

    /** The player put the goggles on (or is read wearing them): every visible site is shown. Nothing if already on. */
    public void equip(UUID player) {
        Map<String, Shown> shown = new HashMap<>();
        if (wearers.putIfAbsent(player, shown) == null) {
            update(player, shown);
        }
    }

    /** The player took the goggles off or left: their previews are removed. Nothing if they did not wear any. */
    public void unequip(UUID player) {
        if (wearers.remove(player) != null) {
            previews.hideAll(player);
        }
    }

    /** One core tick; every {@link #CHECK_INTERVAL_TICKS}, updates each wearer. */
    public void tick() {
        ticks++;
        if (ticks % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        for (Map.Entry<UUID, Map<String, Shown>> wearer : new ArrayList<>(wearers.entrySet())) {
            update(wearer.getKey(), wearer.getValue());
        }
    }

    /** Hides the sites gone out of view, shows the new ones and refreshes the old ones that changed. */
    private void update(UUID player, Map<String, Shown> shown) {
        Optional<BlockPos> pos = players.position(player);
        if (pos.isEmpty()) { // left the world: the plugin re-equips on the way back
            unequip(player);
            return;
        }
        List<GogglesView.Site> sites = view.visible(pos.get());
        Set<String> ids = new HashSet<>();
        sites.forEach(s -> ids.add(s.id()));
        for (String id : new ArrayList<>(shown.keySet())) {
            if (!ids.contains(id)) {
                shown.remove(id);
                previews.hide(player, id);
            }
        }
        for (GogglesView.Site site : sites) {
            Shown before = shown.get(site.id());
            if (before == null || ticks - before.tick() >= REFRESH_TICKS) {
                List<PreviewPort.Block> blocks = view.remaining(site);
                if (before == null || !blocks.equals(before.blocks())) {
                    previews.show(player, site.id(), site.order().buildingPos(), blocks);
                }
                shown.put(site.id(), new Shown(blocks, ticks));
            }
        }
    }
}
