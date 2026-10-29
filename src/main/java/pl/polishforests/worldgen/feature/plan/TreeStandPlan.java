package pl.polishforests.worldgen.feature.plan;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.CrownTerritories;
import pl.polishforests.worldgen.tree.StandMap;

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
 *
 * <p><b>Layered stands</b> (step V9 of docs/04-pionowy-wycinek.md): a rule with {@link Layers} (the forests of the
 * vertical slice) plants the stand of the stand map ({@link StandMap}) instead: each kept candidate has the crown layer
 * of its site ({@link CrownTerritories#site}: canopy, subcanopy or regeneration from the shares of its stand, the
 * regeneration in a canopy gap) and becomes a tree with the chance of its stand (managed or natural and the age class,
 * or the gap chance), of a species from the list of its layer; instead of the draw it compares its rank value
 * ({@link Candidates#rank}: the rank of its draw among the candidates around it), so the candidates without a tree
 * spread out as in a thinned stand; no thinning of sparse stands and no M2 gap noise. The candidates and the draws stay those of M2 (rule ZW-2), so the stands of the other biomes do not change.
 * The plan packs the layer of every tree ({@link #layer}) when the conditions carry the crown territories.
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
	/** Salt of a candidate's chance draw, mixed into the candidate's hash. */
	private static final long DRAW_SALT = 0x5DEE_CE66_DA11L;
	/** Salt of the uniform part of a candidate's rank value (step V9: a new field, a new salt). */
	private static final long RANK_SALT = 0x2A9C_15E7_0B3DL;
	/** Radius of the neighborhood of a rank value, blocks (about 7 kept candidates). */
	static final int RANK_RADIUS = 6;
	private static final int MIN_SPACING_SQ = MIN_SPACING * MIN_SPACING;

	/**
	 * A palette rule resolved for planning: the habitat condition, the number of trees per chunk and the candidate trees
	 * (indices into the dispatcher's tree list) with their weights, range flag masks ({@code Species.flag()}, 0 for
	 * none), alternative trees (-1 for none) with their range flag masks, and the deepest water each tree may stand in
	 * (0: dry columns only).
	 */
	public record Rule(HabitatMatch match, float treesPerChunk, int[] trees, int[] weights, int[] flags, int[] alternatives,
			int[] alternativeFlags, int[] maxWaterDepth, @Nullable Layers layers) {
		public Rule(HabitatMatch match, float treesPerChunk, int[] trees, int[] weights, int[] flags, int[] alternatives,
				int[] alternativeFlags, int[] maxWaterDepth) {
			this(match, treesPerChunk, trees, weights, flags, alternatives, alternativeFlags, maxWaterDepth, null);
		}

		/** The same rule with a layered stand. */
		public Rule withLayers(Layers layers) {
			return new Rule(match, treesPerChunk, trees, weights, flags, alternatives, alternativeFlags, maxWaterDepth, layers);
		}

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

	/**
	 * The layered stand of a rule (step V9): the species lists of the layers (rules used as lists: their match and trees
	 * per chunk are not read) and the chances of a candidate to grow a tree by stand.
	 *
	 * @param managedCanopy   canopy of managed stands, or null for the rule's own trees
	 * @param managed         chance by age class ({@link AgeClass} ordinal) in a managed stand
	 * @param natural         chance by age class in a natural stand
	 * @param gap             chance in a canopy gap
	 */
	public record Layers(@Nullable Rule managedCanopy, Rule subcanopy, Rule regeneration, Rule gapRegeneration, double[] managed,
			double[] natural, double gap) {
		public Layers {
			if (managed.length != 3 || natural.length != 3) {
				throw new IllegalArgumentException("one chance per age class");
			}
		}

		/** Chance of a candidate of a stand (or in a canopy gap) to grow a tree. */
		public double chance(boolean natural, AgeClass age, boolean gap) {
			return gap ? this.gap : natural ? this.natural[age.ordinal()] : managed[age.ordinal()];
		}

		/** Highest chance of any candidate. */
		double highest() {
			double h = gap;
			for (int a = 0; a < 3; a++) {
				h = Math.max(h, Math.max(managed[a], natural[a]));
			}
			return h;
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

		/**
		 * Highest chance of any rule ({@link TreeStandPlan#chance}): a candidate whose draw is at or above it gets no tree
		 * whatever its habitat code, also where an ecotone gives it the rule of another column.
		 */
		public double highestChance(boolean gaps) {
			double highest = 0;
			for (Rule rule : rules) {
				highest = Math.max(highest, rule.layers() != null ? rule.layers().highest() : chance(rule.treesPerChunk(), gaps));
			}
			return highest;
		}

		/**
		 * Whether the kept candidate at world column (x, z) surely grows no tree under any rule, whatever its habitat code
		 * (an ecotone may give it the rule of another column): its draw is at or above the highest chance of the M2 rules
		 * (or it stands in an M2 gap), and its rank value ({@link Candidates#rank}) at or above the chance of every layered
		 * rule at its stand. Needs only the seed, the gap noise and the stand map.
		 */
		public boolean surelyTreeless(Candidates candidates, int x, int z, Stand stand) {
			double m2 = 0;
			double layered = 0;
			boolean anyLayered = false;
			StandSite site = null;
			for (Rule rule : rules) {
				Layers l = rule.layers();
				if (l == null) {
					m2 = Math.max(m2, chance(rule.treesPerChunk(), stand.gaps() != null));
				} else {
					anyLayered = true;
					if (stand.crowns() == null) {
						layered = Math.max(layered, l.highest());
					} else {
						if (site == null) {
							site = stand.site(x, z);
						}
						layered = Math.max(layered, l.chance(site.natural(), site.age(), site.gap()));
					}
				}
			}
			boolean m2None = stand.gap(x, z) || candidates.draw(x, z) >= m2;
			return m2None && (!anyLayered || candidates.rank(x, z) >= layered);
		}

		/** Whether any rule has a layered stand. */
		public boolean layered() {
			for (Rule rule : rules) {
				if (rule.layers() != null) {
					return true;
				}
			}
			return false;
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
	public record Stand(float[] ramp, Noise gaps, double gapScale, @Nullable StandMap map, @Nullable CrownTerritories crowns) {
		/** Full weights everywhere and no gaps. */
		public static final Stand PLAIN = new Stand(new float[] {1, 1, 1, 1}, null, 1);

		/** Conditions without the stand map (the M2 stand). */
		public Stand(float[] ramp, Noise gaps, double gapScale) {
			this(ramp, gaps, gapScale, null, null);
		}

		/** The same conditions with the range ramp of another chunk. */
		public Stand withRamp(float[] ramp) {
			return new Stand(ramp, gaps, gapScale, map, crowns);
		}

		boolean gap(int x, int z) {
			return gaps != null && gaps.at(x, z, GAP_WAVELENGTH * gapScale) > GAP_THRESHOLD;
		}

		/** The stand facts of a candidate column (requires the crown territories). */
		StandSite site(int x, int z) {
			CrownTerritories.Site s = crowns.site(x, z);
			return new StandSite(s.layer(), s.age(), s.natural(), s.gap(), s.gap() ? crowns.stands().gapDepth(x, z) : 0);
		}
	}

	/** Layer, age class, natural flag and canopy gap (with its depth) of a candidate. */
	record StandSite(CrownLayer layer, AgeClass age, boolean natural, boolean gap, double gapDepth) {
	}

	/**
	 * Least depth (blocks) inside a canopy gap of a candidate that takes the gap regeneration ({@link Layers#gapRegeneration}):
	 * the crowns at the gap's edge lean over its outer meters (a young pine there would stand "under the canopy", N6).
	 */
	public static final double GAP_INTERIOR = 7;

	/** Bits of the packed tree of {@link #of}: tree index in bits 0-13, layer in 14-15, column in 16-23. */
	private static final int TREE_MASK = 0x3FFF;
	private static final int LAYER_SHIFT = 14;

	/** Column ({@code x * 16 + z}) of a packed tree of {@link #of}. */
	public static int column(int packed) {
		return packed >>> 16;
	}

	/** Tree index of a packed tree of {@link #of}. */
	public static int tree(int packed) {
		return packed & TREE_MASK;
	}

	/** Crown layer of a packed tree of {@link #of} (the canopy without the crown territories). */
	public static CrownLayer layer(int packed) {
		return CrownLayer.values()[packed >>> LAYER_SHIFT & 3];
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
	 * Chance of a kept candidate to become a tree under a rule of n trees per chunk, before the thinning of sparse stands
	 * and the gaps: the candidate's draw ({@link Candidates#draw}) must be below it. The potential trees per chunk over
	 * the candidate density, raised by 1 / (1 − {@value #GAP_SHARE}) when the stand has gaps; 1 or more means every
	 * candidate that is neither thinned nor in a gap.
	 */
	public static double chance(double treesPerChunk, boolean gaps) {
		double boost = 1 / (DENSITY * (gaps ? 1 - GAP_SHARE : 1));
		return potential(treesPerChunk) * boost;
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
				int tree = decide(pattern, gx, gz, h, chunkX * 16 + lx, chunkZ * 16 + lz, code, waterDepth[column], palette, stand);
				if (tree >= 0) {
					out[n++] = column << 16 | tree;
				}
			}
		}
		return Arrays.copyOf(out, n);
	}

	/**
	 * The tree of a kept candidate (cell (gx, gz) with hash h, world column (x, z)) under its habitat code: the layer and
	 * tree index packed as in {@link #of} (without the column), or -1 for none.
	 */
	private static int decide(Pattern pattern, int gx, int gz, long h, int x, int z, int code, int depth, Palette palette,
			Stand stand) {
		int r = palette.ruleFor(code);
		if (r < 0) {
			return -1;
		}
		Rule rule = palette.rules().get(r);
		long h2 = Noise.mix(h ^ DRAW_SALT);
		Layers layers = rule.layers();
		StandSite site = stand.crowns() == null ? null : stand.site(x, z);
		Rule list = rule;
		if (layers == null) {
			double chance = chance(rule.treesPerChunk(), stand.gaps() != null);
			if (unit(h2) >= chance) {
				return -1;
			}
			double spacing = rule.spacing();
			if (spacing > MIN_SPACING && pattern.thinned(gx, gz, h, spacing, chance)) {
				return -1;
			}
			if (stand.gap(x, z)) {
				return -1;
			}
		} else {
			boolean natural = site != null ? site.natural() : stand.map() != null && stand.map().natural(x, z);
			AgeClass age = site != null ? site.age() : stand.map() != null ? stand.map().ageClass(x, z) : AgeClass.MATURE;
			boolean gap = site != null && site.gap();
			if (pattern.rank(gx, gz, h) >= layers.chance(natural, age, gap)) {
				return -1;
			}
			CrownLayer layer = site == null ? CrownLayer.CANOPY : site.layer();
			list = switch (layer) {
				case CANOPY -> natural || layers.managedCanopy() == null ? rule : layers.managedCanopy();
				case SUBCANOPY -> layers.subcanopy();
				case REGENERATION -> gap && site.gapDepth() >= GAP_INTERIOR ? layers.gapRegeneration() : layers.regeneration();
			};
		}
		int tree = pickTree(list, pl.polishforests.worldgen.habitat.Habitat.flags(code), depth, stand.ramp(), unit(Noise.mix(h2)));
		if (tree < 0) {
			return -1;
		}
		return site == null ? tree : site.layer().ordinal() << LAYER_SHIFT | tree;
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

	/**
	 * Read-only view of the world's candidate pattern for the planners of the vertical slice (crown territories, step
	 * V5): the kept candidates of an area in world block coordinates, before any habitat, palette or gap rule, so a pure
	 * function of the world seed. Kept states are memoized in a table shared by the threads (step O1), so an instance
	 * may serve any thread; the results never depend on what it was asked before.
	 */
	public static final class Candidates {
		private final Pattern pattern;

		public Candidates(long worldSeed) {
			this.pattern = new Pattern(worldSeed);
		}

		private Pattern pattern() {
			return pattern;
		}

		/** Receives a kept candidate: its column in world block coordinates. */
		@FunctionalInterface
		public interface Visitor {
			void accept(int x, int z);
		}

		/**
		 * Calls the visitor for every kept candidate whose column lies in [minX, maxX] × [minZ, maxZ] (block coordinates,
		 * inclusive), in the order of the cells (x major).
		 */
		public void forEach(int minX, int minZ, int maxX, int maxZ, Visitor visitor) {
			Pattern pattern = pattern();
			int gx0 = Math.floorDiv(minX, CELL);
			int gx1 = Math.floorDiv(maxX, CELL);
			int gz0 = Math.floorDiv(minZ, CELL);
			int gz1 = Math.floorDiv(maxZ, CELL);
			for (int gx = gx0; gx <= gx1; gx++) {
				for (int gz = gz0; gz <= gz1; gz++) {
					if (!pattern.kept(gx, gz)) {
						continue;
					}
					long h = pattern.hash(gx, gz);
					int x = gx * CELL + offsetX(h);
					int z = gz * CELL + offsetZ(h);
					if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
						visitor.accept(x, z);
					}
				}
			}
		}

		/**
		 * The chance draw in [0, 1) of the candidate of the cell holding column (x, z): the candidate becomes a tree only if
		 * its draw is below the chance of its rule ({@link TreeStandPlan#chance}). A pure function of the world seed.
		 */
		public double draw(int x, int z) {
			long h = pattern().hash(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
			return unit(Noise.mix(h ^ DRAW_SALT));
		}

		/**
		 * The rank value in [0, 1) of the kept candidate at column (x, z), which decides its tree under a layered rule
		 * ({@link Layers}): uniform over the candidates like the draw, but low where the neighbors' draws are higher, so the
		 * candidates without a tree spread out evenly (a thinned stand, not clumps and clearings). A pure function of the
		 * world seed.
		 */
		public double rank(int x, int z) {
			Pattern pattern = pattern();
			int gx = Math.floorDiv(x, CELL);
			int gz = Math.floorDiv(z, CELL);
			return pattern.rank(gx, gz, pattern.hash(gx, gz));
		}

		/** {@link #rank} without the memo of step O2 (tests). */
		double rankUncached(int x, int z) {
			Pattern pattern = pattern();
			int gx = Math.floorDiv(x, CELL);
			int gz = Math.floorDiv(z, CELL);
			return pattern.rankUncached(gx, gz, pattern.hash(gx, gz));
		}

		/**
		 * Whether the kept candidate at column (x, z) surely gets no tree under every rule whose chance is at most
		 * {@code chance}, whatever its habitat code: its draw is at or above {@code chance} or it stands in a gap of the
		 * stand. With {@link Palette#highestChance} this needs no habitats at all (only the seed and the gap noise), so
		 * every chunk gives the same answer, also for a candidate whose rule comes from an ecotone (crown territories,
		 * round 2 of the V5 review). The thinning of sparse stands, a missing rule and the water depth can remove more trees.
		 */
		public boolean treeless(int x, int z, double chance, Stand stand) {
			return draw(x, z) >= chance || stand.gap(x, z);
		}

		/**
		 * The tree of the kept candidate at world column (x, z) under the habitat code {@code code} and the water depth
		 * {@code depth} of its column, with the conditions of its chunk ({@code stand}, with that chunk's range ramp): the
		 * packed layer and tree index as in {@link TreeStandPlan#of} (without the column), or -1 for none. The same decision
		 * as the plan of the candidate's chunk, for the exact vacancies of the crown territories.
		 */
		public int decide(int x, int z, int code, int depth, Palette palette, Stand stand) {
			Pattern pattern = pattern();
			int gx = Math.floorDiv(x, CELL);
			int gz = Math.floorDiv(z, CELL);
			return TreeStandPlan.decide(pattern, gx, gz, pattern.hash(gx, gz), x, z, code, depth, palette, stand);
		}

		/** Whether the column (x, z) holds a kept candidate. */
		public boolean kept(int x, int z) {
			Pattern pattern = pattern();
			int gx = Math.floorDiv(x, CELL);
			int gz = Math.floorDiv(z, CELL);
			long h = pattern.hash(gx, gz);
			return gx * CELL + offsetX(h) == x && gz * CELL + offsetZ(h) == z && pattern.kept(gx, gz);
		}
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
	 * Memo of the kept states of the candidate pattern of one world seed (step O1), shared by the generation threads: a
	 * direct-mapped table of {@value #KEPT_BITS} bits, each slot one {@code long} with the cell, the state and a valid bit,
	 * read and written whole (opaque access), so a thread never sees half of another thread's entry. A kept state is a pure
	 * function of the seed and the cell, so an entry is the computed answer; a slot taken by another cell only means
	 * computing again. Before O1 every chunk and every session had its own {@code HashMap<Long, Boolean>}, whose key
	 * hash (gx ^ gz) put the cells of an area into few bins.
	 */
	private static final class KeptTable {
		private static final int KEPT_BITS = 18;
		/** Cells with coordinates of up to this many bits (signed) are memoized: ±33 million cells, past the world border. */
		private static final int COORD_BITS = 26;
		private static final long COORD_MASK = (1L << COORD_BITS) - 1;
		private static volatile @Nullable KeptTable last;

		private final long worldSeed;
		private final java.util.concurrent.atomic.AtomicLongArray slots = new java.util.concurrent.atomic.AtomicLongArray(1 << KEPT_BITS);

		private KeptTable(long worldSeed) {
			this.worldSeed = worldSeed;
		}

		/** The table of a world seed (the last one asked for is kept; another seed gets a new table). */
		static KeptTable of(long worldSeed) {
			KeptTable t = last;
			if (t == null || t.worldSeed != worldSeed) {
				t = new KeptTable(worldSeed);
				last = t;
			}
			return t;
		}

		private static boolean fits(int g) {
			return g >= -(1 << (COORD_BITS - 1)) && g < 1 << (COORD_BITS - 1);
		}

		/** The cell part of a slot: gx in bits 38-63, gz in bits 12-37. */
		private static long cell(int gx, int gz) {
			return (gx & COORD_MASK) << 38 | (gz & COORD_MASK) << 12;
		}

		private static int slot(int gx, int gz) {
			return (int) (Noise.mix(gx * 0x9E37_79B9_7F4A_7C15L + gz * 0xC2B2_AE3D_27D4_EB4FL) >>> (64 - KEPT_BITS));
		}

		/** The memoized state of a cell: 1 kept, 0 not, -1 unknown. */
		int get(int gx, int gz) {
			if (!fits(gx) || !fits(gz)) {
				return -1;
			}
			long e = slots.getOpaque(slot(gx, gz));
			if ((e & 1) == 0 || (e & ~0xFFFL) != cell(gx, gz)) {
				return -1;
			}
			return (int) (e >>> 1 & 1);
		}

		void put(int gx, int gz, boolean kept) {
			if (fits(gx) && fits(gz)) {
				slots.setOpaque(slot(gx, gz), cell(gx, gz) | (kept ? 2 : 0) | 1);
			}
		}

		/**
		 * Step O2: the rank values of candidates ({@link Pattern#rank}), direct-mapped by the cell in {@value #RANK_BITS}
		 * bits. An entry is an immutable record (final fields), so a thread that reads an entry another thread has just
		 * written sees it whole; the rank is a pure function of the seed, the cell and its hash, all three part of the key.
		 */
		private static final int RANK_BITS = 16;
		private final RankEntry[] ranks = new RankEntry[1 << RANK_BITS];

		@Nullable RankEntry rank(int gx, int gz) {
			return ranks[(int) (Noise.mix(gx * 0xC2B2_AE3D_27D4_EB4FL + gz * 0x9E37_79B9_7F4A_7C15L) >>> (64 - RANK_BITS))];
		}

		void putRank(RankEntry e) {
			ranks[(int) (Noise.mix(e.gx() * 0xC2B2_AE3D_27D4_EB4FL + e.gz() * 0x9E37_79B9_7F4A_7C15L) >>> (64 - RANK_BITS))] = e;
		}
	}

	/** A memoized rank value of the candidate of cell (gx, gz) with hash h (step O2). */
	private record RankEntry(int gx, int gz, long h, double rank) {
	}

	/**
	 * The world's candidate pattern: candidate of cell (gx, gz) and its mark from a hash of (world seed, cell); a
	 * candidate is kept unless a kept candidate with a lower mark is closer than {@link #MIN_SPACING}. Kept states are
	 * memoized in a table shared by all threads ({@link KeptTable}, step O1).
	 */
	private static final class Pattern {
		private final long seed;
		private final KeptTable table;

		Pattern(long worldSeed) {
			this.seed = worldSeed ^ SALT;
			this.table = KeptTable.of(worldSeed);
		}

		long hash(int gx, int gz) {
			return Noise.mix(seed ^ Noise.mix(gx * 0x9E37_79B9_7F4A_7C15L + gz * 0xC2B2_AE3D_27D4_EB4FL));
		}

		boolean kept(int gx, int gz) {
			int known = table.get(gx, gz);
			if (known >= 0) {
				return known == 1;
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
			table.put(gx, gz, result);
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
							&& unit(Noise.mix(hn ^ DRAW_SALT)) < chance && kept(gx + di, gz + dj)) {
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Rank value of the candidate of cell (gx, gz) with hash h (step V9): (k + u) / n, where n counts it and the kept
		 * candidates within {@value #RANK_RADIUS} blocks, k those of them with a lower draw and u a uniform value of its
		 * own; uniform in [0, 1) for every candidate (its draw's rank among n independent draws is uniform), and the
		 * candidates with the highest draws of their neighborhood get the highest values.
		 */
		double rank(int gx, int gz, long h) {
			RankEntry e = table.rank(gx, gz);
			if (e != null && e.gx() == gx && e.gz() == gz && e.h() == h) {
				return e.rank();
			}
			double r = rankUncached(gx, gz, h);
			table.putRank(new RankEntry(gx, gz, h, r));
			return r;
		}

		/** The rank value computed without the memo of step O2 ({@link KeptTable#rank}). */
		double rankUncached(int gx, int gz, long h) {
			int x = gx * CELL + offsetX(h);
			int z = gz * CELL + offsetZ(h);
			double d = unit(Noise.mix(h ^ DRAW_SALT));
			int reach = RANK_RADIUS / CELL + 1;
			int n = 1;
			int lower = 0;
			for (int di = -reach; di <= reach; di++) {
				for (int dj = -reach; dj <= reach; dj++) {
					if (di == 0 && dj == 0) {
						continue;
					}
					long hn = hash(gx + di, gz + dj);
					int dx = (gx + di) * CELL + offsetX(hn) - x;
					int dz = (gz + dj) * CELL + offsetZ(hn) - z;
					if (dx * dx + dz * dz > RANK_RADIUS * RANK_RADIUS || !kept(gx + di, gz + dj)) {
						continue;
					}
					n++;
					double dn = unit(Noise.mix(hn ^ DRAW_SALT));
					if (dn < d || dn == d && lower(hn, gx + di, gz + dj, h, gx, gz)) {
						lower++;
					}
				}
			}
			return (lower + unit(Noise.mix(h ^ RANK_SALT))) / n;
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
