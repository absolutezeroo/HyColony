package dev.hycolony.plugin.ui.hut.restaurant;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.crafting.restaurant.MenuView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.hut.HutTab;
import java.util.UUID;

/**
 * A dining hall's Menu page (MC RestaurantMenuModuleWindow): the menu with its tier colours, X and warnings, the
 * ingredients with the customers' daily need; then the food options, filtered, with {@code <<} (greyed for a dish
 * already on the menu). The core checks MANAGE_HUTS, the limit and the food, and re-shows. Deviation from MC: MC's two
 * pages show one at a time (see Hut/Menu.ui).
 */
public final class MenuTab implements HutTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final MenuView view;
    private boolean options;
    private String filter = "";

    public MenuTab(ColonyManager manager, UUID player, BlockPos hut, MenuView view) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.view = view;
    }

    @Override
    public String document() {
        return "Pages/HyColony/Hut/Menu.ui";
    }

    @Override
    public String icon() {
        return "food";
    }

    @Override
    public String descKey() {
        return "hycolony.ui.building.tab.menu";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #MenuPage.Visible", !options);
        ui.set(root + " #OptionsPage.Visible", options);
        ColonyPage.bind(events, root + " #ToOptions", "menuOptions");
        ColonyPage.bind(events, root + " #ToMenu", "menuOptions");
        if (options) {
            renderOptions(ui, events, root);
        } else {
            renderMenu(ui, events, root);
        }
    }

    /** MC updateStockList: the menu, its warnings and the ingredients. */
    private void renderMenu(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #Warning.Visible", view.menu().isEmpty());
        ui.set(root + " #PoorWarning.Visible", !view.menu().isEmpty() && view.poor());
        for (int i = 0; i < view.menu().size(); i++) {
            MenuView.Dish d = view.menu().get(i);
            String row = root + " #Menu[" + i + "]";
            ui.append(root + " #Menu", "Pages/HyColony/Mc/MenuDishRow.ui");
            dish(ui, row, d, "");
            ui.set(row + " #Remove.Visible", true);
            ColonyPage.bindRef(events, row + " #Remove", "menuRemove", d.item().id());
        }
        for (int i = 0; i < view.ingredients().size(); i++) {
            MenuView.Ingredient in = view.ingredients().get(i);
            String row = root + " #Ingredients[" + i + "]";
            ui.append(root + " #Ingredients", "Pages/HyColony/Mc/IngredientRow.ui");
            ui.set(row + " #Icon.ItemId", in.item().id());
            ui.set(
                    row + " #Name.TextSpans",
                    Message.join(
                            Message.raw(in.amount() + " "),
                            ColonyPage.itemName(in.item().id())));
            ui.set(
                    row + " #Daily.TooltipTextSpans",
                    Message.translation("hycolony.ui.menu.consumption")
                            .param("p0", String.valueOf(in.dailyMin()))
                            .param("p1", String.valueOf(in.dailyMax())));
        }
    }

    /** MC updateResources: the food options passing the filter, each with {@code <<}. */
    private void renderOptions(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #Filter.Value", filter);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                root + " #Filter",
                EventData.of("Action", "menuFilter").append("@Name", root + " #Filter.Value"),
                false);
        int line = 0;
        for (MenuView.Dish d : view.options()) {
            if (!ItemNames.matches(player, d.item(), filter)) {
                continue;
            }
            String row = root + " #Options[" + line++ + "]";
            ui.append(root + " #Options", "Pages/HyColony/Mc/MenuDishRow.ui");
            dish(ui, row, d, "Option");
            ui.set(row + " #Add.Visible", true);
            if (view.full()) {
                // MC disables << on a full menu then enables it again for a dish not on it: only the tooltip stays.
                ui.set(row + " #Add.TooltipText", Message.translation("hycolony.ui.menu.limit"));
            }
            if (d.onMenu()) {
                ui.set(row + " #Add.Disabled", true);
            } else {
                ColonyPage.bindRef(events, row + " #Add", "menuAdd", d.item().id());
            }
        }
    }

    /**
     * A dish's icon and name (the menu's, or with {@code layout} "Option" the options'), its tier colour and MC's
     * quality tooltip (with the vanilla line for a tier 0 food).
     */
    private static void dish(UICommandBuilder ui, String row, MenuView.Dish d, String layout) {
        ui.set(row + " #" + layout + "Icon.Visible", true);
        ui.set(row + " #" + layout + "Icon.ItemId", d.item().id());
        ui.set(row + " #" + layout + "Name.Visible", true);
        ui.set(
                row + " #" + layout + "Name.TextSpans",
                ColonyPage.itemName(d.item().id()));
        if (d.tier() > 0) {
            ui.set(row + " #Tier" + Math.min(d.tier(), 3) + ".Visible", true);
        }
        Message quality = Message.translation("hycolony.ui.menu.quality").param("p0", String.valueOf(d.homeLevel()));
        ui.set(
                row + " #Quality.TooltipTextSpans",
                d.tier() > 0
                        ? quality
                        : Message.join(quality, Message.raw("\n"), Message.translation("hycolony.ui.menu.vanilla")));
    }

    /** {@code <<}, X, the page switch and the filter. The core re-shows the window after a change. */
    @Override
    public void handle(ColonyPage.Act act) {
        switch (act.action()) {
            case "menuAdd" -> manager.hutWindows().restaurant().addToMenu(player, hut, new ItemKey(act.ref()));
            case "menuRemove" -> manager.hutWindows().restaurant().removeFromMenu(player, hut, new ItemKey(act.ref()));
            case "menuOptions" -> options = !options;
            case "menuFilter" -> filter = act.name();
            default -> {} // BuildingPage offers every action to every tab
        }
    }

    /** The page switch and the filter change the page only: nothing in the core, so the page redraws itself. */
    @Override
    public boolean redraws(ColonyPage.Act act) {
        return act.action().equals("menuOptions") || act.action().equals("menuFilter");
    }
}
