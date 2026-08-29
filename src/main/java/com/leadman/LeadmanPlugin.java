package com.leadman;

import com.google.inject.Provides;
import com.leadman.rules.ConsumeClass;
import com.leadman.rules.ItemNames;
import com.leadman.rules.ItemRule;
import com.leadman.rules.RuleRepository;
import com.leadman.ui.LeadmanPanel;
import com.leadman.ui.LeadmanOverlay;
import com.leadman.ui.UnlockNotification;
import com.leadman.unlock.UnlockService;
import net.runelite.client.util.ImageUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeSearched;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
@PluginDescriptor(
	name = "Leadman Mode",
	description = "Ironman conduct with a Grand Exchange that unlocks item-by-item as you level the skill that makes each item",
	tags = {"ironman", "bronzeman", "challenge", "unlock", "restriction", "leadman"}
)
public class LeadmanPlugin extends Plugin
{
	private static final Set<String> GE_OPS = new HashSet<>(Arrays.asList(
		"offer", "sell", "buy"
	));

	/**
	 * Options that never spend a charge. Everything else on a charged item is assumed to,
	 * because teleport options are named after their destination and cannot be listed.
	 */
	private static final Set<String> INERT_OPS = new HashSet<>(Arrays.asList(
		"drop", "examine", "use", "take", "remove", "deposit", "withdraw", "value",
		"cancel", "destroy", "bury", "note", "un-note", "open", "close", "check",
		"toggle", "empty", "fill", "clean", "search", "talk-to", "attack", "walk here",
		"eat", "drink", "wear", "wield", "equip", "offer", "sell", "buy"
	));

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private LeadmanConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private RuleRepository rules;

	@Inject
	private UnlockService unlockService;

	@Inject
	private LeadmanOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private LeadmanPanel panel;

	private NavigationButton navButton;

	private final Map<Skill, Integer> lastLevels = new EnumMap<>(Skill.class);
	private final Map<Integer, Integer> lastInventory = new HashMap<>();
	private final Map<Long, Integer> groundOwnership = new HashMap<>();

	private boolean shopOpen;
	private boolean geOpen;
	private boolean autocastOpen;
	private boolean profileLoaded;
	private int ticksSinceSave;

