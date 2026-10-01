package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.action.PermissionActions;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.permission.RankType;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The Permissions tab's Ranks page (MC WindowPermissionsPage, managePermissions view): add a rank, the ranks (the
 * chosen one, Officer at first, disabled), its type, its actions as On/Off switches and Remove Rank.
 */
final class PermissionRanksPage {
    private final PermissionActions actions;
    private final UUID player;
    private final int colonyId;
    private final TownHallView.Permissions view;
    /** MC actionsRank: the rank whose actions show; the officers first. */
    private int rankId = Permissions.OFFICER;

    PermissionRanksPage(PermissionActions actions, UUID player, int colonyId, TownHallView.Permissions view) {
        this.actions = actions;
        this.player = player;
        this.colonyId = colonyId;
        this.view = view;
    }

    /** Keeps the rank {@code previous} showed while it exists. */
    void keepStateOf(PermissionRanksPage previous) {
        if (view.ranks().stream().anyMatch(r -> r.id() == previous.rankId)) {
            rankId = previous.rankId;
        }
    }

    private Optional<TownHallView.RankRow> chosen() {
        return view.ranks().stream().filter(r -> r.id() == rankId).findFirst();
    }

    void render(UICommandBuilder ui, UIEventBuilder events, String page) {
        if (view.canEdit()) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    page + " #AddRank",
                    EventData.of("Action", "permAddRank").append("@Name", page + " #RankName.Value"),
                    false);
        } else {
            ui.set(page + " #RankName.Disabled", true);
            ui.set(page + " #AddRank.Disabled", true);
            ui.set(page + " #RankName.TooltipText", Message.translation("hycolony.ui.townhall.perm.rankError"));
        }
        for (int i = 0; i < view.ranks().size(); i++) {
            TownHallView.RankRow r = view.ranks().get(i);
            String button = page + " #RankButtons[" + i + "] #Rank";
            ui.append(page + " #RankButtons", "Pages/HyColony/Mc/RankButtonRow.ui");
            ui.set(button + ".Text", r.name());
            if (r.id() == rankId) {
                ui.set(button + ".Disabled", true);
            } else {
                ColonyPage.bind(events, button, "permSelectRank", r.id());
            }
        }
        chosen().ifPresent(r -> rank(ui, events, page, r));
    }

    /** The chosen rank's type, its action switches (disabled where MC canAlterPermission says no) and Remove Rank. */
    private void rank(UICommandBuilder ui, UIEventBuilder events, String page, TownHallView.RankRow r) {
        List<DropdownEntryInfo> types = new ArrayList<>();
        for (RankType t : RankType.values()) {
            String key = "hycolony.ui.townhall.perm.type." + t.name().toLowerCase(Locale.ROOT);
            types.add(new DropdownEntryInfo(LocalizableString.fromMessageId(key), t.name()));
        }
        ui.set(page + " #RankType.Entries", types);
        ui.set(page + " #RankType.Value", r.type().name());
        String rank = String.valueOf(r.id());
        events.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                page + " #RankType",
                EventData.of("Action", "permRankType")
                        .append("@Name", page + " #RankType.Value")
                        .append("Ref", rank),
                false);
        for (int i = 0; i < r.actions().size(); i++) {
            TownHallView.ActionState a = r.actions().get(i);
            String row = page + " #Actions[" + i + "]";
            ui.append(page + " #Actions", "Pages/HyColony/Mc/ActionRow.ui");
            ui.set(
                    row + " #Name.Text",
                    TownHallPermissionsTab.actionName(a.action().name()));
            ui.set(
                    row + " #Switch.Text",
                    Message.translation("hycolony.ui.townhall.setting." + (a.on() ? "on" : "off")));
            if (a.alterable()) {
                // MC WindowPermissionsPage.trigger sends the state the shown button asks for, not a flip.
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        row + " #Switch",
                        EventData.of("Action", a.on() ? "permDisable" : "permEnable")
                                .append("Index", String.valueOf(a.action().ordinal()))
                                .append("Ref", rank),
                        false);
            } else {
                ui.set(row + " #Switch.Disabled", true);
            }
        }
        if (r.initial()) {
            ui.set(page + " #RemoveRank.Disabled", true);
        } else {
            ColonyPage.bindRef(events, page + " #RemoveRank", "permRemoveRank", rank);
        }
    }

    /**
     * Choosing a rank is page state; the other buttons go to the core, which shows the town hall again. They name
     * their rank in the event: a page a live refresh did not redraw still acts on the rank it shows.
     */
    TownHallTab.Outcome handle(ColonyPage.Act act) {
        switch (act.action()) {
            case "permSelectRank" -> {
                if (view.ranks().stream().anyMatch(r -> r.id() == act.index())) {
                    rankId = act.index();
                    return TownHallTab.Outcome.REDRAW;
                }
            }
            case "permAddRank" -> actions.addRank(player, colonyId, act.name());
            case "permRankType" ->
                type(act.name())
                        .ifPresent(t -> rankOf(act).ifPresent(id -> actions.setRankType(player, colonyId, id, t)));
            case "permEnable", "permDisable" -> alter(act, act.action().equals("permEnable"));
            case "permRemoveRank" -> rankOf(act).ifPresent(id -> actions.removeRank(player, colonyId, id));
            default -> {}
        }
        return TownHallTab.Outcome.NONE;
    }

    /** The rank id the event names in {@code Ref}; empty if it names none. */
    private static Optional<Integer> rankOf(ColonyPage.Act act) {
        try {
            return Optional.of(Integer.parseInt(act.ref()));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    private static Optional<RankType> type(String name) {
        try {
            return Optional.of(RankType.valueOf(name));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    /** MC PermissionsMessage.Permission: sets the rank's action as asked (the core checks canAlterPermission). */
    private void alter(ColonyPage.Act act, boolean enable) {
        int ordinal = act.index();
        if (ordinal >= 0 && ordinal < Action.values().length) {
            rankOf(act)
                    .ifPresent(id -> actions.alterPermission(player, colonyId, id, Action.values()[ordinal], enable));
        }
    }
}
