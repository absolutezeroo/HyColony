# HyLens V2, lot 1 : le livre de MineColonies et la caméra libre — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** le menu de HyLens devient le livre de la mairie de MineColonies (onglets Colonies, Citoyens, Vue), le suivi gagne la caméra libre, le HUD prend le parchemin de la fenêtre de débogage de MC.

**Architecture :** le cœur de HyLens (`hylens/core`, Java pur, TDD) apprend l'onglet ouvert (`MenuTab` dans `MenuState` et `MenuView`). Le plugin (`hylens/plugin`) remplace `Menu.ui` par le cadre du livre et une page par onglet, découpe `MenuRender` en un rendu par onglet, et passe la caméra par `CitizenWatch` (composant `Spectating` de Hytale). Les textures de MC sont copiées dans le pack de HyLens.

**Tech Stack :** Java 25, JUnit 5, Gradle (`:hylens-core`, `:hylens-plugin`), `.ui` de Hytale 0.7.0-pre.5.

**Spec :** `docs/superpowers/specs/2026-10-02-hylens-livre-mc-design.md`

## Global Constraints

- CLAUDE.md s'applique en entier : 400 lignes par fichier au plus (300 visé), 40 lignes par méthode, 5 paramètres, Javadoc courte sur chaque classe et méthode non triviale, 120 colonnes, pas de commentaires séparateurs.
- Pas d'import `com.hypixel` dans `hylens/core` **[ArchitectureTest]**. HyLens ne voit de HyColony que `dev.hycolony.api` et `dev.hycolony.plugin.api` **[checkModApis]**.
- Tout texte vu par le joueur passe par une clé de `hylens.lang`, présente en en-US et fr-FR **[checkLangParity]**. Une clé complète par variante de bouton. Un texte imbriqué (`param(key, Message)`) va sur `.TextSpans`.
- Les identifiants d'assets Hytale ne vivent que dans `hylens/id-map.json` (ce plan n'en ajoute aucun).
- Positions et tailles de MC ×2 ; textures de `Pages/HyColony/Mc/` (déjà en @2x) copiées, jamais référencées depuis HyLens.
- Commits : `git add <chemins>` explicites ; message `type(module): description` en anglais ; fin de message `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. D'autres sessions travaillent dans le même dossier : `git status` avant chaque commit, `git diff --cached --name-only` pour vérifier l'index.
- Formatage fichier par fichier : `./gradlew :hylens-core:spotlessApply -PspotlessIdeHook="<chemin absolu>"` (le hook de la session le fait à la fin de chaque tour).
- On ne lance jamais le serveur. `./gradlew build` vert avant chaque commit ; relecture par `hycolony-reviewer` (et `ui-lang-checker` dès qu'un `.ui` ou un `.lang` change) avant d'annoncer la modification prête.

## Review Focus

1. **Changer d'onglet après avoir tapé une case « Envoyer ici »** : en revenant sur Citoyens, les X Y Z tapés sont toujours là. `MenuSend.remember` ignore un clic sans champs ; il ne faut donc lier `@X/@Y/@Z` que sur l'onglet Citoyens (tâche 2, `MenuBinds`), sinon le client lit un élément absent. Essai en jeu dans la tâche 2.
2. **Caméra libre, puis le citoyen meurt ou se décharge** : « Reprendre le suivi » refuse avec `hylens.watch.noBody`, le suivi et le HUD restent. Essai en jeu dans la tâche 3.
3. **Déconnexion en caméra libre** : `OperatorExit` voit toujours `Spectating` et rend le mode de jeu. Essai en jeu dans la tâche 3.
4. **Caméra libre, puis « Suivre » sur un autre citoyen** : `CitizenWatch.start` pose la nouvelle cible sans ressortir du mode (il est déjà spectateur). Essai en jeu dans la tâche 3.
5. **Choisir une colonie depuis l'onglet Citoyens** n'existe pas (la liste est sur Colonies) ; mais choisir une colonie **ouvre** Citoyens, et choisir un citoyen ne change pas d'onglet. Tests du cœur dans la tâche 1.

---

### Tâche 1 : l'onglet ouvert, dans le cœur de HyLens

**Files :**
- Create : `hylens/core/src/main/java/dev/hylens/core/menu/MenuTab.java`
- Modify : `hylens/core/src/main/java/dev/hylens/core/menu/MenuState.java`
- Modify : `hylens/core/src/main/java/dev/hylens/core/menu/MenuView.java`
- Modify : `hylens/core/src/main/java/dev/hylens/core/menu/MenuViews.java:49`
- Test : `hylens/core/src/test/java/dev/hylens/core/menu/MenuStateTest.java`, `MenusTest.java:50`, `MenuViewsTest.java`

**Interfaces :**
- Produces : `public enum MenuTab { COLONIES, CITIZENS, VIEW }` ; `MenuState.tab()`, `MenuState.withTab(MenuTab)` ; `MenuView.tab()` (dernière composante).

- [ ] **Étape 1 : les tests qui échouent**

Dans `MenuStateTest.java`, remplacer le premier test et ajouter les trois suivants :

```java
    @Test
    void nothingIsChosenAtFirstEveryLayerShowsAndTheColoniesTabIsOpen() {
        assertEquals(
                new MenuState(Optional.empty(), Optional.empty(), 1, Layers.ALL, false, MenuTab.COLONIES),
                MenuState.INITIAL);
    }

    @Test
    void choosingAColonyOpensItsCitizens() {
        assertEquals(MenuTab.CITIZENS, MenuState.INITIAL.withColony(A).tab());
    }

    @Test
    void choosingATabKeepsEveryChoice() {
        MenuState s = MenuState.INITIAL.withColony(A).withCitizen(ANN).withStep(3);

        MenuState view = s.withTab(MenuTab.VIEW);

        assertEquals(MenuTab.VIEW, view.tab());
        assertEquals(Optional.of(ANN), view.citizen());
        assertEquals(3, view.step());
    }

    @Test
    void theTabSurvivesEveryOtherChoiceButAColony() {
        MenuState s = MenuState.INITIAL.withTab(MenuTab.VIEW);

        assertEquals(MenuTab.VIEW, s.withCitizen(ANN).tab());
        assertEquals(MenuTab.VIEW, s.withStep(5).tab());
        assertEquals(MenuTab.VIEW, s.toggle(Layers.Layer.ZONE).tab());
        assertEquals(MenuTab.VIEW, s.toggleAutoCheck().tab());
    }
```

Dans `MenusTest.java:50`, l'attendu devient :

```java
                new MenuState(
                        Optional.of(A),
                        Optional.empty(),
                        1,
                        Layers.ALL.toggle(Layers.Layer.ZONE),
                        false,
                        MenuTab.CITIZENS),
                both);
```

Dans `MenuViewsTest.java`, ajouter :

```java
    @Test
    void theViewShowsTheOpenTab() {
        MenuView v = MenuViews.of(world, false, MenuState.INITIAL.withTab(MenuTab.VIEW), Optional.empty());

        assertEquals(MenuTab.VIEW, v.tab());
    }
```

- [ ] **Étape 2 : vérifier l'échec**

Run : `./gradlew :hylens-core:test --tests "dev.hylens.core.menu.*"`
Attendu : échec de compilation (`MenuTab` introuvable).

- [ ] **Étape 3 : le code**

`MenuTab.java` :

```java
package dev.hylens.core.menu;

