# Domum Ornamentum DO-1 « les blocs » : plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** régénérer le sous-plugin `DomumOrnamentum` pour que chaque famille DO (hors lumières, briques, blocs « extra », tonneaux, tapis) se comporte comme dans DO : orientation, formes selon les voisins, portes et trappes qui s'ouvrent, rendu propre, onglet créatif.

**Architecture :** le générateur Python `tools/domum/` est piloté par les **blockstates DO**. Une table de familles (`families.py`) dit, pour chaque bloc DO, ses composants, son orientation de base, son groupe du cutter et le mécanisme Hytale qui le porte. `assemble` produit la géométrie d'un état, `faces` la nettoie, `convert` écrit le `.blockymodel` et l'atlas, `blocks/*` écrit les `BlockType` en copiant les motifs vanilla (toit, porte, trappe, gabarits de connexion), `icons` et `tabs` finissent le pack. Aucun code Java.

**Tech Stack :** Python 3.10+, Pillow (déjà utilisé par `tools/decorations`), assets Hytale 0.6.8 (`server/Assets.zip` ou `~/.gradle/caches/hytale-assets/release-0.6.8-Assets.zip`), Gradle (`checkSubpluginAssets`).

**Spec :** `docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do1-design.md`. Faits : `docs/research/domum-ornamentum.md` (A.1-A.3 DO, B.9 défauts du banc, B.10 mécanismes Hytale).

## Global Constraints

- Lire `CLAUDE.md` en entier avant de commencer. Réponses et docs en français, code et commentaires en anglais.
- Pas de nouvelle dépendance Python : stdlib + Pillow.
- Sorties générées **commitées** dans `plugin/src/subplugins/DomumOrnamentum/` ; le build ne lance jamais le générateur.
- Sous-plugin **`EnabledByDefault: false`**.
- Ids d'objets : `HyColony_DO_<Famille>_<Variante>` ; clés de traduction `hycolony.item.do.<...>.name` présentes en **en-US et fr-FR**.
- Chemins Common sous les racines de `CommonAssetValidator` (`pack.py` : `ICON_ROOTS`, `MODEL_ROOTS`, `TEXTURE_ROOTS`) ; icônes de catégories sous `Icons/ItemCategories/`.
- Matériaux : 1ᵉʳ composant DO → `BlockTextures/Wood_Darkwood_Planks.png`, 2ᵉ → `BlockTextures/Wood_Lightwood_Planks.png` ; famille à un composant → Darkwood.
- Commit DO épinglé : `82729d6c9dc0499b256b36b4506d0d9ef20e8aec`.
- Écarts à MC : commentaire `Deviation from MC: …` dans le générateur **et** dans la spec (déjà listés : colombage dynamique → `framed`).
- `./gradlew build` vert avant chaque commit ; aucune entrée ajoutée aux listes d'exceptions ; `git add` explicite ; messages `type(scope): description` + ligne `Co-Authored-By` de la session.
- On ne lance jamais le serveur Hytale.

## Review Focus

1. **Bloc DO posé à côté d'un bloc vanilla ou d'une autre famille DO** : les connexions (bardeaux, pilier, murs de papier, demi-bardeau) ne doivent se former qu'avec la même famille (ou ce que DO accepte), jamais avec un toit vanilla. Test : chaque gabarit généré ne cite que des ids `HyColony_DO_*` (Task 11).
2. **Porte cassée par sa moitié haute** : pas de filler orphelin. Test : la porte déclare la même hitbox 2 blocs que la porte vanilla, donc le même mécanisme de filler (Task 8).
3. **Pose dans les 4 directions et à l'envers** : chaque famille orientable a la bonne `VariantRotation` et son modèle de base tourné vers -Z. Test d'orientation par famille (Task 3).
4. **Cache DO partiel** (téléchargement interrompu) : le générateur ne doit pas produire un pack à moitié. Test : sans marqueur `.complete`, le cache est retéléchargé (Task 1).
5. **Pack désactivé avec des blocs DO déjà posés** : bloc « inconnu », pas de plantage. Pas testable hors ligne : étape `docs/TESTING.md` (Task 12).

---

## Structure des fichiers

