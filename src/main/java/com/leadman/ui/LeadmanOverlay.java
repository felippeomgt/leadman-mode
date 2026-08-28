package com.leadman.ui;

import com.leadman.LeadmanConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The unlock popup, in the shape Bronzeman and the collection log use: item sprite on
 * the left, two lines of text on the right, one at a time from a queue.
 *
 * <p>Level-ups are batched upstream, so this never has to show fourteen popups in a row.
 */
@Singleton
public class LeadmanOverlay extends Overlay
{
	private static final int WIDTH = 232;
	private static final int HEIGHT = 46;
	private static final int ICON = 32;
	private static final int PAD = 7;

	private static final Color BACKDROP = new Color(24, 28, 34, 226);
	private static final Color EDGE = new Color(155, 116, 43);
	private static final Color TITLE = new Color(226, 174, 74);
	private static final Color BODY = new Color(219, 224, 232);

	private final ItemManager itemManager;
	private final LeadmanConfig config;

	private final Queue<UnlockNotification> queue = new ConcurrentLinkedQueue<>();

	@Inject
	public LeadmanOverlay(ItemManager itemManager, LeadmanConfig config)
	{
		this.itemManager = itemManager;
		this.config = config;

		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(Overlay.PRIORITY_HIGH);
	}

	public void push(UnlockNotification notification)
	{
		queue.offer(notification);
	}

	public void clear()
	{
		queue.clear();
	}

	public int pending()
	{
		return queue.size();
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showPopup())
		{
			queue.clear();
			return null;
		}

		UnlockNotification current = queue.peek();
		if (current == null)
		{
			return null;
		}

		current.startClock(config.popupDuration());
		if (current.isExpired())
		{
			queue.poll();
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		g.setColor(BACKDROP);
		g.fillRect(0, 0, WIDTH, HEIGHT);

		g.setStroke(new BasicStroke(1f));
		g.setColor(EDGE);
		g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);

		if (current.getItemId() > 0)
		{
			Image sprite = itemManager.getImage(current.getItemId());
			if (sprite != null)
			{
				int y = (HEIGHT - ICON) / 2;
				g.drawImage(sprite, PAD, y, null);
			}
		}

		int textX = PAD + ICON + PAD;

		Font base = g.getFont();
		g.setFont(base.deriveFont(Font.BOLD, 12f));
		g.setColor(TITLE);
		g.drawString(fit(g, current.getTitle(), WIDTH - textX - PAD), textX, 20);

		g.setFont(base.deriveFont(Font.PLAIN, 12f));
		g.setColor(BODY);
		g.drawString(fit(g, current.getSubtitle(), WIDTH - textX - PAD), textX, 35);

		g.setFont(base);

		return new Dimension(WIDTH, HEIGHT);
	}

	private static String fit(Graphics2D g, String text, int maxWidth)
	{
		if (text == null)
		{
			return "";
		}
		if (g.getFontMetrics().stringWidth(text) <= maxWidth)
		{
			return text;
		}
		String ellipsis = "...";
		int limit = maxWidth - g.getFontMetrics().stringWidth(ellipsis);
		StringBuilder sb = new StringBuilder();
		for (char c : text.toCharArray())
		{
			if (g.getFontMetrics().stringWidth(sb.toString() + c) > limit)
			{
				break;
			}
			sb.append(c);
		}
		return sb + ellipsis;
	}
}
