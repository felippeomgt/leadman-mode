# Leadman

A RuneLite plugin for a self-imposed challenge mode: **Ironman conduct on a normal
account, with a Grand Exchange that unlocks item-by-item as you reach the skill level
that could have made the item yourself.**

Lead is softer than bronze. The mode is easier than Bronzeman on acquisition and harder
on consumption.

```
You can buy a Raw swordfish from a shop, but you cannot sell one on the GE
until 50 Fishing.  You can only eat a Swordfish once you hit 45 Cooking.
You can wield a looted Rune scimitar at 40 Attack, but you cannot trade one
until 90 Smithing.  An Amulet of glory needs 80 Crafting before you can put
it on, and 68 Magic before its teleports will fire.
```

The full rule system, the skill-by-skill gate map and the remaining open question live in
[`docs/DESIGN.md`](docs/DESIGN.md). Read that first — this file covers building, testing
and publishing.

## Two gates

Every item carries two independent booleans.

| Gate | Unlocked when | Blocks |
|---|---|---|
| **TRADE** | you can fabricate it, **or** you have obtained one | GE buy, sell, offers |
| **USE** | you can fabricate it | `Eat` `Drink` `Wear` `Wield` `Cast`, charge ops |

`everObtained` appears in TRADE and not in USE on purpose: looting an item never grants
the right to use it. Buying from an NPC shop sets neither, so shop stock can never
launder an item onto the Grand Exchange.

**USE applies only where the game provides no requirement of its own.** A rune scimitar
already asks for 40 Attack, rune arrows already ask for 40 Ranged, and every spell carries
a Magic level — those requirements are the permission, so a looted item you meet them for
is fair to use. Nothing at all stands between you and eating a shark, drinking a brew, or
putting on an amulet, so Leadman supplies the requirement the game left out.

| Gated by default | Off by default, one toggle each |
|---|---|
| Cooking gates food | Fletching gates ammunition |
| Herblore gates potions | Runecrafting gates runes |
| Crafting gates wearing jewellery | Smithing gates equipment |
| Magic gates charges and teleports | Smithing gates tools |

The toggles are per skill, so tightening Fletching does not drag Runecrafting along with
it. `Strict` mode turns all of them on at once; `Bronzeman+` turns them all off and leaves
only the trade gates.

Jewellery splits across two toggles because two skills made it: Crafting shaped the
amulet, Magic put the teleports in it. Turn the Crafting gate off and a glory you may now
wear still will not teleport you until 68 Magic.

## Build and test

Requires **JDK 11**. If you do not have one installed, everything below runs in a
container instead — no local toolchain needed:

```sh
docker run --rm -v "$PWD:/app" -v leadman-gradle-cache:/home/gradle/.gradle \
  -w /app gradle:7.6.4-jdk11 gradle --no-daemon build
```

On Git Bash, prefix that with `MSYS_NO_PATHCONV=1` and use an absolute Windows-style
path (`-v "D:/workspace/osrs:/app"`) so the mount is not mangled.

With a local JDK it is just:

```sh
gradle build      # compiles, runs the tests, produces build/libs/leadman-<version>.jar
gradle test       # tests only
```

`src/test/java/com/leadman/GateRulesTest.java` exercises the gate engine against the real
generated ruleset. Every example from the original brief is a test case, so the suite
fails if the rules or the data drift away from `docs/DESIGN.md`.

## Running it in RuneLite

There is no sideloading path for a plugin jar — the client has to be launched with the
plugin registered as a built-in. `LeadmanPluginLauncher` does exactly that, and it lives
in the test source set so it never ships inside the jar.

```sh
gradle run
```

That needs a display, so run it on your desktop rather than in the container. From an IDE,
run `com.leadman.LeadmanPluginLauncher` directly — IntelliJ picks it up with no extra
configuration. Log in, and Leadman appears in the plugin list and as a padlock icon in the
sidebar.

Worth checking by hand, since none of it is unit-testable:

- Search the GE for an item you cannot make yet — it should not appear in the results.
- Try to eat a shark below 80 Cooking; the `Eat` option should be gone and the chatbox
  should say why.
- Wear an amulet of glory below 80 Crafting, then rub one below 68 Magic.
- Buy something from a shop, then search for it on the GE — it must still be locked.
- Drop an item as another player and try to take it.

## Regenerating the ruleset

The bundled ruleset is generated, not hand-written. Most OSRS requirements are ladders —
smithing is one base level per metal plus a fixed offset per item shape, jewellery is one
level per gem per slot — so the generator encodes the ladders and expands them.

```sh
node tools/generate-rules.mjs            # writes src/main/resources/com/leadman/leadman-rules.json
node tools/generate-rules.mjs --verify   # also checks every name against the live wiki item mapping
```

`--verify` fetches the OSRS Wiki prices API item mapping and reports any generated name
that does not correspond to a real item. It currently reports zero mismatches across 422
item rules and 62 spell rules. Run it after every game update — a rename then shows up as
a mismatch instead of a silent free unlock.

