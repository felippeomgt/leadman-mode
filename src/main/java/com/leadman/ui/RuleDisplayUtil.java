package com.leadman.ui;

import com.leadman.rules.ConsumeClass;
import com.leadman.rules.ItemRule;
import com.leadman.rules.PathType;
import com.leadman.rules.Requirement;
import com.leadman.rules.UnlockPath;
import com.leadman.unlock.CustomRule;
import com.leadman.unlock.UnlockService;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Skill;

/** Shared helpers for turning rules into panel display values. */
public final class RuleDisplayUtil
{
	private RuleDisplayUtil()
	{
	}

	static List<LeadmanUi.ActionLine> actionLines(
		UnlockService unlockService,
		String key,
		ItemRule rule,
		CustomRule custom)
	{
		List<LeadmanUi.ActionLine> lines = new ArrayList<>();
		if (rule == null && custom == null)
		{
			return lines;
		}

		addLine(lines, "Can trade:", unlockService.meetsTradeRequirements(key),
			fromRequirements(unlockService.displayTradeRequirements(key)), rule, true);

		addLine(lines, "Can shop:", unlockService.meetsShopRequirements(key),
			fromRequirements(unlockService.displayShopRequirements(key)), rule, true);

		addLine(lines, "Can use:", unlockService.meetsUseRequirements(key),
			useMenuLevels(custom, rule, key, unlockService), rule, false);

		addLine(lines, "Can eat:", unlockService.meetsEatRequirements(key),
			fromRequirements(unlockService.displayEatRequirements(key)), rule, false);

		addLine(lines, "Can drink:", unlockService.meetsDrinkRequirements(key),
			fromRequirements(unlockService.displayDrinkRequirements(key)), rule, false);

		addLine(lines, "Can wield:", unlockService.meetsWieldRequirements(key),
			fromRequirements(unlockService.displayWieldRequirements(key)), rule, false);

		addLine(lines, "Can teleport:", unlockService.meetsActivateRequirements(key),
			fromRequirements(unlockService.displayActivateRequirements(key)), rule, false);

		addLine(lines, "Can bury:", unlockService.meetsBuryRequirements(key),
			fromRequirements(unlockService.displayBuryRequirements(key)), rule, false);

		return lines;
	}

	public static boolean hasLockedActions(
		UnlockService unlockService,
		String key,
		ItemRule rule,
		CustomRule custom)
	{
		return actionLines(unlockService, key, rule, custom).stream()
			.anyMatch(line -> !line.unlocked);
	}

	/** Comma-separated list of locked action names, or null if everything is open. */
	public static String lockedActionsSummary(
		UnlockService unlockService,
		String key,
		ItemRule rule,
		CustomRule custom)
	{
		List<String> locked = new ArrayList<>();
		for (LeadmanUi.ActionLine line : actionLines(unlockService, key, rule, custom))
		{
			if (!line.unlocked)
			{
				locked.add(line.label.replace(":", "").trim());
			}
		}
		return locked.isEmpty() ? null : String.join(", ", locked);
	}

	private static void addLine(
		List<LeadmanUi.ActionLine> lines,
		String label,
		boolean unlocked,
		List<LeadmanUi.SkillLevel> levels,
		ItemRule rule,
		boolean showObtainFallback)
	{
		if (levels.isEmpty())
		{
			if (showObtainFallback && rule != null && rule.getPaths().isEmpty())
			{
				lines.add(new LeadmanUi.ActionLine(label, unlocked, levels));
			}
			return;
		}
		lines.add(new LeadmanUi.ActionLine(label, unlocked, levels));
	}

	private static List<LeadmanUi.SkillLevel> useMenuLevels(
		CustomRule custom,
		ItemRule rule,
		String key,
		UnlockService unlockService)
	{
		List<LeadmanUi.SkillLevel> scoped = fromRequirements(unlockService.displayUseRequirements(key));
		if (!scoped.isEmpty())
		{
			return scoped;
		}
		if (rule == null)
		{
			return scoped;
		}
		ConsumeClass consume = rule.getConsume();
		if (consume == ConsumeClass.FOOD
			|| consume == ConsumeClass.POTION
			|| consume == ConsumeClass.JEWELLERY)
		{
			return scoped;
		}
		return scoped;
	}

