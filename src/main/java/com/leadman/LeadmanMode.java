package com.leadman;

/**
 * How far the USE gate reaches. TRADE is always gated, on every item.
 *
 * <p>The default follows one principle: <em>gate use only where the game provides no
 * requirement of its own.</em> A rune scimitar already asks for 40 Attack and rune arrows
 * already ask for 40 Ranged, so a looted one is fair to use. Nothing at all stands between
 * you and eating a shark, drinking a brew, or putting on an amulet of glory -- so Leadman
 * supplies the requirement the game left out.
 */
public enum LeadmanMode
{
	/** TRADE gates only. Bronzeman with skilling unlocks bolted on. */
	BRONZEMAN_PLUS("Bronzeman+"),
	/** Per-skill use gates, defaulting to the items the game does not gate itself. */
	STANDARD("Standard"),
	/** Every use gate on, including equipment and tools. Ignores the per-skill toggles. */
	STRICT("Strict");

	private final String label;

	LeadmanMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
