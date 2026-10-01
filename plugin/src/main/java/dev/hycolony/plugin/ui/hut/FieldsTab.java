package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.FieldActions;
import dev.hycolony.core.farming.field.FieldStage;
import dev.hycolony.core.farming.hut.FieldsView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.highlight.Highlight;
import dev.hycolony.plugin.ui.highlight.Highlights;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * A farmer hut's Fields page (MC FarmFieldsModuleWindow): the assignment mode, "n/m fields in use", then a row per
 * field with its seed, distance, stage and MC's assign box (checked for a field of this hut). The box works in manual
 * mode only; a refused one is disabled with its reason as tooltip. The core checks MANAGE_HUTS and re-shows. Locate
 * makes the field block glow, with a map marker for the viewer, for a minute; a second click turns it off.
 * Deviation from MC: Locate is a button added at the user's request.
 */
final class FieldsTab implements HutTab {
    /** MC's red for a refused field's tooltip (ChatFormatting.RED). */
    private static final String REFUSAL_COLOR = "#ff5555";

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
        return "Pages/HyColony/Hut/Fields.ui";
    }

    @Override
    public String icon() {
        return "field";
    }

    @Override
    public String descKey() {
        return "hycolony.ui.building.tab.fields";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #Desc.Text", Message.translation(descKey()));
        // MC hiring.on / hiring.off: "Manual" or "Automatic"; the button stays enabled for all, the core refuses.
        ui.set(
                root + " #Mode.Text",
                Message.translation("hycolony.ui.fields.mode." + (fields.manual() ? "manual" : "auto")));
        ColonyPage.bind(events, root + " #Mode", "fieldsMode");
        ui.set(
                root + " #Count.Text",
                Message.translation("hycolony.ui.fields.count")
                        .param("p0", String.valueOf(fields.owned()))
                        .param("p1", String.valueOf(fields.max())));
        for (int i = 0; i < fields.rows().size(); i++) {
            ui.append(root + " #Fields", "Pages/HyColony/Mc/FarmFieldRow.ui");
            row(ui, events, root + " #Fields[" + i + "]", fields.rows().get(i));
        }
    }

    /** Fills field row {@code sel}: seed icon, distance and direction, stage, Locate and the assign box. */
    private void row(UICommandBuilder ui, UIEventBuilder events, String sel, FieldsView.Row r) {
        r.seed().ifPresent(seed -> ui.set(sel + " #Icon.ItemId", seed.id()));
        ui.set(
                sel + " #Distance.TextSpans",
                Message.translation("hycolony.ui.fields.distance")
                        .param("p0", String.valueOf(r.distance()))
                        .param("p1", Message.translation("hycolony.ui.direction." + r.direction())));
        if (r.seed().isPresent()) { // MC: without a seed, no stage
            stage(ui, sel, r);
        }
        String ref = ref(r);
        ColonyPage.bindRef(events, sel + " #Locate", "fieldLocate", ref);
        if (Highlights.isActive(player, r.field())) {
            ui.set(sel + " #LocateIcon.Visible", false);
            ui.set(sel + " #LocateIconOn.Visible", true);
        }
        String box = sel + (r.owned() ? " #Owned" : " #Free");
        ui.set(box + ".Visible", true);
        if (!fields.canAssign()) {
            ui.set(box + ".Disabled", true);
        } else if (r.refusal().isPresent()) {
            ui.set(box + ".Disabled", true);
            ui.set(
                    box + ".TooltipTextSpans",
                    Message.translation(r.refusal().get()).color(REFUSAL_COLOR));
        } else {
            ColonyPage.bindRef(events, box, "fieldAssign", ref);
        }
    }

    /** MC: "Stage:" and the stage, "Current: …" and "Next: …" as tooltip; HyColony adds "done today" after it. */
    private static void stage(UICommandBuilder ui, String sel, FieldsView.Row r) {
        ui.set(sel + " #StageLabel.Text", Message.translation("hycolony.ui.fields.status"));
        Message stage = stageName(r.stage());
        ui.set(
                sel + " #Stage.TextSpans",
                r.doneToday()
                        ? Message.join(stage, Message.raw(" - "), Message.translation("hycolony.ui.fields.doneToday"))
                        : stage);
        ui.set(
                sel + " #Stage.TooltipTextSpans",
                Message.join(
                        Message.translation("hycolony.ui.fields.status.current").param("p0", stage),
                        Message.raw("\n"),
                        Message.translation("hycolony.ui.fields.status.next")
                                .param("p0", stageName(r.stage().next()))));
    }

    private static Message stageName(FieldStage stage) {
        return Message.translation("hycolony.ui.fields.stage." + stage.name().toLowerCase(Locale.ROOT));
    }

    /** A row's stable id in events: its field's position, so a refresh that moves rows still names the right one. */
    private static String ref(FieldsView.Row r) {
        return r.field().x() + "," + r.field().y() + "," + r.field().z();
    }

    private Optional<FieldsView.Row> row(ColonyPage.Act act) {
        return fields.rows().stream().filter(r -> ref(r).equals(act.ref())).findFirst();
    }

    /** Mode, then the assign box or Locate of the row's field. The core re-shows the window. */
    @Override
    public void handle(ColonyPage.Act act) {
        FieldActions actions = new FieldActions(manager);
        switch (act.action()) {
            case "fieldsMode" -> actions.toggleMode(player, hut);
            case "fieldAssign" -> row(act).ifPresent(r -> assignOrFree(actions, r));
            case "fieldLocate" -> row(act).ifPresent(r -> Highlights.toggle(player, highlight(r)));
            default -> {} // BuildingPage offers every action to every tab
        }
    }

    /** Locate changes the icon of its row only: nothing in the core, so the page redraws itself. */
    @Override
    public boolean redraws(ColonyPage.Act act) {
        return act.action().equals("fieldLocate");
    }

    /** A field as highlighted: its block glows, a "Field" marker on the map. */
    private static Highlight highlight(FieldsView.Row row) {
        return new Highlight(row.field(), Message.translation("hycolony.ui.fields.marker"));
    }

    /** Frees the row's field if the hut owns it, else assigns it (MC AssignFieldMessage). */
    private void assignOrFree(FieldActions actions, FieldsView.Row r) {
        if (r.owned()) {
            actions.free(player, hut, r.field());
        } else {
            actions.assign(player, hut, r.field());
        }
    }
}
