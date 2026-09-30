package dev.hylens.plugin.check;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.ColonySummary;
import dev.hylens.core.check.CheckReport;
import dev.hylens.plugin.watch.ApiMessages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Checks a world's colonies for broken invariants through HyColony's api and tells an operator in the chat (spec
 * 2026-09-30, § 6.5). World thread.
 */
public final class ColonyChecks {
    private ColonyChecks() {}

    /**
     * Tells {@code player} the confirmed violations of every colony of {@code world}: one check confirms the traces at
     * once, a lasting state only across regular checks (the automatic one).
     */
    public static void tell(PlayerRef player, ColonyWorld world) {
        List<ApiText> lines = new ArrayList<>();
        for (ColonySummary c : world.colonies()) {
            lines.addAll(CheckReport.colony(c.name(), world.debug().check(c.ref()), names(world, c.ref())));
        }
        if (lines.isEmpty()) {
            lines.add(ApiText.of("hylens.check.none"));
        }
        lines.forEach(l -> player.sendMessage(ApiMessages.of(l)));
    }

    /** The names of {@code colony}'s citizens. */
    static Map<CitizenRef, String> names(ColonyWorld world, ColonyRef colony) {
        return world.citizens(colony).stream()
                .collect(Collectors.toMap(CitizenSnapshot::ref, CitizenSnapshot::name, (a, b) -> a));
    }
}
