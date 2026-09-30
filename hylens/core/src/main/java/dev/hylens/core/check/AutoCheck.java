package dev.hylens.core.check;

import dev.hylens.core.menu.MenuState;
import dev.hylens.core.menu.Menus;
import java.util.UUID;

/** Turning an operator's automatic check on or off (spec 2026-09-30, § 6.5). */
public final class AutoCheck {
    private AutoCheck() {}

    /**
     * Turns {@code operator}'s automatic check on or off, and forgets what it told them either way: once on, every
     * lasting violation is told afresh, even after a round that raced a switch-off. True once on.
     */
    public static boolean toggle(Menus menus, NewAlerts alerts, UUID operator) {
        alerts.forget(operator);
        return menus.update(operator, MenuState::toggleAutoCheck).autoCheck();
    }
}
