package pl.polishforests.entity.base;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * What the fauna code needs to know about a species of the mod (step FA1, docs/04-pionowy-wycinek.md "Projekt FA1"): its
 * size, its groups in summer and in winter, its daily schedule, the season of its winter coat, the habitats it lives in
 * with their weights and the density the spawners aim at. One record per species ({@code RoeDeer.PROFILE}); the spawn at
 * generation and the fauna spawner read only this, so a new species is a new profile, an entity class and its assets.
 *
 * @param species        the id of the entity type without the namespace ({@code roe_deer})
 * @param width          hitbox width (blocks)
 * @param height         hitbox height (blocks)
 * @param summerGroup    group size at spawn in summer (sub-seasons without the winter coat), inclusive
 * @param winterGroup    group size at spawn in winter, inclusive (step FA2 merges and splits the groups by sub-season)
 * @param schedule       the daily schedule: periods from a time of day (ticks 0-23999, 0 = 6:00) to the next one; read by FA2
 * @param winterCoat     the sub-seasons with the winter coat
 * @param habitats       weight 0-1 of each habitat biome of the mod (missing = 0): the share of the target density a column of
 *                       that biome carries, and the walk targets of the brain (a weight above 0 is "its" land)
 * @param edgeWeight     weight of an open land column (meadow, field, heath) near a forest of weight above 0: the forest edge
 *                       (the ecotone forest - open land of {@code Ecotone.edges}, mantle and fringe), within {@link #EDGE_REACH}
 * @param realDensity    the density in Polish forests (animals per hectare of forest, source in "Projekt FA1")
 * @param factor         the factor of the game density over the real one (PW-15 (b), CP-D.3: about 20-50)
 * @param maxNearPlayer  the own limit of the fauna spawner: at most this many animals of the species within
 *                       {@code FaunaSpawner.COUNT_RADIUS} of a player before it adds any
 * @param fleeMin        distance (blocks) at which a sneaking player makes the animal flee
 * @param fleeMax        distance (blocks) at which a sprinting player makes the animal flee (a walking one: the middle)
 */
public record AnimalProfile(String species, float width, float height, Range summerGroup, Range winterGroup, List<Period> schedule,
		Set<SubSeason> winterCoat, Map<HabitatBiome, Float> habitats, float edgeWeight, float realDensity, float factor, int maxNearPlayer,
		int fleeMin, int fleeMax) {
	/** Distance (blocks) from a forest within which an open column is the forest edge. */
	public static final int EDGE_REACH = 8;
	/** Blocks in a hectare: one block is one meter in both scales for the animals (KFA-3: animals 1:1). */
	public static final double BLOCKS_PER_HECTARE = 10_000;

	public AnimalProfile {
		if (summerGroup.min() < 1 || winterGroup.min() < 1) {
			throw new IllegalArgumentException("A group has at least one animal");
		}
		EnumMap<HabitatBiome, Float> copy = new EnumMap<>(HabitatBiome.class);
		copy.putAll(habitats);
		for (Map.Entry<HabitatBiome, Float> e : copy.entrySet()) {
			if (e.getValue() < 0 || e.getValue() > 1) {
				throw new IllegalArgumentException("Weight of " + e.getKey() + " outside 0-1: " + e.getValue());
			}
		}
		habitats = java.util.Collections.unmodifiableMap(copy);
		winterCoat = java.util.Collections.unmodifiableSet(winterCoat.isEmpty() ? EnumSet.noneOf(SubSeason.class) : EnumSet.copyOf(winterCoat));
		schedule = List.copyOf(schedule);
		if (edgeWeight < 0 || edgeWeight > 1 || fleeMin > fleeMax) {
			throw new IllegalArgumentException("Bad edge weight or flee distances of " + species);
		}
	}

	/** An inclusive range of whole numbers. */
	public record Range(int min, int max) {
		public Range {
			if (max < min) {
				throw new IllegalArgumentException(min + " > " + max);
			}
		}

		public int sample(java.util.function.IntUnaryOperator nextInt) {
			return min + nextInt.applyAsInt(max - min + 1);
		}
	}

	/** What the species does in a part of the day (step FA2 drives the brain by it). */
	public enum Activity {
		FEED, REST, MOVE
	}

	/** A part of the daily schedule from {@code fromTick} (time of day) to the start of the next one. */
	public record Period(int fromTick, Activity activity) {
	}

	/** Weight of a habitat biome (0 when the species does not live there). */
	public float weight(HabitatBiome biome) {
		Float w = biome == null ? null : habitats.get(biome);
		return w == null ? 0 : w;
	}

	/** The target density of the game (animals per block of forest of weight 1). */
	public double densityPerBlock() {
		return realDensity * factor / BLOCKS_PER_HECTARE;
	}

	/** Whether the species wears its winter coat in a sub-season. */
	public boolean winterCoat(SubSeason subSeason) {
		return winterCoat.contains(subSeason);
	}

	/** The group size range of a sub-season. */
	public Range group(SubSeason subSeason) {
		return winterCoat(subSeason) ? winterGroup : summerGroup;
	}

	/** The activity at a time of day (ticks, any value: taken modulo a day). */
	public Activity activity(long dayTime) {
		int t = (int) Math.floorMod(dayTime, 24_000L);
		Activity a = schedule.isEmpty() ? Activity.MOVE : schedule.getLast().activity();
		for (Period p : schedule) {
			if (p.fromTick() <= t) {
				a = p.activity();
			}
		}
		return a;
	}
}
