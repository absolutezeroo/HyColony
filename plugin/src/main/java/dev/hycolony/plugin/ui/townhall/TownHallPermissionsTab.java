package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.PermissionActions;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.colony.permission.PermissionEvents;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The town hall's Permissions tab (MC WindowPermissionsPage): its pages turned by {@code <} {@code >}, the Players page
 * drawn here (add by name or from the online players, members with their rank, refused actions), the Ranks page by
 * {@link PermissionRanksPage}. The page, the picker and the chosen rank are page state.
 */
final class TownHallPermissionsTab implements TownHallTab {
    private static final int PAGES = 2;

    private final PermissionActions actions;
    private final UUID player;
    private final int colonyId;
    private final TownHallView.Permissions view;
    private final PermissionRanksPage ranks;
    private int page;
    private boolean picker;
    private String picked = "";

    TownHallPermissionsTab(ColonyManager manager, UUID player, int colonyId, TownHallView.Permissions view) {
        this.actions = new PermissionActions(manager);
        this.player = player;
        this.colonyId = colonyId;
        this.view = view;
        this.ranks = new PermissionRanksPage(actions, player, colonyId, view);
    }

    /** Keeps the page and the chosen rank {@code previous} showed (the core re-shows the window after actions). */
    void keepStateOf(TownHallPermissionsTab previous) {
        page = previous.page;
        ranks.keepStateOf(previous.ranks);
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(root + " #PlayersPage.Visible", page == 0);
        ui.set(root + " #RanksPage.Visible", page == 1);
        ui.set(root + " #PageNum.Text", (page + 1) + "/" + PAGES);
        ColonyPage.bind(events, root + " #PrevPage", "permPrev");
        ColonyPage.bind(events, root + " #NextPage", "permNext");
        if (page == 0) {
            players(ui, events, root + " #PlayersPage");
        } else {
            ranks.render(ui, events, root + " #RanksPage");
        }
    }

