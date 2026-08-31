#!/usr/bin/env node
/**
 * Audits NPC shop items against wiki obtain sources.
 * Flags items where shop appears to be the only legitimate obtain path.
 *
 *   node tools/audit-shop-sources.mjs
 *   node tools/audit-shop-sources.mjs --write docs/shop-only-candidates.md
 *
 * Data: OSRS Wiki Bucket API (storeline, dropsline, recipe, clue_equipment,
 * bountytaskline, couriertaskline).
 */

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RULES = resolve(HERE, "../src/main/resources/com/leadman/leadman-rules.json");
const GE = resolve(HERE, "../src/main/resources/com/leadman/ge-tradeables.json");
const WIKI = "https://oldschool.runescape.wiki/api.php";
const UA = "leadman-shop-audit/1.0 (github.com/felippeomgt/leadman-mode)";

const writePath = process.argv.includes("--write")
  ? process.argv[process.argv.indexOf("--write") + 1]
  : null;

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function bucketQuery(query) {
  const url = `${WIKI}?action=bucket&format=json&query=${encodeURIComponent(query)}`;
  for (let attempt = 0; attempt < 5; attempt++) {
    const res = await fetch(url, { headers: { "User-Agent": UA } });
    if (res.status === 429) {
      await sleep(2000 * (attempt + 1));
      continue;
    }
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${query}`);
    const data = await res.json();
    if (data.error) throw new Error(data.error);
    return data.bucket || [];
  }
  throw new Error(`Rate limited: ${query}`);
}

async function fetchAll(bucket, fields, pageSize = 5000) {
  const select = fields.map((f) => `'${f}'`).join(",");
  const all = [];
  let offset = 0;
  while (true) {
    const q =
      offset === 0
        ? `bucket('${bucket}').select(${select}).limit(${pageSize}).run()`
        : `bucket('${bucket}').select(${select}).limit(${pageSize}).offset(${offset}).run()`;
    const rows = await bucketQuery(q);
    all.push(...rows);
    if (rows.length < pageSize) break;
    offset += pageSize;
    await sleep(150);
  }
  return all;
}

/** Strip wiki link markup: [[Item|Label]] -> Item */
function wikiPlain(s) {
  if (!s) return "";
  return String(s)
    .replace(/\[\[([^|\]]+)\|[^\]]+\]\]/g, "$1")
    .replace(/\[\[([^\]]+)\]\]/g, "$1")
    .trim();
}

function parseRecipeOutputs(productionJson) {
  const names = new Set();
  try {
    const p = JSON.parse(productionJson);
    const name = wikiPlain(p.name);
    const output = wikiPlain(p.output);
    if (name) names.add(name);
    if (output) {
      for (const part of output.split(/[,;]/)) {
        const t = wikiPlain(part);
        if (t) names.add(t);
      }
    }
    if (Array.isArray(p.products)) {
      for (const prod of p.products) {
        const t = wikiPlain(prod.item || prod.name || prod);
        if (t) names.add(t);
      }
    }
  } catch {
    /* ignore */
  }
  return names;
}

function normaliseKey(raw) {
  return String(raw).trim().toLowerCase();
}

function isRewardShop(shop) {
  return /reward|deadman|diango|justine|bounty hunter store|lost property|twiggy|stuff for the last shopper/i.test(
    shop,
  );
}

function isPackName(item) {
  return /\bpack\b/i.test(item);
}

function hasSkillPath(rule) {
  return (rule?.paths || []).some((p) => p.type === "SKILL" && (p.reqs || []).length > 0);
}

console.log("Fetching wiki buckets…");

const storeRows = await fetchAll("storeline", ["sold_item", "sold_by"]);
console.log(`  storeline: ${storeRows.length} rows`);

const dropRows = await fetchAll("dropsline", ["item_name", "page_name"]);
console.log(`  dropsline: ${dropRows.length} rows`);

const recipeRows = await fetchAll("recipe", ["production_json", "uses_skill"]);
console.log(`  recipe: ${recipeRows.length} rows`);

const clueRows = await fetchAll("clue_equipment", ["item", "base_item"]);
console.log(`  clue_equipment: ${clueRows.length} rows`);

const bountyRows = await fetchAll("bountytaskline", ["item"]);
console.log(`  bountytaskline: ${bountyRows.length} rows`);

const courierRows = await fetchAll("couriertaskline", ["item"]);
console.log(`  couriertaskline: ${courierRows.length} rows`);

// --- index alternative sources ---
const dropSources = new Map(); // item -> Set<source page>
for (const row of dropRows) {
  const item = row.item_name;
  if (!item) continue;
  if (!dropSources.has(item)) dropSources.set(item, new Set());
  dropSources.get(item).add(row.page_name);
}

const recipeOutputs = new Set();
for (const row of recipeRows) {
  for (const n of parseRecipeOutputs(row.production_json)) {
    recipeOutputs.add(n);
  }
}

const clueItems = new Set();
for (const row of clueRows) {
  if (row.item) clueItems.add(row.item);
  if (row.base_item) clueItems.add(row.base_item);
}

const bountyItems = new Set(bountyRows.map((r) => r.item).filter(Boolean));
const courierItems = new Set(courierRows.map((r) => r.item).filter(Boolean));

// --- shop index ---
const shopItems = new Map(); // item -> Set<shop>
for (const row of storeRows) {
  const item = row.sold_item;
  const shop = row.sold_by;
  if (!item) continue;
  if (!shopItems.has(item)) shopItems.set(item, new Set());
  shopItems.get(item).add(shop);
}

const rules = JSON.parse(readFileSync(RULES, "utf8"));
const geKeys = new Set(JSON.parse(readFileSync(GE, "utf8")));
const byName = new Map(rules.items.map((i) => [i.name.toLowerCase(), i]));

function enrich(entry) {
  const rule = ruleForItem(byName, entry.item);
  const key = normaliseKey(entry.item);
  entry.rule = rule;
  entry.currentClass = rule?.itemClass || (rule ? "mapped" : "unmapped");
  entry.alreadyShopOnly = rule?.itemClass === "SHOP_ONLY";
  entry.packOf = rule?.packOf || null;
  entry.geTradeable = geKeys.has(key);
  entry.hasSkillPath = rule ? hasSkillPath(rule) : false;
  entry.shopDeadlock =
    !entry.alreadyShopOnly &&
    entry.geTradeable &&
    !entry.packOf &&
    !entry.hasSkillPath &&
    entry.currentClass !== "FABRICABLE" &&
    entry.currentClass !== "GATHERABLE";
  entry.onlyRewardShops = entry.shops.every(isRewardShop);
  entry.isPack = isPackName(entry.item);
  return entry;
}

function ruleForItem(byName, itemName) {
  return byName.get(itemName.toLowerCase()) || null;
}

const candidates = [];
const hasAlt = [];

for (const [item, shops] of [...shopItems.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
  const alt = [];

  const drops = dropSources.get(item);
  if (drops && drops.size > 0) {
    alt.push({ type: "drop/reward", sources: [...drops].sort().slice(0, 8), total: drops.size });
  }
  if (recipeOutputs.has(item)) alt.push({ type: "recipe/creation" });
  if (clueItems.has(item)) alt.push({ type: "clue" });
  if (bountyItems.has(item)) alt.push({ type: "bounty" });
  if (courierItems.has(item)) alt.push({ type: "courier" });

  const entry = enrich({
    item,
    shops: [...shops].sort(),
    alternatives: alt,
  });

  if (alt.length === 0) {
    candidates.push(entry);
  } else {
    hasAlt.push(entry);
  }
}

console.log(`\nShop items: ${shopItems.size}`);
console.log(`Shop-only candidates (no wiki alt source): ${candidates.length}`);
console.log(`Shop items with other sources: ${hasAlt.length}`);

const lines = [];
lines.push("# Shop-only candidates (review before rule changes)");
lines.push("");
lines.push(`Generated: ${new Date().toISOString()}`);
lines.push("");
lines.push("## Method");
lines.push("");
lines.push(
  "For every item in wiki `storeline` (~2k unique items), checked alternative obtain paths in:"
);
lines.push("- `dropsline` (monster drops, minigame rewards, etc.)");
lines.push("- `recipe` (wiki **Creation** / production outputs)");
lines.push("- `clue_equipment`, `bountytaskline`, `couriertaskline`");
lines.push("");
lines.push(
  "**Gaps (manual review):** ground spawns, quest dialogue rewards, thieving/pickpocket, implings, bird nests, POH, player-made items."
);
lines.push("");
lines.push(`Raw wiki shop-only candidates: **${candidates.length}**`);
lines.push("");

const priority = candidates.filter(
  (c) => c.shopDeadlock && !c.isPack && !c.onlyRewardShops,
);
const packCandidates = candidates.filter((c) => c.isPack && !c.alreadyShopOnly);
const skillMapped = candidates.filter(
  (c) =>
    !c.alreadyShopOnly &&
    (c.currentClass === "FABRICABLE" ||
      c.currentClass === "GATHERABLE" ||
      c.hasSkillPath),
);
const rewardShopOnly = candidates.filter((c) => c.onlyRewardShops && !c.alreadyShopOnly);
const nonGeShopOnly = candidates.filter(
  (c) => !c.geTradeable && !c.alreadyShopOnly && !c.onlyRewardShops,
);
const needReview = candidates.filter((c) => !c.alreadyShopOnly);
const already = candidates.filter((c) => c.alreadyShopOnly);

function tableRows(items) {
  const out = [];
  out.push("| Item | Class | GE | Shops |");
  out.push("| --- | --- | --- | --- |");
  for (const c of items) {
    const shops =
      c.shops.slice(0, 2).join("; ") + (c.shops.length > 2 ? ` (+${c.shops.length - 2})` : "");
    out.push(`| ${c.item} | ${c.currentClass} | ${c.geTradeable ? "yes" : "no"} | ${shops} |`);
  }
  return out;
}

lines.push("## Priority — shop deadlock today");
lines.push("");
lines.push(
  "Wiki shows no drop/recipe/clue source, item is **GE tradeable**, plugin blocks shop until obtain, and rule has **no skill path / packOf**. These are the most likely `SHOP_ONLY` fixes."
);
lines.push("");
lines.push(`Count: **${priority.length}**`);
lines.push("");
lines.push(...tableRows(priority));

lines.push("");
lines.push("## Ali's Discount Wares — priority subset");
lines.push("");
const aliShop = "Ali's Discount Wares.";
const aliPriority = priority.filter((c) => c.shops.includes(aliShop));
lines.push(`Count: **${aliPriority.length}**`);
lines.push("");
for (const c of aliPriority) {
  lines.push(`- **${c.item}** (${c.currentClass}, GE: ${c.geTradeable ? "yes" : "no"})`);
}

lines.push("");
lines.push("## Pack shop items (prefer `packOf`, not `SHOP_ONLY`)");
lines.push("");
lines.push(
  "Bulk packs sold in shops; unlock after obtaining the single item once. Already handled for Eye of newt / Feather pack."
);
lines.push("");
lines.push(`Count: **${packCandidates.length}**`);
lines.push("");
lines.push(...tableRows(packCandidates.slice(0, 80)));
if (packCandidates.length > 80) {
  lines.push("");
  lines.push(`_… and ${packCandidates.length - 80} more in appendix._`);
}

lines.push("");
lines.push("## Excluded — already skill-gated in rules");
lines.push("");
lines.push(
  "Wiki missed the recipe or our generator already maps a skill path (e.g. rune packs → FABRICABLE). Do **not** mark SHOP_ONLY."
);
lines.push("");
lines.push(`Count: **${skillMapped.length}**`);
lines.push("");
for (const c of skillMapped.slice(0, 30)) {
  lines.push(`- ${c.item} (${c.currentClass})`);
}
if (skillMapped.length > 30) {
  lines.push(`- _… and ${skillMapped.length - 30} more_`);
}

lines.push("");
lines.push("## Reward / cosmetic shops (lower priority)");
lines.push("");
lines.push(
  "Sold only in reward or cosmetic shops (Diango, Deadman, minigame rewards). Often not GE-tradeable or not part of normal ironman shop flow."
);
lines.push("");
lines.push(`Count: **${rewardShopOnly.length}**`);
lines.push("");
for (const c of rewardShopOnly.slice(0, 40)) {
  lines.push(`- ${c.item} — ${c.shops[0]}`);
}
if (rewardShopOnly.length > 40) {
  lines.push(`- _… and ${rewardShopOnly.length - 40} more_`);
}

lines.push("");
lines.push("## Non-GE shop-only (shop allowed today)");
lines.push("");
lines.push(
  "Not on GE list — plugin already allows shop buy without obtain. Listed for completeness if you want explicit SHOP_ONLY anyway."
);
lines.push("");
lines.push(`Count: **${nonGeShopOnly.length}**`);
lines.push("");
for (const c of nonGeShopOnly.slice(0, 40)) {
  lines.push(`- ${c.item} (${c.shops[0]})`);
}
if (nonGeShopOnly.length > 40) {
  lines.push(`- _… and ${nonGeShopOnly.length - 40} more_`);
}

lines.push("");
lines.push("## Already SHOP_ONLY");
lines.push("");
if (already.length === 0) {
  lines.push("_None._");
} else {
  for (const c of already) {
    lines.push(`- ${c.item} (${c.shops.join(", ")})`);
  }
}

lines.push("");
lines.push("## Appendix — all raw candidates (not yet SHOP_ONLY)");
lines.push("");
lines.push(`Count: **${needReview.length}**`);
lines.push("");
lines.push(...tableRows(needReview));

lines.push("");
lines.push("## Sample: shop items WITH wiki alternatives");
lines.push("");
const samples = hasAlt
  .filter((c) => c.shops.some((s) => s.includes("Ali") || s.includes("Zaff") || s.includes("Nardok")))
  .slice(0, 15);
for (const c of samples) {
  const altDesc = c.alternatives
    .map((a) => {
      if (a.type === "drop/reward") return `drop (${a.total} sources, e.g. ${a.sources[0]})`;
      return a.type;
    })
    .join("; ");
  lines.push(`- ${c.item}: ${altDesc}`);
}

const md = lines.join("\n") + "\n";

if (writePath) {
  writeFileSync(writePath, md, "utf8");
  console.log(`\nWrote ${writePath}`);
  console.log(`  Priority (shop deadlock): ${priority.length}`);
  console.log(`  Ali's priority: ${aliPriority.length}`);
} else {
  console.log("\n--- Priority preview ---\n");
  for (const c of priority.slice(0, 40)) {
    console.log(`${c.item} [${c.currentClass}] <- ${c.shops[0]}`);
  }
  console.log(`\nPriority total: ${priority.length}`);
  console.log("Pass --write docs/shop-only-candidates.md to save full report.");
}
