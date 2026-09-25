package dev.hycolony.core.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.FakeCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class RequestManagerTest {

    static final ItemKey PLANK = new ItemKey("Plank");
    static final ItemKey LOG = new ItemKey("Log");
    static final ItemKey LOG2 = new ItemKey("Log2");
    static final ItemKey STONE = new ItemKey("Stone");

    static StackRequest stack(ItemKey item) {
        return new StackRequest(item, 4, 1, true);
    }

    static Predicate<Deliverable> item(ItemKey... items) {
        Set<ItemKey> set = Set.of(items);
        return d -> d instanceof StackRequest s && set.contains(s.item());
    }

    /** Shared event log to check callback ordering. */
    final List<String> log = new ArrayList<>();
    final Map<RequesterId, Requester> known = new HashMap<>();
    final RequestManager m = new RequestManager(id -> Optional.ofNullable(known.get(id)), new FakeCatalog());
    final TestRequester hut = requester("hut");

    TestRequester requester(String name) {
        TestRequester r = new TestRequester(name);
        known.put(r.requesterId(), r);
        return r;
    }

    FixedResolver resolver(String id, int priority, double suitability) {
        FixedResolver r = new FixedResolver(id, priority, suitability);
        m.registerBuiltIn(r);
        return r;
    }

    class TestRequester implements Requester {
        final String name;
        final List<Request> completed = new ArrayList<>();
        final List<Request> cancelled = new ArrayList<>();
        BiConsumer<RequestManager, Request> onComplete = (mm, r) -> {};

        TestRequester(String name) { this.name = name; }

        @Override public RequesterId requesterId() { return new RequesterId("req:" + name); }
        @Override public BlockPos location() { return new BlockPos(0, 0, 0); }
        @Override public String displayName() { return name; }

        @Override
        public void onRequestComplete(RequestManager manager, Request request) {
            log.add(name + ".complete:" + request.requestable().describe());
            completed.add(request);
            onComplete.accept(manager, request);
        }

        @Override
        public void onRequestCancelled(RequestManager manager, Request request) {
            log.add(name + ".requestCancelled:" + request.requestable().describe());
            cancelled.add(request);
        }
    }

    /** FixedResolver(priority, suitability, canResolve, children, followups). */
    class FixedResolver extends TestRequester implements Resolver {
        final int priority;
        final double suitability;
        boolean canResolve = true;
        boolean attemptSucceeds = true;
        boolean resolveImmediately = false;
        List<Deliverable> children = List.of();
        List<Deliverable> followups = List.of();
        Predicate<Deliverable> handles = d -> true;
        RequesterId servesOnly;
        int canResolveCalls, attempts, ticks;
        final List<Request> assigned = new ArrayList<>();
        final List<Request> resolved = new ArrayList<>();
        final List<Request> cancelling = new ArrayList<>();
        final List<Request> cancelledAssigned = new ArrayList<>();
        final List<Predicate<Request>> updates = new ArrayList<>();

        FixedResolver(String id, int priority, double suitability) {
            super(id);
            this.priority = priority;
            this.suitability = suitability;
        }

        @Override public String resolverId() { return name; }
        @Override public int priority() { return priority; }
        @Override public boolean handles(Deliverable d) { return handles.test(d); }
        @Override public Optional<RequesterId> servesOnly() { return Optional.ofNullable(servesOnly); }
        @Override public double suitability(RequestManager mm, Request r) { return suitability; }

        @Override
        public boolean canResolve(RequestManager mm, Request r) {
            canResolveCalls++;
            return canResolve;
        }

        @Override
        public Optional<List<Deliverable>> attemptResolve(RequestManager mm, Request r) {
            attempts++;
            return attemptSucceeds ? Optional.of(children) : Optional.empty();
        }

        @Override
        public void resolve(RequestManager mm, Request r) {
            log.add(name + ".resolve:" + r.requestable().describe());
            resolved.add(r);
            if (resolveImmediately) {
                mm.updateState(r.token(), RequestState.RESOLVED);
            }
        }

        @Override public List<Deliverable> followups(RequestManager mm, Request r) { return followups; }

        @Override
        public void onAssigned(RequestManager mm, Request r) {
            log.add(name + ".assigned:" + r.requestable().describe());
            assigned.add(r);
        }

        @Override
        public void onCancelling(RequestManager mm, Request r) {
            log.add(name + ".cancelling:" + r.requestable().describe());
            cancelling.add(r);
        }

        @Override
        public void onCancelled(RequestManager mm, Request r) {
            log.add(name + ".cancelled:" + r.requestable().describe());
            cancelledAssigned.add(r);
        }

        @Override public void onColonyUpdate(RequestManager mm, Predicate<Request> which) { updates.add(which); }
        @Override public void tick(RequestManager mm) { ticks++; }
    }

    Request req(RequestToken t) {
        return m.get(t).orElseThrow();
    }

    Resolver resolverOf(RequestToken t) {
        return m.resolverOf(t).orElseThrow();
    }

    @Test
    void assignsHighestPriorityThatCanResolve() {
        FixedResolver player = resolver("player", 0, 0);
        FixedResolver low = resolver("low", 100, 0);
        FixedResolver attemptFails = resolver("attemptFails", 150, 0);
        attemptFails.attemptSucceeds = false;
        FixedResolver cannot = resolver("cannot", 200, 0);
        cannot.canResolve = false;
        FixedResolver other = resolver("other", 300, 0);
        other.handles = item(STONE);

        RequestToken t = m.createAndAssign(hut, stack(PLANK), 3);

        assertSame(low, resolverOf(t));
        assertEquals(1, cannot.canResolveCalls);
        assertEquals(0, cannot.attempts, "attempt only after canResolve");
        assertEquals(1, attemptFails.attempts);
        assertEquals(0, other.canResolveCalls, "does not handle the requestable");
        assertEquals(0, player.canResolveCalls, "lower priority never reached");
        assertEquals(RequestState.IN_PROGRESS, req(t).state());
        assertEquals(3, req(t).citizenId());
        assertEquals(List.of(req(t)), m.assignedTo("low"));
        assertEquals(List.of(req(t)), m.byRequester(hut.requesterId()));
    }

    @Test
    void samePriorityBetterSuitabilityWinsAndCancelsPreviousChildren() {
        FixedResolver stock = resolver("stock", 200, 0);
        stock.handles = item(LOG);
        FixedResolver a = resolver("a", 100, 5);
        a.handles = item(PLANK);
        a.children = List.of(stack(LOG));
        FixedResolver b = resolver("b", 100, 1);
        b.handles = item(PLANK);
        FixedResolver c = resolver("c", 100, 3);
        c.handles = item(PLANK);

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);

        assertSame(b, resolverOf(t));
        assertEquals(1, a.cancelled.size(), "a's already-created child is cancelled");
        assertEquals(RequestState.CANCELLED, a.cancelled.get(0).state());
        assertEquals(0, c.attempts, "suitability 3 is not better than 1");
        assertTrue(stock.assigned.isEmpty(), "the discarded child was never assigned");
        assertEquals(1, m.all().size());
        assertTrue(m.byRequester(a.requesterId()).isEmpty());
        assertTrue(req(t).children().isEmpty());
        assertEquals(List.of(req(t)), b.resolved, "no children: resolved immediately");
    }

    @Test
    void priorityChangeStopsSearch() {
        FixedResolver high = resolver("high", 200, 5);
        FixedResolver better = resolver("better", 100, 0);

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);

        assertSame(high, resolverOf(t));
        assertEquals(0, better.canResolveCalls);
        assertEquals(0, better.attempts);
    }

    @Test
    void blacklistIsInheritedByChildren() {
        FixedResolver s1 = resolver("s1", 200, 0);
        s1.handles = item(LOG);
        FixedResolver s2 = resolver("s2", 150, 0);
        s2.handles = item(LOG);

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);
        assertEquals(RequestState.REPORTED, req(t).state(), "no candidate: stays REPORTED");
        assertTrue(m.resolverOf(t).isEmpty());

        FixedResolver crafter = resolver("crafter", 100, 0);
        crafter.handles = item(PLANK);
        crafter.children = List.of(stack(LOG));
        m.assign(t, Set.of("s1"));

        assertSame(crafter, resolverOf(t));
        RequestToken child = req(t).children().get(0);
        assertSame(s2, resolverOf(child));
        assertEquals(0, s1.canResolveCalls);
        assertEquals(Optional.of(t), req(child).parent());
        assertEquals(crafter.requesterId(), req(child).requester());
    }

    @Test
    void noChildrenResolvesImmediately() {
        FixedResolver r = resolver("r", 100, 0);

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);

        assertEquals(RequestState.IN_PROGRESS, req(t).state());
        assertEquals(List.of(req(t)), r.resolved);
        assertEquals(List.of("r.assigned:4 x Plank", "r.resolve:4 x Plank"), log);
    }

    @Test
    void childrenCompleteThenParentResolved() {
        FixedResolver stock = resolver("stock", 200, 0);
        stock.handles = item(LOG, LOG2);
        FixedResolver crafter = resolver("crafter", 100, 0);
        crafter.handles = item(PLANK);
        crafter.children = List.of(stack(LOG), stack(LOG2));

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);
        Request parent = req(t);
        assertEquals(RequestState.IN_PROGRESS, parent.state());
        assertTrue(crafter.resolved.isEmpty(), "waits for its children");
        RequestToken c1 = parent.children().get(0);
        RequestToken c2 = parent.children().get(1);
        assertEquals(RequestState.IN_PROGRESS, req(c1).state());
        assertEquals(2, stock.resolved.size());

        m.updateState(c1, RequestState.RESOLVED);
        assertTrue(m.get(c1).isEmpty(), "child received and cleaned");
        assertEquals(List.of(c2), parent.children());
        assertTrue(crafter.resolved.isEmpty());

        m.updateState(c2, RequestState.RESOLVED);
        assertEquals(2, crafter.completed.size(), "the crafter is the children's requester");
        assertEquals(List.of(parent), crafter.resolved);
        assertEquals(RequestState.IN_PROGRESS, parent.state());
        assertEquals(1, m.all().size());
    }

    @Test
    void followupsThenCompleted() {
        FixedResolver courier = resolver("courier", 200, 0);
        courier.handles = item(LOG);
        FixedResolver warehouse = resolver("warehouse", 100, 0);
        warehouse.handles = item(PLANK);
        warehouse.followups = List.of(stack(LOG));

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);
        m.updateState(t, RequestState.RESOLVED);

        Request parent = req(t);
        assertEquals(RequestState.FOLLOWUP_IN_PROGRESS, parent.state());
        RequestToken followup = parent.children().get(0);
        assertSame(courier, resolverOf(followup));
        assertEquals(warehouse.requesterId(), req(followup).requester());
        assertTrue(hut.completed.isEmpty());

        m.updateState(followup, RequestState.RESOLVED);
        assertEquals(RequestState.COMPLETED, parent.state());
        assertEquals(List.of(parent), hut.completed);
        assertTrue(m.get(followup).isEmpty());

        m.updateState(t, RequestState.RECEIVED);
        assertTrue(m.all().isEmpty());
    }

    @Test
    void childFailureReassignsParentWithBlacklistPreserved() {
        FixedResolver stock = resolver("stock", 300, 0);
        stock.handles = item(LOG);
        FixedResolver banned = resolver("banned", 250, 0);
        FixedResolver crafter = resolver("crafter", 200, 0);
        crafter.handles = item(PLANK);
        crafter.children = List.of(stack(LOG), stack(LOG2));
        FixedResolver fallback = resolver("fallback", 100, 0);
        fallback.handles = item(PLANK, LOG2);

        RequestToken p = m.createAndAssign(hut, stack(PLANK), -1);
        assertSame(banned, resolverOf(p));
        m.reassign(p, Set.of("banned"));
        assertSame(crafter, resolverOf(p));
        Request parent = req(p);
        RequestToken c1 = parent.children().get(0);
        RequestToken c2 = parent.children().get(1);
        m.addDelivery(p, new ItemAmount(PLANK, 2));

        crafter.canResolve = false;
        m.updateState(c1, RequestState.FAILED);

        assertSame(fallback, resolverOf(p), "banned (250) stays blacklisted");
        assertEquals(1, banned.assigned.stream().filter(r -> r == parent).count());
        assertTrue(parent.deliveries().isEmpty());
        assertTrue(m.get(c1).isEmpty());
        assertTrue(m.get(c2).isEmpty(), "siblings are cancelled too");
        assertEquals(2, crafter.cancelled.size(), "crafter notified as requester of both children");
        assertEquals(List.of(parent), crafter.cancelledAssigned);
        assertTrue(parent.children().isEmpty());
        assertEquals(2, fallback.resolved.size(), "first the LOG2 sibling, then the reassigned parent");
        assertSame(parent, fallback.resolved.get(1));
        assertTrue(hut.cancelled.isEmpty());
    }

    @Test
    void cancelWithoutParentCancelsSubtreeAndNotifiesRequester() {
        FixedResolver quarry = resolver("quarry", 300, 0);
        quarry.handles = item(STONE);
        FixedResolver stock = resolver("stock", 200, 0);
        stock.handles = item(LOG);
        stock.children = List.of(stack(STONE));
        FixedResolver crafter = resolver("crafter", 100, 0);
        crafter.handles = item(PLANK);
        crafter.children = List.of(stack(LOG));

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);
        Request parent = req(t);
        Request child = req(parent.children().get(0));
        Request grandchild = req(child.children().get(0));
        assertSame(quarry, resolverOf(grandchild.token()));

        m.updateState(t, RequestState.CANCELLED);

        assertTrue(m.all().isEmpty());
        assertEquals(List.of(parent), hut.cancelled);
        assertEquals(List.of(child), crafter.cancelled);
        assertEquals(List.of(grandchild), stock.cancelled);
        assertEquals(List.of(grandchild), quarry.cancelling);
        assertEquals(List.of(grandchild), quarry.cancelledAssigned);
        assertEquals(List.of(parent), crafter.cancelledAssigned);
        assertEquals(RequestState.CANCELLED, grandchild.state());
        assertTrue(m.assignedTo("quarry").isEmpty());
        assertTrue(m.byRequester(hut.requesterId()).isEmpty());
        assertTrue(log.indexOf("quarry.cancelled:4 x Stone") < log.indexOf("hut.requestCancelled:4 x Plank"),
                "children first");
    }

    @Test
    void cancelAllFromCancelsEveryRequestOfThatRequesterWithSubtrees() {
        FixedResolver stock = resolver("stock", 200, 0);
        stock.handles = item(LOG);
        FixedResolver crafter = resolver("crafter", 100, 0);
        crafter.handles = item(PLANK);
        crafter.children = List.of(stack(LOG));
        TestRequester other = requester("other");

        RequestToken a = m.createAndAssign(hut, stack(PLANK), -1);
        RequestToken b = m.createAndAssign(hut, stack(LOG), -1);
        RequestToken kept = m.createAndAssign(other, stack(LOG), -1);
        Request child = req(req(a).children().get(0));

        m.cancelAllFrom(hut.requesterId());

        assertEquals(List.of(kept), m.all().stream().map(Request::token).toList());
        assertEquals(2, hut.cancelled.size());
        assertEquals(List.of(child), crafter.cancelled);
        assertTrue(m.byRequester(hut.requesterId()).isEmpty());
        assertTrue(m.get(b).isEmpty());
    }

    @Test
    void cancelAllFromCitizenCancelsOnlyThatCitizensRequests() {
        resolver("stock", 200, 0).handles = item(LOG);
        RequestToken mine = m.createAndAssign(hut, stack(LOG), 7);
        RequestToken building = m.createAndAssign(hut, stack(LOG), -1);
        RequestToken other = m.createAndAssign(hut, stack(LOG), 8);

        m.cancelAllFrom(hut.requesterId(), 7);

        assertTrue(m.get(mine).isEmpty());
        assertEquals(List.of(building, other), m.all().stream().map(Request::token).toList());
        assertEquals(1, hut.cancelled.size());
    }

    @Test
    void makeSyncMovesABuildingRequestToACitizen() {
        resolver("stock", 200, 0).handles = item(LOG);
        RequestToken t = m.createAndAssign(hut, stack(LOG), -1);

        m.makeSync(t, 7);

        assertEquals(7, req(t).citizenId());
        assertEquals(RequestState.IN_PROGRESS, req(t).state()); // untouched otherwise
    }

    @Test
    void overruleCompletesOnceAndCancelsChildren() {
        FixedResolver stock = resolver("stock", 200, 0);
        stock.handles = item(LOG);
        FixedResolver crafter = resolver("crafter", 100, 0);
        crafter.handles = item(PLANK);
        crafter.children = List.of(stack(LOG));

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);
        RequestToken child = req(t).children().get(0);
        log.clear();

        m.overrule(t, List.of(new ItemAmount(PLANK, 3)));
        m.overrule(t, List.of(new ItemAmount(PLANK, 1)));

        Request parent = req(t);
        assertEquals(1, hut.completed.size());
        assertEquals(RequestState.COMPLETED, parent.state());
        assertEquals(List.of(new ItemAmount(PLANK, 3)), parent.deliveries());
        assertTrue(m.get(child).isEmpty());
        assertEquals(1, crafter.cancelled.size(), "child cancelled directly");
        assertEquals(List.of("stock.cancelling:4 x Log", "stock.cancelled:4 x Log", "crafter.requestCancelled:4 x Log",
                "crafter.cancelling:4 x Plank", "hut.complete:4 x Plank", "crafter.cancelled:4 x Plank"), log);
    }

    @Test
    void receivedCleansAllIndexes() {
        FixedResolver r = resolver("r", 100, 0);

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);
        m.updateState(t, RequestState.RESOLVED);
        assertEquals(RequestState.COMPLETED, req(t).state());
        m.updateState(t, RequestState.RECEIVED);

        assertTrue(m.get(t).isEmpty());
        assertTrue(m.resolverOf(t).isEmpty());
        assertTrue(m.byRequester(hut.requesterId()).isEmpty());
        assertTrue(m.assignedTo(r.resolverId()).isEmpty());
        assertTrue(m.all().isEmpty());
    }

    @Test
    void providerRemovedReassignsToOthers() {
        FixedResolver stock = resolver("stock", 300, 0);
        stock.handles = item(LOG);
        FixedResolver other = resolver("other", 100, 0);
        other.handles = item(PLANK);
        FixedResolver a = new FixedResolver("a", 200, 0);
        a.handles = item(PLANK);
        a.children = List.of(stack(LOG));
        FixedResolver a2 = new FixedResolver("a2", 150, 0);
        ResolverProvider provider = new ResolverProvider() {
            @Override public String providerId() { return "hut-provider"; }
            @Override public List<Resolver> resolvers() { return List.of(a, a2); }
        };
        m.onProviderAdded(provider);

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);
        assertSame(a, resolverOf(t));
        RequestToken child = req(t).children().get(0);

        m.onProviderRemoved(provider);

        assertSame(other, resolverOf(t));
        assertTrue(m.get(child).isEmpty());
        assertEquals(List.of(req(t)), a.cancelledAssigned);
        assertEquals(0, a2.canResolveCalls, "all the provider's resolvers are blacklisted");
        assertTrue(m.assignedTo("a").isEmpty());
        assertEquals(List.of(req(t)), other.resolved);

        int before = a.canResolveCalls;
        m.createAndAssign(hut, stack(PLANK), -1);
        assertEquals(before, a.canResolveCalls, "removed resolvers are no longer candidates");
    }

    @Test
    void reentrantUpdateFromCallbackIsQueued() {
        FixedResolver stock = resolver("stock", 200, 0);
        stock.handles = item(LOG);
        stock.resolveImmediately = true;
        FixedResolver crafter = resolver("crafter", 100, 0);
        crafter.handles = item(PLANK);
        crafter.children = List.of(stack(LOG));
        crafter.resolveImmediately = true;
        // In MineColonies this would re-enter onRequestCompleted and crash on the already-cleaned child.
        crafter.onComplete = (mm, r) -> mm.updateState(r.token(), RequestState.RECEIVED);
        List<RequestState> seen = new ArrayList<>();
        hut.onComplete = (mm, r) -> {
            mm.updateState(r.token(), RequestState.RECEIVED);
            seen.add(r.state());
            seen.add(mm.get(r.token()).map(Request::state).orElse(null));
        };

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);

        assertEquals(List.of(RequestState.COMPLETED, RequestState.COMPLETED), seen,
                "the RECEIVED update is queued, not applied inside the callback");
        assertEquals(1, crafter.completed.size());
        assertEquals(1, hut.completed.size());
        assertTrue(m.get(t).isEmpty());
        assertTrue(m.all().isEmpty());
    }

    @Test
    void tickAndColonyUpdateDelegateToEveryResolver() {
        FixedResolver a = resolver("a", 100, 0);
        FixedResolver b = resolver("b", 0, 0);
        Predicate<Request> which = r -> true;

        m.tick();
        m.onColonyUpdate(which);

        assertEquals(1, a.ticks);
        assertEquals(1, b.ticks);
        assertEquals(List.of(which), a.updates);
        assertEquals(List.of(which), b.updates);
    }

    @Test
    void duplicateRequesterIdIsRejected() {
        resolver("a", 100, 0);
        FixedResolver sameRequesterId = new FixedResolver("a", 50, 0) {
            @Override public String resolverId() { return "a-bis"; }
        };
        assertThrows(IllegalArgumentException.class, () -> m.registerBuiltIn(sameRequesterId));

        FixedResolver clashesWithHut = new FixedResolver("hut", 50, 0); // requesterId "req:hut" is a known requester
        assertThrows(IllegalArgumentException.class, () -> m.registerBuiltIn(clashesWithHut));

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);
        assertEquals("a", resolverOf(t).resolverId(), "rejected resolvers were not registered");
    }

    @Test
    void providerWithDuplicateIdRegistersNothing() {
        FixedResolver taken = resolver("taken", 0, 0);
        FixedResolver fresh = new FixedResolver("fresh", 200, 0);
        FixedResolver dup = new FixedResolver("taken", 150, 0);
        ResolverProvider bad = provider("p", fresh, dup);
        assertThrows(IllegalArgumentException.class, () -> m.onProviderAdded(bad));

        RequestToken t = m.createAndAssign(hut, stack(PLANK), -1);
        assertSame(taken, resolverOf(t));
        assertEquals(0, fresh.canResolveCalls, "fresh must not be partially registered");

        FixedResolver f1 = new FixedResolver("f1", 200, 0);
        FixedResolver f1again = new FixedResolver("f2", 200, 0) {
            @Override public String resolverId() { return "f1"; }
        };
        assertThrows(IllegalArgumentException.class, () -> m.onProviderAdded(provider("q", f1, f1again)),
                "duplicates inside the provider itself");
        m.onProviderAdded(provider("p", fresh)); // the failed provider id is still free
        assertSame(fresh, resolverOf(m.createAndAssign(hut, stack(PLANK), -1)));
    }

    ResolverProvider provider(String id, Resolver... rs) {
        return new ResolverProvider() {
            @Override public String providerId() { return id; }
            @Override public List<Resolver> resolvers() { return List.of(rs); }
        };
    }

    @Test
    void failingOpDoesNotLeakQueuedOpsIntoNextCall() {
        FixedResolver boom = new FixedResolver("boom", 100, 0) {
            @Override
            public void resolve(RequestManager mm, Request r) {
                mm.updateState(r.token(), RequestState.RESOLVED); // queued...
                throw new IllegalStateException("boom");           // ...then the op fails
            }
        };
        m.registerBuiltIn(boom);

        assertThrows(IllegalStateException.class, () -> m.createAndAssign(hut, stack(PLANK), -1));
        Request r = m.all().iterator().next();
        assertEquals(RequestState.IN_PROGRESS, r.state());

        m.tick();

        assertEquals(RequestState.IN_PROGRESS, r.state(), "the queued RESOLVED was dropped, not run later");
        assertTrue(hut.completed.isEmpty());
    }

    @Test
    void thousandRequestsAssignInUnder50ms() {
        runBatch(new RequestManager(id -> Optional.empty(), new FakeCatalog())); // JIT warm-up
        RequestManager fresh = new RequestManager(id -> Optional.ofNullable(known.get(id)), new FakeCatalog());

        long elapsed = runBatch(fresh);

        assertEquals(1000, fresh.all().size());
        assertTrue(fresh.all().stream().allMatch(r -> fresh.resolverOf(r.token()).isPresent()));
        assertTrue(elapsed < 50_000_000L, "took " + elapsed / 1_000_000 + " ms");
    }

    private long runBatch(RequestManager manager) {
        FixedResolver cannot = new FixedResolver("cannot", 200, 0);
        cannot.canResolve = false;
        manager.registerBuiltIn(cannot);
        FixedResolver stone = new FixedResolver("stone", 150, 0);
        stone.handles = item(STONE);
        manager.registerBuiltIn(stone);
        manager.registerBuiltIn(new FixedResolver("w1", 100, 2));
        manager.registerBuiltIn(new FixedResolver("w2", 100, 1));
        manager.registerBuiltIn(new FixedResolver("player", 0, 0));
        ItemKey[] items = {PLANK, LOG, STONE};
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            manager.createAndAssign(hut, stack(items[i % items.length]), i % 7);
        }
        return System.nanoTime() - start;
    }

    @Test
    void ownResolverIsOfferedOnlyItsRequestersRequests() {
        TestRequester other = requester("other");
        FixedResolver own = new FixedResolver("own", 200, 0);
        own.servesOnly = hut.requesterId();
        m.registerBuiltIn(own);
        FixedResolver player = resolver("player", 0, 0);

        RequestToken fromOther = m.createAndAssign(other, stack(PLANK), -1);
        assertEquals(0, own.canResolveCalls);
        assertSame(player, m.resolverOf(fromOther).orElseThrow());

        RequestToken fromHut = m.createAndAssign(hut, stack(PLANK), -1);
        assertEquals(1, own.canResolveCalls);
        assertSame(own, m.resolverOf(fromHut).orElseThrow());
    }
}
