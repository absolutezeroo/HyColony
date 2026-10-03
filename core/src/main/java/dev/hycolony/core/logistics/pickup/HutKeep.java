package dev.hycolony.core.logistics.pickup;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.food.FoodRules;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What a building keeps during one dump or pickup pass (MC {@code buildingRequiresCertainAmountOfItem} over
 * {@code getRequiredItemsAndAmount}). Stateful: each stack offered counts towards what is already kept, like MC's
 * {@code localAlreadyKept}; make a new one per pass.
 */
public final class HutKeep {
    /** MC AbstractBuilding.getRequiredItemsAndAmount: food kept per hut level. */
    static final int KEPT_FOOD_PER_LEVEL = 2;

    private final List<KeepRule> rules;
    private final ItemCatalog catalog;
    private final int[] kept;

    private HutKeep(List<KeepRule> rules, ItemCatalog catalog) {
        this.rules = rules;
        this.catalog = catalog;
        this.kept = new int[rules.size()];
    }

    /**
     * The keep rules of {@code building}: its {@link KeepsItems} modules (MC {@code keepX} and
     * {@code IHasRequiredItemsModule}), its workers' food (MC {@code keepFood}, unless an {@code EatingRule} says no),
     * plus, for a pickup from the hut ({@code inventory} false), the deliveries of the requests its resolvers made.
     * {@code inventory} true is a worker dumping its inventory: MC skips the rules whose inventory flag is false.
     */
    public static HutKeep of(Colony colony, Building building, boolean inventory) {
        List<KeepRule> rules = new ArrayList<>();
        if (!inventory) {
            rules.addAll(deliveryRules(colony.requests(), building));
        }
        if (FoodRules.keepsFood(building)) {
            // MC AbstractBuilding.keepFood: food its workers may eat (as from no home), level x 2, inventory too.
            Predicate<ItemKey> allowed = FoodRules.workAllows(colony, building.position());
            FoodCatalog foods = colony.context().ports().foods();
            rules.add(new KeepRule(
                    item -> FoodRules.canEat(foods, item, 0, allowed), building.level() * KEPT_FOOD_PER_LEVEL, true));
        }
        for (var module : building.modules().values()) {
            if (module instanceof KeepsItems keeps) {
                for (KeepRule rule : keeps.keepRules(colony, building)) {
                    if (!inventory || rule.inventory()) {
                        rules.add(rule);
                    }
                }
            }
        }
        return new HutKeep(rules, colony.context().ports().catalog());
    }

    /** MC: the items in {@code getDeliveries()} of the requests made by the building's resolvers, summed per item. */
    private static List<KeepRule> deliveryRules(RequestManager requests, Building building) {
        Map<ItemKey, Integer> delivered = new LinkedHashMap<>();
        for (Resolver resolver : building.resolvers()) {
            for (Request r : requests.byRequester(resolver.requesterId())) {
                if (r.deliverable().isPresent()) {
                    r.deliveries().forEach(d -> delivered.merge(d.item(), d.count(), Integer::sum));
                }
            }
        }
        List<KeepRule> out = new ArrayList<>(delivered.size());
        delivered.forEach((item, n) -> out.add(new KeepRule(item::equals, n, false)));
        return out;
    }

    /**
     * How many of {@code stack} may leave the building; what stays counts as kept for the next stacks. The first rule
     * matching the item decides; an item no rule matches leaves whole, and so does a worn-out tool
     * ({@link ItemCatalog#wornOut}): MC has destroyed it, so it is never the tool a hut keeps.
     *
     * <p>MC also keeps a stack that is better equipment than the one already kept, but then counts it past the kept
     * amount and lets it leave anyway, so that check is not ported.
     */
    public int removable(ItemAmount stack) {
        if (catalog.wornOut(stack)) {
            return stack.count();
        }
        int i = 0;
        while (i < rules.size() && !rules.get(i).matches().test(stack.item())) {
            i++;
        }
        if (i == rules.size() || kept[i] >= rules.get(i).amount()) {
            return stack.count();
        }
        int rest = kept[i] + stack.count() - rules.get(i).amount();
        kept[i] += stack.count() - Math.max(0, rest);
        return Math.max(0, rest);
    }
}