/** The HyLens menu's tabs, in the order of their bookmarks in the book (spec 2026-10-02, § 3.1). */
public enum MenuTab {
    COLONIES,
    CITIZENS,
    VIEW
}
```

`MenuState.java` : la Javadoc de la classe ajoute « and the tab open » ; le record gagne `MenuTab tab` en dernière composante ; chaque `new MenuState(...)` existant passe `tab` en dernier argument, sauf :

```java
    /** Nothing chosen, a step of one tick, every layer shown, no automatic check, the Colonies tab open. */
    static final MenuState INITIAL =
            new MenuState(Optional.empty(), Optional.empty(), 1, Layers.ALL, false, MenuTab.COLONIES);

    /** {@code chosen} as the colony, its citizens' tab open; the citizen chosen is kept only if it belongs to it. */
    public MenuState withColony(ColonyRef chosen) {
        Optional<CitizenRef> kept = citizen.filter(c -> c.colony().equals(chosen));
        return new MenuState(Optional.of(chosen), kept, step, layers, autoCheck, MenuTab.CITIZENS);
    }

    /** {@code open} as the tab shown. */
    public MenuState withTab(MenuTab open) {
        return new MenuState(colony, citizen, step, layers, autoCheck, open);
    }
```

`MenuView.java` : composante `MenuTab tab` ajoutée après `boolean autoCheck`, Javadoc de la classe « …, and the tab open ». `MenuViews.java:49` :

```java
        return new MenuView(
                colonies, colony, citizens, citizen, s.layers(), paused, s.step(), s.autoCheck(), s.tab());
```

- [ ] **Étape 4 : vérifier le succès**

Run : `./gradlew :hylens-core:test`
Attendu : PASS. Puis `./gradlew :hylens-plugin:compileJava` : PASS (le plugin ne construit pas de `MenuState` ni de `MenuView`).

- [ ] **Étape 5 : commit**

```bash
git add hylens/core/src/main/java/dev/hylens/core/menu/MenuTab.java hylens/core/src/main/java/dev/hylens/core/menu/MenuState.java hylens/core/src/main/java/dev/hylens/core/menu/MenuView.java hylens/core/src/main/java/dev/hylens/core/menu/MenuViews.java hylens/core/src/test/java/dev/hylens/core/menu/MenuStateTest.java hylens/core/src/test/java/dev/hylens/core/menu/MenusTest.java hylens/core/src/test/java/dev/hylens/core/menu/MenuViewsTest.java
git commit -m "feat(hylens-core): the menu keeps the tab open, and choosing a colony opens its citizens"
```

---

### Tâche 2 : le livre (cadre, trois onglets, textures, crédits)

**Files :**
- Create : `hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens/Mc/` (21 textures copiées + `Book.ui`)
- Create : `hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens/Book/Colonies.ui`, `Citizens.ui`, `View.ui`, `ColonyRow.ui`, `CitizenRow.ui`, `LayerButton.ui`
- Delete : `Pages/HyLens/MenuColonyRow.ui`, `MenuCitizenRow.ui`, `MenuLayerButton.ui` (`git rm`)
- Modify : `Pages/HyLens/Menu.ui` (réécrit)
- Create : `hylens/plugin/src/main/java/dev/hylens/plugin/command/MenuBinds.java`, `ColoniesTab.java`, `CitizensTab.java`, `ViewTab.java`
- Modify : `hylens/plugin/src/main/java/dev/hylens/plugin/command/MenuRender.java` (réécrit), `MenuClicks.java`, `MenuPage.java`
- Modify : `hylens/plugin/src/main/resources/Server/Languages/en-US/hylens.lang`, `fr-FR/hylens.lang`
- Create : `NOTICE` ; Modify : `README.md:14`
- Modify : `docs/TESTING.md` (nouvelle section)

**Interfaces :**
- Consumes : `MenuTab`, `MenuView.tab()`, `MenuState.withTab` (tâche 1).
- Produces : `record MenuBinds(UIEventBuilder events, boolean cell)` avec `void on(String selector, String action, String index)` ; `MenuRender.render(UICommandBuilder ui, UIEventBuilder events, MenuView v, Optional<ApiText> result, CitizensTab.Watch watch)` ; `record CitizensTab.Watch(MenuSend.Typed cell, boolean cameraFree)` ; `MenuClicks.tab(String) : Optional<MenuTab>` ; `MenuClicks.WATCH` (utilisé en tâche 3).

Les plugins n'ont pas de tests unitaires (CLAUDE.md § 8) : la vérification est `./gradlew build` puis les essais en jeu ajoutés à `docs/TESTING.md`.

- [ ] **Étape 1 : copier les textures**

```bash
S=plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Mc
D=hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens/Mc
mkdir -p "$D"
for f in townhall_book bookmark_ribbon_01 bookmark_ribbon_02 bookmark_ribbon_03 \
  bookmark_short_ribbon_01 bookmark_short_ribbon_02 bookmark_short_ribbon_03 \
  red_wax_home red_wax_citizens red_wax_settings \
  builder_button_medium_large builder_button_medium_large_hover builder_button_medium_large_dim \
  builder_button_small builder_button_small_hover builder_button_small_dim \
  builder_button_mini builder_button_mini_hover builder_button_mini_dim \
  builder_paper_wide2; do cp "$S/$f@2x.png" "$D/"; done
ls "$D" | wc -l
```

Attendu : `20`. (`Book.ui` sera le 21ᵉ fichier.)

- [ ] **Étape 2 : les styles `Mc/Book.ui` de HyLens**

```
$C = "../../../Common.ui";

// HyLens' MC-look styles, from HyColony's Pages/HyColony/Mc/Book.ui (only those HyLens uses): ink on parchment and
// MC's textured buttons (builderhut/*). Textures are MineColonies' (ldtteam, GPL-3), scaled x4 nearest-neighbour into
// @2x files; paths resolve from this file. A hovered button shows the *_hover file, a disabled one the *_dim file.

