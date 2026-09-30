package dev.hycolony.api.read;

import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;
import java.util.UUID;

/**
 * A colony as it was when read: its name, its centre (where it was founded, which stays if the town hall goes), its
 * owner and how many citizens it has.
 *
 * @since 1.0
 */
public record ColonySummary(ColonyRef ref, String name, Pos center, UUID owner, int citizens) {}
