package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ui.CitizenRow;
import java.util.List;

/**
 * The town hall's Citizens tab (MC WindowCitizenPage citizenList): one row per citizen with its status.
 *
 * <p>Deviation from MC: no search, selection, health, happiness, saturation nor recall (no such systems yet).
 */
final class TownHallCitizensTab implements TownHallTab {
    private final List<CitizenRow> rows;

    TownHallCitizensTab(List<CitizenRow> rows) {
        this.rows = rows;
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        for (int i = 0; i < rows.size(); i++) {
            String row = root + " #CitizenList[" + i + "]";
            ui.append(root + " #CitizenList", "Pages/HyColony/Mc/CitizenRow.ui");
            ui.set(row + " #Name.Text", rows.get(i).name());
            ui.set(
                    row + " #Status.Text",
                    Message.translation("hycolony.status." + rows.get(i).status()));
        }
    }
}
