package com.leadman.ui;

import com.leadman.rules.ItemRule;
import com.leadman.rules.Requirement;
import com.leadman.unlock.CustomRule;
import com.leadman.unlock.UnlockService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.FlatTextField;

/** Modal editor for per-item gate overrides. Changes persist via {@link UnlockService}. */
final class ItemRuleEditorDialog extends JDialog
{
	private static final int DIALOG_WIDTH = 340;

	private final SkillIconManager skillIcons;
	private final Skill[] skills;

	private final JPanel body = new JPanel();
	private final JLabel itemTitle = LeadmanUi.sectionLabel("");

	private final ActionGateRow trade;
	private final ActionGateRow shop;
	private final ActionGateRow use;
	private final ActionGateRow eat;
	private final ActionGateRow drink;
	private final ActionGateRow wield;
	private final ActionGateRow teleport;
	private final ActionGateRow bury;

	ItemRuleEditorDialog(java.awt.Window owner, SkillIconManager skillIcons, Skill[] skills)
	{
		super(owner, "Edit item rules", ModalityType.APPLICATION_MODAL);
		this.skillIcons = skillIcons;
		this.skills = skills;

		trade = new ActionGateRow("Can trade", Skill.SMITHING);
		shop = new ActionGateRow("Can shop", Skill.SMITHING);
		use = new ActionGateRow("Can use", Skill.WOODCUTTING);
		eat = new ActionGateRow("Can eat", Skill.COOKING);
		drink = new ActionGateRow("Can drink", Skill.HERBLORE);
		wield = new ActionGateRow("Can wield", Skill.ATTACK);
		teleport = new ActionGateRow("Can teleport", Skill.MAGIC);
		bury = new ActionGateRow("Can bury", Skill.PRAYER);

		setLayout(new BorderLayout());
		getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);

		body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
		LeadmanUi.darkPanel(body);
		body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		body.add(itemTitle);
		body.add(trade.panel);
		body.add(shop.panel);
		body.add(use.panel);
		body.add(eat.panel);
		body.add(drink.panel);
		body.add(wield.panel);
		body.add(teleport.panel);
		body.add(bury.panel);

		JScrollPane scroll = new JScrollPane(body);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setPreferredSize(new Dimension(DIALOG_WIDTH, 420));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		LeadmanUi.darkPanel(buttons);

		JButton reset = new JButton("Reset to default");
		JButton cancel = new JButton("Cancel");
		JButton save = new JButton("Save");
		LeadmanUi.styleSecondaryButton(reset);
		LeadmanUi.styleSecondaryButton(cancel);
		LeadmanUi.stylePrimaryButton(save);

		buttons.add(reset);
		buttons.add(cancel);
		buttons.add(save);

		add(scroll, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);

