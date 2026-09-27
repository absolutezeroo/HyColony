package dev.hycolony.plugin.command;

import dev.hycolony.plugin.command.LogisticsSelfTest.SelfTestReport;
import dev.hycolony.plugin.subplugin.SubPlugins;
import java.util.Locale;

/** Selftest step: what startup made of each bundled sub-plugin. */
final class SubPluginsSelfTest {
    private SubPluginsSelfTest() {}

    /** One line per pack, KO only when it failed (a disabled one is the config's choice), then the fragment count. */
    static void run(SelfTestReport report, SubPlugins packs) {
        packs.statuses().forEach(p -> {
            String state = p.state().name().toLowerCase(Locale.ROOT);
            String step = "sub-plugin " + p.name() + " " + p.version() + " (" + state + ")";
            report.line(step, p.state() != SubPlugins.State.FAILED, "see the server log");
        });
        report.line("sub-plugin fragments: " + packs.fragmentsMerged() + " merged", true, "");
    }
}
