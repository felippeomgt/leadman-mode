package com.leadman.unlock;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.leadman.LeadmanConfig;
import com.leadman.rules.ConsumeClass;
import com.leadman.rules.ItemNames;
import com.leadman.rules.ItemRule;
import com.leadman.rules.PathType;
import com.leadman.rules.Requirement;
import com.leadman.rules.RuleRepository;
import com.leadman.rules.SpellRule;
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
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.client.RuneLite;
import net.runelite.client.game.ItemManager;

/**
 * The gate engine. Everything the plugin blocks or allows resolves through here.
 *
 * <p>Two independent gates, per docs/DESIGN.md section 1:
 * <pre>
 * tradeUnlocked = fabricable OR everObtained OR unmapped
 * useUnlocked   = fabricable OR no skill path
 * </pre>
 * {@code everObtained} appears in the first and not the second on purpose: looting an
 * item never grants the right to use it.
 */
@Slf4j
@Singleton
public class UnlockService
{
	private static final Type CUSTOM_RULES = new TypeToken<List<CustomRule>>()
	{
	}.getType();

	private static final String[] STARTER_ITEMS = {
		"bronze dagger", "bronze axe", "bronze pickaxe", "bronze sword", "bronze scimitar",
		"bronze med helm", "bronze full helm", "bronze platebody", "bronze platelegs",
		"bronze kiteshield", "bronze arrow", "shortbow", "iron dagger", "iron axe",
		"iron pickaxe", "iron scimitar", "iron arrow", "shrimps", "anchovies", "bread",
		"air rune", "mind rune", "water rune", "earth rune", "fire rune", "body rune",
		"staff of air", "staff of water", "staff of earth", "staff of fire"
	};

	private final Client client;
	private final ItemManager itemManager;
	private final RuleRepository rules;
	private final LeadmanConfig config;
	private final Gson gson;

	private UnlockState state = new UnlockState();
	private File stateFile;
	private boolean dirty;

	private Map<String, CustomRule> customRules = Collections.emptyMap();

	/** Rule keys currently fabricable. Diffed on level-up to find new unlocks. */
	private Set<String> satisfied = new HashSet<>();

	/** Items the active quest bypass currently permits, keyed by normalised name. */
	private Set<String> questBypass = new HashSet<>();

	@Inject
	public UnlockService(Client client, ItemManager itemManager, RuleRepository rules,
						 LeadmanConfig config, Gson gson)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.rules = rules;
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

		if (config.starterKit() && !state.isSeeded())
		{
			Collections.addAll(state.getObtained(), STARTER_ITEMS);
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

	public void setCustomRules(List<CustomRule> list)
	{
		config.setCustomRules(gson.toJson(list));
		reloadCustomRules();
	}

	// ------------------------------------------------------------------ lookup

	/** Canonical rule key for an item id, unnoting and collapsing variants first. */
	public String keyFor(int itemId)
	{
		int canonical = itemManager.canonicalize(itemId);
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
		return canTradeKey(keyFor(itemId));
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
			return meets(custom.getReqs());
		}

		if (state.getObtained().contains(key))
		{
			return true;
		}

		ItemRule rule = rules.forName(key);
		if (rule == null)
		{
			return true;
		}
		return satisfies(rule);
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

		if (config.questItemBypass() && questBypass.contains(key))
		{
			return true;
		}

		CustomRule custom = customRules.get(key);
		if (custom != null && custom.isGateUse())
		{
			return meets(custom.getReqs());
		}

		ItemRule rule = rules.forName(key);
		if (rule == null || !rule.hasSkillPath())
		{
			return true;
		}

		ConsumeClass consume = rule.getConsume();
		if (!wearGateApplies(consume))
		{
			return true;
		}

		return satisfies(rule, ReqFilter.WEAR);
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
		// You cannot spend a charge on something you are not allowed to hold or wear.
		if (!canUseKey(key))
		{
			return false;
		}

		if (key.isEmpty() || (config.questItemBypass() && questBypass.contains(key)))
		{
			return true;
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

		return satisfies(rule, ReqFilter.ACTIVATE);
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
		return consume == ConsumeClass.JEWELLERY || consume == ConsumeClass.CHARGED;
	}

	/**
	 * @return true when a cast is permitted. Blocked when any rune the spell consumes
	 * is itself locked -- gating at the cast rather than the item, so an elemental
	 * staff cannot supply a rune the player could not craft.
	 */
	public boolean canCast(String spellName)
	{
		if (!runeGateOn() || config.staffLaundering())
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
		switch (config.mode())
		{
			case BRONZEMAN_PLUS:
				return false;
			case STRICT:
				return true;
			default:
				return config.gateRunes();
		}
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

	/** Applies to eat, drink, wear and wield. */
	private boolean wearGateApplies(ConsumeClass consume)
	{
		if (consume == ConsumeClass.NONE)
		{
			return false;
		}

		switch (config.mode())
		{
			case BRONZEMAN_PLUS:
				return false;
			case STRICT:
				return true;
			case STANDARD:
			default:
				break;
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
				return config.gateEquipment();
			case TOOL:
				return config.gateTools();
			default:
				return false;
		}
	}

	/** Applies to rubbing, teleporting and anything else that spends a charge. */
	private boolean activateGateApplies(ConsumeClass consume)
	{
		switch (config.mode())
		{
			case BRONZEMAN_PLUS:
				return false;
			case STRICT:
				return true;
			default:
				break;
		}

		if (consume == ConsumeClass.JEWELLERY || consume == ConsumeClass.CHARGED)
		{
			return config.gateCharged();
		}
		return wearGateApplies(consume);
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
		ALL,
		WEAR,
		ACTIVATE
	}

	/** Trade needs every requirement: you must be able to make the whole item. */
	private boolean satisfies(ItemRule rule)
	{
		return satisfies(rule, ReqFilter.ALL);
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
		return meets(reqs, ReqFilter.ALL);
	}

	private boolean meets(List<Requirement> reqs, ReqFilter filter)
	{
		if (reqs.isEmpty())
		{
			return true;
		}
		for (Requirement req : reqs)
		{
			if (filter == ReqFilter.WEAR && req.isActivateOnly())
			{
				continue;
			}
			if (filter == ReqFilter.ACTIVATE && !req.isActivateOnly())
			{
				continue;
			}
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

	public boolean markSeen(String key)
	{
		if (state.getSeen().add(key))
		{
			dirty = true;
			return true;
		}
		return false;
	}

	public void setQuestBypass(Set<String> keys)
	{
		questBypass = keys == null ? new HashSet<>() : keys;
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
