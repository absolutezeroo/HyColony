// PreToolUse guard for the CLAUDE.md rules the build cannot enforce:
// exception lists may only shrink (§ 8) and `git add` stays explicit, never config.json (§ 9.5).
const fs = require("fs");

const SHRINK_ONLY = ["gradle/file-size-allowlist.txt", "config/pmd/known-violations.txt"];
const GIT_ADD = /\bgit\s+add\b[^;&|\n]*?(\s(-A|--all|-u|\.)(?=\s|$)|config\.json)/;

const lines = (s) => (s || "").split("\n").filter((l) => l.trim()).length;

function deny(reason) {
    console.log(JSON.stringify({
        hookSpecificOutput: { hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: reason },
    }));
    process.exit(0);
}

const { tool_name: tool, tool_input: input = {} } = JSON.parse(fs.readFileSync(0, "utf8"));

if (tool === "Bash" || tool === "PowerShell") {
    if (GIT_ADD.test(input.command || "")) {
        deny("CLAUDE.md § 9.5: git add with explicit paths only; never -A/./--all, never config.json(.bak).");
    }
} else if (tool === "Edit" || tool === "Write") {
    const path = (input.file_path || "").replace(/\\/g, "/");
    const list = SHRINK_ONLY.find((p) => path.endsWith(p));
    if (list) {
        const before = tool === "Edit" ? lines(input.old_string) : lines(fs.existsSync(path) ? fs.readFileSync(path, "utf8") : "");
        const after = tool === "Edit" ? lines(input.new_string) : lines(input.content);
        if (after > before) deny(`CLAUDE.md § 8: ${list} may only shrink. Split or clean the file instead.`);
    }
}
