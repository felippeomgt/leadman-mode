# Leadman Mode

**Ironman discipline on a normal account — with a Grand Exchange that opens one item at a time.**

You play a regular account, but hold yourself to Ironman rules: no player trades, no looting other players' drops, and a GE that only unlocks what you could plausibly have earned yourself.

> **Beta.** Some shop items may still be wrong — override them in the **⚙ catalog** or [open an issue](https://github.com/felippeomgt/leadman-mode/issues). See the [**FAQ**](docs/FAQ.md) for shops, obtain paths, and GE behaviour.

## The rule in one sentence

**Having an item is not enough — you must unlock it, then meet the skill level to use it.**

## How it works

**1. Obtain it legitimately.**  
Before the GE or most shops will sell something, you need one real copy: a drop, a chest, something you made, or a shop marked as the only source. Buying from a normal shop does *not* count — that would let you flip stock straight onto the GE.

**2. Unlock use with your skills.**  
Even after trade opens, eating, drinking, wearing, wielding, and casting can stay locked until you hit the level that would create or use that item yourself. Trade and use are separate permissions.

When something opens, you get a popup and a chat message.

## Examples by skill

| Skill | Trade / shop | Use |
| --- | --- | --- |
| **Fishing** | Raw swordfish at 50 Fishing + one caught | — |
| **Cooking** | Cooked swordfish at 45 + one cooked | Eat at 45 Cooking |
| **Smithing** | Rune scimitar at 90 + one obtained | Wield at 40 Smithing (Mixed default) |
| **Herblore** | Saradomin brew at 81 + one obtained | Drink at 81 Herblore |
| **Crafting** | Amulet of glory at 80 + one obtained | Wear at 80 Crafting |
| **Magic** | — | Glory teleport at 68 Magic |
| **Fletching** | Rune arrows at 75 + one obtained | Use at 75 Fletching |
| **Runecrafting** | Chaos rune at 35 + one obtained | Cast spells that need those runes |

Boss drops and uniques with no recipe: obtain one, then trade and use are open.

## Settings (short)

Under **Configuration → Leadman Mode**:

- **Use gates** — toggle which skills also gate *using* items (food, potions, jewellery, ammo, runes, smithing/ranged equipment modes).
- **Ironman conduct** — block trades, others' drops, optional GE off entirely.
- **⚙ catalog** — browse rules, override any item, reset to defaults.

Locked inventory items look slightly dimmed. **Examine** or try an action to see what is still blocked.

## Honour system

Client-side only, like Bronzeman. Disabling the plugin removes restrictions.

## Links

| | |
| --- | --- |
| Player FAQ | [`docs/FAQ.md`](docs/FAQ.md) |
| Build & publish | [`INSTRUCTIONS.md`](INSTRUCTIONS.md) |
| Contribute | [`CONTRIBUTING.md`](CONTRIBUTING.md) |
| Design details | [`docs/DESIGN.md`](docs/DESIGN.md) |

## License

BSD 2-Clause — see [LICENSE](LICENSE).
