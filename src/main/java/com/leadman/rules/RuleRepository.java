package com.leadman.rules;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the generated ruleset from resources and answers "what gates this item".
 *
 * <p>The bundled file is produced by {@code tools/generate-rules.mjs} and should not be
 * hand-edited -- put corrections in {@code overrides.json}, which is layered on top and
 * is never regenerated.
 */
@Slf4j
@Singleton
public class RuleRepository
{
	private static final String RULES = "/com/leadman/leadman-rules.json";
	private static final String OVERRIDES = "/com/leadman/overrides.json";

	private final Gson gson;

	private Map<String, ItemRule> byName = Collections.emptyMap();
	private Map<String, SpellRule> spellsByName = Collections.emptyMap();

	@Inject
	public RuleRepository(Gson gson)
	{
		this.gson = gson;
	}

	public void load()
	{
		RuleFile base = read(RULES);
		RuleFile overrides = read(OVERRIDES);

		Map<String, ItemRule> items = new HashMap<>();
		Map<String, SpellRule> spells = new HashMap<>();

		index(base, items, spells);
		// Overrides win outright: an overridden item replaces the generated rule
		// rather than merging with it, so a correction is always predictable.
		index(overrides, items, spells);

		byName = Collections.unmodifiableMap(items);
		spellsByName = Collections.unmodifiableMap(spells);

		log.info("Leadman: loaded {} item rules and {} spell rules", byName.size(), spellsByName.size());
	}

	private void index(RuleFile file, Map<String, ItemRule> items, Map<String, SpellRule> spells)
	{
		if (file == null)
		{
			return;
		}

		if (file.items != null)
		{
			for (ItemRule rule : file.items)
			{
				if (rule == null || rule.getName() == null)
				{
					continue;
				}
				String key = ItemNames.normalise(rule.getName());
				if (key.isEmpty())
				{
					continue;
				}
				rule.setName(key);
				items.put(key, rule);
			}
		}

		if (file.spells != null)
		{
			for (SpellRule spell : file.spells)
			{
				if (spell == null || spell.getName() == null)
				{
					continue;
				}
				spells.put(spell.getName().toLowerCase().trim(), spell);
			}
		}
	}

	private RuleFile read(String resource)
	{
		try (InputStream in = RuleRepository.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				log.debug("Leadman: no resource at {}", resource);
				return null;
			}
			return gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), RuleFile.class);
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.warn("Leadman: could not read {}", resource, e);
			return null;
		}
	}

	/**
	 * @param itemName raw in-game item name
	 * @return the rule, or null when the item has no mapped gate (treated as free)
	 */
	public ItemRule forName(String itemName)
	{
		return byName.get(ItemNames.normalise(itemName));
	}

	public SpellRule forSpell(String spellName)
	{
		if (spellName == null)
		{
			return null;
		}
		return spellsByName.get(spellName.toLowerCase().trim());
	}

	public Map<String, ItemRule> all()
	{
		return byName;
	}

	public int size()
	{
		return byName.size();
	}

	/** Wire format of the bundled JSON files. */
	private static final class RuleFile
	{
		private int version;
		private String generated;
		private List<ItemRule> items;
		private List<SpellRule> spells;
	}
}
