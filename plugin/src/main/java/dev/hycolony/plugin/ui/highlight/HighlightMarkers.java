package dev.hycolony.plugin.ui.highlight;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import dev.hycolony.core.kernel.BlockPos;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * The world map marker of a player's highlight ({@link Highlights}), as the game's own providers add theirs
 * (SpawnMarkerProvider): whatever the distance, in the world it was asked in, while it lasts. Called on the world map
 * thread. The player is known by Player.getPlayerRef, marked for removal but still what vanilla providers call in
 * 0.7.0 (the provider gets no other handle on it; Entity.getUuid is marked for removal too).
 */
public final class HighlightMarkers implements WorldMapManager.MarkerProvider {
    /** The provider's key in a world's map manager. */
    public static final String KEY = "hycolony_highlight";

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** The first failure is logged SEVERE, the next ones FINE: this runs for every player at every map update. */
    private boolean failed;

    /** Adds the player's marker; never throws, as the map thread does not catch (MapMarkerTracker.updatePoints). */
    @Override
    public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
        try {
            Highlights.active(player.getPlayerRef().getUuid())
                    .filter(a -> a.world().equals(world.getWorldConfig().getUuid()))
                    .ifPresent(a -> {
                        BlockPos p = a.highlight().anchor();
                        collector.addIgnoreViewDistance(new MapMarkerBuilder(
                                        KEY, "Coordinate.png", new Transform(p.x() + 0.5, p.y(), p.z() + 0.5))
                                .withName(a.highlight().markerName())
                                .build());
                    });
        } catch (RuntimeException e) {
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony highlight marker failed");
            failed = true;
        }
    }
}
