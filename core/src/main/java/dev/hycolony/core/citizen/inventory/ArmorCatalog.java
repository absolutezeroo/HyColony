package dev.hycolony.core.citizen.inventory;

import dev.hycolony.core.kernel.item.ArmorInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Optional;

/** Port: which items are armour pieces, read from the game's item assets (spec 2026-10-02 citizen inventory, § 3). */
public interface ArmorCatalog {
    /** The armour piece {@code item} is: its slot and level; empty for anything else, or an item the game lacks. */
    Optional<ArmorInfo> armor(ItemKey item);
}
