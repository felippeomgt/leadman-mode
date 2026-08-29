#!/usr/bin/env node
/**
 * Generates src/main/resources/com/leadman/leadman-rules.json.
 *
 * Most of OSRS's crafting requirements are ladders, not lists: the smithing table is
 * one base level per metal plus a fixed offset per item shape, and the jewellery table
 * is one level per gem per slot. Encoding the ladders and expanding them keeps the data
 * small enough to audit and correct enough to trust -- and reproduces the levels in the
 * brief exactly (rune scimitar 90, amulet of glory 80 Crafting + 68 Magic).
 *
 * Corrections belong in overrides.json, which is layered on top at runtime and is never
 * regenerated.
 *
 *   node tools/generate-rules.mjs [--verify]
 *
 * --verify additionally fetches the OSRS Wiki item mapping and reports any generated
 * name that does not match a real tradeable item. Requires network access.
 */

import { writeFileSync, readFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(HERE, "../src/main/resources/com/leadman/leadman-rules.json");

const items = [];

/**
 * @param display   in-game item name
 * @param itemClass FABRICABLE | GATHERABLE | DROP_ONLY | REWARD_ONLY | FREE
 * @param consume   FOOD | POTION | AMMO | RUNE | CHARGED | EQUIPMENT | ELEMENTAL_STAFF | TOOL | NONE
 * @param reqs      [[skill, level], ...] or [[skill, level, "ACTIVATE"]] -- all must be
 *                  met. An ACTIVATE-scoped requirement guards spending the item's charge
 *                  rather than wearing or holding it, so an amulet of glory carries
 *                  Crafting 80 unscoped and Magic 68 scoped: 80 Crafting puts it on,
 *                  68 Magic fires the teleport.
 */
function rule(display, itemClass, consume, reqs, source, opts = {}) {
  const paths = reqs.length
    ? [{
        type: "SKILL",
        reqs: reqs.map(([skill, level, scope]) =>
          scope ? { skill, level, scope } : { skill, level }),
        source: source || null,
      }]
    : [];
  const entry = { name: display, display, itemClass, consume, paths };
  // Untradeable items still carry a USE gate; they just cannot be checked against
  // the tradeable-item mapping.
  if (opts.tradeable === false) entry.tradeable = false;
  if (opts.packOf) entry.packOf = opts.packOf;
  items.push(entry);
}

// --------------------------------------------------------------------- smithing

// Every smithable item is (metal base level + shape offset). Rune base 85 plus the
// scimitar offset of 5 gives the 90 in the brief.
const METALS = { Bronze: 1, Iron: 15, Steel: 30, Mithril: 50, Adamant: 70, Rune: 85 };

const METAL_ATTACK = { Bronze: 1, Iron: 1, Steel: 5, Mithril: 20, Adamant: 30, Rune: 40 };
const METAL_DEFENCE = { Bronze: 1, Iron: 1, Steel: 5, Mithril: 20, Adamant: 30, Rune: 40 };
const TOOL_SKILL = { Bronze: 1, Iron: 1, Steel: 6, Mithril: 21, Adamant: 31, Rune: 41 };

const ARMOUR_SHAPES = new Set([
  "med helm", "full helm", "chainbody", "platelegs", "plateskirt", "platebody", "kiteshield", "sq shield",
]);

const SHAPES = {
  dagger: 0, axe: 1, mace: 2, "med helm": 3, sword: 4, scimitar: 5, longsword: 6,
  "full helm": 7, "sq shield": 8, warhammer: 9, battleaxe: 10, chainbody: 11,
  kiteshield: 12, claws: 13, "2h sword": 14, platelegs: 16, plateskirt: 16,
  platebody: 18,
};

// Bars are smelted, not smithed, so they carry their own table.
const BARS = {
  "Bronze bar": 1, "Iron bar": 15, "Silver bar": 20, "Steel bar": 30,
  "Gold bar": 40, "Mithril bar": 50, "Adamantite bar": 70, "Runite bar": 85,
};

for (const [metal, base] of Object.entries(METALS)) {
  for (const [shape, offset] of Object.entries(SHAPES)) {
    // Rune platebody computes to 103 on the raw ladder; the game caps it at 99.
    const level = Math.min(base + offset, 99);
    const consume = shape === "axe" ? "TOOL" : "EQUIPMENT";
    const reqs = [["SMITHING", level]];
    if (shape === "axe") {
      reqs.push(["WOODCUTTING", TOOL_SKILL[metal], "USE"]);
      reqs.push(["ATTACK", METAL_ATTACK[metal], "WIELD"]);
    } else if (ARMOUR_SHAPES.has(shape)) {
      reqs.push(["DEFENCE", METAL_DEFENCE[metal], "WIELD"]);
    } else {
      reqs.push(["ATTACK", METAL_ATTACK[metal], "WIELD"]);
    }
    rule(`${metal} ${shape}`, "FABRICABLE", consume, reqs, "Smithing");
  }
}

for (const [bar, level] of Object.entries(BARS)) {
  rule(bar, "FABRICABLE", "NONE", [["SMITHING", level]], "Smelting");
}

// Cannonballs: trade/shop gates on Smithing; tiers above steel also need Sailing to fire
// from a boat cannon. Dragon cannonballs are not smithed (DROP_ONLY).
function cannonball(display, smithing, sailing = null) {
  const reqs = [["SMITHING", smithing, "TRADE"]];
  if (sailing != null) {
    reqs.push(["SAILING", sailing, "USE"]);
  }
  rule(display, "FABRICABLE", "AMMO", reqs, "Smithing");
}

const CANNONBALLS = [
  ["Bronze cannonball", 5], ["Iron cannonball", 20], ["Steel cannonball", 35],
  ["Mithril cannonball", 55, 55], ["Adamant cannonball", 75, 75],
  ["Rune cannonball", 90, 90],
];
for (const entry of CANNONBALLS) {
  const [base, smith, sail] = entry;
  cannonball(base, smith, sail ?? null);
  for (const variant of ["chainshot", "incendiary"]) {
    const name = base.replace(" cannonball", ` ${variant} cannonball`);
    cannonball(name, smith, sail ?? null);
  }
}
rule("Dragon cannonball", "DROP_ONLY", "AMMO", [["SAILING", 90, "USE"]], "Sailing");
for (const variant of ["chainshot", "incendiary"]) {
  rule(`Dragon ${variant} cannonball`, "DROP_ONLY", "AMMO", [["SAILING", 90, "USE"]], "Sailing");
}

// GE armour sets bundle plate items -- gate at platebody Smithing for that metal.
const SET_VARIANTS = [
  "set (lg)", "set (sk)",
  "trimmed set (lg)", "trimmed set (sk)",
  "gold-trimmed set (lg)", "gold-trimmed set (sk)",
];

function armourSet(display, smithing) {
  rule(display, "FABRICABLE", "EQUIPMENT", [["SMITHING", smithing]], "Smithing");
}

for (const [metal, base] of Object.entries(METALS)) {
  const smithing = Math.min(base + SHAPES.platebody, 99);
  for (const variant of SET_VARIANTS) {
    armourSet(`${metal} ${variant}`, smithing);
  }
  armourSet(`${metal} armour set (lg)`, smithing);
  armourSet(`${metal} armour set (sk)`, smithing);
}

const GWD_SETS = ["Bandos", "Armadyl", "Ancient", "Guthix", "Saradomin", "Zamorak"];
const RUNE_PLATE = Math.min(METALS.Rune + SHAPES.platebody, 99);
for (const god of GWD_SETS) {
  armourSet(`${god} rune armour set (lg)`, RUNE_PLATE);
  armourSet(`${god} rune armour set (sk)`, RUNE_PLATE);
}
armourSet("Gilded armour set (lg)", RUNE_PLATE);
armourSet("Gilded armour set (sk)", RUNE_PLATE);

// Black armour is not player-smithable (Treasure Trails); sets stay unrestricted.
for (const variant of SET_VARIANTS) {
  rule(`Black ${variant}`, "FREE", "EQUIPMENT", [], null);
}

// ----------------------------------------------------------------------- mining

const ORES = {
  "Copper ore": 1, "Tin ore": 1, Clay: 1, "Iron ore": 15, "Silver ore": 20,
  Coal: 30, "Gold ore": 40, "Mithril ore": 55, "Adamantite ore": 70,
  "Runite ore": 85, "Rune essence": 1, "Pure essence": 30, Amethyst: 92,
};
for (const [ore, level] of Object.entries(ORES)) {
  rule(ore, "GATHERABLE", "NONE", [["MINING", level]], "Mining");
}

// ------------------------------------------------------------------ woodcutting

const LOGS = {
  Logs: 1, "Oak logs": 15, "Willow logs": 30, "Teak logs": 35, "Maple logs": 45,
  "Mahogany logs": 50, "Yew logs": 60, "Magic logs": 75, "Redwood logs": 90,
};
for (const [log, level] of Object.entries(LOGS)) {
  rule(log, "GATHERABLE", "NONE", [["WOODCUTTING", level]], "Woodcutting");
}

// ---------------------------------------------------------- fishing and cooking

// [raw name, fishing level, cooked name, cooking level]
const FISH = [
  ["Raw shrimps", 1, "Shrimps", 1],
  ["Raw sardine", 5, "Sardine", 1],
  ["Raw herring", 10, "Herring", 5],
  ["Raw anchovies", 15, "Anchovies", 1],
  ["Raw mackerel", 16, "Mackerel", 10],
  ["Raw trout", 20, "Trout", 15],
  ["Raw cod", 23, "Cod", 18],
  ["Raw pike", 25, "Pike", 20],
  ["Raw salmon", 30, "Salmon", 25],
  ["Raw tuna", 35, "Tuna", 30],
  ["Raw karambwan", 65, "Cooked karambwan", 30],
  ["Raw lobster", 40, "Lobster", 40],
  ["Raw bass", 46, "Bass", 43],
  ["Raw swordfish", 50, "Swordfish", 45],
  ["Raw monkfish", 62, "Monkfish", 62],
  ["Raw shark", 76, "Shark", 80],
  ["Raw sea turtle", 79, "Sea turtle", 82],
  ["Raw manta ray", 81, "Manta ray", 91],
  ["Raw anglerfish", 82, "Anglerfish", 84],
  ["Raw dark crab", 85, "Dark crab", 90],
];

for (const [raw, fishing, cooked, cooking] of FISH) {
  rule(raw, "GATHERABLE", "NONE", [["FISHING", fishing]], "Fishing");
  rule(cooked, "FABRICABLE", "FOOD", [["COOKING", cooking]], "Cooking");
}

const OTHER_FOOD = {
  Bread: 1, "Redberry pie": 10, "Meat pie": 20, Stew: 25, "Apple pie": 30,
  "Jug of wine": 35, Cake: 40, "Chocolate cake": 50, Curry: 60,
  "Tuna potato": 68, "Summer pie": 95,
};
for (const [food, level] of Object.entries(OTHER_FOOD)) {
  rule(food, "FABRICABLE", "FOOD", [["COOKING", level]], "Cooking");
}

// ------------------------------------------------------------ farming, herblore

// [herb, farming level to grow, herblore level to clean]
const HERBS = [
  ["Guam leaf", 9, 3], ["Marrentill", 14, 5], ["Tarromin", 19, 11],
  ["Harralander", 26, 20], ["Ranarr weed", 32, 25], ["Toadflax", 38, 30],
  ["Irit leaf", 44, 40], ["Avantoe", 50, 48], ["Kwuarm", 56, 54],
  ["Snapdragon", 62, 59], ["Cadantine", 67, 65], ["Lantadyme", 73, 67],
  ["Dwarf weed", 79, 70], ["Torstol", 85, 75],
];

for (const [herb, farming, herblore] of HERBS) {
  rule(`Grimy ${herb.toLowerCase()}`, "GATHERABLE", "NONE", [["FARMING", farming]], "Farming");
  rule(herb, "FABRICABLE", "NONE", [["HERBLORE", herblore]], "Cleaning herbs");
}

const POTIONS = {
  "Attack potion": 3, Antipoison: 5, "Strength potion": 12, "Restore potion": 22,
  "Energy potion": 26, "Defence potion": 30, "Agility potion": 34,
  "Combat potion": 36, "Prayer potion": 38, "Super attack": 45,
  Superantipoison: 48, "Fishing potion": 50, "Super energy": 52,
  "Hunter potion": 53, "Super strength": 55, "Weapon poison": 60,
  "Super restore": 63, "Sanfew serum": 65, "Super defence": 66,
  "Antidote++": 68, "Antifire potion": 69, "Divine super attack potion": 70,
  "Ranging potion": 72, "Divine super strength potion": 72,
  "Divine super defence potion": 74, "Divine ranging potion": 74,
  "Magic potion": 76, "Stamina potion": 77, "Zamorak brew": 78,
  "Divine magic potion": 78, "Divine battlemage potion": 80,
  "Saradomin brew": 81, "Extended antifire": 84, "Ancient brew": 85,
  "Divine bastion potion": 86, "Anti-venom": 87, "Menaphite remedy": 88,
  "Super combat potion": 90, "Forgotten brew": 91, "Super antifire potion": 92,
  "Anti-venom+": 94, "Divine super combat potion": 97,
  "Extended super antifire": 98,
};
for (const [potion, level] of Object.entries(POTIONS)) {
  rule(potion, "FABRICABLE", "POTION", [["HERBLORE", level]], "Herblore");
}

// ----------------------------------------------------------------- runecrafting

const RUNES = {
  "Air rune": 1, "Mind rune": 2, "Water rune": 5, "Earth rune": 9, "Fire rune": 14,
  "Body rune": 20, "Cosmic rune": 27, "Chaos rune": 35, "Astral rune": 40,
  "Nature rune": 44, "Law rune": 54, "Death rune": 65, "Blood rune": 77,
  "Soul rune": 90, "Wrath rune": 95,
  // Combination runes are craftable well below their component tiers. This is the
  // deliberate early-game valve described in docs/DESIGN.md section 6.1.
  "Mist rune": 6, "Dust rune": 10, "Mud rune": 13, "Smoke rune": 15,
  "Steam rune": 19, "Lava rune": 23,
};
for (const [rune, level] of Object.entries(RUNES)) {
  rule(rune, "FABRICABLE", "RUNE", [["RUNECRAFT", level]], "Runecrafting");
}

// Magic shops sell 100-rune packs that are not GE-tradeable. Without explicit rules
// they bypass shop gates (canShopKey treats unmapped non-GE items as free).
const RUNE_PACKS = {
  "Air rune pack": 1,
  "Mind rune pack": 2,
  "Water rune pack": 5,
  "Earth rune pack": 9,
  "Fire rune pack": 14,
  "Chaos rune pack": 35,
};
for (const [pack, level] of Object.entries(RUNE_PACKS)) {
  rule(pack, "FABRICABLE", "RUNE", [["RUNECRAFT", level]], "Runecrafting", { tradeable: false });
}

// --------------------------------------------------------------------- fletching

const AMMO_LADDERS = [
  ["arrow", { Bronze: 1, Iron: 15, Steel: 30, Mithril: 45, Adamant: 60, Rune: 75, Amethyst: 82, Dragon: 90 }],
  ["dart", { Bronze: 1, Iron: 22, Steel: 37, Mithril: 52, Adamant: 67, Rune: 81, Amethyst: 90, Dragon: 95 }],
  ["javelin", { Bronze: 3, Iron: 17, Steel: 32, Mithril: 47, Adamant: 62, Rune: 77, Dragon: 92 }],
];
for (const [shape, ladder] of AMMO_LADDERS) {
  for (const [metal, level] of Object.entries(ladder)) {
    rule(`${metal} ${shape}`, "FABRICABLE", "AMMO", [["FLETCHING", level]], "Fletching");
  }
}

const BOLTS = {
  "Bronze bolts": 9, "Iron bolts": 39, "Steel bolts": 46,
  "Mithril bolts": 54, "Broad bolts": 55, "Adamant bolts": 61, "Runite bolts": 69,
  "Dragon bolts": 84,
};
for (const [bolt, level] of Object.entries(BOLTS)) {
  rule(bolt, "FABRICABLE", "AMMO", [["FLETCHING", level]], "Fletching");
}
rule("Broad arrows", "FABRICABLE", "AMMO", [["FLETCHING", 52]], "Fletching", { tradeable: false });

const BOWS = {
  Shortbow: 5, Longbow: 10, "Oak shortbow": 20, "Oak longbow": 25,
  "Willow shortbow": 35, "Willow longbow": 40, "Maple shortbow": 50,
  "Maple longbow": 55, "Yew shortbow": 65, "Yew longbow": 70,
  "Magic shortbow": 80, "Magic longbow": 85,
};
for (const [bow, level] of Object.entries(BOWS)) {
  rule(bow, "FABRICABLE", "EQUIPMENT", [["FLETCHING", level]], "Fletching");
}

// Crossbows: Fletching only (limbs are usually dropped, not smithed).
const CROSSBOWS = {
  Crossbow: 9, "Crossbow string": 10,
  "Bronze crossbow": 9, "Bronze crossbow (u)": 9,
  "Iron crossbow": 39, "Iron crossbow (u)": 39,
  "Steel crossbow": 46, "Steel crossbow (u)": 46,
  "Mithril crossbow": 54, "Mithril crossbow (u)": 54,
  "Adamant crossbow": 61, "Adamant crossbow (u)": 61,
  "Rune crossbow": 69, "Runite crossbow (u)": 69,
  "Dragon crossbow": 78, "Dragon crossbow (u)": 78,
};
for (const [crossbow, level] of Object.entries(CROSSBOWS)) {
  rule(crossbow, "FABRICABLE", "EQUIPMENT", [["FLETCHING", level]], "Fletching");
}

// Bolt tips: gate at the highest skill involved (usually Smithing for metal, Fletching for gems).
function boltTip(display, skill, level) {
  rule(display, "FABRICABLE", "AMMO", [[skill, level]], "Fletching");
}

const METAL_BOLT_TIPS = {
  "Bronze bolt tips": 5,
  "Iron bolt tips": 15,
  "Steel bolt tips": 30,
  "Mithril bolt tips": 50,
  "Adamant bolt tips": 70,
  "Runite bolt tips": 85,
};
for (const [tip, level] of Object.entries(METAL_BOLT_TIPS)) {
  boltTip(tip, "SMITHING", level);
}

const GEM_BOLT_TIPS = {
  "Opal bolt tips": 11, "Jade bolt tips": 26, "Pearl bolt tips": 41,
  "Topaz bolt tips": 48, "Sapphire bolt tips": 56, "Emerald bolt tips": 58,
  "Ruby bolt tips": 63, "Diamond bolt tips": 65, "Dragonstone bolt tips": 71,
  "Onyx bolt tips": 73, "Amethyst bolt tips": 82,
};
for (const [tip, level] of Object.entries(GEM_BOLT_TIPS)) {
  boltTip(tip, "FLETCHING", level);
}

// ------------------------------------------------------------------ construction / magic tablets

function standardTablet(display, construction) {
  rule(display, "FABRICABLE", "CHARGED", [
    ["CONSTRUCTION", construction, "TRADE"],
    ["CONSTRUCTION", construction, "ACTIVATE"],
  ], "Construction");
}

function magicTablet(display, magic) {
  rule(display, "FABRICABLE", "CHARGED", [
    ["MAGIC", magic, "TRADE"],
    ["MAGIC", magic, "ACTIVATE"],
  ], "Magic");
}

const STANDARD_TABLETS = {
  "Varrock teleport (tablet)": 40,
  "Lumbridge teleport (tablet)": 47,
  "Falador teleport (tablet)": 47,
  "Camelot teleport (tablet)": 57,
  "Ardougne teleport (tablet)": 57,
  "Kourend castle teleport (tablet)": 57,
  "Watchtower teleport (tablet)": 67,
};
for (const [tablet, construction] of Object.entries(STANDARD_TABLETS)) {
  standardTablet(tablet, construction);
}

const MAGIC_TABLETS = {
  "Paddewwa teleport (tablet)": 54,
  "Senntisten teleport (tablet)": 60,
  "Kharyrll teleport (tablet)": 66,
  "Lassar teleport (tablet)": 72,
  "Dareeyak teleport (tablet)": 78,
  "Carrallanger teleport (tablet)": 84,
  "Annakarl teleport (tablet)": 90,
  "Ghorrock teleport (tablet)": 96,
  "Ape atoll teleport (tablet)": 64,
  "Arceuus library teleport (tablet)": 6,
  "Draynor manor teleport (tablet)": 17,
  "Battlefront teleport (tablet)": 23,
  "Mind altar teleport (tablet)": 28,
  "Salve graveyard teleport (tablet)": 40,
  "Fenkenstrain's castle teleport (tablet)": 48,
  "West ardougne teleport (tablet)": 58,
  "Harmony island teleport (tablet)": 65,
  "Cemetery teleport (tablet)": 71,
  "Barrows teleport (tablet)": 83,
  "Moonclan teleport (tablet)": 69,
  "Ourania teleport (tablet)": 71,
  "Waterbirth teleport (tablet)": 72,
  "Barbarian teleport (tablet)": 75,
  "Khazard teleport (tablet)": 80,
  "Fishing guild teleport (tablet)": 85,
  "Catherby teleport (tablet)": 87,
  "Ice plateau teleport (tablet)": 89,
};
for (const [tablet, magic] of Object.entries(MAGIC_TABLETS)) {
  magicTablet(tablet, magic);
}

// ------------------------------------------------------------------------ hunter

rule("Chinchompa", "GATHERABLE", "AMMO", [["HUNTER", 53]], "Hunter");
rule("Red chinchompa", "GATHERABLE", "AMMO", [["HUNTER", 63]], "Hunter");
rule("Black chinchompa", "GATHERABLE", "AMMO", [["HUNTER", 73]], "Hunter");

// ---------------------------------------------------------------------- crafting

// Unenchanted jewellery: crafting only.
const JEWELLERY = {
  "Gold ring": 5, "Gold necklace": 6, "Gold amulet": 8,
  "Sapphire ring": 20, "Sapphire necklace": 22, "Sapphire bracelet": 23, "Sapphire amulet": 24,
  "Emerald ring": 27, "Emerald necklace": 29, "Emerald bracelet": 30, "Emerald amulet": 31,
  "Ruby ring": 34, "Ruby necklace": 40, "Ruby bracelet": 42, "Ruby amulet": 50,
  "Diamond ring": 43, "Diamond necklace": 56, "Diamond bracelet": 58, "Diamond amulet": 70,
  "Dragonstone ring": 55, "Dragon necklace": 72, "Dragonstone bracelet": 74, "Dragonstone amulet": 80,
  "Onyx ring": 67, "Onyx necklace": 82, "Onyx bracelet": 84, "Onyx amulet": 90,
  "Zenyte ring": 89, "Zenyte necklace": 92, "Zenyte bracelet": 95, "Zenyte amulet": 98,
};
for (const [piece, level] of Object.entries(JEWELLERY)) {
  rule(piece, "FABRICABLE", "JEWELLERY", [["CRAFTING", level]], "Crafting");
}

// Enchanted jewellery needs the crafting level for the base and the magic level for
// the enchant. Amulet of glory is the worked example from the brief: 80 + 68.
const ENCHANT = { sapphire: 7, emerald: 27, ruby: 49, diamond: 57, dragonstone: 68, onyx: 87, zenyte: 93 };

const ENCHANTED = [
  ["Ring of recoil", "Sapphire ring", "sapphire"],
  ["Amulet of magic", "Sapphire amulet", "sapphire"],
  ["Ring of dueling", "Emerald ring", "emerald"],
  ["Amulet of strength", "Ruby amulet", "ruby"],
  ["Ring of life", "Diamond ring", "diamond"],
  ["Amulet of power", "Diamond amulet", "diamond"],
  ["Ring of wealth", "Dragonstone ring", "dragonstone"],
  ["Skills necklace", "Dragon necklace", "dragonstone"],
  ["Combat bracelet", "Dragonstone bracelet", "dragonstone"],
  ["Amulet of glory", "Dragonstone amulet", "dragonstone"],
  ["Amulet of fury", "Onyx amulet", "onyx"],
  ["Ring of suffering", "Zenyte ring", "zenyte"],
  ["Necklace of anguish", "Zenyte necklace", "zenyte"],
  ["Tormented bracelet", "Zenyte bracelet", "zenyte"],
  ["Amulet of torture", "Zenyte amulet", "zenyte"],
];
// Two skills made these and they gate two different acts, so the Magic half is scoped
// to ACTIVATE: Crafting 80 wears an amulet of glory, Magic 68 uses its teleports.
for (const [name, base, gem] of ENCHANTED) {
  rule(name, "FABRICABLE", "JEWELLERY", [
    ["CRAFTING", JEWELLERY[base]],
    ["MAGIC", ENCHANT[gem], "ACTIVATE"],
  ], "Crafting + Enchant");
}

const DHIDE = [
  ["Green", 57, 60, 63], ["Blue", 66, 68, 71], ["Red", 73, 75, 77], ["Black", 79, 82, 84],
];
for (const [colour, vambs, chaps, body] of DHIDE) {
  rule(`${colour} d'hide vambraces`, "FABRICABLE", "EQUIPMENT", [["CRAFTING", vambs]], "Crafting");
  rule(`${colour} d'hide chaps`, "FABRICABLE", "EQUIPMENT", [["CRAFTING", chaps]], "Crafting");
  rule(`${colour} d'hide body`, "FABRICABLE", "EQUIPMENT", [["CRAFTING", body]], "Crafting");
}

// Battlestaves need a charged orb, so magic co-gates them.
const BATTLESTAVES = [
  ["Water battlestaff", 54, 56], ["Earth battlestaff", 58, 60],
  ["Fire battlestaff", 62, 63], ["Air battlestaff", 66, 66],
];
for (const [staff, crafting, magic] of BATTLESTAVES) {
  rule(staff, "FABRICABLE", "EQUIPMENT", [["CRAFTING", crafting], ["MAGIC", magic]], "Crafting + Charge orb");
}

// Basic elemental staves supply runes on autocast. Wield gates on RC when runes are gated:
// Air uses 2 (Wind Strike needs Mind rune), not 1 (Air rune alone).
const ELEMENTAL_STAVES = [
  ["Staff of air", 2], ["Staff of water", 5], ["Staff of earth", 9], ["Staff of fire", 14],
];
for (const [staff, level] of ELEMENTAL_STAVES) {
  rule(staff, "FREE", "ELEMENTAL_STAFF", [["RUNECRAFT", level, "WIELD"]], "Runecrafting");
}

// Gem shops sell uncut and cut stones. Gate at the Crafting level to cut each gem.
const GEM_CUTTING = [
  ["Uncut opal", "Opal", 1],
  ["Uncut jade", "Jade", 13],
  ["Uncut red topaz", "Red topaz", 16],
  ["Uncut sapphire", "Sapphire", 20],
  ["Uncut emerald", "Emerald", 27],
  ["Uncut ruby", "Ruby", 34],
  ["Uncut diamond", "Diamond", 43],
  ["Uncut dragonstone", "Dragonstone", 55],
  ["Uncut onyx", "Onyx", 67],
  ["Uncut zenyte", "Zenyte", 89],
];
for (const [uncut, cut, level] of GEM_CUTTING) {
  rule(uncut, "FABRICABLE", "NONE", [["CRAFTING", level]], "Crafting");
  rule(cut, "FABRICABLE", "NONE", [["CRAFTING", level]], "Crafting");
}

// Herblore/Fletching shop supplies with no recipe — obtain singles before bulk packs.
rule("Eye of newt", "DROP_ONLY", "NONE", [], "Shop/Drop");
rule("Feather", "DROP_ONLY", "NONE", [], "Shop/Drop");
rule("Eye of newt pack", "FREE", "NONE", [], "Shop pack", { packOf: "eye of newt", tradeable: false });
rule("Feather pack", "FREE", "NONE", [], "Shop pack", { packOf: "feather", tradeable: false });

// NPC-shop-only items: buying counts as obtain so use is not deadlocked.
rule("Teleport card", "SHOP_ONLY", "NONE", [], "Shop");

// ------------------------------------------------------------------------- free

// No fabrication route and no natural level. Open on both gates by default, and the
// exact set the custom rule list exists to let players tighten.
const FREE = [
  "Bones", "Big bones", "Babydragon bones", "Wolf bones", "Burnt bones",
  "Monkey bones", "Bat bones", "Jogre bones", "Zogre bones", "Shaikahan bones",
  "Dragon bones", "Wyvern bones", "Dagannoth bones", "Ourg bones",
  "Superior dragon bones", "Lava dragon bones", "Fayrg bones", "Raurg bones",
  "Fiendish ashes", "Vile ashes", "Malicious ashes", "Abyssal ashes", "Infernal ashes",
  "Coins", "Bucket", "Jug", "Pot", "Vial", "Bowl", "Cake tin",
];
for (const name of FREE) {
  rule(name, "FREE", "NONE", [], null);
}

// ------------------------------------------------------------------------ spells

// Cast gating blocks the spellbook; basic elemental staves are gated separately on wield
// (see ELEMENTAL_STAVES above) because autocast selection bypasses menu hooks.
const spells = [];

function spell(name, runes, utility = false) {
  spells.push({ name, runes, utility });
}

const ELEMENTS = { Wind: [], Water: ["water rune"], Earth: ["earth rune"], Fire: ["fire rune"] };
const TIERS = [
  ["Strike", "mind rune"], ["Bolt", "chaos rune"], ["Blast", "death rune"],
  ["Wave", "blood rune"], ["Surge", "wrath rune"],
];
for (const [element, extra] of Object.entries(ELEMENTS)) {
  for (const [tier, catalyst] of TIERS) {
    spell(`${element} ${tier}`, ["air rune", ...extra, catalyst]);
  }
}

spell("Low Level Alchemy", ["fire rune", "nature rune"], true);
spell("High Level Alchemy", ["fire rune", "nature rune"], true);
spell("Superheat Item", ["fire rune", "nature rune"], true);
spell("Telekinetic Grab", ["air rune", "law rune"], true);
spell("Bones to Bananas", ["earth rune", "water rune", "nature rune"], true);
spell("Bones to Peaches", ["earth rune", "water rune", "nature rune"], true);

spell("Varrock Teleport", ["air rune", "fire rune", "law rune"], true);
spell("Lumbridge Teleport", ["air rune", "earth rune", "law rune"], true);
spell("Falador Teleport", ["air rune", "water rune", "law rune"], true);
spell("Camelot Teleport", ["air rune", "law rune"], true);
spell("Ardougne Teleport", ["water rune", "law rune"], true);
spell("Watchtower Teleport", ["earth rune", "law rune"], true);
spell("Trollheim Teleport", ["fire rune", "law rune"], true);
spell("Teleport to House", ["air rune", "earth rune", "law rune"], true);

spell("Lvl-1 Enchant", ["water rune", "cosmic rune"], true);
spell("Lvl-2 Enchant", ["air rune", "cosmic rune"], true);
spell("Lvl-3 Enchant", ["fire rune", "cosmic rune"], true);
spell("Lvl-4 Enchant", ["earth rune", "cosmic rune"], true);
spell("Lvl-5 Enchant", ["water rune", "earth rune", "cosmic rune"], true);
spell("Lvl-6 Enchant", ["fire rune", "earth rune", "cosmic rune"], true);
spell("Lvl-7 Enchant", ["blood rune", "soul rune", "cosmic rune"], true);

spell("Ice Rush", ["water rune", "chaos rune", "death rune"]);
spell("Ice Burst", ["water rune", "chaos rune", "death rune"]);
spell("Ice Blitz", ["water rune", "blood rune", "death rune"]);
spell("Ice Barrage", ["water rune", "blood rune", "death rune"]);
spell("Blood Barrage", ["blood rune", "death rune", "soul rune"]);
spell("Shadow Barrage", ["air rune", "blood rune", "death rune", "soul rune"]);
spell("Smoke Barrage", ["air rune", "fire rune", "blood rune", "death rune"]);

spell("Paddewwa Teleport", ["air rune", "fire rune", "law rune"], true);
spell("Senntisten Teleport", ["law rune", "soul rune"], true);
spell("Kharyrll Teleport", ["blood rune", "law rune"], true);
spell("Lassar Teleport", ["water rune", "law rune"], true);
spell("Dareeyak Teleport", ["air rune", "fire rune", "law rune"], true);
spell("Carrallanger Teleport", ["law rune", "soul rune"], true);
spell("Annakarl Teleport", ["blood rune", "law rune"], true);
spell("Ghorrock Teleport", ["water rune", "law rune"], true);

spell("Humidify", ["astral rune", "fire rune", "water rune"], true);
spell("Plank Make", ["astral rune", "earth rune", "nature rune"], true);
spell("NPC Contact", ["air rune", "astral rune", "cosmic rune"], true);
spell("Spin Flax", ["air rune", "astral rune", "nature rune"], true);
spell("String Jewellery", ["astral rune", "earth rune", "water rune"], true);
spell("Vengeance", ["astral rune", "death rune", "earth rune"], true);

// ------------------------------------------------------------------------ output

// ------------------------------------------------------------------ GE catalog

// Every GE tradeable gets a rule entry so the catalog and custom editor can find it.
// Items without a fabrication ladder are FREE (no skill gate unless overridden).
const GE_KEYS = JSON.parse(
  readFileSync(resolve(HERE, "../src/main/resources/com/leadman/ge-tradeables.json"), "utf8"),
);
const covered = new Set(items.map((i) => normalise(i.name)));
for (const key of GE_KEYS) {
  if (covered.has(key)) {
    continue;
  }
  const display = key.charAt(0).toUpperCase() + key.slice(1);
  rule(display, "FREE", "NONE", [], null);
  covered.add(key);
}

// ------------------------------------------------------------------ output

const seen = new Set();
const deduped = [];
for (const item of items) {
  const key = item.name.toLowerCase();
  if (seen.has(key)) {
    console.warn(`duplicate rule for "${item.name}" -- keeping the first`);
    continue;
  }
  seen.add(key);
  deduped.push(item);
}

const payload = {
  version: 1,
  generated: new Date().toISOString(),
  note: "Generated by tools/generate-rules.mjs. Do not hand-edit; use overrides.json.",
  items: deduped,
  spells,
};

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(payload, null, 1) + "\n", "utf8");

