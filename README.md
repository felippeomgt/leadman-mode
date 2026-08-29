# Leadman Mode

A RuneLite plugin for a self-imposed challenge: **Ironman conduct on a normal account,
with a Grand Exchange that unlocks item-by-item as you reach the skill level that could
have made each item yourself.**

Lead is softer than bronze. The mode is easier than Bronzeman on acquisition and harder on
consumption.

## The idea in practice

```
You can buy a raw swordfish from a shop, but you cannot sell one on the GE until 50 Fishing.
You can only eat a cooked swordfish once you hit 45 Cooking.
You can wield a looted rune scimitar at 40 Attack, but you cannot trade one until 90 Smithing.
An amulet of glory needs 80 Crafting before you can wear it, and 68 Magic before its teleports fire.
An abyssal whip has no Smithing recipe — equippable uniques trade freely once you can wield them.
Dragon bones need to be obtained once before they unlock on the GE.
```

You still play on a normal account. The plugin does not change server behaviour — it removes
menu options and filters search results so you can hold yourself to the rules without
rolling an Ironman.

## Two independent gates

Every item carries two separate permissions.

| Gate | Unlocked when | What it blocks |
|---|---|---|
| **Trade** | you can fabricate it, or the item falls under an obtain/equip fallback | GE buy, sell, offers; NPC shop buy |
| **Use** | you can fabricate it, or the same fallback applies | Eat, Drink, Wear, Wield, Cast, charge and teleport ops |

**Fabrication** means your real skill levels meet at least one mapped recipe for that item.
Temporary boosts do not count.

**Obtaining** an item unlocks trade for drop-only and supply items that have no skill path.
It never bypasses a fabrication gate — looting a saradomin brew does not let you drink or
trade it until 81 Herblore.

**Equippable unmapped items** (gear with no fabrication rule, like whips and GWD uniques)
trade and shop freely. **Non-equippable unmapped GE items** (bones, herbs, misc supplies)
need one genuine obtain before trade, shop, or use opens up. Buying from a shop does not
count as an obtain.

## What the game already gates vs what Leadman adds

OSRS already enforces Attack, Ranged, Magic, and similar levels on equipment and spells.
Leadman does not duplicate those — a rune scimitar you meet 40 Attack for is fair to wield
even below 90 Smithing.

Leadman fills the gaps the game leaves open:

| Gated by default | Optional toggles (off by default) |
|---|---|
| Cooking gates food | Fletching gates ammunition |
| Herblore gates potions | Runecrafting gates runes |
| Crafting gates wearing jewellery | Smithing gates equipment |
| Magic gates charges and teleports | Smithing gates tools |

Each toggle is per skill. **Strict** turns all use gates on at once. **Bronzeman+** turns
them all off and leaves only the trade gates.

Jewellery splits across two skills on purpose: Crafting shaped the amulet, Magic put the
teleports in it. Turning off the Crafting gate does not hand you the teleports.

## What you get in-game

- **Sidebar panel** — browse items, see which gates apply, search by name.
- **Unlock popup** — when a skill level crosses a fabrication threshold, new GE items appear.
- **Menu blocking** — blocked actions are removed with a chatbox reason (once per reason per session).
- **GE filtering** — locked items do not appear in search results.
- **Per-item overrides** — edit any item's trade, shop, eat, drink, wield, use, activate, or bury gate from the panel.
- **Ironman conduct helpers** — blocks taking other players' drops and player trades.

## Modes

| Mode | Trade gates | Use gates |
|---|---|---|
| **Leadman** (default) | On | Default set above |
| **Strict** | On | All use gates on |
| **Bronzeman+** | On | All use gates off |

## Limits (honour system)

RuneLite cannot enforce server-side rules. Disabling the plugin bypasses everything — the
same honour system Bronzeman runs on.

The plugin reliably blocks actions that go through client menu entries. It cannot retroactively
cancel GE offers placed before the plugin was enabled, or stop actions triggered without a
menu click.

## More detail

- Full rule system and skill map: [`docs/DESIGN.md`](docs/DESIGN.md)
- Build, test, and publish: [`INSTRUCTIONS.md`](INSTRUCTIONS.md)

## License

BSD 2-Clause. See [LICENSE](LICENSE).
