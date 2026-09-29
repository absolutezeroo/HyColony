package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.app.ui.CitizenRow;
import java.util.List;

/**
 * The town hall's Citizens tab (MC WindowCitizenPage citizenList): one row per citizen with its status.
 *
 * <p>Deviation from MC: no search, selection, health, happiness, saturation nor recall (no such systems yet).
 */
final class TownHallCitizensTab {
    private TownHallCitizensTab() {}

    static void render(UICommandBuilder ui, List<CitizenRow> rows) {
        for (int i = 0; i < rows.size(); i++) {
            String row = "#CitizenList[" + i + "]";
            ui.append("#CitizenList", "Pages/HyColony/CitizenRow.ui");
            ui.set(row + " #Name.Text", rows.get(i).name());
            ui.set(
                    row + " #Status.Text",
                    Message.translation("hycolony.status." + rows.get(i).status()));
        }
    }
}
