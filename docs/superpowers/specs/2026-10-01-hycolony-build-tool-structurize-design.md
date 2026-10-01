# Outil de construction comme Structurize

Date : 2026-10-01. Sous-projet 4 du portage des fenêtres, après les huttes, le citoyen, les requêtes et le champ (`2026-10-01-hycolony-citizen-requests-field-windows-mc-design.md`, dont cette spec reprend les règles d'apparence). Demandé par l'utilisateur (« le Build Tools ne reprend pas le design de Structurize »), conception présentée puis validée : « Autonomie complète ».

## 1. But

La fenêtre de l'outil de construction (la baguette) prend l'apparence et la navigation de Structurize :

- positions et tailles de `windowbuildtool.xml`, `layoutmanipulation.xml` et `windowswitchpack.xml` ×2 ; textures de `sources/structurize/.../textures/gui/buildtool/` et icônes de catégories des packs de MC, agrandies au plus proche voisin en `@2x` (taille affichée ×4) sous `Pages/HyColony/Structurize/` ;
- le contenu et la navigation de `WindowExtendedBuildTool`, `AbstractBlueprintManipulationWindow` et `WindowSwitchPack` : pack, catégories, sous-catégories, plans, niveaux, croix de manipulation, choix de placement ;
- un écart seulement s'il est imposé, nommé `Deviation from MC:` (ou `from Structurize:`) et listé au § 7.

Succès : en jeu, la baguette ouvre la fenêtre transparente de Structurize (le monde et le fantôme restent visibles), avec « Changer de pack » et le fil en haut, les icônes de catégories en bas, les boutons de plans au-dessus, les niveaux à gauche et la croix à droite.

## 2. Données : packs et catégories

Structurize range les plans d'un pack en dossiers (`fundamentals/builder1.blueprint`) ; le pack a un `pack.json` (nom, description, auteurs, icône) et chaque dossier de premier niveau une icône (`icon.png`, `icon_disabled.png`). HyColony range ses plans par style dans `hycolony/styles.json` (style → hutte → niveau). On ajoute :

- **`hycolony/packs.json`**, lu et fusionné comme `styles.json` (fichier du mod puis fragments des sous-plugins) :
  ```json
  { "layout": { "hycolony:builder": "fundamentals", "hycolony:farmer": "agriculture/horticulture" },
    "packs": { "kweebec": { "name": "Kweebec", "desc": "…", "authors": ["…"], "icon": "Kweebec", "owner": "hytale" } } }
  ```
  - `layout` : le dossier de chaque hutte, celui des packs de MC (constructeur, cuisinier, résidence, hôtel de ville : `fundamentals` ; livreur, entrepôt : `craftsmanship/storage` ; fermier : `agriculture/horticulture`). Une hutte absente va dans `fundamentals`.
  - `packs` : les métadonnées d'un style. Absentes : le nom est l'id du style, sans description ni auteur, icône par défaut (le sceptre de Structurize), propriétaire `hycolony`.
  - Lecture tolérante : clé absente = défaut, valeur mal formée = ignorée (journalisée une fois).
- **Port** `BlueprintSource.pack(style)` → `PackInfo(name, desc, authors, icon, owner)` et `BlueprintSource.category(buildingTypeId)` → le chemin ; jamais d'exception. Le cœur ne voit aucun chemin de texture : `icon` est un nom que le plugin résout.

Pas d'état persisté nouveau (la session de la baguette vit en mémoire) : pas de migration.

## 3. Fenêtre principale (`windowbuildtool.xml` + `layoutmanipulation.xml`, 420 × 240 → 840 × 480, centrée, sans fond ni assombrissement)

| Élément | XML | Contenu (Structurize) |
|---|---|---|
| `switch` | 86 × 17 en (5, 5), `button_medium` | « Changer de pack » → fenêtre des packs (§ 4) |
| `tree` | 300 × 12 en (100, 5), blanc gras | `pack`, puis `pack/dossier`, puis `pack/dossier/fichier` (`builder1`) |
| `categories` | (120, 200), boutons 19 × 19 tous les 20 px | une icône par dossier de premier niveau du pack, infobulle = nom du dossier capitalisé ; le dossier ouvert est désactivé (icône `_disabled`) |
| `subcategories` | 270 × 20/40/60 en (100, 180/160/140) selon le nombre de boutons (≤ 3, ≤ 6, plus) | « retour » (`back_medium`) puis un bouton par sous-dossier (`button_medium`, nom capitalisé, texte noir) ; trois par ligne |
| `blueprints` | mêmes tailles et positions | « retour » puis un bouton par hutte (`button_blueprint`, texte blanc) : nom de la hutte ; `_selected` pour la hutte choisie ; `_disabled` et infobulle des exigences en rouge si elle est verrouillée (§ 3.1) ; trois par ligne |
| `levels` | 100 × 120 en (5, 50), boutons 86 × 17 | « Niveau : n » pour chaque niveau de la hutte choisie (aucun bouton désactivé, comme Structurize) |
| `manipulator` | 48 × 64 en (370, 100), boutons 16 × 16 | rotation gauche, haut, rotation droite / gauche, miroir, droite / moins, bas, plus |
| `tip` | 200 × 50 en (150, 100), blanc | `structurize.gui.manipulation.info` à la première ouverture d'une session (§ 7) |
| `placement` | 100 × 80 en (160, 80) | en créatif, après Valider : « Construit » (collage comme un constructeur) et « Placer la hutte » |
| `cancel`, `confirm` | 30 × 30 en (90, 213) et (300, 213) | Annuler ; Valider (visible seulement si la hutte n'est pas verrouillée) |

### 3.1 Navigation (`onButtonClicked`, `handleBlueprintCategory`)

- **Ouverture** : un clic sur un bloc fixe l'ancre (inchangé). Sans pack choisi, la fenêtre des packs s'ouvre à la place (`onOpened`). Sinon la fenêtre s'ouvre là où la session en était (dossier, hutte).
- **Icône de catégorie** : ouvre ce dossier (`depth`). Un dossier avec des sous-dossiers montre les sous-dossiers, sinon les plans.
- **Sous-dossier** : l'ouvre de même. **Retour** : remonte au parent (à la racine, rien en bas sauf les icônes).
- **Plan** : choisit la hutte au niveau 1 (Structurize `setBlueprint(leveled.get(0))`), montre ses niveaux à gauche et la croix ; le fantôme suit. Une hutte verrouillée se choisit aussi (Structurize), mais Valider reste caché.
- **Niveau** : choisit ce niveau. **Croix, Annuler, Valider** : comme aujourd'hui (`WandActions`).
- **Huttes montrées** : toutes celles du pack qui ont un plan dans le dossier, plus seulement celles que le joueur porte (Structurize liste tout le pack).
- **Verrou** (`AbstractBlockHut.getRequirements`, sauf en créatif) : sans le bloc de la hutte dans l'inventaire → « Coûte : <hutte> » ; hors de toute colonie (sauf l'hôtel de ville, `BlockHutTownHall`) → « Doit être placée dans une colonie ». L'ancre décide de la colonie.
- **Valider** (`confirmClicked`) : en survie, place la hutte (un seul gestionnaire) ; en créatif, montre la liste `placement`.

## 4. Fenêtre des packs (`windowswitchpack.xml`, 420 × 240 → 840 × 480, assombrie)

- `cancel` 86 × 17 en (3, 3) : revient à la fenêtre principale si un pack est choisi, sinon ferme.
- `filter` 129 × 17 en (150, 3) : ne garde que les packs dont le nom contient le texte (sans casse).
- `packs` 420 × 200 en (0, 40) : par propriétaire (ordre alphabétique), une ligne de titre (jaune, grand), puis deux packs par ligne de 100 px : icône 80 × 80, nom (blanc), description (petite), « Auteurs : … », bouton « Choisir » 86 × 17 en (0, 80). Les packs d'un propriétaire sont mélangés à chaque ouverture (`Collections.shuffle`).
- Choisir un pack le retient pour le joueur (en mémoire, comme le pack choisi de Structurize) ; un autre pack que celui en cours oublie le dossier et la hutte (`init`), puis la fenêtre principale s'ouvre.

## 5. Cœur

- `WandSession` gagne `depth` (le dossier ouvert, « » à la racine).
- `WandView` devient : `pack` (nom), `tree`, `categories` (id, ouvert), `folders` ou `blueprints` (une seule des deux listes, avec « retour »), chaque plan avec `buildingTypeId`, `selected`, `locked`, `requirements` (`Msg`), `levels`, `manipulate`, `canConfirm`, `creative`, `tip`.
- `WandPacksView` : les packs groupés par propriétaire avec leurs métadonnées, le filtre, le pack choisi.
- `WandActions` : `openCategory(path)`, `back()`, `switchPack()`, `filterPacks(text)`, `selectStyle` (existant, depuis la fenêtre des packs), `selectBuilding` (niveau 1), le reste inchangé. La construction des vues va dans des classes à part (`WandViews`, `WandTree`) pour garder `WandActions` sous les tailles.
- TDD : arbre des dossiers (racine, sous-dossiers, plans, retour), verrous (créatif, bloc porté, hors colonie, hôtel de ville), niveau 1 au choix d'une hutte, ouverture sans pack → fenêtre des packs, filtre et groupes des packs, Valider caché si verrouillé, `packs.json` tolérant.

## 6. Plugin

- `WandPage` réécrite sur `Pages/HyColony/Structurize/BuildTool.ui` ; `WandPacksPage` nouvelle sur `Structurize/SwitchPack.ui` ; gabarits de lignes. Boutons à texte de Structurize : texte noir (blanc pour un plan), gris au survol (`texthovercolor="gray"`), textures `_hover`/`_dim` comme BlockUI.
- `HytaleBlueprintSource` lit `packs.json` ; les icônes de packs (`icon`) et de catégories sont des textures du mod, résolues par nom, avec repli sur l'icône par défaut.
- `docs/TESTING.md` : un point par fenêtre (principale, packs, verrous, placement en créatif), et mise à jour des points existants de la baguette.

## 7. Écarts retenus

- Pas de miroir : le bouton est montré désactivé (le cœur ne sait pas construire en miroir).
- Pas de bouton Réglages : ce sont les réglages du rendu du fantôme côté client de Structurize ; le fantôme de Hytale est dessiné par le serveur.
- Pas de « Collage du schéma » (`Complete`) en créatif : seul « Construit » (le collage existant) et la pose de la hutte.
- Pas de variantes (`alternatives`) : un style HyColony a un seul plan par hutte et par niveau.
- Pas de raccourcis clavier : Hytale n'envoie pas les touches au serveur.
- L'astuce reste jusqu'à la première action, où Structurize la cache après 10 s.
- L'infobulle d'un plan est le nom de la hutte et ses exigences, sans la description de MC (ses textes de langue ne sont pas dans `sources/`) ; exigence de recherche absente (pas de recherche).
- Un seul jeu d'icônes de catégories (celui de 23 packs de MC sur 24), une seule disposition (`layout`) pour tous les packs.
- Les packs de Hytale (kweebec, outlander) n'ont pas d'icône de MC : icône du pack dessinée à partir d'une icône du jeu, ou le sceptre de Structurize.
