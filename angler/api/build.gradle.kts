plugins { id("hy.java-core") }

// HyAngler's public api (spec 2026-10-04-hyangler-design § 8): pure Java without project dependency, so another mod
// compiles against it alone. HyAngler's jar carries it.
group = "dev.hyangler"
