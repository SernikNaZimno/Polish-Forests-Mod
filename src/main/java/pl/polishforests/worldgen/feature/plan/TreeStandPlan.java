package pl.polishforests.worldgen.feature.plan;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Tree stand plan of one chunk (docs/03-m2-biomy.md §8.2, §8.5, §9): a pure function of the chunk habitats, the palette,
 * the world seed and the chunk position, testable without the game.
 *
 * <p>Candidate trunks form one pattern for the whole world, independent of chunk borders: a world-aligned grid of
 * {@value #CELL} × {@value #CELL} block cells has one candidate per cell at a random column of the cell (so every column
 * is equally likely), and a candidate is kept only if no kept candidate with a lower random mark stands closer than
 * {@value #MIN_SPACING} blocks (sequential inhibition in the order of the marks, evaluated locally and recursively, so a
 * chunk sees the same kept candidates near its border as its neighbor). The kept candidates have a known mean density of
 * {@value #DENSITY} per chunk. A kept candidate takes the first palette rule that matches its column's habitat code
 * (biomes, zones, forest site types, associations; {@link HabitatMatch}) and becomes a tree with probability
 * n / {@value #DENSITY}, where n is the rule's number of trees per chunk, so the stand has no grid, no lanes along chunk
 * borders and at least {@value #MIN_SPACING} blocks between trunks.
 *
 * <p>Spacing of sparse stands (§12.1: at least c/2 between trunks, where c = 16 / ⌈√n⌉ is the mesh of a stratified
 * sample of n trees per chunk): where c/2 exceeds {@value #MIN_SPACING} (n ≤ 4), a tree is dropped when a kept candidate
 * with a lower mark closer than c/2 would also become a tree at the same chance (a hard-core thinning without
 * recursion, computable across chunk borders from the candidate pattern alone). The chance is raised so that the mean
 * stays n: for an exclusion area a = π ((c/2)² − 0.7 · {@value #MIN_SPACING}²) / 256 chunks the potential density x
 * solves (1 − e^(−x a)) / a = n.
 *
 * <p>Gaps (§8.2): a noise field of wavelength {@value #GAP_WAVELENGTH} m·k removes the trees from about
 * {@value #GAP_SHARE} of the area; the chance of the other candidates is raised by 1 / (1 − {@value #GAP_SHARE}), so the
 * mean number of trees per chunk stays the palette's. Then a species from the rule's weights, among the species within
 * their range (the flags of the habitat code, §9): the weight of a species with a range flag is multiplied by the
 * chunk's range ramp ({@link SpeciesRamp}), and the rest of it goes to the entry's alternative species where that one is
 * within its range ("beech or spruce", §8.5). A column under water takes only the entries whose maximum water depth
 * covers it (willows and alders in shallow water, §8.2); others get no tree.
 */
public final class TreeStandPlan {
	/** Salt of the tree stand layer in the dispatcher seed and in the candidate pattern. */
	public static final long SALT = 0x7EE5_7A9DL;
	/** Size of the candidate cells in blocks (divides 16, so a chunk holds whole cells). */
	public static final int CELL = 2;
	/** Smallest distance between two trunks in blocks. */
	public static final int MIN_SPACING = 3;
	/**
	 * Mean number of kept candidates per chunk (measured over 100,000 chunks in {@code TreeStandPlanTest}); the largest
	 * possible number of trees per chunk.
	 */
	public static final double DENSITY = 16.65;
	/** Wavelength of the gap noise in meters at the realistic scale (times k, §8.2: 40–80 m). */
	public static final double GAP_WAVELENGTH = 60;
	/** Gap noise level above which a candidate is in a gap. */
	public static final double GAP_THRESHOLD = 0.455;
	/** Share of the area in gaps at {@link #GAP_THRESHOLD} (measured in {@code TreeStandPlanTest}; §8.2: 5–10%). */
	public static final double GAP_SHARE = 0.075;

	private static final int CELLS = 16 / CELL;
	private static final int MIN_SPACING_SQ = MIN_SPACING * MIN_SPACING;

	/**
	 * A palette rule resolved for planning: the habitat condition, the number of trees per chunk and the candidate trees
	 * (indices into the dispatcher's tree list) with their weights, range flag masks ({@code Species.flag()}, 0 for
	 * none), alternative trees (-1 for none) with their range flag masks, and the deepest water each tree may stand in
	 * (0: dry columns only).
	 */
	public record Rule(HabitatMatch match, float treesPerChunk, int[] trees, int[] weights, int[] flags, int[] alternatives,
			int[] alternativeFlags, int[] maxWaterDepth) {
		public Rule {
			int n = trees.length;
			if (weights.length != n || flags.length != n || alternatives.length != n || alternativeFlags.length != n
					|| maxWaterDepth.length != n) {
				throw new IllegalArgumentException("rule arrays differ in length");
			}
		}

		/** Smallest distance between two trunks of this rule: c/2 with c = 16 / ⌈√n⌉, at least {@link #MIN_SPACING}. */
		public double spacing() {
			return TreeStandPlan.spacing(treesPerChunk);
		}

		/** A rule of dry trees without alternatives. */
		public static Rule of(HabitatMatch match, float treesPerChunk, int[] trees, int[] weights, int[] flags) {
			int[] none = new int[trees.length];
			Arrays.fill(none, -1);
			return new Rule(match, treesPerChunk, trees, weights, flags, none, new int[trees.length], new int[trees.length]);
		}
	}

	/** Rules in order: the first rule that matches a column applies; a column without a rule gets no tree. */
	public record Palette(List<Rule> rules) {
		public Palette {
			rules = List.copyOf(rules);
		}

		/** Index of the first rule matching the habitat code, or -1. */
		public int ruleFor(int code) {
			for (int r = 0; r < rules.size(); r++) {
				if (rules.get(r).match().matches(code)) {
					return r;
				}
			}
			return -1;
		}

		/** Trees per chunk of the rule matching the habitat code (0 without a rule). */
		public float treesPerChunk(int code) {
			int r = ruleFor(code);
			return r < 0 ? 0 : rules.get(r).treesPerChunk();
		}
	}

	/**
	 * Conditions of the chunk: the range ramp factor of each flag bit ({@link SpeciesRamp}, 1 = full weight) and the gap
	 * noise (null: no gaps).
	 *
	 * @param ramp      factor of the species with flag bit 0–3 (beech, fir, spruce, hornbeam)
	 * @param gaps      gap noise, or null
	 * @param gapScale  horizontal scale of the gap noise (k of the landscape scale)
	 */
	public record Stand(float[] ramp, Noise gaps, double gapScale) {
		/** Full weights everywhere and no gaps. */
		public static final Stand PLAIN = new Stand(new float[] {1, 1, 1, 1}, null, 1);

		boolean gap(int x, int z) {
			return gaps != null && gaps.at(x, z, GAP_WAVELENGTH * gapScale) > GAP_THRESHOLD;
		}
	}

	private TreeStandPlan() {
	}

	/** Smallest distance between trunks for n trees per chunk: c/2 with c = 16 / ⌈√n⌉, at least {@link #MIN_SPACING}. */
	public static double spacing(double n) {
		double c = 16.0 / Math.ceil(Math.sqrt(Math.max(n, 1e-9)));
		return Math.max(MIN_SPACING, c / 2);
	}

	/**
	 * Potential trees per chunk before the thinning of sparse stands, so that n remain: x with (1 − e^(−x a)) / a = n for
	 * the exclusion area a = π (d² − {@value #THINNING_CORE} · {@value #MIN_SPACING}²) / 256 (chunks) of the spacing d (no
	 * candidates stand closer than {@value #MIN_SPACING} anyway); n itself when the spacing is {@link #MIN_SPACING}.
	 */
	static double potential(double n) {
		double d = spacing(n);
		if (d <= MIN_SPACING || n <= 0) {
			return n;
		}
		double a = Math.PI * (d * d - THINNING_CORE * MIN_SPACING * MIN_SPACING) / 256;
		return -Math.log(1 - Math.min(n * a, 0.95)) / a;
	}

	/**
	 * Share of the hard-core disk of {@value #MIN_SPACING} blocks taken out of the exclusion area: the candidates are not
	 * a Poisson pattern (cells of {@value #CELL} blocks with a hard core), so few of them stand just outside the core
	 * either (calibrated in {@code TreeStandPlanTest}: the mean stays within 2% of n for n from 0.125 to 4).
	 */
	static final double THINNING_CORE = 0.7;

	/** Seed of a dispatcher layer in a chunk: (world seed, chunk, layer salt). */
	public static long seed(long worldSeed, int chunkX, int chunkZ, long salt) {
		return Noise.mix(worldSeed ^ Noise.mix(chunkX * 0x9E37_79B9_7F4A_7C15L + chunkZ * 0xC2B2_AE3D_27D4_EB4FL + salt));
	}

	/**
	 * Trees of the chunk, each packed as {@code column << 16 | tree}, where column is {@code x * 16 + z} and tree the
	 * index into the palette's tree list.
	 *
	 * @param codes      habitat codes of the 256 columns
	 * @param waterDepth water depth of each column in blocks (0: dry)
	 */
	public static int[] of(int[] codes, int[] waterDepth, Palette palette, long worldSeed, int chunkX, int chunkZ,
			Stand stand) {
		boolean any = false;
		int last = 0;
		boolean lastKnown = false;
		for (int code : codes) {
			if (!lastKnown || code != last) {
				last = code;
				lastKnown = true;
				if (palette.treesPerChunk(code) > 0) {
					any = true;
					break;
				}
			}
		}
		if (!any) {
			return new int[0];
		}
		Pattern pattern = new Pattern(worldSeed);
		int[] out = new int[CELLS * CELLS];
		int n = 0;
		double boost = 1 / (DENSITY * (stand.gaps() == null ? 1 : 1 - GAP_SHARE));
		for (int i = 0; i < CELLS; i++) {
			for (int j = 0; j < CELLS; j++) {
				int gx = chunkX * CELLS + i;
				int gz = chunkZ * CELLS + j;
				if (!pattern.kept(gx, gz)) {
					continue;
				}
				long h = pattern.hash(gx, gz);
				int lx = i * CELL + offsetX(h);
				int lz = j * CELL + offsetZ(h);
				int column = lx << 4 | lz;
				int code = codes[column];
				int r = palette.ruleFor(code);
				if (r < 0) {
					continue;
				}
				Rule rule = palette.rules().get(r);
				long h2 = Noise.mix(h ^ 0x5DEE_CE66_DA11L);
				double chance = potential(rule.treesPerChunk()) * boost;
				if (unit(h2) >= chance) {
					continue;
				}
				double spacing = rule.spacing();
				if (spacing > MIN_SPACING && pattern.thinned(gx, gz, h, spacing, chance)) {
					continue;
				}
				if (stand.gap(chunkX * 16 + lx, chunkZ * 16 + lz)) {
					continue;
				}
				int tree = pickTree(rule, pl.polishforests.worldgen.habitat.Habitat.flags(code), waterDepth[column],
						stand.ramp(), unit(Noise.mix(h2)));
				if (tree >= 0) {
					out[n++] = column << 16 | tree;
				}
			}
		}
		return Arrays.copyOf(out, n);
	}

	/** Kept candidates of the chunk (local columns {@code x * 16 + z}), for tests: the pattern before biome thinning. */
	static int[] keptCandidates(long worldSeed, int chunkX, int chunkZ) {
		Pattern pattern = new Pattern(worldSeed);
		int[] out = new int[CELLS * CELLS];
		int n = 0;
		for (int i = 0; i < CELLS; i++) {
			for (int j = 0; j < CELLS; j++) {
				int gx = chunkX * CELLS + i;
				int gz = chunkZ * CELLS + j;
				if (pattern.kept(gx, gz)) {
					long h = pattern.hash(gx, gz);
					out[n++] = (i * CELL + offsetX(h)) << 4 | (j * CELL + offsetZ(h));
				}
			}
		}
		return Arrays.copyOf(out, n);
	}

	private static int offsetX(long h) {
		return (int) (h & (CELL - 1));
	}

	private static int offsetZ(long h) {
		return (int) ((h >>> 8) & (CELL - 1));
	}

	/** Uniform value in [0, 1) from the high 53 bits of a hash. */
	static double unit(long h) {
		return (h >>> 11) * 0x1.0p-53;
	}

	/**
	 * The world's candidate pattern: candidate of cell (gx, gz) and its mark from a hash of (world seed, cell); a
	 * candidate is kept unless a kept candidate with a lower mark is closer than {@link #MIN_SPACING}. Kept states are
	 * memoized for one chunk.
	 */
	private static final class Pattern {
		private final long seed;
		private final Map<Long, Boolean> kept = new HashMap<>();

		Pattern(long worldSeed) {
			this.seed = worldSeed ^ SALT;
		}

		long hash(int gx, int gz) {
			return Noise.mix(seed ^ Noise.mix(gx * 0x9E37_79B9_7F4A_7C15L + gz * 0xC2B2_AE3D_27D4_EB4FL));
		}

		boolean kept(int gx, int gz) {
			long key = (long) gx << 32 | (gz & 0xFFFF_FFFFL);
			Boolean known = kept.get(key);
			if (known != null) {
				return known;
			}
			long h = hash(gx, gz);
			int x = gx * CELL + offsetX(h);
			int z = gz * CELL + offsetZ(h);
			boolean result = true;
			// With cells of 2 blocks and a spacing of 3, only the 8 neighboring cells can hold a closer candidate.
			for (int di = -1; di <= 1 && result; di++) {
				for (int dj = -1; dj <= 1; dj++) {
					if (di == 0 && dj == 0) {
						continue;
					}
					long hn = hash(gx + di, gz + dj);
					int dx = (gx + di) * CELL + offsetX(hn) - x;
					int dz = (gz + dj) * CELL + offsetZ(hn) - z;
					if (dx * dx + dz * dz < MIN_SPACING_SQ && lower(hn, gx + di, gz + dj, h, gx, gz)
							&& kept(gx + di, gz + dj)) {
						result = false;
						break;
					}
				}
			}
			kept.put(key, result);
			return result;
		}

		/**
		 * Whether the candidate of cell (gx, gz) with hash h gives way in a sparse stand: a kept candidate with a lower mark
		 * closer than the spacing that would become a tree at the same chance.
		 */
		boolean thinned(int gx, int gz, long h, double spacing, double chance) {
			int x = gx * CELL + offsetX(h);
			int z = gz * CELL + offsetZ(h);
			int reach = (int) Math.ceil(spacing / CELL) + 1;
			double limit = spacing * spacing;
			for (int di = -reach; di <= reach; di++) {
				for (int dj = -reach; dj <= reach; dj++) {
					if (di == 0 && dj == 0) {
						continue;
					}
					long hn = hash(gx + di, gz + dj);
					int dx = (gx + di) * CELL + offsetX(hn) - x;
					int dz = (gz + dj) * CELL + offsetZ(hn) - z;
					if (dx * dx + dz * dz < limit && lower(hn, gx + di, gz + dj, h, gx, gz)
							&& unit(Noise.mix(hn ^ 0x5DEE_CE66_DA11L)) < chance && kept(gx + di, gz + dj)) {
						return true;
					}
				}
			}
			return false;
		}

		/** Whether mark a (cell a) is lower than mark b (cell b); ties broken by the cell, so the order is strict. */
		private static boolean lower(long a, int ax, int az, long b, int bx, int bz) {
			int c = Long.compareUnsigned(a, b);
			if (c != 0) {
				return c < 0;
			}
			return ax != bx ? ax < bx : az < bz;
		}
	}

	/**
	 * Tree by weight among the entries of the rule within their range and water depth, or -1. Entry i contributes its
	 * weight times the ramp of its species (0 out of range) and the rest of the weight times the ramp of its alternative
	 * (0 out of range or without one).
	 */
	private static int pickTree(Rule rule, int rangeFlags, int depth, float[] ramp, double pick) {
		int[] trees = rule.trees();
		double total = 0;
		for (int i = 0; i < trees.length; i++) {
			if (depth <= rule.maxWaterDepth()[i]) {
				total += own(rule, i, rangeFlags, ramp) + alternative(rule, i, rangeFlags, ramp);
			}
		}
		if (total <= 0) {
			return -1;
		}
		double target = pick * total;
		for (int i = 0; i < trees.length; i++) {
			if (depth > rule.maxWaterDepth()[i]) {
				continue;
			}
			target -= own(rule, i, rangeFlags, ramp);
			if (target < 0) {
				return trees[i];
			}
			target -= alternative(rule, i, rangeFlags, ramp);
			if (target < 0) {
				return rule.alternatives()[i];
			}
		}
		return -1;
	}

	/** Weight of entry i's own species: its weight times its range ramp, 0 out of range. */
	private static double own(Rule rule, int i, int rangeFlags, float[] ramp) {
		return rule.weights()[i] * factor(rule.flags()[i], rangeFlags, ramp);
	}

	/** Weight of entry i's alternative species: the rest of the entry's weight times the alternative's ramp. */
	private static double alternative(Rule rule, int i, int rangeFlags, float[] ramp) {
		if (rule.alternatives()[i] < 0) {
			return 0;
		}
		double rest = 1 - factor(rule.flags()[i], rangeFlags, ramp);
		return rest <= 0 ? 0 : rule.weights()[i] * rest * factor(rule.alternativeFlags()[i], rangeFlags, ramp);
	}

	/** Range factor of a species: 1 without a flag, 0 out of range, else the ramp of its flag bit. */
	private static double factor(int flag, int rangeFlags, float[] ramp) {
		if (flag == 0) {
			return 1;
		}
		if ((rangeFlags & flag) == 0) {
			return 0;
		}
		return ramp[Integer.numberOfTrailingZeros(flag)];
	}
}
