package com.leadman;

import com.google.gson.Gson;
import com.leadman.rules.RuleRepository;
import com.leadman.rules.TradeableIndex;
import com.leadman.unlock.CustomRule;
import com.leadman.unlock.UnlockService;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
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
	private TradeableIndex tradeables;
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
		tradeables = mock(TradeableIndex.class);
		when(tradeables.isGeTradeableKey(any())).thenAnswer(inv -> {
			String key = inv.getArgument(0);
			return "abyssal whip".equals(key) || "big bones".equals(key);
		});

		service = new UnlockService(client, null, rules, tradeables, config, gson);
		service.reloadCustomRules();
	}

	private void level(Skill skill, int value)
	{
		levels.put(skill, value);
	}

	// ------------------------------------------------------------ the six examples

	@Test
	public void runeScimitarMixedModeUsesBalancedSmithingToWield()
	{
		level(Skill.SMITHING, 89);
		level(Skill.ATTACK, 40);
		assertFalse("90 Smithing not reached, so it cannot be traded",
			service.canTradeKey("rune scimitar"));
		assertTrue("40 Attack and 89 Smithing satisfy mixed-mode wield",
			service.canWieldKey("rune scimitar"));

		level(Skill.SMITHING, 39);
		assertFalse("mixed mode also needs 40 Smithing to wield",
			service.canWieldKey("rune scimitar"));

		level(Skill.SMITHING, 90);
		assertTrue(service.canTradeKey("rune scimitar"));
	}

	@Test
	public void cookedSwordfishNeedsCookingToEat()
	{
		level(Skill.COOKING, 44);
		assertFalse(service.canEatKey("swordfish"));

		level(Skill.COOKING, 45);
		assertTrue(service.canEatKey("swordfish"));
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
	public void lootedPotionIsNotTradeableOrDrinkableWithoutHerblore()
	{
		level(Skill.HERBLORE, 40);
		service.getState().getObtained().add("saradomin brew");

		assertFalse("obtaining one does not bypass the fabrication gate",
			service.canTradeKey("saradomin brew"));
		assertFalse("obtaining never grants the right to drink it",
			service.canDrinkKey("saradomin brew"));

		level(Skill.HERBLORE, 81);
		assertTrue(service.canTradeKey("saradomin brew"));
		assertTrue(service.canDrinkKey("saradomin brew"));
	}

	@Test
	public void equippedPlateskirtDoesNotBypassSmithingTradeGate()
	{
		level(Skill.DEFENCE, 40);
		level(Skill.SMITHING, 50);
		service.getState().getObtained().add("rune plateskirt");

		assertFalse("99 Smithing is required to trade even when worn",
			service.canTradeKey("rune plateskirt"));
		assertTrue("40 Defence satisfies the wield requirement",
			service.canWieldKey("rune plateskirt"));

		level(Skill.SMITHING, 99);
		assertTrue(service.canTradeKey("rune plateskirt"));
	}

	@Test
	public void elementalStaffRequiresRunecraftingToWieldWhenRuneGateOn()
	{
		config.runes = true;
		level(Skill.RUNECRAFT, 1);
		assertFalse(service.canWieldKey("staff of air"));

		service.getState().getObtained().add("staff of air");
		assertTrue("obtain-one unlocks GE trade on a FREE staff", service.canTradeKey("staff of air"));
		assertFalse("but wield still needs Runecrafting 2", service.canWieldKey("staff of air"));

		level(Skill.RUNECRAFT, 2);
		assertTrue(service.canWieldKey("staff of air"));
	}

	@Test
	public void elementalStaffWieldIsFreeWhenRuneGateOff()
	{
		config.runes = false;
		level(Skill.RUNECRAFT, 1);
		assertTrue(service.canWieldKey("staff of air"));
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
	public void ammoAndRunesGateUseByDefault()
	{
		level(Skill.FLETCHING, 1);
		level(Skill.RUNECRAFT, 1);

		assertFalse(service.canTradeKey("rune arrow"));
		assertFalse(service.canTradeKey("law rune"));
		assertFalse("Fletching gate is on by default", service.canUseKey("rune arrow"));
		assertFalse("Runecraft gate is on by default for rune items", service.canUseKey("law rune"));

		level(Skill.FLETCHING, 75);
		assertTrue(service.canUseKey("rune arrow"));
	}

	@Test
	public void ammoAndRunesUseFreeWhenGatesOff()
	{
		config.ammo = false;
		config.runes = false;
		level(Skill.FLETCHING, 1);
		level(Skill.RUNECRAFT, 1);

		assertTrue(service.canUseKey("rune arrow"));
		assertTrue(service.canUseKey("law rune"));
	}

	@Test
	public void ammoUseGateCanBeTurnedOnIndependently()
	{
		level(Skill.FLETCHING, 1);
		level(Skill.RUNECRAFT, 1);

		config.runes = false;
		config.ammo = false;
		assertTrue(service.canUseKey("rune arrow"));

		config.ammo = true;
		assertFalse("Fletching gate now applies", service.canUseKey("rune arrow"));
		assertTrue("Runecrafting was left alone", service.canUseKey("law rune"));

		level(Skill.FLETCHING, 75);
		assertTrue(service.canUseKey("rune arrow"));
	}

	@Test
	public void plainJewelleryIsNotActivatableForMenuHooks()
	{
		assertFalse("no teleport charge on a gold necklace",
			service.isActivatable("gold necklace"));
		level(Skill.CRAFTING, 6);
		assertTrue(service.canUseKey("gold necklace"));
		assertTrue("activate check passes when there is nothing to activate",
			service.canActivateKey("gold necklace"));
	}

	@Test
	public void gloryRemainsActivatable()
	{
		assertTrue(service.isActivatable("amulet of glory"));
	}

	@Test
	public void brutalArrowsNeedFletchingForShop()
	{
		level(Skill.FLETCHING, 62);
		assertFalse(service.canShopKey("adamant brutal"));

		level(Skill.FLETCHING, 63);
		assertTrue(service.canShopKey("adamant brutal"));
	}

	@Test
	public void crossbowLimbsNeedSmithingForShop()
	{
		level(Skill.SMITHING, 75);
		assertFalse(service.canShopKey("adamantite limbs"));

		level(Skill.SMITHING, 76);
		assertTrue(service.canShopKey("adamantite limbs"));
	}

	@Test
	public void shopOnlyItemsAllowShopWithoutPriorObtain()
	{
		when(tradeables.isGeTradeableKey("ale yeast")).thenReturn(true);
		when(tradeables.isGeTradeableKey("fake beard")).thenReturn(true);

		assertTrue(service.canShopKey("ale yeast"));
		assertTrue(service.canShopKey("fake beard"));
	}

	@Test
	public void dyesNeedObtainBeforeShop()
	{
		when(tradeables.isGeTradeableKey("blue dye")).thenReturn(true);
		assertFalse(service.canShopKey("blue dye"));

		service.getState().getObtained().add("blue dye");
		assertTrue(service.canShopKey("blue dye"));
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

	@Test
	public void allUseGatesOffAllowsEatingAndWearingWithoutSkill()
	{
		config.food = false;
		config.potions = false;
		config.jewel = false;
		config.charged = false;
		level(Skill.COOKING, 1);
		level(Skill.CRAFTING, 1);

		assertTrue(service.canEatKey("swordfish"));
		assertTrue(service.canUseKey("amulet of glory"));
		assertFalse("trade is still gated", service.canTradeKey("swordfish"));
	}

	@Test
	public void restrictModeRequiresFabricationSmithingToWield()
	{
		config.equipmentSmithingMode = EquipmentSmithingMode.RESTRICT;
		level(Skill.ATTACK, 40);
		level(Skill.SMITHING, 85);
		assertFalse("rune scimitar needs 90 Smithing to use in restrict mode",
			service.canUseKey("rune scimitar"));
		assertFalse("wield also needs fabrication Smithing in restrict mode",
			service.canWieldKey("rune scimitar"));

		level(Skill.SMITHING, 90);
		assertTrue(service.canUseKey("rune scimitar"));
		assertTrue(service.canWieldKey("rune scimitar"));
	}

	@Test
	public void balancedModeUsesWieldStatForTradeAndWield()
	{
		config.equipmentSmithingMode = EquipmentSmithingMode.BALANCED;
		level(Skill.ATTACK, 40);
		level(Skill.SMITHING, 39);
		assertFalse(service.canTradeKey("rune scimitar"));
		assertFalse(service.canWieldKey("rune scimitar"));

		level(Skill.SMITHING, 40);
		assertTrue(service.canTradeKey("rune scimitar"));
		assertTrue(service.canWieldKey("rune scimitar"));
	}

	@Test
	public void mixedModeSplitsWieldAndTradeSmithing()
	{
		config.equipmentSmithingMode = EquipmentSmithingMode.MIXED;
		level(Skill.ATTACK, 40);
		level(Skill.SMITHING, 40);
		assertFalse("trade still needs fabrication Smithing 90",
			service.canTradeKey("rune scimitar"));
		assertTrue(service.canWieldKey("rune scimitar"));
		assertFalse(service.canShopKey("rune scimitar"));

		level(Skill.SMITHING, 90);
		assertTrue(service.canTradeKey("rune scimitar"));
		assertTrue(service.canShopKey("rune scimitar"));
	}

	@Test
	public void mithrilAxeSeparatesTradeUseAndWield()
	{
		level(Skill.SMITHING, 50);
		level(Skill.ATTACK, 20);
		level(Skill.WOODCUTTING, 21);

		assertFalse(service.canTradeKey("mithril axe"));
		assertTrue(service.canWieldKey("mithril axe"));
		assertTrue(service.canUseKey("mithril axe"));

		level(Skill.WOODCUTTING, 20);
		assertFalse("Woodcutting 21 is required to use the axe", service.canUseKey("mithril axe"));

		level(Skill.SMITHING, 51);
		assertTrue(service.canTradeKey("mithril axe"));
	}

	@Test
	public void shopRequiresFabricationWithoutObtainBypass()
	{
		level(Skill.SMITHING, 89);
		service.getState().getObtained().add("rune scimitar");
		assertFalse(service.canShopKey("rune scimitar"));
		assertFalse("obtain-one does not bypass fabrication for trade either", service.canTradeKey("rune scimitar"));

		level(Skill.SMITHING, 90);
		assertTrue(service.canShopKey("rune scimitar"));
		assertTrue(service.canTradeKey("rune scimitar"));
	}

	@Test
	public void chaosRuneShopRequiresRunecrafting35()
	{
		level(Skill.RUNECRAFT, 1);
		assertFalse(service.canShopKey("chaos rune"));
		assertFalse(service.canShopKey("chaos rune pack"));

		level(Skill.RUNECRAFT, 35);
		assertTrue(service.canShopKey("chaos rune"));
		assertTrue(service.canShopKey("chaos rune pack"));
	}

	@Test
	public void runeArmourSetShopRequiresPlatebodySmithing()
	{
		when(tradeables.isGeTradeableKey("rune armour set (lg)")).thenReturn(true);
		level(Skill.SMITHING, 98);
		assertFalse(service.canShopKey("rune armour set (lg)"));
		assertFalse(service.canTradeKey("rune armour set (lg)"));

		level(Skill.SMITHING, 99);
		assertTrue(service.canShopKey("rune armour set (lg)"));
		assertTrue(service.canTradeKey("rune armour set (lg)"));
	}

	@Test
	public void mithrilCannonballSplitsSmithingTradeAndSailingUse()
	{
		level(Skill.SMITHING, 54);
		level(Skill.SAILING, 54);
		assertFalse(service.canShopKey("mithril cannonball"));

		level(Skill.SMITHING, 55);
		assertTrue(service.canShopKey("mithril cannonball"));
		assertFalse(service.canUseKey("mithril cannonball"));

		level(Skill.SAILING, 55);
		assertTrue(service.canUseKey("mithril cannonball"));
	}

	@Test
	public void runePackShopRequiresSameRunecraftingAsRune()
	{
		level(Skill.RUNECRAFT, 1);
		assertTrue(service.canShopKey("air rune pack"));
		assertFalse(service.canShopKey("chaos rune pack"));

		level(Skill.RUNECRAFT, 35);
		assertTrue(service.canShopKey("chaos rune pack"));
	}

	@Test
	public void uncutSapphireShopRequiresCrafting20()
	{
		level(Skill.CRAFTING, 19);
		assertFalse(service.canShopKey("uncut sapphire"));
		assertFalse(service.canShopKey("sapphire"));

		level(Skill.CRAFTING, 20);
		assertTrue(service.canShopKey("uncut sapphire"));
		assertTrue(service.canShopKey("sapphire"));
	}

	@Test
	public void bulkShopPackRequiresObtainingSingleFirst()
	{
		assertFalse(service.canShopKey("eye of newt pack"));
		assertFalse(service.canShopKey("feather pack"));

		service.getState().getObtained().add("eye of newt");
		service.getState().getObtained().add("feather");
		assertTrue(service.canShopKey("eye of newt pack"));
		assertTrue(service.canShopKey("feather pack"));
	}

	@Test
	public void teleportTabletRequiresObtainBeforeGeTrade()
	{
		when(tradeables.isGeTradeableKey("varrock teleport (tablet)")).thenReturn(true);

		assertFalse(service.canTradeKey("varrock teleport (tablet)"));
		assertFalse(service.canShopKey("varrock teleport (tablet)"));

		service.getState().getObtained().add("varrock teleport (tablet)");
		assertTrue(service.canTradeKey("varrock teleport (tablet)"));
		assertTrue(service.canShopKey("varrock teleport (tablet)"));
	}

	@Test
	public void teleportTabletGeSearchUsesBundledKeyNotClientName()
	{
		Client client = mock(Client.class);
		ItemManager itemManager = mock(ItemManager.class);
		when(itemManager.canonicalize(8007)).thenReturn(8007);

		ItemComposition comp = mock(ItemComposition.class);
		when(comp.getName()).thenReturn("Varrock teleport");
		when(itemManager.getItemComposition(8007)).thenReturn(comp);

		when(tradeables.geKeyForItemId(8007)).thenReturn("varrock teleport (tablet)");
		when(tradeables.isGeTradeableKey("varrock teleport (tablet)")).thenReturn(true);

		UnlockService keyed = new UnlockService(client, itemManager, service.getRules(),
			tradeables, config, new Gson());
		keyed.reloadCustomRules();

		assertFalse(keyed.canTrade(8007));

		keyed.getState().getObtained().add("varrock teleport (tablet)");
		assertTrue(keyed.canTrade(8007));
	}

	@Test
	public void shopOnlyItemCanUseWhenHeldEvenBeforeObtainFlag()
	{
		when(tradeables.isGeTradeableKey("teleport card")).thenReturn(true);
		assertFalse(service.canUseKey("teleport card"));

		service.getState().getObtained().add("teleport card");
		assertTrue(service.canUseKey("teleport card"));
	}

	// ------------------------------------------------------------- unmapped & custom

	@Test
	public void unmappedGeItemsNeedObtainIncludingEquippable()
	{
		when(tradeables.isEquippableKey("abyssal whip")).thenReturn(true);
		when(tradeables.isGeTradeableKey("abyssal whip")).thenReturn(true);

		assertFalse(service.canTradeKey("abyssal whip"));
		service.getState().getObtained().add("abyssal whip");
		assertTrue(service.canTradeKey("abyssal whip"));
	}

	@Test
	public void unmappedNonEquippableGeItemsNeedObtain()
	{
		String key = "zzzz unmapped test item";
		when(tradeables.isEquippableKey(key)).thenReturn(false);
		when(tradeables.isGeTradeableKey(key)).thenReturn(true);

		assertFalse(service.canTradeKey(key));
		assertFalse(service.canShopKey(key));
		assertFalse(service.canUseKey(key));

		service.getState().getObtained().add(key);
		assertTrue(service.canTradeKey(key));
		assertTrue(service.canShopKey(key));
		assertTrue(service.canUseKey(key));
	}

	@Test
	public void customRuleCanGateBurySeparately()
	{
		level(Skill.PRAYER, 29);
		assertTrue(service.canBuryKey("big bones"));

		CustomRule rule = new CustomRule();
		rule.setItem("Big bones");
		rule.setGateBury(true);
		rule.setBuryReqs(Collections.singletonList(new com.leadman.rules.Requirement(Skill.PRAYER, 30)));
		service.setCustomRules(Collections.singletonList(rule));

		assertFalse(service.canBuryKey("big bones"));
		level(Skill.PRAYER, 30);
		assertTrue(service.canBuryKey("big bones"));
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
		private Boolean food;
		private Boolean potions;
		private Boolean jewel;
		private Boolean charged;
		private Boolean ammo;
		private Boolean runes;
		private EquipmentSmithingMode equipmentSmithingMode;
		private String custom = "[]";

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
		public EquipmentSmithingMode equipmentSmithingMode()
		{
			return equipmentSmithingMode != null
				? equipmentSmithingMode
				: LeadmanConfig.super.equipmentSmithingMode();
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
