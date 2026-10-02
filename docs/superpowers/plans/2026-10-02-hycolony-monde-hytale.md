# Systèmes MC, monde Hytale : plan d'implémentation

> **Pour les agents :** sous-skill requise : superpowers:executing-plans (ou subagent-driven-development). Les étapes sont des cases à cocher (`- [ ]`).

**But :** écrire la règle « systèmes MC, monde Hytale » dans CLAUDE.md et les garde-fous qui la répètent, puis livrer l'audit `docs/research/audit-monde-hytale.md`.

**Architecture :** pas de code Java. Le plan ne touche que de la documentation et des garde-fous : il pose la règle, puis l'audit qui ouvre les corrections par domaine. Chaque correction aura ensuite sa propre conception (spec § 6), hors de ce plan.

**Outils :** Edit/Write (jamais de script, voir mémoire), agents `hycolony-researcher` (audit) et `hycolony-reviewer` (relecture).

**Spec :** `docs/superpowers/specs/2026-10-02-hycolony-monde-hytale-design.md`

## Contraintes globales

- Session lancée avec `HYCOLONY_GUARDRAILS_UNLOCKED=1` (sinon `guard.js` refuse l'écriture des garde-fous).
- Marquage d'un écart dû au monde : `Deviation from MC (Hytale world): <règle MC> → <équivalent Hytale, source>`.
- Les écarts déjà présents dans le code ne sont pas réécrits par ce plan. L'audit les recense, et les corrections par domaine les mettront au nouveau format.
- `git add` de chemins explicites. D'autres sessions modifient l'arbre (`Warehouse.*`, `tools/common/*`) : on n'y touche pas.
- Docs en français. Agents et skills en anglais, comme aujourd'hui.

## Points d'attention pour la relecture

1. Les quatre garde-fous (CLAUDE.md, `port-mc`, `mc-fidelity-checker`, `hycolony-implementer`/`hycolony-reviewer`) doivent tracer **la même frontière**. Une liste qui diverge produirait des relectures contradictoires.
2. L'attendu d'un test d'une règle du monde vient du fait Hytale cité, plus de MC. Il faut le dire partout où l'on disait « l'attendu vient de MC » : `port-mc` étape 4, implementer « TDD », reviewer « Changed expectations », fidelity-checker étape 5, `pieges-portage` § 2.5.
3. La mémoire « If MC does it, do it » ne vaut plus que pour les systèmes. Elle est à nuancer, sinon une prochaine session recopiera une règle du monde.
4. Une entrée d'audit sans ses trois sources (MC, HyColony, Hytale), ou avec un fait Hytale supposé, est un défaut. La relecture de l'audit le vérifie.
5. Les écarts déjà marqués `Deviation from MC:` et dus au monde doivent figurer dans l'audit (verdict *conforme* s'ils suivent déjà Hytale), pas seulement les copies de MC.

---

### Tâche 1 : la règle dans CLAUDE.md

**Fichiers :** modifier `CLAUDE.md` (intro l. 5, § 6).

- [ ] **Étape 1 : remplacer l'intro (l. 5)** par :

```markdown
HyColony porte MineColonies sur Hytale 0.7.0-pre.4 (Update 7, épinglé dans `gradle.properties`). **Les systèmes de MC sont portés à l'identique** : huttes, métiers, IA, requêtes, colonie, formules des citoyens, fenêtres, avec leurs règles, constantes et formules. **Le monde est celui de Hytale** : blocs, objets, outils, artisanat, culture, nourriture, mobs, temps. Une règle de MC qui s'appuie sur le monde Minecraft suit l'équivalent Hytale le plus proche. Les écarts sont justifiés et documentés (voir § 6).
```

- [ ] **Étape 2 : remplacer les trois premières puces du § 6** (source citée, constantes, écart) par :

