package com.leadman.ui;

/** One entry in the unlock popup queue. */
public class UnlockNotification
{
	private final String title;
	private final String subtitle;
	private final int itemId;

	private long expiresAt;

	public UnlockNotification(String title, String subtitle, int itemId)
	{
		this.title = title;
		this.subtitle = subtitle;
		this.itemId = itemId;
	}

	public String getTitle()
	{
		return title;
	}

	public String getSubtitle()
	{
		return subtitle;
	}

	public int getItemId()
	{
		return itemId;
	}

	public long getExpiresAt()
	{
		return expiresAt;
	}

	public void startClock(int durationMs)
	{
		if (expiresAt == 0)
		{
			expiresAt = System.currentTimeMillis() + durationMs;
		}
	}

	public boolean isExpired()
	{
		return expiresAt != 0 && System.currentTimeMillis() > expiresAt;
	}
}