    /** MC's Players page: without EDIT_PERMISSIONS the add field and buttons are disabled with MC's tooltip. */
    private void players(UICommandBuilder ui, UIEventBuilder events, String page) {
        ui.set(page + " #PlayerName.Value", picked);
        if (view.canEdit()) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    page + " #AddPlayer",
                    EventData.of("Action", "permAddPlayer").append("@Name", page + " #PlayerName.Value"),
                    false);
            ColonyPage.bind(events, page + " #OnlinePlayers", "permPicker");
        } else {
            Message error = Message.translation("hycolony.ui.townhall.perm.playerError");
            for (String id : List.of(" #PlayerName", " #AddPlayer", " #OnlinePlayers")) {
                ui.set(page + id + ".Disabled", true);
                ui.set(page + id + ".TooltipText", error);
            }
        }
        members(ui, events, page + " #Members");
        refusals(ui, events, page + " #Refusals");
        ui.set(page + " #Picker.Visible", picker);
        for (int i = 0; picker && i < view.online().size(); i++) {
            String row = page + " #Picker[" + i + "]";
            ui.append(page + " #Picker", "Pages/HyColony/Mc/PickerRow.ui");
            ui.set(row + " #Pick.Text", view.online().get(i).name());
            ColonyPage.bind(events, row + " #Pick", "permPick", i);
        }
    }

    /** MC updateUsers: the owner's rank as text and no remove cross, the others' rank as a dropdown without Owner. */
    private void members(UICommandBuilder ui, UIEventBuilder events, String list) {
        List<DropdownEntryInfo> rankEntries = new ArrayList<>();
        view.ranks().stream()
                .filter(r -> r.id() != Permissions.OWNER)
                .forEach(r -> rankEntries.add(
                        new DropdownEntryInfo(LocalizableString.fromString(r.name()), String.valueOf(r.id()))));
        for (int i = 0; i < view.members().size(); i++) {
            TownHallView.MemberRow m = view.members().get(i);
            String row = list + "[" + i + "]";
            ui.append(list, "Pages/HyColony/Mc/MemberRow.ui");
            ui.set(row + " #Name.Text", m.name());
            if (m.rankId() == Permissions.OWNER) {
                ui.set(row + " #Remove.Visible", false);
                ui.set(row + " #RankPicker.Visible", false);
                ui.set(row + " #Rank.Visible", true);
                ui.set(row + " #Rank.Text", m.rankName());
                continue;
            }
            ColonyPage.bind(events, row + " #Remove", "permRemove", i);
            ui.set(row + " #RankPicker.Entries", rankEntries);
            ui.set(row + " #RankPicker.Value", String.valueOf(m.rankId()));
            events.addEventBinding(
                    CustomUIEventBindingType.ValueChanged,
                    row + " #RankPicker",
                    EventData.of("Action", "permSetRank")
                            .append("Index", String.valueOf(i))
                            .append("@Name", row + " #RankPicker.Value"),
                    false);
        }
    }

    /** MC fillEventsList: the action, the player ("<fake>" when unknown), x y z, and Add for a known player. */
    private void refusals(UICommandBuilder ui, UIEventBuilder events, String list) {
        for (int i = 0; i < view.refusals().size(); i++) {
            PermissionEvents.Event e = view.refusals().get(i);
            String row = list + "[" + i + "]";
            ui.append(list, "Pages/HyColony/Mc/RefusalRow.ui");
            ui.set(row + " #Action.Text", actionName(e.action().name()));
            ui.set(row + " #Name.Text", e.player().isPresent() ? e.name() : e.name() + " <fake>");
            ui.set(
                    row + " #Pos.Text",
                    e.pos().x() + " " + e.pos().y() + " " + e.pos().z());
            if (e.player().isPresent()) {
                ColonyPage.bind(events, row + " #Add", "permAddKnown", i);
            } else {
                ui.set(row + " #Add.Visible", false);
            }
        }
    }

    /** The translated name of an action (MC KEY_TO_PERMISSIONS). */
    static Message actionName(String action) {
        return Message.translation("hycolony.ui.permission." + action.toLowerCase(Locale.ROOT));
    }

    @Override
    public Outcome handle(ColonyPage.Act act) {
        switch (act.action()) {
            case "permPrev", "permNext", "permPicker", "permPick" -> {
                return navigate(act);
            }
            case "permAddPlayer" -> actions.addPlayer(player, colonyId, act.name());
            case "permRemove" -> member(act.index()).ifPresent(m -> actions.removePlayer(player, colonyId, m.id()));
            case "permSetRank" -> member(act.index()).ifPresent(m -> setRank(m, act.name()));
            case "permAddKnown" -> addKnown(act.index());
            default -> {
                return ranks.handle(act);
            }
        }
        return Outcome.NONE;
    }

    /** The page state: turning pages, opening the online players, picking one into the name field. */
    private Outcome navigate(ColonyPage.Act act) {
        switch (act.action()) {
            case "permPicker" -> picker = !picker;
            case "permPick" -> {
                if (act.index() < 0 || act.index() >= view.online().size()) {
                    return Outcome.NONE;
                }
                picked = view.online().get(act.index()).name();
                picker = false;
            }
            default -> {
                page = Math.floorMod(page + ("permNext".equals(act.action()) ? 1 : -1), PAGES);
                picker = false;
            }
        }
        return Outcome.REDRAW;
    }

    private Optional<TownHallView.MemberRow> member(int i) {
        return i >= 0 && i < view.members().size() ? Optional.of(view.members().get(i)) : Optional.empty();
    }

    private void setRank(TownHallView.MemberRow m, String value) {
        try {
            actions.setRank(player, colonyId, m.id(), Integer.parseInt(value));
        } catch (NumberFormatException _) {
            // A forged value: ignored.
        }
    }

    private void addKnown(int i) {
        if (i >= 0 && i < view.refusals().size()) {
            PermissionEvents.Event e = view.refusals().get(i);
            e.player().ifPresent(id -> actions.addKnownPlayer(player, colonyId, id, e.name()));
        }
    }
}
