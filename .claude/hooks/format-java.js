// Formats only the Java files this session wrote (CLAUDE.md § 3: file by file, since other sessions work in the same
// folder and a module-wide spotlessApply would reformat their files mid-edit).
// `record` (PostToolUse on Edit|Write|MultiEdit): notes the written .java file in a per-session list.
// `format` (Stop, at the end of each turn): runs spotlessApply with -PspotlessIdeHook on each noted file, then drops
// the list. Formatting at Stop rather than after each edit keeps the file unchanged under the next Edit of the turn.
// Known limits, all caught by the pre-commit hook's spotlessCheck: files changed by a script (python, sed) are not
// noted; a background subagent still editing a file may see it reformatted under it; a turn touching so many files
// that the Stop timeout kills the hook leaves the rest unformatted.
// Tests: node .claude/hooks/test/format-java.test.js
"use strict";
const fs = require("fs");
const path = require("path");
const os = require("os");
const { spawnSync } = require("child_process");

/** The Gradle path of the project holding the file (domum/core -> :domum-core), or null outside a subproject. */
function gradleProject(root, file) {
    for (let dir = path.dirname(file); ; dir = path.dirname(dir)) {
        const rel = path.relative(root, dir);
        if (!rel || rel.startsWith("..") || path.isAbsolute(rel)) return null;
        if (fs.existsSync(path.join(dir, "build.gradle.kts"))) return ":" + rel.split(path.sep).join("-");
    }
}

/** Notes the written file; its real path, since Spotless matches -PspotlessIdeHook case-sensitively (c: vs C:). */
function record(root, list, input) {
    const written = path.resolve(root, String((input.tool_input || {}).file_path || ""));
    if (!written.endsWith(".java") || !fs.existsSync(written)) return;
    const file = fs.realpathSync.native(written);
    if (gradleProject(root, file)) fs.appendFileSync(list, file + "\n");
}

/** Formats each noted file still present; reports the failures as a system message. */
function format(root, list) {
    // Renaming first is atomic: a record written meanwhile starts a new list instead of being lost.
    const taken = `${list}.${process.pid}`;
    try {
        fs.renameSync(list, taken);
    } catch {
        return;
    }
    const files = [...new Set(fs.readFileSync(taken, "utf8").split("\n").filter(Boolean))];
    fs.unlinkSync(taken);
    const failed = [];
    for (const file of files.filter((f) => fs.existsSync(f))) {
        const task = `${gradleProject(root, file)}:spotlessApply`;
        const args = ["./gradlew", "-q", "--offline", task, `-PspotlessIdeHook=${file}`];
        if (spawnSync("sh", args, { cwd: root, stdio: "ignore" }).status !== 0) failed.push(path.relative(root, file));
    }
    if (failed.length) {
        const msg = `spotlessApply a échoué sur ${failed.join(", ")} : `
            + "lance ./gradlew :<projet>:spotlessApply -PspotlessIdeHook=<fichier>";
        process.stdout.write(JSON.stringify({ systemMessage: msg }));
    }
}

function main() {
    const input = JSON.parse(fs.readFileSync(0, "utf8"));
    const root = process.env.CLAUDE_PROJECT_DIR || input.cwd;
    const session = String(input.session_id || "none").replace(/\W/g, "");
    const list = path.join(os.tmpdir(), `hycolony-java-${session}.txt`);
    if (process.argv[2] === "record") record(root, list, input);
    else if (process.argv[2] === "format") format(root, list);
}

module.exports = { gradleProject, record };
if (require.main === module) {
    try {
        main();
    } catch (e) {
        // A formatting helper never blocks the session; the pre-commit hook still checks the format.
        process.stdout.write(JSON.stringify({ systemMessage: `format-java.js: ${e.message}` }));
    }
}
