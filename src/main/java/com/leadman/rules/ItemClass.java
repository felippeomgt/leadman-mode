package com.leadman.rules;

/** How an item can legitimately enter a Leadman account. */
public enum ItemClass
{
	/** A skilling recipe produces it. */
	FABRICABLE,
	/** A gathering skill produces it directly (raw fish, logs, ores, herbs). */
	GATHERABLE,
	/** No recipe exists. Unlocks for trade on first obtain, Bronzeman style. */
	DROP_ONLY,
	/** Quest, diary, minigame or activity reward. */
	REWARD_ONLY,
	/** Only sold in NPC shops — buying counts as obtain; use allowed once held. */
	SHOP_ONLY,
	/** No level attached to it at all. Open on both gates by default. */
	FREE
}
