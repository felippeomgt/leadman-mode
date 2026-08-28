package com.leadman.rules;

/**
 * How an item is spent when it is used. This is the single predicate that decides
 * whether the USE gate applies to an item at all -- see docs/DESIGN.md section 2.
 *
 * TRADE is gated on every item regardless of this value.
 */
public enum ConsumeClass
{
	/** Eaten. Destroyed on use. */
	FOOD,
	/** Drunk. Consumes a dose. */
	POTION,
	/** Arrows, bolts, darts, javelins, chinchompas. Consumed when fired. */
	AMMO,
	/** Runes. Consumed when cast. */
	RUNE,
	/**
	 * Jewellery, plain or enchanted. Nothing in the game stops you putting on an amulet,
	 * so the Crafting level that made it is the requirement Leadman supplies. Split from
	 * {@link #CHARGED} because wearing one and firing its teleport are separate acts
	 * gated by separate skills: Crafting to wear, Magic to activate.
	 */
	JEWELLERY,
	/** Teleport tabs and scrolls, and the charge half of enchanted jewellery. */
	CHARGED,
	/** Permanent weapons, armour and shields. Wielding costs nothing. */
	EQUIPMENT,
	/** Axes, pickaxes, harpoons, knives, needles, hammers. */
	TOOL,
	/** Materials and everything else. Never USE-gated. */
	NONE
}
