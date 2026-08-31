#!/usr/bin/env node
/**
 * Groups every wiki NPC shop with its stock for manual review.
 *
 *   node tools/generate-shop-review.mjs
 *   node tools/generate-shop-review.mjs --write docs/shop-review-by-store.md
 *
 * Use this while reviewing shop buy gates: for each shop, decide which items should
 * stay fabrication-gated vs become SHOP_ONLY (shop is the only obtain path).
 */

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const RULES = resolve(HERE, "../src/main/resources/com/leadman/leadman-rules.json");
const GE = resolve(HERE, "../src/main/resources/com/leadman/ge-tradeables.json");
const WIKI = "https://oldschool.runescape.wiki/api.php";
const UA = "leadman-shop-review/1.0 (github.com/felippeomgt/leadman-mode)";

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

function wikiShopUrl(shopName) {
  const slug = shopName.trim().replace(/ /g, "_");
  return `https://oldschool.runescape.wiki/w/${encodeURIComponent(slug).replace(/%2F/g, "/")}`;
}

function normaliseKey(raw) {
  return String(raw).trim().toLowerCase();
}

function hasSkillPath(rule) {
  return (rule?.paths || []).some((p) => p.type === "SKILL" && (p.reqs || []).length > 0);
}

function shopBuyBlocked(rule, geKeys) {
  if (!rule) return "unmapped";
  if (rule.itemClass === "SHOP_ONLY") return "open";
  if (rule.packOf) return "needs-pack-single";
  if (hasSkillPath(rule)) return "skill-gated";
  if (rule.itemClass === "DROP_ONLY" || rule.itemClass === "REWARD_ONLY") {
    return geKeys.has(normaliseKey(rule.name)) ? "needs-obtain" : "open";
  }
  if (geKeys.has(normaliseKey(rule.name))) return "needs-obtain";
  return "open";
}

console.log("Fetching wiki storeline…");
const storeRows = await fetchAll("storeline", ["sold_item", "sold_by"]);
console.log(`  ${storeRows.length} rows`);

const rules = JSON.parse(readFileSync(RULES, "utf8"));
const geKeys = new Set(JSON.parse(readFileSync(GE, "utf8")));
const byName = new Map(rules.items.map((i) => [i.name.toLowerCase(), i]));

/** @type {Map<string, Set<string>>} */
const byShop = new Map();
for (const row of storeRows) {
  const shop = row.sold_by?.trim();
  const item = row.sold_item?.trim();
  if (!shop || !item) continue;
  if (!byShop.has(shop)) byShop.set(shop, new Set());
  byShop.get(shop).add(item);
}

const shops = [...byShop.entries()].sort((a, b) => a[0].localeCompare(b[0]));

const lines = [];
lines.push("# Shop-by-shop review");
lines.push("");
lines.push(`Generated: ${new Date().toISOString()}`);
lines.push("");
lines.push(
  "Walk through each shop and note items that should **not** require obtain/skill before buying.",
);
lines.push("");
lines.push("**Shop buy status legend:**");
lines.push("- `open` — buy allowed without prior obtain");
lines.push("- `SHOP_ONLY` — buy allowed; purchase counts as obtain");
lines.push("- `needs-obtain` — GE item with no skill path; must obtain once elsewhere first");
lines.push("- `skill-gated` — needs fabrication level (Cooking, Smithing, etc.) to buy");
lines.push("- `needs-pack-single` — bulk pack; obtain the single item first");
lines.push("");
lines.push(`Total shops: **${shops.length}**`);
lines.push("");

let blockedShops = 0;
for (const [shop, itemSet] of shops) {
  const items = [...itemSet].sort((a, b) => a.localeCompare(b));
  const rows = items.map((item) => {
    const rule = byName.get(item.toLowerCase()) || null;
    const status = shopBuyBlocked(rule, geKeys);
    return { item, rule, status };
  });

  const needsReview = rows.filter(
    (r) => r.status === "needs-obtain" || r.status === "skill-gated",
  );
  if (needsReview.length > 0) blockedShops++;

  lines.push(`## ${shop}`);
  lines.push("");
  lines.push(`Wiki: [${shop}](${wikiShopUrl(shop)})`);
  lines.push("");
  lines.push(`Items: **${items.length}** · blocked today: **${needsReview.length}**`);
  lines.push("");
  lines.push("| Item | Rule class | Shop buy | Notes |");
  lines.push("| --- | --- | --- | --- |");
  for (const { item, rule, status } of rows) {
    const cls = rule?.itemClass || "unmapped";
    const note =
      status === "needs-obtain"
        ? "likely SHOP_ONLY candidate if shop is only source"
        : status === "skill-gated"
          ? "fabrication gate on shop buy"
          : "";
    lines.push(`| ${item} | ${cls} | ${status} | ${note} |`);
  }
  lines.push("");
  lines.push("**Review:** _(items here that should buy freely — list names or \"none\")_");
  lines.push("");
  lines.push("---");
  lines.push("");
}

lines.push(`Shops with at least one blocked item: **${blockedShops}**`);
lines.push("");

const report = lines.join("\n");
if (writePath) {
  writeFileSync(writePath, report, "utf8");
  console.log(`Wrote ${writePath}`);
} else {
  console.log(report.slice(0, 4000));
  console.log("\n… truncated. Pass --write docs/shop-review-by-store.md");
}

console.log(`\nShops: ${shops.length}, with blocked stock: ${blockedShops}`);