console.log(`wrote ${deduped.length} item rules and ${spells.length} spell rules`);
console.log(`  -> ${OUT}`);

/**
 * Mirrors com.leadman.rules.ItemNames#normalise. Both sides of the comparison have to
 * be normalised the same way, or every dosed potion looks like a missing item.
 */
function normalise(raw) {
  let s = String(raw).trim().toLowerCase();
  let changed = true;
  while (changed) {
    changed = false;
    let next = s.replace(/\(\d+\)$/, "").trim();
    if (next !== s) { s = next; changed = true; }
    next = s.replace(
      /\((?:t\d*|g|or|i|l|u|p\+{0,2}|nz|cr|m|e|x|s|broken|inactive|free|deadman|last man standing)\)$/,
      "").trim();
    if (next !== s) { s = next; changed = true; }
    if (s.includes("'s ")) {
      next = s.replace(/\s(?:0|25|50|75|100)$/, "").trim();
      if (next !== s) { s = next; changed = true; }
    }
  }
  return s;
}

if (process.argv.includes("--verify")) {
  const res = await fetch("https://prices.runescape.wiki/api/v1/osrs/mapping", {
    headers: { "User-Agent": "leadman-rules-generator" },
  });
  const mapping = await res.json();
  const known = new Set(mapping.map((m) => normalise(m.name)));

  const missing = deduped
    .filter((i) => i.itemClass !== "FREE" && i.tradeable !== false)
    .map((i) => i.name)
    .filter((n) => !known.has(normalise(n)));

  if (missing.length) {
    console.warn(`\n${missing.length} generated names do not match a tradeable item:`);
    for (const name of missing) console.warn(`  ${name}`);
    console.warn("\nFix the ladder or add an entry to overrides.json.");
  } else {
    console.log("all generated names matched a real tradeable item");
  }
}
