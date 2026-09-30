package dev.hylens.core.check;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.debug.Violation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which confirmed violations each operator has not been told of yet, colony by colony (spec 2026-09-30, § 6.5). A
 * violation is known as ViolationWatch knows it (code, citizen, message key), plus its place; not by the message's
 * values, which may change while it lasts (a stale step's seconds). Two violations alike (two requests of one
 * citizen without resolver) are counted: the second is new. One gone and back is told again; one replacing another
 * alike within a round is not, until the api tells violations apart. Worlds' threads and the thread removing a
 * player share it.
 */
public final class NewAlerts {
    private record Key(String code, Optional<CitizenRef> citizen, Optional<Pos> pos, String message) {}

    private record Told(UUID operator, ColonyRef colony) {}

    private final Map<Told, Map<Key, Integer>> told = new ConcurrentHashMap<>();

    /** Of {@code current}, those not told to {@code operator} at its last call for {@code colony}; remembers all. */
    public List<Violation> fresh(UUID operator, ColonyRef colony, List<Violation> current) {
        Map<Key, Integer> now = new HashMap<>();
        List<Violation> fresh = new ArrayList<>();
        Map<Key, Integer> before =
                Optional.ofNullable(told.get(new Told(operator, colony))).orElse(Map.of());
        for (Violation v : current) {
            Key k = key(v);
            int seen = now.merge(k, 1, Integer::sum);
            if (seen > before.getOrDefault(k, 0)) {
                fresh.add(v);
            }
        }
        told.put(new Told(operator, colony), now);
        return fresh;
    }

    /** Forgets what {@code operator} was told, as they leave or turn the checks on or off. */
    public void forget(UUID operator) {
        told.keySet().removeIf(t -> t.operator().equals(operator));
    }

    private static Key key(Violation v) {
        return new Key(v.code(), v.citizen(), v.pos(), v.detail().key());
    }
}
