package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.WandPacksView;
import dev.hycolony.core.construction.blueprint.PackInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Shows the build tool's pack window (ST WindowSwitchPack): the styles shuffled at each opening (ST onUpdate's
 * Collections.shuffle), grouped by owner in name order (sortAndFilterPacks). The window filters them itself
 * ({@link WandPacksView#filtered}), so that typing keeps the text field.
 */
final class WandPacks {
    private final ColonyManager manager;
    private final Random random = new Random();

    WandPacks(ColonyManager manager) {
        this.manager = manager;
    }

    /** ST StructurePacks.ensureSelectedPack: a style at random; empty when there is none. */
    Optional<String> random() {
        List<String> styles = manager.context().ports().blueprints().styles();
        return styles.isEmpty() ? Optional.empty() : Optional.of(styles.get(random.nextInt(styles.size())));
    }

    /** Shows the window to {@code player}; {@code hasStyle} makes Cancel go back to the build tool. */
    void open(UUID player, boolean hasStyle) {
        List<String> styles =
                new ArrayList<>(manager.context().ports().blueprints().styles());
        Collections.shuffle(styles, random);
        Map<String, List<WandPacksView.Pack>> byOwner = new TreeMap<>();
        for (String style : styles) {
            PackInfo info = manager.context().ports().blueprints().pack(style);
            byOwner.computeIfAbsent(info.owner(), k -> new ArrayList<>()).add(new WandPacksView.Pack(style, info));
        }
        List<WandPacksView.Group> groups = byOwner.entrySet().stream()
                .map(e -> new WandPacksView.Group(e.getKey(), e.getValue()))
                .toList();
        manager.windows().ui().showWandPacks(player, new WandPacksView(groups, hasStyle));
    }
}