@Ink = LabelStyle(FontSize: 15, TextColor: #000000, VerticalAlignment: Center);
@InkCentered = LabelStyle(...@Ink, HorizontalAlignment: Center);
@Faded = LabelStyle(FontSize: 12, TextColor: #555555, VerticalAlignment: Center);
@FadedCentered = LabelStyle(...@Faded, HorizontalAlignment: Center);
@Heading = LabelStyle(...@Ink, TextColor: #aa0000, HorizontalAlignment: Center, RenderBold: true);
@Alert = LabelStyle(FontSize: 12, TextColor: #aa0000, VerticalAlignment: Center, HorizontalAlignment: End);
@CompactInkCentered = LabelStyle(...@InkCentered, FontSize: 12);

// MC's builder_button_medium_large (129 x 17), doubled.
@WideButtonStyle = TextButtonStyle(
  Default: (Background: "builder_button_medium_large.png", LabelStyle: @InkCentered),
  Hovered: (Background: "builder_button_medium_large_hover.png", LabelStyle: @InkCentered),
  Pressed: (Background: "builder_button_medium_large_hover.png", LabelStyle: @InkCentered),
  Disabled: (Background: "builder_button_medium_large_dim.png", LabelStyle: @InkCentered),
  Sounds: $C.@ButtonSounds
);

// MC's builder_button_small (64 x 17), doubled.
@SmallButtonStyle = TextButtonStyle(
  Default: (Background: "builder_button_small.png", LabelStyle: @InkCentered),
  Hovered: (Background: "builder_button_small_hover.png", LabelStyle: @InkCentered),
  Pressed: (Background: "builder_button_small_hover.png", LabelStyle: @InkCentered),
  Disabled: (Background: "builder_button_small_dim.png", LabelStyle: @InkCentered),
  Sounds: $C.@ButtonSounds
);

// The same, its label smaller for the longer texts ("Arrêter le suivi").
@SmallCompactButtonStyle = TextButtonStyle(
  Default: (Background: "builder_button_small.png", LabelStyle: @CompactInkCentered),
  Hovered: (Background: "builder_button_small_hover.png", LabelStyle: @CompactInkCentered),
  Pressed: (Background: "builder_button_small_hover.png", LabelStyle: @CompactInkCentered),
  Disabled: (Background: "builder_button_small_dim.png", LabelStyle: @CompactInkCentered),
  Sounds: $C.@ButtonSounds
);

// MC's builder_button_mini (14 x 15), doubled: the row arrows and the step's "-" and "+".
@MiniButtonStyle = TextButtonStyle(
  Default: (Background: "builder_button_mini.png", LabelStyle: @InkCentered),
  Hovered: (Background: "builder_button_mini_hover.png", LabelStyle: @InkCentered),
  Pressed: (Background: "builder_button_mini_hover.png", LabelStyle: @InkCentered),
  Disabled: (Background: "builder_button_mini_dim.png", LabelStyle: @InkCentered),
  Sounds: $C.@ButtonSounds
);
```

- [ ] **Étape 3 : le cadre `Menu.ui` (réécrit)**

```
$C = "../../Common.ui";

// HyLens' menu as MC's town hall book (gui/townhall/windowtownhall.xml), MC positions and sizes doubled, as HyColony's
// TownHall.ui (spec 2026-10-02, § 3.1): the book, the open tab's page appended into #Page (Book/*.ui), the bookmarks
// drawn over it, the last action's result under the right page. Textures under Mc/ are MineColonies' (ldtteam, GPL-3),
// scaled x4 nearest-neighbour into @2x files. Bookmarks are numbered by MenuTab ordinal.

// A closed tab: its short ribbon behind its wax seal (MC "<tab>0" image and "<tab>" button).
@Mark = Group {
  @Slot = 0;

  Anchor: (Left: 112, Top: 146 + 48 * @Slot, Width: 62, Height: 28);
  Visible: false;
};

@Seal = Button {
  @Slot = 0;

  Anchor: (Left: 124, Top: 142 + 48 * @Slot, Width: 34, Height: 34);
  TextTooltipStyle: $C.@DefaultTextTooltipStyle;
  Visible: false;
};

// The open tab: its long ribbon with its name (MC "<tab>1" button).
@Ribbon = Group {
  Anchor: (Left: 116, Top: 86, Width: 408, Height: 34);
  Padding: (Left: 70, Top: 4);
  Visible: false;
};

@RibbonLabel = Label {
  Style: (FontSize: 15, TextColor: #ffffff, VerticalAlignment: Center);
};

$C.@PageOverlay {
  LayoutMode: Middle;

  Group {
    Anchor: (Width: 1048, Height: 486);

    Group {
      Anchor: (Left: 150, Top: 0, Width: 748, Height: 486);
      Background: "Mc/townhall_book.png";
    }

    Group #Page {
      Anchor: (Left: 0, Top: 0, Width: 1048, Height: 486);
    }

    Label #Result {
      Anchor: (Left: 552, Top: 404, Width: 296, Height: 34);
      Style: (FontSize: 12, TextColor: #000000, HorizontalAlignment: Center, VerticalAlignment: Center);
    }

    @Mark #Mark0 { @Slot = 0; Background: "Mc/bookmark_short_ribbon_01.png"; }
    @Mark #Mark1 { @Slot = 1; Background: "Mc/bookmark_short_ribbon_02.png"; }
    @Mark #Mark2 { @Slot = 2; Background: "Mc/bookmark_short_ribbon_03.png"; }

    @Ribbon #Ribbon0 { Background: "Mc/bookmark_ribbon_01.png"; @RibbonLabel #RibbonText0 {} }
    @Ribbon #Ribbon1 { Background: "Mc/bookmark_ribbon_02.png"; @RibbonLabel #RibbonText1 {} }
    @Ribbon #Ribbon2 { Background: "Mc/bookmark_ribbon_03.png"; @RibbonLabel #RibbonText2 {} }

    @Seal #Seal0 { @Slot = 0; Style: (Default: (Background: "Mc/red_wax_home.png"), Sounds: $C.@ButtonSounds); }
    @Seal #Seal1 { @Slot = 1; Style: (Default: (Background: "Mc/red_wax_citizens.png"), Sounds: $C.@ButtonSounds); }
    @Seal #Seal2 { @Slot = 2; Style: (Default: (Background: "Mc/red_wax_settings.png"), Sounds: $C.@ButtonSounds); }
  }
}

$C.@BackButton {}
```

- [ ] **Étape 4 : l'onglet Colonies (`Book/Colonies.ui`, `Book/ColonyRow.ui`)**

`Book/Colonies.ui` :

```
$B = "../Mc/Book.ui";
$C = "../../../Common.ui";

// HyLens' Colonies tab (spec 2026-10-02, § 3.2), on the town hall book's pages: on the left the world's colonies; on
// the right the chosen one's citizens and alerts, and the check.

Group {
  Anchor: (Left: 0, Top: 0, Width: 1048, Height: 486);

  Label {
    Anchor: (Left: 200, Top: 130, Width: 296, Height: 22);
    Text: %hylens.menu.colonies;
    Style: $B.@Heading;
  }

  Group #Colonies {
    Anchor: (Left: 200, Top: 160, Width: 296, Height: 278);
    Padding: (Left: 19);
    LayoutMode: TopScrolling;
    ScrollbarStyle: $C.@DefaultScrollbarStyle;
  }

  Label #ColonyName {
    Anchor: (Left: 552, Top: 56, Width: 296, Height: 22);
    Style: $B.@Heading;
  }

  Label #ColonyDetail {
    Anchor: (Left: 552, Top: 90, Width: 296, Height: 22);
    Style: $B.@InkCentered;
  }

  TextButton #CheckNowButton {
    Anchor: (Left: 571, Top: 130, Width: 258, Height: 34);
    Style: $B.@WideButtonStyle;
    Text: %hylens.menu.checkNow;
  }
}
```

`Book/ColonyRow.ui` :

```
$B = "../Mc/Book.ui";

TextButton #Button {
  Anchor: (Width: 258, Height: 34, Bottom: 4);
  Style: $B.@WideButtonStyle;
  Text: "";
}
```

- [ ] **Étape 5 : l'onglet Citoyens (`Book/Citizens.ui`, `Book/CitizenRow.ui`)**

`Book/Citizens.ui` :

```
$B = "../Mc/Book.ui";
$C = "../../../Common.ui";

// HyLens' Citizens tab (spec 2026-10-02, § 3.2-3.3), on the town hall book's pages: on the left the chosen colony's
// citizens; on the right the chosen citizen, its actions (Watch, or Free camera / Follow again and Stop watching while
// watched), and "send here".

