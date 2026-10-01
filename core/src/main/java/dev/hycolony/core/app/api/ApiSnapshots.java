package dev.hycolony.core.app.api;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Vec;
import dev.hycolony.api.read.BuildingSnapshot;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.api.read.CitizenWellbeing;
import dev.hycolony.api.read.ColonySummary;
import dev.hycolony.api.read.RequestSnapshot;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.logistics.warehouse.RequesterLocation;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackList;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Turns the core's colonies, citizens, buildings and requests into the api's snapshots. */
final class ApiSnapshots {
    private ApiSnapshots() {}

    /** {@code c} under {@code ref}: its name, founding centre, owner and citizen count. */
    static ColonySummary colony(ColonyRef ref, Colony c) {
        return new ColonySummary(
                ref,
                c.name(),
                pos(c.center()),
                c.permissions().owner(),
                c.citizens().all().size());
    }

    /** {@code d} of {@code colony}, where its body stands now (empty while it has none loaded). */
    static CitizenSnapshot citizen(ColonyRef colony, Colony c, CitizenData d) {
        Optional<Vec> at = c.citizens()
                .bodyOf(d.id())
                .flatMap(c.context().bodies()::position)
                .map(ApiSnapshots::vec);
        return new CitizenSnapshot(
                new CitizenRef(colony, d.id()),
                d.name(),
                d.job().map(job -> job.type().id()),
                optionalPos(d.homeBuilding()),
                optionalPos(d.workBuilding()),
                at);
    }

    /** How {@code d} fares: its saturation, happiness (MC getHappiness) and each modifier's last factor. */
    static CitizenWellbeing wellbeing(CitizenRef ref, Colony c, CitizenData d) {
        return new CitizenWellbeing(
                ref,
                d.saturation(),
                CitizenData.MAX_SATURATION,
                d.happiness().happiness(c, d),
                d.happiness().modifiers().stream()
                        .map(m -> new CitizenWellbeing.HappinessFactor(m.id(), m.lastFactor()))
                        .toList());
    }

    /** {@code b} of {@code colony}. */
    static BuildingSnapshot building(ColonyRef colony, Building b) {
        return new BuildingSnapshot(colony, b.type().id(), pos(b.position()), b.level(), b.isBuilt(), b.style());
    }

    /**
     * {@code r} of {@code colony}: what it asks, and who asks (the hut found by RequesterLocation, and the citizen
     * when one does).
     */
    static RequestSnapshot request(ColonyRef colony, Colony c, Request r) {
        Requestable what = r.requestable();
        return new RequestSnapshot(
                id(r.token()),
                colony,
                r.state().name(),
                kind(what),
                item(what),
                count(what),
                RequesterLocation.of(c, r.requester()).map(ApiSnapshots::pos),
                r.citizenId() == Request.NO_CITIZEN
                        ? Optional.empty()
                        : Optional.of(new CitizenRef(colony, r.citizenId())),
                c.requests().resolverOf(r.token()).map(Resolver::resolverId),
                r.parent().map(ApiSnapshots::id),
                r.children().stream().map(ApiSnapshots::id).toList());
    }

    /** The requestable's kind, named as the api documents it. */
    private static String kind(Requestable what) {
        return switch (what) {
            case StackRequest _ -> "stack";
            case ToolRequest _ -> "tool";
            case Delivery _ -> "delivery";
            case Pickup _ -> "pickup";
            case StackList _ -> "stack_list";
            case Crafting _ -> "crafting";
        };
    }

    /** The item asked for; the first accepted one stands for a list, as in the requests window. */
    private static Optional<String> item(Requestable what) {
        return switch (what) {
            case StackRequest s -> Optional.of(s.item().id());
            case Delivery d -> Optional.of(d.stack().item().id());
            case StackList l -> Optional.of(l.accepted().getFirst().id());
            case Crafting c -> Optional.of(c.stack().id());
            case ToolRequest _ -> Optional.empty();
            case Pickup _ -> Optional.empty();
        };
    }

    /** How many are asked for; the minimum for a crafting (MC AbstractCraftingRequest's short display). */
    private static int count(Requestable what) {
        return switch (what) {
            case StackRequest s -> s.count();
            case Delivery d -> d.stack().count();
            case StackList l -> l.count();
            case Crafting c -> c.minCount();
            case ToolRequest _ -> 1;
            case Pickup _ -> 0;
        };
    }

    private static String id(RequestToken token) {
        return token.id().toString();
    }

    static Pos pos(BlockPos p) {
        return new Pos(p.x(), p.y(), p.z());
    }

    private static Optional<Pos> optionalPos(@Nullable BlockPos p) {
        return Optional.ofNullable(p).map(ApiSnapshots::pos);
    }

    static Vec vec(Vec3 v) {
        return new Vec(v.x(), v.y(), v.z());
    }
}
