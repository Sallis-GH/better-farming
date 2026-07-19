package com.betterfarming.travel;

// Availability rules adapted from Skretzo/shortest-path (BSD-2-Clause)
// pathfinder.PathfinderConfig, see resources/transports/LICENSE-shortest-path

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.JewelleryBoxTier;
import com.betterfarming.item.ItemTracker;
import com.betterfarming.ui.ClientLevelSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Filters the loaded teleport edges down to the ones this account can use
 * right now: quest state, boosted skill levels, varbit/varplayer values, and
 * item possession (inventory + equipment, optionally bank).
 *
 * Type-level gates mirror shortest-path's PathfinderConfig: fairy rings need
 * Fairytale II progress (varbit FAIRY2_QUEENCURE_QUEST > 39) plus a dramen or
 * lunar staff unless the Lumbridge Elite diary is done; spirit trees need
 * Tree Gnome Village; gliders The Grand Tree; mushtrees Bone Voyage. POH
 * variants and player-grown spirit trees sit behind config toggles because
 * house layout and planted trees aren't readable from static state.
 *
 * VarbitChanged fires very frequently, so recomputation is deferred to the
 * next GameTick via a dirty flag rather than running per event.
 */
@Slf4j
public class TeleportAvailabilityService
{
	// The DRAMEN_STAFF variation includes the lunar staff.
	private static final TeleportItemRequirement DRAMEN_OR_LUNAR_STAFF =
		new TeleportItemRequirement(
			ItemVariations.DRAMEN_STAFF.getIds(), new int[0], new int[0], 1, "Dramen staff");

	private final List<Teleport> allTeleports;
	private final ClientLevelSource client;
	private final ItemTracker itemTracker;
	private final BetterFarmingConfig config;

	private final Set<Runnable> listeners = new LinkedHashSet<>();
	private List<Teleport> available = Collections.emptyList();
	private boolean dirty = true;

	public TeleportAvailabilityService(List<Teleport> allTeleports,
		ClientLevelSource client, ItemTracker itemTracker, BetterFarmingConfig config)
	{
		this.allTeleports = allTeleports;
		this.client = client;
		this.itemTracker = itemTracker;
		this.config = config;
		itemTracker.addListener(this::markDirty);
	}

	// ── public API ──

	/** Teleports usable now (items may come from the bank when configured). */
	public List<Teleport> available()
	{
		return available;
	}

	public void addListener(Runnable l)    { listeners.add(l); }
	public void removeListener(Runnable l) { listeners.remove(l); }

