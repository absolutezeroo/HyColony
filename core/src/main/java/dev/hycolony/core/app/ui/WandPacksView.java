package dev.hycolony.core.app.ui;

import dev.hycolony.core.construction.blueprint.PackInfo;
import java.util.List;
import java.util.Locale;

/**
 * The build tool's pack window (ST WindowSwitchPack): the styles grouped by owner in name order, each group's packs
 * shuffled once per opening; {@code hasStyle} when a style is already chosen, so that Cancel goes back to the build
 * tool rather than closing.
 */
public record WandPacksView(List<Group> groups, boolean hasStyle) {
    public WandPacksView {
        groups = List.copyOf(groups);
    }

    /** An owner's title row and its packs. */
    public record Group(String owner, List<Pack> packs) {
        public Group {
            packs = List.copyOf(packs);
        }
    }

    /** A pack: the style id that Select chooses and its metadata. */
    public record Pack(String style, PackInfo info) {}

    /** Every style shown, group after group. */
    public List<String> styles() {
        return groups.stream().flatMap(g -> g.packs().stream()).map(Pack::style).toList();
    }

    /**
     * ST sortAndFilterPacks: the packs whose name contains {@code filter}, ignoring case, in the same order; a group
     * left empty goes. Deviation from Structurize: the style id matches too, and the name is the one written in
     * packs.json (a translation key there), since the core cannot translate.
     */
    public WandPacksView filtered(String filter) {
        if (filter.isBlank()) {
            return this;
        }
        String lower = filter.toLowerCase(Locale.ROOT);
        List<Group> kept = groups.stream()
                .map(g -> new Group(
                        g.owner(),
                        g.packs().stream()
                                .filter(p -> p.style().toLowerCase(Locale.ROOT).contains(lower)
                                        || p.info()
                                                .name()
                                                .toLowerCase(Locale.ROOT)
                                                .contains(lower))
                                .toList()))
                .filter(g -> !g.packs().isEmpty())
                .toList();
        return new WandPacksView(kept, hasStyle);
    }
}
