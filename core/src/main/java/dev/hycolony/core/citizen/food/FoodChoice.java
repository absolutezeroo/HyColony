package dev.hycolony.core.citizen.food;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.food.hall.DiningHalls;
import dev.hycolony.core.citizen.home.HomePosition;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Which food a citizen picks: the one it ate least recently (a dish counting half as recent), with MC's early picks
 * and its preference for the dining hall when its meals lack variety or quality. Port of MC FoodUtils
 * getBestFoodForCitizen, checkForFoodInBuilding and hasBestOptionInInv; a smaller score is better.
 */
public final class FoodChoice {
    private final Colony colony;
    private final CitizenData citizen;
    private final ItemCatalog catalog;
    private final FoodHistory.Stats stats;
    private final int homeLevel;
    private final int diversityNeeded;
    private final int qualityNeeded;
    /** MC: its meals lack variety or quality. */
    private final boolean critical;

    /** The choice of {@code citizen}, its needs read now (MC reads them at each call). */
    public FoodChoice(Colony colony, CitizenData citizen) {
        this.colony = colony;
        this.citizen = citizen;
        this.catalog = colony.context().ports().catalog();
        this.stats = citizen.hunger().history().stats(catalog);
        this.homeLevel = HungerTicks.homeLevel(colony, citizen);
        this.diversityNeeded = FoodRules.minDiversity(homeLevel);
        this.qualityNeeded = FoodRules.minQuality(homeLevel);
        this.critical = stats.diversity() <= diversityNeeded || stats.quality() <= qualityNeeded;
    }

    /**
     * MC getBestFoodForCitizen: the slot of {@code inventory} with the best food the citizen may eat, on {@code menu}
     * if given; -1 for none, and also when it should rather go to a dining hall (one exists, no menu given, its meals
     * known and about to bore it).
     */
    public int bestSlot(Inventory inventory, @Nullable Set<ItemKey> menu) {
        int bestScore = Integer.MAX_VALUE;
        int bestSlot = -1;
        ItemKey bestItem = null;
        for (int i = 0; i < inventory.size(); i++) {
            ItemKey item = inventory.slot(i).map(ItemAmount::item).orElse(null);
            if (item == null || !eligible(item, menu)) {
                continue;
            }
            int score = score(item);
            if (goodEnough(score, FoodRules.isDish(catalog, item))) {
                return i;
            }
            if (score < bestScore) {
                bestScore = score;
                bestSlot = i;
                bestItem = item;
            }
        }
        return menu == null && ratherAtHall(bestScore, bestItem) ? -1 : bestSlot;
    }

    /**
     * MC getBestFoodForCitizen's last check: with a dining hall nearby and a full history, the best food it carries
     * ({@code bestItem}, scored {@code bestScore}) is eaten lately while its meals lack variety, or is no dish while
     * they lack quality.
     */
    private boolean ratherAtHall(int bestScore, @Nullable ItemKey bestItem) {
        boolean bestIsDish = bestItem != null && FoodRules.isDish(catalog, bestItem);
        boolean bored = (bestScore >= 0 && stats.diversity() <= diversityNeeded)
                || (!bestIsDish && stats.quality() <= qualityNeeded);
        return bored && citizen.hunger().history().isFull() && hallNearby();
    }

    /**
     * MC checkForFoodInBuilding: the best food in {@code building}'s containers the citizen may eat, on {@code menu}
     * if given; a dish it has not eaten lately at once when its meals lack variety or quality, and a dish sometimes
     * preferred at random (more likely for a low home). Empty for none.
     */
    public Optional<ItemKey> bestInBuilding(Building building, @Nullable Set<ItemKey> menu) {
        int bestScore = Integer.MAX_VALUE;
        ItemKey best = null;
        boolean criticalQuality = stats.quality() <= qualityNeeded;
        for (ItemKey item : contents(building)) {
            if (!eligible(item, menu)) {
                continue;
            }
            int score = score(item);
            if (urgentDish(item, score)) {
                return Optional.of(item);
            }
            if (score > bestScore) {
                continue;
            }
            bestScore = score;
            best = item;
            boolean dish = FoodRules.isDish(catalog, item);
            if (dish && !criticalQuality && luckyDish(item)) {
                continue;
            }
            if (goodEnough(score, dish)) {
                return Optional.of(item);
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * MC hasBestOptionInInv: whether the best food of {@code inventory} (a waiter's) for the citizen beats what
     * {@code building} holds; a dish it has not eaten lately in the building, when its meals are critical, wins.
     */
    public boolean bestIsInInventory(Inventory inventory, @Nullable Set<ItemKey> menu, Building building) {
        int slot = bestSlot(inventory, menu);
        int invScore = Integer.MAX_VALUE;
        if (slot >= 0) {
            ItemKey item = inventory.slot(slot).orElseThrow().item();
            invScore = score(item);
            if (urgentDish(item, invScore)) {
                return true;
            }
        }
        Optional<ItemKey> inBuilding = bestInBuilding(building, menu);
        if (inBuilding.isEmpty()) {
            return invScore < Integer.MAX_VALUE;
        }
        ItemKey item = inBuilding.get();
        return !urgentDish(item, score(item)) && invScore <= score(item);
    }

    /** MC: a dish it has not eaten lately (scored {@code score}) while its meals are critical, taken at once. */
    private boolean urgentDish(ItemKey item, int score) {
        return score < 0 && critical && FoodRules.isDish(catalog, item);
    }

    /** Whether it may eat {@code item}, on {@code menu} if given. */
    private boolean eligible(ItemKey item, @Nullable Set<ItemKey> menu) {
        return (menu == null || menu.contains(item)) && canEat(item);
    }

    /** MC: the last index in its history, doubled for an ordinary food; -1 (best) when not eaten lately. */
    private int score(ItemKey item) {
        return citizen.hunger().history().lastIndexOf(item) * (FoodRules.isDish(catalog, item) ? 1 : 2);
    }

    /** MC: food good enough to stop looking (a new dish, or a new food or any dish when its meals are fine). */
    private boolean goodEnough(int score, boolean dish) {
        return (score < 0 && dish)
                || (score < 0 && stats.quality() > qualityNeeded * 2)
                || (dish && stats.diversity() > diversityNeeded * 2);
    }

    /** MC: {@code RANDOM.nextInt(max(1, tier + 2 - homeLevel)) <= 0}, a dish taken for good at once. */
    private boolean luckyDish(ItemKey item) {
        int bound = Math.max(1, FoodRules.tier(catalog, item) + 2 - homeLevel);
        return colony.context().random().nextInt(bound) <= 0;
    }

    private boolean canEat(ItemKey item) {
        return FoodRules.canEat(colony, citizen, item);
    }

    /** MC: a dining hall near its work hut, else its home position. */
    private boolean hallNearby() {
        BlockPos from = citizen.workBuilding() != null
                ? citizen.workBuilding()
                : HomePosition.of(colony, citizen).orElse(null);
        return from != null && DiningHalls.closest(colony, from, false).isPresent();
    }

    private Set<ItemKey> contents(Building building) {
        return colony.context()
                .ports()
                .containers()
                .contents(building.containers())
                .keySet();
    }
}
