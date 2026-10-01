package dev.hycolony.core.construction.tape;

/**
 * The shape of a construction tape block, by the tapes it joins (MC BlockConstructionTape's NORTH/EAST/SOUTH/WEST).
 * At rotation 0: a straight runs north-south, a corner joins north and east, a T joins north, east and west; each
 * quarter turn takes north to west.
 */
public enum TapeShape {
    STRAIGHT,
    CORNER,
    T_JUNCTION,
    CROSS_JUNCTION
}
