// PreToolUse guard for the CLAUDE.md rules the build cannot enforce (§ 8, § 9, § 10):
// exception lists only shrink, staging stays explicit, hooks are never bypassed, the server is never launched,
// local settings and the guardrails themselves are not edited without the user.
const fs = require("fs");

const SHRINK_ONLY = [
    "gradle/file-size-allowlist.txt",
    "gradle/package-size-allowlist.txt",
    "config/pmd/known-violations.txt",
];
const LOCAL_FILES = /(^|\/)(\.mcp\.json|plugin\/src\/main\/resources\/config\.json|config\.json\.bak)$/;
// The user unlocks guardrail maintenance by launching Claude Code with HYCOLONY_GUARDRAILS_UNLOCKED=1.
const GUARDRAIL_FILES = /^(\.githooks\/|\.claude\/hooks\/|\.claude\/settings\.json$)/;
const GUARDRAIL_MENTION = /(^|[\/\s"'])(\.githooks\/|\.claude\/hooks\/|\.claude\/settings\.json)/;
const UNLOCKED = process.env.HYCOLONY_GUARDRAILS_UNLOCKED === "1";

const GIT_ADD = /\bgit\s+add\b[^;&|\n]*?(\s(-A|--all|-u|\.)(?=\s|$)|config\.json)/;
const COMMIT_BYPASS = /\bgit\s+commit\b[^;&|\n]*\s(--no-verify|--all|-[a-zA-Z]*[an][a-zA-Z]*)(?=\s|$)/;
const PUSH_BYPASS = /\bgit\s+push\b[^;&|\n]*\s(--no-verify|--force\S*|-[a-zA-Z]*f[a-zA-Z]*|\+\S+)(?=\s|$)/;
const HOOKS_PATH = /core\.hookspath(?:[ \t]*=[ \t]*|[ \t]+)["']?([^\s;&|"']+)/gi;
const HOOKS_UNSET = /--unset(-all)?\s+core\.hookspath/i;
const SERVER = /\bgradlew?(\.bat)?\b[^;&|\n]*\brunServer\b|\bjava(w|\.exe)?\b[^;&|\n]*\s-jar\s+\S*server\S*\.jar/i;
const WRITES = /(^|[^0-9&>])>|\btee\b|\bsed\s+-i|\b(rm|mv|cp|chmod|truncate)\b|(Set|Add|Clear)-Content|Out-File|(Remove|Move|Copy|New|Rename)-Item/i;

const ASK_USER = "Ask the user: guardrail changes need their explicit approval (CLAUDE.md § 10).";
const lines = (s) => (s || "").split("\n").filter((l) => l.trim()).length;

function deny(reason) {
    console.log(JSON.stringify({
        hookSpecificOutput: { hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: reason },
    }));
    process.exit(0);
}

function checkCommand(cmd) {
    if (GIT_ADD.test(cmd)) deny("CLAUDE.md § 9.5: git add with explicit paths only; never -A/./--all/-u, never config.json(.bak).");
    if (COMMIT_BYPASS.test(cmd)) deny("CLAUDE.md § 10: never git commit --no-verify/-n (fix what the hook reports) nor -a/--all (stage explicit paths).");
    if (PUSH_BYPASS.test(cmd)) deny("CLAUDE.md § 10: never git push --no-verify nor --force/-f/+refspec. Ask the user.");
    if (SERVER.test(cmd)) deny("CLAUDE.md § 9.4: never launch the Hytale server; the user restarts it and tests in game.");
    const badPath = [...cmd.matchAll(HOOKS_PATH)].some((m) => m[1] !== ".githooks");
    if (badPath || HOOKS_UNSET.test(cmd)) deny("CLAUDE.md § 10: core.hooksPath stays .githooks. " + ASK_USER);
    if (!UNLOCKED && GUARDRAIL_MENTION.test(cmd) && WRITES.test(cmd)) deny("This command may modify a guardrail file. " + ASK_USER);
}

function checkFile(tool, input, cwd) {
    const path = (input.file_path || "").replace(/\\/g, "/");
    const root = (process.env.CLAUDE_PROJECT_DIR || cwd || "").replace(/\\/g, "/").replace(/\/$/, "");
    const rel = root && path.toLowerCase().startsWith(root.toLowerCase() + "/") ? path.slice(root.length + 1) : path;
    if (LOCAL_FILES.test(path)) deny("CLAUDE.md § 10: local settings (.mcp.json, config.json, config.json.bak) are the user's. Ask them.");
    if (!UNLOCKED && GUARDRAIL_FILES.test(rel)) deny(`${path} is a guardrail. ` + ASK_USER);
    const list = SHRINK_ONLY.find((p) => path.endsWith(p));
    if (list) {
        const before = tool === "Edit" ? lines(input.old_string) : lines(fs.existsSync(path) ? fs.readFileSync(path, "utf8") : "");
        const after = tool === "Edit" ? lines(input.new_string) : lines(input.content);
        if (after > before) deny(`CLAUDE.md § 8: ${list} may only shrink. Split or clean the file instead.`);
    }
}

const { tool_name: tool, tool_input: input = {}, cwd } = JSON.parse(fs.readFileSync(0, "utf8"));
if (tool === "Bash" || tool === "PowerShell") checkCommand(input.command || "");
else if (tool === "Edit" || tool === "Write") checkFile(tool, input, cwd);
