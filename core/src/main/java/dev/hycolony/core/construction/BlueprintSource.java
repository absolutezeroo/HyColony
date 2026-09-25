package dev.hycolony.core.construction;

import java.util.List;
import java.util.Optional;

public interface BlueprintSource {
    Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation);

    List<String> styles();
}
