# HyDomum : variantes sur la planche, plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**But :** les variantes à deux matériaux de HyDomum lisent une planche de textures dessinée au démarrage, avec un modèle remappé par paire, pour qu'une paire neuve s'affiche sans aucun scintillement.

**Architecture :**
- **Cœur (`domum/core`, paquet `dev.hydomum.core.palette`)** : la mise en page de la planche (`PaletteLayout`) et le remappage d'un `.blockymodel` (`PaletteModel`, `FaceRect`).
- **Plugin (`domum/plugin`)** :
  - `runtime/VariantPalette` dessine la planche, inscrit un bloc caché qui la lit et donne les modèles remappés ;
  - le moteur de variantes (`VariantBuilder`, `DynamicBlockTypeFactory`, `VariantAssets`, `BlockTypeSynchronizer`, `OrnamentVariantRegistry`) les utilise à la place des textures de paire.

**Pile technique :** Java 25, Gson (compileOnly dans le cœur), JUnit 5, API serveur Hytale 0.7.0-pre.4, Gradle (`:domum-core`, `:domum-plugin`).

**Spec :** `docs/superpowers/specs/2026-09-30-hydomum-palette-design.md`. Contexte : `docs/research/client-block-atlas.md`.

## Contraintes globales

- CLAUDE.md s'applique en entier :
  - fichiers de 400 lignes au plus (300 visées), méthodes de 40 lignes au plus, 5 paramètres au plus ;
  - paquets de 15 fichiers au plus ;
  - Javadoc courte sur chaque classe et méthode ;
  - aucun import `com.hypixel` dans le cœur ;
  - PMD, Error Prone, NullAway, spotless et `checkLineLength` (120 colonnes, commentaires compris) doivent passer.
- **D'autres sessions travaillent dans le même dossier.** On formate fichier par fichier (`./gradlew :<module>:spotlessApply -PspotlessIdeHook="<chemin absolu>"`) et on indexe par chemins explicites, jamais `-A`. Leurs fichiers (`core/`, `plugin/`…) ne sont pas touchés.
- Scripts d'édition : Python avec `newline=''`, dans le scratchpad.
- Ne jamais lancer le serveur Hytale : l'utilisateur teste en jeu.
- Commits : `feat(domum): …` ou `docs: …`, en anglais, avec la ligne de fin `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Règle d'une face de `.blockymodel` (spec, « Remappage d'un modèle ») :
  - l'offset est un pivot ;
  - la taille lue est (x, y) pour front, back et quad, (z, y) pour left et right, (x, z) pour top et bottom ;
  - le miroir inverse la largeur ou la hauteur ;
  - l'angle tourne le rectangle : 0 → (x, y), 90 → (-y, x), 180 → (-x, -y), 270 → (y, -x).
- Chemins d'assets :
  - planche : `Blocks/HyDomum/Palette.png` ;
  - bloc caché : `HyDomum_Palette` ;
  - modèles : `Blocks/HyDomum/Variants/<nom du modèle de gabarit sans extension>__<m1>__<m2>.blockymodel`.

## Points à surveiller en relecture

1. **Face du 1ᵉʳ matériau dont le pivot est à x = 32** (angle 180° ou miroir x), et face du 2ᵉ en miroir à x = 64 : elles doivent rester sur leur matériau. Couvert par `PaletteModelTest` et par le test sur tous les modèles (tâche 1).
2. **Planche impossible à dessiner au démarrage** : les variantes à deux matériaux échouent une à une, journalisées, et celles à un matériau marchent. `OrnamentVariantRegistry.start` attrape l'échec de `VariantPalette.start` (tâche 2), et `VariantBuilder` attrape par clé.
3. **Joueur connecté pendant une création** : les modèles neufs partent **avant** les blocs (`BlockTypeSynchronizer.sendModels`, puis `register`). Étape en jeu (tâche 3).
4. **Joueur qui se connecte après une création** : les modèles sont dans la liste des assets requis (`VariantAssets.register` rafraîchit la liste). Étape en jeu (tâche 3).
5. **Redémarrage** : la planche est inscrite **avant** la restauration des variantes sauvegardées. Ordre dans `OrnamentVariantRegistry.start` (tâche 2), étape en jeu (tâche 3).

---

### Tâche 1 : le remappage juste, dans le cœur

**Fichiers :**
- Garder : `domum/core/src/main/java/dev/hydomum/core/palette/PaletteLayout.java` (prototype, non commité) et son test `PaletteLayoutTest.java`.
- Créer : `domum/core/src/main/java/dev/hydomum/core/palette/FaceRect.java`
- Réécrire : `domum/core/src/main/java/dev/hydomum/core/palette/PaletteModel.java`
- Réécrire : `domum/core/src/test/java/dev/hydomum/core/palette/PaletteModelTest.java`
- Créer : `domum/core/src/test/java/dev/hydomum/core/palette/DomumModelsRemapTest.java`

**Interfaces :**
- Produit :
  - `PaletteLayout.of(Collection<String>)`, `materials()`, `tile(String) : Optional<Tile>`, `widthPx()`, `heightPx()`, `TILE_PX = 32`, `record Tile(int x, int y)` ;
  - `PaletteModel.remap(String model, Tile first, Tile second) : String`, qui lève `IllegalArgumentException` sur un modèle illisible ou sur une face à cheval sur les deux moitiés ;
  - `FaceRect.of(JsonObject shape, String side, JsonObject layout)` et `half(int tilePx) : OptionalInt` (paquet uniquement).

- [ ] **Étape 1 : écrire les tests qui échouent**

`PaletteModelTest.java` (remplace celui du prototype) :

```java
package dev.hydomum.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class PaletteModelTest {
    private static final PaletteLayout.Tile FIRST = new PaletteLayout.Tile(96, 64);
    private static final PaletteLayout.Tile SECOND = new PaletteLayout.Tile(0, 128);

    /** A one-box model: size, then one face's side, offset, mirror and angle. */
    private static String box(String type, int sx, int sy, int sz, String side, int x, int y, boolean mx, int angle) {
        return """
                {"nodes": [{"id": "1", "children": [{"id": "2", "children": [],
                  "shape": {"type": "%s", "settings": {"size": {"x": %d, "y": %d, "z": %d}}, "textureLayout": {
                    "%s": {"offset": {"x": %d, "y": %d}, "mirror": {"x": %b, "y": false}, "angle": %d}}}}]}]}"""
                .formatted(type, sx, sy, sz, side, x, y, mx, angle);
    }

    private static JsonObject face(String remapped, String side) {
        return JsonParser.parseString(remapped)
                .getAsJsonObject()
                .getAsJsonArray("nodes")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("children")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("shape")
                .getAsJsonObject("textureLayout")
                .getAsJsonObject(side);
    }

    private static int offset(String remapped, String side, String axis) {
        return face(remapped, side).getAsJsonObject("offset").get(axis).getAsInt();
    }

    @Test
    void secondMaterialFaceMovesIntoTheSecondTile() {
        String remapped = PaletteModel.remap(box("box", 4, 2, 2, "front", 34, 2, false, 0), FIRST, SECOND);
        assertEquals(2, offset(remapped, "front", "x"));
        assertEquals(130, offset(remapped, "front", "y"));
    }

    @Test
    void firstMaterialFaceTurnedHalfWayKeepsItsPivotOnTheFirstTileEdge() {
        // TimberFrame_Framed E6 top: offset (32, 32), angle 180, reads texels 30..32 of the frame.
        String remapped = PaletteModel.remap(box("box", 2, 4, 2, "top", 32, 32, false, 180), FIRST, SECOND);
        assertEquals(128, offset(remapped, "top", "x"));
        assertEquals(96, offset(remapped, "top", "y"));
    }

    @Test
    void mirroredSecondMaterialFaceWithItsPivotAtTheLayoutEdgeStaysOnTheSecondMaterial() {
        String remapped = PaletteModel.remap(box("box", 6, 4, 2, "front", 64, 0, true, 0), FIRST, SECOND);
        assertEquals(32, offset(remapped, "front", "x"));
        assertEquals(true, face(remapped, "front").getAsJsonObject("mirror").get("x").getAsBoolean());
    }

    @Test
    void quarterTurnsReadTheSizeTheirWay() {
        // 90: x spans -6..0 from the pivot, first material; 270 on a left face: x spans 0..y, second material.
        String turned = PaletteModel.remap(box("box", 4, 6, 2, "front", 10, 0, false, 90), FIRST, SECOND);
        assertEquals(106, offset(turned, "front", "x"));
        String back = PaletteModel.remap(box("box", 2, 6, 4, "left", 40, 4, false, 270), FIRST, SECOND);
        assertEquals(8, offset(back, "left", "x"));
        assertEquals(132, offset(back, "left", "y"));
    }

    @Test
    void quadReadsItsXAndYSize() {
        String remapped = PaletteModel.remap(box("quad", 8, 8, 0, "front", 32, 0, false, 0), FIRST, SECOND);
        assertEquals(0, offset(remapped, "front", "x"));
    }

    @Test
    void faceAcrossBothMaterialsIsRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PaletteModel.remap(box("box", 8, 2, 2, "front", 28, 0, false, 0), FIRST, SECOND));
    }

    @Test
    void malformedModelsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> PaletteModel.remap("not json", FIRST, SECOND));
        assertThrows(IllegalArgumentException.class, () -> PaletteModel.remap("{\"nodes\": 3}", FIRST, SECOND));
        String noX = box("box", 4, 2, 2, "front", 34, 2, false, 0).replace("\"x\": 34, ", "");
        assertThrows(IllegalArgumentException.class, () -> PaletteModel.remap(noX, FIRST, SECOND));
    }
}
```

Vérification des attendus (règle de face, contraintes globales) :
- **2ᵉ matériau, cas simple** : 34 → moitié 1, x = 34 + 0 - 32 = 2 ; y = 2 + 128 = 130.
- **Pivot à 32, angle 180°** : top lit (x, z) = (2, 2), tourné de 180° → x ∈ [-2, 0], donc rectangle [30, 32] → moitié 0. x = 32 + 96 = 128, y = 32 + 64 = 96. Le rectangle vertical [30, 32] reste dans la hauteur de 32.
- **Miroir à 64** : largeur -6 → [58, 64] → moitié 1. x = 64 + 0 - 32 = 32.
- **Angle 90°** : front (4, 6) → coins tournés x ∈ [-6, 0], rectangle [4, 10] → moitié 0, x = 10 + 96 = 106.
- **Angle 270° sur left** : taille (z, y) = (4, 6) → (y, -x) donne x ∈ [0, 6], y ∈ [-4, 0]. Rectangle x [40, 46] → moitié 1, x = 40 - 32 = 8 ; y [0, 4], y = 4 + 128 = 132.
- **Quad** : largeur 8, [32, 40] → moitié 1, x = 0.
- **À cheval** : [28, 36], refusée.

`DomumModelsRemapTest.java` : ce test lit tous les modèles réels des formes à deux matériaux.

```java
package dev.hydomum.core.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Every generated two-material model remaps without a face across materials, each onto its own tile. */
class DomumModelsRemapTest {
    // The plugin's generated pack, read from domum/core (Gradle runs tests in the project directory).
    private static final Path PACK = Path.of("../plugin/src/main/resources");
    private static final PaletteLayout.Tile FIRST = new PaletteLayout.Tile(96, 64);
    private static final PaletteLayout.Tile SECOND = new PaletteLayout.Tile(0, 128);

