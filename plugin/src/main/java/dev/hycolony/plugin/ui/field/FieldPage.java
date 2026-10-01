package dev.hycolony.plugin.ui.field;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.FieldActions;
import dev.hycolony.core.app.ui.FieldView;
import dev.hycolony.core.farming.field.FieldRadii.Direction;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.ItemPickerPage;
import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * A field block's window (MC WindowField): its farmer, its biome, Pick seed and the seed, then the field block between
 * the scarecrow's four side buttons, each showing its side's size, with its absolute and, in grey italics, its relative
 * direction from the player's look as tooltip. A side button grows the side by one, past what the budget allows back
 * to 1; each button goes to the core, which checks MANAGE_HUTS and shows the window again. Without MANAGE_HUTS the
 * buttons are disabled.
 *
 * <p>Deviation from MC: the seed is picked from the game's crop seeds, where MC's list shows the seeds and crops the
 * colony knows; the biome is the world generator's name (Hytale names no biome for players); no crop climate line.
 */
public final class FieldPage extends ColonyPage {
    private final FieldView view;
    private final IdMap ids;

    public FieldPage(PlayerRef playerRef, FieldView view, ColonyManager manager, IdMap ids) {
        super(playerRef, manager);
        this.view = view;
        this.ids = ids;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Field.ui");
        ui.set(
                "#Farmer.TextSpans",
                view.farmer()
                        .map(name ->
                                Message.translation("hycolony.ui.field.farmer").param("p0", name))
                        .orElse(Message.translation("hycolony.ui.field.farmer.none")));
        view.biome()
                .ifPresent(b -> ui.set(
                        "#Biome.TextSpans",
                        Message.translation("hycolony.ui.field.biome").param("p0", b)));
        view.seed().ifPresent(seed -> {
            ui.set("#Seed.Visible", true);
            ui.set("#Seed.ItemId", seed.id());
        });
        ui.set("#Center.ItemId", ids.fieldBlockId());
        for (Direction dir : Direction.values()) {
            side(ui, events, dir);
        }
        if (view.canManage()) {
            bind(events, "#PickSeed", "pick");
        } else {
            ui.set("#PickSeed.Disabled", true);
        }
    }

    /** MC updateButtons: the side's size, its tooltip, and the cycle action when the viewer may manage. */
    private void side(UICommandBuilder ui, UIEventBuilder events, Direction dir) {
        String name = dir.name().toLowerCase(Locale.ROOT);
        String sel = "#" + dir.name().charAt(0) + name.substring(1);
        ui.set(sel + ".Text", String.valueOf(view.radii().get(dir)));
        String relative = view.relative()
                .getOrDefault(dir, FieldView.Relative.NEAREST)
                .name()
                .toLowerCase(Locale.ROOT);
        ui.set(
                sel + ".TooltipTextSpans",
                Message.join(
                        Message.translation("hycolony.ui.field.radius." + name),
                        Message.raw("\n"),
                        Message.translation("hycolony.ui.field.relative." + relative)
                                .italic(true)
                                .color("#aaaaaa")));
        if (view.canManage()) {
            bind(events, sel, "radius", dir.ordinal());
        } else {
            ui.set(sel + ".Disabled", true);
        }
    }

    /** The game's crop seeds; a pick sets the field's seed, Cancel shows the field window again. */
    private ItemPickerPage.Picker seeds(FieldActions fields) {
        return new ItemPickerPage.Picker(
                "Pages/HyColony/SeedPicker.ui",
                view.seeds().stream().map(ItemKey::id).toList(),
                view.seed().map(ItemKey::id),
                view.canManage(),
                i -> fields.setSeed(player, view.pos(), view.seeds().get(i)),
                () -> fields.open(player, view.pos()));
    }

    /** Pick seed opens the seed list (MC WindowSelectRes); a side goes to the core, which re-shows the window. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        FieldActions fields = new FieldActions(manager);
        int i = act.index();
        if (act.action().equals("pick") && view.canManage()) {
            open(ref, store, new ItemPickerPage(playerRef, manager, seeds(fields)));
        } else if (act.action().equals("radius") && i >= 0 && i < Direction.values().length) {
            fields.cycleRadius(player, view.pos(), Direction.values()[i]);
        }
    }
}
