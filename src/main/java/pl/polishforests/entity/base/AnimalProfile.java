package pl.polishforests.entity.base;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * What the fauna code needs to know about a species of the mod (step FA1, docs/04-pionowy-wycinek.md "Projekt FA1"; step
 * FA2 "Projekt FA2": the daily schedule as shares of activities, the sub-seasons of the winter groups and of the young,
 * two density calibrations and the home range; step FA3 "Projekt FA3": the share of lone animals and the young of a
 * mother): its size, its groups in summer and in winter, its daily schedule, the
 * season of its winter coat, the habitats it lives in with their weights and the densities the spawners aim at. One record
 * per species ({@code RoeDeer.PROFILE}); the spawn at generation and the fauna spawner read only this, so a new species is
 * a new profile, an entity class and its assets.
 *
 * @param species            the id of the entity type without the namespace ({@code roe_deer})
 * @param width              hitbox width (blocks)
 * @param height             hitbox height (blocks)
 * @param summerGroup        group size in summer (sub-seasons outside {@code winterGroupSeasons}), inclusive
 * @param winterGroup        group size in winter ({@code winterGroupSeasons}), inclusive: groups merge up to its maximum
 * @param winterGroupSeasons the sub-seasons of the winter groups (step FA2: the groups merge in them and split outside)
 * @param schedule           the daily schedule: periods from a time of day (ticks 0-23999, 0 = 6:00) to the next one, each
 *                           with the shares of feeding, resting and moving (step FA2)
 * @param winterCoat         the sub-seasons with the winter coat
 * @param youngSeasons       the sub-seasons in which a summer group may be a mother with her young (step FA2)
 * @param youngChance        the chance that a summer group of at least two in {@code youngSeasons} is a mother with young
 * @param habitats           weight 0-1 of each habitat biome of the mod (missing = 0): the share of the target density a
 *                           column of that biome carries, and the walk targets of the brain (a weight above 0 is "its" land)
 * @param edgeWeight         weight of an open land column (meadow, field, heath) near a forest of weight above 0: the forest
 *                           edge (the ecotone forest - open land of {@code Ecotone.edges}, mantle and fringe), within
 *                           {@link #EDGE_REACH}
 * @param realDensity        the density in Polish forests (animals per hectare of forest, source in "Projekt FA2")
 * @param generationFactor   the factor of the density of the spawn at generation over the real one (PW-15 (b), CP-D.3: about
 *                           20-50; R16: calibrated apart from the spawner)
 * @param spawnerFactor      the factor of the target density of the fauna spawner over the real one (about 20-50; the
 *                           spawner tops the animals around a player up to it, counting those of the generation)
 * @param maxNearPlayer      the own limit of the fauna spawner: at most this many animals of the species within
 *                           {@code FaunaSpawner.COUNT_RADIUS} of a player before it adds any
 * @param fleeMin            distance (blocks) at which a sneaking player makes the animal flee
 * @param fleeMax            distance (blocks) at which a sprinting player makes the animal flee (a walking one: the middle)
 * @param homeRange          distance (blocks) from its home (where a group leader appeared or was loaded) beyond which the
 *                           walk targets lead back (step FA2: the daily range stays bounded)
 * @param soloShare          the share of the groups placed by the spawners that are one lone animal (step FA3: the adult
 *                           wild boar males live alone; 0 for the roe deer, whose lone animals come from the group range)
 * @param maxYoung           the most young one mother has (step FA3: roe deer 2, wild boar 4); a young one without a mother
 *                           takes one with fewer, and a spawned group with young has one mother per this many young
 */
public record AnimalProfile(String species, float width, float height, Range summerGroup, Range winterGroup,
		Set<SubSeason> winterGroupSeasons, List<Period> schedule, Set<SubSeason> winterCoat, Set<SubSeason> youngSeasons,
		float youngChance, Map<HabitatBiome, Float> habitats, float edgeWeight, float realDensity, float generationFactor,
		float spawnerFactor, int maxNearPlayer, int fleeMin, int fleeMax, int homeRange, float soloShare, int maxYoung) {
	/** Distance (blocks) from a forest within which an open column is the forest edge. */
	public static final int EDGE_REACH = 8;
	/** Blocks in a hectare: one block is one meter in both scales for the animals (KFA-3: animals 1:1). */
	public static final double BLOCKS_PER_HECTARE = 10_000;
	/** Ticks of a day. */
	public static final int DAY = 24_000;

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
		winterCoat = frozen(winterCoat);
		winterGroupSeasons = frozen(winterGroupSeasons);
		youngSeasons = frozen(youngSeasons);
		schedule = List.copyOf(schedule);
		for (int i = 1; i < schedule.size(); i++) {
			if (schedule.get(i).fromTick() <= schedule.get(i - 1).fromTick()) {
				throw new IllegalArgumentException("The schedule of " + species + " is not in order");
			}
		}
		if (edgeWeight < 0 || edgeWeight > 1 || fleeMin > fleeMax || youngChance < 0 || youngChance > 1 || homeRange <= 0) {
			throw new IllegalArgumentException("Bad edge weight, flee distances, young chance or home range of " + species);
		}
		if (soloShare < 0 || soloShare >= 1 || maxYoung < 1) {
			throw new IllegalArgumentException("Bad solo share or young per mother of " + species);
		}
	}

	private static Set<SubSeason> frozen(Set<SubSeason> set) {
		return java.util.Collections.unmodifiableSet(set.isEmpty() ? EnumSet.noneOf(SubSeason.class) : EnumSet.copyOf(set));
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

		public boolean contains(int n) {
			return n >= min && n <= max;
		}

		public double mean() {
			return (min + max) / 2.0;
		}
	}

	/** What the species does in a bout of its daily routine (step FA2 drives the brain by it). */
	public enum Activity {
		FEED, REST, MOVE
	}

	/**
	 * A part of the daily schedule from {@code fromTick} (time of day) to the start of the next one: the shares of the bouts
	 * of feeding, resting and moving that start in it (normalized when drawn).
	 */
	public record Period(int fromTick, float feed, float rest, float move) {
		public Period {
			if (fromTick < 0 || fromTick >= DAY || feed < 0 || rest < 0 || move < 0 || feed + rest + move <= 0) {
				throw new IllegalArgumentException("Bad period from " + fromTick);
			}
		}

		/** The share of an activity (0-1). */
		public float share(Activity a) {
			float sum = feed + rest + move;
			return switch (a) {
				case FEED -> feed / sum;
				case REST -> rest / sum;
				case MOVE -> move / sum;
			};
		}

		/** The activity of a draw {@code r} in [0, 1). */
		public Activity pick(float r) {
			float x = r * (feed + rest + move);
			if (x < feed) {
				return Activity.FEED;
			}
			return x < feed + rest ? Activity.REST : Activity.MOVE;
		}

		/** The activity with the largest share. */
		public Activity main() {
			if (feed >= rest && feed >= move) {
				return Activity.FEED;
			}
			return rest >= move ? Activity.REST : Activity.MOVE;
		}
	}

	/** Weight of a habitat biome (0 when the species does not live there). */
	public float weight(HabitatBiome biome) {
		Float w = biome == null ? null : habitats.get(biome);
		return w == null ? 0 : w;
	}

	/** The density of the spawn at generation (animals per block of forest of weight 1). */
	public double generationDensityPerBlock() {
		return realDensity * generationFactor / BLOCKS_PER_HECTARE;
	}

	/** The target density of the fauna spawner (animals per block of forest of weight 1). */
	public double spawnerDensityPerBlock() {
		return realDensity * spawnerFactor / BLOCKS_PER_HECTARE;
	}

	/** Whether the species wears its winter coat in a sub-season. */
	public boolean winterCoat(SubSeason subSeason) {
		return winterCoat.contains(subSeason);
	}

	/**
	 * The mean number of animals of a group placed by the spawners with a group range (step FA3): the lone animals
	 * ({@link #soloShare}) count one, the rest the mean of the range. Equal to the mean of the range for {@code soloShare}
	 * 0 (the roe deer).
	 */
	public double meanGroupSize(Range group) {
		return soloShare <= 0 ? group.mean() : soloShare + (1 - soloShare) * group.mean();
	}

	/** Whether a sub-season has the winter groups. */
	public boolean winterGroups(SubSeason subSeason) {
		return winterGroupSeasons.contains(subSeason);
	}

	/** The group size range of a sub-season. */
	public Range group(SubSeason subSeason) {
		return winterGroups(subSeason) ? winterGroup : summerGroup;
	}

	/** Whether a summer group may be a mother with young in a sub-season. */
	public boolean young(SubSeason subSeason) {
		return youngSeasons.contains(subSeason) && !winterGroups(subSeason);
	}

	/** The index of the period of the schedule at a time of day (ticks, any value: taken modulo a day). */
	public int periodIndex(long dayTime) {
		int t = (int) Math.floorMod(dayTime, (long) DAY);
		int index = schedule.size() - 1;
		for (int i = 0; i < schedule.size(); i++) {
			if (schedule.get(i).fromTick() <= t) {
				index = i;
			}
		}
		return index;
	}

	/** The period of the schedule at a time of day. */
	public Period period(long dayTime) {
		return schedule.get(periodIndex(dayTime));
	}

	/** The main activity at a time of day (the largest share of its period). */
	public Activity activity(long dayTime) {
		return schedule.isEmpty() ? Activity.MOVE : period(dayTime).main();
	}

	/** The share of an activity at a time of day. */
	public float share(Activity activity, long dayTime) {
		return period(dayTime).share(activity);
	}
}