    private record Face(JsonObject shape, String side, JsonObject layout) {}

    @Test
    void everyTwoMaterialModelMovesEachFaceOntoItsMaterialsTile() throws IOException {
        TreeSet<String> models = twoMaterialModels();
        assertFalse(models.isEmpty());
        for (String model : models) {
            String json = Files.readString(PACK.resolve("Common").resolve(model), StandardCharsets.UTF_8);
            List<Face> before = faces(JsonParser.parseString(json).getAsJsonObject());
            List<Face> after = faces(JsonParser.parseString(PaletteModel.remap(json, FIRST, SECOND)).getAsJsonObject());
            assertEquals(before.size(), after.size(), model);
            for (int i = 0; i < before.size(); i++) {
                FaceRect was = FaceRect.of(before.get(i).shape(), before.get(i).side(), before.get(i).layout());
                FaceRect now = FaceRect.of(after.get(i).shape(), after.get(i).side(), after.get(i).layout());
                int half = was.half(PaletteLayout.TILE_PX).orElseThrow();
                PaletteLayout.Tile tile = half == 0 ? FIRST : SECOND;
                double dx = tile.x() - half * PaletteLayout.TILE_PX;
                String where = model + " " + before.get(i).side();
                assertEquals(was.minX() + dx, now.minX(), 1e-6, where);
                assertEquals(was.maxX() + dx, now.maxX(), 1e-6, where);
                assertEquals(was.minY() + tile.y(), now.minY(), 1e-6, where);
            }
        }
    }