```markdown
- **Système ou monde** (spec `docs/superpowers/specs/2026-10-02-hycolony-monde-hytale-design.md`) :
  - le **système** suit MC : huttes, niveaux et plans, métiers, IA et délais, requêtes, entrepôt, colonie, territoire, permissions, commandes, formules des citoyens (compétences, XP, bonheur, faim), fenêtres, configuration ;
  - le **monde** suit Hytale : blocs, objets, matériaux, outils (paliers, durabilité), artisanat (bancs, recettes, carburant), culture, nourriture, mobs, raids et combat, jour et nuit, météo, taille du monde, navigation et physique des PNJ, sons, particules, animations ;
  - une durée de MC liée au monde (la nuit, 24 000 ticks par jour) se recalcule en fraction du jour de Hytale ; un délai du système reste en ticks de MC ;
  - ce que Hytale n'a pas prend l'équivalent Hytale le plus proche, vérifié dans `build/vineflower/hytale-server` ou les assets. HyVanilla n'ajoute une chose de Minecraft que s'il n'existe rien de comparable et que le système ne peut pas s'en passer ;
  - ce que seul Hytale a (magie, montures…) n'est ajouté que si un système de MC y touche ;
  - les objets propres à MC (outil de construction, blocs de huttes, Domum Ornamentum) restent ; leurs recettes passent par les bancs de Hytale.
- Chaque système porté cite sa source MineColonies dans sa Javadoc (`MC EntityAIStructureBuilder.placeBlock`).
- Constantes et formules du système reprises telles quelles, en ticks (chaque cœur tourne à 20 ticks/s). Une valeur qui mesure le monde Minecraft (durée de croissance, palier, durabilité) suit Hytale.
- Un écart (contrainte Hytale, bug de MC corrigé, ajout demandé) porte un commentaire `Deviation from MC: …` et figure dans la spec du sous-projet. Un écart dû au monde s'écrit `Deviation from MC (Hytale world): <règle MC> → <équivalent Hytale, source>`.
```

La puce « Référence : … » reste telle quelle.

- [ ] **Étape 3 : vérifier `AGENTS.md`.** `grep -n "identi\|fidelity\|faithful" AGENTS.md` : s'il répète la règle « à l'identique », remplacer par un renvoi au § 6 ; sinon, rien à faire.

### Tâche 2 : la skill et les agents

**Fichiers :** modifier `.claude/skills/port-mc/SKILL.md`, `.claude/agents/mc-fidelity-checker.md`, `.claude/agents/hycolony-implementer.md`, `.claude/agents/hycolony-reviewer.md`.

- [ ] **Étape 1 : `port-mc`, description (frontmatter)** :

```yaml
description: Port a MineColonies system, class or behaviour into HyColony's core — MC's systems faithfully (same rules, constants, formulas), Hytale's world (CLAUDE.md § 6). Use when the user asks to port, implement or align something "comme dans MineColonies".
```

- [ ] **Étape 2 : `port-mc`, étape 3**. Insérer en tête, avant « Anything touching the world goes through a port » :

```markdown
3. **Map to Hytale.** Sort every rule you noted into **system** (follows MC) or **world** (follows Hytale), with CLAUDE.md § 6. For each world rule (tool tier, block, recipe or bench, crop growth, food, mob, time of day…), find Hytale's closest equivalent in `build/vineflower/hytale-server` and the assets (skill `hytale-api`), and note it with its source; a world rule copied from MC while Hytale has an equivalent is a bug. A Minecraft thing is added (HyVanilla) only when nothing comparable exists and the system cannot do without it. Anything touching the world goes through a port …
```

(la suite de la phrase actuelle est gardée telle quelle).

- [ ] **Étape 3 : `port-mc`, étape 4.** Remplacer « The test's expected values come from the MC source it cites, never from the code. » par « The test's expected values come from the MC source it cites (system) or the Hytale fact it cites (world), never from the code. »

- [ ] **Étape 4 : `port-mc`, étape 5.** Remplacer la phrase sur les écarts par : « Every deviation carries `Deviation from MC: <why>`; one forced by Hytale's world carries `Deviation from MC (Hytale world): <MC rule> → <Hytale equivalent, source>`. Each is listed in the sub-project spec. »

- [ ] **Étape 5 : `mc-fidelity-checker`.**
  - Dans l'en-tête, après « Read `CLAUDE.md` § 6 », ajouter : « MC's **systems** must match MC; the **world** (blocks, items, tools, crafting, farming, food, mobs, time of day…) must follow Hytale (§ 6). »
  - Ajouter à l'étape 3 la puce : « **world rules**: for each rule § 6 puts in the world, check that HyColony follows Hytale's closest equivalent and cites its Hytale source (decompiled class or asset path); a world rule copied from MC while Hytale has an equivalent is a finding. »
  - À l'étape 5, remplacer « every test's expected value, must match the MC source » par « every test's expected value, must match the MC source (system) or the cited Hytale fact (world) ».
  - À l'étape 6, ajouter : « A world deviation is written `Deviation from MC (Hytale world): <MC rule> → <Hytale equivalent, source>`. »

