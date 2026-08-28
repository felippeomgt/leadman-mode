package com.leadman.rules;

import net.runelite.api.Skill;

/**
 * One skill level requirement. Deserialised straight from the rules JSON, so the
 * skill is carried as a string and resolved lazily -- an unknown skill name must not
 * blow up the whole ruleset.
 */
public class Requirement
{
	private String skill;
	private int level;

	/**
	 * "ACTIVATE" marks a requirement that guards using an item's charge rather than
	 * holding or wearing it. An amulet of glory carries Crafting 80 unscoped and Magic 68
	 * scoped to ACTIVATE: 80 Crafting puts it round your neck, 68 Magic fires the
	 * teleport. Null means the requirement applies to everything.
	 */
	private String scope;

	private transient Skill resolved;
	private transient boolean resolveAttempted;

	public Requirement()
	{
	}

	public Requirement(Skill skill, int level)
	{
		this.skill = skill.name();
		this.level = level;
		this.resolved = skill;
		this.resolveAttempted = true;
	}

	public int getLevel()
	{
		return level;
	}

	public String getSkillName()
	{
		return skill;
	}

	/** True when this requirement guards the charge, not wearing or holding the item. */
	public boolean isActivateOnly()
	{
		return "ACTIVATE".equalsIgnoreCase(scope);
	}

	/**
	 * @return the resolved skill, or null if the rules file names a skill this client
	 * does not know about (new skill, typo, or a renamed enum constant).
	 */
	public Skill getSkill()
	{
		if (!resolveAttempted)
		{
			resolveAttempted = true;
			try
			{
				resolved = Skill.valueOf(skill.toUpperCase());
			}
			catch (IllegalArgumentException | NullPointerException e)
			{
				resolved = null;
			}
		}
		return resolved;
	}

	@Override
	public String toString()
	{
		return prettySkill() + " " + level;
	}

	/** Skill enum constants are SCREAMING_CASE; players read "Runecrafting". */
	public String prettySkill()
	{
		Skill s = getSkill();
		String raw = s == null ? String.valueOf(skill) : s.name();
		if (raw.isEmpty())
		{
			return raw;
		}
		return raw.charAt(0) + raw.substring(1).toLowerCase();
	}
}
