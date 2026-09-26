// Test bench for guard.js: node .claude/hooks/test/run.js [path/to/guard.js]
// commands.txt: one case per line, "<allow|deny> [unlocked|research] <Bash|PowerShell> <command>", "<NL>" = newline.
// File-tool cases are below. A crash (reported apart, it fails closed) never counts as a deny. Every case runs locked (HYCOLONY_GUARDRAILS_UNLOCKED=0) unless marked unlocked.
"use strict";
const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "../../..");
const GUARD = path.resolve(process.argv[2] || path.join(__dirname, "../guard.js"));
const W = (rel) => path.join(ROOT, rel);
// The same root as Git Bash writes it (/c/Users/...).
const GIT_BASH_ROOT = ROOT.replace(/\\/g, "/").replace(/^([A-Za-z]):/, (_, d) => "/" + d.toLowerCase());

function run(tool, input, mode, extra = {}) {
    const env = { ...process.env, CLAUDE_PROJECT_DIR: ROOT, HYCOLONY_GUARDRAILS_UNLOCKED: mode === "unlocked" ? "1" : "0" };
    Object.assign(env, extra.env || {});
    const args = [GUARD, ...(mode === "research" ? ["--research-only"] : [])];
    const stdin = extra.raw !== undefined ? extra.raw : JSON.stringify({ tool_name: tool, tool_input: input, cwd: extra.cwd || ROOT });
    const out = execFileSync("node", args, { input: stdin, env, stdio: ["pipe", "pipe", "ignore"] }).toString();
    if (out.includes("guard.js crashed")) return "crash";
    return out.includes('"deny"') ? "deny" : "allow";
}

