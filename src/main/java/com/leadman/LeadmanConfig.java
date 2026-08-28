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
		name = "Exceptions",
		description = "Carve-outs that keep the mode playable",
		position = 1
	)
	String exceptions = "exceptions";

	@ConfigSection(
		name = "Ironman conduct",
		description = "Restrictions a real Ironman gets from the server",
		position = 2
	)
	String conduct = "conduct";

	@ConfigSection(
		name = "Notifications",
		description = "Unlock popups and chat feedback",
		position = 3
	)
	String notifications = "notifications";

	// ------------------------------------------------------------------ gates

	@ConfigItem(
		keyName = "mode",
		name = "Strictness",
		description = "Trade is always gated. This decides how far the use gate reaches.",
		position = 0,
		section = gates
	)
	default LeadmanMode mode()
	{
		return LeadmanMode.STANDARD;
	}

	// On by default: the game asks nothing of you before you eat, drink or put on an
	// amulet, so without a Leadman gate these items would have no requirement at all.

	@ConfigItem(
		keyName = "gateFood",
		name = "Cooking gates food",
		description = "You can only eat what you could cook. On by default: eating has no in-game requirement.",
		position = 1,
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
		position = 2,
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
		position = 3,
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
		position = 4,
		section = gates
	)
	default boolean gateCharged()
	{
		return true;
	}

	// Off by default: the game already gates these behind Ranged, Magic, Attack and
	// Defence levels, so a looted item you meet the requirements for is fair to use.
	// Turn one on to also require that you could have made it.

	@ConfigItem(
		keyName = "gateAmmo",
		name = "Fletching gates ammunition",
		description = "Off by default: arrows and bolts already have Ranged requirements. On, you must also be able to fletch them.",
		position = 5,
		section = gates
	)
	default boolean gateAmmo()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gateRunes",
		name = "Runecrafting gates runes",
		description = "Off by default: spells already have Magic requirements. On, you must also be able to craft every rune a spell uses.",
		position = 6,
		section = gates
	)
	default boolean gateRunes()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gateEquipment",
		name = "Smithing gates equipment",
		description = "Off by default: a rune scimitar is trade-locked until 90 Smithing but wieldable at 40 Attack.",
		position = 7,
		section = gates
	)
	default boolean gateEquipment()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gateTools",
		name = "Smithing gates tools",
		description = "Off by default. Turning this on can deadlock gathering skills: a rune axe needs 86 Smithing.",
		position = 8,
		section = gates
	)
	default boolean gateTools()
	{
		return false;
	}

	// ------------------------------------------------------------- exceptions

	@ConfigItem(
		keyName = "staffLaundering",
		name = "Staves bypass rune gates",
		description = "Only applies when Runecrafting gates runes. Off by default: an elemental staff should not launder a rune you could not craft.",
		position = 0,
		section = exceptions
	)
	default boolean staffLaundering()
	{
		return false;
	}

	@ConfigItem(
		keyName = "questItemBypass",
		name = "Quest item bypass",
		description = "Allow gated items through while a quest that needs them is incomplete",
		position = 1,
		section = exceptions
	)
	default boolean questItemBypass()
	{
		return true;
	}

	@ConfigItem(
		keyName = "starterKit",
		name = "Seed starter items",
		description = "Unlock bronze and iron gear, shrimp and basic runes on a new profile",
		position = 2,
		section = exceptions
	)
	default boolean starterKit()
	{
		return true;
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
		keyName = "blockGrandExchange",
		name = "Filter the Grand Exchange",
		description = "Hide locked items from search and block offers on them",
		position = 2,
		section = conduct
	)
	default boolean blockGrandExchange()
	{
		return true;
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
		description = "Print why an action was blocked. A silently dead menu entry reads as a bug.",
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
