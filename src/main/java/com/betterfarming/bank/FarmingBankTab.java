/*
 * Adapted from Zoinkwiz/quest-helper (QuestBankTab.java), BSD-2-Clause.
 * See src/main/resources/LICENSE-quest-helper for the full license text.
 *
 * Copyright (c) 2021, geheur <https://github.com/geheur>
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Ron Young <https://github.com/raiyni>
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * All rights reserved.
 */
package com.betterfarming.bank;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.ParamID;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

/**
 * Repaints the bank as a sectioned "farming run items" view while the custom
 * tab is active. Mechanism (see FarmingBankTabInterface for the button):
 * force the bank into a fake search state (BANKMAIN_SEARCHING return value +
 * "getSearchingTagTab" callback), let the vanilla build script lay out all
 * items flat, then after BANKMAIN_FINISHBUILDING hide the real item widgets
 * and recycle them into our own sectioned grid. Withdraw clicks are remapped
 * to the item's true bank slot in onMenuOptionClicked.
 *
 * Everything here runs on the client thread via script/menu events.
 */
@Singleton
@Slf4j
public class FarmingBankTab
{
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_VERTICAL_SPACING = 36;
	private static final int ITEM_HORIZONTAL_SPACING = 48;
	private static final int ITEM_ROW_START = 51;
	private static final int LINE_VERTICAL_SPACING = 5;
	private static final int LINE_HEIGHT = 2;
	private static final int TEXT_HEIGHT = 15;

	// Same values as net.runelite.client.plugins.banktags.BankTagsPlugin's
	// BANK_ITEM_* constants; inlined to avoid depending on that plugin class.
	private static final int BANK_ITEM_WIDTH = 36;
	private static final int BANK_ITEM_HEIGHT = 32;
	private static final int BANK_ITEM_X_PADDING = 12;
	private static final int BANK_ITEM_Y_PADDING = 4;

	private static final int CROSS_SPRITE_ID = SpriteID.Checkbox.CROSSED;
	private static final int TICK_SPRITE_ID = SpriteID.Checkbox.CHECKED;

	private final ArrayList<Widget> addedWidgets = new ArrayList<>();
	private final HashMap<Widget, BankTabItem> widgetItems = new HashMap<>();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private FarmingBankTabInterface tabInterface;

	/** Hand-wired in plugin startUp (RunItemsService is not Guice-managed). */
	private FarmingBankTagService bankTagService;

	private int originalContainerChildren = -1;
	private int currentWidgetToUse = 0;

	public void setBankTagService(FarmingBankTagService bankTagService)
	{
		this.bankTagService = bankTagService;
	}

	public void startUp()
	{
		clientThread.invokeLater(() -> {
			if (bankTagService != null && bankTagService.hasItems())
			{
				tabInterface.init();
			}
		});
	}

	public void shutDown()
	{
		clientThread.invokeLater(tabInterface::destroy);
		clientThread.invokeLater(this::removeAddedWidgets);
	}

	/** Relayout while the tab is open; call on the client thread. */
	public void refreshBankTab()
	{
		tabInterface.refreshTab();
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		int scriptId = event.getScriptId();
		if (scriptId == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			resetWidgets();
			if (tabInterface.isFarmingTabActive())
			{
				Widget bankTitle = client.getWidget(InterfaceID.Bankmain.TITLE);
				if (bankTitle != null)
				{
					bankTitle.setText("Tab <col=ff0000>Better Farming</col>");
				}
			}
		}
		else if (scriptId == ScriptID.BANKMAIN_SEARCH_TOGGLE)
		{
			tabInterface.handleSearch();
		}
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if ("getSearchingTagTab".equals(event.getEventName()))
		{
			client.getIntStack()[client.getIntStackSize() - 1] =
				tabInterface.isFarmingTabActive() ? 1 : 0;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN
			&& bankTagService != null && bankTagService.hasItems())
		{
			tabInterface.init();
		}
	}

	@Subscribe(priority = -1)
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		tabInterface.handleClick(event);

