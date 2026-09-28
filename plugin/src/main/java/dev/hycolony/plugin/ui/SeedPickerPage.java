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
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.action.FieldActions;
import dev.hycolony.core.colony.ui.FieldView;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * The seed list the field window opens (MC WindowSelectRes from WindowField): the game's crop seeds, filtered as the
 * player types in the search field by their name in the player's language or their id. Picking one sets the field's
 * seed; the core then shows the field window again.
 */
public final class SeedPickerPage extends ColonyPage {
    private static final String LIST = "#Seeds";

    private final FieldView view;
    /** The seeds the list shows now, in its order: a row's index points here. */
    private final List<ItemKey> shown = new ArrayList<>();

    public SeedPickerPage(PlayerRef playerRef, FieldView view, ColonyManager manager) {
        super(playerRef, manager);
        this.view = view;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/SeedPicker.ui");
        // As the game's own search lists (CommandListPage): every keystroke sends the field's value.
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#SearchInput",
                EventData.of("Action", "search").append("@Name", "#SearchInput.Value"),
                false);
        fill(ui, events, "");
    }

    /** The typed text is kept by the client: the list alone is redrawn. */
    @Override
    protected boolean showsInput() {
        return true;
    }

    /** Clears the list and appends the seeds matching {@code query}, or the "no match" line. */
    private void fill(UICommandBuilder ui, UIEventBuilder events, String query) {
        ui.clear(LIST);
        shown.clear();
        List<String> terms = List.of(query.trim().toLowerCase(Locale.ROOT).split("\\s+"));
        for (ItemKey seed : view.seeds()) {
            if (matches(seed, terms)) {
                row(ui, events, shown.size(), seed);
                shown.add(seed);
            }
        }
        ui.set("#SeedsEmpty.Visible", shown.isEmpty());
    }

    /** True when each term is in the seed's name, in the player's language, or in its id. */
    private boolean matches(ItemKey seed, List<String> terms) {
        String text = (name(seed) + " " + seed.id()).toLowerCase(Locale.ROOT);
        return terms.stream().allMatch(text::contains);
    }

    /** The seed's name in the player's language; its id when the game has no translation. */
    private String name(ItemKey seed) {
        Item item = Item.getAssetMap().getAsset(seed.id());
        String name =
                item == null ? null : I18nModule.get().getMessage(playerRef.getLanguage(), item.getTranslationKey());
        return name == null ? seed.id() : name;
    }

    /** One seed; its Select button is disabled for the field's current seed or a viewer who may not manage. */
    private void row(UICommandBuilder ui, UIEventBuilder events, int i, ItemKey seed) {
        String row = LIST + "[" + i + "]";
        ui.append(LIST, "Pages/HyColony/FieldSeedRow.ui");
        ui.set(row + " #Icon.ItemId", seed.id());
        ui.set(row + " #Name.TextSpans", itemName(seed.id()));
        if (view.canManage() && !view.seed().map(seed::equals).orElse(false)) {
            bind(events, row + " #Select", "seed", i);
        } else {
            ui.set(row + " #Select.Disabled", true);
        }
    }

    /** A keystroke redraws the list; a pick goes to the core, which shows the field window again. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if (act.action().equals("search")) {
            UICommandBuilder ui = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            fill(ui, events, act.name());
            sendUpdate(ui, events, false);
        } else if (act.action().equals("seed") && act.index() >= 0 && act.index() < shown.size()) {
            new FieldActions(manager).setSeed(player, view.pos(), shown.get(act.index()));
        }
    }
}
