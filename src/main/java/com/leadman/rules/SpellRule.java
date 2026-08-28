package com.leadman.rules;

import java.util.Collections;
import java.util.List;

/**
 * Which runes a spell consumes, so a cast can be blocked when the player could not
 * craft one of them. Gating at the cast rather than at the rune item is deliberate:
 * an elemental staff supplies runes you never owned, and gating the item alone would
 * let a 30k staff launder a third of the rule. See docs/DESIGN.md section 6.1.
 */
public class SpellRule
{
	private String name;
	private List<String> runes;
	/** Teleports, alchemy, enchanting and other non-combat casts. */
	private boolean utility;

	public SpellRule()
	{
	}

	public String getName()
	{
		return name;
	}

	public List<String> getRunes()
	{
		return runes == null ? Collections.emptyList() : runes;
	}

	public boolean isUtility()
	{
		return utility;
	}
}
