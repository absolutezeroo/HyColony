package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.PreviewPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The build tool ghost: the full plan of the chosen hut, level and rotation at the anchor, seen only by its player
 * (ST {@code share_previews=false}). Unlike the goggles, it is not compared with the world: Structurize draws the
 * whole blueprint.
 */
final class WandPreview {
    private static final String ID = "wand";

    private final ColonyManager manager;
    private final PreviewPort previews;

    WandPreview(ColonyManager manager, PreviewPort previews) {
        this.manager = manager;
        this.previews = previews;
    }

    /** Shows the session's plan without its air blocks; hides the ghost when no hut is chosen or the plan is gone. */
    void refresh(UUID player, WandSession s) {
        Optional<Blueprint> bp = s.hasBuilding() && s.anchor().isPresent()
                ? manager.context().ports().blueprints().load(s.style(), s.buildingTypeId(), s.level(), s.rotation())
                : Optional.empty();
        if (bp.isEmpty()) {
            hide(player);
            return;
        }
        ItemCatalog catalog = manager.context().ports().catalog();
        List<PreviewPort.Block> blocks = new ArrayList<>();
        for (BlueprintEntry e : bp.get().entries()) {
            if (catalog.kind(e.state().key()) != BlockKind.AIR) {
                blocks.add(new PreviewPort.Block(e.offset(), e.state()));
            }
        }
        previews.show(player, ID, s.anchor().get(), blocks);
    }

    /** Removes the player's ghost; nothing if there is none. */
    void hide(UUID player) {
        previews.hide(player, ID);
    }
}
