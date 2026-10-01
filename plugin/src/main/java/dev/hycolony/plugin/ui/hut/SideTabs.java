package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * A hut window's side tabs (MC TabsWindowModule, built by AbstractBuildingWindow): Main, then one per module tab, each
 * on one of MC's four tab backgrounds drawn from a random seeded by the hut's position, with its module icon and its
 * description as tooltip. No tab looks open, as in MC where each tab is its own window.
 */
public final class SideTabs {
    /** MC TabImageSide.LEFT imageCount: tab_left_side1..4. */
    private static final int BACKGROUNDS = 4;

    /** The module icons SideTab.ui holds (MC textures/gui/modules). */
    public static final Set<String> ICONS =
            Set.of("main", "crafting", "inventory", "settings", "info", "stock", "stats", "field", "entity");

    /** A side tab: its icon (one of {@link #ICONS}) and its description's language key. */
    public record Tab(String icon, String descKey) {}

    private SideTabs() {}

    /**
     * Appends {@code tabs} into {@code #Tabs}; a click sends {@code "tab"} with the tab's index. The backgrounds follow
     * MC's {@code new Random(buildingView.getID().hashCode())}, Minecraft's BlockPos hash being (y + z * 31) * 31 + x.
     */
    public static void render(UICommandBuilder ui, UIEventBuilder events, BlockPos hut, List<Tab> tabs) {
        int minecraftHash = (hut.y() + hut.z() * 31) * 31 + hut.x(); // int overflow on purpose, as Vec3i.hashCode
        Random backgrounds = new Random(minecraftHash);
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            String tab = "#Tabs[" + i + "]";
            ui.append("#Tabs", "Pages/HyColony/Mc/SideTab.ui");
            ui.set(tab + " #Back" + (backgrounds.nextInt(BACKGROUNDS) + 1) + ".Visible", true);
            if (ICONS.contains(t.icon())) {
                ui.set(tab + " #Icon" + capitalized(t.icon()) + ".Visible", true);
            }
            ui.set(tab + " #Hit.TooltipText", Message.translation(t.descKey()));
            ColonyPage.bind(events, tab + " #Hit", "tab", i);
        }
    }

    private static String capitalized(String icon) {
        return icon.substring(0, 1).toUpperCase(Locale.ROOT) + icon.substring(1);
    }
}