const kv = fs.readFileSync(W("config/pmd/known-violations.txt"), "utf8");
const firstEntry = kv.split("\n").find((l) => l.trim() && !l.startsWith("#")).trim();
const rule = firstEntry.split(/\s+/)[0];
const gradle = fs.readFileSync(W("build.gradle.kts"), "utf8");
const FILE_CASES = [
    ["deny", "Write", { file_path: W("gradle/file-size-allowlist.txt"), content: "core/src/main/java/Big.java\n" }],
    ["deny", "Write", { file_path: W("config/pmd/known-violations.txt"), content: kv + "GodClass core/X.java\n" }],
    ["deny", "Write", { file_path: W("config/pmd/known-violations.txt"), content: kv.replace(/^#.*\n/m, "") + "GodClass core/X.java\n" }],
    ["allow", "Write", { file_path: W("config/pmd/known-violations.txt"), content: kv + "\n\n# note\n" }],
    ["allow", "Write", { file_path: W("config/pmd/known-violations.txt"), content: kv.replace(firstEntry + "\n", "") }],
    ["deny", "Edit", { file_path: W("config/pmd/known-violations.txt"), old_string: firstEntry, new_string: "GodClass core/X.java" }],
    ["deny", "Edit", { file_path: W("config/pmd/known-violations.txt"), old_string: firstEntry, new_string: firstEntry + " GodClass core/X.java" }],
    ["deny", "Edit", { file_path: W("config/pmd/known-violations.txt"), old_string: rule, new_string: "GodClass", replace_all: true }],
    ["allow", "Edit", { file_path: W("config/pmd/known-violations.txt"), old_string: firstEntry + "\n", new_string: "" }],
    ["deny", "Edit", { file_path: W("Config/PMD/Known-Violations.txt").toUpperCase(), old_string: firstEntry, new_string: firstEntry + "\nGodClass x" }],
    ["deny", "Edit", { file_path: "config/pmd/known-violations.txt", old_string: firstEntry, new_string: firstEntry + "\nGodClass x" }],
    ["deny", "MultiEdit", { file_path: W("gradle/file-size-allowlist.txt"), edits: [{ old_string: "", new_string: "core/Big.java\n" }] }],
    ["deny", "Edit", { file_path: W(".githooks/pre-commit"), old_string: "a", new_string: "b" }],
    ["deny", "Edit", { file_path: W(".GitHooks/pre-commit"), old_string: "a", new_string: "b" }],
    ["deny", "Edit", { file_path: W(".githooks/pre-commit").toLowerCase().replace(/\\/g, "/"), old_string: "a", new_string: "b" }],
    ["deny", "Edit", { file_path: W(".githooks/pre-commit").replace(/^([A-Za-z]):/, (_, d) => "/" + d.toLowerCase()).replace(/\\/g, "/"), old_string: "a", new_string: "b" }],
    ["deny", "MultiEdit", { file_path: W(".githooks/pre-commit"), edits: [] }],
    ["deny", "Edit", { file_path: W(".claude/settings.json"), old_string: "a", new_string: "b" }],
    ["deny", "Edit", { file_path: W(".claude/hooks/guard.js"), old_string: "a", new_string: "b" }],
    ["deny", "Write", { file_path: W(".claude/settings.local.json"), content: '{"disableAllHooks": true}' }],
    ["deny", "Write", { file_path: W(".claude/settings.local.json"), content: "{}" }, "unlocked"],
    ["deny", "Edit", { file_path: W("AGENTS.md"), old_string: "a", new_string: "b" }],
    ["deny", "Edit", { file_path: W("CLAUDE.md"), old_string: "a", new_string: "b" }],
    ["deny", "Edit", { file_path: W(".claude/agents/hycolony-reviewer.md"), old_string: "a", new_string: "b" }],
    ["deny", "Write", { file_path: W(".claude/skills/port-mc/SKILL.md"), content: "x" }],
    ["deny", "Edit", { file_path: W("config/pmd/ruleset.xml"), old_string: "a", new_string: "b" }],
    ["deny", "Edit", { file_path: W("build.gradle.kts"), old_string: "val maxLines = 400", new_string: "val maxLines = 800" }],
    ["deny", "Edit", { file_path: W("build.gradle.kts"), old_string: "isIgnoreFailures = false", new_string: "isIgnoreFailures = true" }],
    ["deny", "Write", { file_path: W("build.gradle.kts"), content: gradle.split("// CLAUDE.md §")[0] }],
    ["allow", "Edit", { file_path: W("build.gradle.kts"), old_string: 'version "8.10.3"', new_string: 'version "8.10.4"' }],
    ["allow", "Edit", { file_path: W("build.gradle.kts"), old_string: "val maxLines = 400", new_string: "val maxLines = 800" }, "unlocked"],
    ["deny", "Write", { file_path: W(".mcp.json"), content: "{}" }],
    ["deny", "Write", { file_path: W("plugin/src/main/resources/config.json"), content: "{}" }],
    ["deny", "Write", { file_path: W("plugin/src/main/resources/config.json.bak"), content: "{}" }],
    ["deny", "Edit", { file_path: W(".git/config"), old_string: "a", new_string: "b" }, "unlocked"],
    ["allow", "Write", { file_path: W("docs/config.json"), content: "{}" }],
    ["allow", "Edit", { file_path: W(".gitattributes"), old_string: "a", new_string: "b" }],
    ["allow", "Edit", { file_path: W("core/src/main/java/Foo.java"), old_string: "a", new_string: "b" }],
    ["allow", "Read", { file_path: W(".githooks/pre-commit") }],
    ["allow", "Edit", { file_path: W(".githooks/pre-commit"), old_string: "a", new_string: "b" }, "unlocked"],
    ["allow", "Write", { file_path: W(".claude/hooks/guard.js"), content: "x" }, "unlocked"],
    ["deny", "NotebookEdit", { notebook_path: W(".claude/hooks/x.ipynb"), new_source: "x" }],
    ["deny", "Edit", { file_path: W(".githooks/pre-commit"), old_string: "a", new_string: "b" }, "locked", { cwd: W("core"), env: { CLAUDE_PROJECT_DIR: "" } }],
    ["allow", "Write", { file_path: W("docs/research/x.md"), content: "x" }, "research"],
    ["allow", "Write", { file_path: "docs/research/x.md", content: "x" }, "research", { cwd: GIT_BASH_ROOT, env: { CLAUDE_PROJECT_DIR: GIT_BASH_ROOT } }],
    ["deny", "Write", { file_path: "docs/BACKLOG.md", content: "x" }, "research", { cwd: GIT_BASH_ROOT, env: { CLAUDE_PROJECT_DIR: GIT_BASH_ROOT } }],
    ["allow", "Edit", { file_path: "config/pmd/known-violations.txt", old_string: firstEntry + "\n", new_string: "" }, "locked", { cwd: GIT_BASH_ROOT }],
    ["deny", "Edit", { file_path: "config/pmd/known-violations.txt", old_string: firstEntry, new_string: firstEntry + "\nGodClass x" }, "locked", { cwd: GIT_BASH_ROOT }],
    ["allow", "Edit", { file_path: GIT_BASH_ROOT + "/build.gradle.kts", old_string: 'version "8.10.3"', new_string: 'version "8.10.4"' }],
    ["deny", "Write", { file_path: W("docs/BACKLOG.md"), content: "x" }, "research"],
    ["deny", "Edit", { file_path: W("core/src/main/java/Foo.java"), old_string: "a", new_string: "b" }, "research"],
    ["deny", "Write", { file_path: "C:/Temp/x.md", content: "x" }, "research"],
    ["crash", "Edit", {}, "locked", { raw: "{not json" }],
    ["crash", "Write", {}, "locked", { raw: "" }],
    ["allow", "Write", {}, "unlocked", { raw: "{not json" }],
];

let pass = 0;
const failures = [];
function check(expect, tool, input, mode, extra, label) {
    const got = run(tool, input, mode, extra);
    if (got === expect) pass++;
    else failures.push(`expected ${expect}, got ${got}: [${mode || "locked"}] ${tool} ${label}`);
}

const lines = fs.readFileSync(path.join(__dirname, "commands.txt"), "utf8").split(/\r?\n/);
let commands = 0;
for (const line of lines) {
    if (!line.trim() || line.startsWith("//")) continue;
    const m = /^(allow|deny) (?:(unlocked|research) )?(Bash|PowerShell) (.*)$/.exec(line);
    if (!m) throw new Error("bad case line: " + line);
    commands++;
    check(m[1], m[3], { command: m[4].split("<NL>").join("\n") }, m[2], {}, m[4]);
}
for (const [expect, tool, input, mode, extra] of FILE_CASES) check(expect, tool, input, mode, extra, JSON.stringify(input).slice(0, 140));

const total = commands + FILE_CASES.length;
failures.forEach((f) => console.log("FAIL " + f));
console.log(`${pass}/${total} passed (${commands} commands, ${FILE_CASES.length} file tools)` + (failures.length ? "" : ": ALL PASS"));
process.exit(failures.length ? 1 : 0);