Group {
  Anchor: (Left: 0, Top: 0, Width: 1048, Height: 486);

  Label {
    Anchor: (Left: 200, Top: 130, Width: 296, Height: 22);
    Text: %hylens.menu.citizens;
    Style: $B.@Heading;
  }

  Group #Citizens {
    Anchor: (Left: 200, Top: 160, Width: 296, Height: 278);
    LayoutMode: TopScrolling;
    ScrollbarStyle: $C.@DefaultScrollbarStyle;
  }

  Label #Chosen {
    Anchor: (Left: 552, Top: 56, Width: 296, Height: 22);
    Style: $B.@Heading;
  }

  Label #ChosenState {
    Anchor: (Left: 552, Top: 84, Width: 296, Height: 20);
    Style: $B.@FadedCentered;
  }

  TextButton #WatchButton {
    Anchor: (Left: 571, Top: 116, Width: 258, Height: 34);
    Style: $B.@WideButtonStyle;
    Text: %hylens.menu.watch;
  }

  TextButton #FreeButton {
    Anchor: (Left: 552, Top: 116, Width: 128, Height: 34);
    Style: $B.@SmallCompactButtonStyle;
    Text: "";
    Visible: false;
  }

  TextButton #UnwatchButton {
    Anchor: (Left: 720, Top: 116, Width: 128, Height: 34);
    Style: $B.@SmallCompactButtonStyle;
    Text: %hylens.menu.unwatch;
    Visible: false;
  }

  TextButton #LeisureButton {
    Anchor: (Left: 571, Top: 156, Width: 258, Height: 34);
    Style: $B.@WideButtonStyle;
    Text: %hylens.menu.leisure;
  }

  TextButton #TeleportButton {
    Anchor: (Left: 571, Top: 196, Width: 258, Height: 34);
    Style: $B.@WideButtonStyle;
    Text: %hylens.menu.teleport;
  }

  TextButton #RespawnButton {
    Anchor: (Left: 571, Top: 236, Width: 258, Height: 34);
    Style: $B.@WideButtonStyle;
    Text: %hylens.menu.respawn;
  }

  Label {
    Anchor: (Left: 552, Top: 284, Width: 296, Height: 22);
    Text: %hylens.menu.sendHere;
    Style: $B.@Heading;
  }

  $C.@TextField #SendX {
    @Anchor = (Left: 552, Top: 312, Width: 88, Height: 34);
  }

  $C.@TextField #SendY {
    @Anchor = (Left: 656, Top: 312, Width: 88, Height: 34);
  }

  $C.@TextField #SendZ {
    @Anchor = (Left: 760, Top: 312, Width: 88, Height: 34);
  }

  TextButton #SendButton {
    Anchor: (Left: 552, Top: 356, Width: 128, Height: 34);
    Style: $B.@SmallButtonStyle;
    Text: %hylens.menu.send;
  }

  TextButton #SendMapButton {
    Anchor: (Left: 720, Top: 356, Width: 128, Height: 34);
    Style: $B.@SmallCompactButtonStyle;
    Text: %hylens.menu.sendMap;
  }
}
```

`Book/CitizenRow.ui` (mêmes identifiants qu'avant : `#Name`, `#State`, `#Alerts`, `#Choose`) :

```
$B = "../Mc/Book.ui";

Group {
  LayoutMode: Left;
  Anchor: (Width: 290, Height: 40, Bottom: 2);

  Group {
    LayoutMode: Top;
    FlexWeight: 1;

    Label #Name {
      Anchor: (Height: 20);
      Style: (...$B.@Ink, FontSize: 14, RenderBold: true);
    }

    Label #State {
      Anchor: (Height: 18);
      Style: $B.@Faded;
    }
  }

  Label #Alerts {
    Anchor: (Width: 74);
    Style: $B.@Alert;
  }

  TextButton #Choose {
    Anchor: (Left: 4, Width: 28, Height: 30);
    Style: $B.@MiniButtonStyle;
    Text: ">";
  }
}
```

- [ ] **Étape 6 : l'onglet Vue (`Book/View.ui`, `Book/LayerButton.ui`)**

`Book/View.ui` :

```
$B = "../Mc/Book.ui";

// HyLens' View tab (spec 2026-10-02, § 3.2), on the town hall book's pages: on the left the drawings and the automatic
// check; on the right the colony clock.

Group {
  Anchor: (Left: 0, Top: 0, Width: 1048, Height: 486);

  Label {
    Anchor: (Left: 200, Top: 130, Width: 296, Height: 22);
    Text: %hylens.menu.layers;
    Style: $B.@Heading;
  }

  Group #Layers {
    Anchor: (Left: 200, Top: 160, Width: 296, Height: 162);
    Padding: (Left: 19);
    LayoutMode: Top;
  }

  Label {
    Anchor: (Left: 200, Top: 330, Width: 296, Height: 22);
    Text: %hylens.menu.checks;
    Style: $B.@Heading;
  }

  TextButton #AutoCheckButton {
    Anchor: (Left: 219, Top: 360, Width: 258, Height: 34);
    Style: $B.@WideButtonStyle;
    Text: "";
  }

  Label {
    Anchor: (Left: 552, Top: 56, Width: 296, Height: 22);
    Text: %hylens.menu.clock;
    Style: $B.@Heading;
  }

  Label #ClockState {
    Anchor: (Left: 552, Top: 90, Width: 296, Height: 22);
    Style: $B.@InkCentered;
  }

  TextButton #PauseButton {
    Anchor: (Left: 552, Top: 126, Width: 128, Height: 34);
    Style: $B.@SmallButtonStyle;
    Text: %hylens.menu.pause;
  }

  TextButton #ResumeButton {
    Anchor: (Left: 720, Top: 126, Width: 128, Height: 34);
    Style: $B.@SmallButtonStyle;
    Text: %hylens.menu.resume;
  }

  TextButton #StepLessButton {
    Anchor: (Left: 610, Top: 172, Width: 28, Height: 30);
    Style: $B.@MiniButtonStyle;
    Text: %hylens.menu.stepLess;
  }

  Label #StepCount {
    Anchor: (Left: 642, Top: 172, Width: 116, Height: 30);
    Style: $B.@InkCentered;
  }

  TextButton #StepMoreButton {
    Anchor: (Left: 762, Top: 172, Width: 28, Height: 30);
    Style: $B.@MiniButtonStyle;
    Text: %hylens.menu.stepMore;
  }

  TextButton #StepButton {
    Anchor: (Left: 571, Top: 214, Width: 258, Height: 34);
    Style: $B.@WideButtonStyle;
    Text: %hylens.menu.step;
  }
}
```

`Book/LayerButton.ui` :

```
$B = "../Mc/Book.ui";

TextButton #Button {
  Anchor: (Width: 258, Height: 34, Bottom: 6);
  Style: $B.@WideButtonStyle;
  Text: "";
}
```

Puis retirer les anciennes lignes :

```bash
git rm hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens/MenuColonyRow.ui hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens/MenuCitizenRow.ui hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens/MenuLayerButton.ui
```

- [ ] **Étape 7 : `MenuBinds`**

```java
package dev.hylens.plugin.command;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

/**
 * Binds the HyLens menu's buttons. Each click sends an "Action" and in "Index" the id of its colony or citizen, its
 * layer's or tab's name; on the Citizens tab it also carries the "send here" fields as "@X", "@Y" and "@Z", so the
 * redraw that follows keeps what was typed. Other tabs have no such fields, so their clicks carry none.
 */
record MenuBinds(UIEventBuilder events, boolean cell) {
    /** Binds the button at {@code selector} to {@code action} on {@code index}. */
    void on(String selector, String action, String index) {
        EventData data = EventData.of("Action", action).append("Index", index);
        if (cell) {
            data = data.append("@X", "#SendX.Value").append("@Y", "#SendY.Value").append("@Z", "#SendZ.Value");
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, data, false);
    }
}
```

- [ ] **Étape 8 : les trois rendus d'onglet**

`ColoniesTab.java` :

