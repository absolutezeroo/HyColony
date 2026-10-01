package dev.hycolony.plugin.ui.hut.annex;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * A hut's help pages (MC WindowInfo): one page at a time, its name in red and its text, the arrows turning pages and
 * the left one going back to the hut on the first page (MC's "exit" over "prevPage"), "n/m" at the bottom.
 */
public final class HutInfoPage extends ColonyPage {
    /**
     * MC's {@code com.minecolonies.coremod.info.<type>.<i>} pages, counted in manual_en_us.json: four for each hut that
     * has help, none for the residence (whose window then hides the help seal).
     */
    private static final Map<String, Integer> PAGES =
            Map.of("builder", 4, "farmer", 4, "deliveryman", 4, "warehouse", 4);

    private final BlockPos hut;
    private final String type;
    private final int count;
    private int page;

    public HutInfoPage(PlayerRef playerRef, ColonyManager manager, BlockPos hut, String typeId) {
        super(playerRef, manager);
        this.hut = hut;
        this.type = shortType(typeId);
        this.count = pages(typeId);
    }

    /** The number of help pages of hut type {@code typeId} ("hycolony:builder"); 0 for none. */
    public static int pages(String typeId) {
        return PAGES.getOrDefault(shortType(typeId), 0);
    }

    private static String shortType(String typeId) {
        return typeId.substring(typeId.indexOf(':') + 1);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/HutInfo.ui");
        String key = "hycolony.ui.info." + type + "." + page;
        ui.set("#Name.Text", Message.translation(key + ".name"));
        ui.set("#Text.Text", Message.translation(key));
        ui.set("#PageNum.Text", (page + 1) + "/" + count);
        bind(events, "#Prev", page == 0 ? "exit" : "prev");
        if (page + 1 < count) {
            bind(events, "#Next", "next");
        } else {
            ui.set("#Next.Visible", false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action()) {
            case "exit" -> manager.windows().openBuilding(player, hut);
            case "prev" -> turn(page - 1);
            case "next" -> turn(page + 1);
            default -> {}
        }
    }

    private void turn(int to) {
        if (to >= 0 && to < count) {
            page = to;
            rebuild();
        }
    }
}
