package com.leadman.rules;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemStats;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

/**
 * Grand Exchange tradeables bundled from the wiki prices API. Unmapped items with no
 * fabrication rule fall back to equip detection: equippable items trade freely; everything
 * else unlocks on first obtain.
 */
@Slf4j
@Singleton
public class TradeableIndex
{
	private static final Type KEY_LIST = new TypeToken<List<String>>()
	{
	}.getType();
	private static final Type ID_MAP = new TypeToken<Map<String, Integer>>()
	{
	}.getType();

	private final ItemManager itemManager;
	private Set<String> geTradeableKeys = Collections.emptySet();
	private Map<String, Integer> geItemIds = Collections.emptyMap();
	private Map<Integer, String> geIdToKey = Collections.emptyMap();

	@Inject
	TradeableIndex(ItemManager itemManager, Gson gson)
	{
		this.itemManager = itemManager;
		loadBundled(gson);
		loadItemIds(gson);
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

	private void loadItemIds(Gson gson)
	{
		try (InputStream in = TradeableIndex.class.getResourceAsStream("/com/leadman/ge-item-ids.json"))
		{
			if (in == null)
			{
				log.warn("Leadman: ge-item-ids.json missing; equip detection falls back to obtain-only");
				return;
			}
			Map<String, Integer> loaded = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), ID_MAP);
			if (loaded != null)
			{
				geItemIds = new HashMap<>(loaded);
				Map<Integer, String> reverse = new HashMap<>();
				for (Map.Entry<String, Integer> entry : geItemIds.entrySet())
				{
					reverse.putIfAbsent(entry.getValue(), entry.getKey());
				}
				geIdToKey = Collections.unmodifiableMap(reverse);
			}
		}
		catch (Exception e)
		{
			log.warn("Leadman: could not read ge-item-ids.json", e);
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
		int canonical = itemManager.canonicalize(itemId);
		if (geIdToKey.containsKey(canonical))
		{
			return true;
		}
		ItemComposition comp = itemManager.getItemComposition(canonical);
		return comp != null && comp.isGeTradeable();
	}

	/**
	 * Canonical rules/GE key for a tradeable item id, from the bundled wiki mapping.
	 * Prefer this over normalising the client display name — in-game names can omit
	 * suffixes such as {@code (tablet)} that the ruleset uses.
	 */
	public String geKeyForItemId(int itemId)
	{
		if (itemId <= 0)
		{
			return null;
		}
		return geIdToKey.get(itemManager.canonicalize(itemId));
	}

	/**
	 * Whether an unmapped GE item may trade or shop without a fabrication rule. Equippable
	 * gear is always open; supplies and miscellany need one obtain first.
	 */
	public boolean isEquippableKey(String key)
	{
		if (key == null || key.isEmpty())
		{
			return false;
		}
		Integer id = geItemIds.get(key);
		if (id == null)
		{
			return false;
		}
		ItemStats stats = itemManager.getItemStats(id);
		return stats != null && stats.isEquipable();
	}
}
