package com.leadman.unlock;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Everything about a Leadman profile that cannot be recomputed.
 *
 * <p>Deliberately absent: which items are currently fabricable. That is derived from
 * live skill levels on every login, so it can never drift out of sync with the account
 * and never needs migrating when the ruleset changes.
 */
public class UnlockState
{
	private int version = 1;

	/** Normalised names of every item this account has held at least once. */
	private Set<String> obtained = new LinkedHashSet<>();

	/** Completed quests, diaries and activities, by rule key. */
	private Set<String> activities = new HashSet<>();

	/** Unlocks already shown, so a popup never fires twice for the same item. */
	private Set<String> seen = new HashSet<>();

	/** Most recent unlocks (obtain or fabrication), newest first. Capped in UnlockService. */
	private java.util.List<String> recentUnlocked = new java.util.ArrayList<>();

	/** True once the first full scan has run, so login does not fire a popup storm. */
	private boolean seeded;

	public Set<String> getObtained()
	{
		if (obtained == null)
		{
			obtained = new LinkedHashSet<>();
		}
		return obtained;
	}

	public Set<String> getActivities()
	{
		if (activities == null)
		{
			activities = new HashSet<>();
		}
		return activities;
	}

	public Set<String> getSeen()
	{
		if (seen == null)
		{
			seen = new HashSet<>();
		}
		return seen;
	}

	public boolean isSeeded()
	{
		return seeded;
	}

	public void setSeeded(boolean seeded)
	{
		this.seeded = seeded;
	}

	public java.util.List<String> getRecentUnlocked()
	{
		if (recentUnlocked == null)
		{
			recentUnlocked = new java.util.ArrayList<>();
		}
		return recentUnlocked;
	}

	public int getVersion()
	{
		return version;
	}
}
