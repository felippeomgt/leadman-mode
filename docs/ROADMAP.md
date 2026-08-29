# Leadman — roadmap to launch

> Last updated: 2026-08-29. Resume here after manual testing.

## Done (playtest-ready)

| Phase | Scope | Status |
|---|---|---|
| **A** | GE enforcement, trade rule (`fabricable OR obtain` only when no skill path), shop buy hook, `TradeableIndex` | Done — user confirmed GE + trade |
| **B** | Config cleanup, elemental staff wield gates, autocast/spellbook blocking | Done — user confirmed with `gateRunes` ON |
| **C** | Popup width, search ✕ button, sidebar horizontal scroll | Done |
| **D** | Per-action editor (8 gates + Override checkbox), card click to edit, hover/tooltip, eat/drink/bury/wield/shop enforcement | Done except **Can Shop** needs in-game verify |

## Tomorrow — manual test checklist

Run `run-dev.cmd`, log in, tick through these:

### Must verify

- [ ] **Shop buy** — Chaos rune at RC 1 from any magic shop → blocked with Runecrafting 35 message
- [ ] **Shop buy** — Item with custom Override on Can shop only
- [ ] **Editor** — Override checkbox: save one field only, confirm other actions not polluted in profile JSON
- [ ] **Sidebar** — Card hover + tooltip; long names (Divine super strength potion) open editor on card click
- [ ] **Elemental staff** — RC 1 cannot wield Staff of air when `gateRunes` ON

### Already confirmed (regression only if touching related code)

- [x] GE search hides locked items
- [x] Rune plateskirt equipped does not unlock GE without Smithing 99
- [x] Autocast blocked via staff wield gate (indirect)

## Phase E — coverage & data (in progress)

Goal: every **fabricable** GE item has a skill gate; **drop-only** items stay blocked until obtained.

### Tooling added

```sh
node tools/fetch-ge-tradeables.mjs   # refresh ge-tradeables.json (~4410 names)
node tools/generate-rules.mjs --verify
node tools/coverage-report.mjs     # writes docs/ge-unmapped.txt
./gradlew coverageReport           # same via Gradle
```

Current coverage (2026-08-29):

- **424** generated item rules
- **4410** GE tradeable keys
- **~3986** GE items intentionally unmapped → blocked until `everObtained` (drops, uniques, etc.)

### Next data work (priority order)

1. **Shop-sold fabricables** — items sold in NPC shops that *are* in `leadman-rules.json` (runes, bars, food…) — shop enforcement must work (Phase D fix).
2. **Missing fabrication ladders** — cannonballs above steel, crossbows, bolt tips, teleport tablets (listed in DESIGN.md §11).
3. **Wiki recipe import** — optional pipeline: OSRS Wiki Cargo → candidate rules → human review → `overrides.json`.
4. **Quest allowlist** — `questItemBypass` removed; if quests stall, curate `overrides.json` per quest item.

### Not needed for v1

- Mapping every boss unique (whip, bandos…) — obtain-unlock is correct
- NPC shop catalogue (which NPC sells what) — enforcement is per-item, not per-shop

## Phase F — launch prep

- [ ] Bump version in `build.gradle` when ready for hub
- [ ] Plugin Hub PR (`runelite/plugin-hub`, file `plugins/leadman`)
- [ ] Final pass on `docs/DESIGN.md` vs implemented behaviour (trade formula in §1.1 still mentions `hasNoGate`)
- [ ] Optional: inventory overlay (padlock on locked items) — DESIGN §10, not built

## Quick commands

```sh
run-dev.cmd
./gradlew.bat test
node tools/generate-rules.mjs --verify
node tools/coverage-report.mjs --sample
```

Profile reset: delete `~/.runelite/leadman/<accountHash>.json`
