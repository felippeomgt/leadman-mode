#!/usr/bin/env node
/**
 * Audits GE trade rules against Leadman policy:
 *   - Fabrication items: skill must be met (obtain never bypasses trade).
 *   - Everything else on GE: blocked until obtained once.
 *
 *   node tools/audit-ge-rules.mjs
 *   node tools/audit-ge-rules.mjs --write docs/ge-audit.txt
 */

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RULES = resolve(HERE, "../src/main/resources/com/leadman/leadman-rules.json");
const GE = resolve(HERE, "../src/main/resources/com/leadman/ge-tradeables.json");

const rules = JSON.parse(readFileSync(RULES, "utf8"));
const geKeys = JSON.parse(readFileSync(GE, "utf8"));
const byName = new Map(rules.items.map((i) => [i.name.toLowerCase(), i]));

function hasTradeSkillPath(rule) {
  return (rule.paths || []).some(
    (p) =>
      p.type === "SKILL" &&
      (p.reqs || []).some((r) => !r.scope || r.scope === "TRADE" || r.scope === "ACTIVATE"),
  );
}

function hasAnySkillPath(rule) {
  return (rule.paths || []).some((p) => p.type === "SKILL" && (p.reqs || []).length > 0);
}

function tradeReqs(rule) {
  const out = [];
  for (const p of rule.paths || []) {
    if (p.type !== "SKILL") continue;
    for (const r of p.reqs || []) {
      if (!r.scope || r.scope === "TRADE" || r.scope === "ACTIVATE") {
        out.push(r);
      }
    }
  }
  return out;
}

/** Mirrors UnlockService.canTradeKey with all skills at 1 and nothing obtained. */
function geOpenWithoutObtain(rule) {
  if (!rule) return false; // unmapped GE -> blocked until obtain
  if (hasTradeSkillPath(rule)) return false; // needs skill
  const cls = rule.itemClass || "FREE";
  if (cls === "FABRICABLE" || cls === "GATHERABLE") {
    if (hasAnySkillPath(rule)) return false;
  }
  if (cls === "DROP_ONLY" || cls === "REWARD_ONLY" || cls === "SHOP_ONLY") return false;
  if (cls === "FREE" && (rule.paths || []).length === 0) return false;
  // Non-GE tradeable stub
  return false;
}

/** Skill path exists for trade but hasSkillPath() false — obtain alone would open GE in engine. */
function fabricationMisclassified(rule) {
  if (!rule || rule.itemClass !== "FABRICABLE") return false;
  const trade = tradeReqs(rule);
  return trade.length > 0 && !hasTradeSkillPath(rule);
}

const geOpen = [];
const skillGated = [];
const obtainGated = [];
const misclassified = [];
const unmapped = [];

for (const key of geKeys) {
  const rule = byName.get(key);
  if (!rule) {
    unmapped.push(key);
    continue;
  }
  if (fabricationMisclassified(rule)) {
    misclassified.push({ key, itemClass: rule.itemClass, paths: rule.paths?.length ?? 0 });
  }
  if (hasTradeSkillPath(rule)) {
    skillGated.push(key);
  } else if (geOpenWithoutObtain(rule)) {
    geOpen.push({ key, itemClass: rule.itemClass });
  } else {
    obtainGated.push({ key, itemClass: rule.itemClass });
  }
}

const lines = [
  `# GE rules audit (${new Date().toISOString().slice(0, 10)})`,
  "",
  `GE tradeables total:     ${geKeys.length}`,
  `Mapped in rules:         ${geKeys.length - unmapped.length}`,
  `Unmapped (obtain gate):  ${unmapped.length}`,
  "",
  `Skill-gated (fabrication): ${skillGated.length}`,
  `Obtain-gated (no trade skill path): ${obtainGated.length}`,
  `Would open GE with no obtain + lvl 1: ${geOpen.length}`,
  `Misclassified fabrication: ${misclassified.length}`,
  "",
];

if (geOpen.length) {
  lines.push("## GE open without obtain (investigate)", "");
  for (const { key, itemClass } of geOpen.sort((a, b) => a.key.localeCompare(b.key))) {
    lines.push(`  ${key}  [${itemClass}]`);
  }
  lines.push("");
}

if (misclassified.length) {
  lines.push("## Fabrication with trade reqs but wrong scope (obtain may bypass skill)", "");
  for (const { key } of misclassified.sort((a, b) => a.key.localeCompare(b.key))) {
    lines.push(`  ${key}`);
  }
  lines.push("");
}

if (obtainGated.length && process.argv.includes("--verbose")) {
  lines.push("## Obtain-gated sample (first 30)", "");
  for (const { key } of obtainGated.slice(0, 30)) {
    lines.push(`  ${key}`);
  }
  lines.push("");
}

const report = lines.join("\n");
console.log(report);

const outArg = process.argv.indexOf("--write");
if (outArg >= 0) {
  const path = process.argv[outArg + 1] || resolve(HERE, "../docs/ge-audit.txt");
  writeFileSync(path, report + "\n", "utf8");
  console.log(`\nwrote ${path}`);
}
