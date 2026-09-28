package dev.hycolony.plugin.ui.field;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.action.FieldActions;
import dev.hycolony.core.colony.ui.FieldView;
import dev.hycolony.core.farming.field.FieldRadii.Direction;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * A field block's window (MC WindowField): its farmer, its seed, a button per side showing that side's size, and Pick
 * seed, which opens the seed list ({@link SeedPickerPage}). A side button grows the side by one, past what the budget
 * allows back to 1; each button goes to the core, which checks MANAGE_HUTS and shows the window again. Without
 * MANAGE_HUTS the buttons are disabled.
 *
 * <p>Deviation from MC: the seed is picked from the game's crop seeds, where MC's list shows the seeds the player
 * holds or the colony knows.
 */
public final class FieldPage extends ColonyPage {
    private final FieldView view;

    public FieldPage(PlayerRef playerRef, FieldView view, ColonyManager manager) {
        super(playerRef, manager);
        this.view = view;
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
        view.seed().ifPresent(seed -> ui.set("#SeedIcon.ItemId", seed.id()));
        ui.set(
                "#SeedName.TextSpans",
                view.seed()
                        .map(seed -> itemName(seed.id()))
                        .orElse(Message.translation("hycolony.ui.field.seed.none")));
        for (Direction dir : Direction.values()) {
            radiusButton(ui, events, dir);
        }
        if (view.canManage()) {
            bind(events, "#PickSeed", "pick");
        } else {
            ui.set("#PickSeed.Disabled", true);
        }
    }

    /** The side's button: its size, its tooltip, and the cycle action when the viewer may manage. */
    private void radiusButton(UICommandBuilder ui, UIEventBuilder events, Direction dir) {
        String name = dir.name().toLowerCase(Locale.ROOT);
        String sel = "#" + dir.name().charAt(0) + name.substring(1);
        ui.set(sel + ".Text", String.valueOf(view.radii().get(dir)));
        ui.set(sel + ".TooltipText", Message.translation("hycolony.ui.field.radius." + name));
        if (view.canManage()) {
            bind(events, sel, "radius", dir.ordinal());
        } else {
            ui.set(sel + ".Disabled", true);
        }
    }

    /** Pick seed opens the seed list (MC WindowSelectRes); a side goes to the core, which re-shows the window. */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        FieldActions fields = new FieldActions(manager);
        int i = act.index();
        if (act.action().equals("pick") && view.canManage()) {
            Player p = store.getComponent(ref, Player.getComponentType());
            if (p != null) {
                p.getPageManager().openCustomPage(ref, store, new SeedPickerPage(playerRef, view, manager));
            }
        } else if (act.action().equals("radius") && i >= 0 && i < Direction.values().length) {
            fields.cycleRadius(player, view.pos(), Direction.values()[i]);
        }
    }
}
