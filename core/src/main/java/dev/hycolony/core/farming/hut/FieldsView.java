package dev.hycolony.core.farming.hut;

import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.farming.field.FieldStage;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;

/**
 * A farmer hut's Fields tab (MC FarmFieldsModuleWindow): the assignment mode, {@code owned} of {@code max} fields in
 * use, the Request Fertilizer setting, and one row per field free or owned by the hut, owned first, then by distance.
 */
public record FieldsView(boolean manual, int owned, int max, boolean fertilize, List<Row> rows, boolean canManage)
        implements ModuleTab {
    public FieldsView {
        rows = List.copyOf(rows);
    }

    /**
     * One field: its seed, its distance to the hut and short direction key ({@code hycolony.ui.direction.*}), its
     * stage, whether the hut owns it, the lang key of why it cannot be assigned, if so, and whether it got its pass
     * today (the farmer comes back tomorrow). Deviation from MC: that last mark is an addition, MC shows nothing.
     */
    public record Row(
            BlockPos field,
            Optional<ItemKey> seed,
            int distance,
            String direction,
            FieldStage stage,
            boolean owned,
            Optional<String> refusal,
            boolean doneToday) {}
}