| Fichier | Responsabilité |
|---|---|
| `tools/domum/source.py` (modifié) | Téléchargement au commit épinglé des modèles **et** blockstates ; résolution des parents |
| `tools/domum/families.py` (nouveau) | Table des familles DO : blocs, composants, orientation de base, groupe du cutter, mécanisme |
| `tools/domum/assemble.py` (nouveau) | Géométrie MC d'un état de blockstate : variante ou multipart, rotations `x`/`y`, normalisation vers -Z |
| `tools/domum/faces.py` (nouveau) | Nettoyage : faces superposées, faces presque dans le même plan, faces de bout |
| `tools/domum/convert.py` (modifié) | `.blockymodel` + atlas ; matériau par composant |
| `tools/domum/blocks/common.py` (nouveau) | `BlockType` de base (sons, particules, modèle, icône, catégorie), hitbox calculée |
| `tools/domum/blocks/static.py` (nouveau) | Familles orientables sans état : colombage, poteau, panneau, all brick |
| `tools/domum/blocks/roof.py` (nouveau) | Bardeaux (règle `Roof`) et demi-bardeau (gabarit 6 formes) |
| `tools/domum/blocks/pillar.py` (nouveau) | Pilier (gabarit pilier + `Full`) |
| `tools/domum/blocks/pane.py` (nouveau) | Murs de papier (gabarit vitre) |
| `tools/domum/blocks/door.py` (nouveau) | Portes et trappes (mécanique vanilla) |
| `tools/domum/blocks/vanilla.py` (nouveau) | Clôture, portillon, muret, escalier, dalle sur modèles Hytale vanilla |
| `tools/domum/icon.py` (modifié) | Rendu sur le modèle final, caméra `IconProperties` vanilla |
| `tools/domum/tabs.py` (nouveau) | Onglet `CreativeLibrary`, sous-catégories, icônes de catégories, traductions |
| `tools/domum/names.py` (modifié) | Noms en-US / fr-FR par famille et variante |
| `tools/domum/validate.py` (nouveau) | Contrôles croisés du pack (états, gabarits, icônes, catégories, clés) |
| `tools/domum/generate.py` (modifié) | Orchestration |
| `tools/domum/check.py` (modifié) | Auto-contrôle à assertions, hors ligne |
| `tools/decorations/pack.py` (modifié) | Docstring : helpers partagés par les deux générateurs |
| `docs/TESTING.md` (modifié) | Étape en jeu DO-1 |

Chaque fichier Python reste sous 300 lignes, chaque fonction sous 40 lignes, avec une docstring courte.

---

### Task 1 : blockstates DO et table des familles

**Files :**
- Modify : `tools/domum/source.py`
- Create : `tools/domum/families.py`
- Test : `tools/domum/check.py`

**Interfaces :**
- Produces : `source.fetch() -> Path` (télécharge `models/block/**` **et** `blockstates/**`, de `src/main/resources` et `src/datagen/generated`, sous `CACHE/models/` et `CACHE/blockstates/` ; marqueur `.complete` écrit en dernier), `source.blockstate(root, block_id) -> dict`, `source.load(root, name) -> {"textures", "elements"}` (inchangé).
- Produces : `families.Family` (dataclass figée) : `name: str` (ex. `"TimberFrame"`), `group: str` (groupe du cutter, ex. `"btimberframe"`), `blocks: tuple[str, ...]` (ids DO, ex. `"framed"`), `components: tuple[str, ...]` (textures placeholder dans l'ordre DO, ex. `("block/oak_planks", "block/dark_oak_planks")`), `base_y: int` (rotation `y` de la variante `facing=north`, 0/90/180/270), `mechanism: str` (`"static"`, `"roof"`, `"shingle_slab"`, `"pillar"`, `"pane"`, `"door"`, `"trapdoor"`, `"vanilla"`).
- Produces : `families.FAMILIES: tuple[Family, ...]`, `families.material(family, texture) -> "dark" | "light"`.

- [ ] **Step 1 : lire les sources DO.** Pour chaque famille de la spec, ouvrir au commit épinglé la classe du bloc (chemins en A.1) pour relever l'ordre des `SimpleRetexturableComponent` (leur id = texture placeholder), et le blockstate généré pour relever `y` de `facing=north` (B.9 § 8 : bardeaux et portes 270, trappes et panneaux 180). Colombage dynamique, lumières, briques, « extra », tonneaux, tapis : absents de la table (spec « Hors DO-1 »).

- [ ] **Step 2 : écrire les tests qui échouent** dans `check.py` :

```python
from families import FAMILIES, material


def families_cover_the_spec():
    names = {f.name for f in FAMILIES}
    assert names == {"TimberFrame", "Shingle", "ShingleSlab", "Pillar", "Post", "Panel", "Door", "FancyDoor",
                     "Trapdoor", "FancyTrapdoor", "PaperWall", "Fence", "FenceGate", "Wall", "Stairs", "Slab",
                     "AllBrick"}, names
    assert not any("dynamic" in b or "light" in b for f in FAMILIES for b in f.blocks)


def material_follows_component_order():
    timber = next(f for f in FAMILIES if f.name == "TimberFrame")
    assert material(timber, timber.components[0]) == "dark"
    assert material(timber, timber.components[1]) == "light"
    post = next(f for f in FAMILIES if f.name == "Post")
    assert len(post.components) == 1 and material(post, "anything/else") == "dark"


def partial_cache_is_refetched(tmp):
    # Review Focus 4: a cache without its .complete marker is never trusted.
    (tmp / "models").mkdir(parents=True)
    assert not source.is_complete(tmp)
```

- [ ] **Step 3 : lancer** `python tools/domum/check.py` → FAIL (`ModuleNotFoundError: families`).
- [ ] **Step 4 : implémenter** `families.py` (table littérale, une entrée par famille, sources DO en commentaire) et, dans `source.py`, le téléchargement des blockstates, `is_complete(root)`, `blockstate(root, block_id)`. `material` : composant d'indice 0 → `"dark"`, autres composants connus → `"light"`, texture inconnue → `"dark"` si la famille n'a qu'un composant, sinon `"light"`.
- [ ] **Step 5 : lancer** `python tools/domum/check.py` → OK.
- [ ] **Step 6 : commit** `feat(tools): Domum Ornamentum blockstates and family table`.

---

### Task 2 : assemblage d'un état de blockstate

**Files :**
- Create : `tools/domum/assemble.py`
- Test : `tools/domum/check.py`

**Interfaces :**
- Consumes : `source.blockstate`, `source.load`, `families.Family`.
- Produces : `assemble.state_model(root, family, block_id, props: dict[str, str]) -> {"textures", "elements"}` : choisit la variante (`variants`) ou les parties qui s'appliquent (`multipart`, conditions `when` avec `OR` et valeurs `a|b`), charge chaque modèle, applique sa rotation `x` puis `y` (autour de (8, 8, 8), éléments et rotations d'élément compris, faces renommées), puis applique `-family.base_y` pour ramener le modèle à -Z. `props` omis → valeurs par défaut du bloc (`facing=north`, `half=bottom`, etc.).
- Produces : `assemble.rotate_y(model, degrees)`, `assemble.rotate_x(model, degrees)` (multiples de 90, sens de Minecraft).

