package com.betterfarming.travel;

// Adapted from Skretzo/shortest-path (BSD-2-Clause) VarRequirement/VarCheckType,
// see resources/transports/LICENSE-shortest-path

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * One varbit or varplayer condition on a teleport, e.g. "varbit 4070 = 0"
 * (standard spellbook) or "varbit 6069 &lt; 3" (Ardougne cloak daily charges).
 */
@Value
@Accessors(fluent = true)
public class VarCheck
{
	public enum VarType
	{
		VARBIT,
		VARPLAYER
	}

	public enum Op
	{
		BIT_SET("&"),
		COOLDOWN_MINUTES("@"),
		EQUAL("="),
		GREATER(">"),
		SMALLER("<");

		private final String code;

		Op(String code)
		{
			this.code = code;
		}

		public String code()
		{
			return code;
		}
	}

	VarType varType;
	int id;
	int value;
	Op op;

	public boolean satisfiedBy(int currentValue)
	{
		switch (op)
		{
			case EQUAL:
				return currentValue == value;
			case GREATER:
				return currentValue > value;
			case SMALLER:
				return currentValue < value;
			case BIT_SET:
				return (currentValue & value) > 0;
			case COOLDOWN_MINUTES:
				return ((System.currentTimeMillis() / 60000) - currentValue) > value;
			default:
				return false;
		}
	}
}
