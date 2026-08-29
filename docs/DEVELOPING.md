# Developing Leadman

## Running it locally

There is no sideloading path for a RuneLite plugin jar — the client has to be started
with the plugin registered as a built-in. `src/test/java/com/leadman/LeadmanPluginLauncher.java`
does that, and it lives in the test source set so it never ships inside the plugin jar.

```sh
./run-dev.sh          # Git Bash / WSL
run-dev.cmd           # cmd or PowerShell
```

Either script picks up a portable JDK from `.tools/` if one is there, so no system-wide
Java install is required. With JDK 11 already on your `PATH`, `./gradlew run` is enough.

A successful start prints this, which is the plugin loading its ruleset:

```
INFO com.leadman.rules.RuleRepository - Leadman: loaded 422 item rules and 62 spell rules
```

If that line is missing, the plugin did not start — check for a stack trace above it.

### Getting a portable JDK

`.tools/` is gitignored, so this is a per-machine step:

```sh
mkdir -p .tools && cd .tools
curl -L -o jdk11.zip \
  "https://api.adoptium.net/v3/binary/latest/11/ga/windows/x64/jdk/hotspot/normal/eclipse"
# verify against the checksum at
#   https://api.adoptium.net/v3/assets/latest/11/hotspot?os=windows&architecture=x64&image_type=jdk
unzip jdk11.zip && rm jdk11.zip
```

Swap `windows/x64` for your platform. Any JDK 11 works; Temurin is just convenient.

### From an IDE

Run `com.leadman.LeadmanPluginLauncher` directly. IntelliJ picks it up with no extra
configuration, and you get breakpoints, which matters for the menu-entry code — it runs
on every frame and is awkward to reason about from logs.

## Logging in

**A dev-built client shares your real `~/.runelite` directory.** It will load your
existing config and every plugin you already have installed from the Plugin Hub. That is
usually what you want, but be aware Leadman is running alongside all of them.

**Jagex accounts need the official launcher.** Running `RuneLite.main()` directly skips
the launcher, which is what normally supplies the Jagex session, so the login screen will
only offer the legacy email-and-password form. Options:

- Use a legacy (non-Jagex) account for development.
- Or export the four `JX_` variables the launcher would have set — `JX_ACCESS_TOKEN`,
  `JX_REFRESH_TOKEN`, `JX_CHARACTER_ID`, `JX_DISPLAY_NAME` — before launching. You can
  read them off a normal launcher-started client's process environment.

## Testing a gate by hand

The unit tests in `GateRulesTest` cover the rule engine. What they cannot cover is
anything that touches the live client: menu entries, the GE search interface, the unlock
popup, ground-item ownership. Those need a real login.

| What to try | Expected |
|---|---|
| GE-search an item you cannot make yet | It does not appear in the results at all |
| `Eat` a shark below 80 Cooking | No `Eat` option; chatbox explains why |
| `Wear` an amulet of glory below 80 Crafting | No `Wear` option |
| Rub a glory at 80 Crafting but below 68 Magic | Wears fine, teleport options blocked |
| Buy from an NPC shop, then GE-search that item | Still locked — shops do not unlock trade |
| Take an item another player dropped | No `Take` option |
| Level up a gating skill | Unlock popup, batched if it crosses several items |

### Resetting a profile

Progress is one file per account. Delete it to start clean:

```sh
rm ~/.runelite/leadman/<accountHash>.json
```

Handy when testing the first-login path, which deliberately seeds the baseline silently
rather than firing a popup for every already-unlocked item.

### Faking levels

Gates read `client.getRealSkillLevel`, so a stat spell or a boost will not move them —
that is deliberate (docs/DESIGN.md §3.3). To exercise a high-level gate without the
levels, either add the item to `obtained` in the profile JSON to test the trade path, or
temporarily lower the requirement in `overrides.json`.

## Regenerating the ruleset

See the README. The short version:

```sh
node tools/generate-rules.mjs --verify
```

Run it after every game update. A renamed item then surfaces as a verification mismatch
instead of a silent free unlock.

## Building without a local JDK

Everything except running the client works in a container:

```sh
docker run --rm -v "$PWD:/app" -v leadman-gradle-cache:/home/gradle/.gradle \
  -w /app gradle:7.6.4-jdk11 gradle --no-daemon build
```

On Git Bash prefix with `MSYS_NO_PATHCONV=1` and use a Windows-style absolute path for
the mount. The client itself needs a display, so it will not run this way.
