# HyColony : organisation du code, héritage, duplication, extension, outillage

Recherche web (septembre 2026). Les sources sont citées à chaque section. Quelques références « classiques » (Bloch 2018, Metz 2016, Nystrom 2014) sont antérieures à 2023. Elles restent la référence actuelle et sont signalées comme telles.

## 1. Organisation du code à grande échelle

**Recommandation.** Garder un monolithe modulaire, découpé par fonctionnalité, avec des ports et adaptateurs. Faire vérifier les frontières par des tests plutôt que par la seule discipline. C'est déjà la forme de HyColony. Il reste à la durcir, pas à la changer.

**Pourquoi.** Spring Modulith (O. Drotbohm, VMware/Broadcom) formalise ce modèle : chaque paquet de premier niveau est un module, avec une API publique et des internes. `ApplicationModules.verify()` échoue sur un cycle entre modules ou sur l'accès aux internes d'un autre module, et `allowedDependencies` restreint le graphe. Tout cela repose sur ArchUnit. jMolecules 2.0 (Drotbohm, nov. 2025) donne des stéréotypes « Hexagonal » (Port, Adapter) qu'ArchUnit sait vérifier. Gradle, dans ses *Best Practices for Structuring Builds*, conseille de découper en projets le long des frontières naturelles (API/implémentation, cœur/UI, tranches verticales) pour profiter de l'évitement de travail et du parallélisme. Il ajoute que des projets minuscules d'une ou deux classes sont contre-productifs.

**Pour HyColony.** Spring Modulith dépend de Spring : inutile ici. Il suffit de reproduire ses règles avec ArchUnit, qui est déjà présent :
- `slices().matching("dev.hycolony.core.(*)..").should().beFreeOfCycles()` sur **tout** le cœur, pas seulement `construction` comme aujourd'hui ;
- une règle « seul le paquet racine d'une fonctionnalité est accessible depuis une autre fonctionnalité » ;
- une liste de dépendances autorisées par fonctionnalité (`job` ne voit pas `construction`, etc.).

Un module Gradle de plus ne paie que lorsqu'une frontière est stable et coûteuse à compiler, par exemple un futur `api` pour d'autres mods. JPMS (`module-info`) n'apporte rien de plus qu'ArchUnit dans un plugin chargé par le serveur Hytale, et il risque de mal cohabiter avec son chargeur de classes.

