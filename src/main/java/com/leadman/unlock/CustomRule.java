package com.leadman.unlock;

import com.leadman.rules.ItemNames;
import com.leadman.rules.Requirement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Skill;

/**
 * Per-item overrides for individual gate actions. Stored in RuneLite config as JSON.
 * When {@code gateX} is false, that action falls back to the generated rule.
 */
public class CustomRule
{
	private String item;
	private boolean gateTrade;
	private boolean gateShop;
	private boolean gateUse;
	private boolean gateEat;
	private boolean gateDrink;
	private boolean gateWield;
	private boolean gateActivate;
	private boolean gateBury;
	private List<Requirement> tradeReqs = new ArrayList<>();
	private List<Requirement> shopReqs = new ArrayList<>();
	private List<Requirement> useReqs = new ArrayList<>();
	private List<Requirement> eatReqs = new ArrayList<>();
	private List<Requirement> drinkReqs = new ArrayList<>();
	private List<Requirement> wieldReqs = new ArrayList<>();
	private List<Requirement> activateReqs = new ArrayList<>();
	private List<Requirement> buryReqs = new ArrayList<>();
	/** Legacy single list; used when use/trade lists are empty after deserialisation. */
	private List<Requirement> reqs = new ArrayList<>();

	public CustomRule()
	{
	}

	public CustomRule(String item, Skill skill, int level, boolean gateTrade, boolean gateUse)
	{
		this.item = item;
		this.gateTrade = gateTrade;
		this.gateUse = gateUse;
		Requirement req = new Requirement(skill, level);
		this.reqs.add(req);
		this.useReqs.add(req);
		this.tradeReqs.add(req);
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

	public boolean isGateShop()
	{
		return gateShop;
	}

	public boolean isGateUse()
	{
		return gateUse;
	}

	public boolean isGateEat()
	{
		return gateEat;
	}

	public boolean isGateDrink()
	{
		return gateDrink;
	}

	public boolean isGateWield()
	{
		return gateWield;
	}

	public boolean isGateActivate()
	{
		return gateActivate;
	}

	public boolean isGateBury()
	{
		return gateBury;
	}

	public boolean hasAnyGate()
	{
		return gateTrade || gateShop || gateUse || gateEat || gateDrink
			|| gateWield || gateActivate || gateBury;
	}

	public List<Requirement> getTradeReqs()
	{
		if (tradeReqs != null && !tradeReqs.isEmpty())
		{
			return tradeReqs;
		}
		return reqs == null ? new ArrayList<>() : reqs;
	}

	public List<Requirement> getShopReqs()
	{
		return shopReqs == null ? new ArrayList<>() : shopReqs;
	}

	public List<Requirement> getUseReqs()
	{
		if (useReqs != null && !useReqs.isEmpty())
		{
			return useReqs;
		}
		return reqs == null ? new ArrayList<>() : reqs;
	}

	public List<Requirement> getEatReqs()
	{
		return eatReqs == null ? new ArrayList<>() : eatReqs;
	}

	public List<Requirement> getDrinkReqs()
	{
		return drinkReqs == null ? new ArrayList<>() : drinkReqs;
	}

	public List<Requirement> getWieldReqs()
	{
		return wieldReqs == null ? new ArrayList<>() : wieldReqs;
	}

	public List<Requirement> getActivateReqs()
	{
		return activateReqs == null ? new ArrayList<>() : activateReqs;
	}

	public List<Requirement> getBuryReqs()
	{
		return buryReqs == null ? new ArrayList<>() : buryReqs;
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

	public void setGateShop(boolean gateShop)
	{
		this.gateShop = gateShop;
	}

	public void setGateUse(boolean gateUse)
	{
		this.gateUse = gateUse;
	}

	public void setGateEat(boolean gateEat)
	{
		this.gateEat = gateEat;
	}

	public void setGateDrink(boolean gateDrink)
	{
		this.gateDrink = gateDrink;
	}

	public void setGateWield(boolean gateWield)
	{
		this.gateWield = gateWield;
	}

	public void setGateActivate(boolean gateActivate)
	{
		this.gateActivate = gateActivate;
	}

	public void setGateBury(boolean gateBury)
	{
		this.gateBury = gateBury;
	}

	public void setTradeReqs(List<Requirement> tradeReqs)
	{
		this.tradeReqs = tradeReqs == null ? new ArrayList<>() : tradeReqs;
	}

	public void setShopReqs(List<Requirement> shopReqs)
	{
		this.shopReqs = shopReqs == null ? new ArrayList<>() : shopReqs;
	}

	public void setUseReqs(List<Requirement> useReqs)
	{
		this.useReqs = useReqs == null ? new ArrayList<>() : useReqs;
	}

	public void setEatReqs(List<Requirement> eatReqs)
	{
		this.eatReqs = eatReqs == null ? new ArrayList<>() : eatReqs;
	}

	public void setDrinkReqs(List<Requirement> drinkReqs)
	{
		this.drinkReqs = drinkReqs == null ? new ArrayList<>() : drinkReqs;
	}

	public void setWieldReqs(List<Requirement> wieldReqs)
	{
		this.wieldReqs = wieldReqs == null ? new ArrayList<>() : wieldReqs;
	}

	public void setActivateReqs(List<Requirement> activateReqs)
	{
		this.activateReqs = activateReqs == null ? new ArrayList<>() : activateReqs;
	}

	public void setBuryReqs(List<Requirement> buryReqs)
	{
		this.buryReqs = buryReqs == null ? new ArrayList<>() : buryReqs;
	}

	public void setReqs(List<Requirement> reqs)
	{
		this.reqs = reqs;
	}

	public String describe()
	{
		if (getTradeReqs().isEmpty())
		{
			return "no requirement";
		}
		return getTradeReqs().stream().map(Requirement::toString).collect(Collectors.joining(" + "));
	}
}
