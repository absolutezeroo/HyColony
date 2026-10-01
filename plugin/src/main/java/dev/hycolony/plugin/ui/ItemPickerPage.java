package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.select.SelectResOrder;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * A searchable list of items to pick one from (Structurize WindowSelectRes): the field's seed, the builder hut's fill
 * block. The current item shows at the top left with "->"; the list follows SelectResOrder as the player types.
 * Picking one and Cancel both go to the core, which shows the window the list came from again (MC WindowSelectRes
 * cancel); Cancel closes the list when that window cannot show any more.
 */
public final class ItemPickerPage extends ColonyPage {
    private static final String LIST = "#Items";

    /**
     * What the list shows and does: its {@code .ui} document (Mc/SelectRes.ui's template with its description), the
     * item ids, the current one, whether the viewer may pick, and the core actions for a pick (by index in
     * {@code ids}) and Cancel (false when no window opened in its place).
     */
    public record Picker(
            String document,
            List<String> ids,
            Optional<String> current,
            boolean canPick,
            IntConsumer pick,
            BooleanSupplier cancel) {
        public Picker {
            ids = List.copyOf(ids);
        }
    }

    private final Picker picker;
    /** Each id's index in the picker, so that filling the list stays linear. */
    private final Map<String, Integer> indexOf = new HashMap<>();

    public ItemPickerPage(PlayerRef playerRef, ColonyManager manager, Picker picker) {
        super(playerRef, manager);
        this.picker = picker;
        for (int i = 0; i < picker.ids().size(); i++) {
            indexOf.putIfAbsent(picker.ids().get(i), i);
        }
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append(picker.document());
        // As the game's own search lists (CommandListPage): every keystroke sends the field's value. Deviation from
        // MC: the list is redrawn at once, where WindowSelectRes waits 10 client ticks after the last keystroke.
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("Action", "search").append("@Name", "#SearchInput.Value"),
                false);
        bind(events, "#CancelButton", "back");
        // WindowSelectRes: the previous item, its name and "->" at the top left.
        picker.current().ifPresent(id -> {
            ui.set("#FromIcon.Visible", true);
            ui.set("#FromIcon.ItemId", id);
            ui.set("#FromName.Visible", true);
            ui.set("#FromName.TextSpans", itemName(id));
            ui.set("#To.Visible", true);
        });
        fill(ui, events, "");
    }

    /** Clears the list and appends the items SelectResOrder keeps for {@code filter}, in its order. */
    private void fill(UICommandBuilder ui, UIEventBuilder events, String filter) {
        ui.clear(LIST);
        Set<String> held = manager.context().ports().playerInventory().contents(player).keySet().stream()
                .map(ItemKey::id)
                .collect(Collectors.toSet());
        List<String> shown = SelectResOrder.sorted(picker.ids(), this::name, held, filter);
        for (int line = 0; line < shown.size(); line++) {
            String id = shown.get(line);
            row(ui, events, new Row(line, indexOf.getOrDefault(id, -1)), id);
        }
    }

    /** The item's name in the player's language; its id when the game has no translation. */
    private String name(String id) {
        Item item = Item.getAssetMap().getAsset(id);
        String name =
                item == null ? null : I18nModule.get().getMessage(playerRef.getLanguage(), item.getTranslationKey());
        return name == null ? id : name;
    }

    /**
     * A list row: its line in the list and the item's index in the picker. Its event carries the picker's index, which
     * a later keystroke does not change, so a click on a row the client still shows picks that row's item.
     */
    private record Row(int line, int item) {}

    /** One item; its Select button is disabled for a viewer who may not pick (MC lets the current one be picked). */
    private void row(UICommandBuilder ui, UIEventBuilder events, Row r, String id) {
        String row = LIST + "[" + r.line() + "]";
        ui.append(LIST, "Pages/HyColony/Mc/SelectResRow.ui");
        ui.set(row + " #Icon.ItemId", id);
        ui.set(row + " #Name.TextSpans", itemName(id));
        if (picker.canPick()) {
            bind(events, row + " #Select", "pick", r.item());
        } else {
            ui.set(row + " #Select.Disabled", true);
        }
    }

    /**
     * A keystroke redraws the list; a pick or Cancel goes to the core, which shows the previous window again; Cancel
     * closes the list when it cannot.
     */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action()) {
            case "search" -> {
                UICommandBuilder ui = new UICommandBuilder();
                UIEventBuilder events = new UIEventBuilder();
                fill(ui, events, act.name());
                sendUpdate(ui, events, false);
            }
            case "pick" -> {
                if (act.index() >= 0 && act.index() < picker.ids().size()) {
                    picker.pick().accept(act.index());
                }
            }
            case "back" -> {
                if (!picker.cancel().getAsBoolean()) {
                    close(); // nothing to go back to
                }
            }
            default -> {}
        }
    }
}
