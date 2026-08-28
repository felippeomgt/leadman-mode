package com.leadman.rules;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One legitimate way to produce an item. All requirements in a path must be met;
 * an item is fabricable when <em>any</em> of its paths is satisfied.
 */
public class UnlockPath
{
	private PathType type;
	private List<Requirement> reqs;
	private String source;
	/** For ACTIVITY paths: the flag key that must be present in the saved state. */
	private String activity;

	public UnlockPath()
	{
	}

	public UnlockPath(PathType type, List<Requirement> reqs, String source)
	{
		this.type = type;
		this.reqs = reqs;
		this.source = source;
	}

	public PathType getType()
	{
		return type == null ? PathType.SKILL : type;
	}

	public List<Requirement> getReqs()
	{
		return reqs == null ? Collections.emptyList() : reqs;
	}

	public String getSource()
	{
		return source;
	}

	public String getActivity()
	{
		return activity;
	}

	/** Human readable requirement line, e.g. "Crafting 80 + Magic 68". */
	public String describe()
	{
		if (getType() == PathType.ACTIVITY)
		{
			return source != null ? source : String.valueOf(activity);
		}
		if (getReqs().isEmpty())
		{
			return "no requirement";
		}
		return getReqs().stream().map(Requirement::toString).collect(Collectors.joining(" + "));
	}
}