Sources : [Spring Modulith, Verification](https://docs.spring.io/spring-modulith/reference/verification.html) ; [Spring Modulith, Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html) ; [Drotbohm, jMolecules 2.0](http://odrotbohm.github.io/2025/11/jmolecules-2.0-stereotypical/) ; [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html) ; [Gradle, Structuring Builds](https://docs.gradle.org/current/userguide/best_practices_structuring_builds.html) ; [JetBrains, Modular Monolith (fév. 2026)](https://blog.jetbrains.com/idea/2026/02/migrating-to-modular-monolith-using-spring-modulith-and-intellij-idea/).

## 2. Héritage ou composition

**Recommandation.** Au plus **un** niveau de classe abstraite, qui fixe le cycle de vie (méthode patron). Tout le comportement partagé passe par des **collaborateurs composés** et par des transitions de machine à états enregistrées. Pas de chaîne `Basic → Interact → Structure → …`.

**Pourquoi.** Bloch, *Effective Java* 3ᵉ éd. (2018) :
- l'item 18 privilégie la composition, car l'héritage casse l'encapsulation ;
- l'item 19 demande de concevoir et documenter pour l'héritage, ou de l'interdire (`final`) ;
- l'item 20 préfère les interfaces aux classes abstraites, avec une « implémentation squelette » abstraite en complément si besoin ;
- l'item 25 veut une seule classe de premier niveau par fichier.

Une classe abstraite est le bon outil quand il faut un **état partagé et un invariant de cycle de vie** que les méthodes par défaut d'une interface ne peuvent pas porter, faute de champs. Les classes scellées (JEP 409, Java 17) conviennent aux ensembles **fermés**, par exemple les types de requête ou les états, avec un `switch` exhaustif. Elles ne conviennent pas aux jobs si d'autres doivent pouvoir en ajouter. Nystrom (*Game Programming Patterns*) propose le patron **Component** contre les hiérarchies profondes et larges d'entités de jeu, et le patron **Type Object** pour varier le comportement par des données plutôt que par des sous-classes. MineColonies lui-même compose déjà une partie de son IA : ses workers enregistrent des `AITarget` dans un `TickRateStateMachine` au lieu de redéfinir `tick()`.

**Pour HyColony.** `kernel/ai` porte déjà `TickRateStateMachine` et `AITarget`, et `Job` est une seule classe abstraite. On garde `Job` et `JobAI` comme uniques bases abstraites. On extrait les capacités de MC sous forme de petits composants réutilisables, chacun enregistrant ses transitions dans la machine :
- `ToolRequirement` pour `checkForToolOrWeapon` ;
- `InventoryDump` pour `dumpInventory` ;
- `WalkToBuilding` ;
- `StructureProgress` pour `AbstractEntityAIStructure`.

C'est le prolongement de `BuilderStock`, `BuilderWalker` et `BuildCompletion`. Chaque méthode MC reste citée dans la Javadoc du composant. La fidélité ne souffre pas : les mêmes états et les mêmes transitions sont simplement assemblés autrement. Une bibliothèque de machine à états externe (Spring Statemachine, etc.) serait de trop : celle de MC est portée et fidèle.

Sources : Bloch, *Effective Java* 3ᵉ éd., Addison-Wesley 2018, items 18-20 et 25 ; [JEP 409 Sealed Classes](https://openjdk.org/jeps/409) ; [Nystrom, Component](https://gameprogrammingpatterns.com/component.html) et [Type Object](https://gameprogrammingpatterns.com/type-object.html) ; [MC tickratestatemachine](https://github.com/ldtteam/minecolonies/tree/version/main/src/main/java/com/minecolonies/api/entity/ai/statemachine/tickratestatemachine).

## 3. Détecter et prévenir la duplication

**Recommandation.** Ajouter PMD CPD au build avec un seuil d'environ 100 tokens, en rapport d'abord, et bloquer seulement sur le code du cœur. Appliquer la « règle de trois » : on factorise à la troisième occurrence, ou quand la duplication gêne réellement un changement.

**Pourquoi.**
- CPD fait partie de PMD. Il repère les copies à l'échelle des tokens, avec un seuil par défaut de 50 tokens, et 100 tokens correspondent à peu près à 5 à 10 lignes.
- La barrière « Sonar way » échoue au-delà de **3 %** de lignes dupliquées sur le code nouveau, et ignore ce critère sous 20 nouvelles lignes.
- IntelliJ détecte aussi les doublons (inspection *Duplicated code fragment*), mais seulement dans l'IDE.

Côté conception, Dodds (AHA, *Avoid Hasty Abstractions*, 2020) et Metz (« *prefer duplication over the wrong abstraction* », 2016) mettent en garde contre la factorisation prématurée. La règle de trois vient de Fowler, *Refactoring* 2ᵉ éd. (2018).

**Pour HyColony.** 20 à 30 jobs quasi identiques poussent au copier-coller entre agents. CPD fait remonter ces copies en relecture. La règle de trois évite de figer une abstraction dès le deuxième job, avant de voir comment le pêcheur diffère du bûcheron.

Sources : [Gradle PMD plugin](https://docs.gradle.org/current/userguide/pmd_plugin.html) ; [aaschmid/gradle-cpd-plugin](https://github.com/aaschmid/gradle-cpd-plugin) ; [Sonar, quality gates](https://docs.sonarsource.com/sonarqube-server/quality-standards-administration/managing-quality-gates/introduction-to-quality-gates) ; [Dodds, AHA Programming](https://kentcdodds.com/blog/aha-programming) ; [Metz, The Wrong Abstraction](https://sandimetz.com/blog/2016/1/20/the-wrong-abstraction).

## 4. API d'extension et registres

**Recommandation.** Un job ou un bâtiment se **déclare en un seul endroit**, par une entrée de registre : identifiant, fabrique, fabrique de vue, désérialiseur. On ne l'éparpille pas dans des `switch`.

**Pourquoi.** Dans MineColonies, `api` contient les interfaces et les registres, et `core`/`apiimp` les implémentations. La PR #3777 (marchermans) a remplacé la réflexion par des lambdas : un `JobEntry` est construit avec `setJobProducer(JobBuilder::new)`, `setJobViewProducer(...)` et `setRegistryName(...)`, puis enregistré dans `ModJobsInitializer`. Le chargement d'une sauvegarde passe par le nom de registre. C'est le patron Type Object de Nystrom. Pour versionner une API publique, on suit SemVer, on garde l'API dans un paquet ou module séparé et on n'y expose que des interfaces et des records.

**Pour HyColony.** `JobRegistry`/`JobType` existent déjà. Il faut y ajouter la fabrique de `Job` et de sa vue, pour que « ajouter un job » tienne en un record et une ligne d'enregistrement par fonctionnalité, comme `ConstructionBuildingTypes.register`. Pas de module `api` séparé tant qu'aucun mod tiers ne le demande (YAGNI). En revanche, les identifiants de registre sont déjà un contrat de sauvegarde : un renommage passe par `MigrationChain`.

Sources : [MC PR #3777 Feature/api](https://github.com/ldtteam/minecolonies/pull/3777) ; [MC ModJobsInitializer](https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/apiimp/initializer/ModJobsInitializer.java) ; [IMinecoloniesAPI](https://github.com/ldtteam/minecolonies/blob/version/main/src/main/java/com/minecolonies/api/IMinecoloniesAPI.java) ; [SemVer 2.0](https://semver.org/).

## 5. Outillage

| Outil | Coût | Bénéfice | Avis |
|---|---|---|---|
| **PMD CPD** | Très faible (PMD est déjà là) | Signale le copier-coller entre jobs | **Oui** |
| **JaCoCo 0.8.14+** | Faible, rapport seulement | Montre les branches du cœur jamais testées. Java 25 est officiellement pris en charge depuis 0.8.14 (oct. 2025). | **Oui**, sans seuil bloquant au début |
| **PIT (pitest 1.30, gradle-pitest-plugin)** | Moyen : lent, à lancer à la demande ou sur un paquet | Mesure si les tests détectent une constante MC modifiée, ce qui colle à l'exigence de fidélité. Prend en charge Java 25 (ASM 9.10). | **Oui, ciblé** (formules, `kernel/ai`) |
| **jqwik 1.10** | Faible techniquement | Tests de propriétés pour les formules et la persistance | **Non** : projet en maintenance pure, et depuis 1.10 il inclut une clause interdisant l'usage par des agents IA, incompatible avec le flux de travail. À défaut, `@ParameterizedTest` de JUnit. |
| **ArchUnit (règles en plus)** | Très faible | Frontières entre fonctionnalités, profondeur d'héritage | **Oui** |

Sources : [JaCoCo 0.8.14](https://www.jacoco.org/jacoco/trunk/doc/changes.html) ; [pitest releases](https://github.com/hcoles/pitest/releases) ; [gradle-pitest-plugin](https://github.com/szpak/gradle-pitest-plugin) ; [jqwik](https://github.com/jqwik-team/jqwik) et [guide 1.10.1](https://jqwik.net/docs/current/user-guide.html) ; [JAVAPRO, PIT (jan. 2026)](https://javapro.io/2026/01/21/test-your-tests-mutation-testing-in-java-with-pit/).

## Actions prioritisées pour HyColony

1. **ArchUnit** : `beFreeOfCycles` sur tous les paquets de premier niveau du cœur, plus une matrice de dépendances autorisées par fonctionnalité, à la manière de Spring Modulith.
2. **ArchUnit** : interdire une chaîne d'héritage de plus d'un niveau sous `Job`/`JobAI` et des bâtiments. On compose au lieu d'hériter.
3. Avant le 2ᵉ job : extraire de `BuilderAI` les capacités génériques de MC (`ToolRequirement`, `InventoryDump`, `WalkToBuilding`) en composants qui enregistrent leurs `AITarget`, chacun avec sa source MC.
4. Enrichir `JobType` (fabrique de job, fabrique de vue, désérialiseur) pour qu'un job se déclare en un seul enregistrement, comme `JobEntry` dans MC.
5. Ajouter **PMD CPD** (seuil 100 tokens) à `./gradlew build`, avec une liste d'exceptions qui ne peut que rétrécir comme les autres.
6. Ajouter **JaCoCo** en rapport, sans seuil bloquant.
7. Ajouter **PIT** en tâche manuelle, ciblée sur les formules portées et `kernel/ai`.
8. Écrire la règle de trois dans CLAUDE.md : on factorise à la troisième occurrence. C'est un garde-fou : il faut l'accord de l'utilisateur.
