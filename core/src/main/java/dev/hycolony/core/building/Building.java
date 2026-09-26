package dev.hycolony.core.building;

import com.google.gson.JsonObject;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Requester;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.ResolverProvider;
import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class Building implements Requester, ResolverProvider {
    private final BuildingType type;
    private final BlockPos position;
    private final int rotation;
    private int level;
    private boolean built;
    private boolean deconstructed;
    private String customName = "";
    private String style = "";
    private final Map<String, BuildingModule> modules = new LinkedHashMap<>();
    private final Map<String, JsonObject> unknownModules = new LinkedHashMap<>();
    private final RequesterId requesterId;
    /** Registered containers, besides the hut block itself. */
    private final Set<BlockPos> containers = new LinkedHashSet<>();

    private List<Resolver> resolvers = List.of();

    private Building(BuildingType type, BlockPos position, int rotation) {
        this.type = type;
        this.position = position;
        this.rotation = rotation;
        this.requesterId = new RequesterId("building:" + position.x() + "," + position.y() + "," + position.z());
    }

    /** New building at level 0 with one fresh instance of each module of its type. */
    public static Building create(BuildingType type, BlockPos position, int rotation) {
        Building b = new Building(type, position, rotation);
        for (ModuleProducer producer : type.modules()) {
            b.modules.put(producer.key(), producer.factory().get());
        }
        return b;
    }

    public <T extends BuildingModule> Optional<T> module(Class<T> kind) {
        return modules.values().stream()
                .filter(kind::isInstance)
                .map(kind::cast)
                .findFirst();
    }

    public BuildingType type() {
        return type;
    }

    public BlockPos position() {
        return position;
    }

    public int rotation() {
        return rotation;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public boolean isBuilt() {
        return built;
    }

    public void setBuilt(boolean built) {
        this.built = built;
    }

    public boolean isDeconstructed() {
        return deconstructed;
    }

    public void setDeconstructed(boolean deconstructed) {
        this.deconstructed = deconstructed;
    }

    public String customName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String style() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public Map<String, BuildingModule> modules() {
        return Collections.unmodifiableMap(modules);
    }
    /** Injected by the colony when the building is added; creates its {@link BuildingResolver}. */
    public void attachContainers(ContainerAccess access) {
        resolvers = List.of(new BuildingResolver(this, access));
    }

    /** The hut block first, then the registered containers. */
    public List<BlockPos> containers() {
        List<BlockPos> out = new ArrayList<>(containers.size() + 1);
        out.add(position);
        out.addAll(containers);
        return out;
    }

    public Set<BlockPos> registeredContainers() {
        return Collections.unmodifiableSet(containers);
    }

    public void addContainer(BlockPos pos) {
        if (!pos.equals(position)) {
            containers.add(pos);
        }
    }

    public void removeContainer(BlockPos pos) {
        containers.remove(pos);
    }

    @Override
    public RequesterId requesterId() {
        return requesterId;
    }

    @Override
    public BlockPos location() {
        return position;
    }

    @Override
    public String displayName() {
        return customName.isEmpty() ? type.id() : customName;
    }

    @Override
    public void onRequestComplete(RequestManager manager, Request request) {}

    @Override
    public void onRequestCancelled(RequestManager manager, Request request) {}

    @Override
    public String providerId() {
        return requesterId.value();
    }

    @Override
    public List<Resolver> resolvers() {
        return resolvers;
    }

    /** Saved data of modules no longer registered for this type: written back untouched. */
    public Map<String, JsonObject> unknownModules() {
        return unknownModules;
    }
}