Corrections go in [`src/main/resources/com/leadman/overrides.json`](src/main/resources/com/leadman/overrides.json),
which is layered on top at runtime and is never regenerated. An override replaces the
generated rule for that item outright rather than merging with it, so a fix is always
predictable.

### Rules are keyed by name, not item id

Item ids churn every update and would need a regenerated table each patch. Names are
stable, and normalising them collapses charge, degrade, ornament and poison variants for
free — `Amulet of glory(6)`, `Amulet of glory (t4)` and `Amulet of glory` are one rule.
See `ItemNames` and its mirror in the generator; both sides must normalise identically or
every dosed potion looks like a missing item.

## Publishing to the Plugin Hub

The hub does not host jars — it builds from your repository at a pinned commit.

1. **Push this repo to a public GitHub repository.** The hub will not accept a private
   one, and it builds the source rather than trusting an artifact.
2. **Add an `icon.png`** at the repository root, 128×128, if you want a listing image.
3. **Commit and note the full 40-character SHA** of the commit you want published. Tagging
   it (`v1.0.0`) is good practice, but the hub pins the SHA, not the tag.
4. **Fork [`runelite/plugin-hub`](https://github.com/runelite/plugin-hub)** and add a
   single file named after your plugin, `plugins/leadman`, containing:

   ```
   repository=https://github.com/<you>/leadman.git
   commit=<the full 40-character SHA>
   ```

5. **Open a pull request** against `runelite/plugin-hub`. Their CI builds the plugin,
   checks it, and a reviewer looks it over. Every later release is another PR bumping the
   `commit=` line — and a `version` bump in `build.gradle`, or the hub will not treat it as
   a new release.

Things the review will care about, and where this plugin stands:

- **No runtime network calls.** The only fetch is in `tools/generate-rules.mjs`, which is a
  development script and is not on the plugin's classpath. Clean.
- **No third-party runtime dependencies.** RuneLite and Lombok are `compileOnly`; JUnit and
  Mockito are test-only. Clean.
- **Nothing that automates gameplay.** Leadman only removes menu options and filters a
  search. Restriction plugins of this shape are already on the hub.
- **`runelite-plugin.properties` must name the plugin class** — it points at
  `com.leadman.LeadmanPlugin`.

## Layout

```
src/main/java/com/leadman/
  LeadmanPlugin.java      event wiring, menu blocking, GE filtering, ground items
  LeadmanConfig.java      the config surface
  LeadmanMode.java        Bronzeman+ / Standard / Strict
  rules/                  the ruleset model and its loader
  unlock/UnlockService    the gate engine -- everything resolves through here
  ui/                     unlock popup and the sidebar panel
src/test/java/com/leadman/
  GateRulesTest.java      the design rules, as assertions
  LeadmanPluginLauncher   starts RuneLite with the plugin loaded
tools/generate-rules.mjs  ruleset generator
docs/DESIGN.md            the game design and gate map
```

Profile state is one file per account at `~/.runelite/leadman/<accountHash>.json`,
holding obtained items, activity flags and popup de-duplication. What is *fabricable* is
never persisted — it is recomputed from live skill levels on every login, so it cannot
drift out of sync with the account or need migrating when the ruleset changes.

## What is enforced, and what is not

RuneLite cannot change server behaviour. Every restriction is client-side and defeatable
by disabling the plugin — the same honour system Bronzeman runs on.

Enforceable, because the action goes through a menu op the client controls:

- removing `Eat` / `Drink` / `Wear` / `Wield` / `Take` / `Trade with` entries
- blocking casts by filtering spell menu entries
- blocking charge and teleport options on jewellery
- overriding GE search results, and blocking offers on locked ids

Advisory only: anything triggered without a menu click, offers placed before the plugin
was enabled, and items already held when a rule tightens.

When something is blocked the plugin says why, once per reason per session. A silently
dead menu entry reads as a bug.

## Status

Implemented and building against RuneLite 1.12.37, with 15 passing tests.

The two-gate engine, the ruleset loader with overrides, GE search filtering, menu blocking
for use/trade/cast/charge/take/player-trade, ground-item ownership checks, obtain detection
with the shop carve-out, batched unlock popups, per-account persistence, the sidebar
browser and the custom rule editor.

Not yet mapped, tracked in `overrides.json`: cannonball tiers above steel, teleport
tablets, crossbows and bolt tips, and quest-completion `ACTIVITY` paths. Barrows, God Wars
and boss uniques are intentionally absent — with no recipe they fall through to drop
unlocks, which is the correct behaviour.

One design question in [`docs/DESIGN.md` §11](docs/DESIGN.md) is still open: the
quest-item allowlist. `questItemBypass` is on by default but the list behind it is empty,
so nothing bypasses anything yet — a quest that hands you a gated item and asks you to
consume it can still stall.
