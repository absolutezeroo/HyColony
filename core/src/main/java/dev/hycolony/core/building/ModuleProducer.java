package dev.hycolony.core.building;

import java.util.function.Supplier;

/** {@code key} is stable and used as the save key of the module. */
public record ModuleProducer(String key, Supplier<? extends BuildingModule> factory) {}
