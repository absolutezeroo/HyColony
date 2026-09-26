package dev.hycolony.core.citizen;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.random.RandomGenerator;

/** Name lists + MineColonies' generateName format. */
public final class CitizenNames {
    public enum Order {
        WESTERN,
        EASTERN
    }

    private record Data(
            Order order, List<String> maleFirstNames, List<String> femaleFirstNames, List<String> surnames) {}

    private final Data data;

    private CitizenNames(Data data) {
        this.data = data;
    }

    public static CitizenNames loadDefault() {
        try (InputStream in = CitizenNames.class.getResourceAsStream("names/default.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing names/default.json");
            }
            return new CitizenNames(new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Data.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String generate(RandomGenerator random, Gender gender) {
        String first = pick(random, gender == Gender.FEMALE ? data.femaleFirstNames() : data.maleFirstNames());
        String last = pick(random, data.surnames());
        if (data.order() == Order.EASTERN) {
            return last + " " + first;
        }
        char middle = (char) ('A' + random.nextInt(26));
        return first + " " + middle + ". " + last;
    }

    private static String pick(RandomGenerator random, List<String> list) {
        return list.get(random.nextInt(list.size()));
    }
}
