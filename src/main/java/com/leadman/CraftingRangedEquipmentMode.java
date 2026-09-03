package com.leadman;

/**
 * How Crafting and Fletching gate leather, dragonhide, snakeskin, and bow/crossbow
 * equipment ({@code EQUIPMENT} consume with a Crafting or Fletching fabrication path).
 *
 * <p>Trade and shop always follow fabrication levels in {@link #RESTRICT} and
 * {@link #MIXED}. {@link #BALANCED} uses the same level as vanilla Ranged/Defence
 * wield stats for every action.
 */
public enum CraftingRangedEquipmentMode
{
	RESTRICT("Restrict"),
	BALANCED("Balanced"),
	MIXED("Mixed");

	private final String name;

	CraftingRangedEquipmentMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
