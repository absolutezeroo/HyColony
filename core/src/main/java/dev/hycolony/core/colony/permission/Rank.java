package dev.hycolony.core.colony.permission;

public final class Rank {
    private final int id;
    private final String name;
    private long permissions;
    private final boolean initial;
    private boolean colonyManager;
    private boolean hostile;

    /** Neither colony manager nor hostile until set. */
    public Rank(int id, String name, long permissions, boolean initial) {
        this.id = id;
        this.name = name;
        this.permissions = permissions;
        this.initial = initial;
    }

    public boolean has(Action action) {
        return (permissions & action.mask()) != 0;
    }

    public void add(Action action) {
        permissions |= action.mask();
    }

    public void remove(Action action) {
        permissions &= ~action.mask();
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long permissions() {
        return permissions;
    }

    public boolean isInitial() {
        return initial;
    }

    public boolean isColonyManager() {
        return colonyManager;
    }

    public boolean isHostile() {
        return hostile;
    }

    /** MC's rank type shown in the Permissions tab: manager first, then hostile, else none. */
    public RankType type() {
        return colonyManager ? RankType.COLONY_MANAGER : hostile ? RankType.HOSTILE : RankType.NONE;
    }

    void setColonyManager(boolean colonyManager) {
        this.colonyManager = colonyManager;
    }

    void setHostile(boolean hostile) {
        this.hostile = hostile;
    }
}
