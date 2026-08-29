package com.leadman.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.Collections;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.SwingUtil;

/** Shared RuneLite-style panel chrome for Leadman Mode. */
final class LeadmanUi
{
	private static final int ITEM_ICON = 32;
	private static final int SKILL_ICON = 14;

	private LeadmanUi()
	{
	}

	static void darkPanel(JPanel panel)
	{
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setOpaque(true);
	}

	static IconTextField createSearchField()
	{
		IconTextField field = new IconTextField();
		field.setIcon(IconTextField.Icon.SEARCH);
		field.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 16, 30));
		field.setMinimumSize(new Dimension(0, 30));
		styleIconTextField(field);
		javax.swing.SwingUtilities.invokeLater(() -> styleSearchFieldInternals(field));
		field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e)
			{
				styleSearchFieldInternals(field);
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e)
			{
				styleSearchFieldInternals(field);
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e)
			{
				styleSearchFieldInternals(field);
			}
		});
		return field;
	}

	static void styleSearchFieldInternals(IconTextField field)
	{
		FlatTextField flat = findFlatTextField(field);
		if (flat != null)
		{
			javax.swing.JTextField text = flat.getTextField();
			text.setForeground(Color.WHITE);
			text.setCaretColor(Color.WHITE);
			text.setOpaque(false);
			text.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		}

		JButton clear = findClearButton(field);
		if (clear != null)
		{
			styleClearButton(clear);
		}
	}

	private static FlatTextField findFlatTextField(Container root)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof FlatTextField)
			{
				return (FlatTextField) child;
			}
			if (child instanceof Container)
			{
				FlatTextField nested = findFlatTextField((Container) child);
				if (nested != null)
				{
					return nested;
				}
			}
		}
		return null;
	}

	private static JButton findClearButton(Container root)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JButton)
			{
				JButton button = (JButton) child;
				if ("\u00D7".equals(button.getText()))
				{
					return button;
				}
			}
			if (child instanceof Container)
			{
				JButton nested = findClearButton((Container) child);
				if (nested != null)
				{
					return nested;
				}
			}
		}
		return null;
	}

	private static void styleClearButton(JButton clear)
	{
		SwingUtil.removeButtonDecorations(clear);
		clear.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clear.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		clear.setFocusPainted(false);
		clear.setBorderPainted(false);
		clear.setContentAreaFilled(true);
		clear.setOpaque(true);
	}

	static FlatTextField createLevelField(int defaultLevel)
	{
		FlatTextField field = new FlatTextField();
		field.setPreferredSize(new Dimension(48, 32));
		field.setMinimumSize(new Dimension(48, 32));
		styleFlatField(field);
		field.setText(String.valueOf(defaultLevel));
		return field;
	}

	static void styleIconTextField(IconTextField field)
	{
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setHoverBackgroundColor(ColorScheme.DARKER_GRAY_HOVER_COLOR);
	}

	static void styleFlatField(FlatTextField field)
	{
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setHoverBackgroundColor(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		field.getTextField().setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		field.getTextField().setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
		field.getTextField().setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
	}

	static JButton createSkillPicker(SkillIconManager skillIcons, Skill[] skills, Skill selected, Runnable onChange)
	{
		JButton button = new JButton(formatSkill(skillIcons, selected));
		button.setHorizontalAlignment(JButton.LEFT);
		stylePickerButton(button);
		button.addActionListener(e -> {
			javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
			menu.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			for (Skill skill : skills)
			{
				javax.swing.JMenuItem item = new javax.swing.JMenuItem(formatSkill(skillIcons, skill));
				item.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				item.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				item.addActionListener(ev -> {
					button.setText(formatSkill(skillIcons, skill));
					button.putClientProperty("skill", skill);
					onChange.run();
					menu.setVisible(false);
				});
				menu.add(item);
			}
			menu.show(button, 0, button.getHeight());
		});
		button.putClientProperty("skill", selected);
		return button;
	}

	static Skill skillFromPicker(JButton button)
	{
		Object skill = button.getClientProperty("skill");
		return skill instanceof Skill ? (Skill) skill : Skill.ATTACK;
	}

	static void setSkillPicker(JButton button, SkillIconManager skillIcons, Skill skill)
	{
		button.setText(formatSkill(skillIcons, skill));
		button.putClientProperty("skill", skill);
	}

	private static String formatSkill(SkillIconManager skillIcons, Skill skill)
	{
		return "  " + prettySkill(skill);
	}

	static void stylePickerButton(JButton button)
	{
		SwingUtil.removeButtonDecorations(button);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setFocusPainted(false);
		button.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 70, 32));
		addButtonHover(button, ColorScheme.DARKER_GRAY_HOVER_COLOR, Color.WHITE);
	}

	static void styleCheckBox(JCheckBox check)
	{
		check.setBackground(ColorScheme.DARK_GRAY_COLOR);
		check.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		check.setOpaque(false);
	}

	static void stylePrimaryButton(JButton button)
	{
		SwingUtil.removeButtonDecorations(button);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setFocusPainted(false);
		addButtonHover(button, ColorScheme.DARKER_GRAY_HOVER_COLOR, Color.WHITE);
	}

	static void styleSecondaryButton(JButton button)
	{
		SwingUtil.removeButtonDecorations(button);
		button.setBackground(ColorScheme.DARK_GRAY_COLOR);
		button.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		button.setFocusPainted(false);
		addButtonHover(button, ColorScheme.DARKER_GRAY_COLOR, ColorScheme.LIGHT_GRAY_COLOR);
	}

	static JLabel sectionLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	static JLabel hintLabel(String text)
	{
		JLabel label = new JLabel("<html><div style='width:220px;color:#a5a5a5;'>" + text + "</div></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBackground(ColorScheme.DARK_GRAY_COLOR);
		label.setOpaque(true);
		label.setBorder(new EmptyBorder(8, 4, 8, 4));
		return label;
	}

	static JLabel linkLabel(String text, Runnable action)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setForeground(Color.WHITE);
				Font font = label.getFont();
				label.setFont(font.deriveFont(Font.PLAIN, font.getSize2D()).deriveFont(
					Collections.singletonMap(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				label.setFont(FontManager.getRunescapeSmallFont());
			}
		});
		return label;
	}

	static JPanel itemCard(
		BufferedImage itemImage,
		String name,
		boolean fullyUnlocked,
		JPanel details,
		Runnable onClick)
	{
		JPanel card = new JPanel(new BorderLayout(6, 0));
		applyCardStyle(card);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(ITEM_ICON, ITEM_ICON));
		icon.setHorizontalAlignment(JLabel.CENTER);
		icon.setOpaque(false);
		if (itemImage != null)
		{
			icon.setIcon(new ImageIcon(itemImage));
		}

		int titleWidth = PluginPanel.PANEL_WIDTH - 56;
		JLabel title = new JLabel("<html><div style='width:" + titleWidth + "px'>"
			+ escapeHtml(name) + "</div></html>");
		title.setForeground(fullyUnlocked ? Color.WHITE : ColorScheme.MEDIUM_GRAY_COLOR);

		JPanel center = new JPanel(new BorderLayout());
		center.setOpaque(false);
		center.add(title, BorderLayout.NORTH);
		if (details != null)
		{
			center.add(details, BorderLayout.CENTER);
		}

		JPanel west = new JPanel(new BorderLayout());
		west.setOpaque(false);
		west.add(icon, BorderLayout.NORTH);

		card.add(west, BorderLayout.WEST);
		card.add(center, BorderLayout.CENTER);

		if (onClick != null)
		{
			makeClickable(card, onClick);
		}
		return card;
	}

	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static final String CARD_TOOLTIP = "Click to edit restrictions";

	private static void makeClickable(java.awt.Container root, Runnable action)
	{
		java.awt.Cursor hand = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR);
		Color normal = ColorScheme.DARKER_GRAY_COLOR;
		Color hover = ColorScheme.DARKER_GRAY_HOVER_COLOR;
		java.awt.event.MouseAdapter adapter = new MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				action.run();
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				setCardBackground(root, hover);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				setCardBackground(root, normal);
			}
		};
		wireClickable(root, hand, adapter, CARD_TOOLTIP);
	}

	private static void setCardBackground(java.awt.Component component, Color color)
	{
		if (component instanceof JPanel)
		{
			JPanel panel = (JPanel) component;
			if (panel.isOpaque())
			{
				panel.setBackground(color);
			}
		}
		if (component instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) component).getComponents())
			{
				setCardBackground(child, color);
			}
		}
	}

	private static void wireClickable(
		java.awt.Component component,
		java.awt.Cursor hand,
		java.awt.event.MouseAdapter adapter,
		String tooltip)
	{
		component.setCursor(hand);
		if (component instanceof javax.swing.JComponent)
		{
			((javax.swing.JComponent) component).setToolTipText(tooltip);
		}
		component.addMouseListener(adapter);
		if (component instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) component).getComponents())
			{
				wireClickable(child, hand, adapter, tooltip);
			}
		}
	}

	static JPanel actionLines(SkillIconManager skillIcons, java.util.List<ActionLine> actions)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(2, 0, 0, 0));
		for (ActionLine action : actions)
		{
			panel.add(actionLine(skillIcons, action));
		}
		return panel;
	}

	private static JPanel actionLine(SkillIconManager skillIcons, ActionLine action)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 24, 18));

		JLabel prefix = new JLabel(action.label);
		prefix.setFont(FontManager.getRunescapeSmallFont());
		prefix.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		row.add(prefix);

		if (action.levels.isEmpty())
		{
			JLabel open = new JLabel(action.unlocked ? "yes" : "obtain one");
			open.setFont(FontManager.getRunescapeSmallFont());
			open.setForeground(action.unlocked
				? ColorScheme.PROGRESS_COMPLETE_COLOR
				: ColorScheme.BRAND_ORANGE);
			row.add(open);
			return row;
		}

		for (SkillLevel req : action.levels)
		{
			row.add(scaledSkillIcon(skillIcons, req.skill));
			JLabel level = new JLabel(String.valueOf(req.level));
			level.setFont(FontManager.getRunescapeSmallFont());
			level.setForeground(action.unlocked
				? ColorScheme.PROGRESS_COMPLETE_COLOR
				: ColorScheme.BRAND_ORANGE);
			row.add(level);
		}
		return row;
	}

	private static JLabel scaledSkillIcon(SkillIconManager skillIcons, Skill skill)
	{
		java.awt.image.BufferedImage image = skillIcons.getSkillImage(skill);
		Image scaled = image.getScaledInstance(SKILL_ICON, SKILL_ICON, Image.SCALE_SMOOTH);
		JLabel icon = new JLabel(new ImageIcon(scaled));
		icon.setPreferredSize(new Dimension(SKILL_ICON, SKILL_ICON));
		icon.setMinimumSize(new Dimension(SKILL_ICON, SKILL_ICON));
		icon.setMaximumSize(new Dimension(SKILL_ICON, SKILL_ICON));
		return icon;
	}

	static void applyCardStyle(JPanel panel)
	{
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(4, 4, 4, 4));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
		panel.setOpaque(true);
	}

	static JButton editIconButton(Runnable action)
	{
		JButton button = new JButton("\u270E");
		button.setToolTipText("Edit requirements");
		SwingUtil.removeButtonDecorations(button);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		button.setFocusPainted(false);
		button.addActionListener(e -> action.run());
		addButtonHover(button, ColorScheme.DARKER_GRAY_HOVER_COLOR, Color.WHITE);
		return button;
	}

	static String prettySkill(Skill skill)
	{
		String raw = skill.name();
		return raw.charAt(0) + raw.substring(1).toLowerCase();
	}

	private static void addButtonHover(JButton button, Color bgHover, Color fgHover)
	{
		final Color bg = button.getBackground();
		final Color fg = button.getForeground();
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setBackground(bgHover);
				button.setForeground(fgHover);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(bg);
				button.setForeground(fg);
			}
		});
	}

	static final class ActionLine
	{
		final String label;
		final boolean unlocked;
		final java.util.List<SkillLevel> levels;

		ActionLine(String label, boolean unlocked, java.util.List<SkillLevel> levels)
		{
			this.label = label;
			this.unlocked = unlocked;
			this.levels = levels;
		}
	}

	static final class SkillLevel
	{
		final Skill skill;
		final int level;

		SkillLevel(Skill skill, int level)
		{
			this.skill = skill;
			this.level = level;
		}
	}
}
