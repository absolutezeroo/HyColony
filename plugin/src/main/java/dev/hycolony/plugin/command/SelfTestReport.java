package dev.hycolony.plugin.command;

/** Where a selftest step writes its lines: one per check, OK or KO with a detail. */
@FunctionalInterface
interface SelfTestReport {
    /** One report line; {@code detail} is shown only for a KO. */
    void line(String step, boolean ok, String detail);
}