		// Update the widget index of the menu so withdraws work in our layout —
		// the clicked widget is no longer at its real bank slot index.
		if (event.getParam1() == InterfaceID.Bankmain.ITEMS && tabInterface.isFarmingTabActive())
		{
			MenuEntry menu = event.getMenuEntry();
			if ("Details".equals(menu.getOption()))
			{
				event.consume();
				Widget widget = event.getWidget();
				if (widget == null)
				{
					return;
				}
				BankTabItem bankTabItem = widgetItems.get(widget);
				if (bankTabItem == null)
				{
					return;
				}
				handleFakeItemClick(bankTabItem);
				return;
			}

			Widget w = menu.getWidget();
			if (w != null && w.getItemId() > -1)
			{
				ItemContainer bank = client.getItemContainer(InventoryID.BANK);
				int idx = bank == null ? -1 : bank.find(w.getItemId());
				if (idx > -1 && menu.getParam0() != idx)
				{
					menu.setParam0(idx);
				}
			}
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCHING)
		{
			// The return value of bankmain_searching is on the stack. If our
			// tab is active, make it return true to put the bank in a
			// searching state.
			if (tabInterface.isFarmingTabActive())
			{
				client.getIntStack()[client.getIntStackSize() - 1] = 1;
			}
			return;
		}

		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING)
		{
			return;
		}

		removeAddedWidgets();

		if (!tabInterface.isFarmingTabActive() || bankTagService == null)
		{
			return;
		}

		Widget itemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (itemContainer == null)
		{
			return;
		}
		Widget[] children = itemContainer.getChildren();
		if (children != null && originalContainerChildren == -1)
		{
			originalContainerChildren = children.length;
		}

		Widget[] containerChildren = itemContainer.getDynamicChildren();
		// invokeAtTickEnd so other plugins' post-build handlers (e.g. Bank
		// Tags) run before we repaint.
		clientThread.invokeAtTickEnd(() -> {
			List<BankTabItems> tabLayout = bankTagService.buildSections();
			sortBankTabItems(itemContainer, containerChildren, tabLayout);
		});
	}

	private void sortBankTabItems(Widget itemContainer, Widget[] containerChildren, List<BankTabItems> newLayout)
	{
		int totalSectionsHeight = 0;
		currentWidgetToUse = 0;

		widgetItems.clear();
		hideBankWidgets(itemContainer, containerChildren);

		List<BankText> bankItemTexts = new ArrayList<>();

		addRemainingBankTab(newLayout);

		for (BankTabItems bankTabItems : newLayout)
		{
			totalSectionsHeight = addSection(itemContainer, bankTabItems, totalSectionsHeight, bankItemTexts);
		}

		// Texts added after all items so they always overlay them.
		for (BankText bankText : bankItemTexts)
		{
			addedWidgets.add(createText(itemContainer, bankText.text, Color.WHITE.getRGB(),
				ITEM_HORIZONTAL_SPACING, TEXT_HEIGHT - 3, bankText.x, bankText.y));

			if (bankText.spriteId != -1)
			{
				addedWidgets.add(createIcon(itemContainer, bankText.spriteId,
					bankText.spriteX, bankText.spriteY));
			}
		}

		itemContainer.setScrollHeight(Math.max(totalSectionsHeight, itemContainer.getHeight()));
		final int itemContainerScroll = itemContainer.getScrollY();
		clientThread.invokeLater(() ->
			client.runScript(ScriptID.UPDATE_SCROLLBAR,
				InterfaceID.Bankmain.SCROLLBAR,
				InterfaceID.Bankmain.ITEMS,
				itemContainerScroll));
	}

	/** Final section with every bank item not already shown, so the user keeps access to everything. */
	private void addRemainingBankTab(List<BankTabItems> newLayout)
	{
		BankTabItems leftoverTab = new BankTabItems("Other items");
		ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
		if (bankContainer == null)
		{
			newLayout.add(leftoverTab);
			return;
		}

		Set<Integer> usedIds = new LinkedHashSet<>();
		for (BankTabItems tab : newLayout)
		{
			for (BankTabItem item : tab.getItems())
			{
				usedIds.addAll(item.itemIds());
			}
			for (BankTabItem item : tab.getRecommendedItems())
			{
				usedIds.addAll(item.itemIds());
			}
		}

		Set<Integer> remaining = new LinkedHashSet<>();
		for (Item item : bankContainer.getItems())
		{
			if (item.getId() > -1 && !usedIds.contains(item.getId()))
			{
				remaining.add(item.getId());
			}
		}

		for (Integer id : remaining)
		{
			String name = client.getItemDefinition(id).getName();
			leftoverTab.addItem(new BankTabItem(name, List.of(id), 0, true));
		}
		newLayout.add(leftoverTab);
	}

	private void resetWidgets()
	{
		// We adjust the bank item container children's sizes in layouts, but
		// they are only initially set when the bank is opened, so we have to
		// reset them each time the bank is rebuilt.
		Widget w = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (w == null || w.getChildren() == null)
		{
			return;
		}

		for (Widget c : w.getChildren())
		{
			if (c.getOriginalHeight() < BANK_ITEM_HEIGHT)
			{
				break;
			}
			if (c.getOriginalWidth() != BANK_ITEM_WIDTH || c.getOriginalHeight() != BANK_ITEM_HEIGHT)
			{
				c.setOriginalWidth(BANK_ITEM_WIDTH);
				c.setOriginalHeight(BANK_ITEM_HEIGHT);
				c.revalidate();
			}
		}
	}

	private void removeAddedWidgets()
	{
		if (originalContainerChildren == -1 || addedWidgets.isEmpty())
		{
			return;
		}
		Widget parent = addedWidgets.get(0).getParent();
		if (parent == null || parent.getChildren() == null)
		{
			return;
		}
		parent.setChildren(Arrays.copyOf(parent.getChildren(), originalContainerChildren));
		parent.revalidate();
		addedWidgets.clear();
	}

	private void hideBankWidgets(Widget itemContainer, Widget[] containerChildren)
	{
		for (int i = 0; i < containerChildren.length; ++i)
		{
			Widget widget = itemContainer.getChild(i);
			if (widget == null)
			{
				continue;
			}

			// ~bankmain_drawitem uses BLANKOBJECT for empty item slots
			if (!widget.isSelfHidden()
				&& (widget.getItemId() > -1 && widget.getItemId() != ItemID.BLANKOBJECT)
				|| (widget.getSpriteId() == SpriteID.TRADEBACKING_DARK || widget.getText().contains("Tab")))
			{
				widget.setHidden(true);
			}
		}
	}

	private int addSection(Widget itemContainer, BankTabItems items, int totalSectionsHeight, List<BankText> bankItemTexts)
	{
		if (items == null || (items.getItems().isEmpty() && items.getRecommendedItems().isEmpty()))
		{
			return totalSectionsHeight;
		}

		int newHeight = addSectionHeader(itemContainer, items.getName(), totalSectionsHeight);

		if (!items.getItems().isEmpty())
		{
			newHeight = createPartialSection(items.getItems(), newHeight, bankItemTexts);
		}

		if (!items.getRecommendedItems().isEmpty())
		{
			newHeight = addSubSectionHeader(itemContainer, "Recommended", newHeight);
			newHeight = createPartialSection(items.getRecommendedItems(), newHeight, bankItemTexts);
		}

		return newHeight;
	}

	private int createPartialSection(List<BankTabItem> items, int totalSectionsHeight, List<BankText> bankItemTexts)
	{
		int totalItemsAdded = 0;

		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null)
		{
			return totalSectionsHeight;
		}
		Widget bankItemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (bankItemContainer == null)
		{
			return totalSectionsHeight;
		}

		for (BankTabItem item : items)
		{
			int itemId = displayId(bank, item);
			if (itemId == -1)
			{
				continue;
			}

			Widget c = bankItemContainer.getChild(currentWidgetToUse);
			if (c == null)
			{
				return totalSectionsHeight;
			}
			drawItem(c, itemId, bank, item);
			placeItem(c, totalItemsAdded, totalSectionsHeight);

			if (item.quantity() > 0)
			{
				makeBankText(c.getItemQuantity(), item.quantity(), item.satisfied(),
					c.getOriginalX(), c.getOriginalY(), bankItemTexts);
			}

			currentWidgetToUse++;
			totalItemsAdded++;
		}

		int newHeight = totalSectionsHeight + (totalItemsAdded / ITEMS_PER_ROW) * ITEM_VERTICAL_SPACING;
		return totalItemsAdded % ITEMS_PER_ROW != 0 ? newHeight + ITEM_VERTICAL_SPACING : newHeight;
	}

	/** Preferred display id: the first variant present in the bank, else the first variant. */
	private static int displayId(ItemContainer bank, BankTabItem item)
	{
		if (item.itemIds().isEmpty())
		{
			return -1;
		}
		for (Integer id : item.itemIds())
		{
			if (bank.count(id) > 0)
			{
				return id;
			}
		}
		return item.itemIds().get(0);
	}

	private void drawItem(Widget c, int item, ItemContainer bank, BankTabItem bankTabItem)
	{
		if (item > -1 && item != ItemID.BANK_FILLER)
		{
			int qty = bank.count(item);
			ItemComposition def = client.getItemDefinition(item);

			c.setItemId(item);
			c.setItemQuantity(qty);
			c.setItemQuantityMode(ItemQuantityMode.ALWAYS);

			// Effectively avoid dragging — rearranging would corrupt our layout.
			c.setDragDeadTime(1000);

			c.setName("<col=ff9040>" + def.getName() + "</col>");
			c.clearActions();

			// Jagex placeholder
			if (def.getPlaceholderTemplateId() >= 0 && def.getPlaceholderId() >= 0)
			{
				c.setItemQuantity(qty);
				c.setOpacity(120);
				c.setAction(8 - 1, "Release");
				c.setAction(10 - 1, "Examine");
			}
			// Layout placeholder — item not in bank at all
			else if (qty == 0)
			{
				c.setOpacity(120);
				c.setItemQuantity(0);
				c.setItemQuantityMode(1);
				c.setText("<col=ff9040>" + bankTabItem.text() + "</col>");
				c.setAction(1, "Details");
			}
			else
			{
				int quantityType = client.getVarbitValue(VarbitID.BANK_QUANTITY_TYPE);
				int requestQty = client.getVarbitValue(VarbitID.BANK_REQUESTEDQUANTITY);
				// ~script2759
				String suffix;
				switch (quantityType)
				{
					default:
						suffix = "1";
						break;
					case 1:
						suffix = "5";
						break;
					case 2:
						suffix = "10";
						break;
					case 3:
						suffix = Integer.toString(Math.max(1, requestQty));
						break;
					case 4:
						suffix = "All";
						break;
				}
				// ~script669
				c.setAction(0, "Withdraw-" + suffix);
				if (quantityType != 0)
				{
					c.setAction(1, "Withdraw-1");
				}
				c.setAction(2, "Withdraw-5");
				c.setAction(3, "Withdraw-10");
				if (requestQty > 0)
				{
					c.setAction(4, "Withdraw-" + requestQty);
				}
				c.setAction(5, "Withdraw-X");
				c.setAction(6, "Withdraw-All");
				c.setAction(7, "Withdraw-All-but-1");
				if (client.getVarbitValue(VarbitID.BANK_BANKOPS_TOGGLE_ON) == 1
					&& def.getIntValue(ParamID.BANK_AUTOCHARGE) != -1)
				{
					c.setAction(8, "Configure-Charges");
				}
				if (client.getVarbitValue(VarbitID.BANK_LEAVEPLACEHOLDERS) == 0)
				{
					c.setAction(9, "Placeholder");
				}
				c.setAction(10, "Examine");
				c.setOpacity(0);
			}

			c.setOnDragListener(ScriptID.BANKMAIN_DRAGSCROLL, ScriptEvent.WIDGET_ID, ScriptEvent.WIDGET_INDEX,
				ScriptEvent.MOUSE_X, ScriptEvent.MOUSE_Y, InterfaceID.Bankmain.SCROLLBAR, 0);
			c.setOnDragCompleteListener((JavaScriptCallback) ev -> { });
		}
		else
		{
			// pad size to not leave a gap between items
			c.setOriginalWidth(BANK_ITEM_WIDTH + BANK_ITEM_X_PADDING);
			c.setOriginalHeight(BANK_ITEM_HEIGHT + BANK_ITEM_Y_PADDING);
			c.clearActions();
			c.setItemId(-1);
			c.setItemQuantity(0);
			c.setOnDragListener((Object[]) null);
			c.setOnDragCompleteListener((Object[]) null);
		}
		widgetItems.put(c, bankTabItem);
		c.setHidden(false);
		c.revalidate();
	}

	private void makeBankText(int currentQuantity, int goalQuantity, boolean satisfied,
		int baseX, int baseY, List<BankText> bankItemTexts)
	{
		// Single-item goals (tools, outfit pieces) just get the tick/cross —
		// a "1 / 1" overlay is noise.
		if (goalQuantity == 1)
		{
			bankItemTexts.add(new BankText("", baseX, baseY - 1,
				satisfied ? TICK_SPRITE_ID : CROSS_SPRITE_ID, baseX + 2, baseY + 9));
			return;
		}
		String quantityString = QuantityFormatter.quantityToStackSize(goalQuantity);
		int requirementLength = (int) Math.round(quantityString.length() * 5.5);
		int extraLength = QuantityFormatter.quantityToStackSize(currentQuantity).length() * 6;

		int xPos = baseX + 2 + extraLength;
		int yPos = baseY - 1;
		if (extraLength + requirementLength > 20)
		{
			xPos = baseX;
			yPos = baseY + 9;
		}

		int spritePosX = xPos + requirementLength + 10;
		int spritePosY = yPos;
		// If the goal quantity moved down a line, put tick/cross after the current quantity
		if (yPos != baseY - 1)
		{
			spritePosX = baseX + 2 + extraLength;
			spritePosY = baseY - 1;
		}

		bankItemTexts.add(new BankText("/ " + quantityString, xPos, yPos,
			satisfied ? TICK_SPRITE_ID : CROSS_SPRITE_ID, spritePosX, spritePosY));
	}

	private int addSubSectionHeader(Widget itemContainer, String title, int totalSectionsHeight)
	{
		addedWidgets.add(createText(itemContainer, title, new Color(228, 216, 162).getRGB(),
			(ITEMS_PER_ROW * ITEM_HORIZONTAL_SPACING) + ITEM_ROW_START,
			TEXT_HEIGHT, ITEM_ROW_START, totalSectionsHeight + LINE_VERTICAL_SPACING));

		return totalSectionsHeight + LINE_VERTICAL_SPACING + TEXT_HEIGHT;
	}

	private int addSectionHeader(Widget itemContainer, String title, int totalSectionsHeight)
	{
		addedWidgets.add(createDividerLine(itemContainer, ITEM_ROW_START, totalSectionsHeight));
		addedWidgets.add(createText(itemContainer, title, new Color(228, 216, 162).getRGB(),
			(ITEMS_PER_ROW * ITEM_HORIZONTAL_SPACING) + ITEM_ROW_START,
			TEXT_HEIGHT, ITEM_ROW_START, totalSectionsHeight + LINE_VERTICAL_SPACING));

		return totalSectionsHeight + LINE_VERTICAL_SPACING + TEXT_HEIGHT;
	}

	private void placeItem(Widget widget, int totalItemsAdded, int totalSectionsHeight)
	{
		int adjYOffset = totalSectionsHeight + (totalItemsAdded / ITEMS_PER_ROW) * ITEM_VERTICAL_SPACING;
		int adjXOffset = (totalItemsAdded % ITEMS_PER_ROW) * ITEM_HORIZONTAL_SPACING + ITEM_ROW_START;

		if (widget.getOriginalY() != adjYOffset)
		{
			widget.setOriginalY(adjYOffset);
			widget.revalidate();
		}
		if (widget.getOriginalX() != adjXOffset)
		{
			widget.setOriginalX(adjXOffset);
			widget.revalidate();
		}
	}

	private void handleFakeItemClick(BankTabItem bankTabItem)
	{
		String quantity = bankTabItem.quantity() > 0
			? QuantityFormatter.formatNumber(bankTabItem.quantity()) + " x "
			: "some ";
		final ChatMessageBuilder message = new ChatMessageBuilder()
			.append("You need ")
			.append(ChatColorType.HIGHLIGHT)
			.append(quantity)
			.append(Text.removeTags(bankTabItem.text()))
			.append(".");

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.ITEM_EXAMINE)
			.runeLiteFormattedMessage(message.build())
			.build());
	}

	private Widget createDividerLine(Widget container, int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(ITEMS_PER_ROW * ITEM_HORIZONTAL_SPACING);
		widget.setOriginalHeight(LINE_HEIGHT);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setSpriteId(SpriteID.TRADEBACKING_DARK);
		widget.revalidate();
		return widget;
	}

	private Widget createText(Widget container, String text, int color, int width, int height, int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setText(text);
		widget.setFontId(FontID.PLAIN_11);
		widget.setTextColor(color);
		widget.setTextShadowed(true);
		widget.revalidate();
		return widget;
	}

	private Widget createIcon(Widget container, int spriteId, int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(10);
		widget.setOriginalHeight(10);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setSpriteId(spriteId);
		widget.revalidate();
		return widget;
	}
}
