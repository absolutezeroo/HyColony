plugins { id("hy.java-core") }

// HyColony's public api (spec 2026-09-30-hycolony-api-hylens-design): pure Java without project dependency, so an
// addon compiles against it alone. HyColony's jar carries it.
group = "dev.hycolony"
