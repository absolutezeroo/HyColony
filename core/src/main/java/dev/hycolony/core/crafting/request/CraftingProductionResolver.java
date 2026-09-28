package dev.hycolony.core.crafting.request;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.job.Crafter;
import dev.hycolony.core.crafting.job.Crafters;
import dev.hycolony.core.crafting.job.RecipeExecution;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.module.CraftingModules;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Makes a hut's crafting tasks (MC AbstractCraftingProductionResolver, with PublicWorkerCraftingProductionResolver and
 * PrivateWorkerCraftingProductionResolver): it takes a {@link Crafting} the hut's crafting request resolver asked for,
 * asks for the ingredients the hut lacks, then has it made. The public one gives the task to the least busy crafter of
 * the job, who makes it; the items then go to the requester. The private one makes it at once in the hut.
 */
final class CraftingProductionResolver implements Resolver {
    /** MC RSConstants.CONST_DEFAULT_RESOLVER_PRIORITY, which AbstractRequestResolver keeps. */
    static final int PRIORITY = 100;

    private final Colony colony;
    private final Building hut;
    private final String jobId;
    private final boolean isPublic;
    private final String id;
    private final RequesterId requesterId;

    /** {@code jobId}: the job whose workers craft at {@code hut} (MC jobEntry). */
    CraftingProductionResolver(Colony colony, Building hut, String jobId, boolean isPublic) {
        this.colony = colony;
        this.hut = hut;
        this.jobId = jobId;
        this.isPublic = isPublic;
        this.id = "crafting-production:" + (isPublic ? "public" : "private") + ":" + jobId + ":"
                + hut.requesterId().value();
        this.requesterId = new RequesterId("resolver:" + id);
    }

    /** Whether it makes public crafting tasks (MC PublicCrafting) rather than private ones. */
    boolean isPublic() {
        return isPublic;
    }

