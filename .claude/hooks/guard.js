// PreToolUse guard for the CLAUDE.md rules the build cannot enforce (§ 8, § 9, § 10): exception lists only shrink,
// staging stays explicit, hooks are never bypassed, the server is never launched, local settings and the guardrails
// are not written without the user. It stops an AI drifting by mistake, not a determined adversary.
// Commands are split into words with quotes removed; only the words that name a command, an option or a written
// file are judged, so text that merely quotes a path or an option (commit messages, heredocs) is allowed.
// `--research-only` (hycolony-researcher frontmatter) also confines every write to docs/research/.
// Tests: node .claude/hooks/test/run.js
"use strict";
const fs = require("fs");
const path = require("path");
const os = require("os");

const UNLOCKED = process.env.HYCOLONY_GUARDRAILS_UNLOCKED === "1";
const RESEARCH_ONLY = process.argv.includes("--research-only");
const LOCAL = "local";
const LIST = "list";
const GUARD = "guardrail";
// Paths relative to the project root, lowercase; a trailing "/" protects a whole directory.
// Guardrails: keep in sync with CLAUDE.md § 10, AGENTS.md and .claude/agents/hycolony-implementer.md.
const PROTECTED = [
    ["gradle/file-size-allowlist.txt", LIST],
    ["gradle/package-size-allowlist.txt", LIST],
    ["config/pmd/known-violations.txt", LIST],
    [".mcp.json", LOCAL],
    [".claude/settings.local.json", LOCAL],
    ["plugin/src/main/resources/config.json", LOCAL],
    ["plugin/src/main/resources/config.json.bak", LOCAL],
    ["blockui/src/main/resources/config.json", LOCAL],
    ["blockui/src/main/resources/config.json.bak", LOCAL],
    ["domum/plugin/src/main/resources/config.json", LOCAL],
    ["domum/plugin/src/main/resources/config.json.bak", LOCAL],
    ["vanilla/plugin/src/main/resources/config.json", LOCAL],
    ["vanilla/plugin/src/main/resources/config.json.bak", LOCAL],
    ["hylens/plugin/src/main/resources/config.json", LOCAL],
    ["hylens/plugin/src/main/resources/config.json.bak", LOCAL],
    [".git/config", LOCAL],
    [".githooks/", GUARD],
    [".claude/hooks/", GUARD],
    [".claude/agents/", GUARD],
    [".claude/skills/", GUARD],
    [".claude/settings.json", GUARD],
    ["agents.md", GUARD],
    ["claude.md", GUARD],
    ["build.gradle.kts", GUARD],
    ["config/pmd/ruleset.xml", GUARD],
    ["build-logic/", GUARD],
];
// Inline code (node -e, python -c, [IO.File]::…) cannot be parsed: a protected name next to a write call is enough.
// A guardrail name counts only where code would write it as a path, right after a quote or a path separator (or as
// os.path.join's '.claude', 'hooks', or PowerShell's unquoted Join-Path $root CLAUDE.md), so prose in a string that cites CLAUDE.md, even as Markdown `CLAUDE.md`, passes.
const MENTION_ALWAYS = /size-allowlist\.txt|known-violations\.txt|\.mcp\.json|settings\.local\.json|resources\/config\.json|config\.json\.bak|\.git\/config/;
const MENTION_GUARD = /(^|['"/]|join-path\s+\S+\s+)(\.githooks|\.claude(\/|['"],\s*['"])(hooks|agents|skills|settings\.json)|agents\.md|claude\.md|build\.gradle\.kts|build-logic|pmd\/ruleset\.xml)/;
const WRITE_CALL = /write|append|delete|unlink|\brm|rename|copy|truncate|chmod|symlink|mkdir|remove|move|replace|open\s*\(|set-content|out-file|>/;
const ASK_USER = "Ask the user: guardrail changes need their explicit approval (CLAUDE.md § 10).";

const WRAPPERS = new Set(["command", "builtin", "exec", "nohup", "time", "sudo", "then", "do", "else", "if",
    "elif", "while", "until", "!", "{", "&", "."]);
const DESTROY = new Set(["rm", "rmdir", "unlink", "shred", "del", "erase", "rd", "ri", "remove-item", "mv", "move",
    "mi", "move-item", "ren", "rni", "rename-item", "rename"]);
const WRITE = new Set(["tee", "tee-object", "touch", "truncate", "chmod", "chown", "chgrp", "ln", "mkdir", "md", "ni",
    "new-item", "set-content", "sc", "add-content", "ac", "clear-content", "clc", "out-file", "set-item", "si",
    "clear-item", "cli", "set-itemproperty", "sp", "patch", "export-csv", "export-clixml"]);
const COPY = new Set(["cp", "copy", "cpi", "copy-item", "install", "rsync", "scp"]);
const INTERPRETERS = new Set(["node", "python", "python3", "py", "perl", "ruby", "php", "deno", "bun"]);
const SHELLS = new Set(["bash", "sh", "zsh", "dash"]);
// Wrappers that run the command after their options, with the options that take a value.
const PREFIXES = {
    timeout: ["-s", "-k", "--signal", "--kill-after"],
    nice: ["-n", "--adjustment"],
    stdbuf: ["-i", "-o", "-e", "--input", "--output", "--error"],
    env: ["-u", "--unset", "-C", "--chdir", "-S", "--split-string"],
};

function deny(reason) {
    console.log(JSON.stringify({
        hookSpecificOutput: { hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: reason },
    }));
    process.exit(0);
}

function norm(p) {
    let s = String(p).replace(/\\/g, "/").toLowerCase();
    const drive = /^\/([a-z])(\/|$)/.exec(s);
    if (drive) s = drive[1] + ":/" + s.slice(3);
    return s;
}

function findRoot(cwd) {
    if (process.env.CLAUDE_PROJECT_DIR) return norm(process.env.CLAUDE_PROJECT_DIR).replace(/\/$/, "");
    const start = path.resolve(norm(cwd || "."));
    for (let dir = start; ; dir = path.dirname(dir)) {
        if (fs.existsSync(path.join(dir, ".git"))) return norm(dir);
        if (path.dirname(dir) === dir) return norm(start);
    }
}

const ctx = { root: "", cwd: "" };

/** Absolute lowercase form of a word read as a path from `base`, or null when it cannot be a path. */
function absolute(word, base) {
    const p = norm(word);
    if (!p || /^[~$%]/.test(p)) return null;
    const abs = /^[a-z]:\//.test(p) || p.startsWith("/");
    return path.posix.normalize(abs ? p : base + "/" + p).replace(/(.)\/$/, "$1");
}

/** Path relative to the project root, "" for the root itself, null outside the project. */
function relative(word, base = ctx.cwd) {
    const a = absolute(word, base);
    if (a === null) return null;
    if (a === ctx.root) return "";
    return a.startsWith(ctx.root + "/") ? a.slice(ctx.root.length + 1) : null;
}

/** Glob pattern as a regex, or null for a literal path (or a pattern that is not a valid regex). */
function globRegex(rel) {
    if (!/[*?[]/.test(rel)) return null;
    try {
        return new RegExp("^" + rel.replace(/[.+^${}()|\\]/g, "\\$&").replace(/\*/g, "[^/]*").replace(/\?/g, "[^/]") + "$");
    } catch {
        return null;
    }
}

/**
 * First protected path hit by a project path, as [category, protected path], or null. `whole` (deletions, moves,
 * checkouts) also counts a directory that contains a protected path; a glob counts whatever it can match.
 */
function category(rel, whole) {
    const glob = globRegex(rel);
    for (const [p, cat] of PROTECTED) {
        const self = p.replace(/\/$/, "");
        const parents = self.split("/").map((_, k, parts) => parts.slice(0, k + 1).join("/"));
        const hit = glob
            ? [...parents, p + "x"].some((x) => glob.test(x))
            : rel === self || (p.endsWith("/") && rel.startsWith(p)) || (whole && (rel === "" || p.startsWith(rel + "/")));
        if (hit) return [cat, self];
    }
    return /(^|\/)config\.json\.bak$/.test(rel) ? [LOCAL, rel] : null;
}

/** True when `word` names the user's own Claude settings (~/.claude/settings.json or settings.local.json). */
function isUserSettings(word, base) {
    const home = norm(os.homedir()).replace(/\/$/, "");
    const expanded = norm(word).replace(/^(~|\$home|\$\{home\}|\$env:userprofile|%userprofile%)(?=\/)/, home);
    const a = absolute(expanded, base);
    return a === home + "/.claude/settings.json" || a === home + "/.claude/settings.local.json";
}

/** Denies a command writing `word` (a file or, when `whole`, a directory tree). */
function checkTarget(word, whole, base = ctx.cwd) {
    if (isUserSettings(word, base)) deny(`${word}: the user's local settings are never written by an agent. Ask them.`);
    const rel = relative(word, base);
    if (rel === null) return;
    if (RESEARCH_ONLY && !rel.startsWith("docs/research/")) deny(`hycolony-researcher writes only under docs/research/ (${word}).`);
    const [cat, hit] = category(rel, whole) || [];
    const what = hit === rel ? word : `${word} (covers ${hit})`;
    if (cat === LOCAL) deny(`${what}: the user's local settings and git config are never written by an agent. Ask them.`);
    if (cat === LIST) deny(`${what}: shrink-only lists (CLAUDE.md § 8) are changed with Edit/Write only, so the guard can check they shrink.`);
    if (cat === GUARD && !UNLOCKED) deny(`${what} is a guardrail. ` + ASK_USER);
}

function checkInlineCode(code) {
    const c = norm(code);
    if (WRITE_CALL.test(c) && (MENTION_ALWAYS.test(c) || (!UNLOCKED && MENTION_GUARD.test(c)))) {
        deny("Inline code writing a protected file (guardrail, shrink-only list or local settings). " + ASK_USER);
    }
}

/** Bash heredoc bodies, removed from the text and keyed by the offset of their `<<`. */
function stripHeredocs(src) {
    const lines = src.split("\n");
    const out = [];
    const bodies = new Map();
    let pos = 0;
    for (let k = 0; k < lines.length; k++) {
        const line = lines[k];
        const start = pos;
        out.push(line);
        pos += line.length + 1;
        for (const m of line.matchAll(/(?<!<)<<(?!<)(-?)[ \t]*(['"]?)([A-Za-z_][\w.-]*)\2/g)) {
            const body = [];
            while (++k < lines.length) {
                const l = (m[1] ? lines[k].replace(/^\t+/, "") : lines[k]).replace(/\r$/, "");
                if (l === m[3]) break;
                body.push(lines[k]);
            }
            bodies.set(start + m.index, body.join("\n"));
        }
    }
    return { text: out.join("\n"), bodies };
}

/** Splits a shell command into segments of quote-stripped words, redirect targets and stdin bodies. */
function parse(source, ps) {
    const { text: src, bodies } = ps ? { text: source, bodies: new Map() } : stripHeredocs(source);
    const segs = [];
    let seg = { words: [], targets: [], bodies: [] };
    let word = null;
    let expect = null;
    const add = (s) => { word = (word === null ? "" : word) + s; };
    const endWord = () => {
        if (word === null) return;
        if (expect === "target") seg.targets.push(word);
        else if (expect === "body") seg.bodies.push(word);
        else if (expect === null) seg.words.push(word);
        word = null;
        expect = null;
    };
    const endSeg = () => {
        endWord();
        expect = null;
        if (seg.words.length || seg.targets.length || seg.bodies.length) segs.push(seg);
        seg = { words: [], targets: [], bodies: [] };
    };
    let i = 0;
    while (i < src.length) {
        const c = src[i];
        const n = src[i + 1];
        if (c === "\n" || c === ";" || c === "|" || c === ")" || (c === "&" && n !== ">") || (c === "(" && word === null)) {
            endSeg();
            i++;
        } else if (c === " " || c === "\t" || c === "\r") {
            endWord();
            i++;
        } else if (c === "#" && word === null) {
            while (i < src.length && src[i] !== "\n") i++;
        } else if (!ps && c === "\\") {
            if (n === "\n") i += 2;
            else if (n !== undefined && " \t;&|<>()'\"$`\\".includes(n)) { add(n); i += 2; }
            else { add(c); i++; }
        } else if (ps && c === "`") {
            if (n !== "\n") add(n || "");
            i += 2;
        } else if (ps && c === "@" && word === null && (n === "'" || n === '"') && /\r?\n/.test(src.slice(i + 2, i + 4))) {
            const end = src.indexOf("\n" + n + "@", i + 2);
            const stop = end < 0 ? src.length : end;
            add(src.slice(src.indexOf("\n", i) + 1, stop));
            i = stop + 3;
        } else if (c === "'") {
            let j = i + 1;
            let s = "";
            for (; j < src.length; j++) {
                if (src[j] === "'" && ps && src[j + 1] === "'") { s += "'"; j++; } else if (src[j] === "'") break;
                else s += src[j];
            }
            add(s);
            i = j + 1;
        } else if (c === '"') {
            let j = i + 1;
            let s = "";
            for (; j < src.length && src[j] !== '"'; j++) {
                const esc = ps ? src[j] === "`" : src[j] === "\\" && "\"\\$`\n".includes(src[j + 1]);
                if (esc) j++;
                if (ps && src[j] === '"' && src[j + 1] === '"') j++;
                s += src[j];
            }
            add(s);
            i = j + 1;
        } else if (c === ">" || (c === "&" && n === ">")) {
            if (word !== null && /^(\d+|\*)$/.test(word)) word = null;
            endWord();
            let j = i + (c === "&" ? 2 : 1);
            if (src[j] === ">" || src[j] === "|") j++;
            if (src[j] === "&") {
                j++;
                while (j < src.length && /[\d-]/.test(src[j])) j++;
            } else expect = "target";
            i = j;
        } else if (c === "<" && !ps) {
            endWord();
            if (n === "<" && src[i + 2] === "<") { expect = "body"; i += 3; }
            else if (n === "<") {
                if (bodies.has(i)) seg.bodies.push(bodies.get(i));
                expect = "delimiter";
                i += 2;
            } else { expect = "input"; i++; }
        } else {
            add(c);
            i++;
        }
    }
    endSeg();
    return segs;
}

function checkCommand(command, ps) {
    for (const m of command.matchAll(/\]::\w+\s*\(([^)]*)\)/g)) checkInlineCode(m[0]);
    for (const seg of parse(command, ps)) checkSegment(seg, ps);
}

function checkSegment(seg, ps) {
    for (const t of seg.targets) checkTarget(t, false);
    const all = seg.words.map((w) => w.toLowerCase());
    if (all.some((w) => /^(\$env:)?git_config/.test(w)) && all.some((w) => w.includes("core.hookspath"))) {
        deny("CLAUDE.md § 10: core.hooksPath stays .githooks (GIT_CONFIG_* environment). " + ASK_USER);
    }
    const words = stripPrefixes(seg.words);
    if (!words.length) return;
    const name = norm(words[0]).split("/").pop().replace(/\.(exe|bat|cmd|ps1)$/, "");
    const args = words.slice(1);
    const plain = args.filter((a) => !a.startsWith("-"));
    if (name === "git") checkGit(args);
    else if (name === "gradle" || name === "gradlew") checkGradle(args);
    else if (name === "java" || name === "javaw") checkJava(args);
    else if (["cd", "pushd", "chdir", "set-location", "sl"].includes(name)) moveTo(plain[0]);
    else if (DESTROY.has(name)) plain.forEach((a) => checkTarget(a, true));
    else if (WRITE.has(name)) plain.forEach((a) => checkTarget(a, false));
    else if (COPY.has(name)) copyTargets(args).forEach((a) => checkTarget(a, false));
    else if (name === "sed" && args.some((a) => /^(-[a-z]*i|--in-place)/i.test(a))) plain.forEach((a) => checkTarget(a, false));
    else if (name === "dd") args.filter((a) => a.startsWith("of=")).forEach((a) => checkTarget(a.slice(3), false));
    else if (name === "curl" || name === "wget") optionValues(args, ["-o", "--output", "-O"]).forEach((a) => checkTarget(a, false));
    else if (INTERPRETERS.has(name)) checkInterpreter(name, args, seg.bodies);
    else if (SHELLS.has(name)) {
        const c = args.findIndex((a) => /^-[a-z]*c$/.test(a));
        if (c >= 0 && args[c + 1] !== undefined) checkCommand(args[c + 1], false);
        const file = args.findIndex((a) => !a.startsWith("-"));
        if (c < 0 && file >= 0 && norm(args[file]).split("/").pop() === "gradlew") checkGradle(args.slice(file + 1));
        seg.bodies.forEach((b) => checkCommand(b, false));
    } else if (name === "pwsh" || name === "powershell") checkPowerShell(args);
    else if (["eval", "iex", "invoke-expression"].includes(name)) checkCommand(args.join(" "), ps);
    // Git Bash turns cmd's /c into //c.
    else if (name === "cmd") checkCommand(args.filter((a) => !/^\/\/?[a-z]$/i.test(a)).join(" "), false);
    else if (["start-process", "saps", "start"].includes(name)) {
        // -FilePath may come after -ArgumentList: the program goes first. -ArgumentList is often one string or an @(...)
        // array: split it into words.
        const file = args.findIndex((a) => /^-filepath$/i.test(a));
        const ordered = file >= 0 && args[file + 1] !== undefined
            ? [args[file + 1], ...args.filter((_, n) => n !== file && n !== file + 1)]
            : args;
        const launched = ordered.flatMap((a) => a.replace(/^@\(|\)$/g, "").split(/[,\s]+/)).filter((a) => a && !/^-(filepath|argumentlist|wait|nonewwindow|passthru|workingdirectory|windowstyle|verb)$/i.test(a));
        checkSegment({ words: launched, targets: [], bodies: [] }, ps);
    }
}

/** Words left once wrappers (sudo, timeout 60, nice -n 5, env -i VAR=x…) and their options are removed. */
function stripPrefixes(words) {
    while (words.length) {
        const w = words[0].toLowerCase();
        const valued = PREFIXES[w];
        if (WRAPPERS.has(w) || /^[A-Za-z_]\w*=/.test(words[0])) {
            words = words.slice(1);
        } else if (valued) {
            let i = 1;
            while (i < words.length && words[i].startsWith("-")) {
                if (words[i] === "--") { i++; break; }
                i += valued.includes(words[i]) ? 2 : 1;
            }
            words = words.slice(w === "timeout" ? i + 1 : i);
        } else break;
    }
    return words;
}

function moveTo(dir) {
    const a = dir === undefined || dir === "-" ? null : absolute(dir, ctx.cwd);
    ctx.cwd = a === null ? "/unknown-directory" : a;
}

function optionValues(args, names) {
    return args.flatMap((a, i) => (names.includes(a) && args[i + 1] !== undefined ? [args[i + 1]] : []));
}

function copyTargets(args) {
    const plain = args.filter((a) => !a.startsWith("-"));
    return [...optionValues(args, ["-t", "-Destination", "-destination"]), ...plain.slice(-1)];
}

function checkInterpreter(name, args, bodies) {
    const codeFlags = name === "python" || name === "python3" || name === "py" ? ["-c"] : ["-e", "-E", "--eval", "-p", "--print", "-r", "eval"];
    const code = optionValues(args, codeFlags);
    [...code, ...bodies].forEach(checkInlineCode);
    if ((name === "perl" || name === "ruby") && args.some((a) => /^-[a-z]*i/i.test(a))) {
        args.filter((a) => !a.startsWith("-") && !code.includes(a)).forEach((a) => checkTarget(a, false));
    }
}

function checkPowerShell(args) {
    const i = args.findIndex((a) => /^-(c|command|e|ec|encodedcommand)$/i.test(a));
    if (i < 0 || args[i + 1] === undefined) return;
    const encoded = /^-e/i.test(args[i]);
    checkCommand(encoded ? Buffer.from(args[i + 1], "base64").toString("utf16le") : args.slice(i + 1).join(" "), true);
}

/** Gradle tasks that start a Hytale server: runServer (each mod) and runAllMods (the workspace, all mods at once). */
const SERVER_TASKS = ["runServer", "runAllMods"];

function checkServer(task) {
    const humps = task.match(/[A-Z]?[^A-Z]*/g).filter(Boolean);
    const camel = new RegExp("^" + humps.map((h) => h.replace(/[^\w]/g, "\\$&") + "[a-z0-9]*").join(""), "i");
    if (task && SERVER_TASKS.some((t) => camel.test(t))) deny("CLAUDE.md § 9.4: never launch the Hytale server; the user restarts it and tests in game.");
}

function checkGradle(args) {
    for (let i = 0; i < args.length; i++) {
        if (args[i] === "-x" || args[i] === "--exclude-task") i++;
        else if (!args[i].startsWith("-")) checkServer(args[i].split(":").pop());
    }
}

const SERVER_JAR = /server[^/]*\.jar$/i;
const HYTALE_MAIN = /com[./]hypixel[./]hytale[./]\w*main\b/i;

/**
 * Denies java when any word names a server jar, Hytale's main class (also as a -m module/class) or an @argfile. The one
 * exception: -jar runs another jar (the first .jar word after it, options may come between), so a server jar after
 * that one is only its input (a decompiler's); the words up to that jar are still judged.
 */
function checkJava(args) {
    const words = args.flatMap((a) => a.split(/\s+/)).filter(Boolean);
    const jar = words.indexOf("-jar");
    const run = jar < 0 ? -1 : words.findIndex((w, n) => n > jar && /\.jar$/i.test(w));
    const other = run >= 0 && !SERVER_JAR.test(norm(words[run]));
    if ((other ? words.slice(0, run + 1) : words).some((a) => SERVER_JAR.test(norm(a)) || HYTALE_MAIN.test(a) || a.startsWith("@"))) {
        deny("CLAUDE.md § 9.4: never launch the Hytale server; the user restarts it and tests in game.");
    }
}

/** True when `word` is a (possibly abbreviated, git accepts unique prefixes) form of the long option `full`. */
function isLong(word, full, min) {
    const base = word.split("=")[0].toLowerCase();
    return base.length >= min && full.startsWith(base);
}

/** Letters of a short-option cluster, cut at the first letter that takes a value. */
function shortLetters(word, valued) {
    if (!/^-[^-]/.test(word)) return "";
    let out = "";
    for (const ch of word.slice(1)) {
        out += ch;
        if (valued.includes(ch)) break;
    }
    return out;
}

/** Splits git arguments into options and operands, skipping option values. */
function operands(args, valuedShort, valuedLong) {
    const opts = [];
    const ops = [];
    for (let i = 0; i < args.length; i++) {
        const a = args[i];
        if (a === "--") { ops.push(...args.slice(i + 1)); break; }
        if (!a.startsWith("-") || a === "-") { ops.push(a); continue; }
        opts.push(a);
        const letters = shortLetters(a, valuedShort);
        const shortValue = letters && valuedShort.includes(letters.slice(-1)) && letters.length === a.length - 1;
        const longValue = a.startsWith("--") && !a.includes("=") && valuedLong.some((l) => isLong(a, l, 3) && a.length > 3);
        if (shortValue || longValue) i++;
    }
    return { opts, ops };
}

function checkGit(args) {
    let i = 0;
    let base = ctx.cwd;
    while (i < args.length && args[i].startsWith("-")) {
        const o = args[i];
        if (o === "-c" || o === "--config-env") checkHooksSetting(args[i + 1] || "", o !== "-c");
        if (o.startsWith("--config-env=")) checkHooksSetting(o.slice(13), true);
        if (o === "-C" && args[i + 1] !== undefined) base = absolute(args[i + 1], base) || base;
        i += ["-c", "-C", "--git-dir", "--work-tree", "--namespace", "--exec-path", "--config-env"].includes(o) ? 2 : 1;
    }
    const sub = (args[i] || "").toLowerCase();
    const rest = args.slice(i + 1);
    if (sub === "commit") checkCommit(rest, base);
    else if (sub === "push") checkPush(rest);
    else if (sub === "add" || sub === "stage") checkAdd(rest, base);
    else if (sub === "config") checkConfig(rest);
    else if (["checkout", "restore", "rm", "mv", "update-index", "checkout-index"].includes(sub)) checkGitWrite(sub, rest, base);
    if (RESEARCH_ONLY && /^(add|stage|commit|push|checkout|switch|restore|reset|stash|rm|mv|merge|rebase|cherry-pick|revert|apply|am|clean|pull)$/.test(sub)) {
        deny("hycolony-researcher never changes git state.");
    }
}

function checkHooksSetting(kv, envOnly) {
    const [key, ...value] = kv.split("=");
    if (key.toLowerCase() === "core.hookspath" && (envOnly || value.join("=") !== ".githooks")) {
        deny("CLAUDE.md § 10: core.hooksPath stays .githooks. " + ASK_USER);
    }
}

const COMMIT_VALUED_LONG = ["--message", "--file", "--reuse-message", "--reedit-message", "--fixup", "--squash",
    "--author", "--date", "--cleanup", "--template", "--trailer"];

function checkCommit(args, base) {
    const { opts, ops } = operands(args, "mFCct", COMMIT_VALUED_LONG);
    for (const o of opts) {
        if (/[an]/.test(shortLetters(o, "mFCct")) || isLong(o, "--no-verify", 6) || isLong(o, "--all", 3) || isLong(o, "--pathspec-from-file", 4)) {
            deny("CLAUDE.md § 10: never git commit --no-verify/-n (fix what the hook reports) nor -a/--all (stage explicit paths).");
        }
        if (isLong(o, "--amend", 4)) {
            deny("CLAUDE.md § 10: never git commit --amend: HEAD may be another session's commit. Fix a bad commit with a new one.");
        }
    }
    checkPathspecs(ops, base);
}

function checkPush(args) {
    const { opts, ops } = operands(args, "o", ["--repo", "--receive-pack", "--exec", "--push-option"]);
    for (const o of opts) {
        const b = o.split("=")[0].toLowerCase();
        if (/[fd]/.test(shortLetters(o, "o")) || b.startsWith("--force") || isLong(o, "--force", 4) || isLong(o, "--no-verify", 6)
            || isLong(o, "--mirror", 4) || isLong(o, "--delete", 3) || isLong(o, "--prune", 4)) {
            deny("CLAUDE.md § 10: never git push --no-verify, --force/-f, --mirror, --delete/-d or --prune. Ask the user.");
        }
    }
    if (ops.some((o) => o.startsWith("+") || o.startsWith(":"))) deny("CLAUDE.md § 10: never force (+refspec) or delete (:branch) with git push. Ask the user.");
}

function checkAdd(args, base) {
    const { opts, ops } = operands(args, "", ["--chmod", "--pathspec-from-file"]);
    for (const o of opts) {
        if (/[Au]/.test(shortLetters(o, "")) || isLong(o, "--all", 3) || isLong(o, "--update", 3) || isLong(o, "--no-ignore-removal", 6) || isLong(o, "--pathspec-from-file", 4)) {
            deny("CLAUDE.md § 9.5: git add with explicit paths only; never -A/--all/-u/--update.");
        }
    }
    checkPathspecs(ops, base);
}

function checkPathspecs(ops, base) {
    for (const o of ops) {
        const rel = relative(o, base);
        if (o === ":" || o.startsWith(":/") || o.startsWith(":(") || /[*?[]/.test(o) || rel === "") {
            deny(`CLAUDE.md § 9.5: stage explicit paths only, not "${o}".`);
        }
        if (rel !== null && (category(rel, false) || [])[0] === LOCAL) deny(`CLAUDE.md § 9.5: ${o} is a local file, never committed.`);
    }
}

function checkConfig(args) {
    const w = args.map((a) => a.toLowerCase());
    if (w.some((a) => a === "--edit" || a === "-e" || a === "edit")) deny("CLAUDE.md § 10: git config --edit can change core.hooksPath. " + ASK_USER);
    const section = w.findIndex((a) => /^(--)?(remove|rename)-section$/.test(a));
    if (section >= 0 && w[section + 1] === "core") deny("CLAUDE.md § 10: the core section holds core.hooksPath. " + ASK_USER);
    const k = w.findIndex((a) => a.split("=")[0] === "core.hookspath");
    if (k < 0 || w.some((a) => /^(--get|get$|--list$|-l$|list$|--show)/.test(a))) return;
    const value = w[k].includes("=") ? args[k].slice(args[k].indexOf("=") + 1) : args[k + 1];
    if (w.some((a) => a.includes("unset")) || (value !== undefined && value !== ".githooks")) {
        deny("CLAUDE.md § 10: core.hooksPath stays .githooks. " + ASK_USER);
    }
}

function checkGitWrite(sub, args, base) {
    const has = (...names) => args.some((a) => names.includes(a));
    const indexOnly = (sub === "restore" && has("--staged", "-S") && !has("--worktree", "-W")) || (sub === "rm" && has("--cached"));
    if (indexOnly) return;
    const { ops } = operands(args, sub === "restore" ? "s" : "bB", ["--source", "--conflict", "--pathspec-from-file"]);
    ops.forEach((o) => checkTarget(o, true, base));
}

function simulate(tool, input, before) {
    if (tool === "Write") return input.content || "";
    const edits = tool === "MultiEdit" ? input.edits || [] : [input];
    return edits.reduce((text, e) => {
        if (!e.old_string) return text + (e.new_string || "");
        return e.replace_all ? text.split(e.old_string).join(e.new_string || "") : text.replace(e.old_string, () => e.new_string || "");
    }, before);
}

const entries = (text) => text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l && !l.startsWith("#"));
// The root build file's checks (sizes, section dividers, spotless, PMD) start at its first "// CLAUDE.md §" comment.
const checks = (text) => text.replace(/\r\n/g, "\n").slice(Math.max(0, text.replace(/\r\n/g, "\n").indexOf("// CLAUDE.md §")));

function checkFile(tool, input) {
    const file = input.file_path || input.notebook_path || "";
    if (isUserSettings(file, ctx.cwd)) deny(`${file}: the user's local settings are never written by an agent. Ask them.`);
    const rel = relative(file);
    if (RESEARCH_ONLY && (rel === null || !rel.startsWith("docs/research/"))) deny(`hycolony-researcher writes only under docs/research/ (${file}).`);
    const [cat] = (rel !== null && category(rel, false)) || [];
    if (cat === LOCAL) deny(`${file}: the user's local settings and git config are never written by an agent. Ask them.`);
    if (!cat || (cat === GUARD && UNLOCKED)) return;
    if (cat === GUARD && rel !== "build.gradle.kts") deny(`${file} is a guardrail. ` + ASK_USER);
    if (!["Write", "Edit", "MultiEdit"].includes(tool)) deny(`${file}: use Edit or Write. ` + ASK_USER);
    const disk = absolute(file, ctx.cwd);
    const before = fs.existsSync(disk) ? fs.readFileSync(disk, "utf8") : "";
    const after = simulate(tool, input, before);
    if (cat === GUARD && checks(before) !== checks(after)) deny(`${file}: the build checks and their thresholds are guardrails. ` + ASK_USER);
    if (cat === LIST) {
        const left = new Map();
        entries(before).forEach((e) => left.set(e, (left.get(e) || 0) + 1));
        for (const e of entries(after)) {
            if (!left.get(e)) deny(`CLAUDE.md § 8: ${rel} may only shrink ("${e}" is new). Split or clean the file instead.`);
            left.set(e, left.get(e) - 1);
        }
    }
}

try {
    const { tool_name: tool, tool_input: input = {}, cwd } = JSON.parse(fs.readFileSync(0, "utf8"));
    ctx.root = findRoot(cwd);
    ctx.cwd = (cwd && absolute(cwd, ctx.root)) || ctx.root;
    if (tool === "Bash" || tool === "PowerShell") checkCommand(String(input.command || ""), tool === "PowerShell");
    else if (["Edit", "Write", "MultiEdit", "NotebookEdit"].includes(tool)) checkFile(tool, input);
} catch (e) {
    // Fail closed; an unlocked session (the user maintaining the guardrails) fails open so a broken guard can be fixed.
    if (UNLOCKED) process.stderr.write(`guard.js crashed, allowed because the guardrails are unlocked: ${e.stack}\n`);
    else deny(`guard.js crashed (${e.message}); the call is refused. ` + ASK_USER);
}
