package dev.hylens.plugin.command;

import dev.hylens.core.check.NewAlerts;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.HyLensIds;

/**
 * What HyLens keeps while it runs, shared by its commands and menu (its systems take the parts they read): each
 * operator's watch and menu choices, the colony clock's pauses, what the automatic check told, and the asset ids.
 */
public record LensParts(Watches watches, Menus menus, MenuClock clock, NewAlerts alerts, HyLensIds ids) {}
