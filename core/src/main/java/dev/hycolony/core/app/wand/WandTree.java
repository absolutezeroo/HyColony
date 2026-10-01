package dev.hycolony.core.app.wand;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The folders of a pack as the build tool walks them (ST StructurePacks.getCategories): built from each hut's folder
 * ({@code fundamentals}, {@code agriculture/horticulture}), so only the folders that lead to a hut of the style exist.
 * Folders are listed in name order, huts in the order given.
 */
final class WandTree {
    private static final char SEP = '/';

    private final Map<String, String> folderOf;
    private final List<String> order;

    /** {@code folderOf}: each offered hut type and its folder, in the order the huts are listed. */
    WandTree(Map<String, String> folderOf) {
        this.folderOf = Map.copyOf(folderOf);
        this.order = List.copyOf(folderOf.keySet());
    }

    /** The top folders (ST's category icons). */
    List<String> roots() {
        TreeSet<String> out = new TreeSet<>();
        folderOf.values().forEach(f -> out.add(top(f)));
        return List.copyOf(out);
    }

    /** The direct subfolders of {@code folder}, as full paths. */
    List<String> children(String folder) {
        String prefix = folder + SEP;
        TreeSet<String> out = new TreeSet<>();
        for (String f : folderOf.values()) {
            if (f.startsWith(prefix)) {
                int next = f.indexOf(SEP, prefix.length());
                out.add(next < 0 ? f : f.substring(0, next));
            }
        }
        return List.copyOf(out);
    }

    /** The hut types filed directly in {@code folder}. */
    List<String> huts(String folder) {
        return order.stream().filter(t -> folder.equals(folderOf.get(t))).toList();
    }

    /** Whether {@code buildingTypeId} is one of the style's huts. */
    boolean hasHut(String buildingTypeId) {
        return folderOf.containsKey(buildingTypeId);
    }

    /** Whether {@code folder} leads to a hut of the style; false for the root. */
    boolean contains(String folder) {
        return !folder.isEmpty()
                && folderOf.values().stream().anyMatch(f -> f.equals(folder) || f.startsWith(folder + SEP));
    }

    /** The folder above {@code folder}; the root ("") for a top folder or the root. */
    static String parent(String folder) {
        int last = folder.lastIndexOf(SEP);
        return last < 0 ? "" : folder.substring(0, last);
    }

    private static String top(String folder) {
        int first = folder.indexOf(SEP);
        return first < 0 ? folder : folder.substring(0, first);
    }
}
