package dev.hycolony.api.read;

import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.Pos;
import dev.hycolony.api.Vec;
import java.util.Optional;

/**
 * A citizen as it was when read. {@code job} is the job type's id ({@code hycolony:builder}...); {@code home} and
 * {@code work} are the huts' positions; {@code position} is where its body stands, empty while it has none loaded.
 *
 * @since 1.0
 */
public record CitizenSnapshot(
        CitizenRef ref,
        String name,
        Optional<String> job,
        Optional<Pos> home,
        Optional<Pos> work,
        Optional<Vec> position) {}
