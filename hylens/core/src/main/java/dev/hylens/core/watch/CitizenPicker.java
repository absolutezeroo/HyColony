package dev.hylens.core.watch;

import dev.hycolony.api.read.CitizenSnapshot;
import java.util.List;
import java.util.Locale;

/**
 * The citizen a player names, ignoring case and surrounding spaces: the one with that full name, else the one whose
 * name holds it (spec 2026-09-30, § 6.1).
 */
public final class CitizenPicker {
    private CitizenPicker() {}

    /** What a name picks. */
    public sealed interface Pick {}

    /** The one citizen named. */
    public record Found(CitizenSnapshot citizen) implements Pick {}

    /** No citizen has that name. */
    public record NotFound() implements Pick {}

    /** Several citizens match: their names, sorted. */
    public record Ambiguous(List<String> names) implements Pick {}

    /** Picks among {@code citizens} the one {@code query} names; a blank query names none. */
    public static Pick pick(List<CitizenSnapshot> citizens, String query) {
        String wanted = query.strip().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return new NotFound();
        }
        List<CitizenSnapshot> exact = citizens.stream()
                .filter(c -> c.name().toLowerCase(Locale.ROOT).equals(wanted))
                .toList();
        List<CitizenSnapshot> matching = exact.isEmpty()
                ? citizens.stream()
                        .filter(c -> c.name().toLowerCase(Locale.ROOT).contains(wanted))
                        .toList()
                : exact;
        return switch (matching.size()) {
            case 0 -> new NotFound();
            case 1 -> new Found(matching.getFirst());
            default ->
                new Ambiguous(
                        matching.stream().map(CitizenSnapshot::name).sorted().toList());
        };
    }
}
