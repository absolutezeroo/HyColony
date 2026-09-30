package dev.hycolony.api.read;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Experimental;
import dev.hycolony.api.Pos;
import java.util.List;
import java.util.Optional;

/**
 * A request as it was when read: data, not text, so an addon words it in its own way. Experimental until its first
 * client, the request graph, settles what it needs (a tool request's type and levels are not here yet).
 *
 * <ul>
 *   <li>{@code id}: the request's id; {@code parent} and {@code children} name other requests by it.
 *   <li>{@code state}: its state's name, as MineColonies' RequestState ({@code CREATED}, {@code ASSIGNED},
 *       {@code IN_PROGRESS}...).
 *   <li>{@code kind}: {@code stack}, {@code tool}, {@code delivery}, {@code pickup}, {@code stack_list} or
 *       {@code crafting}. The list may grow: an addon accepts a kind it does not know.
 *   <li>{@code item}: the item's id, empty for a tool or a pickup. {@code count}: how many, 0 for a pickup.
 *   <li>{@code building}: the hut that asks; for a child request, the hut whose resolver asks. Empty when no hut
 *       asks (a colony-wide resolver such as the player's) or the hut is gone. {@code citizen}: the citizen of that
 *       hut who asks, if one does.
 *   <li>{@code resolver}: the opaque id of what handles it, to compare, not to parse; empty while nothing does.
 * </ul>
 *
 * @since 1.0
 */
@Experimental
public record RequestSnapshot(
        String id,
        ColonyRef colony,
        String state,
        String kind,
        Optional<String> item,
        int count,
        Optional<Pos> building,
        Optional<CitizenRef> citizen,
        Optional<String> resolver,
        Optional<String> parent,
        List<String> children) {
    /** Keeps its own copy of the children. */
    public RequestSnapshot {
        children = List.copyOf(children);
    }
}
