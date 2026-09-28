package dev.hycolony.core.crafting.request;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackList;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.request.FakeResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CraftingCyclesTest {
    private static final ItemKey X = new ItemKey("X");

    private final RequestManager m = new RequestManager(id -> Optional.empty(), new FakeCatalog());
    private final Chain chain = new Chain();

    CraftingCyclesTest() {
        m.registerBuiltIn(chain);
    }

    private static ItemKey level(int i) {
        return new ItemKey("L" + i);
    }

    private static StackRequest stack(ItemKey item, int count) {
        return new StackRequest(item, count, count, true);
    }

    /** The one request asking for {@code item}. */
    private Request asking(ItemKey item) {
        return m.all().stream()
                .filter(r ->
                        r.requestable() instanceof StackRequest s && s.item().equals(item))
                .findFirst()
                .orElseThrow();
    }

    /** A root request for {@code count} X, whose resolver asks for L1, which asks for L2, ... up to L25. */
    private Request rootOfAChain(int count) {
        return m.get(m.createAndAssign(chain, stack(X, count), -1)).orElseThrow();
    }

    @Test
    void sameItemAskedAgainByTheRequestIsACycle() {
        Request root = rootOfAChain(5);

        assertTrue(CraftingCycles.createsCycle(m, root, stack(X, 10), null));
    }

    @Test
    void countsAreIgnoredWhenComparingStacks() {
        Request root = m.get(m.createAndAssign(chain, new StackRequest(X, 5, 1, true), -1))
                .orElseThrow();

        assertTrue(CraftingCycles.createsCycle(m, root, stack(X, 10), null), "MC Stack.equals ignores the counts");
    }

    @Test
    void ancestorAskingMoreThanTheTargetIsNoCycle() {
        rootOfAChain(50);
        Request child = asking(level(1));

        assertFalse(CraftingCycles.createsCycle(m, child, stack(X, 10), null));
        assertTrue(CraftingCycles.createsCycle(m, child, stack(X, 50), null));
    }

    @Test
    void theTargetRequestItselfIsSkipped() {
        Request root = rootOfAChain(5);

        assertFalse(CraftingCycles.createsCycle(m, root, stack(X, 5), root));
    }

    @Test
    void anotherKindOfRequestNeverMatches() {
        Request root = rootOfAChain(5);

        assertFalse(CraftingCycles.createsCycle(m, root, new StackList(List.of(X), "X", 10, 10), null));
    }

    @Test
    void cycleCheckGivesUpBeyondTwentyAncestors() {
        rootOfAChain(1);
        StackRequest unrelated = stack(new ItemKey("Unrelated"), 1);

        assertFalse(CraftingCycles.createsCycle(m, asking(level(20)), unrelated, null), "20 ancestors");
        assertTrue(CraftingCycles.createsCycle(m, asking(level(21)), unrelated, null), "MC MAX_CRAFTING_CYCLE_DEPTH");
    }

    /** Asks, for X or any level below 25, for one of the next level. */
    private static final class Chain extends FakeResolver {
        Chain() {
            super("test:chain", 100);
        }

        @Override
        public boolean handles(Requestable requestable) {
            return requestable instanceof StackRequest s && next(s.item()).isPresent();
        }

        @Override
        public boolean canResolve(RequestManager manager, Request r) {
            return true;
        }

        @Override
        public Optional<List<Requestable>> attemptResolve(RequestManager manager, Request r) {
            return Optional.of(
                    List.of(stack(next(((StackRequest) r.requestable()).item()).orElseThrow(), 1)));
        }

        @Override
        public void resolve(RequestManager manager, Request r) {
            manager.updateState(r.token(), RequestState.RESOLVED);
        }

        private static Optional<ItemKey> next(ItemKey item) {
            if (item.equals(X)) {
                return Optional.of(level(1));
            }
            int i = Integer.parseInt(item.id().substring(1));
            return i < 25 ? Optional.of(level(i + 1)) : Optional.empty();
        }
    }
}
