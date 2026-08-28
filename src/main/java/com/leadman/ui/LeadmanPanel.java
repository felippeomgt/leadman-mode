package com.leadman.ui;

import com.leadman.rules.ItemRule;
import com.leadman.rules.RuleRepository;
import com.leadman.unlock.CustomRule;
import com.leadman.unlock.UnlockService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;

/**
 * Sidebar: what is unlocked, what is not and why, and the custom rule editor.
 *
 * <p>Rule state is read on the client thread and rendered from an immutable snapshot,
 * because skill levels and item data are not safe to touch from Swing.
 */
@Singleton
public class LeadmanPanel extends PluginPanel
{
	private static final int MAX_ROWS = 300;

	private final UnlockService unlockService;
	private final RuleRepository rules;
	private final ClientThread clientThread;

	private final JTextField search = new JTextField();
	private final JPanel unlockedList = new JPanel();
	private final JPanel lockedList = new JPanel();
	private final JPanel customList = new JPanel();
	private final JLabel summary = new JLabel();

	private final JTextField customItem = new JTextField();
	private final JComboBox<Skill> customSkill = new JComboBox<>(realSkills());
	private final JSpinner customLevel = new JSpinner(new SpinnerNumberModel(30, 1, 99, 1));
	private final JCheckBox customGateUse = new JCheckBox("Use", true);
	private final JCheckBox customGateTrade = new JCheckBox("Trade", true);

	private List<Row> unlockedRows = new ArrayList<>();
	private List<Row> lockedRows = new ArrayList<>();
	private List<CustomRule> customRows = new ArrayList<>();

	/** Skill.values() can include a synthetic OVERALL entry, which is not a real gate. */
	private static Skill[] realSkills()
	{
		return java.util.Arrays.stream(Skill.values())
			.filter(s -> !"OVERALL".equals(s.name()))
			.toArray(Skill[]::new);
	}

