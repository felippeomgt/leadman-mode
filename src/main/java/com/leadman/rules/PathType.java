package com.leadman.rules;

/** What kind of thing satisfies an {@link UnlockPath}. */
public enum PathType
{
	/** Satisfied by skill levels. */
	SKILL,
	/** Satisfied by a completed quest, diary or activity flag. */
	ACTIVITY
}
