package dev.hycolony.core.crafting.restaurant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingEventsModule;
import dev.hycolony.core.building.module.PersistentModule;
import dev.hycolony.core.citizen.food.EatingRule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.persist.SavedJson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A dining hall's room (MC BuildingCook): its seats, the citizens it serves, and no food kept for its waiter (MC
 * keepFood false). Deviation from MC: MC's seats are the plan's {@code sit} tags, which Hytale plans lack; here they
 * are the seat blocks (chairs, stools, benches) its plan or a player placed in its footprint, all "inside" (MC's
 * {@code sit_out}, skipped in the rain, has no counterpart).
 */
public final class DiningRoomModule implements PersistentModule, BuildingEventsModule, EatingRule {
    /** MC getNextSittingPosition: random draws before giving up. */
    static final int SEAT_DRAWS = 3;

    private final List<BlockPos> seats = new ArrayList<>();
    private final Set<Integer> customers = new LinkedHashSet<>();

    public List<BlockPos> seats() {
        return Collections.unmodifiableList(seats);
    }

    /** MC customers: the citizens this hall serves. */
    public Set<Integer> customers() {
        return Collections.unmodifiableSet(customers);
    }

    /** Adds the seat at {@code pos}, once. */
    public void addSeat(BlockPos pos) {
        if (!seats.contains(pos)) {
            seats.add(pos);
        }
    }

    /**
     * MC getNextSittingPosition: up to {@link #SEAT_DRAWS} random seats, the first nobody sits on; empty without a
     * free one (or any). A drawn seat whose loaded block is no seat any more (broken, or a placement that failed) is
     * forgotten, since its seats are not the plan's tags (see the class).
     */
    Optional<BlockPos> nextSeat(Colony colony) {
        for (int i = 0; i < SEAT_DRAWS && !seats.isEmpty(); i++) {
            BlockPos seat = seats.get(colony.context().random().nextInt(seats.size()));
            if (gone(colony, seat)) {
                seats.remove(seat);
                colony.markDirty();
            } else if (!colony.context().seats().isSeatTaken(seat)) {
                return Optional.of(seat);
            }
        }
        return Optional.empty();
    }

    private static boolean gone(Colony colony, BlockPos seat) {
        return colony.context()
                .ports()
                .blocks()
                .get(seat)
                .filter(b -> !colony.context().ports().catalog().isSeat(b.key()))
                .isPresent();
    }

    /**
     * MC storeCustomer: the first time, every worker of the colony goes to the hall closest to its work hut; then
     * {@code citizenId} is this hall's customer and no other's.
     */
    void storeCustomer(Colony colony, Building hall, int citizenId) {
        List<Building> halls = colony.buildings().all().stream()
                .filter(b -> b.module(DiningRoomModule.class).isPresent())
                .toList();
        if (customers.isEmpty()) {
            for (Building work : colony.buildings().all()) {
                work.module(WorkerModule.class)
                        .ifPresent(w -> closest(halls, hall, work)
                                .module(DiningRoomModule.class)
                                .ifPresent(room -> room.customers.addAll(w.workers())));
            }
        }
        for (Building other : halls) {
            if (!other.equals(hall)) {
                other.module(DiningRoomModule.class).ifPresent(room -> room.customers.remove(citizenId));
            }
        }
        customers.add(citizenId);
        colony.markDirty();
    }

    /** The hall of {@code halls} closest to {@code work}, {@code self} winning ties (MC starts from itself). */
    private static Building closest(List<Building> halls, Building self, Building work) {
        Building best = self;
        long bestDistance = self.position().distSq(work.position());
        for (Building h : halls) {
            long d = h.position().distSq(work.position());
            if (d < bestDistance) {
                best = h;
                bestDistance = d;
            }
        }
        return best;
    }

    /** MC BuildingCook.keepFood: false, the hall keeps its menu instead. */
    @Override
    public boolean keepsFood() {
        return false;
    }

    /** A seat of the plan joins the room (see the class: MC's {@code sit} tags). */
    @Override
    public void onBlockPlacedInBuilding(Colony colony, Building building, BlockPos pos, BlockKey block) {
        if (colony.context().ports().catalog().isSeat(block)) {
            addSeat(pos);
        }
    }

    /** A seat a player placed in the footprint joins the room too (see the class: a deviation); marks the colony. */
    @Override
    public void onBlockPlacedByPlayer(Colony colony, Building building, BlockPos pos, BlockKey block) {
        if (colony.context().ports().catalog().isSeat(block) && !seats.contains(pos)) {
            seats.add(pos);
            colony.markDirty();
        }
    }

    @Override
    public void write(JsonObject out) {
        JsonArray s = new JsonArray();
        seats.forEach(p -> s.add(SavedJson.pos(p)));
        out.add("seats", s);
        JsonArray c = new JsonArray();
        customers.forEach(c::add);
        out.add("customers", c);
    }

    /** Tolerant (CLAUDE.md § 5): a malformed seat or customer is skipped. */
    @Override
    public void read(JsonObject in) {
        seats.clear();
        customers.clear();
        for (JsonElement e : SavedJson.arrayOr(in.get("seats"))) {
            SavedJson.tryPos(e).ifPresent(this::addSeat);
        }
        for (JsonElement e : SavedJson.arrayOr(in.get("customers"))) {
            if (e instanceof JsonPrimitive p && p.isNumber()) {
                customers.add(p.getAsInt());
            }
        }
    }
}
