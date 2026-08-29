package com.leadman;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.Text;

/**
 * Exemptions to {@link LeadmanConfig#blockPlayerTrade()} — named partners or RuneLite party members.
 */
final class TradePartnerAllowlist
{
	private TradePartnerAllowlist()
	{
	}

	static String normalizePlayerName(String raw)
	{
		if (raw == null)
		{
			return "";
		}

		String name = Text.removeTags(raw).trim();
		int levelSuffix = name.indexOf(" (level-");
		if (levelSuffix > 0)
		{
			name = name.substring(0, levelSuffix).trim();
		}
		return name.toLowerCase(Locale.ENGLISH);
	}

	static Set<String> parseAllowlist(String csv)
	{
		if (csv == null || csv.trim().isEmpty())
		{
			return Collections.emptySet();
		}

		Set<String> names = new HashSet<>();
		for (String part : csv.split(","))
		{
			String name = normalizePlayerName(part);
			if (!name.isEmpty())
			{
				names.add(name);
			}
		}
		return names;
	}

	static boolean isAllowed(String menuTarget, String allowlistCsv, boolean allowPartyMembers, PartyService party)
	{
		String name = normalizePlayerName(menuTarget);
		if (name.isEmpty())
		{
			return false;
		}

		if (parseAllowlist(allowlistCsv).contains(name))
		{
			return true;
		}

		if (!allowPartyMembers || party == null || !party.isInParty())
		{
			return false;
		}

		for (PartyMember member : party.getMembers())
		{
			if (member == null)
			{
				continue;
			}
			if (name.equals(normalizePlayerName(member.getDisplayName())))
			{
				return true;
			}
		}
		return false;
	}
}
