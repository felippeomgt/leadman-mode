#!/usr/bin/env node
/**
 * Reports GE tradeables that have no item rule in leadman-rules.json.
 * Drop-only items are intentionally unmapped — they block GE until obtained.
 *
 *   node tools/coverage-report.mjs
 *   node tools/coverage-report.mjs --write-stubs   # append DROP_ONLY stubs to overrides template
 */

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RULES = resolve(HERE, "../src/main/resources/com/leadman/leadman-rules.json");
const GE = resolve(HERE, "../src/main/resources/com/leadman/ge-tradeables.json");
const OUT = resolve(HERE, "../docs/ge-unmapped.txt");

const rules = JSON.parse(readFileSync(RULES, "utf8"));
const ge = JSON.parse(readFileSync(GE, "utf8"));
const byName = new Map(rules.items.map((i) => [i.name.toLowerCase(), i]));

const missing = ge.filter((k) => !byName.has(k));
const mapped = ge.length - missing.length;

console.log(`GE tradeables: ${ge.length}`);
console.log(`Item rules:    ${rules.items.length}`);
console.log(`GE covered:    ${mapped} (${((mapped / ge.length) * 100).toFixed(1)}%)`);
console.log(`GE unmapped:   ${missing.length} (blocked until obtained — by design)`);

const lines = [
  `# GE tradeables without a generated rule (${missing.length} items)`,
  `# Generated ${new Date().toISOString().slice(0, 10)} by tools/coverage-report.mjs`,
  `# These block GE/trade/shop until the player obtains one.`,
  `# To add fabrication gates, extend generate-rules.mjs or overrides.json.`,
  "",
  ...missing.sort(),
  "",
];
writeFileSync(OUT, lines.join("\n"), "utf8");
console.log(`\nwrote ${OUT}`);

if (process.argv.includes("--sample")) {
  console.log("\nFirst 50 unmapped:");
  for (const name of missing.slice(0, 50)) {
    console.log(`  ${name}`);
  }
}
