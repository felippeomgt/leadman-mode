package com.leadman;

/**
 * How Smithing gates metal armour and weapons ({@code EQUIPMENT} consume).
 *
 * <p>Trade and shop always follow fabrication Smithing in {@link #RESTRICT} and
 * {@link #MIXED}. {@link #BALANCED} uses the same level as vanilla wield stats for
 * every action.
 */
public enum EquipmentSmithingMode
{
	RESTRICT("Restrict"),
	BALANCED("Balanced"),
	MIXED("Mixed");

	private final String name;

	EquipmentSmithingMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
