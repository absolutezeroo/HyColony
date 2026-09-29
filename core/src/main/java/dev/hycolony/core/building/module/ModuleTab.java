package dev.hycolony.core.building.module;

/**
 * A hut window's module tab: one kind per MC module view ({@code IBuildingModuleView}, from the view suppliers of
 * {@code BuildingModules}), declared by the feature whose module provides it ({@link ProvidesTab}). The plugin picks
 * each renderer by its record type; a tab without one yet is not shown.
 */
public interface ModuleTab {}
