package dev.hycolony.core.building;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import java.util.UUID;

/**
 * A module with a tab in its hut's window (MC {@code IBuildingModule.serializeToView} feeding its
 * {@code IBuildingModuleView}). The window lists these tabs in module order.
 */
public interface ProvidesTab extends BuildingModule {
    /** This module's tab of {@code building}'s window as {@code viewer} sees it; reads only, changes nothing. */
    ModuleTab tab(Colony colony, Building building, UUID viewer);
}
