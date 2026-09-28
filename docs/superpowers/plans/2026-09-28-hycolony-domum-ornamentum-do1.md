# Domum Ornamentum DO-1 « les blocs » : plan d'implémentation (v2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** chaque forme DO de DO-1 existe comme gabarit (modèle DO converti, états, connexions, onglet créatif), et n'importe quelle combinaison de matériaux permise par les tags devient à l'exécution un bloc complet (états, objet, icône), comme dans DO.

**Architecture :** le générateur Python `tools/domum/` produit, au build, tout ce qui ne dépend pas des matériaux : les modèles DO convertis vers une disposition de texture fixe (32 × 32 pour 1 matériau, 64 × 32 pour 2), un gabarit par forme dans le pack `DomumOrnamentum`, un manifeste des formes et une carte d'icône par forme dans les ressources du plugin, et les tags de matériaux dans le fragment `id-map.json` du pack. À l'exécution, le moteur du prototype `/hyornament` (paquet `plugin/.../ornament`, B.11) crée les variantes demandées ; la logique pure (formes, tags, validation, clés, format de sauvegarde) passe dans le cœur, testée.

**Tech Stack :** Python 3.10+ et Pillow (générateur) ; Java 25, Gson, JUnit (cœur) ; API serveur Hytale 0.6.8 (plugin).