    /** The Common paths of the models (main block and states) of every template of a two-slot shape. */
    private static TreeSet<String> twoMaterialModels() throws IOException {
        JsonObject manifest = read(PACK.resolve("hydomum/shapes.json"));
        TreeSet<String> models = new TreeSet<>();
        for (JsonElement shape : manifest.getAsJsonArray("shapes")) {
            JsonObject s = shape.getAsJsonObject();
            if (s.getAsJsonArray("slots").size() != 2) {
                continue;
            }
            Path item = PACK.resolve("Server/Item/Items/HyDomum/" + s.get("template").getAsString() + ".json");
            JsonObject block = read(item).getAsJsonObject("BlockType");
            addModel(block, models);
            if (block.get("State") instanceof JsonObject state
                    && state.get("Definitions") instanceof JsonObject definitions) {
                definitions.entrySet().forEach(e -> {
                    if (e.getValue() instanceof JsonObject def) {
                        addModel(def, models);
                    }
                });
            }
        }
        return models;
    }

    private static void addModel(JsonObject block, TreeSet<String> models) {
        if (block.has("CustomModel")) {
            models.add(block.get("CustomModel").getAsString());
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Every face of the model, in document order. */
    private static List<Face> faces(JsonObject model) {
        List<Face> faces = new ArrayList<>();
        model.getAsJsonArray("nodes").forEach(n -> collect(n.getAsJsonObject(), faces));
        return faces;
    }

    private static void collect(JsonObject node, List<Face> faces) {
        if (node.get("shape") instanceof JsonObject shape && shape.get("textureLayout") instanceof JsonObject layout) {
            layout.entrySet().forEach(e -> faces.add(new Face(shape, e.getKey(), e.getValue().getAsJsonObject())));
        }
        if (node.get("children") instanceof JsonArray children) {
            children.forEach(c -> collect(c.getAsJsonObject(), faces));
        }
    }
}
```

- [ ] **Étape 2 : lancer les tests et les voir échouer**

Commande : `./gradlew -q :domum-core:test --tests 'dev.hydomum.core.palette.*'`

Attendu : erreur de compilation, `FaceRect` n'existe pas.

- [ ] **Étape 3 : écrire `FaceRect` et le nouveau `PaletteModel`**

`FaceRect.java` :

```java
package dev.hydomum.core.palette;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.OptionalInt;

/**
 * The texture rectangle one face of a blockymodel box or quad reads, in texels: the offset is the pivot, the mirror
 * flips the face's size over it, then the angle turns it about it (the rule tools/vanilla/models.py face_rects
 * verified on vanilla models, which tools/domum/convert.py writes).
 */
record FaceRect(double minX, double minY, double maxX, double maxY) {
    private static final double EPSILON = 1e-6;

    /** The rectangle side's layout reads; throws {@link IllegalArgumentException} on a missing size, offset or angle. */
    static FaceRect of(JsonObject shape, String side, JsonObject layout) {
        JsonObject size = object(object(shape, "settings"), "size");
        boolean quad = shape.get("type") instanceof JsonPrimitive type && "quad".equals(type.getAsString());
        boolean sideways = !quad && ("left".equals(side) || "right".equals(side));
        boolean flat = !quad && ("top".equals(side) || "bottom".equals(side));
        double w = number(size, sideways ? "z" : "x");
        double h = number(size, flat ? "z" : "y");
        if (layout.get("mirror") instanceof JsonObject mirror) {
            w = flag(mirror, "x") ? -w : w;
            h = flag(mirror, "y") ? -h : h;
        }
        int angle = layout.has("angle") ? (int) number(layout, "angle") : 0;
        JsonObject offset = object(layout, "offset");
        double u = number(offset, "x");
        double v = number(offset, "y");
        double[][] corners = {turn(0, 0, angle), turn(w, 0, angle), turn(0, h, angle), turn(w, h, angle)};
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] c : corners) {
            minX = Math.min(minX, c[0]);
            minY = Math.min(minY, c[1]);
            maxX = Math.max(maxX, c[0]);
            maxY = Math.max(maxY, c[1]);
        }
        return new FaceRect(u + minX, v + minY, u + maxX, v + maxY);
    }

    /**
     * The half of a two-tile pair layout (0 or 1, tiles of tilePx) this rectangle lies in; empty when it crosses
     * both or leaves the layout.
     */
    OptionalInt half(int tilePx) {
        int half = (int) Math.floor((minX + EPSILON) / tilePx);
        boolean inside = half >= 0
                && half <= 1
                && maxX <= (half + 1) * tilePx + EPSILON
                && minY >= -EPSILON
                && maxY <= tilePx + EPSILON;
        return inside ? OptionalInt.of(half) : OptionalInt.empty();
    }

    /** (x, y) turned by angle, a quarter turn multiple (tools/vanilla/models.py TURNS). */
    private static double[] turn(double x, double y, int angle) {
        return switch (Math.floorMod(angle, 360)) {
            case 0 -> new double[] {x, y};
            case 90 -> new double[] {-y, x};
            case 180 -> new double[] {-x, -y};
            case 270 -> new double[] {y, -x};
            default -> throw new IllegalArgumentException("angle " + angle + " is not a quarter turn");
        };
    }

    static JsonObject object(JsonObject parent, String key) {
        if (parent.get(key) instanceof JsonObject child) {
            return child;
        }
        throw new IllegalArgumentException("missing object " + key);
    }

    private static double number(JsonObject parent, String key) {
        if (parent.get(key) instanceof JsonPrimitive p && p.isNumber()) {
            return p.getAsDouble();
        }
        throw new IllegalArgumentException("missing number " + key);
    }

    private static boolean flag(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
    }
}
```

`PaletteModel.java` :

```java
package dev.hydomum.core.palette;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.Map;

/**
 * Rewrites a two-material {@code .blockymodel} made for a pair layout (first material's face in texels 0-31, second's
 * in 32-63) so that each face reads its material's {@link PaletteLayout} tile instead: the face's rectangle
 * ({@link FaceRect}) tells its half, then its offset moves by that tile's distance to the half. Mirror, angle and
 * every other field are kept.
 */
public final class PaletteModel {
    private PaletteModel() {}

    /**
     * model with every face moved onto first's or second's tile. Throws {@link IllegalArgumentException} when model is
     * not a readable model JSON or a face reads across both halves or outside the pair layout.
     */
    public static String remap(String model, PaletteLayout.Tile first, PaletteLayout.Tile second) {
        JsonObject root;
        try {
            root = JsonParser.parseString(model).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException("unreadable model", e);
        }
        for (JsonElement node : array(root, "nodes")) {
            remapNode(node, first, second);
        }
        return root.toString();
    }