	@Provides
	LeadmanConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LeadmanConfig.class);
	}

	@Override
	protected void startUp()
	{
		rules.load();
		unlockService.reloadCustomRules();
		overlayManager.add(overlay);

		panel.init();
		navButton = NavigationButton.builder()
			.tooltip("Leadman Mode")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::loadProfile);
		}
	}

	@Override
	protected void shutDown()
	{
		unlockService.save();
		overlayManager.remove(overlay);
		overlay.clear();
		clientToolbar.removeNavigation(navButton);

		lastLevels.clear();
		lastInventory.clear();
		groundOwnership.clear();
		profileLoaded = false;
	}

	// ------------------------------------------------------------- lifecycle

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				if (!profileLoaded)
				{
					loadProfile();
				}
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				unlockService.save();
				profileLoaded = false;
				lastLevels.clear();
				lastInventory.clear();
				groundOwnership.clear();
				break;
			default:
				break;
		}
	}

	private void loadProfile()
	{
		long hash = client.getAccountHash();
		if (hash == -1)
		{
			return;
		}

		unlockService.loadProfile(hash);

		for (Skill skill : Skill.values())
		{
			// Older clients expose a synthetic OVERALL constant that has no real level.
			if ("OVERALL".equals(skill.name()))
			{
				continue;
			}
			lastLevels.put(skill, client.getRealSkillLevel(skill));
		}

		// The first scan for a profile records the baseline silently. Every scan after
		// that reports what changed, so logging in never fires a popup storm.
		boolean firstScan = !unlockService.getState().isSeeded();
		unlockService.refreshSatisfied(firstScan);
		if (firstScan)
		{
			unlockService.refreshSatisfied(true);
		}

		profileLoaded = true;
		unlockService.save();
		panel.refresh();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (++ticksSinceSave >= 50)
		{
			ticksSinceSave = 0;
			unlockService.save();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!LeadmanConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		unlockService.reloadCustomRules();
		clientThread.invokeLater(() -> {
			unlockService.refreshSatisfied(true);
			panel.refresh();
		});
	}

	// ------------------------------------------------------------- unlocking

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (!profileLoaded)
		{
			return;
		}

		Skill skill = event.getSkill();
		Integer previous = lastLevels.get(skill);
		int level = event.getLevel();

		if (previous != null && level <= previous)
		{
			return;
		}
		lastLevels.put(skill, level);

		if (previous == null)
		{
			return;
		}

		List<ItemRule> newly = unlockService.refreshSatisfied(false);
		if (newly.isEmpty())
		{
			return;
		}

		announceLevelUnlocks(skill, level, newly);
	}

	private void announceLevelUnlocks(Skill skill, int level, List<ItemRule> newly)
	{
		String heading = level + " " + pretty(skill);

		if (newly.size() > config.popupBatchThreshold())
		{
			pushPopup(heading, newly.size() + " items unlocked", firstItemId(newly));
			chat(heading + " unlocked " + newly.size() + " items. See the Leadman Mode panel for the list.");
			for (ItemRule rule : newly)
			{
				unlockService.markSeen(rule.getName());
			}
		}
		else
		{
			for (ItemRule rule : newly)
			{
				if (!unlockService.markSeen(rule.getName()))
				{
					continue;
				}
				pushPopup(heading, rule.getDisplay(), lookupItemId(rule));
				chat("Unlocked: " + rule.getDisplay() + " (" + heading + ")");
			}
		}

		panel.refresh();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!profileLoaded)
		{
			return;
		}

		int id = event.getContainerId();

		if (id == InventoryID.BANK)
		{
			// You did obtain everything in your own bank, so it counts for trade.
			for (Item item : event.getItemContainer().getItems())
			{
				if (item.getId() > 0)
				{
					unlockService.markObtained(item.getId());
				}
			}
			return;
		}

		if (id != InventoryID.INV && id != InventoryID.WORN)
		{
			return;
		}

		ItemContainer container = event.getItemContainer();
		Map<Integer, Integer> current = new HashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() > 0)
			{
				current.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}

		if (id == InventoryID.INV)
		{
			for (Map.Entry<Integer, Integer> entry : current.entrySet())
			{
				int itemId = entry.getKey();
				int before = lastInventory.getOrDefault(itemId, 0);
				if (entry.getValue() <= before)
				{
					continue;
				}
				// Shop stock must never launder an item onto the Grand Exchange, so a
				// purchase made with the shop open is not an "obtain".
				if (shopOpen)
				{
					continue;
				}
				if (unlockService.markObtained(itemId))
				{
					announceObtain(itemId);
				}
			}
			lastInventory.clear();
			lastInventory.putAll(current);
		}
		else
		{
			for (Integer itemId : current.keySet())
			{
				unlockService.markObtained(itemId);
			}
		}
	}

	private void announceObtain(int itemId)
	{
		String key = unlockService.keyFor(itemId);
		if (key.isEmpty() || !unlockService.markSeen(key))
		{
			return;
		}

		ItemRule rule = rules.forName(key);
		// A fabricable item you happen to loot is not news -- the skill already
		// unlocked it. Only report items whose only route is obtaining one.
		if (rule != null && rule.hasSkillPath() && unlockService.isSatisfiedKey(key))
		{
			return;
		}

		pushPopup("Item unlocked", unlockService.displayFor(itemId), itemId);
		chat("Unlocked: " + unlockService.displayFor(itemId));
		panel.refresh();
	}

	// ------------------------------------------------------------ enforcement

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!profileLoaded || event.isConsumed())
		{
			return;
		}

		String option = Text.removeTags(event.getMenuOption()).toLowerCase().trim();
		String target = Text.removeTags(event.getMenuTarget()).trim();
		if (isShopBuy(event, option))
		{
			String key = resolveMenuItemKey(event, target);
			if (!key.isEmpty() && !unlockService.canShopKey(key))
			{
				event.consume();
				explainOnce("shop:" + key, "Locked: " + displayName(key, target)
					+ " needs " + unlockService.shopReason(key) + " to buy from a shop.");
				return;
			}
		}

		String spell = resolveSpellName(event);
		if (spell == null || unlockService.canCast(spell))
		{
			return;
		}

		event.consume();
		explainOnce("cast:" + spell, "You cannot craft the runes for " + spell
			+ " yet. Missing " + unlockService.missingRuneFor(spell) + ".");
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!profileLoaded)
		{
			return;
		}

		String option = Text.removeTags(event.getOption()).toLowerCase().trim();
		String target = Text.removeTags(event.getTarget()).trim();

		if (config.blockPlayerTrade() && option.startsWith("trade with"))
		{
			removeEntry(event);
			return;
		}

		if (config.blockOtherPlayerDrops() && "take".equals(option) && isOtherPlayersDrop(event))
		{
			removeEntry(event);
			return;
		}

		if (option.startsWith("cast") || "autocast".equals(option))
		{
			blockSpellCast(event, target);
			return;
		}

		if (autocastOpen && !target.isEmpty())
		{
			blockSpellCast(event, target);
			return;
		}

		boolean isShopBuy = isShopBuy(event, option);
		boolean isGeOp = geOpen && GE_OPS.contains(option);
		boolean isWieldOp = "wear".equals(option) || "wield".equals(option) || "equip".equals(option);
		boolean isEatOp = "eat".equals(option);
		boolean isDrinkOp = "drink".equals(option);
		boolean isBuryOp = "bury".equals(option);
		boolean isUseOp = "use".equals(option);

		if (!isShopBuy && !isGeOp && !isWieldOp && !isEatOp && !isDrinkOp && !isBuryOp && !isUseOp)
		{
			int itemId = resolveItemId(event);
			if (itemId > 0)
			{
				String key = unlockService.keyFor(itemId);
				boolean maybeActivate = !INERT_OPS.contains(option);
				if (maybeActivate && !key.isEmpty() && unlockService.isActivatable(key)
					&& !unlockService.canActivateKey(key))
				{
					removeEntry(event);
					explainOnce("charge:" + key, "Locked: using " + unlockService.displayFor(itemId)
						+ " needs " + unlockService.activateReason(key) + ".");
				}
			}
			return;
		}

		int itemId = resolveItemId(event);
		String key = resolveMenuItemKey(event, target, itemId);
		if (key.isEmpty())
		{
			return;
		}

		if (isShopBuy && !unlockService.canShopKey(key))
		{
			removeEntry(event);
			explainOnce("shop:" + key, "Locked: " + displayName(key, target)
				+ " needs " + unlockService.shopReason(key) + " to buy from a shop.");
			return;
		}

		if (itemId <= 0)
		{
			itemId = lookupItemIdForKey(key);
		}
		if (itemId <= 0 && !isShopBuy)
		{
			return;
		}

		if (isGeOp && !unlockService.canTradeKey(key))
		{
			removeEntry(event);
			ItemRule tradeRule = rules.forName(key);
			String obtainHint = tradeRule != null && tradeRule.hasSkillPath()
				? "."
				: ", or obtain one.";
			explainOnce("trade:" + key, "Not tradeable yet: " + unlockService.displayFor(itemId)
				+ " needs " + unlockService.lockReason(key) + obtainHint);
			return;
		}

		if (isWieldOp)
		{
			ItemRule rule = rules.forName(key);
			ConsumeClass consume = rule == null ? ConsumeClass.NONE : rule.getConsume();
			if (consume == ConsumeClass.JEWELLERY)
			{
				if (!unlockService.canUseKey(key))
				{
					removeEntry(event);
					explainOnce("use:" + key, "Locked: " + unlockService.displayFor(itemId)
						+ " needs " + unlockService.lockReason(key) + ".");
				}
			}
			else if (!unlockService.canWieldKey(key))
			{
				removeEntry(event);
				explainOnce("wield:" + key, "Locked: " + unlockService.displayFor(itemId)
					+ " needs " + unlockService.wieldReason(key) + " to wield.");
			}
			return;
		}

		if (isEatOp && !unlockService.canEatKey(key))
		{
			removeEntry(event);
			explainOnce("eat:" + key, "Locked: " + unlockService.displayFor(itemId)
				+ " needs " + unlockService.lockReason(key) + " to eat.");
			return;
		}

		if (isDrinkOp && !unlockService.canDrinkKey(key))
		{
			removeEntry(event);
			explainOnce("drink:" + key, "Locked: " + unlockService.displayFor(itemId)
				+ " needs " + unlockService.lockReason(key) + " to drink.");
			return;
		}

		if (isBuryOp && !unlockService.canBuryKey(key))
		{
			removeEntry(event);
			explainOnce("bury:" + key, "Locked: " + unlockService.displayFor(itemId)
				+ " needs " + unlockService.lockReason(key) + " to bury.");
			return;
		}

		if (isUseOp && !unlockService.canUseKey(key))
		{
			removeEntry(event);
			explainOnce("use:" + key, "Locked: " + unlockService.displayFor(itemId)
				+ " needs " + unlockService.lockReason(key) + ".");
		}
	}

	private void blockSpellCast(MenuEntryAdded event, String target)
	{
		String spell = normaliseSpellName(target);
		if (spell == null || unlockService.canCast(spell))
		{
			return;
		}
		removeEntry(event);
		explainOnce("cast:" + spell, "You cannot craft the runes for " + spell
			+ " yet. Missing " + unlockService.missingRuneFor(spell) + ".");
	}

	private String resolveSpellName(MenuOptionClicked event)
	{
		String option = Text.removeTags(event.getMenuOption()).trim();
		String target = Text.removeTags(event.getMenuTarget()).trim();

		String fromTarget = normaliseSpellName(target);
		if (fromTarget != null)
		{
			return fromTarget;
		}

		String fromOption = normaliseSpellName(option);
		if (fromOption != null)
		{
			return fromOption;
		}

		if (option.toLowerCase().startsWith("cast") && !target.isEmpty())
		{
			return normaliseSpellName(target);
		}

		Widget widget = event.getWidget();
		if (widget != null && isSpellWidget(widget.getId()))
		{
			String fromWidget = normaliseSpellName(Text.removeTags(widget.getName()));
			if (fromWidget != null)
			{
				return fromWidget;
			}
			if (!target.isEmpty())
			{
				return normaliseSpellName(target);
			}
		}

		if (autocastOpen && !target.isEmpty())
		{
			return normaliseSpellName(target);
		}

		return null;
	}

	private String normaliseSpellName(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return null;
		}
		return rules.forSpell(raw) == null ? null : raw;
	}

	private static boolean isSpellWidget(int widgetId)
	{
		if (widgetId <= 0)
		{
			return false;
		}
		int group = WidgetUtil.componentToInterface(widgetId);
		return group == InterfaceID.AUTOCAST
			|| group == InterfaceID.MAGIC_SPELLBOOK
			|| group == InterfaceID.COMBAT_INTERFACE;
	}

	/**
	 * Filters Grand Exchange search results down to unlocked items -- the same
	 * mechanism the Bronzeman plugin uses.
	 */
	@Subscribe(priority = -100)
	public void onGrandExchangeSearched(GrandExchangeSearched event)
	{
		if (!profileLoaded)
		{
			return;
		}

		final String input = client.getVarcStrValue(VarClientID.MESLAYERINPUT);
		if (input == null || input.isEmpty() || event.isConsumed())
		{
			return;
		}

		event.consume();

		List<Integer> allowed = new ArrayList<>();
		for (ItemPrice price : itemManager.search(input))
		{
			if (unlockService.canTrade(price.getId()))
			{
				allowed.add(price.getId());
				if (allowed.size() >= 250)
				{
					break;
				}
			}
		}

		client.setGeSearchResultIndex(0);
		client.setGeSearchResultCount(allowed.size());

		short[] ids = new short[allowed.size()];
		for (int i = 0; i < allowed.size(); i++)
		{
			ids[i] = (short) allowed.get(i).intValue();
		}
		client.setGeSearchResultIds(ids);
	}

	// ------------------------------------------------------------ ground items

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		TileItem item = event.getItem();
		groundOwnership.put(groundKey(event.getTile(), item.getId()), item.getOwnership());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundOwnership.remove(groundKey(event.getTile(), event.getItem().getId()));
	}

	private boolean isOtherPlayersDrop(MenuEntryAdded event)
	{
		Integer ownership = groundOwnership.get(
			groundKey(event.getActionParam0(), event.getActionParam1(), client.getTopLevelWorldView().getPlane(), event.getIdentifier()));

		if (ownership == null)
		{
			return false;
		}
		return ownership == TileItem.OWNERSHIP_OTHER;
	}

	private static long groundKey(Tile tile, int itemId)
	{
		return groundKey(tile.getSceneLocation().getX(), tile.getSceneLocation().getY(),
			tile.getPlane(), itemId);
	}

	private static long groundKey(int x, int y, int plane, int itemId)
	{
		return ((long) x << 40) | ((long) y << 24) | ((long) plane << 22) | (itemId & 0x3FFFFFL);
	}

	// ---------------------------------------------------------------- widgets

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		switch (event.getGroupId())
		{
			case InterfaceID.SHOPMAIN:
			case InterfaceID.SHOPSIDE:
			case InterfaceID.OMNISHOP_MAIN:
			case InterfaceID.OMNISHOP_SIDE:
			case InterfaceID.MAGICTRAINING_SHOP:
				shopOpen = true;
				break;
			case InterfaceID.GE_OFFERS:
			case InterfaceID.GE_OFFERS_SIDE:
				geOpen = true;
				break;
			case InterfaceID.AUTOCAST:
				autocastOpen = true;
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		switch (event.getGroupId())
		{
			case InterfaceID.SHOPMAIN:
			case InterfaceID.SHOPSIDE:
			case InterfaceID.OMNISHOP_MAIN:
			case InterfaceID.OMNISHOP_SIDE:
			case InterfaceID.MAGICTRAINING_SHOP:
				shopOpen = shopInterfaceVisible();
				break;
			case InterfaceID.GE_OFFERS:
			case InterfaceID.GE_OFFERS_SIDE:
				geOpen = false;
				break;
			case InterfaceID.AUTOCAST:
				autocastOpen = false;
				break;
			default:
				break;
		}
	}

	// ----------------------------------------------------------------- helpers

	private static boolean isShopBuyOption(String option)
	{
		return option.equals("buy") || option.startsWith("buy-") || option.startsWith("buy ");
	}

	private boolean isShopBuy(MenuEntryAdded event, String option)
	{
		if (!isShopBuyOption(option) || geOpen || isGeMenu(event))
		{
			return false;
		}
		return shopOpen || shopInterfaceVisible() || isShopMenu(event);
	}

	private boolean isShopBuy(MenuOptionClicked event, String option)
	{
		if (!isShopBuyOption(option) || geOpen || isGeMenu(event))
		{
			return false;
		}
		return shopOpen || shopInterfaceVisible() || isShopMenu(event);
	}

	private static final int[] SHOP_INTERFACES = {
		InterfaceID.SHOPMAIN,
		InterfaceID.SHOPSIDE,
		InterfaceID.OMNISHOP_MAIN,
		InterfaceID.OMNISHOP_SIDE,
		InterfaceID.MAGICTRAINING_SHOP,
	};

	private boolean shopInterfaceVisible()
	{
		for (int groupId : SHOP_INTERFACES)
		{
			Widget widget = client.getWidget(groupId);
			if (widget != null && !widget.isHidden())
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isShopInterface(int groupId)
	{
		for (int id : SHOP_INTERFACES)
		{
			if (id == groupId)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isGeInterface(int groupId)
	{
		return groupId == InterfaceID.GE_OFFERS
			|| groupId == InterfaceID.GE_OFFERS_SIDE;
	}

	private boolean isGeMenu(MenuEntryAdded event)
	{
		MenuEntry entry = event.getMenuEntry();
		return entry != null && isGeWidget(entry.getWidget());
	}

	private boolean isGeMenu(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		return entry != null && isGeWidget(entry.getWidget());
	}

	private static boolean isGeWidget(Widget widget)
	{
		while (widget != null)
		{
			if (isGeInterface(WidgetUtil.componentToInterface(widget.getId())))
			{
				return true;
			}
			widget = widget.getParent();
		}
		return false;
	}

	private boolean isShopMenu(MenuEntryAdded event)
	{
		MenuEntry entry = event.getMenuEntry();
		return entry != null && isShopWidget(entry.getWidget());
	}

	private boolean isShopMenu(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		return entry != null && isShopWidget(entry.getWidget());
	}

	private static boolean isShopWidget(Widget widget)
	{
		while (widget != null)
		{
			if (isShopInterface(WidgetUtil.componentToInterface(widget.getId())))
			{
				return true;
			}
			widget = widget.getParent();
		}
		return false;
	}

	private String resolveMenuItemKey(MenuEntryAdded event, String target)
	{
		return resolveMenuItemKey(event, target, resolveItemId(event));
	}

	private String resolveMenuItemKey(MenuEntryAdded event, String target, int itemId)
	{
		if (itemId > 0)
		{
			String key = unlockService.keyFor(itemId);
			if (!key.isEmpty())
			{
				return key;
			}
		}

		MenuEntry entry = event.getMenuEntry();
		if (entry != null)
		{
			itemId = itemIdFromWidgetChain(entry.getWidget());
			if (itemId > 0)
			{
				String key = unlockService.keyFor(itemId);
				if (!key.isEmpty())
				{
					return key;
				}
			}
		}

		return keyFromTarget(target);
	}

	private String resolveMenuItemKey(MenuOptionClicked event, String target)
	{
		int itemId = resolveClickedItemId(event);
		if (itemId > 0)
		{
			String key = unlockService.keyFor(itemId);
			if (!key.isEmpty())
			{
				return key;
			}
		}

		return keyFromTarget(target);
	}

	private int resolveClickedItemId(MenuOptionClicked event)
	{
		int itemId = event.getItemId();
		if (itemId > 0)
		{
			return itemId;
		}

		MenuEntry entry = event.getMenuEntry();
		if (entry != null)
		{
			itemId = entry.getItemId();
			if (itemId > 0)
			{
				return itemId;
			}

			itemId = entry.getIdentifier();
			if (isKnownItemId(itemId))
			{
				return itemId;
			}

			itemId = itemIdFromWidgetChain(entry.getWidget());
			if (itemId > 0)
			{
				return itemId;
			}
		}

		itemId = event.getId();
		if (isKnownItemId(itemId))
		{
			return itemId;
		}

		return itemIdFromWidgetChain(event.getWidget());
	}

	private static int itemIdFromWidgetChain(Widget widget)
	{
		while (widget != null)
		{
			int id = widget.getItemId();
			if (id > 0)
			{
				return id;
			}
			widget = widget.getParent();
		}
		return -1;
	}

	private static String keyFromTarget(String target)
	{
		if (target.isEmpty())
		{
			return "";
		}
		String cleaned = target.replaceAll("\\s*\\([^)]*\\)\\s*$", "").trim();
		return ItemNames.normalise(cleaned);
	}

	private String displayName(String key, String fallback)
	{
		ItemRule rule = rules.forName(key);
		if (rule != null)
		{
			return rule.getDisplay();
		}
		return fallback.isEmpty() ? key : fallback;
	}

	private int lookupItemIdForKey(String key)
	{
		for (var price : itemManager.search(key))
		{
			if (unlockService.keyFor(price.getId()).equals(key))
			{
				return price.getId();
			}
		}
		return -1;
	}

	private void removeEntry(MenuEntryAdded event)
	{
		client.getMenu().removeMenuEntry(event.getMenuEntry());
	}

	/**
	 * Resolves the item a menu entry refers to. The event carries the id for most item
	 * entries; the widget walk is a fallback for the ones it does not, such as some
	 * worn-equipment and Grand Exchange slots.
	 */
	private int resolveItemId(MenuEntryAdded event)
	{
		int direct = event.getItemId();
		if (direct > 0)
		{
			return direct;
		}

		MenuEntry entry = event.getMenuEntry();
		if (entry != null)
		{
			int fromEntry = entry.getItemId();
			if (fromEntry > 0)
			{
				return fromEntry;
			}

			int fromWidget = itemIdFromWidgetChain(entry.getWidget());
			if (fromWidget > 0)
			{
				return fromWidget;
			}
		}

		int widgetId = event.getActionParam1();
		int index = event.getActionParam0();

		// Packed widget ids are (group << 16 | child), so anything smaller belongs to an
		// NPC, object or ground item rather than an item slot.
		if (widgetId < 1 << 16)
		{
			int fromIdentifier = event.getIdentifier();
			return isKnownItemId(fromIdentifier) ? fromIdentifier : -1;
		}

		Widget widget = client.getWidget(widgetId);
		if (widget == null)
		{
			int fromIdentifier = event.getIdentifier();
			return isKnownItemId(fromIdentifier) ? fromIdentifier : -1;
		}

		Widget[] children = widget.getChildren();
		if (children != null && index >= 0 && index < children.length)
		{
			int id = children[index].getItemId();
			if (id > 0)
			{
				return id;
			}
		}

		int id = widget.getItemId();
		if (id > 0)
		{
			return id;
		}

		id = event.getIdentifier();
		if (isKnownItemId(id))
		{
			return id;
		}

		return itemIdFromWidgetChain(widget);
	}

	private boolean isKnownItemId(int itemId)
	{
		return itemId > 0 && !unlockService.keyFor(itemId).isEmpty();
	}

	private int lookupItemId(ItemRule rule)
	{
		for (ItemPrice price : itemManager.search(rule.getDisplay()))
		{
			if (unlockService.keyFor(price.getId()).equals(rule.getName()))
			{
				return price.getId();
			}
		}
		return -1;
	}

	private int firstItemId(List<ItemRule> unlocked)
	{
		for (ItemRule rule : unlocked)
		{
			int id = lookupItemId(rule);
			if (id > 0)
			{
				return id;
			}
		}
		return -1;
	}

	private void pushPopup(String title, String subtitle, int itemId)
	{
		overlay.push(new UnlockNotification(title, subtitle, itemId));
	}

	private void chat(String message)
	{
		if (!config.popupChat())
		{
			return;
		}
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Leadman Mode] " + message, null);
	}

	private final Set<String> explained = new HashSet<>();

	/**
	 * Menu entries are rebuilt every frame, so a naive message would spam the chatbox.
	 * Each distinct reason is explained once per session.
	 */
	private void explainOnce(String key, String message)
	{
		if (!config.explainBlocks() || !explained.add(key))
		{
			return;
		}
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Leadman Mode] " + message, null);
	}

	private static String pretty(Skill skill)
	{
		String raw = skill.name();
		return raw.charAt(0) + raw.substring(1).toLowerCase();
	}

}
