# Contributing to Leadman Mode

Thank you for helping improve Leadman Mode. This plugin follows the same open-source model as other [RuneLite](https://github.com/runelite/runelite) community plugins: contributions happen on GitHub via issues and pull requests.

## Bug reports

Leadman is **client-side** and **honour-based** — we cannot fix server behaviour or accounts that disabled the plugin. We *can* fix wrong item rules, broken menu gates, misleading messages, and crashes.

**Before opening an issue**, check:

1. [**FAQ**](docs/FAQ.md) — shop locks, obtain vs buy, and use vs trade confuse many first-time reports.
2. **Examine the item in-game** — the plugin lists which actions are locked and why.
3. **Catalog override** — if only your run needs an exception, you can fix it locally without waiting for a release (see [README — Customize your experience](README.md#customize-your-experience)).

### Opening a bug report

Use [GitHub Issues](https://github.com/felippeomgt/leadman-mode/issues). Choose or title it clearly as a **bug**.

Include as much as you can:

| Field | Why it helps |
| --- | --- |
| **Item name(s)** | Exact OSRS name (e.g. `Fake beard`, not “Ali beard”). |
| **Action blocked** | Shop buy, GE trade, eat, wield, deposit, cast, etc. |
| **Your levels** | Relevant skills and whether you had **obtained** the item before. |
| **Expected vs actual** | What you think should happen under Leadman rules. |
| **Steps** | Menu clicks or location (shop name, NPC) to reproduce. |
| **Config** | Use gate toggles that differ from defaults, custom catalog rules, GE disabled, etc. |
| **RuneLite version** | Client build from **Client Information**. |

Screenshots or short clips help for menu options that disappear.

If the fix belongs in the **generated ruleset** (wrong `SHOP_ONLY`, missing skill gate, shop-only item not whitelisted), say so — we track bulk shop review separately, but clear reports speed up the next rules pass.

## Feature requests and rule changes

Open an issue describing:

- The **item or category** (e.g. “all Slayer Equipment shop stock”).
- The **intended obtain path** (shop-only, skill gate, obtain once, etc.).
- Why the **current behaviour** is wrong for the mode.

Large rule changes usually land in `tools/generate-rules.mjs` and `src/main/resources/com/leadman/overrides.json`, then regenerate `leadman-rules.json`.

## Code contributions

1. **Fork** [felippeomgt/leadman-mode](https://github.com/felippeomgt/leadman-mode) and branch from `main` (or the active beta branch if coordinating a release).
2. **Build and test** — see [`INSTRUCTIONS.md`](INSTRUCTIONS.md):
   ```sh
   gradle test
   node tools/generate-rules.mjs   # if you changed the generator
   ```
3. **Keep scope focused** — one logical change per pull request when possible.
4. **Open a PR** against `main` with a short description and test plan.

### Where things live

| Area | Location |
| --- | --- |
| Gate engine | `src/main/java/com/leadman/unlock/UnlockService.java` |
| Menu enforcement | `src/main/java/com/leadman/LeadmanPlugin.java` |
| Generated item rules | `tools/generate-rules.mjs` → `leadman-rules.json` |
| Hand-maintained exceptions | `src/main/resources/com/leadman/overrides.json` |
| Design intent | `docs/DESIGN.md` |

Add or update tests in `src/test/java/com/leadman/GateRulesTest.java` when behaviour changes.

### Plugin Hub

Release flow (tags, changelog, plugin-hub bump) is documented in [`docs/RELEASING.md`](docs/RELEASING.md). Hub maintainers merge listing updates separately; code PRs do not need to include plugin-hub commits unless you are explicitly publishing.

## Conduct

Leadman is a voluntary challenge mode. Contributions should respect that players choose their own restrictions — defaults are suggestions, not moral enforcement. Be precise in issues; avoid shaming play styles.

## License

By contributing, you agree that your contributions are licensed under the same [BSD 2-Clause](LICENSE) license as the project.
