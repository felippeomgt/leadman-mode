# Leadman — build, test, and publish

This file is for developers and contributors. The plugin listing on the RuneLite Plugin Hub
reads [`README.md`](README.md) instead.

## Requirements

- **JDK 11** for local builds
- **Node.js** for ruleset generation (`tools/*.mjs`)
- **Docker** (optional) if you do not want a local JDK

## Build and test

With a local JDK:

```sh
gradle build      # compile, test, produce build/libs/leadman-<version>.jar
gradle test       # tests only
```

Without a local JDK:

```sh
docker run --rm -v "$PWD:/app" -v leadman-gradle-cache:/home/gradle/.gradle \
  -w /app gradle:7.6.4-jdk11 gradle --no-daemon build
```

On Git Bash, prefix with `MSYS_NO_PATHCONV=1` and use a Windows-style absolute path
(`-v "D:/workspace/osrs:/app"`) so the mount is not mangled.

`src/test/java/com/leadman/GateRulesTest.java` exercises the gate engine against the real
generated ruleset. Examples from the design doc are test cases — the suite fails if rules
or data drift from [`docs/DESIGN.md`](docs/DESIGN.md).

## Running locally

There is no sideloading path for a plugin jar — the client must be launched with the
plugin registered as a built-in. `LeadmanPluginLauncher` lives in the test source set so
it never ships inside the jar.

```sh
./run-dev.sh      # Git Bash / WSL
run-dev.cmd       # cmd or PowerShell
./gradlew run     # if JDK 11 is on your PATH
```

The scripts pick up a portable JDK from `.tools/` if present. This needs a display, so it
will not run in the container.

A successful start prints:

```
INFO com.leadman.rules.RuleRepository - Leadman: loaded N item rules and M spell rules
```

See [`docs/DEVELOPING.md`](docs/DEVELOPING.md) for Jagex-account login, profile reset,
manual gate checks, and IDE setup.

### Portable JDK (`.tools/`)

`.tools/` is gitignored:

```sh
mkdir -p .tools && cd .tools
curl -L -o jdk11.zip \
  "https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jdk/hotspot/normal/eclipse"
unzip jdk11.zip && rm jdk11.zip
```

Swap `windows/x64` for your platform.

## Regenerating the ruleset

The bundled ruleset is generated, not hand-written:

```sh
node tools/generate-rules.mjs            # writes src/main/resources/com/leadman/leadman-rules.json
node tools/generate-rules.mjs --verify   # also checks names against the live wiki item mapping
node tools/fetch-ge-tradeables.mjs       # refreshes ge-tradeables.json and ge-item-ids.json
node tools/coverage-report.mjs           # GE items without a rule -> docs/ge-unmapped.txt
```

Run `--verify` after every game update. Corrections go in
[`src/main/resources/com/leadman/overrides.json`](src/main/resources/com/leadman/overrides.json),
which is layered at runtime and never regenerated.

### Rules are keyed by name, not item id

Item ids churn every update. Names are stable, and normalising them collapses charge,
degrade, ornament, and poison variants. See `ItemNames` and its mirror in the generator.

## Publishing to the Plugin Hub

The hub does not host jars — it builds from your public repository at a pinned commit.

1. **Repository must be public** — [`felippeomgt/leadman-mode`](https://github.com/felippeomgt/leadman-mode)
2. **`icon.png`** at the repository root (48×72) for the Plugin Hub listing
3. **Commit and note the full 40-character SHA** you want published
4. **Fork [`runelite/plugin-hub`](https://github.com/runelite/plugin-hub)** and add
   `plugins/leadman`:

   ```
   repository=https://github.com/felippeomgt/leadman-mode.git
   commit=<full 40-character SHA>
   ```

5. **Open a pull request** against `runelite/plugin-hub`. CI builds and reviews the plugin.
   Each release is another PR bumping `commit=` plus a `version` bump in `build.gradle`.

Review checklist:

- No runtime network calls (wiki fetch is dev-only in `tools/`)
- No third-party runtime dependencies
- No gameplay automation — menu removal and search filtering only
- `runelite-plugin.properties` points at `com.leadman.LeadmanPlugin`
- BSD 2-Clause license

## Layout

```
src/main/java/com/leadman/
  LeadmanPlugin.java      event wiring, menu blocking, GE filtering
  LeadmanConfig.java      config surface
  rules/                  ruleset model, TradeableIndex, loader
  unlock/UnlockService    gate engine
  ui/                     unlock popup and sidebar panel
src/test/java/com/leadman/
  GateRulesTest.java      design rules as assertions
  LeadmanPluginLauncher   starts RuneLite with the plugin loaded
tools/generate-rules.mjs  ruleset generator
docs/DESIGN.md            game design and gate map
```

Profile state: `~/.runelite/leadman/<accountHash>.json` (obtained items, activities,
popup de-duplication). Fabrication is recomputed from live skill levels on every login.
