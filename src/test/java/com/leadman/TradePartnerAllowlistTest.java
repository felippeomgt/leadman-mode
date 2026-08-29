package com.leadman;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import org.junit.Test;

public class TradePartnerAllowlistTest
{
	@Test
	public void parsesCommaSeparatedNamesCaseInsensitively()
	{
		assertTrue(TradePartnerAllowlist.isAllowed("WifeName", "wifeName, Other", false, null));
		assertTrue(TradePartnerAllowlist.isAllowed("Other", "wifeName, Other", false, null));
		assertFalse(TradePartnerAllowlist.isAllowed("Stranger", "wifeName, Other", false, null));
	}

	@Test
	public void stripsCombatLevelFromMenuTarget()
	{
		assertTrue(TradePartnerAllowlist.isAllowed(
			"WifeName (level-99)", "WifeName", false, null));
	}

	@Test
	public void allowsPartyMemberWhenToggleOn()
	{
		PartyService party = mock(PartyService.class);
		PartyMember member = mock(PartyMember.class);
		when(party.isInParty()).thenReturn(true);
		when(member.getDisplayName()).thenReturn("WifeName");
		when(party.getMembers()).thenReturn(Arrays.asList(member));

		assertTrue(TradePartnerAllowlist.isAllowed("WifeName", "", true, party));
		assertFalse(TradePartnerAllowlist.isAllowed("Stranger", "", true, party));
	}

	@Test
	public void partyToggleOffIgnoresPartyMembership()
	{
		PartyService party = mock(PartyService.class);
		PartyMember member = mock(PartyMember.class);
		when(party.isInParty()).thenReturn(true);
		when(member.getDisplayName()).thenReturn("WifeName");
		when(party.getMembers()).thenReturn(Arrays.asList(member));

		assertFalse(TradePartnerAllowlist.isAllowed("WifeName", "", false, party));
	}
}