    @Override
    public String resolverId() {
        return id;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    /** MC getRequestType: PublicCrafting or PrivateCrafting. */
    @Override
    public boolean handles(Requestable requestable) {
        return requestable instanceof Crafting task && task.isPublic() == isPublic;
    }

    /** MC canResolveRequest: the task was asked at this hut (by one of its crafting request resolvers). */
    @Override
    public boolean canResolve(RequestManager m, Request r) {
        return HutLookups.isAt(hut, r.requester());
    }

    /**
     * MC attemptResolveForBuildingAndStack: the ingredients the task still needs ({@link TaskIngredients}). Empty if no
     * crafting module of the hut holds the task's recipe, or (public) the hut has no crafter: the task then waits for
     * another resolver, never cancelled and asked again in a loop.
     */
    @Override
    public Optional<List<Requestable>> attemptResolve(RequestManager m, Request r) {
        Crafting task = (Crafting) r.requestable(); // handles() takes crafting tasks only
        Optional<CraftingModule> module = CraftingModules.holding(hut, new RecipeId(task.recipeId()));
        if (module.isEmpty() || !canBuildingCraft()) {
            return Optional.empty();
        }
        return TaskIngredients.of(colony, hut, module.get(), task);
    }

    /** MC canBuildingCraftStack: the public one needs a crafter of the job at the hut; the private one crafts itself. */
    private boolean canBuildingCraft() {
        return !isPublic || !Crafters.ofJob(colony, hut, jobId).isEmpty();
    }

    /**
     * MC onAssignedToThisResolverForBuilding (public): the task is scheduled for the least busy crafter of the job,
     * the first of equals; nothing if there is none (MC then calls its no-op onAssignedRequestBeingCancelled).
     */
    @Override
    public void onAssigned(RequestManager m, Request r) {
        if (!isPublic) {
            return;
        }
        Crafter least = null;
        for (Crafter crafter : Crafters.ofJob(colony, hut, jobId)) {
            if (least == null
                    || crafter.craftingTasks().load() < least.craftingTasks().load()) {
                least = crafter;
            }
        }
        if (least != null) {
            least.craftingTasks().onTaskBeingScheduled(r.token());
            colony.markDirty();
        }
    }

    /** MC resolveForBuilding: the ingredients are there; see {@link #queueForCrafter} and {@link #craftNow}. */
    @Override
    public void resolve(RequestManager m, Request r) {
        if (isPublic) {
            queueForCrafter(m, r);
        } else {
            craftNow(m, r);
        }
    }

    /**
     * MC PublicWorkerCraftingProductionResolver.resolveForBuilding: the crafter of the job it was scheduled for
     * queues it, and the task stays in progress until that crafter finishes it; cancelled if none holds it any more.
     */
    private void queueForCrafter(RequestManager m, Request r) {
        for (Crafter crafter : Crafters.ofJob(colony, hut, jobId)) {
            if (crafter.craftingTasks().assignedTasks().contains(r.token())) {
                crafter.craftingTasks().onTaskBeingResolved(r.token());
                colony.markDirty();
                return;
            }
        }
        m.updateState(r.token(), RequestState.CANCELLED);
    }

    /**
     * MC PrivateWorkerCraftingProductionResolver.resolveForBuilding: all the runs are made at once in the hut
     * ({@link RecipeExecution#craftInHut}), then the task is RESOLVED; FAILED if the recipe or the crafting module
     * holding it is gone. MC first marks the task FINALIZING, a state nothing reads.
     */
    private void craftNow(RequestManager m, Request r) {
        Crafting task = (Crafting) r.requestable();
        RecipeId recipeId = new RecipeId(task.recipeId());
        Optional<Recipe> recipe = colony.recipes().get(recipeId);
        if (recipe.isEmpty() || CraftingModules.holding(hut, recipeId).isEmpty()) {
            m.updateState(r.token(), RequestState.FAILED);
            return;
        }
        for (int run = 0; run < task.count(); run++) {
            RecipeExecution.craftInHut(colony, hut, recipe.get());
        }
        m.updateState(r.token(), RequestState.RESOLVED);
    }

    /**
     * MC getFollowupRequestForCompletion (public; the private one has none): the task leaves its crafter's lists.
     * Unless the parent request was asked at this hut, the task's items become the parent's deliveries, and one
     * {@link Delivery} per stack takes them from the hut to the parent's requester.
     */
    @Override
    public List<Requestable> followups(RequestManager m, Request r) {
        if (!isPublic) {
            return List.of();
        }
        dropFromCrafter(r);
        Optional<Request> parent = r.parent().flatMap(m::get);
        if (parent.isEmpty() || HutLookups.isAt(hut, parent.get().requester())) {
            return List.of();
        }
        List<ItemAmount> stacks = fullStacks(r.deliveries(), m.catalog());
        List<Requestable> deliveries = new ArrayList<>(stacks.size());
        for (ItemAmount stack : stacks) {
            m.addDelivery(parent.get().token(), stack);
            deliveries.add(
                    new Delivery(hut.position(), parent.get().requester(), stack, Delivery.DEFAULT_DELIVERY_PRIORITY));
        }
        return deliveries;
    }

    /**
     * MC AbstractRequest.addDelivery's merge (InventoryUtils.processItemStackListAndMerge): the stacks of one item and
     * damage summed, then split into full stacks, in the order each item came first. Deviation from MC: the crafter
     * adds each run's output as a stack of its own, merged here once the task is done; MC merges at each run.
     */
    private static List<ItemAmount> fullStacks(List<ItemAmount> stacks, ItemCatalog catalog) {
        Map<ItemAmount, Integer> totals = new LinkedHashMap<>();
        for (ItemAmount stack : stacks) {
            totals.merge(stack.withCount(1), stack.count(), Integer::sum);
        }
        List<ItemAmount> out = new ArrayList<>(totals.size());
        totals.forEach((one, total) -> {
            int max = Math.max(1, catalog.maxStack(one.item()));
            for (int left = total; left > 0; left -= max) {
                out.add(one.withCount(Math.min(max, left)));
            }
        });
        return out;
    }

    /** MC onAssignedRequestCancelled (public): the task leaves its crafter's lists. */
    @Override
    public void onCancelled(RequestManager m, Request r) {
        if (isPublic) {
            dropFromCrafter(r);
        }
    }

    /** MC removeRequestFromTaskList: the first crafter of the colony holding the task forgets it. */
    private void dropFromCrafter(Request r) {
        Crafters.holding(colony, r.token()).ifPresent(crafter -> {
            crafter.craftingTasks().onTaskDeletion(r.token());
            colony.markDirty();
        });
    }

    /** MC AbstractRequestResolver.getSuitabilityMetric: 0, only one production resolver takes a task anyway. */
    @Override
    public double suitability(RequestManager m, Request r) {
        return 0;
    }

    @Override
    public RequesterId requesterId() {
        return requesterId;
    }

    @Override
    public BlockPos location() {
        return hut.position();
    }

    @Override
    public String displayName() {
        return hut.displayName();
    }

    /** MC onRequestedRequestComplete: nothing, the ingredients stay in the hut for the crafter. */
    @Override
    public void onRequestComplete(RequestManager manager, Request request) {}

    /** MC onRequestedRequestCancelled: nothing. */
    @Override
    public void onRequestCancelled(RequestManager manager, Request request) {}
}
