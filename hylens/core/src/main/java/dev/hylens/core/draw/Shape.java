package dev.hylens.core.draw;

import dev.hycolony.api.Vec;

/**
 * One debug shape drawn for an operator: a line from {@code from} to {@code to}, or a sphere or a one-block cube
 * centred on {@code from} ({@code to} is then {@code from}).
 */
public record Shape(Kind kind, Vec from, Vec to, Colour colour) {
    /** What is drawn. */
    public enum Kind {
        LINE,
        SPHERE,
        CUBE
    }

    /** What the shape says, which the plugin turns into a colour. */
    public enum Colour {
        /** Its last started walk. */
        WALK,
        /** A walk HyColony saw end away from this goal, or teleported or given up. */
        FAILED,
        /** Where the last walk ended. */
        STOP,
        /** The citizen's workplace. */
        WORK
    }

    /** A line from {@code from} to {@code to}. */
    public static Shape line(Vec from, Vec to, Colour colour) {
        return new Shape(Kind.LINE, from, to, colour);
    }

    /** A sphere centred on {@code at}. */
    public static Shape sphere(Vec at, Colour colour) {
        return new Shape(Kind.SPHERE, at, at, colour);
    }

    /** A one-block cube centred on {@code at}. */
    public static Shape cube(Vec at, Colour colour) {
        return new Shape(Kind.CUBE, at, at, colour);
    }
}