- [ ] **Étape 6 : `hycolony-implementer`.**
  - Puce « TDD » : remplacer « The expected values come from the MC source, cited in the test's Javadoc » par « The expected values come from the MC source (system) or the Hytale fact (world, CLAUDE.md § 6), cited in the test's Javadoc ».
  - Puce « Fidelity (§ 6) » :

```markdown
- **Fidelity (§ 6)**: MC's systems verbatim, Hytale's world. Javadoc cites the MC source (`MC EntityAIStructureBuilder.placeBlock`); system constants and formulas verbatim, in ticks; a world rule (tool tier, block, recipe, crop, food, mob, time of day…) follows Hytale's closest equivalent, verified in the decompiled server or the assets. Every difference carries `Deviation from MC: <why>` (`Deviation from MC (Hytale world): <MC rule> → <Hytale equivalent, source>` for the world) and goes into the sub-project spec.
```

- [ ] **Étape 7 : `hycolony-reviewer`.**
  - Puce « Changed expectations are suspect » : « …the MC source (`file:line`), the Hytale fact for a world rule (CLAUDE.md § 6), or only the new code. »
  - Puce « MineColonies fidelity » :

```markdown
- **MineColonies fidelity**: Javadoc cites the MC source; system constants and formulas match MC (in ticks); world rules follow Hytale's closest equivalent with its source (CLAUDE.md § 6), and a world rule copied from MC while Hytale has one is a finding. Any deviation carries `Deviation from MC: …` (`(Hytale world)` for the world). When in doubt, check `sources/minecolonies/` (CLAUDE.md § 6) and `docs/research/`.
```

- [ ] **Étape 8 : contrôle de cohérence.** `grep -rn "Hytale world" CLAUDE.md .claude/` : les cinq fichiers apparaissent. `node .claude/hooks/test/run.js` passe.

### Tâche 3 : les pièges du portage et la mémoire

**Fichiers :** modifier `docs/research/pieges-portage.md`, la mémoire `feedback-if-mc-does-it-do-it.md`.

