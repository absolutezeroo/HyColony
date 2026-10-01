package dev.hycolony.core.crafting.furnace;

/** The cooking ports a colony is given: what cooks and burns ({@code catalog}) and the stations in the world. */
public record CookingSetup(CookingCatalog catalog, CookingStations stations) {}
