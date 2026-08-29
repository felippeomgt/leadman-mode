package com.leadman.rules;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

/**
 * Set of normalised item names that appear on the Grand Exchange. Used to block
 * unmapped tradeables until the player obtains one.
 */
@Slf4j
@Singleton
public class TradeableIndex
{
	private static final Type KEY_LIST = new TypeToken<List<String>>()
	{
	}.getType();

	private final ItemManager itemManager;
	private Set<String> geTradeableKeys = Collections.emptySet();

	@Inject
	TradeableIndex(ItemManager itemManager, Gson gson)
	{
		this.itemManager = itemManager;
		loadBundled(gson);
	}

	private void loadBundled(Gson gson)
	{
		try (InputStream in = TradeableIndex.class.getResourceAsStream("/com/leadman/ge-tradeables.json"))
		{
			if (in == null)
			{
				log.warn("Leadman: ge-tradeables.json missing; unmapped GE items stay blocked by default");
				return;
			}
			List<String> loaded = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), KEY_LIST);
			if (loaded != null)
			{
				geTradeableKeys = new HashSet<>(loaded);
				log.info("Leadman: loaded {} GE tradeable names", geTradeableKeys.size());
			}
		}
		catch (Exception e)
		{
			log.warn("Leadman: could not read ge-tradeables.json", e);
		}
	}

	public boolean isGeTradeableKey(String key)
	{
		if (key == null || key.isEmpty())
		{
			return false;
		}
		return geTradeableKeys.contains(key);
	}

	public boolean isGeTradeableId(int itemId)
	{
		if (itemId <= 0)
		{
			return false;
		}
		ItemComposition comp = itemManager.getItemComposition(itemManager.canonicalize(itemId));
		return comp != null && comp.isGeTradeable();
	}
}