	// ── event subscriptions ──

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN
			|| event.getGameState() == GameState.LOGIN_SCREEN)
		{
			markDirty();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		dirty = true;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (dirty)
		{
			dirty = false;
			refresh();
		}
	}

	private void markDirty()
	{
		dirty = true;
	}

	/** Recompute now (client thread). Fires listeners when the set changed. */
	public void refresh()
	{
		List<Teleport> next = compute();
		if (next.equals(available))
		{
			return;
		}
		available = next;
		for (Runnable l : new ArrayList<>(listeners))
		{
			try
			{
				l.run();
			}
			catch (Exception | AssertionError ex)
			{
				log.warn("Better Farming: teleport listener {} threw", l.getClass().getName(), ex);
			}
		}
	}

	private List<Teleport> compute()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return Collections.emptyList();
		}

		boolean fairyRingsUnlocked =
			client.getVarbitValue(VarbitID.FAIRY2_QUEENCURE_QUEST) > 39;
		boolean fairyRingsNeedStaff =
			client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1;
		boolean spiritTreesUnlocked = isFinished(Quest.TREE_GNOME_VILLAGE);
		boolean glidersUnlocked = isFinished(Quest.THE_GRAND_TREE);
		boolean mushtreesUnlocked = isFinished(Quest.BONE_VOYAGE);

		// The free home teleport's 30-minute cooldown needs no special code:
		// the vendored rows carry a "892@30" varplayer check (VarCheck
		// COOLDOWN_MINUTES against LAST_HOME_TELEPORT), evaluated with every
		// other var condition below. Note the spell goes to the spellbook's
		// home/respawn area — it never enters the POH; "Teleport to House"
		// (runes/tab) is a different spell entirely.

		Map<Integer, Integer> varCache = new HashMap<>();
		Map<Quest, QuestState> questCache = new HashMap<>();
		List<Teleport> out = new ArrayList<>();
		// House-entry teleports (destination inside the POH) and enabled
		// POH-origin facility edges, composed into house-chain edges below.
		List<Teleport> houseEntries = new ArrayList<>();
		List<Teleport> pohFacilities = new ArrayList<>();

		for (Teleport t : allTeleports)
		{
			switch (t.type())
			{
				case FAIRY_RING:
					if (!fairyRingsUnlocked
						|| (fairyRingsNeedStaff && !hasItems(DRAMEN_OR_LUNAR_STAFF)))
					{
						continue;
					}
					if (t.originInPoh() && !config.pohFairyRing())
					{
						continue;
					}
					break;
				case SPIRIT_TREE:
					if (!spiritTreesUnlocked)
					{
						continue;
					}
					if (t.originInPoh() && !config.pohSpiritTree())
					{
						continue;
					}
					break;
				case GNOME_GLIDER:
					if (!glidersUnlocked)
					{
						continue;
					}
					break;
				case MUSHTREE:
					if (!mushtreesUnlocked)
					{
						continue;
					}
					break;
				case POH_PORTAL:
				{
					PohPortal portal = PohPortal.forDisplayInfo(t.displayInfo());
					if (portal == null || !portal.isEnabled(config))
					{
						continue;
					}
					break;
				}
				case JEWELLERY_BOX:
					if (!hasJewelleryFacility(t.objectInfo()))
					{
						continue;
					}
					break;
				default:
					break;
			}
			if (!isUsable(t, varCache, questCache))
			{
				continue;
			}
			if (t.destinationInPoh())
			{
				// Farm patches are never inside the house: only useful as the
				// first hop of a house chain.
				houseEntries.add(t);
			}
			else if (t.originInPoh())
			{
				pohFacilities.add(t);
			}
			else
			{
				out.add(t);
			}
		}

		out.addAll(composeHouseChains(houseEntries, pohFacilities));
		return out;
	}

	/**
	 * A POH facility is only reachable after teleporting home, so expose each
	 * (house entry × facility) pair as one anywhere-usable edge whose cost and
	 * requirements combine both hops plus the walk between them.
	 */
	private static List<Teleport> composeHouseChains(
		List<Teleport> houseEntries, List<Teleport> pohFacilities)
	{
		List<Teleport> out = new ArrayList<>();
		for (Teleport entry : houseEntries)
		{
			for (Teleport facility : pohFacilities)
			{
				int walk = (int) Math.ceil(
					RoutePlanner.walkTicks(entry.destination(), facility.origin()));
				Map<Skill, Integer> skills = new HashMap<>(entry.skillLevels());
				for (Map.Entry<Skill, Integer> e : facility.skillLevels().entrySet())
				{
					skills.merge(e.getKey(), e.getValue(), Math::max);
				}
				Set<Quest> quests = new HashSet<>(entry.quests());
				quests.addAll(facility.quests());
				Set<VarCheck> varChecks = new LinkedHashSet<>(entry.varChecks());
				varChecks.addAll(facility.varChecks());
				List<TeleportItemRequirement> items = new ArrayList<>(entry.items());
				items.addAll(facility.items());
				String display = entry.displayInfo() + " → "
					+ (facility.displayInfo() != null ? facility.displayInfo()
						: facility.type().name());
				// The entry's origin is preserved: the free walk-in house
				// portal (teleportation_portals.tsv "Home Portal" rows) is an
				// entry too, and dropping its origin would let the planner
				// treat "walk into your house" as a zero-item teleport usable
				// from anywhere — starving rune/tab entries out of every plan.
				out.add(new Teleport(facility.type(), entry.origin(), facility.destination(),
					entry.durationTicks() + walk + facility.durationTicks(),
					display, skills, quests, varChecks, items,
					entry.consumable() || facility.consumable(), null, true,
					// Generic propagation; house entries are Teleport to House
					// variants (runes/tab/cape/walk-in portal), never the free
					// home teleport, so this is false today.
					entry.oncePerRun() || facility.oncePerRun()));
			}
		}
		return out;
	}

	private boolean hasJewelleryFacility(String objectInfo)
	{
		if (objectInfo == null)
		{
			return false;
		}
		if (objectInfo.contains("Basic Jewellery Box"))
		{
			return config.pohJewelleryBox().covers(JewelleryBoxTier.BASIC);
		}
		if (objectInfo.contains("Fancy Jewellery Box"))
		{
			return config.pohJewelleryBox().covers(JewelleryBoxTier.FANCY);
		}
		if (objectInfo.contains("Ornate Jewellery Box"))
		{
			return config.pohJewelleryBox().covers(JewelleryBoxTier.ORNATE);
		}
		if (objectInfo.contains("Amulet of Glory"))
		{
			return config.pohMountedGlory();
		}
		if (objectInfo.contains("Xeric's Talisman"))
		{
			return config.pohMountedXerics();
		}
		if (objectInfo.contains("Digsite Pendant"))
		{
			return config.pohMountedDigsite();
		}
		if (objectInfo.contains("Mythical cape"))
		{
			return config.pohMythicalCape();
		}
		return false;
	}

	private boolean isUsable(Teleport t,
		Map<Integer, Integer> varCache, Map<Quest, QuestState> questCache)
	{
		for (Map.Entry<Skill, Integer> req : t.skillLevels().entrySet())
		{
			if (client.getBoostedSkillLevel(req.getKey()) < req.getValue())
			{
				return false;
			}
		}
		for (Quest quest : t.quests())
		{
			QuestState state = questCache.computeIfAbsent(quest, this::questStateOrNull);
			if (state != QuestState.FINISHED)
			{
				return false;
			}
		}
		for (VarCheck check : t.varChecks())
		{
			int key = (check.varType() == VarCheck.VarType.VARBIT ? 1 : -1) * (check.id() + 1);
			int value = varCache.computeIfAbsent(key, k ->
				check.varType() == VarCheck.VarType.VARBIT
					? client.getVarbitValue(check.id())
					: client.getVarpValue(check.id()));
			if (!check.satisfiedBy(value))
			{
				return false;
			}
		}
		for (TeleportItemRequirement req : t.items())
		{
			if (!hasItems(req))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A staff/offhand alternative satisfies the whole term regardless of
	 * quantity (it supplies the runes); otherwise `quantity` of the item ids
	 * must be on the player or (when configured) banked.
	 */
	private boolean hasItems(TeleportItemRequirement req)
	{
		boolean useBank = config.teleportItemsFromBank();
		if (countAll(req.staffIds(), useBank) > 0 || countAll(req.offhandIds(), useBank) > 0)
		{
			return true;
		}
		return countAll(req.itemIds(), useBank) >= req.quantity();
	}

	private int countAll(int[] ids, boolean useBank)
	{
		if (ids.length == 0)
		{
			return 0;
		}
		Set<Integer> idSet = new HashSet<>();
		for (int id : ids)
		{
			idSet.add(id);
		}
		int count = itemTracker.countOnPlayer(idSet);
		if (useBank)
		{
			count += itemTracker.countBanked(idSet);
		}
		return count;
	}

	private boolean isFinished(Quest quest)
	{
		return questStateOrNull(quest) == QuestState.FINISHED;
	}

	private QuestState questStateOrNull(Quest quest)
	{
		try
		{
			return client.getQuestState(quest);
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}
}
