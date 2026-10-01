package dev.hycolony.core.app.citizen;

import dev.hycolony.core.citizen.CitizenData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The citizen window's food bar (MC CitizenWindowUtils.createSaturationBar): one icon per 6 saturation. */
public final class SaturationBar {
    /** MC: one icon stands for 6 saturation points. */
    private static final double PER_ICON = 6.0;

    /** MC citizen/empty.png, full.png and half.png. */
    public enum Icon {
        EMPTY,
        FULL,
        HALF
    }

    private SaturationBar() {}

    /** The icons from the left: full for each whole 6, half for a remainder, empty up to MAX_SATURATION / 6. */
    public static List<Icon> of(double saturation) {
        List<Icon> icons = new ArrayList<>(
                Collections.nCopies((int) Math.ceil(CitizenData.MAX_SATURATION / PER_ICON), Icon.EMPTY));
        int full = (int) (saturation / PER_ICON);
        for (int i = 0; i < full && i < icons.size(); i++) {
            icons.set(i, Icon.FULL);
        }
        if (saturation / PER_ICON % 1 > 0 && full < icons.size()) {
            icons.set(full, Icon.HALF);
        }
        return List.copyOf(icons);
    }
}
