package com.betterfarming.bank;

import com.betterfarming.item.ItemTracker;
import com.betterfarming.item.RunItem;
import com.betterfarming.item.RunItemsService;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the run-items list into the bank tab's section layout:
 * Tools (with recommended extras), Seeds & saplings, Payments. The renderer
 * appends a final "Other items" section itself from live bank contents.
 *
 * satisfied is computed here from ItemTracker rather than reusing
 * RunItem.status so the value is fresh at layout time on the client thread
 * (RunItemsService recomputes via an EDT hop, which may not have run yet
 * when the bank rebuilds).
 */
public class FarmingBankTagService
{
	private final RunItemsService runItemsService;
	private final ItemTracker itemTracker;

	public FarmingBankTagService(RunItemsService runItemsService, ItemTracker itemTracker)
	{
		this.runItemsService = runItemsService;
		this.itemTracker = itemTracker;
	}

	public boolean hasItems()
	{
		return !runItemsService.items().isEmpty();
	}

	public List<BankTabItems> buildSections()
	{
		BankTabItems tools = new BankTabItems("Tools");
		BankTabItems plantables = new BankTabItems("Seeds & saplings");
		BankTabItems payments = new BankTabItems("Payments");

		for (RunItem item : runItemsService.items())
		{
			BankTabItem tabItem = toTabItem(item);
			switch (item.category())
			{
				case TOOL:
					if (item.recommended())
					{
						tools.addRecommended(tabItem);
					}
					else
					{
						tools.addItem(tabItem);
					}
					break;
				case PLANTABLE:
					plantables.addItem(tabItem);
					break;
				case PAYMENT:
					payments.addItem(tabItem);
					break;
			}
		}

		List<BankTabItems> sections = new ArrayList<>();
		sections.add(tools);
		sections.add(plantables);
		sections.add(payments);
		return sections;
	}

	private BankTabItem toTabItem(RunItem item)
	{
		boolean satisfied = itemTracker.countOnPlayer(item.itemIds())
			+ itemTracker.countBanked(item.itemIds()) >= item.quantity();
		return new BankTabItem(item.displayName(), new ArrayList<>(item.itemIds()),
			item.quantity(), satisfied);
	}
}
