package com.betterfarming.state;

/**
 * Narrow facade over RuneLite's ConfigManager for the two operations
 * PatchSelectionService needs. Lets tests substitute an in-memory fake
 * without subclassing the real ConfigManager (which is fragile across
 * RuneLite versions).
 */
public interface ConfigStore
{
	String get(String group, String key);

	void set(String group, String key, String value);
}
