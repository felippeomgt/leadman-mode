package com.leadman.ui;

import com.leadman.rules.ItemRule;
import com.leadman.rules.Requirement;
import com.leadman.rules.RuleRepository;
import com.leadman.unlock.CustomRule;
import com.leadman.unlock.UnlockService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

@Singleton
public class LeadmanPanel extends PluginPanel
{
	private static final int MAX_ROWS = 300;
	private static final int CARD_GAP = 2;

	private final UnlockService unlockService;
	private final RuleRepository rules;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final SkillIconManager skillIcons;

	private final IconTextField search = LeadmanUi.createSearchField();
	private final JPanel itemsList = new JPanel();
	private final JLabel summary = new JLabel();
	private final ItemRuleEditorDialog editor;

	private List<Row> itemRows = new ArrayList<>();
	private final Map<String, Integer> itemIdCache = new HashMap<>();

	@Inject
	public LeadmanPanel(
		UnlockService unlockService,
		RuleRepository rules,
		ClientThread clientThread,
		ItemManager itemManager,
		SkillIconManager skillIcons)
	{
		super(false);
		this.unlockService = unlockService;
		this.rules = rules;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.skillIcons = skillIcons;
		this.editor = new ItemRuleEditorDialog(null, skillIcons, realSkills());
	}

	public void init()
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		summary.setFont(FontManager.getRunescapeSmallFont());
		summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		summary.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		search.setToolTipText("Filter by item name");
		search.getDocument().addDocumentListener(filterListener());

		JPanel head = new JPanel(new BorderLayout());
		LeadmanUi.darkPanel(head);
		head.add(summary, BorderLayout.NORTH);
		head.add(search, BorderLayout.CENTER);

		itemsList.setLayout(new BoxLayout(itemsList, BoxLayout.Y_AXIS));
		LeadmanUi.darkPanel(itemsList);

		add(head, BorderLayout.NORTH);
		add(scroll(itemsList), BorderLayout.CENTER);

