---
name: ship
description: Finish a HyColony change done in this session - green build, independent reviews, review fixes re-reviewed, then explicit staging and a conventional commit (CLAUDE.md § 9.3-9.5).
disable-model-invocation: true
argument-hint: <what the change does, in one sentence>
---

Ship `$ARGUMENTS`. CLAUDE.md applies in full; this is the order of the end of work. Answer the user in French.

1. **Scope.** `git branch --show-current` and `git status --short`: other sessions work in the same folder. List the exact paths of this change; everything else is not yours and is never staged, formatted or reverted.
2. **Build.** Format this change's Java files one by one (`./gradlew :<project>:spotlessApply -PspotlessIdeHook="<absolute path>"`), then `./gradlew build`. Read the exit code and the output. If only another session's unfinished files break it, build a `git archive HEAD` export plus `git diff HEAD -- <paths>` and the untracked files of the scope in the scratchpad instead, and say so.
3. **Reviews, in parallel** (one message, several Agent calls), each given the scope (paths or commit range) and the one-sentence intent:
   - `hycolony-reviewer`, always;
   - `mc-fidelity-checker`, when the change ports or touches a MineColonies system in a core;
   - `ui-lang-checker`, when the diff touches a `.ui`, a `.lang`, a view record or a window class.
4. **Fixes.** Apply the **Bloquant** findings (and the **Mineur** ones that are cheap), then send the fixes back to the reviewer that raised them. Loop until every verdict is `PRÊT`. A finding you reject gets its reason, written back to the user.
5. **Stage.** `git add <explicit paths>` only (the guard refuses `-A`, `.`, `-u`). Never `config.json`, `config.json.bak`, `.mcp.json`, `.claude/settings.local.json`. A path removed with `git rm` is never re-added. Then `git diff --cached --stat` and `git diff --cached`: only this change is staged.
6. **Commit.** One logical unit per commit, each compiling alone. Message in English, `type(module): description` (`feat(core):`, `fix(plugin):`, `refactor(domum):`, `test(core):`, `docs:`), ending with the trailer lines this session asks for. Never `--no-verify`, never `--amend`: if a hook fails, fix the cause and commit again.
7. **Report.** Commits (hash and title), build result, review verdicts, anything left out of scope. Never launch the Hytale server: tell the user what to test in game (`docs/TESTING.md`, `/hycolony selftest`) once the build is green and the reviews are done.
