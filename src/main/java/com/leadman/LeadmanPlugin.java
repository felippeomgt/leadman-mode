package com.leadman;

import com.google.inject.Provides;
import com.leadman.rules.ConsumeClass;
import com.leadman.rules.ItemClass;
import com.leadman.rules.ItemNames;
import com.leadman.rules.ItemRule;
import com.leadman.rules.RuleRepository;
import com.leadman.ui.ItemCatalogDialog;
import com.leadman.ui.LeadmanInventoryOverlay;
import com.leadman.ui.LeadmanPanel;
import com.leadman.ui.LeadmanOverlay;
import com.leadman.ui.RuleDisplayUtil;
import com.leadman.ui.UnlockNotification;
import com.leadman.unlock.CustomRule;
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
import net.runelite.api.MenuAction;
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
import net.runelite.client.party.PartyService;
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

	private static final String COL_TAG = "<col=E2AE4A>";
	private static final String COL_GOOD = "<col=00FF80>";
	private static final String COL_WARN = "<col=FF981F>";
	private static final String COL_END = "</col>";

	/**
	 * Options that never spend a charge. Everything else on a charged item is assumed to,
	 * because teleport options are named after their destination and cannot be listed.
	 */
	private static final Set<String> INERT_OPS = new HashSet<>(Arrays.asList(
		"drop", "examine", "use", "take", "remove", "deposit", "withdraw", "value",
		"cancel", "destroy", "bury", "note", "un-note", "open", "close", "check",
		"toggle", "empty", "fill", "clean", "search", "talk-to", "attack", "walk here",
		"eat", "drink", "wear", "wield", "equip", "offer", "sell", "buy",
		"bank", "collect", "poll booth", "ring", "quick-withdraw", "quick-deposit"
	));

	private static boolean isInertOp(String option)
	{
		if (INERT_OPS.contains(option))
		{
			return true;
		}
		return option.startsWith("deposit")
			|| option.startsWith("withdraw")
			|| option.startsWith("quick-deposit")
			|| option.startsWith("quick-withdraw");
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private LeadmanConfig config;

	@Inject
	private PartyService partyService;

	@Inject
	private ItemManager itemManager;

	@Inject
	private RuleRepository rules;

	@Inject
	private UnlockService unlockService;

	@Inject
	private LeadmanOverlay overlay;

	@Inject
	private LeadmanInventoryOverlay inventoryOverlay;

	@Inject
	private ItemCatalogDialog catalog;

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
		overlayManager.add(inventoryOverlay);

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
		overlayManager.remove(inventoryOverlay);
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
			if (catalog.isVisible())
			{
				catalog.refresh();
			}
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
			chatUnlock(heading + " unlocked " + newly.size() + " items for trade.");
			for (ItemRule rule : newly)
			{
				unlockService.markSeen(rule.getName());
				unlockService.recordRecentUnlock(rule.getName());
			}
		}
		else
		{
			for (ItemRule rule : newly)
			{
				unlockService.recordRecentUnlock(rule.getName());
				if (!unlockService.markSeen(rule.getName()))
				{
					continue;
				}
				pushPopup(heading, rule.getDisplay(), lookupItemId(rule));
				chatUnlock("Trade unlocked: " + rule.getDisplay() + " (" + heading + ")");
			}
		}

		panel.refresh();
		if (catalog.isVisible())
		{
			catalog.refresh();
		}
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
				// purchase made with the shop open is not an "obtain" — except SHOP_ONLY
				// items that cannot be acquired any other way.
				if (shopOpen)
				{
					ItemRule shopRule = rules.forName(unlockService.keyFor(itemId));
					if (shopRule == null || shopRule.getItemClass() != ItemClass.SHOP_ONLY)
					{
						continue;
					}
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
		chatUnlock("Obtain unlock: " + unlockService.displayFor(itemId));
		panel.refresh();
		if (catalog.isVisible())
		{
			catalog.refresh();
		}
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

		if ("examine".equals(option))
		{
			String key = resolveMenuItemKey(event, target);
			if (!key.isEmpty())
			{
				ItemRule rule = rules.forName(key);
				CustomRule custom = unlockService.getCustomRule(key);
				String locked = RuleDisplayUtil.lockedActionsSummary(
					unlockService, key, rule, custom);
				if (locked != null)
				{
					explainAttempt(displayName(key, target) + ": " + locked + " locked.");
				}
			}
			return;
		}

		if (isShopBuy(event, option))
		{
			String key = resolveMenuItemKey(event, target);
			if (!key.isEmpty() && !unlockService.canShopKey(key))
			{
				event.consume();
				explainAttempt("Locked: " + displayName(key, target)
					+ " needs " + unlockService.shopReason(key) + " to buy from a shop.");
				return;
			}
		}

		if (blockItemUseOnClick(event, option, target))
		{
			return;
		}

		String spell = resolveSpellName(event);
		if (spell == null || unlockService.canCast(spell))
		{
			return;
		}

		event.consume();
		explainAttempt("You cannot craft the runes for " + spell
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
			if (!TradePartnerAllowlist.isAllowed(
				target,
				config.allowedTradePartners(),
				config.allowTradeWithParty(),
				partyService))
			{
				removeEntry(event);
			}
			return;
		}

		if (config.blockOtherPlayerDrops() && "take".equals(option) && isOtherPlayersDrop(event))
		{
			removeEntry(event);
			return;
		}

		if (option.startsWith("cast") || "autocast".equals(option))
		{
			blockSpellCast(event, option, target);
			return;
		}

		if (autocastOpen && !target.isEmpty())
		{
			blockSpellCast(event, option, target);
			return;
		}

		boolean isShopBuy = isShopBuy(event, option);
		boolean isGeOp = geOpen && GE_OPS.contains(option);
		if (isGeOp && config.disableGe())
		{
			removeEntry(event);
			return;
		}
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
				boolean maybeActivate = !isInertOp(option);
				if (maybeActivate && !key.isEmpty() && unlockService.isActivatable(key)
					&& !unlockService.canActivateKey(key))
				{
					removeEntry(event);
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
				}
			}
			else if (!unlockService.canWieldKey(key))
			{
				removeEntry(event);
			}
			return;
		}

		if (isEatOp && !unlockService.canEatKey(key))
		{
			removeEntry(event);
			return;
		}

		if (isDrinkOp && !unlockService.canDrinkKey(key))
		{
			removeEntry(event);
			return;
		}

		if (isBuryOp && !unlockService.canBuryKey(key))
		{
			removeEntry(event);
			return;
		}

		if (isUseOp && !unlockService.canUseKey(key))
		{
			removeEntry(event);
		}
	}

	private void blockSpellCast(MenuEntryAdded event, String option, String target)
	{
		String spell = resolveSpellFromMenu(option, target);
		if (spell == null || unlockService.canCast(spell))
		{
			return;
		}
		removeEntry(event);
	}

	/**
	 * Blocks and explains item gates only when the player actually clicks (including left-click).
	 * MenuEntryAdded only hides options silently.
	 */
	private boolean blockItemUseOnClick(MenuOptionClicked event, String option, String target)
	{
		boolean isGeOp = geOpen && GE_OPS.contains(option);
		boolean isWieldOp = "wear".equals(option) || "wield".equals(option) || "equip".equals(option);
		boolean isEatOp = "eat".equals(option);
		boolean isDrinkOp = "drink".equals(option);
		boolean isBuryOp = "bury".equals(option);
		boolean isUseOp = "use".equals(option);

		if (!isGeOp && !isWieldOp && !isEatOp && !isDrinkOp && !isBuryOp && !isUseOp)
		{
			return false;
		}

		String key = resolveMenuItemKey(event, target);
		if (key.isEmpty())
		{
			return false;
		}

		int itemId = resolveClickedItemId(event);
		String display = itemId > 0 ? unlockService.displayFor(itemId) : displayName(key, target);

		if (isGeOp && config.disableGe())
		{
			event.consume();
			explainAttempt("Grand Exchange is disabled in Leadman conduct settings.");
			return true;
		}

		if (isGeOp && !unlockService.canTradeKey(key))
		{
			event.consume();
			ItemRule tradeRule = rules.forName(key);
			String obtainHint = tradeRule != null && tradeRule.hasSkillPath()
				? "."
				: ", or obtain one.";
			explainAttempt("Not tradeable yet: " + display
				+ " needs " + unlockService.lockReason(key) + obtainHint);
			return true;
		}

		if (isWieldOp)
		{
			ItemRule rule = rules.forName(key);
			ConsumeClass consume = rule == null ? ConsumeClass.NONE : rule.getConsume();
			if (consume == ConsumeClass.JEWELLERY)
			{
				if (!unlockService.canUseKey(key))
				{
					event.consume();
					explainAttempt("Locked: " + display
						+ " needs " + unlockService.lockReason(key) + ".");
					return true;
				}
			}
			else if (!unlockService.canWieldKey(key))
			{
				event.consume();
				explainAttempt("Locked: " + display
					+ " needs " + unlockService.wieldReason(key) + " to wield.");
				return true;
			}
			return false;
		}

		if (isEatOp && !unlockService.canEatKey(key))
		{
			event.consume();
			explainAttempt("Locked: " + display
				+ " needs " + unlockService.lockReason(key) + " to eat.");
			return true;
		}

		if (isDrinkOp && !unlockService.canDrinkKey(key))
		{
			event.consume();
			explainAttempt("Locked: " + display
				+ " needs " + unlockService.lockReason(key) + " to drink.");
			return true;
		}

		if (isBuryOp && !unlockService.canBuryKey(key))
		{
			event.consume();
			explainAttempt("Locked: " + display
				+ " needs " + unlockService.lockReason(key) + " to bury.");
			return true;
		}

		if (isUseOp && !unlockService.canUseKey(key))
		{
			event.consume();
			explainAttempt("Locked: " + display
				+ " needs " + unlockService.lockReason(key) + ".");
			return true;
		}

		return false;
	}

	private String resolveSpellName(MenuOptionClicked event)
	{
		String option = Text.removeTags(event.getMenuOption()).trim();
		String target = Text.removeTags(event.getMenuTarget()).trim();
		String optionLower = option.toLowerCase();

		Widget widget = event.getWidget();
		boolean spellWidget = widget != null && isSpellWidget(widget.getId());

		if (spellWidget)
		{
			String fromWidget = normaliseSpellName(Text.removeTags(widget.getName()));
			if (fromWidget != null)
			{
				return fromWidget;
			}
		}

		if (optionLower.startsWith("cast"))
		{
			String fromTarget = spellFromMenuTarget(target);
			if (fromTarget != null)
			{
				return fromTarget;
			}
			if (option.length() > 4)
			{
				String fromOption = normaliseSpellName(option.substring(4).trim());
				if (fromOption != null)
				{
					return fromOption;
				}
			}
		}

		if (spellWidget || "autocast".equals(optionLower))
		{
			String fromOption = normaliseSpellName(option);
			if (fromOption != null)
			{
				return fromOption;
			}
			return spellFromMenuTarget(target);
		}

		return null;
	}

	/** Spell name from a menu option/target pair (handles {@code Wind Wave -> Goblin}). */
	private String resolveSpellFromMenu(String option, String target)
	{
		String fromTarget = spellFromMenuTarget(target);
		if (fromTarget != null)
		{
			return fromTarget;
		}

		if (option != null && option.toLowerCase().startsWith("cast") && option.length() > 4)
		{
			String fromOption = normaliseSpellName(option.substring(4).trim());
			if (fromOption != null)
			{
				return fromOption;
			}
		}

		return normaliseSpellName(option);
	}

	private String spellFromMenuTarget(String target)
	{
		if (target == null || target.isEmpty())
		{
			return null;
		}
		int arrow = target.indexOf(" -> ");
		String head = arrow >= 0 ? target.substring(0, arrow).trim() : target;
		return normaliseSpellName(head);
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

		if (config.disableGe())
		{
			event.consume();
			client.setGeSearchResultIndex(0);
			client.setGeSearchResultCount(0);
			client.setGeSearchResultIds(new short[0]);
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
			if (isGroundItemMenuAction(event.getMenuAction()) && isKnownItemId(itemId))
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
		if (isGroundItemMenuAction(event.getMenuAction()) && isKnownItemId(itemId))
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
		MenuAction action = MenuAction.of(event.getType());

		// Packed widget ids are (group << 16 | child), so anything smaller belongs to an
		// NPC, object or ground item rather than an item slot.
		if (widgetId < 1 << 16)
		{
			if (isGroundItemMenuAction(action))
			{
				int fromIdentifier = event.getIdentifier();
				return isKnownItemId(fromIdentifier) ? fromIdentifier : -1;
			}
			return -1;
		}

		Widget widget = client.getWidget(widgetId);
		if (widget == null)
		{
			if (isGroundItemMenuAction(action))
			{
				int fromIdentifier = event.getIdentifier();
				return isKnownItemId(fromIdentifier) ? fromIdentifier : -1;
			}
			return -1;
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

		if (isGroundItemMenuAction(action))
		{
			id = event.getIdentifier();
			if (isKnownItemId(id))
			{
				return id;
			}
		}

		return itemIdFromWidgetChain(widget);
	}

	private static boolean isGroundItemMenuAction(MenuAction action)
	{
		if (action == null)
		{
			return false;
		}
		switch (action)
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
			case EXAMINE_ITEM_GROUND:
			case WIDGET_TARGET_ON_GROUND_ITEM:
				return true;
			default:
				return false;
		}
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

	private void chatUnlock(String message)
	{
		chatColored(COL_GOOD, message);
	}

	private void chatColored(String color, String message)
	{
		if (!config.popupChat())
		{
			return;
		}
		client.addChatMessage(
			ChatMessageType.GAMEMESSAGE,
			"",
			COL_TAG + "[Leadman]" + COL_END + " " + color + message + COL_END,
			null);
	}

	private void chat(String message)
	{
		chatUnlock(message);
	}

	/** Chat feedback when the player examines or attempts a blocked action. */
	private void explainAttempt(String message)
	{
		if (!config.explainBlocks())
		{
			return;
		}
		client.addChatMessage(
			ChatMessageType.GAMEMESSAGE,
			"",
			COL_TAG + "[Leadman]" + COL_END + " " + COL_WARN + message + COL_END,
			null);
	}

	private static String pretty(Skill skill)
	{
		String raw = skill.name();
		return raw.charAt(0) + raw.substring(1).toLowerCase();
	}

}
