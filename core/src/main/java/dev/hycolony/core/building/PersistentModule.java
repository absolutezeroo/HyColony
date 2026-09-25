package dev.hycolony.core.building;

import com.google.gson.JsonObject;

public interface PersistentModule extends BuildingModule {
    void write(JsonObject out);

    void read(JsonObject in);
}
