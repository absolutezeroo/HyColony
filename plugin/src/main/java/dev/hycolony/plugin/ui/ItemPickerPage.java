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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import javax.annotation.Nonnull;

/**
 * A searchable list of items to pick one from (MC WindowSelectRes): the field's seed, the builder hut's fill block.
 * The list is filtered as the player types, by each item's name in the player's language or its id. Picking one and
 * Back both go to the core, which shows the window the list came from again (MC WindowSelectRes cancel).
 */
public final class ItemPickerPage extends ColonyPage {
    private static final String LIST = "#Items";

    /**
     * What the list shows and does: its {@code .ui} document (title and texts, with {@code #Items},
     * {@code #ItemsEmpty}, {@code #SearchInput} and {@code #CancelButton}), the item ids, the current one (its row is
     * disabled), whether the viewer may pick, and the core actions for a pick (by index in {@code ids}) and Back
     * (false when no window opened in its place).
     */
    public record Picker(
            String document,
            List<String> ids,
            Optional<String> current,
            boolean canPick,
            IntConsumer pick,
            BooleanSupplier back) {
        public Picker {
            ids = List.copyOf(ids);
        }
    }

    private final Picker picker;

    public ItemPickerPage(PlayerRef playerRef, ColonyManager manager, Picker picker) {
        super(playerRef, manager);
        this.picker = picker;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append(picker.document());
        // As the game's own search lists (CommandListPage): every keystroke sends the field's value.
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("Action", "search").append("@Name", "#SearchInput.Value"),
                false);
        bind(events, "#CancelButton", "back");
        fill(ui, events, "");
    }

    /** Clears the list and appends the items matching {@code query}, or the "no match" line. */
    private void fill(UICommandBuilder ui, UIEventBuilder events, String query) {
        ui.clear(LIST);
        List<String> terms = List.of(query.trim().toLowerCase(Locale.ROOT).split("\\s+"));
        int lines = 0;
        for (int i = 0; i < picker.ids().size(); i++) {
            String id = picker.ids().get(i);
            if (matches(id, terms)) {
                row(ui, events, new Row(lines++, i), id);
            }
        }
        ui.set("#ItemsEmpty.Visible", lines == 0);
    }

    /** True when each term is in the item's name, in the player's language, or in its id. */
    private boolean matches(String id, List<String> terms) {
        String text = (name(id) + " " + id).toLowerCase(Locale.ROOT);
        return terms.stream().allMatch(text::contains);
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

    /** One item; its Select button is disabled for the current item or a viewer who may not pick. */
    private void row(UICommandBuilder ui, UIEventBuilder events, Row r, String id) {
        String row = LIST + "[" + r.line() + "]";
        ui.append(LIST, "Pages/HyColony/FieldSeedRow.ui");
        ui.set(row + " #Icon.ItemId", id);
        ui.set(row + " #Name.TextSpans", itemName(id));
        if (picker.canPick() && !picker.current().map(id::equals).orElse(false)) {
            bind(events, row + " #Select", "pick", r.item());
        } else {
            ui.set(row + " #Select.Disabled", true);
        }
    }

    /** A keystroke redraws the list; a pick or Back goes to the core, which shows the previous window again. */
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
                if (!picker.back().getAsBoolean()) {
                    close(); // nothing to go back to
                }
            }
            default -> {}
        }
    }
}
