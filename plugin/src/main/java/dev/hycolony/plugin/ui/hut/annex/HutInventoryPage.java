package dev.hycolony.plugin.ui.hut.annex;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.hut.HutStock;
import dev.hycolony.core.app.hut.HutStockOrder;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ui.BuildingPage;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.highlight.Highlight;
import dev.hycolony.plugin.ui.highlight.Highlights;
import dev.hycolony.plugin.ui.hut.HutWindow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * A hut's inventory summary (MC WindowHutAllInventory): the filter, the five sorts, the items with their name cut at
 * 17 characters and their quantity abbreviated, Locate (closes the window and highlights for 60 s each container
 * holding the item) and Back. The filter and the list are redrawn in place on each keystroke.
 */
public final class HutInventoryPage extends ColonyPage implements HutWindow {
    private static final String LIST = "#Items";
    /** MC WindowHutAllInventory.ressourceStackName: the name is cut to its first 17 characters. */
    private static final int NAME_LENGTH = 17;
    /**
     * Deviation from MC: its sortDescriptor is a static field of the client, kept for the game session; the server
     * keeps one per player until it stops.
     */
    private static final Map<UUID, HutStockOrder.Sort> SORTS = new ConcurrentHashMap<>();

    private final BuildingView view;
    private String filter = "";

    public HutInventoryPage(PlayerRef playerRef, ColonyManager manager, BuildingView view) {
        super(playerRef, manager);
        this.view = view;
    }

    @Override
    public BlockPos hutPos() {
        return view.pos();
    }

    @Override
    public ColonyPage with(PlayerRef playerRef, BuildingView fresh) {
        HutInventoryPage page = new HutInventoryPage(playerRef, manager, fresh);
        page.filter = filter;
        return page;
    }

    /** The filter field is typed into: a live refresh must not redraw it. */
    @Override
    protected boolean showsInput() {
        return true;
    }

    private HutStockOrder.Sort sort() {
        return SORTS.getOrDefault(player, HutStockOrder.Sort.NONE);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/HutInventory.ui");
        ui.set("#Filter.Value", filter);
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#Filter",
                EventData.of("Action", "filter").append("@Name", "#Filter.Value"),
                false);
        bind(events, "#Sort", "sort");
        bind(events, "#Back", "back");
        list(ui, events);
    }

    /**
     * The sort label and the rows, in MC's order. Deviation from MC: no Shift for the exact quantity (no modifier key
     * event); the abbreviated one shows.
     */
    private void list(UICommandBuilder ui, UIEventBuilder events) {
        ui.set("#Sort.Text", sort().label());
        List<HutStock> shown =
                HutStockOrder.sorted(view.stock(), s -> name(s.item().id()), filter, sort());
        for (int i = 0; i < shown.size(); i++) {
            HutStock s = shown.get(i);
            String row = LIST + "[" + i + "]";
            ui.append(LIST, "Pages/HyColony/Mc/InventoryLine.ui");
            ui.set(row + " #Icon.ItemId", s.item().id());
            String name = name(s.item().id());
            ui.set(row + " #Name.Text", name.substring(0, Math.min(NAME_LENGTH, name.length())));
            ui.set(row + " #Count.Text", HutStock.abbreviate(s.count()));
            // The item's id, not the row: a page a live refresh did not redraw still finds it.
            bindRef(events, row + " #Locate", "locate", s.item().id());
        }
    }

    /** The item's name in the player's language; its id when the game has no translation. */
    private String name(String id) {
        Item item = Item.getAssetMap().getAsset(id);
        String name =
                item == null ? null : I18nModule.get().getMessage(playerRef.getLanguage(), item.getTranslationKey());
        return name == null ? id : name;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action()) {
            case "filter" -> {
                filter = act.name();
                redrawList();
            }
            case "sort" -> {
                SORTS.put(player, sort().next());
                redrawList();
            }
            case "locate" ->
                view.stock().stream()
                        .filter(s -> s.item().id().equals(act.ref()))
                        .findFirst()
                        .ifPresent(this::locate);
            case "back" -> BuildingPage.back(ref, store, playerRef, view, manager);
            default -> {}
        }
    }

    private void redrawList() {
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        ui.clear(LIST);
        list(ui, events);
        sendUpdate(ui, events, false);
    }

    /**
     * MC locate: closes the window, says {@code coremod.locating} and highlights for 60 s each container holding the
     * item. Deviation from MC: a glow and a map marker named "item count" for each, without MC's red-to-green colour
     * by count (a glowing block has no colour), and it replaces any highlight the player had (MC clears only its own).
     */
    private void locate(HutStock s) {
        playerRef.sendMessage(HytaleNotifier.toMessage(Msg.of("hycolony.hut.locating")));
        close();
        Highlights.showAll(
                player,
                s.holders().stream()
                        .map(h -> new Highlight(
                                h.pos(), Message.join(itemName(s.item().id()), Message.raw(" " + h.count()))))
                        .toList());
    }
}