    /** Moves the faces of node, then of its children. */
    private static void remapNode(JsonElement node, PaletteLayout.Tile first, PaletteLayout.Tile second) {
        if (!(node instanceof JsonObject object)) {
            throw new IllegalArgumentException("a node is not an object");
        }
        if (object.get("shape") instanceof JsonObject shape && shape.get("textureLayout") instanceof JsonObject faces) {
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                if (!(face.getValue() instanceof JsonObject layout)) {
                    throw new IllegalArgumentException("face " + face.getKey() + " is not an object");
                }
                move(shape, face.getKey(), layout, first, second);
            }
        }
        for (JsonElement child : array(object, "children")) {
            remapNode(child, first, second);
        }
    }

    /** Moves one face's offset onto its material's tile. */
    private static void move(
            JsonObject shape, String side, JsonObject layout, PaletteLayout.Tile first, PaletteLayout.Tile second) {
        int half = FaceRect.of(shape, side, layout)
                .half(PaletteLayout.TILE_PX)
                .orElseThrow(() -> new IllegalArgumentException("face " + side + " reads across both materials"));
        PaletteLayout.Tile tile = half == 0 ? first : second;
        JsonObject offset = FaceRect.object(layout, "offset");
        // Offsets are whole texels: tools/domum/convert.py rounds them (_snap).
        offset.addProperty("x", offset.get("x").getAsInt() + tile.x() - half * PaletteLayout.TILE_PX);
        offset.addProperty("y", offset.get("y").getAsInt() + tile.y());
    }

    /** parent's array key; empty when absent. Throws {@link IllegalArgumentException} when it is not an array. */
    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null) {
            return new JsonArray();
        }
        if (value instanceof JsonArray array) {
            return array;
        }
        throw new IllegalArgumentException(key + " is not an array");
    }
}
```

- [ ] **Étape 4 : lancer les tests et les voir passer**

Commande : `./gradlew -q :domum-core:test --tests 'dev.hydomum.core.palette.*'`

Attendu : succès. Si `DomumModelsRemapTest` échoue sur un modèle réel :
- d'abord, relire la règle de face pour ce cas (miroir y, angle) contre `tools/vanilla/models.py` `face_rects` ;
- ne jamais assouplir le test.

- [ ] **Étape 5 : formater, construire, commiter**

```bash
R=$(pwd -W); for f in main/java/dev/hydomum/core/palette/PaletteLayout.java main/java/dev/hydomum/core/palette/PaletteModel.java main/java/dev/hydomum/core/palette/FaceRect.java test/java/dev/hydomum/core/palette/PaletteLayoutTest.java test/java/dev/hydomum/core/palette/PaletteModelTest.java test/java/dev/hydomum/core/palette/DomumModelsRemapTest.java; do ./gradlew -q :domum-core:spotlessApply -PspotlessIdeHook="$R/domum/core/src/$f"; done
./gradlew :domum-core:build
git add domum/core/src/main/java/dev/hydomum/core/palette domum/core/src/test/java/dev/hydomum/core/palette
git commit -m "feat(domum): a palette layout and a model remap that moves each face onto its material's tile

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tâche 2 : le moteur de variantes lit la planche

**Fichiers :**
- Créer : `domum/plugin/src/main/java/dev/hydomum/plugin/runtime/VariantPalette.java`
- Réécrire en partie :
  - `runtime/VariantAssets.java` ;
  - `runtime/BlockTypeSynchronizer.java` ;
  - `runtime/DynamicBlockTypeFactory.java` ;
  - `runtime/VariantBlockType.java` ;
  - `registry/VariantBuilder.java` ;
  - `registry/OrnamentVariantRegistry.java` ;
  - `HyDomumPlugin.java`.
- Garder du prototype : `MaterialCatalog.materials()` et `Textures.palette(...)`.
- Supprimer : `runtime/PaletteVariants.java` et `debug/PaletteGive.java`.
- Remettre à l'état de `HEAD` (`git restore`) : `debug/OrnamentCommand.java` et `debug/VariantGift.java`, qui n'ont été modifiés que par le prototype.

Tous les chemins ci-dessus sont sous `domum/plugin/src/main/java/dev/hydomum/plugin/`.

