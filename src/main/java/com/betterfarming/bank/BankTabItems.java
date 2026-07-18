package com.betterfarming.bank;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * One section of the farming bank tab: a header name, required items, and an
 * optional "Recommended" sub-list rendered beneath its own sub-header.
 */
@Getter
public class BankTabItems
{
	private final String name;
	private final List<BankTabItem> items = new ArrayList<>();
	private final List<BankTabItem> recommendedItems = new ArrayList<>();

	public BankTabItems(String name)
	{
		this.name = name;
	}

	public void addItem(BankTabItem item)
	{
		items.add(item);
	}

	public void addRecommended(BankTabItem item)
	{
		recommendedItems.add(item);
	}
}
