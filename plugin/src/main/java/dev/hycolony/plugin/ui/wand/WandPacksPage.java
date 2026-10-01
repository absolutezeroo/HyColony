package dev.hycolony.plugin.ui.wand;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.WandPacksView;
import dev.hycolony.core.app.wand.WandActions;
import dev.hycolony.core.construction.blueprint.PackInfo;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * The build tool's pack window as Structurize's (windowswitchpack.xml, WindowSwitchPack): Cancel, the name filter,
 * then each owner's title and its packs two per row, each with its icon, name, description, authors and Select. The
 * filter redraws the list in place (the core's view filters itself), so the text field keeps its text; Select and
 * Cancel go to {@link WandActions}.
 */
public final class WandPacksPage extends ColonyPage {
    private static final String DIR = "Pages/HyColony/Structurize/";
    private static final String ICONS = "UI/Custom/Pages/HyColony/Structurize/";
    private static final String DEFAULT_ICON = "pack_default";

    private final WandPacksView view;
    private final WandActions wand;

    public WandPacksPage(PlayerRef playerRef, WandPacksView view, ColonyManager manager, WandActions wand) {
        super(playerRef, manager);
        this.view = view;
        this.wand = wand;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append(DIR + "SwitchPack.ui");
        bind(events, "#Cancel", "cancel");
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#Filter",
                EventData.of("Action", "filter").append("@Name", "#Filter.Value"),
                false);
        fill(ui, events, view);
    }

    /** ST sortAndFilterPacks: a title row per owner (capitalised, as ST), then rows of two packs. */
    private void fill(UICommandBuilder ui, UIEventBuilder events, WandPacksView packs) {
        ui.clear("#Packs");
        ui.set("#Empty.Visible", packs.groups().isEmpty());
        int row = 0;
        for (WandPacksView.Group g : packs.groups()) {
            ui.append("#Packs", DIR + "PackTitle.ui");
            ui.set("#Packs[" + row++ + "] #Owner.Text", capitalised(g.owner()));
            for (int i = 0; i < g.packs().size(); i += 2) {
                ui.append("#Packs", DIR + "PackRow.ui");
                for (int side = 0; side < 2 && i + side < g.packs().size(); side++) {
                    pack(
                            ui,
                            events,
                            "#Packs[" + row + "] #P" + side + " ",
                            g.packs().get(i + side));
                }
                row++;
            }
        }
    }

    /** ST fillForMeta: one pack's box. */
    private void pack(UICommandBuilder ui, UIEventBuilder events, String box, WandPacksView.Pack p) {
        PackInfo info = p.info();
        ui.set(box.strip() + ".Visible", true);
        ui.set(box + "#Icon.AssetPath", ICONS + (info.icon().isEmpty() ? DEFAULT_ICON : info.icon()) + ".png");
        ui.set(box + "#Name.Text", Message.translation(info.name()));
        if (!info.desc().isEmpty()) {
            ui.set(box + "#Desc.Text", Message.translation(info.desc()));
        }
        ui.set(
                box + "#Authors.Text",
                Message.translation("hycolony.ui.wand.authors").param("p0", String.join(", ", info.authors())));
        // By the style id: a click sent before a filtered list reached the client still picks the pack it showed.
        bindRef(events, box + "#Select", "select", p.style());
    }

    private static String capitalised(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action()) {
            case "filter" -> {
                UICommandBuilder ui = new UICommandBuilder();
                UIEventBuilder events = new UIEventBuilder();
                fill(ui, events, view.filtered(act.name()));
                sendUpdate(ui, events, false);
            }
            case "select" -> {
                if (view.styles().contains(act.ref())) {
                    wand.selectStyle(player, act.ref());
                }
            }
            case "cancel" -> wand.cancelPacks(player);
            default -> {
                // A forged or stale action: the window stays as it is.
            }
        }
    }
}