- [ ] **Étape 1 : `pieges-portage.md` § 2.5.** Après « L'attendu d'un test vient de la source MC citée (`MC Fichier:ligne`). » ajouter : « Pour une règle du monde (CLAUDE.md § 6), il vient du fait Hytale cité (classe décompilée ou chemin d'asset). »

- [ ] **Étape 2 : `pieges-portage.md`, nouvelle section en fin de fichier**, après « ## 3. Pièges de méthode » :

```markdown
## 4. Pièges du monde

Depuis le 2026-10-02, les systèmes de MC sont portés à l'identique, mais le monde suit Hytale (CLAUDE.md § 6, spec `2026-10-02-hycolony-monde-hytale-design.md`). Avant de recopier une règle de MC, se demander si elle mesure le monde Minecraft. Règles du monde déjà rencontrées :

1. **Chunks et hauteur** : chunks de 32 blocs, hauteur 0–320 (pièges 1.2 et 1.10).
2. **Culture** : stades, durées, eau ×2,5, engrais ×2, lumière ×2, sol labouré qui revient, essence de vie (`sp3b-hytale-farming.md`).
3. **Nourriture et cuisson** : objets, effets, bancs de cuisson (`sp4b-hytale-food.md`).
4. **Monstres** : pas de condition de lumière sur ceux de surface (`colony-bounds-and-mob-spawns.md`).
5. **Arbres, minerais, échelles, navigation des PNJ** : `sp3a-mc-miner-hytale-world.md` § B.

L'audit `audit-monde-hytale.md` recense le reste.
```

- [ ] **Étape 3 : mémoire.** Dans `feedback-if-mc-does-it-do-it.md`, préciser que la règle vaut pour les **systèmes** de MC ; une règle du monde suit l'équivalent Hytale (CLAUDE.md § 6, 2026-10-02). Mettre à jour sa ligne dans `MEMORY.md`.

### Tâche 4 : relecture et commit de la règle

- [ ] **Étape 1 : relecture** par `hycolony-reviewer` sur le diff non commité des tâches 1–3. Points à vérifier : même frontière partout, aucune contradiction restante avec « à l'identique », points d'attention 1 et 2. Corriger, puis faire relire les corrections.
- [ ] **Étape 2 : commit.**

```bash
git add CLAUDE.md .claude/skills/port-mc/SKILL.md .claude/agents/mc-fidelity-checker.md .claude/agents/hycolony-implementer.md .claude/agents/hycolony-reviewer.md docs/research/pieges-portage.md
git diff --cached --stat
git commit -m "docs: MC's systems faithfully, Hytale's world (CLAUDE.md § 6, port-mc, review agents)"
```

(ajouter `AGENTS.md` s'il a changé ; le message se termine par les lignes de fin de la session).

### Tâche 5 : l'audit

**Fichiers :** créer `docs/research/audit-monde-hytale.md`. Parties temporaires, non commitées : `docs/research/audit-monde-hytale/{a,b,c,d}.md`.

- [ ] **Étape 1 : lancer quatre `hycolony-researcher` en parallèle**, un par groupe :
  - **A. Outils, matériaux et plans** : paliers et durabilité des outils (`builder-tools-durability-breaking.md`), blocs cassables, ressources du chantier, `styles.json`, `id-map.json`, et chaque bloc de HyVanilla (a-t-il désormais un équivalent Hytale ?) ;
  - **B. Artisanat, culture, nourriture** : bancs et recettes (artisans, recettes des objets de MC), fermier, champs, cuisinier, nourriture, effets ;
  - **C. Temps, mobs et combat** : cycle jour et nuit, sommeil, pluie et loisirs, apparition des monstres, raids, gardes s'ils existent, dégâts ;
  - **D. Navigation et le reste** : déplacement et blocage des PNJ, échelles, eau, sons, particules, animations, défauts de `config.json` qui mesurent le monde.

  Consigne commune à chaque agent, à reprendre mot pour mot :

  > Lis CLAUDE.md § 6 (la frontière système / monde) et la spec `docs/superpowers/specs/2026-10-02-hycolony-monde-hytale-design.md` § 2 et § 5. Dans `core/`, `plugin/` (et `vanilla/` pour le groupe A), trouve chaque endroit où HyColony porte une règle de MineColonies qui relève du **monde** dans ton groupe <groupe>. Cela inclut les écarts déjà marqués `Deviation from MC`. Pour chacun, écris une entrée dans `docs/research/audit-monde-hytale/<lettre>.md` avec : (1) la règle de MC, `sources/minecolonies/…:ligne` (lu, pas de mémoire) ; (2) le code HyColony, `fichier:ligne`, et ce qu'il fait (copie de MC, écart marqué, ajout HyVanilla) ; (3) le fait Hytale, avec sa classe décompilée (`build/vineflower/hytale-server`) ou son chemin d'asset ; (4) le verdict : *conforme*, *à adapter* ou *à retirer de HyVanilla* ; (5) la proposition, avec son effet sur les sauvegardes et la config. Classe chaque entrée : *casse le jeu*, *incohérent* ou *cosmétique*. Aucune affirmation sans source. N'écris que ce fichier. En français.

- [ ] **Étape 2 : fusionner** les quatre parties dans `docs/research/audit-monde-hytale.md` :
  - en tête : la date, le renvoi à la spec, puis la table des domaines (domaine, nombre d'entrées par verdict, gravité la plus haute, ordre proposé : *casse le jeu* d'abord) ;
  - ensuite : les entrées par domaine.

  Supprimer `docs/research/audit-monde-hytale/` (jamais indexé).

- [ ] **Étape 3 : relecture de l'audit** par `hycolony-reviewer`. Il vérifie au hasard au moins deux entrées par domaine (les trois sources existent et disent ce qui est écrit), et que les écarts déjà marqués et dus au monde sont recensés : `grep -rn "Deviation from MC" core plugin` comparé aux entrées. Corriger, puis faire relire les corrections.
- [ ] **Étape 4 : commit.**

```bash
git add docs/research/audit-monde-hytale.md
git commit -m "docs: audit of MC world rules against Hytale's world"
```

- [ ] **Étape 5 : présenter l'audit à l'utilisateur** : la table des domaines et l'ordre proposé. Il choisit l'ordre. Chaque domaine suit ensuite CLAUDE.md § 9.1 ou § 9.2 (spec § 6), hors de ce plan.
