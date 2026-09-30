package dev.hycolony.core.building;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.module.BuildingModule;
import dev.hycolony.core.building.module.CreatesResolvers;
import dev.hycolony.core.building.module.ModuleProducer;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Requester;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.ResolverProvider;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequesterId;
import dev.hycolony.core.request.model.ToolRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntBiFunction;

public final class Building implements Requester, ResolverProvider {
    /** MC IBuilding.WOOD_HUT_LEVEL: up to this hut level the worker gets {@link #BASIC_TOOL_LEVEL} tools. */
    private static final int WOOD_HUT_LEVEL = 0;
    /** MC EquipmentLevelConstants.BASIC_TOOL_LEVEL (stone). */
    private static final int BASIC_TOOL_LEVEL = 1;

    private final BuildingType type;
    private final BlockPos position;
    private final int rotation;
    private int level;
    private boolean built;
    private boolean deconstructed;
    private String customName = "";
    private String style = "";
    private final PickupPriority pickupPriority = new PickupPriority();
    private final Map<String, BuildingModule> modules = new LinkedHashMap<>();
    private final Map<String, JsonObject> unknownModules = new LinkedHashMap<>();
    private final RequesterId requesterId;
    private final RegisteredBlocks registeredBlocks;

    private List<Resolver> resolvers = List.of();

    private Building(BuildingType type, BlockPos position, int rotation) {
        this.type = type;
        this.position = position;
        this.rotation = rotation;
        this.requesterId = new RequesterId("building:" + position.x() + "," + position.y() + "," + position.z());
        this.registeredBlocks = new RegisteredBlocks(position);
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

    /** MC AbstractBuilding.pickUp: a deconstructed hut goes back to a player's inventory; the town hall never does. */
    public boolean canBePickedUp() {
        return deconstructed && !type.equals(BuildingTypes.TOWN_HALL);
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

    /**
     * The highest tool level this hut's worker may use (MC {@code IBuilding.getMaxEquipmentLevel}): 1 at level 0
     * ({@code BASIC_TOOL_LEVEL}), the hut level up to its max, then unlimited ({@link ToolRequest#ANY_LEVEL}, MC
     * TOOL_LEVEL_MAXIMUM).
     */
    public int maxEquipmentLevel() {
        if (level >= type.maxLevel()) {
            return ToolRequest.ANY_LEVEL;
        }
        return level <= WOOD_HUT_LEVEL ? BASIC_TOOL_LEVEL : level - WOOD_HUT_LEVEL;
    }

    public PickupPriority pickupPriority() {
        return pickupPriority;
    }

    public Map<String, BuildingModule> modules() {
        return Collections.unmodifiableMap(modules);
    }
    /**
     * Injected by the colony when the building is added: its {@link BuildingResolver}, then {@code extra} (those of
     * its {@link CreatesResolvers} modules, MC {@code AbstractBuilding.createResolvers}).
     */
    public void attachResolvers(
            ContainerAccess access, List<Resolver> extra, ToIntBiFunction<Request, ItemKey> reserved) {
        List<Resolver> all = new ArrayList<>(extra.size() + 1);
        all.add(new BuildingResolver(this, access, reserved));
        all.addAll(extra);
        resolvers = List.copyOf(all);
    }

    /** The registered containers, then the hut block (MC AbstractBuildingContainer.getContainers). */
    public List<BlockPos> containers() {
        Set<BlockPos> registered = registeredBlocks.containers();
        List<BlockPos> out = new ArrayList<>(registered.size() + 1);
        out.addAll(registered);
        out.add(position);
        return out;
    }

    /** The containers and crafting benches of its plan this hut registered once placed. */
    public RegisteredBlocks registeredBlocks() {
        return registeredBlocks;
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

    /**
     * MC AbstractBuilding.onRequestedRequestComplete: a building-level request that brings no items (a pickup, a
     * courier delivery) is received at once, so it leaves the request system. Deviation from MC: MC receives every
     * building-level request here; a building-level item request stays COMPLETED for the worker taking its items,
     * since our builder files at building level the async requests MC files under its citizen.
     */
    @Override
    public void onRequestComplete(RequestManager manager, Request request) {
        if (request.citizenId() == Request.NO_CITIZEN && request.deliverable().isEmpty()) {
            manager.updateState(request.token(), RequestState.RECEIVED);
        }
    }

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

    /**
     * Whether this hut's own stock can serve {@code r} now: its {@link BuildingResolver}, not its crafters, which
     * asked again would rebuild a tree they hold. False before the colony attached the resolvers.
     */
    public boolean stockCanServe(RequestManager m, Request r) {
        return !resolvers.isEmpty() && resolvers.getFirst().canResolve(m, r);
    }

    /** Saved data of modules no longer registered for this type: written back untouched. */
    public Map<String, JsonObject> unknownModules() {
        return unknownModules;
    }
}
