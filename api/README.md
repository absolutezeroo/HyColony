# HyColony API: a guide for addon authors

HyColony ports MineColonies to Hytale. Its API lets another Hytale mod read the colonies, hear what happens in them and
act on them, without depending on HyColony's internals. HyLens, HyColony's debugging mod (`hylens/`), uses nothing else:
read it as a complete, working example.

The API has two parts:

| Package | What it holds | Depends on |
|---|---|---|
| `dev.hycolony.api` (`api/`) | Colonies, citizens and buildings as snapshots, events, actions, texts | The JDK only |
| `dev.hycolony.plugin.api` (`plugin/`) | The entry point (`HyColonyApi`), and whatever needs Hytale types | The above, plus Hytale |

## 1. Depending on HyColony

Compile against HyColony, but never ship it: HyColony's jar already holds the API, and only one copy of it must be
loaded, or its types would not be the same on both sides.

```kotlin
dependencies {
    compileOnly(files("libs/HyColony-0.1.0.jar")) // or compileOnly(project(":api")) and (":plugin") in this repo
}
```

Then declare the dependency in your `manifest.json`, so that Hytale loads HyColony first:

```json
"Dependencies": { "HyColony:hycolony": "=0.1.0" }
```

Without HyColony, Hytale refuses your plugin and names the missing dependency. If your mod also has an asset pack, it
cannot order the packs and shuts the whole server down; without a pack, only your plugin is left out. See § 8 for the
version to require.

## 2. The entry point and the world's thread

```java
HyColonyApi api = HyColonyApi.get();                 // any thread; throws before HyColony's setup or after its shutdown
Optional<ColonyWorld> colonies = api.world(world);   // empty where HyColony does not run, or is disabled
```

**Every call that names a world must run on that world's thread**, or it throws `IllegalStateException`, as Hytale's
`Store.assertThread` does. A call from off that thread is a programming error, so it is never silently ignored. From
another thread, go through the world first:

```java
world.execute(() -> HyColonyApi.get().world(world).ifPresent(w -> show(w.colonies())));
```

Only three calls are safe from any thread: `HyColonyApi.get()`, `subscribeWorlds(...)` and `Subscription.close()`.

HyColony starts and stops with each world. Hear it with `subscribeWorlds`, called once at your plugin's start. The
worlds already running are not told; ask `world(World)` for them.

```java
api.subscribeWorlds(this, event -> {
    switch (event) {
        case ColonyWorldStarted s -> onStart(s.world()); // on that world's thread
        case ColonyWorldStopped s -> onStop(s.world());  // on the thread removing the world
    }
});
```

## 3. Reading: snapshots

`ColonyWorld` reads one world's colonies:
- `colonies()` and `colony(ref)`;
- `citizens(colony)` and `citizen(ref)`;
- `wellbeing(ref)`, a citizen's saturation, happiness and happiness factors, which is experimental (since 1.1);
- `buildings(colony)`;
- `requests(colony)`, which is experimental.

Every result is a **snapshot**: an immutable record, taken now, that does not follow the colony afterwards. Read again
to see a change, or listen to the events (§ 4). A missing thing is an empty `Optional`, or an empty list for a colony
that does not exist in this world. The API never returns `null`.

Things are named by stable references, which you may keep between ticks and across saves:
- `ColonyRef(world, colonyId)`, where `world` is the world's name;
- `CitizenRef(colony, citizenId)`.

`Pos` is a block cell and `Vec` a body's exact position. A citizen's id may be reused after its death, but a colony's
id never is.

The link with Hytale's entities goes through the plugin API:
- `citizenOf(entityRef, accessor)` returns the citizen whose body an entity is;
- `bodyOf(citizenRef)` returns the loaded body of a citizen, or empty while it is unloaded.

## 4. Events

```java
Optional<Subscription> sub = api.subscribe(this, world, BuildingPlaced.class, e -> onPlaced(e.colony(), e.cause()));
```

Events are records, delivered on the world's thread **after** the change. You hear exactly the type you subscribed
to.

- The stable events are in `dev.hycolony.api.event`:
  - `ColonyCreated`, `ColonyDeleted`;
  - `BuildingPlaced`, `BuildingRemoved`, `BuildingLevelChanged`;
  - `WorkOrderCreated`, `CitizenSpawned`;
  - `DayStarted`, `NightFell`.
- The experimental events are in `dev.hycolony.api.debug`: `CitizenStateChanged`, `JobStateChanged`, `WalkEnded`,
  `StuckAction` and `RequestStateChanged`.

Any other type throws `IllegalArgumentException` (in a world where HyColony does not run, `HyColonyApi.subscribe`
returns an empty `Optional` first).

Events that a player or the colony can cause carry an `Actor cause`: today `Actor.Player(uuid)`, a player, or
`Actor.Colony()`, the colony itself (its AI, a stale building removed…). `Actor` has a third case, `Actor.Plugin`:
switch on all three, since a later version may report a plugin as a cause.

What costs nothing when nobody listens: HyColony builds no event of a type without subscribers.

Your listener is isolated. If it throws, or if its class is gone because your mod was unloaded, HyColony logs it once
as a warning, then at FINE, and serves the other listeners.

### Unsubscribing

