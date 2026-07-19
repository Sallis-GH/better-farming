package com.betterfarming.bank;

import com.betterfarming.item.ItemTracker;
import com.betterfarming.item.OutfitPiece;
import com.betterfarming.item.RunItem;
import com.betterfarming.item.RunItemsService;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the run-items list into the bank tab's section layout: Tools (with
 * recommended extras), Seeds &amp; saplings, Payments, Teleports, then one
 * section per outfit (Graceful, Farming outfit) with one slot per piece.
 * The renderer appends a final "Other items" section itself from live bank
 * contents.
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
		BankTabItems teleports = new BankTabItems("Teleports");
		List<BankTabItems> outfits = new ArrayList<>();

		for (RunItem item : runItemsService.items())
		{
			switch (item.category())
			{
				case TOOL:
					if (item.recommended())
					{
						tools.addRecommended(toTabItem(item));
					}
					else
					{
						tools.addItem(toTabItem(item));
					}
					break;
				case PLANTABLE:
					plantables.addItem(toTabItem(item));
					break;
				case PAYMENT:
					payments.addItem(toTabItem(item));
					break;
				case TELEPORT:
					teleports.addItem(toTabItem(item));
					break;
				case GEAR:
					// One section per outfit, one slot per piece.
					BankTabItems outfitSection = new BankTabItems(item.displayName());
					for (OutfitPiece piece : item.pieces())
					{
						boolean satisfied = itemTracker.countOnPlayer(piece.ids())
							+ itemTracker.countBanked(piece.ids()) > 0;
						outfitSection.addItem(new BankTabItem(piece.name(),
							new ArrayList<>(piece.ids()), 1, satisfied));
					}
					outfits.add(outfitSection);
					break;
			}
		}

		List<BankTabItems> sections = new ArrayList<>();
		sections.add(tools);
		sections.add(plantables);
		sections.add(payments);
		sections.add(teleports);
		sections.addAll(outfits);
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