		LeadmanUi.styleSearchFieldInternals(search);
		refresh();
	}

	private static Skill[] realSkills()
	{
		return java.util.Arrays.stream(Skill.values())
			.filter(s -> !"OVERALL".equals(s.name()))
			.toArray(Skill[]::new);
	}

	private DocumentListener filterListener()
	{
		return new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				renderItems();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				renderItems();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				renderItems();
			}
		};
	}

	private JScrollPane scroll(JPanel content)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		LeadmanUi.darkPanel(wrapper);
		wrapper.add(content, BorderLayout.NORTH);

		JScrollPane pane = new JScrollPane(wrapper);
		pane.setBorder(BorderFactory.createEmptyBorder());
		pane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		pane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		pane.getVerticalScrollBar().setUnitIncrement(16);
		return pane;
	}

	public void refresh()
	{
		clientThread.invoke(() -> {
			if (!unlockService.isLoaded())
			{
				SwingUtilities.invokeLater(this::renderItems);
				return;
			}

			List<Row> rows = new ArrayList<>();
			for (ItemRule rule : rules.all().values())
			{
				String key = rule.getName();
				CustomRule custom = unlockService.getCustomRule(key);
				List<LeadmanUi.ActionLine> actions = RuleDisplayUtil.actionLines(
					unlockService, key, rule, custom);
				boolean fullyUnlocked = actions.stream().allMatch(a -> a.unlocked);
				rows.add(new Row(
					key,
					rule.getDisplay(),
					fullyUnlocked,
					custom != null,
					actions));
			}

			rows.sort(Comparator.comparing(r -> r.displayName));

			SwingUtilities.invokeLater(() -> {
				itemRows = rows;
				updateSummary();
				renderItems();
			});
		});
	}

	private void updateSummary()
	{
		if (!unlockService.isLoaded())
		{
			summary.setText("<html>Log in to see your items.<br>"
				+ "<span style='color:#808080'>Click \u270E to override an item's gates.</span></html>");
			return;
		}

		long unlocked = itemRows.stream().filter(r -> r.fullyUnlocked).count();
		long overrides = itemRows.stream().filter(r -> r.hasOverride).count();
		summary.setText("<html>" + unlocked + " fully unlocked / " + itemRows.size() + " items"
			+ (overrides > 0 ? " &middot; " + overrides + " customized" : "")
			+ "<br><span style='color:#808080'>Bright name = all actions open. Gray = something locked.</span></html>");
	}

	private void renderItems()
	{
		updateSummary();
		String filter = search.getText().trim().toLowerCase();
		itemsList.removeAll();

		if (!unlockService.isLoaded())
		{
			itemsList.add(LeadmanUi.hintLabel("Log in to see items here."));
		}
		else
		{
			int shown = 0;
			for (Row row : itemRows)
			{
				if (!filter.isEmpty() && !row.displayName.toLowerCase().contains(filter))
				{
					continue;
				}
				if (shown++ >= MAX_ROWS)
				{
					itemsList.add(LeadmanUi.hintLabel((itemRows.size() - MAX_ROWS) + " more -- narrow the search."));
					break;
				}
				itemsList.add(buildItemRow(row));
				itemsList.add(Box.createVerticalStrut(CARD_GAP));
			}

			if (shown == 0)
			{
				itemsList.add(LeadmanUi.hintLabel("Nothing here."));
			}
		}

		itemsList.revalidate();
		itemsList.repaint();
	}

	private JPanel buildItemRow(Row row)
	{
		JPanel card = LeadmanUi.itemCard(
			null,
			row.displayName,
			row.fullyUnlocked,
			LeadmanUi.actionLines(skillIcons, row.actions),
			() -> openEditor(row));

		attachItemIcon(card, row.ruleKey, row.displayName);
		return card;
	}

	private void openEditor(Row row)
	{
		ItemRule generated = rules.forName(row.ruleKey);
		CustomRule existing = unlockService.getCustomRule(row.ruleKey);
		java.awt.Window owner = SwingUtilities.getWindowAncestor(this);
		if (owner != null)
		{
			editor.setLocationRelativeTo(owner);
		}
		editor.open(row.displayName, generated, existing, unlockService, this::refresh);
	}

	private void attachItemIcon(JPanel card, String cacheKey, String displayName)
	{
		JLabel iconLabel = findIconLabel(card);
		if (iconLabel == null)
		{
			return;
		}

		Integer cached = itemIdCache.get(cacheKey);
		if (cached != null)
		{
			if (cached > 0)
			{
				itemManager.getImage(cached).addTo(iconLabel);
			}
			return;
		}

		clientThread.invoke(() -> {
			int itemId = -1;
			for (var price : itemManager.search(displayName))
			{
				if (displayName.equalsIgnoreCase(itemManager.getItemComposition(price.getId()).getName()))
				{
					itemId = price.getId();
					break;
				}
			}
			if (itemId <= 0 && !itemManager.search(displayName).isEmpty())
			{
				itemId = itemManager.search(displayName).get(0).getId();
			}
			itemIdCache.put(cacheKey, itemId);
			if (itemId > 0)
			{
				itemManager.getImage(itemId).addTo(iconLabel);
			}
		});
	}

	private static JLabel findIconLabel(JPanel card)
	{
		for (Component child : card.getComponents())
		{
			if (child instanceof JPanel)
			{
				for (Component westChild : ((JPanel) child).getComponents())
				{
					if (westChild instanceof JLabel)
					{
						return (JLabel) westChild;
					}
				}
			}
		}
		return null;
	}

	private static final class Row
	{
		private final String ruleKey;
		private final String displayName;
		private final boolean fullyUnlocked;
		private final boolean hasOverride;
		private final List<LeadmanUi.ActionLine> actions;

		private Row(
			String ruleKey,
			String displayName,
			boolean fullyUnlocked,
			boolean hasOverride,
			List<LeadmanUi.ActionLine> actions)
		{
			this.ruleKey = ruleKey;
			this.displayName = displayName;
			this.fullyUnlocked = fullyUnlocked;
			this.hasOverride = hasOverride;
			this.actions = actions;
		}
	}
}
