package dev.hylens.core.check;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What /hylens check tells in the chat (spec 2026-09-30, § 6.5): per colony, the confirmed violations, each with its
 * citizen or its place; HyColony's own text says what is wrong.
 */
public final class CheckReport {
    private CheckReport() {}

    /**
     * The lines for the colony {@code name} and its confirmed {@code violations}, citizens named by {@code names}
     * (their id when missing): a header and one line each, or one line saying it is healthy.
     */
    public static List<ApiText> colony(String name, List<Violation> violations, Map<CitizenRef, String> names) {
        if (violations.isEmpty()) {
            return List.of(ApiText.of("hylens.check.healthy", name));
        }
        List<ApiText> out = new ArrayList<>(violations.size() + 1);
        out.add(ApiText.of("hylens.check.header", name, String.valueOf(violations.size())));
        violations.forEach(v -> out.add(line(v, names)));
        return out;
    }

    /** The line telling the operator of {@code v}, new in the colony {@code name}. */
    public static ApiText fresh(String name, Violation v, Map<CitizenRef, String> names) {
        return ApiText.of("hylens.check.new", name, line(v, names));
    }

    private static ApiText line(Violation v, Map<CitizenRef, String> names) {
        if (v.citizen().isPresent()) {
            CitizenRef c = v.citizen().get();
            return ApiText.of("hylens.check.citizen", names.getOrDefault(c, "#" + c.citizenId()), v.detail());
        }
        return v.pos()
                .map(p -> ApiText.of("hylens.check.place", pos(p), v.detail()))
                .orElse(ApiText.of("hylens.check.plain", v.detail()));
    }

    private static String pos(Pos p) {
        return p.x() + " " + p.y() + " " + p.z();
    }
}
