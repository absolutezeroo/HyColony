package dev.hycolony.plugin.npc.body;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The citizen bodies of one world: each NPC entity tracked under the BodyId the core knows it by. World thread. */
public final class BodyRefs {
    private final Map<Long, Ref<EntityStore>> refs = new HashMap<>();
    private final IdentityHashMap<Ref<EntityStore>, Long> ids = new IdentityHashMap<>();
    private long nextId = 1;

    /** The id of the entity {@code ref}, given on its first sight and kept until it is untracked. */
    public BodyId track(Ref<EntityStore> ref) {
        Long existing = ids.get(ref);
        if (existing != null) {
            return new BodyId(existing);
        }
        long id = nextId++;
        refs.put(id, ref);
        ids.put(ref, id);
        return new BodyId(id);
    }

    /** Forgets the entity {@code ref}; its id, empty if it was not tracked. */
    public Optional<BodyId> untrack(Ref<EntityStore> ref) {
        Long id = ids.remove(ref);
        if (id == null) {
            return Optional.empty();
        }
        refs.remove(id);
        return Optional.of(new BodyId(id));
    }

    /** The loaded entity of {@code body}; empty once gone. */
    public Optional<Ref<EntityStore>> entity(BodyId body) {
        return Optional.ofNullable(ref(body));
    }

    /**
     * The loaded entity of {@code body}; null once gone or unloaded. A null rather than an Optional: the bodies' port
     * asks it on every call of a citizen's per-tick moves, where no allocation is wanted (CLAUDE.md § 4).
     */
    @Nullable
    public Ref<EntityStore> ref(BodyId body) {
        Ref<EntityStore> ref = refs.get(body.value());
        return ref != null && ref.isValid() ? ref : null;
    }

    /** Every tracked entity, loaded or not, read only. */
    public Collection<Ref<EntityStore>> all() {
        return Collections.unmodifiableCollection(refs.values());
    }
}