```java
package dev.hylens.plugin.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hylens.core.menu.MenuView;
import java.util.List;

/** Draws the HyLens menu's Colonies tab (spec 2026-10-02, § 3.2): the world's colonies, the chosen one, the check. */
final class ColoniesTab {
    static final String PAGE = "Pages/HyLens/Book/Colonies.ui";
    private static final String ROW = "Pages/HyLens/Book/ColonyRow.ui";

    private ColoniesTab() {}

    /** Fills the tab appended just before with {@code v}; binds every button. */
    static void render(UICommandBuilder ui, MenuBinds binds, MenuView v) {
        List<MenuView.ColonyRow> colonies = v.colonies();
        for (int i = 0; i < colonies.size(); i++) {
            MenuView.ColonyRow c = colonies.get(i);
            String row = "#Colonies[" + i + "]";
            ui.append("#Colonies", ROW);
            String key =
                    (c.chosen() ? "hylens.menu.colonyChosen" : "hylens.menu.colony") + (c.alerts() > 0 ? "Alerts" : "");
            ui.set(
                    row + ".Text",
                    Message.translation(key)
                            .param("p0", c.name())
                            .param("p1", String.valueOf(c.citizens()))
                            .param("p2", String.valueOf(c.alerts())));
            binds.on(row, "colony", String.valueOf(c.ref().colonyId()));
        }
        colonies.stream()
                .filter(MenuView.ColonyRow::chosen)
                .findFirst()
                .ifPresentOrElse(
                        c -> {
                            ui.set("#ColonyName.Text", c.name());
                            ui.set(
                                    "#ColonyDetail.TextSpans",
                                    Message.translation("hylens.menu.colonyDetail")
                                            .param("p0", String.valueOf(c.citizens()))
                                            .param("p1", String.valueOf(c.alerts())));
                        },
                        () -> ui.set("#ColonyDetail.TextSpans", Message.translation("hylens.menu.colonyNone")));
        binds.on("#CheckNowButton", "checkNow", "");
    }
}
```

`CitizensTab.java` :

```java
package dev.hylens.plugin.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hylens.core.menu.MenuView;
import dev.hylens.plugin.watch.ApiMessages;
import java.util.List;
import java.util.Optional;

/**
 * Draws the HyLens menu's Citizens tab (spec 2026-10-02, § 3.2-3.3): the chosen colony's citizens, the chosen one and
 * its actions, "send here". While the operator watches the chosen citizen, Watch gives way to Free camera (or Follow
 * again, when the camera is free) and Stop watching.
 */
final class CitizensTab {
    static final String PAGE = "Pages/HyLens/Book/Citizens.ui";
    private static final String ROW = "Pages/HyLens/Book/CitizenRow.ui";

    /** The "send here" fields to show, and whether the operator's camera is free of the body watched. */
    record Watch(MenuSend.Typed cell, boolean cameraFree) {}

    private CitizensTab() {}

    /** Fills the tab appended just before with {@code v} and {@code watch}; binds every button. */
    static void render(UICommandBuilder ui, MenuBinds binds, MenuView v, Watch watch) {
        List<MenuView.CitizenRow> citizens = v.citizens();
        for (int i = 0; i < citizens.size(); i++) {
            row(ui, binds, i, citizens.get(i));
        }
        Optional<MenuView.CitizenRow> chosen =
                citizens.stream().filter(MenuView.CitizenRow::chosen).findFirst();
        ui.set(
                "#Chosen.TextSpans",
                chosen.map(c -> Message.translation("hylens.menu.chosen").param("p0", c.name()))
                        .orElse(Message.translation("hylens.menu.noneChosen")));
        chosen.ifPresent(c -> ui.set("#ChosenState.TextSpans", state(c)));
        boolean watched = chosen.map(MenuView.CitizenRow::watched).orElse(false);
        ui.set("#WatchButton.Visible", !watched);
        ui.set("#FreeButton.Visible", watched);
        ui.set("#UnwatchButton.Visible", watched);
        ui.set(
                "#FreeButton.Text",
                Message.translation(watch.cameraFree() ? "hylens.menu.follow" : "hylens.menu.freeCamera"));
        binds.on("#WatchButton", "watch", "");
        binds.on("#FreeButton", watch.cameraFree() ? "follow" : "free", "");
        binds.on("#UnwatchButton", "unwatch", "");
        binds.on("#LeisureButton", "leisure", "");
        binds.on("#TeleportButton", "teleport", "");
        binds.on("#RespawnButton", "respawn", "");
        ui.set("#SendX.Value", watch.cell().x());
        ui.set("#SendY.Value", watch.cell().y());
        ui.set("#SendZ.Value", watch.cell().z());
        binds.on("#SendButton", "send", "");
        binds.on("#SendMapButton", "sendMap", "");
    }

    private static Message state(MenuView.CitizenRow c) {
        return Message.translation("hylens.menu.rowState")
                .param("p0", ApiMessages.of(c.job()))
                .param("p1", c.ai())
                .param("p2", c.step());
    }

    private static void row(UICommandBuilder ui, MenuBinds binds, int i, MenuView.CitizenRow c) {
        String row = "#Citizens[" + i + "]";
        ui.append("#Citizens", ROW);
        String name = c.watched() ? "hylens.menu.rowWatched" : c.chosen() ? "hylens.menu.rowChosen" : "hylens.menu.row";
        ui.set(row + " #Name.TextSpans", Message.translation(name).param("p0", c.name()));
        ui.set(row + " #State.TextSpans", state(c));
        if (c.alerts() > 0) {
            ui.set(
                    row + " #Alerts.TextSpans",
                    Message.translation("hylens.menu.rowAlerts").param("p0", String.valueOf(c.alerts())));
        }
        binds.on(row + " #Choose", "citizen", String.valueOf(c.ref().citizenId()));
    }
}
```

`ViewTab.java` :

```java
package dev.hylens.plugin.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hylens.core.draw.Layers;
import dev.hylens.core.menu.MenuView;
import java.util.Locale;

/** Draws the HyLens menu's View tab (spec 2026-10-02, § 3.2): the layers, the automatic check, the colony clock. */
final class ViewTab {
    static final String PAGE = "Pages/HyLens/Book/View.ui";
    private static final String LAYER_BUTTON = "Pages/HyLens/Book/LayerButton.ui";

    private ViewTab() {}

    /** Fills the tab appended just before with {@code v}; binds every button. */
    static void render(UICommandBuilder ui, MenuBinds binds, MenuView v) {
        layers(ui, binds, v.layers());
        ui.set(
                "#AutoCheckButton.Text",
                Message.translation(v.autoCheck() ? "hylens.menu.autoCheck.on" : "hylens.menu.autoCheck.off"));
        binds.on("#AutoCheckButton", "autoCheck", "");
        ui.set(
                "#ClockState.TextSpans",
                Message.translation(v.paused() ? "hylens.menu.clockPaused" : "hylens.menu.clockRunning"));
        ui.set(
                "#StepCount.TextSpans",
                Message.translation("hylens.menu.stepCount").param("p0", String.valueOf(v.step())));
        binds.on("#PauseButton", "pause", "");
        binds.on("#StepLessButton", "stepLess", "");
        binds.on("#StepMoreButton", "stepMore", "");
        binds.on("#StepButton", "step", "");
        binds.on("#ResumeButton", "resume", "");
    }

    /** One button per layer, its text saying whether the layer shows: one complete key per state (CLAUDE.md § 7). */
    private static void layers(UICommandBuilder ui, MenuBinds binds, Layers layers) {
        Layers.Layer[] all = Layers.Layer.values();
        for (int i = 0; i < all.length; i++) {
            Layers.Layer layer = all[i];
            String button = "#Layers[" + i + "]";
            ui.append("#Layers", LAYER_BUTTON);
            String key = "hylens.menu.layer." + layer.name().toLowerCase(Locale.ROOT)
                    + (layers.shows(layer) ? ".on" : ".off");
            ui.set(button + ".Text", Message.translation(key));
            binds.on(button, "layer", layer.name());
        }
    }
}
```

- [ ] **Étape 9 : `MenuRender` (réécrit en cadre)**

