package dev.hycolony.core.crafting.request;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.module.CraftingModules;
import dev.hycolony.core.crafting.module.RecipeChoice;
import dev.hycolony.core.crafting.module.RecipeChoice.Chosen;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.logistics.warehouse.RequesterLocation;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.RequesterId;
import java.util.List;
import java.util.Optional;

/**
 * Turns an item request into crafting tasks for a hut's crafters (MC AbstractCraftingRequestResolver, with
 * PublicWorkerCraftingRequestResolver and PrivateWorkerCraftingRequestResolver): it takes a request that a recipe the
 * hut learnt answers, unless crafting it would loop, and asks for one {@link Crafting} per batch that fits a crafter's
 * inventory; the request is resolved once they are done. The public one serves any requester; the private one only its
 * own hut (the hut or one of its resolvers), with recipes made by hand. MC also refuses a furnace recipe for a generic
 * food request; there is neither here.
 */
final class CraftingRequestResolver implements Resolver {
    /** MC RSConstants.CONST_CRAFTING_RESOLVER_PRIORITY: the default resolver priority (100) plus 25. */
    static final int PRIORITY = 125;

    private final Colony colony;
    private final Building hut;
    private final String jobId;
    private final boolean isPublic;
    private final CraftingBatches batches;
    private final String id;
    private final RequesterId requesterId;

    /** {@code jobId}: the job whose workers craft at {@code hut} (MC jobEntry). */
    CraftingRequestResolver(Colony colony, Building hut, String jobId, boolean isPublic) {
        this.colony = colony;
        this.hut = hut;
        this.jobId = jobId;
        this.isPublic = isPublic;
        this.batches = new CraftingBatches(
                colony.context().ports().catalog(),
                colony.context().ports().crafting().catalog());
        this.id = "crafting:" + (isPublic ? "public" : "private") + ":" + jobId + ":"
                + hut.requesterId().value();
        this.requesterId = new RequesterId("resolver:" + id);
    }

    /** MC isPublicCrafter. */
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

    /** MC getRequestType: IDeliverable. */
    @Override
    public boolean handles(Requestable requestable) {
        return requestable instanceof Deliverable;
    }

    /**
     * MC canResolveRequest: any requester for the public resolver, its own hut for the private one; then the hut must
     * still stand and be able to craft it ({@link #canCraft}).
     */
    @Override
    public boolean canResolve(RequestManager m, Request r) {
        if (!isPublic && !HutLookups.isAt(hut, r.requester())) {
            return false;
        }
        Optional<Deliverable> asked = r.deliverable();
        return asked.isPresent() && standing() && canCraft(m, r, asked.get());
    }

    /**
     * MC canResolveForBuilding: a built hut with a worker for the job, no cycle for the request itself, and a crafting
     * module whose first recipe for the item this resolver may use and whose ingredients make no cycle.
     */
    private boolean canCraft(RequestManager m, Request r, Deliverable asked) {
        if (hut.level() <= 0 || !HutLookups.employs(hut, jobId) || CraftingCycles.createsCycle(m, r, asked, r)) {
            return false;
        }
        for (CraftingModule module : CraftingModules.of(hut)) {
            Optional<Recipe> recipe = firstRecipe(m, module, asked).map(Chosen::recipe);
            if (recipe.isPresent() && mayUse(recipe.get()) && !ingredientsCycle(m, r, asked, recipe.get())) {
                return true;
            }
        }
        return false;
    }

    /** MC's check of each ingredient, asked for as many runs as the request needs (MC's integer division). */
    private boolean ingredientsCycle(RequestManager m, Request r, Deliverable asked, Recipe recipe) {
        int runs = asked.count() / recipe.primaryOutput().count();
        for (Ingredient in : recipe.cleanedInput()) {
            int count = in.amount() * runs;
            Deliverable ingredient = IngredientRequests.of(
                    in, count, count, colony.context().ports().crafting().catalog());
            if (CraftingCycles.createsCycle(m, r, ingredient, null)) {
                return true;
            }
        }
        return false;
    }

    /** MC canBuildingCraftRecipe: any recipe for the public resolver, one made by hand (intermediate AIR) otherwise. */
    private boolean mayUse(Recipe recipe) {
        return isPublic || recipe.bench().isFieldcraft();
    }

    /**
     * MC attemptResolveForBuildingAndStack: the batches ({@link CraftingBatches}) of the first recipe the hut's
     * crafting modules have for the item; empty if none.
     */
    @Override
    public Optional<List<Requestable>> attemptResolve(RequestManager m, Request r) {
        Deliverable asked = r.deliverable().orElseThrow(); // handles() takes deliverables only
        for (CraftingModule module : CraftingModules.of(hut)) {
            Optional<Chosen> chosen = firstRecipe(m, module, asked);
            if (chosen.isPresent()) {
                return Optional.of(List.<Requestable>copyOf(
                        batches.split(chosen.get(), asked.count(), asked.minCount(), isPublic)));
            }
        }
        return Optional.empty();
    }

    /** MC resolveForBuilding: its crafting tasks are done. */
    @Override
    public void resolve(RequestManager m, Request r) {
        m.updateState(r.token(), RequestState.RESOLVED);
    }

    /**
     * MC getSuitabilityMetric: the distance from the requester to the hut, in whole blocks. Deviation from MC: an
     * unknown requester (its hut removed) scores the worst; MC's requester keeps its saved location.
     */
    @Override
    public double suitability(RequestManager m, Request r) {
        Optional<BlockPos> from = RequesterLocation.of(colony, r.requester());
        if (from.isEmpty()) {
            return Double.MAX_VALUE;
        }
        return (int) Math.sqrt((double) from.get().distSq(hut.position()));
    }

    /** MC getFirstRecipe with {@code request.getRequest().matches(itemStack)}. */
    private Optional<Chosen> firstRecipe(RequestManager m, CraftingModule module, Deliverable asked) {
        return RecipeChoice.firstRecipe(colony, hut, module, item -> asked.matches(item, m.catalog()));
    }

    /** MC getBuilding: the hut is still one of the colony's buildings. */
    private boolean standing() {
        return colony.buildings().at(hut.position()).filter(hut::equals).isPresent();
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

    /** MC onRequestedRequestComplete: nothing, the production resolver hands the items over. */
    @Override
    public void onRequestComplete(RequestManager manager, Request request) {}

    /** MC onRequestedRequestCancelled: nothing. */
    @Override
    public void onRequestCancelled(RequestManager manager, Request request) {}
}
