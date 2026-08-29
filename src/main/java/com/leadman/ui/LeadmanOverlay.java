package com.leadman.ui;

import com.leadman.LeadmanConfig;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Unlock popup drawn manually so the backdrop always matches the text width at any
 * client scale (OverlayPanel/SplitComponent sizing was consistently too narrow).
 */
@Singleton
public class LeadmanOverlay extends OverlayPanel
{
	private static final Color BACKDROP = new Color(24, 28, 34, 226);
	private static final Color TITLE = new Color(226, 174, 74);
	private static final Color BODY = new Color(219, 224, 232);
	private static final int H_PAD = 12;
	private static final int V_PAD = 8;
	private static final int ICON = 32;
	private static final int GAP = 10;
	private static final int MIN_TEXT_WIDTH = 160;

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
		setPriority(PRIORITY_HIGH);
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
	public Dimension render(Graphics2D graphics)
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

		Font titleFont = FontManager.getRunescapeBoldFont();
		Font bodyFont = FontManager.getRunescapeSmallFont();
		FontMetrics titleFm = graphics.getFontMetrics(titleFont);
		FontMetrics bodyFm = graphics.getFontMetrics(bodyFont);

		int textWidth = Math.max(MIN_TEXT_WIDTH, Math.max(
			titleFm.stringWidth(current.getTitle()),
			bodyFm.stringWidth(current.getSubtitle())));
		int textHeight = titleFm.getHeight() + 3 + bodyFm.getHeight();
		boolean hasIcon = current.getItemId() > 0;
		int iconBlock = hasIcon ? ICON + GAP : 0;
		int totalWidth = H_PAD * 2 + iconBlock + textWidth;
		int totalHeight = V_PAD * 2 + Math.max(hasIcon ? ICON : 0, textHeight);

		graphics.setColor(BACKDROP);
		graphics.fillRoundRect(0, 0, totalWidth, totalHeight, 6, 6);

		int textX = H_PAD + iconBlock;
		int textY = V_PAD;

		if (hasIcon)
		{
			BufferedImage image = itemManager.getImage(current.getItemId());
			int iconY = textY + (textHeight - ICON) / 2;
			graphics.drawImage(image, H_PAD, iconY, ICON, ICON, null);
		}

		graphics.setFont(titleFont);
		graphics.setColor(TITLE);
		graphics.drawString(current.getTitle(), textX, textY + titleFm.getAscent());

		graphics.setFont(bodyFont);
		graphics.setColor(BODY);
		graphics.drawString(
			current.getSubtitle(),
			textX,
			textY + titleFm.getHeight() + 3 + bodyFm.getAscent());

		return new Dimension(totalWidth, totalHeight);
	}
}