```java
package dev.hylens.plugin.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.api.ApiText;
import dev.hylens.core.menu.MenuTab;
import dev.hylens.core.menu.MenuView;
import dev.hylens.plugin.watch.ApiMessages;
import java.util.Optional;

/**
 * Draws the HyLens menu as MC's town hall book (spec 2026-10-02, § 3.1): the open tab's page, its long ribbon, the
 * other tabs' short ribbons and wax seals (MC AbstractWindowTownHall), and the last action's result. Each tab draws
 * its own page ({@link ColoniesTab}, {@link CitizensTab}, {@link ViewTab}). An id in a button's "Index" still names
 * the same row once the list changed, unless HyColony gave a dead citizen's id to a newcomer in between (colony ids
 * are never reused).
 */
final class MenuRender {
    static final String PAGE = "Pages/HyLens/Menu.ui";

    private MenuRender() {}

    /**
     * Appends {@code v}'s open tab into the book appended just before, fills it with {@code v} and {@code watch}, shows
     * the last {@code result}, and binds every button and seal.
     */
    static void render(
            UICommandBuilder ui,
            UIEventBuilder events,
            MenuView v,
            Optional<ApiText> result,
            CitizensTab.Watch watch) {
        MenuBinds binds = new MenuBinds(events, v.tab() == MenuTab.CITIZENS);
        switch (v.tab()) {
            case COLONIES -> {
                ui.append("#Page", ColoniesTab.PAGE);
                ColoniesTab.render(ui, binds, v);
            }
            case CITIZENS -> {
                ui.append("#Page", CitizensTab.PAGE);
                CitizensTab.render(ui, binds, v, watch);
            }
            case VIEW -> {
                ui.append("#Page", ViewTab.PAGE);
                ViewTab.render(ui, binds, v);
            }
        }
        bookmarks(ui, binds, v.tab());
        result.ifPresent(r -> ui.set("#Result.TextSpans", ApiMessages.of(r)));
    }

    /** Shows {@code text} alone, on a book that has nothing else to show. */
    static void only(UICommandBuilder ui, ApiText text) {
        ui.set("#Result.TextSpans", ApiMessages.of(text));
    }

    /** Shows the open tab's long ribbon, and the others' short ribbon and wax seal; a seal click opens its tab. */
    private static void bookmarks(UICommandBuilder ui, MenuBinds binds, MenuTab open) {
        for (MenuTab t : MenuTab.values()) {
            int slot = t.ordinal();
            boolean shown = t == open;
            ui.set("#Ribbon" + slot + ".Visible", shown);
            ui.set("#Mark" + slot + ".Visible", !shown);
            ui.set("#Seal" + slot + ".Visible", !shown);
            ui.set("#RibbonText" + slot + ".TextSpans", Message.translation(name(t)));
            ui.set("#Seal" + slot + ".TooltipText", Message.translation(name(t)));
            if (!shown) {
                binds.on("#Seal" + slot, "tab", t.name());
            }
        }
    }

    private static String name(MenuTab t) {
        return switch (t) {
            case COLONIES -> "hylens.menu.colonies";
            case CITIZENS -> "hylens.menu.citizens";
            case VIEW -> "hylens.menu.view";
        };
    }
}
```

- [ ] **Étape 10 : `MenuClicks` et `MenuPage`**

`MenuClicks.java` : `CHOICES` gagne `"tab"` ; ajouter, sur le modèle de `layer` :

```java
    /** The clicks on the watch: start it, free or follow its camera, stop it. */
    static final Set<String> WATCH = Set.of("watch", "free", "follow", "unwatch");

    /** The tab named {@code name}, if it is one. */
    static Optional<MenuTab> tab(String name) {
        try {
            return Optional.of(MenuTab.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
```

`MenuPage.java`, dans `choose` :

```java
            case "tab" -> MenuClicks.tab(index).ifPresent(t -> menus.update(operator, s -> s.withTab(t)));
```

et dans `build`, l'appel devient (`camera` sera branché à la tâche 3 ; ici `false`) :

```java
                        v -> MenuRender.render(
                                ui,
                                events,
                                v,
                                result,
                                new CitizensTab.Watch(send.fields(MenuActions.feet(ref, store)), false)),
```

La Javadoc de `choose` cite « a tab ».

- [ ] **Étape 11 : les textes**

`en-US/hylens.lang` : retirer `menu.title` (plus employé) ; ajouter :

```
menu.view = View
menu.colonyNone = Choose a colony.
menu.colonyDetail = {p0} citizen(s), {p1} alert(s)
```

`fr-FR/hylens.lang` : retirer `menu.title` ; ajouter :

```
menu.view = Vue
menu.colonyNone = Choisissez une colonie.
menu.colonyDetail = {p0} citoyen(s), {p1} alerte(s)
```

Vérifier qu'aucune source n'emploie plus `hylens.menu.title` : `grep -rn "menu.title" hylens --include=*.java --include=*.ui`. Attendu : rien.

- [ ] **Étape 12 : crédits**

`NOTICE` (racine) :

```
HyColony
Licensed under the GNU General Public License v3.0 (see LICENSE).

Game mechanics are ported from MineColonies (https://github.com/ldtteam/minecolonies), by ldtteam, GPL-3.0.

Textures from MineColonies (ldtteam, GPL-3.0, src/main/resources/assets/minecolonies/textures/gui/) are used in a
modified form: scaled x4 nearest-neighbour into @2x files (2026). They live in:
- plugin/src/main/resources/Common/UI/Custom/Pages/HyColony/Mc/ (HyColony)
- hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens/Mc/ (HyLens)
```

`README.md:14` devient :

```
- License: GPL-3.0. Game mechanics are ported from [MineColonies](https://github.com/ldtteam/minecolonies) (GPL-3.0), and its window textures are used, scaled x4 (see `NOTICE`).
```

- [ ] **Étape 13 : essais en jeu**

À la fin de `docs/TESTING.md`, une section `## HyLens en livre de MineColonies`, numérotée à la suite du dernier essai **au moment du commit** (une autre session ajoute des essais) :

- **Le livre.** `/hylens menu` : le livre de la mairie, ruban long « Colonies » ouvert, sceaux Citoyens et Vue à gauche, bulles au survol. Cliquer un sceau ouvre son onglet.
- **Colonies.** La liste des colonies ; en choisir une ouvre l'onglet Citoyens. Revenir à Colonies : la colonie choisie, son nombre de citoyens et d'alertes. « Contrôler maintenant » écrit le résumé sous la page de droite.
- **Citoyens.** Choisir un citoyen : son nom, son état, ses actions. Loisir, Téléporter, Refaire le corps marchent comme avant ; le résultat s'écrit sous la page de droite.
- **La case gardée.** Taper X Y Z dans « Envoyer ici », ouvrir l'onglet Vue, revenir à Citoyens : la case tapée est toujours là. « Envoyer » envoie le citoyen ; « Par la carte » arme la carte et ferme le livre.
- **Vue.** Les quatre dessins se cochent ; « Contrôler toutes les 2 s » se coche ; Pause, -, +, Avancer, Reprendre pilotent l'horloge comme avant.
- **Le menu se souvient.** Fermer le livre sur l'onglet Vue, le rouvrir : il s'ouvre sur Vue.

- [ ] **Étape 14 : build et commit**

Run : `./gradlew build`
Attendu : BUILD SUCCESSFUL (dont `checkLangParity`, `checkFileSizes`, `spotlessCheck`).

