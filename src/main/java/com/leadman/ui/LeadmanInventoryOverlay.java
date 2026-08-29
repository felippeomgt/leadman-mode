package com.leadman.ui;

import com.leadman.rules.ItemRule;
import com.leadman.rules.RuleRepository;
import com.leadman.unlock.CustomRule;
import com.leadman.unlock.UnlockService;
import java.awt.Color;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Dims inventory items that still have a Leadman gate locked. */
@Singleton
public class LeadmanInventoryOverlay extends WidgetItemOverlay
{
	private final UnlockService unlockService;
	private final RuleRepository rules;

	@Inject
	LeadmanInventoryOverlay(UnlockService unlockService, RuleRepository rules)
	{
		this.unlockService = unlockService;
		this.rules = rules;
		showOnInventory();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int slot, WidgetItem widgetItem)
	{
		if (!unlockService.isLoaded() || widgetItem == null)
		{
			return;
		}

		String key = unlockService.keyFor(widgetItem.getId());
		if (key.isEmpty())
		{
			return;
		}

		ItemRule rule = rules.forName(key);
		CustomRule custom = unlockService.getCustomRule(key);
		if (!RuleDisplayUtil.hasLockedActions(unlockService, key, rule, custom))
		{
			return;
		}

		java.awt.Rectangle bounds = widgetItem.getCanvasBounds();
		int w = bounds != null ? bounds.width : 32;
		int h = bounds != null ? bounds.height : 32;
		graphics.setColor(new Color(0, 0, 0, 110));
		graphics.fillRect(0, 0, w, h);
	}
}
