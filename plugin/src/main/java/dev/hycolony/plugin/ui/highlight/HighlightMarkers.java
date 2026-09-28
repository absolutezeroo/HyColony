package dev.hycolony.plugin.ui.highlight;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import dev.hycolony.core.kernel.BlockPos;
import javax.annotation.Nonnull;

/**
 * The world map marker of a player's highlight ({@link Highlights}), as the game's own providers add theirs
 * (SpawnMarkerProvider): whatever the distance, in the world it was asked in, while it lasts. Called on the world map
 * thread. The player is known by Player.getPlayerRef, deprecated but kept in the pinned 0.6.8 (the provider gets no
 * other handle on it).
 */
public final class HighlightMarkers implements WorldMapManager.MarkerProvider {
    /** The provider's key in a world's map manager. */
    public static final String KEY = "hycolony_highlight";

    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        Highlights.active(player.getPlayerRef().getUuid())
                .filter(a -> a.world().equals(world.getWorldConfig().getUuid()))
                .ifPresent(a -> {
                    BlockPos p = a.highlight().anchor();
                    collector.addIgnoreViewDistance(
                            new MapMarkerBuilder(KEY, "Coordinate.png", new Transform(p.x() + 0.5, p.y(), p.z() + 0.5))
                                    .withName(a.highlight().markerName())
                                    .build());
                });
    }
}
