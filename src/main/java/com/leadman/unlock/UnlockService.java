package com.leadman.unlock;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.leadman.EquipmentSmithingMode;
import com.leadman.LeadmanConfig;
import com.leadman.rules.ConsumeClass;
import com.leadman.rules.ItemNames;
import com.leadman.rules.ItemRule;
import com.leadman.rules.PathType;
import com.leadman.rules.Requirement;
import com.leadman.rules.RuleRepository;
import com.leadman.rules.SpellRule;
import com.leadman.rules.TradeableIndex;
import com.leadman.rules.UnlockPath;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.RuneLite;
import net.runelite.client.game.ItemManager;

/**
 * The gate engine. Everything the plugin blocks or allows resolves through here.
 *
 * <p>Two independent gates, per docs/DESIGN.md section 1:
 * <pre>
 * tradeUnlocked = fabricable OR (everObtained AND unmapped non-equippable GE)
 *                 OR (unmapped equippable GE)
 * useUnlocked   = fabricable OR no skill path (per gate toggles)
 *                 OR (unmapped equippable GE) OR (everObtained AND unmapped non-equippable GE)
 * </pre>
 * {@code everObtained} bypasses trade for drop-only items and unmapped GE supplies. Looting
 * a smithable plate does not unlock the Grand Exchange; it never grants the right to use it.
 */
@Slf4j
@Singleton
public class UnlockService
{
	private static final Type CUSTOM_RULES = new TypeToken<List<CustomRule>>()
	{
	}.getType();

	private final Client client;
	private final ItemManager itemManager;
	private final RuleRepository rules;
	private final TradeableIndex tradeableIndex;
	private final LeadmanConfig config;
	private final Gson gson;

	private UnlockState state = new UnlockState();
	private File stateFile;
	private boolean dirty;

	private Map<String, CustomRule> customRules = Collections.emptyMap();

	/** Rule keys currently fabricable. Diffed on level-up to find new unlocks. */
	private Set<String> satisfied = new HashSet<>();

