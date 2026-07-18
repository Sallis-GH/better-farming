/*
 * Adapted from Zoinkwiz/quest-helper (QuestBankTabInterface.java), BSD-2-Clause.
 * See src/main/resources/LICENSE-quest-helper for the full license text.
 *
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * Copyright (c) 2018, Ron Young <https://github.com/raiyni>
 * All rights reserved.
 */
package com.betterfarming.bank;

import javax.inject.Inject;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.bank.BankSearch;

/**
 * The "Farming items" button overlaid on the bank interface. Toggling it puts
 * the bank into a fake search state that FarmingBankTab repaints with the run
 * items layout. All methods must run on the client thread.
 */
public class FarmingBankTabInterface
{
	private static final String VIEW_TAB = "View tab ";
	static final String BUTTON_NAME = "better-farming";

	private static final int BANKTAB_POTIONSTORE = 15;

	@Getter
	private boolean farmingTabActive = false;

	private Widget parent;
	private Widget iconWidget;
	private Widget backgroundWidget;

	private final Client client;
	private final ClientThread clientThread;
	private final BankSearch bankSearch;

	@Inject
	public FarmingBankTabInterface(Client client, ClientThread clientThread, BankSearch bankSearch)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.bankSearch = bankSearch;
	}

	public void init()
	{
		if (isHidden())
		{
			return;
		}

		parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);

		final int buttonSize = 25;
		final int buttonX = 408;
		final int buttonY = 5;
		backgroundWidget = createGraphic(BUTTON_NAME, SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL,
			buttonSize, buttonSize, buttonX, buttonY);
		backgroundWidget.setAction(1, VIEW_TAB);
		backgroundWidget.setOnOpListener((JavaScriptCallback) this::handleTagTab);

		iconWidget = createGraphic("", net.runelite.api.SpriteID.SKILL_FARMING,
			buttonSize - 6, buttonSize - 6, buttonX + 3, buttonY + 3);

		// Re-activate after an interface reload (e.g. bank closed and reopened
		// while the tab was selected).
		if (farmingTabActive)
		{
			boolean wasInPotionStorage = client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == BANKTAB_POTIONSTORE;
			farmingTabActive = false;
			clientThread.invokeLater(() -> activateTab(wasInPotionStorage));
		}
	}

	public void destroy()
	{
		if (farmingTabActive)
		{
			closeTab();
			bankSearch.reset(true);
		}

		parent = null;

		if (iconWidget != null)
		{
			iconWidget.setHidden(true);
		}
		if (backgroundWidget != null)
		{
			backgroundWidget.setHidden(true);
		}
		farmingTabActive = false;
	}

	public void handleClick(MenuOptionClicked event)
	{
		if (isHidden())
		{
			return;
		}
		String menuOption = event.getMenuOption();

		// Clicking any real tab, tag tab, or the potion store closes ours.
		boolean clickedTabTag = menuOption.startsWith("View tab") && !event.getMenuTarget().equals(BUTTON_NAME);
		boolean clickedPotionStorage = menuOption.startsWith("Potion store");
		boolean clickedOtherTab = menuOption.equals("View all items") || menuOption.startsWith("View tag tab");
		if (farmingTabActive && (clickedTabTag || clickedOtherTab || clickedPotionStorage))
		{
			closeTab();
		}
	}

	public void handleSearch()
	{
		if (farmingTabActive)
		{
			closeTab();
			// Ensures that clicking Search while our tab is selected opens the
			// search input rather than the client trying to close it first.
			client.setVarcStrValue(VarClientID.MESLAYERINPUT, "");
			client.setVarcIntValue(VarClientID.MESLAYERMODE, 0);
		}
	}

	public boolean isHidden()
	{
		Widget widget = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return widget == null || widget.isHidden();
	}

	private void handleTagTab(ScriptEvent event)
	{
		if (event.getOp() == 2)
		{
			boolean wasInPotionStorage = client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == BANKTAB_POTIONSTORE;
			client.setVarbit(VarbitID.BANK_CURRENTTAB, 0);

			if (farmingTabActive)
			{
				closeTab();
				bankSearch.reset(true);
			}
			else
			{
				activateTab(wasInPotionStorage);
			}

			client.playSoundEffect(SoundEffectID.UI_BOOP);
		}
	}

	public void closeTab()
	{
		farmingTabActive = false;
		if (backgroundWidget != null)
		{
			backgroundWidget.setSpriteId(SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL);
			backgroundWidget.revalidate();
		}
	}

	public void refreshTab()
	{
		if (!farmingTabActive)
		{
			return;
		}

		client.setVarbit(VarbitID.BANK_CURRENTTAB, 0);
		bankSearch.reset(true); // clear search dialog & relayout bank
		fixSearchButton();
	}

	private void activateTab(boolean wasInPotionStorage)
	{
		if (farmingTabActive)
		{
			return;
		}

		if (wasInPotionStorage)
		{
			// Opening a tag tab with the potion store open would leave the store
			// open in the background, making deposits not work. Force close it.
			client.menuAction(-1, InterfaceID.Bankmain.POTIONSTORE_BUTTON, MenuAction.CC_OP, 1, -1, "Potion store", "");
		}

		backgroundWidget.setSpriteId(SpriteID.Miscgraphics3.UNKNOWN_BUTTON_SQUARE_SMALL_SELECTED);
		backgroundWidget.revalidate();
		farmingTabActive = true;

		bankSearch.reset(true); // clear search dialog & relayout bank
		fixSearchButton();
	}

	/**
	 * When searching, the search button has an on-timer script that detects
	 * search end, resets the background sprite and removes the timer. Going
	 * from a real search into our fake search would re-add it instead, so
	 * remove the timer and reset the sprite ourselves — same behaviour as
	 * bankmain_search_setbutton.
	 */
	private void fixSearchButton()
	{
		Widget searchButtonBackground = client.getWidget(InterfaceID.Bankmain.SEARCH);
		if (searchButtonBackground != null)
		{
			searchButtonBackground.setOnTimerListener((Object[]) null);
			searchButtonBackground.setSpriteId(SpriteID.Miscgraphics.EQUIPMENT_SLOT_TILE);
		}
	}

	private Widget createGraphic(String name, int spriteId, int width, int height, int x, int y)
	{
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setOriginalX(x);
		widget.setOriginalY(y);

		widget.setSpriteId(spriteId);
		widget.setOnOpListener(ScriptID.NULL);
		widget.setHasListener(true);
		widget.setName(name);
		widget.revalidate();

		return widget;
	}
}
