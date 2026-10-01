package dev.hycolony.plugin.ui.hut.main;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.citizen.home.ResidentsView;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.hut.annex.HutInfoPage;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A hut's main page (MC AbstractBuildingMainWindow and its three layouts): the title strip with the hut's name and
 * level, the rename pencil, Build Options, Help, Inventory and the summary chest, then the workers (MC
 * AbstractWindowWorkerModuleBuilding) or the residents (MC WindowHutLiving). Buttons that open another window answer
 * with the {@link Annex} to open.
 */
public final class MainTab {
    /** The windows the main page opens in its place. */
    public enum Annex {
        RENAME,
        INFO,
        INVENTORY,
        INVENTORY_SUMMARY,
        HIRE,
        ASSIGN
    }

    /** The frame's buttons that open another window. */
    private static final Map<String, Annex> OPENS = Map.of(
            "rename", Annex.RENAME,
            "info", Annex.INFO,
            "inventory", Annex.INVENTORY,
            "allInventory", Annex.INVENTORY_SUMMARY);

    private final ColonyManager manager;
    private final UUID player;
    private final BuildingView view;

    public MainTab(ColonyManager manager, UUID player, BuildingView view) {
        this.manager = manager;
        this.player = player;
        this.view = view;
    }

    /** The page's {@code .ui}, per MC layout. */
    public String document() {
        return switch (view.mainKind()) {
            case WORKERS -> "Pages/HyColony/Hut/MainWorkers.ui";
            case LIVING -> "Pages/HyColony/Hut/MainLiving.ui";
            case SIMPLE -> "Pages/HyColony/Hut/MainSimple.ui";
        };
    }

    /** Fills the page appended at {@code root}. */
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #Title.TextSpans", title(view));
        ColonyPage.bind(events, root + " #EditName", "rename");
        buildButton(ui, events, root);
        if (HutInfoPage.pages(view.typeId()) > 0) {
            ColonyPage.bind(events, root + " #Info", "info");
        } else {
            ui.set(root + " #Info.Visible", false); // MC: no help pages for this hut type
        }
        ColonyPage.bind(events, root + " #Inventory", "inventory");
        ColonyPage.bind(events, root + " #AllInventory", "allInventory");
        switch (view.mainKind()) {
            case WORKERS -> new WorkersSection(manager, player, view).render(ui, events, root);
            case LIVING ->
                view.tab(ResidentsView.class)
                        .ifPresent(r -> new LivingSection(manager, player, view.pos()).render(ui, events, root, r));
            case SIMPLE -> {}
        }
    }

    /** MC AbstractBuildingMainWindow: the custom name, else the type's, then the level. */
    public static Message title(BuildingView view) {
        Message name =
                view.customName().isEmpty() ? ColonyPage.buildingName(view.typeId()) : Message.raw(view.customName());
        return Message.join(name, Message.raw(" " + view.level()));
    }

    /**
     * MC updateButtonBuild: "Build Options" opens the build options window; with an order it becomes "Cancel Build /
     * Upgrade / Repair / Removal" (a full key per variant), which cancels it; the core checks MANAGE_HUTS.
     */
    private void buildButton(UICommandBuilder ui, UIEventBuilder events, String root) {
        String button = root + " #Build";
        if (view.order().isEmpty()) {
            ui.set(button + ".Text", Message.translation("hycolony.ui.building.buildOptions"));
            ColonyPage.bind(events, button, "build");
            return;
        }
        String type = view.order().get().type().name().toLowerCase(Locale.ROOT);
        ui.set(button + ".Text", Message.translation("hycolony.ui.building.cancel." + type));
        ColonyPage.bind(events, button, "cancel");
    }

    /** Runs {@code act}; returns the window it opens in place of this one, if any. */
    public Optional<Annex> handle(ColonyPage.Act act) {
        Annex opens = OPENS.get(act.action());
        if (opens != null) {
            return Optional.of(opens);
        }
        switch (act.action()) {
            case "build" -> manager.windows().openBuildOptions(player, view.pos());
            case "cancel" -> manager.workOrders().cancel(player, view.pos());
            default -> {
                return switch (view.mainKind()) {
                    case WORKERS -> new WorkersSection(manager, player, view).handle(act);
                    case LIVING -> new LivingSection(manager, player, view.pos()).handle(act);
                    case SIMPLE -> Optional.empty();
                };
            }
        }
        return Optional.empty();
    }
}
