# Leadman Mode

**Ironman discipline on a normal account — with a Grand Exchange that opens one item at a time.**

Leadman is a RuneLite plugin for a self-imposed challenge. You play on a regular account, but hold yourself to Ironman rules: no player trades, no looting other players' drops, and a Grand Exchange that only unlocks items when your skills say you could have made them yourself.

Lead is softer than bronze. Easier to **get** items than Bronzeman. Harder to **use** them without the levels.

> **Beta.** Leadman Mode is in active development. Some items — especially NPC shop stock — may not have been reviewed yet and can be **blocked incorrectly**. If that happens, **override the item in the plugin** (sidebar **⚙ catalog**) so you can obtain or use it in the way that fits your run. If the default rule should change for everyone, please [open an issue on GitHub](https://github.com/felippeomgt/leadman-mode/issues).

**New to the mode?** Read the [**FAQ**](docs/FAQ.md) — it explains shop locks, obtain vs buy, GE behaviour, and other situations that often confuse people at first.

<!-- Screenshots: panel, unlock popup, and catalog — add here when ready -->

## The idea in practice

Leadman is not “Ironman with extra steps.” It is a normal account where **the GE still exists**, but it **opens one item at a time** — only when your account could plausibly have earned that item under Ironman-style rules.

Think of three layers:

1. **Conduct** — no player trading (unless you allow specific names or party members), no taking other players’ drops.
2. **Acquisition** — can you **buy or sell** this on the GE or from an NPC shop? Skill recipes gate trade until you could make the item yourself. Items with no recipe unlock after you **obtain one** legitimately (drop, chest, clue, craft, etc.). **Shop purchases never count as obtained** — that stops shop stock from laundering straight onto the GE. A small set of **shop-only** items (where the shop really is the only source — e.g. teleport card) is exempt: buying those counts as obtaining them.
3. **Consumption** — can you **eat, drink, wear, wield, chop, or spend charges** on it? Trade and use are separate permissions. By default, food, potions, jewellery, and teleports/charges also need the skills that would create or enchant the item — even when trade is already unlocked.

### What that feels like in game

This mode has a different progression than most Ironman guides assume. Extra skilling and shop locks close the usual shortcuts — there is no way around the grind. For example:

- **Early food.** A normal Ironman often buys jugs of wine from Fortunato in Draynor. Here you cannot **drink** wine until you have **35 Cooking** (the level to make it yourself). The same applies to food in general with the default gates on. You can only **buy** food from shops after you have legitimately obtained that item once (drop or create) — except shop-only items such as the teleport card or chronicle, where buying *is* the obtain path.
- **Early ranged.** Many Ironmen hit 28 Ranged and grab a bone crossbow from Nardok. Here you need to **obtain** a Dorgeshuun crossbow first — for example from **Dorgesh-Kaan average chests** at **52 Thieving** — before Nardok’s shop or the GE opens up for it.
- **Magic and runes.** **High Level Alchemy** stays blocked until you can craft **nature runes** yourself (Runecrafting gates runes is on by default). The list goes on.

**Smithing and equipment.** The mode originally also blocked **wearing** gear behind smithing levels. That is **off by default** now: smithing only gates **buying and selling**, while wielding follows normal OSRS Attack and Defence requirements. Requiring 90 Smithing just to equip a rune scimitar was unbalanced and not fun. You can still turn **Smithing gates equipment** on if you want the full restriction — but it is miserable, and hopefully Smithing gets rebalanced someday so proper progression becomes viable.

When a gate opens, the plugin shows a popup and a coloured chat message.

## How restrictions work

Each item can be blocked on several **actions**, independently:

| Action | What the plugin blocks |
| --- | --- |
| **Trade** | Grand Exchange buy, sell, and offers |
| **Shop** | Buying from NPC shops |
| **Eat / Drink** | Consuming food and potions |
| **Wear / Wield** | Equipping armour, weapons, and jewellery |
| **Use** | Chopping with axes and other tool actions where a skill gate applies |
| **Teleport / charges** | Rubbing jewellery, firing teleports, spending charges |
| **Bury** | Burying bones and ashes (only when you add a custom rule) |
| **Cast** | Spells whose runes you cannot craft (when the rune gate is on) |

**Trade** and **use** are separate — see [The idea in practice](#the-idea-in-practice) above. This table is the quick reference for which menu actions each gate affects.

Items with no skill recipe (boss drops, uniques, and misc supplies) unlock for trade, shop, and use **once you obtain one**.

## Examples

| Your levels / state | Item | Trade (GE + shop buy) | Use (eat, wear, etc.) |
| --- | --- | --- | --- |
| 49 Fishing | Raw swordfish | Locked — needs 50 Fishing | N/A (raw fish is not eaten) |
| 50 Fishing, 44 Cooking | Raw swordfish | Unlocked | N/A |
| 44 Cooking | Swordfish (cooked) | Locked — needs 45 Cooking | Locked — needs 45 Cooking |
| 45 Cooking | Swordfish (cooked) | Unlocked | Unlocked — can eat |
| 40 Attack, 89 Smithing | Rune scimitar | Locked — needs 90 Smithing | Wield OK — game requires 40 Attack |
| 40 Defence, 89 Smithing | Rune platebody | Locked — needs 99 Smithing | Wield OK — game requires 40 Defence |
| 40 Herblore + obtained brew | Saradomin brew | Unlocked (you obtained one) | Locked — needs 81 Herblore to drink |
| 70 Attack + obtained whip | Abyssal whip | Unlocked once obtained from a monster drop | Wield OK at 70 Attack |

See [The idea in practice](#the-idea-in-practice) for more grounded early-game examples (wine, bone crossbow, High Alchemy).

## Use gate toggles

These live under **Configuration → Leadman Mode → Use gates**. Each toggle adds a skill check on top of what the game already enforces. All are independent — turn on Fletching without touching Runecrafting.

| Toggle | Default | What it means | Example |
| --- | --- | --- | --- |
| **Cooking gates food** | On | You can only **eat** food you could cook | Shark needs 80 Cooking to eat, even if you bought it at 1 Cooking |
| **Herblore gates potions** | On | You can only **drink** potions you could brew | Saradomin brew needs 81 Herblore to drink |
| **Crafting gates wearing jewellery** | On | You can only **wear** jewellery you could craft | Amulet of glory needs 80 Crafting to equip |
| **Magic gates charges and teleports** | On | Spending charges needs the **enchant** level, separate from Crafting | Glory equips at 80 Crafting, but teleports need 68 Magic |
| **Fletching gates ammunition** | On | You must also be able to **fletch** ammo before use | Rune arrows need 75 Fletching on top of 40 Ranged |
| **Runecrafting gates runes** | On | You must be able to **craft** every rune a spell consumes | High Alchemy blocked until you can craft nature runes |
| **Smithing gates equipment** | Off | **Optional:** wearing armour/weapons also needs the **smithing** level to smith them | With toggle **on**, rune scimitar needs 90 Smithing to wield; with default **off**, 40 Attack is enough |

With defaults, food, potions, jewellery, charge/teleport actions, ammunition, and rune casting are gated. Trade always follows the fabrication ladder. Wielding armour and weapons follows normal OSRS Attack/Defence requirements unless you turn on **Smithing gates equipment**.

Metal axes still require the matching **Woodcutting** level to chop — that comes from the item rule, not a config toggle. Pickaxes are not in the smithing ladder.

## Ironman conduct

Under **Configuration → Leadman Mode → Ironman conduct**:

| Option | Default | Effect |
| --- | --- | --- |
| **Block others' ground items** | On | Removes Take on other players' drops |
| **Block player trading** | On | Removes Trade with on players |
| **Allow trade with** | *(empty)* | Comma-separated names still allowed to trade with you while the block is on |
| **Allow trade with party members** | Off | Also allow trading with your RuneLite party (Party plugin, same passphrase on both clients) |
| **Disable Grand Exchange** | Off | Blocks every GE action, regardless of unlock progress |

## Customize your experience

Leadman Mode is about **leading your journey** — you are not locked into our ladder. The plugin lets you **change restrictions per item**, so you can shape a progression that matches how you want to play.

Open the sidebar **⚙ catalog** to browse every item, see which actions are open or locked, and click any item to edit its rules. For each item you can override **trade, shop, eat, drink, wield, use, teleport, and bury** independently, with your own skill levels. Save an override, or reset that item back to the generated default. **Reset all custom rules** clears every override at once without wiping your profile progress.

**Example — Prayer on bones:** If you want to gate Prayer levelling so you can only bury certain bones after a specific Prayer level — for instance, **Dragon bones only after 40 Prayer** — open the catalog, find Dragon bones, enable **Bury** (and any other actions you want gated), set **Prayer 40**, and save. The plugin enforces it on your client.

Other examples: gate big bones behind 30 Prayer for bury, trade, and use; keep a whip trade-locked until 85 Attack even though the game allows 70.

The main sidebar shows your **20 most recent unlocks** (view-only). The catalog is where you search the full list and customize.

Locked items in your inventory appear slightly dimmed. **Examine** an item to see which actions are still restricted.

## Honour system

Everything is client-side, like Bronzeman. Disabling the plugin removes the restrictions. The plugin removes menu options and filters GE search — it cannot change server behaviour or cancel offers already placed.

## For developers

Build, test, and Plugin Hub publishing: [`INSTRUCTIONS.md`](INSTRUCTIONS.md)

Contributing and bug reports: [`CONTRIBUTING.md`](CONTRIBUTING.md)

Player FAQ (shops, obtain, GE, duo trade): [`docs/FAQ.md`](docs/FAQ.md)

Full design (including backlog features): [`docs/DESIGN.md`](docs/DESIGN.md)

## License

BSD 2-Clause — see [LICENSE](LICENSE).
