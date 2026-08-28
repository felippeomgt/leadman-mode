package com.leadman.rules;

import java.util.regex.Pattern;

/**
 * Collapses the many in-game spellings of one conceptual item down to a single
 * lookup key, so that "Amulet of glory(6)", "Amulet of glory (t4)" and
 * "Amulet of glory" all resolve to the same rule.
 *
 * <p>Noted and placeholder ids are handled upstream by
 * {@code ItemManager#canonicalize}; this class only deals with the name text.
 */
public final class ItemNames
{
	/** Dose or charge counts: (1) (4) (6) (8) (10). */
	private static final Pattern CHARGES = Pattern.compile("\\(\\d+\\)$");

	/** Cosmetic, imbue, poison and trim suffixes. */
	private static final Pattern SUFFIXES = Pattern.compile(
		"\\((?:t\\d*|g|or|i|l|u|p\\+{0,2}|nz|cr|m|e|x|s|broken|inactive|free|deadman|last man standing)\\)$");

	/** Barrows-style degradation: "Ahrim's robetop 75". */
	private static final Pattern DEGRADE = Pattern.compile("\\s(?:0|25|50|75|100)$");

	private ItemNames()
	{
	}

	/**
	 * @param raw an item's in-game name
	 * @return lowercase canonical key, or an empty string for a null/blank name
	 */
	public static String normalise(String raw)
	{
		if (raw == null)
		{
			return "";
		}

		String s = raw.trim().toLowerCase();
		if (s.isEmpty() || "null".equals(s))
		{
			return "";
		}

		// Strip repeatedly: "Amulet of glory (t4)" can carry more than one marker.
		boolean changed = true;
		while (changed)
		{
			changed = false;

			String next = CHARGES.matcher(s).replaceFirst("");
			if (!next.equals(s))
			{
				s = next.trim();
				changed = true;
			}

			next = SUFFIXES.matcher(s).replaceFirst("");
			if (!next.equals(s))
			{
				s = next.trim();
				changed = true;
			}

			// Only apply the degrade rule to barrows-shaped names, so that a genuine
			// item ending in a number is never truncated.
			if (s.contains("'s "))
			{
				next = DEGRADE.matcher(s).replaceFirst("");
				if (!next.equals(s))
				{
					s = next.trim();
					changed = true;
				}
			}
		}

		return s;
	}

	/** Title-cases a normalised key for display when no display name was supplied. */
	public static String display(String normalised)
	{
		if (normalised == null || normalised.isEmpty())
		{
			return "";
		}
		return Character.toUpperCase(normalised.charAt(0)) + normalised.substring(1);
	}
}
