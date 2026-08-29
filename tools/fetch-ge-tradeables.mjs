#!/usr/bin/env node
/**
 * Fetches every Grand Exchange tradeable name from the OSRS Wiki prices API
 * and writes src/main/resources/com/leadman/ge-tradeables.json (normalised keys).
 *
 *   node tools/fetch-ge-tradeables.mjs
 */

import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(HERE, "../src/main/resources/com/leadman/ge-tradeables.json");
const IDS_OUT = resolve(HERE, "../src/main/resources/com/leadman/ge-item-ids.json");

function normalise(raw) {
  let s = String(raw).trim().toLowerCase();
  let changed = true;
  while (changed) {
    changed = false;
    let next = s.replace(/\(\d+\)$/, "").trim();
    if (next !== s) {
      s = next;
      changed = true;
    }
    next = s.replace(
      /\((?:t\d*|g|or|i|l|u|p\+{0,2}|nz|cr|m|e|x|s|broken|inactive|free|deadman|last man standing)\)$/,
      ""
    ).trim();
    if (next !== s) {
      s = next;
      changed = true;
    }
  }
  return s;
}

const res = await fetch("https://prices.runescape.wiki/api/v1/osrs/mapping", {
  headers: { "User-Agent": "leadman-rules-generator" },
});
const items = await res.json();
const keys = [...new Set(items.map((i) => normalise(i.name)))].sort();

/** First GE item id per normalised name — used for equip detection at runtime. */
const idByKey = {};
for (const item of items) {
  const key = normalise(item.name);
  if (!idByKey[key]) {
    idByKey[key] = item.id;
  }
}

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(keys, null, 1) + "\n", "utf8");
writeFileSync(IDS_OUT, JSON.stringify(idByKey, null, 1) + "\n", "utf8");
console.log(`wrote ${keys.length} GE tradeable keys -> ${OUT}`);
console.log(`wrote ${Object.keys(idByKey).length} item ids -> ${IDS_OUT}`);
