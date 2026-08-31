package com.leadman;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(LeadmanConfig.GROUP)
public interface LeadmanConfig extends Config
{
	String GROUP = "leadman";

	@ConfigSection(
		name = "Use gates",
		description = "Which skills also gate using an item, not just trading it",
		position = 0
	)
	String gates = "gates";

	@ConfigSection(
		name = "Ironman conduct",
		description = "Restrictions a real Ironman gets from the server",
		position = 1
	)
	String conduct = "conduct";

	@ConfigSection(
		name = "Notifications",
		description = "Unlock popups and chat feedback",
		position = 2
	)
	String notifications = "notifications";

	// ------------------------------------------------------------------ gates

	@ConfigItem(
		keyName = "gateFood",
		name = "Cooking gates food",
		description = "You can only eat what you could cook. On by default: eating has no in-game requirement.",
		position = 0,
		section = gates
	)
	default boolean gateFood()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gatePotions",
		name = "Herblore gates potions",
		description = "You can only drink what you could brew. On by default: drinking has no in-game requirement.",
		position = 1,
		section = gates
	)
	default boolean gatePotions()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gateJewel",
		name = "Crafting gates wearing jewellery",
		description = "You can only wear jewellery you could craft. On by default: putting on an amulet has no in-game requirement.",
		position = 2,
		section = gates
	)
	default boolean gateJewel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gateCharged",
		name = "Magic gates charges and teleports",
		description = "Rubbing a glory or firing its teleport needs the enchant level, separately from the Crafting level that made it.",
		position = 3,
		section = gates
	)
	default boolean gateCharged()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gateAmmo",
		name = "Fletching gates ammunition",
		description = "On by default: you must be able to fletch ammo before using it. Off, normal Ranged requirements apply.",
		position = 4,
		section = gates
	)
	default boolean gateAmmo()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gateRunes",
		name = "Runecrafting gates runes",
		description = "On by default: you must be able to craft every rune a spell uses before casting. Off, spells only need their Magic level.",
		position = 5,
		section = gates
	)
	default boolean gateRunes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "equipmentSmithingMode",
		name = "Smithing gates equipment",
		description = "Restrict: fabrication Smithing for every action. Balanced: Smithing level matches Attack/Defence/Ranged wield reqs. Mixed (default): Balanced for wield/wear, fabrication Smithing for trade and shop.",
		position = 6,
		section = gates
	)
	default EquipmentSmithingMode equipmentSmithingMode()
	{
		return EquipmentSmithingMode.MIXED;
	}

	// ---------------------------------------------------------------- conduct

	@ConfigItem(
		keyName = "blockOtherPlayerDrops",
		name = "Block others' ground items",
		description = "Remove Take on items dropped by other players",
		position = 0,
		section = conduct
	)
	default boolean blockOtherPlayerDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "blockPlayerTrade",
		name = "Block player trading",
		description = "Remove Trade with, and warn on the trade interface",
		position = 1,
		section = conduct
	)
	default boolean blockPlayerTrade()
	{
		return true;
	}

	@ConfigItem(
		keyName = "allowedTradePartners",
		name = "Allow trade with",
		description = "Comma-separated player names that may still receive trades while player trading is blocked (e.g. your duo partner)",
		position = 2,
		section = conduct
	)
	default String allowedTradePartners()
	{
		return "";
	}

	@ConfigItem(
		keyName = "allowTradeWithParty",
		name = "Allow trade with party members",
		description = "Also allow trading with members of your RuneLite party. Join the same party via the Party plugin on both clients",
		position = 3,
		section = conduct
	)
	default boolean allowTradeWithParty()
	{
		return false;
	}

	@ConfigItem(
		keyName = "disableGe",
		name = "Disable Grand Exchange",
		description = "Block every GE action regardless of unlock progress",
		position = 4,
		section = conduct
	)
	default boolean disableGe()
	{
		return false;
	}

	// ---------------------------------------------------------- notifications

	@ConfigItem(
		keyName = "showPopup",
		name = "Unlock popup",
		description = "Show the on-screen unlock overlay",
		position = 0,
		section = notifications
	)
	default boolean showPopup()
	{
		return true;
	}

	@ConfigItem(
		keyName = "popupChat",
		name = "Unlock chat message",
		description = "Also print unlocks to the chatbox",
		position = 1,
		section = notifications
	)
	default boolean popupChat()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@ConfigItem(
		keyName = "popupBatchThreshold",
		name = "Batch above",
		description = "A level-up unlocking more items than this collapses into one popup",
		position = 2,
		section = notifications
	)
	default int popupBatchThreshold()
	{
		return 5;
	}

	@Range(min = 1000, max = 10000)
	@ConfigItem(
		keyName = "popupDuration",
		name = "Popup duration (ms)",
		description = "How long each unlock popup stays on screen",
		position = 3,
		section = notifications
	)
	default int popupDuration()
	{
		return 4000;
	}

	@ConfigItem(
		keyName = "explainBlocks",
		name = "Explain blocked actions",
		description = "Print why an action was blocked when you Examine an item or try to use, eat, cast, trade, etc.",
		position = 4,
		section = notifications
	)
	default boolean explainBlocks()
	{
		return true;
	}

	// ------------------------------------------------------------------ state

	@ConfigItem(
		keyName = "customRules",
		name = "",
		description = "",
		hidden = true
	)
	default String customRules()
	{
		return "[]";
	}

	@ConfigItem(
		keyName = "customRules",
		name = "",
		description = "",
		hidden = true
	)
	void setCustomRules(String json);
}