- [ ] **Step 1 : tests qui échouent :**

```python
def rotate_y_turns_north_face_to_east():
    model = {"textures": {}, "elements": [{"from": [0, 0, 0], "to": [16, 16, 2],
             "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#a"}}}]}
    turned = assemble.rotate_y(model, 90)
    element = turned["elements"][0]
    assert element["from"] == [14, 0, 0] and element["to"] == [16, 16, 16], element
    assert set(element["faces"]) == {"east"}


def multipart_keeps_matching_parts_only():
    state = {"multipart": [{"apply": {"model": "p/post"}},
                           {"when": {"north": "true"}, "apply": {"model": "p/side"}},
                           {"when": {"north": "false|none"}, "apply": {"model": "p/side_off"}}]}
    assert [p["model"] for p in assemble.parts(state, {"north": "false"})] == ["p/post", "p/side_off"]
```

- [ ] **Step 2 : lancer** → FAIL.
- [ ] **Step 3 : implémenter** `parts`, `rotate_x`, `rotate_y`, `state_model`. La rotation d'un point est celle de Minecraft (`y` = sens horaire vu du dessus : nord → est). Les uv ne changent pas, sauf `uvlock` qu'aucun blockstate DO retenu n'utilise (le vérifier ; sinon lever une erreur explicite).
- [ ] **Step 4 : lancer** → OK.
- [ ] **Step 5 : commit** `feat(tools): assemble Domum Ornamentum blockstate states`.

---

### Task 3 : orientation de base et test par famille

**Files :**
- Modify : `tools/domum/check.py`, `tools/domum/families.py` (si un `base_y` est faux)

**Interfaces :**
- Consumes : `assemble.state_model`.

- [ ] **Step 1 : test qui échoue ou passe selon la table** (Review Focus 3). Pour chaque famille à pente ou battant, mesurer où est le « haut » après `state_model` avec les props par défaut :

```python
def oriented_families_face_minus_z():
    root = source.fetch()
    for family in FAMILIES:
        if family.mechanism not in ("roof", "door", "trapdoor", "static"):
            continue
        model = assemble.state_model(root, family, family.blocks[0], {})
        top = max(model["elements"], key=lambda e: e["to"][1])
        # The highest part of a slope, and the hinge side of a door or trapdoor, sits on -Z (Hytale's front, like
        # Stairs.blockymodel and Slope_Hay.blockymodel).
        assert top["from"][2] <= 8, (family.name, top)
```

