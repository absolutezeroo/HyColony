package dev.hycolony.core.crafting.request;

import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.StackRequest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * MC AbstractCraftingRequestResolver.createsCraftingCycle: whether crafting for a request would ask again for what the
 * request or one of its ancestors already asks, so that crafting it would never end.
 */
final class CraftingCycles {
    /** MC Constants.MAX_CRAFTING_CYCLE_DEPTH: past this many ancestors, the chain is taken as a cycle. */
    static final int MAX_CRAFTING_CYCLE_DEPTH = 20;

    private CraftingCycles() {}

    /**
     * Whether {@code request} or one of its ancestors, other than {@code targetRequest}, asks for the same thing as
     * {@code target} and no more of it; true past {@link #MAX_CRAFTING_CYCLE_DEPTH} ancestors. Deviation from MC: an
     * ancestor the manager no longer knows ends the walk (no cycle), where MC would throw.
     */
    static boolean createsCycle(
            RequestManager m, Request request, Deliverable target, @Nullable Request targetRequest) {
        Request current = request;
        for (int depth = 0; depth <= MAX_CRAFTING_CYCLE_DEPTH; depth++) {
            if (!isTargetRequest(current, targetRequest)
                    && current.requestable() instanceof Deliverable asked
                    && sameThing(asked, target)
                    && asked.count() <= target.count()) {
                return true;
            }
            Optional<Request> parent = current.parent().flatMap(m::get);
            if (parent.isEmpty()) {
                return false;
            }
            current = parent.get();
        }
        return true;
    }

    private static boolean isTargetRequest(Request current, @Nullable Request targetRequest) {
        return targetRequest != null && targetRequest.token().equals(current.token());
    }

    /**
     * MC {@code request.getRequest().equals(target)}. MC Stack.equals ignores the counts, which our {@link StackRequest}
     * record compares, so two stacks are compared by item and building flag here.
     *
     * <p>Deviation from MC: a {@code StackList} (an ingredient given by resource type or tag) is never the same thing
     * as a {@link StackRequest}, even one of an item it accepts; MC asks an ingredient as a Stack of its exact item,
     * which such an ancestor would match. Such a loop only ends at {@link #MAX_CRAFTING_CYCLE_DEPTH}.
     */
    private static boolean sameThing(Deliverable asked, Deliverable target) {
        if (asked instanceof StackRequest a && target instanceof StackRequest b) {
            return a.item().equals(b.item()) && a.canBeResolvedByBuilding() == b.canBeResolvedByBuilding();
        }
        return asked.equals(target);
    }
}
