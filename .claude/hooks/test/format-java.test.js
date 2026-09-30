// Checks for format-java.js: node .claude/hooks/test/format-java.test.js
"use strict";
const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { gradleProject, record } = require("../format-java.js");

const ROOT = path.resolve(__dirname, "../../..");
const W = (rel) => path.join(ROOT, rel);
const JAVA = "core/src/main/java/dev/hycolony/core/kernel/persist/MigrationChain.java";

assert.strictEqual(gradleProject(ROOT, W(JAVA)), ":core");
assert.strictEqual(gradleProject(ROOT, W("domum/core/src/main/java/X.java")), ":domum-core");
assert.strictEqual(gradleProject(ROOT, W("vanilla/plugin/src/main/java/X.java")), ":vanilla-plugin");
assert.strictEqual(gradleProject(ROOT, W("X.java")), null);
assert.strictEqual(gradleProject(ROOT, path.resolve(ROOT, "..", "Other", "core", "X.java")), null);

// Spotless matches -PspotlessIdeHook case-sensitively: a path written with another case is noted as on disk.
const list = path.join(os.tmpdir(), `hycolony-java-test-${process.pid}.txt`);
try {
    record(ROOT, list, { tool_input: { file_path: W(JAVA).toLowerCase() } });
    record(ROOT, list, { tool_input: { file_path: W("README.md") } });
    record(ROOT, list, { tool_input: { file_path: W("core/src/main/java/Missing.java") } });
    const noted = fs.existsSync(list) ? fs.readFileSync(list, "utf8") : "";
    // Only Windows finds the lowercased path; elsewhere it does not exist and nothing is noted.
    assert.strictEqual(noted, process.platform === "win32" ? W(JAVA) + "\n" : "");
} finally {
    fs.rmSync(list, { force: true });
}
console.log("format-java.js: ALL PASS");
