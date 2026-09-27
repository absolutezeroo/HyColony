package dev.hycolony.core.colony.ui.tab;

/**
 * The content of a hut window's module tabs, one kind per module view (MC {@code IBuildingModuleView}, produced by
 * the {@code ModuleProducer} view suppliers of {@code BuildingModules}). Sealed so the plugin picks each renderer by an
 * exhaustive switch.
 */
public sealed interface ModuleTab permits BuilderTabs, WarehouseTabs, CourierTabs {}
