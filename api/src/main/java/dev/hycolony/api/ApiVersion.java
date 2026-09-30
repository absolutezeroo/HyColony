package dev.hycolony.api;

/**
 * The api's own semantic version, apart from the mod's. A major version breaks addons: a type or method removed or
 * renamed, a component added to a record, a case added to a sealed type. A minor one only adds.
 * {@link Experimental} parts may change in a minor version.
 *
 * @since 1.0
 */
public record ApiVersion(int major, int minor, int patch) {
    /** This api's version. */
    public static final ApiVersion CURRENT = new ApiVersion(1, 0, 0);

    /** {@code major.minor.patch}. */
    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