	@Inject
	public LeadmanPanel(UnlockService unlockService, RuleRepository rules, ClientThread clientThread)
	{
		super(false);
		this.unlockService = unlockService;
		this.rules = rules;
		this.clientThread = clientThread;
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
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				render();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				render();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				render();
			}
		});

		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);
		head.add(summary, BorderLayout.NORTH);
		head.add(search, BorderLayout.CENTER);

		unlockedList.setLayout(new BoxLayout(unlockedList, BoxLayout.Y_AXIS));
		lockedList.setLayout(new BoxLayout(lockedList, BoxLayout.Y_AXIS));
		customList.setLayout(new BoxLayout(customList, BoxLayout.Y_AXIS));

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Unlocked", scroll(unlockedList));
		tabs.addTab("Locked", scroll(lockedList));
		tabs.addTab("Custom", buildCustomTab());

		add(head, BorderLayout.NORTH);
		add(tabs, BorderLayout.CENTER);

		refresh();
	}

	private JScrollPane scroll(JPanel content)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(content, BorderLayout.NORTH);

		JScrollPane pane = new JScrollPane(wrapper);
		pane.setBorder(BorderFactory.createEmptyBorder());
		pane.getVerticalScrollBar().setUnitIncrement(16);
		return pane;
	}

	private JPanel buildCustomTab()
	{
		JPanel tab = new JPanel(new BorderLayout());
		tab.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
		form.setBackground(ColorScheme.DARK_GRAY_COLOR);
		form.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));

		customItem.setToolTipText("Item name, e.g. Big bones");

		JPanel gates = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		gates.setBackground(ColorScheme.DARK_GRAY_COLOR);
		gates.add(new JLabel("Gate:"));
		gates.add(customGateUse);
		gates.add(customGateTrade);

		JPanel skillRow = new JPanel(new BorderLayout(4, 0));
		skillRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		skillRow.add(customSkill, BorderLayout.CENTER);
		skillRow.add(customLevel, BorderLayout.EAST);

		javax.swing.JButton add = new javax.swing.JButton("Add rule");
		add.addActionListener(e -> addCustomRule());

		form.add(new JLabel("Lock an item to a skill"));
		form.add(customItem);
		form.add(skillRow);
		form.add(gates);
		form.add(add);

		tab.add(form, BorderLayout.NORTH);
		tab.add(scroll(customList), BorderLayout.CENTER);
		return tab;
	}

	private void addCustomRule()
	{
		String name = customItem.getText().trim();
		if (name.isEmpty())
		{
			return;
		}

		CustomRule rule = new CustomRule(
			name,
			(Skill) customSkill.getSelectedItem(),
			(Integer) customLevel.getValue(),
			customGateTrade.isSelected(),
			customGateUse.isSelected());

		List<CustomRule> all = unlockService.getCustomRules();
		all.removeIf(r -> r.getKey().equals(rule.getKey()));
		all.add(rule);
		unlockService.setCustomRules(all);

		customItem.setText("");
		refresh();
	}

	private void removeCustomRule(CustomRule rule)
	{
		List<CustomRule> all = unlockService.getCustomRules();
		all.removeIf(r -> r.getKey().equals(rule.getKey()));
		unlockService.setCustomRules(all);
		refresh();
	}

	/** Safe to call from the client thread; rendering hops to Swing on its own. */
	public void refresh()
	{
		clientThread.invoke(() -> {
			if (!unlockService.isLoaded())
			{
				return;
			}

			List<Row> unlocked = new ArrayList<>();
			List<Row> locked = new ArrayList<>();

			for (ItemRule rule : rules.all().values())
			{
				boolean canTrade = unlockService.canTradeKey(rule.getName());
				boolean canUse = unlockService.canUseKey(rule.getName());

				Row row = new Row(rule.getDisplay(), rule.describeRequirements(), canTrade, canUse);
				if (canTrade && canUse)
				{
					unlocked.add(row);
				}
				else
				{
					locked.add(row);
				}
			}

			unlocked.sort(Comparator.comparing(r -> r.name));
			locked.sort(Comparator.comparing(r -> r.name));

			List<CustomRule> custom = unlockService.getCustomRules();

			SwingUtilities.invokeLater(() -> {
				unlockedRows = unlocked;
				lockedRows = locked;
				customRows = custom;
				render();
			});
		});
	}

	private void render()
	{
		String filter = search.getText().trim().toLowerCase();

		summary.setText(unlockedRows.size() + " unlocked / "
			+ (unlockedRows.size() + lockedRows.size()) + " mapped items");

		fill(unlockedList, unlockedRows, filter);
		fill(lockedList, lockedRows, filter);
		fillCustom(filter);

		revalidate();
		repaint();
	}

	private void fill(JPanel target, List<Row> rows, String filter)
	{
		target.removeAll();

		int shown = 0;
		for (Row row : rows)
		{
			if (!filter.isEmpty() && !row.name.toLowerCase().contains(filter))
			{
				continue;
			}
			if (shown++ >= MAX_ROWS)
			{
				target.add(hint((rows.size() - MAX_ROWS) + " more -- narrow the search"));
				break;
			}
			target.add(rowPanel(row));
		}

		if (shown == 0)
		{
			target.add(hint("Nothing here"));
		}

		target.revalidate();
		target.repaint();
	}

	private void fillCustom(String filter)
	{
		customList.removeAll();

		if (customRows.isEmpty())
		{
			customList.add(hint("No custom rules. Use the form above to lock an item that "
				+ "the game does not tie to a skill, such as bones to Prayer."));
		}

		for (CustomRule rule : customRows)
		{
			if (!filter.isEmpty() && !rule.getItem().toLowerCase().contains(filter))
			{
				continue;
			}

			JPanel panel = new JPanel(new BorderLayout(4, 0));
			panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			panel.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
			panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

			JPanel text = new JPanel(new GridLayout(0, 1));
			text.setBackground(ColorScheme.DARKER_GRAY_COLOR);

			JLabel name = new JLabel(rule.getItem());
			name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

			StringBuilder gates = new StringBuilder(rule.describe());
			gates.append("  [");
			gates.append(rule.isGateTrade() ? "trade" : "");
			gates.append(rule.isGateTrade() && rule.isGateUse() ? "+" : "");
			gates.append(rule.isGateUse() ? "use" : "");
			gates.append("]");

			JLabel detail = new JLabel(gates.toString());
			detail.setFont(FontManager.getRunescapeSmallFont());
			detail.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

			text.add(name);
			text.add(detail);

			javax.swing.JButton remove = new javax.swing.JButton("x");
			remove.setToolTipText("Remove this rule");
			remove.addActionListener(e -> removeCustomRule(rule));

			panel.add(text, BorderLayout.CENTER);
			panel.add(remove, BorderLayout.EAST);

			customList.add(panel);
		}

		customList.revalidate();
		customList.repaint();
	}

	private Component rowPanel(Row row)
	{
		JPanel panel = new JPanel(new GridLayout(0, 1));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

		JLabel name = new JLabel(row.name);
		name.setForeground(row.canUse ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR);

		String detail;
		if (row.canTrade && row.canUse)
		{
			detail = "unlocked";
		}
		else if (row.canTrade)
		{
			detail = "tradeable, cannot use -- needs " + row.requirement;
		}
		else if (row.canUse)
		{
			detail = "usable, not tradeable -- needs " + row.requirement;
		}
		else
		{
			detail = "needs " + row.requirement;
		}

		JLabel sub = new JLabel(detail);
		sub.setFont(FontManager.getRunescapeSmallFont());
		sub.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

		panel.add(name);
		panel.add(sub);
		return panel;
	}

	private Component hint(String text)
	{
		PluginErrorPanel error = new PluginErrorPanel();
		error.setContent("Leadman", text);
		return error;
	}

	/** Immutable snapshot of one item's gate state, safe to hand to Swing. */
	private static final class Row
	{
		private final String name;
		private final String requirement;
		private final boolean canTrade;
		private final boolean canUse;

		private Row(String name, String requirement, boolean canTrade, boolean canUse)
		{
			this.name = name;
			this.requirement = requirement;
			this.canTrade = canTrade;
			this.canUse = canUse;
		}
	}
}