```bash
git add hylens/plugin/src/main/resources/Common/UI/Custom/Pages/HyLens hylens/plugin/src/main/java/dev/hylens/plugin/command/MenuBinds.java hylens/plugin/src/main/java/dev/hylens/plugin/command/ColoniesTab.java hylens/plugin/src/main/java/dev/hylens/plugin/command/CitizensTab.java hylens/plugin/src/main/java/dev/hylens/plugin/command/ViewTab.java hylens/plugin/src/main/java/dev/hylens/plugin/command/MenuRender.java hylens/plugin/src/main/java/dev/hylens/plugin/command/MenuClicks.java hylens/plugin/src/main/java/dev/hylens/plugin/command/MenuPage.java hylens/plugin/src/main/resources/Server/Languages/en-US/hylens.lang hylens/plugin/src/main/resources/Server/Languages/fr-FR/hylens.lang NOTICE README.md docs/TESTING.md
git diff --cached --name-only
git commit -m "feat(hylens-plugin): the menu as MC's town hall book, with Colonies, Citizens and View tabs"
```

(`git add` d'un dossier est permis par le hook : seuls `-A`, `-u`, `.`, `:/` et `*` sont refusés. Si `docs/TESTING.md` a aussi des changements d'une autre session non indexés, n'indexer que nos lignes par `git add -p` n'est pas possible ici (interactif) : attendre que l'autre session ait commité, ou commiter TESTING.md à part ensuite.)

---

### Tâche 3 : la caméra libre

**Files :**
- Modify : `hylens/plugin/src/main/java/dev/hylens/plugin/command/CitizenWatch.java`
- Create : `hylens/plugin/src/main/java/dev/hylens/plugin/command/MenuWatchClicks.java`
- Modify : `hylens/plugin/src/main/java/dev/hylens/plugin/command/MenuPage.java` (`closing`, `handleDataEvent`, `build` ; `startWatch` part dans `MenuWatchClicks`)
- Modify : `hylens/plugin/src/main/java/dev/hylens/plugin/command/UnwatchCommand.java`, `HyLensCommand.java:34`
- Modify : `hylens.lang` (en-US, fr-FR), `docs/TESTING.md`

**Interfaces :**
- Consumes : `CitizensTab.Watch(MenuSend.Typed cell, boolean cameraFree)`, `MenuClicks.WATCH` (tâche 2).
- Produces : `CitizenWatch.free(PlayerRef, Store<EntityStore>, Ref<EntityStore>) : boolean`, `CitizenWatch.follow(PlayerRef, Store<EntityStore>, Ref<EntityStore>, CitizenRef, String) : boolean`, `CitizenWatch.stop(PlayerRef, Store<EntityStore>, Ref<EntityStore>) : void`, `CitizenWatch.cameraFree(Store<EntityStore>, Ref<EntityStore>) : boolean`.

Vérifié dans les sources décompilées (0.7.0-pre.5) : `Spectating()` sans cible et `getTargetRef()` (`server/core/modules/entity/component/Spectating.java:20-34`) ; `SpectateControlInteraction.java:49-52` (Detach) ; `SpectatorSystems.OnSpectatingChange.onComponentSet` (l. 221-237) : une cible nulle appelle `moveToCameraExitPosition` puis `applyFreeCamera`, une cible posée appelle `applyFollowCamera`, qui téléporte l'opérateur sur elle.

- [ ] **Étape 1 : `CitizenWatch`**

Extraire la recherche du corps de `start` dans une méthode privée, et ajouter :

```java
    /**
     * On the world's thread: the operator at {@code ref}'s camera leaves the body watched, as Hytale's SpectateControl
     * Detach (SpectateControlInteraction:49-52); they stay in the spectator mode, and the watch, its history, HUD and
     * drawings go on. False, and they are told, when they are not spectating.
     */
    boolean free(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref) {
        if (!Spectating.isSpectating(ref, store)) {
            Chat.tell(player, "hylens.watch.notWatching");
            return false;
        }
        store.putComponent(ref, Spectating.getComponentType(), new Spectating());
        Chat.tell(player, "hylens.watch.freed");
        return true;
    }

    /**
     * On the world's thread: the operator at {@code ref}'s camera follows {@code citizen}'s body again, named
     * {@code name}, its watch and history kept; refused, and they are told, when not spectating or when the body is
     * refused as {@link #start} refuses it. True once following.
     */
    boolean follow(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref, CitizenRef citizen, String name) {
        if (!Spectating.isSpectating(ref, store)) {
            Chat.tell(player, "hylens.watch.notWatching");
            return false;
        }
        Optional<Ref<EntityStore>> body = body(store, citizen);
        if (body.isEmpty()) {
            Chat.tell(player, "hylens.watch.noBody", name);
            return false;
        }
        store.putComponent(ref, Spectating.getComponentType(), new Spectating(body.get()));
        Chat.tell(player, "hylens.watch.followed", name);
        return true;
    }

    /**
     * Stops {@code player}'s watch and its history and leaves the spectator mode, even with no watch held, since the
     * mode is saved with the player and outlives a server restart; tells them which.
     */
    void stop(PlayerRef player, Store<EntityStore> store, Ref<EntityStore> ref) {
        boolean watched = watches.stop(player.getUuid()).isPresent();
        boolean left = Spectating.isSpectating(ref, store) && GameModeTypes.exit(ref, store);
        Chat.tell(player, watched || left ? "hylens.watch.stopped" : "hylens.watch.notWatching");
    }

    /** Whether the operator at {@code ref} spectates with a camera free of any body. */
    boolean cameraFree(Store<EntityStore> store, Ref<EntityStore> ref) {
        Spectating s = store.getComponent(ref, Spectating.getComponentType());
        return s != null && s.getTargetRef() == null;
    }

    /** {@code citizen}'s body when loaded in {@code store} and not dying (FollowTarget would drop it, SpectateCommand:94). */
    private static Optional<Ref<EntityStore>> body(Store<EntityStore> store, CitizenRef citizen) {
        return HyColonyApi.get()
                .bodyOf(citizen)
                .filter(b -> b.isValid()
                        && store.equals(b.getStore())
                        && !store.getArchetype(b).contains(DeathComponent.getComponentType()));
    }
```

`start` emploie `body(store, citizen)` à la place de son filtre. `UnwatchCommand` prend `CitizenWatch` au lieu de `Watches` et son `execute` devient `watch.stop(player, store, ref);` ; `HyLensCommand.java:34` : `addSubCommand(new UnwatchCommand(watch));`.

- [ ] **Étape 2 : `MenuWatchClicks`**

```java
package dev.hylens.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.ApiText;
import dev.hylens.core.menu.MenuView;
import dev.hylens.core.watch.Watches;
import java.util.Optional;

/**
 * The HyLens menu's watch clicks (spec 2026-10-02, § 3.3): watch the chosen citizen, free or follow the camera, stop.
 * Watching or moving the camera closes the menu so the operator sees the world; stopping keeps it open.
 */
final class MenuWatchClicks {
    /** What a click asks of the page: to close, and the text to show if it stays open. */
    record Outcome(boolean close, Optional<ApiText> result) {}

    private final CitizenWatch watch;
    private final Watches watches;

    MenuWatchClicks(CitizenWatch watch, Watches watches) {
        this.watch = watch;
        this.watches = watches;
    }

    /** On the world's thread: runs the watch click {@code action} of {@code player}, at {@code ref}, on {@code v}. */
    Outcome run(String action, PlayerRef player, MenuView v, Ref<EntityStore> ref, Store<EntityStore> store) {
        Optional<MenuView.CitizenRow> chosen =
                v.citizens().stream().filter(MenuView.CitizenRow::chosen).findFirst();
        return switch (action) {
            case "free" -> new Outcome(watch.free(player, store, ref), Optional.empty());
            case "unwatch" -> {
                watch.stop(player, store, ref);
                yield new Outcome(false, Optional.empty());
            }
            default -> chosen.map(c -> start(action, player, c, ref, store))
                    .orElse(new Outcome(false, Optional.of(ApiText.of("hylens.action.noneChosen"))));
        };
    }

    /**
     * "watch" starts watching {@code c}, "follow" puts the camera back on it; closes once it follows. {@code start}
     * tells no result, so the watch held afterwards says whether it started (as MenuPage.startWatch did).
     */
    private Outcome start(
            String action, PlayerRef player, MenuView.CitizenRow c, Ref<EntityStore> ref, Store<EntityStore> store) {
        if ("follow".equals(action)) {
            return new Outcome(watch.follow(player, store, ref, c.ref(), c.name()), Optional.empty());
        }
        watch.start(player, store, ref, c.ref(), c.name());
        return new Outcome(watches.watched(player.getUuid()).equals(Optional.of(c.ref())), Optional.empty());
    }
}
```

- [ ] **Étape 3 : `MenuPage`**

- champ `private final MenuWatchClicks watchClicks;`, construit par `new MenuWatchClicks(watch, parts.watches())` ;
- `closing` :

```java
    /**
     * Handles the clicks that may close the page: "sendMap" arms the map and closes it so the operator can open the
     * map; a watch click ({@link MenuWatchClicks}) closes it once the camera follows or is free. True once closed.
     */
    private boolean closing(String action, MenuView v, Ref<EntityStore> ref, Store<EntityStore> store) {
        if ("sendMap".equals(action)) {
            send.byMap(playerRef);
            close();
            return true;
        }
        if (!MenuClicks.WATCH.contains(action)) {
            return false;
        }
        MenuWatchClicks.Outcome o = watchClicks.run(action, playerRef, v, ref, store);
        o.result().ifPresent(r -> result = Optional.of(r));
        if (o.close()) {
            close();
        }
        return o.close();
    }
```

- dans `handleDataEvent`, `else if (!"watch".equals(data.action))` devient `else if (!MenuClicks.WATCH.contains(data.action))` ;
- `startWatch` est supprimé (il vit dans `MenuWatchClicks`) ;
- dans `build`, `false` devient `watch.cameraFree(store, ref)`.

- [ ] **Étape 4 : les textes**

en-US :

```
menu.freeCamera = Free camera
menu.follow = Follow again
menu.unwatch = Stop watching
watch.freed = Free camera: the watch goes on. /hylens menu to follow again.
watch.followed = Following {p0} again.
```

fr-FR :

```
menu.freeCamera = Caméra libre
menu.follow = Reprendre le suivi
menu.unwatch = Arrêter le suivi
watch.freed = Caméra libre : le suivi continue. /hylens menu pour le reprendre.
watch.followed = Vous suivez de nouveau {p0}.
```

- [ ] **Étape 5 : essais en jeu** (à la suite de la section de la tâche 2)

- **Caméra libre.** Suivre un citoyen depuis le menu, rouvrir `/hylens menu` : à la place de « Suivre », « Caméra libre » et « Arrêter le suivi ». « Caméra libre » ferme le livre : on vole et traverse les blocs, le HUD et les dessins continuent, message « Caméra libre… ».
- **Reprendre.** `/hylens menu` : le bouton dit « Reprendre le suivi » ; il ferme le livre et la caméra revient derrière le citoyen.
- **Corps perdu.** En caméra libre, s'éloigner jusqu'à décharger le citoyen (ou le tuer) : « Reprendre le suivi » refuse avec « Le corps de … n'est pas chargé… », le livre reste ouvert, le HUD reste.
- **Autre citoyen.** En caméra libre, choisir un autre citoyen et « Suivre » : la caméra le suit.
- **Arrêter.** « Arrêter le suivi » : retour au mode de jeu normal, HUD retiré, le livre reste ouvert et montre « Suivre ». `/hylens unwatch` fait toujours de même.
- **Déconnexion.** Se déconnecter en caméra libre, se reconnecter : mode de jeu normal.

- [ ] **Étape 6 : build et commit**

Run : `./gradlew build` → BUILD SUCCESSFUL.

```bash
git add hylens/plugin/src/main/java/dev/hylens/plugin/command/CitizenWatch.java hylens/plugin/src/main/java/dev/hylens/plugin/command/MenuWatchClicks.java hylens/plugin/src/main/java/dev/hylens/plugin/command/MenuPage.java hylens/plugin/src/main/java/dev/hylens/plugin/command/UnwatchCommand.java hylens/plugin/src/main/java/dev/hylens/plugin/command/HyLensCommand.java hylens/plugin/src/main/resources/Server/Languages/en-US/hylens.lang hylens/plugin/src/main/resources/Server/Languages/fr-FR/hylens.lang docs/TESTING.md
git diff --cached --name-only
git commit -m "feat(hylens-plugin): free the camera while watching a citizen, and follow it again, from the menu"
```

---

### Tâche 4 : le HUD sur le parchemin de la fenêtre de débogage de MC

**Files :**
- Modify : `hylens/plugin/src/main/resources/Common/UI/Custom/Hud/HyLens/WatchHud.ui`, `WatchHudLine.ui`
- Modify : `docs/TESTING.md`

- [ ] **Étape 1 : `WatchHud.ui`**

```
// The watch panel as MC's citizen debug window (gui/citizen/debug.xml, DebugWindowCitizen), MC positions and sizes
// doubled (spec 2026-10-02, § 3.4): builder_paper_wide2 (MineColonies', ldtteam, GPL-3, scaled x4 nearest-neighbour),
// its lines in black ink from MC's first text, at (10, 15).
Group {
  Anchor: (Right: 20, Top: 80, Width: 800, Height: 488);
  Background: "../../Pages/HyLens/Mc/builder_paper_wide2.png";

  Group #Lines {
    Anchor: (Left: 20, Top: 30, Width: 760, Height: 428);
    LayoutMode: Top;
  }
}
```

- [ ] **Étape 2 : `WatchHudLine.ui`**

```
Group {
  Anchor: (Height: 20);

  Label #Text {
    Style: (FontSize: 14, TextColor: #000000, VerticalAlignment: Center);
  }
}
```

- [ ] **Étape 3 : essai en jeu**

- **HUD sur parchemin.** Suivre un citoyen : le panneau est le parchemin large de MC à droite de l'écran, ses lignes à l'encre noire et lisibles. Si le parchemin ne s'affiche pas (chemin vers un autre dossier du pack), copier `builder_paper_wide2@2x.png` sous `Hud/HyLens/` et pointer dessus (spec § 7).

- [ ] **Étape 4 : build et commit**

Run : `./gradlew build` → BUILD SUCCESSFUL.

```bash
git add hylens/plugin/src/main/resources/Common/UI/Custom/Hud/HyLens/WatchHud.ui hylens/plugin/src/main/resources/Common/UI/Custom/Hud/HyLens/WatchHudLine.ui docs/TESTING.md
git commit -m "feat(hylens-plugin): the watch panel on MC's citizen debug window parchment"
```

---

### Tâche 5 : relectures et règle du § 7

- [ ] **Étape 1 :** relecture indépendante de la plage de commits par `hycolony-reviewer` et `ui-lang-checker` ; corriger, puis faire relire les corrections.
- [ ] **Étape 2 :** demander à l'utilisateur d'écrire dans `CLAUDE.md` § 7 (garde-fou) : « Les autres mods, et les contrôles sans équivalent chez MC, copient les motifs vanilla » devient « HyDomum, HyVanilla, HyBlockUI, et les contrôles sans équivalent chez MC, copient les motifs vanilla. HyLens reprend aussi l'apparence de MineColonies : le livre de la mairie et ses boutons. » (ou relancer la session avec `HYCOLONY_GUARDRAILS_UNLOCKED=1`).
- [ ] **Étape 3 :** feu vert à l'utilisateur pour les essais en jeu de `docs/TESTING.md`.