**Spec :** `docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do1-design.md` (v2). Faits : `docs/research/domum-ornamentum.md` A.1-A.3 (DO), B.9-B.10 (géométrie, mécanismes Hytale), **B.11 (moteur à l'exécution, vérifié en jeu)**.

## Global Constraints

- Lire `CLAUDE.md` en entier avant de commencer. Docs et réponses en français ; code, Javadoc, docstrings et commentaires en anglais.
- Générateur : pas de nouvelle dépendance Python (stdlib + Pillow) ; sorties **commitées** ; le build ne lance jamais le générateur ; fichiers Python < 300 lignes, fonctions < 40 lignes, docstring courte.
- Java : règles de CLAUDE.md §§ 1-5 (cœur sans `com.hypixel`, fichiers ≤ 400 lignes, paquets ≤ 15 fichiers, méthodes ≤ 40 lignes et ≤ 5 paramètres, Javadoc courte, TDD dans le cœur).
- Pack `DomumOrnamentum` : **`EnabledByDefault: false`**.
- Gabarits : ids `HyColony_DO_<Forme>` (ex. `HyColony_DO_Shingle`, `HyColony_DO_TimberFrame_Framed`). Variantes : `<gabarit>__<matériau1>[__<matériau2>]` (ids de blocs Hytale, ex. `HyColony_DO_Shingle__Rock_Stone_Brick__Wood_Hardwood_Planks`). États : `*<clé>_State_Definitions_<état>` (règle vanilla, B.11).
- Disposition de texture : 1 matériau → le modèle lit une texture 32 × 32 (celle du matériau) ; 2 matériaux → 64 × 32, composant 1 dans x ∈ [0, 32), composant 2 dans x ∈ [32, 64). Texture de paire : `Blocks/HyColony/DO/Pairs/<matériau1>__<matériau2>.png`, même nom au build et à l'exécution.
- Manifeste et cartes d'icône : `plugin/src/main/resources/hycolony/ornament/shapes.json` et `plugin/src/main/resources/hycolony/ornament/icons/<forme>.png` (serveur seulement).
- Tags : section `ornamentTags` du fragment `plugin/src/subplugins/DomumOrnamentum/hycolony/id-map.json`.
- Commit DO épinglé : `82729d6c9dc0499b256b36b4506d0d9ef20e8aec`.
- Écarts à MC : commentaire `Deviation from MC: …` dans le code **et** dans la spec.
- Jamais `loadAssets` sur un thread de monde ; jamais `RequestCommonAssetsRebuild`.
- `./gradlew build` vert avant chaque commit ; aucune entrée ajoutée aux listes d'exceptions ; `git add` explicite ; messages `type(scope): description` + ligne `Co-Authored-By` de la session. On ne lance jamais le serveur Hytale.

## Review Focus

1. **Matériau hors tag ou inconnu** (`/hyornament give Shingle Dirt_Grass`) : refus avec la liste des matériaux permis, aucune variante créée ni sauvegardée. Test : Task 13 (`requestOutsideTheTagIsRefused`).
2. **Pack désactivé alors que des variantes sont sauvegardées** : pas de plantage, variantes ignorées (journal WARNING), fichier de sauvegarde conservé intact. Test : Task 14 (`savedVariantsOfAMissingShapeAreKeptAndSkipped`).
3. **Porte ouvragée sans 2ᵉ matériau** : le 2ᵉ reprend le 1ᵉʳ (DO), la clé est celle de la paire (m1, m1). Test : Task 13 (`optionalSecondSlotRepeatsTheFirst`).
4. **Bloc DO posé à côté d'un bloc vanilla ou d'une autre famille** : les connexions ne se forment qu'avec la même famille. Test : Task 11 (`validate.pack` rejette un gabarit qui cite un id hors `HyColony_DO_*`).
5. **Carte d'icône qui lit hors de sa disposition** (forme à 1 matériau lisant x ≥ 32) : icône fausse. Test : Task 11 (`iconMapStaysInsideItsLayout`).

---

## Structure des fichiers

| Fichier | Responsabilité |
|---|---|
| `tools/domum/source.py` | Téléchargement au commit épinglé (fait, Task 1 v1) |
| `tools/domum/families.py` | Table des familles (fait) ; + matériaux par défaut, tags des emplacements, quantité du cutter |
| `tools/domum/assemble.py` | Géométrie MC d'un état de blockstate, orientée vers -Z |
| `tools/domum/faces.py` | Nettoyage des faces |
| `tools/domum/convert.py` | `.blockymodel` en disposition 32 / 64 × 32 |
| `tools/domum/blocks/*.py` | Gabarits `BlockType` par mécanisme (statique, toit, porte, pilier, vitre, compat) |
| `tools/domum/tags.py` | Tags de matériaux (ids de blocs Hytale) et matériaux par défaut |
| `tools/domum/pairs.py` | Textures de paire des gabarits |
| `tools/domum/iconmap.py` | Carte d'icône d'une forme |
| `tools/domum/manifest.py` | `shapes.json` |
| `tools/domum/tabs.py` | Onglet créatif |
| `tools/domum/validate.py` | Contrôles croisés du pack |
| `tools/domum/generate.py`, `check.py` | Orchestration, auto-contrôle hors ligne |
| `core/.../ornament/OrnamentShape.java`, `ShapeCatalog.java` | Formes lues du manifeste |
| `core/.../ornament/MaterialTags.java`, `VariantKey.java`, `VariantRequests.java` | Tags, clé, validation d'une demande |
| `core/.../ornament/SavedVariants.java` | Format de sauvegarde (v1) et lecture tolérante |
| `plugin/.../IdMap.java` | Section `ornamentTags` |
| `plugin/.../ornament/runtime/MaterialCatalog.java` | Texture de chaque matériau, lue dans son `BlockType` |
| `plugin/.../ornament/runtime/IconMap.java` | Échantillonnage d'une carte d'icône |
| `plugin/.../ornament/runtime/*` (existants) | Fabrique, états, synchronisation, assets : adaptés |
| `plugin/.../ornament/debug/OrnamentCommand.java` | `/hyornament give`, `/hyornament shapes` |

---

## Partie A : générateur (géométrie au build)

### Task 1 : assemblage d'un état de blockstate

**Files :** Create `tools/domum/assemble.py` ; Test `tools/domum/check.py`

**Interfaces :**
- Consumes : `source.fetch() -> Path`, `source.blockstate(root, block_id) -> dict`, `source.load(root, name) -> {"textures", "elements"}`, `families.Family` (existants).
- Produces : `assemble.parts(state, props) -> list[dict]` ; `assemble.rotate_x(model, degrees)`, `assemble.rotate_y(model, degrees)` (multiples de 90, sens de Minecraft : `y` = horaire vu du dessus, nord → est) ; `assemble.state_model(root, family, block_id, props) -> {"textures", "elements"}` (variante ou parties multipart, rotations `x` puis `y` autour de (8, 8, 8), puis `-family.base_y` ; `props` absents → valeurs par défaut du bloc).

- [ ] **Step 1 : tests qui échouent** dans `check.py` :

```python
def rotate_y_turns_north_face_to_east():
    model = {"textures": {}, "elements": [{"from": [0, 0, 0], "to": [16, 16, 2],
             "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#a"}}}]}
    element = assemble.rotate_y(model, 90)["elements"][0]
    assert element["from"] == [14, 0, 0] and element["to"] == [16, 16, 16], element
    assert set(element["faces"]) == {"east"}


def multipart_keeps_matching_parts_only():
    state = {"multipart": [{"apply": {"model": "p/post"}},
                           {"when": {"north": "true"}, "apply": {"model": "p/side"}},
                           {"when": {"north": "false|none"}, "apply": {"model": "p/side_off"}}]}
    assert [p["model"] for p in assemble.parts(state, {"north": "false"})] == ["p/post", "p/side_off"]
```

- [ ] **Step 2 : lancer** `python tools/domum/check.py` → FAIL (`ModuleNotFoundError: assemble`).
- [ ] **Step 3 : implémenter.** Conditions `when` : `OR` (liste), valeurs `a|b`. Les uv ne changent pas ; si un blockstate retenu a `uvlock: true`, lever `ValueError(f"uvlock not supported: {block_id}")`.
- [ ] **Step 4 : lancer** → OK.
- [ ] **Step 5 : commit** `feat(tools): assemble Domum Ornamentum blockstate states`.

### Task 2 : orientation de base par famille

**Files :** Modify `tools/domum/check.py`, `tools/domum/families.py` (si un `base_y` est faux)

- [ ] **Step 1 : test** :

```python
def oriented_families_face_minus_z():
    root = source.fetch()
    for family in FAMILIES:
        if family.name not in ("Shingle", "Door", "FancyDoor", "Trapdoor", "FancyTrapdoor", "Panel", "Post"):
            continue
        model = assemble.state_model(root, family, family.blocks[0], {})
        top = max(model["elements"], key=lambda e: e["to"][1])
        # The highest part of a slope and the hinge side of a door sit on -Z, Hytale's front (Stairs.blockymodel).
        assert top["from"][2] <= 8, (family.name, top)
```

- [ ] **Step 2 : lancer**, corriger `base_y` dans `families.py` jusqu'à OK.
- [ ] **Step 3 : commit** `test(tools): Domum Ornamentum families face Hytale's front`.

### Task 3 : nettoyage des faces

**Files :** Create `tools/domum/faces.py` ; Test `tools/domum/check.py`

**Interfaces :**
- Produces : `faces.clean(model) -> model` (supprime une face entièrement recouverte par une autre du même plan et de même orientation ; recule à `MIN_GAP_PX = 0.05` px MC la face intérieure de deux faces parallèles de même orientation plus proches que cela) ; `faces.cap_ends(model) -> model` (ajoute la face `up`/`down` manquante d'un élément qui touche y = 0 ou 16, avec l'uv de sa face latérale ; un seul couvercle 16 × 16 pour `blockpillar`) ; `faces.overlapping_pairs(model) -> list`.

- [ ] **Step 1 : tests qui échouent** :

```python
def coplanar_overlap_is_removed():
    a = {"from": [0, 0, 0], "to": [16, 16, 1], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#a"}}}
    b = {"from": [4, 4, 0], "to": [12, 12, 1], "faces": {"north": {"uv": [0, 0, 8, 8], "texture": "#b"}}}
    assert faces.overlapping_pairs(faces.clean({"textures": {}, "elements": [a, b]})) == []


def pillar_column_is_capped():
    root = source.fetch()
    pillar = next(f for f in FAMILIES if f.name == "Pillar")
    model = faces.cap_ends(assemble.state_model(root, pillar, "blockpillar", {"column": "pillar_column"}))
    assert [e for e in model["elements"] if e["to"][1] == 16 and "up" in e["faces"]]
```

- [ ] **Step 2 : lancer** → FAIL. **Step 3 : implémenter** (projection dans le plan de la face, recouvrement de rectangles, tolérance 1e-6). **Step 4 : lancer** → OK.
- [ ] **Step 5 : commit** `feat(tools): clean Domum Ornamentum faces (overlaps, near-coplanar, open ends)`.

### Task 4 : tags, matériaux par défaut et textures de paire

**Files :** Create `tools/domum/tags.py`, `tools/domum/pairs.py` ; Modify `tools/domum/families.py` ; Test `tools/domum/check.py`

**Interfaces :**
- Produces : `tags.TAGS: dict[str, tuple[str, ...]]` (tag DO → ids de blocs Hytale ; les 18 tags de la spec) ; `tags.DEFAULTS: dict[str, str]` (tag → matériau par défaut, transposé de DO : bois sombre pour un cadre, planches claires pour un centre…) ; `tags.texture(assets, block_id) -> str` (chemin `BlockTextures/…` lu dans le `BlockType` vanilla, faces latérales ; `ValueError` si le bloc n'est pas un cube texturé).
- Produces : `Family.slot_tags: tuple[str, ...]` (1 ou 2 tags, dans l'ordre des composants DO), `Family.optional_second: bool` (porte et trappe ouvragées), `Family.cutter_quantity: int` (A.3).
- Produces : `pairs.pair_path(m1, m2) -> str` = `"Blocks/HyColony/DO/Pairs/{m1}__{m2}.png"` ; `pairs.write(pack, assets, m1, m2) -> str` (64 × 32 : texture de m1 à gauche, de m2 à droite, chacune ramenée à 32 × 32 au plus proche voisin).

- [ ] **Step 1 : tests qui échouent** :

```python
def every_tag_lists_real_cubes():
    assets = Assets.open()
    for tag, blocks in TAGS.items():
        assert blocks, tag
        for block in blocks:
            assert tags.texture(assets, block).startswith("BlockTextures/"), (tag, block)


def every_slot_has_a_tag_and_a_default():
    for family in FAMILIES:
        assert 1 <= len(family.slot_tags) <= 2, family.name
        for tag in family.slot_tags:
            assert tag in TAGS and DEFAULTS[tag] in TAGS[tag], (family.name, tag)


def pair_texture_is_both_materials_side_by_side(tmp):
    assets = Assets.open()
    path = pairs.write(tmp, assets, "Wood_Hardwood_Planks", "Rock_Stone_Brick")
    image = Image.open(tmp / "Common" / path)
    assert image.size == (64, 32)
    left = Image.open(io.BytesIO(assets.read("Common/" + tags.texture(assets, "Wood_Hardwood_Planks"))))
    assert image.getpixel((0, 0)) == left.convert("RGBA").resize((32, 32), Image.NEAREST).getpixel((0, 0))
```

- [ ] **Step 2 : lancer** → FAIL.
- [ ] **Step 3 : implémenter.** Remplir `TAGS` d'après `DO-gen: tags/blocks/*.json` (commit épinglé) en choisissant pour chaque matériau MC son équivalent Hytale (planches, bûches, pierres, briques, argiles, laines, grès…) ; ne garder que des blocs `Cube` à texture unique par face latérale. Sources DO en commentaire à côté de chaque tag.
- [ ] **Step 4 : lancer** → OK.
- [ ] **Step 5 : commit** `feat(tools): Domum Ornamentum material tags and pair textures`.

### Task 5 : conversion vers la disposition de texture

**Files :** Modify `tools/domum/convert.py` ; Test `tools/domum/check.py`

**Interfaces :**
- Consumes : `Family.slot_tags`.
- Produces : `convert.to_blockymodel(model, family, group=None) -> dict` : chaque face lit la tuile de son composant (`families.component_index(family, texture_placeholder)` : 0 ou 1 ; une famille à 1 composant lit toujours la tuile 0) ; uv DO (sprite 16 px) mis à l'échelle 32 px par bloc puis décalés de `32 * index` en x ; nœud nommé `group` (`Door`, `Trapdoor`) autour des éléments si donné. `convert.layout_size(family) -> (32, 32) | (64, 32)`.
- Garde : correspondance des faces, unités, uv inversés et tournés du banc (`bd451ec`, jugés corrects).

- [ ] **Step 1 : tests qui échouent** :

```python
def second_component_reads_the_right_tile():
    timber = next(f for f in FAMILIES if f.name == "TimberFrame")
    model = convert.to_blockymodel(framed_model(), timber)
    offsets = {face["offset"]["x"] for node in walk(model["nodes"]) for face in node["shape"]["textureLayout"].values()}
    assert any(x >= 32 for x in offsets) and any(x < 32 for x in offsets)


def single_material_stays_in_one_tile():
    post = next(f for f in FAMILIES if f.name == "Post")
    model = convert.to_blockymodel(post_model(), post)
    assert all(face["offset"]["x"] < 32 for node in walk(model["nodes"])
               for face in node["shape"]["textureLayout"].values())


def flipped_uv_mirrors_pixels():
    image = face_image(gradient_texture(), {"uv": [16, 0, 0, 16]}, (32, 32))
    assert image.getpixel((0, 0))[0] == 31 * 8
```

- [ ] **Step 2 : lancer** → FAIL. **Step 3 : implémenter.** **Step 4 : lancer** → OK.
- [ ] **Step 5 : commit** `feat(tools): Domum Ornamentum models read a 32 or 64x32 material layout`.

### Task 6 : gabarits statiques, manifeste et onglet créatif

**Files :** Create `tools/domum/blocks/__init__.py`, `blocks/common.py`, `blocks/static.py`, `tools/domum/manifest.py`, `tools/domum/tabs.py` ; Modify `tools/domum/names.py`, `tools/domum/generate.py` ; Test `tools/domum/check.py`

**Interfaces :**
- Produces : `common.template(ident, family, model_path) -> dict` : objet + `BlockType` du gabarit (`DrawType: Model`, `CustomModel`, `CustomModelTexture` = texture des matériaux par défaut : texture du matériau pour 1 emplacement, `pairs.write(...)` pour 2 ; sons, particules et `PhysicalMaterialId` du matériau par défaut ; `Opacity` `Solid` pour plein ou pente, `Transparent` pour ajouré ; `Icon` = icône rendue avec les matériaux par défaut ; `Categories: ["DomumOrnamentum.<Famille>"]`) ; `common.hitbox(elements) -> dict`.
- Produces : `static.generate(ctx, family)` pour `TimberFrame` (10 motifs), `Post` (6), `Panel` (15), `AllBrick` : un gabarit par bloc DO et par `type`, `VariantRotation: "NESW"` (panneau : celle de la trappe vanilla).
- Produces : `manifest.write(ctx)` → `plugin/src/main/resources/hycolony/ornament/shapes.json` :

```json
{
  "schemaVersion": 1,
  "shapes": [
    {"id": "TimberFrame_Framed", "template": "HyColony_DO_TimberFrame_Framed", "group": "btimberframe",
     "slots": ["timber_frames_frame", "timber_frames_center"], "optionalSecond": false, "cutterQuantity": 4}
  ]
}
```

- Produces : `tabs.generate(ctx)` : `Server/Item/Category/CreativeLibrary/DomumOrnamentum.json` (forme de `Blocks.json` vanilla, `Order` 4), un enfant par famille dans l'ordre des groupes du cutter, icônes `Icons/ItemCategories/DomumOrnamentum*.png` et `*Active.png`, clés `hycolony.category.do.<famille>` en en-US et fr-FR.
- Produces : fragment `plugin/src/subplugins/DomumOrnamentum/hycolony/id-map.json` : `{"ornamentTags": TAGS}`.
- **Remplacé depuis (2026-09-28, commit e2ec22ce)** : l'onglet n'a plus qu'un enfant, `All`, avec tous les gabarits (choix de l'utilisateur, voir la spec DO-1 § onglet), et le fragment id-map porte aussi `sounds` (sons de la découpeuse lus sur `Bench_Builders`). Ne pas revenir à un enfant par famille.
- Colombage dynamique : aucun gabarit. Commentaire dans `families.py` : `# Deviation from MC: no dynamic timber frame; MineColonies' builder requests it as framed (DoBlockPlacementHandler).`

- [ ] **Step 1 : tests qui échouent** :

```python
def manifest_lists_every_template_with_its_slots():
    ctx = generate_into_temp()
    shapes = json.loads((ctx.resources / "hycolony/ornament/shapes.json").read_text())["shapes"]
    for shape in shapes:
        assert shape["template"] in ctx.items, shape
        assert all(tag in TAGS for tag in shape["slots"]), shape


def every_category_has_both_icons():
    ctx = generate_into_temp()
    for child in ctx.tab["Children"]:
        for suffix in ("", "Active"):
            assert (ctx.pack / "Common" / child["Icon"].replace(".png", suffix + ".png")).exists(), child
```

- [ ] **Step 2 : lancer** → FAIL.
- [ ] **Step 3 : implémenter** (supprimer le code du banc : hitbox `HyColony_DO_Door`, filtre `rglob` de l'établi, textures Darkwood/Lightwood). `subplugin.json` : `Description` « Domum Ornamentum blocks (DO-1): every shape, materials chosen at runtime. », `Version` 0.2.0.
- [ ] **Step 4 : lancer** `python tools/domum/check.py`, `python tools/domum/generate.py`, `./gradlew build` → OK.
- [ ] **Step 5 : commit** `feat(plugin): Domum Ornamentum static templates, shape manifest and creative tab`.

### Task 7 : bardeaux et demi-bardeau

**Files :** Create `tools/domum/blocks/roof.py` ; Test `tools/domum/check.py`

**Interfaces :**
- Produces : `roof.shingles(ctx, family)` : pour chaque pente DO (`shingle`, `shingle_flat`, `shingle_flat_lower`, `shingle_steep`, `shingle_steep_lower`), un gabarit dont le `BlockType` copie la structure de `Wood_Softwood_Roof.json` : `HitboxType` en marches, `VariantRotation: "UpDownNESW"`, `ConnectedBlockRuleSet` `{"Type": "Roof", "Regular": {…}, "MaterialName": "HyColonyDoShingle"}` (`Regular` seul, **sans `Block`**), `State.Definitions` `Corner_Left`, `Corner_Right`, `Inverted_Corner_Left`, `Inverted_Corner_Right` pointant vers les modèles DO `shape=outer_left/right`, `inner_left/right`, avec les `HitboxType`/`FlipType` vanilla (vérifié en jeu, B.11).
- Produces : `roof.shingle_slab(ctx, family)` : gabarit de connexion `Server/Item/CustomConnectedBlockTemplates/HyColony_DO_ShingleSlabConnectedBlockTemplate.json` aux 6 formes `Top`, `OneWay`, `TwoWay`, `ThreeWay`, `FourWay`, `Curved` (règles de `ShingleSlabBlock.java:163-257`), `ConnectsToOtherMaterials: true` et `FaceTags` commun ; un état par forme.

- [ ] **Step 1 : tests qui échouent** :

```python
def shingle_rules_name_states_only():
    ctx = generate_into_temp()
    shingle = ctx.items["HyColony_DO_Shingle"]["BlockType"]
    assert shingle["ConnectedBlockRuleSet"]["Type"] == "Roof"
    outputs = shingle["ConnectedBlockRuleSet"]["Regular"].values()
    assert all("State" in o and "Block" not in o for o in outputs)  # runtime copies resolve their own states
    assert set(shingle["State"]["Definitions"]) == {"Corner_Left", "Corner_Right",
                                                    "Inverted_Corner_Left", "Inverted_Corner_Right"}


def shingle_slab_template_has_six_shapes():
    ctx = generate_into_temp()
    template = ctx.json("Server/Item/CustomConnectedBlockTemplates/HyColony_DO_ShingleSlabConnectedBlockTemplate.json")
    assert set(template["Shapes"]) == {"Top", "OneWay", "TwoWay", "ThreeWay", "FourWay", "Curved"}
```

- [ ] **Step 2 : lancer** → FAIL. **Step 3 : implémenter.** **Step 4 : lancer** `check.py`, `generate.py`, `./gradlew build` → OK.
- [ ] **Step 5 : commit** `feat(plugin): Domum Ornamentum shingles form corners and shingle slabs follow neighbours`.

### Task 8 : portes et trappes

**Files :** Create `tools/domum/blocks/door.py` ; Test `tools/domum/check.py`

**Interfaces :**
- Produces : `door.doors(ctx, family)` pour `Door` (`full`, `port_manteau`, `vertically_striped`, `waffle`) et `FancyDoor` (`full`, `creeper`) : modèle fermé, charnière à gauche, bas et haut assemblés sur 2 blocs, **éléments sous un nœud `Door`** placé à la charnière comme `Blocks/Decorative_Sets/Crude/Door.blockymodel` (modèle DO recentré en Z). `BlockType` copié de `Furniture_Crude_Door.json` : `HitboxType: Door`, `ConnectedBlockRuleSet` `DoorConnectedBlockTemplate` (motif `Default` = notre id), `State.Definitions` complets avec hitboxes et animations vanilla, `Interactions` de la porte vanilla. Charnière droite : rotation de 180° (B.10 § 1).
- Produces : `door.trapdoors(ctx, family)` pour `Trapdoor` (15) et `FancyTrapdoor` (2) : copie de `Furniture_Crude_Trapdoor.json`, nœud `Trapdoor` à la charnière. Pose en bas : `UpDownNESW` si la charnière reste du bon côté une fois retournée, sinon un gabarit `_Bottom` ; noter le choix dans la spec.

- [ ] **Step 1 : tests qui échouent** :

```python
def door_copies_vanilla_mechanics():
    ctx = generate_into_temp()
    vanilla = ctx.assets.item("Furniture_Crude_Door")["BlockType"]
    door = ctx.items["HyColony_DO_Door_Full"]["BlockType"]
    assert door["HitboxType"] == vanilla["HitboxType"] == "Door"
    assert set(door["State"]["Definitions"]) == set(vanilla["State"]["Definitions"])
    assert door["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"]["Default"] == "HyColony_DO_Door_Full"
    assert "Door" in node_names(ctx.model("Door_Full"))


def trapdoor_opens_like_vanilla():
    ctx = generate_into_temp()
    vanilla = ctx.assets.item("Furniture_Crude_Trapdoor")["BlockType"]
    trapdoor = ctx.items["HyColony_DO_Trapdoor_Full"]["BlockType"]
    assert set(trapdoor["State"]["Definitions"]) == set(vanilla["State"]["Definitions"])
    assert "Trapdoor" in node_names(ctx.model("Trapdoor_Full"))
```

- [ ] **Step 2 : lancer** → FAIL. **Step 3 : implémenter** (vérifier dans `Door_Open_In.blockyanim` les noms de nœuds animés). **Step 4 : lancer** → OK.
- [ ] **Step 5 : commit** `feat(plugin): Domum Ornamentum doors and trapdoors open like vanilla`.

### Task 9 : piliers et murs de papier

**Files :** Create `tools/domum/blocks/pillar.py`, `tools/domum/blocks/pane.py` ; Test `tools/domum/check.py`

**Interfaces :**
- Produces : `pillar.generate(ctx, family)` : `blockpillar`, `blockypillar`, `squarepillar`, règle `CustomTemplate` sur `HyColony_DO_PillarConnectedBlockTemplate.json` (copie de `PillarConnectedBlockTemplate.json` + forme `Full`), états `Base`, `Base_Inverted`, `Middle`, `Full`, modèles passés par `faces.cap_ends`.
- Produces : `pane.generate(ctx, family)` : `blockpaperwall`, `blocktiledpaperwall`, gabarit `HyColony_DO_PaneConnectedBlockTemplate.json` (poteau seul, bout, droit, coin, T, croix ; `IsCardinallyRotatable`), chaque forme assemblée par `assemble.state_model` avec les props multipart DO.

- [ ] **Step 1 : tests qui échouent** :

```python
def pillar_has_four_closed_shapes():
    ctx = generate_into_temp()
    template = ctx.json("Server/Item/CustomConnectedBlockTemplates/HyColony_DO_PillarConnectedBlockTemplate.json")
    assert set(template["Shapes"]) == {"Base", "Base_Inverted", "Middle", "Full"}
    for state in ("Base", "Base_Inverted", "Middle", "Full"):
        assert closed_ends(ctx.state_model("HyColony_DO_Pillar_Blockpillar", state)), state


def paper_wall_is_one_template_per_block():
    ctx = generate_into_temp()
    assert ctx.items["HyColony_DO_PaperWall_Paperwall"]["BlockType"]["ConnectedBlockRuleSet"]["Type"] == "CustomTemplate"
    assert len([i for i in ctx.items if i.startswith("HyColony_DO_PaperWall")]) == 2
```

- [ ] **Step 2 : lancer** → FAIL. **Step 3 : implémenter.** **Step 4 : lancer** → OK.
- [ ] **Step 5 : commit** `feat(plugin): Domum Ornamentum pillars and paper walls connect to neighbours`.

### Task 10 : familles « compat vanilla » sur modèles DO

**Files :** Create `tools/domum/blocks/compat.py` ; Test `tools/domum/check.py`

**Interfaces :**
- Produces : `compat.generate(ctx, family)` pour `Fence`, `FenceGate`, `Wall`, `Stairs`, `Slab` : **modèles DO convertis** (choix de l'utilisateur), un par forme ; règles de connexion, états et interactions repris de l'équivalent vanilla (`Wood_Softwood_Fence`, `…_Fence_Gate`, `…_Stairs`, `…_Half`, `Rock_Stone_Brick_Wall` : vérifier les ids dans le zip), chaque état pointant vers le modèle DO de la forme correspondante ; tout id vanilla réécrit vers le nôtre.

- [ ] **Step 1 : test qui échoue** :

```python
def compat_uses_do_models_and_vanilla_shapes():
    ctx = generate_into_temp()
    ours = ctx.items["HyColony_DO_Fence"]["BlockType"]
    theirs = ctx.assets.item("Wood_Softwood_Fence")["BlockType"]
    assert ours["ConnectedBlockRuleSet"]["Type"] == theirs["ConnectedBlockRuleSet"]["Type"]
    assert ours["CustomModel"].startswith("Blocks/HyColony/DO/")
    assert "Wood_Softwood_Fence" not in json.dumps(ours)
```

- [ ] **Step 2 : lancer** → FAIL. **Step 3 : implémenter.** **Step 4 : lancer** → OK.
- [ ] **Step 5 : commit** `feat(plugin): Domum Ornamentum fences, gates, walls, stairs and slabs`.

### Task 11 : cartes d'icône et contrôles croisés

**Files :** Create `tools/domum/iconmap.py`, `tools/domum/validate.py` ; Modify `tools/domum/icon.py`, `tools/domum/generate.py`, `tools/domum/check.py`

**Interfaces :**
- Produces : `iconmap.render(blockymodel, layout_size, properties=DEFAULT_ICON) -> Image` (64 × 64, RGBA) : même caméra que les icônes vanilla (`Rotation [22.5, 45, 22.5]`, `Scale 0.58823` ; portes et trappes : `IconProperties` de la porte et de la trappe Crude), tri des faces par profondeur, cadrage sur les bornes du modèle. **Chaque pixel code où lire, pas une couleur** : `R = floor(u * 256 / layout_w)`, `G = floor(v * 256 / layout_h)`, `B = ombrage × 255` (1 = face du dessus, 0,85 et 0,7 pour les côtés), `A = 255` si couvert, sinon 0. Écrit sous `plugin/src/main/resources/hycolony/ornament/icons/<forme>.png`.
- Produces : `icon.from_map(icon_map, layout) -> Image` (même lecture que Java, pour les icônes des gabarits et pour le test).
- Produces : `validate.pack(ctx)` : `AssertionError` si un état référencé manque, si un gabarit cite un id absent ou hors `HyColony_DO_*`, si une icône, icône de catégorie, hitbox, modèle ou texture manque, si une clé de traduction manque dans en-US ou fr-FR, ou si `faces.overlapping_pairs` trouve une paire.

- [ ] **Step 1 : tests qui échouent** (Review Focus 4 et 5) :

```python
def icon_map_stays_inside_its_layout():
    ctx = generate_into_temp()
    for shape in ctx.shapes:
        icon_map = Image.open(ctx.resources / f"hycolony/ornament/icons/{shape['id']}.png")
        width = 32 if len(shape["slots"]) == 1 else 64
        for r, g, b, a in icon_map.getdata():
            if a:
                assert r * width // 256 < width and g * 32 // 256 < 32, shape["id"]


def validation_rejects_a_foreign_id_in_a_template():
    ctx = generate_into_temp()
    broken = copy.deepcopy(ctx)
    broken.items["HyColony_DO_Door_Full"]["BlockType"]["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"][
        "Default"] = "Furniture_Crude_Door"
    with expect(AssertionError):
        validate.pack(broken)
```

- [ ] **Step 2 : lancer** → FAIL. **Step 3 : implémenter.** **Step 4 : lancer** `check.py`, `generate.py`, `./gradlew build` → OK.
- [ ] **Step 5 : commit** `feat(tools): Domum Ornamentum icon maps and pack cross-checks`.

---

## Partie B : moteur (matériaux à l'exécution)

### Task 12 : formes et tags dans le cœur

**Files :** Create `core/src/main/java/dev/hycolony/core/ornament/OrnamentShape.java`, `ShapeCatalog.java`, `MaterialTags.java` ; Test `core/src/test/java/dev/hycolony/core/ornament/ShapeCatalogTest.java`

**Interfaces :**
- Produces :
  - `public record OrnamentShape(String id, String templateKey, String group, List<String> slotTags, boolean optionalSecond, int cutterQuantity)` ; `int slotCount()`.
  - `public final class ShapeCatalog` : `static ShapeCatalog parse(String json)` (tolérant : entrée incomplète ignorée, `schemaVersion` > 1 → catalogue vide), `Optional<OrnamentShape> shape(String id)` (casse ignorée), `List<OrnamentShape> all()`.
  - `public record MaterialTags(Map<String, Set<String>> tags)` : `boolean accepts(String tag, String blockId)`, `Set<String> materials(String tag)` (vide si tag inconnu).

- [ ] **Step 1 : test qui échoue** :

```java
class ShapeCatalogTest {
    private static final String JSON = """
            {"schemaVersion": 1, "shapes": [
              {"id": "Shingle", "template": "HyColony_DO_Shingle", "group": "cshingle",
               "slots": ["shingles_roof", "shingles_support"], "optionalSecond": false, "cutterQuantity": 4},
              {"id": "Broken"}
            ]}""";

    @Test
    void catalogReadsCompleteShapesAndSkipsBrokenOnes() {
        ShapeCatalog catalog = ShapeCatalog.parse(JSON);
        assertEquals(1, catalog.all().size());
        OrnamentShape shingle = catalog.shape("shingle").orElseThrow();
        assertEquals("HyColony_DO_Shingle", shingle.templateKey());
        assertEquals(2, shingle.slotCount());
    }

    @Test
    void newerManifestGivesAnEmptyCatalog() {
        assertTrue(ShapeCatalog.parse("{\"schemaVersion\": 2, \"shapes\": []}").all().isEmpty());
    }
}
```

- [ ] **Step 2 : lancer** `./gradlew :core:test --tests '*ShapeCatalogTest'` → FAIL (classes absentes).
- [ ] **Step 3 : implémenter** (Gson, lecture tolérante comme `ColonySerializer`).
- [ ] **Step 4 : lancer** → PASS.
- [ ] **Step 5 : commit** `feat(core): Domum Ornamentum shape catalog and material tags`.

### Task 13 : clé de variante et validation d'une demande

**Files :** Create `core/.../ornament/VariantKey.java`, `VariantRequests.java` ; Test `core/src/test/java/dev/hycolony/core/ornament/VariantRequestsTest.java`

**Interfaces :**
- Consumes : `OrnamentShape`, `MaterialTags`, `ShapeCatalog`.
- Produces :
  - `public record VariantKey(OrnamentShape shape, List<String> materials)` : `String blockTypeKey()` = `templateKey + "__" + String.join("__", materials)` ; `String id()` = `shapeId + "|" + join("|", materials)` ; `static Optional<VariantKey> parse(String id, ShapeCatalog catalog)`.
  - `public final class VariantRequests` : `static Result check(OrnamentShape shape, List<String> materials, MaterialTags tags)` ; `public sealed interface Result permits Accepted, Refused` avec `record Accepted(VariantKey key)` et `record Refused(String reasonKey, int slot, Set<String> allowed)` (`reasonKey` = `hycolony.ornament.badCount` ou `hycolony.ornament.badMaterial`).

- [ ] **Step 1 : tests qui échouent** (Review Focus 1 et 3) :

```java
class VariantRequestsTest {
    private final OrnamentShape shingle = new OrnamentShape(
            "Shingle", "HyColony_DO_Shingle", "cshingle", List.of("roof", "support"), false, 4);
    private final OrnamentShape fancyDoor = new OrnamentShape(
            "FancyDoor_Full", "HyColony_DO_FancyDoor_Full", "ddoor", List.of("fancy", "fancy"), true, 2);
    private final MaterialTags tags = new MaterialTags(Map.of(
            "roof", Set.of("Rock_Stone_Brick"), "support", Set.of("Wood_Hardwood_Planks"), "fancy", Set.of("Wood_Hardwood_Planks")));

    @Test
    void acceptedRequestGivesAStableKey() {
        var result = VariantRequests.check(shingle, List.of("Rock_Stone_Brick", "Wood_Hardwood_Planks"), tags);
        VariantKey key = ((VariantRequests.Accepted) result).key();
        assertEquals("HyColony_DO_Shingle__Rock_Stone_Brick__Wood_Hardwood_Planks", key.blockTypeKey());
        assertEquals("Shingle|Rock_Stone_Brick|Wood_Hardwood_Planks", key.id());
    }

    @Test
    void requestOutsideTheTagIsRefused() {
        var result = VariantRequests.check(shingle, List.of("Soil_Dirt", "Wood_Hardwood_Planks"), tags);
        var refused = (VariantRequests.Refused) result;
        assertEquals("hycolony.ornament.badMaterial", refused.reasonKey());
        assertEquals(0, refused.slot());
        assertEquals(Set.of("Rock_Stone_Brick"), refused.allowed());
    }

    @Test
    void optionalSecondSlotRepeatsTheFirst() {
        var result = VariantRequests.check(fancyDoor, List.of("Wood_Hardwood_Planks"), tags);
        assertEquals(List.of("Wood_Hardwood_Planks", "Wood_Hardwood_Planks"),
                ((VariantRequests.Accepted) result).key().materials());
    }

    @Test
    void wrongMaterialCountIsRefused() {
        var result = VariantRequests.check(shingle, List.of("Rock_Stone_Brick"), tags);
        assertEquals("hycolony.ornament.badCount", ((VariantRequests.Refused) result).reasonKey());
    }
}
```

- [ ] **Step 2 : lancer** → FAIL. **Step 3 : implémenter.** **Step 4 : lancer** → PASS.
- [ ] **Step 5 : commit** `feat(core): Domum Ornamentum variant keys and request validation`.

### Task 14 : format de sauvegarde des variantes

**Files :** Create `core/.../ornament/SavedVariants.java` ; Test `core/src/test/java/dev/hycolony/core/ornament/SavedVariantsTest.java`

**Interfaces :**
- Produces : `public record SavedVariants(List<String> ids, List<String> foreign, boolean readOnly)` : `static SavedVariants parse(String json)` (tableau nu du prototype ou `{"schemaVersion": 1, "variants": [...]}` ; entrée non texte → `foreign` en JSON brut ; version > 1 → `readOnly`), `String toJson()` (réécrit `ids` puis `foreign`), `SavedVariants with(String id)` (sans doublon), `List<VariantKey> keys(ShapeCatalog catalog)` (ids illisibles gardés dans `ids`, absents du résultat).
- Le plugin `persistence/VariantStore` garde les E/S (fichier `.tmp` puis déplacement atomique, `.corrupt` si illisible) et délègue le format.

- [ ] **Step 1 : tests qui échouent** (Review Focus 2) :

```java
class SavedVariantsTest {
    @Test
    void savedVariantsOfAMissingShapeAreKeptAndSkipped() {
        SavedVariants saved = SavedVariants.parse(
                "{\"schemaVersion\":1,\"variants\":[\"Shingle|Rock_Stone_Brick|Wood_Hardwood_Planks\",\"Gone|X\"]}");
        ShapeCatalog catalog = ShapeCatalog.parse("""
                {"schemaVersion": 1, "shapes": [{"id": "Shingle", "template": "HyColony_DO_Shingle", "group": "c",
                 "slots": ["a", "b"], "optionalSecond": false, "cutterQuantity": 4}]}""");
        assertEquals(1, saved.keys(catalog).size());
        assertTrue(saved.toJson().contains("Gone|X"));
    }

    @Test
    void newerFileIsReadOnly() {
        assertTrue(SavedVariants.parse("{\"schemaVersion\":2,\"variants\":[]}").readOnly());
    }

    @Test
    void addingAKnownIdChangesNothing() {
        SavedVariants saved = SavedVariants.parse("{\"schemaVersion\":1,\"variants\":[\"A|x\"]}");
        assertEquals(saved, saved.with("A|x"));
    }
}
```

- [ ] **Step 2 : lancer** → FAIL. **Step 3 : implémenter**, puis réduire `plugin/.../persistence/VariantStore.java` aux E/S. **Step 4 : lancer** `./gradlew build` → vert.
- [ ] **Step 5 : commit** `feat(core): Domum Ornamentum saved variants format`.

### Task 15 : catalogue des matériaux et tags dans l'id-map

**Files :** Modify `plugin/src/main/java/dev/hycolony/plugin/IdMap.java` ; Create `plugin/.../ornament/runtime/MaterialCatalog.java`

**Interfaces :**
- Produces : `IdMap.ornamentTags() -> Map<String, List<String>>` (section absente → vide ; ajoutée au record `Data`).
- Produces : `MaterialCatalog.load(Map<String, List<String>> tags) -> MaterialCatalog` (au démarrage, `LoadAssetEvent` 64) : pour chaque id, lit le `BlockType` vanilla ; garde `textures[0]` face nord s'il est de `DrawType` `Cube` ; sinon WARNING et écarté. `MaterialTags tags()`, `Optional<String> texture(String blockId)`, `Optional<String> icon(String blockId)` (icône de son objet).
- Vérifier dans les sources décompilées les accesseurs de `BlockTypeTextures` (`getNorth()`…), de `BlockType.getDrawType()` et `getItem().getIcon()`, et noter les trouvailles dans `docs/research/plugin-b-api.md`.

- [ ] **Step 1 : implémenter** (le plugin n'a pas de tests unitaires, CLAUDE.md § 8).
- [ ] **Step 2 : `./gradlew build`** → vert.
- [ ] **Step 3 : commit** `feat(plugin): Domum Ornamentum material catalog read from vanilla block types`.

### Task 16 : moteur générique (formes du manifeste, disposition, icônes)

**Files :** Modify `plugin/.../ornament/runtime/DynamicBlockTypeFactory.java`, `VariantAssets.java`, `plugin/.../ornament/registry/OrnamentVariantRegistry.java`, `plugin/.../ornament/Ornaments.java` ; Create `plugin/.../ornament/runtime/IconMap.java` ; Delete `plugin/.../ornament/api/OrnamentShape.java`, `OrnamentMaterial.java`, `VariantKey.java`, `runtime/VariantTextureComposer.java`, `runtime/VariantIconRenderer.java`, `plugin/src/main/resources/Server/Item/Items/HyColony/HyColony_Ornament_*.json`, `plugin/src/main/resources/Common/Blocks/HyColony/Ornament/`

**Interfaces :**
- Consumes : `core ornament` (`OrnamentShape`, `ShapeCatalog`, `VariantKey`, `VariantRequests`, `SavedVariants`), `MaterialCatalog`.
- Produces :
  - `DynamicBlockTypeFactory.create(VariantKey key, String modelTexture) -> List<BlockType>` (texture unique pour le modèle : celle du matériau si 1 emplacement, la texture de paire sinon ; plus de cube) ; `createItem(VariantKey key, String icon) -> Item`.
  - `VariantAssets.pairTexture(String m1, String m2, String tex1, String tex2) -> String` : nom `Blocks/HyColony/DO/Pairs/<m1>__<m2>.png` ; **réutilise l'asset s'il existe déjà** (`CommonAssetRegistry.getByName`), sinon le génère (64 × 32) et l'inscrit en silence ; `VariantAssets.icon(VariantKey key, IconMap map, String layoutTexture) -> String`.
  - `IconMap.load(String shapeId) -> Optional<IconMap>` (ressource `hycolony/ornament/icons/<id>.png`) ; `BufferedImage sample(BufferedImage layout)` (lecture inverse de Task 11 : `u = R * w / 256`, `v = G * h / 256`, couleur × `B / 255`, alpha = `A`).
  - `OrnamentVariantRegistry.request(List<VariantKey> keys) -> CompletableFuture<List<OrnamentVariant>>` : **création groupée**, un seul `register` et un seul `registerItems` pour toutes les nouvelles variantes ; `rebuild` = `TEXTURES` si une nouvelle texture de paire a été générée, sinon `NONE` ; double envoi toujours (drapeaux sur le second).
  - `Ornaments.register` : n'active rien si `ornamentTags` est vide (pack désactivé) ; charge `ShapeCatalog` depuis `hycolony/ornament/shapes.json` ; une forme dont le gabarit n'est pas chargé est écartée (WARNING).

- [ ] **Step 1 : implémenter** en gardant les pièges vérifiés du prototype : `data = null`, `getItem`/`getDefaultStateKey`/`toPacket` redéfinis, copie des règles sans `Block`, règles reconstruites à chaque envoi, textures avant blocs, blocs avant objets, `announce` silencieux par défaut, réflexion pour la liste des assets à la connexion.
- [ ] **Step 2 : `./gradlew build`** → vert.
- [ ] **Step 3 : commit** `feat(plugin): Domum Ornamentum runtime variants from the shape manifest`.

### Task 17 : commande et messages

**Files :** Modify `plugin/.../ornament/debug/OrnamentCommand.java`, `VariantGift.java`, `plugin/src/main/resources/Server/Languages/{en-US,fr-FR}/hycolony.lang`

**Interfaces :**
- `/hyornament give <forme> <matériau1> [matériau2]` (opérateurs) : `VariantRequests.check` ; refus → message avec l'emplacement et les matériaux permis (Review Focus 1) ; sinon demande, puis 16 objets. `/hyornament shapes` : liste des formes et de leurs tags. Options d'essai `--rebuild`, `--twice`, `--notify`, `--iconrefresh` retirées (réglages vérifiés en jeu fixés dans le code).
- Clés : `ornament.badCount`, `ornament.badMaterial` (`{p0}` emplacement, `{p1}` matériaux permis), `ornament.unknownShape`, `ornament.shapes`, `ornament.created`, `ornament.reused`, `ornament.given`, `ornament.failed`, en en-US et fr-FR.

- [ ] **Step 1 : implémenter.** **Step 2 : `./gradlew build`** → vert.
- [ ] **Step 3 : commit** `feat(plugin): /hyornament gives any Domum Ornamentum variant`.

### Task 18 : documentation et relectures

**Files :** Modify `docs/TESTING.md`, `docs/research/domum-ornamentum.md` (B.11 : état final), `docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do1-design.md` (choix de la trappe du bas, écarts découverts)

- [ ] **Step 1 : `docs/TESTING.md`**, étape « Domum Ornamentum DO-1 » : la liste « En jeu » de la spec, plus Review Focus 2 (désactiver le pack avec des variantes sauvegardées : pas de plantage, fichier intact).
- [ ] **Step 2 : `python tools/domum/check.py`, `python tools/domum/generate.py` (sortie identique : `git status` propre), `./gradlew build`** → OK.
- [ ] **Step 3 : commit** `docs: Domum Ornamentum DO-1 in-game checks`.
- [ ] **Step 4 : relectures** `hycolony-reviewer` et `mc-fidelity-checker` sur toute la branche ; corrections relues à leur tour.
