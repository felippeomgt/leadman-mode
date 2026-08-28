package com.leadman.rules;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The gate definition for one canonical item.
 *
 * <p>Rules are keyed by <em>normalised name</em> rather than item id. Names are stable
 * across game updates and collapse charge/degrade/ornament variants for free, whereas
 * ids churn and would need a regenerated table every patch. See
 * {@link com.leadman.rules.ItemNames}.
 */
public class ItemRule
{
	/** Normalised lookup key, e.g. "amulet of glory". */
	private String name;
	/** Display name for the UI, e.g. "Amulet of glory". */
	private String display;
	private ItemClass itemClass;
	private ConsumeClass consume;
	private List<UnlockPath> paths;

	public ItemRule()
	{
	}

	public ItemRule(String name, String display, ItemClass itemClass, ConsumeClass consume, List<UnlockPath> paths)
	{
		this.name = name;
		this.display = display;
		this.itemClass = itemClass;
		this.consume = consume;
		this.paths = paths;
	}

	public String getName()
	{
		return name;
	}

	public String getDisplay()
	{
		return display != null ? display : name;
	}

	public ItemClass getItemClass()
	{
		return itemClass == null ? ItemClass.FREE : itemClass;
	}

	public ConsumeClass getConsume()
	{
		return consume == null ? ConsumeClass.NONE : consume;
	}

	public List<UnlockPath> getPaths()
	{
		return paths == null ? Collections.emptyList() : paths;
	}

	public boolean hasSkillPath()
	{
		return getPaths().stream().anyMatch(p -> p.getType() == PathType.SKILL && !p.getReqs().isEmpty());
	}

	/** "Crafting 80 + Magic 68, or Smithing 90" -- every path, joined. */
	public String describeRequirements()
	{
		if (getPaths().isEmpty())
		{
			return "obtain one";
		}
		return getPaths().stream().map(UnlockPath::describe).collect(Collectors.joining(", or "));
	}

	void setName(String name)
	{
		this.name = name;
	}
}
