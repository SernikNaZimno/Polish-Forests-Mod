package pl.polishforests.worldgen.tree;

import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The parameters of the stand map by forest (step F1 of docs/04-pionowy-wycinek.md, "Kroki F": the stand map's
 * parameters in a table by habitat biome): the natural share of D9 (a factor on the world's natural share), the shares
 * of the age classes in managed and natural stands, the size of the stands (the chance that a Voronoi cell of
 * {@link ForestStandMap} merges with its neighbor; the cell spacing stays the same for all forests, so the lattice of the
 * stands is one pure function of the seed), the canopy gaps of the natural stands and the regeneration nests of the
 * managed stands (gaps the forester cuts, {@code nestChance}; none in the forests of the slice).
 *
 * <p>The stand map is habitat-free (the crown territories need the stand attributes on both sides of a chunk border,
 * rule ZW-2): a stand takes the profile of the forest at its site point and a gap or a nest the profile at its center,
 * read from a pure function of the world ({@link Field}, in a world the lattice of {@code ForestBlend}). The profile of
 * the forests of the slice ({@link #DEFAULT}) is the stand map of the build before step F1, so the stands far from the
 * forests with their own profile do not change.
 *
 * @param naturalFactor factor on the world's natural share (D9) of the stands of this forest
 * @param managedAges   shares of the age classes (pole stage, mature, old growth) of managed stands
 * @param naturalAges   shares of the age classes of natural stands
 * @param mergeChance   chance that a stand's Voronoi cell merges with its neighbor (larger stands at a higher chance)
 * @param gapChance     chance by age class of a canopy gap in a cell of {@value ForestStandMap#GAP_CELL} m (natural stands)
 * @param clusterChance chance that a gap has a smaller second gap next to it (a group of fallen trees)
 * @param gapMedian     median diameter of a gap (m), log-normal with {@code gapSigma}, clamped to {@code gapMin}–{@code gapMax}
 * @param gapSigma      sigma of the log-normal diameter
 * @param gapMin        least diameter of a gap (m)
 * @param gapMax        largest diameter of a gap (m, at most {@value ForestStandMap#GAP_CELL})
 * @param nestChance    chance by age class of a regeneration nest in a cell (managed stands; 0: none)
 * @param nestMedian    median diameter of a nest (m; the same sigma and bounds as the gaps)
 */
public record StandProfile(double naturalFactor, double[] managedAges, double[] naturalAges, double mergeChance,
		double[] gapChance, double clusterChance, double gapMedian, double gapSigma, double gapMin, double gapMax,
		double[] nestChance, double nestMedian) {
	/**
	 * The stand map of the forests of the vertical slice (steps V9–V11): the constants of {@link ForestStandMap} before
	 * step F1.
	 */
	public static final StandProfile DEFAULT = new StandProfile(1.0, new double[] {0.20, 0.58, 0.22}, new double[] {0.10, 0.35, 0.55},
			ForestStandMap.MERGE_CHANCE, new double[] {0.09, 0.32, 0.36}, ForestStandMap.CLUSTER_CHANCE, ForestStandMap.GAP_MEDIAN,
			ForestStandMap.GAP_SIGMA, ForestStandMap.GAP_MIN, ForestStandMap.GAP_MAX, new double[] {0, 0, 0}, 0);
	/**
	 * The lowland beech forest (step F1; research/06 §4.3, docs/04 §6 card of the beech forest): the natural stands open
	 * by the fall of single trees and of small groups (gaps of mostly 10–30 m, often two together, more of them than in the
	 * slice forests, N7 0–15%); the managed stands are single-storied shelterwood stands whose mature and old stands have
	 * regeneration nests of about 20–30 m (the group cutting of the beech, the regeneration under the edge of the nest).
	 * The natural share and the ages are those of the slice (D9 and the age classes of V9).
	 */
	public static final StandProfile BEECH = new StandProfile(1.0, DEFAULT.managedAges, DEFAULT.naturalAges,
			ForestStandMap.MERGE_CHANCE, new double[] {0.12, 0.42, 0.50}, 0.5, 20, 0.4, 10, 40, new double[] {0, 0.16, 0.30}, 24);
	/**
	 * The alder carr (step F2; research/06 §4.6): managed stands even-aged alder stands (every stand has one age class),
	 * natural stands open by the fall of single alders and small groups on the soft peat (gaps of mostly 8–25 m, often two
	 * together); no regeneration nests. The natural share and the ages are those of the slice (as the beech forest's): a
	 * stand reaches far beyond its site point, so other ages would change the stands of the neighboring forests outside
	 * the blend belt (default choice F2-e).
	 */
	public static final StandProfile ALDER_CARR = new StandProfile(1.0, DEFAULT.managedAges, DEFAULT.naturalAges,
			ForestStandMap.MERGE_CHANCE, new double[] {0.10, 0.30, 0.40}, 0.4, 16, 0.4, 8, 32, new double[] {0, 0, 0}, 0);
	/**
	 * The ash-alder forest (step F2): managed stands as the carr's; natural stands with more and larger gaps (the ash
	 * dieback, the Dutch elm disease); no regeneration nests.
	 */
	public static final StandProfile ASH_ALDER = new StandProfile(1.0, DEFAULT.managedAges, DEFAULT.naturalAges,
			ForestStandMap.MERGE_CHANCE, new double[] {0.10, 0.35, 0.45}, 0.45, 18, 0.4, 8, 36, new double[] {0, 0, 0}, 0);
	/**
	 * The upland fir forest (step F3; research/06 §3.8, §4.3): natural stands of the selection structure, opened by the
	 * fall of single firs and of small groups (the fir dieback; gaps of mostly 8–25 m, the fir regenerates under the
	 * canopy, so the gaps stay small); managed stands of the shelterwood and selection cutting with regeneration nests of
	 * about 15–25 m (the fir regenerates in shade, the nests smaller than the beech's). The natural share and the ages are
	 * those of the slice (as the beech forest's, default choice F2-e).
	 */
	public static final StandProfile FIR = new StandProfile(1.0, DEFAULT.managedAges, DEFAULT.naturalAges,
			ForestStandMap.MERGE_CHANCE, new double[] {0.10, 0.35, 0.45}, 0.4, 14, 0.4, 6, 30, new double[] {0, 0.12, 0.25}, 20);
	/**
	 * The Carpathian beech forest (step F3; research/06 §3.3, §4.3): natural stands opened by the fall of single beeches
	 * and firs and of small groups (the windthrows of the slopes; gaps of mostly 10–30 m, often two together); managed
	 * stands of the shelterwood and group cutting with regeneration nests of about 20–25 m.
	 */
	public static final StandProfile MONTANE_BEECH = new StandProfile(1.0, DEFAULT.managedAges, DEFAULT.naturalAges,
			ForestStandMap.MERGE_CHANCE, new double[] {0.10, 0.38, 0.48}, 0.45, 18, 0.4, 8, 36, new double[] {0, 0.14, 0.28}, 22);
	/**
	 * The elm-ash and the willow-poplar floodplain forest (step F5; research/06 §4.5): natural stands opened by the fall of
	 * old willows and poplars, the floods and the Dutch elm disease (gaps of mostly 10–30 m, often two together, more of
	 * them than in the alder forests); managed stands without regeneration nests (clear cuts and plantings of the floodplain).
	 * The gap chances stay within those of the table, so the gap draws of the other forests do not change.
	 */
	public static final StandProfile FLOODPLAIN = new StandProfile(1.0, DEFAULT.managedAges, DEFAULT.naturalAges,
			ForestStandMap.MERGE_CHANCE, new double[] {0.11, 0.38, 0.48}, 0.5, 18, 0.4, 8, 36, new double[] {0, 0, 0}, 0);
	/** All profiles of the table (the gap process draws against the highest chance of any of them). */
	static final StandProfile[] ALL = {DEFAULT, BEECH, ALDER_CARR, ASH_ALDER, FIR, MONTANE_BEECH, FLOODPLAIN};

	public StandProfile {
		if (managedAges.length != 3 || naturalAges.length != 3 || gapChance.length != 3 || nestChance.length != 3) {
			throw new IllegalArgumentException("three age classes");
		}
		if (gapMax > ForestStandMap.GAP_CELL || gapMin <= 0 || gapMin > gapMax) {
			throw new IllegalArgumentException("gap diameters " + gapMin + "–" + gapMax);
		}
	}

	/** The profile of a forest (the forests without their own profile: {@link #DEFAULT}). */
	public static StandProfile of(@Nullable HabitatBiome biome) {
		if (biome == null) {
			return DEFAULT;
		}
		return switch (biome) {
			case LOWLAND_BEECH_FOREST -> BEECH;
			case ALDER_CARR -> ALDER_CARR;
			case ASH_ALDER_FOREST -> ASH_ALDER;
			case UPLAND_FIR_FOREST -> FIR;
			case MONTANE_BEECH_FOREST -> MONTANE_BEECH;
			case ELM_ASH_FOREST, WILLOW_POPLAR_FOREST -> FLOODPLAIN;
			default -> DEFAULT;
		};
	}

	/** Whether the stands themselves (natural share, ages, size) differ from {@link #DEFAULT} (not only the gaps). */
	boolean standsDiffer() {
		return naturalFactor != DEFAULT.naturalFactor || !java.util.Arrays.equals(managedAges, DEFAULT.managedAges)
				|| !java.util.Arrays.equals(naturalAges, DEFAULT.naturalAges) || mergeChance != DEFAULT.mergeChance;
	}

	/** The profile of the stand map at a world column: a pure function of the world (safe for concurrent use). */
	@FunctionalInterface
	public interface Field {
		/** The same profile everywhere. */
		static Field uniform(StandProfile p) {
			return (x, z) -> p;
		}

		StandProfile at(int x, int z);
	}
}