		cancel.addActionListener(e -> dispose());
		reset.addActionListener(e -> {});
		save.addActionListener(e -> {});
	}

	void open(
		String displayName,
		ItemRule generated,
		CustomRule existing,
		UnlockService unlockService,
		Runnable onSaved)
	{
		itemTitle.setText(displayName);

		ActionDefaults defaults = ActionDefaults.from(generated);
		ActionDefaults current = existing == null ? defaults : ActionDefaults.from(existing, defaults);

		trade.apply(current.trade);
		shop.apply(current.shop);
		use.apply(current.use);
		eat.apply(current.eat);
		drink.apply(current.drink);
		wield.apply(current.wield);
		teleport.apply(current.teleport);
		bury.apply(current.bury);

		String key = generated != null ? generated.getName() : displayName;

		JButton save = findButton("Save");
		JButton reset = findButton("Reset to default");

		for (var l : save.getActionListeners())
		{
			save.removeActionListener(l);
		}
		for (var l : reset.getActionListeners())
		{
			reset.removeActionListener(l);
		}

		save.addActionListener(e -> {
			unlockService.upsertCustomRule(buildRule(displayName));
			onSaved.run();
			dispose();
		});

		reset.addActionListener(e -> {
			unlockService.removeCustomRule(key);
			onSaved.run();
			dispose();
		});

		setMinimumSize(new Dimension(DIALOG_WIDTH, 200));
		pack();
		setLocationRelativeTo(getOwner());
		setVisible(true);
	}

	private CustomRule buildRule(String displayName)
	{
		CustomRule rule = new CustomRule();
		rule.setItem(displayName);
		trade.write(rule::setGateTrade, rule::setTradeReqs);
		shop.write(rule::setGateShop, rule::setShopReqs);
		use.write(rule::setGateUse, rule::setUseReqs);
		eat.write(rule::setGateEat, rule::setEatReqs);
		drink.write(rule::setGateDrink, rule::setDrinkReqs);
		wield.write(rule::setGateWield, rule::setWieldReqs);
		teleport.write(rule::setGateActivate, rule::setActivateReqs);
		bury.write(rule::setGateBury, rule::setBuryReqs);
		return rule;
	}

	private JButton findButton(String text)
	{
		JPanel buttons = (JPanel) getContentPane().getComponent(1);
		for (java.awt.Component c : buttons.getComponents())
		{
			if (c instanceof JButton && text.equals(((JButton) c).getText()))
			{
				return (JButton) c;
			}
		}
		throw new IllegalStateException("Button not found: " + text);
	}

	private final class ActionGateRow
	{
		private final JCheckBox override = new JCheckBox("Override");
		private final JButton skillPicker;
		private final FlatTextField level;
		private final JPanel panel;

		ActionGateRow(String label, Skill defaultSkill)
		{
			skillPicker = LeadmanUi.createSkillPicker(skillIcons, skills, defaultSkill, () -> {});
			level = LeadmanUi.createLevelField(1);
			LeadmanUi.styleCheckBox(override);

			panel = new JPanel(new BorderLayout(4, 4));
			LeadmanUi.darkPanel(panel);
			panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
			panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

			JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			LeadmanUi.darkPanel(head);
			head.add(LeadmanUi.sectionLabel(label));
			head.add(override);

			JPanel reqs = new JPanel(new BorderLayout(4, 0));
			LeadmanUi.darkPanel(reqs);
			reqs.add(skillPicker, BorderLayout.CENTER);
			reqs.add(level, BorderLayout.EAST);

			panel.add(head, BorderLayout.NORTH);
			panel.add(reqs, BorderLayout.CENTER);
		}

		void apply(GateValues values)
		{
			override.setSelected(values.override);
			LeadmanUi.setSkillPicker(skillPicker, skillIcons, values.skill);
			level.setText(String.valueOf(values.level));
		}

		void write(java.util.function.Consumer<Boolean> gateSetter, java.util.function.Consumer<List<Requirement>> reqSetter)
		{
			gateSetter.accept(override.isSelected());
			reqSetter.accept(override.isSelected()
				? Collections.singletonList(new Requirement(LeadmanUi.skillFromPicker(skillPicker), parseLevel(level)))
				: Collections.emptyList());
		}
	}

	private static int parseLevel(FlatTextField field)
	{
		try
		{
			return Math.max(1, Math.min(99, Integer.parseInt(field.getText().trim())));
		}
		catch (NumberFormatException e)
		{
			return 1;
		}
	}

	private static final class GateValues
	{
		final boolean override;
		final Skill skill;
		final int level;

		GateValues(boolean override, Skill skill, int level)
		{
			this.override = override;
			this.skill = skill;
			this.level = level;
		}
	}

	private static final class ActionDefaults
	{
		final GateValues trade;
		final GateValues shop;
		final GateValues use;
		final GateValues eat;
		final GateValues drink;
		final GateValues wield;
		final GateValues teleport;
		final GateValues bury;

		ActionDefaults(
			GateValues trade,
			GateValues shop,
			GateValues use,
			GateValues eat,
			GateValues drink,
			GateValues wield,
			GateValues teleport,
			GateValues bury)
		{
			this.trade = trade;
			this.shop = shop;
			this.use = use;
			this.eat = eat;
			this.drink = drink;
			this.wield = wield;
			this.teleport = teleport;
			this.bury = bury;
		}

		static ActionDefaults from(ItemRule rule)
		{
			if (rule == null)
			{
				return new ActionDefaults(
					new GateValues(false, Skill.SMITHING, 1),
					new GateValues(false, Skill.SMITHING, 1),
					new GateValues(false, Skill.WOODCUTTING, 1),
					new GateValues(false, Skill.COOKING, 1),
					new GateValues(false, Skill.HERBLORE, 1),
					new GateValues(false, Skill.ATTACK, 1),
					new GateValues(false, Skill.MAGIC, 1),
					new GateValues(false, Skill.PRAYER, 1));
			}

			return new ActionDefaults(
				gate(RuleDisplayUtil.tradeReqs(rule), Skill.SMITHING),
				gate(RuleDisplayUtil.shopReqs(rule), Skill.SMITHING),
				gate(RuleDisplayUtil.useReqs(rule), Skill.WOODCUTTING),
				gate(RuleDisplayUtil.eatReqs(rule), Skill.COOKING),
				gate(RuleDisplayUtil.drinkReqs(rule), Skill.HERBLORE),
				gate(RuleDisplayUtil.wieldReqs(rule), Skill.ATTACK),
				gate(RuleDisplayUtil.activateReqs(rule), Skill.MAGIC),
				gate(RuleDisplayUtil.buryReqs(rule), Skill.PRAYER));
		}

		static ActionDefaults from(CustomRule custom, ActionDefaults fallback)
		{
			return new ActionDefaults(
				customGate(custom.isGateTrade(), custom.getTradeReqs(), fallback.trade),
				customGate(custom.isGateShop(), custom.getShopReqs(), fallback.shop),
				customGate(custom.isGateUse(), custom.getUseReqs(), fallback.use),
				customGate(custom.isGateEat(), custom.getEatReqs(), fallback.eat),
				customGate(custom.isGateDrink(), custom.getDrinkReqs(), fallback.drink),
				customGate(custom.isGateWield(), custom.getWieldReqs(), fallback.wield),
				customGate(custom.isGateActivate(), custom.getActivateReqs(), fallback.teleport),
				customGate(custom.isGateBury(), custom.getBuryReqs(), fallback.bury));
		}

		private static GateValues gate(List<LeadmanUi.SkillLevel> levels, Skill fallbackSkill)
		{
			if (levels.isEmpty())
			{
				return new GateValues(false, fallbackSkill, 1);
			}
			LeadmanUi.SkillLevel first = RuleDisplayUtil.firstSkillLevel(levels);
			return new GateValues(false, first.skill, first.level);
		}

		private static GateValues customGate(boolean overridden, List<Requirement> reqs, GateValues fallback)
		{
			if (!overridden)
			{
				return new GateValues(false, fallback.skill, fallback.level);
			}
			if (reqs.isEmpty())
			{
				return new GateValues(true, fallback.skill, fallback.level);
			}
			LeadmanUi.SkillLevel level = RuleDisplayUtil.firstSkillLevel(
				RuleDisplayUtil.fromRequirements(reqs));
			return new GateValues(true, level.skill, level.level);
		}
	}
}
