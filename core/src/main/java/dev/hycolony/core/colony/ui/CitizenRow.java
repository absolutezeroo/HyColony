package dev.hycolony.core.colony.ui;

import dev.hycolony.core.citizen.Gender;

/** {@code status} is an i18n key suffix: "idle", "wandering" or "absent". */
public record CitizenRow(String name, Gender gender, String status) {}
