package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.module.SettingRow;
import dev.hycolony.core.building.module.SettingsView;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.ItemPickerPage;
import java.util.Optional;
import java.util.UUID;

/**
 * A hut's Settings page (MC SettingsModuleWindow): one row per setting, its name, then On/Off, its value (a click
 * moves to the next) or Switch with the block's icon (a click lists the blocks, MC WindowSelectRes). An inactive row
 * shows disabled with MC's "needs research" tooltip. The core checks MANAGE_HUTS and shows the hut again.
 */
final class SettingsTab implements HutTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final SettingsView view;

    SettingsTab(ColonyManager manager, UUID player, BlockPos hut, SettingsView view) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.view = view;
    }

    @Override
    public String document() {
        return "Pages/HyColony/Hut/Settings.ui";
    }

    @Override
    public String icon() {
        return "settings";
    }

    @Override
    public String descKey() {
        return "hycolony.ui.building.tab.settings";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #Desc.Text", Message.translation(descKey()));
        for (int i = 0; i < view.rows().size(); i++) {
            SettingRow r = view.rows().get(i);
            String row = root + " #Settings[" + i + "]";
            ui.append(root + " #Settings", "Pages/HyColony/Mc/SettingRow.ui");
            ui.set(row + " #Desc.Text", Message.translation("hycolony.ui.setting." + r.id()));
            String button = row + " #" + button(ui, row, r);
            r.researchKey()
                    .ifPresent(research -> ui.set(
                            row + ".TooltipTextSpans",
                            Message.translation("hycolony.ui.setting.needsResearch")
                                    .param("p0", Message.translation(research))));
            if (r.active()) {
                ColonyPage.bindRef(events, button, "setting", r.id());
            } else {
                ui.set(button + ".Disabled", true);
            }
        }
    }

    /** Shows and fills the row's button of its kind; returns its id. */
    private static String button(UICommandBuilder ui, String row, SettingRow r) {
        return switch (r.kind()) {
            case BOOL -> {
                ui.set(row + " #Bool.Visible", true);
                ui.set(
                        row + " #Bool.Text",
                        Message.translation("hycolony.ui.townhall.setting." + (r.on() ? "on" : "off")));
                yield "Bool";
            }
            case STRING -> {
                ui.set(row + " #String.Visible", true);
                ui.set(row + " #String.Text", Message.translation(r.valueKey()));
                yield "String";
            }
            case BLOCK -> {
                ui.set(row + " #Block.Visible", true);
                r.block().ifPresent(b -> {
                    ui.set(row + " #Icon.Visible", true);
                    ui.set(row + " #Icon.ItemId", b.id()); // a fill block is placed by its own item (FillBlocks)
                });
                yield "Block";
            }
        };
    }

    /** A BOOL or STRING row goes to the core (MC TriggerSettingMessage); a BLOCK row opens its list instead. */
    @Override
    public void handle(ColonyPage.Act act) {
        if ("setting".equals(act.action())
                && row(act).filter(r -> r.kind() != SettingRow.Kind.BLOCK).isPresent()) {
            manager.hutWindows().triggerSetting(player, hut, act.ref());
        }
    }

    private Optional<SettingRow> row(ColonyPage.Act act) {
        return view.rows().stream().filter(r -> r.id().equals(act.ref())).findFirst();
    }

    /** The fill block list; a pick goes to the core (which re-shows the hut), Back reopens the hut window. */
    @Override
    public Optional<ItemPickerPage.Picker> picker(ColonyPage.Act act) {
        if (!"setting".equals(act.action())) {
            return Optional.empty();
        }
        return row(act).filter(r -> r.kind() == SettingRow.Kind.BLOCK && r.active())
                .filter(r -> r.id().equals(BuilderSettingsModule.FILL_BLOCK))
                .map(r -> new ItemPickerPage.Picker(
                        "Pages/HyColony/FillBlockPicker.ui",
                        r.choices().stream().map(BlockKey::id).toList(),
                        r.block().map(BlockKey::id),
                        true,
                        i -> manager.huts()
                                .setFillBlock(player, hut, r.choices().get(i)),
                        () -> manager.windows().openBuilding(player, hut)));
    }
}