(Pour `static`, ne garder que les familles non symétriques : `Panel`, `Post` ; noter l'exception dans le test.)

- [ ] **Step 2 : lancer**, corriger `base_y` dans la table jusqu'à OK.
- [ ] **Step 3 : commit** `test(tools): Domum Ornamentum families face Hytale's front`.

---

### Task 4 : nettoyage des faces

**Files :**
- Create : `tools/domum/faces.py`
- Test : `tools/domum/check.py`

**Interfaces :**
- Produces : `faces.clean(model) -> model` : (1) supprime une face entièrement recouverte par une autre face du même plan, de même orientation, dans le même modèle ; (2) quand deux faces parallèles de même orientation se recouvrent à moins de `MIN_GAP_PX = 0.05` px MC (0,1 unité Hytale), recule la face intérieure à `MIN_GAP_PX` ; (3) rien d'autre.
- Produces : `faces.cap_ends(model) -> model` : ajoute une face `up`/`down` manquante à tout élément dont la face touche y = 0 ou y = 16, avec l'uv de sa face latérale ; pour un pilier à lames (`blockpillar`), un seul couvercle 16×16 au lieu d'un par lame.
- Produces : `faces.overlapping_pairs(model) -> list` (utilisé par les tests et `validate`).

- [ ] **Step 1 : tests qui échouent :**

```python
def coplanar_overlap_is_removed():
    a = {"from": [0, 0, 0], "to": [16, 16, 1], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#a"}}}
    b = {"from": [4, 4, 0], "to": [12, 12, 1], "faces": {"north": {"uv": [0, 0, 8, 8], "texture": "#b"}}}
    cleaned = faces.clean({"textures": {}, "elements": [a, b]})
    assert faces.overlapping_pairs(cleaned) == []


def pillar_column_is_capped():
    root = source.fetch()
    pillar = next(f for f in FAMILIES if f.name == "Pillar")
    model = faces.cap_ends(assemble.state_model(root, pillar, "blockpillar", {"column": "pillar_column"}))
    tops = [e for e in model["elements"] if e["to"][1] == 16 and "up" in e["faces"]]
    assert tops, "a lone column must not be open at the top"


def no_generated_model_has_overlapping_faces():
    for name, model in generated_models():  # every (family, block, state) the generator writes
        assert faces.overlapping_pairs(model) == [], name
```

- [ ] **Step 2 : lancer** → FAIL.
- [ ] **Step 3 : implémenter** (projection des faces dans leur plan, test de recouvrement de rectangles, tolérance 1e-6).
- [ ] **Step 4 : lancer** → OK.
- [ ] **Step 5 : commit** `feat(tools): clean Domum Ornamentum faces (overlaps, near-coplanar, open ends)`.

---

### Task 5 : conversion par composant et contrôles au pixel près

**Files :**
- Modify : `tools/domum/convert.py`
- Test : `tools/domum/check.py`

**Interfaces :**
- Consumes : `families.material`.
- Produces : `Converter(planks, family).convert(model) -> (nodes, atlas, elements)` : le matériau d'une face vient de `material(family, texture_placeholder)` (plus de règle « oak_planks »). Les éléments d'une porte sont regroupés sous un nœud nommé `Door` (voir Task 8), ceux d'une trappe sous `Trapdoor` : `Converter.convert(model, group=None)`.
- Garde : `check_atlas`, `model_root`, correspondance des faces et unités du banc (jugées correctes à la relecture de `bd451ec`).

- [ ] **Step 1 : tests qui échouent** (point mineur de la relecture du banc) :

```python
def flipped_uv_mirrors_pixels():
    gradient = Image.new("RGBA", (32, 32))
    for x in range(32):
        for y in range(32):
            gradient.putpixel((x, y), (x * 8, y * 8, 0, 255))
    image = face_image(gradient, {"uv": [16, 0, 0, 16]}, (32, 32))
    assert image.getpixel((0, 0))[0] == 31 * 8


def rotated_face_turns_clockwise():
    image = face_image(gradient_texture(), {"uv": [0, 0, 16, 16], "rotation": 90}, (32, 32))
    assert image.getpixel((0, 0))[:2] == (0, 31 * 8)  # the source's bottom-left corner


def centre_slot_is_light():
    timber = next(f for f in FAMILIES if f.name == "TimberFrame")
    nodes, atlas, _ = Converter(planks(), timber).convert(framed_model())
    assert materials_used(nodes, atlas) == {"dark", "light"}
```

- [ ] **Step 2 : lancer** → FAIL (`Converter` ne prend pas de famille).
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : lancer** → OK.
- [ ] **Step 5 : commit** `feat(tools): Domum Ornamentum materials follow DO components`.

---

### Task 6 : BlockType commun, familles statiques, onglet créatif

**Files :**
- Create : `tools/domum/blocks/__init__.py`, `tools/domum/blocks/common.py`, `tools/domum/blocks/static.py`, `tools/domum/tabs.py`
- Modify : `tools/domum/names.py`, `tools/domum/generate.py`
- Test : `tools/domum/check.py`

**Interfaces :**
- Produces : `common.base_block(ident, model_path, texture_path, wood) -> dict` (clés du `BlockType` vanilla des planches Darkwood : `Material`, `DrawType: Model`, `CustomModel`, `CustomModelTexture`, `Gathering`, `BlockParticleSetId`, `ParticleColor`, `BlockSoundSetId`, `PhysicalMaterialId` ; `Opacity` : `Solid` pour les blocs pleins et les pentes, `Transparent` pour les formes ajourées, comme leur équivalent vanilla), `common.hitbox(elements) -> dict` (boîtes `Min/Max` en blocs, fusion des boîtes d'éléments alignées, format de `Server/Item/Block/Hitboxes/Furniture/Door/Door.json`), `common.item(ident, family, block_type) -> dict` (`TranslationProperties`, `Icon`, `Categories: ["DomumOrnamentum.<Famille>"]`, `PlayerAnimationsId: Block`, `Tags`, `ItemSoundSetId`).
- Produces : `static.generate(ctx, family)` pour `TimberFrame`, `Post`, `Panel`, `AllBrick` : un objet par bloc DO et par valeur de `type`, `VariantRotation: "NESW"` (panneau : celle de la trappe vanilla), hitbox calculée. `ctx` = `generate.Context(assets, root, planks, wood, pack)` (dataclass), qui accumule `ctx.items`, `ctx.lang`.
- Produces : `tabs.generate(ctx)` : `Server/Item/Category/CreativeLibrary/DomumOrnamentum.json` sur le modèle de `Blocks.json` vanilla (lire sa forme exacte dans le zip : `Order` 4, `Icon`, `Children` avec `Id`, `Name`, `Icon`), un enfant par famille dans l'ordre des groupes du cutter, icônes `Icons/ItemCategories/DomumOrnamentum*.png` et `*Active.png` (l'icône d'un objet de la famille réduite, fond transparent), clés `hycolony.category.do.<famille>` en en-US et fr-FR.
- Colombage dynamique : pas d'objet. Commentaire dans `families.py` : `# Deviation from MC: no dynamic timber frame; MineColonies' builder requests it as framed (DoBlockPlacementHandler).`

- [ ] **Step 1 : tests qui échouent :**

```python
def static_items_use_do_tab_and_own_hitbox():
    ctx = generate_into_temp()  # runs generate.run(tmp_pack) once, cached for the other tests
    panel = ctx.items["HyColony_DO_Panel_Full"]
    assert panel["Categories"] == ["DomumOrnamentum.Panel"]
    assert panel["BlockType"]["HitboxType"].startswith("HyColony_DO_")
    assert not any(item["Categories"][0].startswith("Blocks.") for item in ctx.items.values())


def every_category_has_both_icons():
    ctx = generate_into_temp()
    for child in ctx.tab["Children"]:
        for suffix in ("", "Active"):
            assert (ctx.pack / "Common" / child["Icon"].replace(".png", suffix + ".png")).exists(), child
```

- [ ] **Step 2 : lancer** → FAIL.
- [ ] **Step 3 : implémenter** `common`, `static`, `tabs`, noms, et l'orchestration `generate.run(pack)` / `main()`. Supprimer la hitbox `HyColony_DO_Door` du banc et le filtre `rglob` (plus d'établi de l'architecte : il ne figure dans aucune famille).
- [ ] **Step 4 : lancer** `python tools/domum/check.py` puis `python tools/domum/generate.py` puis `./gradlew build` → OK.
- [ ] **Step 5 : commit** `feat(plugin): Domum Ornamentum static families in their own creative tab`.

---

### Task 7 : bardeaux et demi-bardeau

**Files :**
- Create : `tools/domum/blocks/roof.py`
- Test : `tools/domum/check.py`

**Interfaces :**
- Produces : `roof.shingles(ctx, family)` : pour chaque pente DO (`shingle`, `shingle_flat`, `shingle_flat_lower`, `shingle_steep`, `shingle_steep_lower`), un objet dont le `BlockType` copie la structure de `Server/Item/Items/Wood/Softwood/Wood_Softwood_Roof.json` : `Group`, `HitboxType` (marches, comme vanilla), `VariantRotation`, `ConnectedBlockRuleSet` de type `Roof` et un état par forme (`Corner_Left`, `Corner_Right`, `Inverted_*`, etc. : reprendre **exactement** la liste des `State.Definitions` du toit vanilla), chaque état pointant vers le modèle DO de la forme correspondante (`shape=straight`, `inner_left/right`, `outer_left/right`, `half=top`). Ne remplir que les états que le rule set exige (`Regular` obligatoire, `Hollow`/`Topper` facultatifs, B.10 § 3).
- Produces : `roof.shingle_slab(ctx, family)` : un gabarit `Server/Item/CustomConnectedBlockTemplates/HyColony_DO_ShingleSlabConnectedBlockTemplate.json` (forme de `PillarConnectedBlockTemplate.json`/`WallConnectedBlockTemplate.json` vanilla) aux 6 formes `Top`, `OneWay`, `TwoWay`, `ThreeWay`, `FourWay`, `Curved`, avec les règles de `ShingleSlabBlock.java:163-257` traduites en motifs de voisins ; `ConnectsToOtherMaterials: false` ; un état par forme.

- [ ] **Step 1 : tests qui échouent :**

```python
def shingle_has_every_vanilla_roof_state():
    ctx = generate_into_temp()
    vanilla = ctx.assets.item("Wood_Softwood_Roof")["BlockType"]["State"]["Definitions"]
    shingle = ctx.items["HyColony_DO_Shingle_Straight"]["BlockType"]
    assert shingle["ConnectedBlockRuleSet"]["Type"] == "Roof"
    assert set(shingle["State"]["Definitions"]) >= {"Corner_Left", "Corner_Right"}
    assert set(shingle["State"]["Definitions"]) <= set(vanilla)


def shingle_slab_template_has_six_shapes():
    ctx = generate_into_temp()
    template = ctx.json("Server/Item/CustomConnectedBlockTemplates/HyColony_DO_ShingleSlabConnectedBlockTemplate.json")
    assert set(template["Shapes"]) == {"Top", "OneWay", "TwoWay", "ThreeWay", "FourWay", "Curved"}
```

- [ ] **Step 2 : lancer** → FAIL.
- [ ] **Step 3 : implémenter.** Les pentes `_lower` n'ont pas d'équivalent vanilla : même mécanisme `Roof`, leurs propres modèles.
- [ ] **Step 4 : lancer** `check.py`, `generate.py`, `./gradlew build` → OK.
- [ ] **Step 5 : commit** `feat(plugin): Domum Ornamentum shingles form corners and shingle slabs follow neighbours`.

---

### Task 8 : portes et trappes

**Files :**
- Create : `tools/domum/blocks/door.py`
- Test : `tools/domum/check.py`

**Interfaces :**
- Produces : `door.doors(ctx, family)` pour `Door` (types `full`, `port_manteau`, `vertically_striped`, `waffle`) et `FancyDoor` (`full`, `creeper`) : modèle fermé, charnière à gauche, bas + haut (`half=lower/upper`, `open=false`, `hinge=left`) assemblés sur 2 blocs de haut, **éléments sous un nœud `Door` placé à la charnière** comme `Blocks/Decorative_Sets/Crude/Door.blockymodel` (x = -16, centrée en Z : mesurer le vanilla et recentrer le modèle DO). `BlockType` = celui de `Furniture_Crude_Door.json` avec nos `CustomModel`/`CustomModelTexture`/sons de bois : `HitboxType: Door`, `ConnectedBlockRuleSet` `DoorConnectedBlockTemplate` (motif `Default` = notre id), `State.Definitions` complets (`CloseDoorIn/Out`, `DoorBlocked`, `OpenDoorIn/Out` avec leurs hitboxes et animations vanilla), `Interactions` de la porte vanilla (lire la suite du JSON vanilla). Charnière droite : rotation 180° (B.10 § 1), pas de bloc à part.
- Produces : `door.trapdoors(ctx, family)` pour `Trapdoor` (15 types) et `FancyTrapdoor` (2) : copie de `Furniture_Crude_Trapdoor.json`, nœud `Trapdoor` à la charnière comme le modèle vanilla. **Pose en bas** : `VariantRotation: "UpDownNESW"` si le modèle retourné garde la charnière du bon côté, sinon un objet `_Bottom` ; trancher en comparant avec `VariantRotation.java:105-142` et noter le choix dans la spec.

- [ ] **Step 1 : tests qui échouent** (Review Focus 2) :

```python
def door_copies_vanilla_mechanics():
    ctx = generate_into_temp()
    vanilla = ctx.assets.item("Furniture_Crude_Door")["BlockType"]
    door = ctx.items["HyColony_DO_Door_Full"]["BlockType"]
    assert door["HitboxType"] == vanilla["HitboxType"] == "Door"
    assert set(door["State"]["Definitions"]) == set(vanilla["State"]["Definitions"])
    assert door["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"]["Default"] == "HyColony_DO_Door_Full"
    assert node_names(ctx.model("Door_Full")) >= {"Door"}


def trapdoor_opens_like_vanilla():
    ctx = generate_into_temp()
    vanilla = ctx.assets.item("Furniture_Crude_Trapdoor")["BlockType"]
    trapdoor = ctx.items["HyColony_DO_Trapdoor_Full"]["BlockType"]
    assert set(trapdoor["State"]["Definitions"]) == set(vanilla["State"]["Definitions"])
    assert "Trapdoor" in node_names(ctx.model("Trapdoor_Full"))
```

- [ ] **Step 2 : lancer** → FAIL.
- [ ] **Step 3 : implémenter.** Vérifier dans `Door_Open_In.blockyanim` les noms de nœuds animés (`Door`, `Door2`, `Door-Knob`…) et n'utiliser que ceux-là.
- [ ] **Step 4 : lancer** `check.py`, `generate.py`, `./gradlew build` → OK.
- [ ] **Step 5 : commit** `feat(plugin): Domum Ornamentum doors and trapdoors open like vanilla`.

---

### Task 9 : pilier et murs de papier

**Files :**
- Create : `tools/domum/blocks/pillar.py`, `tools/domum/blocks/pane.py`
- Test : `tools/domum/check.py`

**Interfaces :**
- Produces : `pillar.generate(ctx, family)` : pour `blockpillar`, `blockypillar`, `squarepillar`, un objet dont le `ConnectedBlockRuleSet` est un `CustomTemplate` sur notre copie `HyColony_DO_PillarConnectedBlockTemplate.json` de `PillarConnectedBlockTemplate.json` vanilla, avec la forme `Full` ajoutée (pas de pilier ni dessus ni dessous) ; états `Base`, `Base_Inverted` (= `capital`), `Middle` (= `column`), `Full` (= `full_pillar`), chacun avec son modèle passé par `faces.cap_ends`.
- Produces : `pane.generate(ctx, family)` : pour `blockpaperwall` et `blocktiledpaperwall`, un gabarit `HyColony_DO_PaneConnectedBlockTemplate.json` (poteau seul, bout N, droit N-S, coin, T, croix ; formes tournées par `IsCardinallyRotatable` comme `WallConnectedBlockTemplate.json`), chaque forme assemblée par `assemble.state_model` avec les props multipart DO correspondantes.

- [ ] **Step 1 : tests qui échouent :**

```python
def pillar_has_four_shapes_all_closed():
    ctx = generate_into_temp()
    template = ctx.json("Server/Item/CustomConnectedBlockTemplates/HyColony_DO_PillarConnectedBlockTemplate.json")
    assert set(template["Shapes"]) == {"Base", "Base_Inverted", "Middle", "Full"}
    for state in ("Base", "Base_Inverted", "Middle", "Full"):
        assert closed_ends(ctx.state_model("HyColony_DO_Pillar_Blockpillar", state)), state


def lone_paper_wall_is_one_piece():
    ctx = generate_into_temp()
    wall = ctx.items["HyColony_DO_PaperWall_Paperwall"]["BlockType"]
    assert wall["ConnectedBlockRuleSet"]["Type"] == "CustomTemplate"
    assert len([i for i in ctx.items if i.startswith("HyColony_DO_PaperWall")]) == 2  # two blocks, not 14 pieces
```

- [ ] **Step 2 : lancer** → FAIL.
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : lancer** `check.py`, `generate.py`, `./gradlew build` → OK.
- [ ] **Step 5 : commit** `feat(plugin): Domum Ornamentum pillars and paper walls connect to neighbours`.

---

### Task 10 : familles « compat vanilla »

**Files :**
- Create : `tools/domum/blocks/vanilla.py`
- Test : `tools/domum/check.py`

**Interfaces :**
- Produces : `vanilla.generate(ctx, family)` pour `Fence`, `FenceGate`, `Wall`, `Stairs`, `Slab` : l'objet reprend le `BlockType` de l'équivalent vanilla Darkwood (`Wood_Darkwood_Fence`, `Wood_Darkwood_Fence_Gate`, `Wood_Darkwood_Stairs`, `Wood_Darkwood_Half`… ; muret : `Rock_Stone_Brick_Wall` avec texture remplacée) avec son modèle, ses états et son rule set, et ne change que l'id, la texture (Darkwood), le nom, l'icône et la catégorie. Les motifs du rule set qui citent l'id vanilla sont réécrits vers notre id.

- [ ] **Step 1 : test qui échoue :**

```python
def vanilla_compat_keeps_vanilla_shapes():
    ctx = generate_into_temp()
    ours = ctx.items["HyColony_DO_Fence"]["BlockType"]
    theirs = ctx.assets.item("Wood_Darkwood_Fence")["BlockType"]
    assert ours["ConnectedBlockRuleSet"]["Type"] == theirs["ConnectedBlockRuleSet"]["Type"]
    assert "Wood_Darkwood_Fence" not in json.dumps(ours)  # Review Focus 1: no link to the vanilla block
```

- [ ] **Step 2 : lancer** → FAIL. (Vérifier d'abord les ids vanilla exacts dans le zip ; adapter le test.)
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : lancer** `check.py`, `generate.py`, `./gradlew build` → OK.
- [ ] **Step 5 : commit** `feat(plugin): Domum Ornamentum fences, gates, walls, stairs and slabs`.

---

### Task 11 : icônes et contrôles croisés

**Files :**
- Modify : `tools/domum/icon.py`
- Create : `tools/domum/validate.py`
- Modify : `tools/domum/generate.py`, `tools/domum/check.py`

**Interfaces :**
- Produces : `icon.render(elements, properties=DEFAULT_ICON) -> Image` : rendu du modèle **final orienté** (état par défaut), caméra `Rotation [22.5, 45, 22.5]` et `Scale 0.58823` (défaut vanilla d'un bloc, `Item.java:99-103`), tri des faces par profondeur du centre de face, cadrage sur les bornes réelles du modèle ; `DEFAULT_ICON` et, pour portes et trappes, les `IconProperties` de la porte et de la trappe Crude (`Scale 0.475` / `0.65`, `Translation`). L'objet porte les mêmes `IconProperties`.
- Produces : `validate.pack(ctx)` : lève `AssertionError` si un état référencé n'existe pas, si un gabarit cite un id absent ou un id non `HyColony_DO_*` (Review Focus 1), si une icône, une icône de catégorie, une hitbox, un modèle ou une texture manque, si une clé de traduction manque dans en-US ou fr-FR, ou si `faces.overlapping_pairs` trouve une paire. Appelé par `generate.main()` après `validate_pack`.

- [ ] **Step 1 : tests qui échouent :**

```python
def shingle_icon_shows_the_front():
    ctx = generate_into_temp()
    icon = ctx.icon("Shingle_Straight")
    # The low edge of the slope faces the camera: the bottom half of the icon holds more roof pixels than the top.
    assert opaque_pixels(icon, lower_half=True) > opaque_pixels(icon, lower_half=False)


def validation_rejects_a_foreign_id_in_a_template():
    ctx = generate_into_temp()
    broken = copy.deepcopy(ctx)
    broken.items["HyColony_DO_Door_Full"]["BlockType"]["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"][
        "Default"] = "Furniture_Crude_Door"
    with expect(AssertionError):
        validate.pack(broken)
```

- [ ] **Step 2 : lancer** → FAIL.
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : lancer** `check.py`, `generate.py`, `./gradlew build` → OK.
- [ ] **Step 5 : commit** `feat(tools): Domum Ornamentum icons from the final model and cross-checks`.

---

### Task 12 : documentation et nettoyage

**Files :**
- Modify : `docs/TESTING.md`, `tools/decorations/pack.py` (docstring), `tools/domum/generate.py` (docstring du module : ce n'est plus un banc), `plugin/src/subplugins/DomumOrnamentum/subplugin.json` (`Description` : « Domum Ornamentum blocks (DO-1): every shape in Darkwood/Lightwood. », `Version` 0.2.0, `EnabledByDefault: false`), `docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do1-design.md` (choix de la trappe du bas, tout écart découvert)

- [ ] **Step 1 : `docs/TESTING.md`**, nouvelle étape « Domum Ornamentum DO-1 », reprenant la liste « À vérifier en jeu » de la spec, plus Review Focus 5 : « désactiver le pack avec des blocs DO posés : bloc inconnu, pas de plantage ».
- [ ] **Step 2 : docstrings** mises à jour ; `python tools/domum/check.py`, `python tools/domum/generate.py` (sortie identique à celle commitée : `git status` propre), `./gradlew build` → OK.
- [ ] **Step 3 : commit** `docs: Domum Ornamentum DO-1 in-game checks`.
- [ ] **Step 4 : relectures** `hycolony-reviewer` et `mc-fidelity-checker` sur toute la branche DO-1 ; corrections relues à leur tour.
