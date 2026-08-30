# Changelog

All notable changes to Leadman Mode are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html) while in beta.

## [Unreleased]

### Added
- Item catalog (⚙), inventory overlay, trade partner allowlist, and `setup-windows.cmd`.
- `SHOP_ONLY` and `packOf` rule types for shop-only items and bulk packs.
- GE rules audit script: `node tools/audit-ge-rules.mjs`.

### Changed
- Block explanations only on Examine or when the player attempts a blocked action.
- Gem shop gated by Crafting level; eye of newt / feather packs require obtaining singles first.
- **Teleport tablets** are `DROP_ONLY` — obtain once before GE/shop (no Construction/Magic ladder).
- Bank booth menus no longer misidentified as locked items (object id vs item id).
- Tightened GE trade: `obtained` alone no longer bypasses skill gates for non obtain-only items.

### Fixed
- Spell block messages firing when other players cast nearby (menu rebuild spam).

## [1.0.0-beta.1] - 2026-08-29

Initial beta on Plugin Hub workflow.

### Added
- Core gate engine: trade, shop, eat, drink, wield, use, activate, bury, cast.
- ~4 000 item rules generated from `tools/generate-rules.mjs`.
- Sidebar panel, per-item rule editor, unlock popups, Ironman conduct toggles.
- Plugin Hub submission (Gradle, icon, `build=gradle`).

[Unreleased]: https://github.com/felippeomgt/leadman-mode/compare/v1.0.0-beta.1...HEAD
[1.0.0-beta.1]: https://github.com/felippeomgt/leadman-mode/releases/tag/v1.0.0-beta.1
