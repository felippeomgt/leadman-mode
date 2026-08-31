# Releasing Leadman Mode

This repo is in **beta**. One open PR collects changes until you are ready to ship.

## Branch workflow

1. **`main`** — last merged, shippable state (matches what you want on Plugin Hub after release).
2. **`beta/unreleased`** — all in-progress work targets this branch.
3. Open **one PR** from `beta/unreleased` → `main` (keep updating the same PR).
4. When satisfied: **merge PR**, tag a release, update Plugin Hub commit hash.

```bash
# Start from main
git checkout main && git pull

# Work on beta branch (create once, then reuse)
git checkout -b beta/unreleased   # first time only
git pull origin beta/unreleased   # later sessions

# After changes
git add …
git commit -m "…"
git push -u origin beta/unreleased
```

## Pull request checklist

- [ ] `CHANGELOG.md` **Unreleased** section updated
- [ ] `./gradlew test` passes
- [ ] If rules changed: `node tools/generate-rules.mjs`
- [ ] If GE coverage changed: `node tools/audit-ge-rules.mjs --write docs/ge-audit.txt`
- [ ] Manual smoke test in RuneLite (`run-dev.cmd` or `./gradlew run`)

## Release (after merge to main)

1. Merge PR on GitHub.
2. Choose version (beta example: `1.0.0-beta.2`).
3. Create GitHub Release from tag; paste **Unreleased** changelog entries into release notes.
4. Clear **Unreleased** in `CHANGELOG.md` (move to new version section) — commit on `main`.
5. Update [runelite plugin-hub](https://github.com/runelite/plugin-hub) fork: bump `commit=` to merged `main` SHA.

## GE policy (reference)

| Item type | GE trade |
|-----------|----------|
| Fabrication / gather (skill path) | Skill levels met (obtain never bypasses) |
| Drop / reward / tablet / unmapped GE | Obtained once |
| `FREE` GE stub | Obtained once |

Audit: `node tools/audit-ge-rules.mjs` — **“Would open GE with no obtain”** should stay **0**.
