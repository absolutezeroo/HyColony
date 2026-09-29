package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.tab.BuilderSettingsView;
import dev.hycolony.core.construction.shared.BuilderSettingsModule.Mode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.ItemPickerPage;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The builder hut's Settings tab (MC SettingsModuleWindow): the Auto/Manual mode button cycles the mode, and the fill
 * block row opens the list of blocks to fill placeholders with (MC BlockSetting). A fill block is a block placed by its
 * own item (see FillBlocks), so its id is also its icon's item id.
 */
final class BuilderSettingsTab implements HutTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final BuilderSettingsView view;
    private final boolean canManage;

    BuilderSettingsTab(ColonyManager manager, UUID player, BlockPos hut, BuilderSettingsView view, boolean canManage) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.view = view;
        this.canManage = canManage;
    }

    @Override
    public String document() {
        return "Pages/HyColony/BuilderSettingsTab.ui";
    }

    @Override
    public String labelKey() {
        return "hycolony.ui.building.tab.settings";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        String button = root + " #ModeButton";
        // A button's Text renders no nested message: one full key per mode.
        ui.set(
                button + ".Text",
                Message.translation(
                        "hycolony.ui.builder.mode." + view.mode().name().toLowerCase(Locale.ROOT)));
        view.fillBlock().ifPresent(block -> {
            ui.set(root + " #FillIcon.ItemId", block.id());
            ui.set(root + " #FillName.TextSpans", ColonyPage.itemName(block.id()));
        });
        if (canManage) {
            ColonyPage.bind(events, button, "mode");
            ColonyPage.bind(events, root + " #FillButton", "fillBlock");
        } else {
            ui.set(button + ".Disabled", true);
            ui.set(root + " #FillButton.Disabled", true);
        }
    }

    /** The core checks MANAGE_HUTS and shows the window again. */
    @Override
    public void handle(ColonyPage.Act act) {
        if ("mode".equals(act.action())) {
            Mode next = view.mode() == Mode.AUTO ? Mode.MANUAL : Mode.AUTO;
            manager.huts().setBuilderMode(player, hut, next);
        }
    }

    /** The fill block list; a pick goes to the core (which re-shows the hut), Back reopens the hut window. */
    @Override
    public Optional<ItemPickerPage.Picker> picker(ColonyPage.Act act) {
        if (!"fillBlock".equals(act.action()) || !canManage) {
            return Optional.empty();
        }
        return Optional.of(new ItemPickerPage.Picker(
                "Pages/HyColony/FillBlockPicker.ui",
                view.fillChoices().stream().map(BlockKey::id).toList(),
                view.fillBlock().map(BlockKey::id),
                true,
                i -> manager.huts().setFillBlock(player, hut, view.fillChoices().get(i)),
                () -> manager.windows().openBuilding(player, hut)));
    }
}
