package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.WandView;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the build tool window's view (ST WindowExtendedBuildTool): the pack's folders from the huts that have a plan
 * in the style, the open folder's subfolders or huts, and each hut's lock (ST AbstractBlockHut.getRequirements).
 */
final class WandViews {
    private final ColonyManager manager;
    private final Function<String, ItemKey> hutItem;

    WandViews(ColonyManager manager, Function<String, ItemKey> hutItem) {
        this.manager = manager;
        this.hutItem = hutItem;
    }

    /**
     * The view of {@code s} for {@code player}; {@code tip} shows Structurize's hint. At the root, nothing below the
     * category icons; in a folder, its subfolders, or its huts when it has none.
     */
    WandView of(UUID player, WandSession s, boolean tip) {
        WandTree tree = tree(s.style());
        List<String> folders = s.depth().isEmpty() ? List.of() : tree.children(s.depth());
        List<WandView.Hut> huts = s.depth().isEmpty() || !folders.isEmpty()
                ? List.of()
                : tree.huts(s.depth()).stream()
                        .map(id -> new WandView.Hut(id, id.equals(s.buildingTypeId()), requirements(player, s, id)))
                        .toList();
        boolean canConfirm =
                s.hasBuilding() && requirements(player, s, s.buildingTypeId()).isEmpty();
        return new WandView(
                s.style(),
                s.style().isEmpty() ? "" : blueprints().pack(s.style()).name(),
                s.depth(),
                tree.roots(),
                folders,
                huts,
                s.buildingTypeId(),
                s.level(),
                maxLevel(s),
                s.rotation(),
                s.hasBuilding(),
                canConfirm,
                manager.context().players().isCreative(player),
                tip);
    }

    /**
     * The pack's huts (ST lists every blueprint of the pack): those with a plan in {@code style}, each in its folder.
     * Empty before a style is chosen.
     */
    WandTree tree(String style) {
        Map<String, String> folders = new LinkedHashMap<>();
        if (!style.isEmpty()) {
            for (BuildingType t : manager.context().buildingTypes().all()) {
                if (blueprints().load(style, t.id(), 1, 0).isPresent()) {
                    folders.put(t.id(), blueprints().category(t.id()));
                }
            }
        }
        return new WandTree(folders);
    }

    /**
     * ST AbstractBlockHut.getRequirements, nothing in creative (areRequirementsMet): a hut other than the town hall
     * needs its position in a colony (BlockHutTownHall skips it), then its hut block in the inventory.
     */
    List<Msg> requirements(UUID player, WandSession s, String typeId) {
        if (manager.context().players().isCreative(player)) {
            return List.of();
        }
        boolean townHall = BuildingTypes.TOWN_HALL.id().equals(typeId);
        if (!townHall && s.anchor().flatMap(manager::colonyAt).isEmpty()) {
            return List.of(Msg.of("hycolony.wand.requirement.inColony"));
        }
        boolean carried = manager.context()
                .buildingTypes()
                .byId(typeId)
                .map(t -> manager.context().ports().playerInventory().count(player, hutItem.apply(t.hutBlockKey())) > 0)
                .orElse(false);
        return carried
                ? List.of()
                : List.of(Msg.of(
                        "hycolony.wand.requirement.cost",
                        "%hycolony.ui.building.type." + typeId.substring(typeId.indexOf(':') + 1)));
    }

    /**
     * The chosen hut's last level with a plan in the style, from level 1 up to the hut's maximum (ST updateLevels
     * lists the pack's blueprints of the hut); 0 before one is chosen.
     */
    int maxLevel(WandSession s) {
        int max = manager.context()
                .buildingTypes()
                .byId(s.buildingTypeId())
                .map(BuildingType::maxLevel)
                .orElse(0);
        int level = 0;
        while (level < max
                && blueprints()
                        .load(s.style(), s.buildingTypeId(), level + 1, 0)
                        .isPresent()) {
            level++;
        }
        return level;
    }

    private BlueprintSource blueprints() {
        return manager.context().ports().blueprints();
    }
}
