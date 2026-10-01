package dev.hycolony.core.crafting.restaurant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.PersistentModule;
import dev.hycolony.core.building.module.ProvidesTab;
import dev.hycolony.core.building.module.TickingModule;
import dev.hycolony.core.citizen.food.FoodRules;
import dev.hycolony.core.citizen.food.hall.DiningHall;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.persist.SavedJson;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.logistics.pickup.KeepRule;
import dev.hycolony.core.logistics.pickup.KeepsItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A dining hall's menu (MC RestaurantMenuModule, canCook true, expected stock = the hall's level): at most
 * {@link #STOCK_PER_LEVEL} foods a level, each one a citizen may eat ({@link FoodRules#edible}); every colony tick its
 * stock is asked for ({@link MenuRequests}) and the hall keeps a stack a level of each dish and of its raw item. It is
 * also the hall's {@link DiningHall} port, the seats and customers kept by the {@link DiningRoomModule}.
 */
public final class RestaurantMenuModule
        implements PersistentModule, TickingModule, KeepsItems, ProvidesTab, DiningHall {
    /** MC STOCK_PER_LEVEL: menu places per hall level. */
    static final int STOCK_PER_LEVEL = 5;

    private final Set<ItemKey> menu = new LinkedHashSet<>();

    @Override
    public Set<ItemKey> menu() {
        return Collections.unmodifiableSet(menu);
    }

    /** MC LivingBuildingView.checkColonyMenu: whether a dining hall of {@code colony} serves a {@code tier}+ dish. */
    public static boolean anyMenuServesTier(Colony colony, int tier) {
        ItemCatalog catalog = colony.context().ports().catalog();
        return colony.buildings().all().stream()
                .flatMap(b -> b.module(RestaurantMenuModule.class).stream())
                .flatMap(m -> m.menu.stream())
                .anyMatch(dish -> FoodRules.tier(catalog, dish) >= tier);
    }

    /** MC hasReachedLimit: whether the menu of {@code hall} is full. */
    public boolean full(Building hall) {
        return menu.size() >= hall.level() * STOCK_PER_LEVEL;
    }

    /**
     * MC addMenuItem: puts {@code food} on the menu; false (nothing changes) for a food citizens may not eat (raw that
     * cooks, no food), one already on it or a full menu.
     */
    public boolean add(Colony colony, Building hall, ItemKey food) {
        if (!FoodRules.edible(colony.context().ports().catalog(), food) || full(hall) || menu.contains(food)) {
            return false;
        }
        menu.add(food);
        return true;
    }

    /** MC removeMenuItem: takes {@code food} off the menu and cancels its open request; false if it was not on it. */
    public boolean remove(Colony colony, Building hall, ItemKey food) {
        if (!menu.remove(food)) {
            return false;
        }
        MenuRequests.cancel(colony, hall, food);
        return true;
    }

    /**
     * MC onColonyTick: drops what is no food any more, then asks for the stock. Deviation from MC: MC filters it out
     * as it reads its save; a module reads without the catalog, so it goes at the first tick.
     */
    @Override
    public void onColonyTick(Colony colony, Building building) {
        menu.removeIf(f -> !FoodRules.edible(colony.context().ports().catalog(), f));
        MenuRequests.update(colony, building, List.copyOf(menu));
    }

    /** MC alterItemsToBeKept: a stack per hall level of each dish and of the raw item that cooks into it. */
    @Override
    public List<KeepRule> keepRules(Colony colony, Building building) {
        ItemCatalog catalog = colony.context().ports().catalog();
        int level = MenuRequests.expectedStock(building);
        List<KeepRule> rules = new ArrayList<>();
        for (ItemKey dish : menu) {
            rules.add(new KeepRule(dish::equals, catalog.maxStack(dish) * level, false));
            colony.context()
                    .ports()
                    .cooking()
                    .catalog()
                    .rawFor(dish)
                    .ifPresent(raw -> rules.add(new KeepRule(raw::equals, catalog.maxStack(raw) * level, false)));
        }
        return rules;
    }

    /** MC RestaurantMenuModuleView: the menu tab. */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        return MenuViews.of(colony, building, this);
    }

    /** MC STAFFED_RESTAURANTS: the hall has a waiter. */
    @Override
    public boolean hasWaiter(Colony colony, Building hall) {
        return hall.module(WorkerModule.class).map(w -> !w.workers().isEmpty()).orElse(false);
    }

    @Override
    public Optional<BlockPos> nextSeat(Colony colony, Building hall) {
        return hall.module(DiningRoomModule.class).flatMap(room -> room.nextSeat(colony));
    }

    @Override
    public void storeCustomer(Colony colony, Building hall, int citizenId) {
        hall.module(DiningRoomModule.class).ifPresent(room -> room.storeCustomer(colony, hall, citizenId));
    }

    @Override
    public void write(JsonObject out) {
        JsonArray arr = new JsonArray();
        menu.forEach(f -> arr.add(f.id()));
        out.add("menu", arr);
    }

    /** Tolerant (CLAUDE.md § 5): a non-string entry is skipped; a food that no longer is goes at the next tick. */
    @Override
    public void read(JsonObject in) {
        menu.clear();
        for (JsonElement e : SavedJson.arrayOr(in.get("menu"))) {
            if (e instanceof JsonPrimitive p && p.isString()) {
                menu.add(new ItemKey(p.getAsString()));
            }
        }
    }
}
