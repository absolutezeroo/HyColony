package dev.hycolony.core.citizen;

import dev.hycolony.core.citizen.happiness.CitizenHappiness;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Map;

/**
 * A new citizen's data, registered in its colony (MC createAndRegisterCivilianData and initForNewCivilian), and its
 * arrival announced.
 */
final class Newcomers {
    private Newcomers() {}

    /** A newcomer moved in at {@code townHall}: the colony's journal, a save, the event (MC CitizenManager). */
    static void arrived(Colony colony, CitizenData data, BlockPos townHall) {
        // MC CitizenManager: a CitizenSpawnedEvent at the town hall for a citizen moving in.
        colony.log().addAt(townHall, "citizenSpawned", colony.day(), data.name());
        colony.markDirty();
        colony.context().bus().post(new CitizenSpawned(colony, data));
    }

    /**
     * Registers in {@code citizens} a new citizen of {@code colony}: the first free id, full saturation, skills capped
     * by the colony's mean happiness (at least 5 below the initial amount), MC's balanced gender and a name.
     */
    static CitizenData register(Colony colony, Map<Integer, CitizenData> citizens) {
        ColonyContext ctx = colony.context();
        int femaleCount = (int) citizens.values().stream()
                .filter(c -> c.gender() == Gender.FEMALE)
                .count();
        int id = 1;
        while (citizens.containsKey(id)) {
            id++;
        }
        CitizenData data = new CitizenData(id);
        data.setSaturation(CitizenData.MAX_SATURATION);
        int levelCap = ((int) CitizenHappiness.overall(colony)) * 2; // MC: the mean happiness before it joins
        if (citizens.size() < ctx.config().gameplay().initialCitizenAmount()) {
            levelCap = Math.max(5, levelCap);
        }
        data.setSkills(Skills.initRandom(levelCap, ctx.random()));
        citizens.put(id, data);
        Gender gender;
        if (citizens.size() == 1) {
            gender = ctx.random().nextBoolean() ? Gender.FEMALE : Gender.MALE;
        } else if (femaleCount < (citizens.size() - 1) / 2.0) {
            gender = Gender.FEMALE;
        } else {
            gender = Gender.MALE;
        }
        data.setGender(gender);
        data.setName(ctx.names().generate(ctx.random(), gender));
        return data;
    }
}
