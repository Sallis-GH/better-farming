package com.betterfarming.data.requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class RequirementEvaluator
{
	/**
	 * Returns the subset of `requirements` not satisfied by the given player state.
	 * Empty list → group is accessible. Order preserved from input (deterministic
	 * tooltip output).
	 *
	 * Logged-out PlayerState always returns an empty unmet list — accessibility
	 * evaluation is suppressed before login so the UI doesn't render misleading
	 * locks (Phase 1.5 spec, decision 2).
	 */
	public List<Requirement> unmet(List<Requirement> requirements, PlayerState state)
	{
		if (!state.loggedIn() || requirements.isEmpty())
		{
			return Collections.emptyList();
		}
		List<Requirement> out = new ArrayList<>();
		for (Requirement r : requirements)
		{
			if (!r.isMet(state))
			{
				out.add(r);
			}
		}
		return out;
	}
}
