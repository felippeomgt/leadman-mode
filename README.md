# Leadman Mode

**Ironman discipline on a normal account — with a Grand Exchange that opens one item at a time.**

Leadman is a RuneLite plugin for a self-imposed challenge. You play on a regular account, but hold yourself to Ironman rules: no player trades, no looting other players' drops, and a Grand Exchange that only unlocks items when your skills say you could have made them yourself.

Lead is softer than bronze. Easier to **get** items than Bronzeman. Harder to **use** them without the levels.

**New to the mode?** Read the [**FAQ**](docs/FAQ.md) — it explains shop locks, obtain vs buy, GE behaviour, and other situations that often confuse people at first.

<!-- Screenshots: panel, unlock popup, and catalog — add here when ready -->

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

**Trade** and **use** are separate. Meeting a Fishing level unlocks buying and selling raw fish on the GE — it does not let you eat the cooked version. Looting an item never grants the right to use it. Buying from a shop does not count as “obtained” and cannot launder an item onto the GE.

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

When you level up and unlock something, the plugin shows a popup and a coloured chat message.

## Use gate toggles

These live under **Configuration → Leadman Mode → Use gates**. Each toggle adds a skill check on top of what the game already enforces. All are independent — turn on Fletching without touching Runecrafting.

| Toggle | Default | What it means | Example |
| --- | --- | --- | --- |
| **Cooking gates food** | On | You can only **eat** food you could cook | Shark needs 80 Cooking to eat, even if you bought it at 1 Cooking |
| **Herblore gates potions** | On | You can only **drink** potions you could brew | Saradomin brew needs 81 Herblore to drink |
| **Crafting gates wearing jewellery** | On | You can only **wear** jewellery you could craft | Amulet of glory needs 80 Crafting to equip |
| **Magic gates charges and teleports** | On | Spending charges needs the **enchant** level, separate from Crafting | Glory equips at 80 Crafting, but teleports need 68 Magic |
| **Fletching gates ammunition** | Off | You must also be able to **fletch** ammo before use | Rune arrows need 75 Fletching on top of 40 Ranged |
| **Runecrafting gates runes** | Off | You must be able to **craft** every rune a spell consumes | High Alchemy blocked until you can craft nature runes |
| **Smithing gates equipment** | Off | **Optional:** wearing armour/weapons also needs the **smithing** level to smith them | With toggle **on**, rune platebody needs 99 Smithing to wear; with default **off**, 40 Defence is enough |

With defaults, food, potions, jewellery, and charge/teleport actions are gated. Trade always follows the fabrication ladder. Wielding armour and weapons follows normal OSRS Attack/Defence requirements unless you turn on **Smithing gates equipment**.

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

Leadman Mode is about **leading your journey** — you are not locked into our ladder. Open the sidebar **⚙ catalog** to browse every item, see which actions are open or locked, and click any item to edit its rules.

For each item you can override **trade, shop, eat, drink, wield, use, teleport, and bury** independently, with your own skill levels. Save an override, or reset that item back to the generated default. **Reset all custom rules** clears every override at once without wiping your profile progress.

Want a Prayer progression on bones? Gate big bones behind 30 Prayer for bury, trade, and use — the plugin enforces it. Want a whip to stay trade-locked until 85 Attack even though the game allows 70? You can do that too.

The main sidebar shows your **20 most recent unlocks** (view-only). The catalog is where you search the full list and customize.

Locked items in your inventory appear slightly dimmed. **Examine** an item to see which actions are still restricted.

## Honour system

Everything is client-side, like Bronzeman. Disabling the plugin removes the restrictions. The plugin removes menu options and filters GE search — it cannot change server behaviour or cancel offers already placed.

## For developers

Build, test, and Plugin Hub publishing: [`INSTRUCTIONS.md`](INSTRUCTIONS.md)

Player FAQ (shops, obtain, GE, duo trade): [`docs/FAQ.md`](docs/FAQ.md)

Full design (including backlog features): [`docs/DESIGN.md`](docs/DESIGN.md)

## License

BSD 2-Clause — see [LICENSE](LICENSE).
