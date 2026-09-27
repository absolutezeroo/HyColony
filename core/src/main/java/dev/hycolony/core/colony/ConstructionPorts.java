package dev.hycolony.core.colony;

import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.PlayerInventory;
import dev.hycolony.core.kernel.port.WorldBlocks;
import dev.hycolony.core.kernel.port.WorldEffects;

/** Ports the construction system needs. */
public record ConstructionPorts(
        ItemCatalog catalog,
        WorldBlocks blocks,
        ContainerAccess containers,
        PlayerInventory playerInventory,
        BlueprintSource blueprints,
        WorldEffects effects) {}
