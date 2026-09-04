# Changelog

All notable changes to Leadman Mode are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html) while in beta.

## [Unreleased]

## [1.0.0-beta.2] - 2026-09-04

### Added
- **Crafting gates ranged equipment** — Restrict / Balanced / Mixed (default Mixed) for leather, dragonhide, snakeskin, and Fletching bows/crossbows.
- Crafting ladders for leather, snakeskin, frog-leather, and d'hide shields.
- Shop rules: Plant cure, Pie recipe book, Shantay pass (item) as `SHOP_ONLY`.
- Rune shop packs use `packOf` (obtain the single rune before buying a pack).

### Changed
- README rewritten — shorter focus on obtain vs use and skill examples.
- Shop review doc trimmed through Bolongo.

## [1.0.0-beta.1] - 2026-08-29

Initial beta on Plugin Hub.

### Added
- Core gate engine: trade, shop, eat, drink, wield, use, activate, bury, cast.
- Item catalog (⚙), inventory overlay, trade partner allowlist.
- `SHOP_ONLY`, `packOf`, and `shopAlwaysOpen` rule types.
- **Smithing gates equipment** — Restrict / Balanced / Mixed (default Mixed).
- GE obtain fix: skill level alone no longer unlocks trade; legitimate obtain required first.
- Shop-by-shop review tooling and expanded shop rules (Fine Fashions, slayer shop, farming, packs, Bolongo batch, etc.).
- Sidebar full unlock history, `build=standard` for Plugin Hub.
- Player FAQ, CONTRIBUTING, audit scripts.

### Fixed
- GE/shop trade bypass without obtain (Cod, Redberry pie, etc.).
- Teleport tablets on GE before obtain; bank booth false blocks; spell menu spam; gold necklace deposit.

[Unreleased]: https://github.com/felippeomgt/leadman-mode/compare/v1.0.0-beta.2...HEAD
[1.0.0-beta.2]: https://github.com/felippeomgt/leadman-mode/compare/v1.0.0-beta.1...v1.0.0-beta.2
[1.0.0-beta.1]: https://github.com/felippeomgt/leadman-mode/releases/tag/v1.0.0-beta.1
