package dev.hycolony.core.crafting.request;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.crafting.job.Crafter;
import dev.hycolony.core.crafting.job.CraftingTasks;
import dev.hycolony.core.crafting.module.CraftingHut;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.crafting.TestCrafters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A crafter hut with two crafter places and its crafting resolvers, plus another hut 5 blocks away that asks for
 * items; helpers to ask, stock the hut and read the crafting tasks.
 */
final class CraftingRig {
    static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");
    static final ItemKey ESSENCE = RecipeFixtures.ESSENCE;

    final CraftingHut h = new CraftingHut(CraftingHut.RULES, TestCrafters.hut(true, 2));
    final Building other =
            Building.create(new BuildingType("test:other", "hut.other", 1, List.of()), new BlockPos(13, 64, 4), 0);

    CraftingRig() {
        h.colony.buildings().add(other);
    }

    RequestManager m() {
        return h.colony.requests();
    }

    /** The Farmingbench seed recipe (2 essence each), taught to the hut with its bench, and a crafter hired. */
    CitizenData benchSeedsWithACrafter() {
        h.bench("Farmingbench", 1);
        h.teach(RecipeFixtures.at("Farmingbench", "Seeds", SEEDS.id()));
        return h.hire();
    }

    /** Puts {@code count} of {@code item} in the hut block's container. */
    void stock(ItemKey item, int count) {
        h.t.containers
                .containers
                .computeIfAbsent(h.hut.position(), _ -> new LinkedHashMap<>())
                .merge(item, count, Integer::sum);
    }

    int inHut(ItemKey item) {
        return h.t.containers.count(h.hut.containers(), item);
    }

    static CraftingTasks tasksOf(CitizenData crafter) {
        return ((Crafter) crafter.job().orElseThrow()).craftingTasks();
    }

    Request ask(Building from, ItemKey item, int count, int minCount) {
        return m().get(m().createAndAssign(from, new StackRequest(item, count, minCount, true), -1))
                .orElseThrow();
    }

    /** The first child of {@code parent}: the crafting task the crafting request resolver asked for. */
    Request task(Request parent) {
        return m().get(parent.children().getFirst()).orElseThrow();
    }

    List<Requestable> children(Request r) {
        List<Requestable> out = new ArrayList<>();
        for (RequestToken child : r.children()) {
            out.add(m().get(child).orElseThrow().requestable());
        }
        return out;
    }

    String resolverOf(Request r) {
        return m().resolverOf(r.token()).map(Resolver::resolverId).orElseThrow();
    }

    /** The hut's public or private resolver of {@code kind}. */
    <T extends Resolver> T resolver(Class<T> kind, boolean isPublic) {
        String visibility = isPublic ? ":public:" : ":private:";
        return h.hut.resolvers().stream()
                .filter(kind::isInstance)
                .filter(r -> r.resolverId().contains(visibility))
                .map(kind::cast)
                .findFirst()
                .orElseThrow();
    }
}