**Interfaces :**
- Consomme : `PaletteLayout`, `PaletteModel.remap` (tâche 1).
- Produit :
  - `VariantPalette(VariantAssets, BlockTypeSynchronizer)` ;
  - `VariantPalette.start(ShapeCatalog, MaterialCatalog)` ;
  - `VariantPalette.model(String templateModel, String first, String second) : String` (nom d'asset du modèle) ;
  - `VariantPalette.TEXTURE` ;
  - `VariantAssets.model(String, byte[]) : String`, `icon(VariantKey, IconMap, List<String> textures) : String`, `takeUnsentModels(Collection<String>) : List<CommonAsset>`, `takeUnsentIcons(Collection<String>) : List<CommonAsset>`, `generated(String, long, Supplier<byte[]>, byte[]...) : String` ;
  - `BlockTypeSynchronizer.sendModels(List<CommonAsset>)` et `publishIcons(List<Item>, List<CommonAsset>)` ;
  - `DynamicBlockTypeFactory.create(VariantKey, String modelTexture, UnaryOperator<String> modelOf) : List<BlockType>`.

Pas de test unitaire côté plugin (CLAUDE.md § 8) : la vérification est le build, puis les étapes en jeu de la tâche 3.

- [ ] **Étape 1 : nettoyer le prototype**

```bash
git restore domum/plugin/src/main/java/dev/hydomum/plugin/debug/OrnamentCommand.java domum/plugin/src/main/java/dev/hydomum/plugin/debug/VariantGift.java
rm domum/plugin/src/main/java/dev/hydomum/plugin/runtime/PaletteVariants.java domum/plugin/src/main/java/dev/hydomum/plugin/debug/PaletteGive.java
```

Dans `DynamicBlockTypeFactory`, retirer la surcharge `createItem(VariantKey, String id, String icon)` et remettre `VariantItem(Item, VariantKey, String)` comme à `HEAD` (`this.id = key.blockTypeKey(); this.blockId = key.blockTypeKey();`). Garder `stateNames` package-private.

- [ ] **Étape 2 : `VariantAssets`, modèles et icônes**

Remplacer, dans `VariantAssets` :
- la Javadoc de classe par :

```java
/**
 * The files a variant needs beyond vanilla ones: the palette texture (VariantPalette), each two-material model
 * remapped onto it, and each variant's inventory icon. Each is generated once per asset name and inputs, kept on disk
 * (reused at the next boot) and registered as a common asset without notification or send: it joins the assets a
 * joining player downloads, whose private cached list is refreshed by reflection. A model or icon then waits until a
 * batch that names it takes it ({@link #takeUnsentModels}, {@link #takeUnsentIcons}) to send it to connected players.
 * Must run off world threads (it reads textures and writes files).
 *
 * <p>ponytail: files of older fingerprints stay on disk, unread; add a sweep of the folder if it ever grows large.
 */
```

- la Javadoc de `DRAWING_VERSION` par `/** Bump when Textures, IconMap or PaletteLayout draw differently: files kept on disk by older code are redrawn. */` ;
- `PAIRS`, `unsentTextures`, `Unsent`, `pairTexture`, `takeUnsent` et l'ancien `icon` : supprimés ;
- à leur place :

```java
    // Registered, not yet sent to connected players, by name: a key that fails keeps its files until it is retried,
    // and of two batches naming one file, only the first to take it sends it.
    // ponytail: a key never retried keeps its entry until restart (one per name); sweep them if that ever matters.
    private final Map<String, CommonAsset> unsentModels = new ConcurrentHashMap<>();
    private final Map<String, CommonAsset> unsentIcons = new ConcurrentHashMap<>();
```

```java
    /**
     * The common asset name of key's icon, painted through map from its layout: the material's texture, or for two
     * materials the 64 x 32 pair drawn in memory (never registered). Throws on failure.
     */
    public String icon(VariantKey key, IconMap map, List<String> textures) {
        byte[][] inputs = textures.stream().map(this::source).toArray(byte[][]::new);
        return registerOnce(
                "Icons/ItemsGenerated/" + key.blockTypeKey() + ".png",
                fingerprint(map.crc(), inputs),
                () -> Textures.png(map.sample(
                        textures.size() == 1
                                ? image(textures.getFirst())
                                : Textures.pair(image(textures.get(0)), image(textures.get(1))))),
                unsentIcons);
    }

    /** The model JSON registered under the common asset name, once; it waits to be sent ({@link #takeUnsentModels}). */
    public String model(String name, byte[] json) {
        return registerOnce(name, fingerprint(0, json), () -> json, unsentModels);
    }

    /**
     * The file drawn by content, registered once under the common asset name for seed and inputs, never sent at
     * runtime (the palette, drawn at boot). Throws when content fails.
     */
    String generated(String name, long seed, Supplier<byte[]> content, byte[]... inputs) {
        return registerOnce(name, fingerprint(seed, inputs), content, null);
    }

    /**
     * Takes the registered models among names not sent yet; a name taken once is never returned again. Call it
     * right before sending them, ahead of the blocks that name them.
     */
    public List<CommonAsset> takeUnsentModels(Collection<String> names) {
        return take(unsentModels, names);
    }

    /**
     * Takes the registered icons among names not sent yet; a name taken once is never returned again. Call it once
     * the stores hold the batch, so that a batch failing before keeps them.
     */
    public List<CommonAsset> takeUnsentIcons(Collection<String> names) {
        return take(unsentIcons, names);
    }
```

`source` et `image` restent package-private (prototype). `registerOnce`, `stored`, `register`, `refreshRequiredAssets`, `fingerprint` et `take` sont inchangés.

- [ ] **Étape 3 : `VariantPalette`**

```java
package dev.hydomum.plugin.runtime;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import dev.hydomum.api.ShapeCatalog;
import dev.hydomum.core.palette.PaletteLayout;
import dev.hydomum.core.palette.PaletteModel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * The palette every two-material variant reads (docs/research/client-block-atlas.md): one texture with each
 * material's face in its {@link PaletteLayout} tile, drawn at boot and read by a hidden block so that clients put it
 * in their atlas on joining. A variant's models are its template's, remapped onto its materials' tiles: a new pair
 * then needs no new block texture, hence no client atlas rebuild and no flicker (checked in game, 2026-09-30).
 *
 * <p>Deviation from MC: DO retextures one model per block on the client; a Hytale model has a single texture.
 */
public final class VariantPalette {
    /** The texture two-material variants read. */
    public static final String TEXTURE = "Blocks/HyDomum/Palette.png";

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String HOLDER = "HyDomum_Palette";
    private static final String MODELS = "Blocks/HyDomum/Variants/";
    /** Side past which a palette may exceed a client atlas page, in pixels. */
    private static final int WARN_SIDE_PX = 2048;

    private final VariantAssets assets;
    private final BlockTypeSynchronizer synchronizer;
    private volatile @Nullable PaletteLayout layout;

    public VariantPalette(VariantAssets assets, BlockTypeSynchronizer synchronizer) {
        this.assets = assets;
        this.synchronizer = synchronizer;
    }

    /**
     * Draws and registers the palette of every material, then the hidden block that reads it; call at boot, off world
     * threads, before players join and before saved variants are restored. Throws when a texture is unreadable, AWT
     * is missing or no template is loaded: two-material variants then fail one by one.
     */
    public void start(ShapeCatalog shapes, MaterialCatalog materials) {
        PaletteLayout loaded = PaletteLayout.of(materials.materials());
        List<String> textures = loaded.materials().stream()
                .map(m -> materials.texture(m).orElseThrow())
                .toList();
        byte[][] inputs = textures.stream().map(assets::source).toArray(byte[][]::new);
        // The layout is in the seed: the same textures on another grid draw another palette.
        long seed = ((long) PaletteLayout.TILE_PX << 32) | loaded.widthPx();
        String palette = assets.generated(
                TEXTURE,
                seed,
                () -> Textures.png(
                        Textures.palette(loaded, m -> assets.image(materials.texture(m).orElseThrow()))),
                inputs);
        BlockType template = shapes.all().stream()
                .map(shape -> BlockType.getAssetMap().getAsset(shape.templateKey()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no ornament template loaded"));
        synchronizer.register(List.of(new Holder(template, palette)), false);
        layout = loaded;
        if (Math.max(loaded.widthPx(), loaded.heightPx()) > WARN_SIDE_PX) {
            LOG.at(Level.WARNING).log(
                    "hydomum: palette of %dx%d px may not fit a client atlas page",
                    loaded.widthPx(), loaded.heightPx());
        }
        LOG.at(Level.INFO).log(
                "hydomum: palette of %d material(s), %dx%d px",
                loaded.materials().size(), loaded.widthPx(), loaded.heightPx());
    }

    /**
     * The common asset name of templateModel remapped to read first's and second's tiles, registered once (it then
     * waits to be sent, {@link VariantAssets#takeUnsentModels}). Throws before {@link #start}, for a material outside
     * the palette, or for a model that cannot be remapped.
     */
    public String model(String templateModel, String first, String second) {
        PaletteLayout loaded = layout;
        if (loaded == null) {
            throw new IllegalStateException("palette not loaded");
        }
        String stem = templateModel.substring(templateModel.lastIndexOf('/') + 1, templateModel.lastIndexOf('.'));
        String remapped = PaletteModel.remap(
                new String(Textures.bytes(templateModel), StandardCharsets.UTF_8),
                tile(loaded, first),
                tile(loaded, second));
        return assets.model(
                MODELS + stem + "__" + first + "__" + second + ".blockymodel",
                remapped.getBytes(StandardCharsets.UTF_8));
    }

    private static PaletteLayout.Tile tile(PaletteLayout layout, String material) {
        return layout.tile(material).orElseThrow(() -> new IllegalArgumentException("not in the palette: " + material));
    }

    /**
     * The hidden block that makes joining clients put the palette in their atlas: never placed, no item, no state,
     * no connection; {@code data} dropped as in {@link VariantBlockType} (getItem and toPacket read it as absent).
     */
    private static final class Holder extends BlockType {
        Holder(BlockType template, String palette) {
            super(template);
            this.data = null;
            this.id = HOLDER;
            this.customModelTexture = new CustomModelTexture[] {new CustomModelTexture(palette, 1)};
            this.state = null;
            this.connectedBlockRuleSet = null;
        }
    }
}
```

- [ ] **Étape 4 : `DynamicBlockTypeFactory.create` et `VariantBlockType`**

Dans `DynamicBlockTypeFactory` :
- Javadoc de classe, dernier point du paragraphe « The block copies keep… » : remplacer « so every variant reuses the {@code .blockymodel}s the client already has; only the texture its models read changes » par « with the models modelOf gives for the template's (its own for one material, remapped onto the palette for two) ».
- Signature et corps :

```java
    /**
     * The variant's main BlockType, then one per template state ({@code *<key>_State_Definitions_<state>}, the key
     * vanilla gives a decoded state), all to register together, each reading modelTexture through the model modelOf
     * gives for its template block's model. Throws {@link IllegalStateException} without a template block.
     */
    public List<BlockType> create(VariantKey key, String modelTexture, UnaryOperator<String> modelOf) {
        String templateKey = key.shape().templateKey();
        BlockType template = template(BlockType.getAssetMap().getAsset(templateKey), key);
        String mainKey = key.blockTypeKey();
        Map<String, String> stateKeys = new LinkedHashMap<>();
        for (String state : stateNames(template)) {
            stateKeys.put(state, "*" + mainKey + STATE + state);
        }
        VariantBlockType.Family family = new VariantBlockType.Family(
                mainKey,
                stateKeys.isEmpty() ? null : new VariantStateData(stateKeys),
                copy(template.getConnectedBlockRuleSet(), templateKey, mainKey),
                templateKey);
        List<BlockType> blocks = new ArrayList<>();
        blocks.add(new VariantBlockType(template, mainKey, model(template, modelOf), modelTexture, family));
        stateKeys.forEach((state, stateKey) -> {
            BlockType stateTemplate = template(template.getBlockForState(state), key);
            blocks.add(new VariantBlockType(
                    stateTemplate, stateKey, model(stateTemplate, modelOf), modelTexture, family));
        });
        return blocks;
    }

    /** block's model through modelOf; null (the template's own, kept by the copy) when block has none. */
    private static @Nullable String model(BlockType block, UnaryOperator<String> modelOf) {
        String model = block.getCustomModel();
        return model == null ? null : modelOf.apply(model);
    }
```

Ajouter `import java.util.function.UnaryOperator;`.

Dans `VariantBlockType`, supprimer le constructeur à 4 paramètres ; garder celui à 5 (`template, id, @Nullable model, modelTexture, family`). Sa Javadoc devient : `/** A copy of template under id, reading modelTexture through model (a common asset path; the template's when null). */`.

- [ ] **Étape 5 : `VariantBuilder`**

```java
/**
 * Builds a batch of variants, not yet registered: each one's BlockTypes (a one-material variant reads its material's
 * texture through its template's models, a two-material one the palette through remapped models) and its Item with
 * its painted icon. A variant that fails is logged and left out.
 */
final class VariantBuilder {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final DynamicBlockTypeFactory factory = new DynamicBlockTypeFactory();
    private final VariantAssets assets;
    private final VariantPalette palette;
    // Plugin resources, fixed while the server runs; batches may build concurrently.
    private final Map<String, Optional<IconMap>> iconMaps = new ConcurrentHashMap<>();

    /** A batch: its BlockTypes and Items, the keys built, and the models and icons they name. */
    record Built(List<BlockType> types, List<Item> items, List<VariantKey> done, Set<String> assetNames) {}

    VariantBuilder(VariantAssets assets, VariantPalette palette) {
        this.assets = assets;
        this.palette = palette;
    }

    /** Builds keys with materials' textures; must run off world threads (it reads and writes files). */
    Built build(List<VariantKey> keys, MaterialCatalog materials) {
        List<BlockType> types = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        List<VariantKey> done = new ArrayList<>();
        Set<String> assetNames = new HashSet<>();
        for (VariantKey key : keys) {
            try {
                List<String> textures = textures(key, materials);
                Set<String> named = new HashSet<>();
                List<BlockType> family = textures.size() == 1
                        ? factory.create(key, textures.getFirst(), UnaryOperator.identity())
                        : factory.create(key, VariantPalette.TEXTURE, m -> {
                            String model = palette.model(m, key.materials().get(0), key.materials().get(1));
                            named.add(model);
                            return model;
                        });
                String icon = icon(key, textures);
                items.add(factory.createItem(key, icon));
                types.addAll(family);
                done.add(key);
                assetNames.addAll(named);
                if (icon != null) {
                    assetNames.add(icon);
                }
            } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
                LOG.at(Level.SEVERE).withCause(e).log("hydomum: cannot create %s", key.id());
            }
        }
        return new Built(types, items, done, assetNames);
    }

    /** The texture of each of key's materials, in slot order. */
    private static List<String> textures(VariantKey key, MaterialCatalog materials) {
        return key.materials().stream()
                .map(m -> materials.texture(m).orElseThrow(() -> new IllegalStateException("not a material: " + m)))
                .toList();
    }

    /** key's icon painted through its shape's icon map; null (the template's icon) when it cannot be. */
    private @Nullable String icon(VariantKey key, List<String> textures) {
        try {
            Optional<IconMap> map = iconMaps.computeIfAbsent(key.shape().id(), IconMap::load);
            if (map.isPresent()) {
                return assets.icon(key, map.get(), textures);
            }
            LOG.at(Level.WARNING).log("hydomum: no icon map for %s, template icon kept", key.shape().id());
        } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
            LOG.at(Level.SEVERE).withCause(e).log("hydomum: icon of %s failed, template icon kept", key.id());
        }
        return null;
    }
}
```

Ajouter les imports `dev.hydomum.plugin.runtime.VariantPalette` et `java.util.function.UnaryOperator`.

- [ ] **Étape 6 : `BlockTypeSynchronizer`**

- Javadoc de classe :

```java
/**
 * Registers runtime BlockTypes and Items in Hytale's stores and sends connected players a batch's new files: its
 * models first, since no rebuild flag makes a client reread a model that arrives after the block naming it; its icons
 * last, with the Items again and {@code updateIcons}. No packet carries {@code updateBlockTextures}: variants read
 * textures clients already hold (VariantPalette), so no client atlas rebuild, hence no flicker.
 *
 * <p>Must not run on a world thread: {@code World.tick} holds the read lock of {@code AssetRegistry.ASSET_LOCK} and
 * {@code AssetStore.loadAssets} takes its write lock, which deadlocks (docs/research/plugin-b-api.md § 17).
 */
```

- Supprimer `BLOCK_TEXTURES` et `publish`.
- Ajouter :

```java
    /**
     * Sends connected players a batch's new models, before its blocks are registered. Sends nothing without model or
     * without player: joining players download them with the required assets. A failure is logged: clients lack the
     * models until they reconnect.
     */
    public void sendModels(List<CommonAsset> models) {
        if (models.isEmpty() || Universe.get().getPlayerCount() == 0) {
            return;
        }
        try {
            CommonAssetModule.get().sendAssets(models, false);
            LOG.at(Level.INFO).log("hydomum: sent %d new model(s)", models.size());
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hydomum: new models not sent, clients need to reconnect");
        }
    }

    /**
     * Sends connected players a registered batch's new icons, then its {@code items} again with {@code updateIcons}
     * (without it, a client ignores a new icon; in game 2026-09-28). Sends nothing without icon or without player;
     * a failure is logged: clients lack the icons until they reconnect.
     */
    public void publishIcons(List<Item> items, List<CommonAsset> icons) {
        if (icons.isEmpty() || Universe.get().getPlayerCount() == 0) {
            return;
        }
        try {
            CommonAssetModule.get().sendAssets(icons, false);
            broadcastItemIcons(items);
            LOG.at(Level.INFO).log("hydomum: published %d icon(s), then updateIcons", icons.size());
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hydomum: new icons not sent, clients need to reconnect");
        }
    }
```

`register`, `registerItems`, `broadcastTypes` (utilisé par le double envoi, `NO_REBUILD`) et `broadcastItemIcons` restent.

- [ ] **Étape 7 : `OrnamentVariantRegistry`**

- Constructeur : `public OrnamentVariantRegistry(BlockTypeSynchronizer synchronizer, VariantStore store, VariantAssets assets, VariantPalette palette)`. Le champ `palette` est gardé, `this.builder = new VariantBuilder(assets, palette);`, et la Javadoc gagne `@param palette the texture two-material variants read, started with the catalogs`.
- `start` :

```java
    /**
     * Takes the catalogs, draws the palette, then registers every saved variant again, in one store load and without
     * any client rebuild; call it at boot, off any world thread and before chunks load. A palette that cannot be
     * drawn is logged: two-material variants then fail one by one. A variant that fails is logged and skipped.
     */
    public void start(Catalogs loaded) {
        catalogs = loaded;
        try {
            palette.start(loaded.shapes(), loaded.materials());
        } catch (RuntimeException | LinkageError | java.awt.AWTError e) { // AWT may lack native libraries
            LOG.at(Level.SEVERE).withCause(e).log("hydomum: palette not drawn, two-material variants unavailable");
        }
        List<VariantKey> saved = store.load(loaded.shapes());
        ...
```

  La suite de `start` est inchangée.
- `create` :

```java
    /**
     * Builds keys' blocks and items (their new models and icons registered, not sent), sends the new models, registers
     * the blocks and items, then publishes the new icons and saves the keys in the {@link VariantStore}. At boot
     * nothing is sent (no player yet: files, blocks and items reach clients when they join); otherwise
     * UpdateBlockTypes goes twice (the client misses the first runtime one), and no packet asks for a texture rebuild.
     */
    private Batch create(List<VariantKey> keys, boolean boot) {
        Catalogs loaded = catalogs;
        if (loaded == null) {
            throw new IllegalStateException("ornament catalogs not loaded yet");
        }
        VariantBuilder.Built built = builder.build(keys, loaded.materials());
        if (!built.types().isEmpty()) {
            // Models go before the blocks that name them: nothing makes a client reread one later.
            List<CommonAsset> models = assets.takeUnsentModels(built.assetNames());
            if (!boot) {
                synchronizer.sendModels(models);
            }
            synchronizer.register(built.types(), !boot);
            synchronizer.registerItems(built.items());
            // Taken once the stores hold this batch: a key that failed before keeps its icon for its retry.
            List<CommonAsset> icons = assets.takeUnsentIcons(built.assetNames());
            if (!boot) {
                synchronizer.publishIcons(built.items(), icons);
                store.add(built.done());
            }
        }
        return new Batch(built.done());
    }
```

Imports : `com.hypixel.hytale.server.core.asset.common.CommonAsset` et `dev.hydomum.plugin.runtime.VariantPalette`.

- [ ] **Étape 8 : `HyDomumPlugin`**

Partir de la version du prototype, qui construit déjà `synchronizer` et `assets`, puis :
- remplacer `PaletteVariants palette = new PaletteVariants(synchronizer, assets);` et la construction du registre par :

```java
        VariantPalette palette = new VariantPalette(assets, synchronizer);
        OrnamentVariantRegistry ornaments = new OrnamentVariantRegistry(
                synchronizer, new VariantStore(DATA.resolve("variants.json")), assets, palette);
```

- remettre `registerVariants(ids, ornaments)` et son gestionnaire d'événement comme à `HEAD` (un seul `try`, `ornaments.start(...)`) ;
- remettre `getCommandRegistry().registerCommand(new OrnamentCommand(ornaments));` ;
- remplacer l'import `PaletteVariants` par `VariantPalette`.

La Javadoc de `registerVariants` redevient celle de `HEAD`.

- [ ] **Étape 9 : formater, construire, commiter**

```bash
R=$(pwd -W); P=domum/plugin/src/main/java/dev/hydomum/plugin; for f in runtime/VariantPalette.java runtime/VariantAssets.java runtime/BlockTypeSynchronizer.java runtime/DynamicBlockTypeFactory.java runtime/VariantBlockType.java runtime/Textures.java runtime/MaterialCatalog.java registry/VariantBuilder.java registry/OrnamentVariantRegistry.java HyDomumPlugin.java; do ./gradlew -q :domum-plugin:spotlessApply -PspotlessIdeHook="$R/$P/$f"; done
./gradlew :domum-core:build :domum-plugin:build
git status --short domum
```

`git status` ne doit montrer que les fichiers de la tâche : `OrnamentCommand` et `VariantGift` n'apparaissent plus, `PaletteVariants` et `PaletteGive` n'existent pas.

```bash
git add domum/plugin/src/main/java/dev/hydomum/plugin/runtime/VariantPalette.java domum/plugin/src/main/java/dev/hydomum/plugin/runtime/VariantAssets.java domum/plugin/src/main/java/dev/hydomum/plugin/runtime/BlockTypeSynchronizer.java domum/plugin/src/main/java/dev/hydomum/plugin/runtime/DynamicBlockTypeFactory.java domum/plugin/src/main/java/dev/hydomum/plugin/runtime/VariantBlockType.java domum/plugin/src/main/java/dev/hydomum/plugin/runtime/Textures.java domum/plugin/src/main/java/dev/hydomum/plugin/runtime/MaterialCatalog.java domum/plugin/src/main/java/dev/hydomum/plugin/registry/VariantBuilder.java domum/plugin/src/main/java/dev/hydomum/plugin/registry/OrnamentVariantRegistry.java domum/plugin/src/main/java/dev/hydomum/plugin/HyDomumPlugin.java
git commit -m "feat(domum): two-material variants read a boot palette through remapped models, without any atlas rebuild

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Tâche 3 : documentation et étapes en jeu

**Fichiers :**
- Modifier : `docs/TESTING.md` (étapes 143, 159 et 209)
- Ajouter au commit : `docs/research/client-block-atlas.md`, la ligne ajoutée à `docs/research/domum-ornamentum.md`, la spec et ce plan.

- [ ] **Étape 1 : `docs/TESTING.md`**

- **Étape 143** : remplacer « un seul scintillement de l'écran au plus (nouvelle paire de textures). Refaire la même commande : « réutilisé », aucun scintillement. » par « **aucun scintillement**, même pour une paire jamais faite. Refaire la même commande : « réutilisé ». ».
- **Étape 159** :
  - remplacer « puis un seul court scintillement, puis l'aperçu » par « puis, sans scintillement, l'aperçu » ;
  - remplacer « Changer de forme dans l'onglet ne fait plus scintiller. » par « Changer de forme dans l'onglet ne fait pas scintiller. » ;
  - remplacer « : un seul scintillement pour tout l'onglet si ces matériaux lui conviennent. » par « : aucun scintillement. ».
- **Section « HyDomum : test de la planche »** : la remplacer par :

```markdown
## HyDomum : variantes sur la planche (sans scintillement)

Spec `docs/superpowers/specs/2026-09-30-hydomum-palette-design.md`.

209. **Planche au démarrage.** Le journal dit « hydomum: palette of N material(s), WxH px » (au plus 296 matériaux, environ 576x544 px), sans avertissement de taille. Se connecter. Pour chaque ligne, une paire jamais faite, avec `/hydomum give` : **aucun scintillement**, l'icône montre les deux matériaux, et le bloc posé les montre aux bons endroits, sans bande d'un autre matériau (regarder de près le haut et le bas des colombages) :
     - `TimberFrame_Framed Wood_Softwood_Planks --second Soil_Clay_Smooth_White` et `TimberFrame_DoubleCrossed Metal_Iron --second Cloth_Block_Wool_Red` ;
     - `Shingle Rock_Slate_Brick --second Wood_Darkwood_Planks` : poser un toit en L, les coins se forment, tuiles et support aux bons matériaux ;
     - `ShingleSlab Soil_Clay_Red --second Wood_Birch_Trunk_Full` ;
     - `FancyDoor_Full Wood_Redwood_Planks --second Metal_Copper` : ouvrir et fermer ; `FancyDoor_Creeper Wood_Ash_Trunk_Full` sans second matériau, qui montre le même matériau partout ;
     - `FancyTrapdoor_Full Rock_Marble --second Wood_Goldenwood_Planks` : ouvrir et fermer ;
     - `PaperWall Wood_Stripped_Deco --second Cloth_Block_Wool_Yellow` : trois en ligne, ils se connectent.
210. **Établi.** Fabriquer à l'établi une paire jamais faite : aucun scintillement à l'aperçu ni à la fabrication.
211. **Second joueur et redémarrage.** Un second joueur qui se connecte après ces créations voit tous ces blocs justes. Redémarrer le serveur : les mêmes blocs sont justes dès la connexion, et `universe/hydomum/assets/Blocks/HyDomum/Variants/` contient leurs modèles.
```

- [ ] **Étape 2 : relire que les matériaux de l'étape 209 sont dans les tags**

Commande : `python -c "import json;d=json.load(open('domum/plugin/src/main/resources/hydomum/id-map.json',encoding='utf-8'))['ornamentTags'];print([m for m in ['Wood_Softwood_Planks','Soil_Clay_Smooth_White','Metal_Iron','Cloth_Block_Wool_Red','Rock_Slate_Brick','Wood_Darkwood_Planks','Soil_Clay_Red','Wood_Birch_Trunk_Full','Wood_Redwood_Planks','Metal_Copper','Wood_Ash_Trunk_Full','Rock_Marble','Wood_Goldenwood_Planks','Wood_Stripped_Deco','Cloth_Block_Wool_Yellow'] if not any(m in v for v in d.values())])"`

Attendu : `[]`. Sinon, prendre un autre matériau du bon tag : `/hydomum shapes` donne les tags de chaque forme.

- [ ] **Étape 3 : commiter la documentation**

```bash
git add docs/TESTING.md docs/research/client-block-atlas.md docs/research/domum-ornamentum.md docs/superpowers/specs/2026-09-30-hydomum-palette-design.md docs/superpowers/plans/2026-09-30-hydomum-palette.md
git diff --cached --stat
git commit -m "docs: the client's block atlas, the palette design and its in-game checks

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

**Avant de commiter**, vérifier avec `git diff --cached docs/research/domum-ornamentum.md` que seule notre ligne est indexée : d'autres sessions peuvent avoir modifié ce fichier. Si le diff contient d'autres changements, indexer notre seule ligne avec `git apply --cached`, à partir d'un patch qui ne contient qu'elle.

---

### Tâche 4 : relectures

- [ ] **Étape 1 :** agent `hycolony-reviewer` sur les trois commits, avec la spec et les « Points à surveiller en relecture ».
- [ ] **Étape 2 :** agent `mc-fidelity-checker` sur `VariantPalette`, `DynamicBlockTypeFactory` et `VariantBuilder` : l'écart de DO est documenté.
- [ ] **Étape 3 :** corriger ce qu'ils trouvent, dans de nouveaux commits (`fix(domum): …`), puis faire relire ces corrections.
- [ ] **Étape 4 :** feu vert à l'utilisateur pour les étapes 209 à 211 de `docs/TESTING.md`.
