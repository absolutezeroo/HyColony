package dev.hycolony.core.colony.ui.tab;

/**
 * A hut window's module tab: one kind per MC module view ({@code IBuildingModuleView}, from the view suppliers of
 * {@code BuildingModules}). Sealed so the plugin picks each renderer by an exhaustive switch.
 */
public sealed interface ModuleTab
        permits BuilderResourcesView,
                BuilderSettingsView,
                WorkOrderListView,
                CourierAssignmentView,
                WarehouseTasksView,
                CourierTasksView,
                RecipesView {}
