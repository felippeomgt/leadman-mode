package com.leadman;

import com.google.gson.Gson;
import com.leadman.rules.RuleRepository;
import com.leadman.unlock.CustomRule;
import com.leadman.unlock.UnlockService;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the gate engine against the real generated ruleset, so these tests fail if
 * either the rules or the data stop matching docs/DESIGN.md.
 *
 * <p>Every example in the original brief is a test case here.
 */
public class GateRulesTest
{
	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);

	private TestConfig config;
	private UnlockService service;

	@Before
	public void setUp()
	{
		levels.clear();

		Client client = mock(Client.class);
		when(client.getRealSkillLevel(any(Skill.class)))
			.thenAnswer(call -> levels.getOrDefault(call.getArgument(0), 1));

		Gson gson = new Gson();
		RuleRepository rules = new RuleRepository(gson);
		rules.load();

		config = new TestConfig();
		// ItemManager is only touched by the id-based lookups; these tests address rules
		// by their canonical key, so it is never dereferenced.
		service = new UnlockService(client, null, rules, config, gson);
		service.reloadCustomRules();
	}

	private void level(Skill skill, int value)
	{
		levels.put(skill, value);
	}

	// ------------------------------------------------------------ the six examples

	@Test
	public void runeScimitarIsTradeGatedButFreelyWielded()
	{
		level(Skill.SMITHING, 89);
		assertFalse("90 Smithing not reached, so it cannot be traded",
			service.canTradeKey("rune scimitar"));
		assertTrue("equipment is not use-gated: 40 Attack already allows the wield",
			service.canUseKey("rune scimitar"));

		level(Skill.SMITHING, 90);
		assertTrue(service.canTradeKey("rune scimitar"));
	}

	@Test
	public void cookedSwordfishNeedsCookingToEat()
	{
		level(Skill.COOKING, 44);
		assertFalse(service.canUseKey("swordfish"));

		level(Skill.COOKING, 45);
		assertTrue(service.canUseKey("swordfish"));
	}

	@Test
	public void rawSwordfishIsTradeGatedOnFishingAndAlwaysUsable()
	{
		level(Skill.FISHING, 49);
		assertFalse(service.canTradeKey("raw swordfish"));
		assertTrue("raw fish carries no use gate", service.canUseKey("raw swordfish"));

		level(Skill.FISHING, 50);
		assertTrue(service.canTradeKey("raw swordfish"));
	}

	@Test
	public void lootedPotionIsTradeableButNotDrinkable()
	{
		level(Skill.HERBLORE, 40);
		service.getState().getObtained().add("saradomin brew");

		assertTrue("obtaining one unlocks the Grand Exchange",
			service.canTradeKey("saradomin brew"));
		assertFalse("but obtaining never grants the right to drink it",
			service.canUseKey("saradomin brew"));

		level(Skill.HERBLORE, 81);
		assertTrue(service.canUseKey("saradomin brew"));
	}

	@Test
	public void gloryNeedsCraftingToWearAndMagicToTeleport()
	{
		level(Skill.CRAFTING, 79);
		level(Skill.MAGIC, 99);
		assertFalse("79 Crafting cannot wear it", service.canUseKey("amulet of glory"));

		level(Skill.CRAFTING, 80);
		level(Skill.MAGIC, 67);
		assertTrue("80 Crafting wears it", service.canUseKey("amulet of glory"));
		assertFalse("but 68 Magic is what fires the teleport",
			service.canActivateKey("amulet of glory"));

		level(Skill.MAGIC, 68);
		assertTrue(service.canActivateKey("amulet of glory"));
	}

	@Test
	public void gloryTradeNeedsBothSkills()
	{
		level(Skill.CRAFTING, 80);
		level(Skill.MAGIC, 67);
		assertFalse("trading needs the whole recipe, charge included",
			service.canTradeKey("amulet of glory"));

		level(Skill.MAGIC, 68);
		assertTrue(service.canTradeKey("amulet of glory"));
	}

	// ----------------------------------------------- the revised rule for ammo/runes

	@Test
	public void ammoAndRunesAreTradeGatedOnlyByDefault()
	{
		level(Skill.FLETCHING, 1);
		level(Skill.RUNECRAFT, 1);

		assertFalse(service.canTradeKey("rune arrow"));
		assertFalse(service.canTradeKey("law rune"));

		assertTrue("the game already asks 40 Ranged for rune arrows",
			service.canUseKey("rune arrow"));
		assertTrue("the spellbook already asks for a Magic level",
			service.canUseKey("law rune"));
	}

	@Test
	public void ammoUseGateCanBeTurnedOnIndependently()
	{
		level(Skill.FLETCHING, 1);
		level(Skill.RUNECRAFT, 1);

		config.ammo = true;

		assertFalse("Fletching gate now applies", service.canUseKey("rune arrow"));
		assertTrue("but Runecrafting was left alone", service.canUseKey("law rune"));

		level(Skill.FLETCHING, 75);
		assertTrue(service.canUseKey("rune arrow"));
	}

	@Test
	public void jewelAndChargeGatesAreIndependent()
	{
		level(Skill.CRAFTING, 1);
		level(Skill.MAGIC, 68);

		config.jewel = false;
		assertTrue("wearing is free with the Crafting gate off",
			service.canUseKey("amulet of glory"));
		assertTrue("and 68 Magic satisfies the charge on its own",
			service.canActivateKey("amulet of glory"));

		level(Skill.MAGIC, 67);
		assertFalse("the Magic gate still holds", service.canActivateKey("amulet of glory"));
	}

	// ------------------------------------------------------------------- the modes

	@Test
	public void bronzemanPlusDropsEveryUseGate()
	{
		config.mode = LeadmanMode.BRONZEMAN_PLUS;
		level(Skill.COOKING, 1);
		level(Skill.CRAFTING, 1);

		assertTrue(service.canUseKey("swordfish"));
		assertTrue(service.canUseKey("amulet of glory"));
		assertFalse("trade is still gated", service.canTradeKey("swordfish"));
	}

	@Test
	public void strictModeGatesEquipmentAndTools()
	{
		config.mode = LeadmanMode.STRICT;

		level(Skill.SMITHING, 85);
		assertFalse("rune scimitar is 90 Smithing", service.canUseKey("rune scimitar"));
		assertFalse("rune axe is 86 Smithing", service.canUseKey("rune axe"));

		level(Skill.SMITHING, 86);
		assertTrue("the axe unlocks first", service.canUseKey("rune axe"));
		assertFalse(service.canUseKey("rune scimitar"));

		level(Skill.SMITHING, 90);
		assertTrue(service.canUseKey("rune scimitar"));
	}

	// ------------------------------------------------------------- unmapped & custom

	@Test
	public void unmappedItemsAreOpenOnBothGates()
	{
		assertTrue(service.canTradeKey("abyssal whip"));
		assertTrue(service.canUseKey("abyssal whip"));
		assertTrue(service.canTradeKey("big bones"));
		assertTrue(service.canUseKey("big bones"));
	}

	@Test
	public void customRuleLocksAnItemTheGameDoesNotGate()
	{
		service.setCustomRules(Collections.singletonList(
			new CustomRule("Big bones", Skill.PRAYER, 30, true, true)));

		level(Skill.PRAYER, 29);
		assertFalse(service.canUseKey("big bones"));
		assertFalse(service.canTradeKey("big bones"));

		level(Skill.PRAYER, 30);
		assertTrue(service.canUseKey("big bones"));
		assertTrue(service.canTradeKey("big bones"));
	}

	@Test
	public void obtainingDoesNotDefeatACustomRule()
	{
		service.setCustomRules(Collections.singletonList(
			new CustomRule("Big bones", Skill.PRAYER, 30, true, true)));
		service.getState().getObtained().add("big bones");

		level(Skill.PRAYER, 29);
		assertFalse("a custom rule is a deliberate hard lock", service.canTradeKey("big bones"));
	}

	// ------------------------------------------------------------------ ruleset data

	@Test
	public void generatedLaddersReproduceTheBriefsNumbers()
	{
		assertEquals("Smithing 90", requirementOf("rune scimitar"));
		assertEquals("Fishing 50", requirementOf("raw swordfish"));
		assertEquals("Cooking 45", requirementOf("swordfish"));
		assertEquals("Fletching 75", requirementOf("rune arrow"));
		assertEquals("Runecraft 54", requirementOf("law rune"));
		assertEquals("Crafting 80 + Magic 68", requirementOf("amulet of glory"));
	}

	private String requirementOf(String key)
	{
		return service.getRules().forName(key).describeRequirements();
	}

	/**
	 * Implements the config interface directly so the tests run against the real shipped
	 * defaults; a field left null falls through to whatever LeadmanConfig declares.
	 */
	private static final class TestConfig implements LeadmanConfig
	{
		private LeadmanMode mode = LeadmanMode.STANDARD;
		private Boolean food;
		private Boolean potions;
		private Boolean jewel;
		private Boolean charged;
		private Boolean ammo;
		private Boolean runes;
		private Boolean equipment;
		private Boolean tools;
		private String custom = "[]";

		@Override
		public LeadmanMode mode()
		{
			return mode;
		}

		@Override
		public boolean gateFood()
		{
			return food != null ? food : LeadmanConfig.super.gateFood();
		}

		@Override
		public boolean gatePotions()
		{
			return potions != null ? potions : LeadmanConfig.super.gatePotions();
		}

		@Override
		public boolean gateJewel()
		{
			return jewel != null ? jewel : LeadmanConfig.super.gateJewel();
		}

		@Override
		public boolean gateCharged()
		{
			return charged != null ? charged : LeadmanConfig.super.gateCharged();
		}

		@Override
		public boolean gateAmmo()
		{
			return ammo != null ? ammo : LeadmanConfig.super.gateAmmo();
		}

		@Override
		public boolean gateRunes()
		{
			return runes != null ? runes : LeadmanConfig.super.gateRunes();
		}

		@Override
		public boolean gateEquipment()
		{
			return equipment != null ? equipment : LeadmanConfig.super.gateEquipment();
		}

		@Override
		public boolean gateTools()
		{
			return tools != null ? tools : LeadmanConfig.super.gateTools();
		}

		@Override
		public String customRules()
		{
			return custom;
		}

		@Override
		public void setCustomRules(String json)
		{
			custom = json;
		}
	}
}