	@Inject
	public UnlockService(Client client, ItemManager itemManager, RuleRepository rules,
						 TradeableIndex tradeableIndex, LeadmanConfig config, Gson gson)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.rules = rules;
		this.tradeableIndex = tradeableIndex;
		this.config = config;
		this.gson = gson;
	}

	// --------------------------------------------------------------- lifecycle

	/** Loads the profile for the logged-in account. Safe to call repeatedly. */
	public void loadProfile(long accountHash)
	{
		File dir = new File(RuneLite.RUNELITE_DIR, "leadman");
		if (!dir.exists() && !dir.mkdirs())
		{
			log.warn("Leadman: could not create {}", dir);
		}

		stateFile = new File(dir, accountHash + ".json");
		state = new UnlockState();

		if (stateFile.exists())
		{
			try (Reader r = Files.newBufferedReader(stateFile.toPath(), StandardCharsets.UTF_8))
			{
				UnlockState loaded = gson.fromJson(r, UnlockState.class);
				if (loaded != null)
				{
					state = loaded;
				}
			}
			catch (IOException | JsonSyntaxException e)
			{
				log.warn("Leadman: could not read {}, starting a fresh profile", stateFile, e);
			}
		}

		reloadCustomRules();
		dirty = true;
	}

	public void save()
	{
		if (!dirty || stateFile == null)
		{
			return;
		}
		try (Writer w = Files.newBufferedWriter(stateFile.toPath(), StandardCharsets.UTF_8))
		{
			gson.toJson(state, w);
			dirty = false;
		}
		catch (IOException e)
		{
			log.warn("Leadman: could not save {}", stateFile, e);
		}
	}

	public boolean isLoaded()
	{
		return stateFile != null;
	}

	public UnlockState getState()
	{
		return state;
	}

	public void reloadCustomRules()
	{
		Map<String, CustomRule> parsed = new LinkedHashMap<>();
		try
		{
			List<CustomRule> list = gson.fromJson(config.customRules(), CUSTOM_RULES);
			if (list != null)
			{
				for (CustomRule rule : list)
				{
					if (rule != null && rule.getKey() != null && !rule.getKey().isEmpty())
					{
						parsed.put(rule.getKey(), rule);
					}
				}
			}
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Leadman: custom rules are not valid JSON, ignoring them", e);
		}
		customRules = parsed;
	}

	public List<CustomRule> getCustomRules()
	{
		return new ArrayList<>(customRules.values());
	}

	public CustomRule getCustomRule(String key)
	{
		return customRules.get(key);
	}

	public void upsertCustomRule(CustomRule rule)
	{
		if (!rule.hasAnyGate())
		{
			removeCustomRule(rule.getKey());
			return;
		}
		List<CustomRule> all = new ArrayList<>(customRules.values());
		all.removeIf(r -> r.getKey().equals(rule.getKey()));
		all.add(rule);
		setCustomRules(all);
	}

	public void removeCustomRule(String key)
	{
		List<CustomRule> all = new ArrayList<>(customRules.values());
		all.removeIf(r -> r.getKey().equals(key));
		setCustomRules(all);
	}

	public void setCustomRules(List<CustomRule> list)
	{
		config.setCustomRules(gson.toJson(list));
		reloadCustomRules();
	}

	public void clearAllCustomRules()
	{
		setCustomRules(Collections.emptyList());
	}

	private static final int MAX_RECENT_UNLOCKS = 20;

	/** Records a unlock for the sidebar recent list (obtain or fabrication). */
	public void recordRecentUnlock(String key)
	{
		if (key == null || key.isEmpty())
		{
			return;
		}
		List<String> recent = state.getRecentUnlocked();
		recent.remove(key);
		recent.add(0, key);
		while (recent.size() > MAX_RECENT_UNLOCKS)
		{
			recent.remove(recent.size() - 1);
		}
		dirty = true;
	}

	public List<String> getRecentUnlockedKeys()
	{
		return Collections.unmodifiableList(new ArrayList<>(state.getRecentUnlocked()));
	}

	// ------------------------------------------------------------------ lookup

	/** Canonical rule key for an item id, unnoting and collapsing variants first. */
	public String keyFor(int itemId)
	{
		int canonical = itemManager.canonicalize(itemId);
		String geKey = tradeableIndex.geKeyForItemId(canonical);
		if (geKey != null && !geKey.isEmpty())
		{
			return geKey;
		}

		ItemComposition comp = itemManager.getItemComposition(canonical);
		return comp == null ? "" : ItemNames.normalise(comp.getName());
	}

	public String displayFor(int itemId)
	{
		int canonical = itemManager.canonicalize(itemId);
		ItemComposition comp = itemManager.getItemComposition(canonical);
		return comp == null ? "Item " + itemId : comp.getName();
	}

	// ------------------------------------------------------------------- gates

	public boolean canTrade(int itemId)
	{
		String key = keyFor(itemId);
		if (key.isEmpty())
		{
			return !tradeableIndex.isGeTradeableId(itemId);
		}
		return canTradeKey(key);
	}

	public boolean canTradeKey(String key)
	{
		if (key.isEmpty())
		{
			return true;
		}

		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateTrade())
		{
			// A custom rule is a deliberate hard lock. Obtaining the item does not
			// satisfy it, or "bones require 30 Prayer" would be defeated by one drop.
			return meets(custom.getTradeReqs());
		}

		ItemRule rule = rules.forName(key);
		if (rule != null && rule.hasSkillPath())
		{
			if (isSmithingEquipment(rule))
			{
				return meetsEquipmentSmithingForTrade(rule);
			}
			return satisfies(rule);
		}

		if (rule != null && isObtainOnlyClass(rule.getItemClass()))
		{
			if (!tradeableIndex.isGeTradeableKey(key))
			{
				return true;
			}
			return state.getObtained().contains(key);
		}

		if (rule != null && rule.getItemClass() == com.leadman.rules.ItemClass.FREE)
		{
			if (!tradeableIndex.isGeTradeableKey(key))
			{
				return true;
			}
			return state.getObtained().contains(key);
		}

		if (rule != null && !rule.hasSkillPath())
		{
			if (tradeableIndex.isGeTradeableKey(key))
			{
				return false;
			}
			return satisfies(rule);
		}

		if (rule == null)
		{
			return unmappedGeAllowed(key);
		}
		return satisfies(rule);
	}

	public boolean canShopKey(String key)
	{
		if (key.isEmpty())
		{
			return true;
		}

		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateShop())
		{
			return meets(custom.getShopReqs());
		}

		if (custom != null && custom.isGateTrade())
		{
			return meets(custom.getTradeReqs());
		}

		ItemRule rule = rules.forName(key);
		if (rule != null && rule.getPackOf() != null && !rule.getPackOf().isEmpty())
		{
			return state.getObtained().contains(rule.getPackOf());
		}

		if (rule != null && rule.getItemClass() == com.leadman.rules.ItemClass.SHOP_ONLY)
		{
			return true;
		}

		if (rule != null && rule.hasSkillPath())
		{
			if (isSmithingEquipment(rule))
			{
				return meetsEquipmentSmithingForTrade(rule);
			}
			return satisfies(rule);
		}

		if (rule != null && isObtainOnlyClass(rule.getItemClass()))
		{
			if (!tradeableIndex.isGeTradeableKey(key))
			{
				return true;
			}
			return state.getObtained().contains(key);
		}

		if (rule != null && !rule.hasSkillPath())
		{
			if (tradeableIndex.isGeTradeableKey(key))
			{
				return state.getObtained().contains(key);
			}
			return true;
		}

		if (rule == null)
		{
			if (!tradeableIndex.isGeTradeableKey(key))
			{
				return true;
			}
			return unmappedGeAllowed(key);
		}
		return true;
	}

	public boolean canWield(int itemId)
	{
		return canWieldKey(keyFor(itemId));
	}

	public boolean canWieldKey(String key)
	{
		if (key.isEmpty())
		{
			return true;
		}

		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateWield())
		{
			return meets(custom.getWieldReqs());
		}

		ItemRule rule = rules.forName(key);
		if (rule == null)
		{
			return true;
		}

		List<Requirement> wieldReqs = wieldRequirements(rule);
		if (!wieldReqs.isEmpty() && wieldGateApplies(rule))
		{
			if (!meets(wieldReqs))
			{
				return false;
			}
		}

		ConsumeClass consume = rule.getConsume();
		if (!wearGateApplies(consume) || consume == ConsumeClass.FOOD || consume == ConsumeClass.POTION)
		{
			return true;
		}

		if (!rule.hasSkillPath())
		{
			return true;
		}

		return meetsEquipmentSmithingForWear(rule);
	}

	public boolean canEat(int itemId)
	{
		return canEatKey(keyFor(itemId));
	}

	public boolean canEatKey(String key)
	{
		if (key.isEmpty())
		{
			return true;
		}

		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateEat())
		{
			return meets(custom.getEatReqs());
		}

		ItemRule rule = rules.forName(key);
		if (rule == null || rule.getConsume() != ConsumeClass.FOOD)
		{
			return true;
		}

		if (!config.gateFood() || !rule.hasSkillPath())
		{
			return true;
		}

		return satisfies(rule, ReqFilter.WEAR);
	}

	public boolean canDrink(int itemId)
	{
		return canDrinkKey(keyFor(itemId));
	}

	public boolean canDrinkKey(String key)
	{
		if (key.isEmpty())
		{
			return true;
		}

		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateDrink())
		{
			return meets(custom.getDrinkReqs());
		}

		ItemRule rule = rules.forName(key);
		if (rule == null || rule.getConsume() != ConsumeClass.POTION)
		{
			return true;
		}

		if (!config.gatePotions() || !rule.hasSkillPath())
		{
			return true;
		}

		return satisfies(rule, ReqFilter.WEAR);
	}

	public boolean canBury(int itemId)
	{
		return canBuryKey(keyFor(itemId));
	}

	public boolean canBuryKey(String key)
	{
		if (key.isEmpty())
		{
			return true;
		}

		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateBury())
		{
			return meets(custom.getBuryReqs());
		}

		ItemRule rule = rules.forName(key);
		if (rule == null)
		{
			return true;
		}

		List<Requirement> buryReqs = buryRequirements(rule);
		return buryReqs.isEmpty() || meets(buryReqs);
	}

	public boolean canUse(int itemId)
	{
		return canUseKey(keyFor(itemId));
	}

	public boolean canUseKey(String key)
	{
		if (key.isEmpty())
		{
			return true;
		}

		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateUse())
		{
			return meets(custom.getUseReqs());
		}

		ItemRule rule = rules.forName(key);
		if (rule == null)
		{
			if (!tradeableIndex.isGeTradeableKey(key))
			{
				return true;
			}
			return unmappedGeAllowed(key);
		}

		ConsumeClass consume = rule.getConsume();
		if (consume == ConsumeClass.FOOD || consume == ConsumeClass.POTION)
		{
			List<Requirement> useReqs = useRequirements(rule);
			return useReqs.isEmpty() || meets(useReqs, ReqFilter.USE);
		}

		List<Requirement> useReqs = useRequirements(rule);
		if (!useReqs.isEmpty())
		{
			if (!meets(useReqs, ReqFilter.USE))
			{
				return false;
			}
			if (wearGateApplies(consume))
			{
				return meetsEquipmentSmithingForWear(rule);
			}
			return true;
		}

		if (!rule.hasSkillPath())
		{
			if (rule.getItemClass() == com.leadman.rules.ItemClass.SHOP_ONLY)
			{
				return state.getObtained().contains(key) || hasItemHeld(key);
			}
			if (tradeableIndex.isGeTradeableKey(key) && !state.getObtained().contains(key))
			{
				return false;
			}
			return true;
		}

		if (!wearGateApplies(consume))
		{
			return true;
		}

		return meetsEquipmentSmithingForWear(rule);
	}

	/**
	 * Whether an item's charge may be spent -- rubbing a glory, firing its teleport.
	 *
	 * <p>Separate from {@link #canUseKey} because wearing a piece of jewellery and using
	 * what it does are gated by different skills: Crafting made the amulet, Magic put the
	 * teleports in it. Turning off the Crafting gate must not also hand over the
	 * teleports, and turning off the Magic gate must not stop you wearing it.
	 */
	public boolean canActivate(int itemId)
	{
		return canActivateKey(keyFor(itemId));
	}

	public boolean canActivateKey(String key)
	{
		if (key.isEmpty())
		{
			return true;
		}

		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateActivate())
		{
			return meets(custom.getActivateReqs());
		}

		// You cannot spend a charge on something you are not allowed to hold or wear.
		if (!canUseKey(key))
		{
			return false;
		}

		ItemRule rule = rules.forName(key);
		if (rule == null || !rule.hasSkillPath())
		{
			return true;
		}

		if (!activateGateApplies(rule.getConsume()))
		{
			return true;
		}

		List<Requirement> activateReqs = collectRequirements(rule, ReqFilter.ACTIVATE);
		if (activateReqs.isEmpty())
		{
			// Plain jewellery (gold necklace, etc.) has no charge to spend.
			return true;
		}

		return meets(activateReqs);
	}

	private boolean hasActivateRequirements(ItemRule rule)
	{
		return !collectRequirements(rule, ReqFilter.ACTIVATE).isEmpty();
	}

	/** True when this item has a charge worth gating separately. */
	public boolean isActivatable(String key)
	{
		ItemRule rule = rules.forName(key);
		if (rule == null)
		{
			return false;
		}
		ConsumeClass consume = rule.getConsume();
		if (consume == ConsumeClass.CHARGED)
		{
			return true;
		}
		if (consume == ConsumeClass.JEWELLERY)
		{
			return hasActivateRequirements(rule);
		}
		return false;
	}

	/**
	 * @return true when a cast is permitted. Blocked when any rune the spell consumes
	 * is itself locked -- gating at the cast rather than the item, so an elemental
	 * staff cannot supply a rune the player could not craft.
	 */
	public boolean canCast(String spellName)
	{
		if (!runeGateOn())
		{
			return true;
		}

		SpellRule spell = rules.forSpell(spellName);
		if (spell == null)
		{
			// Unmapped spell. Allowing it is the safe failure: a false block is a bug
			// the player experiences, a false allow is a rule they can self-enforce.
			return true;
		}

		for (String rune : spell.getRunes())
		{
			ItemRule rule = rules.forName(rune);
			if (rule != null && !satisfies(rule))
			{
				return false;
			}
		}
		return true;
	}

	/** Runes are only use-gated when the player opts in; spells carry Magic levels already. */
	private boolean runeGateOn()
	{
		return config.gateRunes();
	}

	/** The first rune of this spell the player cannot craft, for the block message. */
	public String missingRuneFor(String spellName)
	{
		SpellRule spell = rules.forSpell(spellName);
		if (spell == null)
		{
			return null;
		}
		for (String rune : spell.getRunes())
		{
			ItemRule rule = rules.forName(rune);
			if (rule != null && !satisfies(rule))
			{
				return rule.getDisplay() + " (" + rule.describeRequirements() + ")";
			}
		}
		return null;
	}

	// ------------------------------------------------------------ gate policy

	/** WIELD-scoped reqs on equipment always apply; elemental staves only when runes are gated. */
	private boolean wieldGateApplies(ItemRule rule)
	{
		if (wieldRequirements(rule).isEmpty())
		{
			return false;
		}
		if (rule.getConsume() == ConsumeClass.ELEMENTAL_STAFF)
		{
			return runeGateOn();
		}
		return true;
	}

	/** Applies to eat, drink, wear and wield. */
	private boolean wearGateApplies(ConsumeClass consume)
	{
		if (consume == ConsumeClass.NONE)
		{
			return false;
		}

		switch (consume)
		{
			case FOOD:
				return config.gateFood();
			case POTION:
				return config.gatePotions();
			case AMMO:
				return config.gateAmmo();
			case RUNE:
				return config.gateRunes();
			case JEWELLERY:
				return config.gateJewel();
			case CHARGED:
				return config.gateCharged();
			case EQUIPMENT:
				return true;
			case ELEMENTAL_STAFF:
				return runeGateOn();
			default:
				return false;
		}
	}

	/** Applies to rubbing, teleporting and anything else that spends a charge. */
	private boolean activateGateApplies(ConsumeClass consume)
	{
		if (consume == ConsumeClass.JEWELLERY || consume == ConsumeClass.CHARGED)
		{
			return config.gateCharged();
		}
		return wearGateApplies(consume);
	}

	private boolean isSmithingEquipment(ItemRule rule)
	{
		return rule.getConsume() == ConsumeClass.EQUIPMENT && smithingTradeLevel(rule) > 0;
	}

	private int smithingTradeLevel(ItemRule rule)
	{
		for (Requirement req : tradeRequirements(rule))
		{
			if (req.getSkill() == Skill.SMITHING)
			{
				return req.getLevel();
			}
		}
		return 0;
	}

	/** Smithing level tied to vanilla Attack/Defence/Ranged wield reqs; falls back to fabrication. */
	private int balancedSmithingLevel(ItemRule rule)
	{
		int max = 0;
		for (Requirement req : wieldRequirements(rule))
		{
			max = Math.max(max, req.getLevel());
		}
		if (max > 0)
		{
			return max;
		}
		return smithingTradeLevel(rule);
	}

	private boolean meetsBalancedSmithing(ItemRule rule)
	{
		int level = balancedSmithingLevel(rule);
		if (level <= 0)
		{
			return satisfies(rule, ReqFilter.WEAR);
		}
		return client.getRealSkillLevel(Skill.SMITHING) >= level;
	}

	private boolean meetsEquipmentSmithingForWear(ItemRule rule)
	{
		if (!isSmithingEquipment(rule))
		{
			return satisfies(rule, ReqFilter.WEAR);
		}

		switch (config.equipmentSmithingMode())
		{
			case RESTRICT:
				return satisfies(rule, ReqFilter.WEAR);
			case BALANCED:
			case MIXED:
				return meetsBalancedSmithing(rule);
			default:
				return satisfies(rule, ReqFilter.WEAR);
		}
	}

	private boolean meetsEquipmentSmithingForTrade(ItemRule rule)
	{
		if (!isSmithingEquipment(rule))
		{
			return satisfies(rule);
		}

		switch (config.equipmentSmithingMode())
		{
			case BALANCED:
				return meetsBalancedSmithing(rule);
			case RESTRICT:
			case MIXED:
				return satisfies(rule);
			default:
				return satisfies(rule);
		}
	}

	private List<Requirement> equipmentSmithingDisplayRequirements(ItemRule rule)
	{
		if (!isSmithingEquipment(rule))
		{
			return Collections.emptyList();
		}

		int level;
		switch (config.equipmentSmithingMode())
		{
			case RESTRICT:
				level = smithingTradeLevel(rule);
				break;
			case BALANCED:
			case MIXED:
				level = balancedSmithingLevel(rule);
				break;
			default:
				return Collections.emptyList();
		}

		return level > 0
			? Collections.singletonList(new Requirement(Skill.SMITHING, level))
			: Collections.emptyList();
	}

	private List<Requirement> equipmentSmithingTradeDisplayRequirements(ItemRule rule)
	{
		if (!isSmithingEquipment(rule))
		{
			return Collections.emptyList();
		}

		if (config.equipmentSmithingMode() == EquipmentSmithingMode.BALANCED)
		{
			return equipmentSmithingDisplayRequirements(rule);
		}

		int level = smithingTradeLevel(rule);
		return level > 0
			? Collections.singletonList(new Requirement(Skill.SMITHING, level))
			: Collections.emptyList();
	}

	/**
	 * Which half of an item's requirements a check cares about.
	 *
	 * <p>An amulet of glory carries Crafting 80 and Magic 68. Trading one needs both --
	 * you have to be able to make the whole thing. Wearing it needs the Crafting half.
	 * Firing its teleport needs the Magic half, and only that half: with the Crafting
	 * gate switched off, a glory you were allowed to put on must not then demand
	 * 80 Crafting before it will teleport you.
	 */
	private enum ReqFilter
	{
		/** Trade and shop: every requirement except USE and WIELD. */
		TRADE,
		/** Eat, drink, wear jewellery: fabrication reqs without ACTIVATE/USE/WIELD. */
		WEAR,
		/** Tool use: USE-scoped reqs only. */
		USE,
		/** Wield weapons and armour: WIELD-scoped reqs only. */
		WIELD,
		/** Charge spend: ACTIVATE-scoped reqs only. */
		ACTIVATE,
		/** Bury bones or ashes. */
		BURY
	}

	/** Trade needs every fabrication requirement: you must be able to make the whole item. */
	private boolean satisfies(ItemRule rule)
	{
		return satisfies(rule, ReqFilter.TRADE);
	}

	private List<Requirement> tradeRequirements(ItemRule rule)
	{
		return collectRequirements(rule, ReqFilter.TRADE);
	}

	private List<Requirement> useRequirements(ItemRule rule)
	{
		return collectRequirements(rule, ReqFilter.USE);
	}

	private List<Requirement> wieldRequirements(ItemRule rule)
	{
		return collectRequirements(rule, ReqFilter.WIELD);
	}

	private List<Requirement> buryRequirements(ItemRule rule)
	{
		return collectRequirements(rule, ReqFilter.BURY);
	}

	private List<Requirement> eatRequirements(ItemRule rule)
	{
		if (rule.getConsume() != ConsumeClass.FOOD)
		{
			return Collections.emptyList();
		}
		return tradeRequirements(rule);
	}

	private List<Requirement> drinkRequirements(ItemRule rule)
	{
		if (rule.getConsume() != ConsumeClass.POTION)
		{
			return Collections.emptyList();
		}
		return tradeRequirements(rule);
	}

	private List<Requirement> collectRequirements(ItemRule rule, ReqFilter filter)
	{
		List<Requirement> collected = new ArrayList<>();
		for (UnlockPath path : rule.getPaths())
		{
			if (path.getType() != PathType.SKILL)
			{
				continue;
			}
			for (Requirement req : path.getReqs())
			{
				if (matchesFilter(req, filter))
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

	private static boolean isObtainOnlyClass(com.leadman.rules.ItemClass itemClass)
	{
		return itemClass == com.leadman.rules.ItemClass.DROP_ONLY
			|| itemClass == com.leadman.rules.ItemClass.REWARD_ONLY
			|| itemClass == com.leadman.rules.ItemClass.SHOP_ONLY;
	}

	/**
	 * Unmapped GE items with no fabrication rule: equippable gear trades freely; everything
	 * else needs one genuine obtain before trade, shop or use opens up.
	 */
	private boolean unmappedGeAllowed(String key)
	{
		if (!tradeableIndex.isGeTradeableKey(key))
		{
			return true;
		}
		return state.getObtained().contains(key);
	}

	private static boolean matchesFilter(Requirement req, ReqFilter filter)
	{
		switch (filter)
		{
			case TRADE:
				return req.isTradeOnly() || req.isActivateOnly();
			case WEAR:
				return req.isTradeOnly();
			case USE:
				return req.isUseOnly();
			case WIELD:
				return req.isWieldOnly();
			case ACTIVATE:
				return req.isActivateOnly();
			case BURY:
				return req.isBuryOnly();
			default:
				return false;
		}
	}

	private boolean satisfies(ItemRule rule, ReqFilter filter)
	{
		List<UnlockPath> paths = rule.getPaths();
		if (paths.isEmpty())
		{
			// No mapped path: drop-only or free. Trade handles this via everObtained;
			// use is never gated on it.
			return rule.getItemClass() == com.leadman.rules.ItemClass.FREE;
		}

		for (UnlockPath path : paths)
		{
			if (path.getType() == PathType.ACTIVITY)
			{
				if (path.getActivity() != null && state.getActivities().contains(path.getActivity()))
				{
					return true;
				}
				continue;
			}
			if (meets(path.getReqs(), filter))
			{
				return true;
			}
		}
		return false;
	}

	private boolean meets(List<Requirement> reqs)
	{
		if (reqs.isEmpty())
		{
			return true;
		}
		for (Requirement req : reqs)
		{
			Skill skill = req.getSkill();
			if (skill == null)
			{
				return false;
			}
			if (client.getRealSkillLevel(skill) < req.getLevel())
			{
				return false;
			}
		}
		return true;
	}

	private boolean meets(List<Requirement> reqs, ReqFilter filter)
	{
		boolean matched = false;
		for (Requirement req : reqs)
		{
			if (!matchesFilter(req, filter))
			{
				continue;
			}
			matched = true;
			Skill skill = req.getSkill();
			if (skill == null)
			{
				return false;
			}
			if (client.getRealSkillLevel(skill) < req.getLevel())
			{
				return false;
			}
		}
		return matched;
	}

	public List<Requirement> displayTradeRequirements(String key)
	{
		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateTrade())
		{
			return custom.getTradeReqs();
		}
		ItemRule rule = rules.forName(key);
		if (rule == null)
		{
			return Collections.emptyList();
		}

		List<Requirement> smithing = equipmentSmithingTradeDisplayRequirements(rule);
		if (!smithing.isEmpty())
		{
			return smithing;
		}
		return tradeRequirements(rule);
	}

	public List<Requirement> displayShopRequirements(String key)
	{
		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateShop())
		{
			return custom.getShopReqs();
		}
		if (custom != null && custom.isGateTrade())
		{
			return custom.getTradeReqs();
		}
		return displayTradeRequirements(key);
	}

	public List<Requirement> displayEatRequirements(String key)
	{
		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateEat())
		{
			return custom.getEatReqs();
		}
		ItemRule rule = rules.forName(key);
		return rule == null || !config.gateFood() ? Collections.emptyList() : eatRequirements(rule);
	}

	public List<Requirement> displayDrinkRequirements(String key)
	{
		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateDrink())
		{
			return custom.getDrinkReqs();
		}
		ItemRule rule = rules.forName(key);
		return rule == null || !config.gatePotions() ? Collections.emptyList() : drinkRequirements(rule);
	}

	public List<Requirement> displayActivateRequirements(String key)
	{
		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateActivate())
		{
			return custom.getActivateReqs();
		}
		ItemRule rule = rules.forName(key);
		if (rule == null || !activateGateApplies(rule.getConsume()))
		{
			return Collections.emptyList();
		}
		return collectRequirements(rule, ReqFilter.ACTIVATE);
	}

	public List<Requirement> displayBuryRequirements(String key)
	{
		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateBury())
		{
			return custom.getBuryReqs();
		}
		ItemRule rule = rules.forName(key);
		return rule == null ? Collections.emptyList() : buryRequirements(rule);
	}

	public List<Requirement> displayUseRequirements(String key)
	{
		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateUse())
		{
			return custom.getUseReqs();
		}
		ItemRule rule = rules.forName(key);
		return rule == null ? Collections.emptyList() : useRequirements(rule);
	}

	public List<Requirement> displayWieldRequirements(String key)
	{
		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateWield())
		{
			return custom.getWieldReqs();
		}
		ItemRule rule = rules.forName(key);
		if (rule == null)
		{
			return Collections.emptyList();
		}

		List<Requirement> reqs = new ArrayList<>(wieldRequirements(rule));
		reqs.addAll(equipmentSmithingDisplayRequirements(rule));
		return reqs;
	}

	public boolean meetsTradeRequirements(String key)
	{
		return canTradeKey(key);
	}

	public boolean meetsShopRequirements(String key)
	{
		return canShopKey(key);
	}

	public boolean meetsUseRequirements(String key)
	{
		return canUseKey(key);
	}

	public boolean meetsEatRequirements(String key)
	{
		return canEatKey(key);
	}

	public boolean meetsDrinkRequirements(String key)
	{
		return canDrinkKey(key);
	}

	public boolean meetsWieldRequirements(String key)
	{
		return canWieldKey(key);
	}

	public boolean meetsActivateRequirements(String key)
	{
		return canActivateKey(key);
	}

	public boolean meetsBuryRequirements(String key)
	{
		return canBuryKey(key);
	}

	/** Why a charge cannot be spent -- the enchant level, not the whole recipe. */
	public String activateReason(String key)
	{
		ItemRule rule = rules.forName(key);
		if (rule == null)
		{
			return "unknown";
		}

		List<String> parts = new ArrayList<>();
		for (UnlockPath path : rule.getPaths())
		{
			for (Requirement req : path.getReqs())
			{
				if (req.isActivateOnly())
				{
					parts.add(req.toString());
				}
			}
		}
		return parts.isEmpty() ? rule.describeRequirements() : String.join(" + ", parts);
	}

	/** Why an item cannot be wielded, phrased for the chatbox. */
	public String wieldReason(String key)
	{
		List<Requirement> reqs = displayWieldRequirements(key);
		if (!reqs.isEmpty())
		{
			return reqs.stream().map(Requirement::toString).collect(java.util.stream.Collectors.joining(" + "));
		}
		return lockReason(key);
	}

	/** Why an item is locked, phrased for the chatbox. */
	public String lockReason(String key)
	{
		CustomRule custom = customRules.get(key);
		if (custom != null)
		{
			return custom.describe();
		}
		ItemRule rule = rules.forName(key);
		return rule == null ? "unknown" : rule.describeRequirements();
	}

	/** Shop block message -- uses shop override reqs when present. */
	public String shopReason(String key)
	{
		ItemRule rule = rules.forName(key);
		if (rule != null && rule.getPackOf() != null && !rule.getPackOf().isEmpty())
		{
			ItemRule component = rules.forName(rule.getPackOf());
			String name = component != null ? component.getDisplay() : rule.getPackOf();
			return "obtain " + name + " first";
		}

		List<Requirement> reqs = displayShopRequirements(key);
		if (!reqs.isEmpty())
		{
			return reqs.stream().map(Requirement::toString).collect(java.util.stream.Collectors.joining(" + "));
		}
		return lockReason(key);
	}

	// ------------------------------------------------------------- unlocking

	/**
	 * Records that the account has held this item. Only call this for items the player
	 * genuinely acquired -- shop purchases must not reach here, or shop stock would
	 * launder items onto the Grand Exchange.
	 *
	 * @return true if this was the first time
	 */
	public boolean markObtained(int itemId)
	{
		String key = keyFor(itemId);
		if (key.isEmpty())
		{
			return false;
		}
		if (state.getObtained().add(key))
		{
			recordRecentUnlock(key);
			dirty = true;
			return true;
		}
		return false;
	}

	public boolean hasObtained(String key)
	{
		return state.getObtained().contains(key);
	}

	public void markActivity(String activity)
	{
		if (state.getActivities().add(activity))
		{
			dirty = true;
		}
	}

	/** True when the item is anywhere in inventory, equipment or bank. */
	private boolean hasItemHeld(String key)
	{
		if (client.getGameState() != GameState.LOGGED_IN || key.isEmpty())
		{
			return false;
		}

		int[] containers = {
			InventoryID.INV,
			InventoryID.WORN,
			InventoryID.BANK,
		};
		for (int containerId : containers)
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
			{
				continue;
			}
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && key.equals(keyFor(item.getId())))
				{
					return true;
				}
			}
		}
		return false;
	}

	public boolean markSeen(String key)
	{
		if (state.getSeen().add(key))
		{
			dirty = true;
			return true;
		}
		return false;
	}

	/**
	 * Recomputes which rules are fabricable and returns the ones that just became so.
	 *
	 * @param silent true on the first scan of a profile, which records the baseline
	 *               without reporting it as a wave of unlocks
	 */
	public List<ItemRule> refreshSatisfied(boolean silent)
	{
		Set<String> now = new HashSet<>();
		List<ItemRule> newly = new ArrayList<>();

		for (Map.Entry<String, ItemRule> entry : rules.all().entrySet())
		{
			ItemRule rule = entry.getValue();
			if (!rule.hasSkillPath())
			{
				continue;
			}
			if (!satisfies(rule))
			{
				continue;
			}
			now.add(entry.getKey());
			if (!silent && !satisfied.contains(entry.getKey()))
			{
				newly.add(rule);
			}
		}

		satisfied = now;

		if (silent)
		{
			state.setSeeded(true);
			dirty = true;
			return Collections.emptyList();
		}
		return newly;
	}

	public boolean isSatisfiedKey(String key)
	{
		return satisfied.contains(key);
	}

	public RuleRepository getRules()
	{
		return rules;
	}
}
