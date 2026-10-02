package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.WandView;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the build tool window's view (ST WindowExtendedBuildTool): the pack's folders from the huts that have a plan
 * in the style, the open folder's subfolders or huts when its list shows, and each hut's lock (ST
 * AbstractBlockHut.getRequirements).
 */
final class WandViews {
    private final ColonyManager manager;
    private final Function<String, ItemKey> hutItem;

    WandViews(ColonyManager manager, Function<String, ItemKey> hutItem) {
        this.manager = manager;
        this.hutItem = hutItem;
    }

    /** The view of {@code s} for {@code player}; {@code tip} shows Structurize's hint. */
    WandView of(UUID player, WandSession s, boolean tip) {
        WandNav nav = s.nav();
        WandTree tree = tree(s.style());
        boolean list = nav.grid() == WandNav.Grid.LIST && !nav.depth().isEmpty();
        List<String> folders = list ? tree.children(nav.depth()) : List.of();
        List<WandView.Hut> huts = !list || !folders.isEmpty()
                ? List.of()
                : tree.huts(nav.depth()).stream()
                        .map(id -> new WandView.Hut(id, id.equals(s.buildingTypeId()), requirements(player, s, id)))
                        .toList();
        boolean canConfirm =
                !s.hasBuilding() || requirements(player, s, s.buildingTypeId()).isEmpty();
        return new WandView(
                s.style(),
                s.style().isEmpty() ? "" : blueprints().pack(s.style()).name(),
                new WandView.Panel(
                        nav.depth(), treePath(s), nav.disabledIcon(), nav.canGoBack(), nav.levels(), nav.placing()),
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
     * ST's tree after the pack name: nothing before any navigation at the root, else {@code /folder}, and the
     * blueprint's file ({@code /builder2}) once a hut or level was clicked (setBlueprint).
     */
    private static String treePath(WandSession s) {
        WandNav nav = s.nav();
        if (!nav.navigated()) {
            return "";
        }
        return "/" + nav.depth()
                + (nav.file() && s.hasBuilding() ? "/" + fileName(s.buildingTypeId()) + s.level() : "");
    }

    /** A hut's blueprint file name without its level, as MC's packs name it ({@code builder}). */
    private static String fileName(String typeId) {
        return typeId.substring(typeId.indexOf(':') + 1);
    }

    /**
     * The pack's huts (ST lists every blueprint of the pack): those with a level 1 plan in {@code style}, each in its
     * folder. Empty before a style is chosen.
     */
    WandTree tree(String style) {
        Map<String, String> folders = new LinkedHashMap<>();
        if (!style.isEmpty()) {
            // ST StructurePacks.getBlueprints sorts a folder's blueprints by file name (builder1 before townhall1).
            manager.context().buildingTypes().all().stream()
                    .map(BuildingType::id)
                    .sorted(Comparator.comparing(WandViews::fileName))
                    .filter(id -> blueprints().hasPlan(style, id, 1))
                    .forEach(id -> folders.put(id, blueprints().category(id)));
        }
        return new WandTree(folders);
    }

    /**
     * ST AbstractBlockHut.getRequirements, nothing in creative (areRequirementsMet): a hut other than the town hall
     * (BlockHutTownHall) needs a colony the client knows (getClosestColonyView: the colony at the position, else the
     * nearest one known at any distance), then its hut block in the inventory.
     *
     * <p>As MC sends colony views (EventHandler on login, Permissions subscribers): a client knows the colonies it
     * manages (owner, officer) and the one whose claim the player stands in, besides the one at the position.
     */
    List<Msg> requirements(UUID player, WandSession s, String typeId) {
        if (manager.context().players().isCreative(player)) {
            return List.of();
        }
        boolean townHall = BuildingTypes.TOWN_HALL.id().equals(typeId);
        if (!townHall && !knowsAColony(player, s)) {
            return List.of(Msg.of("hycolony.wand.requirement.inColony"));
        }
        boolean carried = manager.context()
                .buildingTypes()
                .byId(typeId)
                .map(t -> manager.context().ports().playerInventory().count(player, hutItem.apply(t.hutBlockKey())) > 0)
                .orElse(false);
        return carried ? List.of() : List.of(Msg.of("hycolony.wand.requirement.cost", BuildingType.nameParam(typeId)));
    }

    private boolean knowsAColony(UUID player, WandSession s) {
        return s.anchor().flatMap(manager::colonyAt).isPresent()
                || manager.context()
                        .players()
                        .position(player)
                        .flatMap(manager::colonyAt)
                        .isPresent()
                || manager.all().stream()
                        .anyMatch(c -> c.permissions().rankOf(player).isColonyManager());
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
        while (level < max && blueprints().hasPlan(s.style(), s.buildingTypeId(), level + 1)) {
            level++;
        }
        return level;
    }

    private BlueprintSource blueprints() {
        return manager.context().ports().blueprints();
    }
}