	static List<LeadmanUi.SkillLevel> fromRequirements(List<Requirement> reqs)
	{
		List<LeadmanUi.SkillLevel> levels = new ArrayList<>();
		if (reqs == null)
		{
			return levels;
		}
		for (Requirement req : reqs)
		{
			Skill skill = req.getSkill();
			if (skill != null)
			{
				levels.add(new LeadmanUi.SkillLevel(skill, req.getLevel()));
			}
		}
		return levels;
	}

	static List<LeadmanUi.SkillLevel> tradeReqs(ItemRule rule)
	{
		return fromRequirements(collectFromRule(rule, true, false, false, false, false));
	}

	static List<LeadmanUi.SkillLevel> shopReqs(ItemRule rule)
	{
		return tradeReqs(rule);
	}

	static List<LeadmanUi.SkillLevel> useReqs(ItemRule rule)
	{
		return fromRequirements(collectFromRule(rule, false, true, false, false, false));
	}

	static List<LeadmanUi.SkillLevel> eatReqs(ItemRule rule)
	{
		if (rule == null || rule.getConsume() != ConsumeClass.FOOD)
		{
			return new ArrayList<>();
		}
		return wearDisplayLevels(rule);
	}

	static List<LeadmanUi.SkillLevel> drinkReqs(ItemRule rule)
	{
		if (rule == null || rule.getConsume() != ConsumeClass.POTION)
		{
			return new ArrayList<>();
		}
		return wearDisplayLevels(rule);
	}

	static List<LeadmanUi.SkillLevel> wieldReqs(ItemRule rule)
	{
		return fromRequirements(collectFromRule(rule, false, false, true, false, false));
	}

	static List<LeadmanUi.SkillLevel> activateReqs(ItemRule rule)
	{
		return fromRequirements(collectFromRule(rule, false, false, false, true, false));
	}

	static List<LeadmanUi.SkillLevel> buryReqs(ItemRule rule)
	{
		return fromRequirements(collectFromRule(rule, false, false, false, false, true));
	}

	static LeadmanUi.SkillLevel firstSkillLevel(List<LeadmanUi.SkillLevel> levels)
	{
		if (levels.isEmpty())
		{
			return new LeadmanUi.SkillLevel(Skill.ATTACK, 1);
		}
		return levels.get(0);
	}

	private static List<LeadmanUi.SkillLevel> wearDisplayLevels(ItemRule rule)
	{
		List<LeadmanUi.SkillLevel> levels = new ArrayList<>();
		for (UnlockPath path : rule.getPaths())
		{
			if (path.getType() != PathType.SKILL)
			{
				continue;
			}
			for (Requirement req : path.getReqs())
			{
				if (req.isTradeOnly())
				{
					Skill skill = req.getSkill();
					if (skill != null)
					{
						levels.add(new LeadmanUi.SkillLevel(skill, req.getLevel()));
					}
				}
			}
			if (!levels.isEmpty())
			{
				return levels;
			}
		}
		return levels;
	}

	private static List<Requirement> collectFromRule(
		ItemRule rule,
		boolean trade,
		boolean use,
		boolean wield,
		boolean activate,
		boolean bury)
	{
		List<Requirement> collected = new ArrayList<>();
		if (rule == null)
		{
			return collected;
		}
		for (UnlockPath path : rule.getPaths())
		{
			if (path.getType() != PathType.SKILL)
			{
				continue;
			}
			for (Requirement req : path.getReqs())
			{
				if (trade && (req.isTradeOnly() || req.isActivateOnly()))
				{
					collected.add(req);
				}
				else if (use && req.isUseOnly())
				{
					collected.add(req);
				}
				else if (wield && req.isWieldOnly())
				{
					collected.add(req);
				}
				else if (activate && req.isActivateOnly())
				{
					collected.add(req);
				}
				else if (bury && req.isBuryOnly())
				{
					collected.add(req);
				}
			}
			if (!collected.isEmpty())
			{
				return collected;
			}
		}
		return collected;
	}
}
