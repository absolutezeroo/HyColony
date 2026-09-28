package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.action.FieldActions;
import dev.hycolony.core.colony.ui.tab.FieldsView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.highlight.Highlight;
import dev.hycolony.plugin.ui.highlight.Highlights;
import java.util.Locale;
import java.util.UUID;

/**
 * A farmer hut's Fields tab (MC FarmFieldsModuleWindow): {@code owned} of {@code max}, the assignment mode and Request
 * Fertilizer buttons, then a row per field with its seed, distance, stage and Assign or Free. Assign and Free work in
 * manual mode only; a refused Assign is disabled with its reason as tooltip. The core checks MANAGE_HUTS and re-shows.
 * Locate makes the field block glow, with a map marker for the viewer, for a minute; a second click turns it off.
 * Deviation from MC: Locate is a button added at the user's request.
 */
final class FieldsTab implements HutTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final FieldsView fields;

    FieldsTab(ColonyManager manager, UUID player, BlockPos hut, FieldsView fields) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.fields = fields;
    }

    @Override
    public String document() {
        return "Pages/HyColony/FieldsTab.ui";
    }

    @Override
    public String labelKey() {
        return "hycolony.ui.building.tab.fields";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(
                root + " #FieldsCount.Text",
                Message.translation("hycolony.ui.fields.count")
                        .param("p0", String.valueOf(fields.owned()))
                        .param("p1", String.valueOf(fields.max())));
        String mode = "hycolony.ui.fields.mode." + (fields.manual() ? "manual" : "auto");
        setting(ui, events, root + " #ModeButton", mode, "fieldsMode");
        String fertilize = "hycolony.ui.farmer.fertilize." + (fields.fertilize() ? "on" : "off");
        setting(ui, events, root + " #FertilizeButton", fertilize, "fieldsFertilize");
        if (fields.rows().isEmpty()) {
            ui.set(root + " #FieldsEmpty.Visible", true);
            ui.set(root + " #FieldsEmpty.Text", Message.translation("hycolony.ui.fields.none"));
        }
        for (int i = 0; i < fields.rows().size(); i++) {
            row(ui, events, root + " #Fields", i, fields.rows().get(i));
        }
    }

    /** A settings button showing {@code key}, sending {@code action}; disabled for a viewer who may not manage. */
    private void setting(UICommandBuilder ui, UIEventBuilder events, String button, String key, String action) {
        ui.set(button + ".Text", Message.translation(key));
        if (fields.canManage()) {
            ColonyPage.bind(events, button, action);
        } else {
            ui.set(button + ".Disabled", true);
        }
    }

    /** Appends field row {@code i}: seed icon, distance and direction, stage, and its Assign or Free button. */
    private void row(UICommandBuilder ui, UIEventBuilder events, String list, int i, FieldsView.Row r) {
        String sel = list + "[" + i + "]";
        ui.append(list, "Pages/HyColony/FieldRow.ui");
        r.seed().ifPresent(seed -> ui.set(sel + " #Icon.ItemId", seed.id()));
        ui.set(
                sel + " #Distance.TextSpans",
                Message.translation("hycolony.ui.fields.distance")
                        .param("p0", String.valueOf(r.distance()))
                        .param("p1", Message.translation("hycolony.ui.direction." + r.direction())));
        String stage = r.seed().isEmpty()
                ? "hycolony.ui.fields.noseed"
                : "hycolony.ui.fields.stage." + r.stage().name().toLowerCase(Locale.ROOT);
        Message stageLine = Message.translation(stage);
        if (r.doneToday()) {
            stageLine =
                    Message.join(stageLine, Message.raw(" - "), Message.translation("hycolony.ui.fields.doneToday"));
        }
        ui.set(sel + " #Stage.TextSpans", stageLine);
        ColonyPage.bind(events, sel + " #LocateButton", "fieldLocate", i);
        if (Highlights.isActive(player, r.field())) {
            ui.set(sel + " #LocateIcon.Visible", false);
            ui.set(sel + " #LocateIconOn.Visible", true);
        }
        String button = sel + " #AssignButton";
        ui.set(
                button + ".Text",
                Message.translation(r.owned() ? "hycolony.ui.fields.free" : "hycolony.ui.fields.assign"));
        if (!fields.canManage() || !fields.manual()) {
            ui.set(button + ".Disabled", true);
        } else if (r.refusal().isPresent()) {
            ui.set(button + ".Disabled", true);
            ui.set(button + ".TooltipText", Message.translation(r.refusal().get()));
        } else {
            ColonyPage.bind(events, button, "fieldAssign", i);
        }
    }

    /** Mode, fertilizer, then Assign or Free for the row's field. The core re-shows the window. */
    @Override
    public void handle(ColonyPage.Act act) {
        FieldActions actions = new FieldActions(manager);
        switch (act.action()) {
            case "fieldsMode" -> actions.toggleMode(player, hut);
            case "fieldsFertilize" -> actions.toggleFertilize(player, hut);
            case "fieldAssign" -> assignOrFree(actions, act.index());
            case "fieldLocate" -> locate(act.index());
            default -> {} // BuildingPage offers every action to every tab
        }
    }

    /** Locate changes the icon of its row only: nothing in the core, so the page redraws itself. */
    @Override
    public boolean redraws(ColonyPage.Act act) {
        return act.action().equals("fieldLocate");
    }

    /**
     * Highlights the row's field for the viewer, or turns the highlight off, to find it in a large colony; an unknown
     * row does nothing.
     */
    private void locate(int index) {
        if (index >= 0 && index < fields.rows().size()) {
            Highlights.toggle(player, highlight(fields.rows().get(index)));
        }
    }

    /** A field as highlighted: its block glows, a "Field" marker on the map. */
    private static Highlight highlight(FieldsView.Row row) {
        return new Highlight(row.field(), Message.translation("hycolony.ui.fields.marker"));
    }

    /** Frees the row's field if the hut owns it, else assigns it; an unknown row does nothing. */
    private void assignOrFree(FieldActions actions, int index) {
        if (index < 0 || index >= fields.rows().size()) {
            return;
        }
        FieldsView.Row r = fields.rows().get(index);
        if (r.owned()) {
            actions.free(player, hut, r.field());
        } else {
            actions.assign(player, hut, r.field());
        }
    }
}
