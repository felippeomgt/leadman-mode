package com.leadman.unlock;

import com.leadman.rules.ItemNames;
import com.leadman.rules.Requirement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Skill;

/**
 * A player-defined gate for an item the game does not naturally tie to a skill --
 * bones to Prayer, Graceful to Agility, minigame gear to Slayer.
 *
 * <p>Custom rules replace any generated rule for the same item, on the gates they name.
 */
public class CustomRule
{
	private String item;
	private boolean gateTrade = true;
	private boolean gateUse = true;
	private List<Requirement> reqs = new ArrayList<>();

	public CustomRule()
	{
	}

	public CustomRule(String item, Skill skill, int level, boolean gateTrade, boolean gateUse)
	{
		this.item = item;
		this.gateTrade = gateTrade;
		this.gateUse = gateUse;
		this.reqs.add(new Requirement(skill, level));
	}

	public String getItem()
	{
		return item;
	}

	public String getKey()
	{
		return ItemNames.normalise(item);
	}

	public boolean isGateTrade()
	{
		return gateTrade;
	}

	public boolean isGateUse()
	{
		return gateUse;
	}

	public List<Requirement> getReqs()
	{
		return reqs == null ? new ArrayList<>() : reqs;
	}

	public void setItem(String item)
	{
		this.item = item;
	}

	public void setGateTrade(boolean gateTrade)
	{
		this.gateTrade = gateTrade;
	}

	public void setGateUse(boolean gateUse)
	{
		this.gateUse = gateUse;
	}

	public void setReqs(List<Requirement> reqs)
	{
		this.reqs = reqs;
	}

	public String describe()
	{
		if (getReqs().isEmpty())
		{
			return "no requirement";
		}
		return getReqs().stream().map(Requirement::toString).collect(Collectors.joining(" + "));
	}
}
