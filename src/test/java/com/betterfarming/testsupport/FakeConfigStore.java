package com.betterfarming.testsupport;

import com.betterfarming.state.ConfigStore;
import java.util.HashMap;
import java.util.Map;

public class FakeConfigStore implements ConfigStore
{
	private final Map<String, String> store = new HashMap<>();

	private int writeCount = 0;

	public int getWriteCount()
	{
		return writeCount;
	}

	@Override
	public String get(String group, String key)
	{
		return store.get(group + "." + key);
	}

	@Override
	public void set(String group, String key, String value)
	{
		store.put(group + "." + key, value);
		writeCount++;
	}

	public void putRaw(String group, String key, String value)
	{
		store.put(group + "." + key, value);
	}

	public String peek(String group, String key)
	{
		return store.get(group + "." + key);
	}
}