`Subscription.close()` is idempotent, never throws, and is safe from any thread. No delivery starts after it returns;
one already running on the world's thread may still finish. You may subscribe or unsubscribe inside a listener.

Prefer `HyColonyApi.subscribe(owner, world, type, listener)`: it closes the subscription itself when your plugin
(`owner`) stops, as `track(owner, …)` and `subscribeWorlds(owner, …)` do. `ColonyWorld.subscribe(type, listener)`
leaves closing it to you.

## 5. Actions

Actions return a sealed `ActionResult`. Today every action is a debugging one, in `ColonyWorld.debug()`, and so is
experimental (§ 7):

```java
ColonyWorld colonies = api.world(world).orElseThrow();
ActionResult result = colonies.debug().walkTo(new Actor.Player(player.getUuid()), citizen, target);
switch (result) {
    case ActionResult.Done d -> ...;
    case ActionResult.Refused r -> player.sendMessage(toMessage(r.reason())); // why, as a text (§ 6)
    case ActionResult.NotFound n -> ...;    // no such colony or citizen
    case ActionResult.Unavailable u -> ...; // exists, but cannot act now (body unloaded, dying)
}
```

The actor matters:
- `Actor.Player(uuid)` is checked: HyColony accepts a server operator or a manager of the colony, and refuses
  anyone else;
- `Actor.Plugin(name)`, named `Group:Name` as in your manifest, is not checked: your plugin answers for what it does;
- `Actor.Colony()` is only ever a cause, so an action asked in its name is refused.

Some things you hold on behalf of your plugin, for example a colony paused through
`api.clock(world).ifPresent(c -> c.pause(owner))` or a citizen followed through `api.track(owner, citizen)`. They end
when that plugin stops: the colony resumes and the following stops, even if your shutdown forgets them. A pause is
never saved.

## 6. Texts

Every text meant for a player, such as a refusal's reason or an alert's detail, is an `ApiText(key, params)`: a
translation key from HyColony's `hycolony.lang`, and its parameters `{p0}`, `{p1}`… Each parameter is a `String` or
another `ApiText`, to translate in turn. HyLens turns one into a Hytale `Message` like this:

```java
static Message of(ApiText text) {
    Message m = Message.translation(text.key());
    for (int i = 0; i < text.params().size(); i++) {
        m = switch (text.params().get(i)) {
            case ApiText nested -> m.param("p" + i, of(nested));
            case Object plain -> m.param("p" + i, plain.toString());
        };
    }
    return m;
}
```

A message that nests translations shows on a label's `.TextSpans`, not on its `.Text`.

A job's name is `JobNames.of(snapshot.job())`, as HyColony's windows show it. This is experimental.

## 7. Experimental parts

What is marked `@Experimental` may change in a minor version. This covers:
- `ColonyWorld.debug()` and the whole `dev.hycolony.api.debug` package: inspection, history, invariant checks,
  timings, walks, debugging actions;
- `requests(colony)` and `RequestSnapshot`;
- `wellbeing(ref)` and `CitizenWellbeing`;
- `JobNames`;
- `HyColonyApi.track` and `clock`, and `ColonyClock`.

These parts expose internal states as text: they suit a debugging tool like HyLens. An addon that uses them follows
HyColony's releases. The annotation is kept at runtime, so a tool can find it.

## 8. Versions

The API has its own semantic version, `ApiVersion.CURRENT`, apart from the mod's version. Each type says `@since` which
version brought it.

- A **major** version breaks addons. That happens when a type or method is removed or renamed, when a component is
  added to a record (it changes the record's constructor), or when a case is added to a sealed type (it breaks your
  exhaustive `switch`es).
- A **minor** version only adds, except in the `@Experimental` parts.
- `ColonyWorld`, `DebugAccess`, `HyColonyApi` and `ColonyClock` are implemented by HyColony, never by an addon, so a
  minor version may add methods to them.

HyColony's build fails on any change to the stable signatures (`api/api.txt`, `plugin/api.txt`) that is not
deliberate.

Check `ApiVersion.CURRENT` at your start, since the manifest pins HyColony's mod version, not its API version:
- an addon that uses only the stable API needs the same major version, and at least the minor version it was built
  against;
- an addon that uses `@Experimental` parts needs the same major and the same minor version.

HyLens uses experimental parts, so it takes the second rule. It checks it in `/hylens selftest`
(`hylens/core/.../ApiCompatibility.java`).

## 9. HyLens as an example

| What you want | Where HyLens does it |
|---|---|
| Reach the API, empty once HyColony stopped | `hylens/plugin/.../HyColonyAccess.java` |
| Read on the world's thread, every few ticks | `hylens/plugin/.../watch/WatchRefreshSystem.java` |
| Follow a citizen, closed with the plugin | `hylens/plugin/.../command/CitizenWatch.java` (`track(owner, …)`) |
| Show an `ApiText` | `hylens/plugin/.../watch/ApiMessages.java` |
| Act as a player, show the result | `hylens/plugin/.../command/MenuActions.java`, `hylens/core/.../menu/ActionReport.java` |
| Pause and step the colonies | `hylens/plugin/.../command/MenuClock.java` |
| Check the invariants | `hylens/plugin/.../check/` |
| Keep the display logic testable, free of Hytale | `hylens/core/` (pure Java, tested against the real `:api`) |
