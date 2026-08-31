# Leadman Mode — FAQ

Common questions about how the plugin decides what you can buy, sell, and use.

More situations will be added here as they come up. If something feels wrong, **Examine** the item in-game — the plugin lists which actions are still locked and why.

---

## Shops and obtaining items

### Why can't I buy something from a shop?

**Examples:** bone weapons from Nardok, elemental staves from Zaff, and similar NPC stock.

Everything is locked behind **obtaining** the item first (or meeting its **skill fabrication** requirement, when one exists). **Buying from a shop does not count as obtaining** — that rule stops shop stock from laundering straight onto the Grand Exchange.

For many items, the game offers a legitimate obtain path **other than** the shop. Leadman expects you to use that path first. Once you have genuinely held the item (loot, clue, chest, drop, etc.), shop buy and GE trade unlock for that item — subject to any skill gates that still apply.

**Elemental staves** — you can obtain them from sources such as [easy clue caskets](https://oldschool.runescape.wiki/w/Reward_casket_(easy)#Standard), so buying from Zaff stays blocked until you have obtained one that way (or another valid source).

**Bone weapons** — e.g. the Dorgeshuun crossbow can come from [Dorgesh-Kaan average chests](https://oldschool.runescape.wiki/w/Chest_(Dorgesh-Kaan_Average)#Zanik's_chests), so Nardok's shop stays blocked until then.

**General rule:** if there is a legit way to obtain an item outside of buying it in a shop, that is what you need to do before shop buy (and usually GE trade) opens up. This applies broadly across the mode — not only to these examples.

---

## Grand Exchange

### Why can't I buy or sell something on the GE?

Usually one of:

1. **Skill gate** — you have not reached the level to fabricate or gather that item (e.g. rune scimitar needs 90 Smithing to trade, even if you looted one).
2. **Obtain gate** — the item has no skill recipe on your account's rule set; you must **obtain one** first (drop, clue, chest, etc.). Shop purchases do not count.
3. **Custom rule** — you or the catalog editor set a deliberate override.

Looting a fabricable item does **not** bypass its skill requirement for GE trade.

---

## Use vs trade

### I unlocked trade — why can't I eat / drink / wear / teleport with it?

**Trade** and **use** are separate permissions. Example: raw swordfish unlocks on the GE at 50 Fishing; cooked swordfish needs 45 **Cooking** to eat. Looting a saradomin brew may unlock trade after obtain, but drinking still needs Herblore.

See **Configuration → Leadman Mode → Use gates** for optional skill checks on eat, drink, jewellery, charges, ammo, and runes.

---

## Playing with someone else

### How do I trade with my duo partner?

With **Block player trading** on, add their username under **Allow trade with** (comma-separated), or enable **Allow trade with party members** and join the same [RuneLite Party](https://github.com/runelite/runelite/wiki/Party) on both clients.

---

## Custom rules and the catalog

### Can I change the rules for one item?

Yes. Open the sidebar **⚙ catalog**, find the item, and edit trade, shop, eat, drink, wield, use, teleport, or bury independently. **Reset all custom rules** clears overrides without wiping obtain progress.

---

## Technical / honour system

### Does this change my account on the server?

No. Leadman is **client-side only**, like Bronzeman. The plugin hides menu options and filters GE search. Disabling the plugin removes the restrictions. It cannot cancel existing GE offers or undo server-side trades.
